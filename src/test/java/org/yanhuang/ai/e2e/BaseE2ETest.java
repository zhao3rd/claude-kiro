package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import org.yanhuang.ai.e2e.annotation.EnabledIfKiroQuotaAvailable;
import org.yanhuang.ai.e2e.client.ClaudeApiClient;
import org.yanhuang.ai.e2e.config.E2ETestConfig;
import org.yanhuang.ai.e2e.config.KiroCallCounter;
import org.yanhuang.ai.e2e.config.E2ETestConfiguration;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * E2E测试基类，提供通用测试功能和工具方法
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("e2e")
@Import(E2ETestConfiguration.class)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "logging.level.org.yanhuang.ai=DEBUG"
})
public abstract class BaseE2ETest {

    protected static final Logger log = LoggerFactory.getLogger(BaseE2ETest.class);

    @Autowired
    protected ClaudeApiClient apiClient;

    @Autowired
    protected KiroCallCounter callCounter;

    @Autowired
    protected E2ETestConfig config;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String testSessionId;

    @BeforeEach
    void setUpBase(TestInfo testInfo) {
        this.testSessionId = UUID.randomUUID().toString().substring(0, 8);
        log.info("=== 开始E2E测试: {} [会话: {}] ===", testInfo.getDisplayName(), testSessionId);

        // 记录测试前的调用状态
        log.info("测试开始前Kiro调用状态: {}", callCounter.getCallStatus());

        // 健康检查
        performHealthCheck();
    }

    /**
     * 执行健康检查
     */
    protected void performHealthCheck() {
        log.info("执行应用健康检查...");

        try {
            Boolean isHealthy = apiClient.healthCheck()
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (Boolean.TRUE.equals(isHealthy)) {
                log.info("✅ 应用健康检查通过");
            } else {
                log.warn("⚠️ 应用健康检查失败，但继续测试");
            }
        } catch (Exception e) {
            log.error("❌ 健康检查异常: {}", e.getMessage());
            // 不抛出异常，允许测试继续
        }
    }

