# Kiro Gateway Thinking 内容块支持研究报告

**研究日期**: 2025-10-07
**研究目的**: 验证 Kiro CodeWhisperer Gateway 是否支持 Anthropic Claude API 的 extended thinking 特性

---

## 执行摘要

**结论**: **无法从公开渠道确认 Kiro Gateway 对 thinking 内容块的支持情况**

**建议**:
1. ✅ **已完成代码准备**: 项目代码已扩展支持传递 thinking 参数
2. ⚠️ **需要实际测试**: 需要在真实 Kiro Gateway 环境中运行测试验证
3. 📋 **测试工具已就绪**: 已创建 `ThinkingFeatureE2ETest` 用于验证

---

## 1. 研究方法

### 1.1 文档调研
- ✅ 搜索 AWS CodeWhisperer Gateway API 文档
- ✅ 搜索 Kiro Gateway 技术文档
- ✅ 研究 Anthropic Claude extended thinking 规范
- ✅ 分析项目现有文档和代码

### 1.2 代码分析
- ✅ 检查当前项目代码中的 thinking 引用
- ✅ 分析 gap analysis 文档中的相关说明
- ✅ 确认现有 ContentBlock 类型支持

### 1.3 实验性验证
- ✅ 扩展代码支持 thinking 参数传递
- ✅ 创建 E2E 测试用例
- ⏳ 实际测试执行（需要 Kiro Gateway 访问权限）

---

## 2. 研究发现

### 2.1 官方文档情况

#### AWS CodeWhisperer Gateway
- ❌ **无公开 API 文档**: CodeWhisperer 主要作为 IDE 集成工具，不提供公开的程序化 API
- ❌ **内部 Gateway 协议未公开**: Kiro Gateway 的请求/响应格式无官方文档
- ⚠️ **服务定位**: CodeWhisperer 通过 IDE 插件使用，而非独立 API 服务

**搜索引用**:
> "CodeWhisperer does not have public APIs that you can call programmatically, and they are not provided by any SDK."

#### Kiro 官方资源
- ✅ **Kiro IDE 存在**: https://kiro.dev/ 是一个 AI IDE 产品
- ❌ **Gateway API 文档缺失**: 未找到 Kiro Gateway 的 API 规范文档
- ⚠️ **可能使用内部协议**: Kiro 可能使用定制的 CodeWhisperer 集成

### 2.2 Anthropic Claude Extended Thinking

#### 官方规范
```json
// Request format
{
  "thinking": {
    "type": "enabled",
    "budget_tokens": 10000
  }
}

// Response format
{
  "content": [
    {
      "type": "thinking",
      "thinking": "Let me analyze this step by step...",
      "signature": "encrypted_signature_for_verification"
    },
    {
      "type": "text",
      "text": "Based on my analysis..."
    }
  ]
}
```

#### 关键特性
- 🎯 **请求参数**: `thinking: {type: "enabled", budget_tokens: number}`
- 📦 **响应结构**: 包含 `type: "thinking"` 的内容块
- 🔐 **签名机制**: thinking 块包含加密签名用于验证
- 🌊 **流式传输**: 通过 `thinking_delta` 事件传递
- 📚 **模型支持**: Claude Opus 4.1, Sonnet 4.5, Sonnet 4, Sonnet 3.7

**文档来源**: https://docs.claude.com/en/docs/build-with-claude/extended-thinking

### 2.3 项目当前状态

#### 代码分析结果
| 组件 | 当前状态 | thinking 支持 |
|------|----------|---------------|
| `AnthropicMessage.ContentBlock` | ✅ 存在 | ❌ 无 thinking 字段 |
| `AnthropicChatRequest` | ✅ 存在 | ⚠️ 现已添加 thinking 参数 |
| `KiroService.buildKiroPayload` | ✅ 存在 | ⚠️ 现已支持传递 thinking |
| `KiroService.mapResponse` | ✅ 存在 | ❌ 无 thinking 解析逻辑 |
| Event Parsers | ✅ 存在 | ❌ 不识别 thinking 事件 |

#### Gap Analysis 文档记录
```markdown
#### 3.1.3 thinking 内容块 (❌ 缺失)
**Claude Code 特定需求**: 扩展思考模式 (`--think`, `--think-hard`, `--ultrathink`)

**影响**: Claude Code 的高级推理模式不可用

**降级原因** (从 P1 → P2):
- thinking 内容块需要 Kiro 后端支持特殊响应格式
- 非核心功能,不影响基本工具调用和对话
- 可在后续版本中实现
```

---

## 3. 代码实现

### 3.1 已完成的修改

#### 修改 1: 扩展 AnthropicChatRequest
```java
// File: src/main/java/org/yanhuang/ai/model/AnthropicChatRequest.java
private Map<String, Object> thinking;

public Map<String, Object> getThinking() {
    return thinking;
}

public void setThinking(Map<String, Object> thinking) {
    this.thinking = thinking;
}
```

