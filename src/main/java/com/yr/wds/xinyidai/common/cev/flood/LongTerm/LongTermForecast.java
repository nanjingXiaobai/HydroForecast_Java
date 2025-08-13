package com.yr.wds.xinyidai.common.cev.flood.LongTerm;

import com.alibaba.fastjson.JSONObject;


public class LongTermForecast {

    public static Object serviceSwitch(String query_flag, JSONObject query_params) throws Exception {
        Object return_data = new Object();
        switch (query_flag) {

            case "longTerm_init":                   //获取静态数据
                return_data = LongTermResult.getInit(query_params);
                break;
            case "longTerm_forecastCalculation":    //预报计算
                return_data = LongTermResult.forecastCalculation(query_params);
                break;
            case "longTerm_update":
                return_data = LongTermResult.forecastUpdate(query_params);
                break;
            case "longTerm_save":
                return_data = LongTermResult.forecastSave(query_params);
                break;
            case "longTerm_schemeData":             //方案查询
                return_data = LongTermResult.getFilteredResults(query_params);
                break;
            case "longTerm_details":                //方案详细列表
                return_data = LongTermResult.getSingleResultMapList(query_params);
                break;
            case "longTerm_prediction":             //方案详细预报
                return_data = LongTermResult.getSingleAdxlds(query_params);
                break;
            case "longTerm_delete":                 //结果管理==删除某一个预报制作时间下的预报方案及数据
                return_data = LongTermResult.delete(query_params);
                break;
            case "longTerm_delete_detail":          //结果管理==删除某一个预报制作时间下某一个断面的预报方案及数据
                return_data = LongTermResult.deleteDetail(query_params);
                break;
            case "longTerm_getFrequencyValue":         //根据频率计算频率值
                return_data = LongTermResult.getFrequencyValue(query_params);
                break;

        }
        return return_data;
    }
}
