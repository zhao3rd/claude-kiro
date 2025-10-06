package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0 Critical Fixes E2E Tests
 * Validates:
 * 1. Tool call ID format (toolu_ prefix)
 * 2. Streaming tool call events
 * 3. stop_reason correctness
 * 4. tool_result support
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
@DisplayName("P0 Critical Fixes E2E Tests")
public class P0FixesE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("P0-1: Tool call ID should use toolu_ prefix")
    void testToolCallIdFormat() {
        long startTime = System.currentTimeMillis();
        String testName = "Tool ID Format (toolu_)";

        try {
            log.info("🚀 Verifying tool call ID uses toolu_ prefix");

            // Create tool call request
            ObjectNode request = createToolCallRequest(
                "What's the weather in San Francisco?",
                "get_weather",
                "Get weather information for a location"
            );

            JsonNode response = apiClient.createToolCall(request)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "Response should not be null");

            // Verify stop_reason
            assertEquals("tool_use", response.get("stop_reason").asText(),
                "stop_reason should be 'tool_use'");

            // Find tool_use content block
            JsonNode content = response.get("content");
            JsonNode toolUseBlock = null;
            for (JsonNode block : content) {
                if ("tool_use".equals(block.get("type").asText())) {
                    toolUseBlock = block;
                    break;
                }
            }

            assertNotNull(toolUseBlock, "Should find tool_use content block");

            // Verify ID format
            String toolId = toolUseBlock.get("id").asText();
            assertTrue(toolId.startsWith("toolu_"),
                "Tool ID should start with 'toolu_', but got: " + toolId);

            log.info("✅ Tool ID format verified: {}", toolId);
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("Tool ID format test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("P0-2: Streaming should contain input_json_delta events")
    void testStreamingToolCallEvents() {
        long startTime = System.currentTimeMillis();
        String testName = "Streaming Tool Call Events";

        try {
            log.info("🚀 Verifying streaming tool call event structure");

            // Create streaming tool call request
            ObjectNode request = createToolCallRequest(
                "Search for the latest AI news",
                "web_search",
                "Search the web for information"
            );

            List<String> events = new ArrayList<>();
            apiClient.createChatCompletionStream(request)
                .doOnNext(event -> {
                    String eventStr = event.toString();
                    events.add(eventStr);
                    log.debug("Received SSE event: {}", eventStr);
                })
                .blockLast(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertFalse(events.isEmpty(), "Should receive streaming events");

            // Find content_block_start for tool_use
            String blockStartEvent = events.stream()
                .filter(e -> e.contains("content_block_start") && e.contains("tool_use"))
                .findFirst()
                .orElse(null);

            assertNotNull(blockStartEvent, "Should find content_block_start event for tool_use");

            // Verify it contains id and name but NOT complete input
            assertTrue(blockStartEvent.contains("\"type\":\"tool_use\""),
                "Block start should indicate tool_use type");
            assertTrue(blockStartEvent.contains("\"id\":\"toolu_"),
                "Block start should contain tool ID with toolu_ prefix");
            assertTrue(blockStartEvent.contains("\"name\":"),
                "Block start should contain tool name");

            // Find content_block_delta events
            List<String> deltaEvents = events.stream()
                .filter(e -> e.contains("content_block_delta"))
                .toList();

            assertFalse(deltaEvents.isEmpty(), "Should have delta events");

            // Verify at least one delta contains input_json_delta
            boolean hasInputJsonDelta = deltaEvents.stream()
                .anyMatch(e -> e.contains("\"type\":\"input_json_delta\"") &&
                             e.contains("\"partial_json\""));

            assertTrue(hasInputJsonDelta,
                "Should have input_json_delta events for tool input streaming");

            log.info("✅ Streaming events verified: {} total events, {} deltas",
                events.size(), deltaEvents.size());
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("Streaming tool call events test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("P0-3: stop_reason mapping should be accurate")
    void testStopReasonMapping() {
        long startTime = System.currentTimeMillis();
        String testName = "stop_reason Mapping";

        try {
            log.info("🚀 Verifying stop_reason mapping");

            // Test 1: tool_use stop_reason
            ObjectNode toolRequest = createToolCallRequest(
                "Get weather for Tokyo",
                "get_weather",
                "Get weather information"
            );

            JsonNode toolResponse = apiClient.createToolCall(toolRequest)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertEquals("tool_use", toolResponse.get("stop_reason").asText(),
                "Tool call should have stop_reason='tool_use'");

            // Test 2: end_turn stop_reason (simple text response)
            ObjectNode textRequest = createBasicChatRequest("Say hello");
            textRequest.put("max_tokens", 10);

            JsonNode textResponse = apiClient.createChatCompletion(textRequest)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            String stopReason = textResponse.get("stop_reason").asText();
            assertTrue(stopReason.equals("end_turn") || stopReason.equals("max_tokens"),
                "Simple text should have stop_reason='end_turn' or 'max_tokens', got: " + stopReason);

            log.info("✅ stop_reason mapping verified");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("stop_reason mapping test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("P0-4: tool_result content should be processed")
    void testToolResultSupport() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_result Support";

        try {
            log.info("🚀 Verifying tool_result content block support");

            // First request: Get tool call
            ObjectNode request1 = createToolCallRequest(
                "What's the temperature in London?",
                "get_weather",
                "Get weather information"
            );

            JsonNode response1 = apiClient.createToolCall(request1)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            // Extract tool call ID
            JsonNode toolUseBlock = null;
            for (JsonNode block : response1.get("content")) {
                if ("tool_use".equals(block.get("type").asText())) {
                    toolUseBlock = block;
                    break;
                }
            }
            assertNotNull(toolUseBlock, "Should have tool_use block");

            String toolUseId = toolUseBlock.get("id").asText();

            // Second request: Send tool result
            ObjectNode request2 = createBasicChatRequest("");

            var messages = objectMapper.createArrayNode();

            // User message
            var userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            var userContent = objectMapper.createArrayNode();
            var userText = objectMapper.createObjectNode();
            userText.put("type", "text");
            userText.put("text", "What's the temperature in London?");
            userContent.add(userText);
            userMsg.set("content", userContent);
            messages.add(userMsg);

            // Assistant message with tool_use
            var assistantMsg = objectMapper.createObjectNode();
            assistantMsg.put("role", "assistant");
            var assistantContent = objectMapper.createArrayNode();
            assistantContent.add(toolUseBlock);
            assistantMsg.set("content", assistantContent);
            messages.add(assistantMsg);

            // User message with tool_result
            var toolResultMsg = objectMapper.createObjectNode();
            toolResultMsg.put("role", "user");
            var toolResultContent = objectMapper.createArrayNode();
            var toolResult = objectMapper.createObjectNode();
            toolResult.put("type", "tool_result");
            toolResult.put("tool_use_id", toolUseId);
            toolResult.put("content", "Temperature: 15°C, Condition: Cloudy");
            toolResultContent.add(toolResult);
            toolResultMsg.set("content", toolResultContent);
            messages.add(toolResultMsg);

            request2.set("messages", messages);

            JsonNode response2 = apiClient.createChatCompletion(request2)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response2, "Response with tool_result should not be null");

            // Response should contain text (not another tool call)
            JsonNode content = response2.get("content");
            boolean hasTextBlock = false;
            for (JsonNode block : content) {
                if ("text".equals(block.get("type").asText())) {
                    hasTextBlock = true;
                    break;
                }
            }
            assertTrue(hasTextBlock, "Response should contain text block");

            log.info("✅ tool_result processing verified");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_result support test failed: " + e.getMessage());
        }
    }
}
