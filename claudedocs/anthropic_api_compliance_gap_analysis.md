# Anthropic Claude API 兼容性差异分析报告

**生成日期**: 2025-10-06
**项目**: claude-kiro
**版本**: 基于当前主分支代码
**分析范围**: 对比 Anthropic 官方 Messages API (特别是 Claude Code 使用场景)

---

## 执行摘要

本报告对 **claude-kiro** 项目与 **Anthropic 官方 Messages API** (特别是 Claude Code 使用场景) 进行了全面的差异分析。通过系统性研究官方API规范、Claude Code最佳实践,以及对当前实现的深入分析,识别出了多个关键差异和改进机会。

**关键发现**:
- ✅ **已实现**: 基础API端点、请求验证、流式响应、工具调用基础支持
- ⚠️ **部分支持**: 内容块结构、工具调用完整性、流式事件格式
- ❌ **缺失**: 扩展思考模式、图像输入、服务器工具、完整的stop_reason映射

**优先级建议**:
1. **P0 (关键)**: 修复工具调用流式响应格式,完善stop_reason映射
2. **P1 (重要)**: 实现完整内容块结构,添加扩展思考模式支持
3. **P2 (建议)**: 添加图像输入支持,实现多模态能力

---

## 1. API 端点对比分析

### 1.1 端点完整性

| 功能 | Anthropic 官方 | claude-kiro | 差异分析 |
|-----|--------------|-------------|----------|
| **Messages API** | `POST /v1/messages` | ✅ 已实现 | 完全兼容 |
| **流式 API** | `POST /v1/messages` (stream=true) | ⚠️ 部分实现 | 使用独立端点 `/v1/messages/stream` |
| **模型列表** | `GET /v1/models` | ✅ 已实现 | 通过 ModelController |
| **健康检查** | N/A | ✅ 已实现 | 额外功能 (HealthController) |

**差异详情**:
- **流式端点设计**: 官方API使用单一端点 `/v1/messages` 配合 `stream=true` 参数,而 claude-kiro 使用独立端点 `/v1/messages/stream`
- **影响**: 可能导致部分客户端库不兼容
- **建议**: 支持官方的参数化流式调用方式,同时保留现有端点以保持向后兼容

### 1.2 HTTP 头部验证

| Header | Anthropic 规范 | claude-kiro 实现 | 兼容性 |
|--------|--------------|----------------|-------|
| `x-api-key` | 必需 | ✅ 验证实现 | 完全兼容 |
| `anthropic-version` | 必需 | ✅ 验证实现 | 完全兼容 |
| `content-type` | `application/json` | ✅ Spring 自动处理 | 完全兼容 |

**实现代码** (AnthropicController.java:62-69):
```java
private void validateHeaders(String apiKey, String apiVersion) {
    if (!StringUtils.hasText(apiKey) || !properties.getApiKey().equals(apiKey)) {
        throw new IllegalStateException("invalid api key");
    }
    if (!StringUtils.hasText(apiVersion)) {
        throw new IllegalArgumentException("anthropic-version header is required");
    }
}
```

**评估**: ✅ 头部验证逻辑完整,符合官方规范

---

## 2. 请求参数对比分析

### 2.1 必需参数

| 参数 | Anthropic 规范 | claude-kiro | 验证逻辑 | 兼容性 |
|-----|--------------|-------------|---------|-------|
| `model` | 必需 | ✅ | 第72行验证 | ✅ |
| `messages` | 必需,至少一条 | ✅ | 第78-88行验证 | ✅ |
| `max_tokens` | 必需,正整数 | ✅ | 第75-76行验证 | ✅ |

### 2.2 可选参数对比

| 参数 | Anthropic 官方 | claude-kiro 实现 | 差异 |
|-----|--------------|----------------|-----|
| **system** | 支持,作为独立参数 | ✅ 支持 | 完全兼容 |
| **temperature** | 0.0-1.0 | ✅ 支持 | 完全兼容 |
| **top_p** | 0.0-1.0 | ✅ 支持 | 完全兼容 |
| **top_k** | 正整数 | ✅ 支持 | 完全兼容 |
| **stop_sequences** | 字符串数组 | ✅ 支持 | 完全兼容 |
| **tools** | 工具定义数组 | ✅ 支持 | 完全兼容 |
| **tool_choice** | 对象,含type字段 | ⚠️ 基础支持 | 仅验证type存在 |
| **metadata** | 自定义元数据 | ✅ 支持 | 完全兼容 |
| **stream** | 布尔值 | ⚠️ 部分支持 | 使用独立端点而非参数 |

