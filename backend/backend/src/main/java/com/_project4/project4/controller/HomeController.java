package com._project4.project4.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com._project4.project4.common.ApiResponse;

@RestController
public class HomeController {

    @GetMapping("/")
    public ApiResponse<Map<String, String>> root() {
        return ApiResponse.ok(Map.of(
                "service", "RCMS API",
                "health", "/actuator/health",
                "robots", "/api/v1/robots"
        ));
    }
}