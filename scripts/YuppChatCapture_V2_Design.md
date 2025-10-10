# Yupp Chat Capture V2 - 设计文档

## 设计目标

创建一个健壮的TamperMonkey用户脚本，用于捕获Yupp.ai的聊天请求和流式响应，克服浏览器安全隔离和页面动态行为带来的挑战。

## 核心问题分析

### 问题1: TamperMonkey隔离上下文

**问题描述**: TamperMonkey脚本运行在隔离的上下文中，与页面的JavaScript上下文是分离的。

```javascript
// TamperMonkey上下文（隔离的）
window.myFunction = function() { ... }

// 页面上下文（控制台运行在这里）
myFunction()  // ❌ ReferenceError: myFunction is not defined
```

**影响**:
- 在用户脚本中定义的函数无法从浏览器控制台访问
- 无法直接与页面的全局对象交互
- 调试变得困难

**根本原因**: 安全隔离机制，防止用户脚本干扰页面功能。

### 问题2: 页面在脚本注入后覆盖fetch

**问题描述**: Yupp.ai的页面代码在我们的脚本拦截fetch后，又重新覆盖了`window.fetch`。

```javascript
// 时间线：
// 1. document-start: 我们的脚本运行，保存fetch引用
const originalFetch = window.fetch;
window.fetch = ourInterceptor;

// 2. 页面加载: Yupp.ai覆盖fetch
window.fetch = theirFrameworkFetch;  // ❌ 我们的拦截器丢失！

// 3. 用户发送消息: 我们的拦截器永远不会被调用
```

**影响**:
- 拦截到零个fetch调用
- 没有聊天请求被捕获
- 脚本看起来在运行但什么都没捕获到

**根本原因**: 现代框架（React, Next.js）通常在初始化期间包装或替换原生API。

### 问题3: 时序问题

**问题描述**: 难以确定页面何时完成对原生API的覆盖。

**挑战**:
- 页面异步加载
- 框架初始化时机不确定
- 多个脚本可能在不同时间修改fetch

## 解决方案设计

### 方案架构

```
┌─────────────────────────────────────────────────────────────┐
│                    TamperMonkey脚本                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  阶段1: 立即注入 (document-start)                    │   │
│  │  - 保存原始fetch引用                                 │   │
│  │  - 注入拦截器                                        │   │
│  │  - 导出函数到unsafeWindow                           │   │
│  └──────────────────────────────────────────────────────┘   │
│                          ↓                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  阶段2: 延迟重注入 (2秒, 5秒)                       │   │
│  │  - 检查fetch是否被覆盖                              │   │
│  │  - 必要时重新注入                                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                          ↓                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  阶段3: 持续监控 (每1秒)                            │   │
│  │  - 监控fetch.name属性                               │   │
│  │  - 检测到覆盖时自动重新注入                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 核心设计原则

#### 1. **早期捕获，延迟注入**

```javascript
// 在document-start时立即捕获原始fetch
const originalFetch = win.fetch;

// 但准备好多次重新注入
function injectInterceptor() {
    const interceptor = createFetchInterceptor(originalFetch);
    win.fetch = interceptor;
}
```

**原理**:
- 在页面可以覆盖之前保存引用
- 允许灵活的重新注入而不丢失原始引用

#### 2. **使用unsafeWindow实现跨上下文访问**

```javascript
const win = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;

// 导出函数到页面上下文
win.yuppCaptureStatus = function() { ... }
```

**原理**:
- `@grant unsafeWindow`提供对页面全局上下文的访问
- 函数变得可以从浏览器控制台访问
- 支持调试和手动控制

#### 3. **防御性重注入策略**

```javascript
// 多阶段方法：
// 1. 立即（document-start）
injectInterceptor();

// 2. 延迟（在典型框架初始化之后）
setTimeout(() => checkAndReinject(), 2000);
setTimeout(() => checkAndReinject(), 5000);

// 3. 持续监控
setInterval(() => monitorAndReinject(), 1000);
```

**原理**:
- 没有单一时机适用于所有场景
- 多次尝试提高成功率
- 持续监控处理动态变化

#### 4. **拦截器识别**

```javascript
const interceptor = createFetchInterceptor(originalFetch);
Object.defineProperty(interceptor, 'name', { value: 'interceptedFetch' });
win.fetch = interceptor;

// 稍后检查：
if (win.fetch.name !== 'interceptedFetch') {
    // 我们失去控制，重新注入！
}
```

**原理**:
- 命名函数允许检测覆盖
- 无需存储额外状态即可监控
- 简单可靠的检查机制

## 技术实现细节

### 1. 上下文访问模式

```javascript
// 模式：安全的上下文检测
const win = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;

