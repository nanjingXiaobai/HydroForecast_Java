package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TankPlanOutflowTest {
    @Test void oldPageReceivesPlansThroughExistingField() {
        com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigProperties props = new com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigProperties();
        props.setFile("conf/forecast/station-model-config.xml");
        com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry registry = new com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry(props,
                new com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigParser());
        registry.initialize();
        com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.ForecastSourceDataRepository repo = org.mockito.Mockito.mock(com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.ForecastSourceDataRepository.class);
        com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue p = new com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue();
        p.setTime(TankDailyForecastService.calendar(java.time.LocalDateTime.of(2026,11,21,0,0))); p.setValue(112.66);
        org.mockito.Mockito.when(repo.queryShatuoDailyPlan(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any())).thenReturn(Collections.singletonList(p));
        InputQueryRequest q = new InputQueryRequest(); q.setForecastTime("2026-11-21 00:00:00");
        q.setForecastBeginTime("2026-11-21 00:00:00");q.setForecastEndTime("2026-11-23 00:00:00");
        InputResponse input = new ForecastInputAssemblyService(registry,repo,null).assembleTankGroup(q).get(0);
        assertFalse(input.getSeries().stream().anyMatch(s -> "shatuoPlannedOutflow".equals(s.getKey())));
        SeriesResponse out = input.getSeries().stream().filter(s -> "shatuoOutflow".equals(s.getKey())).findFirst().get();
        for(ValuePoint v : out.getValues()) if(v.getTime().compareTo(q.getForecastBeginTime()) >= 0) {
            assertEquals(112.66,v.getValue()); assertTrue(v.getSource().startsWith("PLANLOAD"));
        }
        List<ValuePoint> calculated = ForecastCalculationService.tankResultOutflow(input,Collections.emptyList());
        assertEquals(112.66,calculated.get(calculated.size()-1).getValue());
        assertEquals("PLANLOAD_CARRY_FORWARD",calculated.get(calculated.size()-1).getSource());
    }
    ValuePoint point(String day, double value) {
        ValuePoint p = new ValuePoint(); p.setTime("2026-11-" + day + " 00:00:00");
        p.setValue(value); p.setSource("PLANLOAD"); return p;
    }
    @Test void planZeroMissingDayAndManualOverride() {
        InputResponse input = new InputResponse();
        input.setForecastBeginTime("2026-11-21 00:00:00");
        input.setForecastEndTime("2026-11-24 00:00:00");
        SeriesResponse series = new SeriesResponse(); series.setKey("shatuoPlannedOutflow");
        series.setValues(Arrays.asList(point("21",112.66), point("22",0), point("24",146.3)));
        input.setSeries(Collections.singletonList(series));
        List<ValuePoint> result = ForecastCalculationService.tankResultOutflow(input, Collections.singletonList(point("24",200)));
        assertEquals(112.66,result.get(0).getValue());
        assertEquals(0D,result.get(1).getValue());
        assertEquals(0D,result.get(2).getValue());
        assertEquals("PLANLOAD_CARRY_FORWARD",result.get(2).getSource());
        assertEquals(200D,result.get(3).getValue());
    }
}
