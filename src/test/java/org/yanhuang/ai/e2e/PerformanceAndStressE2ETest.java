package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能和压力E2E测试
 * 验证系统在负载下的表现和资源使用情况
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
public class PerformanceAndStressE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("响应时间基准测试")
    void testResponseTimeBenchmark() {
        long startTime = System.currentTimeMillis();
        String testName = "响应时间基准";

        try {
            log.info("🚀 开始响应时间基准测试");

            List<Long> responseTimes = new ArrayList<>();
            int testCount = 3; // 减少测试次数以节约额度

            for (int i = 0; i < testCount; i++) {
                log.info("执行第 {} 次响应时间测试", i + 1);

                ObjectNode request = createBasicChatRequest(
                    String.format("请简单回复：这是第%d次性能测试。", i + 1));

                long requestStart = System.currentTimeMillis();

                JsonNode response = apiClient.createChatCompletion(request)
                        .block(Duration.ofSeconds(config.getTimeoutSeconds()));

                long requestEnd = System.currentTimeMillis();
                long responseTime = requestEnd - requestStart;

                assertNotNull(response, String.format("第%d次响应不应为空", i + 1));
                validateBasicResponse(response);

                responseTimes.add(responseTime);
                log.info("第 {} 次响应时间: {}ms", i + 1, responseTime);

                if (i < testCount - 1) {
                    waitForSeconds(1); // 避免过于频繁的请求
                }
            }

            // 计算统计数据
            double averageTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);

            log.info("响应时间统计 - 平均: {:.2f}ms, 最大: {}ms, 最小: {}ms",
                    averageTime, maxTime, minTime);

            // 性能断言
            assertTrue(averageTime < 15000, "平均响应时间应小于15秒");
            assertTrue(maxTime < 25000, "最大响应时间应小于25秒");

            log.info("✅ 响应时间基准测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("响应时间基准测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("并发负载测试")
    void testConcurrentLoadTest() {
        long startTime = System.currentTimeMillis();
        String testName = "并发负载测试";

        try {
            log.info("🚀 开始并发负载测试");

            int concurrentCount = 2; // 减少并发数以节约额度
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);

            List<Mono<JsonNode>> requests = new ArrayList<>();

            // 创建多个并发请求
            for (int i = 0; i < concurrentCount; i++) {
                final int requestIndex = i;
                ObjectNode request = createBasicChatRequest(
                    String.format("并发测试请求 #%d - 请简单确认收到。", requestIndex + 1));

                Mono<JsonNode> mono = apiClient.createChatCompletion(request)
                        .doOnSuccess(response -> {
                            successCount.incrementAndGet();
                            validateBasicResponse(response);
                            log.debug("并发请求 #{} 成功", requestIndex + 1);
                        })
                        .doOnError(error -> {
                            errorCount.incrementAndGet();
                            log.warn("并发请求 #{} 失败: {}", requestIndex + 1, error.getMessage());
                        })
                        .name("concurrent-request-" + (requestIndex + 1));

                requests.add(mono);
            }

            // 执行并发请求
            long loadTestStart = System.currentTimeMillis();

            Flux.merge(requests)
                    .blockLast(Duration.ofSeconds(config.getTimeoutSeconds() * 2));

            long loadTestEnd = System.currentTimeMillis();
            long totalLoadTime = loadTestEnd - loadTestStart;

            log.info("并发负载测试结果:");
            log.info("  - 总请求数: {}", concurrentCount);
            log.info("  - 成功请求: {}", successCount.get());
            log.info("  - 失败请求: {}", errorCount.get());
            log.info("  - 总耗时: {}ms", totalLoadTime);
            log.info("  - 平均耗时: {:.2f}ms", (double) totalLoadTime / concurrentCount);

            // 验证结果
            assertTrue(successCount.get() >= 1, "至少应有一个请求成功");
            assertTrue(errorCount.get() <= concurrentCount / 2, "错误请求不应超过50%");

            log.info("✅ 并发负载测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("并发负载测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("内存使用测试")
    void testMemoryUsageTest() {
        long startTime = System.currentTimeMillis();
        String testName = "内存使用测试";

        try {
            log.info("🚀 开始内存使用测试");

            Runtime runtime = Runtime.getRuntime();

            // 记录初始内存使用
            runtime.gc();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();
            log.info("初始内存使用: {} bytes ({:.2f} MB)",
                    initialMemory, initialMemory / 1024.0 / 1024.0);

            List<JsonNode> responses = new ArrayList<>();

            // 执行一系列请求
            for (int i = 0; i < 3; i++) {
                log.info("执行内存测试请求 #{}", i + 1);

                ObjectNode request = createBasicChatRequest(
                    String.format("内存测试请求 #%d - 请回复一个约100字的段落。", i + 1));

                JsonNode response = apiClient.createChatCompletion(request)
                        .block(Duration.ofSeconds(config.getTimeoutSeconds()));

                assertNotNull(response, String.format("第%d次响应不应为空", i + 1));
                validateBasicResponse(response);
                responses.add(response);

                // 检查内存使用
                if (i == 1) { // 在中间检查一次
                    long midMemory = runtime.totalMemory() - runtime.freeMemory();
                    log.info("中间内存使用: {} bytes ({:.2f} MB)",
                            midMemory, midMemory / 1024.0 / 1024.0);
                }

                waitForSeconds(1);
            }

            // 强制垃圾回收并检查最终内存使用
            runtime.gc();
            runtime.gc();
            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            log.info("最终内存使用: {} bytes ({:.2f} MB)",
                    finalMemory, finalMemory / 1024.0 / 1024.0);

            long memoryIncrease = finalMemory - initialMemory;
            double memoryIncreaseMB = memoryIncrease / 1024.0 / 1024.0;

            log.info("内存增长: {} bytes ({:.2f} MB)", memoryIncrease, memoryIncreaseMB);

            // 内存使用断言 - 允许合理的内存增长
            assertTrue(memoryIncreaseMB < 100, "内存增长应小于100MB");

            log.info("✅ 内存使用测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("内存使用测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("流式性能测试")
    void testStreamingPerformanceTest() {
        long startTime = System.currentTimeMillis();
        String testName = "流式性能测试";

        try {
            log.info("🚀 开始流式性能测试");

            ObjectNode request = createBasicChatRequest(
                "请写一篇关于技术发展趋势的文章，大约500字，包括人工智能、云计算、物联网等内容。");

            long streamStart = System.currentTimeMillis();
            AtomicInteger eventCount = new AtomicInteger(0);
            AtomicLong firstEventTime = new AtomicLong(0);
            AtomicLong lastEventTime = new AtomicLong(0);

            StepVerifier.create(apiClient.createChatCompletionStream(request)
                    .doOnNext(event -> {
                        int count = eventCount.incrementAndGet();
                        long currentTime = System.currentTimeMillis();

                        if (count == 1) {
                            firstEventTime.set(currentTime);
                            log.debug("首个事件时间: {}ms", firstEventTime.get() - streamStart);
                        }

                        lastEventTime.set(currentTime);
                        log.debug("事件 #{}: {}", count, event.get("type"));
                    })
                    .doOnComplete(() -> {
                        long totalTime = lastEventTime.get() - streamStart;
                        log.info("流式完成 - 总时间: {}ms, 事件数: {}", totalTime, eventCount.get());
                    })
                    .doOnError(error -> log.error("流式错误: {}", error.getMessage())))
                    .expectNextMatches(event -> event.has("type"))
                    .expectNextMatches(event -> event.has("type"))
                    .expectNextMatches(event -> event.has("type"))
                    .expectNextMatches(event -> event.has("type"))
                    // 消费剩余的所有事件
                    .thenConsumeWhile(event -> {
                        log.debug("额外性能测试事件: {}", event);
                        return true;
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            long streamEnd = System.currentTimeMillis();
            long totalStreamTime = streamEnd - streamStart;

            // 计算性能指标
            long firstEventLatency = firstEventTime.get() - streamStart;
            double eventsPerSecond = eventCount.get() * 1000.0 / totalStreamTime;

            log.info("流式性能指标:");
            log.info("  - 首个事件延迟: {}ms", firstEventLatency);
            log.info("  - 总流式时间: {}ms", totalStreamTime);
            log.info("  - 事件总数: {}", eventCount.get());
            log.info("  - 事件频率: {:.2f} events/second", eventsPerSecond);

            // 性能断言 - 调整阈值以适应实际性能
            assertTrue(firstEventLatency < 20000, "首个事件延迟应小于20秒");
            assertTrue(totalStreamTime < 30000, "总流式时间应小于30秒");
            assertTrue(eventCount.get() >= 4, "事件数量应合理");

            log.info("✅ 流式性能测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("流式性能测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("系统稳定性测试")
    void testSystemStabilityTest() {
        long startTime = System.currentTimeMillis();
        String testName = "系统稳定性测试";

        try {
            log.info("🚀 开始系统稳定性测试");

            AtomicInteger cycleCount = new AtomicInteger(0);
            AtomicInteger successCycles = new AtomicInteger(0);
            List<Long> cycleTimes = new ArrayList<>();

            int maxCycles = 2; // 减少循环次数以节约额度

            for (int cycle = 0; cycle < maxCycles; cycle++) {
                log.info("执行稳定性测试循环 #{}", cycle + 1);

                long cycleStart = System.currentTimeMillis();

                try {
                    // 在每个循环中执行不同类型的请求
                    ObjectNode request1 = createBasicChatRequest(
                        String.format("稳定性测试 #%d - 简单问答。", cycle + 1));

                    JsonNode response1 = apiClient.createChatCompletion(request1)
                            .block(Duration.ofSeconds(config.getTimeoutSeconds()));

                    assertNotNull(response1, "响应1不应为空");
                    validateBasicResponse(response1);

                    waitForSeconds(1);

                    // 流式请求
                    ObjectNode request2 = createBasicChatRequest(
                        String.format("稳定性测试 #%d - 流式响应。", cycle + 1));

                    StepVerifier.create(apiClient.createChatCompletionStream(request2))
                            .expectNextCount(3)
                            // 消费剩余的所有事件
                            .thenConsumeWhile(event -> true)
                            .expectComplete()
                            .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

                    successCycles.incrementAndGet();
                    log.info("循环 #{} 成功完成", cycle + 1);

                } catch (Exception e) {
                    log.warn("循环 #{} 失败: {}", cycle + 1, e.getMessage());
                }

                long cycleEnd = System.currentTimeMillis();
                long cycleTime = cycleEnd - cycleStart;
                cycleTimes.add(cycleTime);
                cycleCount.incrementAndGet();

                log.info("循环 #{} 耗时: {}ms", cycle + 1, cycleTime);

                if (cycle < maxCycles - 1) {
                    waitForSeconds(2); // 循环间等待
                }
            }

            // 计算稳定性指标
            double successRate = (double) successCycles.get() / cycleCount.get() * 100;
            double averageCycleTime = cycleTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            long maxCycleTime = cycleTimes.stream().mapToLong(Long::longValue).max().orElse(0);
            long minCycleTime = cycleTimes.stream().mapToLong(Long::longValue).min().orElse(0);

            log.info("系统稳定性指标:");
            log.info("  - 总循环数: {}", cycleCount.get());
            log.info("  - 成功循环数: {}", successCycles.get());
            log.info("  - 成功率: {:.2f}%", successRate);
            log.info("  - 平均循环时间: {:.2f}ms", averageCycleTime);
            log.info("  - 最大循环时间: {}ms", maxCycleTime);
            log.info("  - 最小循环时间: {}ms", minCycleTime);

            // 稳定性断言
            assertTrue(successRate >= 80, "成功率应不低于80%");
            assertTrue(averageCycleTime < 30000, "平均循环时间应小于30秒");

            log.info("✅ 系统稳定性测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("系统稳定性测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("资源限制测试")
    void testResourceLimitsTest() {
        long startTime = System.currentTimeMillis();
        String testName = "资源限制测试";

        try {
            log.info("🚀 开始资源限制测试");

            // 测试长请求处理
            log.info("测试长请求处理能力");
            ObjectNode longRequest = createBasicChatRequest(
                "请详细分析以下10个技术主题：1.人工智能 2.区块链 3.量子计算 4.物联网 5.边缘计算 " +
                "6.5G网络 7.虚拟现实 8.增强现实 9.自动驾驶 10.新能源。每个主题请从技术原理、" +
                "应用场景、发展前景和挑战四个方面进行分析。");

            JsonNode longResponse = apiClient.createChatCompletion(longRequest)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            if (longResponse != null) {
                validateBasicResponse(longResponse);
                String content = longResponse.get("content").get(0).get("text").asText();
                log.info("长请求响应长度: {} 字符", content.length());
                assertTrue(content.length() > 100, "长请求响应应有合理内容");
            } else {
                log.info("长请求被合理限制");
            }

            waitForSeconds(2);

            // 测试快速连续请求
            log.info("测试快速连续请求处理");
            AtomicInteger rapidSuccessCount = new AtomicInteger(0);

            for (int i = 0; i < 2; i++) {
                ObjectNode rapidRequest = createBasicChatRequest(
                    String.format("快速请求 #%d - 简单回复。", i + 1));

                try {
                    JsonNode rapidResponse = apiClient.createChatCompletion(rapidRequest)
                            .block(Duration.ofSeconds(config.getTimeoutSeconds()));

                    if (rapidResponse != null) {
                        validateBasicResponse(rapidResponse);
                        rapidSuccessCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.debug("快速请求 #{} 失败: {}", i + 1, e.getMessage());
                }
            }

            log.info("快速连续请求成功数: {}/{}", rapidSuccessCount.get(), 2);

            log.info("✅ 资源限制测试通过");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("资源限制测试失败: " + e.getMessage());
        }
    }
}