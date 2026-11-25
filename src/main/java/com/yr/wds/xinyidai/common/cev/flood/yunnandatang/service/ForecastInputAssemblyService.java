package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceDataProfileDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceSeriesDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.AggregationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.AggregationSourceDefinition;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.ForecastSourceDataRepository;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.GfsForecastRainValue;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 根据 XML 点号配置，从生产库组装三个模型页面可以编辑的输入数据。 */
@Service
@ConditionalOnYunnanDatangFeature
public class ForecastInputAssemblyService {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final StationModelRegistry registry;
    private final ForecastSourceDataRepository repository;
    private final ApiModelParameterService apiModelParameterService;

    public ForecastInputAssemblyService(StationModelRegistry registry,
                                        ForecastSourceDataRepository repository,
                                        ApiModelParameterService apiModelParameterService) {
        this.registry = registry;
        this.repository = repository;
        this.apiModelParameterService = apiModelParameterService;
    }

    public ForecastContracts.InputResponse assemble(ForecastContracts.InputQueryRequest request,
                                                     String expectedModule) {
        if (request == null) throw error("请求不能为空");
        StationModelRoute route = registry.resolveRoute(requireText(request.getStationCode(), "stationCode不能为空"));
        StationDefinition station = route.getStation();
        if (!expectedModule.equals(station.getModule())) {
            throw error("电站 " + station.getCode() + " 不属于 " + expectedModule);
        }

        SourceDataProfileDefinition profile = registry.getConfiguration().getSourceDataProfileMap()
                .get(station.getDataProfileRef());
        if (profile == null) throw error("电站 " + station.getCode() + " 没有数据点配置");
        boolean manualTank = "TANK_MODEL".equals(station.getModule());
        boolean api = "API_MODEL".equals(station.getModule());
        validateMapping(profile, manualTank || api);

        Calendar forecastTime = parseRequired(request.getForecastTime(), "forecastTime");
        if (api) configureApiWindow(request, forecastTime);
        TimeRange history = manualTank ? tankHistoryRange(request, forecastTime) : historyRange(request, station, forecastTime);
        TimeRange forecast = forecastRange(request, station, profile, forecastTime);
        List<String> sensorIds = new ArrayList<String>();
        for (SourceSeriesDefinition series : profile.getSeries()) sensorIds.add(series.getSensorId());
        String historyTimeType = "P1D".equals(station.getTimeStep()) ? "day" : "hour";
        List<DatangHourlyValue> rows = measuredRows(profile.getSeries(), history.begin, history.end, historyTimeType);
        List<DatangHourlyValue> averageRows = Collections.emptyList();
        if ("REGRESSION_MODEL".equals(station.getModule()) && forecast != null) {
            // 上年同期DAYDB行携带AVGV，可补齐未来10天尚未落DAYDB记录的多年均值。
            Calendar averageBegin = (Calendar) history.begin.clone();
            Calendar averageEnd = (Calendar) forecast.end.clone();
            averageBegin.add(Calendar.YEAR, -1);
            averageEnd.add(Calendar.YEAR, -1);
            averageRows = measuredRows(profile.getSeries(), averageBegin, averageEnd, historyTimeType);
        }

        ForecastContracts.InputResponse response = baseResponse(route, request, history, forecast);
        for (SourceSeriesDefinition definition : profile.getSeries()) {
            response.getSeries().add("REGRESSION_MODEL".equals(station.getModule())
                    ? toRegressionSeries(definition, rows, averageRows,
                    history.begin, forecast == null ? history.end : forecast.end, historyTimeType)
                    : manualTank ? tankHistorySeries(definition, rows, history) : toSeries(definition, "rainfall".equals(definition.getRole()) ? apiMeasuredRain(rows) : rows, station.getTimeStep(), historyTimeType));
        }
        if (manualTank) {
            ForecastContracts.RainfallResponse manual = new ForecastContracts.RainfallResponse();
            Calendar day = startOfDay(forecast.begin);
            while (!day.after(forecast.end)) {
                manual.getValues().add(point(day, null, "MANUAL"));
                day.add(Calendar.DAY_OF_MONTH, 1);
            }
            response.setForecastRainfall(manual);
        } else if (api || "REGRESSION_MODEL".equals(station.getModule())) {
            List<GfsForecastRainValue> rain = isBlank(profile.getForecastRegionId())
                    || profile.getForecastSubtype() == null || forecast.begin.after(forecast.end) ? Collections.emptyList()
                    : queryForecastRain(profile, forecastTime, forecast.begin, forecast.end, true);
            response.setForecastRainfall(apiForecastRain(profile, rain, forecast.begin, forecast.end, station.getTimeStep()));
        } else if (!isBlank(profile.getForecastTable())) {
            List<GfsForecastRainValue> rain = queryForecastRain(
                    profile, forecastTime, forecast.begin, forecast.end, false);
            response.setForecastRainfall(toForecastRain(profile, rain));
        }
        if ("API_MODEL".equals(station.getModule())) {
            response.setModelParameters(apiModelParameterService.load(station, response.getForecastTime()));
        }
        ForecastMissingValues.normalize(response, expectedModule);
        return response;
    }

