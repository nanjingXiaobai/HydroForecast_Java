package com.yr.wds.xinyidai.common.cev.flood.config;

import com.sgepri.ce.util.TimeFunc;
import com.yr.wds.xinyidai.common.cev.flood.service.abnormalWeather.AbnormalWeatherCalculation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Calendar;
import java.util.List;
@Configuration
@EnableAsync
@EnableScheduling
@ConditionalOnProperty(value = "service.cron.saveabnormalweather.enabled", havingValue = "true")
public class CronSaveAbnormalWeather {
    //定时执行
    @Async
    @Scheduled(cron = "${service.cron.saveabnormalweather.setting:0 0 10 * * *}")
    public void Task() {
        try {
            new AbnormalWeatherCalculation().saveAbnormalWeather(TimeFunc.getFormatStr(Calendar.getInstance(),"yyyy-MM-dd"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
