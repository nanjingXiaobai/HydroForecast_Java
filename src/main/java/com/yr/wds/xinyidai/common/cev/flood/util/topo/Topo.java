package com.yr.wds.xinyidai.common.cev.flood.util.topo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author xcw
 * @version 1.0
 * @date 2025/4/25 11:07
 * @description: TODO
 */
@Data
@Accessors(chain = true)
public class Topo {
    Long objId;
    String objName;
    List<River> children = new ArrayList<>();
    List<Reservoir> reservoirs = new ArrayList<>();
}