    /**
     * 水箱页面首次计算时，根据 XML 汇总方案自动读取四个分区输入。
     * 前端只提交汇总电站和时间范围，不再逐个调用分区取数接口。
     */
    public List<ForecastContracts.InputResponse> assembleTankGroup(
            ForecastContracts.InputQueryRequest request) {
        if (request == null) throw error("请求不能为空");
        StationDefinition aggregateStation = aggregateTankStation();
        if (!isBlank(request.getStationCode())
                && !aggregateStation.getCode().equals(request.getStationCode().trim())) {
            throw error("水箱页面必须使用汇总电站 " + aggregateStation.getCode());
        }
        AggregationDefinition aggregation = registry.getConfiguration().getAggregationMap()
                .get(aggregateStation.getAggregationRef());
        if (aggregation == null) throw error("水箱汇总方案不存在: " + aggregateStation.getAggregationRef());

        List<ForecastContracts.InputResponse> result = new ArrayList<ForecastContracts.InputResponse>();
        for (AggregationSourceDefinition source : aggregation.getSources()) {
            if (!isBlank(source.getPoint())) continue;
            result.add(assemble(copyRequest(request, source.getStationCode()), "TANK_MODEL"));
        }
        if (result.isEmpty()) throw error("水箱汇总方案没有配置可计算分区");
        StationDefinition aggregate = aggregateTankStation();
        SourceDataProfileDefinition aggregateProfile = registry.getConfiguration().getSourceDataProfileMap()
                .get(aggregate.getDataProfileRef());
        if (aggregateProfile != null && !result.isEmpty()) {
            for (SourceSeriesDefinition definition : aggregateProfile.getSeries()) {
                if (!"shatuoOutflow".equals(definition.getRole()) && !"pengshuiInflow".equals(definition.getRole())) continue;
                Calendar begin = parseRequired(result.get(0).getHistoryBeginTime(), "historyBeginTime");
                Calendar end = parseRequired(result.get(0).getForecastEndTime(), "forecastEndTime");
                List<DatangHourlyValue> values = measuredRows(Collections.singletonList(definition), begin, end, "day");
                ForecastContracts.SeriesResponse measured = tankHistorySeries(definition, values, new TimeRange(begin, end));
                List<DatangHourlyValue> prior = repository.queryLatestDailyValueBefore(definition.getSensorId(), MeasuredTimeLabels.queryBegin(begin, "day"));
                if ("shatuoOutflow".equals(definition.getRole()) && prior != null && !prior.isEmpty()) {
                    if (prior.size() > 1) throw error("沙沱最新实测日流量重复");
                    DatangHourlyValue latest = prior.get(0);
                    Calendar businessTime = MeasuredTimeLabels.businessTime(latest.getTime(), "day", false);
                    if (businessTime != null) measured.getValues().add(0, point(businessTime, latest.getValue(), "DAYDB"));
                }
                result.get(0).getSeries().add(measured);
                if ("shatuoOutflow".equals(definition.getRole())) {
                    ForecastContracts.SeriesResponse plan = new ForecastContracts.SeriesResponse();
                    plan.setKey("shatuoPlannedOutflow");
                    List<ForecastContracts.ValuePoint> points = new ArrayList<>();
                    for (DatangHourlyValue value : repository.queryShatuoDailyPlan(definition.getSensorId(), end)) {
                        points.add(point(value.getTime(), value.getValue(), "PLANLOAD"));
                    }
                    plan.setValues(points);
                    result.get(0).getSeries().add(plan);
                    // 计划仅在后台参与默认值合并，对旧页面仍返回 shatuoOutflow。
                    measured.setValues(ForecastCalculationService.tankResultOutflow(result.get(0), null));
                    result.get(0).getSeries().remove(plan);
                }
            }
        }
        for (ForecastContracts.InputResponse input : result) ForecastMissingValues.normalize(input, "TANK_MODEL");
        return result;
    }

