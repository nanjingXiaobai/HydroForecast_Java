package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastCalculationService;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastInputAssemblyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ForecastBusinessControllerTest {
    private MockMvc mockMvc;
    private ForecastInputAssemblyService inputService;
    private ForecastCalculationService calculationService;

    @BeforeEach
    void setUp() {
        inputService = Mockito.mock(ForecastInputAssemblyService.class);
        calculationService = Mockito.mock(ForecastCalculationService.class);
        ForecastBusinessController controller = new ForecastBusinessController(inputService, calculationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldAssembleInputAndCalculateInOneRequest() throws Exception {
        ForecastContracts.InputResponse input = new ForecastContracts.InputResponse();
        input.setStationCode("REGRESSION_SHATUO");
        input.setModelCode("SHATUO_LSTM_MOS");
        Mockito.when(inputService.assemble(any(), eq("REGRESSION_MODEL"))).thenReturn(input);

        mockMvc.perform(post("/forecast/regression/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stationCode\":\"REGRESSION_SHATUO\","
                                + "\"forecastTime\":\"2026-07-22 00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(inputService).assemble(any(), eq("REGRESSION_MODEL"));
        verify(calculationService).calculate(any(), eq("REGRESSION_MODEL"));
    }

    @Test
    void shouldRouteGenericCalculateByModelType() throws Exception {
        mockMvc.perform(post("/forecast/tank/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(calculationService).calculate(any(), eq("TANK_MODEL"));
    }

    @Test
    void shouldRejectUnknownModelType() throws Exception {
        mockMvc.perform(post("/forecast/unknown/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    void shouldNotExposeInputRoute() throws Exception {
        mockMvc.perform(post("/forecast/api/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
