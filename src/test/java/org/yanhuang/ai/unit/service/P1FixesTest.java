package org.yanhuang.ai.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.controller.AnthropicController;
import org.yanhuang.ai.controller.GlobalExceptionHandler;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicErrorResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;
import org.yanhuang.ai.parser.CodeWhispererEventParser;
import org.yanhuang.ai.parser.BracketToolCallParser;
import org.yanhuang.ai.service.KiroService;
import org.yanhuang.ai.service.TokenManager;
import org.yanhuang.ai.parser.ToolCallDeduplicator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * P1 fixes test suite - Tests for Priority 1 Anthropic API compatibility improvements
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("P1 Fixes Test Suite")
class P1FixesTest {

    @Mock
    private KiroService kiroService;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private CodeWhispererEventParser eventParser;

    @Mock
    private BracketToolCallParser bracketToolCallParser;

    @Mock
    private ToolCallDeduplicator toolCallDeduplicator;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private ObjectMapper mapper;
    private AppProperties properties;
    private AnthropicController controller;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        properties = new AppProperties();
        properties.setApiKey("test-api-key");
        properties.setAnthropicVersion("2023-06-01");

        // Initialize controller with mocked KiroService (unit tests stub service methods directly)
        controller = new AnthropicController(properties, kiroService);
        webTestClient = WebTestClient.bindToController(controller)
            .controllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    // P1-1: Unified streaming endpoint tests

    @Test
    @DisplayName("P1-1: Non-streaming request should return JSON response")
    void testUnifiedEndpointNonStreaming() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setStream(false);

        AnthropicChatResponse mockResponse = createBasicResponse();
        when(kiroService.createCompletion(any())).thenReturn(Mono.just(mockResponse));

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").exists()
            .jsonPath("$.type").isEqualTo("message")
            .jsonPath("$.role").isEqualTo("assistant");
    }

    @Test
    @DisplayName("P1-1: Streaming request should return SSE response")
    void testUnifiedEndpointStreaming() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setStream(true);

        when(kiroService.streamCompletion(any())).thenReturn(Flux.just(
            "event: message_start\ndata: {\"type\":\"message_start\"}\n\n",
            "event: content_block_start\ndata: {\"type\":\"content_block_start\"}\n\n",
            "data: [DONE]\n"
        ));

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith("text/event-stream")
            .expectBody()
            .consumeWith(response -> {
                String body = new String(response.getResponseBody());
                assertThat(body).contains("event: message_start");
                assertThat(body).contains("data: {\"type\":\"message_start\"}");
                assertThat(body).contains("data: [DONE]");
            });
    }

    @Test
    @DisplayName("P1-1: Legacy /stream endpoint should still work")
    void testLegacyStreamingEndpoint() {
        // Given
        AnthropicChatRequest request = createBasicRequest();

        when(kiroService.streamCompletion(any())).thenReturn(Flux.just(
            "event: message_start\ndata: {\"type\":\"message_start\"}\n\n"
        ));

        // When & Then
        webTestClient.post()
            .uri("/v1/messages/stream")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith("text/event-stream")
            .expectBody()
            .consumeWith(response -> {
                String body = new String(response.getResponseBody());
                assertThat(body).contains("event: message_start");
            });
    }

    // P1-2: Enhanced tool_choice validation tests

    @Test
    @DisplayName("P1-2: tool_choice.type should be required")
    void testToolChoiceTypeRequired() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("name", "get_weather")); // Missing type

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.type").isEqualTo("error")
            .jsonPath("$.error.type").isEqualTo("invalid_request_error")
            .jsonPath("$.error.message").isEqualTo("tool_choice.type is required when tool_choice is provided");
    }

    @Test
    @DisplayName("P1-2: tool_choice.type should be string")
    void testToolChoiceTypeShouldBeString() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("type", 123)); // Non-string type

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.type").isEqualTo("invalid_request_error")
            .jsonPath("$.error.message").isEqualTo("tool_choice.type must be a string");
    }

    @Test
    @DisplayName("P1-2: tool_choice.type 'none' should not have name")
    void testToolChoiceNoneNoName() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("type", "none", "name", "get_weather"));

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.message").isEqualTo("tool_choice.name should not be provided when type is 'none'");
    }

    @Test
    @DisplayName("P1-2: tool_choice.type 'required' should require tools")
    void testToolChoiceRequiredNeedsTools() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("type", "required"));
        // No tools provided

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.message").isEqualTo("tools must be provided when tool_choice.type is 'required'");
    }

    @Test
    @DisplayName("P1-2: tool_choice with specific tool name should require name")
    void testToolChoiceSpecificNeedsName() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("type", "get_weather")); // Missing name

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.message").isEqualTo("tool_choice.name is required when tool_choice.type is a specific tool name");
    }

    @Test
    @DisplayName("P1-2: tool_choice name should be non-empty string")
    void testToolChoiceNameNonEmpty() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("type", "get_weather", "name", "")); // Empty name

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.message").isEqualTo("tool_choice.name must be a non-empty string");
    }

    @Test
    @DisplayName("P1-2: tool_choice name should exist in tools list")
    void testToolChoiceNameInToolsList() {
        // Given
        AnthropicChatRequest request = createBasicRequest();
        request.setToolChoice(Map.of("type", "get_weather", "name", "get_weather"));

        // Tools list doesn't contain get_weather
        ToolDefinition tool = new ToolDefinition();
        tool.setName("search");
        tool.setDescription("Search the web");
        request.setTools(List.of(tool));

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.error.message").isEqualTo("tool_choice.name 'get_weather' must be present in the tools list");
    }

    // P1-3: Unified error response format tests

    @Test
    @DisplayName("P1-3: Invalid request should return Anthropic error format")
    void testAnthropicErrorFormat() {
        // Given
        AnthropicChatRequest request = new AnthropicChatRequest();
        // Missing required fields

        // When & Then
        webTestClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-api-key")
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.type").isEqualTo("error")
            .jsonPath("$.error.type").exists()
            .jsonPath("$.error.message").exists()
            .jsonPath("$.error.code").exists();
    }

    @Test
    @DisplayName("P1-3: Authentication error should have correct format")
    void testAuthenticationErrorFormat() {
        // Given
        AnthropicChatRequest request = createBasicRequest();

        // When & Then - Missing API key
        webTestClient.post()
            .uri("/v1/messages")
            .header("anthropic-version", "2023-06-01")
            .header("x-api-key", "invalid-api-key") // Invalid API key instead of missing
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.type").isEqualTo("error")
            .jsonPath("$.error.type").isEqualTo("authentication_error")
            .jsonPath("$.error.code").isEqualTo("invalid_api_key");
    }

    // Helper methods

    private AnthropicChatRequest createBasicRequest() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);
        request.setStream(false);

        AnthropicMessage userMsg = new AnthropicMessage();
        userMsg.setRole("user");
        AnthropicMessage.ContentBlock textBlock = new AnthropicMessage.ContentBlock();
        textBlock.setType("text");
        textBlock.setText("Hello");
        userMsg.setContent(List.of(textBlock));

        request.setMessages(List.of(userMsg));
        return request;
    }

    private AnthropicChatResponse createBasicResponse() {
        AnthropicChatResponse response = new AnthropicChatResponse();
        response.setId("msg_test123");
        response.setType("message");
        response.setRole("assistant");
        response.setModel("claude-sonnet-4-5-20250929");

        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText("Hello! How can I help you?");
        response.setContent(List.of(block));

        response.setStopReason("end_turn");
        return response;
    }
}