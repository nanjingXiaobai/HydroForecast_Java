package com.yr.wds.xinyidai.common.cev.flood.common.enums;

public enum ParameterEnum {
    VALUE1(1, "Name1"),
    VALUE2(2, "Name2");
    // 参数

    private int id;
    private String name;

    ParameterEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