// 优点：
// - 有无@grant unsafeWindow都能工作
// - 优雅降级
// - 所有操作的单一访问点
```

### 2. Fetch拦截器工厂

```javascript
function createFetchInterceptor(original) {
    return async function (...args) {
        // 提取请求信息
        const [resource, config] = args;
        let url = parseUrl(resource);

        // 记录每次调用以便调试
        console.log('Fetch调用:', { url, method: config?.method });

        // 条件捕获
        if (shouldCapture(url, config)) {
            return await captureRequest(original, args, url, config);
        }

        // 不变地传递
        return original.apply(this, args);
    };
}
```

**关键特性**:
- **闭包保存原始引用**: 保持对真实fetch的引用
- **透明传递**: 不匹配的请求不受影响
- **全面日志**: 每次调用都被记录用于调试
- **条件捕获**: 只处理相关请求

### 3. 流处理模式

```javascript
async function processStream(response, captureData) {
    // 克隆响应以避免消费原始响应
    const reader = response.body.getReader();
    const decoder = new TextDecoder();

    while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        captureData.responseChunks.push(chunk);
        captureData.fullResponse += chunk;
    }

    saveCapture(captureData);
}
```

**关键特性**:
- **非阻塞**: 在后台异步运行
- **渐进累积**: 从数据块构建完整响应
- **错误隔离**: 失败不影响页面功能

### 4. 监控策略

```javascript
function monitorFetchOverride() {
    let lastFetch = win.fetch;

    setInterval(() => {
        if (win.fetch !== lastFetch) {
            console.warn('Fetch发生变化！');
            if (win.fetch.name !== 'interceptedFetch') {
                injectInterceptor();
            }
            lastFetch = win.fetch;
        }
    }, 1000);
}
```

**关键特性**:
- **变化检测**: 识别fetch何时被修改
- **自动恢复**: 无需用户干预即可重新注入
- **引用跟踪**: 维护状态用于比较

## 数据流设计

### 请求捕获流程

```
用户操作（发送消息）
    ↓
页面调用fetch()
    ↓
我们的拦截器激活
    ↓
解析请求
    ├─ URL解析
    ├─ 头部提取
    └─ 载荷捕获
    ↓
条件检查
    ├─ URL包含/chat/
    └─ 方法是POST
    ↓
执行原始Fetch
    ↓
克隆响应
    ↓
返回给页面 ──────┐
                 │
    ↓           ↓
后台流处理
    ├─ 读取数据块
    ├─ 解码文本
    └─ 累积
    ↓
保存到文件
    └─ GM_download
```

### 数据结构

```javascript
const captureData = {
    // 请求元数据
    timestamp: "ISO-8601时间戳",
    requestUrl: "完整URL",
    requestMethod: "POST",
    requestHeaders: { /* 头部对象 */ },
    requestPayload: /* 解析的载荷 */,

    // 响应元数据
    responseStatus: 200,
    responseHeaders: { /* 头部对象 */ },

    // 流式数据
    responseChunks: ["chunk1", "chunk2", ...],
    fullResponse: "拼接的数据块",

    // 错误跟踪
    error: null
};
```

## 关键代码解析

### 初始化序列

```javascript
// 步骤1: 上下文设置
const win = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;
console.log('使用window:', win === window ? 'window' : 'unsafeWindow');

// 步骤2: 保存原始fetch
const originalFetch = win.fetch;
console.log('原始fetch已保存:', typeof originalFetch);

// 步骤3: 立即注入
injectInterceptor();
console.log('拦截器已安装');

// 步骤4: 延迟检查
setTimeout(() => {
    if (win.fetch.name !== 'interceptedFetch') {
        console.log('Fetch被覆盖，重新注入...');
        injectInterceptor();
    }
}, 2000);

// 步骤5: 开始监控
setTimeout(() => {
    monitorFetchOverride();
}, 5000);
```

### 捕获决策逻辑

```javascript
// 提取URL
let url = '';
if (typeof resource === 'string') {
    url = resource;
} else if (resource instanceof Request) {
    url = resource.url;
} else if (resource instanceof URL) {
    url = resource.href;
}

// 检查条件
const isChatUrl = url.includes('/chat/');
const isPost = config?.method === 'POST';

// 记录日志用于调试
console.log(`Fetch #${fetchCallCount}:`, { url, method: config?.method });

if (isChatUrl) {
    console.log('检测到聊天URL:', { url, method: config?.method, isPost });
}

