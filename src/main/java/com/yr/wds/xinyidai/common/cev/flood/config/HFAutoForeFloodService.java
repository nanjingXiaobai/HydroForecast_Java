package com.yr.wds.xinyidai.common.cev.flood.config;

import com.nari.slsd.hydro.backend.HFAutoForeService;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * @author: WDP
 * 控制短期自动预报服务
 **/
@Configuration
public class HFAutoForeFloodService {


   private static HFAutoForeService HFAUTO_FORE_SERVICE;

    @PostConstruct
    public void init() {
        HFAUTO_FORE_SERVICE=new HFAutoForeService();
        startService();
    }


    public static void startService() {
        HFAUTO_FORE_SERVICE.startService();
    }

    public static void shutDownService() {
        HFAUTO_FORE_SERVICE.shutDownService();
    }

}
