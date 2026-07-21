package com.yr.wds.xinyidai.common.cev.flood.autoforecast;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新自动预报管理接口。
 *
 * <p>只有配置 {@code service.auto-forecast.mode=new} 时，本控制器才会注册。</p>
 * <p>项目统一上下文路径是 {@code /flood}，因此实际访问前缀为
 * {@code /flood/auto-forecast}。</p>
 */
@RestController
@RequestMapping("/auto-forecast")
@ConditionalOnProperty(name = "service.auto-forecast.mode", havingValue = "new")
public class NewAutoForecastController {
    private final NewAutoForecastService service;

    public NewAutoForecastController(NewAutoForecastService service) { this.service = service; }

    /**
     * 查询新自动预报当前状态。
     *
     * @return schemes：自动发现的供应商方案；rules：实际生效规则；
     * nextTimes：各方案下一次执行时间；manualNextTimes：人工覆盖的下一次时间
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemes", service.getAvailableSchemes());
        result.put("rules", service.getRules());
        result.put("nextTimes", service.getNextScheduledTimes());
        result.put("manualNextTimes", service.getManualNextTimes());
        return result;
    }

    /**
     * 临时替换当前进程中的全部调度规则。
     *
     * <p>该接口主要用于调试或管理页面。修改结果不会写入 application.properties，
     * 服务重启后仍以 application.properties 中的小时、日配置为准。</p>
     *
     * @param rules 完整规则列表；不是增量修改，传入内容会整体替换当前规则
     * @return 操作结果
     */
    @PutMapping("/rules")
    public Map<String, Object> replaceRules(@RequestBody List<AutoForecastProperties.Rule> rules) {
        service.replaceRules(rules);
        return ok("规则已更新（本次进程内生效；永久配置请同步修改application.properties）");
    }

    /**
     * 查询当前进程保留的执行历史。
     *
     * @return 按执行编号倒序排列的记录，包括方案、来源、预报时间、状态和错误信息
     */
    @GetMapping("/history")
    public List<AutoForecastExecution> history() { return service.getHistory(); }

    /**
     * 立即提交一次指定方案的预报任务。
     *
     * @param schemeName 供应商方案名称，可从 status 接口的 schemes 字段查看
     * @param forecastTime 可选的计算时间，格式 yyyy-MM-dd HH:mm:ss；不传则使用当前时间
     * @return 任务提交结果；实际计算结果应通过 history 接口查看
     */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestParam String schemeName,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime forecastTime) {
        service.runNow(schemeName, forecastTime);
        return ok("任务已提交");
    }

    /**
     * 临时指定某个方案的下一次执行时间。
     *
     * <p>设置后，该方案在指定时间到达前不会按普通规则执行；指定任务执行一次后，
     * 自动恢复 application.properties 中的正常调度规则。</p>
     *
     * @param schemeName 供应商方案名称
     * @param executeTime 下一次实际执行时间，格式 yyyy-MM-dd HH:mm:ss，必须晚于当前时间
     * @return 设置结果
     */
    @PutMapping("/next-time")
    public Map<String, Object> setNextTime(@RequestParam String schemeName,
                                           @RequestParam
                                           @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime executeTime) {
        service.setManualNextTime(schemeName, executeTime);
        return ok("下一次执行时间已设置");
    }

    /**
     * 取消人工指定的下一次执行时间，立即恢复正常调度规则。
     *
     * @param schemeName 供应商方案名称
     * @return 操作结果
     */
    @DeleteMapping("/next-time")
    public Map<String, Object> clearNextTime(@RequestParam String schemeName) {
        service.clearManualNextTime(schemeName);
        return ok("已恢复配置规则");
    }

    /**
     * 按方案频率滚动提交区间补算。
     *
     * <p>小时方案按照 hour.frequency 逐小时滚动，日方案按照 day.frequency
     * 逐日滚动。补算与正常任务共用单线程队列，避免供应商组件并发入库。</p>
     *
     * @param schemeName 供应商方案名称
     * @param begin 补算开始时间，格式 yyyy-MM-dd HH:mm:ss，包含该时刻
     * @param end 补算结束时间，格式 yyyy-MM-dd HH:mm:ss，包含该时刻
     * @return 任务提交结果；执行进度和结果通过 history 接口查看
     */
    @PostMapping("/compensate")
    public Map<String, Object> compensate(@RequestParam String schemeName,
                                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime begin,
                                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        service.compensate(schemeName, begin, end);
        return ok("补算任务已提交");
    }

    /** 生成统一的成功响应。 */
    private Map<String, Object> ok(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        return result;
    }
}