**关键差异**:

#### tool_choice 实现不完整
**当前实现** (AnthropicController.java:92-96):
```java
if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
    if (!request.getToolChoice().containsKey("type")) {
        throw new IllegalArgumentException("tool_choice.type is required when tool_choice is provided");
    }
}
```

**官方规范**:
- 支持三种模式: `{"type": "auto"}`, `{"type": "any"}`, `{"type": "tool", "name": "tool_name"}`
- 需要验证 `type` 值的有效性
- 当 `type="tool"` 时,必须包含 `name` 字段

**建议改进**:
```java
if (request.getToolChoice() != null && !request.getToolChoice().isEmpty()) {
    String type = (String) request.getToolChoice().get("type");
    if (!StringUtils.hasText(type)) {
        throw new IllegalArgumentException("tool_choice.type is required");
    }
    if (!Arrays.asList("auto", "any", "tool").contains(type)) {
        throw new IllegalArgumentException("tool_choice.type must be 'auto', 'any', or 'tool'");
    }
    if ("tool".equals(type) && !request.getToolChoice().containsKey("name")) {
        throw new IllegalArgumentException("tool_choice.name is required when type is 'tool'");
    }
}
```

---

## 3. 消息内容块结构对比

### 3.1 ContentBlock 类型支持

| 内容块类型 | Anthropic 官方 | claude-kiro | 差异分析 |
|-----------|--------------|-------------|----------|
| **text** | ✅ 基础类型 | ✅ 完全支持 | ContentBlock.java:41-62 |
| **image** | ✅ 支持 (base64/url) | ❌ 未实现 | **缺失多模态能力** |
| **tool_use** | ✅ 助手工具调用 | ⚠️ 部分支持 | 结构存在但处理不完整 |
| **tool_result** | ✅ 用户工具结果 | ❌ 未实现 | **缺失工具结果回传** |
| **thinking** | ✅ 扩展思考模式 | ❌ 未实现 | **缺失扩展思考** |

**详细分析**:

#### 3.1.1 tool_use 内容块
**官方格式**:
```json
{
  "type": "tool_use",
  "id": "toolu_01A09q90qw90lq917835lq9",
  "name": "get_weather",
  "input": {"location": "San Francisco, CA"}
}
```

**当前实现** (AnthropicMessage.ContentBlock):
```java
public static class ContentBlock {
    private String type;
    private String text;
    private String id;      // ✅ 支持
    private String name;    // ✅ 支持
    private Map<String, Object> input;  // ✅ 支持
}
```

**评估**: ✅ 数据结构支持,但响应构建逻辑不完整

#### 3.1.2 tool_result 内容块 (❌ 缺失)
**官方格式**:
```json
{
  "type": "tool_result",
  "tool_use_id": "toolu_01A09q90qw90lq917835lq9",
  "content": "The weather in San Francisco is 70°F"
}
```

**影响**: 无法支持多轮工具调用交互,Claude Code 工具循环将失败

**建议**: 扩展 ContentBlock 支持 `tool_result` 类型

#### 3.1.3 thinking 内容块 (❌ 缺失)
**Claude Code 特定需求**: 扩展思考模式 (`--think`, `--think-hard`, `--ultrathink`)

**官方格式**:
```json
{
  "type": "thinking",
  "thinking": "Let me analyze this step by step..."
}
```

**影响**: Claude Code 的高级推理模式不可用

---

## 4. 工具调用实现对比

### 4.1 工具定义格式

| 组件 | Anthropic 官方 | claude-kiro | 兼容性 |
|-----|--------------|-------------|-------|
| **name** | 必需,唯一标识 | ✅ 支持 | 完全兼容 |
| **description** | 必需,详细说明 | ✅ 支持 | 完全兼容 |
| **input_schema** | 必需,JSON Schema | ✅ 支持 | 完全兼容 |
| **type** | 可选,"function" | ✅ 支持 | 完全兼容 |
| **function** | 可选,嵌套格式 | ✅ 支持 | 兼容 OpenAI 格式 |

**当前实现优势** (ToolDefinition.java:66-104):
```java
// 智能兼容多种格式
public String getEffectiveName() {
    if (name != null) return name;
    if (function != null && function.containsKey("name")) {
        return (String) function.get("name");
    }
    return null;
}
```

