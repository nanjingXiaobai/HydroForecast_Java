package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ForecastResultPointTest {
    StationModelConfiguration config() throws Exception {
        try(InputStream in=Files.newInputStream(Paths.get("conf/forecast/station-model-config.xml"))) {
            return new StationModelConfigParser().parse(in);
        }
    }
    ForecastDailyRows.Row row(String station,String kind,String code) {
        ForecastDailyRows.Row r=new ForecastDailyRows.Row(); r.station=station;r.kind=kind;r.code=code;r.step=1440;
        r.day="2026-11-09";r.present[0]=true;r.values[0]=754.6;return r;
    }
    @Test void allTenPointsAndAllResultIndicators() throws Exception {
        StationModelConfiguration c=config();
        String[][] expected={{"API_YUJIANKOU","API","discharge","21400102031010"},{"API_JIAOMUTANG","API","discharge","16300102031004"},
        {"API_TENGZIGOU_UPSTREAM","API","discharge","21100102031004"},{"API_TENGZIGOU_NIULANKOU","API","discharge","21200102031010"},
        {"TANK_JIANGBIN_UPSTREAM","TANK","flow","11802203021004"},{"TANK_DAHEKOU_UPSTREAM","TANK","flow","15400103021004"},
        {"TANK_YANGONG_INTERVAL","TANK","flow","11802403021010"},{"TANK_GONGPENG_INTERVAL","TANK","flow","11800103021028"},
        {"TANK_PENGSHUI_INFLOW","PENGSHUI","regionalFlowTotal","11800103021010"},{"TANK_PENGSHUI_INFLOW","PENGSHUI","pengshuiInflow","11800103021004"}};
        for(String[] e:expected){ForecastDailyRows.Row r=row(e[0],e[1],e[2]);ForecastDailyRows.applyResultPoints(Arrays.asList(r),c);assertEquals(e[3],r.idAttrCode);}
        for(String field:Arrays.asList(ForecastDailyRows.TANK_FIELDS)) {
            ForecastDailyRows.Row r=row("TANK_JIANGBIN_UPSTREAM","TANK",field);ForecastDailyRows.applyResultPoints(Arrays.asList(r),c);assertEquals("11802203021004",r.idAttrCode);
        }
        for (String field : ForecastDailyRows.API_FIELDS) {
            ForecastDailyRows.Row r=row("API_YUJIANKOU","API",field);
            ForecastDailyRows.applyResultPoints(Arrays.asList(r),c);
            assertEquals("21400102031010",r.idAttrCode);
            assertEquals(field,r.code);
        }
        ForecastDailyRows.Row input=row("TANK_JIANGBIN_UPSTREAM","INPUT","actualValue"); input.senid="measured-point";
        ForecastDailyRows.applyResultPoints(Arrays.asList(input),c);assertNull(input.idAttrCode);assertEquals("measured-point",input.senid);
        ForecastDailyRows.Row missing=row("TANK_PENGSHUI_INFLOW","PENGSHUI","shatuoOutflow");ForecastDailyRows.applyResultPoints(Arrays.asList(missing),c);assertNull(missing.idAttrCode);
    }
    @Test void configurationChangesOnlyFutureSaves() throws Exception {
        StationModelConfiguration c=config();ForecastDailyRows.Row old=row("API_YUJIANKOU","API","discharge");
        ForecastDailyRows.applyResultPoints(Arrays.asList(old),c);
        c.getStationMap().get(old.station).getResultPoints().put("API.*","12345");
        ForecastDailyRows.Row next=row(old.station,old.kind,old.code);ForecastDailyRows.applyResultPoints(Arrays.asList(next),c);
        assertEquals("21400102031010",old.idAttrCode);assertEquals("12345",next.idAttrCode);
    }
    @Test void jdbcWritesPointAndKeepsOriginalDateAndValue() throws Exception {
        StationModelConfiguration c=config();ForecastDailyRows.Row r=row("TANK_PENGSHUI_INFLOW","PENGSHUI","pengshuiInflow");
        ForecastDailyRows.applyResultPoints(Arrays.asList(r),c);
        Connection connection=mock(Connection.class);PreparedStatement ps=mock(PreparedStatement.class);
        final String[] sql={null};final Map<Integer,Object> params=new HashMap<>();
        when(connection.prepareStatement(anyString())).thenAnswer(a->{sql[0]=a.getArgument(0);return ps;});
        doAnswer(a->{params.put(a.getArgument(0),a.getArgument(1));return null;}).when(ps).setObject(anyInt(),any());
        ForecastContracts.ResultSaveRequest q=new ForecastContracts.ResultSaveRequest();q.setStationCode(r.station);q.setForecastTime("2026-11-09 00:00:00");q.setModelCode("THREE_LAYER_TANK");q.setCalculationId("test");
        StationDefinition st=c.getStationMap().get(r.station);
        ForecastDailyStore.save(connection,"test",q,new StationModelRoute(st,c.getModelMap().get(st.getModelRef())),"test",new java.util.Date(),Arrays.asList(r));
        String[] columns=sql[0].substring(sql[0].indexOf('(')+1,sql[0].indexOf(')')).split(",");
        assertEquals("11800103021004",params.get(Arrays.asList(columns).indexOf("ID_ATTR_CODE")+1));
        assertEquals(Timestamp.valueOf("2026-11-09 00:00:00"),params.get(Arrays.asList(columns).indexOf("TIME")+1));
        assertEquals(754.6,params.get(Arrays.asList(columns).indexOf("V0")+1));verify(ps).executeBatch();
    }
    @Test void duplicateConfigurationIsRejected() throws Exception {
        String xml=new String(Files.readAllBytes(Paths.get("conf/forecast/station-model-config.xml")),java.nio.charset.StandardCharsets.UTF_8);
        String point="<resultPoint kind=\"API\" field=\"*\" idAttrCode=\"21400102031010\"/>";
        String duplicate=xml.replace(point,point+point);
        assertThrows(StationModelConfigException.class,()->new StationModelConfigParser().parse(new ByteArrayInputStream(duplicate.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
