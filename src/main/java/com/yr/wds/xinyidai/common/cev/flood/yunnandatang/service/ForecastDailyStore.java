package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import java.sql.*;
import java.util.*;

/** 唯一物理表；一次任务所有日序列在调用方事务内提交。 */
final class ForecastDailyStore {
    static final String TABLE = "YDT_FORECAST_RESULT";
    private static final String META = "ID,TASK_ID,SAVE_KEY,SUMMARY_FLAG,CALCULATION_ID,STATION_CODE,STATION_NAME,MODULE,MODEL_CODE,"
            + "MODEL_VERSION,CONFIG_VERSION,FORECAST_TIME,SAVED_TIME,CREATED_BY,TENANT_ID,REMARK,DELETED,CREATED_TIME,UPDATED_TIME,"
            + "OBJECT_CODE,DATA_KIND,SERIES_CODE,SERIES_NAME,SENID,UNIT,DATA_SOURCE,WEIGHT,DATA_CODE,TIME,TIME_STEP,PRESENT_MASK,"
            + "PARAMETER_TYPE,TEXT_VALUE,ROW_KEY,ID_ATTR_CODE";

    static void save(Connection c, String taskId, ForecastContracts.ResultSaveRequest request,
                     StationModelRoute route, String configVersion, java.util.Date now, List<ForecastDailyRows.Row> rows) throws Exception {
        StringBuilder columns = new StringBuilder(META), values = new StringBuilder();
        for (int i = 0; i < 96; i++) columns.append(",V").append(i);
        for (int i = 0; i < META.split(",").length + 96; i++) { if (i > 0) values.append(','); values.append('?'); }
        String sql = "INSERT INTO " + TABLE + "(" + columns + ") VALUES(" + values + ")";
        Timestamp saved = new Timestamp(now.getTime());
        boolean first = true;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            int pending = 0;
            for (ForecastDailyRows.Row row : rows) {
                List<Object> args = new ArrayList<Object>(Arrays.<Object>asList(
                        first ? taskId : UUID.randomUUID().toString(), taskId, first ? request.getCalculationId() : null,
                        first ? 1 : 0, request.getCalculationId(), request.getStationCode(), route.getStation().getName(),
                        route.getStation().getModule(), request.getModelCode(), text(request.getModelVersion(), "unknown"),
                        configVersion, Timestamp.valueOf(ForecastDailyRows.parse(request.getForecastTime())), saved,
                        limit(request.getForecaster(), 100), null, limit(ForecastSaveDescription.value(request, route.getStation().getModule()), 500),
                        0, saved, saved, row.station, row.kind, row.series, row.name, row.senid, row.unit, row.source, row.weight,
                        row.code, Timestamp.valueOf(row.day + " 00:00:00"), row.step, row.mask(), row.parameterType, row.text, row.key(), row.idAttrCode));
                args.addAll(Arrays.asList(row.values));
                for (int i = 0; i < args.size(); i++) s.setObject(i + 1, args.get(i));
                s.addBatch(); first = false;
                if (++pending == 200) { s.executeBatch(); s.clearBatch(); pending = 0; }
            }
            if (pending != 0) s.executeBatch();
        }
    }

    static List<ForecastDailyRows.Row> load(Connection c, String taskId, boolean tankOnly) throws Exception {
        List<ForecastDailyRows.Row> rows = new ArrayList<ForecastDailyRows.Row>();
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM " + TABLE
                + " WHERE TASK_ID=? AND DELETED=0"
                + (tankOnly ? " AND DATA_KIND IN ('TANK','PENGSHUI')" : "")
                + " ORDER BY OBJECT_CODE,DATA_KIND,TIME,DATA_CODE")) {
            s.setString(1, taskId);
            s.setFetchSize(500);
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    ForecastDailyRows.Row r = new ForecastDailyRows.Row();
                    r.idAttrCode = rs.getString("ID_ATTR_CODE");
                    r.station = rs.getString("OBJECT_CODE"); r.kind = rs.getString("DATA_KIND");
                    r.series = rs.getString("SERIES_CODE"); r.name = rs.getString("SERIES_NAME");
                    r.senid = rs.getString("SENID"); r.unit = rs.getString("UNIT"); r.source = rs.getString("DATA_SOURCE");
                    r.weight = number(rs, "WEIGHT"); r.code = rs.getString("DATA_CODE");
                    r.day = rs.getTimestamp("TIME").toLocalDateTime().toLocalDate().toString(); r.step = rs.getInt("TIME_STEP");
                    r.parameterType = rs.getString("PARAMETER_TYPE"); r.text = rs.getString("TEXT_VALUE");
                    String mask = rs.getString("PRESENT_MASK");
                    for (int i = 0; i < 96; i++) { r.present[i] = mask.charAt(i) == '1'; if (r.present[i]) r.values[i] = number(rs, "V" + i); }
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    private static Double number(ResultSet rs, String name) throws SQLException {
        double value = rs.getDouble(name); return rs.wasNull() ? null : value;
    }
    private static String text(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value.trim(); }
    private static String limit(String value, int size) { String s = value == null ? "" : value.trim(); return s.length() <= size ? s : s.substring(0, size); }
}
