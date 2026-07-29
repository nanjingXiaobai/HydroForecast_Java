package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

/**
 * Python 预报模型定义。
 *
 * <p>这里只描述模型身份和支持的调用方式，HTTP 地址、Python 路径等运行参数
 * 继续使用生产工程现有配置机制管理。</p>
 */
public class ModelDefinition {
    private String code;
    private String name;
    private String module;
    private String packageName;
    private String supportedCallModes;
    private String configPrefix;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getSupportedCallModes() { return supportedCallModes; }
    public void setSupportedCallModes(String supportedCallModes) { this.supportedCallModes = supportedCallModes; }
    public String getConfigPrefix() { return configPrefix; }
    public void setConfigPrefix(String configPrefix) { this.configPrefix = configPrefix; }
}
