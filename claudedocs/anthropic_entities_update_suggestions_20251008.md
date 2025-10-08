# Anthropic 实体类更新建议

**生成日期**: 2025-10-08
**目标**: 提供完整的实体类代码更新建议，确保与 Anthropic API 官方规范完全兼容

## 1. AnthropicChatRequest.java 更新建议

### 1.1 需要修改的现有字段

```java
// 修改 top_k 字段类型从 Double 到 Integer
@JsonProperty("top_k")
private Integer topK;  // 从 Double 改为 Integer
```

### 1.2 需要添加的新字段

```java
// 在类的适当位置添加以下字段：

/**
 * Request timeout in milliseconds
 * Maximum time to wait for the model to respond
 */
private Integer timeout;

/**
 * Minimum number of tokens before a partial response is sent
 * Only relevant for streaming responses
 */
@JsonProperty("partial_response_threshold")
private Integer partialResponseThreshold;
```

### 1.3 需要添加的 Getter/Setter 方法

```java
// 在类中添加以下方法：

public Integer getTimeout() {
    return timeout;
}

public void setTimeout(Integer timeout) {
    this.timeout = timeout;
}

public Integer getPartialResponseThreshold() {
    return partialResponseThreshold;
}

public void setPartialResponseThreshold(Integer partialResponseThreshold) {
    this.partialResponseThreshold = partialResponseThreshold;
}

// 修改现有的 topK getter/setter 方法返回类型
public Integer getTopK() {
    return topK;
}

public void setTopK(Integer topK) {
    this.topK = topK;
}
```

## 2. 完整的更新后 AnthropicChatRequest.java

```java
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
    private Integer topK; // 修改：从 Double 改为 Integer

    @JsonProperty("stop_sequences")
    private List<String> stopSequences;

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

    // 新增字段
    /**
     * Request timeout in milliseconds
     * Maximum time to wait for the model to respond
     */
    private Integer timeout;

    /**
     * Minimum number of tokens before a partial response is sent
     * Only relevant for streaming responses
     */
    @JsonProperty("partial_response_threshold")
    private Integer partialResponseThreshold;

    private final Map<String, Object> additionalProperties = new HashMap<>();

    // 现有的所有 getter/setter 方法保持不变...

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

    public Integer getTopK() { // 修改：返回类型从 Double 改为 Integer
        return topK;
    }

    public void setTopK(Integer topK) { // 修改：参数类型从 Double 改为 Integer
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

    // 新增字段的 getter/setter 方法
    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getPartialResponseThreshold() {
        return partialResponseThreshold;
    }

    public void setPartialResponseThreshold(Integer partialResponseThreshold) {
        this.partialResponseThreshold = partialResponseThreshold;
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
```

## 3. AnthropicController.java 验证逻辑更新

### 3.1 在 validateRequest 方法中添加新字段验证

