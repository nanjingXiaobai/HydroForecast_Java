package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 生产工程电站模型配置注册表。
 *
 * <p>共享生产工程默认将该配置视为可选，未部署云南大唐功能的现场即使没有 XML 也能正常启动。
 * 配置文件存在时会完整解析和校验，成功才原子替换；运行中加载失败则继续使用上一份有效配置。</p>
 */
@Service
public class StationModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(StationModelRegistry.class);

    private final StationModelConfigProperties properties;
    private final StationModelConfigParser parser;
    private final AtomicReference<StationModelConfiguration> current =
            new AtomicReference<StationModelConfiguration>();
    private volatile long loadedLastModified = -1L;
    private volatile long rejectedLastModified = -1L;

    public StationModelRegistry(StationModelConfigProperties properties, StationModelConfigParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    @PostConstruct
    public void initialize() {
        Path path = resolvePath();
        if (!Files.isRegularFile(path)) {
            if (properties.isRequired()) {
                throw new StationModelConfigException("缺少必需的电站模型配置文件: " + path);
            }
            LOGGER.info("未部署电站模型配置文件，云南大唐模型功能保持未启用，不影响应用启动: {}", path);
            return;
        }
        reload(true);
    }

    public StationModelConfiguration getConfiguration() {
        refreshIfChanged();
        StationModelConfiguration configuration = current.get();
        if (configuration == null) {
            throw new StationModelConfigException("云南大唐电站模型配置未启用或尚未加载");
        }
        return configuration;
    }

    public List<StationDefinition> getEnabledStations() {
        List<StationDefinition> result = new ArrayList<StationDefinition>();
        for (StationDefinition station : getConfiguration().getStations()) {
            if (station.isEnabled()) result.add(station);
        }
        result.sort(Comparator.comparing(StationDefinition::getModule)
                .thenComparingInt(StationDefinition::getDisplayOrder)
                .thenComparing(StationDefinition::getCode));
        return result;
    }

    public StationModelRoute resolveRoute(String stationCode) {
        if (stationCode == null || stationCode.trim().isEmpty()) {
            throw new StationModelConfigException("电站code不能为空");
        }
        StationModelConfiguration configuration = getConfiguration();
        StationDefinition station = configuration.getStationMap().get(stationCode.trim());
        if (station == null || !station.isEnabled()) {
            throw new StationModelConfigException("电站不存在或未启用: " + stationCode);
        }
        return new StationModelRoute(station, configuration.getModelMap().get(station.getModelRef()));
    }

    private void refreshIfChanged() {
        if (!properties.isReloadOnChange()) return;
        Path path = resolvePath();
        // 可选文件尚未部署时直接返回；文件后续出现后仍可被自动加载。
        if (!Files.isRegularFile(path)) return;
        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            if (lastModified != loadedLastModified && lastModified != rejectedLastModified) reload(false);
        } catch (IOException ex) {
            // 已有有效快照时，短暂文件访问失败不能影响查询服务。
            LOGGER.error("检查电站模型配置文件失败，继续使用上一有效版本: {}", path, ex);
        }
    }

    private synchronized void reload(boolean failFast) {
        Path path = resolvePath();
        try {
            long lastModified = Files.getLastModifiedTime(path).toMillis();
            try (InputStream inputStream = Files.newInputStream(path)) {
                StationModelConfiguration configuration = parser.parse(inputStream);
                current.set(configuration);
                loadedLastModified = lastModified;
                rejectedLastModified = -1L;
                LOGGER.info("电站模型配置已加载, file={}, version={}, models={}, stations={}",
                        path, configuration.getConfigVersion(), configuration.getModels().size(),
                        configuration.getStations().size());
            }
        } catch (Exception ex) {
            try {
                rejectedLastModified = Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : -1L;
            } catch (IOException ignored) {
                rejectedLastModified = -1L;
            }
            if (failFast || current.get() == null) {
                throw new StationModelConfigException("电站模型配置加载失败: " + path + ", " + ex.getMessage(), ex);
            }
            LOGGER.error("忽略非法电站模型配置，继续使用上一有效版本: {}", path, ex);
        }
    }

    private Path resolvePath() {
        Path path = Paths.get(properties.getFile());
        return path.isAbsolute() ? path.normalize()
                : Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }
}