    /** 页面缺日补零，并在快照保留补值来源。 */
    public List<ForecastContracts.InputResponse> assembleTankGroupWithForecast(
            ForecastContracts.InputQueryRequest request) {
        List<ForecastContracts.InputResponse> inputs = assembleTankGroup(request);
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
        Calendar issue = parseRequired(request.getForecastTime(), "forecastTime");
        for (ForecastContracts.InputResponse input : inputs) {
            StationDefinition station = registry.resolveRoute(input.getStationCode()).getStation();
            SourceDataProfileDefinition profile = registry.getConfiguration().getSourceDataProfileMap()
                    .get(station.getDataProfileRef());
            if (isBlank(profile.getForecastRegionId()) || profile.getForecastSubtype() == null)
                throw error("水箱缺少gfs配置: " + station.getCode());
            Calendar begin = parseRequired(input.getForecastBeginTime(), "forecastBeginTime");
            Calendar end = parseRequired(input.getForecastEndTime(), "forecastEndTime");
            List<GfsForecastRainValue> rain = repository.queryLatestGfsRain(profile.getForecastRegionId(),
                    profile.getForecastSubtype(), issue, begin, end);
            input.setForecastRainfall(TankDailyForecastService.dailyRain(rain, profile.getForecastRegionId(),
                    profile.getForecastSubtype(), begin.toInstant().atZone(zone).toLocalDate(),
                    end.toInstant().atZone(zone).toLocalDate(), true));
            ForecastMissingValues.normalize(input, "TANK_MODEL");
        }
        return inputs;
    }

    /** 只在数据库读取边界转换一次；手工输入、计算结果、保存回读均已是业务时标。 */
    private List<DatangHourlyValue> measuredRows(List<SourceSeriesDefinition> definitions,
            Calendar begin, Calendar end, String timeType) {
        if (begin.after(end)) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        Map<String, Boolean> rainfall = new LinkedHashMap<>();
        for (SourceSeriesDefinition d : definitions) {
            ids.add(d.getSensorId()); rainfall.put(d.getSensorId(), "rainfall".equals(d.getRole()));
        }
        List<DatangHourlyValue> raw = repository.queryHistoryValues(ids,
                MeasuredTimeLabels.queryBegin(begin, timeType), MeasuredTimeLabels.queryEnd(end, timeType), timeType);
        return MeasuredTimeLabels.normalize(raw, rainfall, begin, end, timeType);
    }

    private StationDefinition aggregateTankStation() {
        StationDefinition found = null;
        for (StationDefinition station : registry.getConfiguration().getStations()) {
            if (!station.isEnabled() || !"TANK_MODEL".equals(station.getModule())
                    || isBlank(station.getAggregationRef())) continue;
            if (found != null) throw error("当前只支持一个水箱汇总电站");
            found = station;
        }
        if (found == null) throw error("XML未配置水箱汇总电站");
        return found;
    }

    private ForecastContracts.InputQueryRequest copyRequest(
            ForecastContracts.InputQueryRequest source, String stationCode) {
        ForecastContracts.InputQueryRequest target = new ForecastContracts.InputQueryRequest();
        target.setStationCode(stationCode);
        target.setForecastTime(source.getForecastTime());
        target.setHistoryBeginTime(source.getHistoryBeginTime());
        target.setHistoryEndTime(source.getHistoryEndTime());
        target.setForecastBeginTime(source.getForecastBeginTime());
        target.setForecastEndTime(source.getForecastEndTime());
        return target;
    }

