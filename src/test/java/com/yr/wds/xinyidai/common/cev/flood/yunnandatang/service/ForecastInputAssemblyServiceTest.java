package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ModelDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceDataProfileDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.SourceSeriesDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfiguration;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.DatangHourlyValue;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.ForecastSourceDataRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ForecastInputAssemblyServiceTest {

    @Test
    public void shouldReadDailyDataUsingNaturalDayRange() throws Exception {
        StationModelRegistry registry = mock(StationModelRegistry.class);
        ForecastSourceDataRepository repository = mock(ForecastSourceDataRepository.class);
        StationDefinition station = station();
        SourceDataProfileDefinition profile = profile();
        StationModelConfiguration configuration = new StationModelConfiguration();
        configuration.setConfigVersion("test-v1");
        configuration.getSourceDataProfileMap().put("SHATUO_DATA", profile);
        when(registry.resolveRoute("SHATUO")).thenReturn(new StationModelRoute(station, model()));
        when(registry.getConfiguration()).thenReturn(configuration);

        DatangHourlyValue row = new DatangHourlyValue();
        row.setSenId("10001");
        row.setTime(calendar("2026-07-15 00:00:00"));
        row.setValue(8D);
        when(repository.queryHistoryValues(any(), any(), any(), eq("day")))
                .thenReturn(Collections.singletonList(row));

        ForecastContracts.InputQueryRequest request = new ForecastContracts.InputQueryRequest();
        request.setStationCode("SHATUO");
        request.setForecastTime("2026-07-22 08:00:00");
        ForecastContracts.InputResponse response = new ForecastInputAssemblyService(
                registry, repository, mock(ApiModelParameterService.class))
                .assemble(request, "REGRESSION_MODEL");

        assertEquals("2026-07-15 00:00:00", response.getHistoryBeginTime());
        assertEquals("2026-07-21 23:59:59", response.getHistoryEndTime());
        assertEquals("DAYDB", response.getSeries().get(0).getValues().get(0).getSource());

        ArgumentCaptor<Calendar> begin = ArgumentCaptor.forClass(Calendar.class);
        ArgumentCaptor<Calendar> end = ArgumentCaptor.forClass(Calendar.class);
        verify(repository).queryHistoryValues(eq(Collections.singletonList("10001")),
                begin.capture(), end.capture(), eq("day"));
        assertEquals("2026-07-15 00:00:00", format(begin.getValue()));
        assertEquals("2026-07-21 23:59:59", format(end.getValue()));
    }

    private StationDefinition station() {
        StationDefinition station = new StationDefinition();
        station.setCode("SHATUO");
        station.setName("沙沱");
        station.setModule("REGRESSION_MODEL");
        station.setDataProfileRef("SHATUO_DATA");
        station.setTimeStep("P1D");
        station.setHistorySteps(7);
        station.setForecastSteps(10);
        return station;
    }

    private ModelDefinition model() {
        ModelDefinition model = new ModelDefinition();
        model.setCode("SHATUO_LSTM_MOS");
        return model;
    }

    private SourceDataProfileDefinition profile() {
        SourceSeriesDefinition series = new SourceSeriesDefinition();
        series.setRole("shatuoOutflow");
        series.setName("沙沱出库");
        series.setSensorId("10001");
        SourceDataProfileDefinition profile = new SourceDataProfileDefinition();
        profile.setForecastTable("");
        profile.getSeries().add(series);
        return profile;
    }

    private Calendar calendar(String value) throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value));
        return calendar;
    }

    private String format(Calendar value) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value.getTime());
    }
}
