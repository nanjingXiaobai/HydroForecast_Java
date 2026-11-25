package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将内部配置快照转换为前端稳定使用的 JSON 数据。 */
@Service
@ConditionalOnYunnanDatangFeature
public class StationConfigQueryService {
    private final StationModelRegistry registry;

    public StationConfigQueryService(StationModelRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> getAll() {
        StationModelConfiguration configuration = registry.getConfiguration();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", configuration.getSchemaVersion());
        result.put("configVersion", configuration.getConfigVersion());
        result.put("environment", configuration.getEnvironment());

        List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
        for (ModelDefinition model : configuration.getModels()) models.add(toModel(model));
        result.put("models", models);

        List<Map<String, Object>> stations = new ArrayList<Map<String, Object>>();
        for (StationDefinition station : registry.getEnabledStations()) stations.add(toStation(station));
        result.put("stations", stations);
        return result;
    }

    public Map<String, Object> getStation(String stationCode) {
        return toStation(registry.resolveRoute(stationCode).getStation());
    }

    private Map<String, Object> toModel(ModelDefinition model) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("code", model.getCode());
        item.put("name", model.getName());
        item.put("module", model.getModule());
        item.put("supportedCallModes", model.getSupportedCallModes());
        return item;
    }

    private Map<String, Object> toStation(StationDefinition station) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("code", station.getCode());
        item.put("name", station.getName());
        item.put("module", station.getModule());
        item.put("modelRef", station.getModelRef());
        item.put("role", station.getRole());
        item.put("enabled", station.isEnabled());
        item.put("productionReady", station.isProductionReady());
        item.put("displayOrder", station.getDisplayOrder());
        item.put("timeStep", station.getTimeStep());
        item.put("forecastSteps", station.getForecastSteps());
        item.put("historySteps", station.getHistorySteps());
        item.put("allowManualEdit", station.isAllowManualEdit());
        return item;
    }
}
