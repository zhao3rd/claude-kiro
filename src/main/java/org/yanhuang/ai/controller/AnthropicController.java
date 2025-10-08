package org.yanhuang.ai.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/v1/messages")
public class AnthropicController {

    private static final Logger log = LoggerFactory.getLogger(AnthropicController.class);

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
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

        // Enhanced request logging for debugging
        if (log.isDebugEnabled()) {
            log.debug("=== Claude Code Request Analysis ===");
            log.debug("Endpoint: /v1/messages");
            log.debug("Headers: x-api-key={}, Authorization={}, anthropic-version={}",
                apiKey != null ? "present(" + apiKey.length() + " chars)" : "missing",
                authorization != null ? "present(" + authorization.length() + " chars)" : "missing",
                apiVersion);
        }

        String resolvedApiKey = resolveApiKey(apiKey, authorization);
        if (log.isDebugEnabled()) {
            log.debug("Resolved API key: {}", resolvedApiKey != null ? "present(" + resolvedApiKey.length() + " chars)" : "missing");
        }

        try {
            validateHeaders(resolvedApiKey, apiVersion);
            validateRequest(request);
        } catch (Exception e) {
            log.error("Request validation failed: {}", e.getMessage(), e);
            throw e;
        }

        // Debug: Log full request structure to analyze CC behavior
        if (log.isDebugEnabled() && request.getMessages() != null) {
            log.debug("[DEBUG] Total messages in request: {}", request.getMessages().size());
            for (int i = 0; i < request.getMessages().size(); i++) {
                AnthropicMessage msg = request.getMessages().get(i);
                int contentLength = 0;
                int systemPromptLength = 0;
                int toolResultLength = 0;

                if (msg.getContent() != null) {
                    for (AnthropicMessage.ContentBlock block : msg.getContent()) {
                        if (block.getText() != null) {
                            contentLength += block.getText().length();
                            // Check for system prompt content
                            if (block.getText().startsWith("[System]")) {
                                systemPromptLength += block.getText().length();
                            }
                            // Check for tool result content
                            if (block.getText().startsWith("[Tool ") || block.getText().startsWith("[Called ")) {
                                toolResultLength += block.getText().length();
                            }
                        }
                    }
                }
                log.debug("[DEBUG] Message {}: role={}, total_length={}, system_prompt_length={}, tool_result_length={}, content_preview={}",
                    i, msg.getRole(), contentLength, systemPromptLength, toolResultLength,
                    msg.getContent() != null && !msg.getContent().isEmpty() && msg.getContent().get(0).getText() != null
                        ? truncate(msg.getContent().get(0).getText(), 200) : "");
            }
        }

        // Log Claude Code incoming request summary (non-streaming)
        logClaudeCodeRequest("/v1/messages", request);

        // Log system prompt analysis
        analyzeSystemPrompts(request);

        String version = StringUtils.hasText(apiVersion) ? apiVersion : properties.getAnthropicVersion();
        log.info("Using anthropic-version: {}", version);