    /**
     * 创建基本的聊天请求
     */
    protected ObjectNode createBasicChatRequest(String message) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "claude-3-5-haiku-20241022");
        request.put("max_tokens", 1000);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");

        // Content should be an array of content blocks
        ArrayNode contentArray = objectMapper.createArrayNode();
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", message);
        contentArray.add(textContent);

        userMessage.set("content", contentArray);
        messages.add(userMessage);
        request.set("messages", messages);

        return request;
    }

    /**
     * 创建带工具的聊天请求
     */
    protected ObjectNode createToolCallRequest(String message, String toolName, String toolDescription) {
        ObjectNode request = createBasicChatRequest(message);

        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");

        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", toolName);
        function.put("description", toolDescription);

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        properties.put("query", objectMapper.createObjectNode().put("type", "string").put("description", "搜索查询"));
        parameters.set("properties", properties);
        parameters.putArray("required").add("query");
        function.set("parameters", parameters);

        tool.set("function", function);
        tools.add(tool);
        request.set("tools", tools);

        return request;
    }

    /**
     * 创建多轮对话请求
     */
    protected ObjectNode createMultiRoundRequest(String... messages) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "claude-3-5-haiku-20241022");
        request.put("max_tokens", 1000);

        ArrayNode messageArray = objectMapper.createArrayNode();
        for (int i = 0; i < messages.length; i++) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("role", i % 2 == 0 ? "user" : "assistant");

            // Content should be an array of content blocks
            ArrayNode contentArray = objectMapper.createArrayNode();
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", messages[i]);
            contentArray.add(textContent);

            msg.set("content", contentArray);
            messageArray.add(msg);
        }
        request.set("messages", messageArray);

        return request;
    }

    /**
     * 验证响应结构
     */
    protected void validateBasicResponse(JsonNode response) {
        assert response != null : "响应不应为空";
        assert response.has("id") : "响应应包含id字段";
        assert response.has("type") : "响应应包含type字段";
        assert response.has("role") : "响应应包含role字段";
        assert response.has("content") : "响应应包含content字段";
        assert "message".equals(response.get("type").asText()) : "类型应为message";
        assert "assistant".equals(response.get("role").asText()) : "角色应为assistant";

        log.info("✅ 基本响应结构验证通过");
    }

    /**
     * 验证工具调用响应
     */
    protected void validateToolCallResponse(JsonNode response, String expectedToolName) {
        validateBasicResponse(response);

        assert response.has("content") : "响应应包含content字段";
        JsonNode content = response.get("content");
        assert content.isArray() : "content应为数组";

        boolean hasToolUse = false;
        for (JsonNode contentItem : content) {
            if ("tool_use".equals(contentItem.get("type").asText())) {
                hasToolUse = true;
                assert contentItem.has("id") : "tool_use应包含id";
                assert contentItem.has("name") : "tool_use应包含name";
                assert expectedToolName.equals(contentItem.get("name").asText()) :
                    String.format("工具名称不匹配: 期望 %s, 实际 %s", expectedToolName, contentItem.get("name").asText());
                log.info("✅ 工具调用验证通过: {}", expectedToolName);
                break;
            }
        }

        assert hasToolUse : "响应中未找到预期的工具调用";
    }

    /**
     * 验证流式响应
     */
    protected void validateStreamResponse(StepVerifier.Step<JsonNode> verifier) {
        verifier
            .expectNextMatches(node -> node.has("type"))
            .expectNextMatches(node -> node.has("type"))
            .expectNextMatches(node -> node.has("type"))
            .expectComplete()
            .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

        log.info("✅ 流式响应验证通过");
    }

    /**
     * 等待指定时间
     */
    protected void waitForSeconds(int seconds) {
        try {
            log.debug("等待 {} 秒", seconds);
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待被中断");
        }
    }

    /**
     * 测试执行结束时的清理和状态记录
     */
    protected void logTestCompletion(String testName) {
        log.info("=== E2E测试完成: {} [会话: {}] ===", testName, testSessionId);
        log.info("最终Kiro调用状态: {}", callCounter.getCallStatus());
        log.info("API客户端调用次数: {}", apiClient.getCallCount());

        // 如果达到了额度限制，提醒用户
        if (!callCounter.canMakeCall()) {
            log.warn("⚠️ Kiro调用额度已用完，建议重置批次或等待下次测试");
        }
    }

    /**
     * 记录测试错误
     */
    protected void logTestError(String testName, Throwable error) {
        log.error("❌ E2E测试失败: {} [会话: {}] - 错误: {}", testName, testSessionId, error.getMessage(), error);
    }

    /**
     * 检查是否应该跳过测试
     */
    protected boolean shouldSkipTest() {
        if (config.isSkipTestsOnQuotaExceeded() && !callCounter.canMakeCall()) {
            log.info("⏭️ 跳过测试 - Kiro额度不足");
            return true;
        }
        return false;
    }

    /**
     * 记录性能指标
     */
    protected void logPerformanceMetrics(String testName, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        log.info("性能指标 - {}: {}ms", testName, duration);

        // 如果测试时间过长，记录警告
        if (duration > config.getTimeoutSeconds() * 1000 * 0.8) {
            log.warn("⚠️ 测试时间接近超时限制: {}ms (限制: {}ms)", duration, config.getTimeoutSeconds() * 1000);
        }
    }

    /**
     * 创建测试上下文信息
     */
    protected Map<String, Object> createTestContext(String testName) {
        return Map.of(
            "testName", testName,
            "sessionId", testSessionId,
            "startTime", System.currentTimeMillis(),
            "kiroCallsBefore", callCounter.getCurrentBatchCalls(),
            "kiroTotalCalls", callCounter.getTotalCalls(),
            "maxCallsPerBatch", config.getMaxCallsPerBatch()
        );
    }
}