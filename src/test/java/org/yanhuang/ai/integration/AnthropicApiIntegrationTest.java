package org.yanhuang.ai.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.controller.AnthropicController;
import org.yanhuang.ai.service.KiroService;
import org.yanhuang.ai.TestDataFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@WebFluxTest(controllers = AnthropicController.class)
@ActiveProfiles("test")
@DisplayName("Anthropic API 集成测试")
class AnthropicApiIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AppProperties properties;

    @MockBean
    private KiroService kiroService;

    @BeforeEach
    void setUp() {
        // Setup properties
        when(properties.getApiKey()).thenReturn("test-api-key-12345");
        when(properties.getAnthropicVersion()).thenReturn("2023-06-01");
    }

    @Test
    @DisplayName("应该成功处理有效的聊天请求")
    void shouldHandleValidChatRequestSuccessfully() {
        // Given
        var request = TestDataFactory.createValidChatRequest();
        var mockResponse = TestDataFactory.createSimpleChatResponse("Hello! How can I help you today?");

        when(kiroService.createCompletion(any())).thenReturn(Mono.just(mockResponse));

        // When & Then
        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "test-api-key-12345")
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectHeader().exists("anthropic-version")
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.type").isEqualTo("message")
                .jsonPath("$.content[0].text").isEqualTo("Hello! How can I help you today?");

        verify(kiroService, times(1)).createCompletion(any());
    }

    @Test
    @DisplayName("应该拒绝缺少API密钥的请求")
    void shouldRejectRequestWithoutApiKey() {
        // Given
        var request = TestDataFactory.createValidChatRequest();

        // When & Then
        webTestClient.post()
                .uri("/v1/messages")
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();

        verify(kiroService, never()).createCompletion(any());
            }

    @Test
    @DisplayName("应该拒绝缺少Anthropic版本头的请求")
    void shouldRejectRequestWithoutAnthropicVersion() {
        // Given
        var request = TestDataFactory.createValidChatRequest();

        // When & Then
        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "test-api-key-12345")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();

        verify(kiroService, never()).createCompletion(any());
            }

    @Test
    @DisplayName("应该处理无效的请求体")
    void shouldHandleInvalidRequestBody() {
        // Given
        String invalidRequest = "{ invalid json }";

        // When & Then
        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "test-api-key-12345")
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().is5xxServerError(); // JSON parsing errors result in 500, not 400
    }

    @Test
    @DisplayName("应该处理服务异常")
    void shouldHandleServiceException() {
        // Given
        var request = TestDataFactory.createValidChatRequest();

        when(kiroService.createCompletion(any()))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When & Then
        webTestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", "test-api-key-12345")
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().is5xxServerError();

        verify(kiroService, times(1)).createCompletion(any());
    }

    @Test
    @DisplayName("应该验证请求头大小写不敏感")
    void shouldValidateHeadersCaseInsensitive() {
        // Given
        var request = TestDataFactory.createValidChatRequest();
        var mockResponse = TestDataFactory.createSimpleChatResponse("Hello!");

        when(kiroService.createCompletion(any())).thenReturn(Mono.just(mockResponse));

        // When & Then - Test mixed case headers
        webTestClient.post()
                .uri("/v1/messages")
                .header("X-API-KEY", "test-api-key-12345")
                .header("ANTHROPIC-VERSION", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();

        verify(kiroService, times(1)).createCompletion(any());
    }
}