package com.yr.wds;


import com.nari.slsd.hydro.backend.HFAutoForeService;
import com.nari.slsd.hydro.backend.TestMainPanel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.*;
import java.text.ParseException;

@EnableCaching
@SpringBootApplication(scanBasePackages = "com.yr.wds",exclude = {DataSourceAutoConfiguration.class})


public class CevBackendApplication {

    public static void main(String[] args) throws ParseException {
        //初始化气象告警类型
        System.out.println("------------------------------------------");
        System.out.println("configPath:"+System.getProperty("user.dir"));
        System.out.println("------------------------------------------");

        SpringApplication.run(CevBackendApplication.class, args);

//        // 1. 禁用 Headless
//        System.setProperty("java.awt.headless", "false");
//
//        // 2. 先启动 Spring Boot，获取上下文
//        ConfigurableApplicationContext context = new SpringApplicationBuilder(CevBackendApplication.class)
//                .headless(false) // 另一种写法
//                .run(args);
//
//        // 3. 在 EDT 上启动 Swing 界面
//        SwingUtilities.invokeLater(() -> {
//            // 从 Spring 容器中获取你的 Swing 窗口 Bean
//            // 注意：你的 JFrame 或 JPanel 类应该被 @Component 标记
//            TestMainPanel.main(args);
//        });
        //开始自动预报
//        HFAutoForeService.main(args);


        System.out.println(" ......................阿弥陀佛......................\n"+
                "                       _oo0oo_                      \n"+
                "                      o8888888o                     \n"+
                "                      88\" . \"88                     \n"+
                "                      (| -_- |)                     \n"+
                "                      0\\  =  /0                     \n"+
                "                   ___/‘---’\\___                   \n"+
                "                  .' \\|       |/ '.                 \n"+
                "                 / \\\\|||  :  |||// \\                \n"+
                "                / _||||| -卍-|||||_ \\               \n"+
                "               |   | \\\\\\  -  /// |   |              \n"+
                "               | \\_|  ''\\---/''  |_/ |              \n"+
                "               \\  .-\\__  '-'  ___/-. /              \n"+
                "             ___'. .'  /--.--\\  '. .'___            \n"+
                "         .\"\" ‘<  ‘.___\\_<|>_/___.’>’ \"\".          \n"+
                "       | | :  ‘- \\‘.;‘\\ _ /’;.’/ - ’ : | |        \n"+
                "         \\  \\ ‘_.   \\_ __\\ /__ _/   .-’ /  /        \n"+
                "    =====‘-.____‘.___ \\_____/___.-’___.-’=====     \n"+
                "                       ‘=---=’                      \n"+
                "                                                    \n"+
                "....................启动成功...................");
    }

}
