package com.yr.wds.xinyidai.common.cev.flood.stationconfig;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 电站模型 XML 安全解析器。
 *
 * <p>解析阶段同时验证唯一编码、模型引用及模块归属，禁止带 DOCTYPE
 * 和外部实体的 XML，避免配置文件成为 XXE 攻击入口。</p>
 */
@Component
public class StationModelConfigParser {

    public StationModelConfiguration parse(InputStream inputStream) {
        if (inputStream == null) throw error("电站模型XML输入流不能为空");
        try {
            Document document = secureFactory().newDocumentBuilder().parse(inputStream);
            Element root = document.getDocumentElement();
            if (!"forecast-station-model-config".equals(root.getTagName())) {
                throw error("XML根节点必须是 forecast-station-model-config");
            }

            StationModelConfiguration configuration = new StationModelConfiguration();
            configuration.setSchemaVersion(required(root, "schemaVersion"));
            configuration.setConfigVersion(required(root, "configVersion"));
            configuration.setEnvironment(attribute(root, "environment"));
            parseModels(root, configuration);
            parseParameterProfiles(root, configuration);
            parseSourceDataProfiles(root, configuration);
            parseStations(root, configuration);
            parseAggregations(root, configuration);
            validate(configuration);
            return configuration;
        } catch (StationModelConfigException ex) {
            throw ex;
        } catch (ParserConfigurationException ex) {
            throw error("XML解析器初始化失败: " + ex.getMessage(), ex);
        } catch (SAXException ex) {
            throw error("XML格式错误: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw error("XML读取失败: " + ex.getMessage(), ex);
        }
    }

    private void parseSourceDataProfiles(Element root, StationModelConfiguration configuration) {
        Element profiles = directChild(root, "sourceDataProfiles");
        // 兼容早期分离式配置；简化版 XML 直接在 station 下配置 point 和 gfs。
        if (profiles == null) return;
        for (Element element : directChildren(profiles, "profile")) {
            SourceDataProfileDefinition profile = new SourceDataProfileDefinition();
            profile.setCode(required(element, "code"));
            profile.setName(required(element, "name"));
            profile.setHistoricalTable(attribute(element, "historicalTable"));
            profile.setForecastTable(attribute(element, "forecastTable"));
            profile.setForecastRegionId(attribute(element, "forecastRegionId"));
            profile.setForecastSubtype(nullableIntegerAttribute(element, "forecastSubtype"));
            profile.setTimeStep(attribute(element, "timeStep"));
            profile.setMappingStatus(required(element, "mappingStatus"));
            for (Element seriesElement : directChildren(element, "series")) {
                SourceSeriesDefinition series = new SourceSeriesDefinition();
                series.setRole(required(seriesElement, "role"));
                series.setName(required(seriesElement, "name"));
                series.setLegacyStationCode(attribute(seriesElement, "legacyStationCode"));
                series.setSensorId(attribute(seriesElement, "sensorId"));
                series.setAttributeCode(attribute(seriesElement, "attributeCode"));
                series.setWeight(nullableDoubleAttribute(seriesElement, "weight"));
                series.setUnit(attribute(seriesElement, "unit"));
                profile.getSeries().add(series);
            }
            configuration.getSourceDataProfiles().add(profile);
        }
    }

    private DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        /*
         * 生产工程历史依赖中包含 Xerces 2.4，它会覆盖JDK默认工厂，但没有实现
         * JDK 8 的 setFeature 方法。这里仅为本解析器指定JDK自带实现，不调整
         * 全局系统属性，也不影响旧洪水预报代码继续使用原有 Xerces。
         */
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(
                "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",
                StationModelConfigParser.class.getClassLoader());
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        // JAXP 1.5+同时限制外部DTD和Schema访问，和上面的解析器特性形成双重保护。
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private void parseModels(Element root, StationModelConfiguration configuration) {
        Element models = directChild(root, "models");
        if (models == null) throw error("XML缺少 models 节点");
        for (Element element : directChildren(models, "model")) {
            ModelDefinition model = new ModelDefinition();
            model.setCode(required(element, "code"));
            model.setName(required(element, "name"));
            model.setModule(required(element, "module"));
            model.setPackageName(attribute(element, "packageName"));
            model.setSupportedCallModes(firstNonEmpty(attribute(element, "supportedCallModes"), "process,http"));
            // 新工程字段名为nacosPrefix；生产工程只把它当作通用运行配置前缀。
            model.setConfigPrefix(firstAttribute(element, "config", "nacosPrefix"));
            configuration.getModels().add(model);
        }
    }

    private void parseParameterProfiles(Element root, StationModelConfiguration configuration) {
        Element profiles = directChild(root, "parameterProfiles");
        if (profiles == null) return;
        for (Element element : directChildren(profiles, "tankProfile")) {
            TankParameterProfileDefinition profile = new TankParameterProfileDefinition();
            profile.setCode(required(element, "code"));
            Map<String, Object> parameters = profile.getModelParameters();
            parameters.put("areaFactor", requiredDoubleAttribute(element, "areaFactor"));
            parameters.put("routingMode", required(element, "routingMode"));
            parameters.put("evaporation", numericAttributes(requiredChild(element, "evaporation"),
                    "threshold1", "threshold2", "value1", "value2", "value3"));
            parameters.put("tank1", numericAttributes(requiredChild(element, "tank1"),
                    "upperCoefficient", "upperThreshold", "middleCoefficient", "middleThreshold",
                    "lowerCoefficient", "lowerThreshold", "infiltrationCoefficient"));
            parameters.put("tank2", numericAttributes(requiredChild(element, "tank2"),
                    "outflowCoefficient", "outflowThreshold", "percolationCoefficient"));
            parameters.put("tank3", numericAttributes(requiredChild(element, "tank3"),
                    "middleCoefficient", "middleThreshold", "bottomCoefficient"));
            configuration.getTankParameterProfiles().add(profile);
        }
    }

    private void parseAggregations(Element root, StationModelConfiguration configuration) {
        Element aggregations = directChild(root, "aggregations");
        if (aggregations == null) return;
        for (Element element : directChildren(aggregations, "aggregation")) {
            AggregationDefinition definition = new AggregationDefinition();
            definition.setCode(required(element, "code"));
            definition.setDisplayName(required(element, "displayName"));
            definition.setOperation(required(element, "operation"));
            for (Element sourceElement : directChildren(element, "source")) {
                AggregationSourceDefinition source = new AggregationSourceDefinition();
                source.setStationCode(required(sourceElement, "station"));
                source.setPoint(attribute(sourceElement, "point"));
                definition.getSources().add(source);
            }
            configuration.getAggregations().add(definition);
        }
    }

    private void parseStations(Element root, StationModelConfiguration configuration) {
        Element stations = directChild(root, "stations");
        if (stations == null) throw error("XML缺少 stations 节点");
        int displayOrder = 0;
        for (Element element : directChildren(stations, "station")) {
            StationDefinition station = new StationDefinition();
            station.setCode(required(element, "code"));
            station.setName(required(element, "name"));
            station.setModelRef(requiredFirstAttribute(element, "model", "modelRef"));
            ModelDefinition model = findModel(configuration, station.getModelRef());
            station.setModule(firstNonEmpty(attribute(element, "module"), model == null ? "" : model.getModule()));
            station.setParameterProfileRef(firstAttribute(element, "params", "parameterProfileRef"));
            station.setCurveName(attribute(element, "curveName"));
            station.setDataProfileRef(attribute(element, "dataProfileRef"));
            station.setRainfallProfileRef(attribute(element, "rainfallProfileRef"));
            station.setAggregationRef(firstAttribute(element, "aggregation", "aggregationRef"));
            station.setRole(firstNonEmpty(attribute(element, "role"), defaultRole(station)));
            station.setEnabled(booleanAttribute(element, "enabled", true));
            station.setDisplayOrder(integerAttribute(element, "displayOrder", displayOrder += 10));
            station.setTimeStep(firstNonEmpty(attribute(element, "timeStep"), defaultTimeStep(station.getModule())));
            station.setForecastSteps(firstNonNull(nullableIntegerAttribute(element, "forecastSteps"),
                    "REGRESSION_MODEL".equals(station.getModule()) ? Integer.valueOf(10) : null));
            station.setHistorySteps(firstNonNull(nullableIntegerAttribute(element, "historySteps"),
                    "REGRESSION_MODEL".equals(station.getModule()) ? Integer.valueOf(7) : null));
            station.setAllowManualEdit(booleanAttribute(element, "allowManualEdit", true));
            SourceDataProfileDefinition inlineProfile = parseInlineDataProfile(element, station);
            if (inlineProfile != null) {
                configuration.getSourceDataProfiles().add(inlineProfile);
                station.setDataProfileRef(inlineProfile.getCode());
                station.setRainfallProfileRef("");
                station.setProductionReady("verified".equals(inlineProfile.getMappingStatus()));
            } else {
                station.setProductionReady(booleanAttribute(element, "productionReady", false));
            }
            configuration.getStations().add(station);
        }
    }

    /** 将简化版 station 下的 point/gfs 转换为内部统一数据方案。 */
    private SourceDataProfileDefinition parseInlineDataProfile(Element element, StationDefinition station) {
        List<Element> points = directChildren(element, "point");
        Element gfs = directChild(element, "gfs");
        if (points.isEmpty() && gfs == null) return null;

        SourceDataProfileDefinition profile = new SourceDataProfileDefinition();
        profile.setCode(station.getCode() + "_DATA");
        profile.setName(station.getName() + "数据");
        profile.setHistoricalTable("P1D".equals(station.getTimeStep()) ? "DAYDB" : "HOURDB");
        profile.setTimeStep(station.getTimeStep());
        boolean complete = true;
        if (gfs != null) {
            profile.setForecastTable("WEA_GFSFORRAIN");
            profile.setForecastRegionId(attribute(gfs, "regionId"));
            profile.setForecastSubtype(nullableIntegerAttribute(gfs, "subtype"));
            complete = !profile.getForecastRegionId().isEmpty() && profile.getForecastSubtype() != null;
        }
        for (Element pointElement : points) {
            SourceSeriesDefinition series = new SourceSeriesDefinition();
            series.setRole(requiredFirstAttribute(pointElement, "key", "role"));
            series.setName(required(pointElement, "name"));
            series.setSensorId(firstAttribute(pointElement, "senid", "sensorId"));
            series.setWeight(nullableDoubleAttribute(pointElement, "weight"));
            series.setUnit(defaultUnit(series.getRole()));
            if (series.getSensorId().isEmpty()) complete = false;
            profile.getSeries().add(series);
        }
        profile.setMappingStatus(complete ? "verified" : "pending");
        return profile;
    }

    private void validate(StationModelConfiguration configuration) {
        Set<String> modelCodes = new HashSet<String>();
        for (ModelDefinition model : configuration.getModels()) {
            if (!modelCodes.add(model.getCode())) throw error("模型code重复: " + model.getCode());
            configuration.getModelMap().put(model.getCode(), model);
        }
        if (modelCodes.isEmpty()) throw error("至少需要配置一个模型");

        Set<String> profileCodes = new HashSet<String>();
        for (SourceDataProfileDefinition profile : configuration.getSourceDataProfiles()) {
            if (!profileCodes.add(profile.getCode())) throw error("数据方案code重复: " + profile.getCode());
            validateVerifiedProfileMapping(profile);
            configuration.getSourceDataProfileMap().put(profile.getCode(), profile);
        }

        Set<String> tankProfileCodes = new HashSet<String>();
        for (TankParameterProfileDefinition profile : configuration.getTankParameterProfiles()) {
            if (!tankProfileCodes.add(profile.getCode())) throw error("水箱参数方案code重复: " + profile.getCode());
            String routingMode = String.valueOf(profile.getModelParameters().get("routingMode"));
            if (!"TO_TANK3".equals(routingMode) && !"DIRECT_RUNOFF".equals(routingMode)) {
                throw error("水箱参数方案 " + profile.getCode() + " 的routingMode非法: " + routingMode);
            }
            configuration.getTankParameterProfileMap().put(profile.getCode(), profile);
        }

        Set<String> stationCodes = new HashSet<String>();
        for (StationDefinition station : configuration.getStations()) {
            if (!stationCodes.add(station.getCode())) throw error("电站code重复: " + station.getCode());
            ModelDefinition model = configuration.getModelMap().get(station.getModelRef());
            if (model == null) {
                throw error("电站 " + station.getCode() + " 引用了不存在的模型 " + station.getModelRef());
            }
            if (!station.getModule().equals(model.getModule())) {
                throw error("电站 " + station.getCode() + " 的module与模型不一致");
            }
            if ("TANK_MODEL".equals(station.getModule()) && station.getAggregationRef().isEmpty()
                    && !configuration.getTankParameterProfileMap().containsKey(station.getParameterProfileRef())) {
                throw error("水箱分区 " + station.getCode() + " 引用了不存在的参数方案 "
                        + station.getParameterProfileRef());
            }
            // 生产共享工程兼容旧XML：缺少curveName不阻断启动，实际调用API模型时再给出明确提示。
            validateProfileReference(station.getCode(), "dataProfileRef", station.getDataProfileRef(), configuration);
            validateProfileReference(station.getCode(), "rainfallProfileRef", station.getRainfallProfileRef(), configuration);
            validateProductionDataMapping(station, configuration);
            configuration.getStationMap().put(station.getCode(), station);
        }
        if (stationCodes.isEmpty()) throw error("至少需要配置一个电站");

        Set<String> aggregationCodes = new HashSet<String>();
        for (AggregationDefinition aggregation : configuration.getAggregations()) {
            if (!aggregationCodes.add(aggregation.getCode())) throw error("汇总方案code重复: " + aggregation.getCode());
            if (!"sum".equalsIgnoreCase(aggregation.getOperation())) {
                throw error("当前只支持sum汇总: " + aggregation.getCode());
            }
            if (aggregation.getSources().isEmpty()) throw error("汇总方案没有来源: " + aggregation.getCode());
            for (AggregationSourceDefinition source : aggregation.getSources()) {
                if (!stationCodes.contains(source.getStationCode())) {
                    throw error("汇总方案 " + aggregation.getCode() + " 引用了不存在的电站 "
                            + source.getStationCode());
                }
            }
            configuration.getAggregationMap().put(aggregation.getCode(), aggregation);
        }
        for (StationDefinition station : configuration.getStations()) {
            if (!station.getAggregationRef().isEmpty()
                    && !configuration.getAggregationMap().containsKey(station.getAggregationRef())) {
                throw error("电站 " + station.getCode() + " 引用了不存在的汇总方案 "
                        + station.getAggregationRef());
            }
        }
    }

    private Element requiredChild(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        if (child == null) throw error(parent.getTagName() + " 缺少 " + tagName + " 节点");
        return child;
    }

    private Map<String, Object> numericAttributes(Element element, String... names) {
        Map<String, Object> result = new java.util.LinkedHashMap<String, Object>();
        for (String name : names) result.put(name, requiredDoubleAttribute(element, name));
        return result;
    }

    private Double requiredDoubleAttribute(Element element, String name) {
        Double value = nullableDoubleAttribute(element, name);
        if (value == null) throw error(element.getTagName() + " 缺少必填属性 " + name);
        return value;
    }

    private void validateProfileReference(String stationCode, String fieldName, String profileCode,
                                          StationModelConfiguration configuration) {
        if (!profileCode.isEmpty() && !configuration.getSourceDataProfileMap().containsKey(profileCode)) {
            throw error("电站 " + stationCode + " 的" + fieldName + "引用不存在: " + profileCode);
        }
    }

    private void validateProductionDataMapping(StationDefinition station,
                                               StationModelConfiguration configuration) {
        if (!station.isProductionReady()) return;
        validateVerifiedProfile(station.getCode(), station.getDataProfileRef(), configuration);
        validateVerifiedProfile(station.getCode(), station.getRainfallProfileRef(), configuration);
    }

    /** 标记为已确认的方案必须具备完整点号；预报降雨值固定读取 AVERPRE。 */
    private void validateVerifiedProfileMapping(SourceDataProfileDefinition profile) {
        if (!"verified".equalsIgnoreCase(profile.getMappingStatus())) return;
        for (SourceSeriesDefinition series : profile.getSeries()) {
            if (isEmpty(series.getSensorId())) {
                throw error("已确认数据方案 " + profile.getCode() + " 缺少 " + series.getName() + " 的sensorId");
            }
        }
        // 汇总站可以只有历史点号而没有 GFS 节点，此时 forecastTable 和 regionId 都可能为 null。
        if (!isEmpty(profile.getForecastTable())
                && (isEmpty(profile.getForecastRegionId()) || profile.getForecastSubtype() == null)) {
            throw error("已确认数据方案 " + profile.getCode() + " 缺少forecastRegionId或forecastSubtype");
        }
    }

    private void validateVerifiedProfile(String stationCode, String profileCode,
                                         StationModelConfiguration configuration) {
        if (profileCode.isEmpty()) return;
        SourceDataProfileDefinition profile = configuration.getSourceDataProfileMap().get(profileCode);
        if (profile != null && !"verified".equalsIgnoreCase(profile.getMappingStatus())) {
            throw error("生产就绪电站 " + stationCode + " 引用了未确认的数据方案 " + profileCode);
        }
    }

    private Element directChild(Element parent, String tagName) {
        List<Element> children = directChildren(parent, tagName);
        return children.isEmpty() ? null : children.get(0);
    }

    private List<Element> directChildren(Element parent, String tagName) {
        List<Element> result = new ArrayList<Element>();
        NodeList childNodes = parent.getChildNodes();
        for (int index = 0; index < childNodes.getLength(); index++) {
            Node node = childNodes.item(index);
            if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private ModelDefinition findModel(StationModelConfiguration configuration, String modelCode) {
        for (ModelDefinition model : configuration.getModels()) {
            if (model.getCode().equals(modelCode)) return model;
        }
        return null;
    }

    private String defaultRole(StationDefinition station) {
        if (!station.getAggregationRef().isEmpty()) return "aggregate-forecast-station";
        return "TANK_MODEL".equals(station.getModule()) ? "calculation-unit" : "forecast-station";
    }

    private String defaultTimeStep(String module) {
        return "API_MODEL".equals(module) ? "PT1H" : "P1D";
    }

    private String defaultUnit(String role) {
        if ("rainfall".equals(role)) return "mm";
        if (role.toLowerCase().contains("waterlevel")) return "m";
        return "m3/s";
    }

    private String requiredFirstAttribute(Element element, String firstName, String secondName) {
        String value = firstAttribute(element, firstName, secondName);
        if (value.isEmpty()) {
            throw error(element.getTagName() + " 缺少必填属性 " + firstName);
        }
        return value;
    }

    private String firstAttribute(Element element, String firstName, String secondName) {
        return firstNonEmpty(attribute(element, firstName), attribute(element, secondName));
    }

    private String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second : first;
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    private String required(Element element, String name) {
        String value = attribute(element, name);
        if (value.isEmpty()) throw error(element.getTagName() + " 缺少必填属性 " + name);
        return value;
    }

    private String attribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null ? "" : value.trim();
    }

    private boolean booleanAttribute(Element element, String name, boolean defaultValue) {
        String value = attribute(element, name);
        return value.isEmpty() ? defaultValue : Boolean.parseBoolean(value);
    }

    private int integerAttribute(Element element, String name, int defaultValue) {
        Integer value = nullableIntegerAttribute(element, name);
        return value == null ? defaultValue : value;
    }

    private Integer nullableIntegerAttribute(Element element, String name) {
        String value = attribute(element, name);
        if (value.isEmpty()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw error(element.getTagName() + " 属性 " + name + " 必须是整数");
        }
    }

    private Double nullableDoubleAttribute(Element element, String name) {
        String value = attribute(element, name);
        if (value.isEmpty()) return null;
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException ex) {
            throw error(element.getTagName() + " 属性 " + name + " 必须是数字");
        }
    }

    private StationModelConfigException error(String message) {
        return new StationModelConfigException(message);
    }

    private StationModelConfigException error(String message, Throwable cause) {
        return new StationModelConfigException(message, cause);
    }
}
