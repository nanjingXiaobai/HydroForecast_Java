package com.yr.wds.xinyidai.common.cev.flood.autoforecast;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "service.auto-forecast")
public class AutoForecastProperties {
    private String mode = "old";
    private String userName = "自动预报";
    private String windowFile = "conf/forecast/HFWindowConf.xml";
    private String schemeFile = "conf/forecast/HFSchemeConf.xml";
    private long scanDelayMs = 5000L;
    private int historyLimit = 200;
    private HourSchedule hour = new HourSchedule();
    private DaySchedule day = new DaySchedule();
    private List<Rule> rules = new ArrayList<>();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getWindowFile() { return windowFile; }
    public void setWindowFile(String windowFile) { this.windowFile = windowFile; }
    public String getSchemeFile() { return schemeFile; }
    public void setSchemeFile(String schemeFile) { this.schemeFile = schemeFile; }
    public long getScanDelayMs() { return scanDelayMs; }
    public void setScanDelayMs(long scanDelayMs) { this.scanDelayMs = scanDelayMs; }
    public int getHistoryLimit() { return historyLimit; }
    public void setHistoryLimit(int historyLimit) { this.historyLimit = historyLimit; }
    public HourSchedule getHour() { return hour; }
    public void setHour(HourSchedule hour) { this.hour = hour; }
    public DaySchedule getDay() { return day; }
    public void setDay(DaySchedule day) { this.day = day; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules == null ? new ArrayList<>() : rules; }

    public enum TimeType { HOUR, DAY }

    public static class HourSchedule {
        private boolean enabled = true;
        private int delayMinute = 5;
        private int frequency = 1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getDelayMinute() { return delayMinute; }
        public void setDelayMinute(int delayMinute) { this.delayMinute = delayMinute; }
        public int getFrequency() { return frequency; }
        public void setFrequency(int frequency) { this.frequency = frequency; }
    }

    public static class DaySchedule {
        private boolean enabled = true;
        private int calcHour = 20;
        private int delayMinute = 5;
        private int frequency = 1;
        private String anchorDate = "1970-01-01";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getCalcHour() { return calcHour; }
        public void setCalcHour(int calcHour) { this.calcHour = calcHour; }
        public int getDelayMinute() { return delayMinute; }
        public void setDelayMinute(int delayMinute) { this.delayMinute = delayMinute; }
        public int getFrequency() { return frequency; }
        public void setFrequency(int frequency) { this.frequency = frequency; }
        public String getAnchorDate() { return anchorDate; }
        public void setAnchorDate(String anchorDate) { this.anchorDate = anchorDate; }
    }

    public static class Rule {
        private String schemeName;
        private TimeType timeType = TimeType.HOUR;
        private boolean enabled = true;
        private int calcHour;
        private int delayMinute = 5;
        private int frequency = 1;
        private String anchorDate = "1970-01-01";

        public String getSchemeName() { return schemeName; }
        public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
        public TimeType getTimeType() { return timeType; }
        public void setTimeType(TimeType timeType) { this.timeType = timeType; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getCalcHour() { return calcHour; }
        public void setCalcHour(int calcHour) { this.calcHour = calcHour; }
        public int getDelayMinute() { return delayMinute; }
        public void setDelayMinute(int delayMinute) { this.delayMinute = delayMinute; }
        public int getFrequency() { return frequency; }
        public void setFrequency(int frequency) { this.frequency = frequency; }
        public String getAnchorDate() { return anchorDate; }
        public void setAnchorDate(String anchorDate) { this.anchorDate = anchorDate; }
    }
}
