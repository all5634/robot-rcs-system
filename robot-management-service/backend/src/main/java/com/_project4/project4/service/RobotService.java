package com._project4.project4.service;

import java.util.List;

import com._project4.project4.dto.CommandStatusResponse;
import com._project4.project4.dto.HeartbeatRequest;
import com._project4.project4.dto.LogItemResponse;
import com._project4.project4.dto.RegisterRobotRequest;
import com._project4.project4.dto.RegisterRobotResponse;
import com._project4.project4.dto.RobotControlRequest;
import com._project4.project4.dto.RobotDetailResponse;
import com._project4.project4.dto.RobotRealtimeResponse;
import com._project4.project4.dto.RobotSummaryResponse;
import com._project4.project4.dto.SendCommandRequest;
import com._project4.project4.dto.SendCommandResponse;
import com._project4.project4.dto.StatusUpdateRequest;

public interface RobotService {

    RegisterRobotResponse registerRobot(RegisterRobotRequest request);

    void updateHeartbeat(String robotId, HeartbeatRequest request);

    void updateStatus(String robotId, StatusUpdateRequest request);

    List<RobotSummaryResponse> listRobots();

    List<RobotRealtimeResponse> listRealtimeRobots();

    RobotDetailResponse getRobot(String robotId);

    SendCommandResponse sendCommand(String robotId, SendCommandRequest request);

    SendCommandResponse controlRobot(RobotControlRequest request);

    CommandStatusResponse getCommandStatus(String robotId, String commandId);

    List<LogItemResponse> listLogs();
}
