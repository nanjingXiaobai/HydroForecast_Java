package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import org.junit.jupiter.api.Test;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
class MeasuredTimeLabelsTest {
 Calendar t(String s){return GregorianCalendar.from(LocalDateTime.parse(s).atZone(ZoneId.of("Asia/Shanghai")));}
 DatangHourlyValue row(String id,String s){DatangHourlyValue r=new DatangHourlyValue();r.setSenId(id);r.setTime(t(s));r.setValue(7D);r.setAverageValue(3D);return r;}
 @Test void hourlyCrossesYearAndIncludesLastEndingHour(){
  Calendar begin=t("2025-12-31T23:00:00"),end=t("2026-01-01T00:00:00");
  assertEquals(t("2026-01-01T00:00:00"),MeasuredTimeLabels.queryBegin(begin,"hour"));
  assertEquals(t("2026-01-01T01:00:00"),MeasuredTimeLabels.queryEnd(end,"hour"));
  DatangHourlyValue raw=row("r","2026-01-01T00:00:00");
  List<DatangHourlyValue> out=MeasuredTimeLabels.normalize(Arrays.asList(raw,row("r","2026-01-01T01:00:00"),row("r","2026-01-01T02:00:00")),Collections.singletonMap("r",true),begin,end,"hour");
  assertEquals(2,out.size());assertEquals(begin,out.get(0).getTime());assertEquals(end,out.get(1).getTime());assertEquals(3D,out.get(0).getAverageValue());
  assertEquals(t("2026-01-01T00:00:00"),raw.getTime());
 }
 @Test void dailyRainAtEightAndFlowAtMidnightUsePreviousDay(){
  Calendar day=t("2025-12-31T00:00:00");Map<String,Boolean> roles=new HashMap<>();roles.put("rain",true);roles.put("flow",false);
  List<DatangHourlyValue> out=MeasuredTimeLabels.normalize(Arrays.asList(row("rain","2026-01-01T08:00:00"),row("rain","2026-01-01T00:00:00"),row("flow","2026-01-01T00:00:00"),row("flow","2026-01-01T08:00:00")),roles,day,t("2025-12-31T23:59:59"),"day");
  assertEquals(2,out.size());for(DatangHourlyValue v:out)assertEquals(day,v.getTime());
  assertEquals(t("2026-01-01T08:00:00"),MeasuredTimeLabels.queryEnd(t("2025-12-31T23:59:59"),"day"));
 }
 @Test void leapDayAndMonthBoundary(){
  assertEquals(t("2024-02-29T00:00:00"),MeasuredTimeLabels.businessTime(t("2024-03-01T08:00:00"),"day",true));
  assertEquals(t("2026-02-28T00:00:00"),MeasuredTimeLabels.businessTime(t("2026-03-01T00:00:00"),"day",false));
 }
}
