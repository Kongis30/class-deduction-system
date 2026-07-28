package com.classdeduction.system.common.model;

import com.classdeduction.system.common.time.AppTime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Standard API response wrapper shared by all controllers.
 */
@Schema(description = "Standard API response payload")
public record ApiResponse<T>(
        @Schema(description = "Whether the request succeeded")
        boolean success,
        @Schema(description = "Response code")
        String code,
        @Schema(description = "Response message")
        String message,
        @Schema(description = "Business payload")
        T data,
        @Schema(description = "Response timestamp in the app timezone")
        OffsetDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "Success", data, AppTime.nowOffsetDateTime());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data, AppTime.nowOffsetDateTime());
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null, AppTime.nowOffsetDateTime());
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data, AppTime.nowOffsetDateTime());
    }
}
