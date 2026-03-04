package com.banking.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean       success,
        String        message,
        T             data,
        String        errorCode,
        LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data)                         { return new ApiResponse<>(true,  "Success", data, null, LocalDateTime.now()); }
    public static <T> ApiResponse<T> success(String message, T data)         { return new ApiResponse<>(true,  message,   data, null, LocalDateTime.now()); }
    public static <T> ApiResponse<T> failure(String message, String code)    { return new ApiResponse<>(false, message,   null, code, LocalDateTime.now()); }
}
