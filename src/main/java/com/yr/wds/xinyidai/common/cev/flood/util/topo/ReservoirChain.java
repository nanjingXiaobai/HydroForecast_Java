package com.yr.wds.xinyidai.common.cev.flood.util.topo;

import lombok.Data;

/**
 * @author xcw
 * @version 1.0
 * @date 2025/3/22 11:45
 * @description: TODO
 */
@Data
public class ReservoirChain {
    Reservoir node;
    ReservoirChain next;

    public ReservoirChain(Reservoir node) {
        this.node = node;
    }
}
