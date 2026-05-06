package com._project4.project4.controller;

import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com._project4.project4.client.DataClient;
import com._project4.project4.common.GlobalExceptionHandler;
import com._project4.project4.service.RobotServiceImpl;

class RobotControllerIntegrationTest {

    private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                DataClient dataClient = org.mockito.Mockito.mock(DataClient.class);
                Executor sameThreadExecutor = Runnable::run;
                RobotServiceImpl robotService = new RobotServiceImpl(dataClient, sameThreadExecutor);
                RobotController controller = new RobotController(robotService);
                LogController logController = new LogController(robotService);
                mockMvc = MockMvcBuilders.standaloneSetup(controller, logController)
                                .setControllerAdvice(new GlobalExceptionHandler())
                                .build();
        }

    @Test
    void fullApiFlow_shouldCoverSevenEndpointsHappyPath() throws Exception {
        String robotId = registerRobot("RBT-INT-001");

        mockMvc.perform(post("/api/v1/robots/{robotId}/heartbeat", robotId)
                        .contentType("application/json")
                        .content("{\"timestamp\":1760000000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/robots/{robotId}/status", robotId)
                        .contentType("application/json")
                        .content("{\"status\":\"WORKING\",\"battery\":76,\"position\":{\"x\":12.5,\"y\":-3.2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/robots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/v1/robots/{robotId}", robotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.robotId").value(robotId));

        String commandId = sendCommand(robotId);

        mockMvc.perform(get("/api/v1/robots/{robotId}/commands/{commandId}", robotId, commandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commandId").value(commandId));
    }

    @Test
    void invalidStatusPayload_shouldReturn4001() throws Exception {
        String robotId = registerRobot("RBT-INT-002");

        mockMvc.perform(post("/api/v1/robots/{robotId}/status", robotId)
                        .contentType("application/json")
                        .content("{\"status\":\"working\",\"battery\":101,\"position\":{\"x\":12.5,\"y\":-3.2}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void nonExistingRobot_shouldReturn4041() throws Exception {
        mockMvc.perform(get("/api/v1/robots/{robotId}", "not-exists"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4041));
    }

    @Test
    void realtimeAndControlAndLogs_shouldWorkWithMapping() throws Exception {
        String robotId = registerRobot("RBT-INT-010");

        mockMvc.perform(post("/api/v1/robots/{robotId}/status", robotId)
                        .contentType("application/json")
                        .content("{\"status\":\"WORKING\",\"battery\":76,\"position\":{\"x\":12.5,\"y\":-3.2}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/robots/control")
                        .contentType("application/json")
                        .content("{\"type\":\"DISPATCH\",\"robotId\":\"" + robotId + "\",\"position\":{\"x\":10.0,\"y\":20.0}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commandId").exists());

        mockMvc.perform(get("/api/v1/robots/realtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("RBT-INT-010"))
                .andExpect(jsonPath("$.data[0].name").value("MEC-EX"))
                .andExpect(jsonPath("$.data[0].status").value("运行中"))
                .andExpect(jsonPath("$.data[0].currentTask").value("DISPATCH:(10.0,20.0)"));

        mockMvc.perform(get("/api/v1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(org.hamcrest.Matchers.greaterThan(0))));
    }

    private String registerRobot(String robotCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/robots")
                        .contentType("application/json")
                        .content("{\"robotCode\":\"" + robotCode + "\",\"model\":\"MEC-EX\",\"capabilities\":\"move,lift,scan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

                String robotId = extractField(result.getResponse().getContentAsString(), "robotId");
                assertTrue(robotId != null && !robotId.isEmpty());
                return robotId;
    }

    private String sendCommand(String robotId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/robots/{robotId}/commands", robotId)
                        .contentType("application/json")
                        .content("{\"commandType\":\"MOVE_TO\",\"params\":{\"x\":20,\"y\":8}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

                String commandId = extractField(result.getResponse().getContentAsString(), "commandId");
                assertTrue(commandId != null && !commandId.isEmpty());
                return commandId;
        }

        private String extractField(String json, String fieldName) {
                Pattern pattern = Pattern.compile("\\\"" + fieldName + "\\\":\\\"([^\\\"]+)\\\"");
                Matcher matcher = pattern.matcher(json);
                return matcher.find() ? matcher.group(1) : null;
    }
}
