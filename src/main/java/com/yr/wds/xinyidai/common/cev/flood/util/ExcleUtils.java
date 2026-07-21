package com.yr.wds.xinyidai.common.cev.flood.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.enums.WriteDirectionEnum;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.WriteTable;
import com.alibaba.excel.write.metadata.WriteWorkbook;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nari.slsd.hps.dbmgr.conf.SqlManager;
import com.nari.slsd.hps.dbmgr.dao.ConnectionJDBC;
import com.nari.slsd.hydro.backend.HFWebService;
import com.yr.wds.xinyidai.common.cev.flood.shortForecast.FloodResults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.ResourceUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author wdp
 * @version 1.0
 * @data 2023/8/9 17:54
 */
public class ExcleUtils {

    private static final String CONTENT_TYPE = "application/vnd.ms-excel";
    private static final String CHARACTER_ENCODING = "utf-8";
    private static final String HEADER_S1 = "Content-disposition";
    private static final String HEADER_S2 = "attachment; filename=";
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final double ZERO_EPSILON = 0.000001D;


    /**
     * 获取excle中的sheet的个数
     * @param inputStream
     * @return
     */
    public static List<ReadSheet> listSheet(InputStream inputStream){
        if(inputStream == null){
            throw new RuntimeException("inputStream is null");
        }
        ExcelReader build = EasyExcel.read(inputStream).build();
        List<ReadSheet> readSheets = build.excelExecutor().sheetList();

        build.finish();
        return readSheets;
    }



    public static <T> List<T> convertList(List<LinkedHashMap> objects, Class<T> clazz) {
        List<T> results = new ArrayList<>(objects.size());
        try {
            Map<String, Field> objIndex = new HashMap<>();
            // 获取转化实体字段信息集合
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                // 根据实体上Easy Excel的ExcelProperty注解中的索引值对应excel读取数据的值
                int index = field.getAnnotation(ExcelProperty.class).index();
                // 设置字段可编辑
                field.setAccessible(true);
                objIndex.put(String.valueOf(index), field);
            }

            T obj = null;
            for (LinkedHashMap o : objects) {
                obj = clazz.newInstance();
                for (Object key : o.keySet()) {
                    // 如果表格索引与字段注解指定索引一样则赋值
                    if (objIndex.containsKey(key)) {
                        Object object = o.get(key);
                        Object value = null;
                        Field field = objIndex.get(key);
                        if (ObjectUtil.isEmpty(object)) {
                            continue;
                        }
                        Class<?> type = field.getType();
                        String replace = object.toString();
                        // 有特殊需要处理的字段类型则在此进行处理
                        if (type == BigDecimal.class) {
                            value = "--".equals(replace) ? null : new BigDecimal(replace.replace(",", ""));
                        } else if (type == Integer.class) {
                            // String强转Integer会报错，所以需要单独进行转化
                            value = "--".equals(replace) ? null : Integer.valueOf(replace.replace(",", ""));
                        } else {
                            value = object;
                        }
                        field.set(obj, value);
                    }
                }
                results.add(obj);
            }
        } catch (Exception e) {

        }
        return results;
    }



    /**
     *
     * @param response
     * @param fileName
     * @param map
     * @param list
     * @throws IOException
     */
    public static void createExcel(HttpServletResponse response, String fileName, Map<String, Object> map, List<?> list) throws IOException {
        // 设置公共头信息
        OutputStream out = null;
        BufferedOutputStream bos = null;
        response.setContentType(CONTENT_TYPE);
        response.setCharacterEncoding(CHARACTER_ENCODING);
        response.setHeader(HEADER_S1, HEADER_S2 + fileName);

        out = response.getOutputStream();
        bos = new BufferedOutputStream(out);

//        File file = new File("D:/代码/南瑞/洪水预报/wds_forecast_backend/target/classes/excelFile/schedulingCalculation.xlsx");

//        System.out.println(file.exists());

        File excleFile = ResourceUtils.getFile( ResourceUtils.CLASSPATH_URL_PREFIX+"excelFile/" + fileName);
        ExcelWriter excelWriter = EasyExcel.write(bos)
                .withTemplate(excleFile).excelType(ExcelTypeEnum.XLS).build();
        WriteSheet writeSheet = EasyExcel.writerSheet().build();
        //横向排列
        FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.FALSE).direction(WriteDirectionEnum.HORIZONTAL).build();
        // 填充 list 数据
        excelWriter.fill(list, fillConfig, writeSheet);
        // 填充 map 数据
