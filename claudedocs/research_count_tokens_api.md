# Anthropic API `/v1/messages/count_tokens` 接口研究报告

**研究日期**: 2025-10-10
**官方文档**: https://docs.anthropic.com/en/api/messages-count-tokens
**置信度**: 高 (官方文档直接来源)

---

## 📋 执行摘要

`/v1/messages/count_tokens` 是 Anthropic Claude API 提供的 Token 计数接口，用于在实际发送消息前预先计算请求的 token 消耗量。该接口接受与 `/v1/messages` 几乎相同的请求参数，但不会真正调用模型，仅返回 token 统计信息。

**核心作用**: 成本预估、请求优化、上下文窗口管理

---

## 🔍 接口详细规范

### 1. 基本信息

| 属性 | 值 |
|-----|---|
| **Method** | `POST` |
| **Endpoint** | `/v1/messages/count_tokens` |
| **Content-Type** | `application/json` |
| **官方文档** | https://docs.anthropic.com/en/api/messages-count-tokens |

---

### 2. 请求参数 (Request Body)

#### 2.1 必需参数

| 参数 | 类型 | 说明 | 示例 |
|-----|------|------|------|
| **`model`** | `string` | 模型标识符，长度 1-256 字符 | `"claude-sonnet-4-20250514"` |
| **`messages`** | `object[]` | 消息数组，最多 100,000 条 | `[{"role": "user", "content": "Hello"}]` |

#### 2.2 可选参数

| 参数 | 类型 | 说明 |
|-----|------|------|
| **`system`** | `string` 或 `object[]` | 系统提示 |
| **`tools`** | `object[]` | 工具定义数组 |
| **`tool_choice`** | `object` | 工具选择配置 |
| **`thinking`** | `object` | 扩展思考配置 (budget_tokens ≥ 1024) |
| **`mcp_servers`** | `object[]` | MCP 服务器配置 |

#### 2.3 消息结构 (messages)

```json
{
  "role": "user" | "assistant",  // 必需
  "content": "string" | ContentBlock[]  // 必需
}
```

**ContentBlock 类型**:
- `text`: 文本内容
- `image`: 图片内容
- `document`: 文档内容 (PDF 等)
- `tool_use`: 工具使用
- `tool_result`: 工具结果

#### 2.4 工具定义结构 (tools)

```json
{
  "name": "string",           // 必需, 1-128 字符
  "input_schema": {           // 必需, JSON Schema
    "type": "object",
    "properties": {...},
    "required": [...]
  },
  "type": "custom",           // 可选
  "description": "string",    // 可选但强烈推荐
  "cache_control": {          // 可选
    "type": "ephemeral",
    "ttl": "5m" | "1h"
  }
}
```

---

### 3. 请求头 (Request Headers)

| Header | 类型 | 必需 | 说明 |
|--------|------|------|------|
| **`x-api-key`** | `string` | ✅ | API 密钥 |
| **`anthropic-version`** | `string` | ✅ | API 版本，如 `"2023-06-01"` |
| **`anthropic-beta`** | `string[]` | ❌ | Beta 功能列表，逗号分隔 |
| **`content-type`** | `string` | ✅ | 固定为 `"application/json"` |

---

### 4. 响应格式 (Response - 200 OK)

#### 4.1 成功响应

```json
{
  "input_tokens": 2095
}
```

#### 4.2 响应字段

| 字段 | 类型 | 说明 |
|-----|------|------|
| **`input_tokens`** | `integer` | 总 token 数，包含 messages、system、tools 的所有 token |

**计数范围**:
- 用户消息内容
- 系统提示
- 工具定义
- 图片和文档（转换为 token）
- 扩展思考预算（如果配置）

---

## 🎯 接口作用与使用场景

### 1. 核心作用

#### 1.1 成本预估
```
场景: 在发送请求前计算 API 调用成本
价值: 避免意外的高额费用
```

**计算公式**:
```
成本 = input_tokens × 输入价格 + estimated_output_tokens × 输出价格
```

**模型定价示例** (参考官方定价):
- Claude Sonnet 4.5: 输入 $3/MTok, 输出 $15/MTok
- Claude Haiku 3.5: 输入 $0.80/MTok, 输出 $4/MTok

#### 1.2 上下文窗口管理
```
场景: 确保请求不超过模型的上下文限制
限制:
  - Claude Sonnet 4.x: 200K tokens (标准), 1M tokens (beta)
  - Claude Haiku 3.5: 200K tokens
```

#### 1.3 请求优化
```
场景: 根据 token 数优化提示词或消息结构
优化策略:
  - 精简系统提示
  - 压缩历史对话
  - 优化工具定义描述
```

