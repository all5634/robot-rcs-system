package com._project4.project4.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SendCommandRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must match ^[A-Z][A-Z0-9_]*$")
    private String commandType;

    private Map<String, Object> params;

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
