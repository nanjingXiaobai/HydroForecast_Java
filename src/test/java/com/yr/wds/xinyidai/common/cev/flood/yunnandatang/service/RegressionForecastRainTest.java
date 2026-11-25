package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RegressionForecastRainTest {
 @Test void noGfsStillBuildsTenDaysAndDatabaseFailuresAreNotZero() {
  StationModelConfigProperties props=new StationModelConfigProperties(); props.setFile("conf/forecast/station-model-config.xml");
  StationModelRegistry registry=new StationModelRegistry(props,new StationModelConfigParser());registry.initialize();
  ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
  when(repo.queryHistoryValues(anyList(),any(),any(),anyString())).thenReturn(Collections.emptyList());
  ForecastInputAssemblyService service=new ForecastInputAssemblyService(registry,repo,null);
  InputQueryRequest q=new InputQueryRequest();q.setStationCode("REGRESSION_SHATUO");q.setForecastTime("2026-11-06 00:00:00");
  InputResponse input=service.assemble(q,"REGRESSION_MODEL");
  assertEquals(10,input.getForecastRainfall().getValues().size());
  assertEquals("2026-11-15 00:00:00",input.getForecastRainfall().getValues().get(9).getTime());
  for(ValuePoint p:input.getForecastRainfall().getValues())assertEquals(0D,p.getValue());
  assertEquals(17,input.getSeries().get(0).getValues().size());
  when(repo.queryHistoryValues(anyList(),any(),any(),anyString())).thenThrow(new IllegalStateException("database unavailable"));
  assertThrows(IllegalStateException.class,()->service.assemble(q,"REGRESSION_MODEL"));
 }
 @Test void partialForecastPreservesRainAndFillsOnlyMissingDays() {
  ForecastInputAssemblyService service=new ForecastInputAssemblyService(null,null,null);
  Calendar begin=new GregorianCalendar(2026,10,6);Calendar end=new GregorianCalendar(2026,10,8);
  GfsForecastRainValue row=new GfsForecastRainValue();row.setDataTime(new GregorianCalendar(2026,10,7));row.setAveragePrecipitation(12D);
  RainfallResponse rain=service.apiForecastRain(new SourceDataProfileDefinition(),Arrays.asList(row),begin,end,"P1D");
  assertEquals(0D,rain.getValues().get(0).getValue());assertEquals(12D,rain.getValues().get(1).getValue());assertEquals(0D,rain.getValues().get(2).getValue());
 }
 @Test void dailyMeasurementsAndPriorYearAveragesUseBusinessDay() {
  StationModelConfigProperties props=new StationModelConfigProperties();props.setFile("conf/forecast/station-model-config.xml");
  StationModelRegistry registry=new StationModelRegistry(props,new StationModelConfigParser());registry.initialize();
  ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
  when(repo.queryHistoryValues(anyList(),any(),any(),eq("day"))).thenAnswer(call->{
   Calendar begin=call.getArgument(1);List<DatangHourlyValue> rows=new ArrayList<>();
   boolean previous=begin.get(Calendar.YEAR)==2025;
   Calendar stored=new GregorianCalendar(previous?2025:2026,10,6,0,0);
   for(String id:(List<String>)call.getArgument(0)) {
    DatangHourlyValue row=new DatangHourlyValue();row.setSenId(id);Calendar t=(Calendar)stored.clone();
    if(id.endsWith(".K13100"))t.set(Calendar.HOUR_OF_DAY,8);
    row.setTime(t);row.setValue(previous?999D:123D);row.setAverageValue(previous?12D:null);rows.add(row);
   }
   return rows;
  });
  InputQueryRequest q=new InputQueryRequest();q.setStationCode("REGRESSION_SHATUO");q.setForecastTime("2026-11-06 00:00:00");
  InputResponse result=new ForecastInputAssemblyService(registry,repo,null).assemble(q,"REGRESSION_MODEL");
  for(SeriesResponse series:result.getSeries()) {
   ValuePoint p=series.getValues().stream().filter(v->"2026-11-05 00:00:00".equals(v.getTime())).findFirst().get();
   assertEquals(123D,p.getValue());assertEquals(12D,p.getAverageValue());
   ValuePoint next=series.getValues().stream().filter(v->"2026-11-06 00:00:00".equals(v.getTime())).findFirst().get();assertEquals(0D,next.getValue());
  }
 }
}
