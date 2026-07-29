package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import java.util.ArrayList;
import java.util.List;

/** 一套模型输入数据映射方案。 */
public class SourceDataProfileDefinition {
    private String code;
    private String name;
    private String historicalTable;
    private String forecastTable;
    /** WEA_GFSFORRAIN.REGID，由现场在 XML 中配置。 */
    private String forecastRegionId;
    /** WEA_GFSFORRAIN.SUBTYPE，由现场在 XML 中配置。 */
    private Integer forecastSubtype;
    private String timeStep;
    private String mappingStatus;
    private final List<SourceSeriesDefinition> series = new ArrayList<SourceSeriesDefinition>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHistoricalTable() { return historicalTable; }
    public void setHistoricalTable(String historicalTable) { this.historicalTable = historicalTable; }
    public String getForecastTable() { return forecastTable; }
    public void setForecastTable(String forecastTable) { this.forecastTable = forecastTable; }
    public String getForecastRegionId() { return forecastRegionId; }
    public void setForecastRegionId(String forecastRegionId) { this.forecastRegionId = forecastRegionId; }
    public Integer getForecastSubtype() { return forecastSubtype; }
    public void setForecastSubtype(Integer forecastSubtype) { this.forecastSubtype = forecastSubtype; }
    public String getTimeStep() { return timeStep; }
    public void setTimeStep(String timeStep) { this.timeStep = timeStep; }
    public String getMappingStatus() { return mappingStatus; }
    public void setMappingStatus(String mappingStatus) { this.mappingStatus = mappingStatus; }
    public List<SourceSeriesDefinition> getSeries() { return series; }
}
