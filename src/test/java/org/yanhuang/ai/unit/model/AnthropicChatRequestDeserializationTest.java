package org.yanhuang.ai.unit.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.yanhuang.ai.model.AnthropicChatRequest;
import org.yanhuang.ai.model.AnthropicMessage;
import org.yanhuang.ai.model.ToolDefinition;

class AnthropicChatRequestDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeExtendedFields() throws Exception {
        String json = """
            {
              "model": "claude-3-5-sonnet-20240620",
              "max_tokens": 1024,
              "messages": [
                {
                  "role": "user",
                  "content": [
                    {"type": "text", "text": "Hello"},
                    {"type": "image", "source": {"type": "base64", "media_type": "image/png", "data": "ZmFrZQ=="}}
                  ],
                  "metadata": {"source": "unit-test"},
                  "attachments": [
                    {
                      "file_id": "file_123",
                      "tools": [
                        {"type": "computer_2025-01-24", "name": "browser"}
                      ],
                      "metadata": {"description": "sample attachment"}
                    }
                  ],
                  "extra_message_field": "should survive"
                }
              ],
              "system": "Follow instructions strictly.",
              "stream": true,
              "stream_options": {"include_usage": true},
              "stop_sequences": ["Human:", "Assistant:"],
              "temperature": 0.2,
              "top_p": 0.9,
              "top_k": 5,
              "metadata": {"tracking_id": "123"},
              "tool_choice": {"type": "auto"},
              "tools": [
                {
                  "name": "get_weather",
                  "description": "Fetch weather",
                  "input_schema": {"type": "object"},
                  "additional_config": {"retries": 2}
                }
              ],
              "thinking": {"enabled": true, "budget_tokens": 2048},
              "service_tier": "auto",
              "context_management": {"persist_tool_outputs": true},
              "container": "container-123",
              "mcp_servers": [
                {"name": "filesystem", "url": "https://example.com"}
              ],
              "extra_headers": {"X-Test": "value"},
              "response_format": {"type": "json"},
              "unknown_field": "should be captured"
            }
            """;

        AnthropicChatRequest request = objectMapper.readValue(json, AnthropicChatRequest.class);

        assertThat(request.getModel()).isEqualTo("claude-3-5-sonnet-20240620");
        assertThat(request.getStream()).isTrue();
        assertThat(request.getStreamOptions()).containsEntry("include_usage", true);
        assertThat(request.getServiceTier()).isEqualTo("auto");
        assertThat(request.getContextManagement()).containsEntry("persist_tool_outputs", true);
        assertThat(request.getMcpServers())
            .hasSize(1)
            .first()
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("name", "filesystem")
            .containsEntry("url", "https://example.com");
        assertThat(request.getAdditionalProperties()).containsEntry("unknown_field", "should be captured");
        assertThat(request.getSystem()).hasSize(1);
        AnthropicMessage message = request.getMessages().get(0);
        assertThat(message.getMetadata()).containsEntry("source", "unit-test");
        assertThat(message.getAttachments()).hasSize(1);
        AnthropicMessage.Attachment attachment = message.getAttachments().get(0);
        assertThat(attachment.getFileId()).isEqualTo("file_123");
        assertThat(attachment.getTools())
            .first()
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("type", "computer_2025-01-24")
            .containsEntry("name", "browser");
        assertThat(message.getAdditionalProperties()).containsEntry("extra_message_field", "should survive");
        AnthropicMessage.ContentBlock imageBlock = message.getContent().get(1);
        assertThat(imageBlock.getSource().getMediaType()).isEqualTo("image/png");
        assertThat(request.getTools()).hasSize(1);
        ToolDefinition tool = request.getTools().get(0);
        assertThat(tool.getAdditionalProperties())
            .containsKey("additional_config")
            .extractingByKey("additional_config")
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsEntry("retries", 2);
        assertThat(request.getResponseFormat()).containsEntry("type", "json");
        assertThat(request.getExtraHeaders()).containsEntry("X-Test", "value");
        assertThat(request.getThinking()).containsEntry("enabled", true);
    }

    @Test
    void shouldAcceptMaxOutputTokensAlias() throws Exception {
        String json = "{\n" +
                "  \"model\": \"claude-3-5-sonnet-20240620\",\n" +
                "  \"max_output_tokens\": 512,\n" +
                "  \"messages\": [{\n" +
                "    \"role\": \"user\",\n" +
                "    \"content\": [{\"type\":\"text\",\"text\":\"hi\"}]\n" +
                "  }]\n" +
                "}";
        AnthropicChatRequest req = objectMapper.readValue(json, AnthropicChatRequest.class);
        assertThat(req.getMaxTokens()).isEqualTo(512);
    }

    @Test
    void shouldAcceptParallelToolCallsAndBetas() throws Exception {
        String json = "{\n" +
                "  \"model\": \"claude-3-5-sonnet-20240620\",\n" +
                "  \"parallel_tool_calls\": true,\n" +
                "  \"betas\": [\"tool_use\", \"json_mode\"],\n" +
                "  \"messages\": [{\n" +
                "    \"role\": \"user\",\n" +
                "    \"content\": [{\"type\":\"text\",\"text\":\"hello\"}]\n" +
                "  }]\n" +
                "}";
        AnthropicChatRequest req = objectMapper.readValue(json, AnthropicChatRequest.class);
        assertThat(req.getParallelToolCalls()).isTrue();
        assertThat(req.getBetas()).containsExactly("tool_use", "json_mode");
    }

    @Test
    void shouldParseImageUrlSource() throws Exception {
        String json = "{\n" +
                "  \"model\": \"claude-3-5-sonnet-20240620\",\n" +
                "  \"max_tokens\": 64,\n" +
                "  \"messages\": [{\n" +
                "    \"role\": \"user\",\n" +
                "    \"content\": [{\n" +
                "      \"type\": \"image\",\n" +
                "      \"source\": {\"type\": \"url\", \"url\": \"https://example.com/a.png\"}\n" +
                "    }]\n" +
                "  }]\n" +
                "}";
        AnthropicChatRequest req = objectMapper.readValue(json, AnthropicChatRequest.class);
        AnthropicMessage.ContentBlock block = req.getMessages().get(0).getContent().get(0);
        assertThat(block.getSource().getUrl()).isEqualTo("https://example.com/a.png");
    }

    @Test
    void shouldPreserveSchemaFieldInToolDefinition() throws Exception {
        String json = "{\n" +
                "  \"model\": \"claude-3-5-sonnet-20240620\",\n" +
                "  \"max_tokens\": 64,\n" +
                "  \"messages\": [{\n" +
                "    \"role\": \"user\",\n" +
                "    \"content\": [{\"type\":\"text\",\"text\":\"hello\"}]\n" +
                "  }],\n" +
                "  \"tools\": [{\n" +
                "    \"name\": \"search\",\n" +
                "    \"description\": \"desc\",\n" +
                "    \"input_schema\": {\"type\": \"object\"},\n" +
                "    \"@schema\": {\"$id\": \"custom\"}\n" +
                "  }]\n" +
                "}";
        AnthropicChatRequest req = objectMapper.readValue(json, AnthropicChatRequest.class);
        ToolDefinition tool = req.getTools().get(0);
        assertThat(tool.getAdditionalProperties()).containsKey("@schema");
    }

}