#### 修改 2: KiroService 传递 thinking 参数
```java
// File: src/main/java/org/yanhuang/ai/service/KiroService.java
// Add thinking parameter if present (for extended thinking mode)
if (request.getThinking() != null && !request.getThinking().isEmpty()) {
    userInput.set("thinking", mapper.valueToTree(request.getThinking()));
    log.info("Extended thinking enabled with config: {}", request.getThinking());
}
```

#### 修改 3: 创建 E2E 测试
```java
// File: src/test/java/org/yanhuang/ai/e2e/ThinkingFeatureE2ETest.java
@Test
public void testThinkingParameterAcceptance() {
    // Build request with thinking parameter
    Map<String, Object> thinking = new HashMap<>();
    thinking.put("type", "enabled");
    thinking.put("budget_tokens", 5000);
    request.setThinking(thinking);

    // Send request and analyze response for thinking blocks
    ...
}
```

### 3.2 测试策略

#### 阶段 1: 参数接受测试
**目的**: 验证 Kiro Gateway 是否接受 thinking 参数而不报错

**预期结果**:
- ✅ **接受**: Kiro Gateway 返回 200 OK → 可能支持
- ❌ **拒绝**: Kiro Gateway 返回 400 Bad Request → 不支持

#### 阶段 2: 响应内容分析
**目的**: 检查 Kiro 响应中是否包含 thinking 内容块

**检查点**:
1. 响应事件流中是否有 `type: "thinking"` 字段
2. 是否包含 thinking 相关的事件类型
3. 响应格式是否与 Anthropic 标准一致

#### 阶段 3: 完整实现
**条件**: 仅在 Kiro 确认支持后执行

**任务清单**:
- [ ] 扩展 `ContentBlock` 添加 thinking 字段
- [ ] 实现 thinking 内容块解析逻辑
- [ ] 添加流式 `thinking_delta` 事件处理
- [ ] 实现 thinking 签名验证（如果需要）
- [ ] 编写完整的单元测试和 E2E 测试

---

## 4. 风险评估

### 4.1 高概率不支持的证据

| 指标 | 观察 | 暗示 |
|------|------|------|
| **项目历史** | thinking 从 P1 降级到 P2 | 初步研究发现限制 |
| **文档说明** | "需要 Kiro 后端支持特殊响应格式" | 依赖后端能力 |
| **API 性质** | CodeWhisperer 无公开 API | 可能使用旧版或定制模型 |
| **模型版本** | 不确定 Kiro 使用的 Claude 版本 | thinking 是 Claude 4+ 特性 |

### 4.2 可能的结果场景

#### 场景 A: Kiro Gateway 完全支持 ✅
**概率**: 低 (20%)

**表现**:
- Kiro 接受 thinking 参数
- 响应包含 `type: "thinking"` 内容块
- 格式与 Anthropic 标准一致

**后续行动**:
1. 完成 thinking 内容块解析实现
2. 将优先级从 P2 提升回 P1
3. 添加完整的测试覆盖
4. 更新文档标记为"已支持"

#### 场景 B: Kiro Gateway 部分支持 ⚠️
**概率**: 中 (30%)

**表现**:
- Kiro 接受 thinking 参数但忽略它
- 或返回非标准格式的 thinking 内容
- 需要额外的格式转换逻辑

**后续行动**:
1. 分析实际响应格式
2. 实现格式转换适配器
3. 考虑功能降级或模拟

#### 场景 C: Kiro Gateway 不支持 ❌
**概率**: 高 (50%)

**表现**:
- Kiro 返回 400 Bad Request（无效参数）
- 或接受但响应中无 thinking 内容
- 完全无法使用 extended thinking

**后续行动**:
1. 在文档中明确标记"Kiro 限制"
2. 考虑应用层模拟（记录推理日志）
3. 建议用户使用 Anthropic 官方 API
4. 保持 P2 优先级不变

---

## 5. 下一步行动

### 5.1 立即行动（用户可执行）

#### 步骤 1: 运行测试验证
```bash
# 确保使用 Java 21
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

# 运行 thinking 特性测试
mvn clean test -Dtest=ThinkingFeatureE2ETest

# 或使用提供的批处理脚本
run-thinking-test.bat
```

#### 步骤 2: 分析测试输出
**关键日志查找**:
```
查找成功标记:
- "!!! FOUND THINKING BLOCK !!!"
- "!!! SUCCESS: Kiro Gateway supports extended thinking !!!"

查找失败标记:
- "Kiro Gateway rejected the thinking parameter (400 Bad Request)"
- "No thinking blocks found in response"
- "Kiro Gateway likely does NOT support extended thinking mode"

查看实际 Kiro 响应:
- "Response ID: ..."
- "Content blocks count: ..."
- "Block N: type=..."
```

#### 步骤 3: 决策分支
```
IF 测试通过 AND 发现 thinking 块:
    → 执行完整 thinking 实现 (场景 A)
    → 提升优先级 P2 → P1

ELSE IF 测试通过 BUT 无 thinking 块:
    → 分析 Kiro 响应格式 (场景 B/C)
    → 考虑格式适配或标记不支持

ELSE IF 测试失败 400 错误:
    → 确认 Kiro 不支持 (场景 C)
    → 更新文档标记限制
```

