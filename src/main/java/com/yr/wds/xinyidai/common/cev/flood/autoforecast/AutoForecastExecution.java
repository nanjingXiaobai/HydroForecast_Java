package com.yr.wds.xinyidai.common.cev.flood.autoforecast;

import java.time.LocalDateTime;

public class AutoForecastExecution {
    private long id;
    private String schemeName;
    private String source;
    private LocalDateTime forecastTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String message;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getForecastTime() { return forecastTime; }
    public void setForecastTime(LocalDateTime forecastTime) { this.forecastTime = forecastTime; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
