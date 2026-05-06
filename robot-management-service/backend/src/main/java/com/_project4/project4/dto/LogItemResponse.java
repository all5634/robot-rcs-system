package com._project4.project4.dto;

public class LogItemResponse {

    private final String type;
    private final String msg;

    public LogItemResponse(String type, String msg) {
        this.type = type;
        this.msg = msg;
    }

    public String getType() {
        return type;
    }

    public String getMsg() {
        return msg;
    }
}