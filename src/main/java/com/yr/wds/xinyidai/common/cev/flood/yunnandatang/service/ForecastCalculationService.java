package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.AggregationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.AggregationSourceDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.TankParameterProfileDefinition;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.model.PythonModelHttpClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 校验页面快照、转换模型入参并按 XML 路由调用对应的 Python 服务。 */
@Service
@ConditionalOnYunnanDatangFeature
public class ForecastCalculationService {
    private final StationModelRegistry registry;
    private final PythonModelHttpClient modelClient;
    private final ApiModelParameterService apiModelParameterService;

    public ForecastCalculationService(StationModelRegistry registry, PythonModelHttpClient modelClient,
                                      ApiModelParameterService apiModelParameterService) {
        this.registry = registry;
        this.modelClient = modelClient;
        this.apiModelParameterService = apiModelParameterService;
    }

    public ForecastContracts.CalculateResponse calculate(ForecastContracts.CalculateRequest request,
                                                         String expectedModule) {
        if ("TANK_MODEL".equals(expectedModule) && request != null
                && request.getInputs() != null && !request.getInputs().isEmpty()) {
            return calculateTankGroup(request);
        }
        ForecastContracts.InputResponse input = requireInput(request);
        StationModelRoute route = registry.resolveRoute(input.getStationCode());
        if (!expectedModule.equals(route.getStation().getModule())) {
            throw error("电站 " + input.getStationCode() + " 不属于 " + expectedModule);
        }
        if (!route.getModel().getCode().equals(input.getModelCode())) {
            throw error("页面模型与当前 XML 配置不一致，请重新加载输入数据");
        }

        ForecastMissingValues.normalize(input, expectedModule);
        String calculationId = UUID.randomUUID().toString();
        Map<String, Object> modelRequest = new LinkedHashMap<String, Object>();
        modelRequest.put("taskId", calculationId);
        modelRequest.put("userId", null);
        modelRequest.put("tenantId", null);
        Map<String, Object> payload = buildPayload(input, request.getParameters(), expectedModule,
                route.getStation());
        modelRequest.put("payload", payload);
        Map<String, Object> modelResult = "API_MODEL".equals(expectedModule)
                ? ApiAnnualCalculation.calculate(payload) : modelClient.invoke(route.getModel(), modelRequest);

        ForecastContracts.CalculateResponse response = new ForecastContracts.CalculateResponse();
        response.setCalculationId(calculationId);
        response.setStationCode(input.getStationCode());
        response.setModelCode(input.getModelCode());
        response.setSaved(false);
        response.setInputData(input);
        if ("API_MODEL".equals(expectedModule)) {
            response.setResultData(buildApiResultData(modelResult, payload, input.getModelParameters()));
        } else if ("REGRESSION_MODEL".equals(expectedModule)) {
            response.setResultData(buildRegressionResultData(modelResult, payload));
        } else {
            response.setResultData(modelResult);
        }
        ForecastResultPrecision.applyResultData(response.getResultData());
        return response;
    }

