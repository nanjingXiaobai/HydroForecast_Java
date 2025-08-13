package com.yr.wds.xinyidai.common.controller;

import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.service.powerCapacityPrediction.PowerCapacityPrediction;
import com.yr.wds.xinyidai.common.cev.flood.shortForecast.FloodForecast;
import com.yr.wds.xinyidai.common.cev.flood.LongTerm.LongTermForecast;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * @ClassName: CevController
 * @Description
 * @creatTime 2020-03-26
 */
@RestController
@RequestMapping("/forecast")
public class CevCommonDataController {



	@Autowired
	public PowerCapacityPrediction powerCapacityPrediction;

	/**
	 * 统一数据请
	 //     * @param condition
	 //     * @return
	 */

	@RequestMapping(value = "/commonData",method = {RequestMethod.POST, RequestMethod.GET})
	public Object commonData(@RequestParam String condition) throws Exception {

		JSONObject param_json = JSONObject.parseObject(condition);//JSONObject.parseObject(params);
		System.out.println(param_json);
		if (param_json.size() < 2){
			return JSONObject.parseObject("{message:\"参数缺失\"}");
		}
		String query_flag = param_json.getString("flag");
		JSONObject query_params = param_json.getJSONObject("queryParams");
		Object return_json = new Object();

//		if (!checkAuth.check("cev",query_params.getString("Authorization")cev/commonData,query_flag)) {
//			return new Message().error(401,"没有访问权限");
//		}
        switch (query_flag.split("_")[0]) {

            case "flood":    //洪水预报
                return_json= FloodForecast.serviceSwitch(query_flag,query_params);
                break;

			case "longTerm":		//中长期洪水预报
				return_json= LongTermForecast.serviceSwitch(query_flag,query_params);
				break;

			//发电能力预测
			case "powerCapacityPrediction":
				return_json = powerCapacityPrediction.powerGenerationForecast(query_flag,query_params);
				break;

            default:
                return_json = JSONObject.parseObject("{message:\"无效的请求类型\"}");
        }

        return return_json;
    }

}



