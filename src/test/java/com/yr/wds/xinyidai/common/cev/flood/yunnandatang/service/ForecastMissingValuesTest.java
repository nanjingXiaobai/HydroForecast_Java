package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ForecastMissingValuesTest {
 @Test void annualHoursKeepRealZeroAndManualZeroDistinct() {
  InputResponse input=new InputResponse();input.setForecastBeginTime("2026-01-01 03:00:00");input.setForecastEndTime("2026-01-01 04:00:00");
  input.setHistoryBeginTime("2026-01-01 00:00:00");
  SeriesResponse series=new SeriesResponse();series.setKey("rainfall");series.setWeight(1D);
  ValuePoint real=new ValuePoint();real.setTime("2026-01-01 01:00:00");real.setValue(0D);real.setSource("HOURDB");series.getValues().add(real);input.getSeries().add(series);
  ForecastMissingValues.normalize(input,"API_MODEL");
  assertEquals(3,series.getValues().size());assertEquals("DEFAULT_ZERO",series.getValues().get(0).getSource());assertEquals("HOURDB",series.getValues().get(1).getSource());
  assertEquals(5,ApiAnnualCalculation.rainfall(input).size());
  ValuePoint manual=input.getForecastRainfall().getValues().get(0);manual.setSource("MANUAL");manual.setManuallyModified(true);
  ForecastMissingValues.normalize(input,"API_MODEL");assertEquals("MANUAL",manual.getSource());assertEquals(3,series.getValues().size());
 }
 @Test void syntheticOutflowDoesNotReplaceLatestMeasured() {
  InputResponse input=new InputResponse();input.setForecastBeginTime("2026-01-03 00:00:00");input.setForecastEndTime("2026-01-04 23:59:59");
  SeriesResponse series=new SeriesResponse();series.setKey("shatuoOutflow");ValuePoint real=new ValuePoint();real.setTime("2026-01-01 00:00:00");real.setValue(123D);real.setSource("DAYDB");series.getValues().add(real);input.getSeries().add(series);
  ForecastMissingValues.normalize(input,"TANK_MODEL");List<ValuePoint> out=ForecastCalculationService.tankResultOutflow(input,Collections.emptyList());
  assertEquals(0D,out.get(32).getValue());assertEquals("DEFAULT_ZERO",out.get(32).getSource());assertEquals(123D,out.get(33).getValue());assertEquals(123D,out.get(34).getValue());
 }
}
