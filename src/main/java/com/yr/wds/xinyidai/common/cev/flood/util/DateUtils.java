package com.yr.wds.xinyidai.common.cev.flood.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wdp
 * @version 1.0
 * @data 2023/8/17 15:05
 */
public class DateUtils {

    public static List<String> getMonth(String startTime, String endTime){

        List<DateTime> dateList=  DateUtil.rangeToList(DateUtil.parseDate( startTime),DateUtil.parseDate(endTime) , DateField.MONTH);

        return dateList.stream().map(x->DateUtil.formatDate(x)).collect(Collectors.toList());
    }

    public static List<String> getHour(String startTime, String endTime){

            List<String> hourList = new ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            LocalDateTime current = LocalDateTime.parse(startTime, formatter);
            while (current.isBefore(LocalDateTime.parse(endTime, formatter))) {
                // 获取当前时间的整点小时
                String hour = current.format(formatter);

                // 将整点小时添加到列表中
                hourList.add(hour);

                // 将时间增加1小时
                current = current.plusHours(1);
            }

            return hourList;
    }

    public static List<String> getDays(String startTime, String endTime) {

        // 返回的日期集合
        List<String> days = new ArrayList<String>();

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date start = dateFormat.parse(startTime);
            Date end = dateFormat.parse(endTime);

            Calendar tempStart = Calendar.getInstance();
            tempStart.setTime(start);

            Calendar tempEnd = Calendar.getInstance();
            tempEnd.setTime(end);
            tempEnd.add(Calendar.DATE, +1);// 日期加1(包含结束)
            while (tempStart.before(tempEnd)) {
                days.add(dateFormat.format(tempStart.getTime()));
                tempStart.add(Calendar.DAY_OF_YEAR, 1);
            }

        } catch (ParseException e) {
            e.printStackTrace();
        }

        return days;
    }

    /**
     * 获取每天96点时间
     * @return
     */
    public static List<String> getTimeList(){

        List<String> result=new ArrayList<>();

        List<String> minList= Arrays.asList("00,15,30,45".split(","));

        for(int i=0;i<24;i++){

            for(int j=0;j<minList.size();j++){

                String min=minList.get(j);
                    if(i<=9){
                        result.add("0"+i+":"+min);
                    }else{
                        result.add(i+":"+min);
                    }
            }

        }

        String first=result.get(0);

        CollUtil.removeAny(result,first);
        result.add(first);


        return result;

    }

    public static List<String> getAllMonth( String year){

        List<String> monthList=new ArrayList<>();

       for(int i=1;i<=12;i++){
            if(i<10){
                monthList.add(year+"-0"+i);
            }else{
                monthList.add(year+"-"+i);
            }

       }


        return monthList;

    }

    public static String yearAdd(String date,int num){
        String nowYear = date.substring(0, 4);
        return String.valueOf(Integer.parseInt(nowYear) + num);
    }

    public static int getBetweenDays(String bTime,String eTime){
        bTime = bTime.substring(0,10)+" 00:00:00";
        eTime = eTime.substring(0,10)+" 00:00:00";
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime bt = LocalDateTime.parse(DateFormatter.SECOND_FORMATTER.format(bTime), df);
        LocalDateTime et = LocalDateTime.parse(DateFormatter.SECOND_FORMATTER.format(eTime), df);
        return (int) Duration.between(bt, et).toDays();
    }

    /**
     * 每15分钟计算
     * @param startTime
     * @param endTime
     * @return
     */
    public static List<String> get15Minute(String startTime, String endTime) {

        List<String> hourList = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime current = LocalDateTime.parse(startTime, formatter);
        while (current.isBefore(LocalDateTime.parse(endTime, formatter))) {
            // 获取当前时间的整点分钟
            String hour = current.format(formatter);

            // 将整点小时添加到列表中
            hourList.add(hour);

            // 将时间增加1小时
            current = current.plusMinutes(15L);
        }

        return hourList;
    }


}
