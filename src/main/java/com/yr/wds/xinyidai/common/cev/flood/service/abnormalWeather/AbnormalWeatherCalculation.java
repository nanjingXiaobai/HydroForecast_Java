package com.yr.wds.xinyidai.common.cev.flood.service.abnormalWeather;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSONObject;
import com.sgepri.ce.db.ResultSetInterface;
import com.sgepri.ce.db.util.DBOpUtil;
import com.sgepri.ce.db.util.DBSplitUtil;
import com.sgepri.ce.util.TimeFunc;
import org.apache.poi.ss.formula.functions.T;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class AbnormalWeatherCalculation {
    private static String[] plantid = "154400,154705,154210,154100".split(",");
    private static String[] powerid = "120,150,170,110400".split(",");

    public void saveAbnormalWeather(String date) {
        Calendar cd = TimeFunc.getPeriodBeg(5, Calendar.getInstance());
        try {
            if (!(date == null || date.equals(""))) {
                cd.setTimeInMillis(TimeFunc.parseDate(date));

            }
            saveDispathAbnormalWeather(cd);
            saveForecastAbnormalWeather(cd);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //来水预报判断重大天气
    public void saveForecastAbnormalWeather(Calendar cd) {
        cd = TimeFunc.getPeriodBeg(5, cd);
        JSONObject sumDatas = new JSONObject();
        Calendar startDate = TimeFunc.add(cd, 5, -1);
        Calendar endDate = TimeFunc.add(cd, 5, 30);
        for (int i = 0; i < plantid.length; i++) {
            try {
                Map<String, Double> avgFlowMap = getAllAvgFlowByTime(startDate, endDate, plantid[i] + "1000", "日", 5);
                //计算前一天实测
                {
                    ResultSetInterface rs = DBSplitUtil.exeTimeDBSelSql("daydb", cd.getTimeInMillis(), cd.getTimeInMillis(), plantid[i] + "1000", "v");

                    if (rs.getRowNum() > 0 && avgFlowMap.containsKey(TimeFunc.getFormatStr(startDate, "MM-dd"))) {
                        int flag = 0;
                        double avg = avgFlowMap.get(TimeFunc.getFormatStr(startDate, "MM-dd"));
                        System.out.println(TimeFunc.getFormatStr(startDate,"yyyy-MM-dd")+":"+((rs.getDouble(0, 0) - avg) / avg));
                        if ((rs.getDouble(0, 0) - avg) / avg < -0.2) flag = -1;
                        saveResult(plantid[i] + "90", startDate, flag);
                        if (sumDatas.containsKey(cd.getTimeInMillis() + "")) {
                            sumDatas.getJSONObject(startDate.getTimeInMillis() + "").put("avg", avg + sumDatas.getJSONObject(startDate.getTimeInMillis() + "").getDoubleValue("avg"));
                            sumDatas.getJSONObject(startDate.getTimeInMillis() + "").put("flow", rs.getDouble(0, 0) + sumDatas.getJSONObject(startDate.getTimeInMillis() + "").getDoubleValue("flow"));
                        } else {
                            JSONObject sumData = new JSONObject();
                            sumData.put("avg", avg);
                            sumData.put("flow", rs.getDouble(0, 0));
                            sumDatas.put(startDate.getTimeInMillis() + "", sumData);
                        }

                    }
                }
                //计算当前后30天
                {
                    ResultSetInterface rs = DBSplitUtil.exeTimeDBSelSql("daydb", cd.getTimeInMillis(), endDate.getTimeInMillis(), plantid[i] + "03011004", "time,v");
                    for (int j = 0; j < rs.getRowNum(); j++) {
                        if (avgFlowMap.containsKey(TimeFunc.getFormatStr(rs.getDate(j, 0), "MM-dd"))) {
                            int flag = 0;
                            double avg = avgFlowMap.get(TimeFunc.getFormatStr(rs.getDate(j, 0), "MM-dd"));
                            System.out.println(TimeFunc.getFormatStr(rs.getDate(j, 0),"yyyy-MM-dd")+":"+((rs.getDouble(j, 1) - avg) / avg));
                            if ((rs.getDouble(j, 1) - avg) / avg < -0.2) flag = -1;
                            saveResult(plantid[i] + "90", rs.getDate(j, 0), flag);
                            if (sumDatas.containsKey(rs.getDate(j, 0).getTimeInMillis() + "")) {
                                sumDatas.getJSONObject(rs.getDate(j, 0).getTimeInMillis() + "").put("avg", avg + sumDatas.getJSONObject(rs.getDate(j, 0).getTimeInMillis() + "").getDoubleValue("avg"));
                                sumDatas.getJSONObject(rs.getDate(j, 0).getTimeInMillis() + "").put("flow", rs.getDouble(j, 1) + sumDatas.getJSONObject(rs.getDate(j, 0).getTimeInMillis() + "").getDoubleValue("flow"));
                            } else {
                                JSONObject sumData = new JSONObject();
                                sumData.put("avg", avg);
                                sumData.put("flow", rs.getDouble(j, 1));
                                sumDatas.put(rs.getDate(j, 0).getTimeInMillis() + "", sumData);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (Map.Entry<String, Object> entry : sumDatas.entrySet()) {
            int flag = 0;
            JSONObject sumData = sumDatas.getJSONObject(entry.getKey());
            if ((sumData.getDoubleValue("flow") - sumData.getDoubleValue("avg")) / sumData.getDoubleValue("avg") < -0.2)
                flag = -1;
            saveResult("15400090", DBOpUtil.getCalendar(Long.parseLong(entry.getKey())), flag);

        }

    }

    //    private JSONObject getForecastAbnormalWeather(Calendar cd){
//
//    }
    //发电能力判断重大天气
    public void saveDispathAbnormalWeather(Calendar cd) {
        long date = TimeFunc.getPeriodBeg(5, cd).getTimeInMillis();
        JSONObject sumDatas = new JSONObject();
        long ltb = TimeFunc.add(date, 5, 3);
        long lte = TimeFunc.add(date, 5, 30);
        for (int i = 0; i < plantid.length; i++) {
            try {
                ResultSetInterface rs = DBSplitUtil.exeTimeDBSelSql("daydb", ltb, lte, powerid[i] + "2110", "time,v");
                JSONObject datas = new JSONObject();
                for (int j=0;j<rs.getRowNum();j++){
                    datas.put(rs.getDate(j,0).getTimeInMillis()+"",rs.getDouble(j,1));
                }
                while (date <= lte) {
                    if (datas.containsKey((date - 24*3600*1000)+"") && datas.containsKey((date - 2*24*3600*1000)+"") && datas.containsKey((date - 3*24*3600*1000)+"")){
                        int flag = 0;
                        double avg = (datas.getDoubleValue((date - 24*3600*1000)+"") + datas.getDoubleValue((date - 2*24*3600*1000)+"") + datas.getDoubleValue((date - 3*24*3600*1000)+""))/3;
                        if ((datas.getDoubleValue(date+""))/avg > 0.15) flag = 1;
                        saveResult(plantid[i]+"91",DBOpUtil.getCalendar(date),flag);
                        if (sumDatas.containsKey(date + "")) {
                            sumDatas.getJSONObject(date + "").put("avg", avg + sumDatas.getJSONObject(date + "").getDoubleValue("avg"));
                            sumDatas.getJSONObject(date + "").put("power", datas.getDoubleValue(date+"") + sumDatas.getJSONObject(date + "").getDoubleValue("power"));
                        } else {
                            JSONObject sumData = new JSONObject();
                            sumData.put("avg", avg);
                            sumData.put("power", datas.getDoubleValue(date+""));
                            sumDatas.put(date + "", sumData);
                        }
                    }
                    date += 24*3600*1000;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        for (Map.Entry<String, Object> entry : sumDatas.entrySet()) {
            int flag = 0;
            JSONObject sumData = sumDatas.getJSONObject(entry.getKey());
            if ((sumData.getDoubleValue("avg") - sumData.getDoubleValue("power")) / sumData.getDoubleValue("avg") > 0.15)
                flag = -1;
            saveResult("15400091", DBOpUtil.getCalendar(Long.parseLong(entry.getKey())), flag);

        }
    }

    private void saveResult(String senid, Calendar time, int flag) {
        String sql = "insert into daydb (senid,time,v,avgv) values (" + senid + "," + TimeFunc.toDbString(time) + "," + flag + "," + flag + ")";
        try {
//            System.out.println(sql);
            DBOpUtil.exeHisDBExeWeb(sql);
        } catch (Exception ie) {
            sql = "update daydb set v = " + flag + " where senid = " + senid + " and time = " + TimeFunc.toDbString(time);
            try {
                DBOpUtil.exeHisDBExeWeb(sql);
            } catch (Exception ue) {
                ie.printStackTrace();
            }
        }
    }

    private Map<String, Double> getAllAvgFlowByTime(Calendar startDate, Calendar endDate, String senid, String timeType, int years) {

        Map<String, Double> map = new HashMap<>();
        ;

        String table = "hourdb";
        String datePattern = "MM-DD HH";
        switch (timeType) {
            case "日": {
                table = "daydb";
                datePattern = "MM-DD";
                startDate = TimeFunc.add(startDate, 5, 1);
                endDate = TimeFunc.add(endDate, 5, 1);
            }
            break;
            case "小时": {
                startDate = TimeFunc.add(startDate, 6, 1);
                endDate = TimeFunc.add(endDate, 6, 1);
            }
            break;
        }


        String sql = "select TO_CHAR(dateadd(" + (timeType.equals("日") ? "day" : "hour") + ",-1,time), '" + datePattern + "') as time,avg(v) as v from  " + table +
                " where senid = " + senid;
        if (years > 0) {
            sql += " and (1 = 0";
            for (int i = 1; i <= years; i++) {
                sql += " or (time >= " + TimeFunc.toDbString(TimeFunc.add(startDate, 0, -i)) + " and time <= " + TimeFunc.toDbString(TimeFunc.add(endDate, 0, -i)) + ")";
            }
            sql += ")";
        } else {
            sql += " and time >= " + TimeFunc.toDbString(startDate) + " and time <= " + TimeFunc.toDbString(endDate);
        }
        sql += " group by TO_CHAR(dateadd(" + (timeType.equals("日") ? "day" : "hour") + ",-1,time), '" + datePattern + "')";

        try {
            ResultSetInterface rs = DBOpUtil.exeHisDBSelSqlWeb(sql);
            if (rs != null) {
                for (int i = 0; i < rs.getRowNum(); i++) {
                    String time = rs.getString(i, 0);
                    Double v = rs.getDouble(i, 1);
                    map.put(time, v);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        return map;
    }
}
