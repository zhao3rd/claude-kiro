package org.yanhuang.ai.e2e.annotation;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.yanhuang.ai.e2e.config.KiroCallCounter;

/**
 * 自定义条件注解，用于在Kiro额度不足时跳过测试
 */
public class EnabledIfKiroQuotaAvailable implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        try {
            // 获取Spring应用上下文中的KiroCallCounter
            KiroCallCounter callCounter = SpringExtension.getApplicationContext(context)
                    .getBean(KiroCallCounter.class);

            if (callCounter.canMakeCall()) {
                return ConditionEvaluationResult.enabled(
                        String.format("Kiro额度充足，剩余 %d 次调用", callCounter.getRemainingCalls()));
            } else {
                return ConditionEvaluationResult.disabled(
                        String.format("Kiro额度不足，已使用 %d/%d 次调用",
                                callCounter.getCurrentBatchCalls(),
                                callCounter.getCurrentBatchCalls() + callCounter.getRemainingCalls()));
            }
        } catch (Exception e) {
            // 如果无法获取调用计数器，默认启用测试
            return ConditionEvaluationResult.enabled("无法获取Kiro调用计数器，默认启用测试");
        }
    }
}