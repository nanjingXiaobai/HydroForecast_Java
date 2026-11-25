package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceSeriesDefinition;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.SeriesResponse;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
class TankMidnightHistoryTest {
    Calendar time(int hour,int minute,int second,int millis) {
        Calendar t=new GregorianCalendar(2026,3,25,hour,minute,second);t.set(Calendar.MILLISECOND,millis);return t;
    }
    DatangHourlyValue row(int hour,int minute,int second,int millis,double value) {
        DatangHourlyValue r=new DatangHourlyValue();r.setSenId("1442852573462784.M12000");
        Calendar stored=time(hour,minute,second,millis);stored.add(Calendar.DAY_OF_MONTH,1);r.setTime(stored);r.setValue(999D);r.setAverageValue(value);return r;
    }
    SeriesResponse assemble(DatangHourlyValue... rows) throws Exception {
        return assembleRole("rainfall", rows);
    }
    SeriesResponse assembleRole(String role, DatangHourlyValue... rows) throws Exception {
        Class<?> range=Class.forName(ForecastInputAssemblyService.class.getName()+"$TimeRange");
        Constructor<?> ctor=range.getDeclaredConstructor(Calendar.class,Calendar.class);ctor.setAccessible(true);
        Object window=ctor.newInstance(time(0,0,0,0),time(23,59,59,999));
        Method m=ForecastInputAssemblyService.class.getDeclaredMethod("tankHistorySeries",SourceSeriesDefinition.class,List.class,range);m.setAccessible(true);
        SourceSeriesDefinition d=new SourceSeriesDefinition();d.setSensorId("1442852573462784.M12000");d.setName("砚山");d.setRole(role);
        return (SeriesResponse)m.invoke(new ForecastInputAssemblyService(null,null,null),d,MeasuredTimeLabels.normalize(Arrays.asList(rows),Collections.singletonMap(d.getSensorId(), "rainfall".equals(role)),time(0,0,0,0),time(23,59,59,999),"day"),window);
    }
    @Test void rainUsesOnlyExactEight() throws Exception {
        SeriesResponse s=assemble(row(0,0,0,0,99),row(8,1,0,0,88),row(8,0,1,0,77),row(8,0,0,1,66),row(8,0,0,0,2));
        assertEquals(1,s.getValues().size());assertEquals(2D,s.getValues().get(0).getValue());
    }
    @Test void noEightRemainsMissingAndRealZeroIsKept() throws Exception {
        assertNull(assemble(row(0,0,0,0,99)).getValues().get(0).getValue());
        assertEquals(0D,assemble(row(8,0,0,0,0)).getValues().get(0).getValue());
    }
    @Test void missingAverageDoesNotFallBackToV() throws Exception {
        DatangHourlyValue r=row(8,0,0,0,2);r.setAverageValue(null);
        assertNull(assemble(r).getValues().get(0).getValue());
    }
    @Test void flowStillUsesMidnightV() throws Exception {
        assertEquals(999D,assembleRole("shatuoOutflow",row(0,0,0,0,2),row(8,0,0,0,3)).getValues().get(0).getValue());
        assertNull(assembleRole("shatuoOutflow",row(8,0,0,0,3)).getValues().get(0).getValue());
    }
    @Test void duplicateEightStillFails() {
        InvocationTargetException e=assertThrows(InvocationTargetException.class,()->assemble(row(8,0,0,0,2),row(8,0,0,0,3)));
        assertTrue(e.getCause().getMessage().contains("历史日数据重复"));
    }
}