        // Check if streaming is requested
        if (Boolean.TRUE.equals(request.getStream())) {
            log.info("Processing as streaming request");
            try {
                // Force SSE content type for streaming branch
                Flux<String> sseStream = kiroService.streamCompletion(request)
                    .map(content -> (content.startsWith("event:") || content.startsWith("data:")) ? content : "data: " + content + "\n")
                    .concatWithValues("data: [DONE]\n")
                    .doOnNext(event -> log.debug("Streaming event: {}", truncate(event, 200)))
                    .doOnError(error -> log.error("Streaming error: {}", error.getMessage(), error))
                    .doOnComplete(() -> log.info("Streaming completed successfully"));

                log.info("Returning streaming response");
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("anthropic-version", version)
                    .body(sseStream);
            } catch (Exception e) {
                log.error("Failed to create streaming response: {}", e.getMessage(), e);
                throw e;
            }
        } else {
            log.info("Processing as non-streaming request");
            try {
                Object response = kiroService.createCompletion(request);
                log.info("Non-streaming request completed successfully");
                return response;
            } catch (Exception e) {
                log.error("Failed to create non-streaming response: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> streamMessage(
        @RequestHeader(name = "x-api-key", required = false) String apiKey,
        @RequestHeader(name = "Authorization", required = false) String authorization,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

        if (log.isDebugEnabled()) {
            log.debug("=== Claude Code Streaming Request Analysis ===");
            log.debug("Endpoint: /v1/messages/stream");
            log.debug("Headers: x-api-key={}, Authorization={}, anthropic-version={}",
                apiKey != null ? "present(" + apiKey.length() + " chars)" : "missing",
                authorization != null ? "present(" + authorization.length() + " chars)" : "missing",
                apiVersion);
        }

        String resolvedApiKey = resolveApiKey(apiKey, authorization);
        if (log.isDebugEnabled()) {
            log.debug("Resolved API key: {}", resolvedApiKey != null ? "present(" + resolvedApiKey.length() + " chars)" : "missing");
        }

        try {
            validateHeaders(resolvedApiKey, apiVersion);
            validateRequest(request);
        } catch (Exception e) {
            log.error("Streaming request validation failed: {}", e.getMessage(), e);
            throw e;
        }

        // Log Claude Code incoming request summary (streaming)
        logClaudeCodeRequest("/v1/messages/stream", request);

        // Log system prompt analysis
        analyzeSystemPrompts(request);

        try {
            log.info("Creating streaming response");
            Flux<String> sseStream = kiroService.streamCompletion(request)
                .map(content -> (content.startsWith("event:") || content.startsWith("data:")) ? content : "data: " + content + "\n")
                .doOnNext(event -> log.debug("Streaming event: {}", truncate(event, 200)))
                .doOnError(error -> log.error("Streaming error: {}", error.getMessage(), error))
                .doOnComplete(() -> log.info("Streaming completed successfully"));

            log.info("Returning streaming response");
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("anthropic-version", StringUtils.hasText(apiVersion) ? apiVersion : properties.getAnthropicVersion())
                .body(sseStream);
        } catch (Exception e) {
            log.error("Failed to create streaming response: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void validateHeaders(String apiKey, String apiVersion) {
        if (!StringUtils.hasText(apiKey) || !properties.getApiKey().equals(apiKey)) {
            throw new IllegalStateException("invalid api key");
        }
        if (!StringUtils.hasText(apiVersion)) {
            throw new IllegalArgumentException("anthropic-version header is required");
        }
    }

    private String resolveApiKey(String apiKey, String authorization) {
        if (StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        if (StringUtils.hasText(authorization)) {
            // Accept formats: "Bearer sk-..." or raw token value
            if (authorization.startsWith("Bearer ")) {
                return authorization.substring(7).trim();
            }
            return authorization.trim();
        }
        return apiKey; // null
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
        if (Boolean.TRUE.equals(request.getStream()) && request.getMaxTokens() != null && request.getMaxTokens() > 64000) {
            // Soft-cap max_tokens for streaming to improve compatibility with clients like Claude Code
            // Instead of rejecting the request, cap to the supported limit and continue
            log.warn("max_tokens {} exceeds streaming limit {}; capping to limit", request.getMaxTokens(), 64000);
            request.setMaxTokens(64000);
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

    // Analyze system prompts in the request
    private void analyzeSystemPrompts(AnthropicChatRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("=== System Prompt Analysis ===");

            // Count system prompts in system blocks
            int systemBlockCount = 0;
            int systemBlockTotalLength = 0;
            if (request.getSystem() != null && !request.getSystem().isEmpty()) {
                systemBlockCount = request.getSystem().size();
                for (AnthropicMessage.ContentBlock block : request.getSystem()) {
                    if (block.getText() != null) {
                        systemBlockTotalLength += block.getText().length();
                    }
                }
                log.debug("System blocks: count={}, total_length={}", systemBlockCount, systemBlockTotalLength);
            }

            // Count system prompts embedded in messages
            int messageSystemPromptCount = 0;
            int messageSystemPromptTotalLength = 0;
            int duplicateSystemPrompts = 0;

            if (request.getMessages() != null) {
                for (AnthropicMessage msg : request.getMessages()) {
                    if (msg.getContent() != null) {
                        for (AnthropicMessage.ContentBlock block : msg.getContent()) {
                            if ("text".equalsIgnoreCase(block.getType()) && block.getText() != null) {
                                String text = block.getText();
                                if (text.startsWith("[System]")) {
                                    messageSystemPromptCount++;
                                    messageSystemPromptTotalLength += text.length();
                                    // Check for duplicate system prompts
                                    if (systemBlockCount > 0) {
                                        duplicateSystemPrompts++;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            log.debug("Message system prompts: count={}, total_length={}", messageSystemPromptCount, messageSystemPromptTotalLength);
            log.debug("Duplicate system prompts found: {}", duplicateSystemPrompts);

            // Calculate total payload size
            int totalSize = estimateRequestSize(request);
            log.debug("Estimated request size: {} bytes ({} KB)", totalSize, totalSize / 1024);

            // Check for potential issues
            if (messageSystemPromptCount > 0 && systemBlockCount > 0) {
                log.warn("WARNING: System prompts found in both system blocks and messages - potential duplication");
            }

            if (messageSystemPromptTotalLength > 100000) { // 100KB
                log.warn("WARNING: Large system prompts in messages: {} bytes", messageSystemPromptTotalLength);
            }
        }
    }

    // Estimate total request size
    private int estimateRequestSize(AnthropicChatRequest request) {
        int size = 500; // Base JSON structure

        if (request.getSystem() != null) {
            for (AnthropicMessage.ContentBlock block : request.getSystem()) {
                if (block.getText() != null) {
                    size += block.getText().length();
                }
            }
        }

        if (request.getMessages() != null) {
            for (AnthropicMessage msg : request.getMessages()) {
                if (msg.getContent() != null) {
                    for (AnthropicMessage.ContentBlock block : msg.getContent()) {
                        if (block.getText() != null) {
                            size += block.getText().length();
                        }
                    }
                }
            }
        }

        if (request.getTools() != null) {
            size += request.getTools().size() * 200; // Approximate tool definition size
        }

        return size;
    }

    // Log a concise summary of Claude Code request for troubleshooting
    private void logClaudeCodeRequest(String endpoint, AnthropicChatRequest request) {
        if (log.isDebugEnabled()) {
            try {
                int imageCount = 0;
                int textCount = 0;
                StringBuilder textSamples = new StringBuilder();

                if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                    AnthropicMessage last = request.getMessages().get(request.getMessages().size() - 1);
                    if (last.getContent() != null) {
                        for (AnthropicMessage.ContentBlock cb : last.getContent()) {
                            if ("image".equalsIgnoreCase(cb.getType())) {
                                imageCount++;
                            } else if ("text".equalsIgnoreCase(cb.getType())) {
                                textCount++;
                                if (textSamples.length() < 800) {
                                    String t = cb.getText() == null ? "" : cb.getText();
                                    textSamples.append(truncate(t, 400)).append(" | ");
                                }
                            }
                        }
                    }
                }

                String toolNames = "";
                if (request.getTools() != null && !request.getTools().isEmpty()) {
                    StringBuilder tn = new StringBuilder();
                    for (ToolDefinition td : request.getTools()) {
                        String n = td.getEffectiveName();
                        if (n != null) tn.append(n).append(',');
                    }
                    if (tn.length() > 0) tn.setLength(tn.length() - 1);
                    toolNames = tn.toString();
                }

                Object choice = request.getToolChoice();
                String toolChoiceSummary;
                if (choice instanceof Map<?, ?> m) {
                    String typeStr = String.valueOf(m.get("type"));
                    String nameStr = String.valueOf(m.get("name"));
                    toolChoiceSummary = "type=" + ("null".equals(typeStr) ? "" : typeStr)
                            + ", name=" + ("null".equals(nameStr) ? "" : nameStr);
                } else {
                    toolChoiceSummary = String.valueOf(choice);
                }

                log.debug("[ClaudeCode] endpoint={}, model={}, stream={}, images={}, texts={}, tool_choice={}, tools={}, text_samples={}",
                    endpoint,
                    request.getModel(),
                    Boolean.TRUE.equals(request.getStream()),
                    imageCount,
                    textCount,
                    toolChoiceSummary,
                    toolNames,
                    truncate(textSamples.toString(), 800)
                );
            } catch (Exception e) {
                log.warn("[ClaudeCode] failed to log request summary: {}", e.getMessage());
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 3)) + "...";
    }

    /**
     * Health check endpoint for monitoring application status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("[Health] Health check requested");

        Map<String, Object> health = Map.of(
            "status", "healthy",
            "timestamp", System.currentTimeMillis(),
            "service", "claude-kiro",
            "version", "1.0.0",
            "kiroProfile", properties.getKiro().getProfileArn() != null ? "configured" : "missing",
            "toolsDisabled", System.getProperty("kiro.disable.tools", "false")
        );

        return ResponseEntity.ok(health);
    }
}

