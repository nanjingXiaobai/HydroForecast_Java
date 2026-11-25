package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.model.PythonModelHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;import java.io.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
@EnabledIfSystemProperty(named="offline.models",matches="true")
class MissingDataOfflineIntegrationTest {
 @Test void allMissingInputsStillCalculateWithRealModelEngines() throws Exception {
  StationModelConfigProperties props=new StationModelConfigProperties();props.setFile("conf/forecast/station-model-config.xml");
  StationModelRegistry registry=new StationModelRegistry(props,new StationModelConfigParser());registry.initialize();
  ForecastSourceDataRepository repo=mock(ForecastSourceDataRepository.class);
  when(repo.queryHistoryValues(anyList(),any(),any(),anyString())).thenReturn(Collections.emptyList());
  when(repo.queryLatestGfsRain(anyString(),anyInt(),any(),any(),any())).thenReturn(Collections.emptyList());
  ApiModelParameterService parameters=mock(ApiModelParameterService.class);
  ObjectMapper mapper=new ObjectMapper();
  Map<String,Object> fixture=mapper.readValue(getClass().getResourceAsStream("/api-excel/API_YUJIANKOU.json"),Map.class);
  when(parameters.load(any(),anyString())).thenReturn(fixture);
  when(parameters.applyUnitSelection(anyMap(),anyMap())).thenAnswer(c->new LinkedHashMap<>((Map)c.getArgument(0)));
  MockEnvironment env=new MockEnvironment();
  for(String code:Arrays.asList("TANK_JIANGBIN_UPSTREAM","REGRESSION_SHATUO")) env.setProperty(registry.resolveRoute(code).getModel().getConfigPrefix()+".base-url",code.startsWith("TANK")?"http://127.0.0.1:18081":"http://127.0.0.1:18083");
  ForecastInputAssemblyService assembly=new ForecastInputAssemblyService(registry,repo,parameters);
  ForecastCalculationService service=new ForecastCalculationService(registry,new PythonModelHttpClient(env),parameters);
  File directory=new File("outputs/missing-zero-20261107");directory.mkdirs();
  for(String module:Arrays.asList("API_MODEL","TANK_MODEL","REGRESSION_MODEL")) {
   InputQueryRequest query=new InputQueryRequest();query.setStationCode(module.equals("API_MODEL")?"API_YUJIANKOU":module.equals("TANK_MODEL")?"TANK_PENGSHUI_INFLOW":"REGRESSION_SHATUO");
   query.setForecastTime("2026-11-07 00:00:00");if(!module.equals("REGRESSION_MODEL")){query.setForecastBeginTime(query.getForecastTime());query.setForecastEndTime("2026-11-09 00:00:00");}
   CalculateRequest request=new CalculateRequest();
   if(module.equals("TANK_MODEL"))request.setInputs(assembly.assembleTankGroupWithForecast(query));else request.setInput(assembly.assemble(query,module));
   CalculateResponse response=service.calculate(request,module);
   List<Map<String,Object>> rows=(List<Map<String,Object>>)response.getResultData().get("chartData");assertFalse(rows.isEmpty());
   for(Map<String,Object> row:rows)for(Object value:row.values()) if(value instanceof Number)assertTrue(Double.isFinite(((Number)value).doubleValue()));
   mapper.writeValue(new File(directory,module+".json"),response);
  }
 }
}
