package com.yr.wds.xinyidai.common.cev.flood.util;

import org.springframework.lang.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Supplier;

/**
 * 日期格式化枚举类
 *
 * @author zxh
 * 2019-08-29
 */
public enum DateFormatter {
    /**
     * 年月日时分秒毫秒
     */
    MILLI_SECOND_FORMATTER("yyyy-MM-dd HH:mm:ss.SSSSSS", LocalDateTime.class),
    /**
     * 年月日时分秒
     */
    SECOND_FORMATTER("yyyy-MM-dd HH:mm:ss", LocalDateTime.class),
    /**
     * 年月日时
     */
    HOUR_FORMATTER("yyyy-MM-dd HH", LocalDateTime.class),
    /**
     * 月日时
     */
    MONTH_HOUR_FORMATTER("MM-dd HH", LocalDateTime.class),
    /**
     * 年月日时分
     */
    MINUTE_FORMATTER("yyyy-MM-dd HH:mm", LocalDateTime.class),
    /**
     * 月日时分秒
     */
    MONTH_FORMATTER("MM-dd HH:mm:ss", LocalDateTime.class),
    /**
     * 年月日
     */
    DATE_FORMATTER("yyyy-MM-dd", LocalDate.class),
    /**
     * 时分秒
     */
    TIME_FORMATTER("HH:mm:ss", LocalTime.class),
    /**
     * 年月
     */
    YEAR_MONTH_FORMATTER("yyyy-MM", LocalDate.class),
    /**
     * 年月
     */
    MONTH_DAY_FORMATTER("MM-dd", LocalDate.class),
    /**
     * 年
     */
    YEAR_ONLY_FORMATTER("yyyy", LocalDate.class),
    /**
     * 仅包含日
     */
    DAY_ONLY_FORMATTER("dd", LocalDate.class),
    /**
     * 时分
     */
    HOUR_MINUTE_FORMATTER("HH:mm", LocalTime.class);

    private final DateTimeFormatter formatter;
    private final SimpleDateFormat format;
    private final Class<? extends TemporalAccessor> dateClass;
    private final String regex;

    DateFormatter(final String pattern, final Class<? extends TemporalAccessor> dateClass) {
        this.formatter = DateTimeFormatter.ofPattern(pattern);
        this.format = new SimpleDateFormat(pattern);
        this.dateClass = dateClass;
        this.regex = pattern.replaceAll("[a-zA-z]", "[0-9]");
    }

    /**
     * <pre>
     * 根据时间字符串在预设的几个格式中自动匹配合适的格式化对象
     * 支持如下格式
     * yyyy-MM-dd HH:mm:ss
     * yyyy-MM-dd
     * yyyy-MM
     * yyyy
     * dd
     * HH:mm:ss
     * HH:mm
     * </pre>
     *
     * @param time 时间字符串
     * @return DataFormatter枚举对象
     * @throws FormatterNotFoundException 找不到合适的格式化对象，可根据需要添加格式化对象
     */
    public static DateFormatter matchFormatterByTime(final String time) {
        return Arrays.stream(DateFormatter.values()).filter(value -> time.trim().matches(value.regex)).findFirst().orElseThrow(() -> new FormatterNotFoundException(time));
    }

    /**
     * <pre>
     * 根据提供的时间字符串在预设的几个格式中自动匹配合适的格式化对象
     * 支持如下格式
     * yyyy-MM-dd HH:mm:ss
     * yyyy-MM-dd
     * yyyy-MM
     * yyyy
     * dd
     * HH:mm:ss
     * HH:mm
     * </pre>
     *
     * @param timeSupplier 时间字符串提供器
     * @return DataFormatter枚举对象
     * @throws FormatterNotFoundException 找不到合适的格式化对象，可根据需要添加格式化对象
     */
    public static DateFormatter matchFormatterByTime(final Supplier<String> timeSupplier) {
        return DateFormatter.matchFormatterByTime(timeSupplier.get());
    }

    /**
     * 格式化时间为指定格式
     *
     * @param date 时间对象
     * @return 指定格式的时间字符串
     */
    public String format(@Nullable final Date date) {
        return date != null ? this.format.format(date) : "";
    }

    /**
     * 格式化时间为指定格式
     *
     * @param date 时间对象
     * @return 指定格式的时间字符串
     */
    public String format(@Nullable final String date) {
        if (date == null) {
            return "";
        }
        final DateFormatter formatter = matchFormatterByTime(date);
        if (formatter == this) {
            return date;
        }
        return this.format.format(formatter.parseDate(date));
    }

