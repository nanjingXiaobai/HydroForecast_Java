package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts.*;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 自动任务复用页面的四区计算和单表保存；页面仍可手填预报雨量。 */
@Service
@ConditionalOnYunnanDatangFeature
public class TankDailyForecastService {
    static LocalDate calculationStart(LocalDate forecastBegin) {
        return LocalDate.of(forecastBegin.getYear() - 1, 12, 1);
    }
    private static final Logger LOG = LoggerFactory.getLogger(TankDailyForecastService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final StationModelRegistry registry;
    private final ForecastInputAssemblyService assembly;
    private final ForecastSourceDataRepository repository;
    private final ForecastCalculationService calculation;
    private final ForecastResultService results;

    public TankDailyForecastService(StationModelRegistry registry, ForecastInputAssemblyService assembly,
            ForecastSourceDataRepository repository, ForecastCalculationService calculation,
            ForecastResultService results) {
        this.registry=registry; this.assembly=assembly; this.repository=repository;
        this.calculation=calculation; this.results=results;
    }

    @Scheduled(cron="0 * * * * *", zone="Asia/Shanghai")
    public void scheduled() {
        try {
            LocalDateTime now = LocalDateTime.now(ZONE);
            // 注册表自动检测XML修改；每分钟检查最新的每日运行时间。
            if (!isDue(registry.getConfiguration(), now)) return;
            LOG.info("水箱每日自动预报保存完成: {}", run(now));
        }
        catch (Exception ex) { LOG.error("水箱每日自动预报失败，本次未完成保存", ex); }
    }

    static boolean isDue(StationModelConfiguration config, LocalDateTime now) {
        return config.isTankAutoEnabled()
                && now.toLocalTime().withSecond(0).withNano(0).equals(LocalTime.parse(config.getTankAutoTime()));
    }

    public synchronized Map<String,Object> run(LocalDateTime issue) {
        LocalDate begin=issue.toLocalDate(), end=begin.plusDays(9);
        InputQueryRequest query=new InputQueryRequest();
        query.setForecastTime(issue.format(FORMAT));
        query.setForecastBeginTime(begin+" 00:00:00");
        query.setForecastEndTime(end+" 23:59:59");
        // 预报年份的前一年12月1日至昨日实测；当天尚未结束，使用当天至第9天的完整日预报。
        List<InputResponse> inputs=assembly.assembleTankGroup(query);
        for (InputResponse input: inputs) {
            StationDefinition station=registry.resolveRoute(input.getStationCode()).getStation();
            SourceDataProfileDefinition profile=registry.getConfiguration().getSourceDataProfileMap()
                    .get(station.getDataProfileRef());
            if (profile.getForecastRegionId()==null || profile.getForecastSubtype()==null)
                throw new StationModelConfigException("自动水箱缺少gfs配置: "+station.getCode());
            List<GfsForecastRainValue> rows=repository.queryLatestGfsRain(profile.getForecastRegionId(),
                    profile.getForecastSubtype(), calendar(issue), calendar(begin.atStartOfDay()),
                    calendar(end.atTime(23,59,59)));
            input.setForecastRainfall(dailyRain(rows,profile.getForecastRegionId(),
                    profile.getForecastSubtype(),begin,end,true));
            // 与手动页面一致：缺失日值进入公共补零流程，保留 DEFAULT_ZERO 标记。
            ForecastMissingValues.normalize(input, "TANK_MODEL");
            TankParameterProfileDefinition parameters=registry.getConfiguration().getTankParameterProfileMap()
                    .get(station.getParameterProfileRef());
            if(parameters==null) throw new StationModelConfigException("自动水箱缺少参数方案: "+station.getCode());
            Map<String,Object> snapshot=new LinkedHashMap<>(parameters.getModelParameters());
            Map<String,Object> seed=new LinkedHashMap<>();
            seed.put("d",15D); seed.put("i",15D); seed.put("l",15D);
            snapshot.put("initialState",seed);
            snapshot.put("forecastRegionId",profile.getForecastRegionId());
            snapshot.put("forecastSubtype",profile.getForecastSubtype());
            if(!rows.isEmpty() && rows.get(0).getForecastIssueTime()!=null)
                snapshot.put("forecastIssueTime",rows.get(0).getForecastIssueTime().toInstant()
                        .atZone(ZONE).toLocalDateTime().format(FORMAT));
            input.setModelParameters(snapshot);
        }
        CalculateRequest request=new CalculateRequest(); request.setInputs(inputs);
        CalculateResponse response=calculation.calculate(request,"TANK_MODEL");
        validateForecastCoverage(response.getResultData(), inputs, begin, end);
        ResultSaveRequest save=new ResultSaveRequest();
        // 同一天重跑/多实例由现有 SAVE_KEY 唯一约束阻止重复存库。
        save.setCalculationId("TANK_AUTO_"+begin);
        save.setStationCode(response.getStationCode()); save.setModelCode(response.getModelCode());
        save.setForecastTime(issue.format(FORMAT)); save.setInputs(inputs);
        save.setResult(response.getResultData()); save.setForecaster("系统自动预报");
        save.setRemark("预报制作时间:" + issue.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return results.save(save);
    }

    static Calendar calendar(LocalDateTime time) {
        return GregorianCalendar.from(time.atZone(ZONE));
    }

    static void validateForecastCoverage(Map<String,Object> result,List<InputResponse> inputs,
                                         LocalDate begin,LocalDate end) {
        Object basins=result==null?null:result.get("basinDetails");
        if(!(basins instanceof List)) throw new StationModelConfigException("自动水箱未返回分区结果");
        Set<String> expected=new HashSet<>();
        for(InputResponse input:inputs) expected.add(input.getStationCode());
        for(Object item:(List<?>)basins) {
            Map<?,?> basin=(Map<?,?>)item;
            if(!expected.remove(basin.get("stationCode"))) throw new StationModelConfigException("自动水箱分区结果重复或未知");
            Object table=basin.get("tableData");
            if(!(table instanceof List)) throw new StationModelConfigException("自动水箱缺少过程线");
            Set<LocalDate> dates=new HashSet<>();
            for(Object value:(List<?>)table) {
                Map<?,?> row=(Map<?,?>)value;
                LocalDate date=ForecastDailyRows.parse(String.valueOf(row.get("time"))).toLocalDate();
                if(date.isBefore(begin)||date.isAfter(end)) continue;
                Object flow=row.get("flow");
                if(!(flow instanceof Number)||!Double.isFinite(((Number)flow).doubleValue())||!dates.add(date))
                    throw new StationModelConfigException("自动水箱预报结果重复或流量无效: "+date);
            }
            for(LocalDate date=begin;!date.isAfter(end);date=date.plusDays(1))
                if(!dates.contains(date)) throw new StationModelConfigException("自动水箱计算结果缺少预报日: "+date);
        }
        if(!expected.isEmpty()) throw new StationModelConfigException("自动水箱缺少分区结果: "+expected);
    }

    static RainfallResponse dailyRain(List<GfsForecastRainValue> rows,String region,int subtype,
                                      LocalDate begin,LocalDate end) {
        return dailyRain(rows, region, subtype, begin, end, false);
    }

    static RainfallResponse dailyRain(List<GfsForecastRainValue> rows,String region,int subtype,
                                      LocalDate begin,LocalDate end, boolean allowMissing) {
        Map<LocalDate,Double> days=new TreeMap<LocalDate,Double>();
        for (GfsForecastRainValue row:rows) {
            if(row.getDataTime()==null) throw new StationModelConfigException("WEA雨量缺少BTIME: "+region);
            LocalDate day=row.getDataTime().toInstant().atZone(ZONE).toLocalDate();
            Double value=row.getAveragePrecipitation();
            if (allowMissing && value == null) continue;
            if(day.isBefore(begin)||day.isAfter(end)||value==null||!Double.isFinite(value)||value<0)
                throw new StationModelConfigException("WEA日雨量无效: "+region+"/"+day);
            // 10051按日值读取；多条时段数据不可未经确认直接求和。
            if(days.put(day,value)!=null)
                throw new StationModelConfigException("WEA同一天存在多条雨量，请核对日值: "+region+"/"+day);
        }
        RainfallResponse rain=new RainfallResponse(); rain.setRegionId(region); rain.setSubtype(subtype);
        for(LocalDate day=begin; !day.isAfter(end); day=day.plusDays(1)) {
            if(!allowMissing && !days.containsKey(day)) throw new StationModelConfigException("WEA缺少预报日雨量: "+region+"/"+day);
            ValuePoint point=new ValuePoint(); point.setTime(day+" 00:00:00");
            point.setValue(days.get(day)); point.setOriginalValue(days.get(day));
            point.setSource(days.containsKey(day) ? "WEA_GFSFORRAIN.AVERPRE" : "MISSING");
            point.setEditable(true); point.setManuallyModified(false); rain.getValues().add(point);
        }
        return rain;
    }
}