    /** 将 API 模型三条过程线统一转换为带 time/value 的图表数据，同时保留 Python 原始结果。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildApiResultData(Map<String, Object> rawResult,
                                                   Map<String, Object> payload,
                                                   Map<String, Object> modelParameters) {
        Object modelObject = rawResult == null ? null : rawResult.get("modelResult");
        if (!(modelObject instanceof Map)) throw error("API模型未返回modelResult");
        Map<String, Object> modelResult = (Map<String, Object>) modelObject;
        List<Map<String, Object>> rainfall = (List<Map<String, Object>>) payload.get("rainfallSeries");

        Map<String, Map<String, Object>> rows = new TreeMap<String, Map<String, Object>>();
        for (Map<String, Object> item : rainfall) {
            chartRow(rows, String.valueOf(item.get("time"))).put("rainfall", item.get("rainfall"));
        }
        appendModelValues(rows, "netRainfall", requiredList(modelResult, "net_rainfall_series"), rainfall, 1);
        appendModelValues(rows, "inflow", requiredList(modelResult, "discharge_series"), rainfall, 1);
        fillMissingFields(rows, "rainfall", "netRainfall", "inflow");
        String lastRainTime = String.valueOf(rainfall.get(rainfall.size() - 1).get("time"));
        for (Map.Entry<String, Map<String, Object>> entry : rows.entrySet()) {
            if (entry.getKey().compareTo(lastRainTime) > 0) {
                entry.getValue().put("rainfall", 0D);
                entry.getValue().put("netRainfall", 0D);
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("chartData", new ArrayList<Map<String, Object>>(rows.values()));
        result.put("modelParameters", apiPageModelParameters(modelParameters));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> apiPageModelParameters(Map<String, Object> parameters) {
        Map<String, Object> display = new LinkedHashMap<String, Object>();
        display.put("base_flow", ApiAnnualCalculation.baseFlow(parameters));
        display.put("unit_hydrograph_code", parameters == null ? null : parameters.get("unit_hydrograph_code"));
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        Object source = parameters == null ? null : parameters.get("unit_hydrograph_options");
        if (source instanceof List) {
            for (Object value : (List<Object>) source) {
                if (!(value instanceof Map)) continue;
                Map<String, Object> option = (Map<String, Object>) value;
                Map<String, Object> visible = new LinkedHashMap<String, Object>();
                visible.put("code", option.get("code"));
                visible.put("name", option.get("name"));
                visible.put("values", option.get("values"));
                options.add(visible);
            }
        }
        display.put("unit_hydrograph_options", options);
        return display;
    }

    private List<?> requiredList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof List)) throw error("API模型未返回" + key);
        return (List<?>) value;
    }

    private void appendModelValues(Map<String, Map<String, Object>> rows, String field, List<?> values,
                                   List<Map<String, Object>> rainfall, int intervalHours) {
        for (int i = 0; i < values.size(); i++) {
            chartRow(rows, chartTime(rainfall, i, intervalHours)).put(field, values.get(i));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRegressionResultData(Map<String, Object> rawResult,
                                                          Map<String, Object> payload) {
        Object seriesObject = rawResult == null ? null : rawResult.get("forecastSeries");
        if (!(seriesObject instanceof List)) throw error("沙沱模型未返回forecastSeries");
        Map<String, Map<String, Object>> rows = new TreeMap<String, Map<String, Object>>();
        Object sourceObject = payload.get("dataSeries");
        if (sourceObject instanceof List) {
            for (Object itemObject : (List<?>) sourceObject) {
                if (!(itemObject instanceof Map)) continue;
                Map<String, Object> item = (Map<String, Object>) itemObject;
                String time = item.get("time") == null ? "" : String.valueOf(item.get("time"));
                if (time.isEmpty()) continue;
                Map<String, Object> row = chartRow(rows, time);
                for (String field : Arrays.asList("rainfall", "shatuoOutflow", "shatuoInflow",
                        "shatuoWaterLevel", "goupitanInflow", "goupitanOutflow",
                        "shatuoRainfallAverage", "shatuoOutflowAverage", "shatuoInflowAverage")) {
                    row.put(field, item.get(field));
                }
                row.put("forecast", Boolean.TRUE.equals(item.get("forecast")));
            }
        }
        for (Object itemObject : (List<?>) seriesObject) {
            if (!(itemObject instanceof Map)) throw error("沙沱模型forecastSeries元素必须是对象");
            Map<String, Object> item = (Map<String, Object>) itemObject;
            String time = item.get("time") == null ? "" : String.valueOf(item.get("time"));
            if (time.isEmpty()) throw error("沙沱模型forecastSeries缺少time");
            Map<String, Object> row = chartRow(rows, time);
            row.put("shatuoOutflow", item.get("flow"));
            row.put("forecast", true);
        }
        fillMissingFields(rows, "rainfall", "shatuoOutflow", "shatuoInflow", "shatuoWaterLevel",
                "goupitanInflow", "goupitanOutflow", "shatuoRainfallAverage",
                "shatuoOutflowAverage", "shatuoInflowAverage");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("chartData", new ArrayList<Map<String, Object>>(rows.values()));
        return result;
    }

    private Map<String, Object> chartRow(Map<String, Map<String, Object>> rows, String time) {
        Map<String, Object> row = rows.get(time);
        if (row == null) {
            row = new LinkedHashMap<String, Object>();
            row.put("time", time);
            rows.put(time, row);
        }
        return row;
    }

    private void fillMissingFields(Map<String, Map<String, Object>> rows, String... fields) {
        for (Map<String, Object> row : rows.values()) {
            for (String field : fields) if (!row.containsKey(field)) row.put(field, null);
        }
    }

    private String chartTime(List<Map<String, Object>> rainfall, int index, int intervalHours) {
        if (index < rainfall.size()) return String.valueOf(rainfall.get(index).get("time"));
        String firstTime = String.valueOf(rainfall.get(0).get("time"));
        try {
            LocalDateTime start = LocalDateTime.parse(firstTime.replace('T', ' '),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return start.plusHours((long) index * intervalHours)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ex) {
            throw error("API模型降雨时间格式错误，无法生成流量过程时间: " + firstTime);
        }
    }

    private Map<String, Object> buildPayload(ForecastContracts.InputResponse input,
                                             Map<String, Object> parameters,
                                             String module,
                                             StationDefinition station) {
        if ("API_MODEL".equals(module)) return apiPayload(input, parameters, station);
        if ("TANK_MODEL".equals(module)) return tankPayload(input, parameters, station);
        if ("REGRESSION_MODEL".equals(module)) return regressionPayload(input, parameters);
        throw error("不支持的模型模块: " + module);
    }

    private Map<String, Object> apiPayload(ForecastContracts.InputResponse input,
                                           Map<String, Object> parameters,
                                           StationDefinition station) {
        // 计算时重新查库，页面参数仍可覆盖后触发重新计算。
        Map<String, Object> databaseParameters = apiModelParameterService.load(station, input.getForecastTime());
        Map<String, Object> payload = apiModelParameterService.applyUnitSelection(
                databaseParameters, apiRequestParameters(input, parameters));
        // 返回全部候选单位线，并把本次选中项同步回页面快照。
        Map<String, Object> responseParameters = new LinkedHashMap<String, Object>(databaseParameters);
        responseParameters.putAll(payload);
        responseParameters.put("unit_hydrograph_options", databaseParameters.get("unit_hydrograph_options"));
        input.setModelParameters(responseParameters);
        payload.put("stationCode", input.getStationCode());
        payload.put("forecastTime", input.getForecastTime());
        List<Map<String, Object>> rainfall = ApiAnnualCalculation.rainfall(input);
        payload.put("rainfallSeries", rainfall);
        payload.put("rainfall_series", values(rainfall));
        return payload;
    }

    private Map<String, Object> apiRequestParameters(ForecastContracts.InputResponse input,
                                                      Map<String, Object> parameters) {
        Map<String,Object> result=copy(parameters);
        if(!result.containsKey("base_flow") && input.getModelParameters()!=null
                && input.getModelParameters().containsKey("base_flow")) {
            result.put("base_flow", input.getModelParameters().get("base_flow"));
        }
        return result;
    }

    private Map<String, Object> tankPayload(ForecastContracts.InputResponse input,
                                            Map<String, Object> parameters,
                                            StationDefinition station) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("basinCode", input.getStationCode());
        payload.put("forecastTime", input.getForecastTime());
        payload.put("intervalHours", 24);
        validateTankRainfall(input);
        List<Map<String, Object>> rainfall = tankAnnualRainfall(input, station);
        payload.put("rainfallSeries", rainfall);
        payload.put("rainfallSequence", values(rainfall));
        Map<String, Object> initialState = mapValue(parameters, "initialState");
        if (initialState.isEmpty()) {
            initialState.put("d", 15D);
            initialState.put("i", 15D);
            initialState.put("l", 15D);
        }
        for (String key : Arrays.asList("d", "i", "l")) {
            Object value = initialState.get(key);
            if (!(value instanceof Number) || ((Number) value).doubleValue() != 15D)
                throw error("Excel初始起算种子固定为15，不能用任意日期水深替代: " + key);
        }
        // 旧Python首步会加本日雨量；Excel首行D7=15-C7，不加B7。
        // 仅在调用边界抵消首日雨量，保留真实雨量用于蒸发和结果展示。
        // d可能为负，这是协议适配值，不是业务蓄水深；不得回写页面/保存快照。
        Map<String, Object> pythonInitialState = new LinkedHashMap<String, Object>(initialState);
        pythonInitialState.put("d", 15D - ((Number) rainfall.get(0).get("rainfall")).doubleValue());
        payload.put("initialState", pythonInitialState);
        TankParameterProfileDefinition profile = registry.getConfiguration().getTankParameterProfileMap()
                .get(station.getParameterProfileRef());
        if (profile == null) throw error("水箱分区缺少参数方案: " + station.getCode());
        payload.put("modelParameters", new LinkedHashMap<String, Object>(profile.getModelParameters()));
        return payload;
    }

    private List<Map<String, Object>> tankAnnualRainfall(ForecastContracts.InputResponse input, StationDefinition station) {
        java.time.LocalDate begin = ForecastDailyRows.parse(input.getForecastBeginTime()).toLocalDate();
        java.time.LocalDate yearStart = TankDailyForecastService.calculationStart(begin);
        if (!ForecastDailyRows.parse(input.getForecastTime()).toLocalDate().equals(begin)
                || input.getHistoryBeginTime() == null
                || !ForecastDailyRows.parse(input.getHistoryBeginTime()).toLocalDate().equals(yearStart)
                || input.getHistoryEndTime() == null
                || !ForecastDailyRows.parse(input.getHistoryEndTime()).toLocalDate().equals(begin.minusDays(1)))
            throw error("水箱必须从" + yearStart + "连续计算，请重新加载输入");
        Map<String, ForecastContracts.SeriesResponse> bySensor = new LinkedHashMap<>();
        for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            if (!"rainfall".equals(series.getKey())) continue;
            if (bySensor.put(series.getSenid(), series) != null) throw error("历史雨量站重复: " + series.getSenid());
        }
        Map<String, Double> total = new TreeMap<>();
        com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceDataProfileDefinition profile =
                registry.getConfiguration().getSourceDataProfileMap().get(station.getDataProfileRef());
        for (com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceSeriesDefinition definition : profile.getSeries()) {
            if (!"rainfall".equals(definition.getRole())) continue;
            ForecastContracts.SeriesResponse series = bySensor.remove(definition.getSensorId());
            if (series == null) throw error("缺少历史雨量站: " + definition.getName());
            Map<String, Double> days = new LinkedHashMap<>();
            for (ForecastContracts.ValuePoint point : series.getValues()) {
                if (point == null || point.getTime() == null) throw error("历史雨量日期不能为空");
                LocalDateTime time = ForecastDailyRows.parse(point.getTime());
                if (!time.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)
                        || time.toLocalDate().isBefore(yearStart) || !time.toLocalDate().isBefore(begin))
                    throw error("历史雨量日期不在逐日计算范围内: " + point.getTime());
                if (days.containsKey(point.getTime())) throw error("历史雨量日期重复: " + point.getTime());
                days.put(point.getTime(), point.getValue());
            }
            for (java.time.LocalDate day = yearStart; day.isBefore(begin); day = day.plusDays(1)) {
                String time = day + " 00:00:00";
                Double value = days.get(time);
                if (value == null || !Double.isFinite(value) || value < 0)
                    throw error("历史雨量缺失或无效: " + station.getName() + "/" + definition.getName() + "/" + day);
                total.put(time, total.getOrDefault(time, 0D) + value * definition.getWeight());
            }
        }
        if (!bySensor.isEmpty()) throw error("历史雨量含未配置站点: " + bySensor.keySet());
        for (ForecastContracts.ValuePoint point : input.getForecastRainfall().getValues()) total.put(point.getTime(), point.getValue());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : total.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", entry.getKey()); row.put("rainfall", entry.getValue()); result.add(row);
        }
        return result;
    }

    private void validateTankRainfall(ForecastContracts.InputResponse input) {
        if (input.getForecastRainfall() == null || input.getForecastRainfall().getValues().isEmpty())
            throw error("请填写水箱分区预报雨量: " + input.getStationCode());
        Map<String,Double> values = new LinkedHashMap<String,Double>();
        for (ForecastContracts.ValuePoint p : input.getForecastRainfall().getValues()) {
            if (p == null || p.getValue() == null || !Double.isFinite(p.getValue()) || p.getValue() < 0)
                throw error("预报雨量必须填写非负数，晴天请明确填写0: " + input.getStationCode());
            LocalDateTime time = ForecastDailyRows.parse(p.getTime());
            if (!time.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) || values.put(p.getTime(),p.getValue()) != null)
                throw error("水箱预报雨量必须每天一条且时间为零点: " + p.getTime());
        }
        if (input.getForecastBeginTime() == null || input.getForecastEndTime() == null)
            throw error("水箱输入缺少预报起止时间，请重新加载");
        java.time.LocalDate begin = ForecastDailyRows.parse(input.getForecastBeginTime()).toLocalDate();
        java.time.LocalDate end = ForecastDailyRows.parse(input.getForecastEndTime()).toLocalDate();
        if (begin.isAfter(end)) throw error("预报开始时间不能晚于结束时间");
        for (java.time.LocalDate day=begin; !day.isAfter(end); day=day.plusDays(1))
            if (!values.containsKey(day.toString()+" 00:00:00")) throw error("预报雨量缺少日期: " + input.getStationCode()+"/"+day);
        if (values.size() != java.time.temporal.ChronoUnit.DAYS.between(begin,end)+1)
            throw error("预报雨量存在所选日期范围外的记录");
    }

    /** 完整水箱计算由 Java 编排四个分区，Python 继续只负责单分区算法。 */
    private ForecastContracts.CalculateResponse calculateTankGroup(ForecastContracts.CalculateRequest request) {
        StationDefinition aggregateStation = aggregateTankStation();
        AggregationDefinition aggregation = registry.getConfiguration().getAggregationMap()
                .get(aggregateStation.getAggregationRef());
        if (aggregation == null) throw error("水箱汇总方案不存在: " + aggregateStation.getAggregationRef());

        Map<String, ForecastContracts.InputResponse> inputByStation =
                new LinkedHashMap<String, ForecastContracts.InputResponse>();
        for (ForecastContracts.InputResponse input : request.getInputs()) {
            if (input == null || input.getStationCode() == null || input.getStationCode().trim().isEmpty()) {
                throw error("inputs中的stationCode不能为空");
            }
            if (inputByStation.put(input.getStationCode(), input) != null) {
                throw error("水箱分区输入重复: " + input.getStationCode());
            }
        }

        List<AggregationSourceDefinition> basinSources = new ArrayList<AggregationSourceDefinition>();
        for (AggregationSourceDefinition source : aggregation.getSources()) {
            if (source.getPoint() == null || source.getPoint().isEmpty()) basinSources.add(source);
        }
        if (inputByStation.size() != basinSources.size()) {
            throw error("水箱完整计算必须提交 " + basinSources.size() + " 个分区输入");
        }

        String calculationId = UUID.randomUUID().toString();
        List<Map<String, Object>> basinDetails = new ArrayList<Map<String, Object>>();
        Map<String, Double> regionalFlowByTime = new TreeMap<String, Double>();
        Set<String> expectedTimes = null;
        ForecastContracts.InputResponse firstInput = request.getInputs().get(0);
        for (ForecastContracts.InputResponse input : request.getInputs()) {
            if (!java.util.Objects.equals(firstInput.getForecastBeginTime(), input.getForecastBeginTime())
                    || !java.util.Objects.equals(firstInput.getForecastEndTime(), input.getForecastEndTime()))
                throw error("四个分区的预报起止日期必须一致");
            StationModelRoute route = validateTankInput(input);
            ForecastMissingValues.normalize(input, "TANK_MODEL");
            tankPayload(input, stationParameters(request, input.getStationCode()), route.getStation());
        }
        for (AggregationSourceDefinition source : basinSources) {
            ForecastContracts.InputResponse input = inputByStation.remove(source.getStationCode());
            if (input == null) throw error("缺少水箱分区输入: " + source.getStationCode());
            StationModelRoute route = validateTankInput(input);
            Map<String, Object> parameters = stationParameters(request, source.getStationCode());

            Map<String, Object> modelRequest = new LinkedHashMap<String, Object>();
            modelRequest.put("taskId", calculationId + ":" + source.getStationCode());
            modelRequest.put("userId", null);
            modelRequest.put("tenantId", null);
            Map<String, Object> payload = tankPayload(input, parameters, route.getStation());
            modelRequest.put("payload", payload);
            Map<String, Object> modelResult = modelClient.invoke(route.getModel(), modelRequest);

            Set<String> basinTimes = addBasinFlows(source.getStationCode(), modelResult, regionalFlowByTime);
            Map<String, Object> basinDetail = new LinkedHashMap<String, Object>();
            basinDetail.put("stationCode", source.getStationCode());
            basinDetail.put("stationName", route.getStation().getName());
            // 只返回 Excel 可见参数、首时段起算值和可见过程列，不暴露 Python 内部包装。
            basinDetail.put("modelParameters", tankPageParameterRows(payload.get("modelParameters")));
            Map<String, Object> excelSeed = new LinkedHashMap<String, Object>();
            excelSeed.put("d", 15D); excelSeed.put("i", 15D); excelSeed.put("l", 15D);
            basinDetail.put("initialState", excelSeed);
            basinDetail.put("tableData", tankPageTableData(modelResult.get("forecastSeries")));
            basinDetails.add(basinDetail);
            if (expectedTimes == null) expectedTimes = basinTimes;
            else if (!expectedTimes.equals(basinTimes)) {
                throw error("水箱分区 " + source.getStationCode() + " 返回的日期集合与其他分区不一致");
            }
        }
        if (!inputByStation.isEmpty()) throw error("提交了不属于水箱汇总方案的分区: " + inputByStation.keySet());

        ForecastContracts.CalculateResponse response = new ForecastContracts.CalculateResponse();
        response.setCalculationId(calculationId);
        response.setStationCode(aggregateStation.getCode());
        response.setModelCode(aggregateStation.getModelRef());
        response.setSaved(false);
        response.setInputDataList(new ArrayList<ForecastContracts.InputResponse>(request.getInputs()));
        if (request.getShatuoOutflowSeries() != null && !request.getShatuoOutflowSeries().isEmpty()) {
            Map<String, Double> outflow = new LinkedHashMap<>();
            for (ForecastContracts.ValuePoint point : request.getShatuoOutflowSeries()) {
                if (point == null || point.getTime() == null || point.getValue() == null
                        || !Double.isFinite(point.getValue()) || point.getValue() < 0
                        || !regionalFlowByTime.containsKey(point.getTime())
                        || outflow.put(point.getTime(), point.getValue()) != null)
                    throw error("沙沱出库须为计算范围内每天一条非负数");
            }

        }
        Map<String, Object> result = aggregateResult(basinDetails, regionalFlowByTime,
                tankResultOutflow(firstInput, request.getShatuoOutflowSeries()));
        ForecastResultPrecision.applyResultData(result);
        response.setResultData(result);
        return response;
    }