// 满足条件时捕获
if (isChatUrl && isPost) {
    console.log('正在捕获请求');
    return await captureRequest(originalFetch, args, url, config);
}

// 否则透传
return originalFetch.apply(this, args);
```

### 响应克隆模式

```javascript
// 执行原始fetch
const response = await originalFetch.apply(this, args);

// 捕获响应元数据
captureData.responseStatus = response.status;
response.headers.forEach((value, key) => {
    captureData.responseHeaders[key] = value;
});

// 克隆用于后台处理
const clonedResponse = response.clone();

// 在后台处理克隆（非阻塞）
processStream(clonedResponse, captureData);

// 返回原始响应给页面（不受影响）
return response;
```

**为什么要克隆？**
- Response body只能读取一次
- 页面需要原始响应才能正常工作
- 克隆允许并行数据提取

## 性能优化

### 1. 非目标请求的最小开销

```javascript
// 快速拒绝路径
if (!url.includes('/chat/')) {
    return originalFetch.apply(this, args);
}

// 只在确定是聊天请求时处理
if (isChatUrl && isPost) {
    // 昂贵的操作放在这里
}
```

### 2. 异步流处理

```javascript
// 不阻塞原始响应
processStream(clonedResponse, captureData);  // 触发后忘记
return response;  // 立即返回给页面
```

### 3. 延迟日志

```javascript
// 仅在相关时记录详细信息
if (isChatUrl) {
    console.log('检测到聊天URL:', { url, method, isPost });
}

// 长流的进度日志
if (chunkCount % 10 === 0) {
    console.log(`已接收${chunkCount}个数据块`);
}
```

## 错误处理策略

### 1. 优雅降级

```javascript
try {
    // 捕获逻辑
} catch (error) {
    console.error('捕获错误:', error);
    captureData.error = error.message;
    saveCapture(captureData);  // 保存部分数据
    throw error;  // 重新抛出以维持页面功能
}
```

### 2. 隔离失败

```javascript
async function processStream(response, captureData) {
    try {
        // 流处理
    } catch (error) {
        console.error('流错误:', error);
        captureData.error = error.message;
        saveCapture(captureData);  // 保存我们已有的数据
        // 不抛出 - 页面已经有它的响应了
    }
}
```

### 3. 安全的函数导出

```javascript
win.yuppCaptureStatus = function () {
    try {
        const status = { /* ... */ };
        console.log('状态:', status);
        return { status, captures: capturedSessions };
    } catch (error) {
        console.error('状态错误:', error);
        return { error: error.message };
    }
};
```

## 安全考虑

### 1. 不暴露敏感数据

```javascript
// 不要将完整载荷记录到控制台
console.log('载荷已捕获');  // 不是: console.log(payload)

// 仅文件下载，不进行网络传输
GM_download({ /* ... */ });  // 仅本地
```

### 2. 最小权限要求

```javascript
// @grant        GM_download        - 用于文件保存
// @grant        unsafeWindow        - 用于上下文访问
// 不需要网络权限
// 不需要存储权限
```

### 3. 非侵入式监控

```javascript
// 只读日志
console.log('Fetch调用:', { url, method });

// 不修改请求
return originalFetch.apply(this, args);  // 不变
```

## 调试支持

### 1. 全面的日志

```javascript
// 初始化
console.log('[Yupp Capture V2] 脚本初始化...');
console.log('[Yupp Capture V2] 使用window:', win === window ? 'window' : 'unsafeWindow');

// 操作
console.log('[Yupp Capture V2] 📡 Fetch #15:', { url, method });
console.log('[Yupp Capture V2] 🎯 检测到聊天URL');

// 结果
console.log('[Yupp Capture V2] 💾 已保存: filename.json');
```

### 2. 状态内省

```javascript
win.yuppCaptureStatus = function () {
    return {
        status: {
            totalCaptures: capturedSessions.length,
            fetchCalls: fetchCallCount,
            injections: injectionCount,
            currentFetchName: win.fetch.name,
            isOurInterceptor: win.fetch.name === 'interceptedFetch'
        },
        captures: capturedSessions
    };
};
```

### 3. 手动控制

```javascript
// 强制重新注入
win.yuppForceCapture = function () {
    console.log('强制重新注入...');
    injectInterceptor();
};

