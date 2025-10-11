# Yupp.ai Full Request Capture V3 - 使用指南

## 🎯 V3版本新特性

V3在V2的基础上实现了**完整的网络请求记录系统**，支持：

### ✨ 核心功能
- ✅ **全接口捕获** - 记录所有yupp.ai请求，不仅限于/chat/
- ✅ **完整数据记录** - URL参数、Headers、Cookies、响应时间
- ✅ **录制控制** - 启动/停止/切换录制状态
- ✅ **高级过滤** - 按路径、方法、状态码过滤
- ✅ **统计分析** - 请求分布、平均响应时间、错误率
- ✅ **灵活导出** - 全量导出、过滤导出、分类导出

## 📦 安装

### 1. 安装脚本
1. 打开TamperMonkey Dashboard
2. 创建新脚本
3. 复制 `YuppChatCapture_V3.js` 的完整内容
4. 保存并启用

### 2. 验证安装
刷新Yupp.ai页面，查看：
- 控制台应显示 `[Yupp Capture V3] Full Request Capture V3 Active`
- 页面标题前应显示 `🔴` （录制中）

## 🎮 控制命令

### 录制控制

#### 启动录制
```javascript
yuppStartRecording()
```
- 开始捕获所有请求
- 页面标题显示 `🔴`
- 控制台绿色提示

#### 停止录制
```javascript
yuppStopRecording()
```
- 停止捕获新请求
- 页面标题显示 `⏸️`
- 控制台橙色提示

#### 切换录制状态
```javascript
yuppToggleRecording()
```
- 在录制/停止之间切换
- 返回当前状态和已捕获数量

**返回示例**:
```javascript
{
  status: 'recording',  // 或 'stopped'
  captured: 42          // 已捕获数量
}
```

## ⚙️ 配置选项

### 捕获模式

#### 设置捕获模式
```javascript
yuppSetCaptureMode('all')       // 捕获所有请求
yuppSetCaptureMode('chat-only') // 仅捕获/chat/请求（V2行为）
yuppSetCaptureMode('custom')    // 使用自定义过滤器
```

**模式说明**:
- `all` - 默认模式，捕获所有yupp.ai域名请求
- `chat-only` - 兼容模式，仅捕获POST /chat/请求
- `custom` - 自定义模式，使用过滤器配置

### 过滤器配置

#### 设置过滤器
```javascript
yuppSetFilter({
    paths: ['/api/', '/chat/'],           // 路径过滤（支持正则）
    methods: ['POST', 'GET'],             // HTTP方法过滤
    statusCodes: [200, 201, 400, 500]     // 状态码过滤
})
```

**过滤器示例**:

```javascript
// 只捕获API请求
yuppSetFilter({ paths: ['/api/'] })

// 只捕获POST和PUT请求
yuppSetFilter({ methods: ['POST', 'PUT'] })

// 只捕获错误响应
yuppSetFilter({ statusCodes: [400, 401, 403, 404, 500, 502, 503] })

// 组合过滤
yuppSetFilter({
    paths: ['/chat/', '/api/messages'],
    methods: ['POST'],
    statusCodes: [200]
})

// 清空过滤器（捕获所有）
yuppSetFilter({ paths: [], methods: [], statusCodes: [] })
```

## 📊 统计分析

### 查看统计信息
```javascript
yuppGetStats()
```

**返回信息**:
```javascript
{
  totalCaptures: 156,
  isRecording: true,
  captureMode: 'all',
  fetchCalls: 342,

  byMethod: {
    GET: 89,
    POST: 67
  },

  byPath: {
    '/chat/xxx': 45,
    '/api/models': 23,
    '/api/settings': 12
  },

  byStatus: {
    200: 134,
    201: 12,
    400: 5,
    500: 2
  },

  avgDuration: '245.67ms',
  errors: 3
}
```

### 查看状态
```javascript
yuppCaptureStatus()
```

**返回信息**:
```javascript
{
  status: {
    isRecording: true,
    captureMode: 'all',
    totalCaptures: 156,
    fetchCalls: 342,
    injections: 2,
    filter: { paths: [], methods: [], statusCodes: [] },
    maxCaptures: 1000,
    currentFetchName: 'yuppInterceptor',
    isOurInterceptor: true
  },
  captures: [/* 所有捕获数据 */]
}
```

## 💾 导出功能

