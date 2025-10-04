# E2E (端到端) 测试文档

## 概述

本E2E测试套件为claude-kiro项目提供完整的端到端测试验证，从API调用到Kiro网关的完整流程。测试覆盖基础聊天、工具调用、多轮对话、流式响应、性能和压力测试等核心功能。

## 测试架构

### 核心组件

1. **BaseE2ETest** - 所有E2E测试的基类，提供通用功能和工具方法
2. **ClaudeApiClient** - 真实HTTP客户端，用于调用Spring Boot应用API
3. **KiroCallCounter** - Kiro调用额度管理器，控制测试对网关的调用次数
4. **KiroCallTracker** - AOP切面，追踪所有KiroService方法调用
5. **TestStateManager** - 测试状态管理器，记录测试执行过程和结果
6. **EnabledIfKiroQuotaAvailable** - 自定义JUnit条件，在额度不足时跳过测试

### 配置文件

- `application-e2e.yml` - E2E测试专用配置
- `E2ETestConfig` - 配置属性类，管理测试参数
- `application-test.yml` - 单元测试和集成测试配置

## 测试套件说明

### 1. ClaudeChatE2ETest - 基础聊天功能测试

**测试场景：**
- 基础聊天对话 - 简单问答
- 多轮对话 - 上下文保持
- 流式响应测试
- 长文本处理测试
- 错误处理测试

**验证重点：**
- API响应结构正确性
- 对话内容质量
- 流式数据传输
- 异常处理能力

### 2. ToolCallE2ETest - 工具调用功能测试

**测试场景：**
- 基础工具调用 - 搜索功能
- 复杂工具调用 - 多参数函数
- 多工具选择调用
- 工具调用流式响应
- 工具调用错误处理
- 工具调用响应处理

**验证重点：**
- 工具定义正确性
- 参数传递准确性
- 工具选择逻辑
- 错误处理机制

### 3. MultiRoundConversationE2ETest - 多轮对话测试

**测试场景：**
- 长期对话上下文保持
- 复杂任务分步解决
- 角色扮演对话
- 上下文截断测试

**验证重点：**
- 上下文记忆能力
- 对话连贯性
- 角色一致性
- 长文本处理

### 4. StreamingAndErrorE2ETest - 流式响应和错误处理测试

**测试场景：**
- 流式响应完整流程
- 流式响应中断恢复
- 并发请求处理
- 大请求处理
- 网络超时处理
- 无效输入处理
- 资源清理测试

**验证重点：**
- 流式数据完整性
- 并发处理能力
- 错误恢复机制
- 资源管理

### 5. PerformanceAndStressE2ETest - 性能和压力测试

**测试场景：**
- 响应时间基准测试
- 并发负载测试
- 内存使用测试
- 流式性能测试
- 系统稳定性测试
- 资源限制测试

**验证重点：**
- 响应时间指标
- 并发处理能力
- 资源使用效率
- 系统稳定性

## 环境配置

### 必需环境变量

```bash
# Claude API密钥
export CLAUDE_API_KEY="your-claude-api-key"

# Kiro相关配置（根据需要设置）
export KIRO_BASE_URL="http://localhost:8080"
export KIRO_PROFILE_ARN="arn:aws:codewhisperer:us-east-1:123456789012:profile/test-profile"
export KIRO_ACCESS_TOKEN="your-access-token"
export KIRO_REFRESH_TOKEN="your-refresh-token"
export KIRO_REFRESH_URL="http://localhost:8080/refresh-token"
```

### 可选配置

在 `application-e2e.yml` 中可以调整以下参数：

```yaml
e2e:
  max-calls-per-batch: 5          # 每批测试的最大Kiro调用次数
  timeout-seconds: 30             # 请求超时时间
  retry-attempts: 2               # 重试次数
  enable-call-tracking: true      # 是否启用调用追踪
  skip-tests-on-quota-exceeded: true  # 额度不足时是否跳过测试
```

## 执行方式

### 1. 运行所有E2E测试

```bash
# 使用Maven
mvn test -Dtest=E2ETestRunner

# 或使用Gradle
gradle test --tests E2ETestRunner
```

### 2. 运行特定测试类

```bash
# 基础聊天测试
mvn test -Dtest=ClaudeChatE2ETest

# 工具调用测试
mvn test -Dtest=ToolCallE2ETest

# 多轮对话测试
mvn test -Dtest=MultiRoundConversationE2ETest

# 流式和错误处理测试
mvn test -Dtest=StreamingAndErrorE2ETest

# 性能测试
mvn test -Dtest=PerformanceAndStressE2ETest
```

### 3. 运行特定测试方法

```bash
mvn test -Dtest=ClaudeChatE2ETest#testBasicChatConversation
```

### 4. 带参数的测试执行

