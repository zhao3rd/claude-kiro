# Yupp Chat Capture V2 - 使用指南

## 🎯 V2版本解决的问题

根据你的反馈，V2版本针对性地解决了以下问题：

### 问题1: 函数在控制台无法访问
**原因**: TamperMonkey运行在隔离上下文，控制台在页面上下文
**解决**: 使用 `@grant unsafeWindow` 将函数暴露到页面上下文

### 问题2: 没有拦截到任何fetch调用
**原因**: Yupp.ai页面在加载后覆盖了fetch函数
**解决**:
- 保存原始fetch引用
- 延迟注入（2秒、5秒）
- 持续监控fetch变化，自动重新注入

## 📦 安装步骤

### 1. 移除旧版本
在TamperMonkey Dashboard中：
- 禁用或删除 "Yupp.ai Chat Capture" (v1.x)
- 禁用或删除 "Yupp.ai Chat Capture (Debug)" (v1.2-debug)

### 2. 安装V2
1. 创建新脚本
2. 复制 `YuppChatCapture_V2.js` 的完整内容
3. 保存并启用

### 3. 验证安装
刷新Yupp.ai页面，查看控制台：

```
[Yupp Capture V2] ================================
[Yupp Capture V2] Script initializing...
[Yupp Capture V2] Using window: unsafeWindow     👈 关键！
[Yupp Capture V2] ================================
[Yupp Capture V2] Original fetch saved: function
[Yupp Capture V2] 💉 Injecting interceptor #1...
[Yupp Capture V2] ✅ Interceptor installed
[Yupp Capture V2] ========================================
[Yupp Capture V2]   Yupp.ai Chat Capture V2 Active
[Yupp Capture V2]   - Commands (use these in console):
[Yupp Capture V2]     * yuppCaptureStatus()        👈 新命令！
[Yupp Capture V2]     * yuppExportAll()
[Yupp Capture V2]     * yuppForceCapture()
[Yupp Capture V2] ========================================
```

### 4. 测试函数访问
3秒后会自动测试，你应该看到：
```
[Yupp Capture V2] 🧪 Testing function access...
  - yuppCaptureStatus: function     👈 如果是"function"就成功了！
  - yuppExportAll: function
  - yuppForceCapture: function
[Yupp Capture V2] Try running: yuppCaptureStatus()
```

### 5. 在控制台运行命令
现在应该可以运行了：
```javascript
yuppCaptureStatus()
```

应该返回：
```javascript
[Yupp Capture V2] 📊 Status: {
  totalCaptures: 0,
  fetchCalls: X,        // 应该大于0！
  xhrCalls: 0,
  injections: 1,
  currentFetchName: "interceptedFetch",
  isOurInterceptor: true
}
```

## 🔍 预期行为

### 页面加载时
```
[Yupp Capture V2] 💉 Injecting interceptor #1...
[Yupp Capture V2] ✅ Interceptor installed
[Yupp Capture V2] ⏰ Delayed injection check...     (2秒后)
[Yupp Capture V2] ⏰ Second delayed check...        (5秒后)
```

### 如果页面覆盖了fetch（很可能！）
```
[Yupp Capture V2] ⏰ Delayed injection check...
[Yupp Capture V2] 🔄 Fetch was overridden, re-injecting...
[Yupp Capture V2] 💉 Injecting interceptor #2...
[Yupp Capture V2] ✅ Interceptor installed
```

### 发送消息时
```
[Yupp Capture V2] 📡 Fetch #15: {
  url: "https://yupp.ai/chat/7e2e26d5...",
  method: "POST"
}
[Yupp Capture V2] 🎯 CHAT URL DETECTED: {
  url: "https://yupp.ai/chat/7e2e26d5-907f-49e7-bce0-019daf956dad",
  method: "POST",
  isPost: true
}
[Yupp Capture V2] ✅ CAPTURING request
[Yupp Capture V2] 📦 Payload captured
[Yupp Capture V2] 🔄 Reading stream...
[Yupp Capture V2] ✅ Stream complete, chunks: 25
[Yupp Capture V2] 💾 Saved: yupp-chat-capture-XXX.json
```

## 🎮 新命令

### yuppCaptureStatus()
查看完整状态，包括：
- `fetchCalls`: 拦截到的fetch调用总数
- `injections`: 注入次数
- `isOurInterceptor`: 是否使用我们的拦截器

```javascript
yuppCaptureStatus()
```

### yuppExportAll()
导出所有捕获的数据：
```javascript
yuppExportAll()
```

### yuppForceCapture()
手动强制重新注入拦截器：
```javascript
yuppForceCapture()
```

## 🐛 故障排除

### 情况1: 仍然没有fetch调用
**症状**: `fetchCalls: 0`

**检查**:
1. 运行 `yuppCaptureStatus()` 查看 `isOurInterceptor`
2. 如果是 `false`，运行 `yuppForceCapture()`
3. 再发送消息测试

### 情况2: 有fetch调用但不是聊天请求
**症状**:
```
[Yupp Capture V2] 📡 Fetch #15: {
  url: "https://yupp.ai/api/something",  // 不是/chat/
  method: "GET"
}
```

**说明**: 这是正常的，脚本只捕获包含 `/chat/` 的POST请求。继续发送聊天消息测试。

### 情况3: 检测到CHAT URL但没有捕获
**症状**:
```
[Yupp Capture V2] 🎯 CHAT URL DETECTED: {...}
但没有后续的 "CAPTURING request" 日志
```

**原因**: `isPost` 可能是 `false`

**解决**: 查看日志中的 `method` 字段，如果不是POST，告诉我具体是什么方法。

### 情况4: 函数仍然未定义
**症状**: `yuppCaptureStatus() -> ReferenceError`

**检查**:
1. 确认日志中显示 `Using window: unsafeWindow`
2. 如果显示 `Using window: window`，说明 `@grant unsafeWindow` 没生效
3. 尝试在控制台运行：
   ```javascript
   console.log(typeof yuppCaptureStatus);
   console.log(typeof unsafeWindow.yuppCaptureStatus);
   ```

## 📊 成功标志

你应该看到以下标志表明V2工作正常：

✅ **初始化成功**:
- `Using window: unsafeWindow`
- `Interceptor installed`

✅ **函数可访问**:
- `yuppCaptureStatus: function`
- 控制台能运行 `yuppCaptureStatus()`

✅ **拦截生效**:
- `fetchCalls` > 0
- `isOurInterceptor: true`

✅ **捕获成功**:
- 看到 `CHAT URL DETECTED`
- 看到 `CAPTURING request`
- 下载目录有JSON文件

## 🔬 高级调试

如果V2仍然不工作，请提供：

1. **完整的初始化日志**（从刷新到3秒测试）
2. **yuppCaptureStatus()的完整输出**
3. **发送消息后的所有日志**
4. **Network标签中的聊天请求详情**

## 💡 V2的关键改进

| 改进点 | V1.x | V2 |
|--------|------|-----|
| **函数访问** | ❌ 控制台无法调用 | ✅ 使用unsafeWindow |
| **Fetch拦截** | ❌ 被页面覆盖 | ✅ 监控并重新注入 |
| **初始注入** | 仅document-start | ✅ + 2秒 + 5秒延迟 |
| **持续监控** | 无 | ✅ 每秒检查覆盖 |
| **调试信息** | 部分 | ✅ 记录所有fetch |
| **原始fetch** | 可能丢失 | ✅ 立即保存引用 |

现在试试V2版本，应该能看到fetch调用了！🎯
