package com.yr.wds.xinyidai.common.cev.flood.service.commonsql;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sgepri.ce.db.ResultSetInterface;
import com.sgepri.ce.db.util.DBOpUtil;
import com.yr.wds.xinyidai.common.cev.flood.common.constant.IdConstant;
import com.yr.wds.xinyidai.common.cev.flood.common.constant.TimeConstant;
import com.yr.wds.xinyidai.common.cev.flood.common.riverodel.Children;
import com.yr.wds.xinyidai.common.cev.flood.common.riverodel.FirstGen;
import com.yr.wds.xinyidai.common.cev.flood.common.riverodel.SecondGen;
import com.yr.wds.xinyidai.common.cev.flood.config.R;
import com.yr.wds.xinyidai.common.cev.flood.util.topo.Reservoir;
import com.yr.wds.xinyidai.common.cev.flood.util.topo.River;
import org.springframework.stereotype.Component;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author xcw
 * @version 1.0
 * @date 2024/7/24 14:38
 * @description: 公共sql区域
 */
@Component
public class CommonSql {

    static Map<String, String> timeMap = new TreeMap<>();
    static Map<String, String> valueMap = new HashMap<>();
    static Map<String, String> keyMap = new HashMap<>();


    static {
        timeMap.put("HOURDB", "TIME");
        timeMap.put("REAL", "TIME");
        timeMap.put("DAYDB", "TIME");
        timeMap.put("RTEMS", "TIME");

        keyMap.put("HOURDB", "SENID");
        keyMap.put("REAL", "SENID");
        keyMap.put("DAYDB", "SENID");
        keyMap.put("RTEMS", "SENID");

        valueMap.put("HOURDB", "V");
        valueMap.put("REAL", "FACTV");
        valueMap.put("DAYDB", "V");
        valueMap.put("RTEMS", "FACTV");

    }

