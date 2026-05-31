package com.bothash.admissionservice.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Project-wide handler that turns common service-layer exceptions into clean
 * JSON responses for any {@code @RestController} endpoint. The UI client
 * extracts the {@code message} field and surfaces it through the global
 * notice modal, so users see a focused error rather than a stacktrace.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        String message = ex.getMessage() != null ? ex.getMessage() : "Bad request.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        String message = ex.getMessage() != null ? ex.getMessage() : "Operation conflicts with current state.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", message));
    }
}
