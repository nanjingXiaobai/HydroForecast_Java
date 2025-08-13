package com.yr.wds.xinyidai.common.cev.flood.enums;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

/**
 * @author: WDP
 * @create: 2025-05-08 14:35
 **/
public enum NumberSymbol {

   RSVR_GENFLOWPRO("rsvrGenFlowPro","1100"),      //流量
    RSVR_OUTPUTPRO("rsvrOutPutPro","2000"),       //出力
    RSVR_BLEVEL("rsvrBLevel","0001");               //水位

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private String name;
    private String id;



    NumberSymbol(String name,String id) {
        this.name = name;
        this.id=id;
    }

    //根据id获取name
    public static String getName(String id) {
        for (NumberSymbol value : NumberSymbol.values()) {
            if (StrUtil.equals(value.id, id)) {
                return value.name;
            }
        }
        return null;
    }


    //获取所有id
    public static List<String> getAllIds(){
        List<String> ids=new ArrayList<>();
        for (NumberSymbol value : NumberSymbol.values()) {
            ids.add(value.id);
        }
        return ids;
    }


    public static Map<String,String> getIdName(){

        Map<String,String> map=new HashMap<>();

        for (NumberSymbol value : NumberSymbol.values()) {
            map.put(value.id,value.name);
        }
        return map;

    }

}
