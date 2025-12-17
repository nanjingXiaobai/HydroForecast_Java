package com.yr.wds.xinyidai.common.cev.flood.LongTerm;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.BiMap;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nari.slsd.hydro.longterm.backend.LongHFWebService;
import com.nari.slsd.hydro.longterm.backend.model.AlgorithmModel.FrequencyAnalysis;
import com.nari.slsd.hydro.longterm.backend.unified.base.util.DataTransferUtil;
import com.nari.slsd.hydro.longterm.backend.unified.base.util.RSTime;
import com.nari.slsd.hydro.longterm.backend.unified.dobj.HFDataPoint;
import com.yr.wds.xinyidai.common.cev.flood.listener.PowerStationListener;
import com.yr.wds.xinyidai.common.cev.flood.util.*;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


@Component
public class LongTermResult {

    private static LongHFWebService longHFWebService;

    private static String TIME_STYLE = "Time";

    private static List<PowerStationListener.PowerStation> STATIONLIST;

    @PostConstruct
    public void init() {
       boolean flag= FileUtil.exist(System.getProperty("user.dir") + "/conf/forecastlong/frequency.xlsx");

       if(flag){
           PowerStationListener listener = new PowerStationListener();
           EasyExcel.read(System.getProperty("user.dir") + "/conf/forecastlong/frequency.xlsx", listener)
                   .sheet()
                   .headRowNumber(0)  // 明确指定标题行数
//                .autoTrim(true)    // 自动去除空格
                   .doRead();

           STATIONLIST = listener.getStations();
       }


    }

    //初始化
    public static Message getInit(JSONObject params) {

        longHFWebService = new LongHFWebService();

        //所有时间类型
        List<String> list = new ArrayList<>();

        String userName= params.getString("userName");

        ForecastRequestUtil.setRequestUserName(userName);
        //注册
        longHFWebService.initialize(userName);

        //获取配置文件中所有的时间类型
        Map<String, com.nari.slsd.hydro.longterm.backend.unified.base.util.RSTime.RSTimeType> rsMap = longHFWebService.getAllTimeTypeList();

        //预报对象list
        Map<String, List<String>> objMap = new HashMap<>();

        //预报依据时间
        Map<String, String> timeMap = new HashMap<>();

        //步长
        Map<String, Map<String, Integer>> beforeAfter = new LinkedHashMap<>();

        for (String str : rsMap.keySet()) {

            //时间类型
            list.add(str);

            //获取预报对象
            List<String> list1 = longHFWebService.getAllForeObjList(str);

            objMap.put(str, list1);

            //获取当前时间类型     下预报依据时间
            Calendar calendar = longHFWebService.getForecastBTime(str);

            //转字符串
            String time = DateUtil.formatDateTime(DateUtil.date(calendar));

            //获取当前时间类型下预报时段、前延时段相关属性
            //对应方法：Map<String, Integer> getDefaultConfigNumMap(String timeTypeStr)
            Map<String, Integer> map = longHFWebService.getDefaultConfigNumMap(str);

            beforeAfter.put(str, map);

            timeMap.put(str, time);
        }

        //获取默认得时间类型
        String defaultTimeType = longHFWebService.getDefaultTimeType();


        return new Message().ok(HttpStatus.HTTP_OK, "success")
                .addData("timeType", list)
                .addData("objMap", objMap)
                .addData("timeMap", timeMap)
                .addData("beforeAfter", beforeAfter)
                .addData("defaultTimeType", defaultTimeType);
    }


    //预报计算
    public static Message forecastCalculation(JSONObject params) {

        Map<String, Object> result = new LinkedHashMap<>();

        //依据时间 string 转 Calendar
        Calendar forecastBTime = TimeUtil.sectionName(params.getString("forecastBTime"), DateFormatter.SECOND_FORMATTER);

        Calendar forecastETime = null;

        if (StrUtil.isNotBlank(params.getString("forecastETime"))) {
            forecastETime = TimeUtil.sectionName(params.getString("forecastETime"), DateFormatter.SECOND_FORMATTER);
        }


        //前延显示时段
        Integer showHisSpan = params.getInteger("showHisSpan");

        //预报时段
        Integer forecastNum = params.getInteger("forecastNum");

        //用户名
        String userName = params.getString("userName");

        longHFWebService.initialize(userName);

        //预报对象
        List<String> foreObjNameArr = JSONArray.parseArray(params.getJSONArray("foreObjNameArr").toJSONString(), String.class);

        //时间类型
        String timeType = params.getString("timeType");

        //设置预报条件
        boolean flag = longHFWebService.setForeConfig(userName, foreObjNameArr, timeType, forecastBTime, showHisSpan, forecastNum, forecastETime);

        if (!flag) {
            return new Message().error(HttpStatus.HTTP_OK, "设置预报");
        }


        boolean readFlag = longHFWebService.readData(userName);

        if (!readFlag) {
            return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "读取预报错误");
        }


