package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

/** 数据方案中的一个输入序列，例如某个雨量站或水库出库流量。 */
public class SourceSeriesDefinition {
    private String role;
    private String name;
    private String legacyStationCode;
    private String sensorId;
    private String attributeCode;
    private Double weight;
    private String unit;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLegacyStationCode() { return legacyStationCode; }
    public void setLegacyStationCode(String legacyStationCode) { this.legacyStationCode = legacyStationCode; }
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }
    public String getAttributeCode() { return attributeCode; }
    public void setAttributeCode(String attributeCode) { this.attributeCode = attributeCode; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}
