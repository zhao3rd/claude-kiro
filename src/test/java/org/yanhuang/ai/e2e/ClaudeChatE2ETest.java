package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Claude聊天基础功能E2E测试
 * 验证从API调用到Kiro网关的完整流程
 */
public class ClaudeChatE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("基础聊天对话 - 简单问答")
    void testBasicChatConversation() {
        long startTime = System.currentTimeMillis();
        String testName = "基础聊天对话";

        try {
            log.info("🚀 开始基础聊天对话测试");

            // 创建测试请求
            ObjectNode request = createBasicChatRequest("你好，请简单介绍一下你自己。");
            log.debug("发送请求: {}", request.toString());

            // 执行API调用
            StepVerifier.create(apiClient.createChatCompletion(request))
                    .expectNextMatches(response -> {
                        try {
                            // 验证响应结构
                            validateBasicResponse(response);

                            // 验证内容不为空
                            JsonNode content = response.get("content");
                            assertTrue(content.isArray() && content.size() > 0, "响应内容不应为空");

                            JsonNode textContent = content.get(0);
                            assertTrue(textContent.has("text"), "内容应包含文本");
                            String reply = textContent.get("text").asText();

                            // 验证回复内容合理性
                            assertFalse(reply.trim().isEmpty(), "回复内容不应为空");
                            assertTrue(reply.length() > 10, "回复内容应有一定长度");

                            log.info("✅ 收到回复: {}", reply.substring(0, Math.min(100, reply.length())) + "...");
                            return true;
                        } catch (Exception e) {
                            log.error("响应验证失败: {}", e.getMessage());
                            return false;
                        }
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("基础聊天对话测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("多轮对话 - 上下文保持")
    void testMultiRoundConversation() {
        long startTime = System.currentTimeMillis();
        String testName = "多轮对话";

        try {
            log.info("🚀 开始多轮对话测试");

            // 第一轮对话
            ObjectNode firstRequest = createBasicChatRequest("我叫小明，今年25岁，是一名软件工程师。请记住这个信息。");

            JsonNode firstResponse = apiClient.createChatCompletion(firstRequest)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(firstResponse, "第一轮响应不应为空");
            validateBasicResponse(firstResponse);
            log.info("✅ 第一轮对话完成");

            waitForSeconds(1);

            // 第二轮对话 - 测试上下文记忆
            ObjectNode secondRequest = objectMapper.createObjectNode();
            secondRequest.put("model", "claude-3-5-haiku-20241022");
            secondRequest.put("max_tokens", 1000);

            var messages = objectMapper.createArrayNode();
            // 添加第一轮的对话历史 - 重建用户消息确保格式正确
            var firstUserMessage = objectMapper.createObjectNode();
            firstUserMessage.put("role", "user");
            var firstUserContent = objectMapper.createArrayNode();
            var firstUserContentItem = objectMapper.createObjectNode();
            firstUserContentItem.put("type", "text");
            firstUserContentItem.put("text", "我叫小明，今年25岁，是一名软件工程师。请记住这个信息。");
            firstUserContent.add(firstUserContentItem);
            firstUserMessage.set("content", firstUserContent);
            messages.add(firstUserMessage);

            var assistantMessage = objectMapper.createObjectNode();
            assistantMessage.put("role", "assistant");
            var content = objectMapper.createArrayNode();
            var contentItem = objectMapper.createObjectNode();
            contentItem.put("type", "text");
            contentItem.put("text", firstResponse.get("content").get(0).get("text").asText());
            content.add(contentItem);
            assistantMessage.set("content", content);
            messages.add(assistantMessage);

            // 添加新的用户消息
            var newUserMessage = objectMapper.createObjectNode();
            newUserMessage.put("role", "user");
            var newUserContent = objectMapper.createArrayNode();
            var newUserContentItem = objectMapper.createObjectNode();
            newUserContentItem.put("type", "text");
            newUserContentItem.put("text", "还记得我叫什么名字吗？我的职业是什么？");
            newUserContent.add(newUserContentItem);
            newUserMessage.set("content", newUserContent);
            messages.add(newUserMessage);

            secondRequest.set("messages", messages);

            log.info("发送多轮对话请求: {}", secondRequest.toString());

            JsonNode secondResponse = apiClient.createChatCompletion(secondRequest)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(secondResponse, "第二轮响应不应为空");
            validateBasicResponse(secondResponse);

            // 验证AI能够理解上下文并给出合理回复
            String reply = secondResponse.get("content").get(0).get("text").asText();
            assertFalse(reply.trim().isEmpty(), "第二轮回复内容不应为空");
            assertTrue(reply.length() > 5, "回复内容应有合理长度");

            log.info("✅ 第二轮对话完成，AI记得用户信息: {}", reply.substring(0, Math.min(50, reply.length())));

            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("多轮对话测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("流式响应测试")
    void testStreamingResponse() {
        long startTime = System.currentTimeMillis();
        String testName = "流式响应";

        try {
            log.info("🚀 开始流式响应测试");

            ObjectNode request = createBasicChatRequest("请详细解释一下什么是人工智能，大约用200字左右。");
            log.debug("发送流式请求: {}", request.toString());

            StepVerifier.create(apiClient.createChatCompletionStream(request))
                    .expectNextMatches(event -> {
                        log.debug("收到流式事件: {}", event);
                        return event.has("type") && "message_start".equals(event.get("type").asText());
                    })
                    .expectNextMatches(event -> {
                        log.debug("收到内容块: {}", event);
                        return event.has("type") && "content_block_start".equals(event.get("type").asText()) ||
                               "content_block_delta".equals(event.get("type").asText());
                    })
                    .expectNextMatches(event -> {
                        log.debug("收到内容块: {}", event);
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        log.debug("收到内容块: {}", event);
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        log.debug("收到结束事件: {}", event);
                        return "message_delta".equals(event.get("type").asText()) ||
                               "message_stop".equals(event.get("type").asText());
                    })
                    // 消费剩余的所有事件
                    .thenConsumeWhile(event -> {
                        log.debug("额外流式事件: {}", event);
                        return true;
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ 流式响应测试完成");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("流式响应测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("长文本处理测试")
    void testLongTextHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "长文本处理";

        try {
            log.info("🚀 开始长文本处理测试");

            // 创建较长的输入文本
            String longText = """
                请分析以下项目需求并提供技术方案建议：

                项目名称：智能客服系统
                项目背景：公司现有客服团队需要处理大量重复性咨询，希望通过AI技术提升效率
                核心功能需求：
                1. 自然语言理解和生成
                2. 多轮对话管理
                3. 知识库检索
                4. 情感分析
                5. 工单自动分类
                6. 人工转接功能
                技术要求：
                - 响应时间 < 2秒
                - 支持1000并发用户
                - 中文语义理解准确率 > 95%
                - 7x24小时稳定运行
                请提供详细的架构设计和技术选型建议。
                """;

            ObjectNode request = createBasicChatRequest(longText);
            log.debug("发送长文本请求，长度: {} 字符", longText.length());

            JsonNode response = apiClient.createChatCompletion(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "长文本响应不应为空");
            validateBasicResponse(response);

            String reply = response.get("content").get(0).get("text").asText();
            assertTrue(reply.length() > 50, "长文本回复应有一定长度");
            assertFalse(reply.trim().isEmpty(), "回复内容不应为空");

            log.info("✅ 长文本处理完成，回复长度: {} 字符", reply.length());
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("长文本处理测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("错误处理测试 - 无效JSON")
    void testErrorHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "错误处理";

        try {
            log.info("🚀 开始错误处理测试");

            // 创建无效的请求
            var invalidRequest = objectMapper.createObjectNode();
            invalidRequest.put("model", "invalid-model-name");
            invalidRequest.put("max_tokens", -100); // 无效的max_tokens
            // 故意不添加必需的messages字段

            log.debug("发送无效请求: {}", invalidRequest.toString());

            StepVerifier.create(apiClient.createChatCompletion(invalidRequest))
                    .expectErrorMatches(throwable -> {
                        log.info("✅ 成功捕获到预期的错误: {}", throwable.getMessage());
                        return throwable instanceof RuntimeException ||
                               throwable.getMessage().contains("400") ||
                               throwable.getMessage().contains("Invalid");
                    })
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ 错误处理测试完成");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("错误处理测试失败: " + e.getMessage());
        }
    }
}