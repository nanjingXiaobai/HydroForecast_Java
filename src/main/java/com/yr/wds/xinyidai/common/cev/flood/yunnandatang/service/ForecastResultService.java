package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.nari.slsd.hps.dbmgr.dao.ConnectionJDBC;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.support.ForecastLogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>YDT_FORECAST_RESULT按对象、指标、日期保存快照，API和水箱流量同事务发布到HFIDX、ADXLD。
 * 输入原值、修改标志和参数也保存在同表；详情及导出继续沿用原接口契约。</p>
 */
@Service
@ConditionalOnYunnanDatangFeature
public class ForecastResultService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForecastResultService.class);
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final StationModelRegistry registry;
    private final Environment environment;

    public ForecastResultService(StationModelRegistry registry, Environment environment) {
        this.registry = registry;
        this.environment = environment;
    }

    public synchronized Map<String, Object> save(ForecastContracts.ResultSaveRequest request) {
        long startedAt = System.currentTimeMillis();
        validate(request);
        StationModelRoute route = registry.resolveRoute(request.getStationCode());
        if (!route.getModel().getCode().equals(request.getModelCode())) throw error("保存的模型与当前电站配置不一致");
        request.setModelDescription(ForecastSaveDescription.value(request, route.getStation().getModule()));
        String taskId = UUID.randomUUID().toString();
        Date now = new Date();
        List<ForecastDailyRows.Row> dailyRows = ForecastDailyRows.pack(request, route.getStation().getModule());
        ForecastDailyRows.applyResultPoints(dailyRows, registry.getConfiguration());
        List<ForecastStandardResultStore.Series> standardSeries = ForecastStandardResultStore.collect(
                request, route.getStation().getModule(), dailyRows, registry.getConfiguration());
        ForecastStandardResultStore.addLinks(dailyRows, standardSeries, request.getForecastTime());
        LOGGER.info("[云南大唐][结果管理] 开始保存 taskId={}, calculationId={}, stationCode={}, modelCode={}, forecaster={}",
                taskId, request.getCalculationId(), request.getStationCode(), request.getModelCode(), request.getForecaster());
        try (Connection connection = connection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (countCalculation(connection, request.getCalculationId()) > 0) throw error("该计算结果已经保存，不能重复保存");
                if (!standardSeries.isEmpty()) now = ForecastStandardResultStore.availableTime(connection, now, standardSeries);
                ForecastDailyStore.save(connection, taskId, request, route,
                        registry.getConfiguration().getConfigVersion(), now, dailyRows);
                int publishedPoints = ForecastStandardResultStore.save(connection, request,
                        route.getStation().getModule(), now, standardSeries);
                Map<String, Object> summary = taskSummary(connection, taskId);
                List<Map<String, Object>> summaries = new ArrayList<Map<String, Object>>(); summaries.add(summary);
                enrichSummaries(connection, summaries);
                summary.put("standardResultSeries", standardSeries.size());
                summary.put("standardResultPoints", publishedPoints);
                connection.commit();
                LOGGER.info("[云南大唐][结果管理] 保存完成 taskId={}, calculationId={}, elapsedMs={}",
                        taskId, request.getCalculationId(), System.currentTimeMillis() - startedAt);
                return summary;
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof StationModelConfigException) throw (StationModelConfigException) ex;
                throw error("结果保存失败: " + ex.getMessage(), ex);
            } finally {
                if (!connection.isClosed()) connection.setAutoCommit(originalAutoCommit);
            }
        } catch (StationModelConfigException ex) {
            LOGGER.error("[云南大唐][结果管理] 保存失败 taskId={}, calculationId={}, elapsedMs={}",
                    taskId, request.getCalculationId(), System.currentTimeMillis() - startedAt, ex);
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("[云南大唐][结果管理] 数据库连接失败 taskId={}, calculationId={}, elapsedMs={}",
                    taskId, request.getCalculationId(), System.currentTimeMillis() - startedAt, ex);
            throw error("结果数据库连接失败: " + ex.getMessage(), ex);
        }
    }

    public Map<String, Object> list(String stationCode, String module, String beginTime, String endTime,
                                    String timeFilterType, String forecaster, int pageNum, int pageSize) {
        long startedAt = System.currentTimeMillis();
        pageNum = Math.max(1, pageNum);
        pageSize = Math.max(1, Math.min(200, pageSize));
        List<Object> params = new ArrayList<Object>();
        String where = conditions(stationCode, module, beginTime, endTime, timeFilterType, forecaster, params);
        try (Connection connection = connection()) {
            long total;
            String countSql = "SELECT COUNT(1) FROM YDT_FORECAST_RESULT WHERE " + where;
            logSql("结果管理列表总数", countSql, params);
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, params);
                try (ResultSet rs = statement.executeQuery()) { rs.next(); total = rs.getLong(1); }
            }
            int offset = (pageNum - 1) * pageSize;
            String sql = "SELECT * FROM (SELECT T.*,ROW_NUMBER() OVER(ORDER BY SAVED_TIME DESC,ID DESC) RN "
                    + "FROM YDT_FORECAST_RESULT T WHERE " + where + ") X WHERE RN>? AND RN<=? ORDER BY RN";
            List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                List<Object> all = new ArrayList<Object>(params);
                all.add(offset); all.add(offset + pageSize);
                logSql("结果管理分页列表", sql, all);
                bind(statement, all);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) records.add(readSummary(rs)); }
            }
            enrichSummaries(connection, records);
            Map<String, Object> page = new LinkedHashMap<String, Object>();
            page.put("total", total); page.put("pageNum", pageNum); page.put("pageSize", pageSize); page.put("records", records);
            LOGGER.info("[云南大唐][结果管理] 列表查询完成 total={}, returned={}, pageNum={}, pageSize={}, elapsedMs={}",
                    total, records.size(), pageNum, pageSize, System.currentTimeMillis() - startedAt);
            return page;
        } catch (Exception ex) {
            LOGGER.error("[云南大唐][结果管理] 列表查询失败 stationCode={}, module={}, elapsedMs={}",
                    stationCode, module, System.currentTimeMillis() - startedAt, ex);
            throw error("结果列表查询失败: " + ex.getMessage(), ex);
        }
    }

    /** 返回可直接绑定结果管理预报人下拉框的 value/label。 */
    public List<Map<String, Object>> forecasters() {
        long startedAt = System.currentTimeMillis();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        String sql = "SELECT DISTINCT CREATED_BY FROM YDT_FORECAST_RESULT "
                + "WHERE SUMMARY_FLAG=1 AND DELETED=0 AND LENGTH(TRIM(CREATED_BY))>0 ORDER BY CREATED_BY";
        logSql("预报人下拉列表", sql, new ArrayList<Object>());
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (trim(name).isEmpty()) continue;
                Map<String, Object> option = new LinkedHashMap<String, Object>();
                option.put("value", name); option.put("label", name); result.add(option);
            }
            LOGGER.info("[云南大唐][结果管理] 预报人查询完成 rows={}, elapsedMs={}",
                    result.size(), System.currentTimeMillis() - startedAt);
            return result;
        } catch (Exception ex) {
            LOGGER.error("[云南大唐][结果管理] 预报人查询失败 elapsedMs={}",
                    System.currentTimeMillis() - startedAt, ex);
            throw error("预报人查询失败: " + ex.getMessage(), ex);
        }
    }

    static double savedApiBaseFlow(List<ForecastDailyRows.Row> stored, String station) {
        for (ForecastDailyRows.Row row : stored) {
            if ("PARAMETER".equals(row.kind) && java.util.Objects.equals(station,row.station)
                    && "base_flow".equals(row.series) && row.values[0]!=null) return row.values[0];
        }
        return 0D;
    }

    public Map<String, Object> detail(String taskId) {
        long startedAt = System.currentTimeMillis();
        LOGGER.info("[云南大唐][结果管理] 开始查询详情 taskId={}", taskId);
        try (Connection connection = connection()) {
            Map<String, Object> detail = taskSummary(connection, taskId);
            List<ForecastDailyRows.Row> stored = ForecastDailyStore.load(connection, taskId, "TANK_MODEL".equals(string(detail.get("module"))));
            List<Map<String, Object>> inputs = ForecastDailyRows.expand(stored, "INPUT");
            List<Map<String, Object>> apiRows = ForecastDailyRows.expand(stored, "API");
            List<Map<String, Object>> tankRows = ForecastDailyRows.expand(stored, "TANK");
            List<Map<String, Object>> regressionRows = ForecastDailyRows.expand(stored, "REGRESSION");
            List<Map<String, Object>> pengshuiRows = ForecastDailyRows.expand(stored, "PENGSHUI");
            List<Map<String, Object>> objects = buildObjects(detail, inputs, apiRows, tankRows, regressionRows, pengshuiRows);
            if ("API_MODEL".equals(string(detail.get("module")))) {
                for (Map<String,Object> object : objects) {
                    object.put("baseFlow", savedApiBaseFlow(stored, string(object.get("stationCode"))));
                }
            }
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
            LOGGER.info("[云南大唐][结果管理] 详情查询完成 taskId={}, objects={}, elapsedMs={}",
                    taskId, objects.size(), System.currentTimeMillis() - startedAt);
            return detail;
        } catch (StationModelConfigException ex) {
            LOGGER.error("[云南大唐][结果管理] 详情查询失败 taskId={}, elapsedMs={}",
                    taskId, System.currentTimeMillis() - startedAt, ex);
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("[云南大唐][结果管理] 详情查询异常 taskId={}, elapsedMs={}",
                    taskId, System.currentTimeMillis() - startedAt, ex);
            throw error("结果详情查询失败: " + ex.getMessage(), ex);
        }
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
        long startedAt = System.currentTimeMillis();
        String sql = "UPDATE YDT_FORECAST_RESULT SET DELETED=1,UPDATED_TIME=? WHERE TASK_ID=? AND DELETED=0";
        List<Object> params = new ArrayList<Object>();
        params.add(new Timestamp(System.currentTimeMillis()));
        params.add(taskId);
        logSql("结果删除", sql, params);
        try (Connection connection = connection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                ForecastStandardResultStore.delete(connection, taskId);
                bind(statement, params);
                if (statement.executeUpdate() < 1) throw error("保存结果不存在或已删除: " + taskId);
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                if (!connection.isClosed()) connection.setAutoCommit(originalAutoCommit);
            }
            LOGGER.info("[云南大唐][结果管理] 删除完成 taskId={}, elapsedMs={}",
                    taskId, System.currentTimeMillis() - startedAt);
        } catch (StationModelConfigException ex) {
            LOGGER.error("[云南大唐][结果管理] 删除失败 taskId={}, elapsedMs={}",
                    taskId, System.currentTimeMillis() - startedAt, ex);
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("[云南大唐][结果管理] 删除异常 taskId={}, elapsedMs={}",
                    taskId, System.currentTimeMillis() - startedAt, ex);
            throw error("结果删除失败: " + ex.getMessage(), ex);
        }
    }

    private int countCalculation(Connection c, String id) throws Exception {
        String sql = "SELECT COUNT(1) FROM YDT_FORECAST_RESULT WHERE SAVE_KEY=?";
        List<Object> params = new ArrayList<Object>(); params.add(id);
        long startedAt = System.currentTimeMillis();
        logSql("保存前重复校验", sql, params);
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, params);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                int count = rs.getInt(1);
                logSqlCompleted("保存前重复校验", count, startedAt);
                return count;
            }
        }
    }

    /** 只读取结果指标的日掩码，统计真实时段数量；不把96列容量当成有数据时段。 */
    private void enrichSummaries(Connection c, List<Map<String, Object>> records) throws Exception {
        if (records.isEmpty()) return;
        List<String> ids = new ArrayList<String>();
        for (Map<String, Object> record : records) ids.add(string(record.get("taskId")));
        String sql = "SELECT TASK_ID,OBJECT_CODE,TIME,TIME_STEP,PRESENT_MASK FROM YDT_FORECAST_RESULT "
                + "WHERE DELETED=0 AND TASK_ID IN " + placeholders(ids.size())
                + " AND ((DATA_KIND='API' AND DATA_CODE='discharge')"
                + " OR (DATA_KIND='TANK' AND DATA_CODE='flow')"
                + " OR (DATA_KIND='REGRESSION' AND DATA_CODE='shatuoOutflow')"
                + " OR (DATA_KIND='PENGSHUI' AND DATA_CODE='regionalFlowTotal')) ORDER BY OBJECT_CODE,TIME";
        Map<String, Map<String, java.util.SortedSet<Long>>> periods = new LinkedHashMap<String, Map<String, java.util.SortedSet<Long>>>();
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, new ArrayList<Object>(ids));
            try (ResultSet rs = s.executeQuery()) {
                while (rs.next()) {
                    String task = rs.getString("TASK_ID"), station = rs.getString("OBJECT_CODE");
                    if (!periods.containsKey(task)) periods.put(task, new LinkedHashMap<String, java.util.SortedSet<Long>>());
                    Map<String, java.util.SortedSet<Long>> objects = periods.get(task);
                    if (!objects.containsKey(station)) objects.put(station, new java.util.TreeSet<Long>());
                    String mask = rs.getString("PRESENT_MASK"); long day = rs.getTimestamp("TIME").getTime();
                    for (int i = 0; i < 96; i++) if (mask.charAt(i) == '1')
                        objects.get(station).add(day + (long) i * rs.getInt("TIME_STEP") * 60000L);
                }
            }
        }
        for (Map<String, Object> record : records) {
            Map<String, java.util.SortedSet<Long>> objects = periods.get(string(record.get("taskId")));
            List<String> names = new ArrayList<String>(); int count = 0; Date first = null, last = null;
            if (objects != null) for (Map.Entry<String, java.util.SortedSet<Long>> entry : objects.entrySet()) {
                names.add(stationName(entry.getKey())); java.util.SortedSet<Long> times = entry.getValue();
                count = Math.max(count, times.size());
                if (!times.isEmpty()) {
                    if (first == null || times.first() < first.getTime()) first = new Date(times.first());
                    if (last == null || times.last() > last.getTime()) last = new Date(times.last());
                }
            }
            if (names.isEmpty()) names.add(string(record.get("stationName")));
            TimeScale scale = new TimeScale("API_MODEL".equals(record.get("module")) ? "时" : "日", 1);
            record.put("forecastObjects", names); record.put("forecastPeriodCount", count);
            record.put("timeType", scale.type); record.put("timeInterval", scale.interval);
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
        String[][] columns={{"time","时间",null},{"jiangbinFlow","江滨以上","m³/s"},{"dahekouFlow","大河口以上","m³/s"},{"yangongFlow","沿龚区间","m³/s"},{"gongpengFlow","龚彭区间","m³/s"},{"regionalFlowTotal","四分区流量合计","m³/s"},{"shatuoOutflow","沙沱出库流量","m³/s"},{"pengshuiInflow","彭水","m³/s"}};addBasicColumns(object,columns);
        for(Map<String,Object> row:source){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("time",row.get("resultTime"));for(int i=1;i<columns.length;i++)item.put(columns[i][0],row.get(columns[i][0]));addData(object,item,true);}finish(object,"pengshuiInflow");return object;}

    private Map<String,Object> object(Map<String,Object> task,String stationCode){Map<String,Object> object=new LinkedHashMap<String,Object>();object.put("stationCode",stationCode);object.put("stationName",stationName(stationCode));object.put("modelCode",task.get("modelCode"));object.put("modelName",modelName(task));object.put("forecastBasisTime",task.get("forecastBasisTime"));object.put("forecastProductionTime",task.get("forecastProductionTime"));object.put("chartSeries",new ArrayList<Map<String,Object>>());object.put("tableColumns",new ArrayList<Map<String,Object>>());object.put("chartData",new ArrayList<Map<String,Object>>());object.put("tableData",new ArrayList<Map<String,Object>>());return object;}
    @SuppressWarnings("unchecked") private void addBasicColumns(Map<String,Object> object,String[][] columns){for(String[] column:columns)add((List<Map<String,Object>>)object.get("tableColumns"),column[0],column[1],column[2],null,null);}
    private void add(List<Map<String,Object>> fields,String key,String name,String unit,String type,String axis){Map<String,Object> field=new LinkedHashMap<String,Object>();field.put("key",key);field.put("name",name);field.put("unit",unit);field.put("type",type);field.put("axis",axis);fields.add(field);}
    @SuppressWarnings("unchecked") private void addData(Map<String,Object> object,Map<String,Object> item,boolean chart){((List<Map<String,Object>>)object.get("tableData")).add(item);if(chart)((List<Map<String,Object>>)object.get("chartData")).add(new LinkedHashMap<String,Object>(item));}
    @SuppressWarnings("unchecked") private void finish(Map<String,Object> object,String flowKey){ForecastResultPrecision.apply(object.get("tableData"));ForecastResultPrecision.apply(object.get("chartData"));List<Map<String,Object>> rows=(List<Map<String,Object>>)object.get("tableData");object.put("forecastPeriodCount",rows.size());Date first=null,last=null;Double maximum=null;String maximumTime=null;for(Map<String,Object> row:rows){Date current=date(row.get("time"));if(current!=null){if(first==null||current.before(first))first=current;if(last==null||current.after(last))last=current;}Double value=number(row.get(flowKey));if(value!=null&&(maximum==null||value>maximum)){maximum=value;maximumTime=string(row.get("time"));}}TimeScale scale=new TimeScale("inflow".equals(flowKey) ? "时" : "日",1);object.put("maximumFlow",ForecastResultPrecision.flow(maximum));object.put("maximumFlowTime",maximumTime);object.put("timeType",scale.type);object.put("timeInterval",scale.interval);}
    private Map<String,Double> rainfallByTime(List<Map<String,Object>> inputs){Map<String,Double> result=new LinkedHashMap<String,Double>();for(Map<String,Object> row:inputs){String code=trim(string(row.get("seriesCode"))).toLowerCase();if(!code.contains("rain")&&!code.contains("pre"))continue;String time=string(row.get("dataTime"));Double value=number(row.get("actualValue"));if(value==null||trim(time).isEmpty())continue;Double weight=number(row.get("weight"));double actual=weight==null?value:value*weight;result.put(time,result.containsKey(time)?result.get(time)+actual:actual);}return result;}
    private TimeScale timeScale(Date first,Date last,Integer count){if(first==null||last==null||count==null||count<=1)return new TimeScale("日",1);Calendar begin=Calendar.getInstance();begin.setTime(first);Calendar end=Calendar.getInstance();end.setTime(last);int months=(end.get(Calendar.YEAR)-begin.get(Calendar.YEAR))*12+end.get(Calendar.MONTH)-begin.get(Calendar.MONTH);if(months>0&&months%(count-1)==0&&begin.get(Calendar.DAY_OF_MONTH)==end.get(Calendar.DAY_OF_MONTH))return new TimeScale("月",months/(count-1));long hours=Math.max(1L,(last.getTime()-first.getTime())/3600000L/(count-1));return hours%24L==0L?new TimeScale("日",(int)(hours/24L)):new TimeScale("时",(int)hours);}
    private Date date(Object value){if(value instanceof Date)return(Date)value;if(value==null||trim(String.valueOf(value)).isEmpty())return null;try{return new SimpleDateFormat(DATE_PATTERN).parse(String.valueOf(value));}catch(ParseException ex){return null;}}
    private String stationName(String code){try{return registry.resolveRoute(code).getStation().getName();}catch(RuntimeException ex){return code;}}
    private String modelName(Map<String,Object> task){try{return registry.resolveRoute(string(task.get("stationCode"))).getModel().getName();}catch(RuntimeException ex){return string(task.get("modelCode"));}}
    private String placeholders(int size){StringBuilder value=new StringBuilder("(");for(int i=0;i<size;i++){if(i>0)value.append(',');value.append('?');}return value.append(')').toString();}
    private List<Object> repeat(List<String> values,int times){List<Object> result=new ArrayList<Object>();for(int i=0;i<times;i++)result.addAll(values);return result;}
    private static class TimeScale{private final String type;private final int interval;private TimeScale(String type,int interval){this.type=type;this.interval=interval;}}

    private Map<String, Object> taskSummary(Connection c, String id) throws Exception {
        String sql = "SELECT * FROM YDT_FORECAST_RESULT WHERE ID=? AND SUMMARY_FLAG=1 AND DELETED=0";
        List<Object> params = new ArrayList<Object>(); params.add(id);
        long startedAt = System.currentTimeMillis();
        logSql("结果任务摘要", sql, params);
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, params);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) throw error("保存结果不存在: " + id);
                logSqlCompleted("结果任务摘要", 1, startedAt);
                return readSummary(rs);
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
        long startedAt = System.currentTimeMillis();
        logSql("结果明细查询", sql, params);
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
        logSqlCompleted("结果明细查询", rows.size(), startedAt);
        return rows;
    }

    private String conditions(String station, String module, String begin, String end,
                              String timeFilterType, String forecaster, List<Object> params) {
        StringBuilder where = new StringBuilder("SUMMARY_FLAG=1 AND DELETED=0");
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

    /**
     * SQL 日志默认关闭，只有云南大唐现场显式配置后才输出，保证其他生产现场无配置也能正常升级。
     */
    private void logSql(String operation, String sql, List<?> params) {
        if (!sqlLoggingEnabled()) return;
        LOGGER.info("[云南大唐][SQL] operation={}, databaseService={}, parameterizedSql={}", operation,
                resultDatabaseService(), ForecastLogHelper.compactSql(sql));
        LOGGER.info("[云南大唐][SQL] operation={}, parameters={}", operation,
                ForecastLogHelper.formatParameters(params));
        LOGGER.info("[云南大唐][SQL] operation={}, executableSql={}", operation,
                ForecastLogHelper.renderSql(sql, params));
    }

    private void logSqlCompleted(String operation, int rows, long startedAt) {
        if (!sqlLoggingEnabled()) return;
        LOGGER.info("[云南大唐][SQL] operation={} completed, rows={}, elapsedMs={}", operation, rows,
                System.currentTimeMillis() - startedAt);
    }

    private boolean sqlLoggingEnabled() {
        return environment.getProperty("forecast.logging.sql-enabled", Boolean.class, false);
    }

    private String resultDatabaseService() {
        return environment.getProperty("service.yunnan-datang.result-db-service", "wds");
    }

    Connection connection() throws Exception {
        return ConnectionJDBC.getInstance(resultDatabaseService());
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
    /** 前端可编辑表格会把数字序列化成字符串，关系表落库前统一还原。 */
    private Double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        String text = trim(value == null ? null : String.valueOf(value));
        if (text.isEmpty()) return null;
        try {
            Double result = Double.valueOf(text);
            return result.isNaN() || result.isInfinite() ? null : result;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private Integer integer(Object value) {
        Double result = number(value);
        return result == null ? null : result.intValue();
    }
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