**评估**: ✅ 工具定义处理优秀,支持多种客户端格式

### 4.2 工具调用响应格式

#### 4.2.1 非流式响应

**当前实现问题** (ToolCall.java vs Anthropic 规范):

| 字段 | Anthropic 官方 | claude-kiro ToolCall | 差异 |
|-----|--------------|---------------------|-----|
| `type` | "tool_use" | "function" | ❌ 类型不匹配 |
| `id` | "toolu_xxx" | 任意ID | ⚠️ 格式不一致 |
| `name` | 直接字段 | 嵌套在 function 中 | ❌ 结构不同 |
| `input` | 直接字段,对象 | function.arguments,字符串 | ❌ 数据类型不同 |

**官方期望**:
```json
{
  "type": "tool_use",
  "id": "toolu_01A09q90qw90lq917835lq9",
  "name": "get_weather",
  "input": {"location": "San Francisco"}
}
```

**当前输出** (推测):
```json
{
  "type": "function",
  "id": "some-id",
  "function": {
    "name": "get_weather",
    "arguments": "{\"location\": \"San Francisco\"}"
  }
}
```

**兼容性影响**: ❌ **严重不兼容**,Claude Code 客户端将无法识别工具调用

#### 4.2.2 流式响应格式

**官方 SSE 事件序列** (工具调用场景):
```
event: message_start
data: {"type": "message_start", "message": {...}}

event: content_block_start
data: {"type": "content_block_start", "index": 0, "content_block": {"type": "tool_use", "id": "toolu_xxx", "name": "get_weather"}}

event: content_block_delta
data: {"type": "content_block_delta", "index": 0, "delta": {"type": "input_json_delta", "partial_json": "{\"location\": \""}}

event: content_block_delta
data: {"type": "content_block_delta", "index": 0, "delta": {"type": "input_json_delta", "partial_json": "San Francisco\""}}

event: content_block_stop
data: {"type": "content_block_stop", "index": 0}

event: message_delta
data: {"type": "message_delta", "delta": {"stop_reason": "tool_use"}}

event: message_stop
data: {"type": "message_stop"}
```

**当前实现** (推测基于 KiroService.java:222-300):
- ✅ 支持事件解析和聚合
- ❌ 输出格式不符合官方 SSE 规范
- ❌ 缺少 `content_block_start/delta/stop` 事件
- ❌ 缺少 `input_json_delta` 增量输出

**影响**: Claude Code 流式工具调用将失败

---

## 5. 响应格式对比

### 5.1 响应字段完整性

| 字段 | Anthropic 官方 | claude-kiro | 差异 |
|-----|--------------|-------------|-----|
| **id** | 必需,"msg_xxx" | ✅ 实现 | UUID格式 |
| **type** | 必需,"message" | ✅ 实现 | 完全兼容 |
| **role** | 必需,"assistant" | ✅ 实现 | 完全兼容 |
| **content** | 必需,ContentBlock数组 | ✅ 实现 | 结构兼容 |
| **model** | 必需 | ✅ 实现 | 完全兼容 |
| **stop_reason** | 必需 | ⚠️ 部分实现 | 映射不完整 |
| **stop_sequence** | 可选 | ✅ 实现 | 完全兼容 |
| **usage** | 必需 | ✅ 实现 | 完全兼容 |

### 5.2 stop_reason 映射分析

**官方支持的 stop_reason 值**:
- `end_turn` - 正常完成
- `max_tokens` - 达到token限制
- `stop_sequence` - 遇到停止序列
- `tool_use` - 请求工具调用
- `content_filter` - 内容被过滤 (安全策略)

**当前实现** (需要验证):
- 基础字段支持,但可能缺少从 Kiro 到 Anthropic 格式的准确映射
- **缺失**: `tool_use` stop_reason 的明确设置逻辑

**建议改进** (KiroService.mapResponse):
```java
// 根据工具调用设置正确的 stop_reason
if (!toolCalls.isEmpty()) {
    response.setStopReason("tool_use");
} else if (/* Kiro 表示达到max_tokens */) {
    response.setStopReason("max_tokens");
} else if (/* Kiro 表示遇到stop_sequence */) {
    response.setStopReason("stop_sequence");
    response.setStopSequence(/* 实际停止序列 */);
} else {
    response.setStopReason("end_turn");
}
```

---

## 6. 流式响应实现对比

### 6.1 SSE 事件类型

