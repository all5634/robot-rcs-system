package com._project4.project4.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import org.springframework.test.util.ReflectionTestUtils;

import com._project4.project4.client.DataClient;
import com._project4.project4.client.ExternalDataClientException;
import com._project4.project4.common.BusinessException;
import com._project4.project4.common.ErrorCode;
import com._project4.project4.dto.HeartbeatRequest;
import com._project4.project4.dto.PositionDto;
import com._project4.project4.dto.RegisterRobotRequest;
import com._project4.project4.dto.RegisterRobotResponse;
import com._project4.project4.dto.RobotSummaryResponse;
import com._project4.project4.dto.SendCommandRequest;
import com._project4.project4.dto.SendCommandResponse;
import com._project4.project4.dto.StatusUpdateRequest;

class RobotServiceImplTest {

    private DataClient dataClient;
    private RobotServiceImpl service;

    @BeforeEach
    void setUp() {
        dataClient = org.mockito.Mockito.mock(DataClient.class);
        Executor sameThreadExecutor = Runnable::run;
        service = new RobotServiceImpl(dataClient, sameThreadExecutor);

        ReflectionTestUtils.setField(service, "heartbeatTimeoutMs", 5L);
        ReflectionTestUtils.setField(service, "commandIdempotencyWindowMs", 5000L);
    }

    @Test
    void registerRobot_shouldBeIdempotentByRobotCode() {
        RegisterRobotRequest request = registerRequest("RBT-001");

        RegisterRobotResponse first = service.registerRobot(request);
        RegisterRobotResponse second = service.registerRobot(request);

        assertNotNull(first.getRobotId());
        assertEquals(first.getRobotId(), second.getRobotId());
        verify(dataClient, atLeastOnce()).saveRobot(any());
    }

    @Test
    void updateHeartbeat_shouldSetOnlineAndSave() {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-002"));

        HeartbeatRequest heartbeatRequest = new HeartbeatRequest();
        heartbeatRequest.setTimestamp(123456789L);
        service.updateHeartbeat(registered.getRobotId(), heartbeatRequest);

        verify(dataClient).saveHeartbeat(registered.getRobotId(), 123456789L, true);
    }

    @Test
    void scanOfflineRobots_shouldMarkOfflineAfterTimeout() {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-003"));

        HeartbeatRequest heartbeatRequest = new HeartbeatRequest();
        heartbeatRequest.setTimestamp(1L);
        service.updateHeartbeat(registered.getRobotId(), heartbeatRequest);
        service.scanOfflineRobots();

        List<RobotSummaryResponse> list = service.listRobots();
        RobotSummaryResponse target = list.stream()
                .filter(it -> it.getRobotId().equals(registered.getRobotId()))
                .findFirst()
                .orElseThrow();
        assertFalse(target.isOnline());
        verify(dataClient).saveHeartbeat(registered.getRobotId(), 1L, false);
    }

    @Test
    void updateStatus_shouldPersistLatestStatus() {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-004"));

        StatusUpdateRequest request = new StatusUpdateRequest();
        request.setStatus("WORKING");
        request.setBattery(80);
        PositionDto positionDto = new PositionDto();
        positionDto.setX(10.0);
        positionDto.setY(20.0);
        request.setPosition(positionDto);

        service.updateStatus(registered.getRobotId(), request);

        verify(dataClient).saveStatus(any());
    }

    @Test
    void sendCommand_shouldDeduplicateWithinWindow() {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-005"));

        SendCommandRequest request = new SendCommandRequest();
        request.setCommandType("MOVE_TO");
        Map<String, Object> params = new HashMap<>();
        params.put("x", 1);
        params.put("y", 2);
        request.setParams(params);

        SendCommandResponse first = service.sendCommand(registered.getRobotId(), request);
        SendCommandResponse second = service.sendCommand(registered.getRobotId(), request);

        assertEquals(first.getCommandId(), second.getCommandId());
        verify(dataClient, atLeastOnce()).saveCommand(any());
    }

    @Test
    void registerRobot_shouldThrow500WhenExternalPersistenceFails() {
        doThrow(new ExternalDataClientException("x", new RuntimeException("x")))
                .when(dataClient).saveRobot(any());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.registerRobot(registerRequest("RBT-006")));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, ex.getCode());
    }

    @Test
    void getCommandStatus_shouldThrow4042ForUnknownCommand() {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-007"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getCommandStatus(registered.getRobotId(), "not-exists"));

        assertEquals(ErrorCode.COMMAND_NOT_FOUND, ex.getCode());
    }

    @Test
    void getRobot_shouldThrow4041ForUnknownRobot() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getRobot("not-exists"));

        assertEquals(ErrorCode.ROBOT_NOT_FOUND, ex.getCode());
    }

    @Test
    void scanOfflineRobots_shouldDegradeWhenExternalServiceJitters() {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-008"));

        HeartbeatRequest heartbeatRequest = new HeartbeatRequest();
        heartbeatRequest.setTimestamp(1L);
        service.updateHeartbeat(registered.getRobotId(), heartbeatRequest);

        doThrow(new ExternalDataClientException("jitter", new RuntimeException("timeout")))
                .when(dataClient).saveHeartbeat(registered.getRobotId(), 1L, false);

        service.scanOfflineRobots();

        RobotSummaryResponse target = service.listRobots().stream()
                .filter(it -> it.getRobotId().equals(registered.getRobotId()))
                .findFirst()
                .orElseThrow();
        assertFalse(target.isOnline());
    }

    @Test
    void concurrentCommandRequests_shouldNotThrowUnderLoad() throws Exception {
        RegisterRobotResponse registered = service.registerRobot(registerRequest("RBT-009"));

        SendCommandRequest request = new SendCommandRequest();
        request.setCommandType("MOVE_TO");
        Map<String, Object> params = new HashMap<>();
        params.put("x", 9);
        params.put("y", 9);
        request.setParams(params);

        int workers = 16;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        Executor executor = Executors.newFixedThreadPool(workers);

        for (int i = 0; i < workers; i++) {
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await(2, TimeUnit.SECONDS);
                    SendCommandResponse response = service.sendCommand(registered.getRobotId(), request);
                    assertNotNull(response.getCommandId());
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertTrue(errors.isEmpty());
    }

    private RegisterRobotRequest registerRequest(String robotCode) {
        RegisterRobotRequest request = new RegisterRobotRequest();
        request.setRobotCode(robotCode);
        request.setModel("MEC-EX");
        request.setCapabilities("move,lift");
        return request;
    }
}
