package org.yanhuang.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.yanhuang.ai.model.AnthropicErrorResponse;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AnthropicErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.error("=== IllegalArgumentException Handler ===");
        log.error("Error: {}", ex.getMessage());
        log.error("Stack trace: ", ex);

        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.invalidRequest(ex.getMessage(), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<AnthropicErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.error("=== IllegalStateException Handler ===");
        log.error("Error: {}", ex.getMessage());
        log.error("Stack trace: ", ex);

        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.fromException(ex);
        HttpStatus status = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("api key")
            ? HttpStatus.UNAUTHORIZED
            : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.TooManyRequests.class)
    public ResponseEntity<AnthropicErrorResponse> handleRateLimit(WebClientResponseException.TooManyRequests ex) {
        log.error("=== TooManyRequests Exception Handler ===");
        log.error("Status Code: {}", ex.getStatusCode());
        log.error("Status Text: {}", ex.getStatusText());
        log.error("Response Body: {}", ex.getResponseBodyAsString());
        log.error("Stack trace: ", ex);

        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.rateLimitError("Rate limit exceeded");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.Unauthorized.class)
    public ResponseEntity<AnthropicErrorResponse> handleUnauthorized(WebClientResponseException.Unauthorized ex) {
        log.error("=== Unauthorized Exception Handler ===");
        log.error("Status Code: {}", ex.getStatusCode());
        log.error("Status Text: {}", ex.getStatusText());
        log.error("Response Body: {}", ex.getResponseBodyAsString());
        log.error("Stack trace: ", ex);

        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.authenticationError("Authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.Forbidden.class)
    public ResponseEntity<AnthropicErrorResponse> handleForbidden(WebClientResponseException.Forbidden ex) {
        log.debug("Forbidden: {}", ex.getMessage());
        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.permissionError("Permission denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.NotFound.class)
    public ResponseEntity<AnthropicErrorResponse> handleNotFound(WebClientResponseException.NotFound ex) {
        log.debug("Not found: {}", ex.getMessage());
        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.notFoundError("Resource not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<AnthropicErrorResponse> handleWebClientError(WebClientResponseException ex) {
        log.error("=== WebClientResponseException Handler ===");
        log.error("Status Code: {}", ex.getStatusCode());
        log.error("Status Text: {}", ex.getStatusText());
        log.error("Response Body: {}", ex.getResponseBodyAsString());
        log.error("Headers: {}", ex.getHeaders());
        log.error("Stack trace: ", ex);

        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.apiError("External API error");
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnthropicErrorResponse> handleGeneric(Exception ex) {
        log.error("=== Generic Exception Handler ===");
        log.error("Error Type: {}", ex.getClass().getSimpleName());
        log.error("Error Message: {}", ex.getMessage());
        log.error("Stack trace: ", ex);

        AnthropicErrorResponse errorResponse = AnthropicErrorResponse.internalServerError("Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

