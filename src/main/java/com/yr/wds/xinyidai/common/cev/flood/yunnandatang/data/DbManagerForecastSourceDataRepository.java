package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data;

import com.nari.slsd.hps.dbmgr.conf.DBManager;
import com.yr.wds.xinyidai.common.cev.flood.service.history.HistoryDataService;
import org.springframework.stereotype.Repository;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于生产工程现有 DBManager 的云南大唐只读数据访问实现。
 *
 * <p>表名和字段名来自现场数据库实际元数据；测点 SENID、属性码和 GFS REGID
 * 仍由电站 XML 配置提供，禁止在代码中根据名称猜测。</p>
 */
@Repository
public class DbManagerForecastSourceDataRepository implements ForecastSourceDataRepository {
    private static final String DATABASE = "wds";
    private static final int MAX_SENSOR_COUNT = 200;

    @Override
    public List<ApiCurvePoint> queryApiCurvePoints(String curveName) {
        String name = requireText(curveName, "曲线名称不能为空");
        String sql = "SELECT D.CURVEID AS CURVEID, C.NAME AS CURVENAME, "
                + "D.V0 AS V0, D.V1 AS V1, D.V2 AS V2, D.V3 AS V3 "
                + "FROM INFOMODEL.WDS_HYDRO_CURVE C "
                + "JOIN INFOMODEL.WDS_HYDRO_CURVED D ON D.CURVEID=C.ID_ATTR_CODE "
                + "WHERE C.NAME=? ORDER BY D.V0,D.V1,D.V2,D.V3";
        return DBManager.executeSql(sql, ApiCurvePoint.class, new Object[]{name}, DATABASE);
    }

    @Override
    public List<DatangHourlyValue> queryHistoryValues(List<String> sensorIds,
                                                      Calendar beginTime,
                                                      Calendar endTime,
                                                      String timeType) {
        List<String> normalizedIds = normalizeSensorIds(sensorIds);
        validateTimeRange(beginTime, endTime);
        if (!"hour".equals(timeType) && !"day".equals(timeType)) {
            throw new IllegalArgumentException("timeType只允许hour或day");
        }

        Map<String, List<Map<String, Object>>> grouped = HistoryDataService.queryHistoryData(
                normalizedIds, formatQueryTime(beginTime), formatQueryTime(endTime), timeType);
        List<DatangHourlyValue> result = new ArrayList<DatangHourlyValue>();
        for (String sensorId : normalizedIds) {
            List<Map<String, Object>> values = grouped.get(sensorId);
            if (values == null) continue;
            for (Map<String, Object> value : values) {
                DatangHourlyValue row = new DatangHourlyValue();
                row.setSenId(sensorId);
                row.setTime(parseResultTime(String.valueOf(value.get("time"))));
                row.setValue(number(value.get("v")));
                row.setAverageValue(number(value.get("avgv")));
                result.add(row);
            }
        }
        return result;
    }

    @Override
    public List<GfsForecastRainValue> queryLatestGfsRain(String regionId,
                                                          int subtype,
                                                          Calendar latestIssueTime,
                                                          Calendar beginTime,
                                                          Calendar endTime) {
        String normalizedRegionId = requireText(regionId, "GFS REGID不能为空");
        if (subtype < 0) throw new IllegalArgumentException("GFS subtype不能小于0");
        if (latestIssueTime == null) throw new IllegalArgumentException("GFS最晚起报时间不能为空");
        validateTimeRange(beginTime, endTime);

        String sql = "SELECT REGID AS REGIONID, FTIME AS FORECASTISSUETIME, "
                + "BTIME AS DATATIME, TIMESPAN AS TIMESPAN, "
                + "AVERPRE AS AVERAGEPRECIPITATION, "
                + "EXPLAINS AS DESCRIPTION, SUBTYPE AS SUBTYPE FROM WEA_GFSFORRAIN "
                + "WHERE REGID = ? AND SUBTYPE = ? AND BTIME >= ? AND BTIME <= ? "
                + "AND FTIME = (SELECT MAX(FTIME) FROM WEA_GFSFORRAIN "
                + "WHERE REGID = ? AND SUBTYPE = ? AND FTIME <= ?) "
                + "ORDER BY BTIME,TIMESPAN";
        Object[] parameters = new Object[]{normalizedRegionId, subtype, beginTime, endTime,
                normalizedRegionId, subtype, latestIssueTime};
        return DBManager.executeSql(sql, GfsForecastRainValue.class, parameters, DATABASE);
    }

    private List<String> normalizeSensorIds(List<String> sensorIds) {
        if (sensorIds == null || sensorIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个大唐小时数据SENID");
        }
        if (sensorIds.size() > MAX_SENSOR_COUNT) {
            throw new IllegalArgumentException("单次查询SENID不能超过" + MAX_SENSOR_COUNT + "个");
        }
        List<String> result = new ArrayList<String>();
        for (String sensorId : sensorIds) {
            String normalized = requireText(sensorId, "SENID不能为空");
            if (!result.contains(normalized)) result.add(normalized);
        }
        return Collections.unmodifiableList(result);
    }

    private void validateTimeRange(Calendar beginTime, Calendar endTime) {
        if (beginTime == null || endTime == null) {
            throw new IllegalArgumentException("查询开始时间和结束时间不能为空");
        }
        if (beginTime.after(endTime)) {
            throw new IllegalArgumentException("查询开始时间不能晚于结束时间");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String formatQueryTime(Calendar value) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value.getTime());
    }

    private Calendar parseResultTime(String value) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
            format.setLenient(false);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(format.parse(value));
            return calendar;
        } catch (ParseException ex) {
            throw new IllegalArgumentException("历史数据时间格式无法解析: " + value, ex);
        }
    }

    private Double number(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("历史数据值不是数字: " + value, ex);
        }
    }
}
