package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.nari.slsd.hps.dbmgr.dao.ConnectionJDBC;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 使用生产框架现有 JDBC 连接保存云南大唐预报结果。
 *
 * <p>输入、参数和三类模型结果均写入明确业务列；本类不保存 JSON/CLOB，也不改变生产工程
 * 的数据库组件和启动结构。</p>
 */
@Service
public class ForecastResultService {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final StationModelRegistry registry;
    private final Environment environment;

    public ForecastResultService(StationModelRegistry registry, Environment environment) {
        this.registry = registry;
        this.environment = environment;
    }

    public Map<String, Object> save(ForecastContracts.ResultSaveRequest request) {
        validate(request);
        StationModelRoute route = registry.resolveRoute(request.getStationCode());
        if (!route.getModel().getCode().equals(request.getModelCode())) throw error("保存的模型与当前电站配置不一致");
        String taskId = UUID.randomUUID().toString();
        Date now = new Date();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                if (countCalculation(connection, request.getCalculationId()) > 0) throw error("该计算结果已经保存，不能重复保存");
                insertTask(connection, taskId, request, route, now);
                insertInputs(connection, taskId, request);
                insertParameters(connection, taskId, request);
                insertResults(connection, taskId, route.getStation().getModule(), request.getStationCode(), request.getResult());
                Map<String, Object> summary = taskSummary(connection, taskId);
                List<Map<String, Object>> summaries = new ArrayList<Map<String, Object>>(); summaries.add(summary);
                enrichSummaries(connection, summaries);
                connection.commit();
                return summary;
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof StationModelConfigException) throw (StationModelConfigException) ex;
                throw error("结果保存失败: " + ex.getMessage(), ex);
            }
        } catch (StationModelConfigException ex) { throw ex; }
        catch (Exception ex) { throw error("结果数据库连接失败: " + ex.getMessage(), ex); }
    }

    public Map<String, Object> list(String stationCode, String module, String beginTime, String endTime,
                                    String timeFilterType, String forecaster, int pageNum, int pageSize) {
        pageNum = Math.max(1, pageNum);
        pageSize = Math.max(1, Math.min(200, pageSize));
        List<Object> params = new ArrayList<Object>();
        String where = conditions(stationCode, module, beginTime, endTime, timeFilterType, forecaster, params);
        try (Connection connection = connection()) {
            long total;
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(1) FROM YDT_FORECAST_TASK WHERE " + where)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) { rs.next(); total = rs.getLong(1); }
            }
            int offset = (pageNum - 1) * pageSize;
            String sql = "SELECT * FROM (SELECT T.*,ROW_NUMBER() OVER(ORDER BY SAVED_TIME DESC,ID DESC) RN "
                    + "FROM YDT_FORECAST_TASK T WHERE " + where + ") X WHERE RN>? AND RN<=? ORDER BY RN";
            List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                List<Object> all = new ArrayList<Object>(params);
                all.add(offset); all.add(offset + pageSize); bind(statement, all);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) records.add(readSummary(rs)); }
            }
            enrichSummaries(connection, records);
            Map<String, Object> page = new LinkedHashMap<String, Object>();
            page.put("total", total); page.put("pageNum", pageNum); page.put("pageSize", pageSize); page.put("records", records);
            return page;
        } catch (Exception ex) { throw error("结果列表查询失败: " + ex.getMessage(), ex); }
    }

    /** 返回可直接绑定结果管理预报人下拉框的 value/label。 */
    public List<Map<String, Object>> forecasters() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        String sql = "SELECT DISTINCT CREATED_BY FROM YDT_FORECAST_TASK "
                + "WHERE DELETED=0 AND CREATED_BY IS NOT NULL AND TRIM(CREATED_BY)<>'' ORDER BY CREATED_BY";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (trim(name).isEmpty()) continue;
                Map<String, Object> option = new LinkedHashMap<String, Object>();
                option.put("value", name); option.put("label", name); result.add(option);
            }
            return result;
        } catch (Exception ex) { throw error("预报人查询失败: " + ex.getMessage(), ex); }
    }

    public Map<String, Object> detail(String taskId) {
        try (Connection connection = connection()) {
            Map<String, Object> detail = taskSummary(connection, taskId);
            List<Map<String, Object>> inputs = queryRows(connection,
                    "SELECT STATION_CODE AS \"stationCode\",SERIES_CODE AS \"seriesCode\",SERIES_NAME AS \"seriesName\"," 
                            + "SENID AS \"senid\",DATA_TIME AS \"dataTime\",ORIGINAL_VALUE AS \"originalValue\","
                            + "ACTUAL_VALUE AS \"actualValue\",DATA_SOURCE AS \"dataSource\",EDITABLE AS \"editable\","
                            + "MANUALLY_MODIFIED AS \"manuallyModified\",UNIT AS \"unit\",WEIGHT AS \"weight\",SORT_NO AS \"sortNo\" "
                            + "FROM YDT_FORECAST_INPUT_DATA WHERE TASK_ID=? ORDER BY STATION_CODE,SERIES_CODE,SORT_NO", taskId);
            List<Map<String, Object>> apiRows = queryRows(connection,
                    "SELECT SEQUENCE_NO AS \"sequenceNo\",RESULT_TIME AS \"resultTime\",RAINFALL AS \"rainfall\"," 
                            + "NET_RAINFALL AS \"netRainfall\",CUMULATIVE_NET_RAINFALL AS \"cumulativeNetRainfall\"," 
                            + "CUMULATIVE_RAINFALL AS \"cumulativeRainfall\",PA AS \"pa\",DISCHARGE AS \"discharge\" "
                            + "FROM YDT_FORECAST_API_RESULT WHERE TASK_ID=? ORDER BY SEQUENCE_NO", taskId);
            List<Map<String, Object>> tankRows = queryRows(connection,
                    "SELECT STATION_CODE AS \"stationCode\",RESULT_TIME AS \"resultTime\",SEQUENCE_NO AS \"sequenceNo\",RAINFALL AS \"rainfall\","
                            + "EVAPORATION AS \"evaporation\",TANK1_STORAGE_DEPTH AS \"tank1StorageDepth\",TANK1_UPPER_HOLE AS \"tank1UpperHole\","
                            + "TANK1_MIDDLE_HOLE AS \"tank1MiddleHole\",TANK1_LOWER_HOLE AS \"tank1LowerHole\",TANK1_INFILTRATION AS \"tank1Infiltration\","
                            + "TANK2_STORAGE_DEPTH AS \"tank2StorageDepth\",TANK2_OUTFLOW AS \"tank2Outflow\",TANK2_PERCOLATION AS \"tank2Percolation\","
                            + "TANK3_STORAGE_DEPTH AS \"tank3StorageDepth\",TANK3_MIDDLE_HOLE AS \"tank3MiddleHole\",TANK3_BOTTOM_HOLE AS \"tank3BottomHole\","
                            + "OUTFLOW_DEPTH AS \"outflowDepth\",FLOW AS \"flow\" FROM YDT_FORECAST_TANK_RESULT WHERE TASK_ID=? ORDER BY STATION_CODE,SEQUENCE_NO", taskId);
            List<Map<String, Object>> regressionRows = queryRows(connection,
                    "SELECT RESULT_TIME AS \"resultTime\",HORIZON AS \"horizon\",FORECAST_FLAG AS \"forecastFlag\",RAINFALL AS \"rainfall\"," 
                            + "SHATUO_OUTFLOW AS \"shatuoOutflow\",SHATUO_INFLOW AS \"shatuoInflow\",SHATUO_WATER_LEVEL AS \"shatuoWaterLevel\"," 
                            + "GOUPITAN_INFLOW AS \"goupitanInflow\",GOUPITAN_OUTFLOW AS \"goupitanOutflow\"," 
                            + "SHATUO_RAINFALL_AVERAGE AS \"shatuoRainfallAverage\",SHATUO_OUTFLOW_AVERAGE AS \"shatuoOutflowAverage\"," 
                            + "SHATUO_INFLOW_AVERAGE AS \"shatuoInflowAverage\",ACTUAL_MODEL_NAME AS \"actualModelName\"," 
                            + "RAW_FLOW AS \"rawFlow\",MOS_CORRECTION AS \"mosCorrection\",FORECAST_FLOW AS \"forecastFlow\" "
                            + "FROM YDT_FORECAST_REGRESSION_RESULT WHERE TASK_ID=? ORDER BY HORIZON", taskId);
            List<Map<String, Object>> pengshuiRows = queryRows(connection,
                    "SELECT RESULT_TIME AS \"resultTime\",SEQUENCE_NO AS \"sequenceNo\",JIANGBIN_FLOW AS \"jiangbinFlow\","
                            + "DAHEKOU_FLOW AS \"dahekouFlow\",YANGONG_FLOW AS \"yangongFlow\",GONGPENG_FLOW AS \"gongpengFlow\","
                            + "REGIONAL_FLOW_TOTAL AS \"regionalFlowTotal\",SHATUO_OUTFLOW AS \"shatuoOutflow\",PENGSHUI_INFLOW AS \"pengshuiInflow\" "
                            + "FROM YDT_FORECAST_PENGSHUI_RESULT WHERE TASK_ID=? ORDER BY SEQUENCE_NO", taskId);
            List<Map<String, Object>> objects = buildObjects(detail, inputs, apiRows, tankRows, regressionRows, pengshuiRows);
            detail.put("objects", objects);
            detail.put("topTableData", topTableData(detail, objects));
            List<String> names = new ArrayList<String>(); int periods = 0;
            for (Map<String, Object> object : objects) {
                names.add(string(object.get("stationName")));
                periods = Math.max(periods, integer(object.get("forecastPeriodCount")) == null ? 0 : integer(object.get("forecastPeriodCount")));
            }
            detail.put("forecastObjects", names); detail.put("forecastPeriodCount", periods);
            if (!objects.isEmpty()) {
                detail.put("timeType", objects.get(0).get("timeType"));
                detail.put("timeInterval", objects.get(0).get("timeInterval"));
            }
            return detail;
        } catch (StationModelConfigException ex) { throw ex; }
        catch (Exception ex) { throw error("结果详情查询失败: " + ex.getMessage(), ex); }
    }

    private List<Map<String, Object>> topTableData(Map<String, Object> detail,
            List<Map<String, Object>> objects) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < objects.size(); i++) {
            Map<String, Object> object = objects.get(i);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("objectIndex", i);
            row.put("stationCode", object.get("stationCode"));
            row.put("forecastSection", object.get("stationName"));
            String description = trim(string(detail.get("modelDescription")));
            row.put("modelSchemeName", description.isEmpty() ? object.get("modelName") : description);
            row.put("forecastResultType", resultType(string(detail.get("module")), string(object.get("stationCode"))));
            row.put("maximumFlow", object.get("maximumFlow"));
            row.put("maximumFlowTime", object.get("maximumFlowTime"));
            rows.add(row);
        }
        return rows;
    }

    private String resultType(String module, String stationCode) {
        if ("API_MODEL".equals(module)) return "入库流量";
        if ("REGRESSION_MODEL".equals(module)) return "沙沱出库流量";
        if ("TANK_PENGSHUI_INFLOW".equals(stationCode)) return "彭水入库流量";
        if ("TANK_MODEL".equals(module)) return "分区预报流量";
        return "预报流量";
    }

    public void delete(String taskId) {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE YDT_FORECAST_TASK SET DELETED=1,UPDATED_TIME=? WHERE ID=? AND DELETED=0")) {
            statement.setTimestamp(1, new Timestamp(System.currentTimeMillis())); statement.setString(2, taskId);
            if (statement.executeUpdate() != 1) throw error("保存结果不存在或已删除: " + taskId);
        } catch (StationModelConfigException ex) { throw ex; }
        catch (Exception ex) { throw error("结果删除失败: " + ex.getMessage(), ex); }
    }

    private void insertTask(Connection c, String id, ForecastContracts.ResultSaveRequest r,
                            StationModelRoute route, Date now) throws Exception {
        String sql = "INSERT INTO YDT_FORECAST_TASK(ID,CALCULATION_ID,STATION_CODE,STATION_NAME,MODULE,MODEL_CODE,"
                + "MODEL_VERSION,CONFIG_VERSION,FORECAST_TIME,SAVED_TIME,CREATED_BY,TENANT_ID,REMARK,DELETED,CREATED_TIME,UPDATED_TIME)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            int i = 1;
            s.setString(i++, id); s.setString(i++, r.getCalculationId()); s.setString(i++, route.getStation().getCode());
            s.setString(i++, route.getStation().getName()); s.setString(i++, route.getStation().getModule());
            s.setString(i++, route.getModel().getCode()); s.setString(i++, text(r.getModelVersion(), "unknown"));
            s.setString(i++, registry.getConfiguration().getConfigVersion());
            s.setTimestamp(i++, new Timestamp(parse(r.getForecastTime()).getTime()));
            Timestamp timestamp = new Timestamp(now.getTime());
            s.setTimestamp(i++, timestamp); s.setString(i++, trim(r.getForecaster(), 100)); s.setString(i++, null);
            s.setString(i++, trim(text(r.getModelDescription(), r.getRemark()), 500)); s.setTimestamp(i++, timestamp); s.setTimestamp(i, timestamp);
            s.executeUpdate();
        }
    }

    private void insertInputs(Connection c, String taskId, ForecastContracts.ResultSaveRequest request) throws Exception {
        String sql = "INSERT INTO YDT_FORECAST_INPUT_DATA(ID,TASK_ID,STATION_CODE,SERIES_CODE,SERIES_NAME,SENID,DATA_TIME,"
                + "ORIGINAL_VALUE,ACTUAL_VALUE,DATA_SOURCE,EDITABLE,MANUALLY_MODIFIED,UNIT,WEIGHT,SORT_NO,CREATED_TIME)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSDATE)";
        int count = 0;
        try (PreparedStatement s = c.prepareStatement(sql)) {
            if (request.getInput() != null) count += addInput(s, taskId, request.getInput());
            if (request.getInputs() != null) for (ForecastContracts.InputResponse input : request.getInputs()) count += addInput(s, taskId, input);
            count += addPoints(s, taskId, request.getStationCode(), "shatuoOutflow", "沙沱出库", null,
                    "m3/s", null, request.getShatuoOutflowSeries());
        }
        if (count == 0) throw error("没有可保存的模型输入时序数据");
    }

    private int addInput(PreparedStatement s, String taskId, ForecastContracts.InputResponse input) throws Exception {
        if (input == null) return 0;
        required(input.getStationCode(), "input.stationCode");
        int count = 0;
        if (input.getSeries() != null) for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            count += addPoints(s, taskId, input.getStationCode(), series.getKey(), series.getName(), series.getSenid(),
                    series.getUnit(), series.getWeight(), series.getValues());
        }
        if (input.getForecastRainfall() != null) {
            count += addPoints(s, taskId, input.getStationCode(), "forecastRainfall", "预报面雨量", null,
                    "mm", null, input.getForecastRainfall().getValues());
        }
        return count;
    }

    private int addPoints(PreparedStatement s, String taskId, String stationCode, String code, String name,
                          String senid, String unit, Double weight, List<ForecastContracts.ValuePoint> points) throws Exception {
        if (points == null) return 0;
        int sortNo = 0, count = 0;
        for (ForecastContracts.ValuePoint point : points) {
            if (point == null || point.getValue() == null) continue;
            Double original = point.getOriginalValue() == null ? point.getValue() : point.getOriginalValue();
            int i = 1;
            s.setString(i++, UUID.randomUUID().toString()); s.setString(i++, taskId); s.setString(i++, stationCode);
            s.setString(i++, code); s.setString(i++, name); s.setString(i++, senid);
            s.setTimestamp(i++, new Timestamp(parse(point.getTime()).getTime())); setDouble(s, i++, original);
            setDouble(s, i++, point.getValue()); s.setString(i++, point.getSource()); s.setInt(i++, point.isEditable() ? 1 : 0);
            s.setInt(i++, point.isManuallyModified() || Double.compare(original, point.getValue()) != 0 ? 1 : 0);
            s.setString(i++, unit); setDouble(s, i++, weight); s.setInt(i, sortNo++); s.executeUpdate(); count++;
        }
        return count;
    }

    private void insertParameters(Connection c, String taskId, ForecastContracts.ResultSaveRequest request) throws Exception {
        String sql = "INSERT INTO YDT_FORECAST_PARAMETER(ID,TASK_ID,STATION_CODE,PARAMETER_CODE,PARAMETER_TYPE,"
                + "TEXT_VALUE,NUMERIC_VALUE,BOOLEAN_VALUE,CREATED_TIME) VALUES(?,?,?,?,?,?,?,?,SYSDATE)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            Map<String, Map<String, Object>> effective = new LinkedHashMap<String, Map<String, Object>>();
            mergeInputParameters(effective, request.getInput());
            if (request.getInputs() != null) for (ForecastContracts.InputResponse input : request.getInputs()) {
                mergeInputParameters(effective, input);
            }
            mergeParameters(effective, request.getStationCode(), request.getParameters());
            if (request.getStationParameters() != null) {
                for (Map.Entry<String, Map<String, Object>> entry : request.getStationParameters().entrySet()) {
                    mergeParameters(effective, entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<String, Map<String, Object>> entry : effective.entrySet()) {
                flattenParameters(s, taskId, entry.getKey(), "", entry.getValue());
            }
        }
    }

    private void mergeInputParameters(Map<String, Map<String, Object>> effective,
                                      ForecastContracts.InputResponse input) {
        if (input != null) mergeParameters(effective, input.getStationCode(), input.getModelParameters());
    }

    private void mergeParameters(Map<String, Map<String, Object>> effective, String stationCode,
                                 Map<String, Object> values) {
        if (values == null || values.isEmpty() || trim(stationCode).isEmpty()) return;
        Map<String, Object> target = effective.get(stationCode);
        if (target == null) { target = new LinkedHashMap<String, Object>(); effective.put(stationCode, target); }
        target.putAll(values);
    }

    @SuppressWarnings("unchecked")
    private void flattenParameters(PreparedStatement s, String taskId, String stationCode, String path,
                                   Object value) throws Exception {
        if (value == null) return;
        if (value instanceof Map) {
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                String child = path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
                flattenParameters(s, taskId, stationCode, child, entry.getValue());
            }
            return;
        }
        if (value instanceof Iterable) {
            int index = 0;
            for (Object item : (Iterable<Object>) value) flattenParameters(s, taskId, stationCode, path + "[" + index++ + "]", item);
            return;
        }
        int i = 1;
        s.setString(i++, UUID.randomUUID().toString()); s.setString(i++, taskId); s.setString(i++, stationCode); s.setString(i++, path);
        if (value instanceof Number) {
            s.setString(i++, "NUMBER"); s.setString(i++, null); s.setDouble(i++, ((Number) value).doubleValue()); s.setObject(i, null);
        } else if (value instanceof Boolean) {
            s.setString(i++, "BOOLEAN"); s.setString(i++, null); s.setObject(i++, null); s.setInt(i, (Boolean) value ? 1 : 0);
        } else {
            s.setString(i++, "TEXT"); s.setString(i++, trim(String.valueOf(value), 1000)); s.setObject(i++, null); s.setObject(i, null);
        }
        s.executeUpdate();
    }

    private void insertResults(Connection c, String taskId, String module, String stationCode,
                               Map<String, Object> result) throws Exception {
        if ("API_MODEL".equals(module)) insertApiResults(c, taskId, result);
        else if ("REGRESSION_MODEL".equals(module)) insertRegressionResults(c, taskId, result);
        else if ("TANK_MODEL".equals(module)) insertTankResults(c, taskId, stationCode, result);
        else throw error("不支持保存的模型模块: " + module);
    }

    private void insertApiResults(Connection c, String taskId, Map<String, Object> raw) throws Exception {
        Object source = raw.get("chartData");
        if (!(source instanceof List) || ((List<?>) source).isEmpty()) throw error("API模型结果没有chartData");
        String sql = "INSERT INTO YDT_FORECAST_API_RESULT(ID,TASK_ID,SEQUENCE_NO,RESULT_TIME,RAINFALL,NET_RAINFALL,CUMULATIVE_NET_RAINFALL,"
                + "CUMULATIVE_RAINFALL,PA,DISCHARGE,CREATED_TIME) VALUES(?,?,?,?,?,?,?,?,?,?,SYSDATE)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            double cumulativeNet = 0D, cumulativeRain = 0D;
            int sequence = 1;
            for (Object value : (List<?>) source) {
                Map<String, Object> item = map(value);
                Double netRainfall = number(item.get("netRainfall")), rainfall = number(item.get("rainfall"));
                if (netRainfall != null) cumulativeNet += netRainfall;
                if (rainfall != null) cumulativeRain += rainfall;
                int i = 1; s.setString(i++, uuid()); s.setString(i++, taskId); s.setInt(i++, sequence++);
                s.setTimestamp(i++, timestamp(item.get("time"))); setDouble(s, i++, rainfall);
                setDouble(s, i++, netRainfall); setDouble(s, i++, cumulativeNet);
                setDouble(s, i++, cumulativeRain); setDouble(s, i++, null);
                setDouble(s, i, number(item.get("inflow"))); s.executeUpdate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void insertRegressionResults(Connection c, String taskId, Map<String, Object> raw) throws Exception {
        Object series = raw.get("chartData");
        if (!(series instanceof List) || ((List<?>) series).isEmpty()) throw error("沙沱模型结果没有chartData");
        String sql = "INSERT INTO YDT_FORECAST_REGRESSION_RESULT(ID,TASK_ID,RESULT_TIME,HORIZON,FORECAST_FLAG,RAINFALL,"
                + "SHATUO_OUTFLOW,SHATUO_INFLOW,SHATUO_WATER_LEVEL,GOUPITAN_INFLOW,GOUPITAN_OUTFLOW,"
                + "SHATUO_RAINFALL_AVERAGE,SHATUO_OUTFLOW_AVERAGE,SHATUO_INFLOW_AVERAGE,ACTUAL_MODEL_NAME,"
                + "RAW_FLOW,MOS_CORRECTION,FORECAST_FLOW,CREATED_TIME) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSDATE)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            int horizon = 1;
            for (Object value : (List<Object>) series) {
                Map<String, Object> item = map(value);
                boolean forecast = Boolean.TRUE.equals(item.get("forecast"));
                int i = 1;
                s.setString(i++, uuid()); s.setString(i++, taskId); s.setTimestamp(i++, timestamp(item.get("time")));
                setInteger(s, i++, horizon++); s.setInt(i++, forecast ? 1 : 0);
                setDouble(s, i++, number(item.get("rainfall"))); setDouble(s, i++, number(item.get("shatuoOutflow")));
                setDouble(s, i++, number(item.get("shatuoInflow"))); setDouble(s, i++, number(item.get("shatuoWaterLevel")));
                setDouble(s, i++, number(item.get("goupitanInflow"))); setDouble(s, i++, number(item.get("goupitanOutflow")));
                setDouble(s, i++, number(item.get("shatuoRainfallAverage")));
                setDouble(s, i++, number(item.get("shatuoOutflowAverage")));
                setDouble(s, i++, number(item.get("shatuoInflowAverage"))); s.setString(i++, null);
                setDouble(s, i++, null); setDouble(s, i++, null);
                setDouble(s, i, forecast ? number(item.get("shatuoOutflow")) : null); s.executeUpdate();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void insertTankResults(Connection c, String taskId, String defaultStation, Map<String, Object> raw) throws Exception {
        Map<String, Map<String, Double>> basinFlows = new LinkedHashMap<String, Map<String, Double>>();
        int count = 0;
        String sql = "INSERT INTO YDT_FORECAST_TANK_RESULT(ID,TASK_ID,STATION_CODE,RESULT_TIME,SEQUENCE_NO,RAINFALL,EVAPORATION,"
                + "TANK1_STORAGE_DEPTH,TANK1_UPPER_HOLE,TANK1_MIDDLE_HOLE,TANK1_LOWER_HOLE,TANK1_INFILTRATION,"
                + "TANK2_STORAGE_DEPTH,TANK2_OUTFLOW,TANK2_PERCOLATION,TANK3_STORAGE_DEPTH,TANK3_MIDDLE_HOLE,TANK3_BOTTOM_HOLE,"
                + "OUTFLOW_DEPTH,FLOW,CREATED_TIME) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSDATE)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            Object basins = raw.get("basinDetails");
            if (basins instanceof List) {
                for (Object value : (List<Object>) basins) {
                    Map<String, Object> basin = map(value);
                    Map<String, Object> data = new LinkedHashMap<String, Object>();
                    data.put("forecastSeries", basin.get("tableData"));
                    count += addTankSeries(s, taskId, String.valueOf(basin.get("stationCode")), data, basinFlows);
                }
            } else count += addTankSeries(s, taskId, defaultStation, raw, basinFlows);
        }
        if (count == 0) throw error("水箱模型结果没有forecastSeries");
        insertPengshuiResults(c, taskId, raw);
    }

    @SuppressWarnings("unchecked")
    private int addTankSeries(PreparedStatement s, String taskId, String stationCode, Map<String, Object> raw,
                              Map<String, Map<String, Double>> basinFlows) throws Exception {
        Object series = modelData(raw).get("forecastSeries");
        if (!(series instanceof List)) return 0;
        int sequence = 1, count = 0;
        Map<String, Double> stationFlows = new LinkedHashMap<String, Double>(); basinFlows.put(stationCode, stationFlows);
        for (Object value : (List<Object>) series) {
            Map<String, Object> item = map(value), tank1 = map(item.get("tank1")), tank2 = map(item.get("tank2")), tank3 = map(item.get("tank3"));
            String time = string(item.get("time")); Double flow = number(item.get("flow")); int i = 1;
            s.setString(i++, uuid()); s.setString(i++, taskId); s.setString(i++, stationCode); s.setTimestamp(i++, timestamp(time));
            s.setInt(i++, sequence++); setDouble(s, i++, number(item.get("rainfall"))); setDouble(s, i++, number(item.get("evaporation")));
            setDouble(s, i++, number(tank1.get("storageDepth"))); setDouble(s, i++, number(tank1.get("upperHole")));
            setDouble(s, i++, number(tank1.get("middleHole"))); setDouble(s, i++, number(tank1.get("lowerHole")));
            setDouble(s, i++, number(tank1.get("infiltration"))); setDouble(s, i++, number(tank2.get("storageDepth")));
            setDouble(s, i++, number(tank2.get("outflow"))); setDouble(s, i++, number(tank2.get("percolation")));
            setDouble(s, i++, number(tank3.get("storageDepth"))); setDouble(s, i++, number(tank3.get("middleHole")));
            setDouble(s, i++, number(tank3.get("bottomHole"))); setDouble(s, i++, number(item.get("outflowDepth")));
            setDouble(s, i, flow); s.executeUpdate(); stationFlows.put(time, flow); count++;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private void insertPengshuiResults(Connection c, String taskId, Map<String, Object> raw) throws Exception {
        Object source = raw.get("chartData");
        if (!(source instanceof List)) return;
        String sql = "INSERT INTO YDT_FORECAST_PENGSHUI_RESULT(ID,TASK_ID,RESULT_TIME,SEQUENCE_NO,JIANGBIN_FLOW,DAHEKOU_FLOW,"
                + "YANGONG_FLOW,GONGPENG_FLOW,REGIONAL_FLOW_TOTAL,SHATUO_OUTFLOW,PENGSHUI_INFLOW,CREATED_TIME)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,SYSDATE)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            int sequence = 1;
            for (Object value : (List<Object>) source) {
                Map<String, Object> item = map(value); String time = string(item.get("time")); int i = 1;
                s.setString(i++, uuid()); s.setString(i++, taskId); s.setTimestamp(i++, timestamp(time)); s.setInt(i++, sequence++);
                setDouble(s, i++, number(item.get("jiangbinFlow")));
                setDouble(s, i++, number(item.get("dahekouFlow")));
                setDouble(s, i++, number(item.get("yangongFlow")));
                setDouble(s, i++, number(item.get("gongpengFlow")));
                setDouble(s, i++, number(item.get("regionalFlowTotal"))); setDouble(s, i++, number(item.get("shatuoOutflow")));
                setDouble(s, i, number(item.get("pengshuiInflow"))); s.executeUpdate();
            }
        }
    }

    private int countCalculation(Connection c, String id) throws Exception {
        try (PreparedStatement s = c.prepareStatement("SELECT COUNT(1) FROM YDT_FORECAST_TASK WHERE CALCULATION_ID=? AND DELETED=0")) {
            s.setString(1, id); try (ResultSet rs = s.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    /** 批量补齐结果管理列表字段，前端可以直接绑定截图中的表格。 */
    @SuppressWarnings("unchecked")
    private void enrichSummaries(Connection c, List<Map<String, Object>> records) throws Exception {
        if (records.isEmpty()) return;
        List<String> ids = new ArrayList<String>(); Map<String, Map<String, Object>> byId = new LinkedHashMap<String, Map<String, Object>>();
        for (Map<String, Object> record : records) { String id = string(record.get("taskId")); ids.add(id); byId.put(id, record); }
        String in = placeholders(ids.size());
        List<Object> objectParams = repeat(ids, 3);
        String objectSql = "SELECT DISTINCT X.TASK_ID AS \"taskId\",X.STATION_CODE AS \"stationCode\" FROM ("
                + "SELECT ID TASK_ID,STATION_CODE FROM YDT_FORECAST_TASK WHERE ID IN " + in + " AND MODULE<>'TANK_MODEL' UNION ALL "
                + "SELECT TASK_ID,STATION_CODE FROM YDT_FORECAST_TANK_RESULT WHERE TASK_ID IN " + in + " UNION ALL "
                + "SELECT T.ID,T.STATION_CODE FROM YDT_FORECAST_TASK T WHERE T.ID IN " + in
                + " AND EXISTS(SELECT 1 FROM YDT_FORECAST_PENGSHUI_RESULT P WHERE P.TASK_ID=T.ID)) X ORDER BY X.TASK_ID,X.STATION_CODE";
        for (Map<String, Object> row : queryRows(c, objectSql, objectParams)) {
            Map<String, Object> summary = byId.get(string(row.get("taskId"))); if (summary == null) continue;
            List<String> names = (List<String>) summary.get("forecastObjects"); String name = stationName(string(row.get("stationCode")));
            if (!names.contains(name)) names.add(name);
        }
        List<Object> statisticParams = repeat(ids, 4);
        String statsSql = "SELECT TASK_ID AS \"taskId\",MAX(PERIOD_COUNT) AS \"periodCount\",MIN(MIN_TIME) AS \"minTime\",MAX(MAX_TIME) AS \"maxTime\" FROM ("
                + "SELECT TASK_ID,COUNT(1) PERIOD_COUNT,MIN(RESULT_TIME) MIN_TIME,MAX(RESULT_TIME) MAX_TIME FROM YDT_FORECAST_API_RESULT WHERE TASK_ID IN " + in + " GROUP BY TASK_ID UNION ALL "
                + "SELECT TASK_ID,COUNT(1),MIN(RESULT_TIME),MAX(RESULT_TIME) FROM YDT_FORECAST_REGRESSION_RESULT WHERE TASK_ID IN " + in + " GROUP BY TASK_ID UNION ALL "
                + "SELECT TASK_ID,COUNT(1),MIN(RESULT_TIME),MAX(RESULT_TIME) FROM YDT_FORECAST_PENGSHUI_RESULT WHERE TASK_ID IN " + in + " GROUP BY TASK_ID UNION ALL "
                + "SELECT TASK_ID,COUNT(1),MIN(RESULT_TIME),MAX(RESULT_TIME) FROM YDT_FORECAST_TANK_RESULT WHERE TASK_ID IN " + in + " GROUP BY TASK_ID,STATION_CODE) S GROUP BY TASK_ID";
        for (Map<String, Object> row : queryRows(c, statsSql, statisticParams)) {
            Map<String, Object> summary = byId.get(string(row.get("taskId"))); if (summary == null) continue;
            Integer count = integer(row.get("periodCount")); summary.put("forecastPeriodCount", count);
            TimeScale scale = timeScale(date(row.get("minTime")), date(row.get("maxTime")), count);
            summary.put("timeType", scale.type); summary.put("timeInterval", scale.interval);
        }
        for (Map<String, Object> summary : records) {
            List<String> names = (List<String>) summary.get("forecastObjects");
            if (names.isEmpty()) names.add(string(summary.get("stationName")));
            if (!summary.containsKey("timeType")) { summary.put("timeType", "日"); summary.put("timeInterval", 1); }
        }
    }

    private List<Map<String, Object>> buildObjects(Map<String, Object> task, List<Map<String, Object>> inputs,
            List<Map<String, Object>> apiRows, List<Map<String, Object>> tankRows,
            List<Map<String, Object>> regressionRows, List<Map<String, Object>> pengshuiRows) {
        List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>(); Map<String, Double> rainfall = rainfallByTime(inputs);
        if (!apiRows.isEmpty()) objects.add(apiObject(task, apiRows, rainfall));
        if (!tankRows.isEmpty()) objects.addAll(tankObjects(task, tankRows));
        if (!regressionRows.isEmpty()) objects.add(regressionObject(task, regressionRows, rainfall));
        if (!pengshuiRows.isEmpty()) objects.add(pengshuiObject(task, pengshuiRows));
        return objects;
    }

    private Map<String, Object> apiObject(Map<String, Object> task, List<Map<String, Object>> source, Map<String, Double> rain) {
        Map<String, Object> object = object(task, string(task.get("stationCode")));
        add((List<Map<String,Object>>)object.get("chartSeries"), "rainfall","降雨","mm","bar","right");
        add((List<Map<String,Object>>)object.get("chartSeries"), "netRainfall","净雨","mm","line","right");
        add((List<Map<String,Object>>)object.get("chartSeries"), "inflow","入库流量","m³/s","line","left");
        addBasicColumns(object, new String[][]{{"time","时间",null},{"rainfall","降雨","mm"},{"netRainfall","净雨","mm"},{"inflow","入库流量","m³/s"}});
        List<String> fallback = new ArrayList<String>(rain.keySet()); int index = 0;
        for (Map<String,Object> row : source) { String time=string(row.get("resultTime")); if(trim(time).isEmpty()&&index<fallback.size())time=fallback.get(index);
            Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("time",time);item.put("rainfall",row.get("rainfall")==null?rain.get(time):row.get("rainfall"));
            item.put("netRainfall",row.get("netRainfall"));item.put("inflow",row.get("discharge"));addData(object,item,true);index++; }
        finish(object,"inflow"); return object;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tankObjects(Map<String,Object> task,List<Map<String,Object>> source){
        Map<String,List<Map<String,Object>>> groups=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(Map<String,Object> row:source){String code=string(row.get("stationCode"));if(!groups.containsKey(code))groups.put(code,new ArrayList<Map<String,Object>>());groups.get(code).add(row);}
        List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();
        for(Map.Entry<String,List<Map<String,Object>>> entry:groups.entrySet()){Map<String,Object> object=object(task,entry.getKey());
            add((List<Map<String,Object>>)object.get("chartSeries"),"rainfall","降雨","mm","bar","right");add((List<Map<String,Object>>)object.get("chartSeries"),"flow","流量","m³/s","line","left");
            addBasicColumns(object,new String[][]{{"time","时间",null},{"rainfall","降雨","mm"},{"evaporation","蒸发","mm"},{"tank1StorageDepth","第1层蓄水深","mm"},{"tank1UpperHole","第1层上孔","mm"},{"tank1MiddleHole","第1层中孔","mm"},{"tank1LowerHole","第1层下孔","mm"},{"tank1Infiltration","第1层下渗","mm"},{"tank2StorageDepth","第2层蓄水深","mm"},{"tank2Outflow","第2层出流","mm"},{"tank2Percolation","第2层下渗","mm"},{"tank3StorageDepth","第3层蓄水深","mm"},{"tank3MiddleHole","第3层中孔","mm"},{"tank3BottomHole","第3层下孔","mm"},{"outflowDepth","合计出流深","mm"},{"flow","流量","m³/s"}});
            for(Map<String,Object> row:entry.getValue()){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("time",row.get("resultTime"));for(String key:new String[]{"rainfall","evaporation","tank1StorageDepth","tank1UpperHole","tank1MiddleHole","tank1LowerHole","tank1Infiltration","tank2StorageDepth","tank2Outflow","tank2Percolation","tank3StorageDepth","tank3MiddleHole","tank3BottomHole","outflowDepth","flow"})item.put(key,row.get(key));addData(object,item,false);Map<String,Object> chart=new LinkedHashMap<String,Object>();chart.put("time",item.get("time"));chart.put("rainfall",item.get("rainfall"));chart.put("flow",item.get("flow"));((List<Map<String,Object>>)object.get("chartData")).add(chart);}finish(object,"flow");result.add(object);}return result;}

    @SuppressWarnings("unchecked")
    private Map<String,Object> regressionObject(Map<String,Object> task,List<Map<String,Object>> source,Map<String,Double> rain){Map<String,Object> object=object(task,string(task.get("stationCode")));
        add((List<Map<String,Object>>)object.get("chartSeries"),"rainfall","沙沱降雨","mm","bar","right");add((List<Map<String,Object>>)object.get("chartSeries"),"shatuoOutflow","沙沱出库流量","m³/s","line","left");add((List<Map<String,Object>>)object.get("chartSeries"),"shatuoInflow","沙沱入库流量","m³/s","line","left");
        String[][] columns={{"time","时间",null},{"rainfall","沙沱降雨","mm"},{"shatuoOutflow","沙沱出库流量","m³/s"},{"shatuoInflow","沙沱入库流量","m³/s"},{"shatuoWaterLevel","沙沱水位","m"},{"goupitanInflow","构皮滩入库流量","m³/s"},{"goupitanOutflow","构皮滩出库流量","m³/s"},{"shatuoRainfallAverage","沙沱降雨多年均值","mm"},{"shatuoOutflowAverage","沙沱出库多年均值","m³/s"},{"shatuoInflowAverage","沙沱入库多年均值","m³/s"}};addBasicColumns(object,columns);
        for(Map<String,Object> row:source){String time=string(row.get("resultTime"));Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("time",time);item.put("rainfall",row.get("rainfall")==null?rain.get(time):row.get("rainfall"));for(int i=2;i<columns.length;i++)item.put(columns[i][0],row.get(columns[i][0]));if(item.get("shatuoOutflow")==null)item.put("shatuoOutflow",row.get("forecastFlow"));addData(object,item,true);}finish(object,"shatuoOutflow");return object;}

    @SuppressWarnings("unchecked")
    private Map<String,Object> pengshuiObject(Map<String,Object> task,List<Map<String,Object>> source){Map<String,Object> object=object(task,string(task.get("stationCode")));
        add((List<Map<String,Object>>)object.get("chartSeries"),"regionalFlowTotal","四分区流量合计","m³/s","line","left");add((List<Map<String,Object>>)object.get("chartSeries"),"shatuoOutflow","沙沱出库流量","m³/s","line","left");add((List<Map<String,Object>>)object.get("chartSeries"),"pengshuiInflow","彭水入库流量","m³/s","line","left");
        String[][] columns={{"time","时间",null},{"jiangbinFlow","江滨以上","m³/s"},{"dahekouFlow","大河口以上","m³/s"},{"yangongFlow","沿篓区间","m³/s"},{"gongpengFlow","簸箕区间","m³/s"},{"regionalFlowTotal","四分区流量合计","m³/s"},{"shatuoOutflow","沙沱出库流量","m³/s"},{"pengshuiInflow","彭水入库流量","m³/s"}};addBasicColumns(object,columns);
        for(Map<String,Object> row:source){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("time",row.get("resultTime"));for(int i=1;i<columns.length;i++)item.put(columns[i][0],row.get(columns[i][0]));addData(object,item,true);}finish(object,"pengshuiInflow");return object;}

    private Map<String,Object> object(Map<String,Object> task,String stationCode){Map<String,Object> object=new LinkedHashMap<String,Object>();object.put("stationCode",stationCode);object.put("stationName",stationName(stationCode));object.put("modelCode",task.get("modelCode"));object.put("modelName",modelName(task));object.put("forecastBasisTime",task.get("forecastBasisTime"));object.put("forecastProductionTime",task.get("forecastProductionTime"));object.put("chartSeries",new ArrayList<Map<String,Object>>());object.put("tableColumns",new ArrayList<Map<String,Object>>());object.put("chartData",new ArrayList<Map<String,Object>>());object.put("tableData",new ArrayList<Map<String,Object>>());return object;}
    @SuppressWarnings("unchecked") private void addBasicColumns(Map<String,Object> object,String[][] columns){for(String[] column:columns)add((List<Map<String,Object>>)object.get("tableColumns"),column[0],column[1],column[2],null,null);}
    private void add(List<Map<String,Object>> fields,String key,String name,String unit,String type,String axis){Map<String,Object> field=new LinkedHashMap<String,Object>();field.put("key",key);field.put("name",name);field.put("unit",unit);field.put("type",type);field.put("axis",axis);fields.add(field);}
    @SuppressWarnings("unchecked") private void addData(Map<String,Object> object,Map<String,Object> item,boolean chart){((List<Map<String,Object>>)object.get("tableData")).add(item);if(chart)((List<Map<String,Object>>)object.get("chartData")).add(new LinkedHashMap<String,Object>(item));}
    @SuppressWarnings("unchecked") private void finish(Map<String,Object> object,String flowKey){List<Map<String,Object>> rows=(List<Map<String,Object>>)object.get("tableData");object.put("forecastPeriodCount",rows.size());Date first=null,last=null;Double maximum=null;String maximumTime=null;for(Map<String,Object> row:rows){Date current=date(row.get("time"));if(current!=null){if(first==null||current.before(first))first=current;if(last==null||current.after(last))last=current;}Double value=number(row.get(flowKey));if(value!=null&&(maximum==null||value>maximum)){maximum=value;maximumTime=string(row.get("time"));}}TimeScale scale=timeScale(first,last,rows.size());object.put("maximumFlow",maximum);object.put("maximumFlowTime",maximumTime);object.put("timeType",scale.type);object.put("timeInterval",scale.interval);}
    private Map<String,Double> rainfallByTime(List<Map<String,Object>> inputs){Map<String,Double> result=new LinkedHashMap<String,Double>();for(Map<String,Object> row:inputs){String code=trim(string(row.get("seriesCode"))).toLowerCase();if(!code.contains("rain")&&!code.contains("pre"))continue;String time=string(row.get("dataTime"));Double value=number(row.get("actualValue"));if(value==null||trim(time).isEmpty())continue;Double weight=number(row.get("weight"));double actual=weight==null?value:value*weight;result.put(time,result.containsKey(time)?result.get(time)+actual:actual);}return result;}
    private TimeScale timeScale(Date first,Date last,Integer count){if(first==null||last==null||count==null||count<=1)return new TimeScale("日",1);Calendar begin=Calendar.getInstance();begin.setTime(first);Calendar end=Calendar.getInstance();end.setTime(last);int months=(end.get(Calendar.YEAR)-begin.get(Calendar.YEAR))*12+end.get(Calendar.MONTH)-begin.get(Calendar.MONTH);if(months>0&&months%(count-1)==0&&begin.get(Calendar.DAY_OF_MONTH)==end.get(Calendar.DAY_OF_MONTH))return new TimeScale("月",months/(count-1));long hours=Math.max(1L,(last.getTime()-first.getTime())/3600000L/(count-1));return hours%24L==0L?new TimeScale("日",(int)(hours/24L)):new TimeScale("时",(int)hours);}
    private Date date(Object value){if(value instanceof Date)return(Date)value;if(value==null||trim(String.valueOf(value)).isEmpty())return null;try{return new SimpleDateFormat(DATE_PATTERN).parse(String.valueOf(value));}catch(ParseException ex){return null;}}
    private String stationName(String code){try{return registry.resolveRoute(code).getStation().getName();}catch(RuntimeException ex){return code;}}
    private String modelName(Map<String,Object> task){try{return registry.resolveRoute(string(task.get("stationCode"))).getModel().getName();}catch(RuntimeException ex){return string(task.get("modelCode"));}}
    private String placeholders(int size){StringBuilder value=new StringBuilder("(");for(int i=0;i<size;i++){if(i>0)value.append(',');value.append('?');}return value.append(')').toString();}
    private List<Object> repeat(List<String> values,int times){List<Object> result=new ArrayList<Object>();for(int i=0;i<times;i++)result.addAll(values);return result;}
    private static class TimeScale{private final String type;private final int interval;private TimeScale(String type,int interval){this.type=type;this.interval=interval;}}

    private Map<String, Object> taskSummary(Connection c, String id) throws Exception {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM YDT_FORECAST_TASK WHERE ID=? AND DELETED=0")) {
            s.setString(1, id); try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) throw error("保存结果不存在: " + id); return readSummary(rs);
            }
        }
    }

    private Map<String, Object> readSummary(ResultSet rs) throws Exception {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("taskId", rs.getString("ID")); m.put("calculationId", rs.getString("CALCULATION_ID"));
        m.put("stationCode", rs.getString("STATION_CODE")); m.put("stationName", rs.getString("STATION_NAME"));
        m.put("module", rs.getString("MODULE")); m.put("modelCode", rs.getString("MODEL_CODE"));
        m.put("modelVersion", rs.getString("MODEL_VERSION")); m.put("configVersion", rs.getString("CONFIG_VERSION"));
        m.put("forecastTime", format(rs.getTimestamp("FORECAST_TIME"))); m.put("savedTime", format(rs.getTimestamp("SAVED_TIME")));
        m.put("createdBy", rs.getString("CREATED_BY")); m.put("tenantId", rs.getString("TENANT_ID"));
        m.put("remark", rs.getString("REMARK"));
        m.put("forecastBasisTime", m.get("forecastTime")); m.put("forecastProductionTime", m.get("savedTime"));
        m.put("modelDescription", m.get("remark")); m.put("forecaster", m.get("createdBy"));
        m.put("forecastObjects", new ArrayList<String>()); return m;
    }

    private List<Map<String, Object>> queryRows(Connection c, String sql, String taskId) throws Exception {
        List<Object> params = new ArrayList<Object>(); params.add(taskId); return queryRows(c, sql, params);
    }

    private List<Map<String, Object>> queryRows(Connection c, String sql, List<Object> params) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, params); try (ResultSet rs = s.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object value = rs.getObject(i); if (value instanceof Date) value = format((Date) value);
                        row.put(meta.getColumnLabel(i), value);
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private String conditions(String station, String module, String begin, String end,
                              String timeFilterType, String forecaster, List<Object> params) {
        StringBuilder where = new StringBuilder("DELETED=0");
        if (!trim(station).isEmpty()) { where.append(" AND STATION_CODE=?"); params.add(trim(station)); }
        if (!trim(module).isEmpty()) { where.append(" AND MODULE=?"); params.add(trim(module)); }
        String timeColumn = "forecastBasisTime".equals(timeFilterType) ? "FORECAST_TIME" : "SAVED_TIME";
        if (!trim(begin).isEmpty()) { where.append(" AND ").append(timeColumn).append(">=?"); params.add(new Timestamp(parseQueryTime(begin, false).getTime())); }
        if (!trim(end).isEmpty()) { where.append(" AND ").append(timeColumn).append("<=?"); params.add(new Timestamp(parseQueryTime(end, true).getTime())); }
        List<String> names = split(forecaster);
        if (!names.isEmpty()) {
            where.append(" AND CREATED_BY IN ").append(placeholders(names.size())); params.addAll(names);
        }
        return where.toString();
    }

    private void bind(PreparedStatement statement, List<Object> params) throws Exception {
        for (int i = 0; i < params.size(); i++) statement.setObject(i + 1, params.get(i));
    }
    private Connection connection() throws Exception {
        return ConnectionJDBC.getInstance(environment.getProperty("service.yunnan-datang.result-db-service", "wds"));
    }
    private void validate(ForecastContracts.ResultSaveRequest request) {
        if (request == null) throw error("保存请求不能为空");
        required(request.getCalculationId(), "calculationId"); required(request.getStationCode(), "stationCode");
        required(request.getModelCode(), "modelCode"); required(request.getForecastTime(), "forecastTime");
        if (request.getInput() == null && (request.getInputs() == null || request.getInputs().isEmpty())) throw error("input和inputs不能同时为空");
        if (request.getResult() == null || request.getResult().isEmpty()) throw error("result不能为空");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> modelData(Map<String, Object> raw) {
        if (raw != null && raw.get("data") instanceof Map && !raw.containsKey("forecastSeries")) return (Map<String, Object>) raw.get("data");
        return raw == null ? new LinkedHashMap<String, Object>() : raw;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> modelResult(Map<String, Object> raw) {
        Map<String, Object> data = modelData(raw);
        return data.get("modelResult") instanceof Map ? (Map<String, Object>) data.get("modelResult") : data;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>(); }
    private List<?> list(Map<String, Object> source, String key) { Object value = source.get(key); return value instanceof List ? (List<?>) value : new ArrayList<Object>(); }
    private int max(int... values) { int max = 0; for (int value : values) max = Math.max(max, value); return max; }
    private Double numberAt(List<?> values, int index) { return index < values.size() ? number(values.get(index)) : null; }
    private Double number(Object value) { return value instanceof Number ? ((Number) value).doubleValue() : null; }
    private Integer integer(Object value) { return value instanceof Number ? ((Number) value).intValue() : null; }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private Double flow(Map<String, Map<String, Double>> values, String stationCode, String time) { Map<String, Double> station = values.get(stationCode); return station == null ? null : station.get(time); }
    private void setDouble(PreparedStatement s, int index, Double value) throws Exception { if (value == null) s.setObject(index, null); else s.setDouble(index, value); }
    private void setInteger(PreparedStatement s, int index, Integer value) throws Exception { if (value == null) s.setObject(index, null); else s.setInt(index, value); }
    private Timestamp timestamp(Object value) { return new Timestamp(parse(string(value)).getTime()); }
    private String uuid() { return UUID.randomUUID().toString(); }
    private Date parse(String value) { try { SimpleDateFormat f = new SimpleDateFormat(DATE_PATTERN); f.setLenient(false); return f.parse(value); } catch (ParseException ex) { throw error("时间格式必须是" + DATE_PATTERN); } }
    /** 查询页面允许只传 yyyy-MM-dd，结束日期自动覆盖当天。 */
    private Date parseQueryTime(String value, boolean endOfDay) { String text=trim(value);if(text.length()==10)text+=endOfDay?" 23:59:59":" 00:00:00";return parse(text); }
    private String format(Date value) { return value == null ? null : new SimpleDateFormat(DATE_PATTERN).format(value); }
    private void required(String value, String name) { if (trim(value).isEmpty()) throw error(name + "不能为空"); }
    private String text(String value, String defaultValue) { return trim(value).isEmpty() ? defaultValue : trim(value); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private String trim(String value, int max) { String text = trim(value); return text.length() > max ? text.substring(0, max) : text; }
    private List<String> split(String value) { List<String> result=new ArrayList<String>();for(String item:trim(value).split(","))if(!trim(item).isEmpty())result.add(trim(item));return result; }
    private StationModelConfigException error(String message) { return new StationModelConfigException(message); }
    private StationModelConfigException error(String message, Throwable cause) { return new StationModelConfigException(message, cause); }
}
