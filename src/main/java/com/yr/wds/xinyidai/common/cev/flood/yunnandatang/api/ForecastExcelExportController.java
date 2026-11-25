package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastExcelExportService;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service.ForecastExcelExportService.Download;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 与新工程路由一致的云南大唐 Excel 导出接口。 */
@CrossOrigin(originPatterns = "*")
@RestController
@ConditionalOnYunnanDatangFeature
@RequestMapping("/forecast")
public class ForecastExcelExportController {
    private final ForecastExcelExportService service;
    public ForecastExcelExportController(ForecastExcelExportService service){this.service=service;}

    /** 下载前的业务错误使用普通JSON返回，前端可从blob解析明确原因。 */
    @ExceptionHandler(com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException.class)
    public org.springframework.http.ResponseEntity<java.util.Map<String,Object>> businessError(
            com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException ex) {
        java.util.Map<String,Object> body=new java.util.LinkedHashMap<String,Object>();
        body.put("success",false);body.put("code","400");body.put("message",ex.getMessage());body.put("data",null);
        return org.springframework.http.ResponseEntity.badRequest().body(body);
    }

    @PostMapping("/export")
    public void exportCalculation(@RequestBody ForecastContracts.ExcelExportRequest request,
                                  HttpServletResponse response)throws Exception{write(service.exportCalculation(request),response);}
    @GetMapping("/results/{taskId}/export")
    public void exportSaved(@PathVariable String taskId,HttpServletResponse response)throws Exception{write(service.exportSaved(taskId),response);}

    private void write(Download download,HttpServletResponse response)throws Exception{
        String legacy=new String(download.getFileName().getBytes(StandardCharsets.UTF_8),StandardCharsets.ISO_8859_1).replace("\"","_");
        String encoded=URLEncoder.encode(download.getFileName(),"UTF-8").replace("+","%20");
        response.setContentType(download.getContentType());response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition","attachment; filename=\""+legacy+"\"; filename*=UTF-8''"+encoded);
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition, Content-Type, Content-Length");
        response.setContentLength(download.getContent().length);response.getOutputStream().write(download.getContent());response.getOutputStream().flush();
    }
}
