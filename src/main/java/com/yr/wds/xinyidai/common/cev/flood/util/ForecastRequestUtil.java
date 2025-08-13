package com.yr.wds.xinyidai.common.cev.flood.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 请求用户  工具类
 *
 * @Author 王道彭
 * @Date 2022-05-30 21:22:12
 */
@Slf4j
public class ForecastRequestUtil {

    private static final ThreadLocal<String> REQUEST_THREAD_LOCAL = new ThreadLocal<>();

    public static void setRequestUserName(String userName) {
        REQUEST_THREAD_LOCAL.set(userName);
    }

    public static String getRequestUserName() {
        return REQUEST_THREAD_LOCAL.get();
    }

    public static void remove() {
        REQUEST_THREAD_LOCAL.remove();
    }


}