| 事件类型 | Anthropic 官方 | claude-kiro | 差异分析 |
|---------|--------------|-------------|----------|
| **message_start** | ✅ 必需 | ❓ 未确认 | 需验证 |
| **content_block_start** | ✅ 必需 | ❓ 未确认 | 需验证 |
| **content_block_delta** | ✅ 必需 | ❓ 未确认 | 需验证 |
| **content_block_stop** | ✅ 必需 | ❓ 未确认 | 需验证 |
| **message_delta** | ✅ 必需 | ❓ 未确认 | 需验证 |
| **message_stop** | ✅ 必需 | ❓ 未确认 | 需验证 |
| **ping** | ✅ 可选 | ❌ 未实现 | 保活机制缺失 |
| **error** | ✅ 可选 | ❌ 未实现 | 错误传递缺失 |

**官方事件序列示例** (文本生成):
```
event: message_start
data: {"type":"message_start","message":{"id":"msg_xxx","type":"message","role":"assistant","content":[],"model":"claude-3-5-sonnet-20241022","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":1}}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":15}}

event: message_stop
data: {"type":"message_stop"}
```

**当前实现状态**:
- 代码中存在 `AnthropicStreamChunk` 类,但实际输出格式未验证
- 需要确认是否完整实现了所有事件类型
- 需要确认事件数据结构是否符合官方规范

### 6.2 流式工具调用特殊处理

**官方要求**:
1. `content_block_start` 包含初始工具信息 (id, name)
2. `content_block_delta` 使用 `input_json_delta` 增量传输 JSON
3. 每个 delta 事件的 `partial_json` 必须是有效的 JSON 片段
4. `message_delta` 设置 `stop_reason: "tool_use"`

**当前实现分析** (KiroService.java:232-281):
```java
// 当前是聚合所有事件后一次性返回
Map<String, ToolCallBuilder> toolCallBuilders = new HashMap<>();
// ... 聚合逻辑 ...
if (event.hasNonNull("stop") && event.get("stop").asBoolean()) {
    ToolCall toolCall = builder.build();
    toolCalls.add(toolCall);
}
```

**问题**:
- ✅ 正确聚合了 Kiro 的流式事件
- ❌ 未按 Anthropic 格式重新流式输出
- ❌ 非流式场景可用,流式场景不兼容

---

## 7. Claude Code 特定需求分析

### 7.1 上下文窗口管理

**Claude Code 要求**:
- API 模式: 1M token 上下文窗口
- 订阅模式: 200K token 上下文窗口

**当前实现**:
- ❌ 未检测到上下文窗口限制逻辑
- ❌ 未实现上下文压缩或分块策略
- ⚠️ 依赖 Kiro 后端的限制,可能与官方不一致

**建议**:
```java
// 在 AnthropicController 中添加
private static final int MAX_CONTEXT_TOKENS = 1_000_000;

private void validateContextWindow(AnthropicChatRequest request) {
    int estimatedTokens = estimateTokenCount(request);
    if (estimatedTokens > MAX_CONTEXT_TOKENS) {
        throw new IllegalArgumentException(
            "Request exceeds maximum context window of " + MAX_CONTEXT_TOKENS + " tokens"
        );
    }
}
```

### 7.2 CLAUDE.md 项目配置支持

**Claude Code 最佳实践**:
- 读取项目根目录的 `CLAUDE.md` 文件
- 包含 bash 命令、代码风格、测试指令等配置
- 自动注入到系统提示中

**当前实现**:
- ❌ 未检测到 CLAUDE.md 读取逻辑
- ❌ 未自动扩展系统提示

**影响**: Claude Code 用户无法使用项目配置功能

**建议实现**:
```java
// 在 KiroService 中添加
private String loadClaudeMdIfExists(String projectRoot) {
    Path claudeMd = Paths.get(projectRoot, "CLAUDE.md");
    if (Files.exists(claudeMd)) {
        try {
            String content = Files.readString(claudeMd);
            return "\n\n# Project Configuration\n" + content;
        } catch (IOException e) {
            log.warn("Failed to read CLAUDE.md: {}", e.getMessage());
        }
    }
    return "";
}

// 在构建系统提示时追加
String systemPrompt = baseSystemPrompt + loadClaudeMdIfExists(request.getProjectRoot());
```

### 7.3 MCP (Model Context Protocol) 支持

**Claude Code 架构**:
- 支持 MCP 服务器扩展工具能力
- 通过 `mcp__` 前缀标识 MCP 工具
- 需要特殊的工具调用路由逻辑

