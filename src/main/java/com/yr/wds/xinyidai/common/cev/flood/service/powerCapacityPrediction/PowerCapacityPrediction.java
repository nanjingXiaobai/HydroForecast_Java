package com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction;


import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.config.R;
import com.yr.wds.xinyidai.common.cev.flood.service.commonsql.CommonSql;
import com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction.util.TreeBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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

        Map<String,Object> result=new HashMap<>();

        //获取电站基本数据
        List<JSONObject> reservoirList = commonSql.getAllReservoir();

        //获取流域数据
        List<JSONObject> basinList = commonSql.getRiverBasin();

        //获取断面数据
        List<JSONObject> sectList = commonSql.getSectionList(null);

        // 构建流域树、断面树
        Map<String, Object> tree =new TreeBuilder().buildTrees(basinList,sectList ,reservoirList);
        result.putAll(tree);

        return R.ok(result);
    }



}