### 5.2 可选增强（如果不支持）

#### 选项 1: 应用层模拟
```java
// 在应用层记录推理过程
if (request.getThinking() != null) {
    log.info("User requested thinking mode (simulated)");
    log.info("Note: Actual thinking blocks require Anthropic API");
    // 可以在响应中添加说明性文本
}
```

#### 选项 2: 代理模式
```
用户请求 → claude-kiro 判断:
  IF thinking 参数存在:
    → 转发到 Anthropic 官方 API
  ELSE:
    → 使用 Kiro Gateway
```

#### 选项 3: 文档说明
```markdown
## Extended Thinking Support

### Status: Not Supported by Kiro Gateway

The extended thinking feature is an Anthropic Claude API feature
that requires backend support. Kiro Gateway does not currently
support this feature.

### Workaround:
Use Anthropic's official API directly for extended thinking:
https://docs.claude.com/en/docs/build-with-claude/extended-thinking
```

---

## 6. 技术细节

### 6.1 Kiro Payload 结构（推测）

基于现有代码分析，Kiro Gateway 期望的 payload 结构：
```json
{
  "profileArn": "arn:aws:...",
  "conversationState": {
    "chatTriggerType": "MANUAL",
    "conversationId": "uuid",
    "currentMessage": {
      "userInputMessage": {
        "content": "user message",
        "modelId": "CLAUDE_SONNET_4_5_20250929_V1_0",
        "origin": "AI_EDITOR",
        "thinking": {  // 新增字段
          "type": "enabled",
          "budget_tokens": 10000
        }
      }
    },
    "history": [...]
  }
}
```

### 6.2 期望的 Kiro 响应（如果支持）

**可能的事件格式**:
```json
{
  "type": "thinking",
  "thinking": "Let me analyze...",
  "signature": "..."
}
```

或 Kiro 特有格式（需实际观察）:
```json
{
  "thinkingContent": "...",
  "reasoning": "...",
  "internalThought": "..."
}
```

### 6.3 解析逻辑扩展点

如果 Kiro 支持，需要在以下位置添加代码：

#### `KiroService.mapResponse`:
```java
// 在现有 tool_use/text 处理之后
if ("thinking".equals(event.get("type").asText())) {
    AnthropicMessage.ContentBlock thinkingBlock = new AnthropicMessage.ContentBlock();
    thinkingBlock.setType("thinking");
    thinkingBlock.setThinking(event.get("thinking").asText());
    // Handle signature if present
    response.addContentBlock(thinkingBlock);
}
```

#### `AnthropicMessage.ContentBlock`:
```java
// Add thinking-specific fields
private String thinking;  // The thinking content
private String signature; // Verification signature

// Getters and setters...
```

---

## 7. 参考资料

### 7.1 外部文档
- [Anthropic Extended Thinking](https://docs.claude.com/en/docs/build-with-claude/extended-thinking)
- [AWS CodeWhisperer FAQs](https://aws.amazon.com/codewhisperer/faqs/)
- [Kiro IDE](https://kiro.dev/)

### 7.2 项目文档
- `claudedocs/anthropic_api_compliance_gap_analysis.md`
- `claudedocs/p0_fixes_summary.md`
- `claudedocs/p1_fixes_summary.md`

### 7.3 相关代码
- `src/main/java/org/yanhuang/ai/model/AnthropicChatRequest.java`
- `src/main/java/org/yanhuang/ai/service/KiroService.java`
- `src/test/java/org/yanhuang/ai/e2e/ThinkingFeatureE2ETest.java`

---

## 8. 结论与建议

### 8.1 核心结论

1. **文档证据不足**: 无公开文档证明 Kiro Gateway 支持 thinking
2. **代码已就绪**: 项目代码已准备好传递和测试 thinking 参数
3. **需要实测**: 唯一可靠的验证方法是在真实环境中测试

### 8.2 优先级建议

**保持 P2 优先级** 直到验证结果出来：

- ✅ P0/P1 任务已完成（工具调用、流式响应、错误处理）
- ⚠️ thinking 是增强特性，非核心功能
- 🔬 需要实验性验证才能确定可行性

### 8.3 最终建议

#### 短期（立即）:
1. ✅ **运行 `ThinkingFeatureE2ETest`** 在有 Kiro Gateway 访问权限的环境中
2. 📊 **分析测试日志** 确定 Kiro 的实际行为
3. 📝 **更新文档** 根据测试结果更新 gap analysis

#### 中期（如果支持）:
1. 🛠️ 完成 thinking 内容块解析实现
2. ✅ 添加完整测试覆盖
3. 📚 更新用户文档说明使用方法

#### 长期（如果不支持）:
1. 📋 在文档中明确标记为 "Kiro 限制"
2. 💡 考虑应用层模拟或代理模式
3. 🔄 定期检查 Kiro Gateway 更新

---

**报告生成**: claude-kiro 研究团队
**状态**: 等待实际测试验证
**下次更新**: 测试完成后
