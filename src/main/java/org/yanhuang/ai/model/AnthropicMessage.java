package org.yanhuang.ai.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicMessage {

    private String role;

    @JsonDeserialize(using = ContentDeserializer.class)
    private List<ContentBlock> content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public void setContent(List<ContentBlock> content) {
        this.content = content;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentBlock {
        private String type;
        private String text;
        private String id;
        private String name;
        private Map<String, Object> input;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Map<String, Object> getInput() {
            return input;
        }

        public void setInput(Map<String, Object> input) {
            this.input = input;
        }
    }

    /**
     * 自定义反序列化器，支持content字段为字符串或数组格式
     */
    public static class ContentDeserializer extends JsonDeserializer<List<ContentBlock>> {

        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public List<ContentBlock> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
                // 处理字符串格式
                String textValue = p.getValueAsString();
                List<ContentBlock> content = new ArrayList<>();
                if (textValue != null && !textValue.trim().isEmpty()) {
                    ContentBlock block = new ContentBlock();
                    block.setType("text");
                    block.setText(textValue);
                    content.add(block);
                }
                return content;
            } else if (p.getCurrentToken() == JsonToken.START_ARRAY) {
                // 处理数组格式
                List<ContentBlock> content = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    ContentBlock block = mapper.readValue(p, ContentBlock.class);
                    content.add(block);
                }
                return content;
            } else if (p.getCurrentToken() == JsonToken.VALUE_NULL) {
                return null;
            } else {
                // 尝试作为单个对象处理
                ContentBlock block = mapper.readValue(p, ContentBlock.class);
                List<ContentBlock> content = new ArrayList<>();
                content.add(block);
                return content;
            }
        }
    }
}

