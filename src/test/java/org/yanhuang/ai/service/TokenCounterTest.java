package org.yanhuang.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenCounterTest {

    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
    }

    @Test
    void testEstimateSimpleTextMessage() {
        // Simple message: "Hello, world!" ≈ 14 chars / 4 = 3.5 tokens → ~4 tokens (after overhead)
        AnthropicChatRequest request = createSimpleRequest("Hello, world!");

        int estimatedTokens = tokenCounter.estimateRequestTokens(request);

        // Expected: text tokens + max_tokens + JSON overhead
        assertTrue(estimatedTokens > 0, "Token count should be positive");
        assertTrue(estimatedTokens < 1000, "Token count should be reasonable for simple message");
    }

    @Test
    void testEstimateLongConversation() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-3-5-sonnet-20241022");
        request.setMaxTokens(1000);

        // Create long conversation with system prompt and multiple messages
        request.setSystem(List.of(createTextContentBlock("You are a helpful assistant with deep expertise in software engineering.")));

        List<AnthropicMessage> messages = new ArrayList<>();
        messages.add(createUserMessage("What is the best way to implement authentication in a microservices architecture?"));
        messages.add(createAssistantMessage("Authentication in microservices typically uses JWT tokens with a centralized auth service..."));
        messages.add(createUserMessage("Can you provide a code example in Java Spring Boot?"));
        messages.add(createAssistantMessage("Here's a comprehensive example with Spring Security and JWT..."));

        request.setMessages(messages);

        int estimatedTokens = tokenCounter.estimateRequestTokens(request);

        // Should include all messages, system prompt, and max_tokens
        assertTrue(estimatedTokens > 1000, "Should account for conversation and output budget");
        assertTrue(estimatedTokens < 5000, "Should be reasonable for medium conversation");
    }

    @Test
    void testEstimateWithToolDefinitions() {
        AnthropicChatRequest request = createSimpleRequest("Use the weather tool");

        // Add tool definitions
        List<ToolDefinition> tools = new ArrayList<>();
        ToolDefinition weatherTool = new ToolDefinition();
        weatherTool.setName("get_weather");
        weatherTool.setDescription("Get current weather information for a given location");

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("location", Map.of("type", "string", "description", "City name"));
        properties.put("unit", Map.of("type", "string", "enum", List.of("celsius", "fahrenheit")));
        schema.put("properties", properties);
        schema.put("required", List.of("location"));

        weatherTool.setInputSchema(schema);
        tools.add(weatherTool);

        request.setTools(tools);

        int estimatedTokens = tokenCounter.estimateRequestTokens(request);

        // Should include tool definition overhead
        assertTrue(estimatedTokens > 100, "Should include tool definition tokens");
    }

    @Test
    void testValidateContextWindow_WithinLimit() {
        AnthropicChatRequest request = createSimpleRequest("Short message");

        // Should not throw exception
        assertDoesNotThrow(() ->
            tokenCounter.validateContextWindow(request, TokenCounter.MAX_CONTEXT_TOKENS_API_MODE)
        );
    }

    @Test
    void testValidateContextWindow_ExceedsLimit() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-3-5-sonnet-20241022");
        request.setMaxTokens(1_000_000);  // Set max_tokens to 1M

        // Create very long system prompt to push over limit
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longText.append("This is a very long text designed to exceed token limits. ");
        }

        request.setSystem(List.of(createTextContentBlock(longText.toString())));
        request.setMessages(List.of(createUserMessage("Hello")));

        // Should throw exception for exceeding limit
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            tokenCounter.validateContextWindow(request, TokenCounter.MAX_CONTEXT_TOKENS_API_MODE)
        );

        assertTrue(exception.getMessage().contains("exceeds maximum context window"));
        assertTrue(exception.getMessage().contains("estimated"));
        assertTrue(exception.getMessage().contains("limit"));
    }

    @Test
    void testEstimateWithToolUseContent() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-3-5-sonnet-20241022");
        request.setMaxTokens(500);

        // Create message with tool use content block
        AnthropicMessage.ContentBlock toolUse = new AnthropicMessage.ContentBlock();
        toolUse.setType("tool_use");
        toolUse.setId("toolu_123");
        toolUse.setName("get_weather");
        Map<String, Object> input = new HashMap<>();
        input.put("location", "San Francisco");
        input.put("unit", "celsius");
        toolUse.setInput(input);

        AnthropicMessage message = new AnthropicMessage();
        message.setRole("assistant");
        message.setContent(List.of(toolUse));

        request.setMessages(List.of(message));

        int estimatedTokens = tokenCounter.estimateRequestTokens(request);

        assertTrue(estimatedTokens > 0, "Should estimate tokens for tool use content");
    }

    @Test
    void testEstimateWithToolResultContent() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-3-5-sonnet-20241022");
        request.setMaxTokens(500);

        // Create message with tool result content block
        AnthropicMessage.ContentBlock toolResult = new AnthropicMessage.ContentBlock();
        toolResult.setType("tool_result");
        toolResult.setToolUseId("toolu_123");
        toolResult.setContent("The weather in San Francisco is 18°C and sunny.");

        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        message.setContent(List.of(toolResult));

        request.setMessages(List.of(message));

        int estimatedTokens = tokenCounter.estimateRequestTokens(request);

        assertTrue(estimatedTokens > 0, "Should estimate tokens for tool result content");
    }

    @Test
    void testEstimateEmptyRequest() {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-3-5-sonnet-20241022");
        request.setMaxTokens(100);
        request.setMessages(new ArrayList<>());

        int estimatedTokens = tokenCounter.estimateRequestTokens(request);

        // Should only count max_tokens
        assertEquals(100, estimatedTokens, "Empty request should only count max_tokens");
    }

    // Helper methods

    private AnthropicChatRequest createSimpleRequest(String message) {
        AnthropicChatRequest request = new AnthropicChatRequest();
        request.setModel("claude-3-5-sonnet-20241022");
        request.setMaxTokens(100);
        request.setMessages(List.of(createUserMessage(message)));
        return request;
    }

    private AnthropicMessage createUserMessage(String text) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("user");
        message.setContent(List.of(createTextContentBlock(text)));
        return message;
    }

    private AnthropicMessage createAssistantMessage(String text) {
        AnthropicMessage message = new AnthropicMessage();
        message.setRole("assistant");
        message.setContent(List.of(createTextContentBlock(text)));
        return message;
    }

    private AnthropicMessage.ContentBlock createTextContentBlock(String text) {
        AnthropicMessage.ContentBlock block = new AnthropicMessage.ContentBlock();
        block.setType("text");
        block.setText(text);
        return block;
    }
}
