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
 * E2E tests for tool_choice validation and behaviors (P1 tasks).
 * New code: comments and logs are in English.
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
@DisplayName("P1 Tool Choice Validation E2E Tests")
public class ToolChoiceValidationE2ETest extends BaseE2ETest {

    private ObjectNode buildTwoToolsRequest(String userMessage) {
        ObjectNode request = createBasicChatRequest(userMessage);

        var tools = objectMapper.createArrayNode();

        // Tool A: get_weather
        var toolA = objectMapper.createObjectNode();
        toolA.put("type", "function");
        toolA.put("name", "get_weather");
        var fnA = objectMapper.createObjectNode();
        fnA.put("name", "get_weather");
        fnA.put("description", "Get weather by city name");
        var paramsA = objectMapper.createObjectNode();
        paramsA.put("type", "object");
        var propsA = objectMapper.createObjectNode();
        var locationProp = objectMapper.createObjectNode();
        locationProp.put("type", "string");
        locationProp.put("description", "City name");
        propsA.set("location", locationProp);
        paramsA.set("properties", propsA);
        paramsA.putArray("required").add("location");
        fnA.set("parameters", paramsA);
        toolA.set("function", fnA);
        tools.add(toolA);

        // Tool B: web_search
        var toolB = objectMapper.createObjectNode();
        toolB.put("type", "function");
        toolB.put("name", "web_search");
        var fnB = objectMapper.createObjectNode();
        fnB.put("name", "web_search");
        fnB.put("description", "Search the web");
        var paramsB = objectMapper.createObjectNode();
        paramsB.put("type", "object");
        var propsB = objectMapper.createObjectNode();
        var queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Search query");
        propsB.set("query", queryProp);
        paramsB.set("properties", propsB);
        paramsB.putArray("required").add("query");
        fnB.set("parameters", paramsB);
        toolB.set("function", fnB);
        tools.add(toolB);

        request.set("tools", tools);
        return request;
    }

    @Test
    @DisplayName("tool_choice=auto should allow any tool selection")
    void testToolChoiceAuto() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_choice auto";
        try {
            ObjectNode request = buildTwoToolsRequest("查询北京天气，并简要说明气温");
            var toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "auto");
            request.set("tool_choice", toolChoice);

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "Response should not be null");
            validateBasicResponse(response);

            boolean hasToolUse = false;
            for (JsonNode block : response.get("content")) {
                if ("tool_use".equals(block.get("type").asText())) {
                    hasToolUse = true;
                    assertTrue(block.has("name"));
                    break;
                }
            }
            assertTrue(hasToolUse, "Response should contain a tool_use block under auto mode");

            log.info("✅ tool_choice auto validated");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_choice auto test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("tool_choice=tool with valid name should call that tool")
    void testToolChoiceToolValidName() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_choice tool valid";
        try {
            ObjectNode request = buildTwoToolsRequest("查询北京天气");
            var toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "tool");
            toolChoice.put("name", "get_weather");
            request.set("tool_choice", toolChoice);

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "Response should not be null");
            validateToolCallResponse(response, "get_weather");
            assertEquals("tool_use", response.get("stop_reason").asText());

            log.info("✅ tool_choice tool valid name verified");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_choice tool valid name test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("tool_choice=tool with unknown name should return 400")
    void testToolChoiceToolUnknownName() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_choice tool unknown";
        try {
            ObjectNode request = buildTwoToolsRequest("查询北京天气");
            var toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "tool");
            toolChoice.put("name", "non_exist_tool_xyz");
            request.set("tool_choice", toolChoice);

            StepVerifier.create(apiClient.createToolCall(request))
                    .expectErrorMatches(t -> {
                        log.info("Expected 400 for unknown tool name: {}", t.getMessage());
                        return t.getMessage() != null && t.getMessage().contains("400");
                    })
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ tool_choice tool unknown name rejected as expected");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_choice tool unknown name test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("tool_choice=none should not call tools")
    void testToolChoiceNone() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_choice none";
        try {
            ObjectNode request = buildTwoToolsRequest("请介绍北京的天气特点，尽量不调用工具");
            var toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "none");
            request.set("tool_choice", toolChoice);

            JsonNode response = apiClient.createChatCompletion(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response);
            validateBasicResponse(response);
            boolean hasToolUse = false;
            for (JsonNode block : response.get("content")) {
                if ("tool_use".equals(block.get("type").asText())) {
                    hasToolUse = true;
                    break;
                }
            }
            assertFalse(hasToolUse, "No tool_use block should be present when tool_choice is none");

            log.info("✅ tool_choice none returns text without tool_use");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_choice none test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("tool_choice invalid type should return 400")
    void testToolChoiceInvalidType() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_choice invalid";
        try {
            ObjectNode request = buildTwoToolsRequest("请随便介绍一座城市");
            var toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "invalidtype");
            request.set("tool_choice", toolChoice);

            StepVerifier.create(apiClient.createToolCall(request))
                    .expectErrorMatches(t -> {
                        log.info("Expected 400 for invalid tool_choice type: {}", t.getMessage());
                        return t.getMessage() != null && t.getMessage().contains("400");
                    })
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ invalid tool_choice type rejected as expected");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_choice invalid type test failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("tool_choice=required without tools should return 400")
    void testToolChoiceRequiredWithoutTools() {
        long startTime = System.currentTimeMillis();
        String testName = "tool_choice required without tools";
        try {
            ObjectNode request = createBasicChatRequest("请介绍一座城市");
            var toolChoice = objectMapper.createObjectNode();
            toolChoice.put("type", "required");
            request.set("tool_choice", toolChoice);

            StepVerifier.create(apiClient.createToolCall(request))
                    .expectErrorMatches(t -> {
                        log.info("Expected 400 for required without tools: {}", t.getMessage());
                        return t.getMessage() != null && t.getMessage().contains("400");
                    })
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ tool_choice required without tools rejected as expected");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);
        } catch (Exception e) {
            logTestError(testName, e);
            fail("tool_choice required without tools test failed: " + e.getMessage());
        }
    }
}


