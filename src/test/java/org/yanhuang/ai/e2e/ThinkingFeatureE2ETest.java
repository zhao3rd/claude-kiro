package org.yanhuang.ai.e2e;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test to verify whether Kiro Gateway supports extended thinking mode.
 *
 * This test sends requests with the "thinking" parameter to observe:
 * 1. Whether Kiro Gateway accepts the parameter without errors
 * 2. Whether the response contains thinking-related content blocks
 * 3. The actual response structure from Kiro Gateway
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ThinkingFeatureE2ETest {

    private static final Logger log = LoggerFactory.getLogger(ThinkingFeatureE2ETest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Test
    public void testThinkingParameterAcceptance() {
        log.info("=== Testing Kiro Gateway thinking parameter acceptance ===");

        WebClient client = webClientBuilder.build();

        // Build request with thinking parameter
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(1000);

        // Add thinking configuration (Anthropic format)
        Map<String, Object> thinking = new HashMap<>();
        thinking.put("type", "enabled");
        thinking.put("budget_tokens", 5000);
        request.setThinking(thinking);

        // Simple message
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");

        AnthropicMessage.ContentBlock content = new AnthropicMessage.ContentBlock();
        content.setType("text");
        content.setText("What is 25 * 47? Please think step by step.");

        message.setContent(List.of(content));
        request.setMessages(List.of(message));

        try {
            log.info("Sending request with thinking parameter...");
            log.info("Thinking config: {}", thinking);

            AnthropicChatResponse response = client.post()
                .uri("http://localhost:" + port + "/v1/messages")
                .header("x-api-key", "sk-testing")
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AnthropicChatResponse.class)
                .block();

            log.info("=== Response received successfully ===");
            log.info("Response ID: {}", response.getId());
            log.info("Model: {}", response.getModel());
            log.info("Stop reason: {}", response.getStopReason());
            log.info("Content blocks count: {}", response.getContent() != null ? response.getContent().size() : 0);

            // Analyze content blocks
            if (response.getContent() != null && !response.getContent().isEmpty()) {
                log.info("=== Analyzing content blocks ===");
                for (int i = 0; i < response.getContent().size(); i++) {
                    AnthropicMessage.ContentBlock block = response.getContent().get(i);
                    log.info("Block {}: type={}", i, block.getType());

                    if ("thinking".equals(block.getType())) {
                        log.info("!!! FOUND THINKING BLOCK !!!");
                        log.info("Thinking content: {}", block.getText());
                    } else if ("text".equals(block.getType())) {
                        log.info("Text content: {}", block.getText());

                        // Verify warning message is present
                        String text = block.getText();
                        if (text.contains("Extended thinking mode is not supported by Kiro Gateway")) {
                            log.info("✅ Warning message correctly added to response");
                        } else {
                            log.warn("⚠️ Warning message NOT found in response text");
                        }
                    }
                }
            }

            // Verify basic response structure
            assertThat(response).isNotNull();
            assertThat(response.getId()).isNotNull();
            assertThat(response.getContent()).isNotEmpty();

            // Verify warning message is present in text response
            assertThat(response.getContent().get(0).getText())
                .contains("Extended thinking mode is not supported by Kiro Gateway")
                .contains("Response generated in standard mode");

        } catch (WebClientResponseException ex) {
            log.error("=== Request failed with HTTP error ===");
            log.error("Status code: {}", ex.getStatusCode());
            log.error("Response body: {}", ex.getResponseBodyAsString());

            // If Kiro Gateway rejects the thinking parameter, we expect 400 Bad Request
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.warn("Kiro Gateway rejected the thinking parameter (400 Bad Request)");
                log.warn("This indicates Kiro Gateway does NOT support extended thinking mode");
            } else {
                log.error("Unexpected error: {}", ex.getMessage());
            }

            throw ex;
        } catch (Exception ex) {
            log.error("=== Unexpected exception ===");
            log.error("Error: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @Test
    public void testThinkingWithComplexQuery() {
        log.info("=== Testing thinking parameter with complex reasoning task ===");

        WebClient client = webClientBuilder.build();

        // Build request with thinking parameter
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(2000);

        // Add thinking configuration with higher budget
        Map<String, Object> thinking = new HashMap<>();
        thinking.put("type", "enabled");
        thinking.put("budget_tokens", 10000);
        request.setThinking(thinking);

        // Complex reasoning task
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");

        AnthropicMessage.ContentBlock content = new AnthropicMessage.ContentBlock();
        content.setType("text");
        content.setText("Design a simple REST API for a todo list application. " +
                       "Consider authentication, CRUD operations, and data validation. " +
                       "Think through the architecture before providing the design.");

        message.setContent(List.of(content));
        request.setMessages(List.of(message));

        try {
            log.info("Sending complex reasoning request with thinking...");

            AnthropicChatResponse response = client.post()
                .uri("http://localhost:" + port + "/v1/messages")
                .header("x-api-key", "sk-testing")
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AnthropicChatResponse.class)
                .block();

            log.info("=== Response structure analysis ===");
            if (response.getContent() != null) {
                long thinkingBlocks = response.getContent().stream()
                    .filter(b -> "thinking".equals(b.getType()))
                    .count();
                long textBlocks = response.getContent().stream()
                    .filter(b -> "text".equals(b.getType()))
                    .count();

                log.info("Thinking blocks: {}", thinkingBlocks);
                log.info("Text blocks: {}", textBlocks);

                if (thinkingBlocks > 0) {
                    log.info("!!! SUCCESS: Kiro Gateway supports extended thinking !!!");
                } else {
                    log.warn("No thinking blocks found in response");
                    log.warn("Kiro Gateway likely does NOT support extended thinking mode");
                }

                // Verify warning message is present
                String responseText = response.getContent().get(0).getText();
                if (responseText.contains("Extended thinking mode is not supported by Kiro Gateway")) {
                    log.info("✅ Warning message correctly added to complex query response");
                } else {
                    log.warn("⚠️ Warning message NOT found in complex query response");
                }
            }

            assertThat(response).isNotNull();

            // Verify warning message is present in text response
            assertThat(response.getContent().get(0).getText())
                .contains("Extended thinking mode is not supported by Kiro Gateway")
                .contains("Response generated in standard mode");

        } catch (WebClientResponseException ex) {
            log.error("Request failed: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            // Don't fail the test - we're just exploring
        } catch (Exception ex) {
            log.error("Error: {}", ex.getMessage(), ex);
        }
    }
}
