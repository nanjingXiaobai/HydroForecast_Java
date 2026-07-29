package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.AggregationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.AggregationSourceDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ModelDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfiguration;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRegistry;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelRoute;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.TankParameterProfileDefinition;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.model.PythonModelHttpClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ForecastCalculationServiceTest {

    @Test
    public void shouldReturnApiChartDataWithCompleteTimeAxis() {
        StationModelRegistry registry = mock(StationModelRegistry.class);
        PythonModelHttpClient modelClient = mock(PythonModelHttpClient.class);
        ApiModelParameterService parameterService = mock(ApiModelParameterService.class);
        StationDefinition station = station("API_TEST", "API_PPAR_UH", "");
        station.setModule("API_MODEL");
        ModelDefinition model = new ModelDefinition();
        model.setCode("API_PPAR_UH");
        model.setModule("API_MODEL");
        when(registry.resolveRoute("API_TEST")).thenReturn(new StationModelRoute(station, model));
        Map<String, Object> databaseParameters = unitParameters("DEFAULT_1", 1D);
        Map<String, Object> selectedParameters = unitParameters("DEFAULT_2", 2D);
        selectedParameters.remove("unit_hydrograph_options");
        when(parameterService.load(any(), any())).thenReturn(databaseParameters);
        when(parameterService.applyUnitSelection(any(), any())).thenReturn(selectedParameters);

        Map<String, Object> pythonModelResult = new LinkedHashMap<String, Object>();
        pythonModelResult.put("net_rainfall_series", Arrays.asList(1D, 2D));
        pythonModelResult.put("discharge_series", Arrays.asList(10D, 20D, 30D));
        Map<String, Object> pythonData = new LinkedHashMap<String, Object>();
        pythonData.put("modelResult", pythonModelResult);
        when(modelClient.invoke(any(), any())).thenReturn(pythonData);

        ForecastContracts.InputResponse input = new ForecastContracts.InputResponse();
        input.setStationCode("API_TEST");
        input.setModelCode("API_PPAR_UH");
        input.setForecastTime("2026-07-22 00:00:00");
        input.setSeries(Arrays.asList(rainfallSeries("A", 0.4D, 5D),
                rainfallSeries("B", 0.6D, 5D)));
        ForecastContracts.RainfallResponse forecast = new ForecastContracts.RainfallResponse();
        forecast.setValues(Collections.singletonList(point("2026-07-22 01:00:00", 9D)));
        input.setForecastRainfall(forecast);
        ForecastContracts.CalculateRequest request = new ForecastContracts.CalculateRequest();
        request.setInput(input);
        request.setParameters(Collections.<String, Object>singletonMap("unitHydrographCode", "DEFAULT_2"));

        ForecastContracts.CalculateResponse response = new ForecastCalculationService(
                registry, modelClient, parameterService).calculate(request, "API_MODEL");
        List<Map<String, Object>> chartData = chartData(response);
        assertChartRow(chartData, 0, "2026-07-22 00:00:00", "rainfall", 5D);
        assertChartRow(chartData, 1, "2026-07-22 01:00:00", "netRainfall", 2D);
        assertChartRow(chartData, 2, "2026-07-22 02:00:00", "inflow", 30D);
        assertEquals("DEFAULT_2", response.getInputData().getModelParameters().get("unit_hydrograph_code"));
        assertEquals(2, ((List<?>) response.getInputData().getModelParameters()
                .get("unit_hydrograph_options")).size());
        assertEquals(2, response.getResultData().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> pageParameters = (Map<String, Object>) response.getResultData().get("modelParameters");
        assertEquals(2, pageParameters.size());
        assertEquals("DEFAULT_2", pageParameters.get("unit_hydrograph_code"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pageOptions =
                (List<Map<String, Object>>) pageParameters.get("unit_hydrograph_options");
        assertEquals(2, pageOptions.size());
        assertFalse(pageOptions.get(0).containsKey("curveId"));
    }

    @Test
    public void shouldRunFourTankBasinsAndCalculatePengshuiInflow() {
        StationModelRegistry registry = mock(StationModelRegistry.class);
        PythonModelHttpClient modelClient = mock(PythonModelHttpClient.class);
        StationModelConfiguration configuration = configuration();
        when(registry.getConfiguration()).thenReturn(configuration);
        for (String code : Arrays.asList("TANK_A", "TANK_B", "TANK_C", "TANK_D")) {
            when(registry.resolveRoute(code)).thenReturn(new StationModelRoute(
                    configuration.getStationMap().get(code),
                    configuration.getModelMap().get("THREE_LAYER_TANK")));
        }
        when(modelClient.invoke(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = invocation.getArgument(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) request.get("payload");
            String basin = String.valueOf(payload.get("basinCode"));
            double flow = "TANK_A".equals(basin) ? 10D : "TANK_B".equals(basin) ? 20D
                    : "TANK_C".equals(basin) ? 30D : 40D;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("time", "2026-07-22 00:00:00");
            row.put("flow", flow);
            row.put("pythonDebugData", "不应返回页面");
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("forecastSeries", Collections.singletonList(row));
            return data;
        });

        ForecastContracts.CalculateRequest request = new ForecastContracts.CalculateRequest();
        List<ForecastContracts.InputResponse> inputs = new ArrayList<ForecastContracts.InputResponse>();
        for (String code : Arrays.asList("TANK_A", "TANK_B", "TANK_C", "TANK_D")) {
            inputs.add(input(code));
        }
        request.setInputs(inputs);
        request.setShatuoOutflowSeries(Collections.singletonList(point(50D)));

        ForecastContracts.CalculateResponse response = new ForecastCalculationService(
                registry, modelClient, mock(ApiModelParameterService.class))
                .calculate(request, "TANK_MODEL");
        Map<String, Object> result = response.getResultData();
        assertEquals(2, result.size());
        assertEquals(4, response.getInputDataList().size());
        verify(modelClient, times(4)).invoke(any(), any());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> basinDetails = (List<Map<String, Object>>) result.get("basinDetails");
        assertEquals(4, basinDetails.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameterRows =
                (List<Map<String, Object>>) basinDetails.get(0).get("modelParameters");
        assertEquals(4, parameterRows.size());
        assertEquals(2.7D, parameterRows.get(0).get("evaporation"));
        assertEquals(1000D, parameterRows.get(0).get("flow"));
        assertEquals(2, parameterRows.get(0).size());
        assertEquals(1.5D, parameterRows.get(1).get("evaporation"));
        assertEquals(0.15D, ((Map<?, ?>) parameterRows.get(1).get("tank1")).get("upperHole"));
        assertEquals(70D, ((Map<?, ?>) parameterRows.get(2).get("tank1")).get("upperHole"));
        assertEquals(10D, parameterRows.get(3).get("evaporation"));
        assertEquals(1, parameterRows.get(3).size());
        assertEquals(35D, ((Map<?, ?>) basinDetails.get(0).get("initialState")).get("d"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tableData =
                (List<Map<String, Object>>) basinDetails.get(0).get("tableData");
        assertEquals(2, tableData.get(0).size());
        assertFalse(tableData.get(0).containsKey("pythonDebugData"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chartData = (List<Map<String, Object>>) result.get("chartData");
        assertChartRow(chartData, 0, "2026-07-22 00:00:00", "regionalFlowTotal", 100D);
        assertEquals(50D, chartData.get(0).get("shatuoOutflow"));
        assertEquals(150D, chartData.get(0).get("pengshuiInflow"));
        assertFalse(result.containsKey("basinResults"));
        assertFalse(result.containsKey("regionalFlowSeries"));
        assertFalse(result.containsKey("pengshuiInflowSeries"));
    }

    @Test
    public void shouldMergeRegressionFieldsByTime() {
        StationModelRegistry registry = mock(StationModelRegistry.class);
        PythonModelHttpClient modelClient = mock(PythonModelHttpClient.class);
        StationDefinition station = station("REGRESSION_SHATUO", "SHATUO_LSTM_MOS", "");
        station.setModule("REGRESSION_MODEL");
        ModelDefinition model = new ModelDefinition();
        model.setCode("SHATUO_LSTM_MOS");
        model.setModule("REGRESSION_MODEL");
        when(registry.resolveRoute("REGRESSION_SHATUO"))
                .thenReturn(new StationModelRoute(station, model));
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("time", "2026-07-22 00:00:00");
        row.put("rawFlow", 100D);
        row.put("mosCorrection", 5D);
        row.put("flow", 105D);
        when(modelClient.invoke(any(), any())).thenReturn(Collections.<String, Object>singletonMap(
                "forecastSeries", Collections.singletonList(row)));
        ForecastContracts.InputResponse input = new ForecastContracts.InputResponse();
        input.setStationCode("REGRESSION_SHATUO");
        input.setModelCode("SHATUO_LSTM_MOS");
        input.setForecastTime("2026-07-22 08:00:00");
        ForecastContracts.SeriesResponse outflow = new ForecastContracts.SeriesResponse();
        outflow.setKey("shatuoOutflow");
        ForecastContracts.ValuePoint history = point("2026-07-21 00:00:00", 90D);
        history.setAverageValue(80D);
        ForecastContracts.ValuePoint future = point("2026-07-22 00:00:00", null);
        future.setAverageValue(81D);
        outflow.setValues(Arrays.asList(history, future));
        input.getSeries().add(outflow);
        ForecastContracts.CalculateRequest request = new ForecastContracts.CalculateRequest();
        request.setInput(input);

        ForecastContracts.CalculateResponse response = new ForecastCalculationService(
                registry, modelClient, mock(ApiModelParameterService.class))
                .calculate(request, "REGRESSION_MODEL");
        List<Map<String, Object>> chartData = chartData(response);
        assertChartRow(chartData, 0, "2026-07-21 00:00:00", "shatuoOutflow", 90D);
        assertEquals(80D, chartData.get(0).get("shatuoOutflowAverage"));
        assertEquals(Boolean.FALSE, chartData.get(0).get("forecast"));
        assertChartRow(chartData, 1, "2026-07-22 00:00:00", "shatuoOutflow", 105D);
        assertEquals(81D, chartData.get(1).get("shatuoOutflowAverage"));
        assertEquals(Boolean.TRUE, chartData.get(1).get("forecast"));
        assertEquals(1, response.getResultData().size());
        assertFalse(response.getResultData().containsKey("sourceData"));
        assertFalse(chartData.get(1).containsKey("rawForecast"));
        assertFalse(chartData.get(1).containsKey("mosCorrection"));
        assertFalse(chartData.get(1).containsKey("finalForecast"));
        assertFalse(response.getResultData().containsKey("forecastSeries"));
        assertFalse(response.getResultData().containsKey("metadata"));
    }

    private StationModelConfiguration configuration() {
        StationModelConfiguration configuration = new StationModelConfiguration();
        ModelDefinition model = new ModelDefinition();
        model.setCode("THREE_LAYER_TANK");
        model.setModule("TANK_MODEL");
        configuration.getModels().add(model);
        configuration.getModelMap().put(model.getCode(), model);
        AggregationDefinition aggregation = new AggregationDefinition();
        aggregation.setCode("PENGSHUI");
        aggregation.setDisplayName("加权平均");
        aggregation.setOperation("sum");
        for (String code : Arrays.asList("TANK_A", "TANK_B", "TANK_C", "TANK_D")) {
            StationDefinition station = station(code, model.getCode(), code + "_PROFILE");
            configuration.getStations().add(station);
            configuration.getStationMap().put(code, station);
            TankParameterProfileDefinition profile = new TankParameterProfileDefinition();
            profile.setCode(code + "_PROFILE");
            profile.getModelParameters().put("routingMode", "TANK_A".equals(code) || "TANK_B".equals(code)
                    ? "TO_TANK3" : "DIRECT_RUNOFF");
            profile.getModelParameters().put("areaFactor", 1000D);
            Map<String, Object> evaporation = new LinkedHashMap<String, Object>();
            evaporation.put("threshold1", 2.7D);
            evaporation.put("threshold2", 10D);
            evaporation.put("value1", 2.7D);
            evaporation.put("value2", 1.5D);
            evaporation.put("value3", 1D);
            profile.getModelParameters().put("evaporation", evaporation);
            Map<String, Object> tank1 = new LinkedHashMap<String, Object>();
            tank1.put("upperCoefficient", 0.15D);
            tank1.put("upperThreshold", 70D);
            tank1.put("middleCoefficient", 0.15D);
            tank1.put("middleThreshold", 50D);
            tank1.put("lowerCoefficient", 0.15D);
            tank1.put("lowerThreshold", 20D);
            tank1.put("infiltrationCoefficient", 0.15D);
            profile.getModelParameters().put("tank1", tank1);
            configuration.getTankParameterProfileMap().put(profile.getCode(), profile);
            AggregationSourceDefinition source = new AggregationSourceDefinition();
            source.setStationCode(code);
            source.setPoint("");
            aggregation.getSources().add(source);
        }
        StationDefinition target = station("TANK_PENGSHUI_INFLOW", model.getCode(), "");
        target.setAggregationRef(aggregation.getCode());
        configuration.getStations().add(target);
        configuration.getStationMap().put(target.getCode(), target);
        AggregationSourceDefinition shatuo = new AggregationSourceDefinition();
        shatuo.setStationCode(target.getCode());
        shatuo.setPoint("shatuoOutflow");
        aggregation.getSources().add(shatuo);
        configuration.getAggregations().add(aggregation);
        configuration.getAggregationMap().put(aggregation.getCode(), aggregation);
        return configuration;
    }

    private StationDefinition station(String code, String modelCode, String profile) {
        StationDefinition station = new StationDefinition();
        station.setCode(code);
        station.setName(code);
        station.setModule("TANK_MODEL");
        station.setModelRef(modelCode);
        station.setParameterProfileRef(profile);
        station.setEnabled(true);
        return station;
    }

    private ForecastContracts.InputResponse input(String code) {
        ForecastContracts.InputResponse input = new ForecastContracts.InputResponse();
        input.setStationCode(code);
        input.setModelCode("THREE_LAYER_TANK");
        input.setForecastTime("2026-07-22 00:00:00");
        ForecastContracts.SeriesResponse series = new ForecastContracts.SeriesResponse();
        series.setKey("rainfall");
        series.setWeight(1D);
        series.setValues(Collections.singletonList(point(5D)));
        input.setSeries(Collections.singletonList(series));
        return input;
    }

    private ForecastContracts.ValuePoint point(Double value) {
        return point("2026-07-22 00:00:00", value);
    }

    private ForecastContracts.SeriesResponse rainfallSeries(String name, Double weight, Double value) {
        ForecastContracts.SeriesResponse series = new ForecastContracts.SeriesResponse();
        series.setKey("rainfall");
        series.setName(name);
        series.setWeight(weight);
        series.setValues(Collections.singletonList(point(value)));
        return series;
    }

    private ForecastContracts.ValuePoint point(String time, Double value) {
        ForecastContracts.ValuePoint point = new ForecastContracts.ValuePoint();
        point.setTime(time);
        point.setValue(value);
        return point;
    }

    private Map<String, Object> unitParameters(String selectedCode, Double selectedValue) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("unit_hydrograph_code", selectedCode);
        parameters.put("unit_hydrograph", Collections.singletonList(selectedValue));
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        options.add(unitOption("DEFAULT_1", 1D));
        options.add(unitOption("DEFAULT_2", 2D));
        parameters.put("unit_hydrograph_options", options);
        return parameters;
    }

    private Map<String, Object> unitOption(String code, Double value) {
        Map<String, Object> option = new LinkedHashMap<String, Object>();
        option.put("code", code);
        option.put("name", code);
        option.put("values", Collections.singletonList(value));
        option.put("curveId", "CURVE_" + code);
        return option;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chartData(ForecastContracts.CalculateResponse response) {
        return (List<Map<String, Object>>) response.getResultData().get("chartData");
    }

    private void assertChartRow(List<Map<String, Object>> rows, int index, String time,
                                String field, Double value) {
        assertEquals(time, rows.get(index).get("time"));
        assertEquals(value, rows.get(index).get(field));
    }
}