//        excelWriter.fill(map, writeSheet);
        excelWriter.finish();
        bos.flush();
        bos.close();


    }


    /**
     *
     * EasyExcel 填充报表
     *
     * @param response
     * @param sheetAndDataMap  key:sheet页，value:填充的list集合
     * @param map     填充单个的值
     * @param filename   文件名
     * @param inputStream   文件流.
     */
    public static void fillReportWithEasyExcel(HttpServletResponse response, Map<String, List<?>> sheetAndDataMap,
                                               Map<String, String> map, String filename, InputStream inputStream){
        ExcelWriter excelWriter = null;
        try {
            OutputStream outputStream = response.getOutputStream();
            response.setHeader("Content-disposition", "attachment; filename=" + filename);
            response.setContentType("application/msexcel;charset=UTF-8");//设置类型
            response.setHeader("Pragma", "No-cache");//设置头
            response.setHeader("Cache-Control", "no-cache");//设置头
            response.setDateHeader("Expires", 0);//设置日期头
            excelWriter = EasyExcel.write(outputStream).withTemplate(inputStream).build();
            for(Map.Entry<String, List<?>> entry : sheetAndDataMap.entrySet()){
                List<?> value = entry.getValue();
                WriteSheet writeSheet = EasyExcel.writerSheet(entry.getKey()).build();
                FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
                excelWriter.fill(value, fillConfig, writeSheet);
                excelWriter.fill(map, writeSheet);

            }

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            excelWriter.finish();
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /***
     * 导出excel生成xlsx文件
     * @param response
     */
    public void exportSystemExcel(JSONObject param_json, HttpServletResponse response) {
        OutputStream outputStream = null;
        try {
            long startTime = System.currentTimeMillis();
            outputStream = response.getOutputStream();
            WriteWorkbook writeWorkbook = new WriteWorkbook();
            writeWorkbook.setOutputStream(outputStream);
            writeWorkbook.setExcelType(ExcelTypeEnum.XLSX);
//            ExcelWriter writer = new ExcelWriter(writeWorkbook);
            ExcelWriter writer = EasyExcel.write(response.getOutputStream())
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy()) // 核心：自适应列宽策略
                    .build();
            // 设置EXCEL名称
            String fileName = param_json.getString("预报对象")+param_json.getString("预报制作时间");
            String newFileName = new String((fileName).getBytes(), "UTF-8");


           Map<String, Map<String, List<List<String>>>> map = FloodResults.getAllResultByOne(param_json);

           int i = 0;

            if (MapUtil.isNotEmpty(map)) {

                for(String sheetName : map.keySet()){

                    // 设置SHEET名称
                    WriteSheet sheet = new WriteSheet();
                    sheet.setSheetNo(i);
                    sheet.setSheetName(sheetName);   //sheet 名称
                    // 设置表头标题
                    WriteTable table = new WriteTable();

                    Map<String, List<List<String>>> value = map.get(sheetName);
                    List<List<String>> titles = value.get("tableHeader");
                    table.setHead(titles);
                    List<List<String>> dataList = value.get("tableData");
                    writer.write(dataList, sheet, table);
                    i++;
                }
            }

            //下载Excel
            response.setHeader("Content-Disposition", "attachment;filename=" + new String(newFileName.getBytes(StandardCharsets.UTF_8), "ISO8859-1") + ".xlsx");
            response.setContentType("application/vnd.ms-excel");
            response.setCharacterEncoding("utf-8");
            writer.finish();
            outputStream.flush();
            // 导出时间结束

        } catch (Exception ex) {

        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void exportResultTableExcelPlus(JSONObject paramJson, HttpServletResponse response) {
        JSONObject resultParam = paramJson.getJSONObject("resultParam");
        if (resultParam == null) {
            resultParam = paramJson.getJSONObject("queryParams");
        }
        if (resultParam == null) {
            resultParam = paramJson;
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            boolean hasSheet = false;
            List<TableRef> chartTables = new ArrayList<>();

            if (!Boolean.FALSE.equals(paramJson.getBoolean("includeTopSheet"))) {
                JSONArray topData = paramJson.getJSONArray("topData");
                if ((topData == null || topData.isEmpty()) && resultParam != null) {
                    topData = getResultTopData(resultParam);
                }
                if (topData != null && !topData.isEmpty()) {
                    writeJsonTableSheet(workbook, "\u65b9\u6848\u8be6\u60c5\u603b\u89c8", buildResultTopHeader(), topData);
                    hasSheet = true;
                }
            }

            if (!Boolean.FALSE.equals(paramJson.getBoolean("includeResultSheets")) && resultParam != null) {
                Map<String, Map<String, List<List<String>>>> resultSheets = FloodResults.getAllResultByOne(resultParam);
                if (MapUtil.isNotEmpty(resultSheets)) {
                    for (String sheetName : resultSheets.keySet()) {
                        Map<String, List<List<String>>> sheetData = resultSheets.get(sheetName);
                        TableRef tableRef = writeListTableSheet(workbook, sheetName, sheetData.get("tableHeader"), sheetData.get("tableData"));
                        chartTables.add(tableRef);
                        hasSheet = true;
                    }
                }
            }

            if (!Boolean.FALSE.equals(paramJson.getBoolean("includeChartSheet"))) {
                hasSheet = writeForecastChartImageSheet(workbook, "\u65b9\u6848\u66f2\u7ebf\u56fe", chartTables) || hasSheet;
            }

            if (!hasSheet) {
                workbook.createSheet("Empty");
            }

            writeWorkbookResponse(workbook, response, getExportFileName(paramJson, resultParam, "result-export"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exportCalculateTrafficExcel(JSONObject paramJson, HttpServletResponse response) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            JSONObject queryParam = paramJson.getJSONObject("queryParams");
            if (queryParam == null) {
                queryParam = paramJson;
            }
            List<JSONObject> stationParams = resolveAchievementStationParams(queryParam);
            if (stationParams.isEmpty() || !hasAchievementStationParam(stationParams)) {
                throw new IllegalArgumentException("calculateTrafficExcel requires valid userName, falx, ly and forecast object data; station names are resolved by backend.");
            }
            boolean hasSheet = false;

            for (JSONObject stationParam : stationParams) {
                Message message = FloodResults.achievementDisplay(stationParam);
                Object dataObject = message.getData().get("data");
                JSONObject data = JSONObject.parseObject(JSON.toJSONString(dataObject));
                JSONArray tableHeader = data.getJSONArray("tableHeader");
                JSONArray tableData = data.getJSONArray("tableData");
                String stationName = firstString(stationParam, "name", "dm", "sectionName");
                if (StrUtil.isBlank(stationName)) {
                    stationName = "Data";
                }
                writeChartAndJsonTableSheet(workbook, stationName, stationName + "\u8ba1\u7b97\u7ed3\u679c\u5c55\u793a\u56fe", tableHeader, tableData);
                hasSheet = true;
            }

            if (!hasSheet) {
                workbook.createSheet("Empty");
            }

            writeWorkbookResponse(workbook, response, getExportFileName(paramJson, null, "calculate-traffic-export"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void exportResultStandardDataExcel(JSONObject paramJson, HttpServletResponse response) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            String tableName = resolveHistoryTableName(paramJson);
            Map<String, String> outputIdMap = getOutputIdMap(paramJson);
            writeOutputIdSheet(workbook, outputIdMap);
            HistoryQueryResult result = queryHistoryTable(paramJson, tableName, outputIdMap);
            writeHistoryResultSheet(workbook, tableName, result);
            writeWorkbookResponse(workbook, response, getExportFileName(paramJson, null, tableName.toLowerCase(Locale.ROOT) + "-export"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONArray getResultTopData(JSONObject resultParam) {
        try {
            Message message = FloodResults.selectInformation(resultParam);
            Object data = message.getData().get("data");
            if (data instanceof Map) {
                Object top = ((Map<?, ?>) data).get("top");
                return normalizeResultTopData(JSONArray.parseArray(JSON.toJSONString(top)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JSONArray();
    }

    private JSONArray normalizeResultTopData(JSONArray topData) {
        if (topData == null) {
            return new JSONArray();
        }
        for (int i = 0; i < topData.size(); i++) {
            JSONObject row = topData.getJSONObject(i);
            normalizeTopValue(row, "\u9884\u62a5\u65ad\u9762");
            normalizeTopValue(row, "\u6a21\u578b\u65b9\u6848\u540d\u79f0");
            normalizeTopValue(row, "\u9884\u62a5\u7ed3\u679c\u7c7b\u578b");
            normalizeTopValue(row, "\u6700\u5927\u6d41\u91cf");
            normalizeTopValue(row, "\u6700\u5927\u6d41\u91cf\u65f6\u95f4");
            normalizeTopValue(row, "\u9884\u62a5\u964d\u96e8");
            normalizeTopValue(row, "\u65f6\u95f4\u7c7b\u578b");
            normalizeTopValue(row, "\u65f6\u95f4\u95f4\u9694");
            normalizeTopValue(row, "\u9884\u62a5\u65f6\u6bb5\u6570");
            normalizeTopValue(row, "\u9884\u62a5\u70b9\u53f7");
            normalizeTopValue(row, "\u9884\u62a5\u5236\u4f5c\u65f6\u95f4");
            normalizeTopValue(row, "\u9884\u62a5\u4f9d\u636e\u65f6\u95f4");
            normalizeTopValue(row, "\u9884\u62a5\u5458");
        }
        return topData;
    }

    private void normalizeTopValue(JSONObject row, String canonicalKey) {
        if (row == null || row.containsKey(canonicalKey)) {
            return;
        }
        for (String key : row.keySet()) {
            if (key != null && key.startsWith(canonicalKey)) {
                row.put(canonicalKey, row.get(key));
                return;
            }
        }
    }

    private JSONArray buildResultTopHeader() {
        JSONArray header = new JSONArray();
        header.add(column("\u9884\u62a5\u65ad\u9762", "\u9884\u62a5\u65ad\u9762"));
        header.add(column("\u6a21\u578b\u65b9\u6848\u540d\u79f0", "\u6a21\u578b\u65b9\u6848\u540d\u79f0"));
        header.add(column("\u9884\u62a5\u7ed3\u679c\u7c7b\u578b", "\u9884\u62a5\u7ed3\u679c\u7c7b\u578b"));
        header.add(column("\u6700\u5927\u6d41\u91cf", "\u6700\u5927\u6d41\u91cf(m\u00b3/s)"));
        header.add(column("\u6700\u5927\u6d41\u91cf\u65f6\u95f4", "\u6700\u5927\u6d41\u91cf\u65f6\u95f4"));
        header.add(column("\u9884\u62a5\u964d\u96e8", "\u9884\u62a5\u964d\u96e8(mm)"));
        header.add(column("\u65f6\u95f4\u7c7b\u578b", "\u65f6\u95f4\u7c7b\u578b"));
        header.add(column("\u65f6\u95f4\u95f4\u9694", "\u65f6\u95f4\u95f4\u9694"));
        header.add(column("\u9884\u62a5\u65f6\u6bb5\u6570", "\u9884\u62a5\u65f6\u6bb5\u6570"));
        header.add(column("\u9884\u62a5\u70b9\u53f7", "\u9884\u62a5\u70b9\u53f7"));
        header.add(column("\u9884\u62a5\u5236\u4f5c\u65f6\u95f4", "\u9884\u62a5\u5236\u4f5c\u65f6\u95f4"));
        header.add(column("\u9884\u62a5\u4f9d\u636e\u65f6\u95f4", "\u9884\u62a5\u4f9d\u636e\u65f6\u95f4"));
        header.add(column("\u9884\u62a5\u5458", "\u9884\u62a5\u5458"));
        return header;
    }

    private List<JSONObject> resolveAchievementStationParams(JSONObject queryParam) {
        List<JSONObject> ybdxStationParams = resolveAchievementStationParamsByYbdx(queryParam);
        if (!ybdxStationParams.isEmpty()) {
            return ybdxStationParams;
        }

        List<JSONObject> allStationParams = resolveAllAchievementStationParams(queryParam);
        if (!allStationParams.isEmpty()) {
            return allStationParams;
        }

        List<JSONObject> stationParams = new ArrayList<>();
        JSONArray stations = queryParam.getJSONArray("stations");
        if (stations != null && !stations.isEmpty()) {
            for (int i = 0; i < stations.size(); i++) {
                JSONObject station = stations.getJSONObject(i);
                JSONObject item = JSONObject.parseObject(queryParam.toJSONString());
                item.remove("stations");
                item.putAll(station);
                stationParams.add(item);
            }
            return stationParams;
        }

        Object nameObject = queryParam.get("name");
        JSONObject dxMap = queryParam.getJSONObject("dxMap");
        if (dxMap == null) {
            dxMap = queryParam.getJSONObject("canSelectedObj");
        }
        if (nameObject instanceof JSONArray) {
            JSONArray names = (JSONArray) nameObject;
            for (int i = 0; i < names.size(); i++) {
                String name = names.getString(i);
                JSONObject item = JSONObject.parseObject(queryParam.toJSONString());
                item.put("name", name);
                if (dxMap != null && dxMap.get(name) != null) {
                    item.put("dx", dxMap.getString(name));
                }
                stationParams.add(item);
            }
            return stationParams;
        }

        stationParams.add(queryParam);
        return stationParams;
    }

    private List<JSONObject> resolveAchievementStationParamsByYbdx(JSONObject queryParam) {
        List<JSONObject> stationParams = new ArrayList<>();
        JSONArray ybdx = queryParam.getJSONArray("ybdx");
        if (ybdx == null || ybdx.isEmpty()) {
            return stationParams;
        }
        for (int i = 0; i < ybdx.size(); i++) {
            String stationName = ybdx.getString(i);
            if (StrUtil.isBlank(stationName)) {
                continue;
            }
            JSONObject item = JSONObject.parseObject(queryParam.toJSONString());
            item.put("dx", stationName);
            item.put("name", stationName);
            if (item.get("isCalc") == null) {
                item.put("isCalc", false);
            }
            if (item.get("isRC") == null) {
                item.put("isRC", true);
            }
            if (item.get("modelSchName") == null) {
                item.put("modelSchName", "");
            }
            if (StrUtil.isBlank(item.getString("type"))) {
                item.put("type", "1");
            }
            stationParams.add(item);
        }
        return stationParams;
    }

    private boolean hasAchievementStationParam(List<JSONObject> stationParams) {
        for (JSONObject stationParam : stationParams) {
            if (StrUtil.isNotBlank(stationParam.getString("name")) && StrUtil.isNotBlank(stationParam.getString("dx"))) {
                return true;
            }
        }
        return false;
    }

    private List<JSONObject> resolveAllAchievementStationParams(JSONObject queryParam) {
        List<JSONObject> stationParams = new ArrayList<>();
        try {
            JSONObject initParam = buildAchievementInitParam(queryParam);
            Message message = FloodResults.init(initParam);
            JSONObject data = JSONObject.parseObject(JSON.toJSONString(message.getData()));
            JSONObject initObj = data.getJSONObject("initObj");
            JSONObject skdd = data.getJSONObject("skdd");
            if (initObj == null || initObj.isEmpty()) {
                return stationParams;
            }

            Set<String> stationNames = new TreeSet<>();
            if (skdd != null && !skdd.isEmpty()) {
                stationNames.addAll(skdd.keySet());
            } else {
                for (String name : initObj.keySet()) {
                    String foreObjName = initObj.getString(name);
                    if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(foreObjName) && !StrUtil.equals(name, foreObjName)) {
                        stationNames.add(name);
                    }
                }
            }

            for (String stationName : stationNames) {
                String foreObjName = initObj.getString(stationName);
                if (StrUtil.isBlank(foreObjName)) {
                    continue;
                }
                JSONObject item = JSONObject.parseObject(queryParam.toJSONString());
                item.put("falx", initParam.getString("falx"));
                item.put("ly", initParam.getString("ly"));
                item.put("ybdx", initParam.getJSONArray("ybdx"));
                item.put("name", stationName);
                item.put("dx", foreObjName);
                stationParams.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stationParams;
    }

    private JSONObject buildAchievementInitParam(JSONObject queryParam) throws Exception {
        JSONObject initParam = JSONObject.parseObject(queryParam.toJSONString());
        if (initParam.getJSONArray("ybdx") != null && !initParam.getJSONArray("ybdx").isEmpty()
                && StrUtil.isNotBlank(initParam.getString("falx"))
                && StrUtil.isNotBlank(initParam.getString("ly"))) {
            return initParam;
        }

        Message message = FloodResults.defaultData(initParam);
        JSONObject data = JSONObject.parseObject(JSON.toJSONString(message.getData().get("data")));
        JSONObject lyMap = data.getJSONObject("lyMap");
        String falx = resolveSchemeType(initParam.getString("falx"), initParam.getString("ly"), data.getJSONArray("faList"), lyMap);
        if (StrUtil.isNotBlank(falx)) {
            initParam.put("falx", falx);
        }
        if (StrUtil.isBlank(initParam.getString("ly"))) {
            JSONArray lyList = lyMap == null ? null : lyMap.getJSONArray(falx);
            if (lyList != null && !lyList.isEmpty()) {
                initParam.put("ly", lyList.getString(0));
            }
        }

        if (initParam.getJSONArray("ybdx") == null || initParam.getJSONArray("ybdx").isEmpty()) {
            JSONObject ybObList = data.getJSONObject("ybObList");
            JSONArray foreObjNames = ybObList == null ? null : ybObList.getJSONArray(initParam.getString("ly"));
            if (foreObjNames != null && !foreObjNames.isEmpty()) {
                initParam.put("ybdx", foreObjNames);
            }
        }
        return initParam;
    }

    private String resolveSchemeType(String requestedFalx, String basinName, JSONArray faList, JSONObject lyMap) {
        if (StrUtil.isNotBlank(requestedFalx) && (lyMap == null || lyMap.containsKey(requestedFalx))) {
            return requestedFalx;
        }
        if (lyMap != null && StrUtil.isNotBlank(basinName)) {
            for (String schemeType : lyMap.keySet()) {
                JSONArray basinList = lyMap.getJSONArray(schemeType);
                if (basinList != null && basinList.contains(basinName)) {
                    return schemeType;
                }
            }
        }
        if (faList != null && !faList.isEmpty()) {
            return faList.getString(0);
        }
        return requestedFalx;
    }

    private String resolveHistoryTableName(JSONObject paramJson) {
        String tableName = paramJson.getString("tableName");
        if (tableName == null) {
            String timeType = paramJson.getString("timeType");
            tableName = StrUtil.equalsAnyIgnoreCase(timeType, "day", "日", "DAYDB") ? "DAYDB" : "HOURDB";
        }
        tableName = tableName.toUpperCase(Locale.ROOT);
        if (!StrUtil.equalsAnyIgnoreCase(tableName, "HOURDB", "DAYDB")) {
            throw new IllegalArgumentException("tableName only supports HOURDB or DAYDB");
        }
        return tableName;
    }

    private HistoryQueryResult queryHistoryTable(JSONObject paramJson, String tableName, Map<String, String> outputIdMap) {
        String bTime = firstString(paramJson, "bTime", "startTime", "beginTime");
        String eTime = firstString(paramJson, "eTime", "endTime");

        StringBuilder sql = new StringBuilder("select * from ").append(tableName).append(" where 1=1");
        List<Object> params = new ArrayList<>();
        if (StrUtil.isNotBlank(bTime)) {
            sql.append(" and TIME >= ?");
            params.add(bTime);
        }
        if (StrUtil.isNotBlank(eTime)) {
            sql.append(" and TIME <= ?");
            params.add(eTime);
        }
        if (MapUtil.isNotEmpty(outputIdMap)) {
            for (String outputId : outputIdMap.keySet()) {
                params.add(outputId);
            }
            sql.append(" and SENID in (").append(String.join(",", Collections.nCopies(outputIdMap.size(), "?"))).append(")");
        }
        sql.append(" order by TIME asc");

        try (Connection connection = ConnectionJDBC.getInstance(resolveHistoryDbService(paramJson));
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return readHistoryQueryResult(resultSet);
            }
        } catch (Exception e) {
            throw new RuntimeException("query history table failed", e);
        }
    }

    private String resolveHistoryDbService(JSONObject paramJson) {
        String serviceName = firstString(paramJson, "serviceName", "serviceFlag", "dbService");
        if (StrUtil.isNotBlank(serviceName)) {
            return serviceName;
        }
        try {
            String[] serviceNames = SqlManager.getServiceNames();
            if (serviceNames != null && serviceNames.length > 0 && StrUtil.isNotBlank(serviceNames[0])) {
                return serviceNames[0];
            }
        } catch (Exception ignored) {
        }
        return "wds";
    }

    private HistoryQueryResult readHistoryQueryResult(ResultSet resultSet) throws Exception {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int colNum = metaData.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int col = 1; col <= colNum; col++) {
            String label = metaData.getColumnLabel(col);
            columns.add(StrUtil.isBlank(label) ? metaData.getColumnName(col) : label);
        }

        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<Object> row = new ArrayList<>();
            for (int col = 1; col <= colNum; col++) {
                row.add(resultSet.getObject(col));
            }
            rows.add(row);
        }
        return new HistoryQueryResult(columns, rows);
    }

    private void writeHistoryResultSheet(XSSFWorkbook workbook, String sheetName, HistoryQueryResult result) {
        Sheet sheet = workbook.createSheet(safeSheetName(workbook, sheetName));
        if (result == null) {
            return;
        }
        CellStyle headerStyle = createTableHeaderStyle(workbook);
        CellStyle dataStyle = createTableDataStyle(workbook, false);
        int colNum = result.columns.size();
        Row header = sheet.createRow(0);
        for (int col = 0; col < colNum; col++) {
            Cell cell = header.createCell(col);
            cell.setCellValue(result.columns.get(col));
            cell.setCellStyle(headerStyle);
        }

        for (int rowIndex = 0; rowIndex < result.rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<Object> values = result.rows.get(rowIndex);
            for (int col = 0; col < colNum; col++) {
                Object value = col < values.size() ? values.get(col) : null;
                setCellValue(row, col, formatHistoryCellValue(value), dataStyle);
            }
        }
        autoSize(sheet, colNum);
    }

    private Object formatHistoryCellValue(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.sql.Timestamp) value);
        }
        if (value instanceof java.sql.Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format((java.sql.Date) value);
        }
        if (value instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((java.util.Date) value);
        }
        return value;
    }

    private void writeOutputIdSheet(XSSFWorkbook workbook, Map<String, String> outputIdMap) {
        Sheet sheet = workbook.createSheet(safeSheetName(workbook, "OutputId"));
        CellStyle headerStyle = createTableHeaderStyle(workbook);
        CellStyle dataStyle = createTableDataStyle(workbook, false);
        Row header = sheet.createRow(0);
        Cell senidHeader = header.createCell(0);
        senidHeader.setCellValue("SENID");
        senidHeader.setCellStyle(headerStyle);
        Cell nameHeader = header.createCell(1);
        nameHeader.setCellValue("NAME");
        nameHeader.setCellStyle(headerStyle);
        int rowIndex = 1;
        for (Map.Entry<String, String> entry : outputIdMap.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            Cell senid = row.createCell(0);
            senid.setCellValue(entry.getKey());
            senid.setCellStyle(dataStyle);
            Cell name = row.createCell(1);
            name.setCellValue(entry.getValue());
            name.setCellStyle(dataStyle);
        }
        autoSize(sheet, 2);
    }

    private Map<String, String> getOutputIdMap(JSONObject paramJson) {
        Map<String, String> dataMap = new LinkedHashMap<>();
        File schemeFile = resolveSchemeFile(paramJson);
        if (!schemeFile.exists()) {
            return dataMap;
        }

        try {
            Document document = cn.hutool.core.util.XmlUtil.readXML(schemeFile);
            processHFDataSourceNodes(document, dataMap);
            processPreStationNodes(document, dataMap);
        } catch (Exception e) {
            throw new RuntimeException("parse scheme output id failed", e);
        }
        return dataMap;
    }

    private File resolveSchemeFile(JSONObject paramJson) {
        String schemePath = paramJson.getString("schemePath");
        if (StrUtil.isBlank(schemePath)) {
            schemePath = System.getProperty("user.dir") + "/conf/forecast/HFSchemeConf.xml";
        }
        return new File(schemePath);
    }

    private void processHFDataSourceNodes(Document document, Map<String, String> dataMap) {
        NodeList hfDataSourceList = document.getElementsByTagName("HFDataSource");
        String[] childNodeNames = {
                "flowid", "curveid", "outflowID", "staqeID",
                "wasteFlowID", "powerFolwID", "outputID",
                "planloadID", "powerID", "flowRateID", "waterRateID"
        };

        for (int i = 0; i < hfDataSourceList.getLength(); i++) {
            Node hfDataSourceNode = hfDataSourceList.item(i);
            if (hfDataSourceNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element hfDataSourceElement = (Element) hfDataSourceNode;
            String name = getElementTextContent(hfDataSourceElement, "name");
            if (StrUtil.isBlank(name)) {
                continue;
            }
            for (String childNodeName : childNodeNames) {
                processChildNode(hfDataSourceElement, name, childNodeName, dataMap);
            }
        }
    }

    private void processPreStationNodes(Document document, Map<String, String> dataMap) {
        NodeList preStationList = document.getElementsByTagName("preStation");
        for (int i = 0; i < preStationList.getLength(); i++) {
            Node preStationNode = preStationList.item(i);
            if (preStationNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element preStationElement = (Element) preStationNode;
            String preStaName = getElementTextContent(preStationElement, "preStaName");
            String preStaSenid = getElementTextContent(preStationElement, "preStaSenid");
            if (!isValidOutputId(preStaSenid) || StrUtil.isBlank(preStaName)) {
                continue;
            }
            String finalName = preStaName.trim();
            if (!finalName.contains("雨量")) {
                finalName = finalName + "雨量";
            }
            dataMap.put(preStaSenid.trim(), finalName);
        }
    }

    private void processChildNode(Element parentElement, String parentName, String childNodeName, Map<String, String> dataMap) {
        String childContent = getElementTextContent(parentElement, childNodeName);
        if (isValidOutputId(childContent)) {
            dataMap.put(childContent.trim(), parentName.trim() + childNodeName);
        }
    }

    private String getElementTextContent(Element parentElement, String childTagName) {
        NodeList nodeList = parentElement.getElementsByTagName(childTagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            return node.getTextContent();
        }
        return null;
    }

    private boolean isValidOutputId(String outputId) {
        if (StrUtil.isBlank(outputId)) {
            return false;
        }
        String value = outputId.trim();
        return !StrUtil.equalsAny(value, "-99", "0", "99");
    }

    private String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private TableRef writeJsonTableSheet(XSSFWorkbook workbook, String sheetName, JSONArray tableHeader, JSONArray tableData) {
        Sheet sheet = workbook.createSheet(safeSheetName(workbook, sheetName));
        return writeJsonTableToSheet(workbook, sheet, tableHeader, tableData, 0);
    }

    private TableRef writeChartAndJsonTableSheet(XSSFWorkbook workbook, String sheetName, String chartTitle, JSONArray tableHeader, JSONArray tableData) {
        XSSFSheet sheet = workbook.createSheet(safeSheetName(workbook, sheetName));
        for (int i = 0; i < 14; i++) {
            sheet.setColumnWidth(i, 3600);
        }

        TableRef chartRef = writeJsonTableToSheet(workbook, sheet, tableHeader, tableData, 0, 0);
        byte[] imageBytes = renderForecastChartImage(sheet, new TableRef(chartTitle, chartRef.columns, chartRef.lastRow, chartRef.firstRow, chartRef.firstColumn));
        if (imageBytes != null && imageBytes.length > 0) {
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper helper = workbook.getCreationHelper();
            int chartTopRow = chartRef.lastRow + 2;
            for (int rowIndex = chartTopRow; rowIndex <= chartTopRow + 41; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    row = sheet.createRow(rowIndex);
                }
                row.setHeightInPoints(15f);
            }
            int pictureIndex = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG);
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(chartTopRow);
            anchor.setCol2(9);
            anchor.setRow2(chartTopRow + 42);
            drawing.createPicture(anchor, pictureIndex);
        }
        return chartRef;
    }

    private TableRef writeJsonTableToSheet(XSSFWorkbook workbook, Sheet sheet, JSONArray tableHeader, JSONArray tableData, int startRow) {
        return writeJsonTableToSheet(workbook, sheet, tableHeader, tableData, startRow, 0);
    }

    private TableRef writeJsonTableToSheet(XSSFWorkbook workbook, Sheet sheet, JSONArray tableHeader, JSONArray tableData, int startRow, int startColumn) {
        List<ExcelColumn> columns = resolveColumns(tableHeader, tableData);
        CellStyle headerStyle = createTableHeaderStyle(workbook);
        CellStyle dataStyle = createTableDataStyle(workbook, false);
        CellStyle alternateStyle = createTableDataStyle(workbook, true);
        int rowIndex = startRow;

        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(startColumn + i);
            cell.setCellValue(columns.get(i).label);
            cell.setCellStyle(headerStyle);
        }

        if (tableData != null) {
            for (int i = 0; i < tableData.size(); i++) {
                JSONObject rowData = tableData.getJSONObject(i);
                Row row = sheet.createRow(rowIndex++);
                CellStyle rowStyle = i % 2 == 1 ? alternateStyle : dataStyle;
                for (int j = 0; j < columns.size(); j++) {
                    Object value = rowData.get(columns.get(j).prop);
                    setCellValue(row, startColumn + j, value, rowStyle);
                }
            }
        }

        autoSize(sheet, startColumn, columns.size());
        return new TableRef(sheet.getSheetName(), columns, rowIndex - 1, startRow, startColumn);
    }

    private TableRef writeListTableSheet(XSSFWorkbook workbook, String sheetName, List<List<String>> tableHeader, List<List<String>> tableData) {
        Sheet sheet = workbook.createSheet(safeSheetName(workbook, sheetName));
        CellStyle headerStyle = createTableHeaderStyle(workbook);
        CellStyle dataStyle = createTableDataStyle(workbook, false);
        CellStyle alternateStyle = createTableDataStyle(workbook, true);
        int rowIndex = 0;
        Row headerRow = sheet.createRow(rowIndex++);
        int columnCount = tableHeader == null ? 0 : tableHeader.size();
        List<ExcelColumn> columns = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            List<String> header = tableHeader.get(i);
            String label = header == null || header.isEmpty() ? "" : header.get(0);
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(label);
            cell.setCellStyle(headerStyle);
            columns.add(new ExcelColumn(String.valueOf(i), label));
        }

        if (tableData != null) {
            for (List<String> data : tableData) {
                Row row = sheet.createRow(rowIndex++);
                int dataIndex = rowIndex - 2;
                CellStyle rowStyle = dataIndex % 2 == 1 ? alternateStyle : dataStyle;
                for (int i = 0; i < data.size(); i++) {
                    setCellValue(row, i, data.get(i), rowStyle);
                }
            }
        }

        autoSize(sheet, columnCount);
        return new TableRef(sheet.getSheetName(), columns, rowIndex - 1);
    }

    private boolean writeLineChartsSheet(XSSFWorkbook workbook, String sheetName, List<TableRef> tableRefs) {
        if (tableRefs == null || tableRefs.isEmpty()) {
            return false;
        }

        XSSFSheet chartSheet = workbook.createSheet(safeSheetName(workbook, sheetName));
        Drawing<?> drawing = chartSheet.createDrawingPatriarch();
        CreationHelper helper = workbook.getCreationHelper();
        int chartIndex = 0;

        for (TableRef tableRef : tableRefs) {
            if (tableRef == null || tableRef.lastRow <= tableRef.firstRow || tableRef.columns.size() < 2) {
                continue;
            }
            XSSFSheet dataSheet = workbook.getSheet(tableRef.sheetName);
            if (dataSheet == null) {
                continue;
            }

            int topRow = chartIndex * 20;
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(topRow);
            anchor.setCol2(12);
            anchor.setRow2(topRow + 18);

            XSSFChart chart = ((org.apache.poi.xssf.usermodel.XSSFDrawing) drawing).createChart(anchor);
            chart.setTitleText(tableRef.sheetName);
            chart.setTitleOverlay(false);
            chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

            XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            bottomAxis.setTitle(tableRef.columns.get(0).label);
            XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
            leftAxis.setTitle("Value");
            leftAxis.setCrosses(org.apache.poi.xddf.usermodel.chart.AxisCrosses.AUTO_ZERO);

            XDDFDataSource<String> times = XDDFDataSourcesFactory.fromStringCellRange(dataSheet,
                    new CellRangeAddress(tableRef.firstRow + 1, tableRef.lastRow, 0, 0));
            XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);

            for (int colIndex = 1; colIndex < tableRef.columns.size(); colIndex++) {
                XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(dataSheet,
                        new CellRangeAddress(tableRef.firstRow + 1, tableRef.lastRow, colIndex, colIndex));
                XDDFChartData.Series series = data.addSeries(times, values);
                series.setTitle(tableRef.columns.get(colIndex).label, null);
            }

            chart.plot(data);
            chartIndex++;
        }

        return chartIndex > 0;
    }

    private boolean writeForecastChartImageSheet(XSSFWorkbook workbook, String sheetName, List<TableRef> tableRefs) {
        if (tableRefs == null || tableRefs.isEmpty()) {
            return false;
        }

        XSSFSheet chartSheet = workbook.createSheet(safeSheetName(workbook, sheetName));
        Drawing<?> drawing = chartSheet.createDrawingPatriarch();
        CreationHelper helper = workbook.getCreationHelper();
        for (int i = 0; i < 10; i++) {
            chartSheet.setColumnWidth(i, 3800);
        }

        int chartIndex = 0;
        for (TableRef tableRef : tableRefs) {
            if (tableRef == null || tableRef.lastRow <= tableRef.firstRow || tableRef.columns.size() < 2) {
                continue;
            }
            XSSFSheet dataSheet = workbook.getSheet(tableRef.sheetName);
            if (dataSheet == null) {
                continue;
            }

            byte[] imageBytes = renderForecastChartImage(dataSheet, tableRef);
            if (imageBytes == null || imageBytes.length == 0) {
                continue;
            }
            int topRow = chartIndex * 38;
            for (int rowIndex = topRow; rowIndex <= topRow + 35; rowIndex++) {
                Row row = chartSheet.getRow(rowIndex);
                if (row == null) {
                    row = chartSheet.createRow(rowIndex);
                }
                row.setHeightInPoints(15f);
            }
            int pictureIndex = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG);
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(topRow);
            anchor.setCol2(10);
            anchor.setRow2(topRow + 35);
            drawing.createPicture(anchor, pictureIndex);
            chartIndex++;
        }

        if (chartIndex > 0) {
            workbook.setSheetOrder(chartSheet.getSheetName(), Math.min(1, workbook.getNumberOfSheets() - 1));
        }
        return chartIndex > 0;
    }

    private byte[] renderForecastChartImage(XSSFSheet dataSheet, TableRef tableRef) {
        try {
            DataFormatter formatter = new DataFormatter();
            List<String> times = new ArrayList<>();
            List<ChartSeries> rainSeries = new ArrayList<>();
            List<ChartSeries> flowSeries = new ArrayList<>();
            List<ChartSeries> waterSeries = new ArrayList<>();
            for (int colIndex = 1; colIndex < tableRef.columns.size(); colIndex++) {
                String label = tableRef.columns.get(colIndex).label;
                ChartSeries series = new ChartSeries(label);
                if (isRainColumn(label)) {
                    rainSeries.add(series);
                } else if (isWaterColumn(label)) {
                    waterSeries.add(series);
                } else if (isFlowColumn(label) || flowSeries.isEmpty()) {
                    flowSeries.add(series);
                } else {
                    flowSeries.add(series);
                }
            }

            for (int rowIndex = tableRef.firstRow + 1; rowIndex <= tableRef.lastRow; rowIndex++) {
                Row row = dataSheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                times.add(formatter.formatCellValue(row.getCell(tableRef.firstColumn)));
                int rainIndex = 0;
                int flowIndex = 0;
                int waterIndex = 0;
                for (int colIndex = 1; colIndex < tableRef.columns.size(); colIndex++) {
                    String label = tableRef.columns.get(colIndex).label;
                    Double value = readNumericCell(row, tableRef.firstColumn + colIndex, formatter);
                    if (isRainColumn(label)) {
                        rainSeries.get(rainIndex++).values.add(value);
                    } else if (isWaterColumn(label)) {
                        waterSeries.get(waterIndex++).values.add(value);
                    } else {
                        flowSeries.get(flowIndex++).values.add(value);
                    }
                }
            }

            BufferedImage image = new BufferedImage(1100, 720, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            drawForecastChart(g, image.getWidth(), image.getHeight(), tableRef.sheetName, times, rainSeries, flowSeries, waterSeries);
            g.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private void drawForecastChart(Graphics2D g, int width, int height, String title, List<String> times,
                                   List<ChartSeries> rainSeries, List<ChartSeries> flowSeries, List<ChartSeries> waterSeries) {
        int left = 135;
        int right = 135;
        int titleTop = 34;
        int legendTop = 72;
        int rainTop = 150;
        int panelGap = 32;
        int rainHeight = rainSeries.isEmpty() ? 0 : 120;
        int flowTop = rainSeries.isEmpty() ? 125 : rainTop + rainHeight + panelGap;
        int waterTop = waterSeries.isEmpty() ? 0 : height - 190;
        int flowHeight = waterSeries.isEmpty() ? height - flowTop - 58 : Math.max(120, waterTop - flowTop - panelGap);
        int waterHeight = waterSeries.isEmpty() ? 0 : 145;
        int plotWidth = width - left - right;

        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 24));
        g.setColor(new Color(40, 40, 40));
        drawCenteredString(g, title, 0, titleTop, width);

        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        drawLegend(g, width, legendTop, rainSeries, flowSeries, waterSeries);

        if (!rainSeries.isEmpty()) {
            drawPanel(g, left, rainTop, plotWidth, rainHeight);
            drawYAxisName(g, "\u96e8\u91cf(mm)", 54, rainTop + rainHeight / 2);
            drawXAxis(g, left, rainTop - 36, plotWidth, times, true);
            drawValueAxis(g, left, rainTop, rainHeight, 0, maxValue(rainSeries, 10), true);
            drawRainBars(g, left, rainTop, plotWidth, rainHeight, times.size(), rainSeries, 0, maxValue(rainSeries, 10));
            drawAxisTimeName(g, left + plotWidth + 24, rainTop + 4);
        } else {
            flowTop = 125;
            flowHeight = height - flowTop - 58;
        }

        if (!flowSeries.isEmpty()) {
            drawPanel(g, left, flowTop, plotWidth, flowHeight);
            drawYAxisName(g, "\u6d41\u91cf(m\u00b3/s)", 54, flowTop + flowHeight / 2);
            drawValueAxis(g, left, flowTop, flowHeight, 0, maxFlowValue(flowSeries), false);
            drawFlowLines(g, left, flowTop, plotWidth, flowHeight, times.size(), flowSeries, 0, maxFlowValue(flowSeries));
            if (waterSeries.isEmpty()) {
                drawXAxis(g, left, flowTop + flowHeight + 24, plotWidth, times, false);
                drawAxisTimeName(g, left + plotWidth + 24, flowTop + flowHeight + 4);
            }
        }

        if (!waterSeries.isEmpty()) {
            drawPanel(g, left, waterTop, plotWidth, waterHeight);
            drawYAxisName(g, "\u6c34\u4f4d(m)", 54, waterTop + waterHeight / 2);
            double maxWater = maxValue(waterSeries, 1);
            drawValueAxis(g, left, waterTop, waterHeight, 0, maxWater, false);
            drawFlowLines(g, left, waterTop, plotWidth, waterHeight, times.size(), waterSeries, 0, maxWater);
            drawXAxis(g, left, waterTop + waterHeight + 24, plotWidth, times, false);
            drawAxisTimeName(g, left + plotWidth + 24, waterTop + waterHeight + 4);
        }
    }

    private void drawLegend(Graphics2D g, int width, int y, List<ChartSeries> rainSeries, List<ChartSeries> flowSeries, List<ChartSeries> waterSeries) {
        int x = width / 2 - 310;
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        if (!rainSeries.isEmpty()) {
            g.setColor(new Color(64, 158, 255));
            g.fillRoundRect(x, y, 36, 20, 6, 6);
            g.setColor(new Color(80, 80, 80));
            g.drawString(rainSeries.get(0).name, x + 46, y + 17);
            x += 215;
        }
        if (!flowSeries.isEmpty()) {
            g.setColor(colorForSeries(flowSeries.get(0).name, 0));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x, y + 10, x + 42, y + 10);
            g.setColor(new Color(80, 80, 80));
            g.drawString(flowSeries.get(0).name, x + 52, y + 17);
            x += 240;
        }
        if (!waterSeries.isEmpty()) {
            g.setColor(colorForSeries(waterSeries.get(0).name, flowSeries.size()));
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x, y + 10, x + 42, y + 10);
            g.setColor(new Color(80, 80, 80));
            g.drawString(waterSeries.get(0).name, x + 52, y + 17);
        }
    }

    private void drawPanel(Graphics2D g, int x, int y, int width, int height) {
        g.setColor(new Color(213, 213, 213));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, width, height);
        g.setColor(new Color(224, 224, 224));
        for (int i = 1; i < 5; i++) {
            int yy = y + (height * i / 5);
            drawDashedLine(g, x, yy, x + width, yy);
        }
    }

    private void drawDashedLine(Graphics2D g, int x1, int y1, int x2, int y2) {
        java.awt.Stroke oldStroke = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
        g.drawLine(x1, y1, x2, y2);
        g.setStroke(oldStroke);
    }

    private void drawValueAxis(Graphics2D g, int x, int y, int height, double min, double max, boolean inverse) {
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.setColor(new Color(100, 100, 100));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i <= 5; i++) {
            double value = min + ((max - min) * i / 5);
            int yy = inverse ? y + (height * i / 5) : y + height - (height * i / 5);
            String text = formatAxisValue(value);
            g.drawString(text, x - metrics.stringWidth(text) - 12, yy + 6);
        }
    }

    private void drawXAxis(Graphics2D g, int x, int y, int width, List<String> times, boolean topAxis) {
        if (times == null || times.isEmpty()) {
            return;
        }
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.setColor(new Color(100, 100, 100));
        int[] indexes = sampleIndexes(times.size());
        for (int index : indexes) {
            int xx = x + (times.size() == 1 ? 0 : width * index / (times.size() - 1));
            String text = times.get(index);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, xx - metrics.stringWidth(text) / 2, y);
        }
    }

    private void drawRainBars(Graphics2D g, int x, int y, int width, int height, int count, List<ChartSeries> seriesList, double min, double max) {
        if (count == 0) {
            return;
        }
        int barWidth = Math.max(2, width / Math.max(count * Math.max(seriesList.size(), 1), 1));
        for (int seriesIndex = 0; seriesIndex < seriesList.size(); seriesIndex++) {
            ChartSeries series = seriesList.get(seriesIndex);
            g.setColor(seriesIndex == 0 ? new Color(64, 158, 255) : colorForSeries(series.name, seriesIndex));
            if (isZeroSeries(series)) {
                int zeroY = y;
                g.setStroke(new BasicStroke(2f));
                g.drawLine(x, zeroY + seriesIndex, x + width, zeroY + seriesIndex);
                continue;
            }
            for (int i = 0; i < series.values.size(); i++) {
                Double value = series.values.get(i);
                if (value == null) {
                    continue;
                }
                int center = x + (count == 1 ? 0 : width * i / (count - 1));
                int barHeight = (int) Math.round((value - min) / (max - min) * height);
                g.fillRect(center - barWidth / 2 + seriesIndex * barWidth, y, barWidth, Math.max(barHeight, value > 0 ? 1 : 0));
            }
        }
    }

    private void drawFlowLines(Graphics2D g, int x, int y, int width, int height, int count, List<ChartSeries> seriesList, double min, double max) {
        if (count == 0 || max <= min) {
            return;
        }
        for (int seriesIndex = 0; seriesIndex < seriesList.size(); seriesIndex++) {
            ChartSeries series = seriesList.get(seriesIndex);
            g.setColor(colorForSeries(series.name, seriesIndex));
            g.setStroke(new BasicStroke(3f));
            Integer lastX = null;
            Integer lastY = null;
            for (int i = 0; i < series.values.size(); i++) {
                Double value = series.values.get(i);
                if (value == null) {
                    lastX = null;
                    lastY = null;
                    continue;
                }
                int xx = x + (count == 1 ? 0 : width * i / (count - 1));
                int yy = y + height - (int) Math.round((value - min) / (max - min) * height);
                if (lastX != null) {
                    g.drawLine(lastX, lastY, xx, yy);
                }
                lastX = xx;
                lastY = yy;
            }
        }
    }

    private boolean isZeroSeries(ChartSeries series) {
        boolean hasValue = false;
        for (Double value : series.values) {
            if (value == null) {
                continue;
            }
            hasValue = true;
            if (Math.abs(value) >= ZERO_EPSILON) {
                return false;
            }
        }
        return hasValue;
    }

    private Color colorForSeries(String name, int index) {
        Color[] colors = {
                new Color(87, 213, 132),
                new Color(64, 158, 255),
                new Color(245, 124, 0),
                new Color(113, 107, 230),
                new Color(230, 92, 122),
                new Color(0, 172, 193),
                new Color(255, 193, 7),
                new Color(126, 87, 194),
                new Color(38, 166, 154),
                new Color(239, 83, 80),
                new Color(92, 107, 192),
                new Color(141, 110, 99)
        };
        int colorIndex = index;
        if (StrUtil.isNotBlank(name)) {
            colorIndex = Math.abs(name.hashCode());
        }
        return colors[colorIndex % colors.length];
    }

    private void drawYAxisName(Graphics2D g, String text, int x, int y) {
        Font oldFont = g.getFont();
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.setColor(Color.BLACK);
        java.awt.geom.AffineTransform oldTransform = g.getTransform();
        g.rotate(-Math.PI / 2, x, y);
        drawCenteredString(g, text, x - 120, y + 7, 240);
        g.setTransform(oldTransform);
        g.setFont(oldFont);
    }

    private void drawAxisTimeName(Graphics2D g, int x, int y) {
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.setColor(new Color(60, 60, 60));
        g.drawString("\u65f6\u95f4", x, y);
    }

    private void drawCenteredString(Graphics2D g, String text, int x, int y, int width) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, y);
    }

    private int[] sampleIndexes(int size) {
        if (size <= 1) {
            return new int[]{0};
        }
        return new int[]{0, Math.max(0, size / 3), Math.max(0, size * 2 / 3), Math.max(0, size - 2)};
    }

    private double maxValue(List<ChartSeries> seriesList, double minMax) {
        double max = 0;
        for (ChartSeries series : seriesList) {
            for (Double value : series.values) {
                if (value != null) {
                    max = Math.max(max, value);
                }
            }
        }
        double scaled = max + (max * 0.1);
        return Math.max(minMax, scaled);
    }

    private double maxFlowValue(List<ChartSeries> seriesList) {
        double max = maxValue(seriesList, 0);
        return max <= 500 ? 500 : max;
    }

    private Double readNumericCell(Row row, int columnIndex, DataFormatter formatter) {
        if (row.getCell(columnIndex) == null) {
            return null;
        }
        String value = formatter.formatCellValue(row.getCell(columnIndex));
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return normalizeTinyNumber(Double.parseDouble(value.replace(",", "")));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRainColumn(String label) {
        return label != null && (label.contains("\u964d\u96e8") || label.contains("\u96e8\u91cf"));
    }

    private boolean isFlowColumn(String label) {
        return label != null && label.contains("\u6d41\u91cf");
    }

    private boolean isWaterColumn(String label) {
        return label != null && label.contains("\u6c34\u4f4d");
    }

    private String formatAxisValue(double value) {
        if (Math.abs(value - Math.round(value)) < 0.000001) {
            return String.valueOf((long) Math.round(value));
        }
        return new BigDecimal(value).setScale(2, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString();
    }

    private List<ExcelColumn> resolveColumns(JSONArray tableHeader, JSONArray tableData) {
        List<ExcelColumn> columns = new ArrayList<>();
        if (tableHeader != null && !tableHeader.isEmpty()) {
            for (int i = 0; i < tableHeader.size(); i++) {
                collectColumns(tableHeader.getJSONObject(i), "", columns);
            }
        }

        if (columns.isEmpty() && tableData != null && !tableData.isEmpty()) {
            JSONObject first = tableData.getJSONObject(0);
            for (String key : first.keySet()) {
                columns.add(new ExcelColumn(key, key));
            }
        }
        return columns;
    }

    private void collectColumns(JSONObject header, String parentLabel, List<ExcelColumn> columns) {
        String label = header.getString("label");
        if (label == null) {
            label = header.getString("showName");
        }
        String fullLabel = parentLabel;
        if (label != null && label.length() > 0) {
            fullLabel = parentLabel.length() == 0 ? label : parentLabel + "/" + label;
        }

        JSONArray children = header.getJSONArray("children");
        if (children != null && !children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                collectColumns(children.getJSONObject(i), fullLabel, columns);
            }
            return;
        }

        String prop = header.getString("prop");
        if (prop != null && prop.length() > 0) {
            columns.add(new ExcelColumn(prop, fullLabel.length() == 0 ? prop : fullLabel));
        }
    }

    private JSONObject column(String prop, String label) {
        JSONObject column = new JSONObject();
        column.put("prop", prop);
        column.put("label", label);
        return column;
    }

    private void setCellValue(Row row, int columnIndex, Object value) {
        setCellValue(row, columnIndex, value, null);
    }

    private void setCellValue(Row row, int columnIndex, Object value, CellStyle cellStyle) {
        Cell cell = row.createCell(columnIndex);
        if (cellStyle != null) {
            cell.setCellStyle(cellStyle);
        }
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(normalizeTinyNumber(((Number) value).doubleValue()));
            return;
        }

        String text = String.valueOf(value);
        if (text.length() == 0) {
            cell.setCellValue("");
            return;
        }

        try {
            cell.setCellValue(normalizeTinyNumber(Double.parseDouble(text)));
        } catch (Exception e) {
            cell.setCellValue(text);
        }
    }

    private double normalizeTinyNumber(double value) {
        return Math.abs(value) < ZERO_EPSILON ? 0D : value;
    }

    private CellStyle createCenterStyle(XSSFWorkbook workbook) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        return cellStyle;
    }

    private CellStyle createTableHeaderStyle(XSSFWorkbook workbook) {
        XSSFCellStyle cellStyle = (XSSFCellStyle) createCenterStyle(workbook);
        cellStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(0x5d, 0x8c, 0xd9)));
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = workbook.createFont();
        font.setColor(new XSSFColor(new java.awt.Color(0xff, 0xff, 0xff)));
        font.setBold(true);
        cellStyle.setFont(font);
        return cellStyle;
    }

    private CellStyle createTableDataStyle(XSSFWorkbook workbook, boolean alternate) {
        XSSFCellStyle cellStyle = (XSSFCellStyle) createCenterStyle(workbook);
        if (alternate) {
            cellStyle.setFillForegroundColor(new XSSFColor(new java.awt.Color(0xec, 0xf3, 0xff)));
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return cellStyle;
    }

    private void writeWorkbookResponse(XSSFWorkbook workbook, HttpServletResponse response, String fileName) throws IOException {
        response.setHeader("Content-Disposition", "attachment;filename=" + new String(fileName.getBytes(StandardCharsets.UTF_8), "ISO8859-1") + ".xlsx");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        workbook.write(response.getOutputStream());
        response.getOutputStream().flush();
    }

    private String getExportFileName(JSONObject paramJson, JSONObject resultParam, String defaultName) {
        String fileName = paramJson.getString("fileName");
        if (fileName == null && resultParam != null) {
            String objectName = firstString(resultParam, "预报对象", "棰勬姤瀵硅薄");
            String time = firstString(resultParam, "预报制作时间", "棰勬姤鍒朵綔鏃堕棿");
            if (objectName != null || time != null) {
                fileName = (objectName == null ? "" : objectName) + (time == null ? "" : time);
            }
        }
        if (fileName == null || fileName.trim().length() == 0) {
            fileName = defaultName;
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String firstString(JSONObject jsonObject, String... keys) {
        for (String key : keys) {
            String value = jsonObject.getString(key);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private String safeSheetName(XSSFWorkbook workbook, String sheetName) {
        String name = sheetName == null || sheetName.trim().length() == 0 ? "Sheet" : sheetName;
        name = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        if (name.length() > 31) {
            name = name.substring(0, 31);
        }
        String base = name;
        int index = 1;
        while (workbook.getSheet(name) != null) {
            String suffix = "_" + index++;
            int end = Math.min(base.length(), 31 - suffix.length());
            name = base.substring(0, end) + suffix;
        }
        return name;
    }

    private void autoSize(Sheet sheet, int columnCount) {
        autoSize(sheet, 0, columnCount);
    }

    private void autoSize(Sheet sheet, int startColumn, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            int columnIndex = startColumn + i;
            sheet.autoSizeColumn(columnIndex);
            int width = sheet.getColumnWidth(columnIndex);
            sheet.setColumnWidth(columnIndex, Math.min(Math.max(width + 1200, 4500), 22000));
        }
    }

    private static class ExcelColumn {
        private final String prop;
        private final String label;

        private ExcelColumn(String prop, String label) {
            this.prop = prop;
            this.label = label;
        }
    }

    private static class HistoryQueryResult {
        private final List<String> columns;
        private final List<List<Object>> rows;

        private HistoryQueryResult(List<String> columns, List<List<Object>> rows) {
            this.columns = columns == null ? Collections.emptyList() : columns;
            this.rows = rows == null ? Collections.emptyList() : rows;
        }
    }

    private static class TableRef {
        private final String sheetName;
        private final List<ExcelColumn> columns;
        private final int lastRow;
        private final int firstRow;
        private final int firstColumn;

        private TableRef(String sheetName, List<ExcelColumn> columns, int lastRow) {
            this(sheetName, columns, lastRow, 0);
        }

        private TableRef(String sheetName, List<ExcelColumn> columns, int lastRow, int firstRow) {
            this(sheetName, columns, lastRow, firstRow, 0);
        }

        private TableRef(String sheetName, List<ExcelColumn> columns, int lastRow, int firstRow, int firstColumn) {
            this.sheetName = sheetName;
            this.columns = columns;
            this.lastRow = lastRow;
            this.firstRow = firstRow;
            this.firstColumn = firstColumn;
        }
    }

    private static class ChartSeries {
        private final String name;
        private final List<Double> values = new ArrayList<>();

        private ChartSeries(String name) {
            this.name = name;
        }
    }
}