#### 1.4 批量处理决策
```
场景: 决定是否拆分大型请求为多个小请求
判断: if (input_tokens + max_tokens > context_limit) { split_request() }
```

---

### 2. 典型使用场景

#### 场景 A: 对话应用的上下文管理
```javascript
// 在添加新消息前检查 token 数
const count = await countTokens({
  model: "claude-sonnet-4-5",
  messages: [...conversationHistory, newUserMessage]
});

if (count.input_tokens > 180000) {
  // 触发历史压缩或摘要
  conversationHistory = summarizeHistory(conversationHistory);
}
```

#### 场景 B: 成本控制中间件
```javascript
async function costAwareRequest(request) {
  const count = await countTokens(request);
  const estimatedCost = calculateCost(count.input_tokens, request.max_tokens);

  if (estimatedCost > USER_BUDGET) {
    throw new Error(`Request exceeds budget: $${estimatedCost}`);
  }

  return await createMessage(request);
}
```

#### 场景 C: 提示词工程优化
```python
# 比较不同提示词的 token 效率
prompts = [prompt_v1, prompt_v2, prompt_v3]
token_counts = [count_tokens(p) for p in prompts]

# 选择 token 数最少的版本
best_prompt = prompts[argmin(token_counts)]
```

#### 场景 D: 工具定义优化
```python
# 测试工具描述的详细程度对 token 的影响
tools_minimal = [{"name": "search", "description": "Search"}]
tools_detailed = [{"name": "search", "description": "Comprehensive web search..."}]

minimal_tokens = count_tokens(messages, tools=tools_minimal)
detailed_tokens = count_tokens(messages, tools=tools_detailed)

# 权衡: 详细描述提升准确性 vs token 成本
```

---

## 🔄 与当前实现的对比

### 当前实现 (TokenCountController.java)

```java
@PostMapping(value = "/count_tokens", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<Map<String, Object>> countTokens(
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody(required = false) Map<String, Object> body) {
    int tokens = ThreadLocalRandom.current().nextInt(20, 501);  // ❌ 随机数
    log.info("[ClaudeCode] count_tokens request, version={}, tokens={}", apiVersion, tokens);
    return ResponseEntity.ok(Map.of(
            "type", "token_count",     // ❌ 多余字段
            "input_tokens", tokens
    ));
}
```

### ⚠️ 当前实现的问题

| 问题 | 说明 | 影响 |
|-----|------|------|
| **1. Mock 实现** | 返回随机数 (20-501)，未实际计算 | ❌ 无法用于成本预估和优化 |
| **2. 未解析请求体** | `@RequestBody` 接收但未使用 | ❌ 忽略了所有输入参数 |
| **3. 响应格式不一致** | 多了 `"type": "token_count"` 字段 | ⚠️ 与官方格式不兼容 |
| **4. 缺少验证** | 未验证 `model`、`messages` 等必需参数 | ⚠️ 可能导致客户端误用 |
| **5. 未考虑复杂内容** | 未处理 tools、system、images 等 | ❌ 计数不准确 |

---

## ✅ 正确实现方案

### 方案 1: 集成现有 TokenCounter 服务

**当前项目已有** `TokenCounter` 服务 (`AnthropicController.java:274`)，可直接复用：

```java
@RestController
@RequestMapping("/v1/messages")
public class TokenCountController {

    private static final Logger log = LoggerFactory.getLogger(TokenCountController.class);
    private final TokenCounter tokenCounter;
    private final AppProperties properties;

    public TokenCountController(TokenCounter tokenCounter, AppProperties properties) {
        this.tokenCounter = tokenCounter;
        this.properties = properties;
    }

    @PostMapping(value = "/count_tokens", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> countTokens(
            @RequestHeader(name = "x-api-key", required = false) String apiKey,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
            @RequestBody AnthropicChatRequest request) {

        // 1. 认证验证 (与 AnthropicController 保持一致)
        String resolvedApiKey = resolveApiKey(apiKey, authorization);
        validateHeaders(resolvedApiKey, apiVersion);

        // 2. 基础请求验证
        validateRequest(request);

        // 3. 计算 token 数
        int inputTokens = tokenCounter.countTokens(request);

        log.info("[TokenCount] model={}, input_tokens={}", request.getModel(), inputTokens);

        // 4. 返回符合 Anthropic 格式的响应
        return ResponseEntity.ok(Map.of("input_tokens", inputTokens));
    }

    private String resolveApiKey(String apiKey, String authorization) {
        if (StringUtils.hasText(apiKey)) return apiKey;
        if (StringUtils.hasText(authorization)) {
            return authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : authorization.trim();
        }
        return null;
    }

    private void validateHeaders(String apiKey, String apiVersion) {
        if (!StringUtils.hasText(apiKey) || !properties.getApiKey().equals(apiKey)) {
            throw new IllegalStateException("invalid api key");
        }
        if (!StringUtils.hasText(apiVersion)) {
            throw new IllegalArgumentException("anthropic-version header is required");
        }
    }

    private void validateRequest(AnthropicChatRequest request) {
        if (!StringUtils.hasText(request.getModel())) {
            throw new IllegalArgumentException("model is required");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages must contain at least one entry");
        }
    }
}
```

