package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云南大唐前后端接口契约。
 *
 * <p>这些字段与新工程保持一致，前端切换后端地址时不需要修改请求体和响应解析。</p>
 */
public final class ForecastContracts {
    private ForecastContracts() { }

    public static class InputQueryRequest {
        private String stationCode;
        private String forecastTime;
        private String historyBeginTime;
        private String historyEndTime;
        private String forecastBeginTime;
        private String forecastEndTime;

        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public String getForecastTime() { return forecastTime; }
        public void setForecastTime(String forecastTime) { this.forecastTime = forecastTime; }
        public String getHistoryBeginTime() { return historyBeginTime; }
        public void setHistoryBeginTime(String historyBeginTime) { this.historyBeginTime = historyBeginTime; }
        public String getHistoryEndTime() { return historyEndTime; }
        public void setHistoryEndTime(String historyEndTime) { this.historyEndTime = historyEndTime; }
        public String getForecastBeginTime() { return forecastBeginTime; }
        public void setForecastBeginTime(String forecastBeginTime) { this.forecastBeginTime = forecastBeginTime; }
        public String getForecastEndTime() { return forecastEndTime; }
        public void setForecastEndTime(String forecastEndTime) { this.forecastEndTime = forecastEndTime; }
    }

    public static class ValuePoint {
        private String time;
        private Double value;
        /** DAYDB.AVGV多年同期均值，仅用于回归页面多年均值列。 */
        private Double averageValue;
        private Double originalValue;
        private String source;
        private boolean editable;
        private boolean manuallyModified;

        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }
        public Double getAverageValue() { return averageValue; }
        public void setAverageValue(Double averageValue) { this.averageValue = averageValue; }
        public Double getOriginalValue() { return originalValue; }
        public void setOriginalValue(Double originalValue) { this.originalValue = originalValue; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public boolean isEditable() { return editable; }
        public void setEditable(boolean editable) { this.editable = editable; }
        public boolean isManuallyModified() { return manuallyModified; }
        public void setManuallyModified(boolean manuallyModified) { this.manuallyModified = manuallyModified; }
    }

    public static class SeriesResponse {
        private String key;
        private String name;
        private String senid;
        private String unit;
        private Double weight;
        private List<ValuePoint> values = new ArrayList<ValuePoint>();

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSenid() { return senid; }
        public void setSenid(String senid) { this.senid = senid; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Double getWeight() { return weight; }
        public void setWeight(Double weight) { this.weight = weight; }
        public List<ValuePoint> getValues() { return values; }
        public void setValues(List<ValuePoint> values) { this.values = values; }
    }

    public static class RainfallResponse {
        private String regionId;
        private Integer subtype;
        private String valueField = "AVERPRE";
        private List<ValuePoint> values = new ArrayList<ValuePoint>();

        public String getRegionId() { return regionId; }
        public void setRegionId(String regionId) { this.regionId = regionId; }
        public Integer getSubtype() { return subtype; }
        public void setSubtype(Integer subtype) { this.subtype = subtype; }
        public String getValueField() { return valueField; }
        public void setValueField(String valueField) { this.valueField = valueField; }
        public List<ValuePoint> getValues() { return values; }
        public void setValues(List<ValuePoint> values) { this.values = values; }
    }

