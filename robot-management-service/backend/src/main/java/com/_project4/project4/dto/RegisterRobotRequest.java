package com._project4.project4.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterRobotRequest {

    @NotBlank
    private String robotCode;

    @NotBlank
    private String model;

    @NotBlank
    private String capabilities;

    public String getRobotCode() {
        return robotCode;
    }

    public void setRobotCode(String robotCode) {
        this.robotCode = robotCode;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }
}