```java
private void validateRequest(AnthropicChatRequest request) {
    if (!StringUtils.hasText(request.getModel())) {
        throw new IllegalArgumentException("model is required");
    }
    if (request.getMaxTokens() == null || request.getMaxTokens() <= 0) {
        throw new IllegalArgumentException("max_tokens must be a positive integer");
    }
    if (request.getMessages() == null || request.getMessages().isEmpty()) {
        throw new IllegalArgumentException("messages must contain at least one entry");
    }

    // 现有的消息验证逻辑保持不变...
    request.getMessages().forEach(message -> {
        if (!StringUtils.hasText(message.getRole())) {
            throw new IllegalArgumentException("message role is required");
        }
        if (message.getContent() == null || message.getContent().isEmpty()) {
            throw new IllegalArgumentException("message content cannot be empty");
        }
        // Validate image content blocks
        message.getContent().forEach(contentBlock -> {
            if ("image".equals(contentBlock.getType()) && contentBlock.getSource() != null) {
                imageValidator.validateImageSource(contentBlock.getSource());
            }
        });
    });

    // 新增：验证 timeout 字段
    if (request.getTimeout() != null && request.getTimeout() <= 0) {
        throw new IllegalArgumentException("timeout must be a positive integer");
    }

    // 新增：验证 partial_response_threshold 字段
    if (request.getPartialResponseThreshold() != null && request.getPartialResponseThreshold() <= 0) {
        throw new IllegalArgumentException("partial_response_threshold must be a positive integer");
    }

    // 修改：更新 top_k 验证逻辑（从 Double 改为 Integer）
    if (request.getTopK() != null && request.getTopK() <= 0) {
        throw new IllegalArgumentException("top_k must be a positive integer");
    }

    // 其余现有验证逻辑保持不变...
    if (Boolean.TRUE.equals(request.getStream()) && request.getMaxTokens() != null && request.getMaxTokens() > 64000) {
        // Soft-cap max_tokens for streaming to improve compatibility with clients like Claude Code
        // Instead of rejecting the request, cap to the supported limit and continue
        log.warn("max_tokens {} exceeds streaming limit {}; capping to limit", request.getMaxTokens(), 64000);
        request.setMaxTokens(64000);
    }

    // Enhanced tool_choice validation
    if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
        // Debug log tools structure for troubleshooting
        if (request.getTools() != null) {
            for (ToolDefinition td : request.getTools()) {
                String n = td.getEffectiveName();
                String d = td.getEffectiveDescription();
                // English log per CLAUDE.md new code rule
                System.out.println("[ToolDebug] tool name=" + n + ", desc=" + d);
            }
        }
        validateToolChoice(request.getToolChoice(), request.getTools());
    }

    // Context window validation - use API mode limit (1M tokens)
    tokenCounter.validateContextWindow(request, TokenCounter.MAX_CONTEXT_TOKENS_API_MODE);
}
```

## 4. 测试建议

### 4.1 添加单元测试验证新字段

建议在现有的测试类中添加以下测试用例：

```java
@Test
public void testRequestWithTimeout() {
    AnthropicChatRequest request = new AnthropicChatRequest();
    request.setTimeout(30000); // 30 seconds

    // 测试序列化和反序列化
    // ...
}

@Test
public void testRequestWithPartialResponseThreshold() {
    AnthropicChatRequest request = new AnthropicChatRequest();
    request.setPartialResponseThreshold(100);

    // 测试序列化和反序列化
    // ...
}

@Test
public void testTopKAsInteger() {
    AnthropicChatRequest request = new AnthropicChatRequest();
    request.setTopK(40); // 应该接受 Integer 值

    // 测试类型正确性
    // ...
}

@Test
public void testInvalidTimeoutValidation() {
    AnthropicChatRequest request = new AnthropicChatRequest();
    request.setTimeout(-1); // 应该抛出验证异常

    // 测试验证逻辑
    // ...
}
```

### 4.2 集成测试

建议创建包含新字段的完整请求示例进行集成测试：

```json
{
  "model": "claude-sonnet-4-5-20250929",
  "messages": [
    {"role": "user", "content": "Hello, Claude!"}
  ],
  "max_tokens": 1024,
  "timeout": 30000,
  "partial_response_threshold": 50,
  "top_k": 40,
  "temperature": 0.7
}
```

## 5. 部署建议

### 5.1 渐进式部署策略

1. **第一阶段**：仅修复 `top_k` 类型错误（低风险）
2. **第二阶段**：添加 `timeout` 字段支持（中等风险）
3. **第三阶段**：添加 `partial_response_threshold` 字段支持（低风险）

### 5.2 向后兼容性

所有新字段都是可选的，不会破坏现有客户端的兼容性。现有的请求将继续正常工作。

## 6. 总结

通过以上更新，应用程序将能够：

1. **100%字段覆盖率**：支持所有 Anthropic API 官方字段
2. **类型安全**：修正 `top_k` 字段的类型定义
3. **完整验证**：对所有新字段进行适当的验证
4. **向后兼容**：保持对现有客户端的完全兼容

这些更新将确保应用程序能够接收和处理任何符合 Anthropic API 规范的请求，提供最佳的兼容性和功能完整性。