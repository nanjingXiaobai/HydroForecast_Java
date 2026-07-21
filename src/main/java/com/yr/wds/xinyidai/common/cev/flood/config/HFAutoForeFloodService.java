package com.yr.wds.xinyidai.common.cev.flood.config;

import com.nari.slsd.hydro.backend.HFAutoForeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * 供应商旧版短期自动预报的 Spring 生命周期适配器。
 *
 * <p>仅在 {@code service.auto-forecast.mode=old} 或没有配置 mode 时启用。</p>
 * <p>应用启动后调用一次 {@link HFAutoForeService#startService()}，应用关闭时调用
 * {@link HFAutoForeService#shutDownService()}，避免定时线程遗留。</p>
 *
 * @author WDP
 */
@Configuration
@ConditionalOnProperty(name = "service.auto-forecast.mode", havingValue = "old", matchIfMissing = true)
public class HFAutoForeFloodService {


   private static HFAutoForeService HFAUTO_FORE_SERVICE;

    @PostConstruct
    public void init() {
        HFAUTO_FORE_SERVICE=new HFAutoForeService();
        startService();
    }

    /** 启动供应商旧版自动预报定时服务。 */
    public static void startService() {
        // mode=new/off 时本配置类不会创建旧服务，现有旧版设置接口即使被调用也不能误启动旧任务。
        if (HFAUTO_FORE_SERVICE != null) {
            HFAUTO_FORE_SERVICE.startService();
        }
    }

    /** 关闭供应商旧版自动预报使用的定时线程池。该静态入口保留给现有业务接口使用。 */
    public static void shutDownService() {
        if (HFAUTO_FORE_SERVICE != null) {
            HFAUTO_FORE_SERVICE.shutDownService();
        }
    }

    /** Spring 容器关闭时自动释放供应商旧服务的线程池。 */
    @PreDestroy
    public void destroy() {
        shutDownService();
    }

}
