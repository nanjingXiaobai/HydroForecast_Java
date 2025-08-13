package com.yr.wds.xinyidai.common.cev.flood.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @ClassName StringUtil
 * @Description (字符处理工具类)
 * @Author gf
 * @Date 2019/9/5 16:11
 * @Copyright Techmologies gf Co., Ltd. Copyright,All rights reserverd
 */
public class StringUtil {

    /**
     * 判断字符入参是否为空
     * @param params
     * @return boolean
     */
    public static boolean isEmpty(String... params){
        boolean flag = true;
        if(null==params){
            return flag;
        }
        for(String str : params){
            if(null == str || "".equals(str)){
                flag = true;
                break;
            }else{
                flag = false;
            }
        }

        return flag;
    }

    /**
     * 判断不为空
     * @param objs
     * @return
     */
    public static boolean isAllNull(Object... objs){
        boolean flag = true;
        if(null==objs){
            return flag;
        }
        for(Object ob : objs){
            if(null == ob){
                flag = true;
                break;
            }else{
                flag = false;
            }
        }

        return flag;
    }

    /**
     * 判断字符是否为数字
     * @param params
     * @return boolean
     */
    public static boolean isDigit(String... params){
        boolean flag = false;
        if(isEmpty(params)){
            return flag;
        }
        for(String str : params) {
            for (int i = 0; i < str.length(); i++) {
                if (!Character.isDigit(str.charAt(i))){
                    flag = false;
                    break;
                }else {
                    flag = true;
                }
            }
        }
        return flag;
    }

    /**
     * 校验时间字符串格式是否正确 2017-05-08 19:03:31
     * @param params
     * @return
     */
    public static boolean checkTimeStr(String... params){
        boolean flag = false;
        if(isEmpty(params)){
            return flag;
        }
        Pattern p = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}\\s[0-9]{2}:[0-9]{2}:[0-9]{2}");
        for(String str : params) {
            if(p.matcher(str).matches() == false){
                flag = false;
                break;
            }else{
                flag = true;
            }
        }
        return flag;
    }

    /**
     * 将传入的数值保留指定小数位
     * @param number
     * @return
     */
    public static Double keepTwoNumber(Double number,int flag){
        if(null == number){
            return null;
        }
        NumberFormat ddf=NumberFormat.getNumberInstance() ;
        ddf.setMaximumFractionDigits(flag);
        return Double.parseDouble(ddf.format(number));
    }

    /**
     * 将传入的数值保留指定小数位
     * @param number
     * @return
     */
    public static String keepTwoNum(Double number,int flag){
        if(null == number){
            return null;
        }
        NumberFormat ddf=NumberFormat.getNumberInstance() ;
        ddf.setMaximumFractionDigits(flag);
        return ddf.format(number);
    }

    /**
     *
     * @param first
     * @param last
     * @return
     */
    public static Double addDouble(Double first,Double last){
        if(null == first){
            return last;
        }
        if(null == last){
            return first;
        }
        BigDecimal begin = new BigDecimal(first);
        BigDecimal end = new BigDecimal(last);
        return begin.add(end).doubleValue();
    }

    /**
     *
     * @param first
     * @param last
     * @return
     */
    public static Double reduceDouble(Double first,Double last){
        if(null == first){
            return last;
        }
        if(null == last){
            return first;
        }
        BigDecimal begin = new BigDecimal(first);
        BigDecimal end = new BigDecimal(last);
        return begin.subtract(end).doubleValue();
    }

    /**
     * 将一个数均分正负段，如：[-3,-2,-1,0,1,2,3]
     * @param number 初始值
     * @param count 段数
     * @return
     */
    public static List<String> getTwoPartNumber(Double number,Integer count){
        List<String> resulstList = new ArrayList<>();
        if(!isAllNull(number,count)){
            BigDecimal num = new BigDecimal(number);
            BigDecimal cou = new BigDecimal(count);
            BigDecimal result = num.multiply(new BigDecimal(2)).divide(cou,2,BigDecimal.ROUND_DOWN);
            for(int i=0;i<count;i++){
                resulstList.add(String.valueOf(keepTwoNumber(new BigDecimal(i).multiply(result).subtract(num).doubleValue(),2)));

            }
        }
        return resulstList;
    }

    /**
     * 获取基数的上下浮动的随机数值
     * @param baseNum
     * @param floatNum
     * @return
     */
    public static Double floatValue(Double baseNum,Double floatNum){
        return keepTwoNumber(baseNum+(baseNum*floatNum*(Math.random()))*(Math.random()>0.5?1:-1),2);

    }

    public static void main(String[] args) {
        for(int i=0;i<10;i++){
            System.out.println(floatValue(0.90,0.2));
        }
    }
}
