package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data;

import java.util.Calendar;

/** GFS 区域预报降雨的一条只读记录。 */
public class GfsForecastRainValue {
    private String regionId;
    private Calendar forecastIssueTime;
    private Calendar dataTime;
    private Integer timeSpan;
    /** 模型统一使用 WEA_GFSFORRAIN.AVERPRE，不开放取值字段配置。 */
    private Double averagePrecipitation;
    private String description;
    private Integer subtype;

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }
    public Calendar getForecastIssueTime() { return forecastIssueTime; }
    public void setForecastIssueTime(Calendar forecastIssueTime) { this.forecastIssueTime = forecastIssueTime; }
    public Calendar getDataTime() { return dataTime; }
    public void setDataTime(Calendar dataTime) { this.dataTime = dataTime; }
    public Integer getTimeSpan() { return timeSpan; }
    public void setTimeSpan(Integer timeSpan) { this.timeSpan = timeSpan; }
    public Double getAveragePrecipitation() { return averagePrecipitation; }
    public void setAveragePrecipitation(Double averagePrecipitation) { this.averagePrecipitation = averagePrecipitation; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSubtype() { return subtype; }
    public void setSubtype(Integer subtype) { this.subtype = subtype; }
}
