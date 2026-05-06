package com._project4.project4.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com._project4.project4.client.DataClient;
import com._project4.project4.client.ExternalDataClientException;
import com._project4.project4.common.BusinessException;
import com._project4.project4.common.ErrorCode;
import com._project4.project4.domain.Position;
import com._project4.project4.domain.RobotCommand;
import com._project4.project4.domain.RobotInfo;
import com._project4.project4.dto.CommandStatusResponse;
import com._project4.project4.dto.HeartbeatRequest;
import com._project4.project4.dto.LogItemResponse;
import com._project4.project4.dto.PositionDto;
import com._project4.project4.dto.RegisterRobotRequest;
import com._project4.project4.dto.RegisterRobotResponse;
import com._project4.project4.dto.RobotControlRequest;
import com._project4.project4.dto.RobotDetailResponse;
import com._project4.project4.dto.RobotRealtimeResponse;
import com._project4.project4.dto.RobotSummaryResponse;
import com._project4.project4.dto.SendCommandRequest;
import com._project4.project4.dto.SendCommandResponse;
import com._project4.project4.dto.StatusUpdateRequest;

import jakarta.annotation.PostConstruct;

@Service
public class RobotServiceImpl implements RobotService {

    private static final Logger log = LoggerFactory.getLogger(RobotServiceImpl.class);

    private final Map<String, RobotInfo> robotInfoById = new ConcurrentHashMap<>();
    private final Map<String, String> robotIdByCode = new ConcurrentHashMap<>();
    private final Map<String, RobotCommand> commandById = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyEntry> commandIdempotencyByKey = new ConcurrentHashMap<>();
    private final Map<String, String> currentTaskByRobotId = new ConcurrentHashMap<>();
    private final Deque<LogItemResponse> logs = new ConcurrentLinkedDeque<>();

    private final DataClient dataClient;
    private final Executor commandExecutor;

    private static final int MAX_LOG_SIZE = 300;

    @Value("${robot.heartbeat.timeout-ms:15000}")
    private long heartbeatTimeoutMs;

    @Value("${robot.command.idempotency.window-ms:5000}")
    private long commandIdempotencyWindowMs;

    @Value("${robot.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${robot.seed.robot-code:RBT-DEMO-001}")
    private String seedRobotCode;

    @Value("${robot.seed.model:MEC-DEMO}")
    private String seedModel;

    @Value("${robot.seed.capabilities:move,lift,scan}")
    private String seedCapabilities;

    public RobotServiceImpl(
            DataClient dataClient,
            @Qualifier("commandExecutor") Executor commandExecutor
    ) {
        this.dataClient = dataClient;
        this.commandExecutor = commandExecutor;
    }

    @PostConstruct
    public void initSeedRobot() {
        if (!seedEnabled) {
            return;
        }
        if (robotIdByCode.containsKey(seedRobotCode)) {
            return;
        }

        RegisterRobotRequest request = new RegisterRobotRequest();
        request.setRobotCode(seedRobotCode);
        request.setModel(seedModel);
        request.setCapabilities(seedCapabilities);
        RegisterRobotResponse response = registerRobot(request);

        RobotInfo seedRobot = robotInfoById.get(response.getRobotId());
        if (seedRobot != null) {
            seedRobot.setStatus("IDLE");
            seedRobot.setBattery(88);
            seedRobot.setPosition(new Position(5.0, 5.0));
        }

        addLog("info", "seed robot ready: robotCode=" + seedRobotCode + ", robotId=" + response.getRobotId());
    }

    @Override
    public RegisterRobotResponse registerRobot(RegisterRobotRequest request) {
        String existedRobotId = robotIdByCode.get(request.getRobotCode());
        if (existedRobotId != null) {
            return new RegisterRobotResponse(existedRobotId);
        }

        String robotId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        RobotInfo robotInfo = new RobotInfo();
        robotInfo.setRobotId(robotId);
        robotInfo.setRobotCode(request.getRobotCode());
        robotInfo.setModel(request.getModel());
        robotInfo.setCapabilities(request.getCapabilities());
        robotInfo.setOnline(true);
        robotInfo.setLastHeartbeat(now);
        robotInfo.setStatus("IDLE");

        robotInfoById.put(robotId, robotInfo);
        robotIdByCode.put(request.getRobotCode(), robotId);
        persistToExternal(() -> dataClient.saveRobot(robotInfo), "saveRobot", robotId, false);
        addLog("info", "robot registered: robotId=" + robotId + ", robotCode=" + request.getRobotCode());

        return new RegisterRobotResponse(robotId);
    }

