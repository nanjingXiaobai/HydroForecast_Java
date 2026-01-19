package com.yr.wds.xinyidai.common.cev.flood.shortForecast;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.*;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.BiMap;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.nari.slsd.hps.dbmgr.conf.DBManager;
import com.nari.slsd.hps.dbmgr.conf.SqlManager;
import com.nari.slsd.hydro.backend.HFWebService;
import com.nari.slsd.hydro.backend.hf.model.SchemeModel.HFForeObj;
import com.nari.slsd.hydro.backend.hf.model.SchemeModel.HFScheme;
import com.nari.slsd.hydro.backend.hf.model.TopologyModel.HFPreStation;
import com.nari.slsd.hydro.backend.hf.model.TopologyModel.HFSchRegion;
import com.nari.slsd.hydro.backend.hf.model.TopologyModel.HFSchUnit;
import com.nari.slsd.hydro.backend.hf.model.TopologyModel.HFSection;
import com.nari.slsd.hydro.backend.hf.model.window.HFWindowSpnParam;
import com.nari.slsd.hydro.backend.unified.base.util.DataTransferUtil;
import com.nari.slsd.hydro.backend.unified.base.util.RSTime;
import com.nari.slsd.hydro.backend.unified.constant.EnumConstant;
import com.nari.slsd.hydro.backend.unified.constant.FilePathConstant;
import com.nari.slsd.hydro.backend.unified.dobj.ADXLD;
import com.nari.slsd.hydro.backend.unified.dobj.HFIdx;
import com.nari.slsd.hydro.backend.unified.dobj.HFRunConSet;
import com.nari.slsd.hydro.backend.unified.dobj.HPSTimeDoubleValue;
import com.sgepri.ce.db.ResultSetInterface;
import com.sgepri.ce.db.util.DBOpUtil;
import com.sgepri.ce.db.util.DBSplitUtil;
import com.sgepri.ce.util.TimeFunc;
import com.yr.wds.xinyidai.common.cev.flood.enums.SequenceType;
import com.yr.wds.xinyidai.common.cev.flood.util.*;
import org.apache.commons.lang.StringUtils;
import org.dom4j.io.SAXReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;

@PropertySource(value = {"classpath:floodConfig.properties",}, encoding = "utf-8")
@Component
public class FloodResults {

    private static int defaulttype;

    private static Map<String, String> typeMap;
    @Value("${type}")
    private int type;

    @Value("${shortTerm.hWindow}")
    private String shortTermHWindow;

    @Value("${shortTerm.hfScheme}")
    private String shortTermHfScheme;

    @Value("${flow.senids}")
    private String flow_senids;
    private static JSONObject flowSenids;

    private static File hWindow;

    private static File hfScheme;

    @Value("${service.cron.saveabnormalweather.isMajor}")
    private boolean isMajor;
    private static boolean ISMAJOR;

    @Value("${watershedStatistics.dm}")
    private String watershedStatisticsDm;
    private static String WATERSHEDSTATISTICS_DM;

    @Value("${watershedStatistics.river}")
    private String watershedStatisticsRiver;
    private static String WATERSHEDSTATISTICS_RIVER;

    @Value("${watershedStatistics.basinName}")
    private String watershedStatisticsBasinName;
    private static String WATERSHEDSTATISTICS_BASINNAME;

    @Value("${watershedStatistics.foreObjName}")
    private String watershedStatisticsForeObjName;
    private static String WATERSHEDSTATISTICS_FOREOBJNAME;

    @Value("${watershedStatistics.weights}")
    private String watershedStatisticsWeights;
    private static double[] WATERSHEDSTATISTICS_WEIGHTS;

    @PostConstruct
    public void init() {
        this.defaulttype = type;

        //读取前端的中英文配置
        String chorEn = JsonToObjUtil.getChineseOrEnglish();

        //先获取相对地址的conf文件，如果不存在获取floodConfig.properties配置文件中的地址
        boolean flag = FileUtil.exist(System.getProperty("user.dir") + "/conf/forecast");

        if (flag) {
            this.hWindow = new File(System.getProperty("user.dir") + "/conf/forecast/HFWindowConf.xml");

            this.hfScheme = new File(System.getProperty("user.dir") + "/conf/forecast/HFSchemeConf.xml");
        } else {
            this.hWindow = new File(shortTermHWindow);

            this.hfScheme = new File(shortTermHfScheme);
        }

        flowSenids = JSON.parseObject(new String(flow_senids.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));

        hfWebService = new HFWebService(chorEn, this.hWindow, this.hfScheme);

        this.typeMap = hfWebService.getInternationalMap();

        ISMAJOR = isMajor;
        WATERSHEDSTATISTICS_DM = new String(watershedStatisticsDm.getBytes(StandardCharsets.ISO_8859_1)).replaceAll(" ", "");
        WATERSHEDSTATISTICS_RIVER = new String(watershedStatisticsRiver.getBytes(StandardCharsets.ISO_8859_1)).replaceAll(" ", "");
        WATERSHEDSTATISTICS_BASINNAME = new String(watershedStatisticsBasinName.getBytes(StandardCharsets.ISO_8859_1)).replaceAll(" ", "");
        WATERSHEDSTATISTICS_FOREOBJNAME = new String(watershedStatisticsForeObjName.getBytes(StandardCharsets.ISO_8859_1)).replaceAll(" ", "");
        String[] strArray = watershedStatisticsWeights.replaceAll(" ", "").split(",");
        WATERSHEDSTATISTICS_WEIGHTS = new double[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            WATERSHEDSTATISTICS_WEIGHTS[i] = Double.parseDouble(strArray[i]);
        }
    }

    public static HFWebService hfWebService = null;


    private static List<HFSection> hfSectionList = new LinkedList<>();

    private static String TIME_STYLE = "Time";

    public static Message defaultData(JSONObject params) {

        Map<String, Object> result = new HashMap<>();

//       HFWebService hfWebService = new HFWebService();

        //方案类型
        List<String> faList = hfWebService.getAllSchemeNameList();

        //获取所有的预报模式
        List<String> ybList = hfWebService.getAllForeModelList();

        //根据方案类型获取所有得流域
        Map<String, List<String>> lyMap = new HashMap<>();

        //所有预报对象
        Map<String, List<String>> ybObList = new HashMap<>();

        //类型对应的时间
        Map<String, String> timeMap = new HashMap<>();

        faList.forEach(x -> {
            List<String> lyList = hfWebService.getAllBasinNameList(x);
            lyMap.put(x, lyList);


            //获取指定方案下某一个流域下的所有预报对象
            lyList.forEach(k -> {
                List<String> l = hfWebService.getAllForeObjNameList(x, k);   //=>方案类型名，流域名
                ybObList.put(k, l);
            });

            //获取页面显示的时间
            Calendar calendar = hfWebService.getForecastBTime(params.getString("userName"), x);

            RSTime.RSTimeType timeType=  RSTime.RSTimeType.getTimeTypeByDescription(x);

            String time = DataTransferUtil.CalendarToString(calendar, timeType, RSTime.RSTimeFormat.LINE);

            timeMap.put(x, time);
        });

        result.put("faList", faList);       //方案类型
        result.put("ybList", ybList);       //预报模式
        result.put("lyMap", lyMap);         //流域
        result.put("ybObList", ybObList);   //预报对象
        result.put("timeList", timeMap);


        return new Message().ok(666, "success").addData("data", result);

    }


    //获取流域信息
    public static Set<String> getRiverBasin() {

        Set<String> riverBasin = new HashSet<>();

        //方案类型
        List<String> faList = hfWebService.getAllSchemeNameList();

        faList.forEach(x -> {
            List<String> lyList = hfWebService.getAllBasinNameList(x);

            riverBasin.addAll(lyList);
        });

        return riverBasin;
    }


    public static Message initReadConditions(JSONObject jsonObject) throws Exception {

        String time = jsonObject.getString("time");

        String falx = jsonObject.getString("falx");

        String userName = jsonObject.getString("userName");

        //根据时间字符串获取时间类型
        RSTime.RSTimeType timeType=  RSTime.RSTimeType.getTimeTypeByDescription(falx);
        //将时间转Calendar
        Calendar foreBTime = DataTransferUtil.stringToCalendar(time, timeType, RSTime.RSTimeFormat.LINE);

        List<String> foreObjNameVec = JSONObject.parseArray(jsonObject.getString("ybdx"), String.class);

        hfWebService.initReadConditions(userName, jsonObject.getString("falx"), jsonObject.getString("ybms"), foreBTime, jsonObject.getString("ly"), foreObjNameVec);

        return new Message().ok(HttpStatus.HTTP_OK, "success");
    }

    public static Message runoffParam(JSONObject params) {

        String schemeName = params.getString("schemeName");
        String userName = params.getString("userName");
        Map<HFSchUnit, Map<String, String>> map = hfWebService.getUnitParamMap(userName, schemeName);

        Map<Long, Map<String, String>> answer = new HashMap<>();
        for (HFSchUnit hfSchUnit : map.keySet()) {

            Map<String, String> v = map.get(hfSchUnit);
            v.put("name", hfSchUnit.getName());
            answer.put(hfSchUnit.getId(), v);
        }


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("data", answer);
    }
    public static Message init(JSONObject jsonObject) throws Exception {

        String userName = jsonObject.getString("userName");

        List<String> foreObjNameVec = JSONObject.parseArray(jsonObject.getString("ybdx"), String.class);

        Map<String, Object> initMap = hfWebService.readData(userName);

        Map<String, String> nameMap = new HashMap<>();

        foreObjNameVec.forEach(x -> {
            nameMap.put(x, x);
            //对应方法：static List<String> getAllSectionNameByForeObj(String schemeTypeStr, String basinName,String foreObjName)
            //传入参数：方案类型名，流域名，预报对象名
            List<HFSection> l = hfWebService.getAllSectionByForeObj(jsonObject.getString("falx"), jsonObject.getString("ly"), x);

            //将所有断面保存在全局变量
            hfSectionList.addAll(l);

            l.forEach(k -> {
                nameMap.put(k.getName(), x);
            });
        });

        List<String> list = Arrays.asList("1,3,6".split(","));

        //页面功能：获取某个流域下所有断面历史流量的最大最小值
        Map<String, Map<String, String>> map = hfWebService.getSecHisCharaMap(userName, jsonObject.getString("ly"));

        Map<String, Map<String, Object>> resultMap = new HashMap<>();

        for (String str : map.keySet()) {

            Map<String, String> m = map.get(str);

            Map<String, Object> map1 = new HashMap<>();

            if (null != m.get("显示内容")) {

                String v = m.get("显示内容");

                List<String> l = new ArrayList<>();
                l.add(v);

                map1.put("显示内容", l);

            } else {

                map1.putAll(m);


            }
            resultMap.put(str, map1);

        }

        //24.获取指定预报对象集合下的所有水库断面集合
        //对应方法：Map<HFSection, String> getAllReservoirSecMap(String userName, String schemeTypeStr, String basinName,List<String> foreObjNameList)
        //传入参数：用户名，方案类型名，流域名，预报对象名称集合
        //返回格式：主键是断面对象，值是对应的预报对象名

        Map<HFSection, String> mapSk = hfWebService.getAllReservoirSecMap(userName, jsonObject.getString("falx"), jsonObject.getString("ly"), foreObjNameVec);

        Map<String, Object> skdd = new HashMap<>();

        for (HFSection hfSection : mapSk.keySet()) {

            skdd.put(hfSection.getName(), true);
        }

        //获取所有方案
        Map<String, Long> fanMap = hfWebService.getAllModelNameList(userName);

        List<JSONObject> fanList = new ArrayList<>();

        for (String str : fanMap.keySet()) {

            JSONObject object = JSONObject.parseObject("{\"name\": \"" + str + "\",\"value\": \"" + fanMap.get(str) + "\"}");

            fanList.add(object);

        }

        Message message = new Message().ok(666, "初始化成功");
        //根据流域获取雨量数据
        if (MapUtil.isNotEmpty(fanMap)) {

            jsonObject.put("fanAn", fanList.get(0).getString("name"));

            //判断获取的是预报还是实测
            String forecastMeasurement = jsonObject.getString("type");

            Message me;

            if ((boolean) initMap.get("是否实时预报")) {
                me = getRainfallBySource(jsonObject);
            } else {
                me = getRainfallByLy(jsonObject);
            }

            message.addData("rainData", me.getData());
        }

        List<Map<String, String>> sectionList = hfWebService.getAllSectionLatestInfo(userName);

        Map<String, Map<String, String>> sectionLatestInfo = new HashMap<>();

        for (Map<String, String> map1 : sectionList) {
            sectionLatestInfo.put(map1.get("SectionName"), map1);
        }


        return message
                .addData("initObj", nameMap)
                .addData("radio", list)
                .addData("state", resultMap)
                .addData("skdd", skdd)
                .addData("fanAn", fanList)
                .addData("sectionLatestInfo", sectionLatestInfo)
                .addData("buttonShow", initMap);

    }

    //
    public static Message getHistoricalData(JSONObject jsonObject) {

        //获取断面历史数据(用户名，所选流域名，断面所属预报对象名，断面名称)
        Map<String, Map<String, Object>> map = hfWebService.getHisDataMapBySectionName(jsonObject.getString("userName"), jsonObject.getString("ly"), jsonObject.getString("dx"), jsonObject.getString("name"));
        //将相同时间数据组装
        Map<String, JSONObject> v = new HashMap<>();

        List<JSONObject> tableHeader = new LinkedList<>();
        String userName=jsonObject.getString("userName");
        RSTime.RSTimeType rsTimeType= getTimeKey(userName);

        //时间key

        String typeFlag = typeMap.get("时间");


        tableHeader.add(JSONObject.parseObject("{prop:\"" + TIME_STYLE + "\",label:\"" + typeFlag + "\"}"));

        //组装表头
        for (String key : map.keySet()) {

            Map<String, Object> caMap = map.get(key);

            JSONObject j = new JSONObject();
            j.put("prop", key);
            j.put("showName", key + "(" + caMap.get("unit") + ")");
            j.put("label", key + "(" + caMap.get("unit") + ")");
            j.put("edit", caMap.get("editable"));
            j.put("type", caMap.get("type"));
            j.put("unit", caMap.get("unit"));
            j.put("serieColor", caMap.get("serieColor"));
            tableHeader.add(j);

            if (null != caMap.get("data")) {

                Map<Calendar, String> val = (Map<Calendar, String>) caMap.get("data");

                for (Calendar calendar : val.keySet()) {
                    //时间
                    String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

                    String value = val.get(calendar);

                    JSONObject js = new JSONObject();

                    js.put(TIME_STYLE, time);

                    long stamp = DateUtil.parse(time, "yyyy-MM-dd HH:mm:ss").getTime();

                    js.put("stamp", stamp);

                    js.put(key, value);

                    if (null == v.get(time)) {
                        v.put(time, js);
                    } else {
                        JSONObject js1 = v.get(time);
                        js1.putAll(js);
                        v.put(time, js1);
                    }
                }

            }


        }
//
//
        List<JSONObject> tableData = new ArrayList<>();

        for (String key : v.keySet()) {
            tableData.add(v.get(key));
        }

        tableData = tableData.stream().sorted(Comparator.comparing(x -> DateUtil.parse(x.getString(TIME_STYLE), "yyyy-MM-dd HH:mm:ss").getTime())).collect(Collectors.toList());

        //13.获取指定显示历史默认长度
        //对应方法：Integer getShowHisLength(String userName)
        //传入参数：用户名
        //返回格式：显示历史长度。
        Integer num = hfWebService.getShowHisLength(jsonObject.getString("userName"));

        return new Message().ok(666, "success").addData("tableData", tableData).addData("tableHeader", tableHeader).addData("hisNum", num);
    }


    public static Message updateHistoricalData(JSONObject params) {

        List<JSONObject> list = JSONObject.parseArray(JSON.toJSONString(params.getJSONArray("data")), JSONObject.class);

        //流域名称
        String ly = params.getString("ly");
        //断面名称
        String name = params.getString("name");
        //对象名称
        String dx = params.getString("dx");

        //时间标志位
        String timeKey = typeMap.get("时间");

        List<Map<String, Object>> mapList = list.stream().map(x -> {


                    Map<String, Object> map = new HashMap<>();

                    Iterator iter = x.entrySet().iterator();

                    while (iter.hasNext()) {

                        Map.Entry entry = (Map.Entry) iter.next();

                        String key = entry.getKey().toString();

                        if (!StringUtils.equalsIgnoreCase(key, TIME_STYLE) && !StringUtils.equalsIgnoreCase(key, "stamp")) {

                            Double value = x.getDouble(key);

                            map.put(key, value);
                        }

                        //时间转Calendar
                        if (StringUtils.equalsIgnoreCase(key, TIME_STYLE)) {

                            Calendar time = TimeUtil.sectionName(x.getString(TIME_STYLE), DateFormatter.SECOND_FORMATTER);

                            map.put(timeKey, time);
                        }

                    }

                    return map;
                }
        ).collect(Collectors.toList());

        hfWebService.modifySectionHisData(params.getString("userName"), ly, dx, name, mapList);

        //页面功能：获取某个流域下所有断面历史流量的最大最小值
        Map<String, Map<String, String>> map = hfWebService.getSecHisCharaMap(params.getString("userName"), ly);

        Map<String, Map<String, Object>> resultMap = new HashMap<>();

        for (String str : map.keySet()) {

            Map<String, String> m = map.get(str);

            Map<String, Object> map1 = new HashMap<>();

            if (null != m.get("显示内容")) {

                String v = m.get("显示内容");

                List<String> l = new ArrayList<>();
                l.add(v);

                map1.put("显示内容", l);

            } else {

                map1.putAll(m);


            }
            resultMap.put(str, map1);

        }

        //获取修改后的数据
        Message message = getHistoricalData(params);
        message.addData("result", resultMap);

        return message;
    }


    public static Message floodCalculation(JSONObject params, boolean flag) {

        //用户名
        String userName = params.getString("userName");

        //预报对象名
        List<String> foreObjNameVec = JSONObject.parseArray(params.getString("ybdx"), String.class);

        //流域名
        String ly = params.getString("ly");

        //方案类型
        String falx = params.getString("falx");

        boolean isSimu = null == params.getBoolean("isSimu") ? false : params.getBoolean("isSimu");

        if (flag) {
            hfWebService.forecast(userName, isSimu);
        }

        //获取指定断面的模型计算结果的统计值
        //对应方法：Map<String, Map<String, String>> getSecCharaMap(String userName, String basinName, String foreObjName,String sectionName, int type)
        //传入参数：用户名，流域名，预报对象名，断面名，序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值）
        //返回格式：两层map，外层map的主键是模型名称，内层map的主键是统计值名称（最大值，最小值），内层map的值是统计值

        Map<Integer, Map<String, Map<String, Object>>> result = new HashMap<>();

        //根据预报对象获取断面名
        foreObjNameVec.forEach(x -> {

            //获取断面
            List<HFSection> l = hfWebService.getAllSectionByForeObj(falx, ly, x);

            l.forEach(k -> {
                //Map<String, Map<String, String>> getSecCharaMap(String userName, String basinName, String foreObjName,String sectionName,boolean isFirstSec, int type)
                //传入参数：用户名，流域名，预报对象名，断面名，是否是龙头断面，序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值）
                //返回格式：两层map，外层map的主键是模型名称，内层map的主键是统计值名称（最大值，最小值），内层map的值是统计值

                boolean flagGer = k.getSecClsType().equals(EnumConstant.HFSecClsType.firstSection) ? true : false;

                Map<Integer, Map<String, Map<String, String>>> map = hfWebService.getSecCharaMap(userName, ly, x, k.getName(), flagGer);

                for (Integer integer : map.keySet()) {

                    Map<String, Map<String, String>> intMap = map.get(integer);

                    List<String> list = new ArrayList<>();
                    for (String str : intMap.keySet()) {

                        Map<String, String> strMap = intMap.get(str);

                        String value = strMap.get("显示内容");

                        if (intMap.size() == 1) {
                            list.add(value);
                        } else {
                            list.add(str + ":" + value);
                        }

                    }

                    Map<String, Object> resultMap = new HashMap<>();

                    resultMap.put("显示内容", list);

                    if (null == result.get(integer)) {
                        Map<String, Map<String, Object>> map1 = new HashMap<>();
                        map1.put(k.getName(), resultMap);
                        result.put(integer, map1);

                    } else {
                        Map<String, Map<String, Object>> map1 = result.get(integer);
                        map1.put(k.getName(), resultMap);
                        result.put(integer, map1);
                    }

                }


            });


        });

        return jisuan(foreObjNameVec, ly, falx, userName);

    }


