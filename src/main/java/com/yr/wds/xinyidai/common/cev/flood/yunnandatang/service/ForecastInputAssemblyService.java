package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 根据 XML 点号配置，从生产库组装三个模型页面可以编辑的输入数据。 */
@Service
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
        validateMapping(profile);

        Calendar forecastTime = parseRequired(request.getForecastTime(), "forecastTime");
        TimeRange history = historyRange(request, station, forecastTime);
        TimeRange forecast = forecastRange(request, station, profile, forecastTime);
        List<String> sensorIds = new ArrayList<String>();
        for (SourceSeriesDefinition series : profile.getSeries()) sensorIds.add(series.getSensorId());
        String historyTimeType = "P1D".equals(station.getTimeStep()) ? "day" : "hour";
        List<DatangHourlyValue> rows = repository.queryHistoryValues(
                sensorIds, history.begin, history.end, historyTimeType);
        List<DatangHourlyValue> averageRows = Collections.emptyList();
        if ("REGRESSION_MODEL".equals(station.getModule()) && forecast != null) {
            // 上年同期DAYDB行携带AVGV，可补齐未来10天尚未落DAYDB记录的多年均值。
            Calendar averageBegin = (Calendar) history.begin.clone();
            Calendar averageEnd = (Calendar) forecast.end.clone();
            averageBegin.add(Calendar.YEAR, -1);
            averageEnd.add(Calendar.YEAR, -1);
            averageRows = repository.queryHistoryValues(
                    sensorIds, averageBegin, averageEnd, historyTimeType);
        }

        ForecastContracts.InputResponse response = baseResponse(route, request, history, forecast);
        for (SourceSeriesDefinition definition : profile.getSeries()) {
            response.getSeries().add("REGRESSION_MODEL".equals(station.getModule())
                    ? toRegressionSeries(definition, rows, averageRows,
                    history.begin, forecast == null ? history.end : forecast.end, historyTimeType)
                    : toSeries(definition, rows, station.getTimeStep(), historyTimeType));
        }
        if (!isBlank(profile.getForecastTable())) {
            List<GfsForecastRainValue> rain = repository.queryLatestGfsRain(
                    profile.getForecastRegionId(), profile.getForecastSubtype(), forecastTime,
                    forecast.begin, forecast.end);
            response.setForecastRainfall(toForecastRain(profile, rain));
        }
        if ("API_MODEL".equals(station.getModule())) {
            response.setModelParameters(apiModelParameterService.load(station, response.getForecastTime()));
        }
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
        return result;
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

    private TimeRange historyRange(ForecastContracts.InputQueryRequest request,
                                   StationDefinition station,
                                   Calendar forecastTime) {
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
        if (isBlank(profile.getForecastTable())) return null;
        if ("REGRESSION_MODEL".equals(station.getModule())
                && isBlank(request.getForecastBeginTime()) && isBlank(request.getForecastEndTime())) {
            Calendar begin = copy(forecastTime);
            Calendar end = copy(begin);
            end.add(Calendar.DAY_OF_MONTH, station.getForecastSteps() - 1);
            return new TimeRange(startOfDay(begin), endOfDay(end));
        }
        Calendar begin = parseRequired(request.getForecastBeginTime(), "forecastBeginTime");
        Calendar end = parseRequired(request.getForecastEndTime(), "forecastEndTime");
        return "P1D".equals(station.getTimeStep())
                ? new TimeRange(startOfDay(begin), endOfDay(end)) : new TimeRange(begin, end);
    }

    private void validateMapping(SourceDataProfileDefinition profile) {
        List<String> missing = new ArrayList<String>();
        for (SourceSeriesDefinition series : profile.getSeries()) {
            if (isBlank(series.getSensorId())) missing.add(series.getName() + ".senid");
        }
        if (!isBlank(profile.getForecastTable())) {
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
            if (begin.after(end)) throw new StationModelConfigException("开始时间不能晚于结束时间");
            this.begin = begin;
            this.end = end;
        }
    }
}
