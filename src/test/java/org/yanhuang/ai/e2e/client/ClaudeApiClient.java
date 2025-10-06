package org.yanhuang.ai.e2e.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 真实Claude API客户端，用于E2E测试
 * 提供同步和异步调用能力，支持流式响应
 */
@Component
public class ClaudeApiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int timeoutSeconds;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    public ClaudeApiClient(
            @Value("${server.port:8080}") int serverPort,
            @Value("${test.webclient.timeout:30s}") Duration timeout,
            @Value("${app.api-key:sk-testing}") String apiKey,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = String.format("http://localhost:%d", serverPort);
        this.timeoutSeconds = (int) timeout.getSeconds();

        this.webClient = WebClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * 发送聊天完成请求（同步）
     */
    public Mono<JsonNode> createChatCompletion(JsonNode request) {
        log.info("发送聊天完成请求 #{} - 超时: {}秒", callCounter.incrementAndGet(), timeoutSeconds);

        return webClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(response -> log.info("聊天完成请求成功 #{}", callCounter.get()))
                .doOnError(error -> log.error("聊天完成请求失败 #{} - 错误: {}", callCounter.get(), error.getMessage()))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .doBeforeRetry(retrySignal -> log.warn("重试聊天完成请求 #{} - 尝试: {}",
                                callCounter.get(), retrySignal.totalRetries() + 1)))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("HTTP错误 #{} - 状态码: {}, 响应: {}",
                            callCounter.get(), ex.getStatusCode(), ex.getResponseBodyAsString());
                    return new RuntimeException("聊天完成请求失败: " + ex.getStatusCode(), ex);
                });
    }

    /**
     * 发送聊天完成请求（流式）
     */
    public Flux<JsonNode> createChatCompletionStream(JsonNode request) {
        log.info("发送流式聊天完成请求 #{} - 超时: {}秒", callCounter.incrementAndGet(), timeoutSeconds);

        // 为流式请求添加stream: true
        ObjectNode streamRequest = objectMapper.getNodeFactory().objectNode().setAll((ObjectNode) request);
        streamRequest.put("stream", true);

        return webClient.post()
                .uri("/v1/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)  // 不需要添加stream字段，直接使用原请求
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .map(this::parseServerSentEvent)
                .filter(event -> !event.data().isEmpty())
                .map(event -> parseJsonData(event.data()))
                .filter(jsonNode -> jsonNode != null)
                .doOnComplete(() -> log.info("流式聊天完成请求完成 #{}", callCounter.get()))
                .doOnError(error -> log.error("流式聊天完成请求失败 #{} - 错误: {}", callCounter.get(), error.getMessage()))
                .retryWhen(Retry.backoff(1, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(5))
                        .doBeforeRetry(retrySignal -> log.warn("重试流式聊天完成请求 #{} - 尝试: {}",
                                callCounter.get(), retrySignal.totalRetries() + 1)))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("流式请求HTTP错误 #{} - 状态码: {}, 响应: {}",
                            callCounter.get(), ex.getStatusCode(), ex.getResponseBodyAsString());
                    return new RuntimeException("流式聊天完成请求失败: " + ex.getStatusCode(), ex);
                });
    }

    /**
     * 发送工具调用请求
     */
    public Mono<JsonNode> createToolCall(JsonNode request) {
        log.info("发送工具调用请求 #{} - 超时: {}秒", callCounter.incrementAndGet(), timeoutSeconds);

        return webClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(response -> log.info("工具调用请求成功 #{}", callCounter.get()))
                .doOnError(error -> log.error("工具调用请求失败 #{} - 错误: {}", callCounter.get(), error.getMessage()))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .doBeforeRetry(retrySignal -> log.warn("重试工具调用请求 #{} - 尝试: {}",
                                callCounter.get(), retrySignal.totalRetries() + 1)))
                .onErrorMap(WebClientResponseException.class, ex -> {
                    log.error("工具调用HTTP错误 #{} - 状态码: {}, 响应: {}",
                            callCounter.get(), ex.getStatusCode(), ex.getResponseBodyAsString());
                    return new RuntimeException("工具调用请求失败: " + ex.getStatusCode(), ex);
                });
    }

    /**
     * 健康检查
     */
    public Mono<Boolean> healthCheck() {
        log.debug("执行健康检查");

        return webClient.get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> "UP".equals(response.path("status").asText()))
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(isHealthy -> log.debug("健康检查结果: {}", isHealthy ? "健康" : "不健康"))
                .doOnError(error -> log.warn("健康检查失败: {}", error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 获取服务器信息
     */
    public Mono<Map<String, Object>> getServerInfo() {
        log.debug("获取服务器信息");

        return webClient.get()
                .uri("/actuator/info")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::jsonNodeToMap)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(info -> log.debug("服务器信息获取成功"))
                .doOnError(error -> log.warn("获取服务器信息失败: {}", error.getMessage()))
                .onErrorReturn(Map.of());
    }

    /**
     * 重置调用计数器
     */
    public void resetCallCounter() {
        callCounter.set(0);
        log.info("API客户端调用计数器已重置");
    }

    /**
     * 获取调用次数
     */
    public int getCallCount() {
        return callCounter.get();
    }

    /**
     * 解析服务器发送事件
     */
    private ServerSentEvent parseServerSentEvent(String eventData) {
        String[] lines = eventData.split("\n");
        String eventType = "message";
        String data = "";

        for (String line : lines) {
            if (line.startsWith("event:")) {
                eventType = line.substring(7).trim();
            } else if (line.startsWith("data:")) {
                data = line.substring(6).trim();
            }
        }

        return new ServerSentEvent(eventType, data);
    }

    /**
     * 解析JSON数据
     */
    private JsonNode parseJsonData(String data) {
        try {
            if (data.equals("[DONE]")) {
                return null;
            }
            return objectMapper.readTree(data);
        } catch (Exception e) {
            log.warn("解析JSON数据失败: {} - 数据: {}", e.getMessage(), data);
            return null;
        }
    }

    /**
     * 将JsonNode转换为Map
     */
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, Map.class);
        } catch (Exception e) {
            log.warn("JsonNode转Map失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 服务器发送事件数据结构
     */
    private record ServerSentEvent(String eventType, String data) {}

    /**
     * 关闭客户端资源
     */
    public void close() {
        log.info("ClaudeApiClient正在关闭，总调用次数: {}", callCounter.get());
        // WebClient不需要显式关闭，由Spring管理
    }
}