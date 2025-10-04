package org.yanhuang.ai.e2e.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * E2E测试配置属性
 */
@Component
@ConfigurationProperties(prefix = "e2e")
public class E2ETestConfig {

    /**
     * 每批测试的最大Kiro调用次数
     */
    private int maxCallsPerBatch = 5;

    /**
     * 请求超时时间（秒）
     */
    private int timeoutSeconds = 30;

    /**
     * 重试次数
     */
    private int retryAttempts = 2;

    /**
     * 测试状态文件路径
     */
    private String stateFilePath = "target/e2e-test-state.json";

    /**
     * 是否启用调用追踪
     */
    private boolean enableCallTracking = true;

    /**
     * 是否跳过额度不足的测试
     */
    private boolean skipTestsOnQuotaExceeded = true;

    // Getters and Setters
    public int getMaxCallsPerBatch() {
        return maxCallsPerBatch;
    }

    public void setMaxCallsPerBatch(int maxCallsPerBatch) {
        this.maxCallsPerBatch = maxCallsPerBatch;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public String getStateFilePath() {
        return stateFilePath;
    }

    public void setStateFilePath(String stateFilePath) {
        this.stateFilePath = stateFilePath;
    }

    public boolean isEnableCallTracking() {
        return enableCallTracking;
    }

    public void setEnableCallTracking(boolean enableCallTracking) {
        this.enableCallTracking = enableCallTracking;
    }

    public boolean isSkipTestsOnQuotaExceeded() {
        return skipTestsOnQuotaExceeded;
    }

    public void setSkipTestsOnQuotaExceeded(boolean skipTestsOnQuotaExceeded) {
        this.skipTestsOnQuotaExceeded = skipTestsOnQuotaExceeded;
    }
}