### 导出所有捕获
```javascript
yuppExportAll()
```
- 导出所有捕获的请求到JSON文件
- 弹出保存对话框
- 文件名: `yupp-captures-all-{timestamp}.json`

### 过滤导出
```javascript
yuppExportFiltered({
    method: 'POST',                    // 按方法过滤
    path: '/chat/',                    // 按路径过滤
    startTime: '2025-10-10T10:00:00',  // 时间范围开始
    endTime: '2025-10-10T12:00:00'     // 时间范围结束
})
```

**导出示例**:

```javascript
// 只导出POST请求
yuppExportFiltered({ method: 'POST' })

// 只导出聊天相关请求
yuppExportFiltered({ path: '/chat/' })

// 导出特定时间段
yuppExportFiltered({
    startTime: '2025-10-10T10:00:00',
    endTime: '2025-10-10T12:00:00'
})

// 组合条件
yuppExportFiltered({
    method: 'POST',
    path: '/api/',
    startTime: '2025-10-10T10:00:00'
})
```

### 清空捕获
```javascript
yuppClearCaptures()
```
- 清除内存中的所有捕获数据
- 释放内存空间
- 返回清除的数量

## 📋 捕获数据结构

每条捕获记录包含：

```javascript
{
  // 唯一标识
  requestId: 'req_123',
  timestamp: '2025-10-10T12:34:56.789Z',

  // 时间信息
  timing: {
    startTime: 1234.56,          // 开始时间（performance.now()）
    responseTime: 1456.78,       // 响应时间
    endTime: 1567.89,           // 结束时间
    duration: 222.22,            // 响应耗时（ms）
    totalDuration: 333.33        // 总耗时（ms）
  },

  // 请求信息
  requestUrl: 'https://yupp.ai/chat/xxx',
  requestMethod: 'POST',
  requestPath: '/chat/xxx',
  requestParams: { stream: 'true' },    // URL参数
  requestSearch: '?stream=true',
  requestHash: '',
  requestHeaders: {
    'content-type': 'application/json',
    'authorization': 'Bearer xxx'
  },
  requestPayload: { /* 请求体 */ },
  requestCookies: {                      // 请求时的cookies
    'session': 'xxx',
    'token': 'yyy'
  },

  // 响应信息
  responseStatus: 200,
  responseHeaders: {
    'content-type': 'text/x-component'
  },
  responseChunks: ['chunk1', 'chunk2'],  // 流式响应块
  fullResponse: 'complete response',

  // 元数据
  error: null,
  captureMode: 'all'
}
```

## 🔍 使用场景

### 场景1: 调试聊天功能
```javascript
// 1. 设置为只捕获聊天
yuppSetCaptureMode('chat-only')

// 2. 发送消息
// （在页面上操作）

// 3. 查看统计
yuppGetStats()

// 4. 导出聊天数据
yuppExportFiltered({ path: '/chat/' })
```

### 场景2: 分析API调用
```javascript
// 1. 设置为捕获所有API
yuppSetCaptureMode('all')
yuppSetFilter({ paths: ['/api/'] })

// 2. 正常使用应用
// （在页面上操作）

// 3. 查看API统计
yuppGetStats()

// 4. 导出API数据
yuppExportFiltered({ path: '/api/' })
```

### 场景3: 监控错误
```javascript
// 1. 只捕获错误响应
yuppSetCaptureMode('custom')
yuppSetFilter({
    statusCodes: [400, 401, 403, 404, 500, 502, 503]
})

// 2. 使用应用
// （触发各种操作）

// 3. 查看错误
yuppGetStats()

// 4. 导出错误数据
yuppExportAll()
```

### 场景4: 性能分析
```javascript
// 1. 开始录制
yuppStartRecording()

// 2. 执行性能测试
// （在页面上操作）

// 3. 查看性能统计
const stats = yuppGetStats()
console.log('平均响应时间:', stats.avgDuration)

// 4. 导出性能数据
yuppExportAll()

// 5. 分析JSON文件中的timing字段
```

### 场景5: 短时间记录
```javascript
// 1. 先停止录制
yuppStopRecording()

// 2. 清空之前的数据
yuppClearCaptures()

// 3. 准备好要测试的操作

// 4. 开始录制
yuppStartRecording()

// 5. 执行操作
// （在页面上操作）

// 6. 停止录制
yuppStopRecording()

// 7. 导出精确的数据
yuppExportAll()
```