    private ForecastContracts.InputResponse baseResponse(StationModelRoute route,
                                                         ForecastContracts.InputQueryRequest request,
                                                         TimeRange history,
                                                         TimeRange forecast) {
        StationDefinition station = route.getStation();
        ForecastContracts.InputResponse response = new ForecastContracts.InputResponse();
        response.setStationCode(station.getCode());
        response.setStationName(station.getName());
        response.setModule(station.getModule());
        response.setModelCode(route.getModel().getCode());
        response.setParameterProfile(station.getParameterProfileRef());
        response.setConfigVersion(registry.getConfiguration().getConfigVersion());
        response.setForecastTime(format(parseRequired(request.getForecastTime(), "forecastTime")));
        response.setHistoryBeginTime(format(history.begin));
        response.setHistoryEndTime(format(history.end));
        if (forecast != null) {
            response.setForecastBeginTime(format(forecast.begin));
            response.setForecastEndTime(format(forecast.end));
        }
        return response;
    }

    // 从预报年份的前一年12月1日开始连续递推，展示窗口不能参与模型取数。
    private TimeRange tankHistoryRange(ForecastContracts.InputQueryRequest request, Calendar forecastTime) {
        Calendar begin = startOfDay(forecastTime);
        Calendar forecastBegin = isBlank(request.getForecastBeginTime()) ? begin
                : startOfDay(parseRequired(request.getForecastBeginTime(), "forecastBeginTime"));
        if (!begin.equals(forecastBegin)) throw error("水箱预报起点必须与forecastTime为同一天");
        Calendar end = (Calendar) begin.clone();
        end.add(Calendar.DAY_OF_MONTH, -1);
        begin.set(forecastBegin.get(Calendar.YEAR) - 1, Calendar.DECEMBER, 1);
        return new TimeRange(begin, endOfDay(end), true);
    }

    private ForecastContracts.SeriesResponse tankHistorySeries(SourceSeriesDefinition definition,
            List<DatangHourlyValue> rows, TimeRange history) {
        ForecastContracts.SeriesResponse series = toSeries(definition, Collections.emptyList(), "P1D", "day");
        Map<String, DatangHourlyValue> days = new LinkedHashMap<>();
        for (DatangHourlyValue row : rows) {
            if (!definition.getSensorId().equals(row.getSenId()) || row.getTime() == null
                    || row.getTime().before(history.begin) || row.getTime().after(history.end)) continue;
            // measuredRows已将实测后时标转换为业务日期，日数据统一为业务日零点。
            Calendar dayStart = startOfDay(row.getTime());
            if (row.getTime().getTimeInMillis() != dayStart.getTimeInMillis()) continue;
            String day = format(dayStart);
            if (days.put(day, row) != null) throw error("历史日数据重复: " + definition.getName() + "/" + day);
        }
        for (Calendar day = (Calendar) history.begin.clone(); !day.after(history.end); day.add(Calendar.DAY_OF_MONTH, 1)) {
            DatangHourlyValue row = days.get(format(day));
            // 保留数据库原值；后续统一补零并标记 DEFAULT_ZERO。
            series.getValues().add(point(day, row == null ? null : ("rainfall".equals(definition.getRole()) ? row.getAverageValue() : row.getValue()), "DAYDB"));
        }
        return series;
    }

    /** API实测雨量只使用AVGV；复制记录，避免改变其他模型共享的查询结果。 */
    static List<DatangHourlyValue> apiMeasuredRain(List<DatangHourlyValue> rows) {
        List<DatangHourlyValue> selected = new ArrayList<>();
        for (DatangHourlyValue row : rows) {
            DatangHourlyValue copy = new DatangHourlyValue();
            copy.setSenId(row.getSenId()); copy.setTime(row.getTime());
            copy.setValue(row.getAverageValue());
            copy.setAverageValue(row.getAverageValue());
            selected.add(copy);
        }
        return selected;
    }

    private ForecastContracts.SeriesResponse toSeries(SourceSeriesDefinition definition,
                                                       List<DatangHourlyValue> allRows,
                                                       String timeStep,
                                                       String historyTimeType) {
        ForecastContracts.SeriesResponse response = new ForecastContracts.SeriesResponse();
        response.setKey(definition.getRole());
        response.setName(definition.getName());
        response.setSenid(definition.getSensorId());
        response.setUnit(definition.getUnit());
        response.setWeight(definition.getWeight());
        List<DatangHourlyValue> rows = new ArrayList<DatangHourlyValue>();
        for (DatangHourlyValue row : allRows) {
            if (definition.getSensorId().equals(row.getSenId())) rows.add(row);
        }
        rows.sort(Comparator.comparing(DatangHourlyValue::getTime));
        String source = "day".equals(historyTimeType) ? "DAYDB" : "HOURDB";
        if ("P1D".equals(timeStep)) appendDailyValues(
                response, rows, "rainfall".equals(definition.getRole()), source);
        else for (DatangHourlyValue row : rows) {
            response.getValues().add(point(row.getTime(), preferredValue(row), source));
        }
        return response;
    }