    /**
     * 格式化时间为指定格式
     *
     * @param dateSupplier 时间对象提供器
     * @return 指定格式的时间字符串
     */
    public String format(@Nullable final Supplier<Date> dateSupplier) {
        return this.format(dateSupplier.get());
    }

    /**
     * 格式化时间为指定格式
     *
     * @param dateTime 时间对象
     * @return 指定格式的时间字符串
     */
    public String format(@Nullable final Calendar dateTime) {
        return dateTime != null ? this.format(dateTime::getTime) : "";
    }

    /**
     * 格式化时间为指定格式
     *
     * @param dateTime 时间对象
     * @return 指定格式的时间字符串
     */
    public String format(@Nullable final TemporalAccessor dateTime) {
        return dateTime != null ? this.formatter.format(dateTime) : "";
    }

    /**
     * 将时间字符串解析成<code>java.util.Date</code>对象
     *
     * @param date 时间字符串
     * @return <code>java.util.Date</code>对象
     */
    public Date parseDate(final String date) {
        try {
            return this.format.parse(date.trim());
        } catch (final ParseException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e.getCause());
        }
    }

    /**
     * 将时间字符串解析成<code>java.util.Date</code>对象
     *
     * @param dateSupplier 时间字符串提供器
     * @return <code>java.util.Date</code>对象
     */
    public static Date parseDate(final Supplier<String> dateSupplier) {
        final DateFormatter formatter = matchFormatterByTime(dateSupplier.get());
        return formatter.parseDate(dateSupplier.get());
    }

    public static Calendar parseCalendar(final Supplier<String> dateSupplier) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(DateFormatter.parseDate(dateSupplier));
        return instance;
    }

    public Calendar parseCalendar(final String date) {
        Calendar instance = Calendar.getInstance();
        instance.setTime(this.parseDate(date));
        return instance;
    }

    /**
     * 将时间字符串解析成<code>java.time.LocalDate/LocalTime/LocalDateTime</code>对象
     *
     * @param date 时间字符串
     * @return <code>java.time.temporal.TemporalAccessor</code>子对象
     */
    @SuppressWarnings("unchecked")
    public <T extends TemporalAccessor> T parseLocal(final String date) {
        if (this.dateClass.equals(LocalDate.class)) {
            return (T) LocalDate.parse(date.trim(), this.formatter);
        } else if (dateClass.equals(LocalTime.class)) {
            return (T) LocalTime.parse(date.trim(), this.formatter);
        } else {
            return (T) LocalDateTime.parse(date.trim(), this.formatter);
        }
    }

    /**
     * 将时间字符串解析成<code>java.time.LocalDate/LocalTime/LocalDateTime</code>对象
     *
     * @param dateSupplier 时间字符串提供器
     * @return <code>java.time.LocalDate/LocalTime/LocalDateTime</code>对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T parseLocal(final Supplier<String> dateSupplier) {
        final DateFormatter formatter = matchFormatterByTime(dateSupplier.get());
        return formatter.parseLocal(dateSupplier.get());
    }

    /**
     * 判断时间字符串格式是否符合某个格式化对象
     *
     * @param time 时间字符串
     * @return 是否符合
     */
    public boolean matches(final String time) {
        return time != null && time.trim().matches(regex);
    }

    /**
     * 判断时间字符串格式是否符合某个格式化对象
     *
     * @param timeSupplier 时间字符串提供器
     * @return 是否符合
     */
    public boolean matches(final Supplier<String> timeSupplier) {
        return timeSupplier != null && this.matches(timeSupplier.get());
    }

    /**
     * 将时间字符串显示为上中下旬的字符串
     *
     * @param time 时间字符串
     */
    public static String tenDayTimeStr(final String time) {
        final DateFormatter formatter = DateFormatter.matchFormatterByTime(time);
        final Date date = formatter.parseDate(time);
        switch (formatter) {
            // 年月日全的
            case MILLI_SECOND_FORMATTER:
            case SECOND_FORMATTER:
            case HOUR_FORMATTER:
            case MINUTE_FORMATTER:
            case DATE_FORMATTER: {
                final String format = DATE_FORMATTER.format(date);
                final String[] split = format.split("-");
                final int s = Integer.parseInt(split[2]);
                if (s < 11) {
                    split[2] = "上旬";
                } else if (s < 21) {
                    split[2] = "中旬";
                } else {
                    split[2] = "下旬";
                }
                return String.format("%s年%s月%s", split[0], split[1], split[2]);
            }
            // 月
            case MONTH_DAY_FORMATTER:
            case MONTH_HOUR_FORMATTER: {
                final String format = MONTH_DAY_FORMATTER.format(date);
                final String[] split = format.split("-");
                final int s = Integer.parseInt(split[1]);
                if (s < 11) {
                    split[1] = "上旬";
                } else if (s < 21) {
                    split[1] = "中旬";
                } else {
                    split[1] = "下旬";
                }
                return String.format("%s月%s", split[0], split[1]);
            }
            // 年和月
            case YEAR_MONTH_FORMATTER:
                return time + "上旬";
            default:
                throw new IllegalArgumentException("该格式的日期不支持显示上中下旬：" + time);
        }
    }

    /**
     * 将时间显示为上中下旬的字符串
     *
     * @param time 时间字符串
     */
    public static String tenDayTimeStr(final Date time) {
        final String format = DATE_FORMATTER.format(time);
        final String[] split = format.split("-");
        final int s = Integer.parseInt(split[2]);
        if (s < 11) {
            split[2] = "上旬";
        } else if (s < 21) {
            split[2] = "中旬";
        } else {
            split[2] = "下旬";
        }
        return String.format("%s年%s月%s", split[0], split[1], split[2]);
    }

    /**
     * 将时间字符串显示为月的字符串
     *
     * @param time 时间字符串
     */
    public static String monthTimeStr(final String time) {
        final DateFormatter formatter = DateFormatter.matchFormatterByTime(time);
        final Date date = formatter.parseDate(time);
        switch (formatter) {
            // 年月日全的
            case MILLI_SECOND_FORMATTER:
            case SECOND_FORMATTER:
            case HOUR_FORMATTER:
            case MINUTE_FORMATTER:
            case YEAR_MONTH_FORMATTER:
            case DATE_FORMATTER: {
                final String format = YEAR_MONTH_FORMATTER.format(date);
                final String[] split = format.split("-");
                return String.format("%s年%s月", split[0], split[1]);
            }
            // 月
            case MONTH_DAY_FORMATTER:
            case MONTH_HOUR_FORMATTER: {
                final String format = YEAR_MONTH_FORMATTER.format(date);
                final String[] split = format.split("-");
                return String.format("%s月", split[1]);
            }
            default:
                throw new IllegalArgumentException("该格式的日期不支持显示上中下旬：" + time);
        }
    }

    /**
     * 将时间显示为上中下旬的字符串
     *
     * @param time 时间字符串
     */
    public static String monthTimeStr(final Date time) {
        final String format = YEAR_MONTH_FORMATTER.format(time);
        final String[] split = format.split("-");
        return String.format("%s年%s月", split[0], split[1]);
    }

    /**
     * 将时间字符串显示为月的字符串
     *
     * @param time 时间字符串
     */
    public static String yearStr(final String time) {
        final DateFormatter formatter = DateFormatter.matchFormatterByTime(time);
        final Date date = formatter.parseDate(time);
        switch (formatter) {
            // 包含年的
            case MILLI_SECOND_FORMATTER:
            case SECOND_FORMATTER:
            case HOUR_FORMATTER:
            case MINUTE_FORMATTER:
            case YEAR_MONTH_FORMATTER:
            case YEAR_ONLY_FORMATTER:
            case DATE_FORMATTER: {
                final String format = YEAR_ONLY_FORMATTER.format(date);
                return String.format("%s年", format);
            }
            default:
                throw new IllegalArgumentException("该格式的日期不支持显示上中下旬：" + time);
        }
    }

    /**
     * 将时间显示为上中下旬的字符串
     *
     * @param time 时间字符串
     */
    public static String yearStr(final Date time) {
        final String format = YEAR_ONLY_FORMATTER.format(time);
        return String.format("%s年", format);
    }

    private static class FormatterNotFoundException extends RuntimeException {
        private FormatterNotFoundException(final String time) {
            super("未找到合适的格式化对象，如果需要，可在\"com.nari.wds.common.utils.DateFormatter\"枚举类中添加需要的对象：" + time);
        }
    }
}
