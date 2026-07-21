package com.yr.wds.xinyidai.common.cev.flood.shortForecast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.nari.slsd.hps.dbmgr.conf.DBManager;
import com.nari.slsd.hydro.backend.unified.constant.EnumConstant;
import com.nari.slsd.hydro.backend.unified.dobj.ADXLD;
import com.nari.slsd.hydro.backend.unified.dobj.HFIdx;
import com.yr.wds.xinyidai.common.cev.flood.util.DateFormatter;
import com.yr.wds.xinyidai.common.cev.flood.util.Message;
import com.yr.wds.xinyidai.common.cev.flood.util.TimeUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 乌江项目现场定制的站点预报过程查看接口。
 *
 * <p>只读 HFIDX / ADXLD，不复用或改动原结果管理保存、删除等业务方法。</p>
 */
public class WujiangStationProcessResult {

    private static final Logger LOGGER = LoggerFactory.getLogger(WujiangStationProcessResult.class);
    private static final String CONFIG_PATH = System.getProperty("user.dir") + "/conf/forecast/stationForecastProcess.json";
    private static final String ALL_MODEL = "全部模型方案";
    private static final String ALL_FORECASTER = "全部";
    private static final int DEFAULT_MAX_PLAN_COUNT = 200;
    private static final int DEFAULT_CHART_MAX_LINES = 6;
    private static final String HFIDX_FIELDS = "ADID,YBT,YBDT,YBNUM,TIMETYPE,TIMESPAN,BFP,BADJ,HFFL,FXT,HL_1,HL_3,HL_5,HL_7,S1,S2,BREC,FPAREA,YBR,BPUB,SCHNAME,FOREOBJNAME,SECNAME,MODELNAME,RESTYPE,FORECASTTYPE";

    private WujiangStationProcessResult() {
    }

    public static Message config(JSONObject params) {
        JSONObject config = loadConfig();
        JSONObject data = new JSONObject(true);
        data.put("page", config.getJSONObject("page"));
        data.put("timeTypes", config.getJSONArray("timeTypes"));
        data.put("groups", config.getJSONArray("groups"));
        data.put("elementMap", config.getJSONObject("elementMap"));
        return new Message().ok(666, "success").addData("data", data);
    }

    public static Message tree(JSONObject params) {
        JSONObject config = loadConfig();
        JSONArray configuredSequences = config.getJSONArray("sequences");
        boolean includeDisabled = params != null && params.getBooleanValue("includeDisabled");
        boolean onlyHasData = params == null || !params.containsKey("onlyHasData") || params.getBooleanValue("onlyHasData");
        String keyword = params == null ? "" : StringUtils.trimToEmpty(params.getString("keyword"));

        List<JSONObject> sequences;
        if (configuredSequences != null && !configuredSequences.isEmpty()) {
            sequences = configuredSequences.stream()
                    .map(item -> normalizeSequence((JSONObject) item, config))
                    .filter(item -> includeDisabled || item.getBooleanValue("enabled"))
                    .filter(item -> StringUtils.isBlank(keyword) || matchKeyword(item, keyword))
                    .collect(Collectors.toList());
            if (onlyHasData) {
                List<JSONObject> hasDataSequences = filterSequencesWithPlan(sequences);
                if (!hasDataSequences.isEmpty()) {
                    sequences = hasDataSequences;
                }
            }
        } else {
            sequences = querySequenceFromHfidx(keyword, config);
        }

        JSONObject data = new JSONObject(true);
        data.put("tree", groupSequences(sequences, config.getJSONArray("groups")));
        return new Message().ok(666, "success").addData("data", data);
    }