**当前实现**:
- ❌ 未检测到 MCP 协议支持
- ❌ 未处理 `mcp__` 前缀工具

**影响**: 第三方 MCP 扩展不可用

### 7.4 多模态输入支持

**Claude Code 能力**:
- 支持图像输入 (截图、设计稿分析)
- base64 编码或 URL 引用
- 用于视觉迭代和 UI 验证

**当前实现**:
- ❌ ContentBlock 不支持 image 类型
- ❌ 请求验证不处理图像字段

**建议扩展** (AnthropicMessage.ContentBlock):
```java
public static class ContentBlock {
    private String type;
    private String text;

    // 添加图像支持
    private ImageSource source;  // 新增

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageSource {
        private String type;  // "base64" 或 "url"
        private String media_type;  // "image/jpeg", "image/png", etc.
        private String data;  // base64 数据或 URL
    }
}
```

---

## 8. 安全性和错误处理对比

### 8.1 错误响应格式

**Anthropic 官方错误格式**:
```json
{
  "type": "error",
  "error": {
    "type": "invalid_request_error",
    "message": "max_tokens must be a positive integer"
  }
}
```

**当前实现** (GlobalExceptionHandler):
- ⚠️ 使用 Spring Boot 默认错误格式
- ❌ 不符合 Anthropic 官方格式

**建议改进**:
```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<Map<String, Object>> handleInvalidRequest(IllegalArgumentException ex) {
    Map<String, Object> error = Map.of(
        "type", "error",
        "error", Map.of(
            "type", "invalid_request_error",
            "message", ex.getMessage()
        )
    );
    return ResponseEntity.badRequest().body(error);
}
```

### 8.2 速率限制和配额管理

**官方要求**:
- 返回 `429 Too Many Requests`
- 包含 `Retry-After` 头部
- 错误消息中包含配额信息

**当前实现**:
- ❌ 未检测到速率限制逻辑
- ❌ 依赖 Kiro 后端限制,无本地控制

**建议**: 实现基于令牌桶的速率限制器

---

## 9. 性能和可观测性对比

### 9.1 日志和追踪

**Claude Code 最佳实践**:
- 详细的请求/响应日志
- 工具调用追踪
- 性能指标收集

**当前实现**:
- ✅ 基础日志记录 (KiroService 有详细日志)
- ⚠️ 缺少结构化日志和追踪ID
- ❌ 缺少性能指标 (延迟、吞吐量)

**建议**:
```java
// 添加请求追踪
String requestId = UUID.randomUUID().toString();
MDC.put("request_id", requestId);
log.info("Processing request: {}", requestId);

// 添加性能指标
long startTime = System.currentTimeMillis();
// ... 处理请求 ...
long duration = System.currentTimeMillis() - startTime;
log.info("Request {} completed in {}ms", requestId, duration);
```

### 9.2 健康检查和监控

**当前实现**:
- ✅ HealthController 提供基础健康检查
- ✅ Spring Actuator 集成

**建议增强**:
- 添加依赖服务健康检查 (Kiro gateway 可用性)
- 添加就绪检查 (readiness probe)
- 添加存活检查 (liveness probe)

---

## 10. 关键差异汇总

### 10.1 严重不兼容项 (P0 - 必须修复)

| # | 差异项 | 影响 | 当前状态 | 建议优先级 |
|---|-------|-----|---------|----------|
| 1 | **工具调用响应格式** | Claude Code 工具循环失败 | ❌ 格式不匹配 | 🔴 P0 |
| 2 | **流式工具调用事件** | 流式场景工具调用不可用 | ❌ 未实现 | 🔴 P0 |
| 3 | **stop_reason 映射** | 客户端无法正确判断停止原因 | ⚠️ 不完整 | 🔴 P0 |
| 4 | **tool_result 内容块** | 多轮工具调用中断 | ❌ 未实现 | 🔴 P0 |

### 10.2 重要兼容性问题 (P1 - 应该修复)

| # | 差异项 | 影响 | 当前状态 | 建议优先级 |
|---|-------|-----|---------|----------|
| 5 | **流式端点设计** | 部分客户端库不兼容 | ⚠️ 使用独立端点 | 🟡 P1 |
| 6 | **tool_choice 验证** | 工具选择模式受限 | ⚠️ 验证不完整 | 🟡 P1 |
| 7 | **错误响应格式** | 错误处理不统一 | ⚠️ 非官方格式 | 🟡 P1 |
| 8 | **thinking 内容块** | 扩展思考模式不可用 | ❌ 未实现 | 🟡 P1 |

