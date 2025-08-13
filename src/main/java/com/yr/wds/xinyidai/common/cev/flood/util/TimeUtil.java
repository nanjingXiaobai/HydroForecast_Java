package com.yr.wds.xinyidai.common.cev.flood.util;


import com.yr.wds.xinyidai.common.cev.flood.common.constant.TimeConstant;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


/**
 * @author wdp
 * @version 1.0
 * @data 2024/5/14 15:14
 */
public class TimeUtil {

    private static final String SECOND_FORMATTER = "yyyy-MM-dd HH:mm:ss";
    private static final String MINUTE_FORMATTER = "yyyy-MM-dd HH:mm";
    private static final String HOUR_FORMATTER = "yyyy-MM-dd HH";
    private static final String DATE_FORMATTER = "yyyy-MM-dd";
    private static final String MONTH_FORMATTER = "yyyy-MM";
    private static final String YEAR_FORMATTER = "yyyy";
    private static final int RTEMS     =0;     //实时
    private static final int HOURDB    =1;     //小时
    private static final int DAYDB     =2;     //日
    private static final int DAY10DB   =3;     //旬
    private static final int MONTHDB   =4;     //月
    private static final int QUARTERDB =5;     //季
    private static final int YEARDB    =6;     //年

    /**
     * 将时间字符串转化为Calendar
     * @param str
     * @return
     */
    public static Calendar sectionName(String str,DateFormatter dateFormatter){
        Calendar cal = Calendar.getInstance();
        if(!StringUtil.isEmpty(str)){
            cal.setTime(dateFormatter.parseDate(str));
        }
        return cal;
    }




    /**
     * 将Calendar转化为时间字符串
     * @param calendar
     * @return
     */
    public static String CalendarToString(Calendar calendar,DateFormatter dateFormatter){
        String dataStr = "";
        if(null!=calendar){
            dataStr = dateFormatter.format(calendar.getTime());
        }
        return dataStr;
    }

    /**
     * 将Calendar转化为时间字符串
     * @param calendar
     * @return
     */
    public static String CalendarToStringByFormat(Calendar calendar,String format){
        String strTime = "";
        if(!StringUtil.isAllNull(calendar,format)){
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            strTime = sdf.format(calendar.getTime());
        }
        return strTime;
    }

    /**
     * 将时间字符串转化为Calendar
     * @param str
     * @return
     */
    public static Calendar strToCalendar(String str,String timeType)  {
        Calendar cal = Calendar.getInstance();
        try {
            if(!StringUtil.isEmpty(str,timeType)){
                if(timeType.equals(Constans.MEDLONGTIME)){
                    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMATTER);
                    cal.setTime(sdf.parse(str));

                }else if(timeType.equals(Constans.MONTHTIME) || timeType.equals("5") ){
                    SimpleDateFormat sdf = new SimpleDateFormat(MONTH_FORMATTER);
                    cal.setTime(sdf.parse(str));
                }else{
                    SimpleDateFormat sdf = new SimpleDateFormat(MINUTE_FORMATTER);
                    cal.setTime(sdf.parse(str));
                }

            }
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return cal;
    }

