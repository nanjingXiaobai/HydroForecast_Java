package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StationModelConfigParserTest {
    private final StationModelConfigParser parser = new StationModelConfigParser();

    @Test
    void shouldParseProductionConfiguration() throws Exception {
        try (InputStream inputStream = Files.newInputStream(
                Paths.get("conf/forecast/station-model-config.xml"))) {
            StationModelConfiguration configuration = parser.parse(inputStream);

        assertEquals("2026.07.24", configuration.getConfigVersion());
            assertEquals(3, configuration.getModels().size());
            assertEquals(10, configuration.getStations().size());
            assertEquals(10, configuration.getSourceDataProfiles().size());
            assertEquals("HOURDB",
                    configuration.getSourceDataProfileMap().get("API_YUJIANKOU_DATA").getHistoricalTable());
        assertEquals("SHATUO_LSTM_MOS",
                configuration.getStationMap().get("REGRESSION_SHATUO").getModelRef());
        assertEquals(4, configuration.getTankParameterProfiles().size());
        assertEquals("TO_TANK3", configuration.getTankParameterProfileMap()
                .get("TANK_JIANGBIN_UPSTREAM_V1").getModelParameters().get("routingMode"));
        assertEquals("DIRECT_RUNOFF", configuration.getTankParameterProfileMap()
                .get("TANK_GONGPENG_INTERVAL_V1").getModelParameters().get("routingMode"));
        assertEquals(5, configuration.getAggregationMap()
                .get("PENGSHUI_INFLOW_FORECAST").getSources().size());
        }
    }

    @Test
    void shouldRejectUnknownModelReference() {
        String xml = "<forecast-station-model-config schemaVersion=\"1\" configVersion=\"1\">"
                + "<models><model code=\"M1\" name=\"模型\" module=\"API_MODEL\"/></models>"
                + "<sourceDataProfiles/>"
                + "<stations><station code=\"S1\" name=\"站点\" module=\"API_MODEL\" modelRef=\"M2\"/></stations>"
                + "</forecast-station-model-config>";

        assertThrows(StationModelConfigException.class, () -> parse(xml));
    }

    @Test
    void shouldParseSensorAndGfsMappingFromXml() {
        StationModelConfiguration configuration = parse(configXml(
                "mappingStatus=\"verified\" forecastTable=\"WEA_GFSFORRAIN\" "
                        + "forecastRegionId=\"REGION_1\" forecastSubtype=\"2\"",
                "sensorId=\"RAIN_SENSOR_1\""));

        SourceDataProfileDefinition profile = configuration.getSourceDataProfileMap().get("P1");
        assertEquals("REGION_1", profile.getForecastRegionId());
        assertEquals(Integer.valueOf(2), profile.getForecastSubtype());
        assertEquals("RAIN_SENSOR_1", profile.getSeries().get(0).getSensorId());
    }

    @Test
    void shouldRejectIncompleteVerifiedMapping() {
        String xml = configXml("mappingStatus=\"verified\" forecastTable=\"WEA_GFSFORRAIN\"", "");
        assertThrows(StationModelConfigException.class, () -> parse(xml));
    }

    @Test
    void shouldRejectDoctype() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE config [<!ENTITY xxe SYSTEM \"file:///tmp/a\">]>"
                + "<forecast-station-model-config schemaVersion=\"1\" configVersion=\"1\">"
                + "<models/><stations/></forecast-station-model-config>";

        assertThrows(StationModelConfigException.class, () -> parse(xml));
    }

    private StationModelConfiguration parse(String xml) {
        return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String configXml(String profileAttributes, String seriesAttributes) {
        return "<forecast-station-model-config schemaVersion=\"1\" configVersion=\"1\">"
                + "<models><model code=\"M1\" name=\"模型\" module=\"API_MODEL\"/></models>"
                + "<sourceDataProfiles><profile code=\"P1\" name=\"数据\" historicalTable=\"HOURDB\" "
                + profileAttributes + "><series role=\"rainfall\" name=\"雨量站\" "
                + seriesAttributes + "/></profile></sourceDataProfiles>"
                + "<stations><station code=\"S1\" name=\"站点\" module=\"API_MODEL\" "
                + "modelRef=\"M1\" dataProfileRef=\"P1\" curveName=\"测试水库\"/></stations>"
                + "</forecast-station-model-config>";
    }
}
