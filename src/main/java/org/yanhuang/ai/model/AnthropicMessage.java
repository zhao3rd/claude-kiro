package org.yanhuang.ai.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    private Map<String, Object> metadata;

    private List<Attachment> attachments;

    private final Map<String, Object> additionalProperties = new HashMap<>();

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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    @JsonAnySetter
    public void addAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContentBlock {
        private String type;
        private String text;

        // tool_use fields
        private String id;
        private String name;
        private Map<String, Object> input;

        // tool_result fields
        @JsonProperty("tool_use_id")
        private String toolUseId;
        private Object content;  // Can be string or complex object

        // image fields
        private ImageSource source;

        private List<Map<String, Object>> citations;

        @JsonProperty("is_error")
        private Boolean isError;

        private String status;

        @JsonProperty("status_details")
        private Map<String, Object> statusDetails;

        private Map<String, Object> metadata;

        // optional cache control hints from clients (e.g., {"type":"ephemeral"})
        @com.fasterxml.jackson.annotation.JsonProperty("cache_control")
        private java.util.Map<String, Object> cacheControl;

        private final Map<String, Object> additionalProperties = new HashMap<>();

        public String getType() {
            return type;
        }

        public ImageSource getSource() {
            return source;
        }

        public void setSource(ImageSource source) {
            this.source = source;
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

        public String getToolUseId() {
            return toolUseId;
        }

        public void setToolUseId(String toolUseId) {
            this.toolUseId = toolUseId;
        }

        public Object getContent() {
            return content;
        }

        public void setContent(Object content) {
            this.content = content;
        }

        public java.util.Map<String, Object> getCacheControl() {
            return cacheControl;
        }

        public void setCacheControl(java.util.Map<String, Object> cacheControl) {
            this.cacheControl = cacheControl;
        }

        public List<Map<String, Object>> getCitations() {
            return citations;
        }

        public void setCitations(List<Map<String, Object>> citations) {
            this.citations = citations;
        }

        public Boolean getIsError() {
            return isError;
        }

        public void setIsError(Boolean isError) {
            this.isError = isError;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, Object> getStatusDetails() {
            return statusDetails;
        }

        public void setStatusDetails(Map<String, Object> statusDetails) {
            this.statusDetails = statusDetails;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        @JsonAnySetter
        public void addAdditionalProperty(String key, Object value) {
            this.additionalProperties.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }
    }

    /**
     * Image source for image content blocks
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageSource {
        private String type;  // "base64" or "url"

        @JsonProperty("media_type")
        private String mediaType;  // "image/jpeg", "image/png", "image/gif", "image/webp"

        // For type="base64"
        private String data;  // base64-encoded image data

        // For type="url"
        private String url;   // remote image URL

        private final Map<String, Object> additionalProperties = new HashMap<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMediaType() {
            return mediaType;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        @JsonAnySetter
        public void addAdditionalProperty(String key, Object value) {
            this.additionalProperties.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Attachment {

        @JsonProperty("file_id")
        private String fileId;

        private List<Map<String, Object>> tools;

        private Map<String, Object> metadata;

        private final Map<String, Object> additionalProperties = new HashMap<>();

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }

        public List<Map<String, Object>> getTools() {
            return tools;
        }

        public void setTools(List<Map<String, Object>> tools) {
            this.tools = tools;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }

        @JsonAnySetter
        public void addAdditionalProperty(String key, Object value) {
            this.additionalProperties.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
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
