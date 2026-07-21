package com.yr.wds.xinyidai.common.controller;


import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.util.ExcleUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@Slf4j
@Configuration
@RestController
@RequestMapping("/forecast")
public class FileServiceController {


    @RequestMapping(value = "/downLoadTableExcle", method = RequestMethod.GET)
    @ResponseBody
    public void downLoadTableExcle( HttpServletResponse httpResponse) throws Exception {


//        Map<String, Object> map = JSONObject.parseObject(params.getJSONObject("BottomInformation").toJSONString(), Map.class);

        JSONObject jsonObject=new JSONObject();
        jsonObject.put("level",1.1);

        List<JSONObject> list=new ArrayList<>();
        list.add(jsonObject);

        JSONObject jsonObject1=new JSONObject();
        jsonObject1.put("level",2.2);
        list.add(jsonObject1);

        ExcleUtils.createExcel(httpResponse, "schedulingCalculation.xls", null, list);

    }

    @RequestMapping(value = "/resultTableExcel", method = RequestMethod.POST)
    @ResponseBody
    public void resultTableExcle( HttpServletResponse httpResponse,@RequestParam String condition) throws Exception {

        JSONObject param_json = JSONObject.parseObject(condition);//JSONObject.parseObject(params);

        new ExcleUtils().exportSystemExcel(param_json,httpResponse);


    }

    @RequestMapping(value = "/resultTableExcelPlus", method = RequestMethod.POST)
    @ResponseBody
    public void resultTableExcelPlus(HttpServletResponse httpResponse, @RequestParam String condition) throws Exception {

        JSONObject paramJson = JSONObject.parseObject(condition);

        new ExcleUtils().exportResultTableExcelPlus(paramJson, httpResponse);

    }

    @RequestMapping(value = "/calculateTrafficExcel", method = RequestMethod.POST)
    @ResponseBody
    public void calculateTrafficExcel(HttpServletResponse httpResponse, @RequestParam String condition) throws Exception {

        JSONObject paramJson = JSONObject.parseObject(condition);

        new ExcleUtils().exportCalculateTrafficExcel(paramJson, httpResponse);

    }

    @RequestMapping(value = "/resultStandardDataExcel", method = RequestMethod.POST)
    @ResponseBody
    public void resultStandardDataExcel(HttpServletResponse httpResponse, @RequestParam String condition) throws Exception {

        JSONObject paramJson = JSONObject.parseObject(condition);

        new ExcleUtils().exportResultStandardDataExcel(paramJson, httpResponse);

    }

}
