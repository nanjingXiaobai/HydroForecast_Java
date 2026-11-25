package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceDataProfileDefinition;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ApiForecastRainTest {
    Calendar time(int hour) { Calendar c=new GregorianCalendar(2026,10,5,hour,0,0);c.set(Calendar.MILLISECOND,0);return c; }
    @Test void missingAndPartialForecastAreEditableZeroWithoutLosingRain() {
        ForecastInputAssemblyService service=new ForecastInputAssemblyService(null,null,null);
        SourceDataProfileDefinition profile=new SourceDataProfileDefinition();
        RainfallResponse empty=service.apiForecastRain(profile,Collections.emptyList(),time(0),time(2),"P1H");
        assertEquals(3,empty.getValues().size());
        for(ValuePoint p:empty.getValues()) {assertEquals(0D,p.getValue());assertEquals("DEFAULT_ZERO",p.getSource());}
        GfsForecastRainValue row=new GfsForecastRainValue();row.setDataTime(time(1));row.setAveragePrecipitation(8D);
        RainfallResponse partial=service.apiForecastRain(profile,Collections.singletonList(row),time(0),time(2),"P1H");
        assertEquals(8D,partial.getValues().get(1).getValue());
        assertEquals(0D,partial.getValues().get(2).getValue());
        assertEquals("WEA_GFSFORRAIN.AVERPRE",partial.getValues().get(1).getSource());
    }
    @Test void emptyGfsAllowedForApiButOtherModelsStillFailAndSqlErrorsPropagate() throws Exception {
        ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
        ForecastInputAssemblyService service=new ForecastInputAssemblyService(null,repo,null);
        SourceDataProfileDefinition p=new SourceDataProfileDefinition();p.setForecastRegionId("1");p.setForecastSubtype(10050);
        Method m=ForecastInputAssemblyService.class.getDeclaredMethod("queryForecastRain",SourceDataProfileDefinition.class,Calendar.class,Calendar.class,Calendar.class,boolean.class);m.setAccessible(true);
        assertTrue(((List<?>)m.invoke(service,p,time(0),time(0),time(2),true)).isEmpty());
        assertThrows(InvocationTargetException.class,()->m.invoke(service,p,time(0),time(0),time(2),false));
        when(repo.queryLatestGfsRain(anyString(),anyInt(),any(),any(),any())).thenThrow(new IllegalStateException("database unavailable"));
        InvocationTargetException error=assertThrows(InvocationTargetException.class,()->m.invoke(service,p,time(0),time(0),time(2),true));
        assertEquals("database unavailable",error.getCause().getMessage());
    }
}