    /**
     * 将时间字符串转化为Calendar
     * @param str
     * @return
     */
    public static Calendar strToCalendarByFormat(String str,int timeType)  {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = null;
        try {
            if(!StringUtil.isAllNull(str,timeType)){
                if(timeType==RTEMS){
                    sdf = new SimpleDateFormat(MINUTE_FORMATTER);
                }else if(timeType==HOURDB){
                    sdf = new SimpleDateFormat(HOUR_FORMATTER);
                }else if(timeType == DAYDB || timeType == DAY10DB){
                    sdf = new SimpleDateFormat(DATE_FORMATTER);
                }else if(timeType == MONTHDB || timeType == QUARTERDB){
                    sdf = new SimpleDateFormat(MONTH_FORMATTER);
                }else if(timeType == YEARDB){
                    sdf = new SimpleDateFormat(YEAR_FORMATTER);
                }else{
                    sdf = new SimpleDateFormat(SECOND_FORMATTER);
                }
                cal.setTime(sdf.parse(str));
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return cal;
    }



    // 获取当前时间前一小时和当天0点的字符串
    public static String[] getTimeRangeString(LocalDateTime now) {
        // 创建当天0点的时间
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        LocalDateTime lastDay = startOfDay.plusDays(-1).toLocalDate().atStartOfDay();

        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TimeConstant.DATE_YYYYMMDDHHMMSS);

        // 将时间转换为字符串
        String startOfDayStr = startOfDay.format(formatter);
        String nowStr = now.format(formatter);
        String lastStr = lastDay.format(formatter);

        // 返回包含两个时间点的字符串数组
        return new String[] {startOfDayStr, nowStr, lastStr, startOfDayStr};
    }

    public static String getTodayZeroDate() {
        LocalDateTime now = LocalDateTime.now();

        // 将时间设置为0点
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();

        // 格式化日期时间
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDateTime = startOfDay.format(formatter);
        return formattedDateTime;
    }

    public static String[] getMidnightTimes(String inputDateTime) {
        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 解析输入的日期时间字符串
        LocalDateTime input = LocalDateTime.parse(inputDateTime, formatter);

        // 获取当前天的0点时间
        LocalDateTime todayMidnight = input.toLocalDate().atStartOfDay();

        // 获取次日的0点时间
        LocalDateTime tomorrowMidnight = todayMidnight.plusDays(1);

        // 将时间格式化为字符串，并放入数组中
        String[] result = {
                todayMidnight.format(formatter),
                tomorrowMidnight.format(formatter)
        };

        return result;
    }


    public static String getZeroData(String dateTimeStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 解析字符串为LocalDateTime对象
        LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr, formatter);

        // 设置时间为0点
        LocalDateTime midnight = dateTime.toLocalDate().atStartOfDay();

        // 格式化回字符串
        String midnightStr = midnight.format(formatter);

        return midnightStr;
    }

    public static String getTime(String time, String timeZone, int num) {

        LocalDate timeDate = LocalDate.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LocalDate dayBefore = null;
        String dayBeforeStr = null;
        switch (timeZone) {
            case "YEAR":
                // 计算前一天的日期
                dayBefore = timeDate.plusYears(num);
                // 将前一天的日期转换回字符串
                dayBeforeStr = dayBefore.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return dayBeforeStr;
            case "DAY":
                // 计算前一天的日期
                dayBefore = timeDate.plusDays(num);
                // 将前一天的日期转换回字符串
                dayBeforeStr = dayBefore.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return dayBeforeStr;
        }
        return null;
    }

    /**
     * 将Calendar转化为时间字符串
     * @param calendar
     * @return
     */
    public static String CalendarToStringByTimeType(Calendar calendar,int timeType){
        String strTime = "";
        SimpleDateFormat sdf = null;
        if(!StringUtil.isAllNull(calendar,timeType)){
            if(timeType==RTEMS){
                sdf = new SimpleDateFormat(MINUTE_FORMATTER);
                strTime = sdf.format(calendar.getTime());
            }else if(timeType==HOURDB){
                sdf = new SimpleDateFormat(HOUR_FORMATTER);
                strTime = sdf.format(calendar.getTime());
            }else if(timeType == DAYDB || timeType == DAY10DB){
                sdf = new SimpleDateFormat(DATE_FORMATTER);
                strTime = sdf.format(calendar.getTime());
            }else if(timeType == MONTHDB || timeType == QUARTERDB){
                sdf = new SimpleDateFormat(MONTH_FORMATTER);
                strTime = sdf.format(calendar.getTime());
            }else if(timeType == YEARDB){
                sdf = new SimpleDateFormat(YEAR_FORMATTER);
                strTime = sdf.format(calendar.getTime());
            }

        }
        return strTime;
    }



}
