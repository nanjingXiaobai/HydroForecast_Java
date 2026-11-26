package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data;

import com.nari.slsd.hps.dbmgr.conf.DBManager;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.support.ForecastLogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * 基于生产工程现有 DBManager 的云南大唐只读数据访问实现。
 *
 * <p>实测点号按 ID_ATTR_CODE 匹配；配置中的 senid 保存完整点号，GFS REGID
 * 仍由电站 XML 配置提供，禁止在代码中根据名称猜测。</p>
 */
@Repository
@ConditionalOnYunnanDatangFeature
public class DbManagerForecastSourceDataRepository implements ForecastSourceDataRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbManagerForecastSourceDataRepository.class);
    private static final String DATABASE = "wds";
    private static final int MAX_SENSOR_COUNT = 200;

    /**
     * 其他现场没有该配置时默认为 false，避免升级公共生产包后突然输出大量 SQL。
     * 云南大唐现场在 application.properties 中显式开启。
     */
    @Value("${forecast.logging.sql-enabled:false}")
    private boolean sqlLoggingEnabled;

    @Override
    public List<DatangHourlyValue> queryShatuoDailyPlan(String adid, Calendar end) {
        StringBuilder expanded = new StringBuilder();
        for (int i = 0; i < 96; i++) {
            if (i > 0) expanded.append(" UNION ALL ");
            expanded.append("SELECT ADID, TIME AS PLANTIME, EDITTIME, TIME+").append(i)
                    .append(" AS DATATIME, V").append(i).append(" AS VAL FROM P");
        }
        // 每个业务日取最近计划的有效值；-99/NULL 不阻挡较早批次补缺，零有效。
        String sql = "WITH P AS (SELECT * FROM WDS.PLANLOAD WHERE ADID=? AND FLAG=98 AND TIME<=?), "
                + "E AS (" + expanded + "), R AS (SELECT ADID,DATATIME,VAL,"
                + "ROW_NUMBER() OVER(PARTITION BY DATATIME ORDER BY PLANTIME DESC,EDITTIME DESC) RN "
                + "FROM E WHERE VAL>=0 AND DATATIME<=?) "
                + "SELECT ADID AS SENID,DATATIME AS TIME,VAL AS VALUE FROM R WHERE RN=1 ORDER BY DATATIME";
        return executeSql("沙沱日计划出库", sql, DatangHourlyValue.class,
                new Object[]{requireText(adid, "沙沱计划点号不能为空"), end, end});
    }

    @Override
    public List<ApiCurvePoint> queryApiCurvePoints(String curveName) {
        String name = requireText(curveName, "曲线名称不能为空");
        String sql = "SELECT D.CURVEID AS CURVEID, C.NAME AS CURVENAME, "
                + "D.V0 AS V0, D.V1 AS V1, D.V2 AS V2, D.V3 AS V3 "
                + "FROM INFOMODEL.WDS_HYDRO_CURVE C "
                + "JOIN INFOMODEL.WDS_HYDRO_CURVED D ON D.CURVEID=C.ID_ATTR_CODE "
                + "WHERE C.NAME=? ORDER BY D.V0,D.V1,D.V2,D.V3";
        return executeSql("API模型单位线查询", sql, ApiCurvePoint.class, new Object[]{name});
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

        String tableName = "hour".equals(timeType) ? "HOURDB" : "DAYDB";
        StringBuilder placeholders = new StringBuilder();
        List<Object> parameters = new ArrayList<Object>();
        for (String sensorId : normalizedIds) {
            if (placeholders.length() > 0) placeholders.append(',');
            placeholders.append('?');
            parameters.add(sensorId);
        }
        parameters.add(beginTime);
        parameters.add(endTime);

        // 完整点号匹配 ID_ATTR_CODE；SENID 仅作对象映射别名，兼容既有配置和计算接口。
        String sql = "SELECT ID_ATTR_CODE AS SENID, TIME AS TIME, V AS VALUE, AVGV AS AVERAGEVALUE "
                + "FROM " + tableName + " WHERE ID_ATTR_CODE IN (" + placeholders + ") "
                + "AND TIME >= ? AND TIME <= ? ORDER BY ID_ATTR_CODE,TIME";
        return executeSql("云南大唐" + tableName + "历史数据查询", sql,
                DatangHourlyValue.class, parameters.toArray(new Object[parameters.size()]));
    }

    @Override
    public List<DatangHourlyValue> queryLatestDailyValueBefore(String sensorId, Calendar beforeTime) {
        String id = requireText(sensorId, "实测点号不能为空");
        if (beforeTime == null) throw new IllegalArgumentException("查询截止时间不能为空");
        String sql = "SELECT ID_ATTR_CODE AS SENID, TIME AS TIME, V AS VALUE, AVGV AS AVERAGEVALUE "
                + "FROM DAYDB WHERE ID_ATTR_CODE=? AND TIME=(SELECT MAX(TIME) FROM DAYDB "
                + "WHERE ID_ATTR_CODE=? AND TIME<? AND V>=0 AND TO_CHAR(TIME,'HH24:MI:SS')='00:00:00')";
        return executeSql("沙沱最新实测日流量", sql, DatangHourlyValue.class, new Object[]{id, id, beforeTime});
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
        return executeSql("GFS预报降雨查询", sql, GfsForecastRainValue.class, parameters);
    }

    /** 统一记录 DBManager 查询的 SQL、参数、结果行数和耗时。 */
    private <T> List<T> executeSql(String operation, String sql, Class<T> rowType, Object[] parameters) {
        long startedAt = System.currentTimeMillis();
        if (sqlLoggingEnabled) {
            LOGGER.info("[云南大唐][SQL] operation={}, database={}, parameterizedSql={}", operation, DATABASE,
                    ForecastLogHelper.compactSql(sql));
            LOGGER.info("[云南大唐][SQL] operation={}, parameters={}", operation,
                    ForecastLogHelper.formatParameters(parameters));
            LOGGER.info("[云南大唐][SQL] operation={}, executableSql={}", operation,
                    ForecastLogHelper.renderSql(sql, parameters));
        }
        try {
            List<T> rows = DBManager.executeSql(sql, rowType, parameters, DATABASE);
            LOGGER.info("[云南大唐][数据库查询] operation={}, rows={}, elapsedMs={}", operation,
                    rows == null ? 0 : rows.size(), System.currentTimeMillis() - startedAt);
            return rows;
        } catch (RuntimeException ex) {
            LOGGER.error("[云南大唐][数据库查询] operation={} 失败, elapsedMs={}, sql={}", operation,
                    System.currentTimeMillis() - startedAt, ForecastLogHelper.compactSql(sql), ex);
            throw ex;
        }
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

}
