package org.yanhuang.ai.controller;

import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/v1/messages")
public class AnthropicController {

    private final AppProperties properties;
    private final KiroService kiroService;

    public AnthropicController(AppProperties properties, KiroService kiroService) {
        this.properties = properties;
        this.kiroService = kiroService;
    }

    @PostMapping
    public Mono<ResponseEntity<AnthropicChatResponse>> createMessage(
        @RequestHeader(name = "x-api-key", required = false) String apiKey,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

        validateHeaders(apiKey, apiVersion);
        validateRequest(request);

        String version = StringUtils.hasText(apiVersion) ? apiVersion : properties.getAnthropicVersion();

        return kiroService.createCompletion(request)
            .map(response -> ResponseEntity.ok()
                .header("anthropic-version", version)
                .body(response));
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
        if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
            if (!request.getToolChoice().containsKey("type")) {
                throw new IllegalArgumentException("tool_choice.type is required when tool_choice is provided");
            }
        }
    }
}

