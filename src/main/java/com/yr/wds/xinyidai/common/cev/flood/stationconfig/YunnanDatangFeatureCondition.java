package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 云南大唐功能以外部 station-model-config.xml 是否存在作为启用开关。 */
public class YunnanDatangFeatureCondition implements Condition {
    static final String CONFIG_PROPERTY = "service.yunnan-datang.station-config.file";
    static final String DEFAULT_CONFIG = "conf/forecast/station-model-config.xml";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isEnabled(context.getEnvironment(), Paths.get(System.getProperty("user.dir")));
    }

    static boolean isEnabled(Environment environment, Path workingDirectory) {
        String configured = environment.getProperty(CONFIG_PROPERTY, DEFAULT_CONFIG);
        if (configured == null || configured.trim().isEmpty()) return false;
        try {
            Path path = Paths.get(configured.trim());
            Path resolved = path.isAbsolute() ? path.normalize() : workingDirectory.resolve(path).normalize();
            return Files.isRegularFile(resolved);
        } catch (InvalidPathException ex) {
            return false;
        }
    }
}
