package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/** 同一事务内发布模型流量到 BS 结果表；不发布输入雨量、沙沱模型或水箱预热历史。 */
final class ForecastStandardResultStore {
    static final class Series {
        String point, station, name;
        int step;
        final SortedMap<LocalDateTime, Double> values = new TreeMap<>();
    }

    static boolean flow(ForecastDailyRows.Row r) {
        return ("API".equals(r.kind) && "discharge".equals(r.code))
                || ("TANK".equals(r.kind) && "flow".equals(r.code))
                || ("PENGSHUI".equals(r.kind) && ("regionalFlowTotal".equals(r.code) || "pengshuiInflow".equals(r.code)));
    }

    static List<Series> collect(ForecastContracts.ResultSaveRequest request, String module,
                               List<ForecastDailyRows.Row> rows, StationModelConfiguration config) {
        if (!"API_MODEL".equals(module) && !"TANK_MODEL".equals(module)) return Collections.emptyList();
        List<ForecastContracts.InputResponse> inputs = new ArrayList<>();
        if (request.getInput() != null) inputs.add(request.getInput());
        if (request.getInputs() != null) inputs.addAll(request.getInputs());
        Map<String, Series> series = new LinkedHashMap<>();
        for (ForecastDailyRows.Row r : rows) {
            if (!flow(r)) continue;
            if (r.idAttrCode == null || !r.idAttrCode.matches("[0-9]+"))
                throw new StationModelConfigException("缺少有效的预报结果点号: " + r.station + "/" + r.code);
            ForecastContracts.InputResponse input = null;
            for (ForecastContracts.InputResponse candidate : inputs)
                if (candidate != null && r.station.equals(candidate.getStationCode())) { input = candidate; break; }
            if (input == null && !inputs.isEmpty()) input = inputs.get(0); // 彭水汇总共用四分区的预报窗口。
            if (input == null || input.getForecastBeginTime() == null || input.getForecastEndTime() == null)
                throw new StationModelConfigException("保存流量缺少预报起止时间: " + r.station);
            LocalDateTime begin = ForecastDailyRows.parse(input.getForecastBeginTime());
            LocalDateTime end = ForecastDailyRows.parse(input.getForecastEndTime());
            Series s = series.get(r.idAttrCode);
            if (s == null) {
                s = new Series(); s.point = r.idAttrCode; s.station = r.station; s.step = r.step;
                StationDefinition station = config.getStationMap().get(r.station);
                s.name = "regionalFlowTotal".equals(r.code) ? "四分区流量合计" : station.getName();
                series.put(s.point, s);
            } else if (!s.station.equals(r.station) || s.step != r.step) {
                throw new StationModelConfigException("不同结果不能共用预报点号: " + r.idAttrCode);
            }
            LocalDateTime day = ForecastDailyRows.parse(r.day + " 00:00:00");
            for (int i = 0; i < r.present.length; i++) {
                LocalDateTime time = day.plusMinutes((long) r.step * i);
                if (!r.present[i] || time.isBefore(begin) || time.isAfter(end)) continue;
                Double value = r.values[i];
                if (value == null || !Double.isFinite(value))
                    throw new StationModelConfigException("预报流量无效: " + s.name + "/" + time);
                if (s.values.put(time, value) != null)
                    throw new StationModelConfigException("预报点号时刻重复: " + s.point + "/" + time);
            }
        }
        List<Series> result = new ArrayList<>();
        for (Series s : series.values()) if (!s.values.isEmpty()) result.add(s);
        if (result.isEmpty()) throw new StationModelConfigException("所选预报时段没有可发布的流量结果");
        return result;
    }

