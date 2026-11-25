package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import java.time.LocalDateTime;
import java.util.*;

/** 补齐观测时间网格；保留真实零值与缺测补零的区别，随输入快照保存。 */
final class ForecastMissingValues {
    private ForecastMissingValues() {}
    static void normalize(InputResponse input, String module) {
        boolean hourly = "API_MODEL".equals(module);
        boolean regression = "REGRESSION_MODEL".equals(module);
        LocalDateTime forecast = ForecastDailyRows.parse(input.getForecastBeginTime());
        LocalDateTime end = ForecastDailyRows.parse(input.getForecastEndTime());
        LocalDateTime begin = regression || hourly ? (input.getHistoryBeginTime() == null ? forecast : ForecastDailyRows.parse(input.getHistoryBeginTime()))
                : TankDailyForecastService.calculationStart(forecast.toLocalDate()).atStartOfDay();
        for (SeriesResponse series : input.getSeries()) {
            LocalDateTime historyEnd = regression || "shatuoOutflow".equals(series.getKey())
                    ? end : hourly ? forecast.minusHours(1) : forecast.minusDays(1);
            fill(series.getValues(), begin, historyEnd, hourly);
            if (regression) for (ValuePoint p : series.getValues()) {
                if (p.getAverageValue() == null) p.setAverageValue(0D);
            }
        }
        if (input.getForecastRainfall() == null) input.setForecastRainfall(new RainfallResponse());
        fill(input.getForecastRainfall().getValues(), forecast, end, hourly);
    }
    private static void fill(List<ValuePoint> points, LocalDateTime begin, LocalDateTime end, boolean hourly) {
        Map<String, ValuePoint> byTime = new HashMap<>();
        for (ValuePoint p : points) byTime.put(p.getTime(), p);
        for (LocalDateTime t = begin; !t.isAfter(end); t = hourly ? t.plusHours(1) : t.plusDays(1)) {
            String time = t.format(ForecastDailyRows.TIME);
            ValuePoint p = byTime.get(time);
            if (p == null) {
                p = new ValuePoint(); p.setTime(time); p.setEditable(true);
                points.add(p);
            }
            if (p.getValue() == null) {
                p.setValue(0D); p.setSource("DEFAULT_ZERO"); p.setOriginalValue(null);
                p.setManuallyModified(false);
            }
        }
        points.sort(Comparator.comparing(ValuePoint::getTime));
    }
}
