package com.yr.wds.xinyidai.common.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
public class VueController implements ErrorController {

    @RequestMapping("/error")        //异常处理(主要用于浏览器页面刷新)
    public String handleError(HttpServletRequest request){

        //获取异常代码
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        //如果错误代码和请求方法为404和get,则返回首页
        if (request.getMethod().equals("GET")  && statusCode == 404) {
            return "/index.html";
        }
        return null;
    }

    @RequestMapping("/flood")
    public String getIndex(){
        //初始返回首页
        return "/index.html";
    }



}
