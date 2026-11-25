package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 仅在云南大唐电站模型配置文件存在时注册对应 Spring 组件。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(YunnanDatangFeatureCondition.class)
public @interface ConditionalOnYunnanDatangFeature {
}
