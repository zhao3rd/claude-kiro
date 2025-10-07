package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for image inputs: verify single/multiple images and server-side recognition note.
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
public class ImageInputE2ETest extends BaseE2ETest {

    private String tinyPngBase64() {
        // Minimal PNG signature + IHDR (not a real image; only for base64/mediaType validation, no external dependency)
        byte[] fake = new byte[]{
            (byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A,
            (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0D,(byte)0x49,(byte)0x48,(byte)0x44,(byte)0x52,
            (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,
            (byte)0x08,(byte)0x02,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x90,(byte)0x77,(byte)0x53,
            (byte)0xDE
        };
        return Base64.getEncoder().encodeToString(fake);
    }

    private ObjectNode imageBlock(String mediaType, String type, String data) {
        ObjectNode image = objectMapper.createObjectNode();
        image.put("type", "image");
        ObjectNode src = objectMapper.createObjectNode();
        src.put("type", type);
        src.put("media_type", mediaType);
        src.put("data", data);
        image.set("source", src);
        return image;
    }

    @Test
    @DisplayName("单张图片：base64内联，Kiro识别计数")
    void testSingleImageBase64() {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("model", "claude-sonnet-4-5-20250929");
        req.put("max_tokens", 100);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        content.add(imageBlock("image/png", "base64", tinyPngBase64()));
        ObjectNode text = objectMapper.createObjectNode();
        text.put("type", "text");
        text.put("text", "请描述这张图片的大致内容（若无法解析，也请返回通用回答）。");
        content.add(text);
        user.set("content", content);
        messages.add(user);
        req.set("messages", messages);

        JsonNode resp = apiClient.createChatCompletion(req)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

        assertNotNull(resp);
        validateBasicResponse(resp);
        String txt = resp.get("content").get(0).get("text").asText("");
        assertTrue(txt.contains("[Note: 1 image(s) received by Kiro]") || txt.contains("image(s) received"),
                "Response should contain image recognition count note");
    }

    @Test
    @DisplayName("多张图片：base64+url混合，Kiro识别计数")
    void testMultipleImagesMixed() {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("model", "claude-sonnet-4-5-20250929");
        req.put("max_tokens", 120);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        ArrayNode content = objectMapper.createArrayNode();
        content.add(imageBlock("image/png", "base64", tinyPngBase64()));
        content.add(imageBlock("image/jpeg", "url", "https://example.com/sample.jpg"));
        ObjectNode text = objectMapper.createObjectNode();
        text.put("type", "text");
        text.put("text", "请简述两张图片的差异（若无法解析，也请返回通用回答）。");
        content.add(text);
        user.set("content", content);
        messages.add(user);
        req.set("messages", messages);

        JsonNode resp = apiClient.createChatCompletion(req)
                .block(Duration.ofSeconds(config.getTimeoutSeconds()));

        assertNotNull(resp);
        validateBasicResponse(resp);
        String txt = resp.get("content").get(0).get("text").asText("");
        assertTrue(txt.contains("[Note: 2 image(s) received by Kiro]") || txt.contains("image(s) received"),
                "Response should contain image recognition count note (2 images)");
    }
}
