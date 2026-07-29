package com.yr.wds.xinyidai.common.cev.flood.yunnandatang.data;

/** INFOMODEL曲线明细；属性名与查询别名保持一致，供DBManager自动映射。 */
public class ApiCurvePoint {
    private String curveId;
    private String curveName;
    private Double v0;
    private Double v1;
    private Double v2;
    private Double v3;

    public String getCurveId() { return curveId; }
    public void setCurveId(String curveId) { this.curveId = curveId; }
    public String getCurveName() { return curveName; }
    public void setCurveName(String curveName) { this.curveName = curveName; }
    public Double getV0() { return v0; }
    public void setV0(Double v0) { this.v0 = v0; }
    public Double getV1() { return v1; }
    public void setV1(Double v1) { this.v1 = v1; }
    public Double getV2() { return v2; }
    public void setV2(Double v2) { this.v2 = v2; }
    public Double getV3() { return v3; }
    public void setV3(Double v3) { this.v3 = v3; }
}
