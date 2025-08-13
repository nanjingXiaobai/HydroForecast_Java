package com.yr.wds.xinyidai.common.cev.flood.enums;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedList;
import java.util.List;

public enum LongHTType {

    //0:不考虑降雨,1:倾向值,2:上限值,3:下限值
    WATER_LEVEL("实测数据","v0"),
    HISTORT_TOP("历史上限值","v1"),
    HISTORT_AVG("历史平均值","v2"),
    HISTORT_LOWER("历史下限值","v3"),
    MODEL_NERVE("神经网络模型","v4"),
    MODEL_THRESHOLD("门限回归模型","v5"),
    MODEL_VECTOR("支持向量机模型","v6"),
//    PROBABILITY_TOP("概率预报上限值","v7"),
//    PROBABILITY_LOWER("概率预报下限值","v8"),
    PROBABILITY_COMINATION("组合模型预报值","v9"),
    Apogee("距平","v10"),
    FREQUENCY("频率","v11");


    private String name;
    private String prop;


    LongHTType(String name,String prop) {
        this.name = name;
        this.prop=prop;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProp() {
        return prop;
    }

    public void setProp(String prop) {
        this.prop = prop;
    }



    //根据name获取prop
    public static String getProp(String name) {
        for (LongHTType value : LongHTType.values()) {
            if (StrUtil.equals(value.name, name)) {
                return value.prop;
            }
        }
        return null;
    }



    //获取所有prop和name
    public static List<JSONObject> getTableHeader(){

        List<JSONObject> tableHeader=new LinkedList<>();

        JSONObject js1=new JSONObject();
        js1.put("prop","time");
        js1.put("label","时间");

        tableHeader.add(js1);

        for (LongHTType value : LongHTType.values()) {
            JSONObject js=new JSONObject();
            js.put("prop",value.prop);
            js.put("label",value.name);
            tableHeader.add(js);
        }

        return tableHeader;

    }


}
