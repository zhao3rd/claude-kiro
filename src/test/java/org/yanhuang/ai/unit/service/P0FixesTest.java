package org.yanhuang.ai.unit.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.yanhuang.ai.config.AppProperties;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.parser.BracketToolCallParser;
import org.yanhuang.ai.parser.CodeWhispererEventParser;
import org.yanhuang.ai.parser.ToolCallDeduplicator;
import org.yanhuang.ai.service.KiroService;
import org.yanhuang.ai.service.McpToolIdentifier;
import org.yanhuang.ai.service.TokenManager;

/**
 * Unit tests for P0 critical fixes
 * Validates:
 * 1. Tool call response format (toolu_ ID prefix)
 * 2. Streaming tool call events (input_json_delta)
 * 3. stop_reason mapping logic
 * 4. tool_result content block support
 */
@DisplayName("P0 Critical Fixes Tests")
class P0FixesTest {

    private ObjectMapper mapper;
    private KiroService kiroService;

    @BeforeEach
    void setup() {
        mapper = new ObjectMapper();

        // Create mock dependencies
        AppProperties properties = createMockProperties();
        TokenManager tokenManager = createMockTokenManager();
        CodeWhispererEventParser eventParser = new CodeWhispererEventParser(mapper);
        BracketToolCallParser bracketParser = new BracketToolCallParser();
        ToolCallDeduplicator deduplicator = new ToolCallDeduplicator();
        McpToolIdentifier mcpToolIdentifier = new McpToolIdentifier();

        kiroService = new KiroService(
            properties,
            tokenManager,
            eventParser,
            bracketParser,
            deduplicator,
            mcpToolIdentifier,
            WebClient.builder(),
            mapper
        );
    }

    @Test
    @DisplayName("P0-1: Tool ID should use toolu_ prefix")
    void testToolIdFormat() throws Exception {
        // Prepare request
        AnthropicChatRequest request = createBasicRequest();

        // Create mock Kiro events with tool call
        List<JsonNode> events = createToolCallEvents();

        // Invoke mapResponse via reflection
        AnthropicChatResponse response = invokeMapResponse(events, request);

        // Verify tool_use content block has toolu_ prefix
        assertThat(response.getContent()).isNotEmpty();
        AnthropicMessage.ContentBlock toolBlock = response.getContent().stream()
            .filter(b -> "tool_use".equals(b.getType()))
            .findFirst()
            .orElseThrow();

        assertThat(toolBlock.getId())
            .isNotNull()
            .startsWith("toolu_");

        assertThat(toolBlock.getName()).isEqualTo("get_weather");
        assertThat(toolBlock.getInput()).isNotNull();
    }

    @Test
    @DisplayName("P0-2: Streaming events should contain input_json_delta for tool_use")
    void testStreamingToolCallEvents() throws Exception {
        // Prepare response with tool call
        AnthropicChatResponse response = new AnthropicChatResponse();
        response.setId("msg_test123");
        response.setModel("claude-sonnet-4-5-20250929");
        response.setStopReason("tool_use");
        response.setCreatedAt(System.currentTimeMillis() / 1000);

        // Add tool_use content block
        AnthropicMessage.ContentBlock toolBlock = new AnthropicMessage.ContentBlock();
        toolBlock.setType("tool_use");
        toolBlock.setId("toolu_test123");
        toolBlock.setName("get_weather");
        toolBlock.setInput(Map.of("location", "San Francisco", "unit", "celsius"));
        response.addContentBlock(toolBlock);

        // Build stream events
        List<String> events = invokeBuildStreamEvents(response);

        // Verify event structure
        assertThat(events).hasSizeGreaterThan(4); // message_start, content_block_start, deltas, content_block_stop, message_delta, message_stop

        // Verify content_block_start does NOT contain input
        String blockStartEvent = events.stream()
            .filter(e -> e.contains("content_block_start"))
            .findFirst()
            .orElseThrow();
        assertThat(blockStartEvent).contains("\"type\":\"tool_use\"");
        assertThat(blockStartEvent).contains("\"id\":\"toolu_test123\"");
        assertThat(blockStartEvent).contains("\"name\":\"get_weather\"");
        assertThat(blockStartEvent).doesNotContain("\"input\""); // Input should NOT be in start event

        // Verify content_block_delta contains input_json_delta
        List<String> deltaEvents = events.stream()
            .filter(e -> e.contains("content_block_delta"))
            .toList();
        assertThat(deltaEvents).isNotEmpty();

        String firstDelta = deltaEvents.get(0);
        assertThat(firstDelta).contains("\"type\":\"input_json_delta\"");
        assertThat(firstDelta).contains("\"partial_json\"");
    }

    @Test
    @DisplayName("P0-3: stop_reason should be 'tool_use' when tool calls present")
    void testStopReasonToolUse() throws Exception {
        AnthropicChatRequest request = createBasicRequest();
        List<JsonNode> events = createToolCallEvents();

        AnthropicChatResponse response = invokeMapResponse(events, request);

        assertThat(response.getStopReason()).isEqualTo("tool_use");
    }

    @Test
    @DisplayName("P0-3: stop_reason should be 'end_turn' for normal completion")
    void testStopReasonEndTurn() throws Exception {
        AnthropicChatRequest request = createBasicRequest();
        List<JsonNode> events = createTextOnlyEvents();

        AnthropicChatResponse response = invokeMapResponse(events, request);

        assertThat(response.getStopReason()).isEqualTo("end_turn");
    }

