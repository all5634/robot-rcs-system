package com._project4.project4.common;

public final class ErrorCode {

    public static final int SUCCESS = 0;
    public static final int VALIDATION_ERROR = 4001;
    public static final int ROBOT_NOT_FOUND = 4041;
    public static final int COMMAND_NOT_FOUND = 4042;
    public static final int INTERNAL_SERVER_ERROR = 5000;

    private ErrorCode() {
    }
}