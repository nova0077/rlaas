package com.ratelimiter.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * ApiResponse — A standard JSON envelope for all API responses.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY ENVELOPE RESPONSES?
 * ─────────────────────────────────────────────────────────────────────────────
 * Without an envelope, your API might return varying shapes:
 * Success: { "name": "policy", ... }
 * Error: "Something went wrong" (Plain text)
 *
 * This makes client-side parsing a nightmare. A standard envelope ensures
 * EVERY response has the exact same root structure.
 *
 * {
 * "success": true,
 * "message": "Policy created",
 * "data": { "name": "my-policy", ... }
 * }
 *
 * Using Generics <T> means this one class can wrap any data type (Policy, List,
 * etc).
 */
@Data
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // Static factory for success without data
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    // Static factory for success with data
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // Static factory for errors
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    // Static factory for errors with data
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(data)
                .build();
    }
}