    /** 回归页面统一返回历史7天、未来10天和DAYDB.AVGV多年均值。 */
    private ForecastContracts.SeriesResponse toRegressionSeries(SourceSeriesDefinition definition,
                                                                  List<DatangHourlyValue> currentRows,
                                                                  List<DatangHourlyValue> previousYearRows,
                                                                  Calendar beginTime,
                                                                  Calendar endTime,
                                                                  String historyTimeType) {
        ForecastContracts.SeriesResponse response = new ForecastContracts.SeriesResponse();
        response.setKey(definition.getRole());
        response.setName(definition.getName());
        response.setSenid(definition.getSensorId());
        response.setUnit(definition.getUnit());
        response.setWeight(definition.getWeight());
        Map<String, List<DatangHourlyValue>> currentByDay = rowsByDay(currentRows, definition.getSensorId(), false);
        Map<String, List<DatangHourlyValue>> previousByDay = rowsByDay(previousYearRows, definition.getSensorId(), true);
        boolean rainfall = "rainfall".equals(definition.getRole());
        Calendar day = startOfDay(beginTime);
        Calendar end = startOfDay(endTime);
        while (!day.after(end)) {
            String fullDay = new SimpleDateFormat("yyyy-MM-dd").format(day.getTime());
            String monthDay = new SimpleDateFormat("MM-dd").format(day.getTime());
            List<DatangHourlyValue> todayRows = currentByDay.get(fullDay);
            Double actualValue = aggregateRows(todayRows, rainfall, false);
            Double averageValue = aggregateRows(todayRows, rainfall, true);
            if (averageValue == null) averageValue = aggregateRows(previousByDay.get(monthDay), rainfall, true);
            ForecastContracts.ValuePoint valuePoint = point(day, actualValue,
                    "day".equals(historyTimeType) ? "DAYDB" : "HOURDB");
            valuePoint.setAverageValue(averageValue);
            response.getValues().add(valuePoint);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return response;
    }

    private Map<String, List<DatangHourlyValue>> rowsByDay(List<DatangHourlyValue> rows,
                                                            String sensorId,
                                                            boolean monthDayOnly) {
        Map<String, List<DatangHourlyValue>> grouped = new LinkedHashMap<String, List<DatangHourlyValue>>();
        SimpleDateFormat format = new SimpleDateFormat(monthDayOnly ? "MM-dd" : "yyyy-MM-dd");
        for (DatangHourlyValue row : rows) {
            if (!sensorId.equals(row.getSenId()) || row.getTime() == null) continue;
            String key = format.format(row.getTime().getTime());
            List<DatangHourlyValue> values = grouped.get(key);
            if (values == null) {
                values = new ArrayList<DatangHourlyValue>();
                grouped.put(key, values);
            }
            values.add(row);
        }
        return grouped;
    }

    private Double aggregateRows(List<DatangHourlyValue> rows, boolean rainfall, boolean average) {
        if (rows == null || rows.isEmpty()) return null;
        double total = 0D;
        int count = 0;
        for (DatangHourlyValue row : rows) {
            Double value = average ? row.getAverageValue() : row.getValue();
            if (value == null) continue;
            total += value;
            count++;
        }
        if (count == 0) return null;
        return rainfall ? total : total / count;
    }

    /** 日尺度降雨求和，流量和水位取日平均；聚合后的值仍允许页面人工修改。 */
    private void appendDailyValues(ForecastContracts.SeriesResponse response,
                                   List<DatangHourlyValue> rows,
                                   boolean rainfall,
                                   String source) {
        Map<String, List<Double>> grouped = new LinkedHashMap<String, List<Double>>();
        for (DatangHourlyValue row : rows) {
            Double value = preferredValue(row);
            if (row.getTime() == null || value == null) continue;
            String day = new SimpleDateFormat("yyyy-MM-dd").format(row.getTime().getTime());
            List<Double> values = grouped.get(day);
            if (values == null) {
                values = new ArrayList<Double>();
                grouped.put(day, values);
            }
            values.add(value);
        }
        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            double total = 0D;
            for (Double value : entry.getValue()) total += value;
            double value = rainfall ? total : total / entry.getValue().size();
            response.getValues().add(point(parse(entry.getKey() + " 00:00:00", "day"), value, source));
        }
    }