### 方案 2: 调用 Kiro 后端接口

如果 Kiro 网关也提供 token 计数接口，可以代理转发：

```java
@PostMapping(value = "/count_tokens", produces = MediaType.APPLICATION_JSON_VALUE)
public Mono<Map<String, Object>> countTokens(
        @RequestHeader(name = "x-api-key", required = false) String apiKey,
        @RequestHeader(name = "anthropic-version", required = false) String apiVersion,
        @RequestBody AnthropicChatRequest request) {

    validateHeaders(resolveApiKey(apiKey, null), apiVersion);
    validateRequest(request);

    // 转发到 Kiro 的 token 计数接口
    return kiroService.countTokens(request)
        .map(count -> Map.of("input_tokens", count));
}
```

---

## 📊 Token 计数实现细节

### 计数逻辑

#### 1. 文本内容
```
算法: Anthropic 使用 Claude 的 tokenizer (基于 BPE)
粗略估算: 1 token ≈ 4 字符 (英文)
          1 token ≈ 1-2 字符 (中文)
```

#### 2. 系统提示
```java
// system 可以是 string 或 ContentBlock[]
if (request.getSystem() != null) {
    if (request.getSystem() instanceof String) {
        tokens += tokenize((String) request.getSystem()).size();
    } else {
        for (ContentBlock block : request.getSystem()) {
            tokens += tokenize(block.getText()).size();
        }
    }
}
```

#### 3. 工具定义
```java
// 工具定义的 token 计入总数
for (ToolDefinition tool : request.getTools()) {
    tokens += tokenize(tool.getName()).size();
    tokens += tokenize(tool.getDescription()).size();
    tokens += tokenize(serializeSchema(tool.getInputSchema())).size();
}

// 工具选择的额外 token (见官方定价文档)
if (request.getToolChoice() != null) {
    String type = request.getToolChoice().get("type");
    if ("any".equals(type) || "tool".equals(type)) {
        tokens += 313;  // Claude Sonnet 4.x
    } else if ("auto".equals(type) || "none".equals(type)) {
        tokens += 346;  // Claude Sonnet 4.x
    }
}
```

#### 4. 图片内容
```
base64 编码的图片会被转换为 token
计算方式: 根据图片尺寸和格式
粗略估算:
  - 低分辨率图片: ~85 tokens
  - 高分辨率图片: ~1600 tokens
```

#### 5. 文档内容 (PDF)
```
PDF 会被解析为文本和图片
token 数 = 文本 tokens + 图片 tokens
```

---

## 🧪 测试示例

### 测试用例 1: 简单文本消息

**请求**:
```bash
curl https://api.anthropic.com/v1/messages/count_tokens \
  --header "x-api-key: $ANTHROPIC_API_KEY" \
  --header "anthropic-version: 2023-06-01" \
  --header "content-type: application/json" \
  --data '{
    "model": "claude-sonnet-4-5",
    "messages": [
      {"role": "user", "content": "Hello, world"}
    ]
  }'
```

**期望响应**:
```json
{
  "input_tokens": 10
}
```

---

### 测试用例 2: 包含系统提示和工具

**请求**:
```json
{
  "model": "claude-sonnet-4-5",
  "system": "You are a helpful assistant specialized in weather forecasts.",
  "messages": [
    {"role": "user", "content": "What's the weather in San Francisco?"}
  ],
  "tools": [
    {
      "name": "get_weather",
      "description": "Get the current weather in a given location",
      "input_schema": {
        "type": "object",
        "properties": {
          "location": {
            "type": "string",
            "description": "The city and state, e.g. San Francisco, CA"
          }
        },
        "required": ["location"]
      }
    }
  ]
}
```

**期望响应**:
```json
{
  "input_tokens": 450  // 包含 system + messages + tools + tool_choice overhead
}
```

---

### 测试用例 3: 多轮对话

