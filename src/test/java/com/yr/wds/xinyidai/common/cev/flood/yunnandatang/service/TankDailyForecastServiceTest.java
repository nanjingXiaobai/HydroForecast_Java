package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TankDailyForecastServiceTest {
    @Test void scheduleUsesUpdatedXmlTimeAndSwitch() {
        StationModelConfiguration c=new StationModelConfiguration();
        LocalDateTime now=LocalDateTime.of(2026,11,4,23,0);
        assertFalse(TankDailyForecastService.isDue(c,now));
        c.setTankAutoEnabled(true);
        assertTrue(TankDailyForecastService.isDue(c,now));
        c.setTankAutoTime("22:30");
        assertFalse(TankDailyForecastService.isDue(c,now));
        assertTrue(TankDailyForecastService.isDue(c,now.withHour(22).withMinute(30)));
    }
    @Test void productionXmlContainsConfirmedMappings() throws Exception {
        try(java.io.InputStream stream=java.nio.file.Files.newInputStream(java.nio.file.Paths.get("conf/forecast/station-model-config.xml"))) {
            StationModelConfiguration config=new StationModelConfigParser().parse(stream);
            assertTrue(config.isTankAutoEnabled());
            assertEquals("23:00",config.getTankAutoTime());
            Map<String,String> expected=new HashMap<>();
            expected.put("TANK_JIANGBIN_UPSTREAM","2000000007");
            expected.put("TANK_DAHEKOU_UPSTREAM","2000000006");
            expected.put("TANK_YANGONG_INTERVAL","2000000004");
            expected.put("TANK_GONGPENG_INTERVAL","2000000005");
            int count=0;
            for(StationDefinition station:config.getStations()) {
                if(!expected.containsKey(station.getCode())) continue;
                SourceDataProfileDefinition p=config.getSourceDataProfileMap().get(station.getDataProfileRef());
                assertEquals(expected.get(station.getCode()),p.getForecastRegionId());
                assertEquals(Integer.valueOf(10051),p.getForecastSubtype());
                double sum=0;
                for(SourceSeriesDefinition series:p.getSeries()) {
                    assertTrue(series.getSensorId().endsWith(".M12000")); sum+=series.getWeight();
                }
                assertEquals(1D,sum,1e-10); count++;
            }
            assertEquals(4,count);
        }
    }
    private GfsForecastRainValue row(LocalDate day,double value) {
        GfsForecastRainValue r=new GfsForecastRainValue();
        r.setDataTime(TankDailyForecastService.calendar(day.atStartOfDay()));
        r.setAveragePrecipitation(value); return r;
    }
    @Test void tenDaysAcrossYearAndZeroRain() {
        LocalDate begin=LocalDate.of(2026,12,28);
        List<GfsForecastRainValue> rows=new ArrayList<>();
        for(int i=0;i<10;i++) rows.add(row(begin.plusDays(i),i));
        RainfallResponse rain=TankDailyForecastService.dailyRain(rows,"2000000007",10051,begin,begin.plusDays(9));
        assertEquals(10,rain.getValues().size());
        assertEquals(0D,rain.getValues().get(0).getValue());
        assertEquals("2027-01-06 00:00:00",rain.getValues().get(9).getTime());
    }
    @Test void missingDuplicateAndInvalidFail() {
        LocalDate d=LocalDate.of(2026,11,4);
        assertThrows(StationModelConfigException.class,()->TankDailyForecastService.dailyRain(
                Collections.singletonList(row(d,1)),"r",10051,d,d.plusDays(1)));
        assertThrows(StationModelConfigException.class,()->TankDailyForecastService.dailyRain(
                Arrays.asList(row(d,1),row(d,2)),"r",10051,d,d));
        assertThrows(StationModelConfigException.class,()->TankDailyForecastService.dailyRain(
                Collections.singletonList(row(d,-1)),"r",10051,d,d));
    }
    @Test void runUsesExistingCalculationAndSave() {
        StationModelRegistry registry=mock(StationModelRegistry.class,RETURNS_DEEP_STUBS);
        ForecastInputAssemblyService assembly=mock(ForecastInputAssemblyService.class);
        ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
        ForecastCalculationService calculation=mock(ForecastCalculationService.class);
        ForecastResultService results=mock(ForecastResultService.class);
        List<InputResponse> inputs=new ArrayList<>(); LocalDate day=LocalDate.of(2026,11,4);
        List<GfsForecastRainValue> rows=new ArrayList<>();
        for(int i=0;i<10;i++) rows.add(row(day.plusDays(i),i));
        for(int i=0;i<4;i++) {
            String code="B"+i; InputResponse input=new InputResponse(); input.setStationCode(code); inputs.add(input);
            input.setForecastBeginTime(day+" 00:00:00");
            input.setForecastEndTime(day.plusDays(9)+" 00:00:00");
            when(registry.resolveRoute(code).getStation().getDataProfileRef()).thenReturn(code);
            when(registry.resolveRoute(code).getStation().getParameterProfileRef()).thenReturn(code);
            when(registry.getConfiguration().getTankParameterProfileMap().get(code)).thenReturn(new TankParameterProfileDefinition());
            SourceDataProfileDefinition profile=new SourceDataProfileDefinition();
            profile.setForecastRegionId("R"+i); profile.setForecastSubtype(10051);
            when(registry.getConfiguration().getSourceDataProfileMap().get(code)).thenReturn(profile);
        }
        when(assembly.assembleTankGroup(any())).thenReturn(inputs);
        when(repo.queryLatestGfsRain(anyString(),eq(10051),any(),any(),any())).thenReturn(rows);
        CalculateResponse response=new CalculateResponse(); response.setStationCode("TANK_PENGSHUI_INFLOW");
        response.setModelCode("THREE_LAYER_TANK");
        List<Map<String,Object>> basins=new ArrayList<>();
        for(InputResponse input:inputs) {
            Map<String,Object> basin=new HashMap<>(); basin.put("stationCode",input.getStationCode());
            List<Map<String,Object>> table=new ArrayList<>();
            for(int i=0;i<10;i++) {
                Map<String,Object> row=new HashMap<>(); row.put("time",day.plusDays(i)+" 00:00:00"); row.put("flow",1D); table.add(row);
            }
            basin.put("tableData",table); basins.add(basin);
        }
        response.setResultData(Collections.singletonMap("basinDetails",basins));
        when(calculation.calculate(any(),eq("TANK_MODEL"))).thenReturn(response);
        TankDailyForecastService service=new TankDailyForecastService(registry,assembly,repo,calculation,results);
        service.run(day.atTime(23,0));
        verify(assembly).assembleTankGroup(argThat(q->q.getForecastBeginTime().equals("2026-11-04 00:00:00")
                && q.getForecastEndTime().equals("2026-11-13 23:59:59")));
        verify(results).save(argThat(s->s.getInputs().size()==4 && s.getCalculationId().equals("TANK_AUTO_2026-11-04")
                && s.getForecaster().equals("系统自动预报") && s.getResult()==response.getResultData()));
        assertEquals("R0",inputs.get(0).getModelParameters().get("forecastRegionId"));
        assertEquals(15D,((Map<?,?>)inputs.get(0).getModelParameters().get("initialState")).get("d"));
        List<?> firstTable=(List<?>)basins.get(0).get("tableData");
        Object firstRow=firstTable.remove(0);
        clearInvocations(results);
        assertThrows(StationModelConfigException.class,()->service.run(day.atTime(23,0)));
        verifyNoInteractions(results);
        ((List<Object>)firstTable).add(0,firstRow);
        // 缺部分日期、空日值、全部缺雨均继续调用计算并保存；真实0与补0可区分。
        GfsForecastRainValue nullRow=row(day.plusDays(2),0);
        nullRow.setAveragePrecipitation(null);
        List<GfsForecastRainValue> partial=Arrays.asList(row(day,0),row(day.plusDays(1),12.5),nullRow);
        for(List<GfsForecastRainValue> missing:Arrays.asList(partial,Collections.<GfsForecastRainValue>emptyList())) {
            when(repo.queryLatestGfsRain(anyString(),eq(10051),any(),any(),any())).thenReturn(missing);
            clearInvocations(results,calculation);
            service.run(day.atTime(23,0));
            verify(calculation).calculate(any(),eq("TANK_MODEL"));
            verify(results).save(any());
            for(InputResponse input:inputs) {
                List<ValuePoint> points=input.getForecastRainfall().getValues();
                assertEquals(10,points.size());
                for(int i=0;i<10;i++) {
                    assertEquals(day.plusDays(i)+" 00:00:00",points.get(i).getTime());
                    assertEquals(!missing.isEmpty() && i==1 ? 12.5D : 0D,points.get(i).getValue());
                    assertEquals(!missing.isEmpty() && i<2 ? "WEA_GFSFORRAIN.AVERPRE" : "DEFAULT_ZERO",points.get(i).getSource());
                }
            }
        }
    }
}
