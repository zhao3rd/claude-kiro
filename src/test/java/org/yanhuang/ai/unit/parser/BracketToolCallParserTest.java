package org.yanhuang.ai.unit.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yanhuang.ai.parser.BracketToolCallParser;
import org.yanhuang.ai.model.ToolCall;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BracketToolCallParser 单元测试")
class BracketToolCallParserTest {

    private BracketToolCallParser parser;

    @BeforeEach
    void setUp() {
        parser = new BracketToolCallParser();
    }

    @Test
    @DisplayName("应该成功创建解析器实例")
    void shouldCreateParserSuccessfully() {
        // Then
        assertNotNull(parser);
    }

    @Test
    @DisplayName("应该处理null文本")
    void shouldHandleNullText() {
        // When
        List<ToolCall> result = parser.parse(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理空字符串")
    void shouldHandleEmptyString() {
        // When
        List<ToolCall> result = parser.parse("");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理空白字符串")
    void shouldHandleBlankString() {
        // When
        List<ToolCall> result = parser.parse("   \t\n   ");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该解析简单工具调用")
    void shouldParseSimpleToolCall() {
        // Given
        String text = "[Called get_weather with args: {\"location\":\"New York\"}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        ToolCall call = result.get(0);
        assertEquals("get_weather", call.getFunction().getName());
        assertEquals("{\"location\":\"New York\"}", call.getFunction().getArguments());
        assertEquals("function", call.getType());
        assertNotNull(call.getId());
    }

    @Test
    @DisplayName("应该解析复杂工具调用参数")
    void shouldParseComplexToolCallArguments() {
        // Given
        String text = "[Called search_api with args: {\"query\":\"test\",\"limit\":10,\"filters\":{\"type\":\"news\"}}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        ToolCall call = result.get(0);
        assertEquals("search_api", call.getFunction().getName());
        assertTrue(call.getFunction().getArguments().contains("\"query\":\"test\""));
        assertTrue(call.getFunction().getArguments().contains("\"limit\":10"));
    }

    @Test
    @DisplayName("应该解析多个工具调用")
    void shouldParseMultipleToolCalls() {
        // Given
        String text = "[Called get_weather with args: {\"location\":\"New York\"}] [Called get_time with args: {\"timezone\":\"EST\"}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(2, result.size());
        assertEquals("get_weather", result.get(0).getFunction().getName());
        assertEquals("get_time", result.get(1).getFunction().getName());
    }

    @Test
    @DisplayName("应该处理没有参数的工具调用")
    void shouldHandleToolCallWithoutArguments() {
        // Given
        String text = "[Called refresh_data]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        ToolCall call = result.get(0);
        assertEquals("refresh_data", call.getFunction().getName());
        assertEquals("{}", call.getFunction().getArguments());
    }

    @Test
    @DisplayName("应该处理包含多行参数的工具调用")
    void shouldHandleToolCallWithMultilineArguments() {
        // Given
        String text = "[Called complex_operation with args: {\"param1\":\"value1\",\"param2\":{\"nested\":true},\"param3\":[1,2,3]}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        ToolCall call = result.get(0);
        assertEquals("complex_operation", call.getFunction().getName());
        assertTrue(call.getFunction().getArguments().contains("\"param1\":\"value1\""));
    }

    @Test
    @DisplayName("应该忽略不匹配的文本")
    void shouldIgnoreNonMatchingText() {
        // Given
        String text = "Some random text without tool calls";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该忽略格式错误的工具调用")
    void shouldIgnoreMalformedToolCalls() {
        // Given
        String text = "[Called incomplete_tool_call with args: {\"incomplete\":\"json\"";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理混合文本中的工具调用")
    void shouldHandleToolCallsInMixedText() {
        // Given
        String text = "The assistant is thinking... [Called get_weather with args: {\"location\":\"NYC\"}] Now processing the response.";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        assertEquals("get_weather", result.get(0).getFunction().getName());
    }

    @Test
    @DisplayName("应该生成有效的工具调用ID")
    void shouldGenerateValidToolCallIds() {
        // Given
        String text = "[Called example_tool with args: {\"test\":true}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        String id = result.get(0).getId();
        assertNotNull(id);
        assertTrue(id.startsWith("call_"));
        assertEquals(13, id.length()); // call_ (5 chars) + 8 chars
    }

    @Test
    @DisplayName("应该为每个工具调用生成唯一ID")
    void shouldGenerateUniqueIdsForEachToolCall() {
        // Given
        String text = "[Called tool1] [Called tool2]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(2, result.size());
        assertNotEquals(result.get(0).getId(), result.get(1).getId());
    }

    @Test
    @DisplayName("应该处理转义字符的JSON")
    void shouldHandleJsonWithEscapedCharacters() {
        // Given
        String text = "[Called search with args: {\"query\":\"test \\\"quoted\\\" text\"}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        ToolCall call = result.get(0);
        assertEquals("search", call.getFunction().getName());
        assertTrue(call.getFunction().getArguments().contains("quoted"));
    }

    @Test
    @DisplayName("应该处理嵌套的工具调用")
    void shouldHandleNestedToolCalls() {
        // Given
        String text = "[Called outer_tool with args: {\"inner\":[Called inner_tool with args: {\"param\":\"value\"}]}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        // This should parse the outer tool call, and the inner text becomes part of the arguments
        assertEquals(1, result.size());
        assertEquals("outer_tool", result.get(0).getFunction().getName());
        assertTrue(result.get(0).getFunction().getArguments().contains("inner_tool"));
    }

    @Test
    @DisplayName("应该处理工具调用名称中的下划线和数字")
    void shouldHandleUnderscoresAndNumbersInToolNames() {
        // Given
        String text = "[Called get_user_data_123 with args: {\"id\":456}]";

        // When
        List<ToolCall> result = parser.parse(text);

        // Then
        assertEquals(1, result.size());
        assertEquals("get_user_data_123", result.get(0).getFunction().getName());
    }
}