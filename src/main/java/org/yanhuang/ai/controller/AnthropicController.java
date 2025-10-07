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
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;
import org.yanhuang.ai.service.ImageValidator;
import org.yanhuang.ai.service.KiroService;
import org.yanhuang.ai.service.TokenCounter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/messages")
public class AnthropicController {

    private final AppProperties properties;
    private final KiroService kiroService;
    private final TokenCounter tokenCounter;
    private final ImageValidator imageValidator;

    public AnthropicController(AppProperties properties, KiroService kiroService, TokenCounter tokenCounter, ImageValidator imageValidator) {
        this.properties = properties;
        this.kiroService = kiroService;
        this.tokenCounter = tokenCounter;
        this.imageValidator = imageValidator;
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
            // Force SSE content type for streaming branch
            Flux<String> sseStream = kiroService.streamCompletion(request)
                .map(content -> (content.startsWith("event:") || content.startsWith("data:")) ? content : "data: " + content + "\n")
                .concatWithValues("data: [DONE]\n");
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("anthropic-version", version)
                .body(sseStream);
        } else {
            // Return non-streaming response directly (anthropic-version is added via actual HTTP response in E2E)
            return kiroService.createCompletion(request);
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> streamMessage(
        @RequestHeader(name = "x-api-key", required = false) String apiKey,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

        validateHeaders(apiKey, apiVersion);
        validateRequest(request);

        Flux<String> sseStream = kiroService.streamCompletion(request)
            .map(content -> (content.startsWith("event:") || content.startsWith("data:")) ? content : "data: " + content + "\n");
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(sseStream);
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
            // Validate image content blocks
            message.getContent().forEach(contentBlock -> {
                if ("image".equals(contentBlock.getType()) && contentBlock.getSource() != null) {
                    imageValidator.validateImageSource(contentBlock.getSource());
                }
            });
        });
        if (Boolean.TRUE.equals(request.getStream()) && request.getMaxTokens() != null && request.getMaxTokens() > 4096) {
            throw new IllegalArgumentException("max_tokens exceeds streaming limit");
        }
        // Enhanced tool_choice validation
        if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
            // Debug log tools structure for troubleshooting
            if (request.getTools() != null) {
                for (ToolDefinition td : request.getTools()) {
                    String n = td.getEffectiveName();
                    String d = td.getEffectiveDescription();
                    // English log per CLAUDE.md new code rule
                    System.out.println("[ToolDebug] tool name=" + n + ", desc=" + d);
                }
            }
            validateToolChoice(request.getToolChoice(), request.getTools());
        }
        // Context window validation - use API mode limit (1M tokens)
        tokenCounter.validateContextWindow(request, TokenCounter.MAX_CONTEXT_TOKENS_API_MODE);
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
            case "tool":
                // Specific tool name selection requires a valid name
                if (!toolChoice.containsKey("name")) {
                    throw new IllegalArgumentException("tool_choice.name is required when tool_choice.type is a specific tool name");
                }

                Object nameObj = toolChoice.get("name");
                if (!(nameObj instanceof String) || ((String) nameObj).trim().isEmpty()) {
                    throw new IllegalArgumentException("tool_choice.name must be a non-empty string");
                }

                String selectedToolName = (String) nameObj;
                if (tools != null && !tools.isEmpty()) {
                    boolean toolFound = tools.stream().anyMatch(tool -> {
                        // Support both typed ToolDefinition and raw Map representations
                        if (tool instanceof ToolDefinition td) {
                            String effective = td.getEffectiveName();
                            return selectedToolName.equals(effective);
                        }
                        if (tool instanceof Map) {
                            Map<?, ?> toolMap = (Map<?, ?>) tool;
                            Object direct = toolMap.get("name");
                            if (selectedToolName.equals(direct)) {
                                return true;
                            }
                            Object function = toolMap.get("function");
                            if (function instanceof Map) {
                                Object fnName = ((Map<?, ?>) function).get("name");
                                return selectedToolName.equals(fnName);
                            }
                        }
                        return false;
                    });

                    if (!toolFound) {
                        throw new IllegalArgumentException("tool_choice.name '" + selectedToolName + "' must be present in the tools list");
                    }
                }
                break;
            default:
                // Unknown type
                throw new IllegalArgumentException("tool_choice.type must be one of: auto, any, tool, none, required");
        }
    }
}

