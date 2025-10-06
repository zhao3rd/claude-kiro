package org.yanhuang.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an Anthropic-compatible error response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicErrorResponse {

    @JsonProperty("type")
    private final String type = "error";

    @JsonProperty("error")
    private final ErrorDetail error;

    public AnthropicErrorResponse(ErrorType errorType, String message) {
        this(errorType, message, null);
    }

    public AnthropicErrorResponse(ErrorType errorType, String message, String param) {
        this.error = new ErrorDetail(errorType, message, param);
    }

    public String getType() {
        return type;
    }

    public ErrorDetail getError() {
        return error;
    }

    public static AnthropicErrorResponse invalidRequest(String message, String param) {
        return new AnthropicErrorResponse(ErrorType.INVALID_REQUEST_ERROR, message, param);
    }

    public static AnthropicErrorResponse authenticationError(String message) {
        return new AnthropicErrorResponse(ErrorType.AUTHENTICATION_ERROR, message);
    }

    public static AnthropicErrorResponse permissionError(String message) {
        return new AnthropicErrorResponse(ErrorType.PERMISSION_ERROR, message);
    }

    public static AnthropicErrorResponse notFoundError(String message) {
        return new AnthropicErrorResponse(ErrorType.NOT_FOUND_ERROR, message);
    }

    public static AnthropicErrorResponse rateLimitError(String message) {
        return new AnthropicErrorResponse(ErrorType.RATE_LIMIT_ERROR, message);
    }

    public static AnthropicErrorResponse apiError(String message) {
        return new AnthropicErrorResponse(ErrorType.API_ERROR, message);
    }

    public static AnthropicErrorResponse overloadError(String message) {
        return new AnthropicErrorResponse(ErrorType.OVERLOAD_ERROR, message);
    }

    public static AnthropicErrorResponse internalServerError(String message) {
        return new AnthropicErrorResponse(ErrorType.INTERNAL_SERVER_ERROR, message);
    }

    /**
     * Maps exceptions to appropriate Anthropic error types
     */
    public static AnthropicErrorResponse fromException(Exception ex) {
        if (ex instanceof IllegalArgumentException) {
            return invalidRequest(ex.getMessage(), null);
        } else if (ex instanceof IllegalStateException) {
            String message = ex.getMessage();
            if (message != null && message.toLowerCase().contains("api key")) {
                return authenticationError(message);
            } else {
                return permissionError(message);
            }
        } else if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests) {
            return rateLimitError("Rate limit exceeded");
        } else if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.NotFound) {
            return notFoundError("Resource not found");
        } else if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized) {
            return authenticationError("Authentication failed");
        } else if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden) {
            return permissionError("Permission denied");
        } else if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            return apiError("External API error");
        } else {
            return internalServerError("Internal server error");
        }
    }

    public enum ErrorType {
        @JsonProperty("invalid_request_error")
        INVALID_REQUEST_ERROR("invalid_request_error"),

        @JsonProperty("authentication_error")
        AUTHENTICATION_ERROR("authentication_error"),

        @JsonProperty("permission_error")
        PERMISSION_ERROR("permission_error"),

        @JsonProperty("not_found_error")
        NOT_FOUND_ERROR("not_found_error"),

        @JsonProperty("rate_limit_error")
        RATE_LIMIT_ERROR("rate_limit_error"),

        @JsonProperty("api_error")
        API_ERROR("api_error"),

        @JsonProperty("overload_error")
        OVERLOAD_ERROR("overload_error"),

        @JsonProperty("internal_server_error")
        INTERNAL_SERVER_ERROR("internal_server_error");

        private final String value;

        ErrorType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static class ErrorDetail {
        @JsonProperty("type")
        private final String type;

        @JsonProperty("message")
        private final String message;

        @JsonProperty("param")
        private final String param;

        @JsonProperty("code")
        private final String code;

        public ErrorDetail(ErrorType errorType, String message, String param) {
            this.type = errorType.getValue();
            this.message = message;
            this.param = param;
            this.code = mapToErrorCode(errorType);
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public String getParam() {
            return param;
        }

        public String getCode() {
            return code;
        }

        private String mapToErrorCode(ErrorType errorType) {
            switch (errorType) {
                case INVALID_REQUEST_ERROR:
                    return "invalid_request";
                case AUTHENTICATION_ERROR:
                    return "invalid_api_key";
                case PERMISSION_ERROR:
                    return "permission_denied";
                case NOT_FOUND_ERROR:
                    return "not_found";
                case RATE_LIMIT_ERROR:
                    return "rate_limit_exceeded";
                case API_ERROR:
                    return "api_error";
                case OVERLOAD_ERROR:
                    return "overloaded";
                case INTERNAL_SERVER_ERROR:
                    return "internal_error";
                default:
                    return "unknown_error";
            }
        }
    }
}