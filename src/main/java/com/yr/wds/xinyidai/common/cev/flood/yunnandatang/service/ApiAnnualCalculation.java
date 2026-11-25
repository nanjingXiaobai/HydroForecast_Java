package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import java.time.LocalDateTime;
import java.util.*;

/** Excel逐时连续计算：所选时段起算PA=30；9点为新累计雨量时段；单位线为10mm净雨。 */
final class ApiAnnualCalculation {
    private ApiAnnualCalculation() {}
    static List<Map<String,Object>> rainfall(ForecastContracts.InputResponse input) {
        LocalDateTime forecast = ForecastDailyRows.parse(input.getForecastBeginTime());
        LocalDateTime begin = input.getHistoryBeginTime() == null ? forecast : ForecastDailyRows.parse(input.getHistoryBeginTime());
        LocalDateTime end = ForecastDailyRows.parse(input.getForecastEndTime());
        if (end.isBefore(begin) || end.isAfter(begin.plusDays(366)) || forecast.isBefore(begin)
                || forecast.isAfter(end.plusHours(1))) throw error("API计算日期范围无效");
        Map<String,Double> history = new TreeMap<>();
        int stations=0;
        for (ForecastContracts.SeriesResponse series : input.getSeries()) {
            if (!"rainfall".equals(series.getKey())) continue;
            double weight=series.getWeight()==null?1D:series.getWeight();
            if (weight==0) continue;
            if (!Double.isFinite(weight)||weight<0) throw error("API雨量权重无效: "+series.getName());
            stations++;
            Map<String,Double> pointValues=new HashMap<>();
            for (ForecastContracts.ValuePoint point:series.getValues()) {
                if (pointValues.containsKey(point.getTime())) throw error("API历史雨量重复: "+series.getName()+"/"+point.getTime());
                pointValues.put(point.getTime(),point.getValue());
            }
            for(LocalDateTime t=begin;t.isBefore(forecast);t=t.plusHours(1)) {
                String time=t.format(ForecastDailyRows.TIME); Double value=pointValues.get(time);
                if(value==null||!Double.isFinite(value)||value<0) throw error("API所选时段历史雨量缺失或无效: "+series.getName()+"/"+time);
                history.put(time,history.getOrDefault(time,0D)+weight*value);
            }
        }
        if(stations==0)throw error("API没有配置实测雨量站");
        Map<String,Double> future=new HashMap<>();
        if(input.getForecastRainfall()!=null) for(ForecastContracts.ValuePoint point:input.getForecastRainfall().getValues()) {
            if(future.containsKey(point.getTime()))throw error("API预报雨量重复: "+point.getTime());
            future.put(point.getTime(),point.getValue());
        }
        List<Map<String,Object>> rows=new ArrayList<>();
        for(LocalDateTime t=begin;!t.isAfter(end);t=t.plusHours(1)) {
            String time=t.format(ForecastDailyRows.TIME);
            Double value=t.isBefore(forecast)?history.get(time):future.get(time);
            if(value==null||!Double.isFinite(value)||value<0)throw error("API雨量缺失或无效: "+time);
            Map<String,Object> row=new LinkedHashMap<>();row.put("time",time);row.put("rainfall",value);rows.add(row);
        }
        input.setHistoryBeginTime(begin.format(ForecastDailyRows.TIME));
        input.setHistoryEndTime(forecast.minusHours(1).format(ForecastDailyRows.TIME));
        return rows;
    }

