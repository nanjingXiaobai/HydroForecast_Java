package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data;

import java.util.Calendar;

/**
 * 大唐小时数据表的一条只读记录。
 *
 * <p>字段名称与生产工程 {@code HOURDB} 保持对应，便于生产工程现有
 * DBManager 将查询结果直接映射为 Java 对象。</p>
 */
public class DatangHourlyValue {
    private String senId;
    private Calendar time;
    private Double value;
    private Double averageValue;

    public String getSenId() { return senId; }
    public void setSenId(String senId) { this.senId = senId; }
    public Calendar getTime() { return time; }
    public void setTime(Calendar time) { this.time = time; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public Double getAverageValue() { return averageValue; }
    public void setAverageValue(Double averageValue) { this.averageValue = averageValue; }
}
