package org.yanhuang.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.yanhuang.ai.config.AppProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Test to analyze the difference between payloads with and without history
 * Based on test-payload-large-to-kiro.json
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

    /**
     * Analyze the history structure in the real payload
     */
    @Test
    void analyzeHistoryStructure() throws IOException {
        log.info("=== Analyzing History Structure ===");

        ClassPathResource resource = new ClassPathResource("test-payload-large-to-kiro.json");
        JsonNode payload = mapper.readTree(resource.getInputStream());

        JsonNode history = payload.path("conversationState").path("history");
        log.info("Total history entries: {}", history.size());

        for (int i = 0; i < history.size(); i++) {
            JsonNode entry = history.get(i);
            log.info("\n--- History Entry {} ---", i);

            if (entry.has("userInputMessage")) {
                JsonNode uim = entry.get("userInputMessage");
                log.info("  userInputMessage:");
                log.info("    modelId: {}", uim.path("modelId").asText());
                log.info("    origin: {}", uim.path("origin").asText());
                log.info("    content length: {}", uim.path("content").asText().length());
                log.info("    content preview: {}",
                        uim.path("content").asText().substring(0, Math.min(100, uim.path("content").asText().length())));

                // Check for userInputMessageContext
                if (uim.has("userInputMessageContext")) {
                    JsonNode context = uim.get("userInputMessageContext");
                    log.info("    userInputMessageContext: {}", context);
                }
            }

            if (entry.has("assistantResponseMessage")) {
                JsonNode arm = entry.get("assistantResponseMessage");
                log.info("  assistantResponseMessage:");
                log.info("    content length: {}", arm.path("content").asText().length());
                log.info("    content preview: {}",
                        arm.path("content").asText().substring(0, Math.min(100, arm.path("content").asText().length())));
            }
        }

        // Check if history follows the pattern from ki2api/app.py
        log.info("\n=== Checking History Pattern ===");
        boolean followsPattern = true;
        for (int i = 0; i < history.size(); i++) {
            JsonNode entry = history.get(i);

            // According to ki2api, history should alternate: userInputMessage, assistantResponseMessage
            if (i % 2 == 0) {
                // Even index should have userInputMessage
                if (!entry.has("userInputMessage")) {
                    log.warn("Entry {} should have userInputMessage but doesn't", i);
                    followsPattern = false;
                }
            } else {
                // Odd index should have assistantResponseMessage
                if (!entry.has("assistantResponseMessage")) {
                    log.warn("Entry {} should have assistantResponseMessage but doesn't", i);
                    followsPattern = false;
                }
            }
        }

        log.info("History follows expected pattern: {}", followsPattern);
    }

    /**
     * Test the real payload from test-payload-large-to-kiro.json
     * Compare behavior with and without history
     */
    @Test
    void testRealPayloadWithAndWithoutHistory() throws IOException {
        log.info("=== Testing Real Payload With and Without History ===");

        // Load the real payload
        ClassPathResource resource = new ClassPathResource("test-payload-large-to-kiro.json");
        JsonNode originalPayload = mapper.readTree(resource.getInputStream());

        log.info("Original payload size: {} bytes ({} KB)",
                originalPayload.toString().length(),
                originalPayload.toString().length() / 1024);

        JsonNode history = originalPayload.path("conversationState").path("history");
        log.info("History entries: {}", history.size());

        // Test 1: With history (original payload)
        log.info("\n--- Test 1: WITH History ---");
        ObjectNode payloadWithHistory = (ObjectNode) mapper.readTree(originalPayload.toString());
        TestResult resultWithHistory = testPayload(payloadWithHistory);
        log.info("Result WITH history: {}",
                resultWithHistory.success ? "SUCCESS" : "FAILED - " + resultWithHistory.errorMessage);

        // Wait between tests
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Test 2: Without history (remove history array)
        log.info("\n--- Test 2: WITHOUT History ---");
        ObjectNode payloadWithoutHistory = (ObjectNode) mapper.readTree(originalPayload.toString());
        ObjectNode conversationState = (ObjectNode) payloadWithoutHistory.path("conversationState");
        conversationState.set("history", mapper.createArrayNode()); // Empty history

        log.info("Payload without history size: {} bytes ({} KB)",
                payloadWithoutHistory.toString().length(),
                payloadWithoutHistory.toString().length() / 1024);

        TestResult resultWithoutHistory = testPayload(payloadWithoutHistory);
        log.info("Result WITHOUT history: {}",
                resultWithoutHistory.success ? "SUCCESS" : "FAILED - " + resultWithoutHistory.errorMessage);

        // Summary
        log.info("\n=== SUMMARY ===");
        log.info("WITH history ({} KB): {}",
                resultWithHistory.size / 1024,
                resultWithHistory.success ? "SUCCESS" : "FAILED");
        log.info("WITHOUT history ({} KB): {}",
                resultWithoutHistory.size / 1024,
                resultWithoutHistory.success ? "SUCCESS" : "FAILED");

        if (!resultWithHistory.success && resultWithoutHistory.success) {
            log.error("\n!!! ISSUE CONFIRMED: History causes the request to fail !!!");
            log.error("Error with history: {}", resultWithHistory.errorMessage);
        } else if (resultWithHistory.success && !resultWithoutHistory.success) {
            log.warn("\n!!! UNEXPECTED: Request without history failed but with history succeeded !!!");
        } else if (!resultWithHistory.success && !resultWithoutHistory.success) {
            log.error("\n!!! Both requests failed - may be a different issue !!!");
        } else {
            log.info("\n!!! Both requests succeeded - history is not the issue !!!");
        }
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

            webClient.post()
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