    @SuppressWarnings("unchecked")
    static Map<String,Object> calculate(Map<String,Object> payload) {
        List<Map<String,Object>> rain=(List<Map<String,Object>>)payload.get("rainfallSeries");
        List<Number> unit=(List<Number>)payload.get("unit_hydrograph");
        Map<String,Number> months=(Map<String,Number>)payload.get("monthly_k");
        Grid grid=new Grid((List<Map<String,Object>>)payload.get("pr_relation_table"));
        boolean jiaomutang="API_JIAOMUTANG".equals(payload.get("stationCode"));
        double baseFlow=baseFlow(payload);
        double pa=30D,cumulative=0D,previousNet=0D;
        List<Double> net=new ArrayList<>(),cumNet=new ArrayList<>(),cumRain=new ArrayList<>(),pas=new ArrayList<>();
        for(int i=0;i<rain.size();i++) {
            LocalDateTime t=ForecastDailyRows.parse(String.valueOf(rain.get(i).get("time")));
            if(i>0&&!t.equals(ForecastDailyRows.parse(String.valueOf(rain.get(i-1).get("time"))).plusHours(1)))
                throw error("API逐时雨量不连续: "+t);
            if(t.getHour()==9) {
                // 原表前24个雨量行使用起算PA，之后每日9点使用当月K。
                Number k=months.get(String.valueOf(t.getMonthValue()));
                if(k==null||!Double.isFinite(k.doubleValue())||k.doubleValue()<0)throw error("API月系数缺失或无效: "+t.getMonthValue());
                pa=i<24 ? pa+cumulative : Math.min(60D,(pa+cumulative)*k.doubleValue());
                cumulative=0D;previousNet=0D;
            }
            cumulative+=((Number)rain.get(i).get("rainfall")).doubleValue();
            double current=grid.interpolate(cumulative,pa,jiaomutang);
            double value=current-previousNet;
            if(!Double.isFinite(value))throw error("API净雨计算无效: "+t);
            net.add(value);cumNet.add(current);cumRain.add(cumulative);pas.add(pa);previousNet=current;
        }
        List<Double> flow=new ArrayList<>(Collections.nCopies(rain.size(),baseFlow));
        for(int i=0;i<net.size();i++)for(int k=0;k<unit.size() && i+k<flow.size();k++)
            flow.set(i+k,flow.get(i+k)+net.get(i)*unit.get(k).doubleValue()/10D);
        Map<String,Object> result=new LinkedHashMap<>();result.put("net_rainfall_series",net);
        result.put("discharge_series",flow);result.put("pa_series",pas);
        result.put("cumulative_rainfall_series",cumRain);result.put("cumulative_net_rainfall_series",cumNet);
        Map<String,Object> wrapped=new LinkedHashMap<>();wrapped.put("modelResult",result);return wrapped;
    }

    /** Constant base flow in m3/s, added once after rainfall-runoff conversion. */
    static double baseFlow(Map<String,Object> parameters) {
        Object raw=parameters==null?null:parameters.get("base_flow");
        if(raw==null)return 0D;
        double value;
        try { value=Double.parseDouble(String.valueOf(raw)); }
        catch(NumberFormatException ex) { throw error("基流必须是非负有限数值"); }
        if(!Double.isFinite(value)||value<0)throw error("基流必须是非负有限数值");
        return value;
    }

    private static class Grid {
        final TreeSet<Double> ps=new TreeSet<>(),pas=new TreeSet<>();
        final Map<Double,Map<Double,Double>> rows=new HashMap<>();
        Grid(List<Map<String,Object>> points) {
            for(Map<String,Object> point:points) {
                double p=((Number)point.get("p")).doubleValue(),pa=((Number)point.get("pa")).doubleValue(),r=((Number)point.get("r")).doubleValue();
                ps.add(p);pas.add(pa);if(rows.computeIfAbsent(pa,x->new HashMap<>()).put(p,r)!=null)throw error("P-Pa-R曲线网格重复");
            }
            if(ps.size()<2||pas.size()<2)throw error("P-Pa-R曲线网格不完整");
        }
        double value(double p,double pa) {Double v=rows.get(pa).get(p);if(v==null)throw error("P-Pa-R曲线缺少网格点");return v;}
        double interpolate(double p,double pa,boolean legacy) {
            double pStep=ps.higher(ps.first())-ps.first(),paStep=pas.higher(pas.first())-pas.first();
            double pl=Math.floor(p/pStep)*pStep,ph=pl+pStep;
            double al=Math.min(60D,Math.floor(pa/paStep)*paStep),ah=Math.min(60D,Math.floor((pa+paStep)/paStep)*paStep);
            if(!ps.contains(pl)||!ps.contains(ph)||!pas.contains(al)||!pas.contains(ah))throw error("累计雨量或PA超出P-Pa-R曲线范围: P="+p+", PA="+pa);
            double o=value(pl,al),v=value(pl,ah),q=value(ph,al),r=value(ph,ah);
            // 角木塘原表S/T/U列采用固定1/10与I/5；保留原表业务口径。
            double fraction=legacy?0.1D:(pa-al)/paStep;
            double low=o+fraction*(v-o),high=q+fraction*(r-q);
            return low+(legacy?p/5D:(p-pl)/pStep)*(high-low);
        }
    }
    private static StationModelConfigException error(String message){return new StationModelConfigException(message);}
}
