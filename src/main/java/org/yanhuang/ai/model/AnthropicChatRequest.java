package org.yanhuang.ai.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicChatRequest {

    private String model;

    private List<AnthropicMessage> messages = new ArrayList<>();

    @JsonDeserialize(using = AnthropicMessage.ContentDeserializer.class)
    private List<AnthropicMessage.ContentBlock> system;

    // Accept both legacy and newer naming
    @JsonProperty("max_tokens")
    @JsonAlias({"max_output_tokens"})
    private Integer maxTokens;

    private Boolean stream;

    private Double temperature;

    @JsonProperty("top_p")
    private Double topP;

    @JsonProperty("top_k")
    private Integer topK;

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

    // Request timeout in milliseconds
    private Integer timeout;

    private List<ToolDefinition> tools;

    @JsonProperty("tool_choice")
    private Map<String, Object> toolChoice;

    // Whether the model may run tools in parallel
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    private Map<String, Object> metadata;

    private Map<String, Object> thinking;

    @JsonProperty("service_tier")
    private String serviceTier;

    private String container;

    @JsonProperty("context_management")
    private Map<String, Object> contextManagement;

    @JsonProperty("mcp_servers")
    private List<Map<String, Object>> mcpServers;

    @JsonProperty("stream_options")
    private Map<String, Object> streamOptions;

    @JsonProperty("extra_headers")
    private Map<String, Object> extraHeaders;

    @JsonProperty("response_format")
    private Map<String, Object> responseFormat;

    // Beta flags supported by Anthropic API
    private List<String> betas;

    private final Map<String, Object> additionalProperties = new HashMap<>();

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

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
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

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getServiceTier() {
        return serviceTier;
    }

    public void setServiceTier(String serviceTier) {
        this.serviceTier = serviceTier;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public Map<String, Object> getContextManagement() {
        return contextManagement;
    }

    public void setContextManagement(Map<String, Object> contextManagement) {
        this.contextManagement = contextManagement;
    }

    public List<Map<String, Object>> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<Map<String, Object>> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public Map<String, Object> getStreamOptions() {
        return streamOptions;
    }

    public void setStreamOptions(Map<String, Object> streamOptions) {
        this.streamOptions = streamOptions;
    }

    public Map<String, Object> getExtraHeaders() {
        return extraHeaders;
    }

    public void setExtraHeaders(Map<String, Object> extraHeaders) {
        this.extraHeaders = extraHeaders;
    }

    public Map<String, Object> getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(Map<String, Object> responseFormat) {
        this.responseFormat = responseFormat;
    }

    public List<String> getBetas() {
        return betas;
    }

    public void setBetas(List<String> betas) {
        this.betas = betas;
    }

    public Map<String, Object> getThinking() {
        return thinking;
    }

    public void setThinking(Map<String, Object> thinking) {
        this.thinking = thinking;
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
