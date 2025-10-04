package org.yanhuang.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.service.KiroService;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = AnthropicController.class)
@EnableConfigurationProperties(AppProperties.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
    "app.api-key=test-key",
    "app.anthropic-version=2023-06-01",
    "app.kiro.base-url=http://localhost",
    "app.kiro.profile-arn=test",
    "app.kiro.access-token=test",
    "app.kiro.refresh-token=test",
    "app.kiro.refresh-url=http://localhost/refresh"
})
class AnthropicControllerTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private KiroService kiroService;

    @Test
    void createMessageReturnsResponse() {
        AnthropicChatResponse response = new AnthropicChatResponse();
        response.setId("msg_test");
        response.setModel("claude-sonnet-4-5-20250929");
        response.setType("message");
        response.setCreatedAt(123456789L);
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText("hi");
        response.addContentBlock(block);
        response.setStopReason("end_turn");

        given(kiroService.createCompletion(any())).willReturn(Mono.just(response));

        Map<String, Object> body = Map.of(
            "model", "claude-sonnet-4-5-20250929",
            "max_tokens", 128,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                    "type", "text",
                    "text", "Hello"))))
        );

        webClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-key")
            .header("anthropic-version", "2023-06-01")
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo("msg_test")
            .jsonPath("$.content[0].text").isEqualTo("hi");
    }

    @Test
    void missingVersionHeaderReturns400() {
        Map<String, Object> body = Map.of(
            "model", "claude-sonnet-4-5-20250929",
            "max_tokens", 128,
            "messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of(
                    "type", "text",
                    "text", "Hello"))))
        );

        webClient.post()
            .uri("/v1/messages")
            .header("x-api-key", "test-key")
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest();
    }
}