    private ForecastContracts.RainfallResponse toForecastRain(SourceDataProfileDefinition profile,
                                                               List<GfsForecastRainValue> rows) {
        ForecastContracts.RainfallResponse response = new ForecastContracts.RainfallResponse();
        response.setRegionId(profile.getForecastRegionId());
        response.setSubtype(profile.getForecastSubtype());
        for (GfsForecastRainValue row : rows) {
            response.getValues().add(point(row.getDataTime(), row.getAveragePrecipitation(),
                    "WEA_GFSFORRAIN.AVERPRE"));
        }
        return response;
    }

    /** API预报时段无数据时补零，返回完整可编辑时间序列。 */
    ForecastContracts.RainfallResponse apiForecastRain(SourceDataProfileDefinition profile,
            List<GfsForecastRainValue> rows, Calendar begin, Calendar end, String timeStep) {
        ForecastContracts.RainfallResponse response = new ForecastContracts.RainfallResponse();
        response.setRegionId(profile.getForecastRegionId());
        response.setSubtype(profile.getForecastSubtype());
        Map<Long, Double> values = new LinkedHashMap<>();
        for (GfsForecastRainValue row : rows) {
            Double rain = row.getAveragePrecipitation();
            if (row.getDataTime() != null && rain != null && Double.isFinite(rain) && rain >= 0D)
                values.put(row.getDataTime().getTimeInMillis(), rain);
        }
        Calendar time = copy(begin);
        while (!time.after(end)) {
            Double rain = values.get(time.getTimeInMillis());
            response.getValues().add(point(time, rain == null ? 0D : rain,
                    rain == null ? "DEFAULT_ZERO" : "WEA_GFSFORRAIN.AVERPRE"));
            time.add("P1D".equals(timeStep) ? Calendar.DAY_OF_MONTH : Calendar.HOUR_OF_DAY, 1);
        }
        return response;
    }

    /** 多个生产GFS子区按XML配置的面积权重合成为页面所需的一条区域平均降雨过程。 */
    private List<GfsForecastRainValue> queryForecastRain(SourceDataProfileDefinition profile,
                                                           Calendar issueTime,
                                                           Calendar beginTime,
                                                           Calendar endTime, boolean allowMissing) {
        String[] regionIds = splitRequired(profile.getForecastRegionId(), "gfs.regionId");
        double[] weights = regionWeights(profile.getForecastRegionWeight(), regionIds.length);
        double configuredWeight = 0D;
        for (double weight : weights) configuredWeight += weight;
        Map<Long, GfsForecastRainValue> templates = new LinkedHashMap<Long, GfsForecastRainValue>();
        Map<Long, Double> weightedValues = new LinkedHashMap<Long, Double>();
        Map<Long, Double> totalWeights = new LinkedHashMap<Long, Double>();
        for (int i = 0; i < regionIds.length; i++) {
            List<GfsForecastRainValue> rows = repository.queryLatestGfsRain(
                    regionIds[i], profile.getForecastSubtype(), issueTime, beginTime, endTime);
            if (rows == null || rows.isEmpty()) {
                if (allowMissing) continue;
                throw error("GFS子区没有预报数据: " + regionIds[i]);
            }
            for (GfsForecastRainValue row : rows) {
                if (row.getDataTime() == null || row.getAveragePrecipitation() == null) continue;
                if (allowMissing && (!Double.isFinite(row.getAveragePrecipitation()) || row.getAveragePrecipitation() < 0D)) continue;
                long key = row.getDataTime().getTimeInMillis();
                templates.put(key, row);
                weightedValues.put(key, value(weightedValues, key) + row.getAveragePrecipitation() * weights[i]);
                totalWeights.put(key, value(totalWeights, key) + weights[i]);
            }
        }
        List<Long> times = new ArrayList<Long>(templates.keySet());
        Collections.sort(times);
        List<GfsForecastRainValue> result = new ArrayList<GfsForecastRainValue>();
        for (Long time : times) {
            GfsForecastRainValue row = templates.get(time);
            row.setRegionId(profile.getForecastRegionId());
            row.setAveragePrecipitation(weightedValues.get(time) / (allowMissing ? configuredWeight : totalWeights.get(time)));
            result.add(row);
        }
        return result;
    }

