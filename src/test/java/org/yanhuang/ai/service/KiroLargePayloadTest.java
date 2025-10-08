package org.yanhuang.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.yanhuang.ai.config.AppProperties;

import java.time.Duration;
import java.util.UUID;

/**
 * Simple test to verify the exact payload size limits
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.kiro.disable-tools=false",
    "app.kiro.disable-history=false",
    "app.kiro.max-history-messages=10",
    "app.kiro.max-history-size=131072"
})
class KiroLargePayloadTest {

    private static final Logger log = LoggerFactory.getLogger(KiroLargePayloadTest.class);

    @Autowired
    private AppProperties properties;

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private ObjectMapper mapper;

    private WebClient webClient;

    @BeforeEach
    void setUp() {
        this.webClient = WebClient.builder()
            .baseUrl(properties.getKiro().getBaseUrl())
            .build();
    }

    @Test
    void testSpecificPayloadSizes() {
        log.info("=== Testing Specific Payload Sizes ===");

        int[] testSizes = {
            180 * 1024,  // 180KB
            190 * 1024,  // 190KB
            195 * 1024,  // 195KB
            200 * 1024,  // 200KB
            210 * 1024,  // 210KB
            220 * 1024,  // 220KB
        };

        for (int size : testSizes) {
            ObjectNode payload = createPayloadOfSize(size);
            TestResult result = testPayload(payload);

            log.info("Size: {} KB - {}",
                size / 1024,
                result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);

            // Add delay between tests
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ObjectNode createPayloadOfSize(int targetSize) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        // Calculate current base size
        int currentSize = payload.toString().length() + conversationState.toString().length() +
                         currentMessage.toString().length() + userInput.toString().length() + 100;

        if (currentSize >= targetSize) {
            userInput.put("content", "[user] Simple test message");
        } else {
            int paddingNeeded = targetSize - currentSize - 100;
            String padding = "X".repeat(Math.max(0, paddingNeeded));
            userInput.put("content", "[user] Test message with padding: " + padding);
        }

        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");
        currentMessage.set("userInputMessage", userInput);

        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", mapper.createArrayNode());
        payload.set("conversationState", conversationState);

        return payload;
    }

    private TestResult testPayload(ObjectNode payload) {
        String payloadStr = payload.toString();
        int size = payloadStr.length();

        try {
            tokenManager.refreshIfNeeded().block();
            String token = tokenManager.ensureToken();

            byte[] response = webClient.post()
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(error -> {
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webEx = (WebClientResponseException) error;
                        if (webEx.getStatusCode().value() == 403) {
                            return tokenManager.refreshIfNeeded()
                                .flatMap(refreshed -> {
                                    String newToken = tokenManager.ensureToken();
                                    return webClient.post()
                                        .header("Authorization", "Bearer " + newToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.TEXT_EVENT_STREAM)
                                        .bodyValue(payload)
                                        .retrieve()
                                        .bodyToMono(byte[].class)
                                        .timeout(Duration.ofSeconds(30));
                                });
                        }
                    }
                    return reactor.core.publisher.Mono.error(error);
                })
                .block();

            return new TestResult(size, true, null);

        } catch (WebClientResponseException ex) {
            String errorMsg = String.format("HTTP %s: %s",
                ex.getStatusCode(),
                ex.getResponseBodyAsString());

            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 500) {
                log.error("Payload size: {} bytes ({} KB) failed with: {}",
                    size, size / 1024, errorMsg);
            }

            return new TestResult(size, false, errorMsg);

        } catch (Exception ex) {
            return new TestResult(size, false, ex.getMessage());
        }
    }

    private static class TestResult {
        final int size;
        final boolean success;
        final String errorMessage;

        TestResult(int size, boolean success, String errorMessage) {
            this.size = size;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}
