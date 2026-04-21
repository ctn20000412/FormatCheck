package com.formatcheck.backend.common;

public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }
}