    public static class InputResponse {
        private String stationCode;
        private String stationName;
        private String module;
        private String modelCode;
        private String parameterProfile;
        private String configVersion;
        private String forecastTime;
        private String historyBeginTime;
        private String historyEndTime;
        private String forecastBeginTime;
        private String forecastEndTime;
        private List<SeriesResponse> series = new ArrayList<SeriesResponse>();
        private RainfallResponse forecastRainfall;
        private Map<String, Object> modelParameters = new LinkedHashMap<String, Object>();

        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public String getStationName() { return stationName; }
        public void setStationName(String stationName) { this.stationName = stationName; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getModelCode() { return modelCode; }
        public void setModelCode(String modelCode) { this.modelCode = modelCode; }
        public String getParameterProfile() { return parameterProfile; }
        public void setParameterProfile(String parameterProfile) { this.parameterProfile = parameterProfile; }
        public String getConfigVersion() { return configVersion; }
        public void setConfigVersion(String configVersion) { this.configVersion = configVersion; }
        public String getForecastTime() { return forecastTime; }
        public void setForecastTime(String forecastTime) { this.forecastTime = forecastTime; }
        public String getHistoryBeginTime() { return historyBeginTime; }
        public void setHistoryBeginTime(String historyBeginTime) { this.historyBeginTime = historyBeginTime; }
        public String getHistoryEndTime() { return historyEndTime; }
        public void setHistoryEndTime(String historyEndTime) { this.historyEndTime = historyEndTime; }
        public String getForecastBeginTime() { return forecastBeginTime; }
        public void setForecastBeginTime(String forecastBeginTime) { this.forecastBeginTime = forecastBeginTime; }
        public String getForecastEndTime() { return forecastEndTime; }
        public void setForecastEndTime(String forecastEndTime) { this.forecastEndTime = forecastEndTime; }
        public List<SeriesResponse> getSeries() { return series; }
        public void setSeries(List<SeriesResponse> series) { this.series = series; }
        public RainfallResponse getForecastRainfall() { return forecastRainfall; }
        public void setForecastRainfall(RainfallResponse forecastRainfall) { this.forecastRainfall = forecastRainfall; }
        /** API模型从曲线库自动装配的参数。 */
        public Map<String, Object> getModelParameters() { return modelParameters; }
        public void setModelParameters(Map<String, Object> modelParameters) { this.modelParameters = modelParameters; }
    }

    public static class CalculateRequest {
        /** 首次进入页面时使用；后台据此自动查库并组装模型输入。 */
        private String stationCode;
        private String forecastTime;
        private String historyBeginTime;
        private String historyEndTime;
        private String forecastBeginTime;
        private String forecastEndTime;
        private InputResponse input;
        private Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        private List<InputResponse> inputs = new ArrayList<InputResponse>();
        private Map<String, Map<String, Object>> stationParameters =
                new LinkedHashMap<String, Map<String, Object>>();
        private List<ValuePoint> shatuoOutflowSeries = new ArrayList<ValuePoint>();

        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public String getForecastTime() { return forecastTime; }
        public void setForecastTime(String forecastTime) { this.forecastTime = forecastTime; }
        public String getHistoryBeginTime() { return historyBeginTime; }
        public void setHistoryBeginTime(String historyBeginTime) { this.historyBeginTime = historyBeginTime; }
        public String getHistoryEndTime() { return historyEndTime; }
        public void setHistoryEndTime(String historyEndTime) { this.historyEndTime = historyEndTime; }
        public String getForecastBeginTime() { return forecastBeginTime; }
        public void setForecastBeginTime(String forecastBeginTime) { this.forecastBeginTime = forecastBeginTime; }
        public String getForecastEndTime() { return forecastEndTime; }
        public void setForecastEndTime(String forecastEndTime) { this.forecastEndTime = forecastEndTime; }

        public InputResponse getInput() { return input; }
        public void setInput(InputResponse input) { this.input = input; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        /** 水箱完整计算提交四个分区输入；为空时保持原有单分区语义。 */
        public List<InputResponse> getInputs() { return inputs; }
        public void setInputs(List<InputResponse> inputs) { this.inputs = inputs; }
        /** 按分区覆盖本次初始状态，key 为分区 stationCode。 */
        public Map<String, Map<String, Object>> getStationParameters() { return stationParameters; }
        public void setStationParameters(Map<String, Map<String, Object>> stationParameters) {
            this.stationParameters = stationParameters;
        }
        /** 提供沙沱出库后计算彭水入库；为空时只返回四区流量合计。 */
        public List<ValuePoint> getShatuoOutflowSeries() { return shatuoOutflowSeries; }
        public void setShatuoOutflowSeries(List<ValuePoint> shatuoOutflowSeries) {
            this.shatuoOutflowSeries = shatuoOutflowSeries;
        }
    }

