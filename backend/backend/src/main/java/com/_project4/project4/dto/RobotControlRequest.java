package com._project4.project4.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RobotControlRequest {

    @NotBlank
    @Pattern(regexp = "^(DISPATCH|STOP|RESUME)$", message = "must be one of DISPATCH, STOP, RESUME")
    private String type;

    @NotBlank
    @Size(max = 64)
    private String robotId;

    @Valid
    private PositionDto position;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRobotId() {
        return robotId;
    }

    public void setRobotId(String robotId) {
        this.robotId = robotId;
    }

    public PositionDto getPosition() {
        return position;
    }

    public void setPosition(PositionDto position) {
        this.position = position;
    }
}