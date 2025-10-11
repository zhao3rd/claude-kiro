package org.yanhuang.ai.integration.kiro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.yanhuang.ai.service.TokenManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test to determine the maximum payload size accepted by Kiro Gateway
 * Uses binary search to find the exact boundary between accepted and rejected payloads
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.kiro.disable-tools=false",
    "app.kiro.disable-history=false",
    "app.kiro.max-history-messages=10",
    "app.kiro.max-history-size=131072"
})
class KiroGatewayPayloadLimitTest {

    private static final Logger log = LoggerFactory.getLogger(KiroGatewayPayloadLimitTest.class);

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
     * Test payload limits with binary search
     * Expected to fail at some point between 10KB and 200KB
     */
    @Test
    void testPayloadSizeLimit_BinarySearch() {
        log.info("=== Starting Binary Search for Kiro Gateway Payload Limit ===");

        int minSize = 1024;        // 1KB - definitely works
        int maxSize = 200 * 1024;  // 200KB - likely too large
        int targetSize = findMaxPayloadSize(minSize, maxSize);

        log.info("=== Final Result ===");
        log.info("Maximum payload size: ~{} bytes (~{} KB)", targetSize, targetSize / 1024);
    }

    /**
     * Test specific payload sizes to understand rejection patterns
     */
    @Test
    void testSpecificPayloadSizes() {
        log.info("=== Testing Specific Payload Sizes ===");

        int[] testSizes = {
            2 * 1024,      // 2KB
            10 * 1024,     // 10KB
            50 * 1024,     // 50KB
            80 * 1024,     // 80KB
            90 * 1024,     // 90KB
            100 * 1024,    // 100KB
            110 * 1024,    // 110KB
            120 * 1024,    // 120KB
            150 * 1024     // 150KB
        };

        List<TestResult> results = new ArrayList<>();

        for (int size : testSizes) {
            TestResult result = testPayloadSize(size);
            results.add(result);

            log.info("Size: {} KB, Status: {}, Message: {}",
                size / 1024,
                result.success ? "SUCCESS" : "FAILED",
                result.errorMessage);

            // Add delay to avoid rate limiting
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("=== Test Results Summary ===");
        results.forEach(r ->
            log.info("{} KB: {}", r.size / 1024, r.success ? "✓ SUCCESS" : "✗ FAILED - " + r.errorMessage)
        );
    }

    /**
     * Test impact of different payload components
     */
    @Test
    void testPayloadComponentImpact() {
        log.info("=== Testing Individual Component Impact ===");

        // Test 1: Base payload only
        TestResult baseResult = testPayload(createMinimalPayload());
        log.info("Base payload: {} bytes - {}",
            baseResult.size, baseResult.success ? "SUCCESS" : "FAILED");

        // Test 2: With large system prompt
        TestResult systemPromptResult = testPayload(createPayloadWithLargeSystemPrompt(60 * 1024));
        log.info("With 60KB system prompt: {} bytes - {}",
            systemPromptResult.size, systemPromptResult.success ? "SUCCESS" : "FAILED");

        // Test 3: With large history
        TestResult historyResult = testPayload(createPayloadWithLargeHistory(50 * 1024));
        log.info("With 50KB history: {} bytes - {}",
            historyResult.size, historyResult.success ? "SUCCESS" : "FAILED");

        // Test 4: With tools
        TestResult toolsResult = testPayload(createPayloadWithTools(20));
        log.info("With 20 tools: {} bytes - {}",
            toolsResult.size, toolsResult.success ? "SUCCESS" : "FAILED");

        // Test 5: Combined large payload
        TestResult combinedResult = testPayload(createPayloadWithAll(40 * 1024, 30 * 1024, 10));
        log.info("Combined (40KB system + 30KB history + 10 tools): {} bytes - {}",
            combinedResult.size, combinedResult.success ? "SUCCESS" : "FAILED");
    }

    /**
     * Binary search to find maximum accepted payload size
     */
    private int findMaxPayloadSize(int minSize, int maxSize) {
        int left = minSize;
        int right = maxSize;
        int lastSuccessful = minSize;

        log.info("Starting binary search between {} KB and {} KB", left / 1024, right / 1024);

        while (left <= right) {
            int mid = left + (right - left) / 2;
            log.info("Testing payload size: {} KB", mid / 1024);

            TestResult result = testPayloadSize(mid);

            if (result.success) {
                lastSuccessful = mid;
                left = mid + 1024; // Try larger (increment by 1KB)
                log.info("✓ Success at {} KB, trying larger", mid / 1024);
            } else {
                right = mid - 1024; // Try smaller (decrement by 1KB)
                log.info("✗ Failed at {} KB: {}, trying smaller", mid / 1024, result.errorMessage);
            }

            // Add delay to avoid rate limiting
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return lastSuccessful;
    }

    /**
     * Test a payload of specific size
     */
    private TestResult testPayloadSize(int targetSize) {
        ObjectNode payload = createPayloadOfSize(targetSize);
        return testPayload(payload);
    }

    /**
     * Send payload to Kiro API and check response
     */
    private TestResult testPayload(ObjectNode payload) {
        String payloadStr = payload.toString();
        int size = payloadStr.length();

        try {
            // Refresh token first to ensure it's valid
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
                    // If 403, try refreshing token and retry once
                    if (error instanceof WebClientResponseException) {
                        WebClientResponseException webEx = (WebClientResponseException) error;
                        if (webEx.getStatusCode().value() == 403) {
                            log.warn("Got 403, refreshing token and retrying...");
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
            return new TestResult(size, false, errorMsg);

        } catch (Exception ex) {
            return new TestResult(size, false, ex.getMessage());
        }
    }

    /**
     * Create minimal payload
     */
    private ObjectNode createMinimalPayload() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();
        userInput.put("content", "[user] Hello");
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");

        currentMessage.set("userInputMessage", userInput);
        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", mapper.createArrayNode());

        payload.set("conversationState", conversationState);
        return payload;
    }

    /**
     * Create payload of approximately target size by padding content
     */
    private ObjectNode createPayloadOfSize(int targetSize) {
        ObjectNode payload = createMinimalPayload();

        // Calculate current size
        int currentSize = payload.toString().length();

        if (currentSize >= targetSize) {
            return payload;
        }

        // Add padding to reach target size
        int paddingNeeded = targetSize - currentSize - 100; // Leave some margin
        String padding = "X".repeat(Math.max(0, paddingNeeded));

        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        userInput.put("content", "[user] Test message with padding: " + padding);

        return payload;
    }

    /**
     * Create payload with large system prompt
     */
    private ObjectNode createPayloadWithLargeSystemPrompt(int systemPromptSize) {
        ObjectNode payload = createMinimalPayload();

        String systemPrompt = "[System] " + "A".repeat(Math.max(0, systemPromptSize - 10));

        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        userInput.put("content", systemPrompt + "\n[user] Hello");

        return payload;
    }

    /**
     * Create payload with large history
     */
    private ObjectNode createPayloadWithLargeHistory(int historySize) {
        ObjectNode payload = createMinimalPayload();

        ArrayNode history = mapper.createArrayNode();

        // Add messages until we reach target history size
        int currentSize = 0;
        int messageCount = 0;

        while (currentSize < historySize) {
            // User message
            ObjectNode userMsg = mapper.createObjectNode();
            ObjectNode userInput = mapper.createObjectNode();
            String content = "[user] Message " + messageCount + ": " + "X".repeat(500);
            userInput.put("content", content);
            userInput.put("modelId", "auto");
            userInput.put("origin", "AI_EDITOR");
            userMsg.set("userInputMessage", userInput);
            history.add(userMsg);

            // Assistant message
            ObjectNode assistantMsg = mapper.createObjectNode();
            ObjectNode assistantResponse = mapper.createObjectNode();
            assistantResponse.put("content", "Response to message " + messageCount);
            assistantMsg.set("assistantResponseMessage", assistantResponse);
            history.add(assistantMsg);

            currentSize = history.toString().length();
            messageCount++;
        }

        ObjectNode conversationState = (ObjectNode) payload.path("conversationState");
        conversationState.set("history", history);

        return payload;
    }

    /**
     * Create payload with tools
     */
    private ObjectNode createPayloadWithTools(int toolCount) {
        ObjectNode payload = createMinimalPayload();

        ArrayNode tools = mapper.createArrayNode();

        for (int i = 0; i < toolCount; i++) {
            ObjectNode tool = mapper.createObjectNode();
            ObjectNode toolSpec = mapper.createObjectNode();
            toolSpec.put("name", "test_tool_" + i);
            toolSpec.put("description", "Test tool number " + i + " for payload testing");

            ObjectNode inputSchema = mapper.createObjectNode();
            ObjectNode json = mapper.createObjectNode();
            json.put("type", "object");

            ObjectNode properties = mapper.createObjectNode();
            ObjectNode param = mapper.createObjectNode();
            param.put("type", "string");
            param.put("description", "Parameter for tool " + i);
            properties.set("param" + i, param);

            json.set("properties", properties);
            inputSchema.set("json", json);
            toolSpec.set("inputSchema", inputSchema);

            tool.set("toolSpecification", toolSpec);
            tools.add(tool);
        }

        ObjectNode context = mapper.createObjectNode();
        context.set("tools", tools);

        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        userInput.set("userInputMessageContext", context);

        return payload;
    }

    /**
     * Create payload with all components
     */
    private ObjectNode createPayloadWithAll(int systemPromptSize, int historySize, int toolCount) {
        ObjectNode payload = createPayloadWithLargeSystemPrompt(systemPromptSize);

        // Add history
        ArrayNode history = mapper.createArrayNode();
        int currentSize = 0;
        int messageCount = 0;

        while (currentSize < historySize) {
            ObjectNode userMsg = mapper.createObjectNode();
            ObjectNode userInput = mapper.createObjectNode();
            userInput.put("content", "[user] Message " + messageCount + ": " + "X".repeat(500));
            userInput.put("modelId", "auto");
            userInput.put("origin", "AI_EDITOR");
            userMsg.set("userInputMessage", userInput);
            history.add(userMsg);

            ObjectNode assistantMsg = mapper.createObjectNode();
            ObjectNode assistantResponse = mapper.createObjectNode();
            assistantResponse.put("content", "Response " + messageCount);
            assistantMsg.set("assistantResponseMessage", assistantResponse);
            history.add(assistantMsg);

            currentSize = history.toString().length();
            messageCount++;
        }

        ObjectNode conversationState = (ObjectNode) payload.path("conversationState");
        conversationState.set("history", history);

        // Add tools
        ArrayNode tools = mapper.createArrayNode();
        for (int i = 0; i < toolCount; i++) {
            ObjectNode tool = mapper.createObjectNode();
            ObjectNode toolSpec = mapper.createObjectNode();
            toolSpec.put("name", "test_tool_" + i);
            toolSpec.put("description", "Test tool " + i);

            ObjectNode inputSchema = mapper.createObjectNode();
            ObjectNode json = mapper.createObjectNode();
            json.put("type", "object");
            inputSchema.set("json", json);
            toolSpec.set("inputSchema", inputSchema);

            tool.set("toolSpecification", toolSpec);
            tools.add(tool);
        }

        ObjectNode context = mapper.createObjectNode();
        context.set("tools", tools);

        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        userInput.set("userInputMessageContext", context);

        return payload;
    }

    /**
     * Test result container
     */
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
