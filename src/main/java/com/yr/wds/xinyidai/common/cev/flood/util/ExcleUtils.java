package com.yr.wds.xinyidai.common.cev.flood.util;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
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
import com.alibaba.fastjson.JSONObject;
import com.nari.slsd.hydro.backend.HFWebService;
import com.yr.wds.xinyidai.common.cev.flood.shortForecast.FloodResults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.util.ResourceUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
}
