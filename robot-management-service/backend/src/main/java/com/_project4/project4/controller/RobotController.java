package com._project4.project4.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com._project4.project4.common.ApiResponse;
import com._project4.project4.dto.CommandStatusResponse;
import com._project4.project4.dto.HeartbeatRequest;
import com._project4.project4.dto.RegisterRobotRequest;
import com._project4.project4.dto.RegisterRobotResponse;
import com._project4.project4.dto.RobotControlRequest;
import com._project4.project4.dto.RobotDetailResponse;
import com._project4.project4.dto.RobotRealtimeResponse;
import com._project4.project4.dto.RobotSummaryResponse;
import com._project4.project4.dto.SendCommandRequest;
import com._project4.project4.dto.SendCommandResponse;
import com._project4.project4.dto.StatusUpdateRequest;
import com._project4.project4.service.RobotService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/robots")
public class RobotController {

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @PostMapping
    public ApiResponse<RegisterRobotResponse> register(@Valid @RequestBody RegisterRobotRequest request) {
        return ApiResponse.ok(robotService.registerRobot(request));
    }

    @PostMapping("/{robotId}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable String robotId, @Valid @RequestBody HeartbeatRequest request) {
        robotService.updateHeartbeat(robotId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{robotId}/status")
    public ApiResponse<Void> status(@PathVariable String robotId, @Valid @RequestBody StatusUpdateRequest request) {
        robotService.updateStatus(robotId, request);
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<List<RobotSummaryResponse>> list() {
        return ApiResponse.ok(robotService.listRobots());
    }

    @GetMapping("/realtime")
    public ApiResponse<List<RobotRealtimeResponse>> realtime() {
        return ApiResponse.ok(robotService.listRealtimeRobots());
    }

    @GetMapping("/{robotId}")
    public ApiResponse<RobotDetailResponse> detail(@PathVariable String robotId) {
        return ApiResponse.ok(robotService.getRobot(robotId));
    }

    @PostMapping("/{robotId}/commands")
    public ApiResponse<SendCommandResponse> sendCommand(@PathVariable String robotId,
                                                        @Valid @RequestBody SendCommandRequest request) {
        return ApiResponse.ok(robotService.sendCommand(robotId, request));
    }

    @PostMapping("/control")
    public ApiResponse<SendCommandResponse> control(@Valid @RequestBody RobotControlRequest request) {
        return ApiResponse.ok(robotService.controlRobot(request));
    }

    @GetMapping("/{robotId}/commands/{commandId}")
    public ApiResponse<CommandStatusResponse> commandStatus(@PathVariable String robotId,
                                                            @PathVariable String commandId) {
        return ApiResponse.ok(robotService.getCommandStatus(robotId, commandId));
    }
}