    // HFIDX 无唯一约束的现场库也不能覆盖原方案。由保存入口串行选择秒级制作时间。
    static java.util.Date availableTime(Connection c, java.util.Date now, List<Series> series) throws SQLException {
        long millis = now.getTime() / 1000 * 1000;
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM HFIDX WHERE ADID=? AND YBT=?")) {
            for (int attempt = 0; attempt < 3600; attempt++, millis += 1000) {
                boolean occupied = false;
                for (Series s : series) {
                    p.setBigDecimal(1, new BigDecimal(s.point)); p.setTimestamp(2, new Timestamp(millis));
                    try (ResultSet rs = p.executeQuery()) { rs.next(); if (rs.getLong(1) > 0) occupied = true; }
                }
                if (!occupied) return new java.util.Date(millis);
            }
        }
        throw new SQLException("无法分配不重复的预报制作时间");
    }

    static int save(Connection c, ForecastContracts.ResultSaveRequest request, String module,
                    java.util.Date made, List<Series> series) throws SQLException {
        if (series.isEmpty()) return 0;
        String model = "API_MODEL".equals(module) ? "API模型" : "水箱模型";
        Timestamp basis = Timestamp.valueOf(ForecastDailyRows.parse(request.getForecastTime()));
        Timestamp created = new Timestamp(made.getTime());
        // BS 约定：YBT/T=制作时间，YBDT=依据时间，DT=结果时刻；日3、小时2，RESTYPE4=时段平均流量。
        String sql = "INSERT INTO HFIDX (ADID,YBT,YBDT,YBNUM,TIMETYPE,TIMESPAN,BFP,BADJ,HFFL,FXT,BREC,YBR,BPUB,SCHNAME,FOREOBJNAME,SECNAME,MODELNAME,RESTYPE,FORECASTTYPE) VALUES (?,?,?,?,?,?,0,0,?,?,0,?,0,?,?,?,?,4,0)";
        int count = 0;
        try (PreparedStatement index = c.prepareStatement(sql);
             PreparedStatement data = c.prepareStatement("INSERT INTO ADXLD (ADID,T,D,S,DT,FP,YBDT) VALUES (?,?,?,1,?,0,?)")) {
            for (Series s : series) {
                Map.Entry<LocalDateTime,Double> peak = null;
                for (Map.Entry<LocalDateTime,Double> v : s.values.entrySet())
                    if (peak == null || v.getValue() > peak.getValue()) peak = v;
                index.setBigDecimal(1, new BigDecimal(s.point)); index.setTimestamp(2, created);
                index.setTimestamp(3, basis); index.setInt(4, s.values.size());
                index.setInt(5, s.step == 1440 ? 3 : 2); index.setInt(6, 1);
                index.setDouble(7, peak.getValue()); index.setTimestamp(8, Timestamp.valueOf(peak.getKey()));
                index.setString(9, request.getForecaster()); index.setString(10, request.getModelDescription());
                index.setString(11, s.name); index.setString(12, s.name); index.setString(13, model);
                index.executeUpdate();
                for (Map.Entry<LocalDateTime,Double> v : s.values.entrySet()) {
                    data.setBigDecimal(1, new BigDecimal(s.point)); data.setTimestamp(2, created);
                    data.setDouble(3, v.getValue()); data.setTimestamp(4, Timestamp.valueOf(v.getKey()));
                    data.setTimestamp(5, basis); data.addBatch();
                    if (++count % 200 == 0) { data.executeBatch(); data.clearBatch(); }
                }
            }
            if (count % 200 != 0) data.executeBatch();
        }
        return count;
    }

    static void addLinks(List<ForecastDailyRows.Row> rows, List<Series> series, String forecastTime) {
        for (Series s : series) {
            ForecastDailyRows.Row r = new ForecastDailyRows.Row();
            r.kind = "PARAMETER"; r.station = s.station; r.series = "__bs_link"; r.code = s.point; r.parameterType = "NUMBER";
            r.day = ForecastDailyRows.parse(forecastTime).toLocalDate().toString();
            r.step = 1440; r.idAttrCode = s.point; r.present[0] = true; r.values[0] = (double) s.values.size();
            rows.add(r);
        }
    }

    /** 仅删除由此任务确实发布的键，不碰旧快照或其他 BS 方案。 */
    static void delete(Connection c, String taskId) throws SQLException {
        try (PreparedStatement links = c.prepareStatement("SELECT ID_ATTR_CODE,SAVED_TIME,FORECAST_TIME FROM YDT_FORECAST_RESULT WHERE TASK_ID=? AND DATA_KIND='PARAMETER' AND SERIES_CODE='__bs_link' AND DELETED=0");
             PreparedStatement data = c.prepareStatement("DELETE FROM ADXLD WHERE ADID=? AND T=? AND YBDT=?");
             PreparedStatement index = c.prepareStatement("DELETE FROM HFIDX WHERE ADID=? AND YBT=? AND YBDT=?")) {
            links.setString(1, taskId);
            try (ResultSet rs = links.executeQuery()) {
                while (rs.next()) {
                    for (PreparedStatement p : Arrays.asList(data, index)) {
                        p.setBigDecimal(1, new BigDecimal(rs.getString(1)));
                        p.setTimestamp(2, rs.getTimestamp(2)); p.setTimestamp(3, rs.getTimestamp(3));
                        p.executeUpdate();
                    }
                }
            }
        }
    }
}
