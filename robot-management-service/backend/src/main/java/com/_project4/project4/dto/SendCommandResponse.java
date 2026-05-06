package com._project4.project4.dto;

public class SendCommandResponse {

    private final String commandId;

    public SendCommandResponse(String commandId) {
        this.commandId = commandId;
    }

    public String getCommandId() {
        return commandId;
    }
}