    @Test
    @DisplayName("P0-3: stop_reason should be 'stop_sequence' when stop sequence found")
    void testStopReasonStopSequence() throws Exception {
        AnthropicChatRequest request = createBasicRequest();
        request.setStopSequences(List.of("STOP"));

        List<JsonNode> events = createTextEventsWithStopSequence();

        AnthropicChatResponse response = invokeMapResponse(events, request);

        assertThat(response.getStopReason()).isEqualTo("stop_sequence");
        assertThat(response.getStopSequence()).isEqualTo("STOP");
    }

    @Test
    @DisplayName("P0-3: stop_reason should be 'max_tokens' when estimated tokens reach limit")
    void testStopReasonMaxTokens() throws Exception {
        AnthropicChatRequest request = createBasicRequest();
        request.setMaxTokens(10); // Set very low limit

        // Create events with long text that would exceed token limit
        List<JsonNode> events = createLongTextEvents();

        AnthropicChatResponse response = invokeMapResponse(events, request);

        assertThat(response.getStopReason()).isEqualTo("max_tokens");
    }

    @Test
    @DisplayName("P0-4: tool_result content block should be processed in buildMessageContent")
    void testToolResultContentBlock() throws Exception {
        // Create message with tool_result
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");

        AnthropicMessage.ContentBlock toolResult = new AnthropicMessage.ContentBlock();
        toolResult.setType("tool_result");
        toolResult.setToolUseId("toolu_test123");
        toolResult.setContent("The weather in San Francisco is 70°F");

        message.setContent(List.of(toolResult));

        // Build message content
        String content = invokeBuildMessageContent(message);

        // Verify tool_result is included
        assertThat(content)
            .contains("[Tool toolu_test123 returned:")
            .contains("The weather in San Francisco is 70°F");
    }

    @Test
    @DisplayName("P0-4: tool_result with object content should be serialized to JSON")
    void testToolResultWithObjectContent() throws Exception {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");

        AnthropicMessage.ContentBlock toolResult = new AnthropicMessage.ContentBlock();
        toolResult.setType("tool_result");
        toolResult.setToolUseId("toolu_test456");
        toolResult.setContent(Map.of("temperature", 70, "unit", "F", "condition", "sunny"));

        message.setContent(List.of(toolResult));

        String content = invokeBuildMessageContent(message);

        assertThat(content)
            .contains("[Tool toolu_test456 returned:")
            .contains("temperature")
            .contains("70")
            .contains("sunny");
    }

    // Helper methods

    private AppProperties createMockProperties() {
        AppProperties props = new AppProperties();
        props.setApiKey("test-api-key");
        props.setAnthropicVersion("2023-06-01");

        // KiroProperties is final field, configure it directly
        props.getKiro().setBaseUrl("http://localhost");
        props.getKiro().setProfileArn("test-arn");
        props.getKiro().setAccessToken("test-token");
        props.getKiro().setRefreshToken("test-refresh");
        props.getKiro().setRefreshUrl("http://localhost/refresh");

        return props;
    }

    private TokenManager createMockTokenManager() {
        return new TokenManager(createMockProperties(), WebClient.builder()) {
            @Override
            public String currentToken() {
                return "test-token";
            }

            @Override
            public String ensureToken() {
                return "test-token";
            }
        };
    }

    private AnthropicChatRequest createBasicRequest() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);

        AnthropicMessage userMsg = new AnthropicMessage();
        userMsg.setRole("user");
        AnthropicMessage.ContentBlock textBlock = new AnthropicMessage.ContentBlock();
        textBlock.setType("text");
        textBlock.setText("Hello");
        userMsg.setContent(List.of(textBlock));

        request.setMessages(List.of(userMsg));
        return request;
    }

    private List<JsonNode> createToolCallEvents() throws Exception {
        List<JsonNode> events = new ArrayList<>();

        // Event 1: Tool call start with name and toolUseId
        String event1 = "{\"name\":\"get_weather\",\"toolUseId\":\"toolu_abc123\",\"input\":\"{\\\"location\\\":\\\"San Francisco\\\"}\",\"stop\":true}";
        events.add(mapper.readTree(event1));

        return events;
    }

    private List<JsonNode> createTextOnlyEvents() throws Exception {
        List<JsonNode> events = new ArrayList<>();
        String event = "{\"content\":\"Hello, how can I help you?\"}";
        events.add(mapper.readTree(event));
        return events;
    }

    private List<JsonNode> createTextEventsWithStopSequence() throws Exception {
        List<JsonNode> events = new ArrayList<>();
        String event = "{\"content\":\"This is a test message STOP\"}";
        events.add(mapper.readTree(event));
        return events;
    }

    private List<JsonNode> createLongTextEvents() throws Exception {
        List<JsonNode> events = new ArrayList<>();
        // Create text with ~50 tokens (200 characters)
        String longText = "This is a very long response that contains many words and will definitely exceed the token limit that was set to only ten tokens for this specific test case.";
        String event = "{\"content\":\"" + longText + "\"}";
        events.add(mapper.readTree(event));
        return events;
    }

    @SuppressWarnings("unchecked")
    private AnthropicChatResponse invokeMapResponse(List<JsonNode> events, AnthropicChatRequest request) throws Exception {
        var method = KiroService.class.getDeclaredMethod("mapResponse", List.class, AnthropicChatRequest.class);
        method.setAccessible(true);
        return (AnthropicChatResponse) method.invoke(kiroService, events, request);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeBuildStreamEvents(AnthropicChatResponse response) throws Exception {
        var method = KiroService.class.getDeclaredMethod("buildStreamEvents", AnthropicChatResponse.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(kiroService, response);
    }

    private String invokeBuildMessageContent(AnthropicMessage message) throws Exception {
        var method = KiroService.class.getDeclaredMethod("buildMessageContent", AnthropicMessage.class);
        method.setAccessible(true);
        return (String) method.invoke(kiroService, message);
    }
}