    public static Message options(JSONObject params) {
        List<HFIdx> plans = queryPlans(params, false);

        Set<String> modelSet = new LinkedHashSet<>();
        Set<String> forecasterSet = new LinkedHashSet<>();
        for (HFIdx plan : plans) {
            String modelName = firstNotBlank(plan.getSchname(), plan.getModelName());
            if (StringUtils.isNotBlank(modelName)) {
                modelSet.add(modelName);
            }
            if (StringUtils.isNotBlank(plan.getYbr())) {
                forecasterSet.add(plan.getYbr());
            }
        }

        JSONArray modelSchemes = new JSONArray();
        modelSchemes.add(option(ALL_MODEL, ALL_MODEL));
        modelSet.stream().sorted().forEach(item -> modelSchemes.add(option(item, item)));

        JSONArray forecasters = new JSONArray();
        forecasters.add(option(ALL_FORECASTER, ALL_FORECASTER));
        forecasterSet.stream().sorted().forEach(item -> forecasters.add(option(item, item)));

        JSONObject data = new JSONObject(true);
        data.put("modelSchemes", modelSchemes);
        data.put("forecasters", forecasters);
        data.put("modelNames", names(modelSchemes));
        data.put("forecasterNames", names(forecasters));
        return new Message().ok(666, "success").addData("data", data);
    }

    public static Message query(JSONObject params) {
        LOGGER.info("[flood_stationProcess_query] requestParams={}", JSON.toJSONString(params));
        JSONObject config = loadConfig();
        JSONObject page = config.getJSONObject("page");
        int maxPlanCount = intValue(page, "maxPlanCount", DEFAULT_MAX_PLAN_COUNT);
        int chartMaxLines = intValue(page, "chartMaxLines", DEFAULT_CHART_MAX_LINES);
        int digit = resolveDigit(params, config);
        String unit = resolveUnit(params, config);

        List<HFIdx> plans = queryPlans(params, true);
        boolean hasMore = plans.size() > maxPlanCount;
        if (hasMore) {
            plans = new ArrayList<>(plans.subList(0, maxPlanCount));
        }

        List<ADXLD> values = queryAdxld(plans);
        Map<String, List<ADXLD>> valueMap = values.stream().collect(Collectors.groupingBy(WujiangStationProcessResult::adxldKey));
        List<String> unmatchedPlanKeys = plans.stream()
                .map(WujiangStationProcessResult::planKey)
                .filter(key -> !valueMap.containsKey(key))
                .collect(Collectors.toList());
        LOGGER.info("[flood_stationProcess_query] hfidxCount={}, adxldCount={}, matchedPlanCount={}, unmatchedPlanCount={}, unmatchedPlanKeys={}",
                plans.size(), values.size(), plans.size() - unmatchedPlanKeys.size(), unmatchedPlanKeys.size(), unmatchedPlanKeys);

        int maxStep = 0;
        List<JSONObject> tableData = new ArrayList<>();
        for (HFIdx plan : plans) {
            List<ADXLD> process = valueMap.getOrDefault(planKey(plan), Collections.emptyList());
            if (process.isEmpty()) {
                continue;
            }
            process = process.stream().sorted(Comparator.comparing(ADXLD::getDt, Comparator.nullsLast(Comparator.naturalOrder()))).collect(Collectors.toList());
            JSONObject row = buildRow(plan, process, digit);
            maxStep = Math.max(maxStep, row.getIntValue("maxStep"));
            tableData.add(row);
        }

        JSONArray forecastSteps = new JSONArray();
        for (int i = 0; i <= maxStep; i++) {
            forecastSteps.add(i);
        }

        JSONObject data = new JSONObject(true);
        data.put("forecastSteps", forecastSteps);
        data.put("tableHeader", buildTableHeader(maxStep));
        data.put("tableData", tableData);
        data.put("chartRows", tableData.stream().limit(chartMaxLines).collect(Collectors.toList()));

        JSONObject summary = new JSONObject(true);
        summary.put("planCount", tableData.size());
        summary.put("maxStep", maxStep);
        summary.put("unit", unit);
        summary.put("digit", digit);
        summary.put("hasMore", hasMore);
        data.put("summary", summary);
        LOGGER.info("[flood_stationProcess_query] responsePlanCount={}, maxStep={}, hasMore={}", tableData.size(), maxStep, hasMore);
        return new Message().ok(666, "success").addData("data", data);
    }

