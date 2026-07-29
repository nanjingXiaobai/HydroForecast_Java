package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import java.util.ArrayList;
import java.util.List;

/** 由 XML 定义的逐时段汇总方案。 */
public class AggregationDefinition {
    private String code;
    private String displayName;
    private String operation;
    private final List<AggregationSourceDefinition> sources = new ArrayList<AggregationSourceDefinition>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public List<AggregationSourceDefinition> getSources() { return sources; }
}
