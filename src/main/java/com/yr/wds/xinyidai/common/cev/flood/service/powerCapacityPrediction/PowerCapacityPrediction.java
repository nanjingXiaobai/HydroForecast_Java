package com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction;


import ch.qos.logback.core.joran.spi.XMLUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.config.R;
import com.yr.wds.xinyidai.common.cev.flood.service.commonsql.CommonSql;
import com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction.util.TreeBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.File;
import java.util.*;


/**
 * @author: WDP
 * @create: 2025-04-29 19:59
 **/

@Component
public class PowerCapacityPrediction {

    @Autowired
    private CommonSql commonSql;


    /**
     * 发电预测
     *
     * @param
     * @return
     */
    public Object powerGenerationForecast(String query_flag, JSONObject queryParams) throws Exception {
        String flag = query_flag.split("_")[1];
        // 1.发电能力模型
        switch (flag) {
            // 初始化
            case "getTree":
                return getTree();
        }
        return R.fail("参数错误");
    }





    private R getTree() throws Exception {

        Map<String, Object> tree=new HashMap<>();

        File file = new File( System.getProperty("user.dir") + "/conf/forecast/Tree.xml");
        if(file.exists()){
            Document winDocument = XmlUtil.readXML(file);
            String winXmlStr = XmlUtil.toStr(winDocument);
            JSONObject js = JSONObject.parseObject(JSONUtil.parseFromXml(winXmlStr).toString());
            JSONArray array=js.getJSONObject("root").getJSONArray("basinTree");
            array= getTree(array);
            tree.put("basinTree",array);

        }else{
            //获取电站基本数据
            List<JSONObject> reservoirList = commonSql.getAllReservoir();

            //获取流域数据
            List<JSONObject> basinList = commonSql.getRiverBasin();

            //获取断面数据
            List<JSONObject> sectList = commonSql.getSectionList(null);

            // 构建流域树、断面树
            tree =new TreeBuilder().buildTrees(basinList,sectList ,reservoirList);
        }



        return R.ok(tree);
    }


    private JSONArray getTree(JSONArray array)  {

        for (int i = 0; i < array.size(); i++) {

            JSONObject js = array.getJSONObject(i);

           Object children = js.get("children");
           if(children!=null && children instanceof JSONObject){
               JSONObject jsonObject=js.getJSONObject("children");
               JSONArray jsonArray=new JSONArray();
               jsonArray.add(jsonObject);
               js.put("children",jsonArray);
           }else if(children!=null && children instanceof JSONArray){
               JSONArray jsonArray=js.getJSONArray("children");
               getTree(jsonArray); // 递归处理子节点，但不返回
           }
           
        }

        return array;
    }

}
