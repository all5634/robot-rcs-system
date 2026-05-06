package com._project4.project4.dto;

public class CommandStatusResponse {

    private final String commandId;
    private final String status;

    public CommandStatusResponse(String commandId, String status) {
        this.commandId = commandId;
        this.status = status;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getStatus() {
        return status;
    }
}
