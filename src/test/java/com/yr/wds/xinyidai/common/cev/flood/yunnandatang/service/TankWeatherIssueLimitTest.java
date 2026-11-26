package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TankWeatherIssueLimitTest {
    Calendar at(String time) {
        return GregorianCalendar.from(ZonedDateTime.parse(time + "+08:00[Asia/Shanghai]"));
    }
    @Test void todayUsesCurrentPublicationCutoffWithoutChangingForecastDate() {
        Calendar issue = at("2026-11-26T00:00:00"), now = at("2026-11-26T15:23:00");
        Calendar cutoff = ForecastInputAssemblyService.tankWeatherIssueLimit(issue, now);
        assertEquals(now.getTimeInMillis(), cutoff.getTimeInMillis());
        assertEquals(at("2026-11-26T00:00:00").getTimeInMillis(), issue.getTimeInMillis());
        assertNotSame(now, cutoff);
    }
    @Test void historicalForecastUsesEndOfSelectedDay() {
        Calendar issue = at("2026-11-25T00:00:00");
        assertEquals(at("2026-11-25T23:59:59").getTimeInMillis(), ForecastInputAssemblyService.tankWeatherIssueLimit(
                issue, at("2026-11-26T15:23:00")).getTimeInMillis());
    }
    @Test void midnightUsesShanghaiDateAcrossTimeZones() {
        Calendar now = GregorianCalendar.from(ZonedDateTime.parse("2026-11-25T16:30:00Z"));
        assertEquals(now.getTimeInMillis(), ForecastInputAssemblyService.tankWeatherIssueLimit(
                at("2026-11-26T00:00:00"), now).getTimeInMillis());
    }
}
