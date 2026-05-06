package com._project4.project4.dto;

public class RegisterRobotResponse {

    private final String robotId;

    public RegisterRobotResponse(String robotId) {
        this.robotId = robotId;
    }

    public String getRobotId() {
        return robotId;
    }
}
