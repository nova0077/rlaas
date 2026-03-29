package com.ratelimiter.exception;

import com.ratelimiter.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Centralized Error Handling
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY @RestControllerAdvice?
 * ─────────────────────────────────────────────────────────────────────────────
 * Without this, if an exception is thrown anywhere in the app, Spring Boot
 * returns its default fallback error page (the "White Label Error Page")
 * or a raw HTML stack trace, which breaks API clients expecting JSON.
 *
 * @RestControllerAdvice intercepts ALL exceptions thrown by ALL Controllers.
 *                       It maps Java exceptions into nice `ApiResponse` JSON
 *                       envelopes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Bean Validation errors (e.g. @NotNull, @Min failed).
     * 
     * Spring throws MethodArgumentNotValidException automatically
     * when a @Valid @RequestBody has invalid data.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        log.warn("Validation error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errors));
    }

    /**
     * Handle our custom RateLimitExceededException.
     * This isn't strictly necessary since our RateLimitFilter handles 429s,
     * but it's good practice if we ever throw this inside a service layer.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded exception: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Catch-all fallback for any unexpected errors (NullPointerExceptions, DB
     * drops, etc).
     * We don't want to leak internal stack traces to users.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllOtherExceptions(Exception ex) {
        log.error("Unhandled systemic exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected internal error occurred."));
    }
}
