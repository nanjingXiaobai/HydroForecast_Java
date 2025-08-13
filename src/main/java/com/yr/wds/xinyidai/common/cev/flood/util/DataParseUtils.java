package com.yr.wds.xinyidai.common.cev.flood.util;

import java.util.ArrayList;

/**
 * @author xcw
 * @version 1.0
 * @date 2024/8/23 13:49
 * @description: TODO
 */
public class DataParseUtils {


    public static double[] convertArrayListToObjectArray(ArrayList<Object> list) throws Exception {
        double[] doubleArray = new double[list.size()];

        for (int i = 0; i < list.size(); i++) {
            Object obj = list.get(i);
            if (obj instanceof Number) {
                doubleArray[i] = ((Number) obj).doubleValue();
            } else if (obj instanceof String) {
                try {
                    doubleArray[i] = Double.parseDouble((String) obj);
                } catch (NumberFormatException e) {
                    throw new Exception("无法将列表中的元素转换为double: " + obj);
                }
            } else {
                throw new Exception("列表中包含无法转换为double的元素: " + obj);
            }
        }

        return doubleArray;
    }

    public static int[] convertArrayListToIntArray(ArrayList<Object> list) throws Exception {
        int[] intArray = new int[list.size()];

        for (int i = 0; i < list.size(); i++) {
            Object obj = list.get(i);
            if (obj instanceof Integer) {
                intArray[i] = (Integer) obj;
            } else if (obj instanceof Number) {
                intArray[i] = ((Number) obj).intValue();
            } else if (obj instanceof String) {
                try {
                    intArray[i] = Integer.parseInt((String) obj);
                } catch (NumberFormatException e) {
                    throw new Exception("无法将列表中的元素转换为int: " + obj);
                }
            } else {
                throw new Exception("列表中包含无法转换为int的元素: " + obj);
            }
        }

        return intArray;
    }
}
