package com._project4.project4.dto;

public class RobotDetailResponse {

    private final String robotId;
    private final String robotCode;
    private final String model;
    private final boolean online;
    private final long lastHeartbeat;
    private final Integer battery;
    private final PositionDto position;
    private final String status;

    public RobotDetailResponse(String robotId,
                               String robotCode,
                               String model,
                               boolean online,
                               long lastHeartbeat,
                               Integer battery,
                               PositionDto position,
                               String status) {
        this.robotId = robotId;
        this.robotCode = robotCode;
        this.model = model;
        this.online = online;
        this.lastHeartbeat = lastHeartbeat;
        this.battery = battery;
        this.position = position;
        this.status = status;
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

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public Integer getBattery() {
        return battery;
    }

    public PositionDto getPosition() {
        return position;
    }

    public String getStatus() {
        return status;
    }
}
