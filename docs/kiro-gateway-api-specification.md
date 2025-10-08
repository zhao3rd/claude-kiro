# Kiro Gateway API 规范文档

## 1. API 基本信息

- **服务名称**: AWS Kiro CodeWhisperer Gateway
- **基础 URL**: `https://codewhisperer.us-east-1.amazonaws.com`
- **主要端点**: `/generateAssistantResponse`
- **认证方式**: Bearer Token
- **请求方法**: POST
- **内容类型**: `application/json`
- **响应类型**: `text/event-stream` (Server-Sent Events)

## 2. 接口列表

### 2.1 生成助手响应

**端点**: `POST /generateAssistantResponse`

**功能**: 发送对话请求并获取流式响应

## 3. 请求参数

### 3.1 请求头

| 参数名 | 值 | 必需 | 说明 |
|--------|----|-----|------|
| Authorization | Bearer {token} | ✅ | 访问令牌 |
| Content-Type | application/json | ✅ | 请求内容类型 |
| Accept | text/event-stream | ✅ | 响应类型 |

### 3.2 请求体结构

```json
{
  "profileArn": "string",
  "conversationState": {
    "chatTriggerType": "string",
    "conversationId": "string",
    "currentMessage": {
      "userInputMessage": {
        "content": "string",
        "modelId": "string",
        "origin": "string",
        "userInputMessageContext": {
          "tools": [
            {
              "toolSpecification": {
                "name": "string",
                "description": "string",
                "inputSchema": {
                  "json": {
                    "type": "object",
                    "properties": {
                      "parameterName": {
                        "type": "string",
                        "description": "string"
                      }
                    },
                    "required": ["parameterName"]
                  }
                }
              }
            }
          ]
        }
      }
    },
    "history": [
      {
        "userInputMessage": {
          "content": "string",
          "modelId": "string",
          "origin": "string"
        }
      },
      {
        "assistantResponseMessage": {
          "content": "string"
        }
      }
    ]
  }
}
```

### 3.3 参数详细说明

#### 3.3.1 顶层参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|-----|------|--------|
| profileArn | string | ✅ | AWS Kiro 配置文件的 ARN | `arn:aws:codewhisperer:us-east-1:699475941385:profile/EHGA3GRVQMUK` |
| conversationState | object | ✅ | 对话状态信息 | - |

#### 3.3.2 conversationState 参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|-----|------|--------|
| chatTriggerType | string | ✅ | 聊天触发类型 | `MANUAL` |
| conversationId | string | ✅ | 对话ID，必须为非空字符串 | `550e8400-e29b-41d4-a716-446655440000` |
| currentMessage | object | ✅ | 当前消息 | - |
| history | array | ✅ | 历史消息记录 | `[]` |

#### 3.3.3 userInputMessage 参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|-----|------|--------|
| content | string | ✅ | 消息内容，支持系统提示格式 | `[System] System prompt\n[user] User message` |
| modelId | string | ✅ | 模型ID | `CLAUDE_SONNET_4_5_20250929_V1_0` |
| origin | string | ✅ | 来源标识 | `AI_EDITOR` |
| userInputMessageContext | object | ❌ | 工具定义上下文 | - |

#### 3.3.4 工具定义 (userInputMessageContext.tools)

| 参数名 | 类型 | 必需 | 说明 |
|--------|------|-----|------|
| toolSpecification | object | ✅ | 工具规范定义 |

##### toolSpecification 参数

| 参数名 | 类型 | 必需 | 说明 |
|--------|------|-----|------|
| name | string | ✅ | 工具名称 |
| description | string | ✅ | 工具描述 |
| inputSchema | object | ✅ | 输入参数schema |

##### inputSchema.json 参数

| 参数名 | 类型 | 必需 | 说明 |
|--------|------|-----|------|
| type | string | ✅ | 类型，通常为 "object" |
| properties | object | ✅ | 参数定义 |
| required | array | ✅ | 必需参数列表 |

## 4. 响应格式

### 4.1 响应头

```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

### 4.2 事件流格式

响应采用 Server-Sent Events (SSE) 格式，包含多种事件类型：

```
event: message_start
data: {...}

event: content_block_start
data: {...}

event: content_block_delta
data: {...}

event: content_block_stop
data: {...}

event: message_delta
data: {...}

event: message_stop
data: {...}
```

## 5. 限制和约束

### 5.1 大小限制

| 限制类型 | 大小 | 说明 |
|---------|------|------|
| JSON payload 大小 | 295KB+ | 测试验证的最大值 |
| conversationId | 1-36 字符 | 不能为空，建议使用 UUID |
| modelId | 具体值限制 | 必须是 Kiro 支持的模型ID |

### 5.2 格式约束

| 约束项 | 要求 | 错误类型 |
|--------|------|---------|
| conversationId | 不能为空字符串 | 400 BAD_REQUEST |
| modelId | 必须为有效模型ID | 400 BAD_REQUEST |
| toolSpecification | 必须包含完整字段 | 400 BAD_REQUEST |
| history 结构 | 消息对象结构必须一致 | 400 BAD_REQUEST |

### 5.3 支持的模型ID

| 模型ID | 说明 |
|-------|------|
| `CLAUDE_SONNET_4_5_20250929_V1_0` | Claude Sonnet 4.5 最新版本 |
| `CLAUDE_3_7_SONNET_20250219_V1_0` | Claude 3.7 Sonnet |

## 6. 错误码

### 6.1 400 BAD_REQUEST

格式错误的请求，常见原因：

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| "Improperly formed request." | conversationId 为空 | 使用有效的 UUID |
| "Improperly formed request." | 工具定义不完整 | 确保包含 name、description、inputSchema |
| "Improperly formed request." | 历史记录结构不一致 | 检查消息对象结构 |
| "Invalid model. Please select a different model to continue." | 无效的 modelId | 使用支持的模型ID |

### 6.2 403 FORBIDDEN

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| "The bearer token included in the request is invalid." | Token 无效或过期 | 刷新访问令牌 |

### 6.3 500 INTERNAL_SERVER_ERROR

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| "Encountered an unexpected error when processing the request, please try again." | 服务器内部错误 | 重试请求 |

## 7. 最佳实践

### 7.1 请求构建

1. **conversationId**: 为每个请求生成新的 UUID
2. **content 格式**: 使用 `[System] ... \n[user] ...` 格式
3. **工具定义**: 确保每个工具包含完整的 name、description 和 inputSchema
4. **历史记录**: 保持用户消息和助手回复的成对结构

### 7.2 错误处理

1. **400 错误**: 检查请求格式和数据完整性
2. **403 错误**: 刷新 token 并重试
3. **500 错误**: 实施重试机制
4. **网络错误**: 使用指数退避策略

### 7.3 性能优化

1. **历史记录管理**: 限制历史记录数量和大小
2. **工具定义**: 只包含必要的工具
3. **内容长度**: 避免过长的单条消息内容