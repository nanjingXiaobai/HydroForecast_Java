package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** 统一页面结果精度：图表和表格中的所有计算数值均保留一位小数。 */
final class ForecastResultPrecision {
    private ForecastResultPrecision() {
    }

    /** 只处理页面图表和表格，不改变红色模型参数及 Python 计算使用的原始精度。 */
    @SuppressWarnings("unchecked")
    static void applyResultData(Map<String, Object> resultData) {
        if (resultData == null) return;
        apply(resultData.get("chartData"));
        Object basinDetails = resultData.get("basinDetails");
        if (basinDetails instanceof List) {
            for (Object basin : (List<Object>) basinDetails) {
                if (basin instanceof Map) apply(((Map<String, Object>) basin).get("tableData"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void apply(Object value) {
        if (value instanceof List) {
            for (Object item : (List<Object>) value) apply(item);
            return;
        }
        if (!(value instanceof Map)) return;
        Map<String, Object> row = (Map<String, Object>) value;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object fieldValue = entry.getValue();
            if (fieldValue instanceof Number) {
                // apply 只接收页面结果数据，因此嵌套的蓄水深、孔流、下渗等中间过程也统一处理。
                entry.setValue(decimal(fieldValue, 1));
            } else {
                apply(fieldValue);
            }
        }
    }

    /** 结果管理中的最大流量也必须保持一位小数。 */
    static BigDecimal flow(Object value) {
        return value == null ? null : decimal(value, 1);
    }

    private static BigDecimal decimal(Object value, int scale) {
        return new BigDecimal(String.valueOf(value)).setScale(scale, RoundingMode.HALF_UP);
    }
}
