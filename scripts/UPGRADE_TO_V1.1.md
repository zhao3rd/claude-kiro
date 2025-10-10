# 升级到 v1.1 - 快速指南

## 问题描述

如果你使用 v1.0 版本，发现脚本无法捕获Yupp.ai的聊天请求，这是因为：
- ❌ v1.0 错误地检查了 `stream=true` 参数
- ✅ 实际的API URL格式是: `https://yupp.ai/chat/{uuid}` （不带stream参数）

## 如何升级

### 方法1: TamperMonkey自动更新（推荐）
1. 访问 https://yupp.ai
2. TamperMonkey会自动检测更新
3. 点击通知更新即可

### 方法2: 手动更新
1. 打开TamperMonkey Dashboard
2. 找到 "Yupp.ai Chat Capture" 脚本
3. 点击编辑
4. 全选 (Ctrl+A) 并删除旧代码
5. 复制 `YuppChatCapture.js` 的新内容
6. 粘贴并保存 (Ctrl+S)

### 方法3: 删除重装
1. 在TamperMonkey Dashboard中删除旧脚本
2. 创建新脚本
3. 粘贴完整的v1.1代码
4. 保存

## 验证升级成功

刷新Yupp.ai页面，查看控制台输出：

```
[Yupp Chat Capture] ========================================
[Yupp Chat Capture]   Yupp.ai Chat Capture v1.1 Active   👈 看这里！
[Yupp Chat Capture]   - Monitoring: POST /chat/{uuid}     👈 看这里！
[Yupp Chat Capture]   - Auto-saving captures to Downloads
[Yupp Chat Capture] ========================================
```

✅ 如果看到 `v1.1` 和 `POST /chat/{uuid}`，说明升级成功！

## 测试功能

1. 在Yupp.ai发送一条消息
2. 查看控制台，应该看到：
   ```
   [Yupp Chat Capture] 🎯 Capturing chat request: https://yupp.ai/chat/...
   [Yupp Chat Capture] 📋 Request method: POST
   [Yupp Chat Capture] 📦 Request payload captured, type: object
   [Yupp Chat Capture] 🔄 Starting to read response stream...
   [Yupp Chat Capture] ✅ Stream completed, total chunks: XX
   [Yupp Chat Capture] 💾 Saving capture data...
   [Yupp Chat Capture] 💾 Saved capture to: yupp-chat-capture-XXX.json
   ```

3. 检查浏览器下载目录，应该有新的JSON文件

## 如果仍然无法捕获

### 调试步骤：

1. **检查URL格式**：
   - 打开 Network 标签
   - 发送消息
   - 找到聊天请求
   - URL应该类似: `https://yupp.ai/chat/7e2e26d5-907f-49e7-bce0-019daf956dad`
   - 方法应该是: `POST`

2. **临时宽松模式**（仅用于调试）：
   编辑脚本，找到第41-42行：
   ```javascript
   // 临时改为宽松模式
   const shouldCapture = url.includes('/chat/') && config?.method === 'POST';
   ```

3. **查看错误信息**：
   - 控制台是否有红色错误？
   - TamperMonkey图标是否显示脚本在运行？

4. **联系支持**：
   如果以上都不行，请提供：
   - 控制台完整日志
   - Network标签中的请求URL截图
   - TamperMonkey脚本列表截图

## v1.1 新功能

除了修复捕获问题，v1.1还带来了：

1. **更详细的日志**：
   - 显示请求方法
   - 显示载荷类型
   - 实时流处理进度

2. **更好的错误处理**：
   - 改进的JSON解析
   - FormData类型支持
   - 更友好的错误提示

3. **更准确的匹配**：
   - 使用正则表达式匹配UUID
   - 明确检查POST方法
   - 避免误捕获其他请求

## 完整变更日志

请查看 `yupp-chat-capture-changelog.md` 了解详细技术变更。
