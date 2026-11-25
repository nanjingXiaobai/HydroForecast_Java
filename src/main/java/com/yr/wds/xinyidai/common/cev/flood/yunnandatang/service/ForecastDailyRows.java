package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfigException;
import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

/** PLANLOAD式日序列：一天、一个对象、一个指标一行。保留空值和时刻存在性，不补零。 */
final class ForecastDailyRows {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    static final String[] API_FIELDS = {"rainfall", "netRainfall", "cumulativeNetRainfall", "cumulativeRainfall", "pa", "discharge"};
    static final String[] REGRESSION_FIELDS = {"forecastFlag", "rainfall", "shatuoOutflow", "shatuoInflow", "shatuoWaterLevel",
            "goupitanInflow", "goupitanOutflow", "shatuoRainfallAverage", "shatuoOutflowAverage", "shatuoInflowAverage", "forecastFlow"};
    static final String[] TANK_FIELDS = {"rainfall", "evaporation", "tank1StorageDepth", "tank1UpperHole", "tank1MiddleHole",
            "tank1LowerHole", "tank1Infiltration", "tank2StorageDepth", "tank2Outflow", "tank2Percolation",
            "tank3StorageDepth", "tank3MiddleHole", "tank3BottomHole", "outflowDepth", "flow"};
    static final String[] PENGSHUI_FIELDS = {"jiangbinFlow", "dahekouFlow", "yangongFlow", "gongpengFlow",
            "regionalFlowTotal", "shatuoOutflow", "pengshuiInflow"};

    static final class Row {
        String kind, station, series, name, senid, unit, source, code, day, text, parameterType, idAttrCode;
        Double weight;
        int step;
        final Double[] values = new Double[96];
        final boolean[] present = new boolean[96];
        String key() {
            return digest(Arrays.asList(kind, station, series, name, senid, unit, source, weight, code, day, step).toString());
        }
        String mask() { StringBuilder s = new StringBuilder(96); for (boolean p : present) s.append(p ? '1' : '0'); return s.toString(); }
    }

    private final Map<String, Row> rows = new LinkedHashMap<String, Row>();
    private int inputCount;

    static List<Row> pack(ForecastContracts.ResultSaveRequest request, String module) {
        ForecastDailyRows pack = new ForecastDailyRows();
        int step = "API_MODEL".equals(module) ? 60 : 1440;
        pack.input(request.getInput(), step);
        if (request.getInputs() != null) for (ForecastContracts.InputResponse input : request.getInputs()) pack.input(input, step);
        pack.points(request.getStationCode(), "shatuoOutflow", "沙沱出库", null, "m3/s", null, request.getShatuoOutflowSeries(), step);
        if (pack.inputCount == 0) throw error("没有可保存的模型输入时序数据");
        pack.results(request.getStationCode(), module, request.getResult(), step);
        Map<String, Map<String, Object>> parameters = new LinkedHashMap<String, Map<String, Object>>();
        if (request.getInput() != null) merge(parameters, request.getInput().getStationCode(), request.getInput().getModelParameters());
        if (request.getInputs() != null) for (ForecastContracts.InputResponse input : request.getInputs()) {
            if (input != null) merge(parameters, input.getStationCode(), input.getModelParameters());
        }
        merge(parameters, request.getStationCode(), request.getParameters());
        if (request.getStationParameters() != null) for (Map.Entry<String, Map<String, Object>> entry : request.getStationParameters().entrySet())
            merge(parameters, entry.getKey(), entry.getValue());
        for (Map.Entry<String, Map<String, Object>> entry : parameters.entrySet())
            pack.parameter(entry.getKey(), "", entry.getValue(), parse(request.getForecastTime()).toLocalDate().toString());
        List<Row> saved = new ArrayList<Row>(pack.rows.values());
        if ("API_MODEL".equals(module) || "TANK_MODEL".equals(module)) keepForecastWindow(saved, request);
        return saved;
    }

