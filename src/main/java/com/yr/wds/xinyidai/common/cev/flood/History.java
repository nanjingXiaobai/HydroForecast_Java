package com.yr.wds.xinyidai.common.cev.flood;

import lombok.Data;

@Data
public class History {


    private Double stage;
    private int timeStep;
    private String timeType;
    private Long stamp;
    private Long id;
    private String time;
    private Double flow;


}
