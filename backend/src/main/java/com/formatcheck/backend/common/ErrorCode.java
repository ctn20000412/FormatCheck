package com.formatcheck.backend.common;

public enum ErrorCode {
    BAD_REQUEST(400),
    NOT_FOUND(404),
    FILE_TOO_LARGE(413),
    UNPROCESSABLE_ENTITY(422),
    INTERNAL_ERROR(500),
    SERVICE_UNAVAILABLE(503),
    GATEWAY_TIMEOUT(504);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
