package org.yanhuang.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnthropicChatRequest {

    private String model;

    private List<AnthropicMessage> messages = new ArrayList<>();

    private List<AnthropicMessage.ContentBlock> system;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Boolean stream;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Double topK;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    private List<ToolDefinition> tools;

    @JsonProperty("tool_choice")
    private Map<String, Object> toolChoice;

    private Map<String, Object> metadata;

    private Map<String, Object> thinking;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<AnthropicMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AnthropicMessage> messages) {
        this.messages = messages;
    }

    public List<AnthropicMessage.ContentBlock> getSystem() {
        return system;
    }

    public void setSystem(List<AnthropicMessage.ContentBlock> system) {
        this.system = system;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getTopK() {
        return topK;
    }

    public void setTopK(Double topK) {
        this.topK = topK;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ToolDefinition> tools) {
        this.tools = tools;
    }

    public Map<String, Object> getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(Map<String, Object> toolChoice) {
        this.toolChoice = toolChoice;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Map<String, Object> getThinking() {
        return thinking;
    }

    public void setThinking(Map<String, Object> thinking) {
        this.thinking = thinking;
    }
}