    public JSONObject getProcess(String senid, String tableName, String startTime, String endTime) {
        String time = timeMap.get(tableName);
        String key = keyMap.get(tableName);
        String value = valueMap.get(tableName);
        String sql = "select distinct " + time + ", " + value + " from " + tableName + " where " + key + " = '" + senid + "' and " +
                time + " between  '" + startTime + "' and '" + endTime + "' order by " + time + " asc;";
        // 执行sql
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        List<JSONObject> list = new LinkedList<>();
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("time", rs.getString(i, 0));
                jsonObject.put("value", rs.getString(i, 1));
                list.add(jsonObject);
            }
        }
        List<String> sortedTimes = list.stream()
                .sorted(Comparator.comparing(jsonObject -> jsonObject.getString("time")))
                .map(jsonObject -> jsonObject.getString("time"))
                .collect(Collectors.toList());

        List<String> sortedValues = list.stream()
                .sorted(Comparator.comparing(jsonObject -> jsonObject.getString("time")))
                .map(jsonObject -> jsonObject.getString("value"))
                .collect(Collectors.toList());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("timeList", sortedTimes);
        jsonObject.put("value1List", sortedValues);
        return jsonObject;
    }

    public List<JSONObject> queryData(String startTime, String endTime, String senid, String tableName, Boolean processData) {
        // 求过程数据
        if (processData) {
            String time = timeMap.get(tableName);
            String key = keyMap.get(tableName);
            String value = valueMap.get(tableName);
            String sql = "select " + time + ", " + value + " from " + tableName + " where " + key + " = '" + senid + "' and " +
                    time + " between  '" + startTime + "' and '" + endTime + "' order by " + time + " asc;";

            ResultSetInterface rs = null;
            try {
                rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
            if (rs != null) {
                List<JSONObject> list = new LinkedList<>();
                for (int i = 0; i < rs.getRowNum(); i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("time", rs.getString(i, 0));
                    jsonObject.put("value", rs.getString(i, 1));
                    list.add(jsonObject);
                }
                return list;
            }

            // 获取单点值
        } else {
            String time = timeMap.get(tableName);
            String key = keyMap.get(tableName);
            String value = valueMap.get(tableName);
            String sql = "select " + time + ", " + value + " from " + tableName + " where " + key + " = '" + senid + "' order by " + time + " desc limit 1;";


            ResultSetInterface rs = null;

            try {
                rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
            if (rs != null) {
                List<JSONObject> list = new LinkedList<>();
                for (int i = 0; i < rs.getRowNum(); i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("time", rs.getString(i, 0));
                    jsonObject.put("value", rs.getString(i, 1));
                    list.add(jsonObject);
                }
                return list;
            }
        }
        return null;
    }



    /**
     * 查询所有的站点信息
     *
     * @return
     */
    public List<JSONObject> queryAllStations() {
        List<JSONObject> list = new LinkedList<>();
        String sql = "select DISTINCT OBJID,OBJNAME FROM SD_DSP_TOPO_RESERVOIR;";
        // 执行sql
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("senid", rs.getString(i, 0));
                jsonObject.put("name", rs.getString(i, 1));
                list.add(jsonObject);
            }
            return list;
        }
        return null;
    }

    public Object getValue(String senid, String tableName) {
        String time = timeMap.get(tableName);
        String key = keyMap.get(tableName);
        String value = valueMap.get(tableName);
        String sql = "select " + time + ", " + value + " from " + tableName + " where " + key + " = '" + senid + "' order by " + time + " desc limit 1;";

        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                return rs.getString(0, 1);
            }
        }

        return 0;
    }

    /**
     * 获取坐标信息
     *
     * @param senid
     * @return
     */
    public Map<String, String> getLatAndLng(String senid) {
        String sql = "SELECT LONGITUDE,LATITUDE from PLANTPROFILE  WHERE CSID = '" + senid + "' limit 1";
        // 执行sql
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        Map<String, String> map = new HashMap<>();
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                map.put("LNG", rs.getString(i, 0));
                map.put("LAT", rs.getString(i, 1));
            }
        }
        return map;
    }

    public List<FirstGen> queryStationMap(JSONObject queryParams) {
        String sql = "SELECT distinct T1.OBJID gen1Id,T1.OBJNAME gen1Name,\n" +
                "T3.OBJID gen1NextId,T3.OBJNAME gen1NextName\n" +
                "from SD_DSP_TOPO_RIVER T1 Left Join SD_DSP_TOPO_RESERVOIR T3 \n" +
                "ON T1.OBJID = T3.PID WHERE T1.UPRID = '-99'";
        // 执行sql
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        List<FirstGen> list = new ArrayList<>();
        Map<String, List<Children>> map = new HashMap<>();
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String riverId = rs.getString(i, 1);
                String stationId = rs.getString(i, 2);
                String stationName = rs.getString(i, 3);
                if (map.containsKey(riverId)) {
                    map.get(riverId).add(new Children(stationId, stationName));
                } else {
                    LinkedList<Children> objects = new LinkedList<>();
                    FirstGen firstGen = new FirstGen();
                    firstGen.setId(rs.getString(i, 0));
                    firstGen.setName(riverId);
                    list.add(firstGen);
                    if (stationId != null) {
                        objects.add(new Children(stationId, stationName));
                        map.put(riverId, objects);
                    }
                }
            }
        }

        String sql2 = "SELECT distinct T1.UPRID PID,T1.OBJID gen1Id,T1.OBJNAME gen1Name,\n" +
                "T3.OBJID gen1NextId,T3.OBJNAME gen1NextName\n" +
                "from SD_DSP_TOPO_RIVER\" T1 Left Join SD_DSP_TOPO_RESERVOIR T3 \n" +
                "ON T1.OBJID = T3.PID WHERE T1.UPRID != '-99'";
        // 执行sql
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql2);
        } catch (Exception e) {
            System.out.println(e);
        }
        Map<String, List<Children>> map2 = new HashMap<>();
        Map<String, List<SecondGen>> map3 = new HashMap<>();
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String pid = rs.getString(i, 0);
                String id = rs.getString(i, 1);
                String name = rs.getString(i, 2);
                String stationId = rs.getString(i, 3);
                String stationName = rs.getString(i, 4);
                if (map3.containsKey(pid)) {
                    SecondGen secondGen = new SecondGen();
                    secondGen.setParentId(pid);
                    secondGen.setId(id);
                    secondGen.setName(name);
                    if (!map3.get(pid).contains(secondGen)) {
                        map3.get(pid).add(secondGen);
                    }
                } else {
                    LinkedList<SecondGen> objects = new LinkedList<>();
                    SecondGen secondGen = new SecondGen();
                    secondGen.setParentId(pid);
                    secondGen.setId(id);
                    secondGen.setName(name);
                    objects.add(secondGen);
                    map3.put(pid, objects);
                }
                if (map2.containsKey(name)) {
                    map2.get(name).add(new Children(stationId, stationName));
                } else {
                    LinkedList<Children> objects = new LinkedList<>();
                    if (stationId != null) {
                        objects.add(new Children(stationId, stationName));
                        map2.put(name, objects);
                    }
                }
            }
        }

        for (Map.Entry<String, List<SecondGen>> en : map3.entrySet()) {
            for (SecondGen secondGen : en.getValue()) {
                if (map2.get(secondGen.getName()) != null) {
                    secondGen.getChildren().addAll(map2.get(secondGen.getName()));
                }
            }
        }

        for (FirstGen firstGen : list) {
            if (map.get(firstGen.getName()) != null) {
                firstGen.getChildren().addAll(map.get(firstGen.getName()));
            }
            for (Map.Entry<String, List<SecondGen>> en : map3.entrySet()) {
                String key = en.getKey();
                String id = firstGen.getId();
                if (key.equals(id)) {
                    firstGen.getSecondGenList().addAll(en.getValue());
                }
            }
            if (map.get(firstGen.getName()) != null) {
                firstGen.getChildren().addAll(map.get(firstGen.getName()));
            }
        }
        // 去重
        for (FirstGen firstGen : list) {
            List<Children> children = firstGen.getChildren();
            List<Children> childrenList = new LinkedList<>(new HashSet<>(children));
            firstGen.setChildren(childrenList);
            List<SecondGen> secondGenList = firstGen.getSecondGenList();
            for (SecondGen child : secondGenList) {
                List<Children> childChildren = child.getChildren();
                List<Children> childrenLinkedList = new LinkedList<>(new HashSet<>(childChildren));
                child.setChildren(childrenLinkedList);
            }
        }
        return list;
    }

    public List<JSONObject> queryPlanList(String time, String flag, String type, String idList, List<String> timeList) {
        String sql = "SELECT\n" +
                "    a.objname,\n" +
                "    b.time,\n" +
                "    a.objid,\n" +
                "    MAX(b.edittime) AS edittime,\n" +
                "    b.v0, b.v1, b.v2, b.v3, b.v4, b.v5, b.v6, b.v7, b.v8, b.v9, b.v10,\n" +
                "    b.v11, b.v12, b.v13, b.v14, b.v15, b.v16, b.v17, b.v18, b.v19, b.v20,\n" +
                "    b.v21, b.v22, b.v23, b.v24, b.v25, b.v26, b.v27, b.v28, b.v29, b.v30,\n" +
                "    b.v31, b.v32, b.v33, b.v34, b.v35, b.v36, b.v37, b.v38, b.v39, b.v40,\n" +
                "    b.v41, b.v42, b.v43, b.v44, b.v45, b.v46, b.v47, b.v48, b.v49, b.v50,\n" +
                "    b.v51, b.v52, b.v53, b.v54, b.v55, b.v56, b.v57, b.v58, b.v59, b.v60,\n" +
                "    b.v61, b.v62, b.v63, b.v64, b.v65, b.v66, b.v67, b.v68, b.v69, b.v70,\n" +
                "    b.v71, b.v72, b.v73, b.v74, b.v75, b.v76, b.v77, b.v78, b.v79, b.v80,\n" +
                "    b.v81, b.v82, b.v83, b.v84, b.v85, b.v86, b.v87, b.v88, b.v89, b.v90,\n" +
                "    b.v91, b.v92, b.v93, b.v94, b.v95\n" +
                "FROM\n" +
                " SD_DSP_TOPO_RESERVOIR a\n" +
                "LEFT JOIN PLANLOAD b\n" +
                "ON\n" +
                "    a.objid || '" + type + "' = b.adid\n" +
                "WHERE\n" +
                "    b.flag = '" + flag + "' and TO_CHAR(b.time, 'YYYY-MM-DD') = '" + time + "' \n";
        if (idList != null) {
            sql += "AND a.objid IN (" + idList + ") ";
        }
        String endSql = "GROUP BY\n" +
                "    b.time,\n" +
                "    a.objid,\n" +
                "    b.v0, b.v1, b.v2, b.v3, b.v4, b.v5, b.v6, b.v7, b.v8, b.v9,\n" +
                "    b.v10, b.v11, b.v12, b.v13, b.v14, b.v15, b.v16, b.v17, b.v18, b.v19,\n" +
                "    b.v20, b.v21, b.v22, b.v23, b.v24, b.v25, b.v26, b.v27, b.v28, b.v29,\n" +
                "    b.v30, b.v31, b.v32, b.v33, b.v34, b.v35, b.v36, b.v37, b.v38, b.v39,\n" +
                "    b.v40, b.v41, b.v42, b.v43, b.v44, b.v45, b.v46, b.v47, b.v48, b.v49,\n" +
                "    b.v50, b.v51, b.v52, b.v53, b.v54, b.v55, b.v56, b.v57, b.v58, b.v59,\n" +
                "    b.v60, b.v61, b.v62, b.v63, b.v64, b.v65, b.v66, b.v67, b.v68, b.v69,\n" +
                "    b.v70, b.v71, b.v72, b.v73, b.v74, b.v75, b.v76, b.v77, b.v78, b.v79,\n" +
                "    b.v80, b.v81, b.v82, b.v83, b.v84, b.v85, b.v86, b.v87, b.v88, b.v89,\n" +
                "    b.v90, b.v91, b.v92, b.v93, b.v94, b.v95,a.objname;";
        sql += endSql;
        // 执行sql
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        List<JSONObject> list = new ArrayList<>();
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", rs.getString(i, 0));
                jsonObject.put("time", rs.getString(i, 1));
                jsonObject.put("senid", rs.getString(i, 2));
                jsonObject.put("edittime", rs.getString(i, 3));
                for (int j = 4; j < 100; j++) {
                    jsonObject.put(timeList.get(j - 4), rs.getString(i, j));
                }

                list.add(jsonObject);
            }
        }
        // 数据会有重复  这里 取出editTime最大的数据
        list = duplicateRemoval(list);
        return list;
    }

    public List<JSONObject> newQueryPlanList(String time, String flag, String type, String idList, List<String> timeList) {
        // 表1
        List<JSONObject> list1 = new ArrayList<JSONObject>();
        List<JSONObject> list2 = new ArrayList<JSONObject>();
        {
            String sql = "SELECT\n" +
                    "    b.adid,\n" +
                    "    b.time,\n" +
                    "    b.edittime,\n" +
                    "    b.v0, b.v1, b.v2, b.v3, b.v4, b.v5, b.v6, b.v7, b.v8, b.v9, b.v10,\n" +
                    "    b.v11, b.v12, b.v13, b.v14, b.v15, b.v16, b.v17, b.v18, b.v19, b.v20,\n" +
                    "    b.v21, b.v22, b.v23, b.v24, b.v25, b.v26, b.v27, b.v28, b.v29, b.v30,\n" +
                    "    b.v31, b.v32, b.v33, b.v34, b.v35, b.v36, b.v37, b.v38, b.v39, b.v40,\n" +
                    "    b.v41, b.v42, b.v43, b.v44, b.v45, b.v46, b.v47, b.v48, b.v49, b.v50,\n" +
                    "    b.v51, b.v52, b.v53, b.v54, b.v55, b.v56, b.v57, b.v58, b.v59, b.v60,\n" +
                    "    b.v61, b.v62, b.v63, b.v64, b.v65, b.v66, b.v67, b.v68, b.v69, b.v70,\n" +
                    "    b.v71, b.v72, b.v73, b.v74, b.v75, b.v76, b.v77, b.v78, b.v79, b.v80,\n" +
                    "    b.v81, b.v82, b.v83, b.v84, b.v85, b.v86, b.v87, b.v88, b.v89, b.v90,\n" +
                    "    b.v91, b.v92, b.v93, b.v94, b.v95\n" +
                    "FROM PLANLOAD b\n" +
                    "WHERE\n" +
                    "    b.flag = '" + flag + "' and TO_CHAR(b.time, 'YYYY-MM-DD') = '" + time + "' \n";


            // 执行sql
            ResultSetInterface rs = null;
            try {
                rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
            if (rs != null) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("senid", rs.getString(i, 0));
                    jsonObject.put("time", rs.getString(i, 1));
                    jsonObject.put("edittime", rs.getString(i, 2));
                    for (int j = 3; j < 99; j++) {
                        jsonObject.put(timeList.get(j - 3), rs.getString(i, j));
                    }
                    list1.add(jsonObject);
                }
            }

        }
        {
            String sql = "SELECT DISTINCT OBJNAME,OBJID FROM SD_DSP_TOPO_RESERVOIR ";
            if (idList != null) {
                sql += "WHERE objid IN (" + idList + ") ";
            }
            // 执行sql
            ResultSetInterface rs = null;
            try {
                rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
            if (rs != null) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("name", rs.getString(i, 0));
                    jsonObject.put("senid", rs.getString(i, 1));
                    list2.add(jsonObject);
                }
            }
        }

        return merge(list1, list2, type);
    }

    private List<JSONObject> merge(List<JSONObject> list1, List<JSONObject> list2, String type) {
        Map<String, JSONObject> list2Map = new HashMap<>();
        for (JSONObject jsonObject : list2) {
            String id = jsonObject.getString("senid");
            // 给id加上3700
            id = id + type;
            list2Map.put(id, jsonObject);
        }

        // 用于存储每个senid的最大edittime记录
        Map<String, JSONObject> maxEditTimeMap = new HashMap<>();
        for (JSONObject jsonObject : list1) {
            String senid = jsonObject.getString("senid");
            String edittime = jsonObject.getString("edittime");
            if (!maxEditTimeMap.containsKey(senid) || compareEditTime(maxEditTimeMap.get(senid), edittime) < 0) {
                maxEditTimeMap.put(senid, jsonObject);
            }
        }

        // 合并list1和list2的数据
        List<JSONObject> mergedList = new ArrayList<>();
        for (JSONObject jsonObject : list2Map.values()) {
            String senid = jsonObject.getString("senid") + type;
            // 给senid加上3700
            JSONObject list2Obj = maxEditTimeMap.get(senid);
            if (list2Obj != null) {
                // 合并list1和list2的数据
                mergedList.add(mergeObjects(jsonObject, list2Obj));
            }
        }
        return mergedList;
    }

    // 合并两个JSONObject
    private JSONObject mergeObjects(JSONObject obj1, JSONObject obj2) {
        JSONObject mergedObj = new JSONObject();
        mergedObj.putAll(obj1);
        for (String key : obj2.keySet()) {
            if (key.equals("senid")) {
                continue;
            }
            mergedObj.put(key, obj2.get(key));
        }
        return mergedObj;
    }

    private int compareEditTime(JSONObject obj1, String edittime2) {
        String edittime1 = obj1.getString("edittime");
        String time1 = edittime1.substring(0, 19);
        String time2 = edittime2.substring(0, 19);
        return time1.compareTo(time2);
    }


    private List<JSONObject> duplicateRemoval(List<JSONObject> jsonObjects) {

        Map<String, JSONObject> maxEditTimeMap = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (JSONObject jsonObject : jsonObjects) {
            try {
                String key = jsonObject.getString("senid") + "_" + jsonObject.getString("adid");
                Date currentDate = sdf.parse(jsonObject.getString("edittime").substring(0, 19));
                JSONObject currentMax = maxEditTimeMap.get(key);

                if (currentMax == null || currentDate.after(sdf.parse(currentMax.getString("edittime")))) {
                    maxEditTimeMap.put(key, jsonObject);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new ArrayList<>(maxEditTimeMap.values());
    }

    /**
     * 批量更新至redis
     *
     * @param keyMap
     * @param dateStr
     * @return
     */
    public Map<String, JSONObject> queryHourList(Map<String, String> keyMap, String dateStr) {
        Map<String, JSONObject> map = new HashMap<>();


        Object[] keyList = keyMap.keySet().toArray();
        ResultSetInterface rs = null;
        for (Object keyEnd : keyList) {
            String key = (String) keyEnd;

            String sql = "select SENID,TIME,V FROM WDS_DB.HOURDB WHERE SENID IN ( select OBJID || '"
                    + key +"' from WDS_DB.SD_DSP_TOPO_RESERVOIR)" +
                    " AND TIME <= '" + dateStr + "' AND TIME >= ADD_DAYS('" + dateStr +  "', -7)";

            try {
                rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
            JSONObject jsonObject = new JSONObject();
            if (rs != null) {
                if (rs.getRowNum() > 0) {
                    for (int i = 0; i < rs.getRowNum(); i++) {
                        String senid = rs.getString(i, 0);
                        String time = rs.getString(i, 1);
                        String datas = rs.getString(i, 2);
                        String value = "0.0";
                        if (datas != null) {
                            value = String.format("%.2f", Double.valueOf(datas));
                        }

                        if (jsonObject.containsKey(senid)) {
                            JSONObject valueJson = jsonObject.getJSONObject(senid);
                            JSONArray timeArray = valueJson.getJSONArray("timeList");
                            timeArray.add(time);
                            JSONArray valueArray = valueJson.getJSONArray("value1List");
                            valueArray.add(value);
                        } else {
                            JSONObject valueJson = new JSONObject();
                            jsonObject.put(senid, valueJson);
                            JSONArray timeArray = new JSONArray();
                            timeArray.add(time);
                            JSONArray valueArray = new JSONArray();
                            valueArray.add(value);
                            valueJson.put("timeList", timeArray);
                            valueJson.put("value1List", valueArray);
                        }
                        // 逻辑处理
                    }
                }
            }
            map.put(key, jsonObject);
        }
        dateStr = subtractOneYear(dateStr);
        for (Object keyEnd : keyList) {
            String key = (String) keyEnd;
            String sql = "select SENID,TIME,V FROM WDS_DB.HOURDB WHERE SENID IN ( select OBJID || '"
                    + key +"' from WDS_DB.SD_DSP_TOPO_RESERVOIR)" +
                    " AND TIME <= '" + dateStr + "' AND TIME >= ADD_DAYS('" + dateStr +  "', -7)";


            try {
                rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
            JSONObject jsonObject = map.get(key);
            if (rs != null) {
                if (rs.getRowNum() > 0) {
                    for (int i = 0; i < rs.getRowNum(); i++) {
                        String senid = rs.getString(i, 0);
                        String time = rs.getString(i, 1);
                        String datas = rs.getString(i, 2);
                        String value = "0.0";
                        if (datas != null) {
                            value = String.format("%.2f", Double.valueOf(datas));
                        }
                        if (jsonObject.containsKey(senid)) {
                            JSONObject valueJson = jsonObject.getJSONObject(senid);
                            if (valueJson.containsKey("value2List")) {
                                JSONArray valueArray = valueJson.getJSONArray("value2List");
                                valueArray.add(value);
                            } else {
                                JSONArray valueArray = new JSONArray();
                                valueJson.put("value2List", valueArray);
                                valueArray.add(value);
                            }
                        }
                        // 逻辑处理
                    }
                }
            }

        }
        return map;
    }
    public static String subtractOneYear(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate date = LocalDate.parse(dateString, formatter);
        LocalDate oneYearBefore = date.minus(Period.ofYears(1));
        return oneYearBefore.format(formatter);
    }
    /**
     * 多表联查  一次性查完所有数据
     *
     * @return
     */
    public List<JSONObject> queryMonitorData() {
        List<JSONObject> jsonList = new ArrayList<JSONObject>();
        String sql = "select DISTINCT\n" +
                "        T1.OBJID  ,\n" +
                "        T1.OBJNAME,\n" +
                "        T2.FACTV,\n" +
                "        T3.LONGITUDE,\n" +
                "        T3.LATITUDE\n" +
                "FROM\n" +
                " SD_DSP_TOPO_RESERVOIR T1\n" +
                "LEFT JOIN REAL T2\n" +
                "ON\n" +
                "        T1.OBJID\n" +
                "        || '0001' = T2.SENID\n" +
                "LEFT JOIN PLANTPROFILE T3\n" +
                "ON T1.OBJID = T3.CSID \n";
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("senid", rs.getString(i, 0));
                jsonObject.put("name", rs.getString(i, 1));
                jsonObject.put("waterLevel", rs.getString(i, 2));
                jsonObject.put("lng", rs.getString(i, 3));
                jsonObject.put("lat", rs.getString(i, 4));
                jsonObject.put("energyStorage", "1000");
                jsonList.add(jsonObject);
            }
        }
        return jsonList;
    }

    public Map<String, Object> getNorAndDeadWater(String senid) {
        Map<String, Object> map = new HashMap<>();
        String sql = "select NORMALLEVEL,DEADLEVEL from SD_DSP_RESERVOIR_DEF WHERE OBJID = '" + senid + "' LIMIT 1;";
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    map.put("normalLevel", rs.getString(i, 0));
                    map.put("deadLevel", rs.getString(i, 1));
                }
            } else {
                map.put("normalLevel", null);
                map.put("deadLevel", null);
            }

        }
        return map;
    }

    @SuppressWarnings("DuplicatedCode")
    public Object getAvgValue(String selectTime, String senid, String tableName) {
        String time = timeMap.get(tableName);
        String key = keyMap.get(tableName);
        String value = valueMap.get(tableName);

        String sql = "select AVG(" + value + ") from " + tableName + " where " +
                key + " = '" + senid + "' AND TO_CHAR(" + time + ",'yyyy') = '" + selectTime + "' limit 1;";

        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                return rs.getString(0, 0);
            }
        }

        return 0;
    }

    public Object getSumValue(String selectTime, String senid, String tableName) {
        String time = timeMap.get(tableName);
        String key = keyMap.get(tableName);
        String value = valueMap.get(tableName);

        String sql = "select SUM(" + value + ") from " + tableName + " where " +
                key + " = '" + senid + "' AND TO_CHAR(" + time + ",'yyyy') = '" + selectTime + "' limit 1;";
        System.out.println(sql);
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                return rs.getString(0, 0);
            }
        }

        return 0;
    }

    public R editPlan(String flag, List<JSONObject> data) {
        int reflectRows = 0;
        if (data.size() > 0) {
            StringBuilder sqlPrefix = new StringBuilder();
            sqlPrefix.append("INSERT INTO PLANLOAD (");
            Object[] cloumn = data.get(0).keySet().toArray();
            // 编辑头部数据
            for (int i = 0; i < cloumn.length; i++) {
                sqlPrefix.append(cloumn[i].toString().toUpperCase()).append(", ");
            }
            sqlPrefix.append("edittime,flag,hdsw");
            sqlPrefix.append(") VALUES(");
            String sql = "";

            for (int i = 0; i < data.size(); i++) {
                StringBuilder sqlValues = new StringBuilder();
                sql += sqlPrefix.toString();
                Object[] values = data.get(i).values().toArray();
                for (int j = 0; j < values.length; j++) {
                    sqlValues.append("'").append(values[j].toString()).append("'").append(", ");
                }
                sql += sqlValues.toString();
                sql += "sysdate(), '" + flag + "', 'system'); ";
            }
            sql += " commit;";

            try {
                reflectRows = DBOpUtil.exeHisDBExeWeb(sql);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
        if (reflectRows > 0) {
            return R.ok("编辑成功");
        } else {
            return R.fail("编辑失败");
        }
    }

    /**
     * 分流域展示站点数据
     *
     * @return
     */
    public Map<String, List<String>> queryBasinList() {
        Map<String, List<String>> basinMap = new LinkedHashMap<>();
        String sql = "SELECT distinct \n" +
                "T3.OBJID stationId ,T1.OBJID id, T1.UPRID upId\n" +
                "from SD_DSP_TOPO_RIVER T1 Left Join SD_DSP_TOPO_RESERVOIR T3 \n" +
                "ON T1.OBJID = T3.PID WHERE T3.OBJID IS NOT NULL;";
        List<JSONObject> jsonList = new ArrayList<>();
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("stationId", rs.getString(i, 0));
                jsonObject.put("id", rs.getString(i, 1));
                jsonObject.put("upId", rs.getString(i, 2));
                jsonList.add(jsonObject);
            }
        }
        // 1.湘江
        List<String> collect1 = jsonList.stream()
                .filter(o -> ("1".equals(o.getString("id")) || "1".equals(o.getString("upId"))))
                .map(o -> o.getString("stationId"))
                .collect(Collectors.toList());
        basinMap.put("1", collect1);
        // 2.沅水
        List<String> collect2 = jsonList.stream()
                .filter(o -> ("2".equals(o.getString("id")) || "2".equals(o.getString("upId"))))
                .map(o -> o.getString("stationId"))
                .collect(Collectors.toList());
        basinMap.put("2", collect2);
        // 3.澧水
        List<String> collect3 = jsonList.stream()
                .filter(o -> ("3".equals(o.getString("id")) || "3".equals(o.getString("upId"))))
                .map(o -> o.getString("stationId"))
                .collect(Collectors.toList());
        basinMap.put("3", collect3);
        // 4.资水
        List<String> collect4 = jsonList.stream()
                .filter(o -> ("4".equals(o.getString("id")) || "4".equals(o.getString("upId"))))
                .map(o -> o.getString("stationId"))
                .collect(Collectors.toList());
        basinMap.put("4", collect4);
        return basinMap;
    }

    public Map<String, JSONObject> coordinateSuspensionNumber() {
        String sql = "select DISTINCT\n" +
                "        A.OBJID   ,\n" +
                "        B.FACTV v1,\n" +
                "        C.FACTV v2,\n" +
                "        D.FACTV v3,\n" +
                "        E.FACTV v4\n" +
                "FROM\n" +
                " SD_DSP_TOPO_RESERVOIR A\n" +
                "LEFT JOIN REAL B\n" +
                "ON\n" +
                "        A.OBJID\n" +
                "        || '0001' = B.senid\n" +
                "LEFT JOIN REAL C\n" +
                "ON\n" +
                "        A.OBJID\n" +
                "        || '0002' = C.senid\n" +
                "LEFT JOIN REAL D\n" +
                "ON\n" +
                "        A.OBJID\n" +
                "        || '2000' = D.senid\n" +
                "LEFT JOIN REAL E\n" +
                "ON\n" +
                "        A.OBJID\n" +
                "        || '1020' = E.senid";
        Map<String, JSONObject> map = new HashMap<>();
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String key = rs.getString(i, 0);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("upWater", formatData(rs.getString(i, 1)));
                jsonObject.put("downWater", formatData(rs.getString(i, 2)));
                jsonObject.put("powerStationOutput", formatData(rs.getString(i, 3)));
                jsonObject.put("outboundFlow", formatData(rs.getString(i, 4)));
                map.put(key, jsonObject);
            }
        }
        return map;
    }

    // 求每个站点的全年降雨
    public Map<String, JSONObject> getStationAllRain() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(TimeConstant.DATE_YYYY);
        String dateStr = simpleDateFormat.format(new Date());
        String sql = "select OBJID, SUM(V) FROM (select DISTINCT\n" +
                "        *\n" +
                "FROM\n" +
                " SD_DSP_TOPO_RESERVOIR T1\n" +
                "LEFT JOIN DAYDB T2\n" +
                "ON\n" +
                "        T1.OBJID\n" +
                "        || '1810' = T2.SENID\n" +
                "        WHERE TO_CHAR(T2.TIME, 'YYYY') = '" + dateStr + "' ) ABC\n" +
                "group by OBJID";
        Map<String, JSONObject> map = new HashMap<>();
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String key = rs.getString(i, 0);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("rainValue", rs.getString(i, 1));
                map.put(key, jsonObject);
            }
        }
        return map;
    }

    // 批量
    public Object getSumBatchedValues(List<String> senidList, String tableName) {
        senidList = senidList.stream().map(o -> o + IdConstant.OUTPUT_POWER).collect(Collectors.toList());
        String idList = String.join(", ", senidList);
        String sql = "SELECT SUM(FACTV) FROM  "  + tableName + " WHERE SENID IN (" + idList + ")";
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                return rs.getString(0, 0);
            }
        }
        return 0;
    }



    public Map<String, JSONObject> getStationWaterData() {
        Map<String, JSONObject> map = new HashMap<String, JSONObject>();
        String sql = "select OBJID,NORMALLEVEL,DEADLEVEL from SD_DSP_RESERVOIR_DEF ;";
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    String senid = rs.getString(i, 0);
                    JSONObject json = new JSONObject();
                    json.put("normalLevel", rs.getString(i, 1));
                    json.put("deadLevel", rs.getString(i, 2));
                    map.put(senid, json);
                }
            }

        }
        return map;
    }


    public Map<String,String> getAllData(String ids){

        String sql="select a.senid,b.v from ( " +
                " select senId,max(Time) as time from DAYDB where senid in ("+ids+") group by senid " +
                ") a left join DAYDB b on a.senid=b.senid and a.time=b.time ";

        Map<String, String> map = new HashMap<>();
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String id = rs.getString(i, 0);

                Double v= rs.getDouble(i, 1);
                map.put(id,  NumberUtil.roundStr(v,2));
            }
        }
        return map;

    }


    /**
     * 查询制作人列表
     * @param type
     * @return
     */
    public List<String> queryMakerList(Integer type) {
        List<String> markerList = new ArrayList<String>();
        String sql = "select MAKER FROM SD_DSP_SCHEME_INFO WHERE FUNCTYPE = '" + type + "' GROUP BY MAKER";
        if (type == null) {
            sql = "select MAKER FROM SD_DSP_SCHEME_INFO WHERE FUNCTYPE IN (1,2,3) GROUP BY MAKER";
        }

        System.out.println(sql);
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            if (rs.getRowNum() > 0) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    String maker = rs.getString(i, 0);
                    markerList.add(maker);
                }
            }

        }
        return markerList;
    }

    public List<JSONObject> getActualData(String id, String startTime, String endTime) {
        String sql = "select DATE_FORMAT(TIME, '%Y-%m-%d %H:%i:%s') as TIME, V FROM HOURDB WHERE SENID = '" + id + "2000" + "' AND TIME BETWEEN '"
                + startTime + "' AND '" + endTime + "' order by TIME asc";
        List<JSONObject> result = new ArrayList<JSONObject>();

        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    JSONObject jsonObject = new JSONObject();
                    String time = rs.getString(i, 0);
                    String factv = rs.getString(i, 1);
                    jsonObject.put("time", time);
                    jsonObject.put("value", factv);
                    result.add(jsonObject);
                }
        }
        return result;
    }
    public static String formatData(String data) {
        if (data == null || data.equals("null")) {
            return "0";
        }
        return String.format("%.2f", Double.valueOf(data));
    }

    public Map<String, JSONObject> queryAreaData(List<Long> objList) {
        Map<String, JSONObject> result = new HashMap<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ID, VOLTAGELEVEL, ADMINISTRATIVE_AREA, RIVER_BASIN  FROM PARTITIONED_TABLE WHERE ID IN (");
        List<String> idList = objList.stream().map(o -> o.toString()).collect(Collectors.toList());
        if (idList.size() > 0) {
            sql.append(String.join(",", idList));
        } else {
            sql.append("0");
        }
        sql.append(")");
        ResultSetInterface rs = null;
        try {
            System.out.println(sql.toString());
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql.toString());
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            System.out.println("--------------1");
                System.out.println("--------------2");
                for (int i = 0; i < rs.getRowNum(); i++) {
                    JSONObject jsonObject = new JSONObject();
                    String id = rs.getString(i, 0);
                    String volt = rs.getString(i, 1);
                    String area = rs.getString(i, 2);
                    String basin = rs.getString(i, 3);
                    jsonObject.put("id", id);
                    jsonObject.put("电压等级", volt);
                    jsonObject.put("行政区域", area);
                    jsonObject.put("流域", basin);
                    result.put(id, jsonObject);
                }
        }
        return result;
    }

    public Map<String, String> getIdNameMap() {
        Map<String, String> result = new HashMap<>();
        String sql = "SELECT OBJID, OBJNAME FROM SD_DSP_TOPO_RESERVOIR";
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String id = rs.getString(i, 0);
                String name = rs.getString(i, 1);
                result.put(id, name);
            }
