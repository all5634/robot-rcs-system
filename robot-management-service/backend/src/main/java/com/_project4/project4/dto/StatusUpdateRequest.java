package com._project4.project4.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StatusUpdateRequest {

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must match ^[A-Z][A-Z0-9_]*$")
    private String status;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer battery;

    @NotNull
    @Valid
    private PositionDto position;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getBattery() {
        return battery;
    }

    public void setBattery(Integer battery) {
        this.battery = battery;
    }

    public PositionDto getPosition() {
        return position;
    }

    public void setPosition(PositionDto position) {
        this.position = position;
    }
}
