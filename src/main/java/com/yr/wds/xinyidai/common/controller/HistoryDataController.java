package com.yr.wds.xinyidai.common.controller;

import com.alibaba.fastjson.JSONObject;
import com.yr.wds.xinyidai.common.cev.flood.service.history.HistoryDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/forecast")
public class HistoryDataController {

    @PostMapping("/historyData")
    public ResponseEntity<?> historyData(@RequestBody JSONObject request) {
        try {
            List<String> pointIds = request.getJSONArray("pointIds") == null
                    ? null
                    : request.getJSONArray("pointIds").toJavaList(String.class);
            return ResponseEntity.ok(HistoryDataService.queryHistoryData(
                    pointIds,
                    request.getString("startTime"),
                    request.getString("endTime"),
                    request.getString("timeType")));
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", HttpStatus.BAD_REQUEST.value());
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