    public static class CalculateResponse {
        private String calculationId;
        private String stationCode;
        private String modelCode;
        private boolean saved;
        private InputResponse inputData;
        private List<InputResponse> inputDataList = new ArrayList<InputResponse>();
        private Map<String, Object> resultData;

        public String getCalculationId() { return calculationId; }
        public void setCalculationId(String calculationId) { this.calculationId = calculationId; }
        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public String getModelCode() { return modelCode; }
        public void setModelCode(String modelCode) { this.modelCode = modelCode; }
        public boolean isSaved() { return saved; }
        public void setSaved(boolean saved) { this.saved = saved; }
        public InputResponse getInputData() { return inputData; }
        public void setInputData(InputResponse inputData) { this.inputData = inputData; }
        public List<InputResponse> getInputDataList() { return inputDataList; }
        public void setInputDataList(List<InputResponse> inputDataList) { this.inputDataList = inputDataList; }
        public Map<String, Object> getResultData() { return resultData; }
        public void setResultData(Map<String, Object> resultData) { this.resultData = resultData; }
    }

    /** 用户点击结果保存时提交的数据，服务端会拆成关系表字段，不保存整包 JSON。 */
    public static class ResultSaveRequest {
        private String calculationId;
        private String stationCode;
        private String modelCode;
        private String modelVersion;
        private String forecastTime;
        private InputResponse input;
        private List<InputResponse> inputs = new ArrayList<InputResponse>();
        private Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        private Map<String, Map<String, Object>> stationParameters =
                new LinkedHashMap<String, Map<String, Object>>();
        private List<ValuePoint> shatuoOutflowSeries = new ArrayList<ValuePoint>();
        private Map<String, Object> result = new LinkedHashMap<String, Object>();
        private String modelDescription;
        private String forecaster;
        private String remark;
        public String getCalculationId() { return calculationId; }
        public void setCalculationId(String calculationId) { this.calculationId = calculationId; }
        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public String getModelCode() { return modelCode; }
        public void setModelCode(String modelCode) { this.modelCode = modelCode; }
        public String getModelVersion() { return modelVersion; }
        public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
        public String getForecastTime() { return forecastTime; }
        public void setForecastTime(String forecastTime) { this.forecastTime = forecastTime; }
        public InputResponse getInput() { return input; }
        public void setInput(InputResponse input) { this.input = input; }
        public List<InputResponse> getInputs() { return inputs; }
        public void setInputs(List<InputResponse> inputs) { this.inputs = inputs; }
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        public Map<String, Map<String, Object>> getStationParameters() { return stationParameters; }
        public void setStationParameters(Map<String, Map<String, Object>> stationParameters) { this.stationParameters = stationParameters; }
        public List<ValuePoint> getShatuoOutflowSeries() { return shatuoOutflowSeries; }
        public void setShatuoOutflowSeries(List<ValuePoint> shatuoOutflowSeries) { this.shatuoOutflowSeries = shatuoOutflowSeries; }
        public Map<String, Object> getResult() { return result; }
        public void setResult(Map<String, Object> result) { this.result = result; }
        public String getModelDescription() { return modelDescription; }
        public void setModelDescription(String modelDescription) { this.modelDescription = modelDescription; }
        public String getForecaster() { return forecaster; }
        public void setForecaster(String forecaster) { this.forecaster = forecaster; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    /** 模型计算页面导出请求，resultData 原样使用计算接口返回值。 */
    public static class ExcelExportRequest {
        private String stationCode;
        private String modelCode;
        private String modelDescription;
        private String forecastTime;
        private Map<String, Object> resultData = new LinkedHashMap<String, Object>();
        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public String getModelCode() { return modelCode; }
        public void setModelCode(String modelCode) { this.modelCode = modelCode; }
        public String getModelDescription() { return modelDescription; }
        public void setModelDescription(String modelDescription) { this.modelDescription = modelDescription; }
        public String getForecastTime() { return forecastTime; }
        public void setForecastTime(String forecastTime) { this.forecastTime = forecastTime; }
        public Map<String, Object> getResultData() { return resultData; }
        public void setResultData(Map<String, Object> resultData) { this.resultData = resultData; }
    }
}
