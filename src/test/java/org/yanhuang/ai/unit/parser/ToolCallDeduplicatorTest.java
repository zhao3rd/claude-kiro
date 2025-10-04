package org.yanhuang.ai.unit.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yanhuang.ai.parser.ToolCallDeduplicator;
import org.yanhuang.ai.model.ToolCall;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ToolCallDeduplicator 单元测试")
class ToolCallDeduplicatorTest {

    private ToolCallDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new ToolCallDeduplicator();
    }

    @Test
    @DisplayName("应该成功创建去重器实例")
    void shouldCreateDeduplicatorSuccessfully() {
        // Then
        assertNotNull(deduplicator);
    }

    @Test
    @DisplayName("应该处理null列表")
    void shouldHandleNullList() {
        // When
        List<ToolCall> result = deduplicator.deduplicate(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理空列表")
    void shouldHandleEmptyList() {
        // When
        List<ToolCall> result = deduplicator.deduplicate(List.of());

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该保留唯一的工具调用")
    void shouldKeepUniqueToolCalls() {
        // Given
        ToolCall call1 = createToolCall("tool1", "{\"param\":\"value1\"}");
        ToolCall call2 = createToolCall("tool2", "{\"param\":\"value2\"}");
        List<ToolCall> calls = List.of(call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(2, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
        assertEquals("tool2", result.get(1).getFunction().getName());
    }

    @Test
    @DisplayName("应该移除重复的工具调用")
    void shouldRemoveDuplicateToolCalls() {
        // Given
        ToolCall call1 = createToolCall("tool1", "{\"param\":\"value\"}", "call_1");
        ToolCall call2 = createToolCall("tool1", "{\"param\":\"value\"}", "call_2");
        ToolCall call3 = createToolCall("tool2", "{\"param\":\"other\"}", "call_3");
        List<ToolCall> calls = List.of(call1, call2, call3);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(2, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
        assertEquals("tool2", result.get(1).getFunction().getName());
        // Should keep the first occurrence
        assertEquals("call_1", result.get(0).getId());
        assertEquals("call_3", result.get(1).getId());
    }

    @Test
    @DisplayName("应该区分相同函数名但不同参数的工具调用")
    void shouldDistinguishSameFunctionWithDifferentArguments() {
        // Given
        ToolCall call1 = createToolCall("tool1", "{\"param\":\"value1\"}", "call_1");
        ToolCall call2 = createToolCall("tool1", "{\"param\":\"value2\"}", "call_2");
        List<ToolCall> calls = List.of(call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(2, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
        assertEquals("tool1", result.get(1).getFunction().getName());
        assertNotEquals(result.get(0).getId(), result.get(1).getId());
    }

    @Test
    @DisplayName("应该忽略null工具调用")
    void shouldIgnoreNullToolCalls() {
        // Given
        ToolCall call1 = createToolCall("tool1", "{\"param\":\"value\"}", "call_1");
        List<ToolCall> calls = Arrays.asList(call1, null, null);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
    }

    @Test
    @DisplayName("应该忽略function为null的工具调用")
    void shouldIgnoreToolCallsWithNullFunction() {
        // Given
        ToolCall call1 = createToolCall("tool1", "{\"param\":\"value\"}", "call_1");
        ToolCall call2 = new ToolCall();
        call2.setId("call_2");
        call2.setType("function");
        call2.setFunction(null);
        List<ToolCall> calls = List.of(call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
    }

    @Test
    @DisplayName("应该保持插入顺序")
    void shouldMaintainInsertionOrder() {
        // Given
        ToolCall call3 = createToolCall("tool3", "{\"param\":\"value3\"}");
        ToolCall call1 = createToolCall("tool1", "{\"param\":\"value1\"}");
        ToolCall call2 = createToolCall("tool2", "{\"param\":\"value2\"}");
        List<ToolCall> calls = List.of(call3, call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(3, result.size());
        assertEquals("tool3", result.get(0).getFunction().getName());
        assertEquals("tool1", result.get(1).getFunction().getName());
        assertEquals("tool2", result.get(2).getFunction().getName());
    }

    @Test
    @DisplayName("应该处理复杂的JSON参数")
    void shouldHandleComplexJsonArguments() {
        // Given
        String complexArgs1 = "{\"query\":\"test\",\"limit\":10,\"filters\":{\"type\":\"news\"}}";
        String complexArgs2 = "{\"query\":\"test\",\"limit\":10,\"filters\":{\"type\":\"news\"}}";
        String complexArgs3 = "{\"query\":\"test\",\"limit\":20,\"filters\":{\"type\":\"news\"}}";

        ToolCall call1 = createToolCall("search", complexArgs1);
        ToolCall call2 = createToolCall("search", complexArgs2);
        ToolCall call3 = createToolCall("search", complexArgs3);
        List<ToolCall> calls = List.of(call1, call2, call3);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(2, result.size());
        assertEquals("search", result.get(0).getFunction().getName());
        assertEquals("search", result.get(1).getFunction().getName());
        // First two should be deduplicated
        assertEquals(complexArgs1, result.get(0).getFunction().getArguments());
        assertEquals(complexArgs3, result.get(1).getFunction().getArguments());
    }

    @Test
    @DisplayName("应该处理空参数")
    void shouldHandleEmptyArguments() {
        // Given
        ToolCall call1 = createToolCall("tool1", "{}");
        ToolCall call2 = createToolCall("tool1", "{}");
        List<ToolCall> calls = List.of(call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
        assertEquals("{}", result.get(0).getFunction().getArguments());
    }

    @Test
    @DisplayName("应该处理空白参数")
    void shouldHandleBlankArguments() {
        // Given
        ToolCall call1 = createToolCall("tool1", "");
        ToolCall call2 = createToolCall("tool1", "");
        List<ToolCall> calls = List.of(call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
        assertEquals("", result.get(0).getFunction().getArguments());
    }

    @Test
    @DisplayName("应该处理包含特殊字符的参数")
    void shouldHandleArgumentsWithSpecialCharacters() {
        // Given
        String specialArgs1 = "{\"text\":\"Hello \\\"World\\\"!\nNew line\\tTab\"}";
        String specialArgs2 = "{\"text\":\"Hello \\\"World\\\"!\nNew line\\tTab\"}";

        ToolCall call1 = createToolCall("process_text", specialArgs1);
        ToolCall call2 = createToolCall("process_text", specialArgs2);
        List<ToolCall> calls = List.of(call1, call2);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(1, result.size());
        assertEquals("process_text", result.get(0).getFunction().getName());
        assertEquals(specialArgs1, result.get(0).getFunction().getArguments());
    }

    @Test
    @DisplayName("应该正确处理大量重复的工具调用")
    void shouldHandleManyDuplicateToolCalls() {
        // Given
        ToolCall call = createToolCall("tool1", "{\"param\":\"value\"}", "call_1");
        List<ToolCall> calls = List.of(call, call, call, call, call, call, call, call, call, call);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(1, result.size());
        assertEquals("tool1", result.get(0).getFunction().getName());
        assertEquals("call_1", result.get(0).getId()); // Should keep the first one
    }

    @Test
    @DisplayName("应该处理混合的有效和无效工具调用")
    void shouldHandleMixedValidAndInvalidToolCalls() {
        // Given
        ToolCall validCall1 = createToolCall("valid1", "{\"param\":\"value1\"}", "call_1");
        ToolCall nullFunctionCall = new ToolCall();
        nullFunctionCall.setId("invalid1");
        nullFunctionCall.setType("function");
        nullFunctionCall.setFunction(null);

        ToolCall validCall2 = createToolCall("valid2", "{\"param\":\"value2\"}", "call_2");
        ToolCall nullCall = null;

        List<ToolCall> calls = Arrays.asList(validCall1, nullFunctionCall, validCall2, nullCall);

        // When
        List<ToolCall> result = deduplicator.deduplicate(calls);

        // Then
        assertEquals(2, result.size());
        assertEquals("valid1", result.get(0).getFunction().getName());
        assertEquals("valid2", result.get(1).getFunction().getName());
    }

    // Helper methods

    private ToolCall createToolCall(String name, String arguments, String id) {
        ToolCall call = new ToolCall();
        call.setId(id);
        call.setType("function");
        ToolCall.ToolFunction function = new ToolCall.ToolFunction();
        function.setName(name);
        function.setArguments(arguments);
        call.setFunction(function);
        return call;
    }

    private ToolCall createToolCall(String name, String arguments) {
        return createToolCall(name, arguments, "call_" + System.currentTimeMillis());
    }
}