    @Override
    public void updateHeartbeat(String robotId, HeartbeatRequest request) {
        RobotInfo robotInfo = requireRobot(robotId);
        Long requestTimestamp = request.getTimestamp();
        long timestamp = requestTimestamp == null ? System.currentTimeMillis() : requestTimestamp;
        robotInfo.setLastHeartbeat(timestamp);
        robotInfo.setOnline(true);
        persistToExternal(() -> dataClient.saveHeartbeat(robotId, timestamp, true), "saveHeartbeat", robotId, false);
    }

    @Override
    public void updateStatus(String robotId, StatusUpdateRequest request) {
        RobotInfo robotInfo = requireRobot(robotId);
        robotInfo.setStatus(request.getStatus());
        robotInfo.setBattery(request.getBattery());
        robotInfo.setPosition(new Position(request.getPosition().getX(), request.getPosition().getY()));
        persistToExternal(() -> dataClient.saveStatus(robotInfo), "saveStatus", robotId, false);
    }

    @Override
    public List<RobotSummaryResponse> listRobots() {
        List<RobotSummaryResponse> results = new ArrayList<>();
        for (RobotInfo robotInfo : robotInfoById.values()) {
            results.add(new RobotSummaryResponse(
                    robotInfo.getRobotId(),
                    robotInfo.getRobotCode(),
                    robotInfo.getModel(),
                    robotInfo.isOnline()
            ));
        }
        return results;
    }

    @Override
    public List<RobotRealtimeResponse> listRealtimeRobots() {
        List<RobotRealtimeResponse> results = new ArrayList<>();
        for (RobotInfo robotInfo : robotInfoById.values()) {
            String currentTask = currentTaskByRobotId.get(robotInfo.getRobotId());
            results.add(new RobotRealtimeResponse(
                    robotInfo.getRobotCode(),
                    robotInfo.getModel(),
                    toChineseStatus(robotInfo),
                    currentTask
            ));
        }
        return results;
    }

    @Override
    public RobotDetailResponse getRobot(String robotId) {
        RobotInfo robotInfo = requireRobot(robotId);
        PositionDto positionDto = null;
        if (robotInfo.getPosition() != null) {
            positionDto = new PositionDto();
            positionDto.setX(robotInfo.getPosition().getX());
            positionDto.setY(robotInfo.getPosition().getY());
        }

        return new RobotDetailResponse(
                robotInfo.getRobotId(),
                robotInfo.getRobotCode(),
                robotInfo.getModel(),
                robotInfo.isOnline(),
                robotInfo.getLastHeartbeat(),
                robotInfo.getBattery(),
                positionDto,
                robotInfo.getStatus()
        );
    }

    @Override
    public SendCommandResponse sendCommand(String robotId, SendCommandRequest request) {
        requireRobot(robotId);
        long now = System.currentTimeMillis();

        cleanupExpiredIdempotencyKeys(now);
        String commandDedupKey = buildCommandDedupKey(robotId, request);
        IdempotencyEntry existing = commandIdempotencyByKey.get(commandDedupKey);
        if (existing != null && existing.expireAtMs() > now && commandById.containsKey(existing.commandId())) {
            return new SendCommandResponse(existing.commandId());
        }

        String commandId = UUID.randomUUID().toString();

        RobotCommand command = new RobotCommand();
        command.setCommandId(commandId);
        command.setRobotId(robotId);
        command.setCommandType(request.getCommandType());
        command.setParams(request.getParams());
        command.setStatus("QUEUED");
        command.setCreateTime(now);
        command.setUpdateTime(now);

        commandById.put(commandId, command);
        commandIdempotencyByKey.put(commandDedupKey, new IdempotencyEntry(commandId, now + commandIdempotencyWindowMs));
        refreshCurrentTask(robotId, request);
        persistToExternal(() -> dataClient.saveCommand(command), "saveCommand", robotId, false);

        try {
            CompletableFuture.runAsync(() -> processCommand(command), commandExecutor);
        } catch (RejectedExecutionException ex) {
            command.setStatus("FAILED");
            command.setUpdateTime(System.currentTimeMillis());
            persistToExternal(
                    () -> dataClient.updateCommandStatus(command.getCommandId(), command.getStatus(), command.getUpdateTime()),
                    "updateCommandStatus",
                    command.getRobotId(),
                    false
            );
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "internal server error");
        }

