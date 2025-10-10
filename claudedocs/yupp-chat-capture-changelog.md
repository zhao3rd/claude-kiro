# Yupp Chat Capture - 更新日志

## v1.1 (2025-10-10) - Bug Fix Release

### 🐛 Bug修复

**问题**: 脚本无法捕获聊天请求
- **原因**: 捕获条件要求URL中必须包含 `stream=true` 参数，但实际的Yupp.ai API URL格式为 `https://yupp.ai/chat/{uuid}`，不包含该参数
- **修复**: 改用正则表达式匹配UUID格式的chat URL，并检查POST方法

**修改前**:
```javascript
const shouldCapture = url.includes('/chat/') && url.includes('stream=true');
```

**修改后**:
```javascript
const chatUrlPattern = /\/chat\/[a-f0-9-]{36}/i;
const shouldCapture = chatUrlPattern.test(url) && config?.method === 'POST';
```

### ✨ 功能改进

1. **增强的请求体捕获**:
   - 改进JSON解析错误处理
   - 添加FormData类型支持
   - 更好的类型检测和转换

2. **详细的调试日志**:
   - 添加请求方法日志输出
   - 添加请求载荷类型识别
   - 流处理进度实时显示（每10个chunk）
   - 流完成时显示总chunk数和字节数

3. **改进的流处理监控**:
   - 实时显示已接收的chunk数量
   - 显示累计接收的字节数
   - 更清晰的流处理状态输出

### 📊 控制台输出改进

**新的日志示例**:
```
[Yupp Chat Capture] 🎯 Capturing chat request: https://yupp.ai/chat/7e2e26d5-907f-49e7-bce0-019daf956dad
[Yupp Chat Capture] 📋 Request method: POST
[Yupp Chat Capture] 📦 Request payload captured, type: object
[Yupp Chat Capture] 🔄 Starting to read response stream...
[Yupp Chat Capture] 📊 Received 10 chunks, 2048 bytes
[Yupp Chat Capture] 📊 Received 20 chunks, 4096 bytes
[Yupp Chat Capture] ✅ Stream completed, total chunks: 25
[Yupp Chat Capture] 💾 Saving capture data...
[Yupp Chat Capture] 💾 Saved capture to: yupp-chat-capture-2025-10-10T12-34-56-789Z.json
```

### 🔧 技术变更

1. **URL匹配模式**:
   - 使用正则表达式精确匹配UUID格式
   - 支持大小写不敏感匹配
   - 必须是POST方法才触发捕获

2. **错误处理**:
   - 嵌套try-catch确保请求体解析失败时有降级方案
   - 流处理错误时仍会保存已捕获的数据
   - 更详细的错误日志输出

3. **性能优化**:
   - 每10个chunk才输出一次进度日志，减少控制台噪音
   - 保持原有的非阻塞流处理机制

## v1.0 (2025-10-10) - Initial Release

### ✨ 初始功能

- 自动拦截Yupp.ai聊天请求
- 捕获完整的请求和流式响应
- 自动保存为JSON文件
- 控制台命令支持（exportAllCaptures, checkCaptureStatus）
- 无损拦截，不影响页面功能

---

## 升级指南

### 从 v1.0 升级到 v1.1

1. **自动升级**:
   - TamperMonkey会在访问Yupp.ai时自动检测更新
   - 或者在TamperMonkey Dashboard中手动检查更新

2. **手动升级**:
   - 删除旧版本脚本
   - 复制新版本脚本内容
   - 在TamperMonkey中创建新脚本并粘贴
   - 保存并启用

3. **验证升级**:
   - 刷新Yupp.ai页面
   - 查看控制台，应显示 `v1.1 Active`
   - 启动消息中应显示 `Monitoring: POST /chat/{uuid}`

### 兼容性说明

- ✅ 向后兼容：v1.1生成的JSON文件格式与v1.0完全相同
- ✅ 数据迁移：无需迁移，旧版本捕获的数据仍然有效
- ✅ API兼容：所有控制台命令保持不变

## 已知问题

目前没有已知问题。如果发现问题，请查看控制台的详细日志进行排查。

## 计划功能 (Future Roadmap)

- [ ] 支持自定义捕获过滤条件
- [ ] 添加数据统计和可视化
- [ ] 支持实时预览捕获的响应内容
- [ ] 添加捕获历史记录管理界面
- [ ] 支持导出为其他格式（CSV, XML等）
