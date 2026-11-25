package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue;
import java.util.*;

/** 实测后时标转换；WEA预报不使用此类。日模型仍以00:00表示业务日期。 */
final class MeasuredTimeLabels {
    private MeasuredTimeLabels() {}
    static Calendar queryBegin(Calendar business, String type) {
        Calendar c = (Calendar) business.clone();
        if ("hour".equals(type)) c.add(Calendar.HOUR_OF_DAY, 1);
        else { midnight(c); c.add(Calendar.DAY_OF_MONTH, 1); }
        return c;
    }
    static Calendar queryEnd(Calendar business, String type) {
        Calendar c = queryBegin(business, type);
        if ("day".equals(type)) c.set(Calendar.HOUR_OF_DAY, 8);
        return c;
    }
    static Calendar businessTime(Calendar stored, String type, boolean rainfall) {
        if (stored == null) return null;
        Calendar c = (Calendar) stored.clone();
        if ("hour".equals(type)) { c.add(Calendar.HOUR_OF_DAY, -1); return c; }
        if (c.get(Calendar.HOUR_OF_DAY) != (rainfall ? 8 : 0)
                || c.get(Calendar.MINUTE) != 0 || c.get(Calendar.SECOND) != 0
                || c.get(Calendar.MILLISECOND) != 0) return null;
        midnight(c); c.add(Calendar.DAY_OF_MONTH, -1); return c;
    }
    static List<DatangHourlyValue> normalize(List<DatangHourlyValue> raw, Map<String,Boolean> roles,
            Calendar begin, Calendar end, String type) {
        List<DatangHourlyValue> result = new ArrayList<>();
        if (raw == null) return result;
        for (DatangHourlyValue r : raw) {
            if (!roles.containsKey(r.getSenId())) continue;
            Calendar t = businessTime(r.getTime(), type, Boolean.TRUE.equals(roles.get(r.getSenId())));
            if (t == null || t.before(begin) || t.after(end)) continue;
            DatangHourlyValue copy = new DatangHourlyValue();
            copy.setSenId(r.getSenId()); copy.setTime(t);
            copy.setValue(r.getValue()); copy.setAverageValue(r.getAverageValue());
            result.add(copy);
        }
        return result;
    }
    private static void midnight(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
    }
}
