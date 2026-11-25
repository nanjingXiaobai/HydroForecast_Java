package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

/** 电站或内部计算分区定义。 */
public class StationDefinition {
    private final java.util.Map<String, String> resultPoints = new java.util.LinkedHashMap<String, String>();
    public java.util.Map<String, String> getResultPoints() { return resultPoints; }
    private String code;
    private String name;
    private String module;
    private String modelRef;
    private String parameterProfileRef;
    private String curveName;
    private String dataProfileRef;
    private String rainfallProfileRef;
    private String aggregationRef;
    private String role;
    private boolean enabled;
    private boolean productionReady;
    private int displayOrder;
    private String timeStep;
    private Integer forecastSteps;
    private Integer historySteps;
    private boolean allowManualEdit;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getModelRef() { return modelRef; }
    public void setModelRef(String modelRef) { this.modelRef = modelRef; }
    public String getParameterProfileRef() { return parameterProfileRef; }
    public void setParameterProfileRef(String parameterProfileRef) { this.parameterProfileRef = parameterProfileRef; }
    /** API模型对应的水库曲线名称前缀，例如“鱼剑口水库”。 */
    public String getCurveName() { return curveName; }
    public void setCurveName(String curveName) { this.curveName = curveName; }
    public String getDataProfileRef() { return dataProfileRef; }
    public void setDataProfileRef(String dataProfileRef) { this.dataProfileRef = dataProfileRef; }
    public String getRainfallProfileRef() { return rainfallProfileRef; }
    public void setRainfallProfileRef(String rainfallProfileRef) { this.rainfallProfileRef = rainfallProfileRef; }
    public String getAggregationRef() { return aggregationRef; }
    public void setAggregationRef(String aggregationRef) { this.aggregationRef = aggregationRef; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isProductionReady() { return productionReady; }
    public void setProductionReady(boolean productionReady) { this.productionReady = productionReady; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public String getTimeStep() { return timeStep; }
    public void setTimeStep(String timeStep) { this.timeStep = timeStep; }
    public Integer getForecastSteps() { return forecastSteps; }
    public void setForecastSteps(Integer forecastSteps) { this.forecastSteps = forecastSteps; }
    public Integer getHistorySteps() { return historySteps; }
    public void setHistorySteps(Integer historySteps) { this.historySteps = historySteps; }
    public boolean isAllowManualEdit() { return allowManualEdit; }
    public void setAllowManualEdit(boolean allowManualEdit) { this.allowManualEdit = allowManualEdit; }
}
