package org.yanhuang.ai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.service.KiroService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/messages")
public class AnthropicController {

    private final AppProperties properties;
    private final KiroService kiroService;

    public AnthropicController(AppProperties properties, KiroService kiroService) {
        this.properties = properties;
        this.kiroService = kiroService;
    }

    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object createMessage(
        @RequestHeader(name = "x-api-key", required = false) String apiKey,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

        validateHeaders(apiKey, apiVersion);
        validateRequest(request);

        String version = StringUtils.hasText(apiVersion) ? apiVersion : properties.getAnthropicVersion();

        // Check if streaming is requested
        if (Boolean.TRUE.equals(request.getStream())) {
            // Return streaming response with SSE content type
            return kiroService.streamCompletion(request)
                .map(content -> content.startsWith("event:") ? content : "data: " + content + "\n")
                .concatWithValues("data: [DONE]\n");
        } else {
            // Return non-streaming response
            return kiroService.createCompletion(request)
                .map(response -> ResponseEntity.ok()
                    .header("anthropic-version", version)
                    .body(response));
        }
    }

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamMessage(
        @RequestHeader(name = "x-api-key", required = false) String apiKey,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

        validateHeaders(apiKey, apiVersion);
        validateRequest(request);

        return kiroService.streamCompletion(request);
    }

    private void validateHeaders(String apiKey, String apiVersion) {
        if (!StringUtils.hasText(apiKey) || !properties.getApiKey().equals(apiKey)) {
            throw new IllegalStateException("invalid api key");
        }
        if (!StringUtils.hasText(apiVersion)) {
            throw new IllegalArgumentException("anthropic-version header is required");
        }
    }

    private void validateRequest(AnthropicChatRequest request) {
        if (!StringUtils.hasText(request.getModel())) {
            throw new IllegalArgumentException("model is required");
        }
        if (request.getMaxTokens() == null || request.getMaxTokens() <= 0) {
            throw new IllegalArgumentException("max_tokens must be a positive integer");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages must contain at least one entry");
        }
        request.getMessages().forEach(message -> {
            if (!StringUtils.hasText(message.getRole())) {
                throw new IllegalArgumentException("message role is required");
            }
            if (message.getContent() == null || message.getContent().isEmpty()) {
                throw new IllegalArgumentException("message content cannot be empty");
            }
        });
        if (Boolean.TRUE.equals(request.getStream()) && request.getMaxTokens() != null && request.getMaxTokens() > 4096) {
            throw new IllegalArgumentException("max_tokens exceeds streaming limit");
        }
        // Enhanced tool_choice validation
        if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
            validateToolChoice(request.getToolChoice(), request.getTools());
        }
    }

    private void validateToolChoice(Map<String, Object> toolChoice, List<?> tools) {
        // Check that type is required
        if (!toolChoice.containsKey("type")) {
            throw new IllegalArgumentException("tool_choice.type is required when tool_choice is provided");
        }

        Object type = toolChoice.get("type");
        if (!(type instanceof String)) {
            throw new IllegalArgumentException("tool_choice.type must be a string");
        }

        String choiceType = (String) type;

        // Validate supported types
        switch (choiceType) {
            case "auto":
            case "any":
                // These types don't require additional validation
                break;
            case "none":
                // This type should not have a name field
                if (toolChoice.containsKey("name")) {
                    throw new IllegalArgumentException("tool_choice.name should not be provided when type is 'none'");
                }
                break;
            case "required":
                // For required, tools must be available
                if (tools == null || tools.isEmpty()) {
                    throw new IllegalArgumentException("tools must be provided when tool_choice.type is 'required'");
                }
                break;
            default:
                // For specific tool names, validate that the tool exists in the tools list
                if (!toolChoice.containsKey("name")) {
                    throw new IllegalArgumentException("tool_choice.name is required when tool_choice.type is a specific tool name");
                }

                Object name = toolChoice.get("name");
                if (!(name instanceof String) || ((String) name).trim().isEmpty()) {
                    throw new IllegalArgumentException("tool_choice.name must be a non-empty string");
                }

                // If tools are provided, validate the specific tool exists
                if (tools != null && !tools.isEmpty()) {
                    boolean toolFound = tools.stream()
                        .anyMatch(tool -> {
                            if (tool instanceof Map) {
                                Map<?, ?> toolMap = (Map<?, ?>) tool;
                                Object toolName = toolMap.get("name");
                                return name.equals(toolName);
                            }
                            return false;
                        });

                    if (!toolFound) {
                        throw new IllegalArgumentException("tool_choice.name '" + name + "' must be present in the tools list");
                    }
                }
                break;
        }
    }
}