    private StationDefinition aggregateTankStation() {
        StationDefinition found = null;
        for (StationDefinition station : registry.getConfiguration().getStations()) {
            if (station.isEnabled() && "TANK_MODEL".equals(station.getModule())
                    && station.getAggregationRef() != null && !station.getAggregationRef().isEmpty()) {
                if (found != null) throw error("当前只支持一个水箱汇总电站");
                found = station;
            }
        }
        if (found == null) throw error("XML未配置水箱汇总电站");
        return found;
    }

    private StationModelRoute validateTankInput(ForecastContracts.InputResponse input) {
        if (input.getSeries() == null) input.setSeries(new ArrayList<ForecastContracts.SeriesResponse>());
        StationModelRoute route = registry.resolveRoute(input.getStationCode());
        if (!"TANK_MODEL".equals(route.getStation().getModule())
                || route.getStation().getAggregationRef() != null
                && !route.getStation().getAggregationRef().isEmpty()) {
            throw error("不是可计算的水箱分区: " + input.getStationCode());
        }
        if (!route.getModel().getCode().equals(input.getModelCode())) {
            throw error("分区 " + input.getStationCode() + " 的页面模型与XML配置不一致");
        }
        return route;
    }

    private Map<String, Object> stationParameters(ForecastContracts.CalculateRequest request,
                                                  String stationCode) {
        Map<String, Object> parameters = copy(request.getParameters());
        if (request.getStationParameters() != null) {
            Map<String, Object> station = request.getStationParameters().get(stationCode);
            if (station != null && station.get("initialState") != null) {
                parameters.put("initialState", station.get("initialState"));
            }
        }
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tankPageParameterRows(Object value) {
        Map<String, Object> source = value instanceof Map
                ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
        Map<String, Object> evaporationSource = objectMap(source.get("evaporation"));
        Map<String, Object> tank1Source = objectMap(source.get("tank1"));
        Map<String, Object> tank2Source = objectMap(source.get("tank2"));
        Map<String, Object> tank3Source = objectMap(source.get("tank3"));
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();

        // 四行参数与 Excel 红色参数区逐行、逐列对应，字段名与 tableData 保持一致。
        Map<String, Object> firstRow = new LinkedHashMap<String, Object>();
        putIfNotNull(firstRow, "evaporation", evaporationSource.get("value1"));
        putIfNotNull(firstRow, "flow", source.get("areaFactor"));
        rows.add(firstRow);

        Map<String, Object> coefficientRow = new LinkedHashMap<String, Object>();
        putIfNotNull(coefficientRow, "evaporation", evaporationSource.get("value2"));
        putGroupIfNotEmpty(coefficientRow, "tank1", parameterGroup(tank1Source,
                "upperHole", "upperCoefficient", "middleHole", "middleCoefficient",
                "lowerHole", "lowerCoefficient", "infiltration", "infiltrationCoefficient"));
        putGroupIfNotEmpty(coefficientRow, "tank2", parameterGroup(tank2Source,
                "outflow", "outflowCoefficient", "percolation", "percolationCoefficient"));
        putGroupIfNotEmpty(coefficientRow, "tank3", parameterGroup(tank3Source,
                "middleHole", "middleCoefficient", "bottomHole", "bottomCoefficient"));
        rows.add(coefficientRow);

        Map<String, Object> thresholdRow = new LinkedHashMap<String, Object>();
        putIfNotNull(thresholdRow, "evaporation", evaporationSource.get("value3"));
        putGroupIfNotEmpty(thresholdRow, "tank1", parameterGroup(tank1Source,
                "upperHole", "upperThreshold", "middleHole", "middleThreshold",
                "lowerHole", "lowerThreshold"));
        putGroupIfNotEmpty(thresholdRow, "tank2", parameterGroup(tank2Source,
                "outflow", "outflowThreshold"));
        putGroupIfNotEmpty(thresholdRow, "tank3", parameterGroup(tank3Source,
                "middleHole", "middleThreshold"));
        rows.add(thresholdRow);

        Map<String, Object> evaporationThresholdRow = new LinkedHashMap<String, Object>();
        putIfNotNull(evaporationThresholdRow, "evaporation", evaporationSource.get("threshold2"));
        rows.add(evaporationThresholdRow);
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private Map<String, Object> parameterGroup(Map<String, Object> source, String... keyPairs) {
        Map<String, Object> visible = new LinkedHashMap<String, Object>();
        for (int index = 0; index < keyPairs.length; index += 2) {
            putIfNotNull(visible, keyPairs[index], source.get(keyPairs[index + 1]));
        }
        return visible;
    }

    private void putGroupIfNotEmpty(Map<String, Object> row, String key, Map<String, Object> group) {
        if (!group.isEmpty()) {
            row.put(key, group);
        }
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> tankPageInitialState(Object value) {
        Map<String, Object> source = objectMap(value);
        return parameterGroup(source, "d", "d", "i", "i", "l", "l");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tankPageTableData(Object value) {
        List<Map<String, Object>> visibleRows = new ArrayList<Map<String, Object>>();
        if (!(value instanceof List)) {
            return visibleRows;
        }
        for (Object item : (List<Object>) value) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> source = (Map<String, Object>) item;
            Map<String, Object> row = parameterGroup(source,
                    "time", "time", "rainfall", "rainfall", "evaporation", "evaporation");
            putGroupIfNotEmpty(row, "tank1", parameterGroup(objectMap(source.get("tank1")),
                    "storageDepth", "storageDepth", "upperHole", "upperHole",
                    "middleHole", "middleHole", "lowerHole", "lowerHole",
                    "infiltration", "infiltration"));
            putGroupIfNotEmpty(row, "tank2", parameterGroup(objectMap(source.get("tank2")),
                    "storageDepth", "storageDepth", "outflow", "outflow",
                    "percolation", "percolation"));
            putGroupIfNotEmpty(row, "tank3", parameterGroup(objectMap(source.get("tank3")),
                    "storageDepth", "storageDepth", "middleHole", "middleHole",
                    "bottomHole", "bottomHole"));
            putIfNotNull(row, "outflowDepth", source.get("outflowDepth"));
            putIfNotNull(row, "flow", source.get("flow"));
            visibleRows.add(row);
        }
        return visibleRows;
    }

    @SuppressWarnings("unchecked")
    private Set<String> addBasinFlows(String stationCode, Map<String, Object> data,
                                      Map<String, Double> regionalFlowByTime) {
        Object seriesObject = data == null ? null : data.get("forecastSeries");
        if (!(seriesObject instanceof List)) throw error("水箱模型 " + stationCode + " 未返回forecastSeries");
        Set<String> basinTimes = new HashSet<String>();
        for (Object itemObject : (List<Object>) seriesObject) {
            if (!(itemObject instanceof Map)) throw error("水箱模型forecastSeries元素必须是对象");
            Map<String, Object> item = (Map<String, Object>) itemObject;
            String time = item.get("time") == null ? "" : String.valueOf(item.get("time"));
            Object flowObject = item.get("flow");
            if (time.isEmpty() || !(flowObject instanceof Number)) {
                throw error("水箱模型 " + stationCode + " 的forecastSeries缺少time或flow");
            }
            if (!basinTimes.add(time)) throw error("水箱模型 " + stationCode + " 返回重复时段: " + time);
            Double previous = regionalFlowByTime.get(time);
            regionalFlowByTime.put(time, (previous == null ? 0D : previous)
                    + ((Number) flowObject).doubleValue());
        }
        if (basinTimes.isEmpty()) throw error("水箱模型 " + stationCode + " 返回空forecastSeries");
        return basinTimes;
    }

    // 预报日期手填优先；留空沿用该日期之前最新有效实测，不将手填值当成实测继续后延。
    static List<ForecastContracts.ValuePoint> tankResultOutflow(ForecastContracts.InputResponse input,
            List<ForecastContracts.ValuePoint> boundary) {
        TreeMap<String, ForecastContracts.ValuePoint> measured = new TreeMap<>();
        Map<String, ForecastContracts.ValuePoint> result = new TreeMap<>();
        if (input.getSeries() != null) for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            if (!"shatuoOutflow".equals(series.getKey())) continue;
            for (ForecastContracts.ValuePoint point : series.getValues()) {
                result.put(point.getTime(), point);
                if (point.getValue() == null || "DEFAULT_ZERO".equals(point.getSource())) continue;
                if (!Double.isFinite(point.getValue()) || point.getValue() < 0)
                    throw new StationModelConfigException("沙沱实测出库无效: " + point.getTime());
                measured.put(point.getTime(), point);
            }
        }
        Map<String, ForecastContracts.ValuePoint> manual = new TreeMap<>();
        TreeMap<String, ForecastContracts.ValuePoint> planned = new TreeMap<>();
        if (input.getSeries() != null) for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            if (!"shatuoPlannedOutflow".equals(series.getKey())) continue;
            for (ForecastContracts.ValuePoint p : series.getValues()) {
                if (p.getValue() != null && Double.isFinite(p.getValue()) && p.getValue() >= 0)
                    planned.put(p.getTime(), p);
            }
        }
        if (boundary != null) for (ForecastContracts.ValuePoint point : boundary) manual.put(point.getTime(), point);

        java.time.LocalDate end = ForecastDailyRows.parse(input.getForecastEndTime()).toLocalDate();
        for (java.time.LocalDate day = ForecastDailyRows.parse(input.getForecastBeginTime()).toLocalDate();
                !day.isAfter(end); day = day.plusDays(1)) {
            String time = day + " 00:00:00";
            ForecastContracts.ValuePoint point = manual.get(time);
            if (point == null && planned.floorEntry(time) != null) {
                Map.Entry<String, ForecastContracts.ValuePoint> latest = planned.floorEntry(time);
                point = new ForecastContracts.ValuePoint();
                point.setTime(time); point.setValue(latest.getValue().getValue());
                point.setSource(latest.getKey().equals(time) ? "PLANLOAD" : "PLANLOAD_CARRY_FORWARD");
            }
            if (point == null) {
                Map.Entry<String, ForecastContracts.ValuePoint> latest = measured.floorEntry(time);

                point = new ForecastContracts.ValuePoint();
                point.setTime(time); point.setValue(latest == null ? 0D : latest.getValue().getValue());
                point.setSource(latest == null ? "DEFAULT_ZERO" : latest.getKey().equals(time) ? "DAYDB" : "DAYDB_CARRY_FORWARD");
                if (latest != null && latest.getValue().getSource() != null
                        && latest.getValue().getSource().startsWith("PLANLOAD")) {
                    point.setSource(latest.getKey().equals(time) ? latest.getValue().getSource() : "PLANLOAD_CARRY_FORWARD");
                }
            }
            result.put(time, point);
        }
        return new ArrayList<>(result.values());
    }

    private Map<String, Object> aggregateResult(List<Map<String, Object>> basinDetails,
                                                Map<String, Double> regionalFlowByTime,
                                                List<ForecastContracts.ValuePoint> shatuoSeries) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("chartData", tankChartData(basinDetails, regionalFlowByTime, shatuoSeries));
        result.put("basinDetails", basinDetails);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tankChartData(List<Map<String, Object>> basinDetails,
                                                    Map<String, Double> regionalFlowByTime,
                                                    List<ForecastContracts.ValuePoint> shatuoSeries) {
        Map<String, Map<String, Object>> rows = new TreeMap<String, Map<String, Object>>();
        for (Map<String, Object> basin : basinDetails) {
            String field = tankFlowField(String.valueOf(basin.get("stationCode")));
            Object seriesObject = basin.get("tableData");
            if (!(seriesObject instanceof List)) continue;
            for (Object itemObject : (List<?>) seriesObject) {
                if (!(itemObject instanceof Map)) continue;
                Map<String, Object> item = (Map<String, Object>) itemObject;
                chartRow(rows, String.valueOf(item.get("time"))).put(field, item.get("flow"));
            }
        }
        for (Map.Entry<String, Double> entry : regionalFlowByTime.entrySet()) {
            chartRow(rows, entry.getKey()).put("regionalFlowTotal", entry.getValue());
        }
        if (shatuoSeries != null && !shatuoSeries.isEmpty()) {
            Map<String, Double> shatuoByTime = new LinkedHashMap<String, Double>();
            for (ForecastContracts.ValuePoint point : shatuoSeries) {
                if (point == null || point.getTime() == null || point.getValue() == null) {
                    throw error("沙沱出库过程中的time和value不能为空");
                }
                shatuoByTime.put(point.getTime(), point.getValue());
            }
            for (Map.Entry<String, Double> entry : regionalFlowByTime.entrySet()) {
                Double shatuo = shatuoByTime.get(entry.getKey());
                if (shatuo == null) continue; // 历史出库未提供，不影响未来逐日汇总。
                Map<String, Object> row = chartRow(rows, entry.getKey());
                row.put("shatuoOutflow", shatuo);
                row.put("pengshuiInflow", entry.getValue() + shatuo);
            }
        }
        fillMissingFields(rows, "jiangbinFlow", "dahekouFlow", "yangongFlow", "gongpengFlow",
                "regionalFlowTotal", "shatuoOutflow", "pengshuiInflow");
        return new ArrayList<Map<String, Object>>(rows.values());
    }

    private String tankFlowField(String stationCode) {
        if ("TANK_JIANGBIN_UPSTREAM".equals(stationCode)) return "jiangbinFlow";
        if ("TANK_DAHEKOU_UPSTREAM".equals(stationCode)) return "dahekouFlow";
        if ("TANK_YANGONG_INTERVAL".equals(stationCode)) return "yangongFlow";
        if ("TANK_GONGPENG_INTERVAL".equals(stationCode)) return "gongpengFlow";
        return stationCode.toLowerCase() + "Flow";
    }

    private Map<String, Object> regressionPayload(ForecastContracts.InputResponse input,
                                                   Map<String, Object> parameters) {
        Map<String, Map<String, Object>> rows = new TreeMap<String, Map<String, Object>>();
        for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            for (ForecastContracts.ValuePoint value : series.getValues()) {
                Map<String, Object> row = timeRow(rows, value.getTime());
                row.put(series.getKey(), value.getValue());
                if ("rainfall".equals(series.getKey())) row.put("shatuoRainfallAverage", value.getAverageValue());
                if ("shatuoOutflow".equals(series.getKey())) row.put("shatuoOutflowAverage", value.getAverageValue());
                if ("shatuoInflow".equals(series.getKey())) row.put("shatuoInflowAverage", value.getAverageValue());
            }
        }
        if (input.getForecastRainfall() != null) {
            for (ForecastContracts.ValuePoint value : input.getForecastRainfall().getValues()) {
                timeRow(rows, value.getTime()).put("rainfall", value.getValue());
            }
        }
        String forecastDay = input.getForecastTime() == null ? "" : input.getForecastTime().substring(0, 10);
        for (Map<String, Object> row : rows.values()) {
            String time = String.valueOf(row.get("time"));
            row.put("forecast", !forecastDay.isEmpty() && time.substring(0, 10).compareTo(forecastDay) >= 0);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("basinCode", "SHATUO");
        payload.put("forecastTime", input.getForecastTime());
        payload.put("modelName", valueOrDefault(parameters, "modelName", "auto"));
        payload.put("applyMos", valueOrDefault(parameters, "applyMos", Boolean.TRUE));
        payload.put("dataSeries", new ArrayList<Map<String, Object>>(rows.values()));
        return payload;
    }

    /** 历史站点雨量按 XML 权重合成；未来 GFS 的 AVERPRE 是区域平均值，直接接续。 */
    private List<Map<String, Object>> areaRainfall(ForecastContracts.InputResponse input) {
        Map<String, Double> weighted = new TreeMap<String, Double>();
        for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            if (!"rainfall".equals(series.getKey())) continue;
            double weight = series.getWeight() == null ? 1D : series.getWeight();
            for (ForecastContracts.ValuePoint point : series.getValues()) {
                if (point.getValue() == null) continue;
                Double previous = weighted.get(point.getTime());
                weighted.put(point.getTime(), (previous == null ? 0D : previous) + point.getValue() * weight);
            }
        }
        if (input.getForecastRainfall() != null) {
            for (ForecastContracts.ValuePoint point : input.getForecastRainfall().getValues()) {
                if (point.getValue() != null) weighted.put(point.getTime(), point.getValue());
            }
        }
        if (weighted.isEmpty()) throw error("没有可用于模型计算的降雨数据");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Double> entry : weighted.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("time", entry.getKey());
            item.put("rainfall", entry.getValue());
            result.add(item);
        }
        return result;
    }

    private List<Double> values(List<Map<String, Object>> series) {
        List<Double> values = new ArrayList<Double>();
        for (Map<String, Object> item : series) values.add(((Number) item.get("rainfall")).doubleValue());
        return values;
    }

    private ForecastContracts.InputResponse requireInput(ForecastContracts.CalculateRequest request) {
        if (request == null || request.getInput() == null) throw error("input不能为空");
        ForecastContracts.InputResponse input = request.getInput();
        if (input.getStationCode() == null || input.getStationCode().trim().isEmpty()) {
            throw error("input.stationCode不能为空");
        }
        if (input.getSeries() == null) input.setSeries(new ArrayList<ForecastContracts.SeriesResponse>());
        return input;
    }

    private Map<String, Object> timeRow(Map<String, Map<String, Object>> rows, String time) {
        Map<String, Object> row = rows.get(time);
        if (row == null) {
            row = new LinkedHashMap<String, Object>();
            row.put("time", time);
            rows.put(time, row);
        }
        return row;
    }
    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Map)) return new LinkedHashMap<String, Object>();
        return new LinkedHashMap<String, Object>((Map<String, Object>) source.get(key));
    }
    private Object valueOrDefault(Map<String, Object> source, String key, Object defaultValue) {
        return source == null || source.get(key) == null ? defaultValue : source.get(key);
    }
    private StationModelConfigException error(String message) { return new StationModelConfigException(message); }
}
