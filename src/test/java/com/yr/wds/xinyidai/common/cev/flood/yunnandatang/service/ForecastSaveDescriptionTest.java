package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.service;

import com.yr.wds.xinyidai.common.cev.flood.yunnandatang.api.ForecastContracts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForecastSaveDescriptionTest {
    @Test void editableDescriptionGetsModelPrefix() {
        ForecastContracts.ResultSaveRequest r = new ForecastContracts.ResultSaveRequest();
        r.setModelDescription("人工修订方案A");
        assertEquals("水箱模型人工修订方案A", ForecastSaveDescription.value(r, "TANK_MODEL"));
        assertEquals("API模型人工修订方案A", ForecastSaveDescription.value(r, "API_MODEL"));
    }
    @Test void automaticRemarkAndDefaultDescriptionFollowSameRule() {
        ForecastContracts.ResultSaveRequest r = new ForecastContracts.ResultSaveRequest();
        r.setRemark("预报制作时间:2026-11-10 09:05");
        assertEquals("水箱模型预报制作时间:2026-11-10 09:05", ForecastSaveDescription.value(r, "TANK_MODEL"));
        r.setModelDescription("预报制作时间:2026-11-10 09:05");
        assertEquals("API模型预报制作时间:2026-11-10 09:05", ForecastSaveDescription.value(r, "API_MODEL"));
    }
    @Test void prefixIsNotDuplicatedAndBlankStillIdentifiesModel() {
        ForecastContracts.ResultSaveRequest r = new ForecastContracts.ResultSaveRequest();
        r.setModelDescription("API模型测试");
        assertEquals("API模型测试", ForecastSaveDescription.value(r, "API_MODEL"));
        r.setModelDescription("水箱模型测试");
        assertEquals("水箱模型测试", ForecastSaveDescription.value(r, "TANK_MODEL"));
        r.setModelDescription(null);
        assertEquals("水箱模型", ForecastSaveDescription.value(r, "TANK_MODEL"));
    }
    @Test void otherModelIsUnchangedAndDescriptionTakesPrecedence() {
        ForecastContracts.ResultSaveRequest r = new ForecastContracts.ResultSaveRequest();
        r.setModelDescription("用户描述"); r.setRemark("旧备注");
        assertEquals("用户描述", ForecastSaveDescription.value(r, "REGRESSION_MODEL"));
        assertEquals("API模型用户描述", ForecastSaveDescription.value(r, "API_MODEL"));
    }
}
