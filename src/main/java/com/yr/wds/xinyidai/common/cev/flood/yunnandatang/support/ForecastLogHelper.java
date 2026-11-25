package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.support;

import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * 云南大唐业务日志格式化工具。
 *
 * <p>统一压缩 SQL 空白并格式化时间、数组等参数，避免直接打印 Calendar 的大量内部字段。
 * 日志只记录查询条件，不在这里读取或输出数据库账号、密码。</p>
 */
public final class ForecastLogHelper {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final int MAX_COLLECTION_ITEMS = 30;

    private ForecastLogHelper() {
    }

    /** 将跨多行 SQL 压缩成适合日志检索的一行。 */
    public static String compactSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }

    public static String formatParameters(Object[] parameters) {
        if (parameters == null) return "[]";
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) result.append(", ");
            result.append(formatValue(parameters[i]));
        }
        return result.append(']').toString();
    }

    public static String formatParameters(List<?> parameters) {
        return parameters == null ? "[]" : formatCollection(parameters);
    }

    /**
     * 生成便于现场复制排查的可执行 SQL 文本。
     *
     * <p>这里只用于日志展示，数据库执行仍使用 PreparedStatement/DBManager 参数绑定。</p>
     */
    public static String renderSql(String sql, List<?> parameters) {
        return renderSql(sql, parameters == null ? null : parameters.toArray());
    }

    public static String renderSql(String sql, Object[] parameters) {
        String compact = compactSql(sql);
        if (parameters == null || parameters.length == 0) return compact;
        StringBuilder result = new StringBuilder();
        int parameterIndex = 0;
        for (int i = 0; i < compact.length(); i++) {
            char current = compact.charAt(i);
            if (current == '?' && parameterIndex < parameters.length) {
                result.append(toSqlLiteral(parameters[parameterIndex++]));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    public static String formatTime(Calendar time) {
        return time == null ? "null" : formatDate(time.getTime());
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Calendar) return formatTime((Calendar) value);
        if (value instanceof Date) return formatDate((Date) value);
        if (value instanceof Collection) return formatCollection((Collection<?>) value);
        if (value.getClass().isArray()) return formatArray(value);
        return String.valueOf(value);
    }

    private static String toSqlLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Calendar) return quote(formatTime((Calendar) value));
        if (value instanceof Date) return quote(formatDate((Date) value));
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String formatCollection(Collection<?> values) {
        StringBuilder result = new StringBuilder("[");
        Iterator<?> iterator = values.iterator();
        int count = 0;
        while (iterator.hasNext() && count < MAX_COLLECTION_ITEMS) {
            if (count > 0) result.append(", ");
            result.append(formatValue(iterator.next()));
            count++;
        }
        if (iterator.hasNext()) result.append(", ... total=").append(values.size());
        return result.append(']').toString();
    }

    private static String formatArray(Object values) {
        StringBuilder result = new StringBuilder("[");
        int length = Array.getLength(values);
        int displayed = Math.min(length, MAX_COLLECTION_ITEMS);
        for (int i = 0; i < displayed; i++) {
            if (i > 0) result.append(", ");
            result.append(formatValue(Array.get(values, i)));
        }
        if (length > displayed) result.append(", ... total=").append(length);
        return result.append(']').toString();
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat(DATE_PATTERN).format(date);
    }
}
