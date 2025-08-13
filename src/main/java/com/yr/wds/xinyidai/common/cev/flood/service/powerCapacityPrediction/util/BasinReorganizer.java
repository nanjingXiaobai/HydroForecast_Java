package com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

public class BasinReorganizer {

    // 主处理方法
    public JSONArray reorganizeBasinStructure(JSONArray jsonArray) {
        Map<String, JSONObject> idMap = new HashMap<>();
        List<JSONObject> nodesToProcess = new ArrayList<>();

        // 构建ID映射表并收集待处理节点
        traverseTree(jsonArray, idMap, nodesToProcess);

        // 按层级降序排序（确保先处理子节点）
        sortNodesByLevelDesc(nodesToProcess);

        // 处理每个待处理节点
        processNodes(nodesToProcess, idMap);

        return jsonArray;
    }

    // 递归遍历树结构
    private void traverseTree(JSONArray nodes, Map<String, JSONObject> idMap, List<JSONObject> nodesToProcess) {
        for (int i = 0; i < nodes.size();  i++) {
            JSONObject node = nodes.getJSONObject(i);
            idMap.put(node.getString("id"),  node);

            if (node.getIntValue("level")  > 2) {
                nodesToProcess.add(node);
            }

            JSONArray children = node.getJSONArray("children");
            if (children != null && !children.isEmpty())  {
                traverseTree(children, idMap, nodesToProcess);
            }
        }
    }

    // 节点排序（降序）
    private void sortNodesByLevelDesc(List<JSONObject> nodes) {
        Collections.sort(nodes,  new Comparator<JSONObject>() {
            public int compare(JSONObject o1, JSONObject o2) {
                return o2.getIntValue("level")  - o1.getIntValue("level");
            }
        });
    }

    // 处理节点逻辑
    private void processNodes(List<JSONObject> nodes, Map<String, JSONObject> idMap) {
        for (JSONObject node : nodes) {
            JSONObject level2Ancestor = findLevel2Ancestor(node, idMap);
            if (level2Ancestor != null) {
                mergeChildren(node, level2Ancestor);
                removeFromParent(node, idMap);
            }
        }
    }

    // 查找level=2祖先节点
    private JSONObject findLevel2Ancestor(JSONObject node, Map<String, JSONObject> idMap) {
        String currentPid = node.getString("pId");
        while (currentPid != null) {
            JSONObject parent = idMap.get(currentPid);
            if (parent == null) break;
            if (parent.getIntValue("level")  == 2) {
                return parent;
            }
            currentPid = parent.getString("pId");
        }
        return null;
    }

    // 合并子节点到目标节点
    private void mergeChildren(JSONObject source, JSONObject target) {
        JSONArray sourceChildren = source.getJSONArray("children");
        if (sourceChildren == null || sourceChildren.isEmpty())  return;

        JSONArray targetChildren = target.getJSONArray("children");
        if (targetChildren == null) {
            targetChildren = new JSONArray();
            target.put("children",  targetChildren);
        }
        targetChildren.addAll(sourceChildren);
    }

    // 从父节点移除当前节点
    private void removeFromParent(JSONObject node, Map<String, JSONObject> idMap) {
        JSONObject parent = idMap.get(node.getString("pId"));
        if (parent == null) return;

        JSONArray siblings = parent.getJSONArray("children");
        if (siblings == null) return;

        Iterator<Object> iterator = siblings.iterator();
        while (iterator.hasNext())  {
            JSONObject child = (JSONObject) iterator.next();
            if (child.getString("id").equals(node.getString("id")))  {
                iterator.remove();
                break;
            }
        }
    }
}
