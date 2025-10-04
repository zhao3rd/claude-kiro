package org.yanhuang.ai.e2e.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.yanhuang.ai.e2e.config.E2ETestConfig;
import org.yanhuang.ai.e2e.config.KiroCallCounter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Kiro调用追踪切面，用于监控和记录每次Kiro调用
 */
@Aspect
@Component
public class KiroCallTracker {

    private static final Logger log = LoggerFactory.getLogger(KiroCallTracker.class);

    private final KiroCallCounter callCounter;
    private final E2ETestConfig config;

    public KiroCallTracker(KiroCallCounter callCounter, E2ETestConfig config) {
        this.callCounter = callCounter;
        this.config = config;
    }

    /**
     * 追踪KiroService的所有公共方法调用
     */
    @Around("execution(* org.yanhuang.ai.service.KiroService.*(..))")
    public Object trackKiroCall(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!config.isEnableCallTracking()) {
            return joinPoint.proceed();
        }

        String methodName = joinPoint.getSignature().getName();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始Kiro调用: {} - 剩余额度: {}",
                methodName, callCounter.getRemainingCalls());

        try {
            // 增加调用计数
            callCounter.incrementCall();

            // 执行方法并计算耗时
            long start = System.currentTimeMillis();
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            log.info("Kiro调用完成: {} - 耗时: {}ms - 当前批次: {}/{}",
                    methodName, duration,
                    callCounter.getCurrentBatchCalls(),
                    config.getMaxCallsPerBatch());

            return result;

        } catch (Exception e) {
            log.error("Kiro调用失败: {} - 错误: {}", methodName, e.getMessage());
            throw e;
        }
    }
}