package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;

/** 保存时统一标识模型；不限制用户填写的方案描述。 */
final class ForecastSaveDescription {
    private ForecastSaveDescription() { }

    static String value(ForecastContracts.ResultSaveRequest request, String module) {
        String description = request.getModelDescription();
        if (description == null || description.trim().isEmpty()) description = request.getRemark();
        if (description == null) description = "";
        String prefix = "TANK_MODEL".equals(module) ? "水箱模型"
                : "API_MODEL".equals(module) ? "API模型" : "";
        if (prefix.isEmpty()) return description;
        description = description.trim();
        return description.startsWith(prefix) ? description : prefix + description;
    }
}
