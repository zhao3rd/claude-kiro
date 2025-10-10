# Yupp.ai Chat Capture - 使用文档

## 概述

这是一个TamperMonkey用户脚本，用于捕获Yupp.ai网站上的流式聊天请求和响应数据，并自动保存为JSON文件供后续分析。

## 功能特性

### ✅ 核心功能
- **自动拦截**: 监控所有 POST 请求到 `/chat/{uuid}` 格式的聊天API
- **完整捕获**: 记录请求URL、方法、头部、载荷和完整的流式响应
- **流式处理**: 实时读取流式响应数据块，不影响页面正常功能
- **自动保存**: 每次捕获自动下载为JSON文件到浏览器默认下载目录
- **批量导出**: 支持导出会话期间所有捕获的数据
- **详细日志**: 实时显示捕获进度和流处理状态

### 📊 捕获的数据结构

```json
{
  "timestamp": "2025-10-10T12:34:56.789Z",
  "requestUrl": "https://yupp.ai/chat/7e2e26d5-907f-49e7-bce0-019daf956dad",
  "requestMethod": "POST",
  "requestHeaders": {
    "content-type": "text/x-component",
    "accept": "*/*"
  },
  "requestPayload": [
    {
      "407b4b73-eff3-46d0-ad9f-95f9c697c1b1": "...",
      "23bcc8c2-b970-4582-9a5d-9ab0bf850ff0": "...",
      "modelName": "gpt-5-pro-2025-10-06",
      "promptModifierId": "$undefined"
    }
  ],
  "responseStatus": 200,
  "responseHeaders": {
    "content-type": "text/x-component",
    "content-encoding": "br"
  },
  "responseChunks": [
    "chunk1 data...",
    "chunk2 data...",
    "..."
  ],
  "fullResponse": "complete response text",
  "error": null
}
```

## 安装步骤

### 1. 安装TamperMonkey

