package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

/** 电站与其实际调用模型的解析结果。 */
public class StationModelRoute {
    private final StationDefinition station;
    private final ModelDefinition model;

    public StationModelRoute(StationDefinition station, ModelDefinition model) {
        this.station = station;
        this.model = model;
    }

    public StationDefinition getStation() { return station; }
    public ModelDefinition getModel() { return model; }
}