```bash
# 设置更长的超时时间
mvn test -Dtest=E2ETestRunner -Dmaven.test.timeout=600000

# 跳过测试编译
mvn test -Dtest=E2ETestRunner -Dmaven.test.skip=false

# 并行执行测试
mvn test -Dtest=E2ETestRunner -Dparallel=methods -DthreadCount=2
```

## 额度管理

### 工作原理

1. **批次限制**: 每批测试最多允许5次Kiro调用（可配置）
2. **调用追踪**: 所有KiroService方法调用都会被追踪和计数
3. **自动跳过**: 当额度不足时，标注了`@EnabledIfKiroQuotaAvailable`的测试会自动跳过
4. **批次重置**: 可以手动重置调用计数器开始新的测试批次

### 监控额度使用

测试过程中会实时显示额度使用情况：

```
Kiro调用计数: 当前批次 3/5, 总计 15
剩余额度: 2次调用
```

### 重置额度计数

如需重置测试批次，可以：

1. 修改配置文件中的 `e2e.max-calls-per-batch`
2. 删除状态文件 `target/e2e-test-state-*.json`
3. 重启测试应用

## 结果分析

### 测试报告

测试完成后会生成详细报告：

- **JSON报告**: `target/test-reports/test-report-*.json`
- **状态文件**: `target/e2e-test-state-*.json`
- **Maven报告**: `target/surefire-reports/`

### 报告内容

```json
{
  "suiteName": "E2ETestSuite",
  "sessionId": "session-1699123456789-1a2b3",
  "description": "完整的端到端测试",
  "testStatistics": {
    "total": 25,
    "passed": 23,
    "failed": 1,
    "skipped": 1,
    "passRate": 92.0
  },
  "kiroStatistics": {
    "callsUsed": 12,
    "totalCallsUsed": 45,
    "remainingCalls": 3,
    "maxCallsPerBatch": 5
  }
}
```

### 日志分析

关键日志标识：

- `🚀` - 测试开始
- `✅` - 测试成功
- `❌` - 测试失败
- `⚠️` - 警告信息
- `⏭️` - 测试跳过

## 故障排除

### 常见问题

#### 1. 额度不足

**症状**: 测试大量跳过，显示"Kiro额度不足"

**解决方案**:
- 检查Kiro账户余额
- 等待额度重置
- 调整 `e2e.max-calls-per-batch` 配置
- 手动重置测试批次

#### 2. 网络连接问题

**症状**: 连接超时、连接被拒绝

**解决方案**:
- 检查网络连接
- 验证KIRO_BASE_URL配置
- 检查防火墙设置
- 确认Kiro网关服务状态

#### 3. 认证失败

**症状**: 401/403错误

**解决方案**:
- 验证CLAUDE_API_KEY环境变量
- 检查KIRO_ACCESS_TOKEN配置
- 确认令牌有效期
- 重新获取访问令牌

#### 4. 测试超时

**症状**: 测试执行超时

**解决方案**:
- 增加timeout配置
- 检查系统性能
- 优化测试数据大小
- 并行执行测试

### 调试技巧

#### 1. 启用详细日志

```yaml
logging:
  level:
    org.yanhuang.ai.e2e: DEBUG
    org.yanhuang.ai.service: DEBUG
    reactor.netty: INFO
```

#### 2. 单独运行失败测试

```bash
mvn test -Dtest=ClaudeChatE2ETest#testBasicChatConversation -Dmaven.test.failure.ignore=true
```

#### 3. 保留测试数据

设置 `e2e.state-file-path` 来保存测试状态，便于分析问题。

## 最佳实践

### 1. 测试环境准备

- 使用专用的测试环境
- 配置独立的测试账户
- 确保充足的测试额度
- 准备测试数据集

### 2. 测试执行策略

- 定期执行完整测试套件
- 在CI/CD流水线中集成关键测试
- 监控测试执行时间和资源使用
- 及时清理测试产生的临时文件

### 3. 结果管理

- 定期备份测试报告
- 建立测试结果趋势分析
- 设置测试失败告警
- 维护测试用例文档

### 4. 团队协作

- 共享测试环境配置
- 同步测试执行计划
- 记录已知问题和解决方案
- 定期回顾和优化测试策略

## 扩展指南

### 添加新的E2E测试

1. 继承 `BaseE2ETest` 类
2. 添加 `@EnabledIfKiroQuotaAvailable` 注解
3. 实现测试逻辑和断言
4. 更新测试文档

### 自定义配置

1. 在 `E2ETestConfig` 中添加新属性
2. 在 `application-e2e.yml` 中配置默认值
3. 在测试类中注入和使用配置

### 集成外部工具

1. 扩展 `ClaudeApiClient` 支持新的API端点
2. 创建专用的测试工具类
3. 添加相应的测试场景

---

**维护者**: Claude Code AI Assistant
**最后更新**: 2025年1月
**版本**: 1.0.0