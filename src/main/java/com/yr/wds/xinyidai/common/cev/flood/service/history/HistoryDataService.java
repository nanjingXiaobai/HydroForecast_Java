package com.yr.wds.xinyidai.common.cev.flood.service.history;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.nari.slsd.hps.dbmgr.conf.DBManager;
import com.nari.slsd.hydro.backend.unified.dobj.HPSTimeDoubleValue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class HistoryDataService {

    private HistoryDataService() {
    }

    public static Map<String, List<Map<String, Object>>> queryHistoryData(List<String> pointIds,
                                                                          String startTime,
                                                                          String endTime,
                                                                          String timeType) {
        Set<String> normalizedPointIds = normalizePointIds(pointIds);
        Calendar begin = parseTime(startTime, "startTime");
        Calendar end = parseTime(endTime, "endTime");
        if (begin.after(end)) {
            throw new IllegalArgumentException("startTime cannot be later than endTime");
        }

        String queryMark = resolveQueryMark(timeType);
        Long[] ids = normalizedPointIds.stream()
                .map(HistoryDataService::parsePointId)
                .toArray(Long[]::new);
        Object[] params = new Object[]{ids, begin, end};
        List<HPSTimeDoubleValue> values = DBManager.getTable(
                queryMark, HPSTimeDoubleValue.class, params);

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        normalizedPointIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (values == null) {
            return result;
        }

        values.stream()
                .filter(value -> value != null && value.getId() != null && value.getTime() != null)
                .sorted(Comparator.comparing(value -> value.getTime().getTimeInMillis()))
                .forEach(value -> {
                    List<Map<String, Object>> pointValues = result.get(value.getId());
                    if (pointValues == null) {
                        return;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("time", formatTime(value.getTime()));
                    item.put("v", value.getValue());
                    item.put("avgv", value.getAddValue1());
                    pointValues.add(item);
                });
        return result;
    }

    private static Set<String> normalizePointIds(List<String> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            throw new IllegalArgumentException("pointIds cannot be empty");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String pointId : pointIds) {
            if (StrUtil.isNotBlank(pointId)) {
                result.add(pointId.trim());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("pointIds cannot be empty");
        }
        return result;
    }

    private static Calendar parseTime(String time, String fieldName) {
        if (StrUtil.isBlank(time)) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        try {
            return DateUtil.calendar(DateUtil.parse(time.trim()));
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " has an invalid format", e);
        }
    }

    private static Long parsePointId(String pointId) {
        try {
            return Long.valueOf(pointId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("pointIds contains a non-numeric value: " + pointId, e);
        }
    }

    private static String resolveQueryMark(String timeType) {
        if ("hour".equals(timeType)) {
            return "hf_read_hourdb_v";
        }
        if ("day".equals(timeType)) {
            return "hf_read_daydb_v";
        }
        throw new IllegalArgumentException("timeType only supports lowercase hour or day");
    }

    private static String formatTime(Calendar time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
        TimeZone timeZone = time.getTimeZone();
        format.setTimeZone(timeZone == null ? TimeZone.getDefault() : timeZone);
        return format.format(time.getTime());
    }
}