        //预报计算
        //对应方法：boolean forecast(String userName)
        boolean flag2 = longHFWebService.forecast(userName);
        if (!flag2) {
            return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "预测计算失败");
        }

        //获取所有的时间类型
        Map<String, RSTime.RSTimeType> rsMap = longHFWebService.getAllTimeTypeList();

        com.nari.slsd.hydro.longterm.backend.unified.base.util.RSTime.RSTimeType rsTimeType = rsMap.get(timeType);

        for (String str : foreObjNameArr) {

            Map<String, Object> tabData = new LinkedHashMap<>();

            //根据对象名称获取表头数据
            Map<String, Object[]> tabmap = longHFWebService.getDisplayControlMap(str);

            List<JSONObject> tableHeader = new ArrayList<>();

            JSONObject tr1 = new JSONObject();

            tr1.put("label", "时间");
            tr1.put("prop", "Time");

            tableHeader.add(tr1);

            for (String stri : tabmap.keySet()) {

                //单位
                String unit = "";

                //获取单位
                Object[] objects = tabmap.get(stri);

                if (ArrayUtil.isNotEmpty(objects) && objects.length >= 3 && null != objects[2]) {
                    unit = objects[2].toString();
                }

                JSONObject js = new JSONObject();

                js.put("prop", stri);

                js.put("label", stri + "(" + unit + ")");

                tableHeader.add(js);

            }


            tabData.put("rule", tabmap);


            //根据对象名称获取所有数据
            //11.返回中长期所有展示序列
            //对应方法：Map<String, List<HFDataPoint>> getAllDataList(String userName, String foreObjName)
            //传入参数：用户名，预报对象名
            //返回格式：主键是系列名称，值是对应的时间序列集合

            Map<String, List<HFDataPoint>> map = longHFWebService.getAllDataList(userName, str);

            //频率  频率计算值
            List<JSONObject> tableData = organizeData(map, JsonToObjUtil.language, rsTimeType, tableHeader, str);

            if (RSTime.RSTimeType.RSMonth == rsTimeType && null != STATIONLIST) {
                tableHeader.add(JSONObject.parseObject("{\"prop\":\"频率\",\"label\":\"频率(%)\"}"));
                tableHeader.add(JSONObject.parseObject("{\"prop\":\"频率值\",\"label\":\"频率值(m³/s)\"}"));
            }

            tabData.put("tableHeader", tableHeader);
            tabData.put("tableDate", tableData);

            result.put(str, tabData);

        }

        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("data", result);
    }


    //整理数据
    public static List<JSONObject> organizeData(Map<String, List<HFDataPoint>> map,
                                                String language,
                                                RSTime.RSTimeType rsTimeType,
                                                List<JSONObject> tableHeader,
                                                String stationName) {

        List<JSONObject> result = new LinkedList<>();

        Map<String, JSONObject> value = new LinkedHashMap<>();

        for (String str : map.keySet()) {

            List<HFDataPoint> list = map.get(str);

            //是否是组合模型
            boolean flag = StrUtil.equals(str, "组合模型预报值");

            for (HFDataPoint hfDataPoint : list) {

                if (null != hfDataPoint) {
                    //只保存组合模型的id
                    //时间
                    Calendar calendar = hfDataPoint.m_time;

                    //值
                    double v = hfDataPoint.m_value;

                    String time;


                    if (StrUtil.equals(JsonToObjUtil.language, "zh")) {
                        time = DataTransferUtil.CalendarToString(calendar, rsTimeType, RSTime.RSTimeFormat.CHWORD);
                    } else {
                        time = DataTransferUtil.CalendarToString(calendar, rsTimeType, RSTime.RSTimeFormat.LINE);
                    }

                    String disTime = TimeUtil.CalendarToString(calendar, DateFormatter.SECOND_FORMATTER);

                    //map中是否已经存在记录，如果不存在就add ，存在就put
                    JSONObject jsonObject = value.get(time);

                    if (null == jsonObject) {
                        jsonObject = new JSONObject();
                    }
                    jsonObject.put(str, v);
                    jsonObject.put("Time", time);
                    jsonObject.put("disTime", disTime);
                    if (flag) {
                        long id = hfDataPoint.m_dpid;
                        jsonObject.put("id", id);
                        //添加频率  频率计算值
                        if (RSTime.RSTimeType.RSMonth == rsTimeType && null != STATIONLIST) {
                            //根据名称获取频率xlsx数据
                            PowerStationListener.PowerStation powerStation = STATIONLIST.stream().filter(x -> StrUtil.equals(x.getName(), stationName)).findFirst().orElse(null);
                            //获取时间的月份
                            int monthNum = DateUtil.date(calendar).month();
                            if (null != powerStation) {
                                //根据时间获取xlsx的月份数据
                                List<PowerStationListener.MonthlyData> xlsxList = powerStation.getMonthlyData();

                                PowerStationListener.MonthlyData monthlyData = xlsxList.get(monthNum);

                                //根据组合模型预报值获取频率
                                double frequency = FrequencyAnalysis.Xp2p(monthlyData.getCs(), monthlyData.getCv(), monthlyData.getMean(), v);
                                jsonObject.put("频率", NumberUtil.round(frequency,1));
                                //根据频率获取频率值
                                double frequencyValue = FrequencyAnalysis.p2Xp(monthlyData.getCs(), monthlyData.getCv(), monthlyData.getMean(), frequency);
                                jsonObject.put("频率值", NumberUtil.roundStr(frequencyValue,1));

                            }
                        }


                    }

                    //保存
                    value.put(time, jsonObject);

                }

            }
        }

        //将数据转成List
        for (String str : value.keySet()) {

            JSONObject jsonObject = value.get(str);

            result.add(jsonObject);
        }


        return result;

    }


    //修改
    public static Message forecastUpdate(JSONObject params) {

        //修改预报数据
        //对应方法：boolean modifyForecastData(String userName, String foreObjName, Long id, Calendar time, double value)

        String userName = ForecastRequestUtil.getRequestUserName();

        //方案描述
        //String description=params.getString("description");

        //预报对象的数据
        JSONObject js = params.getJSONObject("foreObjNameArr");

        for (Map.Entry<String, Object> stringObjectEntry : js.entrySet()) {

            String foreObjName = stringObjectEntry.getKey();

            List<JSONObject> list = JSONArray.parseArray(stringObjectEntry.getValue().toString(), JSONObject.class);

            for (JSONObject jsonObject : list) {

                Calendar time = TimeUtil.sectionName(jsonObject.getString("disTime"), DateFormatter.SECOND_FORMATTER);

                double v = jsonObject.getDouble("组合模型预报值");

                Long id = jsonObject.getLong("id");

                //修改
                longHFWebService.modifyForecastData(userName, foreObjName, id, time, v);

            }
        }


        //保存预报结果
        //对应方法：boolean saveForecastResult(String userName, String despValue)
        //传入参数：用户名，方案描述
        //返回格式：boolean型，如果保存成功，返回true，如果出错，返回false


        //longHFWebService.saveForecastResult( userName,  description);
        return new Message().ok(HttpStatus.HTTP_OK, "success");
    }

    //保存
    public static Message forecastSave(JSONObject params) {

        //保存预报结果
        //对应方法：boolean saveForecastResult(String userName, String despValue)
        //传入参数：用户名，方案描述
        //返回格式：boolean型，如果保存成功，返回true，如果出错，返回false

        //用户名
        String userName = ForecastRequestUtil.getRequestUserName();

        //方案描述
        String description = params.getString("description");

        boolean flag = longHFWebService.saveForecastResult(userName, description);

        if (!flag) {
            return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "error");
        }

        return new Message().ok(HttpStatus.HTTP_OK, "success");
    }


    //方案查询
    public static Message getFilteredResults(JSONObject params) {

        //根据时间筛选出预报结果
        //对应方法：static Map<Calendar, Map<String, Object>> getFilteredResults(boolean isYBTOrYBDT, Calendar bTime,Calendar eTime, List<String> ybrList)
        //传入参数：根据预报时间还是依据时间，开始时间，结束时间，用户名集合

        //是根据预报制作时间还是预报依据时间筛选（true为制作时间）
        boolean isYBTOrYBDT = params.getBoolean("isYBTOrYBDT");

        //开始时间
        Calendar bTime = TimeUtil.sectionName(params.getString("bTime"), DateFormatter.SECOND_FORMATTER);

        //结束时间
        Calendar eTime = TimeUtil.sectionName(params.getString("eTime"), DateFormatter.SECOND_FORMATTER);

        //预报人
        List<String> ybrList = JSONArray.parseArray(params.getJSONArray("ybrList").toJSONString(), String.class);


        List<Map<String, Object>> listMap = LongHFWebService.getFilteredResults(isYBTOrYBDT, bTime, eTime, ybrList);

        //表头map
        BiMap<String, String> biMap = new BiMap<>(new HashMap<>());

        //整理表头
        for (Map<String, Object> head : listMap) {

            for (String str : head.keySet()) {

                biMap.put(str, str);
            }

            break;
        }


        List<JSONObject> tableHeader = new LinkedList<>();

        //把map的数据，整理成表头
        for (String str : biMap.keySet()) {

            JSONObject jsonObject = new JSONObject();

            jsonObject.put("prop", biMap.get(str));
            jsonObject.put("label", str);

            tableHeader.add(jsonObject);
        }

        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("tabelHeader", tableHeader).addData("tabelData", listMap);
    }


    //方案详细
    public static Message getSingleResultMapList(JSONObject params) {

        //对应方法：static List<Map<String, String>> getSingleResultMapList(Map<String, Object>resultMap, int dbTyper)
        //传入参数：上一个方法返回的某一个时间的map，数据库类型 1是正常 2是峡江定制
        //返回格式：方案下的预报序列map集合，map的主键是预报统计值的名称，值就是统计值

        Map paraMap = JSONObject.parseObject(JSON.toJSONString(params));

        List<Map<String, String>> list = LongHFWebService.getSingleResultMapList(paraMap);

        //整理表头数据
        Map<String, String> header = new LinkedHashMap<>();

        for (Map<String, String> map : list) {

            for (String str : map.keySet()) {

                header.put(str, str);
            }

            break;
        }

        //表格数据
        List<JSONObject> tableData = new LinkedList<>();

        for (Map<String, String> stringStringMap : list) {

            JSONObject jsonObject = new JSONObject();

            for (String str : stringStringMap.keySet()) {

                String prop = header.get(str);

                String v = stringStringMap.get(str);

                jsonObject.put(prop, v);

            }
            tableData.add(jsonObject);

        }

        List<JSONObject> tableHeader = new LinkedList<>();

        for (String str : header.keySet()) {

            JSONObject js = new JSONObject();

            String prop = header.get(str);

            js.put("prop", prop);

            js.put("label", str);

            tableHeader.add(js);
        }


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("tableData", tableData).addData("tableHeader", tableHeader);
    }


    //结果管理的预测数据
    public static Message getSingleAdxlds(JSONObject params) {

        //对应方法：static Object[] getSingleAdxlds (Map<String, String> resultMap)
        //传入参数：筛选结果里的一个map
        //返回格式：数组的第一个map：外层map的主键是时间，内层map的key是数据名称，value是数据的值；数组的第二个map是定义map，主键是定义雨量还是流量，值是对应第一个数组的数据名称

        Map map = JSONObject.parseObject(JSON.toJSONString(params));

        Object[] arr = LongHFWebService.getSingleAdxlds(map);


        Map<Calendar, Map<String, String>> remap = (Map) arr[0];

        Map<String, String> map1 = (Map) arr[1];

        List<JSONObject> v = new LinkedList<>();

        //表头
        List<JSONObject> tableHeader = new LinkedList<>();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("prop", TIME_STYLE);
        /*********************待定*******************************/
//        jsonObject.put("label", typeMap.get("时间"));
        jsonObject.put("label", "时间");
        tableHeader.add(jsonObject);

//        for (Calendar calendar : map.keySet()) {
//            Map<String, String> trMap = map.get(calendar);
//
//            for (String str : trMap.keySet()) {
//                JSONObject tr = new JSONObject();
//                tr.put("prop", str);
//                tr.put("label", str);
//                tr.put("type", map1.get(str));
//                tableHeader.add(tr);
//            }
//            break;
//        }

        for (String str : map1.keySet()) {
            JSONObject tr = new JSONObject();
            tr.put("prop", str);
            tr.put("label", str);
            tr.put("type", map1.get(str));
            tableHeader.add(tr);
        }

        //组装数据
        for (Calendar calendar : remap.keySet()) {
            Map<String, String> trMap = remap.get(calendar);

            String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

            JSONObject js = (JSONObject) JSONObject.toJSON(trMap);

            js.put(TIME_STYLE, time);

            v.add(js);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("tableHeader", tableHeader);
        result.put("tableData", v);


//        List<JSONObject> result = list.stream().map(x -> {
//            //时间
//            String time = TimeUtil.CalendarToString(x.getDt(), DateFormatter.SECOND_FORMATTER);
//            //值
//            Double v = x.getD();
//
//            Long id = x.getAdid();
//            JSONObject js = new JSONObject();
//            js.put("v", v);
//            js.put("time", time);
//            js.put("id", id);
//            return js;
//        }).sorted(Comparator.comparing(x -> DateUtil.parse(x.getString("time")).getTime())).collect(Collectors.toList());


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("result", result);
    }


    public static Message delete(JSONObject params) {
        //对应方法：static boolean doDeleteResultList(Map<String, Object> singleTimeMap)
        //传入参数：getFilteredResultMap接口下的某一条value
        //返回格式：true代表删除成功

        Map<String, Object> map = JSONObject.parseObject(JSON.toJSONString(params));

        boolean flag = LongHFWebService.doDeleteResultList(map);

        if (flag) {
            return new Message().ok(HttpStatus.HTTP_OK, "success");
        }

        return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "error");
    }

    public static Message deleteDetail(JSONObject params) {

        //对应方法：static boolean doDeleteSingleResult(Map<String, String>dataMap)
        //传入参数：getSingleResultMapList接口下的某一条value
        //返回格式：true代表删除成功

        Map<String, String> map = (Map) JSONObject.parseObject(JSON.toJSONString(params));

        boolean flag = LongHFWebService.doDeleteSingleResult(map);

        if (flag) {
            return new Message().ok(HttpStatus.HTTP_OK, "success");
        }

        return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "error");

    }

    public static Message getFrequencyValue(JSONObject params) {

        String stationName = params.getString("stationName");

        String time = params.getString("time");
        //获取时间的月份
        int monthNum = DateUtil.parseDateTime(time).month();
        //频率
        Double frequency = params.getDouble("frequency");
        //根据水库名称获取xlsx数据
        PowerStationListener.PowerStation powerStation = STATIONLIST.stream().filter(x -> StrUtil.equals(x.getName(), stationName)).findFirst().orElse(null);
        Double frequencyValue=null;
        if (null != powerStation) {
            //根据时间获取xlsx的月份数据
            List<PowerStationListener.MonthlyData> xlsxList = powerStation.getMonthlyData();

            PowerStationListener.MonthlyData monthlyData = xlsxList.get(monthNum);
            //根据频率获取频率值
             frequencyValue = FrequencyAnalysis.p2Xp(monthlyData.getCs(), monthlyData.getCv(), monthlyData.getMean(), frequency);
        }

        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("result", null != frequencyValue?NumberUtil.roundStr(frequencyValue,1):null);
    }


    public static void main(String[] args) {

        SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        LongHFWebService service = new LongHFWebService();
        service.initialize("高熠飞");
        Calendar forecastBTime = service.getForecastBTime("旬");
        List<String> allForeObjList = service.getAllForeObjList("旬");
//        List<String> allForeObjList = Arrays.asList("洪家渡,东风,洪东,索风营,乌江渡,构皮滩,思林,沙沱,大花水,格里桥".split(","));


        Calendar forecastETime = Calendar.getInstance();
        try {
            forecastBTime.setTime(sDateFormat.parse("2025-01-11 00:00:00"));
            forecastETime.setTime(sDateFormat.parse("2030-01-01 00:00:00"));
        } catch (ParseException e) { // TODO Auto-generated catch block
            e.printStackTrace();
        }


        service.setForeConfig("高熠飞", allForeObjList, "旬", forecastBTime, 12, 12, null);
        boolean flag = service.readData("高熠飞");
        service.forecast("高熠飞");

        Map<String, List<HFDataPoint>> secModelForecastListMap = service.getSecModelForecastListMap("高熠飞", "乌江渡");
        for (Map.Entry<String, List<HFDataPoint>> entry : secModelForecastListMap.entrySet()) {
            System.out.println(entry.getKey() + "---------------------------------");
            for (HFDataPoint dataPoint : entry.getValue()) {
                System.out.println(sDateFormat.format(dataPoint.m_time.getTime()) + "    " + dataPoint.m_value);
            }
        }
        for (String foreObj : allForeObjList) {
            System.out.println(foreObj);
        }
    }
}
