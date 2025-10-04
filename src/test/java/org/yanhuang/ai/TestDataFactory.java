package org.yanhuang.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicChatResponse;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;
import org.yanhuang.ai.model.ToolCall;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory class for creating test data objects
 */
public class TestDataFactory {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Request creation methods
    public static AnthropicChatRequest createValidChatRequest() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);
        request.setTemperature(0.7);
        request.setStream(false);
        request.setMessages(List.of(createUserMessage("Hello, how are you?")));
        return request;
    }

    public static AnthropicChatRequest createStreamRequest() {
        AnthropicChatRequest request = createValidChatRequest();
        request.setStream(true);
        AnthropicMessage.ContentBlock block = request.getMessages().get(0).getContent().get(0);
        block.setText("Tell me a story");
        return request;
    }

    public static AnthropicChatRequest createToolCallRequest() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);
        request.setMessages(List.of(createUserMessage("What's the weather like?")));
        request.setTools(List.of(createWeatherTool()));
        request.setToolChoice(Map.of("type", "auto"));
        return request;
    }

    public static AnthropicChatRequest createChatRequestWithTools() {
        return createToolCallRequest();
    }

    public static AnthropicChatRequest createChatRequestWithEmptyMessages() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(100);
        request.setMessages(List.of());
        return request;
    }

    public static AnthropicChatRequest createChatRequestWithLargeContent() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-sonnet-4-5-20250929");
        request.setMaxTokens(1000);
        request.setMessages(List.of(createUserMessage(generateLargeContent(10000))));
        return request;
    }

    public static AnthropicChatRequest createInvalidRequest() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        // Missing required fields
        request.setMaxTokens(-1);
        request.setMessages(List.of());
        return request;
    }

    // Message creation methods
    public static AnthropicMessage createUserMessage(String content) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText(content);
        message.setContent(List.of(block));
        return message;
    }

    public static AnthropicMessage createAssistantMessage(String content) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("assistant");
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText(content);
        message.setContent(List.of(block));
        return message;
    }

    public static AnthropicMessage createSystemMessage(String content) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("system");
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText(content);
        message.setContent(List.of(block));
        return message;
    }

    public static AnthropicMessage createToolResultMessage(String toolCallId, String content) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("assistant"); // Anthropic messages are usually "assistant" or "user"
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("tool_result");
        block.setText(content);
        block.setId(toolCallId);
        message.setContent(List.of(block));
        return message;
    }

    // Tool creation methods
    public static ToolDefinition createWeatherTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("get_weather");
        tool.setDescription("Get current weather information for a location");

        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "object");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode locationProp = mapper.createObjectNode();
        locationProp.put("type", "string");
        locationProp.put("description", "The city and state, e.g. San Francisco, CA");
        properties.set("location", locationProp);

        ArrayNode enumArray = mapper.createArrayNode();
        enumArray.add("celsius");
        enumArray.add("fahrenheit");
        ObjectNode unitProp = mapper.createObjectNode();
        unitProp.put("type", "string");
        unitProp.set("enum", enumArray);
        properties.set("unit", unitProp);

        parameters.set("properties", properties);
        ArrayNode required = mapper.createArrayNode();
        required.add("location");
        parameters.set("required", required);

        tool.setInputSchema(mapper.convertValue(parameters, Map.class));
        return tool;
    }

    public static ToolCall createToolCall(String name, String arguments) {
        ToolCall toolCall = new ToolCall();
        toolCall.setId("call_" + UUID.randomUUID().toString().replace("-", ""));
        toolCall.setType("function");

        ToolCall.ToolFunction function = new ToolCall.ToolFunction();
        function.setName(name);
        function.setArguments(arguments);

        toolCall.setFunction(function);
        return toolCall;
    }

    // Response creation methods
    public static AnthropicChatResponse createValidResponse(String content) {
        AnthropicChatResponse response = new AnthropicChatResponse();
        response.setId("msg_" + UUID.randomUUID().toString().replace("-", ""));
        response.setType("message");
        response.setModel("claude-sonnet-4-5-20250929");
        response.setCreatedAt(Instant.now().getEpochSecond());
        response.setStopReason("end_turn");

        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText(content);

        response.setContent(List.of(block));

        AnthropicChatResponse.Usage usage = new AnthropicChatResponse.Usage();
        usage.setInputTokens(10);
        usage.setOutputTokens(content.length() / 4);
        response.setUsage(usage);

        return response;
    }

    public static AnthropicChatResponse createSimpleChatResponse(String content) {
        return createValidResponse(content);
    }

    public static AnthropicChatResponse createChatResponseWithToolCalls() {
        List<ToolCall> toolCalls = List.of(
            createToolCall("get_weather", "{\"location\":\"New York, NY\",\"unit\":\"fahrenheit\"}")
        );
        return createToolCallResponse(toolCalls);
    }

    public static AnthropicChatResponse createToolCallResponse(List<ToolCall> toolCalls) {
        AnthropicChatResponse response = new AnthropicChatResponse();
        response.setId("msg_" + UUID.randomUUID().toString().replace("-", ""));
        response.setType("message");
        response.setModel("claude-sonnet-4-5-20250929");
        response.setCreatedAt(Instant.now().getEpochSecond());
        response.setStopReason("tool_use");

        // Create content blocks for tool calls
        List<AnthropicMessage.ContentBlock> contentBlocks = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
            block.setType("tool_use");
            block.setId(toolCall.getId());
            block.setName(toolCall.getFunction().getName());
            try {
                // Parse JSON string to Map for the input field
                Map<String, Object> inputMap = mapper.readValue(toolCall.getFunction().getArguments(), Map.class);
                block.setInput(inputMap);
            } catch (Exception e) {
                // If parsing fails, set as string or empty map
                block.setInput(Map.of("arguments", toolCall.getFunction().getArguments()));
            }
            contentBlocks.add(block);
        }
        response.setContent(contentBlocks);

        AnthropicChatResponse.Usage usage = new AnthropicChatResponse.Usage();
        usage.setInputTokens(15);
        usage.setOutputTokens(25);
        response.setUsage(usage);

        return response;
    }

    // Kiro event data methods
    public static List<JsonNode> createMockKiroEvents() {
        ArrayNode events = mapper.createArrayNode();

        ObjectNode event1 = mapper.createObjectNode();
        event1.put("content", "Hello! I'm doing well, thank you for asking.");
        events.add(event1);

        ObjectNode event2 = mapper.createObjectNode();
        event2.put("content", " How can I assist you today?");
        events.add(event2);

        return List.of(events.get(0), events.get(1));
    }

    public static List<JsonNode> createMockToolCallEvents() {
        ArrayNode events = mapper.createArrayNode();

        ObjectNode event1 = mapper.createObjectNode();
        event1.put("content", "I'll check the weather for you.");
        events.add(event1);

        ObjectNode event2 = mapper.createObjectNode();
        event2.put("name", "get_weather");
        event2.put("toolUseId", "toolu_12345");
        event2.put("input", "{\"location\":\"New York, NY\"");
        events.add(event2);

        ObjectNode event3 = mapper.createObjectNode();
        event3.put("name", "get_weather");
        event3.put("toolUseId", "toolu_12345");
        event3.put("input", "\"unit\":\"fahrenheit\"}");
        event3.put("stop", true);
        events.add(event3);

        return List.of(events.get(0), events.get(1), events.get(2));
    }

    public static String createBracketToolCallText() {
        return "[Called get_weather with args: {\"location\":\"New York, NY\",\"unit\":\"fahrenheit\"}]";
    }

    public static String createMultipleToolCallsText() {
        return "[Called get_weather with args: {\"location\":\"New York\"}] [Called get_time with args: {\"timezone\":\"EST\"}]";
    }

    public static String createInvalidBracketToolCallText() {
        return "[Called invalid_tool with args: {invalid json}";
    }

    // Utility methods
    public static String generateLargeContent(int size) {
        return "A".repeat(size);
    }

    public static String generateSpecialCharacters() {
        return "!@#$%^&*()_+-=[]{}|;':\",./<>?\\";
    }

    public static byte[] createMockEventStream() {
        // Create a simple mock event stream for testing
        String eventData = "{\"content\":\"test message\"}";
        String streamData = "event: message\ndata: " + eventData + "\n\n";
        return streamData.getBytes();
    }
}