package com.yr.wds.xinyidai.common.cev.flood.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.net.URL;

@Component
public class JsonToObjUtil {

    public static String language="zh";

    //获取前端配置文件中的中英文
    public static String jsonToObj() {
        String jsonStr = "";

        String path="static/flood/config.json";
        try {
            //获取配置文件中的语言设置，如果分离导致没有的话，默认zh
            ClassLoader cl = ClassUtils.getDefaultClassLoader();
            URL url = cl != null ? cl.getResource(path) : ClassLoader.getSystemResource(path);
            if(null == url){
                return null;
            }

            File jsonFile = ResourceUtils.getFile("classpath:static/flood/config.json");
            FileReader fileReader = new FileReader(jsonFile);

            Reader reader = new InputStreamReader(new FileInputStream(jsonFile), "utf-8");
            int ch = 0;
            StringBuffer sb = new StringBuffer();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            fileReader.close();
            reader.close();
            jsonStr = sb.toString();
            return jsonStr;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    public static String getChineseOrEnglish() {


        String json = JsonToObjUtil.jsonToObj();

        if(StringUtils.isBlank(json)){
            return "zh";
        }

        JSONObject js = JSONObject.parseObject(json);

        language= js.getString("language");

        return js.getString("language");

    }


}