    private static JSONObject buildRow(HFIdx plan, List<ADXLD> process, int digit) {
        JSONObject row = new JSONObject(true);
        String ybdtFull = formatFull(plan.getYbdt());
        String ybtFull = formatFull(plan.getYbt());
        row.put("planId", firstNotBlank(plan.getAdid(), "") + "-" + ybdtFull.replaceAll("[^0-9]", ""));
        row.put("adid", plan.getAdid());
        row.put("forecastTime", formatMinute(plan.getYbdt()));
        row.put("forecastTimeFull", ybdtFull);
        row.put("basisTime", formatMinute(plan.getYbt()));
        row.put("basisTimeFull", ybtFull);
        row.put("modelName", firstNotBlank(plan.getSchname(), plan.getModelName()));
        row.put("forecaster", StringUtils.defaultIfBlank(plan.getYbr(), "--"));
        row.put("timeStep", timeStep(plan.getTimespan()));

        JSONObject actualTimes = new JSONObject(true);
        int maxStep = 0;
        for (int i = 0; i < process.size(); i++) {
            ADXLD item = process.get(i);
            int step = calcStep(plan, item, i);
            if (step < 0) {
                step = i;
            }
            maxStep = Math.max(maxStep, step);
            String prop = "v" + step;
            row.put(prop, round(item.getD(), digit));
            actualTimes.put(prop, formatMinute(item.getDt()));
        }
        row.put("actualTimes", actualTimes);
        row.put("maxStep", maxStep);
        return row;
    }

    private static int calcStep(HFIdx plan, ADXLD item, int index) {
        Calendar start = item.getT() != null ? item.getT() : plan.getYbt();
        Calendar dt = item.getDt();
        int timeSpan = plan.getTimespan() > 0 ? plan.getTimespan() : 1;
        if (start == null || dt == null) {
            return index;
        }
        long diffMillis = dt.getTimeInMillis() - start.getTimeInMillis();
        double diffHours = diffMillis / 3600000.0;
        return (int) Math.round(diffHours / timeSpan);
    }

    private static List<HFIdx> queryPlans(JSONObject params, boolean includeOptionsFilter) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select ").append(HFIDX_FIELDS).append(" from HFIDX where 1=1 ");

        String sequenceCode = value(params, "sequenceCode");
        String explicitAdid = StringUtils.isNumeric(sequenceCode) ? sequenceCode : "";
        if (StringUtils.isNotBlank(explicitAdid)) {
            sql.append(" and ADID = ? ");
            args.add(explicitAdid);
        } else {
            String foreObjName = firstNotBlank(value(params, "foreObjName"), value(params, "stationName"));
            String secName = firstNotBlank(value(params, "secName"), value(params, "stationName"));
            if (StringUtils.isNotBlank(foreObjName) && StringUtils.isNotBlank(secName) && StringUtils.equals(foreObjName, secName)) {
                sql.append(" and (FOREOBJNAME = ? or SECNAME = ?) ");
                args.add(foreObjName);
                args.add(secName);
            } else {
                if (StringUtils.isNotBlank(foreObjName)) {
                    sql.append(" and FOREOBJNAME = ? ");
                    args.add(foreObjName);
                }
                if (StringUtils.isNotBlank(secName)) {
                    sql.append(" and SECNAME = ? ");
                    args.add(secName);
                }
            }
        }

        Calendar beginTime = parseCalendar(value(params, "beginTime"));
        Calendar endTime = parseCalendar(value(params, "endTime"));
        String timeField = "basisTime".equalsIgnoreCase(value(params, "timeType")) ? "YBT" : "YBDT";
        if (beginTime != null) {
            sql.append(" and ").append(timeField).append(" >= ? ");
            args.add(beginTime);
        }
        if (endTime != null) {
            sql.append(" and ").append(timeField).append(" <= ? ");
            args.add(endTime);
        }

        if (includeOptionsFilter) {
            String forecaster = value(params, "forecaster");
            if (StringUtils.isNotBlank(forecaster) && !ALL_FORECASTER.equals(forecaster)) {
                sql.append(" and YBR = ? ");
                args.add(forecaster);
            }
            String modelName = value(params, "modelName");
            if (StringUtils.isNotBlank(modelName) && !ALL_MODEL.equals(modelName)) {
                sql.append(" and (SCHNAME = ? or MODELNAME = ?) ");
                args.add(modelName);
                args.add(modelName);
            }
        }

