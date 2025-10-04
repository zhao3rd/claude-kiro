package org.yanhuang.ai.unit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.controller.AnthropicController;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.service.KiroService;
import org.yanhuang.ai.TestDataFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AnthropicController 单元测试")
class AnthropicControllerTest {

    @Mock
    private AppProperties properties;

    @Mock
    private KiroService kiroService;

    @InjectMocks
    private AnthropicController controller;

    @BeforeEach
    void setUp() {
        // Setup AppProperties mock - these are used by most tests
        when(properties.getApiKey()).thenReturn("test-api-key-12345");
        when(properties.getAnthropicVersion()).thenReturn("2023-06-01");
    }

    @Test
    @DisplayName("应该成功处理有效的非流式请求")
    void shouldHandleValidNonStreamingRequest() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();
        AnthropicChatResponse mockResponse = TestDataFactory.createValidResponse("Hello! How can I help you?");

        when(kiroService.createCompletion(any(AnthropicChatRequest.class)))
                .thenReturn(Mono.just(mockResponse));

        // When & Then
        Mono<org.springframework.http.ResponseEntity<AnthropicChatResponse>> result =
            controller.createMessage("test-api-key-12345", "2023-06-01", request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals("Hello! How can I help you?", response.getBody().getContent().get(0).getText());
                    assertEquals("2023-06-01", response.getHeaders().getFirst("anthropic-version"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("应该成功处理有效的流式请求")
    void shouldHandleValidStreamingRequest() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createStreamRequest();
        Flux<String> mockStream = Flux.just(
                "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"role\":\"assistant\"}}\n\n",
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n",
                "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
        );

        when(kiroService.streamCompletion(any(AnthropicChatRequest.class)))
                .thenReturn(mockStream);

        // When & Then
        Flux<String> result = controller.streamMessage("test-api-key-12345", "2023-06-01", request);

        StepVerifier.create(result)
                .expectNext("event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"role\":\"assistant\"}}\n\n")
                .expectNext("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\n")
                .expectNext("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
                .verifyComplete();
    }

    @Test
    @DisplayName("应该拒绝无效的API密钥")
    void shouldRejectInvalidApiKey() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            controller.createMessage("invalid-api-key", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝缺少API密钥的请求")
    void shouldRejectMissingApiKey() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            controller.createMessage(null, "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝缺少anthropic-version头的请求")
    void shouldRejectMissingAnthropicVersion() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", null, request);
        });
    }

    @Test
    @DisplayName("应该拒绝缺少model字段的请求")
    void shouldRejectMissingModel() {
        // Given
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setMaxTokens(100);
        request.setMessages(List.of(TestDataFactory.createUserMessage("Hello")));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝无效的max_tokens值")
    void shouldRejectInvalidMaxTokens() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();
        request.setMaxTokens(-1); // Invalid negative value

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝空消息列表")
    void shouldRejectEmptyMessages() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();
        request.setMessages(List.of()); // Empty messages

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝缺少role字段的消息")
    void shouldRejectMessageWithoutRole() {
        // Given
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);

        AnthropicMessage message = new AnthropicMessage();
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText("Hello");
        message.setContent(List.of(block));
        // Missing role

        request.setMessages(List.of(message));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝缺少content的消息")
    void shouldRejectMessageWithoutContent() {
        // Given
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);

        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        message.setContent(List.of()); // Empty content

        request.setMessages(List.of(message));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝流式请求中超过限制的max_tokens")
    void shouldRejectStreamingRequestWithExcessiveMaxTokens() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createStreamRequest();
        request.setMaxTokens(5000); // Exceeds streaming limit of 4096

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.streamMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该拒绝缺少type字段的tool_choice")
    void shouldRejectToolChoiceWithoutType() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();
        request.setToolChoice(Map.of("auto", true)); // Missing "type" field

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            controller.createMessage("test-api-key-12345", "2023-06-01", request);
        });
    }

    @Test
    @DisplayName("应该处理KiroService异常")
    void shouldHandleKiroServiceException() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();

        when(kiroService.createCompletion(any(AnthropicChatRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Kiro service unavailable")));

        // When & Then
        Mono<org.springframework.http.ResponseEntity<AnthropicChatResponse>> result =
            controller.createMessage("test-api-key-12345", "2023-06-01", request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().equals("Kiro service unavailable"))
                .verify();
    }

    @Test
    @DisplayName("应该处理流式请求中的KiroService异常")
    void shouldHandleKiroServiceExceptionInStream() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createStreamRequest();

        when(kiroService.streamCompletion(any(AnthropicChatRequest.class)))
                .thenReturn(Flux.error(new RuntimeException("Kiro service error")));

        // When & Then
        Flux<String> result = controller.streamMessage("test-api-key-12345", "2023-06-01", request);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                    throwable instanceof RuntimeException &&
                    throwable.getMessage().equals("Kiro service error"))
                .verify();
    }

    @Test
    @DisplayName("应该使用默认anthropic version当头部未提供时")
    void shouldUseDefaultAnthropicVersion() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createValidChatRequest();
        AnthropicChatResponse mockResponse = TestDataFactory.createValidResponse("Response");

        when(kiroService.createCompletion(any(AnthropicChatRequest.class)))
                .thenReturn(Mono.just(mockResponse));

        // When & Then
        Mono<org.springframework.http.ResponseEntity<AnthropicChatResponse>> result =
            controller.createMessage("test-api-key-12345", "2023-06-01", request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals("2023-06-01", response.getHeaders().getFirst("anthropic-version"));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("应该处理包含工具调用的请求")
    void shouldHandleToolCallRequest() {
        // Given
        AnthropicChatRequest request = TestDataFactory.createToolCallRequest();
        AnthropicChatResponse mockResponse = TestDataFactory.createToolCallResponse(
                List.of(TestDataFactory.createToolCall("get_weather", "{\"location\":\"New York\"}"))
        );

        when(kiroService.createCompletion(any(AnthropicChatRequest.class)))
                .thenReturn(Mono.just(mockResponse));

        // When & Then
        Mono<org.springframework.http.ResponseEntity<AnthropicChatResponse>> result =
            controller.createMessage("test-api-key-12345", "2023-06-01", request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertNotNull(response.getBody());
                    assertEquals("tool_use", response.getBody().getContent().get(0).getType());
                    assertEquals("get_weather", response.getBody().getContent().get(0).getName());
                })
                .verifyComplete();
    }
}