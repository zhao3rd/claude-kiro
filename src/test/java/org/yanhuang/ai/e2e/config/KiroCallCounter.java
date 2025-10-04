package org.yanhuang.ai.e2e.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kiro调用计数器，用于管理测试额度
 */
@Component
public class KiroCallCounter {

    private static final Logger log = LoggerFactory.getLogger(KiroCallCounter.class);

    private final AtomicInteger currentBatchCalls = new AtomicInteger(0);
    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final E2ETestConfig config;
    private LocalDateTime batchStartTime;

    public KiroCallCounter(E2ETestConfig config) {
        this.config = config;
        this.batchStartTime = LocalDateTime.now();
    }

    /**
     * 检查是否可以进行Kiro调用
     */
    public boolean canMakeCall() {
        return currentBatchCalls.get() < config.getMaxCallsPerBatch();
    }

    /**
     * 增加调用计数
     */
    public void incrementCall() {
        int current = currentBatchCalls.incrementAndGet();
        int total = totalCalls.incrementAndGet();

        log.info("Kiro调用计数: 当前批次 {}/{}, 总计 {}",
                current, config.getMaxCallsPerBatch(), total);

        if (current >= config.getMaxCallsPerBatch()) {
            log.warn("已达到本批次最大调用次数限制: {}", config.getMaxCallsPerBatch());
        }
    }

    /**
     * 重置批次计数
     */
    public void resetBatch() {
        int previousCalls = currentBatchCalls.get();
        currentBatchCalls.set(0);
        batchStartTime = LocalDateTime.now();

        log.info("重置Kiro调用批次，上一批次使用了 {} 次调用", previousCalls);
    }

    /**
     * 获取当前批次调用次数
     */
    public int getCurrentBatchCalls() {
        return currentBatchCalls.get();
    }

    /**
     * 获取总调用次数
     */
    public int getTotalCalls() {
        return totalCalls.get();
    }

    /**
     * 获取剩余调用次数
     */
    public int getRemainingCalls() {
        return Math.max(0, config.getMaxCallsPerBatch() - currentBatchCalls.get());
    }

    /**
     * 获取批次开始时间
     */
    public LocalDateTime getBatchStartTime() {
        return batchStartTime;
    }

    /**
     * 检查是否需要重置批次
     */
    public boolean shouldResetBatch() {
        return currentBatchCalls.get() >= config.getMaxCallsPerBatch();
    }

    /**
     * 获取调用状态信息
     */
    public String getCallStatus() {
        return String.format("批次调用: %d/%d, 总计: %d, 剩余: %d, 开始时间: %s",
                currentBatchCalls.get(),
                config.getMaxCallsPerBatch(),
                totalCalls.get(),
                getRemainingCalls(),
                batchStartTime);
    }
}