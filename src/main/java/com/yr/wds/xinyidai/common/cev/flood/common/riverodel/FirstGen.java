package com.yr.wds.xinyidai.common.cev.flood.common.riverodel;


import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * @author xcw
 * @version 1.0
 * @date 2024/7/27 14:19
 * @description: 河道结构树-根节点1
 */
@Data
public class FirstGen {
    private String id;
    private String name;
    private List<SecondGen> secondGenList = new LinkedList<>();
    private List<Children> children = new LinkedList<>();
}
