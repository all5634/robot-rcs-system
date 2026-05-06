package com._project4.project4.client;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com._project4.project4.domain.RobotCommand;
import com._project4.project4.domain.RobotInfo;

@Component
@ConditionalOnProperty(name = "robot.data-client.type", havingValue = "http")
public class HttpDataClient implements DataClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final int maxAttempts;
    private final long retryBackoffMs;
    private final int circuitFailureThreshold;
    private final long circuitOpenMs;
    private final String saveRobotPath;
    private final String saveHeartbeatPath;
    private final String saveStatusPath;
    private final String saveCommandPath;
    private final String updateCommandStatusPath;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenUntilMs;

    public HttpDataClient(
            WebClient.Builder webClientBuilder,
            @Value("${robot.data-client.http.base-url}") String baseUrl,
            @Value("${robot.data-client.http.timeout-ms:2000}") long timeoutMs,
            @Value("${robot.data-client.http.retry.max-attempts:3}") int maxAttempts,
            @Value("${robot.data-client.http.retry.backoff-ms:200}") long retryBackoffMs,
            @Value("${robot.data-client.http.circuit.failure-threshold:5}") int circuitFailureThreshold,
            @Value("${robot.data-client.http.circuit.open-ms:10000}") long circuitOpenMs,
            @Value("${robot.data-client.http.paths.save-robot:/robots}") String saveRobotPath,
            @Value("${robot.data-client.http.paths.save-heartbeat:/robots/heartbeat}") String saveHeartbeatPath,
            @Value("${robot.data-client.http.paths.save-status:/robots/status}") String saveStatusPath,
            @Value("${robot.data-client.http.paths.save-command:/robots/commands}") String saveCommandPath,
            @Value("${robot.data-client.http.paths.update-command-status:/robots/commands/status}") String updateCommandStatusPath
    ) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
        this.circuitFailureThreshold = Math.max(1, circuitFailureThreshold);
        this.circuitOpenMs = Math.max(1000, circuitOpenMs);
        this.saveRobotPath = saveRobotPath;
        this.saveHeartbeatPath = saveHeartbeatPath;
        this.saveStatusPath = saveStatusPath;
        this.saveCommandPath = saveCommandPath;
        this.updateCommandStatusPath = updateCommandStatusPath;
    }

    @Override
    public void saveRobot(RobotInfo robotInfo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("robotId", robotInfo.getRobotId());
        payload.put("robotCode", robotInfo.getRobotCode());
        payload.put("model", robotInfo.getModel());
        payload.put("capabilities", robotInfo.getCapabilities());
        payload.put("online", robotInfo.isOnline());
        payload.put("lastHeartbeat", robotInfo.getLastHeartbeat());
        post(saveRobotPath, payload, "saveRobot");
    }

    @Override
    public void saveHeartbeat(String robotId, long timestamp, boolean online) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("robotId", robotId);
        payload.put("timestamp", timestamp);
        payload.put("online", online);
        post(saveHeartbeatPath, payload, "saveHeartbeat");
    }

    @Override
    public void saveStatus(RobotInfo robotInfo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("robotId", robotInfo.getRobotId());
        payload.put("status", robotInfo.getStatus());
        payload.put("battery", robotInfo.getBattery());

        if (robotInfo.getPosition() != null) {
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("x", robotInfo.getPosition().getX());
            position.put("y", robotInfo.getPosition().getY());
            payload.put("position", position);
        }

        payload.put("timestamp", System.currentTimeMillis());
        post(saveStatusPath, payload, "saveStatus");
    }

    @Override
    public void saveCommand(RobotCommand robotCommand) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", robotCommand.getCommandId());
        payload.put("robotId", robotCommand.getRobotId());
        payload.put("commandType", robotCommand.getCommandType());
        payload.put("params", robotCommand.getParams());
        payload.put("status", robotCommand.getStatus());
        payload.put("createTime", robotCommand.getCreateTime());
        post(saveCommandPath, payload, "saveCommand");
    }

    @Override
    public void updateCommandStatus(String commandId, String status, long updateTime) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commandId", commandId);
        payload.put("status", status);
        payload.put("updateTime", updateTime);
        post(updateCommandStatusPath, payload, "updateCommandStatus");
    }

    private void post(String path, Map<String, Object> payload, String action) {
        if (isCircuitOpen()) {
            throw new ExternalDataClientException("external data service call blocked by circuit breaker: " + action, null);
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                webClient.post()
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .toBodilessEntity()
                        .block(timeout);
                onCallSuccess();
                return;
            } catch (Exception ex) {
                lastException = ex;
                onCallFailure();
                if (attempt == maxAttempts) {
                    break;
                }
                sleepBackoff();
            }
        }

        throw new ExternalDataClientException("external data service call failed: " + action, lastException);
    }

    private boolean isCircuitOpen() {
        long now = System.currentTimeMillis();
        return now < circuitOpenUntilMs;
    }

    private void onCallSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntilMs = 0;
    }

    private void onCallFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= circuitFailureThreshold) {
            circuitOpenUntilMs = System.currentTimeMillis() + circuitOpenMs;
            consecutiveFailures.set(0);
        }
    }

    private void sleepBackoff() {
        if (retryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}