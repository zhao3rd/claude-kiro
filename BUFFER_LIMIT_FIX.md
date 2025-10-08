# Buffer Limit Fix

## 问题描述

应用在处理 `/v1/messages/count_tokens` 请求时出现以下错误：

```
org.springframework.core.io.buffer.DataBufferLimitException: Exceeded limit on max bytes to buffer : 262144
```

这个错误表示请求体超过了默认的缓冲区限制（262144 字节 = 256KB）。

## 根本原因

Spring WebFlux 应用有两个不同的缓冲区配置：

1. **出站请求**（WebClient 调用外部服务）：已在 `WebClientConfig` 中配置为 32MB
2. **入站请求**（接收客户端请求）：使用默认值 256KB ❌

错误发生在入站请求处理时，因为没有配置服务器端的编解码器缓冲区大小。

## 解决方案

创建了新的配置类 `WebFluxConfig.java`，实现 `WebFluxConfigurer` 接口来配置服务器端的编解码器：

```java
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Set max in-memory size to 32MB to handle large requests
        // This applies to incoming requests to the server
        configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024);
    }
}
```

## 配置说明

### 修改前
- **WebClient（出站）**: 32MB ✅
- **Server（入站）**: 256KB（默认）❌

### 修改后
- **WebClient（出站）**: 32MB ✅
- **Server（入站）**: 32MB ✅

## 文件变更

新增文件：
- `src/main/java/org/yanhuang/ai/config/WebFluxConfig.java`
- `src/test/java/org/yanhuang/ai/config/WebFluxConfigTest.java`

修改文件：
- `src/main/java/org/yanhuang/ai/config/WebClientConfig.java` - 添加注释说明
- `src/main/resources/application.yml` - 添加 `spring.codec.max-in-memory-size` 配置

## 验证方法

1. 重启应用
2. 发送大于 256KB 的请求到 `/v1/messages/count_tokens` 端点
3. 确认不再出现 `DataBufferLimitException` 错误

## 相关配置

### application.yml
在配置文件中添加全局缓冲区大小设置：
```yaml
spring:
  codec:
    max-in-memory-size: 32MB
```

### WebClientConfig.java
配置 WebClient（出站请求）的缓冲区大小：
```java
ExchangeStrategies strategies = ExchangeStrategies.builder()
    .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
    .build();
```

### WebFluxConfig.java（新增）
配置 Server（入站请求）的缓冲区大小（编程方式）：
```java
configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024);
```

**注意**：application.yml 中的配置和 WebFluxConfig.java 中的配置是互补的，两者都会生效。建议保留两种配置方式以确保兼容性。

## 注意事项

1. 32MB 的缓冲区大小适用于大多数场景，如果需要处理更大的请求，可以进一步增加这个值
2. 增加缓冲区大小会增加内存使用，需要根据实际情况调整
3. 建议在生产环境中监控内存使用情况

## 参考

- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-codecs-limits)
- [DataBufferLimitException](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/io/buffer/DataBufferLimitException.html)

