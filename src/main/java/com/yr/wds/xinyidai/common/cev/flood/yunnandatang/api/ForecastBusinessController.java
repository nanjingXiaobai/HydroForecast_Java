package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastCalculationService;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastInputAssemblyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 三个模型页面共用的自动取数和计算接口。 */
@CrossOrigin(originPatterns = "*")
@RestController
@RequestMapping("/forecast")
public class ForecastBusinessController {
    private final ForecastInputAssemblyService inputService;
    private final ForecastCalculationService calculationService;

    public ForecastBusinessController(ForecastInputAssemblyService inputService,
                                      ForecastCalculationService calculationService) {
        this.inputService = inputService;
        this.calculationService = calculationService;
    }

    /**
     * 首次进入页面时自动查库并计算；用户修改后可携带完整 input/inputs 重新计算。
     * 响应同时返回本次采用的 inputData 和 resultData，前端只需要调用一次。
     */
    @PostMapping("/{modelType}/calculate")
    public Map<String, Object> modelCalculate(@PathVariable String modelType,
                                              @RequestBody ForecastContracts.CalculateRequest request) {
        String module = resolveModule(modelType);
        prepareInput(request, module);
        return success(calculationService.calculate(request, module));
    }

    private void prepareInput(ForecastContracts.CalculateRequest request, String module) {
        if (request == null) throw new StationModelConfigException("请求不能为空");
        if ("TANK_MODEL".equals(module)) {
            boolean hasGroupInput = request.getInputs() != null && !request.getInputs().isEmpty();
            if (!hasGroupInput && request.getInput() == null) {
                request.setInputs(inputService.assembleTankGroup(toInputQuery(request)));
            }
            return;
        }
        if (request.getInput() == null) {
            request.setInput(inputService.assemble(toInputQuery(request), module));
        }
    }

    private ForecastContracts.InputQueryRequest toInputQuery(ForecastContracts.CalculateRequest request) {
        ForecastContracts.InputQueryRequest query = new ForecastContracts.InputQueryRequest();
        query.setStationCode(request.getStationCode());
        query.setForecastTime(request.getForecastTime());
        query.setHistoryBeginTime(request.getHistoryBeginTime());
        query.setHistoryEndTime(request.getHistoryEndTime());
        query.setForecastBeginTime(request.getForecastBeginTime());
        query.setForecastEndTime(request.getForecastEndTime());
        return query;
    }

    @ExceptionHandler(StationModelConfigException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(StationModelConfigException ex) {
        return new ResponseEntity<Map<String, Object>>(
                envelope(false, "400", ex.getMessage(), null), HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> success(Object data) {
        return envelope(true, "0", "success", data);
    }

    private String resolveModule(String modelType) {
        if ("api".equalsIgnoreCase(modelType)) return "API_MODEL";
        if ("tank".equalsIgnoreCase(modelType)) return "TANK_MODEL";
        if ("regression".equalsIgnoreCase(modelType)) return "REGRESSION_MODEL";
        throw new StationModelConfigException(
                "不支持的模型类型：" + modelType + "，可选值为 api、tank、regression");
    }

    private Map<String, Object> envelope(boolean success, String code, String message, Object data) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("success", success);
        result.put("code", code);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}