    /**
     * 获取预报计算
     *
     * @param foreObjNameVec 预报对象名
     * @param ly             流域
     * @param falx           方案类型
     * @return
     */
    public static Message jisuan(List<String> foreObjNameVec, String ly, String falx, String userName) {
        //获取指定断面的模型计算结果的统计值
        //对应方法：Map<String, Map<String, String>> getSecCharaMap(String userName, String basinName, String foreObjName,String sectionName, int type)
        //传入参数：用户名，流域名，预报对象名，断面名，序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值）
        //返回格式：两层map，外层map的主键是模型名称，内层map的主键是统计值名称（最大值，最小值），内层map的值是统计值

        Map<Integer, Map<String, Map<String, Object>>> result = new HashMap<>();

        //根据预报对象获取断面名
        foreObjNameVec.forEach(x -> {

            //获取断面
            List<HFSection> l = hfWebService.getAllSectionByForeObj(falx, ly, x);

            l.forEach(k -> {
                //Map<String, Map<String, String>> getSecCharaMap(String userName, String basinName, String foreObjName,String sectionName,boolean isFirstSec, int type)
                //传入参数：用户名，流域名，预报对象名，断面名，是否是龙头断面，序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值）
                //返回格式：两层map，外层map的主键是模型名称，内层map的主键是统计值名称（最大值，最小值），内层map的值是统计值

                boolean flagGer = k.getSecClsType().equals(EnumConstant.HFSecClsType.firstSection) ? true : false;

                Map<Integer, Map<String, Map<String, String>>> map = hfWebService.getSecCharaMap(userName, ly, x, k.getName(), flagGer);

                for (Integer integer : map.keySet()) {

                    Map<String, Map<String, String>> intMap = map.get(integer);

                    List<String> list = new ArrayList<>();
                    for (String str : intMap.keySet()) {

                        Map<String, String> strMap = intMap.get(str);

                        String value = strMap.get("显示内容");

                        if (intMap.size() == 1) {
                            list.add(value);
                        } else {
                            list.add(str + "-" + value);
                        }

                    }

                    Map<String, Object> resultMap = new HashMap<>();

                    resultMap.put("显示内容", list);

                    if (null == result.get(integer)) {
                        Map<String, Map<String, Object>> map1 = new HashMap<>();
                        map1.put(k.getName(), resultMap);
                        result.put(integer, map1);

                    } else {
                        Map<String, Map<String, Object>> map1 = result.get(integer);
                        map1.put(k.getName(), resultMap);
                        result.put(integer, map1);
                    }

                }


            });


        });


        return new Message().ok(666, "计算成功").addData("data", result);

    }


    /**
     * 获取时间类型
     * @param userName
     * @return
     */
    private static RSTime.RSTimeType getTimeKey(String userName) {

        //获取当前时间类型
        HFScheme foundScheme = hfWebService.getSelectedScheme(userName);

       return  foundScheme.getTimeType();
    }

    public static Message achievementDisplay(JSONObject params) throws Exception {



        //表格组件表头
        List tr1 = new ArrayList();
        List tr2 = new ArrayList();
        List tr3 = new ArrayList();
        //时间类型key
        String timeKey = typeMap.get("时间");
        tr1.add(timeKey);
        tr2.add(timeKey);
        tr3.add(timeKey);

        //ly:流域名称，dx：对象名称，name ：场站名称
        Map<String, Object> result = new HashMap<>();
        //流域名称
        String basinName = params.getString("ly");
        //预报对象名
        String foreObjName = params.getString("dx");
        //断面名
        String sectionName = params.getString("name");
        //用户名
        String userName = params.getString("userName");
        //置信区间
        String[] probArray=null;
        if(null == params.get("probArray")){
             probArray = hfWebService.getProbArray(userName);
        }else{
             probArray = params.getJSONArray("probArray").toArray(new String[params.getJSONArray("probArray").size()]);
        }

        //序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值）
        String type = params.getString("type");

        RSTime.RSTimeType rsTimeType = getTimeKey(userName);

        //接受模型方案
        List<String> modelS = null;
        if (null != params.getJSONArray("modelScheme")) {
            modelS = JSONArray.parseArray(params.getJSONArray("modelScheme").toJSONString(), String.class);
        }
        if (StringUtils.isBlank(type)) {
            type = SequenceType.SEQUENCE_TYPE1.getIndex() + "";  //默认不考虑降雨
        }
        Map<String, JSONObject> o = new HashMap<>();
        String finalType = type;
        List<JSONObject> tableHeader = new LinkedList<>();
        //时间第一列
        JSONObject no1 = new JSONObject();
        no1.put("prop", TIME_STYLE);
        no1.put("label", timeKey);
        tableHeader.add(no1);
        //二级表头
        List<JSONObject> secondHeader = new LinkedList<>();
        JSONObject first = new JSONObject();
        first.put("label", sectionName);
        List<Integer> typeList = Arrays.asList(finalType.split(",")).stream().map(x -> Integer.parseInt(x)).collect(Collectors.toList());
        boolean flag = false;
        //获取全局的断面,判断是都是龙头断面
        for (int i = 0; i < hfSectionList.size(); i++) {
            HFSection hfSection = hfSectionList.get(i);
            if (hfSection.getName().equals(sectionName)) {
                flag = hfSection.getSecClsType().equals(EnumConstant.HFSecClsType.firstSection) ? true : false;
            }
        }
        //获取实测值
        //对应方法：List<Map<String, Object>> getSecObservedListMap(String userName, String basinName, String foreObjName,String sectionName)
        //传入参数：用户名，流域名，预报对象名，断面名）
        //返回格式：主键分别为类型type、名称name以及数据data
        List<Map<String, Object>> hisList = hfWebService.getSecObservedListMap(userName, basinName, foreObjName, sectionName);
        //System.out.println(sectionName + flag);
        //(传入参数：用户名，流域名，预报对象名，模型方案名，断面名，序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值）
        Map<String, Map<String, Map<String, Object>>> secForecastMap = hfWebService.getSecForecastListMap(userName, basinName, foreObjName, sectionName, flag, typeList);

        List<Map<String, Object>> mapList = hfWebService.getSecProbForecastListMap(userName, foreObjName, sectionName, probArray);
        Map<String, Object> probMap = new LinkedHashMap<>();
        for (Map<String, Object> map : mapList) {
            String name = (String) map.get("confidence");
            Map<Calendar, String> upperList = (Map) map.get("upperBound");
            Map<Calendar, String> lowerList = (Map) map.get("lowerBound");
            List<JSONObject> upperBound = getDataByCalendar(upperList);
            List<JSONObject> lowerBound = getDataByCalendar(lowerList);
            Map<String, List<JSONObject>> valProbMap = new LinkedHashMap<>();
            valProbMap.put("upperBound", upperBound);
            valProbMap.put("lowerBound", lowerBound);
            probMap.put(name, valProbMap);
        }
        //44.获取指定断面的预报界面序列展示控制
        //对应方法：Map<String, Boolean> getSecShowConfig(String userName, String basinName, String foreObjName,String sectionName)
        //传入参数：用户名，流域名，预报对象名，断面名）
        //返回格式：主键是序列名称，值是是否默认展示
        Map<String, Boolean> showConfigMap = hfWebService.getSecShowConfig(userName, basinName, foreObjName, sectionName);
        //如果预测中没有水位数据。把实测中的水位数据删除。
        Set<String> secondSet = new LinkedHashSet<>();
        boolean waterFlag = false;
        //所有模型方案
        Set<String> modelScheme = new LinkedHashSet<>();
        for (String str : secForecastMap.keySet()) {
            Map<String, Map<String, Object>> sMap = secForecastMap.get(str);
            modelScheme.add(str);
            for (String keyName : sMap.keySet()) {
                String t = EnumConstant.HFForeResType.getForeResTypeDescription(EnumConstant.HFForeResType.getForeResType(keyName));
                secondSet.add(t);
                //判断是否存在水位 type:water
                Map<String, Object> re = sMap.get(keyName);
                String allType = (String) re.get("type");
                if (StringUtils.equalsIgnoreCase(allType, "water")) {
                    waterFlag = true;
                }
            }
        }
        //如果waterFlag=false 删除实测中的水位数据
        if (!waterFlag) {
            Iterator<Map<String, Object>> iterator = hisList.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> m = iterator.next();
                String waterType = (String) m.get("type");
                if (StringUtils.equalsIgnoreCase(waterType, "water")) {
                    iterator.remove();
                }
            }
        }
        for (int i = 0; i < hisList.size(); i++) {
            Map<String, Object> map = hisList.get(i);
            //组装表头
            //外层
            JSONObject outSide = new JSONObject();
            outSide.put("label", map.get("name"));
            String prop = (String) map.get("name");
            //下一层
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", map.get("type"));
            jsonObject.put("showName", map.get("showName"));
            jsonObject.put("prop", prop);
            jsonObject.put("label", map.get("unit"));
            jsonObject.put("edit", map.get("editable"));
            jsonObject.put("serieColor", map.get("serieColor"));
            outSide.put("children", CollUtil.newArrayList(jsonObject));
            secondHeader.add(outSide);
            //将数据先放入o（Map）用于过滤相同时间的数据
            Map<Calendar, String> dataMap = (Map) map.get("data");
            for (Calendar calendar : dataMap.keySet()) {
                //时间
                String time= DataTransferUtil.CalendarToString(calendar,rsTimeType, RSTime.RSTimeFormat.LINE);
                JSONObject jsonObject1 = new JSONObject();
                String v = dataMap.get(calendar);
                jsonObject1.put(prop, v);
                jsonObject1.put(TIME_STYLE, time);
                if (null != o.get(time)) {
                    JSONObject jsonObject2 = o.get(time);
                    jsonObject2.putAll(jsonObject1);
                } else {
                    o.put(time, jsonObject1);
                }
            }
            tr1.add(map.get("name"));
            tr2.add(map.get("unit"));
            tr3.add(map.get("unit"));
        }

        List<String> finalModelS = modelS;
        //[
        //  ["时间","实测流量",{"label":"面雨量","colspan":2},{"label":"预报校正流量","colspan":2},{"label":"计算出库","colspan":2},{"label":"计算水位","colspan":2},"最高水位","最低水位"],
        //  ["时间","m³/s","面雨量方案","遥测雨量方案","面雨量方案","遥测雨量方案","面雨量方案","遥测雨量方案","面雨量方案","遥测雨量方案",{"label":"m","colspan":2}],
        //  ["时间","m³/s","mm","mm","m³/s","m³/s","m³/s","m³/s","m","m","m","m"]
        //                    ]
        List<JSONObject> tableHeaderSecond = secondSet.stream().map(x -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("label", x);
            List<JSONObject> list = new LinkedList<>();
            for (String str : secForecastMap.keySet()) {
                //判断模型方案是否存在
                if (CollUtil.isNotEmpty(finalModelS) && !finalModelS.contains(str)) {
                    continue;
                }
                Map<String, Map<String, Object>> seMap = secForecastMap.get(str);
                for (String keyName : seMap.keySet()) {
                    String nameType = EnumConstant.HFForeResType.getForeResTypeDescription(EnumConstant.HFForeResType.getForeResType(keyName));
                    if (StringUtils.equalsIgnoreCase(nameType, x)) {
                        //外层表头
                        JSONObject outside = new JSONObject();
                        outside.put("label", str);
                        JSONObject object = new JSONObject();
                        //两河口-Forecast discharge(Avg)-新安江模型方案-m_FPAvgQCal
                        String prop = sectionName + "-" + nameType + "-" + str + "-" + keyName;
                        object.put("prop", prop);
                        object.put("label", seMap.get(keyName).get("unit"));
                        object.put("type", seMap.get(keyName).get("type"));
                        object.put("edit", seMap.get(keyName).get("editable"));
                        object.put("confirmInfo", seMap.get(keyName).get("confirmInfo"));
                        object.put("dispatch", seMap.get(keyName).get("dispatch"));
                        object.put("showName", seMap.get(keyName).get("showName"));
                        object.put("modelSchName", str);
                        object.put("serieColor", seMap.get(keyName).get("serieColor"));
                        outside.put("children", CollUtil.newArrayList(object));
                        list.add(outside);
                        //将表格数据存入o（Map）,用于过滤时间
                        Map<String, Object> vMap = seMap.get(keyName);
                        Map<Calendar, String> data = (Map) vMap.get("data");
                        for (Calendar calendar : data.keySet()) {
                            String v = data.get(calendar);
                            //时间
                            String time= DataTransferUtil.CalendarToString(calendar,rsTimeType, RSTime.RSTimeFormat.LINE);
                            //value
//                            Double value = hfDataPoint.getFirstValue();
                            JSONObject j = new JSONObject();
                            j.put(TIME_STYLE, time);
                            j.put(prop, v);
                            if (null == o.get(time)) {
                                o.put(time, j);
                            } else {
                                JSONObject js = o.get(time);
                                js.putAll(j);
                                o.put(time, js);
                            }
                        }
                        tr3.add(seMap.get(keyName).get("unit"));
                    }
                }

                tr2.add(str);
            }
            //当只有一个方案的时候，方案不显示
            if (MapUtil.isNotEmpty(secForecastMap) && secForecastMap.size() == 1) {
                jsonObject.putAll(list.get(0));
                jsonObject.put("label", x);
            } else {
                jsonObject.put("children", list);
            }
            tr1.add(JSONObject.parseObject("{\"label\": \"" + x + "\",\"colspan\": " + list.size() + "}"));
            return jsonObject;
        }).collect(Collectors.toList());
        secondHeader.addAll(tableHeaderSecond);
        //58.获取断面调度限制信息
        //对应方法：List<Map<String, Object>> getSecDispatchLimitedInfo(String userName, String foreObjName,
        //			String sectionName)
        //传入参数：用户名，预报对象名，断面名
        //返回格式：两层map，外层主键为模型名称，内层主键为预报序列名称，最内侧的主键为序列键，type为序列类型，data为数据序列
        List<Map<String, Object>> hfList = hfWebService.getSecDispatchLimitedInfo(userName, foreObjName, sectionName);
        for (Map<String, Object> map : hfList) {
            JSONObject tr = new JSONObject();
            String name = map.get("name").toString();
            tr.put("label", map.get("showName"));
            //将数据加入tableData
            HashMap<Calendar, String> hashMap = (HashMap<Calendar, String>) map.get("data");
            for (Calendar calendar : hashMap.keySet()) {
                String time= DataTransferUtil.CalendarToString(calendar,rsTimeType, RSTime.RSTimeFormat.LINE);
                String v = hashMap.get(calendar);
                JSONObject jsonObject = o.get(time);
                if (null == jsonObject) {
                    JSONObject val = JSONObject.parseObject("{\"" + name + "\": \"" + v + "\"}");
                    o.put(time, val);
                } else {
                    jsonObject.put(name, v);
                }
            }
            map.remove("data");
            JSONObject js = JSONObject.parseObject(JSONObject.toJSONString(map), JSONObject.class);
            js.put("prop", js.getString("name"));
            js.put("label", js.get("unit"));
            js.put("type", js.get("type"));
            js.put("edit", js.get("editable"));
            //不展示
            js.put("showCol", false);
            JSONArray array = new JSONArray();
            array.add(js);
            tr.put("children", array);
            secondHeader.add(tr);
        }

//        first.put("children", secondHeader);
//
//
//        tableHeader.add(first);

//        tableHeader.addAll(tableHeaderSecond);
        tableHeader.addAll(secondHeader);
        List<JSONObject> tableData = new ArrayList<>();