// 导出捕获数据
win.yuppExportAll = function () {
    // 批量导出
};
```

## 扩展性设计

### 1. 可插拔的捕获条件

```javascript
function shouldCapture(url, config) {
    // 容易修改条件
    const isChatUrl = url.includes('/chat/');
    const isPost = config?.method === 'POST';
    return isChatUrl && isPost;
}
```

### 2. 模块化处理

```javascript
// 容易添加新的处理步骤
async function captureRequest(originalFetch, args, url, config) {
    captureHeaders(config);      // 模块化
    capturePayload(config);       // 模块化
    const response = await executeRequest(originalFetch, args);
    captureResponse(response);    // 模块化
    processStream(response);      // 模块化
    return response;
}
```

### 3. 灵活的存储

```javascript
function saveCapture(captureData) {
    // 当前: 通过GM_download下载文件
    // 容易添加: localStorage, IndexedDB, 云上传
    GM_download({ /* ... */ });
}
```

## 最佳实践总结

### 1. **尽早保存原始引用**
```javascript
// 好: 在脚本开始时保存
const originalFetch = win.fetch;

// 差: 稍后保存
setTimeout(() => {
    const originalFetch = win.fetch;  // 可能已经被覆盖！
}, 1000);
```

### 2. **使用命名函数进行检测**
```javascript
// 好: 可以检测覆盖
Object.defineProperty(interceptor, 'name', { value: 'interceptedFetch' });
win.fetch = interceptor;

// 稍后:
if (win.fetch.name !== 'interceptedFetch') { /* 检测到覆盖 */ }

// 差: 匿名函数，无法检测
win.fetch = async function(...args) { /* ... */ };
```

### 3. **多阶段注入**
```javascript
// 好: 多次尝试
injectInterceptor();  // 立即
setTimeout(() => checkAndReinject(), 2000);  // 延迟
setInterval(() => monitorAndReinject(), 1000);  // 持续

// 差: 单次尝试
injectInterceptor();  // 可能稍后被覆盖
```

### 4. **使用unsafeWindow进行控制台访问**
```javascript
// 好: 导出到unsafeWindow
const win = unsafeWindow;
win.myFunction = function() { /* ... */ };

// 差: 导出到隔离的window
window.myFunction = function() { /* ... */ };  // 控制台无法访问
```

### 5. **克隆响应进行非阻塞处理**
```javascript
// 好: 克隆并异步处理
const clonedResponse = response.clone();
processStream(clonedResponse, captureData);  // 非阻塞
return response;

// 差: 阻塞原始响应
await processStream(response, captureData);  // 页面必须等待
return response;
```

## 版本演进

### V1.0 → V1.1
- 修复URL匹配模式（UUID正则）
- 增强日志
- 改进错误处理

**问题**:
- 函数无法从控制台访问
- 拦截到零个fetch调用

### V1.1 → V2.0
- **新增**: unsafeWindow支持跨上下文访问
- **新增**: 原始fetch引用保存
- **新增**: 多阶段注入策略
- **新增**: 持续监控和自动重新注入
- **新增**: 拦截器命名用于检测
- **变更**: 命令名称（yuppCaptureStatus vs checkCaptureStatus）
- **修复**: 页面脚本覆盖fetch的问题
- **修复**: 控制台函数访问问题

## 核心设计思想总结

V2设计的核心思想是：

1. **早捕获，晚注入** - 保存原始引用，灵活重注入
2. **跨上下文访问** - 使用unsafeWindow打破隔离
3. **防御性编程** - 多次尝试，持续监控，自动恢复
4. **透明拦截** - 不影响页面功能，最小化性能开销
5. **调试友好** - 详细日志，状态查询，手动控制

这些设计使脚本能够在复杂的现代Web应用环境中可靠工作，同时保持代码的可维护性和可扩展性。

## 架构决策记录

### ADR-001: 选择unsafeWindow而不是window
**上下文**: 需要从控制台访问脚本函数
**决策**: 使用`@grant unsafeWindow`
**后果**: 函数可从控制台访问，但需要额外权限

### ADR-002: 多阶段注入而不是单次注入
**上下文**: 页面在不确定时间覆盖fetch
**决策**: 立即注入 + 2秒延迟 + 5秒延迟 + 持续监控
**后果**: 更高的成功率，但代码更复杂

### ADR-003: 命名函数用于检测
**上下文**: 需要检测fetch是否被覆盖
**决策**: 使用`Object.defineProperty`设置函数名
**后果**: 简单的检测机制，无需额外状态

### ADR-004: 克隆响应而不是共享
**上下文**: 需要读取响应但不影响页面
**决策**: 使用`response.clone()`
**后果**: 非阻塞，但额外的内存使用

### ADR-005: 本地存储而不是云存储
**上下文**: 需要保存捕获数据
**决策**: 使用`GM_download`本地下载
**后果**: 隐私保护，但无法跨设备访问
