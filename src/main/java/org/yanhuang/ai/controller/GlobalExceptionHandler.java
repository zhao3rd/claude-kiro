package org.yanhuang.ai.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request_error", ex.getMessage(), "invalid_request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("authentication_error", ex.getMessage(), "invalid_api_key"));
    }

    @ExceptionHandler(WebClientResponseException.TooManyRequests.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(WebClientResponseException.TooManyRequests ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(error("rate_limit_error", "Rate limit exceeded", "rate_limit_exceeded"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("internal_server_error", "Internal server error", "internal_error"));
    }

    private Map<String, Object> error(String type, String message, String code) {
        Map<String, Object> payload = new java.util.HashMap<>();
        Map<String, Object> error = new java.util.HashMap<>();
        error.put("type", type);
        error.put("message", message);
        error.put("param", null);
        error.put("code", code);
        payload.put("error", error);
        return payload;
    }
}

