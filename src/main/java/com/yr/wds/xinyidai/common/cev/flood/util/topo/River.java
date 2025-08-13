package com.yr.wds.xinyidai.common.cev.flood.util.topo;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xcw
 * @version 1.0
 * @date 2025/3/22 11:44
 * @description: TODO
 */
@Data
public class River {
    private Long objId;
    private String objName;
    private Long upRid;
    private Map<Long, String> map = new HashMap<>();
    private List<Reservoir> reservoirList = new ArrayList<>();
    private List<River> childrens = new ArrayList<>();
    private List<Reservoir> children = new ArrayList<>();

    // Getters and Setters
    public Long getObjId() { return objId; }
    public void setObjId(Long objId) { this.objId = objId; }
    public String getObjName() { return objName; }
    public void setObjName(String objName) { this.objName = objName; }
    public Long getUpRid() { return upRid; }
    public void setUpRid(Long upRid) { this.upRid = upRid; }
    public Map<Long, String> getMap() { return map; }
    public List<Reservoir> getReservoirList() { return reservoirList; }

    public River() {
    }
}