        sql.append(" order by YBT asc, YBDT asc, SCHNAME asc ");
        logSql("HFIDX", sql.toString(), args);
        try {
            List<HFIdx> rawPlans = DBManager.executeSql(sql.toString(), HFIdx.class, args.toArray(), "wds");
            List<HFIdx> plans = rawPlans.stream()
                    .filter(item -> StringUtils.isNotBlank(explicitAdid) || matchRestType(item, params))
                    .filter(item -> StringUtils.isNotBlank(explicitAdid) || matchForecastType(item, params))
                    .collect(Collectors.toList());
            LOGGER.info("[flood_stationProcess_query][HFIDX] rawCount={}, filteredCount={}, explicitAdid={}",
                    rawPlans.size(), plans.size(), StringUtils.defaultIfBlank(explicitAdid, "--"));
            return plans;
        } catch (Exception e) {
            LOGGER.error("[flood_stationProcess_query][HFIDX] query failed", e);
            return Collections.emptyList();
        }
    }

    private static List<ADXLD> queryAdxld(List<HFIdx> plans) {
        if (plans.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, HFIdx> uniquePlans = new LinkedHashMap<>();
        for (HFIdx plan : plans) {
            uniquePlans.putIfAbsent(planKey(plan), plan);
        }

        StringBuilder sql = new StringBuilder("SELECT ADID,T,DT,D,S,FP,YBDT FROM ADXLD WHERE ");
        List<Object> args = new ArrayList<>();
        int index = 0;
        for (HFIdx plan : uniquePlans.values()) {
            if (index++ > 0) {
                sql.append(" OR ");
            }
            sql.append("(ADID = ?");
            args.add(plan.getAdid());
            if (plan.getYbt() != null) {
                sql.append(" AND T = ?");
                args.add(plan.getYbt());
            }
            if (plan.getYbdt() != null) {
                sql.append(" AND YBDT = ?");
                args.add(plan.getYbdt());
            }
            sql.append(")");
        }
        sql.append(" ORDER BY ADID ASC,T ASC,YBDT ASC,DT ASC");
        logSql("ADXLD", sql.toString(), args);

        long start = System.currentTimeMillis();
        try {
            List<ADXLD> result = DBManager.executeSql(sql.toString(), ADXLD.class, args.toArray(), "wds");
            LOGGER.info("[flood_stationProcess_query][ADXLD] requestedPlanCount={}, uniquePlanCount={}, resultCount={}, elapsedMs={}",
                    plans.size(), uniquePlans.size(), result.size(), System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            LOGGER.error("[flood_stationProcess_query][ADXLD] batch query failed", e);
            return Collections.emptyList();
        }
    }

    private static void logSql(String stage, String sql, List<Object> args) {
        String flag = "TREE_HFIDX".equals(stage) ? "flood_stationProcess_tree" : "flood_stationProcess_query";
        LOGGER.info("[{}][{}] parameterizedSql={}", flag, stage, sql);
        LOGGER.info("[{}][{}] parameters={}", flag, stage, args);
        LOGGER.info("[{}][{}] executableSql={}", flag, stage, renderSql(sql, args));
    }

    private static String renderSql(String sql, List<Object> args) {
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '?' && argIndex < args.size()) {
                result.append(sqlLiteral(args.get(argIndex++)));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Calendar) {
            return "TO_DATE('" + formatFull((Calendar) value) + "','YYYY-MM-DD HH24:MI:SS')";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private static List<JSONObject> querySequenceFromHfidx(String keyword, JSONObject config) {
        String sql = "select " + HFIDX_FIELDS + " from HFIDX order by FOREOBJNAME asc, SECNAME asc, RESTYPE asc, FORECASTTYPE asc";
        List<HFIdx> list;
        try {
            list = DBManager.executeSql(sql, HFIdx.class, new Object[]{}, "wds");
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
        Map<String, JSONObject> map = new LinkedHashMap<>();
        for (HFIdx item : list) {
            JSONObject seq = fromHfIdx(item, config);
            if (StringUtils.isNotBlank(keyword) && !matchKeyword(seq, keyword)) {
                continue;
            }
            map.putIfAbsent(seq.getString("id"), seq);
        }
        return new ArrayList<>(map.values());
    }

    private static JSONObject fromHfIdx(HFIdx item, JSONObject config) {
        JSONObject seq = new JSONObject(true);
        String restLabel = toResTypeLabel(item.getResType());
        String forecastLabel = toForecastTypeLabel(item.getForecastType());
        String stationName = firstNotBlank(item.getSecName(), item.getForeObjName());
        String elementLabel = normalizeElementLabel(restLabel);
        JSONObject element = elementInfo(elementLabel, config);

        String sequenceCode = stationName + "-" + item.getResType() + "-" + item.getForecastType();
        seq.put("id", sequenceCode);
        seq.put("label", stationName + element.getString("label"));
        seq.put("sequenceCode", sequenceCode);
        seq.put("code", sequenceCode);
        seq.put("stationName", stationName);
        seq.put("stationType", stationType(forecastLabel));
        seq.put("foreObjName", item.getForeObjName());
        seq.put("secName", item.getSecName());
        seq.put("restType", restLabel);
        seq.put("restTypeCode", item.getResType());
        seq.put("forecastType", forecastLabel);
        seq.put("forecastTypeCode", item.getForecastType());
        seq.putAll(element);
        seq.put("enabled", true);
        return seq;
    }

    private static JSONArray groupSequences(List<JSONObject> sequences, JSONArray groups) {
        JSONArray tree = new JSONArray();
        Set<String> used = new LinkedHashSet<>();
        if (groups != null) {
            for (int i = 0; i < groups.size(); i++) {
                JSONObject group = groups.getJSONObject(i);
                JSONArray stationTypes = group.getJSONArray("stationTypes");
                JSONArray children = new JSONArray();
                for (JSONObject seq : sequences) {
                    boolean matchedByGroupId = StringUtils.equals(group.getString("id"), seq.getString("groupId"));
                    boolean matchedByStationType = StringUtils.isBlank(seq.getString("groupId"))
                            && (stationTypes == null || stationTypes.contains(seq.getString("stationType")));
                    if (matchedByGroupId || matchedByStationType) {
                        children.add(seq);
                        used.add(seq.getString("id"));
                    }
                }
                JSONObject node = new JSONObject(true);
                node.put("id", group.getString("id"));
                node.put("label", group.getString("label"));
                node.put("children", children);
                tree.add(node);
            }
        }
        JSONArray otherChildren = new JSONArray();
        for (JSONObject seq : sequences) {
            if (!used.contains(seq.getString("id"))) {
                otherChildren.add(seq);
            }
        }
        if (!otherChildren.isEmpty()) {
            JSONObject other = new JSONObject(true);
            other.put("id", "other-group");
            other.put("label", "其他预报序列");
            other.put("children", otherChildren);
            tree.add(other);
        }
        return tree;
    }

    private static JSONObject normalizeSequence(JSONObject item, JSONObject config) {
        JSONObject seq = new JSONObject(true);
        seq.putAll(item);
        seq.put("code", firstNotBlank(item.getString("code"), item.getString("sequenceCode"), item.getString("id")));
        seq.put("sequenceCode", firstNotBlank(item.getString("sequenceCode"), seq.getString("code")));
        String elementLabel = firstNotBlank(item.getString("elementLabel"), item.getString("restType"), inferElementLabel(seq.getString("sequenceCode")));
        seq.putAll(elementInfo(elementLabel, config));
        if (!seq.containsKey("enabled")) {
            seq.put("enabled", true);
        }
        return seq;
    }

    private static List<JSONObject> filterSequencesWithPlan(List<JSONObject> sequences) {
        if (sequences.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> args = new ArrayList<>();
        boolean allHaveAdid = sequences.stream()
                .map(item -> item.getString("sequenceCode"))
                .allMatch(StringUtils::isNumeric);
        StringBuilder sql = new StringBuilder("select ").append(HFIDX_FIELDS).append(" from HFIDX");
        if (allHaveAdid) {
            sql.append(" where ADID in (");
            for (int i = 0; i < sequences.size(); i++) {
                if (i > 0) {
                    sql.append(",");
                }
                sql.append("?");
                args.add(sequences.get(i).getString("sequenceCode"));
            }
            sql.append(")");
        }
        logSql("TREE_HFIDX", sql.toString(), args);

        long start = System.currentTimeMillis();
        try {
            List<HFIdx> plans = DBManager.executeSql(sql.toString(), HFIdx.class, args.toArray(), "wds");
            List<JSONObject> result = sequences.stream()
                    .filter(sequence -> plans.stream().anyMatch(plan -> matchesSequence(plan, sequence)))
                    .collect(Collectors.toList());
            LOGGER.info("[flood_stationProcess_tree] configuredSequenceCount={}, hfidxCount={}, matchedSequenceCount={}, elapsedMs={}",
                    sequences.size(), plans.size(), result.size(), System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            LOGGER.error("[flood_stationProcess_tree] batch HFIDX query failed, keep configured tree", e);
            return Collections.emptyList();
        }
    }

    private static boolean matchesSequence(HFIdx plan, JSONObject sequence) {
        String sequenceCode = sequence.getString("sequenceCode");
        if (StringUtils.isNumeric(sequenceCode)) {
            return StringUtils.equals(sequenceCode, plan.getAdid());
        }

        String foreObjName = firstNotBlank(sequence.getString("foreObjName"), sequence.getString("stationName"));
        String secName = firstNotBlank(sequence.getString("secName"), sequence.getString("stationName"));
        boolean stationMatched;
        if (StringUtils.isNotBlank(foreObjName) && StringUtils.isNotBlank(secName) && StringUtils.equals(foreObjName, secName)) {
            stationMatched = StringUtils.equals(foreObjName, plan.getForeObjName()) || StringUtils.equals(secName, plan.getSecName());
        } else {
            stationMatched = (StringUtils.isBlank(foreObjName) || StringUtils.equals(foreObjName, plan.getForeObjName()))
                    && (StringUtils.isBlank(secName) || StringUtils.equals(secName, plan.getSecName()));
        }
        return stationMatched && matchRestType(plan, sequence) && matchForecastType(plan, sequence);
    }

    private static JSONArray buildTableHeader(int maxStep) {
        JSONArray header = new JSONArray();
        JSONObject base = new JSONObject(true);
        base.put("label", "方案信息");
        JSONArray baseChildren = new JSONArray();
        baseChildren.add(column("方案时间", "forecastTime", false, null));
        baseChildren.add(column("依据时间", "basisTime", false, null));
        baseChildren.add(column("模型方案", "modelName", false, null));
        baseChildren.add(column("预报人员", "forecaster", false, null));
        baseChildren.add(column("步长", "timeStep", false, null));
        base.put("children", baseChildren);
        header.add(base);
        for (int i = 0; i <= maxStep; i++) {
            JSONObject group = new JSONObject(true);
            group.put("label", "V" + i);
            JSONArray children = new JSONArray();
            children.add(column(i == 0 ? "起报值" : "+" + i + "步", "v" + i, false, "numeric"));
            group.put("children", children);
            header.add(group);
        }
        return header;
    }

    private static JSONObject column(String label, String prop, boolean edit, String type) {
        JSONObject column = new JSONObject(true);
        column.put("label", label);
        column.put("prop", prop);
        column.put("edit", edit);
        if (StringUtils.isNotBlank(type)) {
            column.put("type", type);
        }
        return column;
    }

    private static JSONObject loadConfig() {
        try {
            File file = new File(CONFIG_PATH);
            if (file.exists()) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                return JSON.parseObject(content);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultConfig();
    }

    private static JSONObject defaultConfig() {
        return JSON.parseObject("{\n" +
                "  \"page\": {\"routePath\":\"/stationForecastProcess\",\"menuName\":\"乌江结果管理\",\"theme\":\"light\",\"defaultTimeType\":\"makeTime\",\"defaultTimeRangeHours\":10,\"maxPlanCount\":200,\"chartMaxLines\":6,\"fixedColumnsLeft\":4,\"enableExport\":true,\"enableEdit\":false},\n" +
                "  \"timeTypes\": [{\"label\":\"预报制作时间\",\"value\":\"makeTime\",\"field\":\"YBDT\"},{\"label\":\"预报依据时间\",\"value\":\"basisTime\",\"field\":\"YBT\"}],\n" +
                "  \"elementMap\": {\"流量\":{\"element\":\"flow\",\"label\":\"流量\",\"unit\":\"m³/s\",\"digit\":1},\"水位\":{\"element\":\"waterLevel\",\"label\":\"水位\",\"unit\":\"m\",\"digit\":2},\"区间流量\":{\"element\":\"flow\",\"label\":\"区间流量\",\"unit\":\"m³/s\",\"digit\":1}},\n" +
                "  \"groups\": [{\"id\":\"reservoir-group\",\"label\":\"水库预报序列\",\"stationTypes\":[\"水库\"]},{\"id\":\"hydro-group\",\"label\":\"水文站预报序列\",\"stationTypes\":[\"水文站\"]},{\"id\":\"section-group\",\"label\":\"断面预报序列\",\"stationTypes\":[\"断面\"]}],\n" +
                "  \"sequences\": []\n" +
                "}");
    }

    private static JSONObject elementInfo(String label, JSONObject config) {
        JSONObject elementMap = config.getJSONObject("elementMap");
        JSONObject info = elementMap == null ? null : elementMap.getJSONObject(label);
        if (info == null) {
            info = elementMap == null ? null : elementMap.getJSONObject(normalizeElementLabel(label));
        }
        if (info == null) {
            info = new JSONObject(true);
            info.put("element", label != null && label.contains("水位") ? "waterLevel" : "flow");
            info.put("label", normalizeElementLabel(label));
            info.put("unit", label != null && label.contains("水位") ? "m" : "m³/s");
            info.put("digit", label != null && label.contains("水位") ? 2 : 1);
        }
        JSONObject result = new JSONObject(true);
        result.put("element", info.getString("element"));
        result.put("elementLabel", info.getString("label"));
        result.put("unit", info.getString("unit"));
        result.put("digit", info.getIntValue("digit"));
        return result;
    }

    private static boolean matchRestType(HFIdx item, JSONObject params) {
        String requested = firstNotBlank(value(params, "restType"), value(params, "elementLabel"), inferElementLabel(value(params, "sequenceCode")));
        if (StringUtils.isBlank(requested)) {
            return true;
        }
        if (StringUtils.isNumeric(requested)) {
            return item.getResType() == Integer.parseInt(requested);
        }
        String actual = toResTypeLabel(item.getResType());
        String normalized = normalizeElementLabel(requested);
        return StringUtils.contains(actual, requested) || StringUtils.contains(actual, normalized)
                || StringUtils.contains(normalized, normalizeElementLabel(actual));
    }

    private static boolean matchForecastType(HFIdx item, JSONObject params) {
        String requested = value(params, "forecastType");
        if (StringUtils.isBlank(requested)) {
            return true;
        }
        if (StringUtils.isNumeric(requested)) {
            return item.getForecastType() == Integer.parseInt(requested);
        }
        return StringUtils.contains(toForecastTypeLabel(item.getForecastType()), requested);
    }

    private static JSONObject option(String label, String value) {
        JSONObject item = new JSONObject(true);
        item.put("label", label);
        item.put("value", value);
        return item;
    }

    private static JSONArray names(JSONArray options) {
        JSONArray names = new JSONArray();
        for (int i = 0; i < options.size(); i++) {
            names.add(options.getJSONObject(i).getString("value"));
        }
        return names;
    }

    private static String toResTypeLabel(int value) {
        try {
            String label = EnumConstant.HFForeResType.getForeResTypeDescriptionByValue(value);
            return StringUtils.defaultIfBlank(label, String.valueOf(value));
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String toForecastTypeLabel(int value) {
        try {
            EnumConstant.HFForeType type = EnumConstant.HFForeType.getType(value);
            return type == null ? String.valueOf(value) : EnumConstant.HFForeType.getDesp(type);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String normalizeElementLabel(String label) {
        if (StringUtils.isBlank(label)) {
            return "流量";
        }
        if (label.contains("水位") || StringUtils.equalsIgnoreCase(label, "waterLevel")) {
            return "水位";
        }
        if (label.contains("区间")) {
            return "区间流量";
        }
        return "流量";
    }

    private static String inferElementLabel(String sequenceCode) {
        if (StringUtils.isBlank(sequenceCode)) {
            return "";
        }
        String code = sequenceCode.toUpperCase();
        if (code.endsWith("-Z") || code.endsWith("_Z") || code.endsWith("Z")) {
            return "水位";
        }
        if (code.contains("IQ")) {
            return "区间流量";
        }
        if (code.endsWith("-Q") || code.endsWith("_Q") || code.endsWith("Q")) {
            return "流量";
        }
        return "";
    }

    private static String stationType(String forecastLabel) {
        if (StringUtils.contains(forecastLabel, "水库") || StringUtils.contains(forecastLabel, "入库")) {
            return "水库";
        }
        if (StringUtils.contains(forecastLabel, "断面") || StringUtils.contains(forecastLabel, "区间")) {
            return "断面";
        }
        return "水文站";
    }

    private static boolean matchKeyword(JSONObject item, String keyword) {
        String text = (item.getString("label") + " " + item.getString("code") + " " + item.getString("sequenceCode") + " " + item.getString("stationName") + " " + item.getString("elementLabel")).toLowerCase();
        return text.contains(keyword.toLowerCase());
    }

    private static String planKey(HFIdx item) {
        return firstNotBlank(item.getAdid(), "") + "|" + formatFull(item.getYbt()) + "|" + formatFull(item.getYbdt());
    }

    private static String adxldKey(ADXLD item) {
        return firstNotBlank(item.getAdid(), "") + "|" + formatFull(item.getT()) + "|" + formatFull(item.getYbdt());
    }

    private static Calendar parseCalendar(String time) {
        if (StringUtils.isBlank(time)) {
            return null;
        }
        try {
            return DateFormatter.SECOND_FORMATTER.parseCalendar(time);
        } catch (Exception e) {
            return DateFormatter.parseCalendar(() -> time);
        }
    }

    private static Object round(Double value, int digit) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(digit, RoundingMode.HALF_UP).doubleValue();
    }

    private static String formatFull(Calendar calendar) {
        return TimeUtil.CalendarToString(calendar, DateFormatter.SECOND_FORMATTER);
    }

    private static String formatMinute(Calendar calendar) {
        return TimeUtil.CalendarToString(calendar, DateFormatter.MINUTE_FORMATTER);
    }

    private static String timeStep(int timeSpan) {
        return (timeSpan > 0 ? timeSpan : 1) + "小时";
    }

    private static int resolveDigit(JSONObject params, JSONObject config) {
        if (params != null && params.getInteger("digit") != null) {
            return params.getIntValue("digit");
        }
        String label = firstNotBlank(value(params, "elementLabel"), value(params, "restType"), inferElementLabel(value(params, "sequenceCode")));
        return elementInfo(label, config).getIntValue("digit");
    }

    private static String resolveUnit(JSONObject params, JSONObject config) {
        if (StringUtils.isNotBlank(value(params, "unit"))) {
            return value(params, "unit");
        }
        String label = firstNotBlank(value(params, "elementLabel"), value(params, "restType"), inferElementLabel(value(params, "sequenceCode")));
        return elementInfo(label, config).getString("unit");
    }

    private static int intValue(JSONObject obj, String key, int defaultValue) {
        return obj == null || obj.getInteger(key) == null ? defaultValue : obj.getIntValue(key);
    }

    private static String value(JSONObject obj, String key) {
        return obj == null ? "" : StringUtils.trimToEmpty(obj.getString(key));
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private static <T> List<List<T>> batches(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