### 10.3 建议改进项 (P2 - 可选增强)

| # | 差异项 | 影响 | 当前状态 | 建议优先级 |
|---|-------|-----|---------|----------|
| 9 | **图像输入支持** | 多模态场景受限 | ❌ 未实现 | 🟢 P2 |
| 10 | **CLAUDE.md 支持** | 项目配置不可用 | ❌ 未实现 | 🟢 P2 |
| 11 | **MCP 协议支持** | 第三方扩展不可用 | ❌ 未实现 | 🟢 P2 |
| 12 | **上下文窗口管理** | 可能超出限制 | ❌ 未实现 | 🟢 P2 |
| 13 | **速率限制** | 无本地配额控制 | ❌ 未实现 | 🟢 P2 |

---

## 11. 完善计划和实施路线图

### 阶段 1: 关键兼容性修复 (P0 - 1-2 周)

#### 任务 1.1: 修复工具调用响应格式
**目标**: 使工具调用输出符合 Anthropic 官方格式

**实施步骤**:
1. 修改 `AnthropicMessage.ContentBlock` 支持 `tool_use` 类型
2. 更新 `KiroService.mapResponse()` 构建正确的工具调用内容块
3. 修复 `stop_reason` 设置逻辑,工具调用时设置为 `"tool_use"`

**关键代码变更**:
```java
// AnthropicMessage.ContentBlock 添加工具调用专用字段
if (!toolCalls.isEmpty()) {
    for (ToolCall toolCall : toolCalls) {
        ContentBlock block = new ContentBlock();
        block.setType("tool_use");
        block.setId(toolCall.getId());
        block.setName(toolCall.getFunction().getName());
        block.setInput(parseJsonToMap(toolCall.getFunction().getArguments()));
        response.addContentBlock(block);
    }
    response.setStopReason("tool_use");
}
```

**验证方式**:
- 运行 E2E 工具调用测试
- 验证响应格式与官方 API 一致
- 测试 Claude Code 客户端集成

#### 任务 1.2: 实现流式工具调用事件
**目标**: 流式场景下正确输出工具调用事件序列

**实施步骤**:
1. 扩展 `AnthropicStreamChunk` 支持所有事件类型
2. 实现事件序列生成器 (EventSequenceBuilder)
3. 更新 `KiroService.streamCompletion()` 输出正确的 SSE 事件

**关键逻辑**:
```java
// 伪代码
Flux<String> generateToolCallEvents(ToolCall toolCall) {
    return Flux.just(
        // 1. content_block_start
        sseEvent("content_block_start", Map.of(
            "type", "content_block_start",
            "index", currentIndex,
            "content_block", Map.of(
                "type", "tool_use",
                "id", toolCall.getId(),
                "name", toolCall.getName()
            )
        )),

        // 2. content_block_delta (分块输出 input JSON)
        ...chunkJson(toolCall.getInput()).map(chunk ->
            sseEvent("content_block_delta", Map.of(
                "type", "content_block_delta",
                "index", currentIndex,
                "delta", Map.of(
                    "type", "input_json_delta",
                    "partial_json", chunk
                )
            ))
        ),

        // 3. content_block_stop
        sseEvent("content_block_stop", Map.of(
            "type", "content_block_stop",
            "index", currentIndex
        ))
    );
}
```

#### 任务 1.3: 实现 tool_result 内容块支持
**目标**: 支持用户回传工具执行结果

**实施步骤**:
1. 扩展 `AnthropicMessage.ContentBlock` 支持 `tool_result` 类型
2. 添加 `tool_use_id` 和 `content` 字段
3. 在请求处理中识别并转发工具结果到 Kiro

**数据结构**:
```java
public static class ContentBlock {
    // ... 现有字段 ...

    // tool_result 专用字段
    @JsonProperty("tool_use_id")
    private String toolUseId;

    private Object content;  // 可以是字符串或复杂对象
}
```

### 阶段 2: 重要兼容性改进 (P1 - 2-3 周)

#### 任务 2.1: 统一流式端点
**目标**: 支持官方的参数化流式调用

**实施步骤**:
1. 修改 `/v1/messages` 端点同时支持流式和非流式
2. 根据 `stream` 参数返回不同响应类型
3. 保留 `/v1/messages/stream` 以保持向后兼容

