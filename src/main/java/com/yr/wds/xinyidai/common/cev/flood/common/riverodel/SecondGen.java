package com.yr.wds.xinyidai.common.cev.flood.common.riverodel;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * @author xcw
 * @version 1.0
 * @date 2024/7/27 14:20
 * @description: 河道结构树-根节点2
 */
@Data
public class SecondGen {
    private String parentId;
    private String id;
    private String name;
    private List<Children> children = new LinkedList<>();
}
