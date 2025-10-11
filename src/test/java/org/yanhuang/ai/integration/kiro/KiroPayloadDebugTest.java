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
import java.util.UUID;

/**
 * Test to reproduce the actual 400 BAD_REQUEST error from logs
 * This test reconstructs the real payload that failed with "Improperly formed request"
 */
@SpringBootTest
@TestPropertySource(properties = {
    "app.kiro.disable-tools=false",
    "app.kiro.disable-history=false",
    "app.kiro.max-history-messages=10",
    "app.kiro.max-history-size=131072"
})
class KiroPayloadDebugTest {

    private static final Logger log = LoggerFactory.getLogger(KiroPayloadDebugTest.class);

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
     * Test 1: Reproduce the exact payload from logs that caused 400 error
     * Based on the conversation summary, the failing payload had:
     * - System prompt with Claude Code instructions (~60KB)
     * - Multiple system-reminder tags
     * - History with 4 messages (27KB)
     * - Full tool definitions for Claude Code
     */
    @Test
    void testReproduceOriginal400Error() {
        log.info("=== Reproducing Original 400 Error ===");

        // Reconstruct the payload similar to what Claude Code sends
        ObjectNode payload = buildClaudeCodeStylePayload();

        log.info("Testing payload size: {} bytes ({} KB)",
            payload.toString().length(),
            payload.toString().length() / 1024);

        TestResult result = testPayload(payload);

        log.info("Result: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);

        if (!result.success) {
            log.info("=== 400 Error Reproduced! ===");
            log.info("Now we can start isolating the problem...");
        }
    }

    /**
     * Test 2: Test minimal Claude Code payload
     */
    @Test
    void testMinimalClaudeCodePayload() {
        log.info("=== Testing Minimal Claude Code Payload ===");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        // Minimal Claude Code style content
        String content = "[System] You are Claude Code, Anthropic's official CLI for Claude.\n" +
                        "[user] Hello, can you help me?";

        userInput.put("content", content);
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");

        currentMessage.set("userInputMessage", userInput);
        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", mapper.createArrayNode());

        payload.set("conversationState", conversationState);

        TestResult result = testPayload(payload);
        log.info("Minimal payload result: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    /**
     * Test 3: Test with system-reminder tags
     */
    @Test
    void testWithSystemReminderTags() {
        log.info("=== Testing With System-Reminder Tags ===");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        // Content with system-reminder tags (like Claude Code sends)
        String content = "[System] You are Claude Code, Anthropic's official CLI for Claude.\n" +
                        "[user] <system-reminder>\n" +
                        "Called the Read tool with the following input: {\"file_path\":\"/path/to/file.txt\"}\n" +
                        "</system-reminder>\n" +
                        "[user] <system-reminder>\n" +
                        "Result of calling the Read tool: \"File contents here\"\n" +
                        "</system-reminder>\n" +
                        "[user] Please analyze this file.";

        userInput.put("content", content);
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");

        currentMessage.set("userInputMessage", userInput);
        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", mapper.createArrayNode());

        payload.set("conversationState", conversationState);

        TestResult result = testPayload(payload);
        log.info("With system-reminder tags result: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    /**
     * Test 4: Test with full Claude Code tools context
     */
    @Test
    void testWithClaudeCodeTools() {
        log.info("=== Testing With Claude Code Tools ===");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        userInput.put("content", "[System] You are Claude Code\n[user] Use the Read tool to read a file");
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");

        // Add typical Claude Code tools
        ObjectNode context = mapper.createObjectNode();
        ArrayNode tools = mapper.createArrayNode();

        // Add Read tool
        tools.add(createToolSpec("Read", "Reads a file from the local filesystem",
            createInputSchema("file_path", "string", "The absolute path to the file to read")));

        // Add Write tool
        tools.add(createToolSpec("Write", "Writes a file to the local filesystem",
            createInputSchema("file_path", "string", "The absolute path to the file to write")));

        // Add Bash tool
        tools.add(createToolSpec("Bash", "Executes a bash command",
            createInputSchema("command", "string", "The command to execute")));

        context.set("tools", tools);
        userInput.set("userInputMessageContext", context);

        currentMessage.set("userInputMessage", userInput);
        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", mapper.createArrayNode());

        payload.set("conversationState", conversationState);

        log.info("Payload with tools size: {} bytes ({} KB)",
            payload.toString().length(),
            payload.toString().length() / 1024);

        TestResult result = testPayload(payload);
        log.info("With Claude Code tools result: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    /**
     * Test 5: Test with history containing system prompts
     */
    @Test
    void testWithDuplicateSystemPromptsInHistory() {
        log.info("=== Testing With Duplicate System Prompts In History ===");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        // Current message
        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();
        userInput.put("content", "[System] You are Claude Code (60KB system prompt)\n[user] What is the weather?");
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");
        currentMessage.set("userInputMessage", userInput);

        // History with duplicate system prompts (like our code was doing)
        ArrayNode history = mapper.createArrayNode();

        // Message 1: User message with full system prompt
        ObjectNode msg1 = mapper.createObjectNode();
        ObjectNode msg1Input = mapper.createObjectNode();
        msg1Input.put("content", "[System] You are Claude Code (60KB system prompt)\n[user] First question");
        msg1Input.put("modelId", "auto");
        msg1Input.put("origin", "AI_EDITOR");
        msg1.set("userInputMessage", msg1Input);
        history.add(msg1);

        // Message 2: Assistant response
        ObjectNode msg2 = mapper.createObjectNode();
        ObjectNode msg2Response = mapper.createObjectNode();
        msg2Response.put("content", "First answer");
        msg2.set("assistantResponseMessage", msg2Response);
        history.add(msg2);

        // Message 3: User message with full system prompt again
        ObjectNode msg3 = mapper.createObjectNode();
        ObjectNode msg3Input = mapper.createObjectNode();
        msg3Input.put("content", "[System] You are Claude Code (60KB system prompt)\n[user] Second question");
        msg3Input.put("modelId", "auto");
        msg3Input.put("origin", "AI_EDITOR");
        msg3.set("userInputMessage", msg3Input);
        history.add(msg3);

        // Message 4: Assistant response
        ObjectNode msg4 = mapper.createObjectNode();
        ObjectNode msg4Response = mapper.createObjectNode();
        msg4Response.put("content", "Second answer");
        msg4.set("assistantResponseMessage", msg4Response);
        history.add(msg4);

        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", history);
        payload.set("conversationState", conversationState);

        log.info("Payload with duplicate system prompts size: {} bytes ({} KB)",
            payload.toString().length(),
            payload.toString().length() / 1024);

        TestResult result = testPayload(payload);
        log.info("With duplicate system prompts result: {}", result.success ? "SUCCESS" : "FAILED - " + result.errorMessage);
    }

    /**
     * Build a payload that closely mimics what Claude Code sends
     */
    private ObjectNode buildClaudeCodeStylePayload() {
        // This will be a large payload similar to what caused the 400 error
        ObjectNode payload = mapper.createObjectNode();
        payload.put("profileArn", properties.getKiro().getProfileArn());

        ObjectNode conversationState = mapper.createObjectNode();
        conversationState.put("chatTriggerType", "MANUAL");
        conversationState.put("conversationId", UUID.randomUUID().toString());

        // Build current message with large system prompt
        ObjectNode currentMessage = mapper.createObjectNode();
        ObjectNode userInput = mapper.createObjectNode();

        // Simulate Claude Code's large system prompt (~60KB)
        String systemPrompt = generateLargeSystemPrompt(60000);
        String content = "[System] " + systemPrompt + "\n[user] Please help me with a coding task";

        userInput.put("content", content);
        userInput.put("modelId", "CLAUDE_SONNET_4_5_20250929_V1_0");
        userInput.put("origin", "AI_EDITOR");

        // Add tools context
        ObjectNode context = mapper.createObjectNode();
        ArrayNode tools = createClaudeCodeTools();
        context.set("tools", tools);
        userInput.set("userInputMessageContext", context);

        currentMessage.set("userInputMessage", userInput);

        // Build history with multiple messages
        ArrayNode history = buildLargeHistory(4, systemPrompt);

        conversationState.set("currentMessage", currentMessage);
        conversationState.set("history", history);
        payload.set("conversationState", conversationState);

        return payload;
    }

    private String generateLargeSystemPrompt(int targetSize) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Claude Code, Anthropic's official CLI for Claude.\n");
        sb.append("You are an interactive CLI tool that helps users with software engineering tasks.\n");

        // Pad to reach target size
        while (sb.length() < targetSize) {
            sb.append("IMPORTANT: Always use the TodoWrite tool to plan and track tasks. ");
            sb.append("Use specialized tools for file operations. ");
            sb.append("Maintain professional objectivity. ");
        }

        return sb.substring(0, targetSize);
    }

    private ArrayNode buildLargeHistory(int messageCount, String systemPrompt) {
        ArrayNode history = mapper.createArrayNode();

        for (int i = 0; i < messageCount; i++) {
            // User message
            ObjectNode userMsg = mapper.createObjectNode();
            ObjectNode userInput = mapper.createObjectNode();
            userInput.put("content", "[System] " + systemPrompt + "\n[user] Question " + i);
            userInput.put("modelId", "auto");
            userInput.put("origin", "AI_EDITOR");
            userMsg.set("userInputMessage", userInput);
            history.add(userMsg);

            // Assistant message
            ObjectNode assistantMsg = mapper.createObjectNode();
            ObjectNode assistantResponse = mapper.createObjectNode();
            assistantResponse.put("content", "Answer to question " + i);
            assistantMsg.set("assistantResponseMessage", assistantResponse);
            history.add(assistantMsg);
        }

        return history;
    }

    private ArrayNode createClaudeCodeTools() {
        ArrayNode tools = mapper.createArrayNode();

        tools.add(createToolSpec("Task", "Launch a new agent to handle complex tasks",
            createInputSchema("prompt", "string", "The task for the agent to perform")));

        tools.add(createToolSpec("Bash", "Executes a bash command",
            createInputSchema("command", "string", "The command to execute")));

        tools.add(createToolSpec("Read", "Reads a file from the local filesystem",
            createInputSchema("file_path", "string", "The absolute path to the file to read")));

        tools.add(createToolSpec("Write", "Writes a file to the local filesystem",
            createInputSchema("file_path", "string", "The absolute path to write")));

        tools.add(createToolSpec("Edit", "Performs exact string replacements in files",
            createInputSchema("file_path", "string", "The absolute path to modify")));

        return tools;
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
            // Refresh token first
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
                log.error("=== 400 BAD REQUEST Details ===");
                log.error("Payload size: {} bytes ({} KB)", size, size / 1024);
                log.error("Response: {}", ex.getResponseBodyAsString());
                log.error("First 1000 chars of payload: {}", payloadStr.substring(0, Math.min(1000, payloadStr.length())));
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
