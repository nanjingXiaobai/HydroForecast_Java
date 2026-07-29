package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StationModelRegistryTest {

    @Test
    void shouldResolveEnabledStationRoute() {
        StationModelRegistry registry = createRegistry();

        StationModelRoute route = registry.resolveRoute("REGRESSION_SHATUO");

        assertEquals("沙沱", route.getStation().getName());
        assertEquals("SHATUO_LSTM_MOS", route.getModel().getCode());
        assertEquals(10, registry.getEnabledStations().size());
    }

    @Test
    void shouldRejectUnknownStation() {
        StationModelRegistry registry = createRegistry();
        assertThrows(StationModelConfigException.class, () -> registry.resolveRoute("UNKNOWN"));
    }

    @Test
    void shouldAllowApplicationStartupWhenOptionalConfigIsMissing() {
        StationModelConfigProperties properties = new StationModelConfigProperties();
        properties.setFile(missingConfigPath());
        StationModelRegistry registry = new StationModelRegistry(properties, new StationModelConfigParser());

        registry.initialize();

        assertThrows(StationModelConfigException.class, registry::getConfiguration);
    }

    @Test
    void shouldRejectMissingConfigWhenExplicitlyRequired() {
        StationModelConfigProperties properties = new StationModelConfigProperties();
        properties.setFile(missingConfigPath());
        properties.setRequired(true);
        StationModelRegistry registry = new StationModelRegistry(properties, new StationModelConfigParser());

        assertThrows(StationModelConfigException.class, registry::initialize);
    }

    private StationModelRegistry createRegistry() {
        StationModelConfigProperties properties = new StationModelConfigProperties();
        properties.setFile(Paths.get("conf/forecast/station-model-config.xml").toAbsolutePath().toString());
        StationModelRegistry registry = new StationModelRegistry(properties, new StationModelConfigParser());
        registry.initialize();
        return registry;
    }

    private String missingConfigPath() {
        return Paths.get("target", "missing-station-model-config-" + System.nanoTime() + ".xml")
                .toAbsolutePath().toString();
    }
}
