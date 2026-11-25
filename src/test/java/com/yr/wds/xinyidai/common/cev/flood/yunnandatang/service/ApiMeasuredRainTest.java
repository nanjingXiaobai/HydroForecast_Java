package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ApiMeasuredRainTest {
    @Test void usesOnlyAvgAndDoesNotMutateSource() {
        DatangHourlyValue row=new DatangHourlyValue();row.setValue(99D);row.setAverageValue(2D);
        assertEquals(2D,ForecastInputAssemblyService.apiMeasuredRain(Collections.singletonList(row)).get(0).getValue());
        assertEquals(99D,row.getValue());
        row.setAverageValue(0D);
        assertEquals(0D,ForecastInputAssemblyService.apiMeasuredRain(Collections.singletonList(row)).get(0).getValue());
        row.setAverageValue(null);
        assertNull(ForecastInputAssemblyService.apiMeasuredRain(Collections.singletonList(row)).get(0).getValue());
    }
}
