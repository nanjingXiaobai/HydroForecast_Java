package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StationConfigControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StationModelConfigProperties properties = new StationModelConfigProperties();
        properties.setFile(Paths.get("conf/forecast/station-model-config.xml").toAbsolutePath().toString());
        StationModelRegistry registry = new StationModelRegistry(properties, new StationModelConfigParser());
        registry.initialize();
        StationConfigController controller = new StationConfigController(new StationConfigQueryService(registry));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldReturnConfiguredStations() throws Exception {
        mockMvc.perform(get("/forecast/station-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.models.length()").value(3))
                .andExpect(jsonPath("$.data.stations.length()").value(10));
    }

    @Test
    void shouldReturnStationRoute() throws Exception {
        mockMvc.perform(get("/forecast/station-config/stations/REGRESSION_SHATUO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("沙沱"))
                .andExpect(jsonPath("$.data.modelRef").value("SHATUO_LSTM_MOS"));
    }

    @Test
    void shouldReturnBadRequestForUnknownStation() throws Exception {
        mockMvc.perform(get("/forecast/station-config/stations/UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("400"));
    }

}
