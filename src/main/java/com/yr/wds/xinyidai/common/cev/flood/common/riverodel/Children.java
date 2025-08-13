package com.yr.wds.xinyidai.common.cev.flood.common.riverodel;

import lombok.Data;

import java.util.Objects;

/**
 * @author xcw
 * @version 1.0
 * @date 2024/7/27 14:21
 * @description: 树
 */
@Data
public class Children {
    private String id;
    private String name;

    public Children(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Children children = (Children) o;
        return Objects.equals(id, children.id) && Objects.equals(name, children.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
