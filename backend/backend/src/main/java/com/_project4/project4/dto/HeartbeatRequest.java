package com._project4.project4.dto;

import jakarta.validation.constraints.Positive;

public class HeartbeatRequest {

    @Positive
    private Long timestamp;

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