    private String[] splitRequired(String value, String name) {
        if (isBlank(value)) throw error(name + "不能为空");
        String[] values = value.split(",");
        for (int i = 0; i < values.length; i++) values[i] = values[i].trim();
        return values;
    }

    private double[] regionWeights(String configured, int size) {
        double[] result = new double[size];
        if (isBlank(configured)) {
            for (int i = 0; i < size; i++) result[i] = 1D;
            return result;
        }
        String[] values = configured.split(",");
        if (values.length != size) throw error("gfs.regionWeight数量必须与regionId一致");
        for (int i = 0; i < size; i++) {
            try {
                result[i] = Double.parseDouble(values[i].trim());
            } catch (NumberFormatException ex) {
                throw error("gfs.regionWeight不是数字: " + values[i]);
            }
            if (result[i] <= 0D) throw error("gfs.regionWeight必须大于0");
        }
        return result;
    }

    private double value(Map<Long, Double> values, Long key) {
        Double value = values.get(key);
        return value == null ? 0D : value;
    }

    private ForecastContracts.ValuePoint point(Calendar time, Double value, String source) {
        ForecastContracts.ValuePoint point = new ForecastContracts.ValuePoint();
        point.setTime(format(time));
        point.setValue(value);
        // 页面首次加载时记录数据库原值，后续人工修改只覆盖 value。
        point.setOriginalValue(value);
        point.setSource(source);
        point.setEditable(true);
        point.setManuallyModified(false);
        return point;
    }

    private void configureApiWindow(ForecastContracts.InputQueryRequest request, Calendar issue) {
        Calendar begin = parseRequired(isBlank(request.getHistoryBeginTime())
                ? request.getForecastBeginTime() : request.getHistoryBeginTime(), "historyBeginTime");
        Calendar end = parseRequired(request.getForecastEndTime(), "forecastEndTime");
        for (Calendar time : Arrays.asList(begin, end, issue)) {
            if (time.get(Calendar.MINUTE) != 0 || time.get(Calendar.SECOND) != 0)
                throw error("API计算时间须为整点");
        }
        Calendar limit = copy(begin); limit.add(Calendar.DAY_OF_YEAR, 366);
        if (begin.after(end) || end.after(limit)) throw error("API计算日期范围无效，最多366天");
        Calendar afterEnd = copy(end); afterEnd.add(Calendar.HOUR_OF_DAY, 1);
        Calendar split = issue.before(begin) ? copy(begin) : issue.after(afterEnd) ? afterEnd : copy(issue);
        Calendar historyEnd = copy(split); historyEnd.add(Calendar.HOUR_OF_DAY, -1);
        request.setHistoryBeginTime(format(begin));
        request.setHistoryEndTime(format(historyEnd));
        request.setForecastBeginTime(format(split));
    }

    private TimeRange historyRange(ForecastContracts.InputQueryRequest request,
                                   StationDefinition station,
                                   Calendar forecastTime) {
        if ("API_MODEL".equals(station.getModule())) {
            Calendar end = parseRequired(request.getForecastBeginTime(), "forecastBeginTime");
            Calendar begin = isBlank(request.getHistoryBeginTime()) ? copy(end)
                    : parseRequired(request.getHistoryBeginTime(), "historyBeginTime");
            end.add(Calendar.HOUR_OF_DAY, -1);
            // 仅计算用户选择的时段；全未来时段允许没有历史小时。
            return new TimeRange(begin, end, true);
        }
        if ("REGRESSION_MODEL".equals(station.getModule())
                && isBlank(request.getHistoryBeginTime()) && isBlank(request.getHistoryEndTime())) {
            Calendar end = copy(forecastTime);
            end.add(Calendar.DAY_OF_MONTH, -1);
            Calendar begin = copy(end);
            begin.add(Calendar.DAY_OF_MONTH, -(station.getHistorySteps() - 1));
            return new TimeRange(startOfDay(begin), endOfDay(end));
        }
        Calendar begin = parseRequired(request.getHistoryBeginTime(), "historyBeginTime");
        Calendar end = parseRequired(request.getHistoryEndTime(), "historyEndTime");
        return "P1D".equals(station.getTimeStep())
                ? new TimeRange(startOfDay(begin), endOfDay(end)) : new TimeRange(begin, end);
    }

