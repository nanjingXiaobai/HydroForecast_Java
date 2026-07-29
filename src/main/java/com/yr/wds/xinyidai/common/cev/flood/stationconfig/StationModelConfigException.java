package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

/** 电站模型配置读取、校验或路由异常。 */
public class StationModelConfigException extends RuntimeException {
    public StationModelConfigException(String message) { super(message); }
    public StationModelConfigException(String message, Throwable cause) { super(message, cause); }
}
