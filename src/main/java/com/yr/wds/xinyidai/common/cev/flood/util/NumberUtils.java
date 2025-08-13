package com.yr.wds.xinyidai.common.cev.flood.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.util.StringUtils;

import java.util.regex.Pattern;

public class NumberUtils {

    /*方法二：推荐，速度最快
     * 判断是否为整数
     * @param str 传入的字符串
     * @return 是整数返回true,否则返回false
     */
    public static boolean isNumeric(String str){

        String reg = "-?[0-9]*\\.?[0-9]+?%*";

        return str.matches(reg);
    }



}
