package com.yr.wds.xinyidai.common.cev.flood.util;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * @author xcw
 * @version 1.0
 * @data 2023/10/16 14:42
 */
public class JSONUtils {


    public static void transformData(JSONObject jsonObject, int scale) {

        for (String key : jsonObject.keySet()) {

            Object value = jsonObject.get(key);

            if (value instanceof JSONObject) {

                transformData(jsonObject.getJSONObject(key), scale);

            } else if (null != value && NumberUtil.isDouble(value.toString())) {

                BigDecimal v = NumberUtil.round(jsonObject.getBigDecimal(key), scale);

                jsonObject.put(key, v);

            } else if (value instanceof Collection) {

                List<Object> list = (List<Object>) value;

                for (int i = 0; i < list.size(); i++) {

                    Object o = list.get(i);

                    if (o instanceof JSONObject) {

                        transformData((JSONObject) o, scale);

                    } else if (null != o && NumberUtil.isDouble(o.toString())) {
                        list.set(i, NumberUtil.round(o.toString(), scale));
                    }

                }
            }


        }


    }

    public static JSONObject getXmlJSON (String pathOrContent ){

        //获取中期精度评定的树节点
//        Document document= XmlUtil.readXML(");
        Document document= XmlUtil.readXML(pathOrContent);

        String xmlStr=  XmlUtil.toStr(document);

        JSONObject js=new JSONObject();

        if(StrUtil.isNotBlank(xmlStr)){

            cn.hutool.json.JSONObject json = JSONUtil.parseFromXml(xmlStr);

            js=JSONObject.parseObject(json.toString(),JSONObject.class);
        }


        return js;

    }

}
