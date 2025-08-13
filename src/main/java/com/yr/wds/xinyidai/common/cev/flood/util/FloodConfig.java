package com.yr.wds.xinyidai.common.cev.flood.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
@PropertySource(value = {"classpath:floodConfig.properties",}, encoding = "utf-8")
public class FloodConfig {



    @Value("${floodConfig}")
    private String floodConfig;

    private static JSONArray floodConfigArr;

    @PostConstruct
    public void init(){

         this.floodConfigArr=JSONArray.parseArray(floodConfig);
    }


    public static JSONObject getOtherConfig(String floodName){

        AtomicReference<JSONObject> jsonObject= new AtomicReference<>();

        floodConfigArr.forEach(x->{

            JSONObject js=JSONObject.parseObject(JSON.toJSONString(x)) ;

            List<String>  list=JSONObject.parseArray(js.getString("sequenceName"),String.class);

            if(list.indexOf(floodName)!=-1){

                js.remove("sequenceName");

                jsonObject.set(js);

            }


        });

        return null == jsonObject ? null:jsonObject.get();

    }


}
