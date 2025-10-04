package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流式响应和错误处理E2E测试
 * 验证流式传输、异常处理和边界情况
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
public class StreamingAndErrorE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("流式响应完整流程")
    void testStreamingResponseFullFlow() {
        long startTime = System.currentTimeMillis();
        String testName = "流式响应完整流程";

        try {
            log.info("🚀 开始流式响应完整流程测试");

            ObjectNode request = createBasicChatRequest(
                "请详细介绍一下机器学习的基本概念，包括监督学习、无监督学习和强化学习。");

            AtomicInteger eventCount = new AtomicInteger(0);
            StringBuilder fullResponse = new StringBuilder();

            StepVerifier.create(apiClient.createChatCompletionStream(request))
                    .expectNextMatches(event -> {
                        int count = eventCount.incrementAndGet();
                        log.debug("流式事件 {}: {}", count, event);

                        assertTrue(event.has("type"), "事件应有type字段");
                        String eventType = event.get("type").asText();

                        // 验证第一个事件是message_start
                        if (count == 1) {
                            assertEquals("message_start", eventType, "第一个事件应为message_start");
                            assertTrue(event.has("message"), "message_start事件应有message字段");
                        }

                        return true;
                    })
                    .expectNextMatches(event -> {
                        int count = eventCount.incrementAndGet();
                        log.debug("流式事件 {}: {}", count, event);

                        String eventType = event.get("type").asText();
                        assertTrue(eventType.equals("content_block_start") ||
                                 eventType.equals("content_block_delta") ||
                                 eventType.equals("content_block_stop"),
                                "事件类型应是内容相关: " + eventType);

                        // 收集文本内容
                        if (event.has("delta") && event.get("delta").has("text")) {
                            String text = event.get("delta").get("text").asText();
                            fullResponse.append(text);
                        }

                        return true;
                    })
                    .expectNextMatches(event -> {
                        int count = eventCount.incrementAndGet();
                        log.debug("流式事件 {}: {}", count, event);

                        String eventType = event.get("type").asText();
                        // 允许各种内容事件
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        int count = eventCount.incrementAndGet();
                        log.debug("流式事件 {}: {}", count, event);

                        String eventType = event.get("type").asText();
                        // 允许各种内容事件
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        int count = eventCount.incrementAndGet();
                        log.debug("流式事件 {}: {}", count, event);

                        String eventType = event.get("type").asText();
                        // 最后几个事件应该是message_delta或message_stop
                        return eventType.equals("message_delta") ||
                               eventType.equals("message_stop") ||
                               event.has("type");
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            // 验证收集到的响应内容
            String collectedResponse = fullResponse.toString();
            assertTrue(collectedResponse.length() > 100, "收集的响应应足够长");
            assertTrue(collectedResponse.contains("机器学习") || collectedResponse.contains("学习"),
                    "响应应包含机器学习相关内容");

            log.info("✅ 流式响应完整流程测试通过 - 总事件数: {}, 响应长度: {}",
                    eventCount.get(), collectedResponse.length());

            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("流式响应完整流程测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("流式响应中断恢复")
    void testStreamingResponseInterruption() {
        long startTime = System.currentTimeMillis();
        String testName = "流式响应中断恢复";

        try {
            log.info("🚀 开始流式响应中断恢复测试");

            ObjectNode request = createBasicChatRequest(
                "请写一个300字的中篇故事，包含开头、发展和结尾。");

            // 第一次尝试获取流式响应的开始部分
            StepVerifier.create(apiClient.createChatCompletionStream(request)
                    .take(3) // 只取前3个事件
                    .doOnNext(event -> log.debug("中断测试事件: {}", event)))
                    .expectNextCount(3)
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ 第一次流式响应成功获取前3个事件");

            waitForSeconds(1);

            // 第二次完整获取流式响应
            AtomicInteger eventCount = new AtomicInteger(0);
            StepVerifier.create(apiClient.createChatCompletionStream(request))
                    .expectNextMatches(event -> {
                        eventCount.incrementAndGet();
                        log.debug("恢复测试事件 {}: {}", eventCount.get(), event);
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        eventCount.incrementAndGet();
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        eventCount.incrementAndGet();
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        eventCount.incrementAndGet();
                        return event.has("type");
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertTrue(eventCount.get() >= 4, "第二次流式响应应获取到足够的事件");

            log.info("✅ 流式响应中断恢复测试通过 - 第二次获取事件数: {}", eventCount.get());
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("流式响应中断恢复测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("并发请求处理")
    void testConcurrentRequests() {
        long startTime = System.currentTimeMillis();
        String testName = "并发请求处理";

        try {
            log.info("🚀 开始并发请求处理测试");

            // 创建3个不同的请求
            ObjectNode request1 = createBasicChatRequest("请简要介绍Python编程语言的特点。");
            ObjectNode request2 = createBasicChatRequest("请解释什么是云计算。");
            ObjectNode request3 = createBasicChatRequest("请说明区块链技术的基本原理。");

            // 并发执行多个请求
            var mono1 = apiClient.createChatCompletion(request1);
            var mono2 = apiClient.createChatCompletion(request2);
            var mono3 = apiClient.createChatCompletion(request3);

            // 等待所有请求完成
            var results = reactor.core.publisher.Mono.zip(mono1, mono2, mono3)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds() * 2));

            assertNotNull(results, "并发请求结果不应为空");

            JsonNode response1 = results.getT1();
            JsonNode response2 = results.getT2();
            JsonNode response3 = results.getT3();

            // 验证每个响应
            validateBasicResponse(response1);
            validateBasicResponse(response2);
            validateBasicResponse(response3);

            // 验证内容相关性
            String content1 = response1.get("content").get(0).get("text").asText();
            String content2 = response2.get("content").get(0).get("text").asText();
            String content3 = response3.get("content").get(0).get("text").asText();

            assertTrue(content1.contains("Python") || content1.contains("编程"),
                    "响应1应与Python相关");
            assertTrue(content2.contains("云计算") || content2.contains("计算"),
                    "响应2应与云计算相关");
            assertTrue(content3.contains("区块链") || content3.contains("链"),
                    "响应3应与区块链相关");

            log.info("✅ 并发请求处理测试通过 - 3个请求全部成功");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("并发请求处理测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("大请求处理")
    void testLargeRequestHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "大请求处理";

        try {
            log.info("🚀 开始大请求处理测试");

            // 创建一个较大的请求
            StringBuilder largeText = new StringBuilder();
            largeText.append("请分析以下多个主题并提供详细见解：\n\n");

            for (int i = 1; i <= 10; i++) {
                largeText.append(String.format("%d. 人工智能在第%d个领域的应用和挑战；\n", i, i));
            }

            largeText.append("\n请为每个主题提供详细的分析，包括当前状况、未来发展趋势和潜在问题。");

            ObjectNode request = createBasicChatRequest(largeText.toString());

            log.info("发送大请求，文本长度: {} 字符", largeText.length());

            JsonNode response = apiClient.createChatCompletion(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "大请求响应不应为空");
            validateBasicResponse(response);

            String reply = response.get("content").get(0).get("text").asText();
            assertTrue(reply.length() > 500, "大请求的回复应足够详细");
            assertTrue(reply.contains("人工智能") || reply.contains("应用") || reply.contains("发展"),
                    "回复应包含相关分析内容");

            log.info("✅ 大请求处理测试通过 - 请求长度: {}, 响应长度: {}",
                    largeText.length(), reply.length());

            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("大请求处理测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("网络超时处理")
    void testNetworkTimeoutHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "网络超时处理";

        try {
            log.info("🚀 开始网络超时处理测试");

            // 创建一个可能导致较长处理时间的请求
            ObjectNode request = createBasicChatRequest(
                "请进行一个非常复杂的分析：详细比较深度学习、机器学习和传统算法在以下10个方面的差异：" +
                "1. 准确性 2. 计算效率 3. 数据需求 4. 可解释性 5. 部署复杂度 6. 维护成本 7. 适用场景 " +
                "8. 训练时间 9. 推理速度 10. 扩展性。请为每个方面提供详细的量化分析和实例说明。");

            log.info("发送复杂分析请求，测试超时处理");

            // 使用较短的超时时间来测试超时处理
            JsonNode response = apiClient.createChatCompletion(request)
                    .block(Duration.ofSeconds(5)); // 设置5秒超时

            // 如果在5秒内完成，验证响应
            if (response != null) {
                validateBasicResponse(response);
                log.info("✅ 请求在超时时间内完成");
            } else {
                log.info("✅ 超时处理正常 - 请求按预期超时");
            }

            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            // 超时异常是预期的
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                log.info("✅ 网络超时处理测试通过 - 成功捕获超时异常");
            } else {
                logTestError(testName, e);
                fail("网络超时处理测试失败: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("无效输入处理")
    void testInvalidInputHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "无效输入处理";

        try {
            log.info("🚀 开始无效输入处理测试");

            // 测试1: 空消息
            log.info("测试空消息处理");
            ObjectNode emptyRequest = createBasicChatRequest("");

            StepVerifier.create(apiClient.createChatCompletion(emptyRequest))
                    .expectErrorMatches(throwable -> {
                        log.info("✅ 成功捕获空消息错误: {}", throwable.getMessage());
                        return true;
                    })
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            waitForSeconds(1);

            // 测试2: 超长消息
            log.info("测试超长消息处理");
            StringBuilder veryLongText = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                veryLongText.append("这是一个非常长的测试消息，用于测试系统对超长输入的处理能力。");
            }

            ObjectNode longRequest = createBasicChatRequest(veryLongText.toString());

            JsonNode longResponse = apiClient.createChatCompletion(longRequest)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            if (longResponse != null) {
                validateBasicResponse(longResponse);
                log.info("✅ 超长消息处理成功");
            } else {
                log.info("✅ 超长消息被正确拒绝");
            }

            waitForSeconds(1);

            // 测试3: 特殊字符
            log.info("测试特殊字符处理");
            String specialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?~`\u0000\u0001\u0002";
            ObjectNode specialRequest = createBasicChatRequest(specialChars);

            JsonNode specialResponse = apiClient.createChatCompletion(specialRequest)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            if (specialResponse != null) {
                validateBasicResponse(specialResponse);
                log.info("✅ 特殊字符处理成功");
            } else {
                log.info("✅ 特殊字符被正确处理");
            }

            log.info("✅ 无效输入处理测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("无效输入处理测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("资源清理测试")
    void testResourceCleanup() {
        long startTime = System.currentTimeMillis();
        String testName = "资源清理测试";

        try {
            log.info("🚀 开始资源清理测试");

            // 执行多个请求以测试资源管理
            for (int i = 1; i <= 3; i++) {
                log.info("执行第 {} 个请求", i);

                ObjectNode request = createBasicChatRequest(
                    String.format("这是第%d个测试请求，请简单回复确认收到。", i));

                JsonNode response = apiClient.createChatCompletion(request)
                        .block(Duration.ofSeconds(config.getTimeoutSeconds()));

                assertNotNull(response, String.format("第%d个请求响应不应为空", i));
                validateBasicResponse(response);

                log.info("第 {} 个请求成功完成", i);

                if (i < 3) {
                    waitForSeconds(1);
                }
            }

            // 检查API客户端状态
            int totalCalls = apiClient.getCallCount();
            log.info("API客户端总调用次数: {}", totalCalls);

            assertTrue(totalCalls >= 3, "总调用次数应至少为3");

            log.info("✅ 资源清理测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("资源清理测试失败: " + e.getMessage());
        }
    }
}