//            return result;
        }
        return result;
    }

    public List<String> getIdList() {
        List<String> result = new ArrayList<>();
        String sql = "SELECT OBJID FROM SD_DSP_TOPO_RESERVOIR";
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                String id = rs.getString(i, 0);
                result.add(id);
            }
        }
        return result;
    }

    public void savePlanOutput(List<JSONObject> saveList, String userName, String planTime) {
        StringBuffer sql = new StringBuffer();
        int reflectRows = 0;
        for (JSONObject jsonObject : saveList) {
            List<Object> values = jsonObject.getObject("value", List.class);
            sql.append("INSERT INTO PLANLOAD(");
            sql.append("ADID,TIME,EDITTIME,");
            for (int i = 0; i < 96; i++) {
                sql.append("V" + i + ",");
            }
            sql.append("HDSW,FLAG)VALUES(");
            sql.append(values.get(0) + "3705,");
            sql.append("'" +planTime + "','").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "',");
            for (int i = 1; i < values.size(); i++) {
                sql.append(values.get(i) + ",");
            }
            sql.append("'" + userName + "','0');");
        }

        try {
            System.out.println(sql.toString());
            reflectRows = DBOpUtil.exeHisDBExeWeb(sql.toString());
        } catch (Exception e) {
            System.out.println(e);
        }
    }



    public List<River> selectRiverTopoList() {
        List<River> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select OBJID,OBJNAME,UPRID from SD_DSP_TOPO_RIVER;");
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sb.toString());
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                River river = new River();
                String id = rs.getString(i, 0);
                String objName = rs.getString(i, 1);
                String upId = rs.getString(i, 2);
                river.setObjId(Long.valueOf(id));
                river.setObjName(objName);
                river.setUpRid(Long.valueOf(upId));
                result.add(river);
            }
        }
        return result;
    }

    public List<Reservoir> selectReservoirTopoList() {
        List<Reservoir> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select a.OBJID,a.OBJNAME,a.PID,a.downId,b.regulateType,b.company from SD_DSP_TOPO_RESERVOIR a left join  SD_DSP_RESERVOIR_DEF b on a.objid=b.objid;");
        ResultSetInterface rs = null;
        try {
            rs = DBOpUtil.exeHisDBSelSqlWeb(sb.toString());
        } catch (Exception e) {
            System.out.println(e);
        }
        if (rs != null) {
            for (int i = 0; i < rs.getRowNum(); i++) {
                Reservoir reservoir = new Reservoir();
                String id = rs.getString(i, 0);
                String objName = rs.getString(i, 1);
                String upId = rs.getString(i, 2);
                String downId = rs.getString(i, 3);

                reservoir.setObjId(Long.valueOf(id));
                reservoir.setObjName(objName);
                reservoir.setPid(Long.valueOf(upId));
                reservoir.setDownId(Long.valueOf(downId));
                reservoir.setCompany(rs.getString(i, 5));
                reservoir.setType(rs.getInt(i, 4));
                result.add(reservoir);
            }
        }
        return result;
    }


    public List<JSONObject> getAllReservoir(){

        List<JSONObject> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("select objid as ID,objname as NAME,riverid as BASIN_ID,SECTION_ID,COMPANY,LONGITUDE,LATITUDE,REGULATETYPE from SD_DSP_RESERVOIR_DEF where objid in(select objid from SD_DSP_OBJFUNC_DEF where funcid=11)");
        ResultSetInterface data = null;
        try {
            data = DBOpUtil.exeHisDBSelSqlWeb(sb.toString());
        } catch (Exception e) {
            System.out.println(e);
        }
        if (data != null) {
            for (int f = 0; f < data.getRowNum(); f++) {
                JSONObject jsonObject=new JSONObject();
                String id = data.getString(f, 0);
                String name = data.getString(f, 1);
                String basinId=data.getString(f, 2);
                String sectionId=data.getString(f, 3);
                String company=data.getString(f, 4);
                String longiTude=data.getString(f, 5);
                String latiTude=data.getString(f, 6);
                int regulateType=data.getInt(f, 7);

                jsonObject.put("id",id);                    //id
                jsonObject.put("name",name);                //名称
                jsonObject.put("basinId",basinId);                  //河流id
                jsonObject.put("sectionId",sectionId);                  //断面id
                jsonObject.put("longiTude",longiTude);                      //经度
                jsonObject.put("latiTude",latiTude);                      //纬度
                jsonObject.put("company",company);
                jsonObject.put("regulateType",regulateType);

                result.add(jsonObject);
            }
        }
        return result;
    }


    //获取流域信息表
    public static List<JSONObject> getRiverBasin() throws Exception{

        String sql="select objid,objName,uprid from SD_DSP_TOPO_RIVER ; ";

        ResultSetInterface data = DBOpUtil.exeHisDBSelSqlWeb(sql);

        List<JSONObject> basinList=new ArrayList<>();

        for (int f = 0; f < data.getRowNum(); f++) {

            JSONObject jsonObject = new JSONObject();

            String id = data.getString(f, 0);
            String name = data.getString(f, 1);
            String pId=data.getString(f, 2);

            jsonObject.put("id",id);                    //id
            jsonObject.put("name",name);                //名称
            jsonObject.put("pId",pId);                  //河流id
            basinList.add(jsonObject);

        }


        return basinList;
    }


    //电站拓扑关系
    public static List<JSONObject> getAllReservoirTopo() throws Exception{

        String sql="select a.objId,a.objName,b.objName as downName,a.downId  from SD_DSP_TOPO_RESERVOIR a left join SD_DSP_TOPO_RESERVOIR b on a.downId=b.objid ";

        ResultSetInterface data = DBOpUtil.exeHisDBSelSqlWeb(sql);

        List<JSONObject> reservoirTopoList=new ArrayList<>();

        for (int f = 0; f < data.getRowNum(); f++) {

            JSONObject jsonObject=new JSONObject();

            String objId = data.getString(f, 0);
            String objName = data.getString(f, 1);
            String downName = data.getString(f, 2);
            String downId= data.getString(f,3);

            jsonObject.put("objId",objId);
            jsonObject.put("objName",objName);
            jsonObject.put("downName",downName);
            jsonObject.put("downId",downId);

            reservoirTopoList.add(jsonObject);

        }

        return reservoirTopoList;
    }

    //获取断面信息表
    public static List<JSONObject> getSectionList(String ids) throws Exception{

        String sql="select ID,NAME,PID from  WOU_DEF_SECTION ";

        if(StrUtil.isNotBlank(ids)){
            sql+=" where ID in ("+ids+")";
        }

        ResultSetInterface data = DBOpUtil.exeHisDBSelSqlWeb(sql);

        List<JSONObject> sectList=new ArrayList<>();

        for (int f = 0; f < data.getRowNum(); f++) {

            JSONObject jsonObject = new JSONObject();

            String id = data.getString(f, 0);
            String name = data.getString(f, 1);
            String pId=data.getString(f, 2);


            jsonObject.put("id",id);                    //id
            jsonObject.put("name",name);                //名称
            jsonObject.put("pId",pId);                  //断面id

            sectList.add(jsonObject);

        }


        return sectList;
    }
}
