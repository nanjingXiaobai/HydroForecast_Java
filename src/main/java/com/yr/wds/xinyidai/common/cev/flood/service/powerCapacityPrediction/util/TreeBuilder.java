package com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction.util;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.service.commonsql.CommonSql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TreeBuilder {

    @Autowired
    private CommonSql commonSql;

    @Value("${firstLevelWatershed}")
    private String firstLevelWatershed;

    private static List<String> TOP_BASIN_IDS;

    @PostConstruct
    public void init() {
        TOP_BASIN_IDS = Arrays.asList(firstLevelWatershed.split(","));
    }

    public Map<String, Object> buildTrees(List<JSONObject> basins,
                                          List<JSONObject> sections,
                                          List<JSONObject> plants) throws Exception {


        // 构建流域树
        Map<String, JSONObject> basinMap = new HashMap<>();
        Map<String, List<JSONObject>> basinChildrenMap = new HashMap<>();

        // 初始化流域数据结构
        for (JSONObject basin : basins) {
            String id = basin.getString("id");
            basin.put("children", new JSONArray());
            basin.put("level", 0);
            basinMap.put(id, basin);

            String pId = basin.getString("pId") != null ? basin.getString("pId") : "null";
            basinChildrenMap.computeIfAbsent(pId, k -> new ArrayList<>())
                    .add(basin);
        }

        // 构建流域层级
        JSONArray basinTree = new JSONArray();
        Queue<JSONObject> queue = new LinkedList<>();
        for (String topId : TOP_BASIN_IDS) {
            JSONObject root = basinMap.get(topId);
            if (root != null) {
                root.put("level", 1);
                queue.add(root);
                basinTree.add(root);
            }
        }

        // 广度优先遍历设置层级
        while (!queue.isEmpty()) {
            JSONObject current = queue.poll();
            List<JSONObject> children = basinChildrenMap.get(current.getString("id"));
            if (children != null) {
                for (JSONObject child : children) {
                    child.put("level", current.getInteger("level") + 1);
                    current.getJSONArray("children").add(child);
                    queue.add(child);
                }
            }
        }


        // 递归构建断面树
        List<JSONObject> nodes = JSONArray.parseArray(JSON.toJSONString(sections), JSONObject.class);
        List<JSONObject> sectionTree = buildSectionTree(nodes);

        //获取电站拓扑关系
        List<JSONObject> reservoirTopoList = commonSql.getAllReservoirTopo();
        Map<String,String> downstreamMap=reservoirTopoList.stream().collect(Collectors.toMap(x->x.getString("objId"),y->y.getString("downId")));

        // 关联电站数据
        linkPlants(plants, basinMap, sectionTree,downstreamMap);

        BasinReorganizer basinReorganizer = new BasinReorganizer();

        //将二级以下电站  放入 二级children
//        basinTree = basinReorganizer.reorganizeBasinStructure(basinTree);

        //删除空数据
        if (CollUtil.isNotEmpty(basinTree)) {
            BasinProcessor.processBasin(basinTree);
        }
//        if (CollUtil.isNotEmpty(sectionTree)) {
//            BasinProcessor.processBasinBySectionTree(sectionTree);
//        }

        //将流域下的电站排序
        // 组装结果
        Map<String, Object> result = new HashMap<>();
        result.put("basinTree", basinTree);
        result.put("sectionTree", sectionTree);
        return result;
    }



    private List<JSONObject> buildSectionTree(List<JSONObject> nodes) {
        Map<String, JSONObject> nodeMap = new HashMap<>();
        List<JSONObject> roots = new ArrayList<>();

        // 创建节点映射
        nodes.forEach(node -> {
            nodeMap.put(node.getString("id"), node);
            node.put("children", new ArrayList<>());
            node.put("level", 1);  // 默认level为1
        });

        // 建立父子关系并设置level
        nodes.forEach(node -> {
            if (StrUtil.isNotBlank(node.getString("pId"))) {
                Arrays.stream(node.getString("pId").split(","))
                        .map(String::trim)
                        .filter(nodeMap::containsKey)
                        .forEach(pid -> {
                            JSONObject parent = nodeMap.get(pid);
                            parent.getJSONArray("children").add(node);

                            // 设置子节点的level值为父节点level + 1
                            node.put("level", parent.getIntValue("level") + 1);
                        });
            } else {
                roots.add(node);
            }
        });

        return roots;
    }





    private void linkPlants(List<JSONObject> plants,
                            Map<String, JSONObject> basinMap,
                            List<JSONObject> sectionTree,
                            Map<String,String> downstreamMap) {
        for (JSONObject plant : plants) {
            // 关联流域
            JSONObject basin = basinMap.get(plant.getString("basinId"));
            if (basin != null) {
                basin.getJSONArray("children").add(plant);
            }
        }

        //将流域下电站排序。按照上下游排序
        for(String str : basinMap.keySet()){
            JSONObject jsonObject=basinMap.get(str);
           List<JSONObject> list= jsonObject.getJSONArray("children").toJavaList(JSONObject.class);

            if(CollUtil.isNotEmpty(list)){
                list= BasinProcessor.sortPowerStations(list,downstreamMap);
                jsonObject.put("children",list);
                basinMap.put(str,jsonObject);
            }

        }


        underTheCrossSection(plants, sectionTree,downstreamMap);
    }


    //将电站放在断面下
    private void underTheCrossSection(List<JSONObject> plants, List<JSONObject> sectionTree,Map<String,String> downstreamMap) {

        Map<String, List<JSONObject>> sectPlantMap = new HashMap<>();
        //提取断面id，组成map
        for (JSONObject plant : plants) {
            String sectionId = plant.getString("sectionId");
            if (StrUtil.isBlank(sectionId)) continue;
            List<String> sectionIds = Arrays.asList(sectionId.split(","));
            for (String secId : sectionIds) {
                secId=StrUtil.trim(secId);
                if (null == sectPlantMap.get(secId)) {
                    List<JSONObject> list = new ArrayList<>();
                    list.add(plant);
                    sectPlantMap.put(secId, list);
                } else {
                    List<JSONObject> list = sectPlantMap.get(secId);
                    boolean flag = true;
                    for (int i = 0; i < list.size(); i++) {
                        JSONObject js = list.get(i);
                        if (StrUtil.equals(js.getString("id") , plant.getString("id"))) {
                            flag = false;
                        }
                    }
                    if (flag) {
                        list.add(plant);
                    }

                }
            }
        }

        //将所有断面下的电站。按照上下游排序
        for(String str : sectPlantMap.keySet()){
            List<JSONObject> list=sectPlantMap.get(str);
            if(CollUtil.isNotEmpty(list)){
                list= BasinProcessor.sortPowerStations(list,downstreamMap);
                sectPlantMap.put(str,list);
            }

        }


        setTopBasinIds(sectPlantMap, sectionTree);

    }


    //将电站放在断面下
    private void setTopBasinIds(Map<String, List<JSONObject>> sectPlantMap, List<JSONObject> sectionTree) {
        if (CollUtil.isNotEmpty(sectionTree)) {
            for (JSONObject js : sectionTree) {
                String id = js.getString("id");

                // 获取sectPlantMap中对应id的list
                List<JSONObject> list = sectPlantMap.get(id);

                // 如果没有children字段，跳过
                if (!js.containsKey("children")) continue;

                // 获取children字段，并且直接将其转换为List<JSONObject>
                List<JSONObject> childrenList = js.getJSONArray("children").toJavaList(JSONObject.class);

                // 如果sectPlantMap中有数据，合并到childrenList
                if (CollUtil.isNotEmpty(list)) {
                    childrenList.addAll(list);
                    js.put("children", childrenList);
                }

                // 递归调用，处理子节点
                setTopBasinIds(sectPlantMap, childrenList);
            }
        }
    }


}
