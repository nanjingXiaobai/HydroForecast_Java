package com.yr.wds.xinyidai.common.cev.flood.enums;

public enum SequenceType {

    //0:不考虑降雨,1:倾向值,2:上限值,3:下限值
    SEQUENCE_TYPE0("不考虑降雨",0),
    SEQUENCE_TYPE1("倾向值",1),
    SEQUENCE_TYPE2("上限值",2),
    SEQUENCE_TYPE3("下限值",3);

    private String name;
    private Integer index;



    SequenceType(String name, Integer index) {
        this.name = name;
        this.index = index;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }


    public static String getName(int index) {
        for (SequenceType value : SequenceType.values()) {
            if (value.index == index) {
                return value.name;
            }
        }
        return null;
    }

}
