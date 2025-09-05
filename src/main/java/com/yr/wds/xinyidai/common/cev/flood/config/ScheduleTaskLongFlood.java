package com.yr.wds.xinyidai.common.cev.flood.config;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


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


}