## ⚠️ 注意事项

### 内存管理
- **默认限制**: 最多存储1000条记录
- **自动清理**: 超过限制时自动删除最旧的记录
- **手动清理**: 使用 `yuppClearCaptures()` 释放内存

### 隐私提醒
- 捕获的数据包含完整的cookies和headers
- 可能包含敏感信息（token、session等）
- 请妥善保管导出的JSON文件
- 不要分享包含敏感信息的文件

### 性能影响
- 录制所有请求会占用一定内存
- 流式响应会存储所有数据块
- 建议按需使用过滤器减少捕获量
- 不需要时及时停止录制

## 🆚 版本对比

| 功能 | V2 | V3 |
|------|----|----|
| **捕获范围** | 仅/chat/请求 | 所有请求 |
| **URL参数** | ❌ | ✅ 完整解析 |
| **Cookies** | ❌ | ✅ 每次请求 |
| **录制控制** | ❌ | ✅ start/stop/toggle |
| **捕获模式** | 固定 | ✅ 3种模式 |
| **过滤器** | ❌ | ✅ 路径/方法/状态 |
| **统计分析** | ❌ | ✅ 详细统计 |
| **过滤导出** | ❌ | ✅ 按条件导出 |
| **时间追踪** | ❌ | ✅ 完整timing |
| **内存管理** | ❌ | ✅ 自动限制 |
| **状态指示** | ❌ | ✅ 页面标题 |

## 📚 常见问题

### Q1: 如何只记录特定接口？
```javascript
// 方法1: 使用custom模式
yuppSetCaptureMode('custom')
yuppSetFilter({ paths: ['/api/chat', '/api/messages'] })

// 方法2: 使用正则匹配
yuppSetFilter({ paths: ['/api/.*'] })
```

### Q2: 如何暂时停止记录？
```javascript
// 停止
yuppStopRecording()

// 继续
yuppStartRecording()

// 或直接切换
yuppToggleRecording()
```

### Q3: 捕获的数据太多怎么办？
```javascript
// 1. 清空已有数据
yuppClearCaptures()

// 2. 设置过滤器
yuppSetFilter({ methods: ['POST'] })

// 3. 或使用chat-only模式
yuppSetCaptureMode('chat-only')
```

### Q4: 如何导出特定时间段的数据？
```javascript
yuppExportFiltered({
    startTime: '2025-10-10T14:00:00',
    endTime: '2025-10-10T15:00:00'
})
```

### Q5: 页面标题的符号是什么意思？
- `🔴` - 正在录制
- `⏸️` - 已停止录制

## 🔧 高级技巧

### 自动化脚本
```javascript
// 在控制台中创建自动化流程
function captureTest() {
    // 1. 清空旧数据
    yuppClearCaptures();

    // 2. 设置过滤
    yuppSetFilter({ paths: ['/chat/'] });

    // 3. 开始录制
    yuppStartRecording();

    console.log('✅ 开始测试，请执行操作...');

    // 4. 30秒后自动停止并导出
    setTimeout(() => {
        yuppStopRecording();
        const stats = yuppGetStats();
        console.log('📊 捕获了', stats.totalCaptures, '条记录');
        yuppExportAll();
    }, 30000);
}

// 运行测试
captureTest();
```

### 实时监控
```javascript
// 每10秒显示统计
setInterval(() => {
    const stats = yuppGetStats();
    console.log(`📊 实时统计: ${stats.totalCaptures} 条记录, 平均 ${stats.avgDuration}`);
}, 10000);
```

### 批量分析
```javascript
// 获取所有数据
const { captures } = yuppCaptureStatus();

// 分析响应时间
const slowRequests = captures.filter(c =>
    c.timing?.totalDuration > 1000 // 超过1秒
);
console.log('慢请求:', slowRequests.length);

// 分析错误
const errors = captures.filter(c => c.error || c.responseStatus >= 400);
console.log('错误请求:', errors.length);
```

## 📝 总结

V3提供了完整的网络请求记录和分析能力：
- 🎮 **灵活控制** - 随时启停，按需过滤
- 📊 **深度分析** - 统计、分类、性能追踪
- 💾 **便捷导出** - 全量、过滤、定制导出
- 🔒 **隐私安全** - 本地存储，数据自控

享受强大的调试和分析能力！🚀
