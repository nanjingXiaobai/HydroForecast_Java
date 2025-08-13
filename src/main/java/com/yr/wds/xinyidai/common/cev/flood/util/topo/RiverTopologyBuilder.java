package com.yr.wds.xinyidai.common.cev.flood.util.topo;
import java.util.*;
import java.util.stream.Collectors;
/**
 * @author xcw
 * @version 1.0
 * @date 2025/4/24 17:12
 * @description: TODO
 */


public class RiverTopologyBuilder {


    public List<Topo> buildTopoStructure(List<River> rivers, List<Reservoir> reservoirs) {
        // 步骤1: 将所有河流按ID建立索引
        Map<Long, River> riverMap = rivers.stream()
                .collect(Collectors.toMap(River::getObjId, r -> r));

        // 步骤2: 构建河流树
        List<River> rootRivers = new ArrayList<>();
        for (River river : rivers) {
            if (river.getUpRid() == -99) {
                rootRivers.add(river);
            } else {
                River parent = riverMap.get(river.getUpRid());
                if (parent != null) {
                    parent.getChildrens().add(river);
                }
            }
        }
        // 构建二层树
        List<Topo> result = new ArrayList<>();
        for (River river : rootRivers) {
            if (river.getUpRid() == -99) {
                Long objId = river.getObjId();
                for (River r : rivers) {
                    if (r.getUpRid() == objId) {
                        Topo topo = new Topo();
                        topo.setObjName(r.getObjName());
                        topo.setObjId(r.getObjId());
                        topo.setChildren(r.getChildrens());
                        result.add(topo);
                    }
                }
                break;
            }
        }

        System.out.println(countNodes(rootRivers.get(0)));
        // 将二层树节点截取成两层树挂在子节点上
        for (Topo topo : result) {
            List<River> children = topo.getChildren();
            if (children != null) {
                for (River river : children) {
                    Map<Long, String> leafAndChildrenIds = traverseAndCollectChildren(river.getChildrens());
                    leafAndChildrenIds.put(river.getObjId(), river.getObjName());
                    river.setChildren(new LinkedList<>());
                    river.setChildrens(null);
                    river.setMap(leafAndChildrenIds);
                }
            }
        }
//        Map<Long, Reservoir> reservoirMap = reservoirs.stream()
//                .collect(Collectors.toMap(r -> r.objId, r -> r));

        for (Topo topo : result) {
            List<Reservoir> collect1 = new ArrayList<>();
            for (Reservoir reservoir : reservoirs) {
                if (topo.getObjId().equals(reservoir.pid)) {
                    reservoir.setDepth(0);
                    collect1.add(reservoir);
                }
            }
            topo.setReservoirs(collect1);
            List<River> children = topo.getChildren();
            if (children != null) {
                for (River river : children) {
                    List<Reservoir> collect2 = new ArrayList<>();
                    for (Reservoir reservoir : reservoirs) {
                        if (river.getMap().containsKey(reservoir.pid)) {
                            reservoir.setDepth(1);
                            collect2.add(reservoir);
                        }
                    }
                    river.setReservoirList(collect2);
                    river.getChildren().addAll(collect1);
                    river.getChildren().addAll(collect2);
                }
            }
        }
        return result;
    }

    /**
     * 递归计算树的节点数量
     * @param root 树的根节点
     * @return 节点总数
     */
    public static int countNodes(River root) {
        if (root == null) {
            return 0;
        }
        int count = 1; // 当前节点计数
        if (root.getChildrens() != null) {
            for (River child : root.getChildrens()) {
                count += countNodes(child); // 递归统计子节点数量
            }
        }
        return count;
    }

    /**
     * 遍历方法，将所有子节点ID添加到父节点的map中
     * @param children 子节点列表
     * @return 包含所有子节点ID的Set集合
     */
    public Map<Long, String> traverseAndCollectChildren(List<River> children) {
        Map<Long, String> allChildIds = new HashMap<>();


        // 遍历直接子节点
        for (River child : children) {
            // 添加子节点ID到父节点的map中
            allChildIds.put(child.getObjId(), child.getObjName());

            // 递归处理子节点的后代
            if (!child.getChildrens().isEmpty()) {
                Map<Long, String> stringMap = traverseAndCollectChildren(child.getChildrens());
                allChildIds.putAll(stringMap);

            }
        }

        return allChildIds;
    }

    public List<Reservoir> getAllReservoirs(List<Topo> topologyTree) {
        List<Reservoir> list = new LinkedList<>();
        for (Topo topo : topologyTree) {
            list.addAll(topo.getReservoirs());
            if (topo.getChildren() != null) {
                for (River river : topo.getChildren()) {
                    list.addAll(river.getReservoirList());
                }
            }
        }
        return list;
    }
}
