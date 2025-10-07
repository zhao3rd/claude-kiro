package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for MCP tool identification and streaming input_json_delta behavior (P2 task).
 * New code: comments and logs are in English.
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
@DisplayName("P2 MCP Tools E2E Tests")
public class McpToolE2ETest extends BaseE2ETest {

    private ObjectNode buildMcpToolRequest(String userMessage, String mcpToolFullName) {
        ObjectNode request = createBasicChatRequest(userMessage);

        var tools = objectMapper.createArrayNode();
        var tool = objectMapper.createObjectNode();
        tool.put("type", "function");

        var function = objectMapper.createObjectNode();
        function.put("name", mcpToolFullName); // e.g., mcp__sequential-thinking__sequentialthinking
        function.put("description", "MCP tool invocation");
        var parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        var properties = objectMapper.createObjectNode();
        var payload = objectMapper.createObjectNode();
        payload.put("type", "object");
        properties.set("payload", payload);
        parameters.set("properties", properties);
        function.set("parameters", parameters);
        tool.set("function", function);
        tools.add(tool);
        request.set("tools", tools);

        return request;
    }

    @Test
    @DisplayName("MCP tool should be recognized and produce tool_use")
    void testMcpToolRecognitionNonStreaming() {
        long startTime = System.currentTimeMillis();
        String testName = "mcp non-streaming";
        try {
            ObjectNode request = buildMcpToolRequest(
                    "请调用MCP工具 sequential-thinking 来分析一个三步推理任务",
                    "mcp__sequential-thinking__sequentialthinking");

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response);
            validateBasicResponse(response);

            boolean hasMcpToolUse = false;
            for (JsonNode block : response.get("content")) {
                if ("tool_use".equals(block.get("type").asText())) {
                    assertTrue(block.has("name"));
                    String toolName = block.get("name").asText();
                    assertTrue(toolName.startsWith("mcp__"));
                    hasMcpToolUse = true;
                    break;
                }
            }
            assertTrue(hasMcpToolUse, "Response should contain an MCP tool_use block");

            log.info("✅ MCP tool recognition in non-streaming verified");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("mcp non-streaming test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("MCP tool streaming should include input_json_delta for tool input")
    void testMcpToolStreamingInputJsonDelta() {
        long startTime = System.currentTimeMillis();
        String testName = "mcp streaming";
        try {
            ObjectNode request = buildMcpToolRequest(
                    "调用MCP工具并传入较大的JSON输入，验证增量传输",
                    "mcp__magic__21st_magic_component_builder");

            StepVerifier.create(apiClient.createChatCompletionStream(request))
                    .recordWith(java.util.ArrayList::new)
                    .thenConsumeWhile(event -> {
                        log.debug("SSE: {}", event);
                        return true;
                    })
                    .consumeRecordedWith(events -> {
                        assertFalse(events.isEmpty(), "Should receive streaming events");
                        boolean hasStart = events.stream().anyMatch(e ->
                                e.has("type") && "content_block_start".equals(e.get("type").asText()) &&
                                e.path("content_block").path("type").asText().equals("tool_use"));
                        assertTrue(hasStart, "Should have content_block_start for tool_use");

                        boolean hasInputDelta = events.stream().anyMatch(e ->
                                e.has("type") && "content_block_delta".equals(e.get("type").asText()) &&
                                e.path("delta").path("type").asText().equals("input_json_delta") &&
                                e.path("delta").has("partial_json"));
                        assertTrue(hasInputDelta, "Should have input_json_delta chunks");
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ MCP tool streaming input_json_delta verified");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("mcp streaming test failed: " + e.getMessage());
        }
    }
}