        return new SendCommandResponse(commandId);
    }

    @Override
    public SendCommandResponse controlRobot(RobotControlRequest request) {
        String type = request.getType();
        if ("DISPATCH".equals(type)
                && (request.getPosition() == null
                || request.getPosition().getX() == null
                || request.getPosition().getY() == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "position{x,y} is required when type is DISPATCH");
        }

        SendCommandRequest commandRequest = new SendCommandRequest();
        commandRequest.setCommandType(type);

        Map<String, Object> params = new HashMap<>();
        if ("DISPATCH".equals(type)) {
            params.put("x", request.getPosition().getX());
            params.put("y", request.getPosition().getY());
        }
        if (!params.isEmpty()) {
            commandRequest.setParams(params);
        }

        SendCommandResponse response = sendCommand(request.getRobotId(), commandRequest);
        addLog("info", "control command accepted: type=" + type + ", robotId=" + request.getRobotId());
        return response;
    }

    @Override
    public CommandStatusResponse getCommandStatus(String robotId, String commandId) {
        requireRobot(robotId);
        RobotCommand command = commandById.get(commandId);
        if (command == null || !Objects.equals(command.getRobotId(), robotId)) {
            throw new BusinessException(ErrorCode.COMMAND_NOT_FOUND, "command not found");
        }
        return new CommandStatusResponse(commandId, command.getStatus());
    }

    @Override
    public List<LogItemResponse> listLogs() {
        return new ArrayList<>(logs);
    }

    @Scheduled(fixedDelayString = "${robot.heartbeat.scan-ms:2000}")
    public void scanOfflineRobots() {
        long now = System.currentTimeMillis();
        for (RobotInfo robotInfo : robotInfoById.values()) {
            boolean offline = now - robotInfo.getLastHeartbeat() > heartbeatTimeoutMs;
            if (offline && robotInfo.isOnline()) {
                robotInfo.setOnline(false);
                String currentRobotId = robotInfo.getRobotId();
                persistToExternal(
                        () -> dataClient.saveHeartbeat(currentRobotId, robotInfo.getLastHeartbeat(), false),
                        "saveHeartbeat",
                    currentRobotId,
                    false
                );
                addLog("warn", "robot offline: robotId=" + currentRobotId);
            }
        }
    }

    private void processCommand(RobotCommand command) {
        try {
            command.setStatus("DISPATCHED");
            command.setUpdateTime(System.currentTimeMillis());
            persistToExternal(
                    () -> dataClient.updateCommandStatus(command.getCommandId(), command.getStatus(), command.getUpdateTime()),
                    "updateCommandStatus",
                    command.getRobotId(),
                    false
            );

            Thread.sleep(200);

            command.setStatus("SUCCESS");
            command.setUpdateTime(System.currentTimeMillis());
            persistToExternal(
                    () -> dataClient.updateCommandStatus(command.getCommandId(), command.getStatus(), command.getUpdateTime()),
                    "updateCommandStatus",
                    command.getRobotId(),
                    false
            );
                addLog("info", "command success: commandId=" + command.getCommandId());
            } catch (InterruptedException ex) {
            command.setStatus("FAILED");
            command.setUpdateTime(System.currentTimeMillis());
            persistToExternal(
                    () -> dataClient.updateCommandStatus(command.getCommandId(), command.getStatus(), command.getUpdateTime()),
                    "updateCommandStatus",
                    command.getRobotId(),
                    false
            );
                    addLog("error", "command interrupted: commandId=" + command.getCommandId());
            Thread.currentThread().interrupt();
            } catch (Exception ex) {
                command.setStatus("FAILED");
                command.setUpdateTime(System.currentTimeMillis());
                persistToExternal(
                    () -> dataClient.updateCommandStatus(command.getCommandId(), command.getStatus(), command.getUpdateTime()),
                    "updateCommandStatus",
                    command.getRobotId(),
                    false
                );
                addLog("error", "command failed: commandId=" + command.getCommandId());
        }
    }

    private RobotInfo requireRobot(String robotId) {
        RobotInfo robotInfo = robotInfoById.get(robotId);
        if (robotInfo == null) {
            throw new BusinessException(ErrorCode.ROBOT_NOT_FOUND, "robot not found");
        }
        return robotInfo;
    }

    private void persistToExternal(Runnable action, String operation, String robotId, boolean failFast) {
        try {
            action.run();
        } catch (ExternalDataClientException ex) {
            if (failFast) {
                log.error("external persist failed operation={} robotId={} message={}", operation, robotId, ex.getMessage());
                addLog("error", "external persist failed: operation=" + operation + ", robotId=" + robotId);
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "internal server error");
            }
            log.warn("external persist degraded operation={} robotId={} message={}", operation, robotId, ex.getMessage());
            addLog("warn", "external persist degraded: operation=" + operation + ", robotId=" + robotId);
        }
    }

    private void refreshCurrentTask(String robotId, SendCommandRequest request) {
        String commandType = request.getCommandType();
        if ("DISPATCH".equals(commandType)) {
            String x = null;
            String y = null;
            if (request.getParams() != null) {
                if (request.getParams().get("x") != null) {
                    x = String.valueOf(request.getParams().get("x"));
                }
                if (request.getParams().get("y") != null) {
                    y = String.valueOf(request.getParams().get("y"));
                }
            }
            if (x == null || x.isBlank() || y == null || y.isBlank()) {
                currentTaskByRobotId.put(robotId, "DISPATCH");
            } else {
                currentTaskByRobotId.put(robotId, "DISPATCH:(" + x + "," + y + ")");
            }
            return;
        }

        if ("STOP".equals(commandType)) {
            currentTaskByRobotId.remove(robotId);
        }
    }

    private String toChineseStatus(RobotInfo robotInfo) {
        if (!robotInfo.isOnline()) {
            return "离线";
        }

        String status = robotInfo.getStatus();
        if ("WORKING".equals(status)) {
            return "运行中";
        }
        if ("IDLE".equals(status)) {
            return "空闲";
        }
        if ("OFFLINE".equals(status)) {
            return "离线";
        }
        if ("ERROR".equals(status)) {
            return "故障";
        }
        return status;
    }

    private void addLog(String type, String msg) {
        logs.addLast(new LogItemResponse(type, msg));
        while (logs.size() > MAX_LOG_SIZE) {
            logs.pollFirst();
        }
    }

    private String buildCommandDedupKey(String robotId, SendCommandRequest request) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("robotId", robotId);
        normalized.put("commandType", request.getCommandType());
        normalized.put("params", request.getParams());
        return stableValue(normalized);
    }

    private String stableValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> mapValue) {
            return "{" + mapValue.entrySet().stream()
                    .map(entry -> String.valueOf(entry.getKey()))
                    .sorted()
                    .map(key -> key + ":" + stableValue(mapValue.get(key)))
                    .collect(Collectors.joining(",")) + "}";
        }
        if (value instanceof List<?> listValue) {
            return "[" + listValue.stream().map(this::stableValue).collect(Collectors.joining(",")) + "]";
        }
        if (value.getClass().isArray()) {
            List<Object> arrayAsList = new ArrayList<>();
            Collections.addAll(arrayAsList, (Object[]) value);
            return "[" + arrayAsList.stream().map(this::stableValue).collect(Collectors.joining(",")) + "]";
        }
        return String.valueOf(value);
    }

    private void cleanupExpiredIdempotencyKeys(long now) {
        commandIdempotencyByKey.entrySet().removeIf(entry -> entry.getValue().expireAtMs() <= now);
    }

    private record IdempotencyEntry(String commandId, long expireAtMs) {
    }
}
