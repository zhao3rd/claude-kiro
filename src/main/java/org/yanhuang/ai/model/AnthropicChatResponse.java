package org.yanhuang.ai.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicChatResponse {

    private String id;

    private String type;

    private String model;

    /**
     * Extension field: Message creation timestamp (seconds since epoch).
     *
     * <p><strong>Note:</strong> This is NOT part of the official Anthropic API
     * specification. This field is provided for internal logging and debugging
     * purposes. Client applications should not rely on this field as it may be
     * removed in future versions for better API compatibility.</p>
     *
     * @see <a href="https://docs.anthropic.com/en/api/messages">Official API Docs</a>
     */
    @JsonProperty("created_at")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long createdAt;

    private String role = "assistant";

    private List<AnthropicMessage.ContentBlock> content = new ArrayList<>();

    private Usage usage;

    @JsonProperty("stop_reason")
    private String stopReason;

    @JsonProperty("stop_sequence")
    private String stopSequence;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<AnthropicMessage.ContentBlock> getContent() {
        return content;
    }

    public void setContent(List<AnthropicMessage.ContentBlock> content) {
        this.content = content;
    }

    public void addContentBlock(AnthropicMessage.ContentBlock block) {
        if (block != null && (block.getType() != null)) {
            this.content.add(block);
        }
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(String stopSequence) {
        this.stopSequence = stopSequence;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {

        @JsonProperty("input_tokens")
        private Integer inputTokens;

        @JsonProperty("output_tokens")
        private Integer outputTokens;

        public Integer getInputTokens() {
            return inputTokens;
        }

        public void setInputTokens(Integer inputTokens) {
            this.inputTokens = inputTokens;
        }

        public Integer getOutputTokens() {
            return outputTokens;
        }

        public void setOutputTokens(Integer outputTokens) {
            this.outputTokens = outputTokens;
        }
    }
}

