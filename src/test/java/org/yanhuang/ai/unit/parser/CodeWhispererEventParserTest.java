package org.yanhuang.ai.unit.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.yanhuang.ai.parser.CodeWhispererEventParser;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CodeWhispererEventParser 单元测试")
class CodeWhispererEventParserTest {

    private CodeWhispererEventParser parser;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        parser = new CodeWhispererEventParser(mapper);
    }

    @Test
    @DisplayName("应该成功创建解析器实例")
    void shouldCreateParserSuccessfully() {
        // Then
        assertNotNull(parser);
    }

    @Test
    @DisplayName("应该处理null数据")
    void shouldHandleNullData() {
        // When
        List<JsonNode> result = parser.parse(null);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理空数组")
    void shouldHandleEmptyArray() {
        // When
        List<JsonNode> result = parser.parse(new byte[0]);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理太小的事件数据")
    void shouldHandleTooSmallEventData() {
        // Given
        byte[] smallData = new byte[]{0x00, 0x00, 0x00, 0x01}; // Less than 12 bytes

        // When
        List<JsonNode> result = parser.parse(smallData);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理无效的总长度")
    void shouldHandleInvalidTotalLength() {
        // Given
        byte[] invalidData = new byte[]{
            // Total length = -1
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            // Header length = 4
            0x00, 0x00, 0x00, 0x04,
            // Minimal data
            0x00, 0x00, 0x00, 0x00
        };

        // When
        List<JsonNode> result = parser.parse(invalidData);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理无效的头部长度")
    void shouldHandleInvalidHeaderLength() {
        // Given
        byte[] invalidData = new byte[]{
            // Total length = 20
            0x00, 0x00, 0x00, 0x14,
            // Header length = -1
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            // Minimal data
            0x00, 0x00, 0x00, 0x00
        };

        // When
        List<JsonNode> result = parser.parse(invalidData);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理超出数据范围的事件")
    void shouldHandleEventExceedingDataRange() {
        // Given
        byte[] invalidData = new byte[]{
            // Total length = 100 (too large for actual data)
            0x00, 0x00, 0x00, 0x64,
            // Header length = 4
            0x00, 0x00, 0x00, 0x04,
            // Some minimal data but not enough for 100 bytes
            0x00, 0x00, 0x00, 0x00
        };

        // When
        List<JsonNode> result = parser.parse(invalidData);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该成功解析简单JSON事件")
    void shouldParseSimpleJsonEventSuccessfully() {
        // Given - Create a minimal valid event format
        String jsonContent = "{\"message\":\"Hello World\",\"type\":\"test\"}";
        byte[] eventData = createEventPacket(jsonContent.getBytes());

        // When
        List<JsonNode> result = parser.parse(eventData);

        // Then
        assertEquals(1, result.size());
        JsonNode event = result.get(0);
        assertEquals("Hello World", event.get("message").asText());
        assertEquals("test", event.get("type").asText());
    }

    @Test
    @DisplayName("应该成功解析复杂JSON事件")
    void shouldParseComplexJsonEventSuccessfully() {
        // Given
        String jsonContent = "{\"user\":{\"id\":123,\"name\":\"Test\"},\"actions\":[\"read\",\"write\"]}";
        byte[] eventData = createEventPacket(jsonContent.getBytes());

        // When
        List<JsonNode> result = parser.parse(eventData);

        // Then
        assertEquals(1, result.size());
        JsonNode event = result.get(0);
        assertEquals(123, event.get("user").get("id").asInt());
        assertEquals("Test", event.get("user").get("name").asText());
        assertEquals(2, event.get("actions").size());
    }

    @Test
    @DisplayName("应该处理包含前缀文本的事件")
    void shouldHandleEventWithPrefixText() {
        // Given
        String contentWithPrefix = "Some prefix text {\"message\":\"Hello World\"}";
        byte[] eventData = createEventPacket(contentWithPrefix.getBytes());

        // When
        List<JsonNode> result = parser.parse(eventData);

        // Then
        assertEquals(1, result.size());
        JsonNode event = result.get(0);
        assertEquals("Hello World", event.get("message").asText());
    }

    @Test
    @DisplayName("应该忽略无效的JSON内容")
    void shouldIgnoreInvalidJsonContent() {
        // Given
        String invalidJson = "{invalid json content}";
        byte[] eventData = createEventPacket(invalidJson.getBytes());

        // When
        List<JsonNode> result = parser.parse(eventData);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("应该处理多个事件")
    void shouldHandleMultipleEvents() {
        // Given
        String json1 = "{\"event\":\"first\",\"id\":1}";
        String json2 = "{\"event\":\"second\",\"id\":2}";

        byte[] event1 = createEventPacket(json1.getBytes());
        byte[] event2 = createEventPacket(json2.getBytes());

        byte[] combinedEvents = new byte[event1.length + event2.length];
        System.arraycopy(event1, 0, combinedEvents, 0, event1.length);
        System.arraycopy(event2, 0, combinedEvents, event1.length, event2.length);

        // When
        List<JsonNode> result = parser.parse(combinedEvents);

        // Then
        assertEquals(2, result.size());
        assertEquals("first", result.get(0).get("event").asText());
        assertEquals("second", result.get(1).get("event").asText());
    }

    @Test
    @DisplayName("应该正确处理大端序整数")
    void shouldHandleBigEndianIntegersCorrectly() {
        // Given
        String jsonContent = "{\"test\":\"hello\"}";
        byte[] eventData = createEventPacket(jsonContent.getBytes());

        // When
        List<JsonNode> result = parser.parse(eventData);

        // Then
        assertEquals(1, result.size());
        assertEquals("hello", result.get(0).get("test").asText());
    }

    // Helper methods

    /**
     * Creates an event packet with the specified payload.
     * The packet format based on the parser implementation:
     * - 4 bytes: total length (big-endian)
     * - 4 bytes: header length (big-endian)
     * - 4 bytes: unknown/padding
     * - header length bytes: header (usually 0)
     * - payload bytes: payload
     * - 4 bytes: footer/padding
     */
    private byte[] createEventPacket(byte[] payload) {
        int headerLength = 0;
        int totalLength = 12 + headerLength + payload.length + 4; // +4 for footer

        byte[] packet = new byte[totalLength];
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // Write total length
        buffer.putInt(totalLength);
        // Write header length
        buffer.putInt(headerLength);
        // Write 4 bytes of unknown/padding
        buffer.putInt(0);

        // Skip header space
        buffer.position(buffer.position() + headerLength);

        // Write payload
        buffer.put(payload);

        // Write footer
        buffer.putInt(0);

        return packet;
    }
}