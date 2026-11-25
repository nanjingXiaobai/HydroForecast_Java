package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 云南大唐电站模型配置查询接口。
 *
 * <p>沿用生产工程现有 {@code /forecast} 路由体系；云南大唐新工程适配相同路径，
 * 前端只需切换服务地址，不需要为不同工程维护两套路由。</p>
 */
@CrossOrigin(originPatterns = "*")
@RestController
@ConditionalOnYunnanDatangFeature
@RequestMapping("/forecast/station-config")
public class StationConfigController {
    private final StationConfigQueryService queryService;

    public StationConfigController(StationConfigQueryService queryService) {
        this.queryService = queryService;
    }

    /** 前端通过该接口生成模块页签和电站列表，不在页面中写死站点。 */
    @GetMapping
    public Map<String, Object> getAll() {
        return success(queryService.getAll());
    }

    /** 查询指定电站或计算分区的模型路由信息。 */
    @GetMapping("/stations/{stationCode}")
    public Map<String, Object> getStation(@PathVariable("stationCode") String stationCode) {
        return success(queryService.getStation(stationCode));
    }

    /** 配置参数错误返回400，便于前端区分业务错误和服务异常。 */
    @ExceptionHandler(StationModelConfigException.class)
    public ResponseEntity<Map<String, Object>> handleConfigException(StationModelConfigException ex) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", false);
        body.put("code", "400");
        body.put("message", ex.getMessage());
        body.put("data", null);
        return new ResponseEntity<Map<String, Object>>(body, HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("code", "0");
        result.put("message", "success");
        result.put("data", data);
        return result;
    }
}
