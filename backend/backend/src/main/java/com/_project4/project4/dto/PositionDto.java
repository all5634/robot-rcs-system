package com._project4.project4.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class PositionDto {

    @NotNull
    @DecimalMin(value = "-1000000")
    @DecimalMax(value = "1000000")
    private Double x;

    @NotNull
    @DecimalMin(value = "-1000000")
    @DecimalMax(value = "1000000")
    private Double y;

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }
}
