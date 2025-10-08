package org.yanhuang.ai.service;

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

import java.time.Duration;
import java.util.UUID;

/**
 * Investigation test to find the exact cause of 400 BAD_REQUEST errors
 * Based on the conversation summary, we know there was a 400 error at 89KB with "Improperly formed request"
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.kiro.disable-tools=false",
    "app.kiro.disable-history=false",
    "app.kiro.max-history-messages=10",
    "app.kiro.max-history-size=131072"
})
class Kiro400ErrorInvestigationTest {

    private static final Logger log = LoggerFactory.getLogger(Kiro400ErrorInvestigationTest.class);

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
     * Test various scenarios that might cause 400 errors
     */
    @Test
    void investigate400ErrorScenarios() {
        log.info("=== Investigating 400 Error Scenarios ===");

        // Test 1: Empty conversationId
        testEmptyConversationId();

        // Test 2: Invalid modelId
        testInvalidModelId();

        // Test 3: Malformed tools
        testMalformedTools();

        // Test 4: Very long content without proper formatting
        testVeryLongContent();

        // Test 5: Unicode and special characters
        testUnicodeAndSpecialCharacters();

        // Test 6: Nested structure too deep
        testDeeplyNestedStructure();

        // Test 7: Missing required fields
        testMissingRequiredFields();

        // Test 8: History with inconsistent structure
        testInconsistentHistoryStructure();
    }

    private void testEmptyConversationId() {
        log.info("--- Test 1: Empty conversationId ---");

        ObjectNode payload = createMinimalPayload();
        ObjectNode conversationState = (ObjectNode) payload.path("conversationState");
        conversationState.put("conversationId", ""); // Empty string

        TestResult result = testPayload(payload);
        log.info("Empty conversationId: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    private void testInvalidModelId() {
        log.info("--- Test 2: Invalid modelId ---");

        ObjectNode payload = createMinimalPayload();
        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");
        userInput.put("modelId", "INVALID_MODEL_ID");

        TestResult result = testPayload(payload);
        log.info("Invalid modelId: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    private void testMalformedTools() {
        log.info("--- Test 3: Malformed tools ---");

        ObjectNode payload = createMinimalPayload();
        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        ObjectNode context = mapper.createObjectNode();
        ArrayNode tools = mapper.createArrayNode();

        // Create malformed tool
        ObjectNode badTool = mapper.createObjectNode();
        ObjectNode toolSpec = mapper.createObjectNode();
        toolSpec.put("name", "bad_tool");
        // Missing required inputSchema
        badTool.set("toolSpecification", toolSpec);
        tools.add(badTool);

        context.set("tools", tools);
        userInput.set("userInputMessageContext", context);

        TestResult result = testPayload(payload);
        log.info("Malformed tools: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    private void testVeryLongContent() {
        log.info("--- Test 4: Very long content ---");

        ObjectNode payload = createMinimalPayload();
        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        // Create very long content without proper formatting
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longContent.append("This is a very long line that keeps getting longer and longer without proper formatting or structure, which might cause issues with the Kiro API. ");
        }

        userInput.put("content", longContent.toString());

        TestResult result = testPayload(payload);
        log.info("Very long content ({} KB): {}",
            longContent.length() / 1024,
            result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    private void testUnicodeAndSpecialCharacters() {
        log.info("--- Test 5: Unicode and special characters ---");

        ObjectNode payload = createMinimalPayload();
        ObjectNode userInput = (ObjectNode) payload
            .path("conversationState")
            .path("currentMessage")
            .path("userInputMessage");

        // Include various unicode characters and control characters
        String unicodeContent = "[System] Test with unicode: 汉字 🚀 📝 \n" +
                               "[user] Special characters: \u0000\u0001\u0002\u0003\n" +
                               "JSON control chars: \"\\/\b\f\n\r\t";

        userInput.put("content", unicodeContent);

        TestResult result = testPayload(payload);
        log.info("Unicode and special characters: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    private void testDeeplyNestedStructure() {
        log.info("--- Test 6: Deeply nested structure ---");

        ObjectNode payload = createMinimalPayload();

        // Create deeply nested structure
        ObjectNode nested = payload;
        for (int i = 0; i < 100; i++) {
            ObjectNode nextLevel = mapper.createObjectNode();
            nextLevel.set("level" + i, nested);
            nested = nextLevel;
        }

        TestResult result = testPayload(nested);
        log.info("Deeply nested structure: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    private void testMissingRequiredFields() {
        log.info("--- Test 7: Missing required fields ---");

        // Test missing profileArn
        ObjectNode payload1 = mapper.createObjectNode();
        ObjectNode conversationState1 = mapper.createObjectNode();
        conversationState1.put("chatTriggerType", "MANUAL");
        conversationState1.set("currentMessage", createCurrentMessage());
        conversationState1.set("history", mapper.createArrayNode());
        payload1.set("conversationState", conversationState1);

        TestResult result1 = testPayload(payload1);
        log.info("Missing profileArn: {}", result1.success ? "SUCCESS" : "FAILED - " + result1.errorMessage);

        // Test missing conversationState
        ObjectNode payload2 = mapper.createObjectNode();
        payload2.put("profileArn", properties.getKiro().getProfileArn());

        TestResult result2 = testPayload(payload2);
        log.info("Missing conversationState: {}", result2.success ? "SUCCESS" : "FAILED - " + result2.errorMessage);
    }

    private void testInconsistentHistoryStructure() {
        log.info("--- Test 8: Inconsistent history structure ---");

        ObjectNode payload = createMinimalPayload();
        ObjectNode conversationState = (ObjectNode) payload.path("conversationState");
        ArrayNode history = mapper.createArrayNode();

        // Add properly formatted message
        ObjectNode goodMsg = mapper.createObjectNode();
        ObjectNode goodInput = mapper.createObjectNode();
        goodInput.put("content", "Good message");
        goodInput.put("modelId", "auto");
        goodInput.put("origin", "AI_EDITOR");
        goodMsg.set("userInputMessage", goodInput);
        history.add(goodMsg);

        // Add malformed message
        ObjectNode badMsg = mapper.createObjectNode();
        badMsg.put("wrongField", "This should be userInputMessage");
        history.add(badMsg);

        // Add another malformed message
        ObjectNode anotherBad = mapper.createObjectNode();
        anotherBad.put("userInputMessage", "This should be an object, not a string");
        history.add(anotherBad);

        conversationState.set("history", history);

        TestResult result = testPayload(payload);
        log.info("Inconsistent history structure: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    /**
     * Test payload that closely mimics real Claude Code usage scenario
     */
    @Test
    void testRealWorldClaudeCodeScenario() {
        log.info("=== Testing Real World Claude Code Scenario ===");

        ObjectNode payload = createRealWorldPayload();

        log.info("Real world payload size: {} bytes ({} KB)",
            payload.toString().length(),
            payload.toString().length() / 1024);

        TestResult result = testPayload(payload);
        log.info("Real world scenario: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);

        // Log the payload for analysis
        if (!result.success) {
            log.error("Failed payload: {}", payload.toString());
        }
    }

    private ObjectNode createRealWorldPayload() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        // Real Claude Code style content with system-reminder
        String content = "[System] You are Claude Code, Anthropic's official CLI for Claude.\n\n" +
                        "## Code References\n\n" +
                        "When referencing specific functions or pieces of code include the pattern `file_path:line_number` to allow the user to easily navigate to the source code location.\n\n" +
                        "## Global Forcing Rules\n\n" +
                        "### 在与用户交互时始终使用中文响应**\n\n" +
                        "## Project Overview\n\n" +
                        "claude-kiro is a Spring Boot WebFlux application that provides an Anthropic Claude-compatible API backed by Kiro CodeWhisperer gateway.\n\n" +
                        "[user] <system-reminder>\n" +
                        "Called the Read tool with the following input: {\"file_path\":\"/data-raw-ssd/code-src/person/claude-kiro/CLAUDE.md\"}\n" +
                        "</system-reminder>\n" +
                        "[user] <system-reminder>\n" +
                        "Result of calling the Read tool: Contents of CLAUDE.md file...\n" +
                        "</system-reminder>\n" +
                        "[user] 请检查debug日志,当前已经报错了";

        userInput.put("content", content);
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");

        // Add comprehensive tools context
        ObjectNode context = mapper.createObjectNode();
        ArrayNode tools = createComprehensiveTools();
        context.set("tools", tools);
        userInput.set("userInputMessageContext", context);

        currentMessage.set("userInputMessage", userInput);

        // Add realistic history
        ArrayNode history = createRealisticHistory();
        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", history);
        payload.set("conversationState", conversationState);

        return payload;
    }

    private ArrayNode createComprehensiveTools() {
        ArrayNode tools = mapper.createArrayNode();

        // Read tool
        tools.add(createToolSpec("Read", "Reads a file from the local filesystem",
            createInputSchema("file_path", "string", "The absolute path to the file to read")));

        // Write tool
        tools.add(createToolSpec("Write", "Writes a file to the local filesystem",
            createInputSchema("file_path", "string", "The absolute path to write")));

        // Edit tool
        tools.add(createToolSpec("Edit", "Performs exact string replacements in files",
            createInputSchema("file_path", "string", "The absolute path to modify")));

        // Bash tool
        tools.add(createToolSpec("Bash", "Executes a bash command",
            createInputSchema("command", "string", "The command to execute")));

        // Task tool
        tools.add(createToolSpec("Task", "Launch a new agent to handle complex tasks",
            createInputSchema("prompt", "string", "The task for the agent to perform")));

        // TodoWrite tool
        tools.add(createToolSpec("TodoWrite", "Create and manage structured task list",
            createInputSchema("todos", "object", "The updated todo list")));

        // WebSearch tool
        tools.add(createToolSpec("WebSearch", "Search the web for information",
            createInputSchema("query", "string", "The search query")));

        return tools;
    }

    private ArrayNode createRealisticHistory() {
        ArrayNode history = mapper.createArrayNode();

        // Previous conversation with system-reminder
        ObjectNode msg1 = mapper.createObjectNode();
        ObjectNode msg1Input = mapper.createObjectNode();
        msg1Input.put("content", "[System] You are Claude Code...\n[user] 历史记录的限制默认值，可以调整为128kb，且走application.yml配置不要走properties。历史记录限制默认为false,工具限制默认值为false. 请你做修改后，只保证编译通过，应用程序启动我会手工执行。");
        msg1Input.put("modelId", "auto");
        msg1Input.put("origin", "AI_EDITOR");
        msg1.set("userInputMessage", msg1Input);
        history.add(msg1);

        ObjectNode msg2 = mapper.createObjectNode();
        ObjectNode msg2Response = mapper.createObjectNode();
        msg2Response.put("content", "我已经修改了 application.yml 配置文件，将历史记录限制设置为 128KB，并调整了默认值。所有相关的配置都已更新为使用 YAML 格式而非 properties。");
        msg2.set("assistantResponseMessage", msg2Response);
        history.add(msg2);

        return history;
    }

    private ObjectNode createMinimalPayload() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        conversationState.set("currentMessage", createCurrentMessage());
        conversationState.set("history", mapper.createArrayNode());

        payload.set("conversationState", conversationState);
        return payload;
    }

    private ObjectNode createCurrentMessage() {
        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();
        userInput.put("content", "[user] Test message");
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");
        currentMessage.set("userInputMessage", userInput);
        return currentMessage;
    }

    private ObjectNode createToolSpec(String name, String description, ObjectNode inputSchema) {
        ObjectNode spec = mapper.createObjectNode();
        ObjectNode toolSpec = mapper.createObjectNode();

        toolSpec.put("name", name);
        toolSpec.put("description", description);
        toolSpec.set("inputSchema", inputSchema);

        spec.set("toolSpecification", toolSpec);
        return spec;
    }

    private ObjectNode createInputSchema(String paramName, String paramType, String paramDesc) {
        ObjectNode schema = mapper.createObjectNode();
        ObjectNode json = mapper.createObjectNode();

        json.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode param = mapper.createObjectNode();
        param.put("type", paramType);
        param.put("description", paramDesc);
        properties.set(paramName, param);

        json.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add(paramName);
        json.set("required", required);

        schema.set("json", json);
        return schema;
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

            // Log detailed error for 400 errors
            if (ex.getStatusCode().value() == 400) {
                log.error("=== FOUND 400 BAD REQUEST ===");
                log.error("Payload size: {} bytes ({} KB)", size, size / 1024);
                log.error("Response: {}", ex.getResponseBodyAsString());
                log.error("First 2000 chars of payload: {}",
                    payloadStr.substring(0, Math.min(2000, payloadStr.length())));
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