**请求**:
```json
{
  "model": "claude-sonnet-4-5",
  "messages": [
    {"role": "user", "content": "What is the capital of France?"},
    {"role": "assistant", "content": "The capital of France is Paris."},
    {"role": "user", "content": "What's its population?"}
  ]
}
```

**期望响应**:
```json
{
  "input_tokens": 35
}
```

---

## 🚨 错误处理

### 可能的错误响应

#### 1. 缺少必需参数
```json
{
  "type": "error",
  "error": {
    "type": "invalid_request_error",
    "message": "model is required"
  }
}
```

#### 2. 无效的 API Key
```json
{
  "type": "error",
  "error": {
    "type": "authentication_error",
    "message": "invalid x-api-key"
  }
}
```

#### 3. 消息数组为空
```json
{
  "type": "error",
  "error": {
    "type": "invalid_request_error",
    "message": "messages must contain at least one entry"
  }
}
```

---

## 📈 性能考虑

### 1. 响应时间
```
目标: < 100ms (仅计数，无模型调用)
当前 Mock 实现: < 10ms (但无实际价值)
真实实现: 50-100ms (需要 tokenization)
```

### 2. 缓存策略
```java
// 可以缓存相同请求的计数结果
@Cacheable(value = "tokenCounts", key = "#request.hashCode()")
public int countTokens(AnthropicChatRequest request) {
    // ... 计数逻辑
}
```

### 3. 速率限制
```
count_tokens 接口与 messages 接口共享速率限制
建议: 客户端本地缓存结果，避免重复计数相同内容
```

---

## 🎓 最佳实践

### 1. 预计数优化
```javascript
// ✅ 好的做法: 在发送前计数
const count = await countTokens(request);
if (count.input_tokens + request.max_tokens > 200000) {
  request.messages = compressHistory(request.messages);
}
await createMessage(request);

// ❌ 不好的做法: 直接发送，依赖错误重试
try {
  await createMessage(request);
} catch (error) {
  if (error.type === 'context_length_exceeded') {
    // 事后处理，浪费了一次 API 调用
  }
}
```

### 2. 批量计数
```javascript
// 对多个候选提示词批量计数
const candidates = [prompt1, prompt2, prompt3];
const counts = await Promise.all(
  candidates.map(p => countTokens({model, messages: [p]}))
);
const bestPrompt = candidates[argmin(counts.map(c => c.input_tokens))];
```

### 3. 成本监控
```javascript
class TokenBudget {
  constructor(dailyLimit) {
    this.dailyLimit = dailyLimit;
    this.used = 0;
  }

  async checkAndCount(request) {
    const count = await countTokens(request);
    if (this.used + count.input_tokens > this.dailyLimit) {
      throw new Error('Daily token budget exceeded');
    }
    this.used += count.input_tokens;
    return count;
  }
}
```

---

## 📝 总结与建议

### 关键发现

1. **接口设计**: `/v1/messages/count_tokens` 与 `/v1/messages` 参数几乎完全一致
2. **核心价值**: 成本预估、上下文管理、请求优化
3. **响应简单**: 仅返回 `{"input_tokens": N}`
4. **计数范围**: 包含 messages、system、tools、images、documents 的所有 token

### 当前实现的改进建议

#### 优先级 1: 集成 TokenCounter (立即)
```
理由: 项目已有 TokenCounter 服务
工作量: 1-2 小时
影响: 高 (启用真实的 token 计数功能)
```

#### 优先级 2: 修复响应格式 (立即)
```
当前: {"type": "token_count", "input_tokens": N}
正确: {"input_tokens": N}
工作量: 5 分钟
影响: 中 (兼容性问题)
```

#### 优先级 3: 添加请求验证 (本周)
```
验证: model, messages, x-api-key, anthropic-version
工作量: 30 分钟
影响: 中 (防止误用)
```

#### 优先级 4: 性能优化 (未来)
```
优化: 缓存、批量计数
工作量: 2-4 小时
影响: 低 (性能提升)
```

---

## 🔗 参考资源

- **官方文档**: https://docs.anthropic.com/en/api/messages-count-tokens
- **Token 计数指南**: https://docs.anthropic.com/en/docs/build-with-claude/token-counting
- **定价信息**: https://docs.anthropic.com/en/docs/about-claude/pricing
- **Messages API**: https://docs.anthropic.com/en/api/messages
- **错误处理**: https://docs.anthropic.com/claude/reference/errors

---

**研究完成时间**: 2025-10-10
**置信度**: 高 (基于官方文档)
**建议优先级**: 立即实施正确实现