**代码示例**:
```java
@PostMapping
public Object createMessage(
    @RequestHeader(name = "x-api-key", required = false) String apiKey,
    @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
    @RequestBody AnthropicChatRequest request) {

    validateHeaders(apiKey, apiVersion);
    validateRequest(request);

    if (Boolean.TRUE.equals(request.getStream())) {
        // 返回 Flux<String> 流式响应
        return kiroService.streamCompletion(request);
    } else {
        // 返回 Mono<ResponseEntity<AnthropicChatResponse>> 非流式响应
        return kiroService.createCompletion(request)
            .map(response -> ResponseEntity.ok()
                .header("anthropic-version", apiVersion)
                .body(response));
    }
}
```

#### 任务 2.2: 完善 tool_choice 验证
**目标**: 严格验证工具选择参数

**实施代码** (已在第2节提供)

#### 任务 2.3: 实现扩展思考模式
**目标**: 支持 Claude Code 的 `--think` 模式

**实施步骤**:
1. 扩展 `ContentBlock` 支持 `thinking` 类型
2. 在系统提示中添加思考模式触发器
3. 解析 Kiro 响应中的思考内容

**请求处理**:
```java
// 检测是否启用思考模式 (通过特殊的 metadata 标记)
if (request.getMetadata() != null &&
    "extended_thinking".equals(request.getMetadata().get("thinking_mode"))) {

    // 在系统提示中添加思考指令
    String thinkingPrompt = "\n\nBefore responding, think step-by-step about the problem. " +
                           "Output your thinking process in a <thinking> block.";
    request.setSystem(appendToSystem(request.getSystem(), thinkingPrompt));
}

// 响应解析
if (/* Kiro 返回思考内容 */) {
    ContentBlock thinkingBlock = new ContentBlock();
    thinkingBlock.setType("thinking");
    thinkingBlock.setThinking(thinkingContent);
    response.addContentBlock(thinkingBlock);
}
```

#### 任务 2.4: 统一错误响应格式
**目标**: 所有错误使用 Anthropic 官方格式

**实施步骤**:
1. 创建 `AnthropicErrorResponse` 类
2. 更新 `GlobalExceptionHandler` 使用统一格式
3. 映射所有异常类型到官方错误类型

**错误类型映射**:
```java
private String mapToAnthropicErrorType(Exception ex) {
    if (ex instanceof IllegalArgumentException) {
        return "invalid_request_error";
    } else if (ex instanceof IllegalStateException && ex.getMessage().contains("api key")) {
        return "authentication_error";
    } else if (/* 速率限制异常 */) {
        return "rate_limit_error";
    } else if (/* Kiro 服务错误 */) {
        return "api_error";
    } else {
        return "internal_server_error";
    }
}
```

### 阶段 3: 增强功能实现 (P2 - 3-4 周)

#### 任务 3.1: 添加图像输入支持
**目标**: 实现多模态能力

**实施步骤**:
1. 扩展 `ContentBlock` 支持 image 类型和 ImageSource
2. 实现图像验证 (格式、大小限制)
3. 转换图像数据到 Kiro 支持的格式

#### 任务 3.2: CLAUDE.md 配置支持
**目标**: 支持项目级配置

**实施步骤**:
1. 实现配置文件读取器 (ClaudeMdLoader)
2. 在请求处理时自动加载并注入到系统提示
3. 支持配置继承 (全局 → 项目 → 目录)

#### 任务 3.3: MCP 协议基础支持
**目标**: 支持第三方工具扩展

**实施步骤**:
1. 实现 MCP 工具注册表
2. 添加工具路由逻辑 (识别 `mcp__` 前缀)
3. 实现工具调用代理到 MCP 服务器

#### 任务 3.4: 上下文窗口管理
**目标**: 防止上下文溢出

**实施步骤**:
1. 实现 token 计数器 (tiktoken 或近似算法)
2. 添加请求预验证
3. 实现上下文压缩策略 (可选)

#### 任务 3.5: 速率限制和配额
**目标**: 本地流量控制

**实施步骤**:
1. 实现令牌桶算法限流器
2. 添加配额跟踪 (每用户/每API密钥)
3. 返回正确的 429 响应和重试提示

### 阶段 4: 可观测性和运维 (持续)

#### 任务 4.1: 结构化日志
- 添加请求追踪 ID
- 实现 MDC 上下文传递
- 输出结构化 JSON 日志

