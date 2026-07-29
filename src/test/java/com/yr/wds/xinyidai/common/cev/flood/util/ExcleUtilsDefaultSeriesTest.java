package com.yr.wds.xinyidai.common.cev.flood.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcleUtilsDefaultSeriesTest {

    @Test
    void shouldKeepAchievementColumnsAndOnlySkipExplicitlyHiddenColumns() throws Exception {
        JSONArray headers = JSON.parseArray("["
                + "{\"prop\":\"Time\",\"label\":\"\\u65f6\\u95f4\"},"
                + "{\"label\":\"\\u5b9e\\u6d4b\\u6d41\\u91cf\",\"children\":["
                + "{\"prop\":\"observedFlow\",\"showName\":\"\\u5b9e\\u6d4b\\u6d41\\u91cf\",\"label\":\"m3/s\"}]},"
                + "{\"label\":\"\\u9884\\u62a5\\u6d41\\u91cf\",\"children\":["
                + "{\"prop\":\"forecastFlow\",\"showName\":\"forecast-flow-series\",\"label\":\"m3/s\"}]},"
                + "{\"label\":\"\\u8c03\\u5ea6\\u9650\\u5236\",\"children\":["
                + "{\"prop\":\"dispatchLimit\",\"label\":\"m3/s\",\"showCol\":false}]}"
                + "]");

        Method method = ExcleUtils.class.getDeclaredMethod("resolveColumns", JSONArray.class, JSONArray.class);
        method.setAccessible(true);
        List<?> columns = (List<?>) method.invoke(new ExcleUtils(), headers, new JSONArray());
        List<String> props = new ArrayList<>();
        for (Object column : columns) {
            Field prop = column.getClass().getDeclaredField("prop");
            prop.setAccessible(true);
            props.add((String) prop.get(column));
        }

        assertEquals(Arrays.asList("Time", "observedFlow", "forecastFlow"), props);
    }

    @Test
    void shouldKeepFiniteValuesForChartClipping() throws Exception {
        Method method = ExcleUtils.class.getDeclaredMethod(
                "isDrawableChartValue", Double.class);
        method.setAccessible(true);
        ExcleUtils utils = new ExcleUtils();

        assertTrue((Boolean) method.invoke(utils, -1D));
        assertFalse((Boolean) method.invoke(utils, Double.NaN));
        assertFalse((Boolean) method.invoke(utils, Double.POSITIVE_INFINITY));
        assertTrue((Boolean) method.invoke(utils, 0D));
    }

    @Test
    void shouldScaleLineAxisLikeFrontend() throws Exception {
        Object waterRange = invokeAxisRange(Arrays.asList(84D, 86D), null);
        assertEquals(83.8D, readRangeValue(waterRange, "min"), 0.0001D);
        assertEquals(86.2D, readRangeValue(waterRange, "max"), 0.0001D);

        Object flowRange = invokeAxisRange(Arrays.asList(-10D, 100D), 500D);
        assertEquals(0D, readRangeValue(flowRange, "min"), 0.0001D);
        assertEquals(500D, readRangeValue(flowRange, "max"), 0.0001D);
    }

    @SuppressWarnings("unchecked")
    private Object invokeAxisRange(List<Double> values, Double fixedMaximum) throws Exception {
        Class<?> seriesClass = Class.forName(ExcleUtils.class.getName() + "$ChartSeries");
        Constructor<?> constructor = seriesClass.getDeclaredConstructor(String.class, Color.class, String.class);
        constructor.setAccessible(true);
        Object series = constructor.newInstance("series", Color.GREEN, "flow");
        Field valuesField = seriesClass.getDeclaredField("values");
        valuesField.setAccessible(true);
        ((List<Double>) valuesField.get(series)).addAll(values);

        Method method = ExcleUtils.class.getDeclaredMethod("calculateAxisRange", List.class, Double.class);
        method.setAccessible(true);
        return method.invoke(new ExcleUtils(), Arrays.asList(series), fixedMaximum);
    }

    private double readRangeValue(Object range, String fieldName) throws Exception {
        Field field = range.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getDouble(range);
    }

}
