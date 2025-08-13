package com.yr.wds.xinyidai.common.cev.flood.config;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;

import com.nari.slsd.hps.dbmgr.dao.ConnectionJDBC;
import com.nari.slsd.hydro.backend.HFWebService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;



@Configuration      //1.主要用于标记配置类，兼备Component的效果。
@EnableScheduling   // 2.开启定时任务
@ConditionalOnProperty(name = "schedule.cronExpression")
public class ScheduleTaskLongFlood {

    @Value("${type}")
    private int type;

    @Value("${schedule.cronExpression}")
    private String cronExpression;

  public String[] getCronExpression() {

        String value[] = new String[4];

        DateTime time = DateUtil.parse(cronExpression, "HH:mm");

        int hour = time.hour(true);

        int minute = time.minute();

        //年
        value[0] = ("0 " + minute + " " + hour + " 1 1 ?");
        //月
        value[1] = ("0 " + minute + " " + hour + " 1 * ?");
        //日
        value[2] = ("0 " + minute + " " + hour + " * * ?");
        //旬
        value[3] = ("0 " + minute + " " + hour + " 1,11,21 * ?");

        return value;
    }


    @Scheduled(cron = "0 0/3 * * * ?")
    public void sendMessage() {

        System.out.println("数据库链接。。。。");

        try {

            HFWebService.getAllYbrList(type);
        } catch (Exception e) {
            e.printStackTrace();

            ConnectionJDBC.getInstance();
        }


    }




}
