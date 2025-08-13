package com.yr.wds.xinyidai.common.cev.flood.service.powergenerationforecast.model;

import lombok.Data;

/**
 * @author xcw
 * @version 1.0
 * @date 2024/8/20 9:08
 * @description: 站点信息
 */
@Data
public class Station {
    private String stationId;
    private String stationName;
    private String latitude;
    private String longitude;
    private int   regulateType;
}
