package org.yanhuang.ai.e2e.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
import org.yanhuang.ai.e2e.client.ClaudeApiClient;
import org.yanhuang.ai.e2e.config.E2ETestConfig;

import java.time.Duration;

/**
 * E2E测试专用配置
 * 只配置测试所需的Bean，不启动Web服务器
 */
@TestConfiguration
public class E2ETestConfiguration {

    @Bean
    @Primary
    public E2ETestConfig e2eTestConfig() {
        E2ETestConfig config = new E2ETestConfig();
        config.setMaxCallsPerBatch(5);
        config.setTimeoutSeconds(30);
        config.setRetryAttempts(2);
        config.setStateFilePath("target/e2e-test-state.json");
        config.setEnableCallTracking(true);
        config.setSkipTestsOnQuotaExceeded(true);
        return config;
    }

    @Bean
    @Primary
    public ClaudeApiClient claudeApiClient(E2ETestConfig config, ObjectMapper objectMapper) {
        return new ClaudeApiClient(7860, Duration.ofSeconds(config.getTimeoutSeconds()), "sk-testing", objectMapper);
    }

    @Bean
    @Primary
    public KiroCallCounter kiroCallCounter(E2ETestConfig config) {
        return new KiroCallCounter(config);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}