    private TimeRange forecastRange(ForecastContracts.InputQueryRequest request,
                                    StationDefinition station,
                                    SourceDataProfileDefinition profile,
                                    Calendar forecastTime) {
        if (isBlank(profile.getForecastTable()) && !"TANK_MODEL".equals(station.getModule())
                && !"API_MODEL".equals(station.getModule()) && !"REGRESSION_MODEL".equals(station.getModule())) return null;
        if ("REGRESSION_MODEL".equals(station.getModule())
                && isBlank(request.getForecastBeginTime()) && isBlank(request.getForecastEndTime())) {
            Calendar begin = copy(forecastTime);
            Calendar end = copy(begin);
            end.add(Calendar.DAY_OF_MONTH, station.getForecastSteps() - 1);
            return new TimeRange(startOfDay(begin), endOfDay(end));
        }
        Calendar begin = parseRequired(request.getForecastBeginTime(), "forecastBeginTime");
        Calendar end = parseRequired(request.getForecastEndTime(), "forecastEndTime");
        if ("API_MODEL".equals(station.getModule())) return new TimeRange(begin, end, true);
        return "P1D".equals(station.getTimeStep())
                ? new TimeRange(startOfDay(begin), endOfDay(end)) : new TimeRange(begin, end);
    }

    private void validateMapping(SourceDataProfileDefinition profile, boolean manualTank) {
        List<String> missing = new ArrayList<String>();
        for (SourceSeriesDefinition series : profile.getSeries()) {
            if (isBlank(series.getSensorId())) missing.add(series.getName() + ".senid");
        }
        if (!manualTank && !isBlank(profile.getForecastTable())) {
            if (isBlank(profile.getForecastRegionId())) missing.add("gfs.regionId");
            if (profile.getForecastSubtype() == null) missing.add("gfs.subtype");
        }
        if (!missing.isEmpty()) throw error("电站点号配置未完成: " + String.join(", ", missing));
    }

    private Double preferredValue(DatangHourlyValue row) {
        return row.getValue() == null ? row.getAverageValue() : row.getValue();
    }

    private Calendar parseRequired(String value, String field) {
        return parse(requireText(value, field + "不能为空"), field);
    }

    private Calendar parse(String value, String field) {
        try {
            SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
            format.setLenient(false);
            Calendar result = Calendar.getInstance();
            result.setTime(format.parse(value));
            return result;
        } catch (ParseException ex) {
            throw error(field + "格式必须是 " + DATE_PATTERN);
        }
    }

    private String format(Calendar value) {
        return value == null ? null : new SimpleDateFormat(DATE_PATTERN).format(value.getTime());
    }

    private Calendar copy(Calendar value) { return (Calendar) value.clone(); }
    private Calendar startOfDay(Calendar value) {
        Calendar result = copy(value);
        result.set(Calendar.HOUR_OF_DAY, 0);
        result.set(Calendar.MINUTE, 0);
        result.set(Calendar.SECOND, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }
    private Calendar endOfDay(Calendar value) {
        Calendar result = copy(value);
        result.set(Calendar.HOUR_OF_DAY, 23);
        result.set(Calendar.MINUTE, 59);
        result.set(Calendar.SECOND, 59);
        result.set(Calendar.MILLISECOND, 999);
        return result;
    }
    private String requireText(String value, String message) {
        if (isBlank(value)) throw error(message);
        return value.trim();
    }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private StationModelConfigException error(String message) { return new StationModelConfigException(message); }

    private static class TimeRange {
        private final Calendar begin;
        private final Calendar end;

        private TimeRange(Calendar begin, Calendar end) {
            this(begin, end, false);
        }

        private TimeRange(Calendar begin, Calendar end, boolean allowEmpty) {
            if (!allowEmpty && begin.after(end)) throw new StationModelConfigException("开始时间不能晚于结束时间");
            this.begin = begin;
            this.end = end;
        }
    }
}
