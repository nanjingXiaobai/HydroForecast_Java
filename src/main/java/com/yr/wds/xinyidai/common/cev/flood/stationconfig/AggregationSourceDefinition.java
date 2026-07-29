package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

/** 汇总方案中的一个来源；point 为空表示使用该分区模型返回的 flow。 */
public class AggregationSourceDefinition {
    private String stationCode;
    private String point;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getPoint() { return point; }
    public void setPoint(String point) { this.point = point; }
}
