package com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.listener.PowerStationListener;

import java.util.*;

public class BasinProcessor {

    public static void processBasin(JSONArray nodes) {
        if (nodes == null || nodes.isEmpty())  return;

        // 倒序遍历避免删除导致的索引错位
        for (int i = nodes.size()  - 1; i >= 0; i--) {
            JSONObject node = nodes.getJSONObject(i);
            if (!node.containsKey("level"))  continue; // 电站节点保留

            // 递归处理子节点
            JSONArray children = node.getJSONArray("children");
            if (children != null) {
                processBasin(children); // 深度优先处理子树
            }

            // 判断当前节点是否需要删除
            if (shouldRemove(node)) {
                nodes.remove(i);
            }
        }
    }

    public static void processBasinBySectionTree(JSONArray nodes) {
        if (nodes == null || nodes.isEmpty())  return;

        // 倒序遍历避免删除导致的索引错位
        for (int i = nodes.size()  - 1; i >= 0; i--) {
            JSONObject node = nodes.getJSONObject(i);
            if (node.containsKey("sectionId"))  continue; // 电站节点保留

            // 递归处理子节点
            JSONArray children = node.getJSONArray("children");
            if (children != null) {
                processBasinBySectionTree(children); // 深度优先处理子树
            }

            // 判断当前节点是否需要删除
            if (shouldRemove(node)) {
                nodes.remove(i);
            }
        }
    }

    private static boolean shouldRemove(JSONObject node) {
        JSONArray children = node.getJSONArray("children");
        // 子节点为空 且 无电站时删除
        return (children == null || children.isEmpty())  && !hasPowerStation(node);
    }

    private static boolean hasPowerStation(JSONObject node) {
        JSONArray children = node.getJSONArray("children");
        if (children == null) return false;
        for (int i = 0; i < children.size();  i++) {
            JSONObject child = children.getJSONObject(i);
            if (!child.containsKey("level"))  {
                return true; // 发现电站节点
            }
            if(!child.containsKey("sectionId")){
                return true; //发现断面节点
            }
        }
        return false;
    }


    public static List<JSONObject> sortPowerStations(List<JSONObject> stations,
                                                     Map<String, String> downstreamMap) {
        // 构建ID->电站映射
        Map<String, JSONObject> idMap = new HashMap<>();
        stations.forEach(s  -> idMap.put(s.getString("id"),  s));

        // 标记非根节点（有上游指向的节点）
        Set<String> nonRootIds = new HashSet<>();
        for (JSONObject station : stations) {
            String downstreamId = downstreamMap.get(station.getString("id"));
            if (downstreamId != null && idMap.containsKey(downstreamId))  {
                nonRootIds.add(downstreamId);
            }
        }

        // 结果集与访问标记
        List<JSONObject> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // 遍历处理根节点
        stations.forEach(station  -> {
            if (!visited.contains(station.getString("id"))  && !nonRootIds.contains(station.getString("id")))  {
                dfs(station, idMap, downstreamMap, result, visited);
            }
        });

        // 兜底处理独立节点
        stations.forEach(station  -> {
            if (!visited.contains(station.getString("id")))  {
                result.add(station);
            }
        });

        return result;
    }

    private static void dfs(JSONObject current, Map<String, JSONObject> idMap,
                            Map<String, String> downstreamMap, List<JSONObject> result,
                            Set<String> visited) {
        if (current == null || visited.contains(current.getString("id")))  return;

        visited.add(current.getString("id"));
        result.add(current);

        String downstreamId = downstreamMap.get(current.getString("id"));
        if (downstreamId != null) {
            JSONObject downstream = idMap.get(downstreamId);
            dfs(downstream, idMap, downstreamMap, result, visited);
        }
    }


}
