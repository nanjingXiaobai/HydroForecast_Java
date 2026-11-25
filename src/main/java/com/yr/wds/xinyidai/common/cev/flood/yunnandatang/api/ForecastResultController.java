package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastResultService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 与新工程路由一致的结果保存和管理接口。 */
@CrossOrigin(originPatterns = "*")
@RestController
@ConditionalOnYunnanDatangFeature
@RequestMapping("/forecast/results")
public class ForecastResultController {
    private final ForecastResultService service;
    public ForecastResultController(ForecastResultService service) { this.service = service; }

    @PostMapping
    public Map<String,Object> save(@RequestBody ForecastContracts.ResultSaveRequest request){return success(service.save(request));}
    @GetMapping
    public Map<String,Object> list(@RequestParam(required=false)String stationCode,
            @RequestParam(required=false)String module,@RequestParam(required=false)String beginTime,
            @RequestParam(required=false)String endTime,
            @RequestParam(defaultValue="forecastProductionTime")String timeFilterType,
            @RequestParam(required=false)String forecaster,@RequestParam(defaultValue="1")int pageNum,
            @RequestParam(defaultValue="20")int pageSize){return success(service.list(stationCode,module,beginTime,endTime,timeFilterType,forecaster,pageNum,pageSize));}
    @GetMapping("/forecasters")
    public Map<String,Object> forecasters(){return success(service.forecasters());}
    @GetMapping("/{taskId}")
    public Map<String,Object> detail(@PathVariable String taskId){return success(service.detail(taskId));}
    @DeleteMapping("/{taskId}")
    public Map<String,Object> delete(@PathVariable String taskId){service.delete(taskId);return success(Boolean.TRUE);}

    @ExceptionHandler(StationModelConfigException.class)
    public ResponseEntity<Map<String,Object>> error(StationModelConfigException ex){
        return new ResponseEntity<Map<String,Object>>(envelope(false,"400",ex.getMessage(),null),HttpStatus.BAD_REQUEST);
    }
    private Map<String,Object> success(Object data){return envelope(true,"0","success",data);}
    private Map<String,Object> envelope(boolean success,String code,String message,Object data){
        Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("success",success);
        result.put("code",code);result.put("message",message);result.put("data",data);return result;
    }
}
