package com._project4.project4.dto;

public class RobotSummaryResponse {

    private final String robotId;
    private final String robotCode;
    private final String model;
    private final boolean online;

    public RobotSummaryResponse(String robotId, String robotCode, String model, boolean online) {
        this.robotId = robotId;
        this.robotCode = robotCode;
        this.model = model;
        this.online = online;
    }

    public String getRobotId() {
        return robotId;
    }

    public String getRobotCode() {
        return robotCode;
    }

    public String getModel() {
        return model;
    }

    public boolean isOnline() {
        return online;
    }
}