//        tableData = o.values().stream().sorted(Comparator.comparing(x -> DateUtil.parse(x.getString(TIME_STYLE), timeType))).collect(Collectors.toList());
        tableData = o.values().stream()
                .sorted(Comparator.comparing(x -> {
                    try {
                        return DataTransferUtil.stringToCalendar(x.getString(TIME_STYLE),rsTimeType, RSTime.RSTimeFormat.LINE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }))
                .collect(Collectors.toList());
        result.put("tableHeader", tableHeader);
        List specialTableHeader = new ArrayList();
        specialTableHeader.add(tr1);
        specialTableHeader.add(tr2);
        specialTableHeader.add(tr3);
        result.put("specialTableHeader", specialTableHeader);
        result.put("tableData", tableData);
        JSONObject markArea = new JSONObject();
        JSONArray markAreaList = new JSONArray();


        if (ISMAJOR) {
            String plantid = flowSenids.getString(params.getString("name")).substring(0, 6);
            //获取时段开始结束,重大天气标识

            Calendar startDate=  DataTransferUtil.stringToCalendar(tableData.get(0).getString("Time"),rsTimeType, RSTime.RSTimeFormat.LINE);

            Calendar endDate = DataTransferUtil.stringToCalendar(tableData.get(tableData.size() - 1).getString("Time"),rsTimeType, RSTime.RSTimeFormat.LINE);
            if (rsTimeType==RSTime.RSTimeType.RSHour) {
                startDate = TimeFunc.getPeriodEnd(5, startDate);
                endDate = TimeFunc.getPeriodBeg(5, TimeFunc.add(endDate, 5, -1));
            }
            //预报重大天气
            {
                //String sql = "select time,v from daydb where senid = " + plantid + "90 and time >= " + TimeFunc.toDbString(startDate) + " and time <= " + TimeFunc.toDbString(endDate);
                ResultSetInterface rs = DBSplitUtil.exeTimeDBSelSql("daydb", startDate.getTimeInMillis(), endDate.getTimeInMillis(), plantid + "90", "time,v");
                if (rs.getRowNum() > 0) {
                    String firstDeviationTime = null;
                    String lastDeviationTime = null;
                    for (int i = 0; i < rs.getRowNum(); i++) {

                        if (rs.getDouble(i, 1) == -1) {
                            if (firstDeviationTime == null) {
                                firstDeviationTime= DataTransferUtil.CalendarToString(rs.getDate(i, 0),rsTimeType, RSTime.RSTimeFormat.LINE);
                            }

                            lastDeviationTime=  DataTransferUtil.CalendarToString(rs.getDate(i, 0),rsTimeType, RSTime.RSTimeFormat.LINE);
                        } else {
                            if (firstDeviationTime == null) continue;
                            try {
                                if (TimeFunc.getInterval(TimeFunc.parseDateTime(firstDeviationTime), TimeFunc.parseDateTime(lastDeviationTime), 5) >= 10) {
                                    JSONArray markAreaData = new JSONArray();
                                    markAreaList.add(markAreaData);
//                            markAreaData.add(JSON.parseObject("{itemStyle:{color:'red'},xAxis:'" + firstDeviationTime + "'}"));
                                    markAreaData.add(JSON.parseObject("{flag:-1,label:{position:\"insideBottom\",offset:[44,0]},name:\"低来水天气过程\",xAxis:'" + firstDeviationTime + "'}"));
                                    markAreaData.add(JSON.parseObject("{xAxis:'" + lastDeviationTime + "'}"));
                                }
                                firstDeviationTime = null;
                                lastDeviationTime = null;

                            } catch (Exception e) {
                            }
                        }

                    }
                    try {
                        if (firstDeviationTime != null) {
                            if (TimeFunc.getInterval(TimeFunc.parseDateTime(firstDeviationTime), TimeFunc.parseDateTime(lastDeviationTime), 5) >= 9) {
                                JSONArray markAreaData = new JSONArray();
                                markAreaList.add(markAreaData);
//                            markAreaData.add(JSON.parseObject("{itemStyle:{color:'red'},xAxis:'" + firstDeviationTime + "'}"));
                                markAreaData.add(JSON.parseObject("{flag:-1,label:{position:\"insideBottom\",offset:[44,0]},name:\"低来水天气过程\",xAxis:'" + firstDeviationTime + "'}"));
                                markAreaData.add(JSON.parseObject("{xAxis:'" + lastDeviationTime + "'}"));
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
            //调度重大天气
            {
                //String sql = "select time,v from daydb where senid = " + plantid + "91 and time >= " + TimeFunc.toDbString(startDate) + " and time <= " + TimeFunc.toDbString(endDate);
                ResultSetInterface rs = DBSplitUtil.exeTimeDBSelSql("daydb", startDate.getTimeInMillis(), endDate.getTimeInMillis(), plantid + "91", "time,v");
                if (rs.getRowNum() > 0) {

                    String firstDeviationTime = null;
                    String lastDeviationTime = null;
                    for (int i = 0; i < rs.getRowNum(); i++) {

                        if (rs.getDouble(i, 1) == 1) {
                            if (firstDeviationTime == null) {
                                firstDeviationTime= DataTransferUtil.CalendarToString(rs.getDate(i, 0),rsTimeType, RSTime.RSTimeFormat.LINE);
                            }

                            lastDeviationTime=  DataTransferUtil.CalendarToString(rs.getDate(i, 0),rsTimeType, RSTime.RSTimeFormat.LINE);
                        } else {
                            if (firstDeviationTime == null) continue;
                            try {
                                if (TimeFunc.getInterval(TimeFunc.parseDateTime(firstDeviationTime), TimeFunc.parseDateTime(lastDeviationTime), 5) >= 0) {
                                    JSONArray markAreaData = new JSONArray();
                                    markAreaList.add(markAreaData);
//                            markAreaData.add(JSON.parseObject("{itemStyle:{color:'red'},xAxis:'" + firstDeviationTime + "'}"));
                                    markAreaData.add(JSON.parseObject("{flag:1,label:{position:\"insideTop\",offset:[-37,0]},hbpc:1,name:\"涨水天气过程\",xAxis:'" + firstDeviationTime + "'}"));
                                    markAreaData.add(JSON.parseObject("{xAxis:'" + lastDeviationTime + "'}"));
                                }
                                firstDeviationTime = null;
                                lastDeviationTime = null;

                            } catch (Exception e) {
                            }
                        }

                    }
                    try {
                        if (firstDeviationTime != null) {
                            if (TimeFunc.getInterval(TimeFunc.parseDateTime(firstDeviationTime), TimeFunc.parseDateTime(lastDeviationTime), 5) >= 1) {
                                JSONArray markAreaData = new JSONArray();
                                markAreaList.add(markAreaData);
//                            markAreaData.add(JSON.parseObject("{itemStyle:{color:'red'},xAxis:'" + firstDeviationTime + "'}"));
                                markAreaData.add(JSON.parseObject("{flag:1,label:{position:\"insideTop\",offset:[-37,0]},hbpc:1,name:\"涨水天气过程\",xAxis:'" + firstDeviationTime + "'}"));
                                markAreaData.add(JSON.parseObject("{xAxis:'" + lastDeviationTime + "'}"));
                            }
                        }
                    } catch (Exception e) {
                    }
                }

            }

            if (markAreaList.size() > 0) {
                markArea.put("data", markAreaList);
                markArea.put("silent", true);
                result.put("markArea", markArea);
            }
        }
        //39.获取单个断面的水库调度设置信息
        //对应方法：Map<String, String> getSecDispatchSettingMap(String userName, String foreObjName, String secName)
        //传入参数：用户名，预报对象名，断面名
        //返回格式：map集合，主键是调洪演算，值是对应的数据

        //map=>水库调度设置信息
//        Map<String, String> singleSecMap = hfWebService.getSecDispatchSettingMap(userName, foreObjName, sectionName);

        //所有置信区间
        String[] strArr = hfWebService.getProbArray(userName);

        return new Message().ok(666, "success")
                .addData("data", result)
                .addData("modelScheme", modelScheme)
                //.addData("singleSecMap", singleSecMap)
                .addData("confidenceInterval", strArr)
                .addData("probForecast", probMap)
                .addData("showConfigMap", showConfigMap);
    }

    private static Double getAvgFlowByTime(String time, Map<String, Double> map, String timeType) {
        DateField dateField = DateField.HOUR_OF_DAY;
        String datePattern = "MM-DD HH";
        switch (timeType) {
            case "日":
                dateField = DateField.DAY_OF_YEAR;
                datePattern = "MM-DD";
        }
        DateTime dateTime = DateUtil.offset(DateUtil.parseDateTime(time), dateField, 1);
        String timePattern = DateUtil.format(dateTime, datePattern);

        return map.get(timePattern);
    }

    public static Message updateRunoffParam(JSONObject params) {

        String userName = params.getString("userName");
        String schemeName = params.getString("schemeName");
        Map<Long, Map<String, String>> answer = new HashMap<>();
        JSONObject dataObj = params.getJSONObject("data");
        for (String key : dataObj.keySet()) {
            JSONObject innerObj = dataObj.getJSONObject(key);
            Map<String, String> innerMap = new HashMap<>();
            for (String innerKey : innerObj.keySet()) {
                innerMap.put(innerKey, innerObj.getString(innerKey));
            }
            answer.put(Long.valueOf(key), innerMap);
        }

        boolean flag = hfWebService.modifyUnitParam(userName, schemeName, answer);

        if (!flag) {
            return new Message().ok(HttpStatus.HTTP_OK, "error");
        }

        return new Message().ok(HttpStatus.HTTP_OK, "success");
    }

    public static Message getNWPProductInfo(JSONObject params) {

        String userName = params.getString("userName");
        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", hfWebService.getNWPProductInfo(userName));
    }

    private static Map<String, Double> getAllAvgFlowByTime(Map<String, JSONObject> o, String senid, String timeType, int year) {

        Map<String, Double> map = new HashMap<>();
        if (MapUtil.isNotEmpty(o)) {
            Optional<Long> maxTime = o.keySet().stream().map(x -> DateUtil.parseDateTime(x).getTime()).max(Long::compareTo);
            Optional<Long> minTime = o.keySet().stream().map(x -> DateUtil.parseDateTime(x).getTime()).min(Long::compareTo);

            Calendar startCalendar = TimeFunc.add(TimeFunc.add(DateUtil.calendar(minTime.get()), timeType.equals("日") ? 5 : 6, 1), 0, -year);
            Calendar endCalendar = TimeFunc.add(TimeFunc.add(DateUtil.calendar(maxTime.get()), timeType.equals("日") ? 5 : 6, 1), 0, -1);

            String table = "hourdb";
            String datePattern = "MM-DD HH";
            String DateUtilPattern = "MM-dd HH";
            switch (timeType) {
                case "日":
                    table = "daydb";
                    datePattern = "MM-DD";
                    DateUtilPattern = "MM-dd";
            }
            String startDate = DateUtil.format(DateUtil.date(startCalendar), DateUtilPattern);
            String endDate = DateUtil.format(DateUtil.date(endCalendar), DateUtilPattern);

            String sql = "select TO_CHAR(time, '" + datePattern + "') as time,avg(v) as v from  " + table +
                    " where senid = " + senid + "  and time>='" + DateUtil.formatDateTime(DateUtil.date(startCalendar)) + "' and time<='" + DateUtil.formatDateTime(DateUtil.date(endCalendar)) + "'" +
                    " and  TO_CHAR(time, '" + datePattern + "') >='" + startDate + "' and  TO_CHAR(time, '" + datePattern + "') <='" + endDate + "' group by TO_CHAR(time, '" + datePattern + "')";

            try {
                ResultSetInterface rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
                if (rs != null) {
                    for (int i = 0; i < rs.getRowNum(); i++) {
                        String time = rs.getString(i, 0);
                        Double v = rs.getDouble(i, 1);
                        map.put(time, v);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
        return map;
    }

    //获取指定时间多年均值
    private static Double getAvgFlow(String senid, String type, Calendar time, int years) {
        Double flow = null;
        time = TimeFunc.add(time, type.equals("日") ? 5 : 6, 1);
        String table = "hourdb";
        switch (type) {
            case "日":
                table = "daydb";
        }
        String sql = "select avg(v) from " + table + " where senid = " + senid;
        if (years > 0) {
            sql += " and (1 = 0";
            for (int i = 1; i <= years; i++) {
                sql += " or time = " + TimeFunc.toDbString(TimeFunc.add(time, 0, -i));
            }
            sql += ")";
        } else {
            //达梦
            sql += " and to_char(time,'MM-DD hh24:mi:ss') = '" + TimeFunc.getFormatStr(time, "MM-dd HH:mm:ss") + "'";
        }
        try {
            ResultSetInterface rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            if (rs.getRowNum() > 0) flow = rs.getDouble(0, 0);
        } catch (Exception e) {
        }
        return flow;
    }

    public static Message updateForecast(JSONObject params) {

        //45.更新预报界面序列展示控制策略
        //对应方法：boolean updateShowConfigByMap(String userName, Map<String, Boolean> showConfigMap)
        //传入参数：用户名，序列展示控制接口返回格式
        //返回格式：true代表更新成功，false代表失败

        String userName = params.getString("userName");

        Map<String, Boolean> showConfigMap = JSONObject.parseObject(params.getJSONObject("showConfigMap").toJSONString(), Map.class);

        boolean flag = hfWebService.updateShowConfigByMap(userName, showConfigMap);

        if (flag) {
            return new Message().ok(666, "success");
        }

        return new Message().error(333, "error");
    }

    public static Message simulation(JSONObject params) {

        //31.模拟仿真计算
        //对应方法：void simuForecast(String userName, String basinName, String foreObjName, String sectionName)
        //传入参数：用户名，流域名，预报对象名，修改了预报过程的断面名

        String userName = params.getString("userName");

        String ly = params.getString("ly");

        String ybdx = params.getString("ybdx");

        String dm = params.getString("dm");

        //方案类型
        String falx = params.getString("falx");

        //模型方案名称
        List<String> modelSchName = JSONArray.parseArray(params.getJSONArray("modelSchName").toJSONString(), String.class);

        //预报对象名
        List<String> foreObjNameVec = JSONObject.parseArray(params.getString("ybdxs"), String.class);

        //对应方法：void simuForecast(String userName, String foreObjName, String modelSchName,String sectionName)
        //传入参数：用户名，预报对象名，模型方案名，修改了预报过程的断面名
        modelSchName.forEach(x -> {
            hfWebService.simuForecast(userName, ybdx, x, dm);
        });


        return jisuan(foreObjNameVec, ly, falx, userName);


    }


    public static Message achievementUpdate(JSONObject params) {
        System.out.println("params" + params);

        String userName = params.getString("userName");
       RSTime.RSTimeType rsTimeType = getTimeKey(userName);
        //流域名basinName
        String basinName = params.getString("ly");

        String dx = params.getString("dx");

        List<JSONObject> list = JSONObject.parseArray(JSON.toJSONString(params.getJSONArray("data")), JSONObject.class);

        list.forEach(x -> {

            Set<String> set = x.keySet();
            Calendar time = null;
            try {
                time = DataTransferUtil.stringToCalendar(x.getString(TIME_STYLE), rsTimeType, RSTime.RSTimeFormat.LINE);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Calendar finalTime = time;
            set.forEach(k -> {
                if (!k.equals(TIME_STYLE) && k.indexOf("-") != -1) {
                    List<String> t = Arrays.asList(k.split("-"));
                    //预报对象名
                    String foreObjName = t.get(0);
                    //模型方案名
                    String modelSchName = t.get(2);
                    //断面名
                    String sectionName = dx;
                    //预报序列名
                    String dpVecName = t.get(t.size() - 1);

                    Map<Calendar, Double> dataMap = new HashMap<>();
                    Double value = x.getDouble(k);

                    dataMap.put(finalTime, value);
                    //对应方法：void modifyForecastData(String userName,  String foreObjName, String 		modelSchName,String sectionName, String dpVecName, Map<Calendar, Double> dataMap)
                    //传入参数：用户名，预报对象名，模型方案名，断面名，预报序列名，数据map   m_FPAvgRCQCal
                    hfWebService.modifyForecastData(params.getString("userName"), sectionName, modelSchName, foreObjName, dpVecName, dataMap);

                }
            });

        });

        return floodCalculation(params, false);
    }


    public static Message dataSave(JSONObject params) {

        Map<String, Vector<String>> foreObjSchemeMap = new LinkedHashMap<String, Vector<String>>();
        //预报对象
        List<String> list = JSONObject.parseArray(JSON.toJSONString(params.getJSONArray("data")), String.class);
        list.forEach(x -> {
            //根据预报对象获取方案名
            List<String> l = hfWebService.getAllModelNameByForeObj(params.getString("userName"), x);
            Vector<String> schemeVec = new Vector<String>();
            l.forEach(k -> {
                schemeVec.add(k);
            });
            foreObjSchemeMap.put(x, schemeVec);
        });

        hfWebService.saveHFRes(params.getString("userName"), params.getString("name"), foreObjSchemeMap);
        //saveAbnormalWeather(params);
        return new Message().ok(666, "保存成功");
    }


    public static Message selectResult(JSONObject params) {

        //是根据预报制作时间还是预报依据时间筛选（true为制作时间）
        boolean isYBTOrYBDT = params.getBoolean("isYBTOrYBDT");

        //开始时间
        Calendar bTime = null;

        Calendar eTime = null;

        if (StringUtils.isNotBlank(params.getString("bTime"))) {
            bTime = TimeUtil.strToCalendarByFormat(params.getString("bTime"), 99);
        }
        if (StringUtils.isNotBlank(params.getString("eTime"))) {
            eTime = TimeUtil.strToCalendarByFormat(params.getString("eTime"), 99);
        }

        List<String> ybrList = JSONObject.parseArray(JSON.toJSONString(params.getJSONArray("ybrList")), String.class);

        List<Map<String, Object>> list = HFWebService.getFilteredResultMap(isYBTOrYBDT, bTime, eTime, ybrList, defaulttype);

//        List<Map<String, Object>> list = new LinkedList<>();
//
//        for (Calendar calendar : map.keySet()) {
//
//            list.add(map.get(calendar));
//        }
        return new Message().ok(666, "success").addData("data", list);
    }


    public static Message selectInformation(JSONObject params) {

        Map<String, Object> result = new HashMap<>();

        Map mapTypes = JSON.parseObject(params.toJSONString());

        List<Map<String, String>> list = HFWebService.getSingleResultMapList(mapTypes, defaulttype);

        result.put("top", list);


        Map<String, List<JSONObject>> map = null;
        //默认获取第一条，获取详细信息
        if (null != list && list.size() > 0) {
            Map<String, String> m = list.get(0);

            JSONObject json = (JSONObject) JSONObject.toJSON(m);

            map = getResultDataByTime(json);

        }

        result.put("buttom", map);


        return new Message().ok(666, "success").addData("data", result);


    }


    public static Map<String, List<JSONObject>> getResultDataByTime(JSONObject params) {


        Map<String, List<JSONObject>> result = new HashMap<>();

        //对应方法：static List<ADXLD> getSingleResultList(Long adid, Calendar ybt)
        //传入参数：预报点号，预报制作时间
        //返回格式：ADXLD对象的序列，其中getDT获取时间，getD获取预报值，getFp获取预报值对应的预报降雨

        Map mapTypes = JSON.parseObject(params.toJSONString());

        String fldIdStr = null;

        //峡江专用
        if (defaulttype == 2) {
            fldIdStr = (String) mapTypes.get("方案描述");
        }

        Object[] arr = HFWebService.getSingleResultList(mapTypes, fldIdStr, defaulttype);

        Map<Calendar, Map<String, String>> map = (Map) arr[0];

        Map<String, String> map1 = (Map) arr[1];

        List<JSONObject> v = new LinkedList<>();

        //表头
        List<JSONObject> tableHeader = new LinkedList<>();

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("prop", TIME_STYLE);
        jsonObject.put("label", typeMap.get("时间"));
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
        for (Calendar calendar : map.keySet()) {
            Map<String, String> trMap = map.get(calendar);

            String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

            JSONObject js = (JSONObject) JSONObject.toJSON(trMap);

            js.put(TIME_STYLE, time);

            v.add(js);
        }

        result.put("tableHeader", tableHeader);
        result.put("tableData", v);


//        list.forEach(x -> {
//
//            JSONObject j = JSONObject.parseObject(JSON.toJSONString(x));
//            j.put("dt", TimeUtil.CalendarToStringByFormat(x.getDt(), "yyyy-MM-dd HH:mm:ss"));
//            v.add(j);
//        });

        return result;

    }

    public static Map<String, Map<String, List<List<String>>>>  getAllResultByOne(JSONObject params) {

        Map<String, Map<String, List<List<String>>>> result = new LinkedHashMap<>();

        Map mapTypes = JSON.parseObject(params.toJSONString());

        List<Map<String, String>> list = HFWebService.getSingleResultMapList(mapTypes, defaulttype);

        for(Map<String, String> x:list){
            String fldIdStr = null;
            //峡江专用
            if (defaulttype == 2) {
                fldIdStr = (String) mapTypes.get("方案描述");
            }

            Object[] arr = HFWebService.getSingleResultList(x, fldIdStr, defaulttype);

            Map<Calendar, Map<String, String>> map = (Map) arr[0];

            Map<String, String> map1 = (Map) arr[1];

            List<List<String>> tableHeader = new LinkedList<>();
            List<List<String>> tableData = new LinkedList<>();
            tableHeader.add(Collections.singletonList("时间"));

            for (String str : map1.keySet()) {
                List<String> v = new ArrayList<>();
                v.add(str);
                tableHeader.add(v);
            }


            //组装数据
            for (Calendar calendar : map.keySet()) {

                List<String> v = new ArrayList<>();
                Map<String, String> trMap = map.get(calendar);
                String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");
                v.add(time);

                for(int i=1;i<tableHeader.size();i++){
                    v.add(trMap.get(tableHeader.get(i).get(0)));
                }

                tableData.add(v);
            }


            Map<String, List<List<String>>> value=new HashMap<>();
            value.put("tableHeader", tableHeader);
            value.put("tableData", tableData);

            result.put(x.get("预报断面")+" - "+x.get("预报结果类型"), value);

        }




        return result;


    }

    public static Message getHisRegionRainMap(JSONObject params) {

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        timeStep = null == timeStep ? 1 : timeStep;

        Map<Calendar, Map<HFForeObj, Map<HFSchRegion, Double>>> map = hfWebService.getHisRegionRainMap(params.getString("userName"), fanAn, timeStep);


        List<JSONObject> tableData = new LinkedList<>();

        Set<JSONObject> tableHeader = new LinkedHashSet<>();

        JSONObject object = new JSONObject();

        object.put("prop", "Time");

        object.put("label", "时间");

        tableHeader.add(object);

        for (Calendar key : map.keySet()) {
            //将Calendar转string
            String time = TimeUtil.CalendarToStringByFormat(key, "yyyy-MM-dd HH:mm:ss");

            Map<HFForeObj, Map<HFSchRegion, Double>> value = map.get(key);

            JSONObject js = new JSONObject();

            js.put("Time", time);


            for (HFForeObj hFForeObj : value.keySet()) {

                //添加表头数据
                JSONObject tr = new JSONObject();

                tr.put("label", hFForeObj.getName());

                Map<HFSchRegion, Double> v = value.get(hFForeObj);


                Set<JSONObject> set = new LinkedHashSet<>();

                for (HFSchRegion hfSchRegion : v.keySet()) {

                    JSONObject td = new JSONObject();
                    td.put("label", hfSchRegion.getName());
                    td.put("prop", hfSchRegion.getId() + "");
                    td.put("topLabel", hFForeObj.getName());
                    td.put("showName", hfSchRegion.getName());
                    set.add(td);
                    //
                    DecimalFormat decimalFormat = new DecimalFormat("#.0");

                    Double d = v.get(hfSchRegion);

                    js.put(hfSchRegion.getId() + "", null != d ? Double.parseDouble(decimalFormat.format(d)) : d);
                }

                tr.put("children", set);

                tableHeader.add(tr);
            }
            tableData.add(js);


        }


        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }

    public static Message getRainfallByLy(JSONObject params) {

        //Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Double>>> getHisRainDataMap(String userName, String basinName)
        //传入参数：用户名，流域名
        //返回格式：外层map的主键是时间，从实际计算时间开始到预报依据时间结束，内层map的主键是单元对象，值是面雨量值，单元对象有getid方法跟getName方法

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        timeStep = null == timeStep ? 1 : timeStep;

        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Double>>> map = hfWebService.getHisRainDataMap(params.getString("userName"), fanAn, timeStep);


        List<JSONObject> tableData = new LinkedList<>();

        Set<JSONObject> tableHeader = new LinkedHashSet<>();

        JSONObject object = new JSONObject();

        object.put("prop", "Time");

        object.put("label", "时间");

        tableHeader.add(object);

        for (Calendar key : map.keySet()) {
            //将Calendar转string
            String time = TimeUtil.CalendarToStringByFormat(key, "yyyy-MM-dd HH:mm:ss");

            Map<HFForeObj, Map<HFSchUnit, Double>> value = map.get(key);

            JSONObject js = new JSONObject();

            js.put("Time", time);


            for (HFForeObj hFForeObj : value.keySet()) {

                //添加表头数据
                JSONObject tr = new JSONObject();

                tr.put("label", hFForeObj.getName());


                Map<HFSchUnit, Double> v = value.get(hFForeObj);


                Set<JSONObject> set = new LinkedHashSet<>();

                for (HFSchUnit hfSchUnit : v.keySet()) {

                    JSONObject td = new JSONObject();
                    td.put("label", hfSchUnit.getName());
                    td.put("prop", hfSchUnit.getId() + "");
                    td.put("topLabel", hFForeObj.getName());
                    td.put("showName", hfSchUnit.getName());
                    set.add(td);
                    //
                    DecimalFormat decimalFormat = new DecimalFormat("#.0");

                    Double d = v.get(hfSchUnit);

                    js.put(hfSchUnit.getId() + "", null != d ? Double.parseDouble(decimalFormat.format(d)) : d);
                }

                tr.put("children", set);

                tableHeader.add(tr);
            }
            tableData.add(js);


        }

        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }

    public static Message getHisPreRainDataMap(JSONObject params) {

        //对应方法：Map<Calendar, Map<HFForeObj, Map<HFPreStation, String>>> getHisPreRainDataMap(String userName, String modelName, int timeStep)
        //传入参数：用户名、方案名、显示步长
        //返回格式：雨量站数据

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        timeStep = null == timeStep ? 1 : timeStep;

        //Map<Calendar, Map<HFPreStation, String>>
        Map<Calendar, Map<HFPreStation, String>> map = hfWebService.getHisPreRainDataMap(params.getString("userName"), fanAn, timeStep);

        List<JSONObject> tableData = new LinkedList<>();

        Set<JSONObject> tableHeader = new LinkedHashSet<>();

        JSONObject object = new JSONObject();

        object.put("prop", "Time");

        object.put("label", "时间");

        tableHeader.add(object);


        for (Calendar key : map.keySet()) {
            //将Calendar转string
            String time = TimeUtil.CalendarToStringByFormat(key, "yyyy-MM-dd HH:mm:ss");

            Map<HFPreStation, String> value = map.get(key);

            JSONObject js = new JSONObject();

            js.put("Time", time);

            for (HFPreStation hfPreStation : value.keySet()) {

                String name = hfPreStation.getName();

                //值
                String v = value.get(hfPreStation);

                JSONObject td = new JSONObject();
                td.put("label", name);
                td.put("prop", hfPreStation.getPreStaSenid() + "");

                td.put("showName", name);

                tableHeader.add(td);

                js.put(hfPreStation.getPreStaSenid() + "", v);

            }

            tableData.add(js);

//            for (HFForeObj hFForeObj : value.keySet()) {
//
//                //添加表头数据
//                JSONObject tr = new JSONObject();
//
//                tr.put("label", hFForeObj.getName());
//
//
//                Map<HFPreStation, String> v = value.get(hFForeObj);
//
//
//                Set<JSONObject> set = new LinkedHashSet<>();
//
//                for (HFPreStation hfPreStation : v.keySet()) {
//
//                    JSONObject td = new JSONObject();
//                    td.put("label", hfPreStation.getName());
//                    td.put("prop", hfPreStation.getPreStaSenid() + "");
//                    td.put("topLabel", hFForeObj.getName());
//                    td.put("showName", hfPreStation.getName());
//                    set.add(td);
//
//                    String d = v.get(hfPreStation);
//
//                    js.put(hfPreStation.getPreStaSenid() + "", d);
//                }
//
//                tr.put("children", set);
//
//                tableHeader.add(tr);
//            }
//            tableData.add(js);


        }


//        List<Map<String, Object>> mapValue = hfWebService.getBasinRainDataList(params.getString("userName"), fanAn, timeStep,false,true);

        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);

    }

    public static Message firstInit(JSONObject params) throws Exception {
        hfWebService.initialize(params.getString("userName"), hWindow, hfScheme);
        //根据用户获取预报降雨源
        List<String> list = hfWebService.getAllFutureRainSource(params.getString("userName"));

        //24.获取选中的预报降雨源
        //对应方法：String getSelectedRainSourceName(String userName)
        //传入参数：用户名
        //返回格式：选中的预报降雨源名称

        String rainSourceName = hfWebService.getSelectedRainSourceName(params.getString("userName"));
        //获取左侧树
        JSONArray tree = getTree();
        return new Message().ok(HttpStatus.HTTP_OK, "success")
                .addData("rainSource", list)
                .addData("rainSourceName", rainSourceName)
                .addData("tree",tree);
    }

    private static JSONArray getTree() throws Exception {

        JSONArray array = null;

        File file = new File(System.getProperty("user.dir") + "/conf/forecast/Tree.xml");
        if (file.exists()) {
            Document winDocument = XmlUtil.readXML(file);
            String winXmlStr = XmlUtil.toStr(winDocument);
            JSONObject js = JSONObject.parseObject(JSONUtil.parseFromXml(winXmlStr).toString());
            JSONObject root= js.getJSONObject("root");
            if(root!=null && root.get("basinTree") instanceof JSONArray){
                array = js.getJSONObject("root").getJSONArray("basinTree");
            }else {
                array = new JSONArray();
                array.add(root.getJSONObject("basinTree"));
            }

            array = getTree(array);

        }

        return array;
    }

    private static JSONArray getTree(JSONArray array) {

        for (int i = 0; i < array.size(); i++) {

            JSONObject js = array.getJSONObject(i);

            Object children = js.get("children");
            if (children != null && children instanceof JSONObject) {
                JSONObject jsonObject = js.getJSONObject("children");
                JSONArray jsonArray = new JSONArray();
                jsonArray.add(jsonObject);
                js.put("children", jsonArray);
            } else if (children != null && children instanceof JSONArray) {
                JSONArray jsonArray = js.getJSONArray("children");
                getTree(jsonArray); // 递归处理子节点，但不返回
            }

        }

        return array;
    }

    public static Message getRainfallBySource(JSONObject params) {


        String fanAn = params.getString("fanAn");

        String userName = params.getString("userName");

        String ly = params.getString("ly");

        //25.重新读取降雨预报数据
        //对应方法：boolean readRainDataBySource(String userName, String basinName, String rainSourceName)
        //传入参数：用户名，流域名，选中的降雨预报源名称
        //返回格式：如果读取成功则返回true

        RSTime.RSTimeType rsTimeType = getTimeKey(userName);

        Integer timeStep = params.getInteger("timeStep");


        //是否展示上下线
        boolean isMax = null == params.getBoolean("isMax") ? false : params.getBoolean("isMax");

        //单元id
        Long unitId = params.getLong("unitId");

        timeStep = null == timeStep ? 1 : timeStep;

        //Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>>> getForeRainDataMap(String userName, String basinName)
        //传入参数：用户名，流域名
        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>>> map = hfWebService.getForeRainDataMap(userName, fanAn, timeStep, isMax);

        List<JSONObject> tableData = new LinkedList<>();

        Set<JSONObject> tableHeader = new LinkedHashSet<>();

        JSONObject object = new JSONObject();

        object.put("prop", TIME_STYLE);

        object.put("label", "时间");

        tableHeader.add(object);

        for (Calendar calendar : map.keySet()) {
            //将Calendar转string
            String time = DataTransferUtil.CalendarToString(calendar, rsTimeType, RSTime.RSTimeFormat.LINE);

            JSONObject js = new JSONObject();

            js.put(TIME_STYLE, time);

            Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>> mapOne = map.get(calendar);

            for (HFForeObj hFForeObj : mapOne.keySet()) {

                //添加表头数据
                JSONObject tr = new JSONObject();

                tr.put("label", hFForeObj.getName());

                Map<HFSchUnit, Map<String, Double>> v = mapOne.get(hFForeObj);

                Set<JSONObject> set = new LinkedHashSet<>();

                for (HFSchUnit hfSchUnit : v.keySet()) {

                    if (null != unitId && hfSchUnit.getId() != unitId.longValue()) {
                        continue;
                    }
                    Map<String, Double> va = v.get(hfSchUnit);

                    Long preStaForeID = hfSchUnit.getUnitPrepRegion().getPreStaForeID(); //倾向值
                    Long preStaMaxForeID = hfSchUnit.getUnitPrepRegion().getPreStaMaxForeID();//上限值
                    Long preStaMinForeID = hfSchUnit.getUnitPrepRegion().getPreStaMinForeID();//下限

                    JSONObject p = new JSONObject();
                    p.put("label", hfSchUnit.getName());

                    p.put("topLabel", hFForeObj.getName());
                    JSONArray array = new JSONArray();


                    //1:倾向值,2:上限值,3:下限值
                    if (null != va.get("下限值")) {
                        JSONObject td3 = new JSONObject();
                        td3.put("label", "下限值");
                        td3.put("showName", hfSchUnit.getName());
                        td3.put("prop", preStaMinForeID + "");
                        td3.put("dataType", 3);
                        td3.put("ybdx", hFForeObj.getName());
                        td3.put("unitId", hfSchUnit.getId());
                        array.add(td3);

                        js.put(preStaMinForeID + "", va.get("下限值"));
                    }


                    if (null != va.get("倾向值")) {
                        JSONObject td1 = new JSONObject();
                        td1.put("showName", hfSchUnit.getName());
                        td1.put("label", "倾向值");
                        td1.put("prop", preStaForeID + "");
                        td1.put("dataType", 1);
                        td1.put("ybdx", hFForeObj.getName());
                        td1.put("unitId", hfSchUnit.getId());
                        array.add(td1);
                        js.put(preStaForeID + "", va.get("倾向值"));

                    }

                    if (null != va.get("上限值")) {
                        JSONObject td2 = new JSONObject();
                        td2.put("showName", hfSchUnit.getName());
                        td2.put("label", "上限值");
                        td2.put("dataType", 2);
                        td2.put("prop", preStaMaxForeID + "");
                        td2.put("ybdx", hFForeObj.getName());
                        td2.put("unitId", hfSchUnit.getId());
                        array.add(td2);

                        js.put(preStaMaxForeID + "", va.get("上限值"));

                    }

                    p.put("children", array);
                    set.add(p);


                }

                tr.put("children", set);

                tableHeader.add(tr);
            }
            tableData.add(js);


        }


        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }

    public static long gettimeStemp(String time, String format) {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (!"".equals(format)) {
            simpleDateFormat = new SimpleDateFormat(format);
        }

        long timeStemp = 0;
        try {

            Date date = simpleDateFormat.parse(time);

            timeStemp = date.getTime();

        } catch (ParseException e) {
            e.printStackTrace();
        }
        return timeStemp;
    }

    public static Message getForecastSettings(JSONObject params) {
        //key的顺序
        List<String> order = new ArrayList<>();
        /*
            计算设置-获取当前时段设置
            对应方法：Map<String, HFWindowSpnParam> getTimePeriodValueMap(String userName, String schemeTypeStr)
            传入参数：用户名，方案类型
            返回格式：主键是时段设置类型，如预见期、校正时段，值是时段设置对象，有getValue方法可以获取当前选择的值，还有getMax以及getMin方法可以获取上限值跟下限值
         */
        Map<String, Object> formData = new HashMap<>();
        //用户名称
        String userName = params.getString("userName");
        //方案类型
        String falx = params.getString("falx");
        Map<String, HFWindowSpnParam> timeSetting = hfWebService.getTimePeriodValueMap(userName, falx);
        if (MapUtil.isNotEmpty(timeSetting)) {
            formData.put("timeSetting", timeSetting);
            order.add("timeSetting");
        }
        //48.计算设置-获取单一预报方案下所有水库断面的水库调度配置
        //对应方法：Map<String, Map<String, Object>> getDispatchSettingMap(String userName, String schemeTypeStr)
        //传入参数：用户名，方案类型名
        //返回格式：外层map的主键是断面名称，内层map的主键是调度配置属性名，值是配置属性值
        Map<String, Map<String, String>> dd = hfWebService.getDispatchSettingMap(userName, falx); //4
        if (MapUtil.isNotEmpty(dd)) {
            formData.put("dd", dd);
            order.add("dd");
        }
        /*
            获取模型方案设置
            Map<String, Map<String, Boolean>> getAllModelSelMap(String userName, String schemeTypeStr)
            传入参数：用户名，方案类型
            返回格式：外层主键是预报对象名，内层map的主键是模型方案名称，值是是否选中
         */

        Map<String, Map<String, Boolean>> value = hfWebService.getAllModelSelMap(userName, falx);
        if (MapUtil.isNotEmpty(value)) {
            //如果每个场站的方案只有一个则不显示
            boolean flag = false;
            for (String str : value.keySet()) {
                Map<String, Boolean> valueMap = value.get(str);
                if (valueMap.size() != 1) flag = true;
            }
            if (flag) {
                formData.put("mx", value);//2
                order.add("mx");
            }
        }
        /*
            获取模型边界流量交互方法设置
            Map<String, Map<String, Object>> getBoundarySettingMethods(String userName,String schemeTypeStr)
            传入参数：用户名，方案类型
            返回格式：外层map主键是断面名称，内层map的主键是属性，值是属性对应的值，包含方法列表以及选中方法号
         */

        Map<String, Map<String, Object>> map = hfWebService.getBoundarySettingMethods(userName, falx);
        if (MapUtil.isNotEmpty(map)) {
            formData.put("jh", map);    //3
            order.add("jh");
        }


        //47.计算设置-获取水库断面的所有可选择的调度模式
        //对应方法：List<String> getAllDispatchMethods(String userName)
        //传入参数：用户名
        //返回格式：调度模式名集合

        List<String> nameSelect = hfWebService.getAllDispatchMethods(userName);

        return new Message().ok(666, "success").addData("formData", formData).addData("nameSelect", nameSelect).addData("order", order);
    }


    public static Message getForecastModification(JSONObject params) {

        //boolean setTimePeriods(String userName, String schemeTypeStr, int realCalcSpan, int forecastSpan,int hisRCSpan, int showHisSpan)
        //传入参数：用户名，方案类型，预热时段，预见期，校正时段，前延时段
        //返回格式：boolean型，如果设置成果，返回true，如果设置的数据不合理，返回false
        String userName = params.getString("userName");

        String falx = params.getString("falx");


        JSONObject timePeriods = params.getJSONObject("timeSetting");
        if (null != timePeriods) {
            Map<String, HFWindowSpnParam> map = new HashMap<>();

            Iterator iter = timePeriods.entrySet().iterator();

            while (iter.hasNext()) {

                Map.Entry entry = (Map.Entry) iter.next();

                String key = entry.getKey().toString();

                JSONObject value = timePeriods.getJSONObject(key);

                HFWindowSpnParam bean = JSONObject.parseObject(value.toJSONString(), HFWindowSpnParam.class);

                map.put(key, bean);

            }

            boolean flag = hfWebService.setTimePeriods(userName, falx, map);


        }


        //44.计算设置-进行模型方案设置
        //对应方法：boolean doAllModelSelect(String userName, String schemeTypeStr, Map<String, Map<String, Boolean>> selMap)
        //传入参数：用户名，方案类型，模型方案设置map
        //返回格式：boolean型，如果设置成功，返回true，如果出错，返回false


        JSONObject mx = params.getJSONObject("mx");

        if (null != mx) {
            Map<String, Map<String, Boolean>> mxMap = new HashMap<>();

            Iterator iter1 = mx.entrySet().iterator();

            while (iter1.hasNext()) {

                Map.Entry entry = (Map.Entry) iter1.next();

                String key = entry.getKey().toString();

                JSONObject value = mx.getJSONObject(key);

                Map<String, Boolean> bea = JSONObject.parseObject(value.toJSONString(), Map.class);

                mxMap.put(key, bea);

            }


            boolean mxFlag = hfWebService.doAllModelSelect(userName, falx, mxMap);

        }


        //46.计算设置-进行边界流量交互方法设置
        //对应方法：boolean doAllMethodSelect(String userName, String schemeTypeStr,Map<String, Map<String, Object>> methodListMap)
        //传入参数：用户名，方案类型，边界流量设置map
        //返回格式：boolean型，如果设置成功，返回true，如果出错，返回false

        JSONObject jh = params.getJSONObject("jh");
        if (null != jh) {
            Map<String, Map<String, Object>> jhMap = new HashMap<>();

            Iterator iter2 = jh.entrySet().iterator();

            while (iter2.hasNext()) {

                Map.Entry entry = (Map.Entry) iter2.next();

                String key = entry.getKey().toString();

                JSONObject value = jh.getJSONObject(key);

                Map<String, Object> bea = JSONObject.parseObject(value.toJSONString(), Map.class);

                jhMap.put(key, bea);

            }

            boolean jhFlag = hfWebService.doAllMethodSelect(userName, falx, jhMap);


        }


        //49.计算设置-设置当前预报方案类型下所有水库断面的水库调度配置
        //对应方法：boolean doAllSecDispatchConfigSet(String userName, String schemeTypeStr,
        //Map<String, Map<String, Object>> dispatchSetMap)
        //传入参数：用户名，方案类型名，水库调度配置map
        //返回格式：boolean型，如果设置成功，返回true，如果出错，返回false
        JSONObject dd = params.getJSONObject("dd");
        if (null != dd) {
            Map<String, Map<String, String>> ddMap = new HashMap<>();

            Iterator iter3 = dd.entrySet().iterator();

            while (iter3.hasNext()) {

                Map.Entry entry = (Map.Entry) iter3.next();

                String key = entry.getKey().toString();

                JSONObject value = dd.getJSONObject(key);

                Map<String, String> bea = JSON.parseObject(value.toJSONString(), Map.class);

                ddMap.put(key, bea);

            }

            boolean ddFlag = hfWebService.doAllSecDispatchConfigSet(userName, falx, ddMap);


        }


        return new Message().ok(666, "success");
    }

    public static Message getFloodAnalysisDefaultLevel(JSONObject params) {

        //51.获取承洪能力分析的默认水位
        //对应方法：public double getFloodAnalysisDefaultLevel(String userName, String reserviorName)
        //传入参数：用户名，水库名
        //返回格式：默认水位
        String userName = params.getString("userName");

        String reserviorName = params.getString("reserviorName");

        double value = hfWebService.getFloodAnalysisDefaultLevel(userName, reserviorName);


        return new Message().ok(666, "success").addData("waterLevel", value);
    }


    /**
     * 获取单元历史数据
     *
     * @param params
     * @return
     */
    private static Message getUnitData(JSONObject params) {

        //用户名
        String userName = params.getString("userName");

        //预报对象名
        String ybdx = params.getString("ybdx");

        //单元点号
        Long unitId = params.getLong("unitId");

        //步长
        int timeStep = params.getInteger("timeStep") == null ? 1 : params.getInteger("timeStep");

        List<JSONObject> tableHeader = JSONArray.parseArray("[{\"prop\": \"time\",\"label\": \"时间\"}]", JSONObject.class);

        List<JSONObject> tableData = new LinkedList<>();

        //Map<Calendar, Map<HFPreStation, String>> getUnitHisRainDataMap(String userName, String foreObjName,long unitId, int timeStep)
        //传入参数：用户名，预报对象名，单元点号，时间步长
        //返回格式：外层map的主键是时间，内层map的主键是雨量站，map的值是对应的面雨量历史值
        Map<Calendar, Map<HFPreStation, String>> map = hfWebService.getUnitHisRainDataMap(userName, ybdx, unitId, timeStep);

        //存属所有名称id  作为表头使用
        Map<String, String> header = new HashMap<>();

        for (Calendar calendar : map.keySet()) {

            String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

            Map<HFPreStation, String> v = map.get(calendar);

            JSONObject js = new JSONObject();
            js.put("time", time);

            for (HFPreStation hfPreStation : v.keySet()) {

                String name = hfPreStation.getName();

                String value = v.get(hfPreStation);

                Long id = hfPreStation.getId();

                header.put(id.toString(), name);

                js.put(id.toString(), value);

            }
            tableData.add(js);

        }

        for (String str : header.keySet()) {

            String label = header.get(str);
            tableHeader.add(JSONObject.parseObject("{\"prop\": \"" + str + "\",\"label\": \"" + label + "\"}"));
        }


        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }


    /**
     * 获取某个单元的下的雨量站信息
     */
    public static Message getHistoryForecast(JSONObject params) {

        String type = params.getString("type");

        if (StrUtil.equals(type, "history")) {
            return getUnitData(params);
        } else {
            return getDataByForecast(params);
        }

    }

    public static Message basinSurfaceRainfall(JSONObject params) {

        List<JSONObject> tableHeader = JSONArray.parseArray("[{\"prop\": \"" + TIME_STYLE + "\",\"label\": \"" + typeMap.get("时间") + "\"}]", JSONObject.class);

        List<JSONObject> tableData = new ArrayList<>();

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        boolean isMax = null == params.get("isMax") ? false : params.getBoolean("isMax");

        boolean hisOrForecast = params.getBoolean("hisOrForecast");

        String userName=params.getString("userName");
        RSTime.RSTimeType rsTimeType = getTimeKey(userName);

        //33.获取流域面雨量
        //对应方法：List<Map<String, Object>> getBasinRainDataList(String userName,String schemeName, int timeStep, boolean isMax,boolean hisOrForecast)
        //传入参数：用户名，预报方案名，时间步长，是否展示上下限，实况true还是预报
        //返回格式：如果是实况 time时间 v实测值  如果是预报  time时间  倾向值  上限值  下限值
        List<Map<String, Object>> mapValue = hfWebService.getBasinRainDataList(userName, fanAn, timeStep, isMax, hisOrForecast);

        //获取所有流域
        Map<Long, String> map = mapValue.stream().collect(Collectors.toMap(x -> (Long) x.get("id"), x -> (String) x.get("name"), (v1, v2) -> v2));

        //实测
        if (hisOrForecast) {

            for (Long lo : map.keySet()) {

                tableHeader.add(JSONObject.parseObject("{\"prop\": \"" + lo + "\",\"label\": \"" + map.get(lo) + "\"}"));

            }

            for (Map<String, Object> ma : mapValue) {

                Calendar calendar = (Calendar) ma.get("time");

                String time = DataTransferUtil.CalendarToString(calendar, rsTimeType, RSTime.RSTimeFormat.LINE);

                Long id = (Long) ma.get("id");

                JSONObject jsonObject = JSONObject.parseObject("{\"" + TIME_STYLE + "\":\"" + time + "\",\"" + id + "\":" + ma.get("v") + ",\"id\": \"" + id + "\"}");

                tableData.add(jsonObject);

            }

        } else {

            //按照流域分组
            Map<Long, List<Map<String, Object>>> value = mapValue.stream().collect(Collectors.groupingBy(x -> (Long) x.get("id")));

            for (Long lo : value.keySet()) {

                //表头的tr
                JSONObject tr = JSONObject.parseObject("{\"label\": \"" + map.get(lo) + "\"}");

                List<Map<String, Object>> list = value.get(lo);

                Set<JSONObject> set = new HashSet<>();

                for (Map<String, Object> va : list) {

                    //一行的表格数据
                    JSONObject js = new JSONObject();

                    Calendar calendar = (Calendar) va.get("time");

                    String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

                    js.put(TIME_STYLE, time);

                    //1:倾向值,2:上限值,3:下限值
                    if (null != va.get("倾向值")) {
                        JSONObject td3 = new JSONObject();
                        td3.put("label", "倾向值");
                        td3.put("showName", "倾向值");
                        td3.put("prop", "1");
                        td3.put("dataType", 1);

                        set.add(td3);

                        js.put("1", va.get("倾向值"));
                    }
                    //1:倾向值,2:上限值,3:下限值
                    if (null != va.get("上限值")) {
                        JSONObject td3 = new JSONObject();
                        td3.put("label", "上限值");
                        td3.put("showName", "上限值");
                        td3.put("prop", "2");
                        td3.put("dataType", 2);

                        set.add(td3);

                        js.put("2", va.get("上限值"));
                    }
                    //1:倾向值,2:上限值,3:下限值
                    if (null != va.get("下限值")) {
                        JSONObject td3 = new JSONObject();
                        td3.put("label", "下限值");
                        td3.put("showName", "下限值");
                        td3.put("prop", "3");
                        td3.put("dataType", 3);
                        set.add(td3);

                        js.put("3", va.get("下限值"));
                    }

                    js.put("id", va.get("id"));
                    tableData.add(js);


                }

                tr.put("children", set);

                tableHeader.add(tr);

            }


        }


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }


    public static Message updateSurfaceRainfall(JSONObject params) {

        //35.修改流域面雨量
        //对应方法：boolean modifyBasinRainData(String userName, Map<Long, List<Map<String, Object>>>dataList, int timeStep, boolean hisOrForecast)
        //传入参数：用户名，修改数据（“time”:Calendar,"v":double,"dataType":int），时间步长，实况还是预报
        //返回格式：修改成功返回true

        String userName = params.getString("userName");

        List<JSONObject> list = JSONArray.parseArray(params.getJSONArray("data").toJSONString(), JSONObject.class);

        int timeStep = params.getInteger("timeStep");

        boolean hisOrForecast = params.getBoolean("hisOrForecast");

        Map<Long, List<Map<String, Object>>> dataList = new HashMap<>();

        //实测
        if (hisOrForecast) {

            dataList = list.stream().map(x -> {

                Calendar calendar = TimeUtil.strToCalendarByFormat(x.getString(TIME_STYLE), 99);

                Map<String, Object> map = new HashMap<>();

                map.put("time", calendar);

                map.put("v", Double.valueOf(x.get("v").toString()));

                map.put("id", x.get("id"));

                return map;

            }).collect(Collectors.groupingBy(x -> (Long.parseLong((String) x.get("id")))));

        } else {

            for (JSONObject js : list) {

                Long id = js.getLong("id");

                Calendar calendar = TimeUtil.strToCalendarByFormat(js.getString(TIME_STYLE), 99);

                for (String key : js.keySet()) {

                    if (!StrUtil.equals("id", key) && !StrUtil.equals(TIME_STYLE, key) && !StrUtil.equals("dataType", key)) {

                        Map<String, Object> objectMap = new HashMap<>();

                        objectMap.put("time", calendar);

                        objectMap.put("v", js.getDouble(key));

                        objectMap.put("dataType", js.getIntValue("dataType"));


                        if (null == dataList.get(id)) {

                            List<Map<String, Object>> ls = new ArrayList<>();

                            ls.add(objectMap);

                            dataList.put(id, ls);

                        } else {

                            List<Map<String, Object>> ls = dataList.get(id);

                            ls.add(objectMap);

                        }

                    }

                }


            }

        }


        boolean flag = hfWebService.modifyBasinRainData(userName, dataList, timeStep, hisOrForecast);


        if (flag) {
            return new Message().ok(HttpStatus.HTTP_OK, "success");
        }

        return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "error");

    }

    public static Message calculateTraffic(JSONObject params) {
//        48.获取当前流域下所有单元的计算流量
//        对应方法：Map<Calendar, Map<HFForeObj, Map<HFSchUnit, String>>> getUnitCalcFlowMap(String userName, int timeStep, boolean hisOrForecast)
//        传入参数：用户名，展示步长，实况还是预报
//        返回格式：外层map的主键是时间，中间层map的主键是预报对象，内层map的主键是单元对象，值是计算流量值，单元对象有getid方法跟getName方法

        String userName = params.getString("userName");

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        boolean hisOrForecast = params.getBoolean("hisOrForecast");

        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, String>>> map = hfWebService.getUnitCalcFlowMap(userName, fanAn, timeStep, hisOrForecast);


        return getTableDate(map);
    }

    public static Message waterDeficiency(JSONObject params) {

        //47.获取当前流域下所有单元的土壤缺水量
        //对应方法：Map<Calendar, Map<HFForeObj, Map<HFSchUnit, String>>> getSoilWaterDeficitDataMap(String userName,int timeStep, boolean hisOrForecast)
        //传入参数：用户名，展示步长，实况还是预报
        //返回格式：外层map的主键是时间，中间层map的主键是预报对象，内层map的主键是单元对象，值是土壤缺水量值，单元对象有getid方法跟getName方法

        String userName = params.getString("userName");

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        boolean hisOrForecast = params.getBoolean("hisOrForecast");

        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, String>>> map = hfWebService.getSoilWaterDeficitDataMap(userName, fanAn, timeStep, hisOrForecast);


        return getTableDate(map);

    }


    public static Message getTableDate(Map<Calendar, Map<HFForeObj, Map<HFSchUnit, String>>> map) {

        List<JSONObject> tableData = new LinkedList<>();

        Set<JSONObject> tableHeader = new LinkedHashSet<>();

        JSONObject object = new JSONObject();

        object.put("prop", "Time");

        object.put("label", "时间");

        tableHeader.add(object);

        for (Calendar key : map.keySet()) {
            //将Calendar转string
            String time = TimeUtil.CalendarToStringByFormat(key, "yyyy-MM-dd HH:mm:ss");

            Map<HFForeObj, Map<HFSchUnit, String>> value = map.get(key);

            JSONObject js = new JSONObject();

            js.put("Time", time);


            for (HFForeObj hFForeObj : value.keySet()) {

                //添加表头数据
                JSONObject tr = new JSONObject();

                tr.put("label", hFForeObj.getName());


                Map<HFSchUnit, String> v = value.get(hFForeObj);


                Set<JSONObject> set = new LinkedHashSet<>();

                for (HFSchUnit hfSchUnit : v.keySet()) {

                    JSONObject td = new JSONObject();
                    td.put("label", hfSchUnit.getName());
                    td.put("prop", hfSchUnit.getId() + "");
                    td.put("topLabel", hFForeObj.getName());
                    td.put("showName", hFForeObj.getName());
                    set.add(td);

                    String d = v.get(hfSchUnit);

                    js.put(hfSchUnit.getId() + "", d);
                }

                tr.put("children", set);

                tableHeader.add(tr);
            }
            tableData.add(js);


        }

        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }

    /**
     * 根据单元id获取单元下的所有预测的数据
     *
     * @param params
     * @return
     */
    private static Message getDataByForecast(JSONObject params) {

        //获取
        String userName = params.getString("userName");

        String fanAn = params.getString("fanAn");

        Integer timeStep = params.getInteger("timeStep");

        timeStep = null == timeStep ? 1 : timeStep;

        //预报对象名
        String ybdx = params.getString("ybdx");

        //单元点号
        Long unitId = params.getLong("unitId");

        //是否展示上下线
        boolean isMax = null == params.getBoolean("isMax") ? false : params.getBoolean("isMax");

        Map<String, String> keyValue = new HashMap<>();

        keyValue.put("倾向值", "v0");
        keyValue.put("上限值", "v1");
        keyValue.put("下限值", "v2");

        List<JSONObject> tableHeader = JSONArray.parseArray("[{\"prop\": \"time\",\"label\": \"时间\"},{\"prop\":\"v0\",\"label\":\"倾向值\"}," +
                "{\"prop\":\"v1\",\"label\":\"上限值\"},{\"prop\":\"v2\",\"label\":\"下限值\"}]", JSONObject.class);

        //用户名，预报对象名，单元点号，时间步长
        Map<Calendar, Map<String, String>> map = hfWebService.getUnitForeRainDataMap(userName, fanAn, ybdx, unitId, timeStep, isMax);

        List<JSONObject> tableData = new LinkedList<>();

        for (Calendar calendar : map.keySet()) {

            String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

            JSONObject js = new JSONObject();

            js.put("time", time);

            Map<String, String> v = map.get(calendar);

            for (String str : v.keySet()) {

                String value = v.get(str);

                String key = keyValue.get(str);

                js.put(key, value);

            }

            tableData.add(js);
        }

        return new Message().ok(666, "success").addData("tabelHeader", tableHeader).addData("tableData", tableData);


    }

    /**
     * 修改某些单元的预报雨量
     *
     * @param params
     * @return
     */
    public static Message updateByUnitData(JSONObject params) throws Exception {

        //用户名
        String userName = params.getString("userName");

        RSTime.RSTimeType rsTimeType = FloodResults. getTimeKey(userName);

        //预报对象名
        String ybdx = params.getString("ybdx");

        //步长
        int timeStep = null == params.getInteger("timeStep") ? 1 : params.getInteger("timeStep");

        List<JSONObject> data = JSONArray.parseArray(params.getJSONArray("data").toJSONString(), JSONObject.class);

        Map<Long, List<Map<String, Object>>> dataList = new HashMap<>();

        int dataType = 1;

        for (int i = 0; i < data.size(); i++) {

            JSONObject js = data.get(i);

            Calendar time = DataTransferUtil.stringToCalendar( js.getString(TIME_STYLE),rsTimeType, RSTime.RSTimeFormat.LINE);

            Double v = js.getDouble("v");

            long unitId = js.getLong("id");

            dataType = null == js.getInteger("dataType") ? 1 : js.getInteger("dataType");

            Map<String, Object> vMap = new HashMap<>();

            vMap.put("time", time);
            vMap.put("v", v);
            vMap.put("dataType", dataType);

            if (null == dataList.get(unitId)) {

                List<Map<String, Object>> mapList = new ArrayList<>();

                mapList.add(vMap);

                dataList.put(unitId, mapList);

            } else {

                List<Map<String, Object>> mapList = dataList.get(unitId);

                mapList.add(vMap);

            }

        }


        //36.修改某个单元的预报雨量
        //对应方法：boolean modifyUnitForeRainData(String userName, Map<Long, List<Map<String, Object>>> dataList，int timeStep)
        //传入参数：用户名，修改数据（“time”:Calendar,"v":double,"dataType":int），时间步长
        //返回格式：修改成功返回true

        boolean flag = hfWebService.modifyUnitForeRainData(userName, dataList, timeStep);

        return new Message().ok(HttpStatus.HTTP_OK, "success");
    }


    /**
     * 修改某些单元的预报雨量
     *
     * @param params
     * @return
     */
    public static Message modifyRegionForeRainData(JSONObject params) throws Exception {

        //用户名
        String userName = params.getString("userName");

        //预报对象名
        String ybdx = params.getString("ybdx");

        //步长
        int timeStep = null == params.getInteger("timeStep") ? 1 : params.getInteger("timeStep");

        List<JSONObject> data = JSONArray.parseArray(params.getJSONArray("data").toJSONString(), JSONObject.class);

        Map<Long, List<Map<String, Object>>> dataList = new HashMap<>();

        int dataType = 1;

        RSTime.RSTimeType rsTimeType= getTimeKey(userName);

        for (int i = 0; i < data.size(); i++) {

            JSONObject js = data.get(i);

            Calendar time = DataTransferUtil.stringToCalendar( js.getString(TIME_STYLE),rsTimeType, RSTime.RSTimeFormat.LINE);

            Double v = js.getDouble("v");

            long unitId = js.getLong("id");

            dataType = js.getInteger("dataType");

            Map<String, Object> vMap = new HashMap<>();

            vMap.put("time", time);
            vMap.put("v", v);
            vMap.put("dataType", dataType);
            if (null == dataList.get(unitId)) {

                List<Map<String, Object>> mapList = new ArrayList<>();

                mapList.add(vMap);

                dataList.put(unitId, mapList);

            } else {

                List<Map<String, Object>> mapList = dataList.get(unitId);

                mapList.add(vMap);

            }


        }

        //37.修改某个区间的预报雨量
        //对应方法：boolean modifyRegionForeRainData(String userName, Map<Long, List<Map<String, Object>>> dataList，int timeStep)
        //传入参数：用户名，修改数据（“time”:Calendar,"v":double,"dataType":int），时间步长
        //返回格式：修改成功返回true

        boolean flag = hfWebService.modifyRegionForeRainData(userName, dataList, timeStep);

        return new Message().ok(666, "success");
    }


    //表头第一列
    private static JSONObject getFirstTd() {

        JSONObject js = new JSONObject();
        js.put("label", "水库调度");
        List<JSONObject> thirdList = new ArrayList<>();
        JSONObject third = new JSONObject();
        third.put("label", "数据类型");
        third.put("prop", "Time");
        thirdList.add(third);
//        JSONObject sencond = new JSONObject();
//        sencond.put("label", "调度方式");
//        sencond.put("children", thirdList);
//        List<JSONObject> sencondList = new ArrayList<>();
//        sencondList.add(sencond);
//        js.put("children", sencondList);
        js.put("children", thirdList);
        return js;
    }


    public static Message reservoirInteraction(JSONObject params) {

        Map<String, Object> result = new HashMap<>();

        //对应方法：Map<Calendar, Map<String, Double>> getReservoirDataList(String serName,String foreObjName, String sectionName)
        //传入参数：用户名，预报对象名、断面名
        //返回格式：外层主键是时间，内层map主键是数据的名称如流量、水位，值是对应的数值

        String userName = params.getString("userName");

        //流域
        String ly = params.getString("ly");

        //预报对象
        List<Map> ybdx = JSONArray.parseArray(params.getJSONArray("dx").toJSONString(), Map.class);

        //方案类型
        String falx = params.getString("falx");

        //判断是否存在调度模式，如果存在先修改调度模式，在获取数据
        String ddms = params.getString("ddms");

        Map<String, Map<String, String>> dd = hfWebService.getDispatchSettingMap(userName, falx); //4


        List<String> hfList = new ArrayList<>();

        //7.根据流域下断面名称获取断面对象
        //对应方法：HFSection getSectionObjByName(String userName, String basinName, String foreObjName, String sectionName)
        //传入参数：用户名，流域名，预报对象名，断面名
        //返回格式：断面对象

        for (int i = 0; i < ybdx.size(); i++) {

            Map<String, List<String>> map = ybdx.get(i);

            for (String str : map.keySet()) {

                hfList.add(str);

                //判断是否存在调度模式，如果存在先修改调度模式，在获取数据
                if (StrUtil.isNotBlank(ddms)) {

                    Map<String, String> objectMap = dd.get(str);

                    objectMap.put("调度模式", ddms);

                    hfWebService.doAllSecDispatchConfigSet(userName, falx, dd);

                }

//                List<String> list = map.get(str);

//                for (int j = 0; j < list.size(); j++) {
//
//                    HFSection hfSection = hfWebService.getSectionObjByName(userName, str, "洪水预报方案", list.get(j));
//
//                    hfList.add(hfSection.getName());
//                }

            }
        }

        boolean isLimit = null == params.getBoolean("isLimit") ? false : params.getBoolean("isLimit");


        //28.水库调度交互-获取水库断面未来的出库流量或者是水位序列以及对应的限制条件
        //对应方法：List<Map<String, Object>> getReservoirDataList(String serName,List<HFSection> sectionList, Boolean isLimit)
        //传入参数：用户名、断面集合、是否展示限制值
        //返回格式：map的list,每一个map的主键是对象的名称，值是对应的对象
        List<Map<String, Object>> listMap = hfWebService.getReservoirDataList(userName, hfList, isLimit);

        //表头数据
        List<JSONObject> tableHeader = new ArrayList<>();

        tableHeader.add(getFirstTd());

        //表格数据
        List<JSONObject> tableData = new ArrayList<>();


        Map<String, Map<String, Object>> data = new HashMap<>();

        for (int i = 0; i < listMap.size(); i++) {

            Map<String, Object> map = listMap.get(i);

            //第一行表头
            JSONObject tr = new JSONObject();
            tr.put("label", map.get("水库对象"));

            String[] trArr = (String[]) map.get("表头");

            //表头map
            Map<String, String> trMap = new HashMap<>();

            //可编辑列
            String[] editTr = (String[]) map.get("可编辑列");

            String[] shuxing = (String[]) map.get("表头属性");

            //第三行
            List<JSONObject> trThree = new ArrayList<>();
            for (int j = 0; j < trArr.length; j++) {
                JSONObject jsonObject = new JSONObject();

                String label = trArr[j];

                jsonObject.put("label", label + "(" + map.get("单位") + ")");
                jsonObject.put("showName", label);
                jsonObject.put("prop", map.get("水库对象") + label);
                jsonObject.put("type", map.get("type"));
                jsonObject.put("attribute", shuxing[j]);
                jsonObject.put("statisticType", map.get("统计类型"));
                jsonObject.put("sericesType", map.get("图形类型"));
                jsonObject.put("sdcsw", map.get("时段初水位"));
                jsonObject.put("unit", map.get("单位"));
                jsonObject.put("serieColor", map.get("系列颜色"));

                //用于为数据赋值“v0”v1。。。。
                trMap.put(trArr[j], map.get("水库对象") + label);

                //判断是否是可编辑列
                if (ArrayUtil.isNotEmpty(editTr) && ArrayUtil.indexOf(editTr, label) != -1) {
                    jsonObject.put("edit", true);
                } else {
                    jsonObject.put("edit", false);
                }
                trThree.add(jsonObject);


            }

            //第二行
//            JSONObject trTwo = new JSONObject();
//            trTwo.put("label", map.get("调度方式"));
//            trTwo.put("children", trThree);
//
//            List<JSONObject> trTwoList = new ArrayList<>();
//
//            trTwoList.add(trTwo);
            tr.put("children", trThree);
            tableHeader.add(tr);


            if (null != map.get("数据")) {
                Map<Calendar, Map<String, String>> trData = (Map) map.get("数据");

                for (Calendar calendar : trData.keySet()) {

                    String time = TimeUtil.CalendarToString(calendar, DateFormatter.SECOND_FORMATTER);

                    Map<String, String> inside = trData.get(calendar);

                    Map<String, Object> inData = new HashMap<>();

                    for (String str : inside.keySet()) {

                        String prop = trMap.get(str);

                        String v = inside.get(str);

                        inData.put(prop, v);

                    }

                    if (null == data.get(time)) {
                        data.put(time, inData);
                    } else {

                        Map<String, Object> jj = data.get(time);

                        jj.putAll(inData);

                    }


                }

            }


        }

        //Map<String, Map<String, Object>> data
        for (String str : data.keySet()) {

            Map<String, Object> objectMap = data.get(str);

            JSONObject jsonObj = JSONObject.parseObject(JSON.toJSONString(objectMap));

            jsonObj.put("Time", str);

            tableData.add(jsonObj);

        }

        tableData = tableData.stream().sorted(Comparator.comparing(x -> DateUtil.parse(x.getString("Time"), "yyyy-MM-dd HH:mm:ss").getTime())).collect(Collectors.toList());
        result.put("tableHeader", tableHeader);
        result.put("tableData", tableData);

        List<String> nameSelect = hfWebService.getAllDispatchMethods(userName);

        result.put("nameSelect", nameSelect);

        return new Message().ok(666, "success")
                .addData("data", result);


    }


    public static Message updateInitialWater(JSONObject params) {

        //39.水库调度交互-修改水库断面时段初水位
        //对应方法：boolean modifyReservoirBeginLevel(String userName, String foreObjName, String sectionName,
        //			double periodBeginLevel)
        //传入参数：用户名，预报对象名、断面名、水位值
        //返回格式：如果设置成功返回true，否则返回false

        String userName = params.getString("userName");
        String ybdx = params.getString("ybdx");
        String dm = params.getString("dm");

        double periodBeginLevel = params.getDouble("periodBeginLevel");

        boolean flag = hfWebService.modifyReservoirBeginLevel(userName, ybdx, dm, periodBeginLevel);

        if (flag) {
            return new Message().ok(666, "success");
        }

        return new Message().error(333, "error");
    }

    public static Message updateReservoirInteraction(JSONObject params) {

        String userName = params.getString("userName");


        List<JSONObject> dataList = JSONArray.parseArray(params.getJSONArray("data").toJSONString(), JSONObject.class);

        for (int i = 0; i < dataList.size(); i++) {

            JSONObject js = dataList.get(i);

            String foreObjName = js.getString("ybdx"); //预报对象

            String sectionName = js.getString("dm"); //断面

            List<JSONObject> list = JSONArray.parseArray(js.getJSONArray("data").toJSONString(), JSONObject.class);

            //27.水库调度交互-修改水库断面未来出库或者水位数据
            //对应方法：boolean modifyReservoirDataList(String userName, String foreObjName, String sectionName,Map<Calendar, Map<String, Double>> dataListMap)
            //传入参数：用户名，预报对象名、断面名、修改的数据map，格式与上接口一致
            //返回格式：如果设置成功返回true，否则返回false

            list.forEach(x -> {

                Calendar calendar = TimeUtil.strToCalendar(x.getString(TIME_STYLE), "99");

                Map<String, Double> value = new HashMap<>();

                Iterator iter = x.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();

                    String key = entry.getKey().toString();

                    if (!StringUtils.equalsIgnoreCase(key, TIME_STYLE) && NumberUtils.isNumeric(entry.getValue().toString())) {
                        value.put(key, Double.parseDouble(entry.getValue().toString()));
                    }

                }

                Map<Calendar, Map<String, Double>> dataListMap = new HashMap<>();

                dataListMap.put(calendar, value);

                boolean flag = hfWebService.modifyReservoirDataList(userName, foreObjName, sectionName, dataListMap);


            });
        }


        //获取水位
        Double periodBeginLevel = params.getDouble("periodBeginLevel");

        if (null != periodBeginLevel) {

            //获取断面
            String dm = params.getString("name");

            //获取预报对象
            String ybdx = params.getString("dx");

            hfWebService.modifyReservoirBeginLevel(userName, ybdx, dm, periodBeginLevel.doubleValue());
        }


        return new Message().ok(666, "success");
    }


    public static Message floodSetMode(JSONObject params) {
        //40.设置单个断面的水库调度模式
        //对应方法：boolean doSingleSecDispatchConfigSet(String userName, String foreObjName, String secName,
        //			Map<String, String> singleSecMap)
        //传入参数：用户名，预报对象名，断面名，水库调度设置信息
        //返回格式：boolean型，设置成功是true

        String userName = params.getString("userName");

        //预报对象名
        String foreObjName = params.getString("dx");

        //断面名
        String sectionName = params.getString("name");

        JSONObject jsonObject = params.getJSONObject("singleSecMap");

        Map<String, String> singleSecMap = JSONObject.parseObject(JSON.toJSONString(jsonObject), Map.class);

        boolean flag = hfWebService.doSingleSecDispatchConfigSet(userName, foreObjName, sectionName, singleSecMap);

        if (flag) {
            return new Message().ok(666, "success");
        }

        return new Message().error(333, "error");
    }


    public static Message regulation(JSONObject params) {

        //获取指定预报对象集合下的所有水库断面集合
        //对应方法：Map<HFSection, String> getAllReservoirSecMap(String userName, String schemeTypeStr, String basinName,List<String> foreObjNameList)           
        //传入参数：用户名，方案类型名，流域名，预报对象名称集合
        //返回格式：主键是断面对象，值是对应的预报对象名

        String userName = params.getString("userName");

        //方案类型名
        String falx = params.getString("falx");

        //流域
        String ly = params.getString("ly");

        //预报对象名称集合
        List<String> ybdx = JSONArray.parseArray(params.getJSONArray("ybdx").toJSONString(), String.class);

        Map<HFSection, String> map = hfWebService.getAllReservoirSecMap(userName, falx, ly, ybdx);

        //组装断面名称
        List<String> treeName = new LinkedList<>();

        List<HFSection> hfSectionList = new LinkedList<>();

        for (HFSection hfSection : map.keySet()) {
            treeName.add(hfSection.getName());
            hfSectionList.add(hfSection);
        }

        //根据断面名称获取数据
        //24.获取水库断面对应的时间序列
        //对应方法：Map<HFSection, String> getAllReservoirSecMap(String userName, String schemeTypeStr, String basinName,List<String> foreObjNameList)           
        //传入参数：用户名，方案类型名，流域名，预报对象名称集合
        //返回格式：主键是断面对象，值是对应的预报对象名

        Object[] mapData = hfWebService.getAllReservoirDataMap(userName, hfSectionList);

        List<Map> list = (List) mapData[0];

        Map<Calendar, Map<String, Double>> vMap = (Map) mapData[1];

        List<JSONObject> tableHeader = new LinkedList<>();

        List<JSONObject> tableData = new LinkedList<>();

        JSONObject json = new JSONObject();
        json.put("prop", "time");
        json.put("label", "时间");
        tableHeader.add(json);

        Map<String, String> tableMap = new HashMap<>();

        for (int i = 0; i < list.size(); i++) {

            Map<String, String> lMap = list.get(i);

            JSONObject js = new JSONObject();

            //断面名
            String dmName = lMap.get("断面名");

            String prop = ("v" + i);

            tableMap.put(dmName, prop);

            js.put("label", dmName);

            //数据类型
            List<JSONObject> childJS1 = JSONArray.parseArray("[{\"label\":\"" + lMap.get("数据类型") + "\",\"prop\":\"" + prop + "\"}]", JSONObject.class);

            //调度类型
            List<JSONObject> childJS = JSONArray.parseArray("[{\"label\":\"" + lMap.get("调度类型") + "\",\"children\":" + childJS1 + "}]", JSONObject.class);

            js.put("children", childJS);

            tableHeader.add(js);

        }


        for (Calendar calendar : vMap.keySet()) {

            String time = TimeUtil.CalendarToString(calendar, DateFormatter.SECOND_FORMATTER);

            Map<String, Double> valueMap = vMap.get(calendar);

            JSONObject jsonObject = new JSONObject();

            jsonObject.put("time", time);

            for (String str : valueMap.keySet()) {

                String prop = tableMap.get(str);

                jsonObject.put(prop, valueMap.get(str));

            }

            tableData.add(jsonObject);
        }


        tableData = tableData.stream().sorted(Comparator.comparing(x -> DateUtil.parse(x.getString("time"), "yyyy-MM-dd HH:mm:ss").getTime())).collect(Collectors.toList());

        return new Message().ok(666, "success").addData("tableData", tableData).addData("tableHeader", tableHeader).addData("tree", treeName);
    }


    public static Message updateModifyRainfall(JSONObject params) {

        String userName = params.getString("userName");

        //类型
        String type = params.getString("type");
        //预报对象
        String ybdx = params.getString("ybdx");
        //方案名称
        String modelSchName = params.getString("modelSchName");
        //小时累计
        int timeStep = params.getInteger("timeStep");

        //[{"Time":"2023-08-28 12:00:00","unitId":"110302","v":"11"}]
        List<JSONObject> list = JSONArray.parseArray(params.getJSONArray("data").toJSONString(), JSONObject.class);

        Map<Long, Map<Calendar, Double>> allDataMap = new HashMap<>();

        for (JSONObject js : list) {

            Long id = js.getLong("id");

            Double v = js.getDouble("v");

            String time = js.getString("Time");

            Calendar calendar = TimeUtil.sectionName(time, DateFormatter.SECOND_FORMATTER);

            Map<Calendar, Double> map = new HashMap<>();

            map.put(calendar, v);

            if (null == allDataMap.get(id)) {
                allDataMap.put(id, map);
            } else {

                Map<Calendar, Double> m = allDataMap.get(id);

                m.putAll(map);

            }


        }

        boolean flag = false;

        //面雨量
        if (StrUtil.equals(type, "areaRainfall")) {

            //修改面雨量 modifyHisUnitRainData
            //  userName:用户名称, foreObjName：预报对象名, modelName：方案名称, Map<Long, Map<Calendar, Double>> allDataMap：数据, int timeStep：
            flag = hfWebService.modifyHisUnitRainData(userName, allDataMap, timeStep);
        }
        //雨量站
        else if (StrUtil.equals(type, "prestationl")) {
            //修改雨量站 modifyHisPreRainData
            flag = hfWebService.modifyHisPreRainData(userName, allDataMap, timeStep);
        }
        //24.修改历史区间面雨量
        else if (StrUtil.equals(type, "historicalRainfall")) {
            flag = hfWebService.modifyHisRegionRainData(userName, allDataMap, timeStep);
        }


        if (flag) {
            return new Message().ok(666, "success");
        }


        return new Message().error(333, "error");

    }


    public static Message getPreStationListByUnit(JSONObject params) {

        //26.获取单元下的所有雨量站
        //对应方法：List<HFPreStation> getPreStationListByUnit(String userName, String foreObjName, Long unitId)
        //传入参数：用户名，预报对象名，单元点号
        //返回格式：HFPreStation对象集合，其中HFPreStation对象有getId方法与getName方法

        String userName = params.getString("userName");

        String ybdx = params.getString("ybdx");

        Long unitId = params.getLong("unitId");

        List<HFPreStation> list = hfWebService.getPreStationListByUnit(userName, ybdx, unitId);

        List<JSONObject> result = new ArrayList<>();

        for (HFPreStation hfPreStation : list) {

            JSONObject js = new JSONObject();

            js.put("id", hfPreStation.getId());

            js.put("name", hfPreStation.getName());

            result.add(js);

        }


        return new Message().ok(666, "success").addData("content", result);
    }


    public static Message getAllRegionForeRainMap(JSONObject params) {

        String userName = params.getString("userName");

        String ly = params.getString("ly");

        String fanAn = params.getString("fanAn");

        int timeStep = params.getInteger("timeStep") == null ? 1 : params.getInteger("timeStep");

        //是否展示上下线
        boolean isMax = null == params.getBoolean("isMax") ? false : params.getBoolean("isMax");

        //单元id
        Long unitId = params.getLong("unitId");
        //32.获取某个流域下所有区间的面预报降雨序列
        //对应方法：Map<Calendar, Map<HFForeObj, Map<HFSchRegion, Map<String, Double>>>> getAllRegionForeRainMap(String userName,String basinName, int timeStep, boolean isMax)
        //传入参数：用户名，流域名，时间步长（若大于1则展示多个时段累计值），是否展示上下限
        //返回格式：外层map的主键是时间，内层map的主键是数值的类型，map的值是对应的预报降雨值

        Map<Calendar, Map<HFForeObj, Map<HFSchRegion, Map<String, Double>>>> map = hfWebService.getAllRegionForeRainMap(userName, fanAn, timeStep, isMax);

        List<JSONObject> tableData = new LinkedList<>();

        Set<JSONObject> tableHeader = new LinkedHashSet<>();

        JSONObject object = new JSONObject();

        object.put("prop", TIME_STYLE);

        object.put("label", "时间");

        tableHeader.add(object);

        RSTime.RSTimeType rsTimeType = getTimeKey(userName);


        for (Calendar calendar : map.keySet()) {
            //将Calendar转string
            String time = DataTransferUtil.CalendarToString(calendar, rsTimeType, RSTime.RSTimeFormat.LINE);

            JSONObject js = new JSONObject();

            js.put(TIME_STYLE, time);

            Map<HFForeObj, Map<HFSchRegion, Map<String, Double>>> mapOne = map.get(calendar);

            for (HFForeObj hFForeObj : mapOne.keySet()) {

                //添加表头数据
                JSONObject tr = new JSONObject();

                tr.put("label", hFForeObj.getName());

                Map<HFSchRegion, Map<String, Double>> v = mapOne.get(hFForeObj);

                Set<JSONObject> set = new LinkedHashSet<>();

                for (HFSchRegion hfSchRegion : v.keySet()) {

                    if (null != unitId && hfSchRegion.getId() != unitId.longValue()) {

                        continue;
                    }
                    Map<String, Double> va = v.get(hfSchRegion);

//                    Long preStaForeID = hfSchRegion.getUnitPrepRegion().getPreStaForeID(); //倾向值
//                    Long preStaMaxForeID = hfSchUnit.getUnitPrepRegion().getPreStaMaxForeID();//上限值
//                    Long preStaMinForeID = hfSchUnit.getUnitPrepRegion().getPreStaMinForeID();//下限

                    JSONObject p = new JSONObject();
                    p.put("label", hfSchRegion.getName());
                    p.put("topLabel", hFForeObj.getName());
                    JSONArray array = new JSONArray();


                    //1:倾向值,2:上限值,3:下限值
                    if (null != va.get("下限值")) {
                        JSONObject td3 = new JSONObject();
                        td3.put("label", "下限值");
                        td3.put("showName", hfSchRegion.getName());
                        td3.put("prop", hfSchRegion.getId() + "01");
                        td3.put("dataType", 3);
                        td3.put("ybdx", hFForeObj.getName());
                        td3.put("unitId", hfSchRegion.getId());
                        array.add(td3);

                        js.put(hfSchRegion.getId() + "01", va.get("下限值"));
                    }


                    if (null != va.get("倾向值")) {
                        JSONObject td1 = new JSONObject();
                        td1.put("label", "倾向值");
                        td1.put("showName", hfSchRegion.getName());
                        td1.put("prop", hfSchRegion.getId());
                        td1.put("dataType", 1);
                        td1.put("ybdx", hFForeObj.getName());
                        td1.put("unitId", hfSchRegion.getId());
                        array.add(td1);
                        js.put(hfSchRegion.getId() + "", va.get("倾向值"));

                    }

                    if (null != va.get("上限值")) {
                        JSONObject td2 = new JSONObject();
                        td2.put("label", "上限值");
                        td2.put("showName", hfSchRegion.getName());
                        td2.put("dataType", 2);
                        td2.put("prop", hfSchRegion.getId() + "02");
                        td2.put("ybdx", hFForeObj.getName());
                        td2.put("unitId", hfSchRegion.getId());
                        array.add(td2);

                        js.put(hfSchRegion.getId() + "02", va.get("上限值"));

                    }

                    p.put("children", array);
                    set.add(p);


                }

                tr.put("children", set);

                tableHeader.add(tr);
            }
            tableData.add(js);


        }


        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", tableData);
    }

    public static Message getAllSecForeStaticMap(JSONObject params) {

        //对应方法：List<Map<String, Object>> getAllSecForeStaticMap(String userName, String basinName, List<Integer> typeList,boolean onlyOutSec)
        //传入参数：用户名，流域名，序列类型（0:不考虑降雨,1:倾向值,2:上限值,3:下限值），支持同时多个，是否只看出口断面
        //返回格式：map集合，主键是统计属性，值是对应的数据

        String userName = params.getString("userName");

        String ly = params.getString("ly");

        JSONArray arr = params.getJSONArray("typeList");

        if (null == arr) {
            arr = JSONArray.parseArray("[0]");
        }
        boolean isFlowOrReg = null == params.getBoolean("isFlowOrReg") ? true : params.getBoolean("isFlowOrReg");
        if (null == arr) {
            arr = JSONArray.parseArray("[0]");
        }

        List<Integer> type = JSONArray.parseArray(arr.toJSONString(), Integer.class);

        boolean onlyOutSec = params.getBoolean("onlyOutSec");

        List<Map<String, String>> list = hfWebService.getAllSecForeStaticMap(userName, ly, type, onlyOutSec, isFlowOrReg);

        //表头数据
        List<JSONObject> tableHeader = new LinkedList<>();

        for (int i = 0; i < list.size(); i++) {

            Map<String, String> map = list.get(i);

            for (String str : map.keySet()) {

                JSONObject jsonObject = new JSONObject();

                jsonObject.put("prop", str);
                jsonObject.put("label", str);

                tableHeader.add(jsonObject);
            }
            break;
        }


        return new Message().ok(666, "success").addData("tableHeader", tableHeader).addData("tableData", list);
    }


    public static Message doDeleteResultList(JSONObject params) {


        Map<String, Object> map = JSONObject.parseObject(params.toJSONString(), Map.class);

        boolean flag = HFWebService.doDeleteResultList(map);

        if (!flag) {
            return new Message().error(333, "error");
        }

        return new Message().ok(666, "success");
    }

    public static Message doDeleteSingleResult(JSONObject params) {

        Map<String, String> map = JSONObject.parseObject(params.toJSONString(), Map.class);

        boolean flag = HFWebService.doDeleteSingleResult(map);
        if (!flag) {
            return new Message().error(333, "error");
        }

        return new Message().ok(666, "success");
    }


    public static Message floodGetDataByTime(JSONObject params) throws Exception {


        String bTime = params.getString("bTime");

        String eTime = params.getString("eTime");

        Long adid = params.getLong("adid");

        List<Object> objectList = new ArrayList<>();

        String sql = "select ADID,YBT, YBDT,YBNUM,TIMETYPE, TIMESPAN, BFP,BADJ,HFFL, FXT, HL_1, HL_3, HL_5, HL_7, S1, S2, BREC,FPAREA,YBR,BPUB, SCHNAME, FOREOBJNAME, SECNAME, MODELNAME, RESTYPE, FORECASTTYPE from HFIDX where 1=1    ";

        if (StringUtils.isNotBlank(bTime)) {

            sql += " and ybt >= ? ";

            objectList.add(DateUtil.calendar(DateUtil.parseDateTime(bTime)));
        }

        if (StringUtils.isNotBlank(eTime)) {
            sql += " and ybt <= ? ";

            objectList.add(DateUtil.calendar(DateUtil.parseDateTime(eTime)));
        }

        if (null != adid) {

            sql += " and adid= ? ";

            objectList.add(adid);
        }

        // executeSql(String sql, Class<T> classFlag, Object[] param, String serviceFlag)
        List<HFIdx> list = DBManager.executeSql(sql, HFIdx.class, objectList.toArray(), "wds");


        List<JSONObject> data = new ArrayList<>();
        //转化
        for (HFIdx hfIdx : list) {

            JSONObject js = JSONObject.parseObject(JSON.toJSONString(hfIdx));

            String ybt = TimeUtil.CalendarToString(hfIdx.getYbt(), DateFormatter.SECOND_FORMATTER);

            String ybdt = TimeUtil.CalendarToString(hfIdx.getYbdt(), DateFormatter.SECOND_FORMATTER);

            js.put("ybt", ybt);

            js.put("ybdt", ybdt);

            data.add(js);
        }


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", data);
    }


    public static Message floodSelectByOne(JSONObject params) {

        Long adid = params.getLong("adid");

        String ybt = params.getString("ybt");

        String ybdt = params.getString("ybdt");


        String sql = "SELECT ADID ,T,DT,D,S,FP,YBDT FROM ADXLD where 1=1 ";

        List<Object> objectList = new ArrayList<>();

        if (null != adid) {

            sql += " and adid=?";
            objectList.add(adid);
        }

        if (StringUtils.isNotBlank(ybt)) {

            sql += " and t=? ";

            objectList.add(DateUtil.calendar(DateUtil.parseDateTime(ybt)));
        }

        if (StringUtils.isNotBlank(ybdt)) {
            sql += "and ybdt=? ";

            objectList.add(DateUtil.calendar(DateUtil.parseDateTime(ybdt)));
        }

        sql += " order by dt  ";

        List<ADXLD> list = DBManager.executeSql(sql, ADXLD.class, objectList.toArray(), "wds");

        List<JSONObject> data = new ArrayList<>();

        for (ADXLD adxld : list) {

            JSONObject js = JSONObject.parseObject(JSON.toJSONString(adxld));

            String dt = TimeUtil.CalendarToString(adxld.getDt(), DateFormatter.SECOND_FORMATTER);

            js.put("dt", dt);

            String t = TimeUtil.CalendarToString(adxld.getT(), DateFormatter.SECOND_FORMATTER);

            js.put("t", t);

            String ybdtTime = TimeUtil.CalendarToString(adxld.getYbdt(), DateFormatter.SECOND_FORMATTER);

            js.put("ybdt", ybdtTime);

            data.add(js);

        }


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", data);


    }


    public static Message selectAllDataTypeById(JSONObject params) {


        String bTime = params.getString("bTime");

        String eTime = params.getString("eTime");

        Long adid = params.getLong("adid");

        //返回值
        List<Map<String, List<JSONObject>>> answer = new ArrayList<>();

        String sql = "select * from ADXLD where adid=" + adid + " and dt>=to_date('" + bTime + "','YYYY-MM-DD HH24:MI:SS') and dt<=to_date('" + eTime + "','YYYY-MM-DD HH24:MI:SS')  order by dt  ";

        List<ADXLD> list = DBManager.executeSql(sql, ADXLD.class, new Object[]{}, "wds");

        if (CollUtil.isEmpty(list)) return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", answer);

        List<JSONObject> data = new ArrayList<>();

        for (ADXLD adxld : list) {

            JSONObject js = JSONObject.parseObject(JSON.toJSONString(adxld));

            String dt = TimeUtil.CalendarToString(adxld.getDt(), DateFormatter.SECOND_FORMATTER);

            js.put("dt", dt);

            String t = TimeUtil.CalendarToString(adxld.getT(), DateFormatter.SECOND_FORMATTER);

            js.put("t", t);

            data.add(js);

        }

        //获取开始时间、结束时间的小时个数
        long num = (DateUtil.between(DateUtil.parseDateTime(bTime), DateUtil.parseDateTime(eTime), DateUnit.HOUR) + 1);

        Map<String, List<JSONObject>> map = data.stream().collect(Collectors.groupingBy(x -> x.getString("t")));

        Map<String, List<JSONObject>> resultMap = new HashMap<>();

        for (String str : map.keySet()) {

            List<JSONObject> strList = map.get(str);

            if (strList.size() == num) {
                resultMap.put(str, strList);
            }
        }

        if (MapUtil.isNotEmpty(resultMap)) {

            List<String> stringList = new ArrayList<>(resultMap.keySet());

            String sqlHfinx = "select ADID,YBT, YBDT,YBNUM,TIMETYPE, TIMESPAN, BFP,BADJ,HFFL, FXT, HL_1, HL_3, HL_5, HL_7, S1, S2, BREC,FPAREA,YBR,BPUB, SCHNAME, FOREOBJNAME, SECNAME, MODELNAME, RESTYPE, FORECASTTYPE from HFIDX where   (";

            for (int i = 0; i < stringList.size(); i++) {

                String st = stringList.get(i);

                if (i == 0) {
                    sqlHfinx += " ybt=to_date('" + st + "','YYYY-MM-DD HH24:MI:SS') ";
                } else {
                    sqlHfinx += " or ybt=to_date('" + st + "','YYYY-MM-DD HH24:MI:SS') ";
                }


            }

            sqlHfinx += " ) and ADID =" + adid + "";

            List<HFIdx> listHfindx = DBManager.executeSql(sqlHfinx, HFIdx.class, new Object[]{}, "wds");

            //转化
            for (HFIdx hfIdx : listHfindx) {

                String ybt = TimeUtil.CalendarToString(hfIdx.getYbt(), DateFormatter.SECOND_FORMATTER);

                String schname = hfIdx.getSchname();

                Map<String, List<JSONObject>> m = new HashMap<>();

                List<JSONObject> v = resultMap.get(ybt);

                m.put(schname, v);

                answer.add(m);

            }
        }
        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", answer);

    }


    public static Message modelComputation(JSONObject params) {

        String userName = params.getString("userName");

        List<Map<String, Object>> allForecastListMap = hfWebService.getAllForecastListMap(userName);

        for (int i = 0; i < allForecastListMap.size(); i++) {

            Map<String, Object> map = allForecastListMap.get(i);

            Map<String, Double> value = new LinkedHashMap<>();

            Map<Calendar, Double> valMap = (Map) map.get("dataList");

            for (Calendar calendar : valMap.keySet()) {

                String time = DateUtil.formatDateTime(DateUtil.date(calendar));

                Double v = valMap.get(calendar);

                value.put(time, v);

            }

            map.put("dataList", value);
        }

        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", allForecastListMap);
    }

    public static Message aSetOfProcesses(JSONObject params) {

        String userName = params.getString("userName");
        //10.默认进行预报计算、存库
        //对应方法：boolean doDefaultForecast(String userName)
        //传入参数：用户名
        //返回格式：true则计算成功

        hfWebService.initialize(userName, hWindow, hfScheme);

        boolean flag = hfWebService.doDefaultForecast(userName, Calendar.getInstance());

        if (flag) {
            return new Message().ok(HttpStatus.HTTP_OK, "success");
        }

        return new Message().error(HttpStatus.HTTP_INTERNAL_ERROR, "error");
    }

    public static Message selectDataForCalculation(JSONObject params) {

        //预报数据
        Map<String, Map<String, Map<Calendar, String>>> forecastListMap = new HashMap<>();

        String userName = params.getString("userName");

        List<JSONObject> list = JSONObject.parseArray(JSON.toJSONString(params.getJSONArray("data")), JSONObject.class);

        list.forEach(x -> {

            Set<String> set = x.keySet();

            Calendar time = TimeUtil.strToCalendarByFormat(x.getString(TIME_STYLE), 99);

            set.forEach(k -> {
                if (!k.equals(TIME_STYLE) && k.indexOf("-") != -1) {
                    List<String> t = Arrays.asList(k.split("-"));

                    //模型方案名
                    String modelSchName = t.get(2);

                    //预报序列名
                    String dpVecName = t.get(t.size() - 1);

                    String value = x.getString(k);

                    Map<Calendar, String> v = new HashMap<>();

                    v.put(time, value);

                    Map<String, Map<Calendar, String>> secondMap = forecastListMap.get(modelSchName);


                    if (null == secondMap) {
                        secondMap = new HashMap<>();

                        secondMap.put(dpVecName, v);

                        forecastListMap.put(modelSchName, secondMap);

                    } else {
                        Map<Calendar, String> v1 = secondMap.get(dpVecName);

                        if (null == v1) {

                            secondMap.put(dpVecName, v);
                        } else {
                            v1.putAll(v);
                        }

                    }


                }
            });

        });


        List<String> dragStaticInfo = hfWebService.getDragStaticInfo(userName, forecastListMap);


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", dragStaticInfo);
    }


    public static Message carryingCapacity(JSONObject params) {

        //50.进行承洪能力分析
        //对应方法：Map<String, Map<String, String>> doFloodWithstandAnalysis(String userName, String foreObjName,String secName, double limitWaterLevel)
        //传入参数：用户名，预报对象名，断面名，目标水位
        //返回格式：外层map的主键是模型，中间层map的主键是要素名，值是结果
        String userName = params.getString("userName");
        //预报对象名
        String foreObjName = params.getString("ybdx");
        //断面名
        String secName = params.getString("dm");
        //目标水位
        double limitWaterLevel = params.getDouble("limitWaterLevel");
        List<String> list = hfWebService.doFloodWithstandAnalysis(userName, foreObjName, secName, limitWaterLevel);
        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", list);
    }

//    public static void saveAbnormalWeather(JSONObject params) {
//        //        岷江-紫坪铺154400、嘉陵江-亭子口154705、大渡河-瀑布沟154210、雅砻江-二滩154100
//        // 入库点号 15400090
//        String[] sectionNames = "紫坪铺,亭子口,瀑布沟,二滩".split(",");
//        String[] senids = "154400,154705,154210,154100".split(",");
//        String[] foreObjNames = "四川预报电站,四川预报电站,四川预报电站,四川预报电站".split(",");
//        String type = "1,2,3";
//        String timeType = params.getString("falx");
//        List<Integer> typeList = Arrays.asList(type.split(",")).stream().map(x -> Integer.parseInt(x)).collect(Collectors.toList());
//        String userName = params.getString("userName");
//        String basinName = "长江上游流域";
//        Map<Long, JSONObject> datas = new HashMap<>();
//        for (int i = 0; i < sectionNames.length; i++) {
//            List<Map<String, Object>> hisList = hfWebService.getSecObservedListMap(userName, basinName, foreObjNames[0], sectionNames[i]);
//            if (hisList != null && hisList.size() > 0) {
//                HashMap<Calendar, String> hisDatas = JSONObject.parseObject(JSON.toJSONString(hisList.get(0).get("data")), new TypeReference<HashMap<Calendar, String>>() {
//                });
//                for (Map.Entry<Calendar, String> entry : hisDatas.entrySet()) {
//                    JSONObject data = new JSONObject();
//                    data.put("time", entry.getKey().getTimeInMillis());
//                    if (datas.containsKey(entry.getKey().getTimeInMillis())) {
//                        data = datas.get(entry.getKey().getTimeInMillis());
//                    } else {
//                        datas.put(entry.getKey().getTimeInMillis(), data);
//                    }
//                    data.put("real", (data.containsKey("real") ? data.getDouble("real") : 0) + (entry.getValue().equals("") ? 0 : Double.parseDouble(entry.getValue())));
//                    Double avgFlow = getAvgFlow(senids[i], timeType, entry.getKey(), 5);
//                    if (avgFlow != null)
//                        data.put("avg", (data.containsKey("avg") ? data.getDouble("avg") : 0) + avgFlow.doubleValue());
//                }
//            }
//            Map<String, Map<String, Map<String, Object>>> secForecastMap = hfWebService.getSecForecastListMap(userName, basinName, foreObjNames[i], sectionNames[i], false, typeList);
//
//            Set<String> keyset = secForecastMap.keySet();
//            if (secForecastMap.get(keyset.iterator().next()).size() <= 0) {
//                System.out.println(basinName + foreObjNames[i] + sectionNames[i] + "没预报");
//            } else {
//                LinkedHashMap<Long, String> foreData = JSONObject.parseObject(JSON.toJSONString(secForecastMap.get(keyset.iterator().next()).get("m_FPAvgRCQCal").get("data")), new TypeReference<LinkedHashMap<Long, String>>() {
//                });
//                for (Map.Entry<Long, String> entry : foreData.entrySet()) {
//                    JSONObject data = new JSONObject();
//                    data.put("time", entry.getKey());
//                    if (datas.containsKey(entry.getKey())) {
//                        data = datas.get(entry.getKey());
//                    } else {
//                        datas.put(entry.getKey(), data);
//                    }
//                    data.put("fore", (data.containsKey("fore") ? data.getDouble("fore") : 0) + (entry.getValue().equals("") ? 0 : Double.parseDouble(entry.getValue())));
//                    if (!data.containsKey("real")) {
//                        Double avgFlow = getAvgFlow(senids[i], timeType, DBOpUtil.getCalendar(entry.getKey()), 5);
//                        if (avgFlow != null)
//                            data.put("avg", (data.containsKey("avg") ? data.getDouble("avg") : 0) + avgFlow.doubleValue());
//                    }
//                }
//            }
//        }
//
//        List<JSONObject> list = new ArrayList<>();
//        list = datas.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).collect(Collectors.toList());
//
//        for (int i = 0; i < list.size(); i++) {
//            JSONObject data = list.get(i);
//            int flag = 0;
//            double flow = data.getDouble("fore");
//            //有实测值用实测值
//            if (data.containsKey("real")) {
//                flow = data.getDouble("real");
//            }
//            double deviation = 0d;
//            if (data.containsKey("avg")) {
//
//                double avg = data.getDouble("avg");
//                if (avg != 0) {
//                    deviation = (flow - avg) / avg;
//                }
//
//            }
//            if (deviation > 0.2) {
//                flag = 1;
//            } else if (deviation < -0.2) {
//                flag = -1;
//            } else {
//                //滑动偏差
//                if (i >= 2) {
//                    double sum = 0;
//                    for (int j = 1; j <= (i > 2 ? 3 : 2); j++) {
//                        if (list.get(i - j).containsKey("real"))
//                            sum += list.get(i - j).getDouble("real");
//                        else
//                            sum += list.get(i - j).getDouble("fore");
//                    }
//                    if (sum != 0) {
//                        if ((sum / (i > 2 ? 3 : 2) - flow) / (sum / (i > 2 ? 3 : 2)) > 0.15)
//                            flag = 1;
//                    }
//
//                }
//            }
//            String tableName = "hourdb";
//            if (timeType.equals("日")) tableName = "daydb";
//
//
//            String sql = "insert into " + tableName + "(senid,time,v,avgv) values (15400090," + TimeFunc.toDbString(data.getLongValue("time")) + "," + flag + "," + flag + ")";
//            try {
//                //System.out.println(sql);
//                DBOpUtil.exeHisDBExeWeb(sql);
//            } catch (Exception ei) {
//                //如果实测值或预测值时间大于当前,覆盖
//                if (data.containsKey("real") || data.getLong("time") > Calendar.getInstance().getTimeInMillis()) {
//                    sql = "update " + tableName + " set v = " + flag + ",avgv = " + flag + " where senid = 15400090 and time = " + TimeFunc.toDbString(data.getLongValue("time"));
//                    try {
//                        DBOpUtil.exeHisDBExeWeb(sql);
//                    } catch (Exception eu) {
//                        eu.printStackTrace();
//                    }
//                }
//            }
//
//
//        }
//
//    }

    public static JSONObject getBasinFlow(JSONObject params) {
//        岷江-紫坪铺154400、嘉陵江-亭子口154705、大渡河-瀑布沟154210、雅砻江-二滩154100
        String sectionName = params.getString("sectionNames");
        String basinName = params.getString("basinNames");
        String foreObjName = params.getString("foreObjNames");

        sectionName = WATERSHEDSTATISTICS_DM;
        basinName = WATERSHEDSTATISTICS_BASINNAME;
        foreObjName = WATERSHEDSTATISTICS_FOREOBJNAME;

        JSONObject return_data = new JSONObject();
        String userName = params.getString("userName");
        double[] weights = WATERSHEDSTATISTICS_WEIGHTS;
        String[] sectionNames = sectionName.split(",");
        String[] foreObjNames = foreObjName.split(",");
        String type = params.getString("type");
        String timeType = params.getString("falx");
        if (StringUtils.isBlank(type)) {
            type = SequenceType.SEQUENCE_TYPE1.getIndex() + "";  //默认不考虑降雨
        }
        List<Integer> typeList = Arrays.asList(type.split(",")).stream().map(x -> Integer.parseInt(x)).collect(Collectors.toList());
        JSONArray pieData = new JSONArray();
        JSONArray processData = new JSONArray();
//        JSONArray basinData = JSONArray.parseArray("[\"大渡河\",\"雅砻江\",\"岷江\",\"嘉陵江\",\"全网\"]");
        List<String> basinData = new ArrayList<>(Arrays.asList(WATERSHEDSTATISTICS_RIVER.split(",")));
        basinData.add("全网");
        JSONArray compareData = new JSONArray();

        double basinFlow = 0d;
        double basinForeFlow = 0d;
        return_data.put("pieData", pieData);
        return_data.put("processData", processData);
        return_data.put("basinData", basinData);
        return_data.put("compareData", compareData);
        for (int i = 0; i < sectionNames.length; i++) {
            boolean flag = false;
            //if (sectionNames[i].equals("瀑布沟")) flag = true;
            Map<String, Map<String, Map<String, Object>>> secForecastMap = hfWebService.getSecForecastListMap(userName, basinName, foreObjNames[0], sectionNames[i], flag, typeList);

            Set<String> keyset = secForecastMap.keySet();
            if (MapUtil.isEmpty(secForecastMap) || secForecastMap.get(keyset.iterator().next()).size() <= 0) {
                System.out.println(basinName + foreObjNames[0] + sectionNames[i] + "没预报");
                continue;
            }
            LinkedHashMap<Long, String> datas = JSONObject.parseObject(JSON.toJSONString(secForecastMap.get(keyset.iterator().next()).get("m_FPAvgRCQCal").get("data")), new TypeReference<LinkedHashMap<Long, String>>() {
            });
            double foreFlow = 0;
            JSONObject process = new JSONObject();
            process.put("name", basinData.get(i));
            process.put("type", "line");
            JSONArray data = new JSONArray();
            process.put("data", data);
            for (Map.Entry<Long, String> entry : datas.entrySet()) {
                data.add(JSONArray.parseArray("[\"" + TimeFunc.getFormatStr(DBOpUtil.getCalendar(entry.getKey()), "yyyy-MM-dd HH:mm:ss") + "\",\"" + entry.getValue() + "\"]"));
                foreFlow += Double.parseDouble(entry.getValue());
            }
            processData.add(process);
            pieData.add(JSONObject.parseObject("{name:\"" + basinData.get(i) + "\",value:" + foreFlow / data.size() + "}"));

            try {
                Calendar btime = DBOpUtil.getCalendar(TimeFunc.parseDateTime(data.getJSONArray(0).getString(0)));
                Calendar etime = DBOpUtil.getCalendar(TimeFunc.parseDateTime(data.getJSONArray(data.size() - 1).getString(0)));
                Double flow = getAvgFlow(flowSenids.getString(sectionNames[i]), timeType, TimeFunc.add(btime, 0, -1), TimeFunc.add(etime, 0, -1));
                Double value = null;
                if (flow != null) {
                    basinFlow += flow * weights[i];
                    basinForeFlow += foreFlow / data.size() * weights[i];
                    if (flow != 0) {
                        value = ((foreFlow / data.size() - flow) / flow * 100);
                    }
                }
                compareData.add(value);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        if (basinFlow != 0) {
            compareData.add((basinForeFlow - basinFlow) / basinFlow * 100);
        }

        //装机占比
        File file = new File("conf/forecast/pieData.json");
        if (FileUtil.exist(file)) {
            cn.hutool.json.JSONArray array = JSONUtil.readJSONArray(file, Charset.forName("UTF-8"));
            return_data.put("pieData", array);

        }

        //排序
        if (CollUtil.isNotEmpty(basinData)) {
            String element = basinData.remove(basinData.size() - 1);
            basinData.add(0, element);
            Collections.reverse(basinData);
        }
        if (CollUtil.isNotEmpty(compareData)) {
            Object ele = compareData.remove(compareData.size() - 1);
            compareData.add(0, ele);
            Collections.reverse(compareData);
        }


        return return_data;
    }

    //获取指定时间范围均值
    private static Double getAvgFlow(String senid, String type, Calendar btime, Calendar etime) {
        Double flow = null;
        btime = TimeFunc.add(btime, type.equals("日") ? 5 : 6, 1);
        etime = TimeFunc.add(etime, type.equals("日") ? 5 : 6, 1);
        String table = "hourdb";
        switch (type) {
            case "日":
                table = "daydb";
        }
        if (StrUtil.equals("WOU", SqlManager.getServiceNames()[0])) {
            table = "WOU_DATA_HOURDB";
            switch (type) {
                case "日":
                    table = "WOU_DATA_DAYDB";
            }
        }
        String sql = "select avg(v) from " + table + " where senid = " + senid + " and time >= " + TimeFunc.toDbString(btime) + " and time <= " + TimeFunc.toDbString(etime);

        List<Double> list = DBManager.executeSql(sql, Double.class, new ArrayList[0], SqlManager.getServiceNames()[0]);
        if (list.size() > 0) flow = list.get(0);
//
//        try {
//            ResultSetInterface rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
//            if (rs.getRowNum() > 0) flow = rs.getDouble(0, 0);
//        } catch (Exception e) {
//        }
        return flow;
    }


    public static void main(String[] args) {

        String winFilePath = FilePathConstant.HFWINDOW_XML_FILEPATH;
        String schemeFilePath = FilePathConstant.HFSCHEME_XML_FILEPATH;
        File winFile = new File(winFilePath);
        if (!winFile.exists()) {
            winFile = new File("HFWindowConf.xml");
        }

        SAXReader saxReader = new SAXReader();
        saxReader.setEncoding("UTF-8");
        File file = new File(schemeFilePath);
        if (!file.exists()) {
            file = new File("HFSchemeConf.xml");
        }

        HFWebService service = new HFWebService("zh", winFile, file);
        SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        service.initialize("高熠飞", winFile, file);

        List<String> allSchemeNameList = service.getAllSchemeNameList();
        String schemeName = allSchemeNameList.get(0);
        List<String> allBasinNameList = service.getAllBasinNameList(schemeName);
        String basinName = allBasinNameList.get(0);
        List<String> allForeObjNameList = service.getAllForeObjNameList(schemeName, basinName);
        Calendar forecastBtime = Calendar.getInstance();
        Calendar time = null;
        try {
            forecastBtime.setTime(sDateFormat.parse("2024-06-18 10:00:00"));

            time = Calendar.getInstance();
            time.setTime(sDateFormat.parse("2024-06-18 12:00:00"));
            Calendar time1 = Calendar.getInstance();
            time1.setTime(sDateFormat.parse("2024-05-12 18:00:00"));
            Calendar time2 = Calendar.getInstance();
            time2.setTime(sDateFormat.parse("2024-06-02 10:00:00"));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        /* service.doDefaultForecast("高熠飞",forecastBtime); */
        /*
         * List<String> allBoundarySecList = service.getAllBoundarySecList("高熠飞",
         * schemeName, basinName, allForeObjNameList); for(String
         * sec:allBoundarySecList) { System.out.println(sec); }
         */

        Map<String, Map<String, Boolean>> allModelSelMap = service.getAllModelSelMap("高熠飞", "小时");

        long btime = Calendar.getInstance().getTimeInMillis();
        Map<String, Object> readData = service.readData("高熠飞");

	/*	List<Integer>typeList = new ArrayList<>();
		typeList.add(1);
		List<Map<String, String>> allSecForeStaticMap = service.getAllSecForeStaticMap("高熠飞", basinName, typeList, false);
		for(Map<String, String>map:allSecForeStaticMap) {
			for(Entry<String, String>entry:map.entrySet()) {
				System.out.println(entry.getKey()+"   "+entry.getValue());
			}
		}
	*/
        boolean bo = service.readRainDataBySource("高熠飞", "新安江流域", "浙江气象局");
//

        //传入参数：用户名，流域名
        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>>> map123 = service.getForeRainDataMap("高熠飞", "气象方案", 1, false);

        Map<Long, List<Map<String, Object>>> dataList = new HashMap<>();

        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> map1 = new HashMap<>();
        map1.put("time", time);
        map1.put("v", 1000d);
        map1.put("dataType", 1);
        list.add(map1);

        dataList.put(1L, list);
        service.modifyBasinRainData("高熠飞", dataList, 1, false);


        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Double>>> map456 = service.getHisRainDataMap("高熠飞", "气象方案", 1);

        service.forecast("高熠飞", false);


        List<Integer> typeList = new ArrayList<>();
        typeList.add(1);
        Map<String, Map<String, Map<String, Object>>> secForecastListMap = service.getSecForecastListMap("高熠飞",
                basinName, allForeObjNameList.get(0), "新安江水库", false, typeList);


        Map<String, Map<String, Object>> map = secForecastListMap.get("气象方案");

        Map<String, Map<String, Object>> swmap = secForecastListMap.get("水文方案");

        Map<String, Object> map2 = map.get("m_WholeRegFPAvg"); // m_FPAvgRCQCal Map<Calendar, String>
        Map<Calendar, String> dataList2 = (Map<Calendar, String>) map2.get("data");
        for (Map.Entry<Calendar, String> data : dataList2.entrySet()) {
            System.out.println(sDateFormat.format(data.getKey().getTime()) + "   " + data.getValue() + "   " + ((Map<Calendar, String>) swmap.get("m_WholeRegFPAvg").get("data")).get(data.getKey()));
        }

    }


    public static Message precisionEvaluation(JSONObject params) {

        String type = params.getString("type");

        String startTime = params.getString("startTime");
        String endTime = params.getString("endTime");
        String stationName = params.getString("stationName");

        //根据时间类型、电站名称组装实测、预测点号500
        BiMap<String, String> map = getFlowSenids(type, stationName);

        Set<String> keySet = map.keySet();
        Long[] senidArr = keySet.stream().map(x -> Long.parseLong(x)).toArray(Long[]::new);

        String mark = "hf_read_hourdb_v_byEvaluate";
        String markShi = "hf_read_hourdb_v";
        DateField dateField = DateField.HOUR_OF_DAY;
        if (StrUtil.equals("日", type)) {
            mark = "hf_read_daydb_v_byEvaluate";
            markShi = "hf_read_daydb_v";
            dateField = DateField.DAY_OF_YEAR;
        }

        Calendar BegT = DateUtil.calendar(DateUtil.offset(DateUtil.parseDateTime(startTime), dateField, 1));
        Calendar EndT = DateUtil.calendar(DateUtil.offset(DateUtil.parseDateTime(endTime), dateField, 1));

        List<HPSTimeDoubleValue> list = new ArrayList<>();

//        List<HFDataPoint> list = HFDatabaseDoc.readHisSenDBFromSLSDByCommon(senidArr, BegT, EndT, timeType, false, 1);
        Object[] param = new Object[]{new Long[]{Long.parseLong(map.getKey("prediction"))}, BegT, EndT};
        List<HPSTimeDoubleValue> list1 = DBManager.getTable(mark, HPSTimeDoubleValue.class, param);
        if (CollUtil.isNotEmpty(list1)) {
            list.addAll(list1);
        }

        Object[] param1 = new Object[]{new Long[]{Long.parseLong(map.getKey("flow"))}, BegT, EndT};
        List<HPSTimeDoubleValue> list2 = DBManager.getTable(markShi, HPSTimeDoubleValue.class, param1);
        if (CollUtil.isNotEmpty(list2)) {
            list.addAll(list2);
        }

        Map<String, JSONObject> objectMap = new HashMap<>();

        for (HPSTimeDoubleValue hpsTimeDoubleValue : list) {
            String time = DateUtil.formatDateTime(DateUtil.offset(DateUtil.date(hpsTimeDoubleValue.getTime()), dateField, -1));
            String prop = map.get(hpsTimeDoubleValue.getId() + "");
            JSONObject json = new JSONObject();
            json.put(TIME_STYLE, time);
            json.put(prop, hpsTimeDoubleValue.getValue());

            if (null == objectMap.get(time)) {
                objectMap.put(time, json);
            } else {
                JSONObject js = objectMap.get(time);
                js.putAll(json);
                objectMap.put(time, js);
            }

        }
        List<JSONObject> value = new ArrayList<>();
        if (MapUtil.isNotEmpty(objectMap)) {
            value = objectMap.values().stream().sorted(Comparator.comparing(x -> x.getString(TIME_STYLE))).collect(Collectors.toList());
        }

        return new Message().
                ok(HttpStatus.HTTP_OK, "success").addData("content", value);
    }

    //根据时间类型、电站名称组装实测、预测点号
    private static BiMap<String, String> getFlowSenids(String type, String stationName) {

        Document document = XmlUtil.readXML(hfScheme);

        String xmlStr = XmlUtil.toStr(document);

        cn.hutool.json.JSONObject json = JSONUtil.parseFromXml(xmlStr);

        cn.hutool.json.JSONObject foreSchemeSet = json.getJSONObject("foreSchemeSet");

        Object obj = foreSchemeSet.get("scheme");
        cn.hutool.json.JSONArray ARR = new cn.hutool.json.JSONArray();
        if (obj instanceof cn.hutool.json.JSONArray) {
            ARR = json.getJSONObject("foreSchemeSet").getJSONArray("scheme");
        } else {
            cn.hutool.json.JSONObject setJSONObject = foreSchemeSet.getJSONObject("scheme");
            ARR.add(setJSONObject);
        }

        BiMap<String, String> map = new BiMap<>(new HashMap<>());

        DecimalFormat decimalFormat = new DecimalFormat("00");
        String timeType = "RSHour";
        String timeNum = "02";
        if (StrUtil.equals("日", type)) {
            timeType = "RSDay";
            timeNum = "03";
        }

        //获取04固定死
        int indexNum = ArrayUtil.indexOf(EnumConstant.HFForeResType.foreResTypeElement, "m_FPAvgRCQCal");
        int num = EnumConstant.HFForeResType.foreResTypeVal[indexNum];
        String suffixNum = decimalFormat.format(num);

        List<JSONObject> stationList = JSONArray.parseArray(ARR.toString(), JSONObject.class);

        for (JSONObject station : stationList) {

            if (StrUtil.equals(timeType, station.getString("timeType"))) {

                JSONObject modelScheme = station.getJSONObject("basin")
                        .getJSONObject("watershed")
                        .getJSONObject("course")
                        .getJSONObject("foreObj")
                        .getJSONObject("modelScheme");
                //步长 如果是个位数前面补0
                String foreTimeStep = decimalFormat.format(modelScheme.getInteger("foreTimeStep"));
                String modelSchID = decimalFormat.format(modelScheme.getInteger("modelSchID"));

                JSONArray interSection = station.getJSONObject("basin")
                        .getJSONObject("watershed")
                        .getJSONObject("course")
                        .getJSONObject("foreObj")
                        .getJSONObject("modelScheme")
                        .getJSONObject("outSection")
                        .getJSONArray("interSection");

                JSONObject section = getChildren(interSection.toJavaList(JSONObject.class), stationName);

                //判断是否有预测点号。如果没有就拼接
                String actualId = null;
                if (null != section.get("publishIdConfig")) {
                    JSONObject publishIdConfig = section.getJSONObject("publishIdConfig");
                    if (null != publishIdConfig) {
                        Object dataSet = publishIdConfig.get("dataSet");
                        if (null != dataSet) {
                            if (dataSet instanceof JSONArray) {
                                for (int i = 0; i < publishIdConfig.getJSONArray("dataSet").size(); i++) {
                                    JSONObject jsonObject = publishIdConfig.getJSONArray("dataSet").getJSONObject(i);
                                    if (StrUtil.equals(jsonObject.getString("dataSetName"), "m_FPAvgRCQCal")) {
                                        actualId = jsonObject.getString("publishId");
                                        break;
                                    }
                                }
                            } else {
                                if (StrUtil.equals(publishIdConfig.getJSONObject("dataSet").getString("dataSetName"), "m_FPAvgRCQCal")) {
                                    actualId = publishIdConfig.getJSONObject("dataSet").getString("publishId");
                                }
                            }

                        }
                    }

                }

                if (StrUtil.isBlank(actualId)) {
                    //获取测站点号
                    String stationId = section.getString("interSecId");
                    //拼接预测点号
                    actualId = stationId + timeNum + foreTimeStep + modelSchID + suffixNum;
                }

                //获取实测点号
                String flowId = section.getJSONObject("HFDataSourceVec").getJSONObject("HFDataSource").getString("flowid");
                map.put(flowId, "flow");
                map.put(actualId, "prediction");
                return map;

            }
        }

        return map;
    }


    private static JSONObject getChildren(List<JSONObject> interSection, String interSecName) {
        for (JSONObject section : interSection) {
            if (StrUtil.equals(interSecName, section.getString("interSecName"))) {
                return section;
            }

            Object subSectionObj = section.get("interSection");
            if (subSectionObj == null) {
                continue;
            }

            List<JSONObject> subSections = new ArrayList<>();

            if (subSectionObj instanceof JSONArray) {
                subSections = ((JSONArray) subSectionObj).toJavaList(JSONObject.class);
            } else if (subSectionObj instanceof JSONObject) {
                subSections.add((JSONObject) subSectionObj);
            }

            JSONObject result = getChildren(subSections, interSecName);
            if (result != null) {
                return result;
            }
        }

        return null;
    }


    private static List<JSONObject> getDataByCalendar(Map<Calendar, String> map) {

        List<JSONObject> list = new ArrayList<>();

        for (Calendar calendar : map.keySet()) {

            String time = DateUtil.formatDateTime(DateUtil.date(calendar));

            String value = map.get(calendar);

            JSONObject jsonObject = new JSONObject();
            jsonObject.put(TIME_STYLE, time);
            jsonObject.put("value", value);
            list.add(jsonObject);

        }


        return list;
    }

    public static Message deductionInit(JSONObject params) {

        String userName = params.getString("userName");
        //获取开始时间
        String time = params.getString("time");
        //方案类型
        String falx = params.getString("falx");

        hfWebService.initialize(userName, hWindow, hfScheme);

        //获取所有的预报模式
        Map<String, Object> data = defaultData(params).getData();
        Map<String, Object> valueData = (Map<String, Object>) data.get("data");
        params.put("ybms", HFRunConSet.HFRunMode.getDesp(HFRunConSet.HFRunMode.RealTimeForecast));

        //流域名称
        Map<String, List<String>> lyMap = (Map<String, List<String>>) valueData.get("lyMap");
        List<String> lyList = lyMap.get(falx);
        params.put("ly", lyList.get(0));

        //预报对象
        Map<String, List<String>> ybObList = (Map<String, List<String>>) valueData.get("ybObList");
        List<String> ybObL = ybObList.get(lyList.get(0));
        JSONArray array = new JSONArray();
        array.addAll(ybObL);
        params.put("ybdx", array);

        hfWebService.initReadConditions(userName, params.getString("falx"), params.getString("ybms"), TimeUtil.strToCalendarByFormat(time, 99), params.getString("ly"), ybObL);

        hfWebService.readData(userName);

        return new Message().ok(HttpStatus.HTTP_OK, "success");
    }

    public static Message catchmentRainfall(JSONObject params) {

        Map<String, List<JSONObject>> result = new HashMap<>();
        String userName = params.getString("userName");
        //流域名称
        String basinName = params.getString("basinName");
        //获取配置文件rainfallForecast.json
        File file = new File(System.getProperty("user.dir") + "/conf/forecast/rainfallForecast.json");
        if (!file.exists()) {
            return new Message().error(500, "json文件缺失");
        }
        JSONObject jsonObject = JSONObject.parseObject(JSONUtil.readJSONObject(file, Charset.defaultCharset()).toString());


        //获取所有方案
        Map<String, Long> fanMap = hfWebService.getAllModelNameList(userName);
        String fanAn = fanMap.keySet().stream().findFirst().get();

        Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>>> map = hfWebService.getForeRainDataMap(userName, fanAn, 1, false);

        //
        List<String> basinNameList = Arrays.asList(basinName.split(","));

        for (String name : basinNameList) {
            List<JSONObject> list = getWatershedData(map, jsonObject, name);
            result.put(name, list);
        }


        return new Message().ok(HttpStatus.HTTP_OK, "success").addData("content", result);
    }

    public static Message updateCatchmentRainfall(JSONObject params) throws Exception {

        //获取配置文件rainfallForecast.json
        File file = new File(System.getProperty("user.dir") + "/conf/forecast/rainfallForecast.json");
        if (!file.exists()) {
            return new Message().error(500, "json文件缺失");
        }
        JSONObject jsonObject = JSONObject.parseObject(JSONUtil.readJSONObject(file, Charset.defaultCharset()).toString());

        //将流域下的所有单元的雨量数据更新
        JSONObject basinData = params.getJSONObject("content");

        List<JSONObject> data = new ArrayList<>();

        for (String basinName : basinData.keySet()) {

            //获取流域下的所有单元
            List<String> unitIds = getAllUnitIds(jsonObject, basinName);

            JSONArray arr = basinData.getJSONArray(basinName);

            List<JSONObject> unitData = getUnitData(arr, unitIds);

            data.addAll(unitData);

        }


        //调用flood_updateByUnitData 修改单元面雨量

        params.put("data", data);
        return updateByUnitData(params);


    }


    private static List<JSONObject> getUnitData(JSONArray array, List<String> unitIds) {

        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject node = array.getJSONObject(i);

            for (String unitId : unitIds) {

                JSONObject unitJSON = new JSONObject();
                unitJSON.putAll(node);
                unitJSON.put("id", unitId);

                result.add(unitJSON);
            }


        }

        return result;
    }

    private static List<String> getAllUnitIds(JSONObject jsonObject, String basinName) {

        List<String> unitIds = new ArrayList<>();
        //根据流域名称获取所有对应的单元
        JSONArray array = jsonObject.getJSONArray("features");

        Map<String, Integer> nodeArea = new HashMap<>();
        for (int i = 0; i < array.size(); i++) {

            JSONObject node = array.getJSONObject(i);
            JSONObject properties = node.getJSONObject("properties");

            if (StrUtil.equals(basinName, properties.getString("Basin"))) {
                String unitid = properties.getString("unitid");
                unitIds.add(unitid);
            }

        }


        return unitIds;
    }


    public static List<JSONObject> getWatershedData(Map<Calendar, Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>>> map, JSONObject jsonObject, String basinName) {

        //根据流域名称获取所有对应的单元
        JSONArray array = jsonObject.getJSONArray("features");

        Map<String, Integer> nodeArea = new HashMap<>();
        for (int i = 0; i < array.size(); i++) {

            JSONObject node = array.getJSONObject(i);
            JSONObject properties = node.getJSONObject("properties");

            if (StrUtil.equals(basinName, properties.getString("Basin"))) {
                String unitid = properties.getString("unitid");
                int area = properties.getInteger("area");
                nodeArea.put(unitid, area);
            }

        }


        List<JSONObject> result = new ArrayList<>();
        for (Calendar calendar : map.keySet()) {
            //将Calendar转string
            String time = TimeUtil.CalendarToStringByFormat(calendar, "yyyy-MM-dd HH:mm:ss");

            JSONObject js = new JSONObject();

            js.put(TIME_STYLE, time);

            Map<HFForeObj, Map<HFSchUnit, Map<String, Double>>> mapOne = map.get(calendar);

            double sumV = 0;
            //总面积
            int properArea = 0;

            for (HFForeObj hFForeObj : mapOne.keySet()) {

                //添加表头数据
                JSONObject tr = new JSONObject();

                tr.put("label", hFForeObj.getName());

                Map<HFSchUnit, Map<String, Double>> v = mapOne.get(hFForeObj);

                Set<JSONObject> set = new LinkedHashSet<>();

                for (HFSchUnit hfSchUnit : v.keySet()) {

                    Map<String, Double> va = v.get(hfSchUnit);

                    Long preStaForeID = hfSchUnit.getUnitPrepRegion().getPreStaForeID(); //倾向值
                    Long preStaMaxForeID = hfSchUnit.getUnitPrepRegion().getPreStaMaxForeID();//上限值
                    Long preStaMinForeID = hfSchUnit.getUnitPrepRegion().getPreStaMinForeID();//下限

                    if (nodeArea.containsKey(preStaForeID + "")) {
                        Double value = va.get("倾向值");
                        //面积
                        int area = nodeArea.get(preStaForeID + "");

                        sumV += (value * area);

                        //总面积
                        properArea += area;
                    }
                }


            }

            double sumValue = sumV / properArea;

            js.put("v", sumValue);

            result.add(js);
        }

        return result;
    }





}
