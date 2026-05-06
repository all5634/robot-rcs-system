package com._project4.project4.dto;

public class RobotRealtimeResponse {

    private final String id;
    private final String name;
    private final String status;
    private final String currentTask;

    public RobotRealtimeResponse(String id, String name, String status, String currentTask) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.currentTask = currentTask;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrentTask() {
        return currentTask;
    }
}