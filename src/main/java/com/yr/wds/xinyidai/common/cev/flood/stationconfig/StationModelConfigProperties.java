package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 生产工程电站模型 XML 的外部文件配置。 */
@Component
@ConditionalOnYunnanDatangFeature
@ConfigurationProperties(prefix = "service.yunnan-datang.station-config")
public class StationModelConfigProperties {
    /** 相对路径以程序工作目录为基准，保持生产工程现有 conf 使用习惯。 */
    private String file = "conf/forecast/station-model-config.xml";
    /**
     * 是否要求启动时必须存在有效配置。
     *
     * <p>共享生产工程默认关闭，避免未部署云南大唐功能的现场因缺少专用 XML 而启动失败。
     * 云南大唐现场可显式设置为 true，以便尽早发现配置缺失。</p>
     */
    private boolean required = false;
    /** 查询配置时检测文件时间戳，变化后自动加载一份完整的新快照。 */
    private boolean reloadOnChange = true;

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isReloadOnChange() { return reloadOnChange; }
    public void setReloadOnChange(boolean reloadOnChange) { this.reloadOnChange = reloadOnChange; }
}
