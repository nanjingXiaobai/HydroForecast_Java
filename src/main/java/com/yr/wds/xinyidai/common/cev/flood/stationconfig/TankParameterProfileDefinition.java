package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import java.util.LinkedHashMap;
import java.util.Map;

/** 水箱模型的一套分区参数，可直接放入 Python 的 modelParameters。 */
public class TankParameterProfileDefinition {
    private String code;
    private final Map<String, Object> modelParameters = new LinkedHashMap<String, Object>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Map<String, Object> getModelParameters() { return modelParameters; }
}
