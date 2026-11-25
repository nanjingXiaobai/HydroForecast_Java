package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ModelDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python 模型 HTTP 客户端。
 *
 * <p>每个模型从 XML 的 config 属性获得配置前缀，因此 API、水箱和沙陀回归模型可以部署在不同地址。</p>
 */
@Service
@ConditionalOnYunnanDatangFeature
public class PythonModelHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonModelHttpClient.class);
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PythonModelHttpClient(Environment environment) {
        this.environment = environment;
    }

    public Map<String, Object> invoke(ModelDefinition model, Map<String, Object> request) {
        String prefix = model.getConfigPrefix();
        String baseUrl = firstText(property(prefix + ".base-url"),
                property(prefix + ".http.base-url"), property("forecast.model.http.base-url"));
        if (baseUrl == null) throw new StationModelConfigException(
                "未配置 Python 模型地址: " + prefix + ".base-url");
        String path = firstText(property(prefix + ".invoke-path"),
                property(prefix + ".http.invoke-path"), property("forecast.model.http.invoke-path"));
        if (path == null) path = "/api/model/invoke";
        String targetUrl = trimSlash(baseUrl) + normalizePath(path);
        long startedAt = System.currentTimeMillis();
        LOGGER.info("[云南大唐][Python模型] 开始调用 modelCode={}, url={}", model.getCode(), targetUrl);

        try {
            RestTemplate restTemplate = restTemplate(prefix);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    targetUrl, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new StationModelConfigException("Python 模型 HTTP 状态码: "
                        + response.getStatusCodeValue());
            }
            Map<String, Object> body = objectMapper.readValue(response.getBody(),
                    new TypeReference<Map<String, Object>>() { });
            if (!Boolean.TRUE.equals(body.get("success"))) {
                throw new StationModelConfigException("Python 模型执行失败: " + body.get("message"));
            }
            Object data = body.get("data");
            Map<String, Object> result;
            if (data == null) {
                result = new LinkedHashMap<String, Object>();
            } else {
                if (!(data instanceof Map)) throw new StationModelConfigException("Python 模型 data 必须是 JSON 对象");
                @SuppressWarnings("unchecked")
                Map<String, Object> modelResult = (Map<String, Object>) data;
                result = modelResult;
            }
            LOGGER.info("[云南大唐][Python模型] 调用成功 modelCode={}, url={}, resultFields={}, elapsedMs={}",
                    model.getCode(), targetUrl, result.size(), System.currentTimeMillis() - startedAt);
            return result;
        } catch (StationModelConfigException ex) {
            LOGGER.error("[云南大唐][Python模型] 调用失败 modelCode={}, url={}, elapsedMs={}, message={}",
                    model.getCode(), targetUrl, System.currentTimeMillis() - startedAt, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("[云南大唐][Python模型] 调用异常 modelCode={}, url={}, elapsedMs={}",
                    model.getCode(), targetUrl, System.currentTimeMillis() - startedAt, ex);
            throw new StationModelConfigException("Python 模型调用失败: " + ex.getMessage(), ex);
        }
    }

    private String property(String key) {
        return key == null || key.startsWith("null.") ? null : environment.getProperty(key);
    }
    /** 为每个模型设置连接和计算超时，防止 Python 无响应时耗尽生产服务线程。 */
    private RestTemplate restTemplate(String prefix) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(intProperty(prefix + ".connect-timeout-millis",
                "forecast.model.http.connect-timeout-millis", 5000));
        factory.setReadTimeout(intProperty(prefix + ".read-timeout-millis",
                "forecast.model.http.read-timeout-millis", 300000));
        return new RestTemplate(factory);
    }
    private int intProperty(String modelKey, String fallbackKey, int defaultValue) {
        String value = firstText(property(modelKey), property(fallbackKey));
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new StationModelConfigException("超时配置必须是整数: " + modelKey);
        }
    }
    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim();
        return null;
    }
    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
    private String normalizePath(String value) { return value.startsWith("/") ? value : "/" + value; }
}
