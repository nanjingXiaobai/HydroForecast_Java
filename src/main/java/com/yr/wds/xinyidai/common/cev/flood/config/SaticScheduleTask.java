package com.yr.wds.xinyidai.common.cev.flood.config;


import com.nari.slsd.hps.dbmgr.dao.ConnectionJDBC;
import com.nari.slsd.hydro.backend.HFWebService;
import com.sgepri.ce.db.util.DBOpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.sql.SQLException;


@Configuration      //1.主要用于标记配置类，兼备Component的效果。
@EnableScheduling   // 2.开启定时任务
public class SaticScheduleTask {

    @Value("${type}")
    private int type;


    /**
     * 定时链接数据库
     *
     * @throws IOException
     */

//    @Scheduled(cron = "0 0/3 * * * ?")
//    public void sendMessage()  {
//
//        System.out.println("数据库链接。。。。");
//
//        try {
//
//            HFWebService.getAllYbrList(type);
//        } catch (Exception e) {
//            e.printStackTrace();
//
//            ConnectionJDBC.getInstance();
//        }
//
//
//    }







}