    /** 结果管理快照与标准预报表共用预报窗口，参数保留，历史展示数据不入库。 */
    static void keepForecastWindow(List<Row> rows, ForecastContracts.ResultSaveRequest request) {
        List<ForecastContracts.InputResponse> inputs = new ArrayList<>();
        if (request.getInput() != null) inputs.add(request.getInput());
        if (request.getInputs() != null) for (ForecastContracts.InputResponse input : request.getInputs())
            if (input != null) inputs.add(input);
        Iterator<Row> iterator = rows.iterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            if ("PARAMETER".equals(row.kind)) continue;
            ForecastContracts.InputResponse input = null;
            for (ForecastContracts.InputResponse candidate : inputs)
                if (Objects.equals(row.station, candidate.getStationCode())) { input = candidate; break; }
            if (input == null && !inputs.isEmpty()) input = inputs.get(0);
            if (input == null || input.getForecastBeginTime() == null || input.getForecastEndTime() == null)
                throw error("保存结果缺少预报起止时间: " + row.station);
            LocalDateTime begin = parse(input.getForecastBeginTime()), end = parse(input.getForecastEndTime());
            LocalDateTime day = parse(row.day + " 00:00:00");
            boolean any = false;
            for (int i = 0; i < row.present.length; i++) {
                LocalDateTime time = day.plusMinutes((long) row.step * i);
                if (time.isBefore(begin) || time.isAfter(end)) { row.present[i] = false; row.values[i] = null; }
                any |= row.present[i];
            }
            if (!any) iterator.remove();
        }
    }

    /** 保存时固化点号，修改配置不改变已保存任务。 */
    static void applyResultPoints(List<Row> rows,
            com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationModelConfiguration config) {
        for (Row row : rows) {
            com.yr.wds.xinyidai.common.cev.flood.stationconfig.StationDefinition station = config.getStationMap().get(row.station);
            row.idAttrCode = station == null ? null : station.getResultPoints().get(row.kind + "." + row.code);
            if (row.idAttrCode == null && station != null) {
                row.idAttrCode = station.getResultPoints().get(row.kind + ".*");
            }
        }
    }

    private static void merge(Map<String, Map<String, Object>> all, String station, Map<String, Object> values) {
        if (values == null || values.isEmpty()) return;
        if (!all.containsKey(station)) all.put(station, new LinkedHashMap<String, Object>());
        all.get(station).putAll(values);
    }

    private void input(ForecastContracts.InputResponse input, int step) {
        if (input == null) return;
        if (input.getSeries() != null) for (ForecastContracts.SeriesResponse s : input.getSeries())
            points(input.getStationCode(), s.getKey(), s.getName(), s.getSenid(), s.getUnit(), s.getWeight(), s.getValues(), step);
        if (input.getForecastRainfall() != null) points(input.getStationCode(), "forecastRainfall", "预报面雨量", null,
                "mm", null, input.getForecastRainfall().getValues(), step);
    }

    private void points(String station, String series, String name, String senid, String unit, Double weight,
                        List<ForecastContracts.ValuePoint> points, int step) {
        if (points == null) return;
        for (ForecastContracts.ValuePoint point : points) {
            if (point == null) continue;
            Double actual = number(point.getValue()), original = point.getOriginalValue() == null ? actual : number(point.getOriginalValue());
            Row meta = meta("INPUT", station, series, step);
            meta.name = name; meta.senid = senid; meta.unit = unit; meta.weight = weight; meta.source = point.getSource();
            put(meta, "actualValue", point.getTime(), actual);
            put(meta, "originalValue", point.getTime(), original);
            put(meta, "averageValue", point.getTime(), point.getAverageValue());
            put(meta, "editable", point.getTime(), point.isEditable() ? 1D : 0D);
            put(meta, "manuallyModified", point.getTime(), point.isManuallyModified() || !Objects.equals(actual, original) ? 1D : 0D);
            inputCount++;
        }
    }

    private void results(String station, String module, Map<String, Object> result, int step) {
        if ("API_MODEL".equals(module)) {
            // 按时间累计，避免乱序或重复时刻导致累计雨量重复计入。
            Map<String, Map<String, Object>> points = new TreeMap<String, Map<String, Object>>();
            for (Object item : requiredRows(result.get("chartData"), "API模型结果没有chartData")) {
                Map<String, Object> row = map(item);
                String time = parse(String.valueOf(row.get("time"))).format(TIME);
                Map<String, Object> previous = points.get(time);
                if (previous != null) {
                    for (String field : new String[]{"rainfall", "netRainfall", "inflow", "pa"})
                        if (!Objects.equals(number(previous.get(field)), number(row.get(field))))
                            throw error("API结果同一时刻有冲突值: " + time + "/" + field);
                } else points.put(time, row);
            }
            double rain = 0D, net = 0D;
            for (Object item : points.values()) {
                Map<String, Object> value = map(item), row = new LinkedHashMap<String, Object>(value);
                Double r = number(row.get("rainfall")), n = number(row.get("netRainfall"));
                if (r != null) rain += r; if (n != null) net += n;
                row.put("cumulativeRainfall", rain); row.put("cumulativeNetRainfall", net); row.put("discharge", row.get("inflow"));
                result(meta("API", station, "", step), row, API_FIELDS);
            }
        } else if ("REGRESSION_MODEL".equals(module)) {
            for (Object item : requiredRows(result.get("chartData"), "沙沱模型结果没有chartData")) {
                Map<String, Object> row = new LinkedHashMap<String, Object>(map(item));
                boolean future = Boolean.TRUE.equals(row.get("forecast"));
                row.put("forecastFlag", future ? 1D : 0D); row.put("forecastFlow", future ? row.get("shatuoOutflow") : null);
                result(meta("REGRESSION", station, "", step), row, REGRESSION_FIELDS);
            }
        } else if ("TANK_MODEL".equals(module)) {
            Object basins = result.get("basinDetails");
            if (basins instanceof List) {
                for (Object item : requiredRows(basins, "水箱分区结果为空")) {
                    Map<String, Object> basin = map(item);
                    tank(String.valueOf(basin.get("stationCode")), basin.get("tableData"), step);
                }
            } else {
                Map<String, Object> data = result.get("data") instanceof Map ? map(result.get("data")) : result;
                tank(station, data.get("forecastSeries"), step);
            }
            if (result.get("chartData") instanceof List) for (Object item : (List<?>) result.get("chartData")) {
                Map<String, Object> row = map(item);
                if (number(row.get("regionalFlowTotal")) == null) throw error("彭水汇总regionalFlowTotal不能为空");
                result(meta("PENGSHUI", station, "", step), row, PENGSHUI_FIELDS);
            }
        } else throw error("不支持保存的模型模块: " + module);
    }

    private void tank(String station, Object points, int step) {
        for (Object item : requiredRows(points, "水箱模型结果没有forecastSeries")) {
            Map<String, Object> row = new LinkedHashMap<String, Object>(map(item));
            if (number(row.get("flow")) == null) throw error("水箱结果flow不能为空: " + station);
            for (int layer = 1; layer <= 3; layer++) {
                for (Map.Entry<String, Object> field : map(row.get("tank" + layer)).entrySet()) {
                    String key = field.getKey();
                    row.put("tank" + layer + Character.toUpperCase(key.charAt(0)) + key.substring(1), field.getValue());
                }
            }
            result(meta("TANK", station, "", step), row, TANK_FIELDS);
        }
    }

    private void result(Row meta, Map<String, Object> row, String[] fields) {
        String time = String.valueOf(row.get("time"));
        for (String field : fields) put(meta, field, time, number(row.get(field)));
    }

    private void parameter(String station, String path, Object value, String day) {
        if (value == null) return;
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : map(value).entrySet()) parameter(station,
                    path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), entry.getValue(), day);
        } else if (value instanceof Iterable) {
            int index = 0; for (Object child : (Iterable<?>) value) parameter(station, path + "[" + index++ + "]", child, day);
        } else {
            Row r = meta("PARAMETER", station, path, 1440); r.day = day; r.code = "value";
            r.parameterType = value instanceof Number ? "NUMBER" : value instanceof Boolean ? "BOOLEAN" : "TEXT";
            if (value instanceof Number) r.values[0] = number(value);
            else if (value instanceof Boolean) r.values[0] = (Boolean) value ? 1D : 0D;
            else r.text = String.valueOf(value);
            r.present[0] = true; rows.put(r.key(), r);
        }
    }

    private static Row meta(String kind, String station, String series, int step) {
        if (station == null || station.trim().isEmpty() || "null".equals(station)) throw error("保存数据缺少stationCode");
        Row r = new Row(); r.kind = kind; r.station = station; r.series = series; r.step = step; return r;
    }

    private void put(Row meta, String code, String time, Double value) {
        LocalDateTime t = parse(time);
        int minute = t.getHour() * 60 + t.getMinute();
        if (t.getSecond() != 0 || minute % meta.step != 0) throw error("时间与模型时步不匹配: " + time + "，时步=" + meta.step + "分钟");
        meta.day = t.toLocalDate().toString(); meta.code = code;
        String key = meta.key(); Row r = rows.get(key);
        if (r == null) {
            r = meta(meta.kind, meta.station, meta.series, meta.step);
            r.name = meta.name; r.senid = meta.senid; r.unit = meta.unit; r.source = meta.source; r.weight = meta.weight;
            r.day = meta.day; r.code = code; rows.put(key, r);
        }
        int slot = minute / meta.step;
        if (slot >= 96) throw error("一天最多96个时段");
        if (r.present[slot] && !Objects.equals(r.values[slot], value)) throw error("同一指标时刻有冲突值: " + meta.station + "/" + code + "/" + time);
        r.present[slot] = true; r.values[slot] = value;
    }

    /** 还原现有详情/导出契约。跨日和缺测时段按实际时间排序，不用数组下标推测时间。 */
    static List<Map<String, Object>> expand(List<Row> rows, String kind) {
        Map<String, Map<String, Object>> points = new TreeMap<String, Map<String, Object>>();
        for (Row r : rows) {
            if (!kind.equals(r.kind) || "PARAMETER".equals(kind)) continue;
            for (int i = 0; i < 96; i++) if (r.present[i]) {
                String time = LocalDateTime.parse(r.day + " 00:00:00", TIME).plusMinutes((long) r.step * i).format(TIME);
                String seriesKey = Arrays.asList(r.series, r.name, r.senid, r.unit, r.source, r.weight).toString();
                String key = r.station + "|" + time + "|" + ("INPUT".equals(kind) ? seriesKey : "");
                Map<String, Object> point = points.get(key);
                if (point == null) {
                    point = new LinkedHashMap<String, Object>(); points.put(key, point);
                    point.put("stationCode", r.station); point.put("resultTime", time); point.put("dataTime", time);
                    point.put("seriesCode", r.series); point.put("seriesName", r.name); point.put("senid", r.senid);
                    point.put("unit", r.unit); point.put("dataSource", r.source); point.put("weight", r.weight);
                }
                point.put(r.code, r.values[i]);
            }
        }
        return new ArrayList<Map<String, Object>>(points.values());
    }

    static LocalDateTime parse(String text) {
        try { return LocalDateTime.parse(text, TIME); }
        catch (Exception ex) { throw error("时间格式必须是yyyy-MM-dd HH:mm:ss: " + text); }
    }
    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder(); for (byte b : hash) s.append(String.format("%02x", b & 255)); return s.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static Double number(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        try { double n = Double.parseDouble(String.valueOf(value)); if (!Double.isFinite(n)) throw new NumberFormatException(); return n; }
        catch (NumberFormatException ex) { throw error("非有效数字: " + value); }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }
    private static List<?> requiredRows(Object value, String message) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) throw error(message); return (List<?>) value;
    }
    private static StationModelConfigException error(String message) { return new StationModelConfigException(message); }
}