在浏览器中安装TamperMonkey扩展：
- **Chrome**: [Chrome Web Store](https://chrome.google.com/webstore/detail/tampermonkey)
- **Firefox**: [Firefox Add-ons](https://addons.mozilla.org/firefox/addon/tampermonkey/)
- **Edge**: [Edge Add-ons](https://microsoftedge.microsoft.com/addons/detail/tampermonkey)

### 2. 安装脚本

1. 点击浏览器工具栏中的TamperMonkey图标
2. 选择 "创建新脚本" 或 "Dashboard"
3. 将 `YuppChatCapture.js` 的完整内容复制粘贴到编辑器
4. 按 `Ctrl+S` (Windows/Linux) 或 `Cmd+S` (Mac) 保存
5. 关闭编辑器标签页

### 3. 验证安装

1. 访问 https://yupp.ai
2. 打开浏览器开发者工具（F12）
3. 切换到 "Console" 标签
4. 应该看到类似以下启动信息：

```
[Yupp Chat Capture] Script initialized
[Yupp Chat Capture] ========================================
[Yupp Chat Capture]   Yupp.ai Chat Capture v1.1 Active
[Yupp Chat Capture]   - Monitoring: POST /chat/{uuid}
[Yupp Chat Capture]   - Auto-saving captures to Downloads
[Yupp Chat Capture]   - Use exportAllCaptures() to export all
[Yupp Chat Capture]   - Use checkCaptureStatus() for status
[Yupp Chat Capture] ========================================
```

## 使用方法

### 基本使用

1. **开始捕获**:
   - 脚本安装后自动激活
   - 在Yupp.ai发送聊天消息
   - 脚本自动拦截并捕获请求/响应

2. **查看捕获**:
   - 每次捕获完成后，JSON文件自动下载到浏览器默认下载目录
   - 文件名格式: `yupp-chat-capture-2025-10-10T12-34-56-789Z.json`
   - 控制台显示捕获统计信息

### 控制台命令

在浏览器控制台中使用以下命令：

#### 1. 查看捕获状态
```javascript
checkCaptureStatus()
```
输出示例：
```
[Yupp Chat Capture] 📊 Status: {
  totalCaptures: 5,
  script: 'Active and monitoring',
  lastCapture: '2025-10-10T12:34:56.789Z'
}
```

#### 2. 导出所有捕获数据
```javascript
exportAllCaptures()
```
- 将会话期间所有捕获的数据合并为一个JSON文件
- 弹出保存对话框，可自定义保存位置
- 文件名格式: `yupp-chat-captures-all-2025-10-10T12-34-56-789Z.json`

## 工作原理

### 技术架构

```
Yupp.ai页面
    ↓ (用户发送聊天)
原始fetch调用
    ↓ (被拦截)
YuppChatCapture脚本
    ├─ 捕获请求数据 (URL, headers, payload)
    ├─ 执行原始fetch
    ├─ Clone响应流
    ├─ 捕获响应数据 (status, headers)
    └─ 后台读取流式数据
          ├─ 逐块收集数据
          ├─ 拼接完整响应
          └─ 保存为JSON文件
    ↓ (返回原始响应)
页面正常显示聊天结果
```

### 关键技术点

1. **Fetch拦截**:
   - 保存原始 `window.fetch` 函数
   - 用包装函数替换，检查URL模式
   - 符合条件则捕获，否则透传

2. **流式响应处理**:
   - 使用 `response.clone()` 创建响应副本
   - 副本用于数据读取，原始响应返回给页面
   - 确保不影响页面功能

3. **异步数据收集**:
   - 后台异步读取流数据块
   - 使用 `TextDecoder` 解码二进制数据
   - 完成后触发保存操作

4. **文件保存**:
   - 使用 `GM_download` API (TamperMonkey提供)
   - Data URL方式编码JSON内容
   - 自动下载，无需用户交互

## 数据分析示例

### 使用Python分析捕获数据

```python
import json

# 读取捕获文件
with open('yupp-chat-capture-2025-10-10T12-34-56-789Z.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# 分析请求
print(f"Request URL: {data['requestUrl']}")
print(f"Request Method: {data['requestMethod']}")
print(f"Request Payload: {json.dumps(data['requestPayload'], indent=2)}")

# 分析响应
print(f"\nResponse Status: {data['responseStatus']}")
print(f"Number of chunks: {len(data['responseChunks'])}")
print(f"Total response length: {len(data['fullResponse'])}")

# 提取模型信息
if data['requestPayload']:
    for item in data['requestPayload']:
        if 'modelName' in item:
            print(f"\nModel used: {item['modelName']}")
```

### 使用jq查询JSON

```bash
# 提取请求URL
jq '.requestUrl' yupp-chat-capture-*.json

# 提取模型名称
jq '.requestPayload[].modelName' yupp-chat-capture-*.json

# 统计响应数据块数量
jq '.responseChunks | length' yupp-chat-capture-*.json

# 提取完整响应文本
jq -r '.fullResponse' yupp-chat-capture-*.json
```

## 故障排除

### 问题1: 脚本未启动

**症状**: 控制台没有启动信息

**解决方案**:
1. 检查TamperMonkey扩展是否已启用
2. 检查脚本是否在TamperMonkey Dashboard中启用
3. 刷新页面 (Ctrl+R 或 Cmd+R)
4. 检查 `@match` 规则是否匹配当前URL

### 问题2: 未捕获到请求

**症状**: 发送消息但没有下载文件

**可能原因**:
1. 请求URL格式不匹配 (不是 `/chat/{uuid}` 格式)
2. 请求方法不是 POST
3. 脚本执行时机过晚 (检查 `@run-at` 设置)

**解决方案**:
1. **检查网络请求**:
   - 打开开发者工具 → Network标签
   - 发送消息并观察请求
   - 确认URL格式类似: `https://yupp.ai/chat/7e2e26d5-907f-49e7-bce0-019daf956dad`
   - 确认方法是 POST

2. **查看控制台日志**:
   - 如果捕获成功，会显示: `🎯 Capturing chat request`
   - 如果没有此日志，说明URL未匹配或方法不对

3. **手动检查状态**:
   ```javascript
   checkCaptureStatus()
   ```

4. **临时降低捕获条件测试** (仅用于调试):
   编辑脚本第41-42行，改为:
   ```javascript
   const shouldCapture = url.includes('/chat/');
   ```
   看是否能捕获到请求，如果可以，说明UUID格式匹配有问题

### 问题3: 下载文件为空或损坏

**症状**: JSON文件无法打开或内容不完整

**解决方案**:
1. 等待流式响应完全结束（看控制台 "Stream completed" 消息）
2. 检查 `responseChunks` 数组是否有内容
3. 检查是否有 `error` 字段记录了错误信息

### 问题4: 影响页面功能

**症状**: 页面聊天功能异常

**解决方案**:
1. 禁用脚本测试是否脚本导致
2. 检查脚本是否正确返回原始响应
3. 查看控制台错误信息
4. 临时禁用脚本: TamperMonkey图标 → 关闭脚本开关

## 高级配置

### 修改捕获条件

编辑脚本第41-42行，修改URL匹配逻辑：

```javascript
// v1.1默认条件: UUID格式的聊天请求
const chatUrlPattern = /\/chat\/[a-f0-9-]{36}/i;
const shouldCapture = chatUrlPattern.test(url) && config?.method === 'POST';

// 宽松模式: 捕获所有/chat/路径的POST请求
const shouldCapture = url.includes('/chat/') && config?.method === 'POST';

// 更宽松: 捕获所有/chat/路径的请求（不检查method）
const shouldCapture = url.includes('/chat/');

// 添加额外过滤: 只捕获特定子路径
const shouldCapture = url.includes('/chat/') &&
                      url.includes('/messages') &&
                      config?.method === 'POST';
```

### 自定义保存行为

修改 `saveCapture()` 函数中的下载选项：

```javascript
GM_download({
    url: 'data:application/json;charset=utf-8,' + encodeURIComponent(jsonContent),
    name: filename,
    saveAs: true  // 改为true: 每次弹出保存对话框
});
```

### 添加数据过滤

在 `processStreamInBackground()` 函数中添加数据过滤：

```javascript
// 只保存特定模型的请求
if (captureData.requestPayload) {
    const hasTargetModel = captureData.requestPayload.some(
        item => item.modelName && item.modelName.includes('gpt-5')
    );
    if (!hasTargetModel) {
        console.log('[Yupp Chat Capture] ⏭️ Skipping non-target model');
        return; // 不保存
    }
}
```

## 注意事项

⚠️ **隐私提醒**:
- 捕获的数据包含完整的聊天内容
- 请妥善保管下载的JSON文件
- 不要分享包含敏感信息的捕获文件

⚠️ **性能提醒**:
- 长时间运行会在内存中累积数据
- 定期刷新页面清空 `capturedSessions` 数组
- 大量捕获时使用 `exportAllCaptures()` 导出后刷新页面

⚠️ **法律提醒**:
- 本脚本仅用于个人学习和调试目的
- 请遵守Yupp.ai的服务条款
- 不要用于商业用途或逆向工程

## 版本历史

### v1.1 (2025-10-10) - Bug Fix Release
- 🐛 **修复**: 捕获条件错误导致无法捕获请求（从 `stream=true` 参数检查改为UUID格式匹配）
- ✨ **改进**: 增强请求体捕获，支持更多数据类型（JSON, FormData等）
- 📊 **增强**: 添加详细的调试日志（请求方法、载荷类型、流处理进度）
- 🔄 **优化**: 流处理进度实时显示（每10个chunk）

### v1.0 (2025-10-10) - Initial Release
- ✅ 初始版本发布
- ✅ 支持流式聊天请求捕获
- ✅ 自动JSON文件下载
- ✅ 控制台命令支持
- ⚠️ **已知问题**: 捕获条件不匹配实际API格式（已在v1.1修复）

## 技术支持

如需帮助或报告问题：
1. 检查本文档的故障排除章节
2. 查看浏览器控制台错误信息
3. 确认TamperMonkey和浏览器版本是否最新
