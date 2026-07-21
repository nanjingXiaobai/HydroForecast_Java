package com.yr.wds.xinyidai.common.cev.flood.autoforecast;

import com.nari.slsd.hydro.backend.HFWebService;
import com.nari.slsd.hydro.backend.unified.dobj.HFSchemeType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Service
@EnableScheduling
@EnableConfigurationProperties(AutoForecastProperties.class)
@ConditionalOnProperty(name = "service.auto-forecast.mode", havingValue = "new")
public class NewAutoForecastService {
    private final AutoForecastProperties properties;
    private final CopyOnWriteArrayList<AutoForecastProperties.Rule> rules = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<AutoForecastExecution> history = new CopyOnWriteArrayList<>();
    private final Map<String, LocalDateTime> manualNextTimes = new ConcurrentHashMap<>();
    private final Set<String> submittedKeys = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final AtomicLong executionIds = new AtomicLong();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "new-auto-forecast-worker");
        t.setDaemon(true);
        return t;
    });
    private HFWebService forecastService;

    public NewAutoForecastService(AutoForecastProperties properties) { this.properties = properties; }

    @PostConstruct
    public void initialize() {
        File windowFile = resolveFile(properties.getWindowFile());
        File schemeFile = resolveFile(properties.getSchemeFile());
        if (!windowFile.isFile() || !schemeFile.isFile()) {
            throw new IllegalStateException("自动预报配置文件不存在: " + windowFile + ", " + schemeFile);
        }
        forecastService = new HFWebService("zh", windowFile, schemeFile);
        List<AutoForecastProperties.Rule> configuredRules = properties.getRules();
        replaceRules(configuredRules == null || configuredRules.isEmpty()
                ? createRulesForAvailableSchemes() : configuredRules);
        validateConfiguredSchemes();
    }

    @PreDestroy
    public void shutdown() { worker.shutdown(); }

    @Scheduled(fixedDelayString = "${service.auto-forecast.scan-delay-ms:5000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        for (AutoForecastProperties.Rule rule : rules) {
            if (!rule.isEnabled()) continue;
            LocalDateTime manual = manualNextTimes.get(rule.getSchemeName());
            if (manual != null) {
                if (!manual.isAfter(now) && manualNextTimes.remove(rule.getSchemeName(), manual)) {
                    submit(rule.getSchemeName(), manual, "MANUAL_NEXT",
                            key(rule.getSchemeName(), "MANUAL_NEXT", manual));
                }
                continue;
            }
            LocalDateTime scheduled = scheduledAt(rule, now);
            if (scheduled != null) {
                submit(rule.getSchemeName(), scheduled, "SCHEDULED", key(rule.getSchemeName(), "SCHEDULED", scheduled));
            }
        }
        if (submittedKeys.size() > Math.max(1000, properties.getHistoryLimit() * 10)) submittedKeys.clear();
    }

    public List<String> getAvailableSchemes() { return new ArrayList<>(forecastService.getAllSchemeNameList()); }
    public List<AutoForecastProperties.Rule> getRules() { return new ArrayList<>(rules); }

    public synchronized void replaceRules(List<AutoForecastProperties.Rule> newRules) {
        if (newRules == null) throw new IllegalArgumentException("rules不能为空");
        Set<String> names = new HashSet<>();
        for (AutoForecastProperties.Rule rule : newRules) {
            validateRule(rule);
            if (forecastService != null) requireKnownScheme(rule.getSchemeName());
            if (!names.add(rule.getSchemeName())) throw new IllegalArgumentException("方案重复配置: " + rule.getSchemeName());
        }
        rules.clear();
        rules.addAll(newRules);
    }

    public Map<String, LocalDateTime> getManualNextTimes() { return new ConcurrentHashMap<>(manualNextTimes); }

    public void setManualNextTime(String schemeName, LocalDateTime executeTime) {
        requireKnownScheme(schemeName);
        if (executeTime == null || !executeTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("下一次执行时间必须晚于当前时间");
        }
        manualNextTimes.put(schemeName, executeTime.withSecond(0).withNano(0));
    }

    public void clearManualNextTime(String schemeName) { manualNextTimes.remove(schemeName); }

    public void runNow(String schemeName, LocalDateTime forecastTime) {
        requireKnownScheme(schemeName);
        LocalDateTime time = forecastTime == null ? LocalDateTime.now() : forecastTime;
        submit(schemeName, time, "RUN_NOW", key(schemeName, "RUN_NOW", LocalDateTime.now()));
    }

    public void compensate(String schemeName, LocalDateTime begin, LocalDateTime end) {
        requireKnownScheme(schemeName);
        AutoForecastProperties.Rule rule = findRule(schemeName);
        if (begin == null || end == null || begin.isAfter(end)) throw new IllegalArgumentException("补算起止时间不正确");
        try {
            worker.submit(() -> {
                LocalDateTime cursor = normalizeForecastTime(rule, begin);
                LocalDateTime finish = normalizeForecastTime(rule, end);
                while (!cursor.isAfter(finish)) {
                    execute(schemeName, cursor, "COMPENSATE");
                    cursor = rule.getTimeType() == AutoForecastProperties.TimeType.HOUR
                            ? cursor.plusHours(rule.getFrequency()) : cursor.plusDays(rule.getFrequency());
                }
            });
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("自动预报服务已停止", e);
        }
    }

    public List<AutoForecastExecution> getHistory() {
        List<AutoForecastExecution> result = new ArrayList<>(history);
        result.sort(Comparator.comparingLong(AutoForecastExecution::getId).reversed());
        return result;
    }

    public Map<String, LocalDateTime> getNextScheduledTimes() {
        Map<String, LocalDateTime> result = new ConcurrentHashMap<>();
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        for (AutoForecastProperties.Rule rule : rules) {
            if (!rule.isEnabled()) continue;
            LocalDateTime manual = manualNextTimes.get(rule.getSchemeName());
            result.put(rule.getSchemeName(), manual != null ? manual : nextScheduledAt(rule, now));
        }
        return result;
    }

    private void submit(String schemeName, LocalDateTime forecastTime, String source, String taskKey) {
        if (!submittedKeys.add(taskKey)) return;
        try {
            worker.submit(() -> execute(schemeName, forecastTime, source));
        } catch (RejectedExecutionException e) {
            submittedKeys.remove(taskKey);
            throw e;
        }
    }

    private void execute(String schemeName, LocalDateTime forecastTime, String source) {
        AutoForecastExecution item = new AutoForecastExecution();
        item.setId(executionIds.incrementAndGet());
        item.setSchemeName(schemeName);
        item.setSource(source);
        item.setForecastTime(forecastTime);
        item.setStartTime(LocalDateTime.now());
        item.setStatus("RUNNING");
        addHistory(item);
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(java.util.Date.from(forecastTime.atZone(ZoneId.systemDefault()).toInstant()));
            boolean success = forecastService.doDefaultForecast(properties.getUserName(), calendar, schemeName);
            item.setStatus(success ? "SUCCESS" : "FAILED");
            item.setMessage(success ? "计算完成" : "供应商接口返回false");
        } catch (Throwable e) {
            item.setStatus("FAILED");
            item.setMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            item.setEndTime(LocalDateTime.now());
        }
    }

    private void addHistory(AutoForecastExecution item) {
        history.add(item);
        int limit = Math.max(10, properties.getHistoryLimit());
        while (history.size() > limit) history.remove(0);
    }

    private LocalDateTime scheduledAt(AutoForecastProperties.Rule rule, LocalDateTime now) {
        if (rule.getTimeType() == AutoForecastProperties.TimeType.HOUR) {
            if (now.getMinute() != rule.getDelayMinute()) return null;
            long hours = ChronoUnit.HOURS.between(LocalDateTime.of(1970, 1, 1, 0, 0), now.withMinute(0));
            return hours % rule.getFrequency() == 0 ? now : null;
        }
        if (now.getHour() != rule.getCalcHour() || now.getMinute() != rule.getDelayMinute()) return null;
        long days = ChronoUnit.DAYS.between(LocalDate.parse(rule.getAnchorDate()), now.toLocalDate());
        return days >= 0 && days % rule.getFrequency() == 0 ? now : null;
    }

    private LocalDateTime nextScheduledAt(AutoForecastProperties.Rule rule, LocalDateTime now) {
        LocalDateTime candidate = now.plusMinutes(1).withSecond(0).withNano(0);
        int maxMinutes = rule.getTimeType() == AutoForecastProperties.TimeType.HOUR
                ? 60 * rule.getFrequency() + 1 : 60 * 24 * rule.getFrequency() + 1;
        for (int i = 0; i <= maxMinutes; i++, candidate = candidate.plusMinutes(1)) {
            if (scheduledAt(rule, candidate) != null) return candidate;
        }
        throw new IllegalStateException("无法计算方案下一次执行时间: " + rule.getSchemeName());
    }

    private LocalDateTime normalizeForecastTime(AutoForecastProperties.Rule rule, LocalDateTime time) {
        return rule.getTimeType() == AutoForecastProperties.TimeType.HOUR
                ? time.withMinute(0).withSecond(0).withNano(0)
                : time.withHour(rule.getCalcHour()).withMinute(0).withSecond(0).withNano(0);
    }

    private AutoForecastProperties.Rule findRule(String schemeName) {
        for (AutoForecastProperties.Rule rule : rules) if (rule.getSchemeName().equals(schemeName)) return rule;
        throw new IllegalArgumentException("方案没有配置自动预报规则: " + schemeName);
    }

    private void validateConfiguredSchemes() {
        List<String> available = forecastService.getAllSchemeNameList();
        for (AutoForecastProperties.Rule rule : rules) {
            if (!available.contains(rule.getSchemeName())) {
                throw new IllegalStateException("自动预报方案不存在: " + rule.getSchemeName() + ", 可用方案: " + available);
            }
        }
    }

    private void requireKnownScheme(String schemeName) {
        if (schemeName == null || !forecastService.getAllSchemeNameList().contains(schemeName)) {
            throw new IllegalArgumentException("未知方案: " + schemeName);
        }
    }

    private void validateRule(AutoForecastProperties.Rule rule) {
        if (rule == null || rule.getSchemeName() == null || rule.getSchemeName().trim().isEmpty()) throw new IllegalArgumentException("schemeName不能为空");
        if (rule.getTimeType() == null) throw new IllegalArgumentException("timeType不能为空");
        if (rule.getCalcHour() < 0 || rule.getCalcHour() > 23) throw new IllegalArgumentException("calcHour必须为0-23");
        if (rule.getDelayMinute() < 0 || rule.getDelayMinute() > 59) throw new IllegalArgumentException("delayMinute必须为0-59");
        if (rule.getFrequency() < 1) throw new IllegalArgumentException("frequency必须大于0");
        LocalDate.parse(rule.getAnchorDate());
    }

    /** 方案名称和类型自动发现，执行时间统一由 application.properties 控制。 */
    private List<AutoForecastProperties.Rule> createRulesForAvailableSchemes() {
        List<AutoForecastProperties.Rule> result = new ArrayList<>();
        for (String schemeName : forecastService.getAllSchemeNameList()) {
            HFSchemeType schemeType = HFSchemeType.getSchemeTypeByName(schemeName);
            AutoForecastProperties.Rule rule = new AutoForecastProperties.Rule();
            rule.setSchemeName(schemeName);
            if ("RSHour".equals(schemeType.getTimeType().name())) {
                AutoForecastProperties.HourSchedule hour = properties.getHour();
                rule.setTimeType(AutoForecastProperties.TimeType.HOUR);
                rule.setEnabled(hour.isEnabled());
                rule.setDelayMinute(hour.getDelayMinute());
                rule.setFrequency(hour.getFrequency());
            } else if ("RSDay".equals(schemeType.getTimeType().name())) {
                AutoForecastProperties.DaySchedule day = properties.getDay();
                rule.setTimeType(AutoForecastProperties.TimeType.DAY);
                rule.setEnabled(day.isEnabled());
                rule.setCalcHour(day.getCalcHour());
                rule.setDelayMinute(day.getDelayMinute());
                rule.setFrequency(day.getFrequency());
                rule.setAnchorDate(day.getAnchorDate());
            } else {
                throw new IllegalArgumentException("新自动预报暂不支持时间类型: "
                        + schemeType.getTimeType().name() + ", 方案: " + schemeName);
            }
            result.add(rule);
        }
        return result;
    }

    private String key(String schemeName, String source, LocalDateTime time) {
        return schemeName + '|' + source + '|' + time.withSecond(0).withNano(0);
    }

    private File resolveFile(String path) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(System.getProperty("user.dir"), path);
    }
}
