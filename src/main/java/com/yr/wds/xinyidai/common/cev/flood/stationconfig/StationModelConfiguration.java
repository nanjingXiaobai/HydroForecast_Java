package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一份已经解析并校验通过的电站模型配置快照。 */
public class StationModelConfiguration {
    private boolean tankAutoEnabled;
    private String tankAutoTime = "23:00";
    public boolean isTankAutoEnabled() { return tankAutoEnabled; }
    public void setTankAutoEnabled(boolean value) { tankAutoEnabled = value; }
    public String getTankAutoTime() { return tankAutoTime; }
    public void setTankAutoTime(String value) { tankAutoTime = value; }
    private String schemaVersion;
    private String configVersion;
    private String environment;
    private final List<ModelDefinition> models = new ArrayList<ModelDefinition>();
    private final List<StationDefinition> stations = new ArrayList<StationDefinition>();
    private final List<SourceDataProfileDefinition> sourceDataProfiles = new ArrayList<SourceDataProfileDefinition>();
    private final List<TankParameterProfileDefinition> tankParameterProfiles = new ArrayList<TankParameterProfileDefinition>();
    private final List<AggregationDefinition> aggregations = new ArrayList<AggregationDefinition>();
    private final Map<String, ModelDefinition> modelMap = new LinkedHashMap<String, ModelDefinition>();
    private final Map<String, StationDefinition> stationMap = new LinkedHashMap<String, StationDefinition>();
    private final Map<String, SourceDataProfileDefinition> sourceDataProfileMap = new LinkedHashMap<String, SourceDataProfileDefinition>();
    private final Map<String, TankParameterProfileDefinition> tankParameterProfileMap = new LinkedHashMap<String, TankParameterProfileDefinition>();
    private final Map<String, AggregationDefinition> aggregationMap = new LinkedHashMap<String, AggregationDefinition>();

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getConfigVersion() { return configVersion; }
    public void setConfigVersion(String configVersion) { this.configVersion = configVersion; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public List<ModelDefinition> getModels() { return models; }
    public List<StationDefinition> getStations() { return stations; }
    public List<SourceDataProfileDefinition> getSourceDataProfiles() { return sourceDataProfiles; }
    public List<TankParameterProfileDefinition> getTankParameterProfiles() { return tankParameterProfiles; }
    public List<AggregationDefinition> getAggregations() { return aggregations; }
    public Map<String, ModelDefinition> getModelMap() { return modelMap; }
    public Map<String, StationDefinition> getStationMap() { return stationMap; }
    public Map<String, SourceDataProfileDefinition> getSourceDataProfileMap() { return sourceDataProfileMap; }
    public Map<String, TankParameterProfileDefinition> getTankParameterProfileMap() { return tankParameterProfileMap; }
    public Map<String, AggregationDefinition> getAggregationMap() { return aggregationMap; }
}
