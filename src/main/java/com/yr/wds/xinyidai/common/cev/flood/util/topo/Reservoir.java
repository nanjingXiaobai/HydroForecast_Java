package com.yr.wds.xinyidai.common.cev.flood.util.topo;

import lombok.Data;

/**
 * @author xcw
 * @version 1.0
 * @date 2025/3/22 11:45
 * @description: TODO
 */
@Data
public class Reservoir {
    Long objId;
    String objName;
    Long pid;       // 所属河流ID
    Long downId;    // 下游电站ID
    int depth;    // 水库深度

    Long basinId; //最外层的流域id
    Long sectionId; //最外层断面id
    int type;       //调节类型
    String company; //发电集团

    public Reservoir() {

    }
}
