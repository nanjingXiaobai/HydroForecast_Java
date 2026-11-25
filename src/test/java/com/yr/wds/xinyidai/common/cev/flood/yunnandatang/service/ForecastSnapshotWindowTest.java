package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ForecastSnapshotWindowTest {
 ForecastContracts.ResultSaveRequest request(String begin,String end) {
  ForecastContracts.InputResponse input=new ForecastContracts.InputResponse(); input.setStationCode("S");input.setForecastBeginTime(begin);input.setForecastEndTime(end);
  ForecastContracts.ResultSaveRequest q=new ForecastContracts.ResultSaveRequest();q.setInput(input);return q;
 }
 ForecastDailyRows.Row row(String day,int step,String kind) {
  ForecastDailyRows.Row r=new ForecastDailyRows.Row();r.station="S";r.day=day;r.step=step;r.kind=kind;
  for(int i=0;i<1440/step;i++){r.present[i]=true;r.values[i]=(double)i;}return r;
 }
 @Test void hourlyKeepsBothEndpointsAndClearsHistoricalSlots() {
  ForecastDailyRows.Row r=row("2026-11-11",60,"API");List<ForecastDailyRows.Row> rows=new ArrayList<>(Arrays.asList(r));
  ForecastDailyRows.keepForecastWindow(rows,request("2026-11-11 17:00:00","2026-11-11 20:00:00"));
  assertEquals(1,rows.size());for(int i=0;i<24;i++){assertEquals(i>=17&&i<=20,r.present[i]);if(!r.present[i])assertNull(r.values[i]);}
 }
 @Test void tankRemovesHistoryAndAfterEndButKeepsParametersAndSummary() {
  ForecastDailyRows.Row param=row("2026-11-01",1440,"PARAMETER"), summary=row("2026-11-12",1440,"PENGSHUI");summary.station="SUMMARY";
  List<ForecastDailyRows.Row> rows=new ArrayList<>(Arrays.asList(row("2026-11-10",1440,"TANK"),row("2026-11-11",1440,"TANK"),summary,row("2026-11-13",1440,"INPUT"),param));
  ForecastDailyRows.keepForecastWindow(rows,request("2026-11-11 00:00:00","2026-11-12 00:00:00"));assertEquals(3,rows.size());assertTrue(rows.contains(param));assertTrue(rows.contains(summary));
 }
 @Test void pastOnlyWindowHasNoResultRows() {
  List<ForecastDailyRows.Row> rows=new ArrayList<>(Arrays.asList(row("2026-11-10",60,"API")));
  ForecastDailyRows.keepForecastWindow(rows,request("2026-11-11 17:00:00","2026-11-10 23:00:00"));assertTrue(rows.isEmpty());
 }
}
