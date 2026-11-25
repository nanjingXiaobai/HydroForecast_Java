package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data;

import java.util.Calendar;
import java.util.List;

/** 云南大唐模型输入数据的只读访问接口。 */
public interface ForecastSourceDataRepository {
    default List<DatangHourlyValue> queryShatuoDailyPlan(String adid, Calendar end) {
        return java.util.Collections.emptyList();
    }

    /** 按完整名称从INFOMODEL读取API模型曲线。 */
    List<ApiCurvePoint> queryApiCurvePoints(String curveName);

    /** 配置中的完整点号匹配 ID_ATTR_CODE，查询小时或日数据，结果按测点、时间排序。 */
    /** timeType只允许hour或day，分别使用生产框架的小时、日数据查询标识。 */
    List<DatangHourlyValue> queryHistoryValues(List<String> sensorIds,
                                               Calendar beginTime,
                                               Calendar endTime,
                                               String timeType);

    /** 年初之前最后一条有效零点日流量，供预报缺测后延使用。 */
    default List<DatangHourlyValue> queryLatestDailyValueBefore(String sensorId, Calendar beforeTime) {
        throw new UnsupportedOperationException("未实现最新日流量查询");
    }

    /**
     * 查询不晚于指定起报时刻的最新一版 GFS 区域预报。
     *
     * @param regionId 数据库中的 REGID，必须由现场映射配置提供
     * @param subtype GFS 预报子类型
     * @param latestIssueTime 允许使用的最晚预报发布时间
     * @param beginTime 预报数据开始时间
     * @param endTime 预报数据结束时间
     */
    List<GfsForecastRainValue> queryLatestGfsRain(String regionId,
                                                   int subtype,
                                                   Calendar latestIssueTime,
                                                   Calendar beginTime,
                                                   Calendar endTime);
}
