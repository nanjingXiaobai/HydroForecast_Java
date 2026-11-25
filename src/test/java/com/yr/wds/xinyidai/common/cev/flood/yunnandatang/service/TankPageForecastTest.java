package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TankPageForecastTest {
 @Test void calculationStartFollowsForecastYearAcrossYearBoundaries() throws Exception {
  StationModelConfigProperties props=new StationModelConfigProperties();props.setFile("conf/forecast/station-model-config.xml");
  StationModelRegistry registry=new StationModelRegistry(props,new StationModelConfigParser());registry.initialize();
  ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
  ForecastInputAssemblyService service=new ForecastInputAssemblyService(registry,repo,null);
  java.lang.reflect.Method method=ForecastCalculationService.class.getDeclaredMethod("tankAnnualRainfall",InputResponse.class,StationDefinition.class);
  method.setAccessible(true);
  for(String date:Arrays.asList("2025-11-03", "2026-01-01", "2026-12-01", "2027-01-01")) {
   java.time.LocalDate day=java.time.LocalDate.parse(date);
   String expected=(day.getYear()-1)+"-12-01 00:00:00";
   InputQueryRequest q=new InputQueryRequest();q.setForecastTime(date+" 00:00:00");q.setForecastBeginTime(date+" 00:00:00");q.setForecastEndTime(date+" 23:59:59");
   // An old client-supplied start must not override the forecast-year rule.
   q.setHistoryBeginTime("2025-12-01 00:00:00");
   for(InputResponse input:service.assembleTankGroupWithForecast(q)) {
    assertEquals(expected,input.getHistoryBeginTime());
    List<Map<String,Object>> rain=(List<Map<String,Object>>)method.invoke(new ForecastCalculationService(registry,null,null),input,registry.resolveRoute(input.getStationCode()).getStation());
    assertEquals(expected,rain.get(0).get("time"));
    assertEquals(date+" 00:00:00",rain.get(rain.size()-1).get("time"));
    assertEquals(java.time.temporal.ChronoUnit.DAYS.between(day.withYear(day.getYear()-1).withMonth(12).withDayOfMonth(1),day)+1,rain.size());
   }
  }
 }
 @Test void pageReadsAllFourConfiguredRegionsAndKeepsZero() throws Exception {
  StationModelConfigProperties props=new StationModelConfigProperties();props.setFile("conf/forecast/station-model-config.xml");
  StationModelRegistry registry=new StationModelRegistry(props,new StationModelConfigParser());registry.initialize();
  ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
  when(repo.queryHistoryValues(anyList(),any(),any(),anyString())).thenReturn(Collections.emptyList());
  when(repo.queryLatestGfsRain(anyString(),eq(10051),any(),any(),any())).thenAnswer(call->{
   GfsForecastRainValue row=new GfsForecastRainValue();row.setDataTime(call.getArgument(3));row.setAveragePrecipitation(0D);return Arrays.asList(row);
  });
  ForecastInputAssemblyService service=new ForecastInputAssemblyService(registry,repo,null);
  InputQueryRequest q=new InputQueryRequest();q.setForecastTime("2026-11-05 00:00:00");q.setForecastBeginTime("2026-11-05 00:00:00");q.setForecastEndTime("2026-11-05 23:59:59");
  List<InputResponse> inputs=service.assembleTankGroupWithForecast(q);assertEquals(4,inputs.size());
  for(InputResponse input:inputs){assertEquals("2025-12-01 00:00:00",input.getHistoryBeginTime());assertEquals(0D,input.getForecastRainfall().getValues().get(0).getValue());assertEquals("WEA_GFSFORRAIN.AVERPRE",input.getForecastRainfall().getValues().get(0).getSource());}
  java.lang.reflect.Method rainfallMethod=ForecastCalculationService.class.getDeclaredMethod("tankAnnualRainfall",InputResponse.class,StationDefinition.class);
  rainfallMethod.setAccessible(true);
  for (InputResponse input : inputs) {
   List<Map<String,Object>> rainfall=(List<Map<String,Object>>)rainfallMethod.invoke(new ForecastCalculationService(registry,null,null),input,registry.resolveRoute(input.getStationCode()).getStation());
   assertEquals("2025-12-01 00:00:00",rainfall.get(0).get("time"));
   assertEquals("2026-11-05 00:00:00",rainfall.get(rainfall.size()-1).get("time"));
   assertEquals(340,rainfall.size());
  }
  for(String region:Arrays.asList("2000000007","2000000006","2000000004","2000000005"))verify(repo).queryLatestGfsRain(eq(region),eq(10051),any(),any(),any());
  DatangHourlyValue outflow=new DatangHourlyValue();outflow.setSenId("1442213919031552.W14100");outflow.setTime(TankDailyForecastService.calendar(java.time.LocalDateTime.of(2026,11,6,0,0)));outflow.setValue(123D);
  when(repo.queryHistoryValues(eq(Collections.singletonList(outflow.getSenId())),any(),any(),eq("day"))).thenReturn(Arrays.asList(outflow));
  InputResponse measuredInput=service.assembleTankGroupWithForecast(q).get(0);
  List<ValuePoint> measured=measuredInput.getSeries().stream().filter(v->"shatuoOutflow".equals(v.getKey())).findFirst().get().getValues();
  assertEquals("2025-12-01 00:00:00",measured.get(0).getTime());
  assertEquals(0D,measured.get(0).getValue());
  assertEquals("DEFAULT_ZERO",measured.get(0).getSource());
  assertEquals(123D,measured.get(measured.size()-1).getValue());
  assertFalse(measuredInput.getModelParameters().containsKey("shatuoOutflowSeries"));
  DatangHourlyValue prior=new DatangHourlyValue();prior.setSenId(outflow.getSenId());prior.setTime(TankDailyForecastService.calendar(java.time.LocalDateTime.of(2025,12,1,0,0)));prior.setValue(88D);
  when(repo.queryLatestDailyValueBefore(eq(outflow.getSenId()),any())).thenReturn(Arrays.asList(prior));
  InputResponse crossYear=service.assembleTankGroupWithForecast(q).get(0);
  SeriesResponse out=crossYear.getSeries().stream().filter(v->"shatuoOutflow".equals(v.getKey())).findFirst().get();
  assertEquals("2025-11-30 00:00:00",out.getValues().get(0).getTime());assertEquals(88D,out.getValues().get(0).getValue());
  when(repo.queryLatestGfsRain(anyString(),eq(10051),any(),any(),any())).thenReturn(Collections.emptyList());
  for (InputResponse missing : service.assembleTankGroupWithForecast(q)) {
   if ("TANK_JIANGBIN_UPSTREAM".equals(missing.getStationCode())) {
    assertEquals(0D, missing.getForecastRainfall().getValues().get(0).getValue());
    continue;
   }
   assertEquals(0D,missing.getForecastRainfall().getValues().get(0).getValue());
   assertEquals("DEFAULT_ZERO", missing.getForecastRainfall().getValues().get(0).getSource());
  }
 }
}
