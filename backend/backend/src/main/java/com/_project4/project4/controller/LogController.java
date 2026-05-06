package com._project4.project4.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._project4.project4.common.ApiResponse;
import com._project4.project4.dto.LogItemResponse;
import com._project4.project4.service.RobotService;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final RobotService robotService;

    public LogController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    public ApiResponse<List<LogItemResponse>> listLogs() {
        return ApiResponse.ok(robotService.listLogs());
    }
}