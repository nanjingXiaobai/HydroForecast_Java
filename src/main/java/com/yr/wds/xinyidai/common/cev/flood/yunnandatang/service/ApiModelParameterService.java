package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.ConditionalOnYunnanDatangFeature;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationDefinition;
import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.ApiCurvePoint;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data.ForecastSourceDataRepository;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用生产DBManager从INFOMODEL装配API模型参数。 */
@Service
@ConditionalOnYunnanDatangFeature
public class ApiModelParameterService {
    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private final ForecastSourceDataRepository repository;

    public ApiModelParameterService(ForecastSourceDataRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> load(StationDefinition station, String forecastTime) {
        String unitCurveName = requireText(station.getCurveName(), "API电站缺少curveName: " + station.getCode());
        String prefix = curvePrefix(unitCurveName);
        List<ApiCurvePoint> relation = required(prefix + ".区间降雨径流曲线", "P-Pa-R");
        List<ApiCurvePoint> emk = required(prefix + ".EMK曲线", "EMK");
        List<ApiCurvePoint> unit = required(unitCurveName, "默认单位线");
        validateUnitLine(prefix, unit);
        int month = month(forecastTime);
        ApiCurvePoint monthPoint = findMonth(emk, month, prefix);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("pa0", 30D);
        result.put("base_flow", 0D);
        Map<String, Double> monthlyK = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) monthlyK.put(String.valueOf(m), findMonth(emk, m, prefix).getV2());
        result.put("monthly_k", monthlyK);
        result.put("calculation_mode", "ANNUAL_EXCEL");
        result.put("k_month", monthPoint.getV2());
        result.put("em_month", monthPoint.getV1());
        result.put("pr_relation_table", relationTable(relation));
        List<Map<String, Object>> unitOptions = new ArrayList<Map<String, Object>>();
        List<Double> defaultOne = unitValues(unit);
        unitOptions.add(unitOption("DEFAULT_1", "默认1", defaultOne, unit.get(0).getCurveId()));
        result.put("unit_hydrograph_code", "DEFAULT_1");
        result.put("unit_hydrograph", defaultOne);
        List<ApiCurvePoint> alternative = repository.queryApiCurvePoints(unitCurveName + "1");
        if (alternative != null && !alternative.isEmpty()) {
            validateUnitLine(prefix + "第二条", alternative);
            unitOptions.add(unitOption("DEFAULT_2", "默认2", unitValues(alternative),
                    alternative.get(0).getCurveId()));
        }
        result.put("unit_hydrograph_options", unitOptions);
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("schema", "INFOMODEL");
        source.put("reservoir", prefix);
        source.put("relationCurveId", relation.get(0).getCurveId());
        source.put("emkCurveId", emk.get(0).getCurveId());
        source.put("unitCurveId", unit.get(0).getCurveId());
        source.put("month", month);
        result.put("parameter_source", source);
        return result;
    }

    /** 页面选择一条默认单位线，Java再转换成Python现有的单数组输入。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyUnitSelection(Map<String, Object> databaseParameters,
                                                   Map<String, Object> requestParameters) {
        Map<String, Object> result = new LinkedHashMap<String, Object>(databaseParameters);
        if (requestParameters != null) result.putAll(requestParameters);
        result.put("base_flow", ApiAnnualCalculation.baseFlow(result));
        Object requested = requestParameters == null ? null : requestParameters.get("unitHydrographCode");
        if (requested == null && requestParameters != null) {
            requested = requestParameters.get("unit_hydrograph_code");
        }
        String code = requested == null ? "DEFAULT_1" : String.valueOf(requested).trim().toUpperCase();
        Object optionsObject = databaseParameters.get("unit_hydrograph_options");
        if (!(optionsObject instanceof List)) throw error("单位线选项装配失败");
        for (Object optionObject : (List<Object>) optionsObject) {
            if (!(optionObject instanceof Map)) continue;
            Map<String, Object> option = (Map<String, Object>) optionObject;
            if (code.equals(option.get("code"))) {
                result.put("unit_hydrograph_code", code);
                result.put("unit_hydrograph", option.get("values"));
                Map<String, Object> source = sourceMetadata(databaseParameters);
                source.put("unitCurveId", option.get("curveId"));
                source.put("unitHydrographCode", code);
                result.put("parameter_source", source);
                result.remove("unitHydrographCode");
                result.remove("unit_hydrograph_options");
                return result;
            }
        }
        throw error("电站没有可用的" + code + "单位线，可选值为" + availableCodes((List<Object>) optionsObject));
    }

    private Map<String, Object> unitOption(String code, String name, List<Double> values, String curveId) {
        Map<String, Object> option = new LinkedHashMap<String, Object>();
        option.put("code", code);
        option.put("name", name);
        option.put("curveId", curveId);
        option.put("values", values);
        return option;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sourceMetadata(Map<String, Object> parameters) {
        Object source = parameters.get("parameter_source");
        return source instanceof Map
                ? new LinkedHashMap<String, Object>((Map<String, Object>) source)
                : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    private List<String> availableCodes(List<Object> options) {
        List<String> result = new ArrayList<String>();
        for (Object option : options) {
            if (option instanceof Map && ((Map<String, Object>) option).get("code") != null) {
                result.add(String.valueOf(((Map<String, Object>) option).get("code")));
            }
        }
        return result;
    }

    private List<Map<String, Object>> relationTable(List<ApiCurvePoint> points) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ApiCurvePoint point : points) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            // C20D固定映射：V0=P、V1=Pa、V2=R，V3不参与计算。
            row.put("p", point.getV0());
            row.put("pa", point.getV1());
            row.put("r", point.getV2());
            result.add(row);
        }
        return result;
    }

    private List<Double> unitValues(List<ApiCurvePoint> points) {
        List<Double> result = new ArrayList<Double>();
        for (ApiCurvePoint point : points) result.add(point.getV1());
        return result;
    }

    private List<ApiCurvePoint> required(String name, String label) {
        List<ApiCurvePoint> result = repository.queryApiCurvePoints(name);
        if (result == null || result.isEmpty()) throw error(label + "曲线不存在或没有明细: " + name);
        return result;
    }

    private void validateUnitLine(String prefix, List<ApiCurvePoint> points) {
        boolean positive = false;
        for (ApiCurvePoint point : points) {
            if (point.getV1() != null && point.getV1() > 0D) positive = true;
        }
        if (points.size() < 2 || !positive) {
            throw error(prefix + "单位线数据无效：至少需要2个点且必须包含正流量；请补齐CURVED数据");
        }
    }

    private ApiCurvePoint findMonth(List<ApiCurvePoint> points, int month, String prefix) {
        for (ApiCurvePoint point : points) {
            if (point.getV0() != null && point.getV0().intValue() == month && point.getV2() != null) return point;
        }
        throw error(prefix + "的EMK曲线缺少" + month + "月数据");
    }

    private int month(String value) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new SimpleDateFormat(DATE_PATTERN).parse(value));
            return calendar.get(Calendar.MONTH) + 1;
        } catch (ParseException ex) {
            throw error("forecastTime格式必须是 " + DATE_PATTERN);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw error(message);
        return value.trim();
    }

    /** curveName保存数据库完整NAME；模型其它曲线仍以点号前的水库名作为前缀定位。 */
    private String curvePrefix(String curveName) {
        int separator = curveName.indexOf(".基础参数及单位");
        if (separator <= 0) throw error("curveName必须是数据库中的完整单位线名称: " + curveName);
        return curveName.substring(0, separator);
    }

    private StationModelConfigException error(String message) {
        return new StationModelConfigException(message);
    }
}