#### 任务 4.2: 性能监控
- 集成 Micrometer
- 添加关键指标 (延迟、吞吐量、错误率)
- 实现自定义仪表板

#### 任务 4.3: 健康检查增强
- 添加依赖服务健康检查
- 实现就绪和存活探针
- 集成 Kubernetes 健康检查

---

## 12. 测试策略

### 12.1 兼容性测试套件

**创建测试集**:
1. **官方 API 对比测试**
   - 使用 Anthropic 官方客户端库发起请求
   - 对比响应格式和内容
   - 验证所有字段和数据类型

2. **Claude Code 集成测试**
   - 模拟 Claude Code CLI 请求
   - 测试工具调用循环
   - 验证流式响应处理

3. **边界条件测试**
   - 超大上下文窗口
   - 复杂工具定义
   - 并发工具调用

### 12.2 回归测试

**现有测试增强**:
- 扩展 `ToolCallE2ETest` 覆盖新的工具调用格式
- 添加流式工具调用专项测试
- 验证向后兼容性

### 12.3 性能测试

**压力测试场景**:
- 高并发请求
- 大 payload 处理
- 长时间流式连接

---

## 13. 风险评估和缓解策略

### 13.1 主要风险

| 风险 | 影响 | 概率 | 缓解策略 |
|-----|-----|-----|---------|
| **Kiro 格式变化** | 🔴 高 | 🟡 中 | 实现适配层,隔离格式转换逻辑 |
| **性能下降** | 🟡 中 | 🟡 中 | 性能基准测试,优化热路径 |
| **向后不兼容** | 🟡 中 | 🟢 低 | API 版本控制,保留旧端点 |
| **工具调用失败** | 🔴 高 | 🔴 高 | 优先修复 P0 项,充分测试 |

### 13.2 回滚计划

**快速回滚机制**:
1. 保留旧版本端点作为 fallback
2. 使用特性开关控制新功能启用
3. 实现灰度发布策略

---

## 14. 成功指标

### 14.1 兼容性指标

- ✅ 所有 E2E 测试通过率: 100%
- ✅ 官方客户端集成成功: 100%
- ✅ Claude Code 功能可用性: 100%

### 14.2 性能指标

- ⚡ P95 延迟: < 200ms (非流式)
- ⚡ 流式首字节时间: < 100ms
- ⚡ 并发支持: 100+ QPS

### 14.3 质量指标

- 🐛 关键 bug 数量: 0
- 📊 代码覆盖率: > 80%
- 📝 API 文档完整性: 100%

---

## 15. 参考资料

### 15.1 官方文档
- [Anthropic Messages API](https://docs.claude.com/en/api/messages)
- [Tool Use Overview](https://docs.claude.com/en/docs/agents-and-tools/tool-use/overview)
- [Claude Code Best Practices](https://www.anthropic.com/engineering/claude-code-best-practices)

### 15.2 相关标准
- [Server-Sent Events (SSE) Specification](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [JSON Schema](https://json-schema.org/)
- [Model Context Protocol (MCP)](https://modelcontextprotocol.io/)

### 15.3 技术资源
- Spring WebFlux Reactive Programming
- Jackson JSON Processing
- Project Reactor Documentation

---

## 附录 A: 完整实施检查清单

### P0 任务 (必须完成)
- [ ] 修复工具调用响应格式
- [ ] 实现流式工具调用事件序列
- [ ] 完善 stop_reason 映射逻辑
- [ ] 实现 tool_result 内容块支持
- [ ] 运行并通过所有 E2E 工具调用测试

### P1 任务 (应该完成)
- [ ] 统一流式端点设计
- [ ] 完善 tool_choice 参数验证
- [ ] 统一错误响应格式
- [ ] 实现 thinking 内容块支持
- [ ] 创建 Claude Code 集成测试套件

### P2 任务 (建议完成)
- [ ] 添加图像输入支持
- [ ] 实现 CLAUDE.md 配置加载
- [ ] 添加 MCP 协议基础支持
- [ ] 实现上下文窗口管理
- [ ] 添加速率限制和配额控制
- [ ] 增强可观测性 (日志、监控、追踪)

### 持续改进
- [ ] 性能优化和基准测试
- [ ] 文档更新和维护
- [ ] 安全审计和加固
- [ ] 用户反馈收集和迭代

---

**报告结束**

*本报告由 AI 助手基于官方文档研究和代码分析生成,建议在实施前进行详细的技术评审和验证。*
