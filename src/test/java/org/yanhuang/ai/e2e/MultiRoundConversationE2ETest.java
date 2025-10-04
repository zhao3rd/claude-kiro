package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多轮对话E2E测试
 * 验证长期对话、上下文管理和会话状态保持
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
public class MultiRoundConversationE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("长期对话上下文保持")
    void testLongTermConversationContext() {
        long startTime = System.currentTimeMillis();
        String testName = "长期对话上下文";

        try {
            log.info("🚀 开始长期对话上下文测试");

            List<JsonNode> conversationHistory = new ArrayList<>();
            String userName = "李华";
            String userJob = "数据分析师";

            // 第一轮：自我介绍
            log.info("第一轮：用户自我介绍");
            ObjectNode request1 = createBasicChatRequest(
                String.format("你好，我叫%s，是一名%s。请记住这个信息。", userName, userJob));

            JsonNode response1 = apiClient.createChatCompletion(request1)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response1, "第一轮响应不应为空");
            validateBasicResponse(response1);
            conversationHistory.add(request1.get("messages").get(0)); // user message
            conversationHistory.add(createAssistantMessage(response1));

            waitForSeconds(1);

            // 第二轮：询问工作相关
            log.info("第二轮：询问工作相关问题");
            ObjectNode request2 = createConversationRequest(conversationHistory,
                "根据我的职业，你建议我学习哪些技能来提升自己？");

            JsonNode response2 = apiClient.createChatCompletion(request2)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response2, "第二轮响应不应为空");
            validateBasicResponse(response2);
            String reply2 = response2.get("content").get(0).get("text").asText();
            assertTrue(reply2.contains("数据") || reply2.contains("分析") || reply2.contains("技能"),
                    "回复应与数据分析相关");

            conversationHistory.add(request2.get("messages").get(0)); // user message
            conversationHistory.add(createAssistantMessage(response2));

            waitForSeconds(1);

            // 第三轮：询问个人信息
            log.info("第三轮：测试个人信息记忆");
            ObjectNode request3 = createConversationRequest(conversationHistory,
                "还记得我叫什么名字吗？我的工作是什么？");

            JsonNode response3 = apiClient.createChatCompletion(request3)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response3, "第三轮响应不应为空");
            validateBasicResponse(response3);
            String reply3 = response3.get("content").get(0).get("text").asText();

            assertTrue(reply3.contains(userName) || reply3.contains(userJob),
                    "AI应该记得用户信息: " + reply3);

            log.info("✅ 长期对话上下文测试通过 - AI记得用户信息");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("长期对话上下文测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("复杂任务分步解决")
    void testComplexTaskStepByStep() {
        long startTime = System.currentTimeMillis();
        String testName = "复杂任务分步解决";

        try {
            log.info("🚀 开始复杂任务分步解决测试");

            List<JsonNode> conversationHistory = new ArrayList<>();
            String projectTopic = "智能家居系统";

            // 第一步：需求讨论
            log.info("第一步：需求讨论");
            ObjectNode request1 = createBasicChatRequest(
                String.format("我想设计一个%s，请帮我分析主要功能需求。", projectTopic));

            JsonNode response1 = apiClient.createChatCompletion(request1)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response1, "需求讨论响应不应为空");
            validateBasicResponse(response1);
            String requirements = response1.get("content").get(0).get("text").asText();
            assertTrue(requirements.length() > 100, "需求分析应足够详细");

            conversationHistory.add(request1.get("messages").get(0));
            conversationHistory.add(createAssistantMessage(response1));

            waitForSeconds(1);

            // 第二步：技术架构设计
            log.info("第二步：技术架构设计");
            ObjectNode request2 = createConversationRequest(conversationHistory,
                "基于刚才的需求，请推荐合适的技术架构和技术栈。");

            JsonNode response2 = apiClient.createChatCompletion(request2)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response2, "架构设计响应不应为空");
            validateBasicResponse(response2);
            String architecture = response2.get("content").get(0).get("text").asText();
            assertTrue(architecture.contains("架构") || architecture.contains("技术"),
                    "回复应包含技术架构内容");

            conversationHistory.add(request2.get("messages").get(0));
            conversationHistory.add(createAssistantMessage(response2));

            waitForSeconds(1);

            // 第三步：实施计划
            log.info("第三步：实施计划");
            ObjectNode request3 = createConversationRequest(conversationHistory,
                "请制定一个详细的开发计划，包括时间安排和里程碑。");

            JsonNode response3 = apiClient.createChatCompletion(request3)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response3, "实施计划响应不应为空");
            validateBasicResponse(response3);
            String plan = response3.get("content").get(0).get("text").asText();
            assertTrue(plan.contains("计划") || plan.contains("时间") || plan.contains("阶段"),
                    "回复应包含实施计划内容");

            // 第四步：总结回顾
            log.info("第四步：总结回顾");
            conversationHistory.add(request3.get("messages").get(0));
            conversationHistory.add(createAssistantMessage(response3));

            ObjectNode request4 = createConversationRequest(conversationHistory,
                "请总结我们刚才讨论的完整方案，包括需求、架构和实施计划。");

            JsonNode response4 = apiClient.createChatCompletion(request4)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response4, "总结响应不应为空");
            validateBasicResponse(response4);
            String summary = response4.get("content").get(0).get("text").asText();

            // 验证总结包含所有阶段的讨论内容
            assertTrue(summary.contains("需求") || summary.contains("功能"),
                    "总结应包含需求内容");
            assertTrue(summary.contains("架构") || summary.contains("技术"),
                    "总结应包含架构内容");
            assertTrue(summary.contains("计划") || summary.contains("实施"),
                    "总结应包含实施计划内容");

            log.info("✅ 复杂任务分步解决测试通过 - 完整方案讨论完成");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("复杂任务分步解决测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("角色扮演对话")
    void testRolePlayingConversation() {
        long startTime = System.currentTimeMillis();
        String testName = "角色扮演对话";

        try {
            log.info("🚀 开始角色扮演对话测试");

            List<JsonNode> conversationHistory = new ArrayList<>();

            // 第一轮：设定角色
            log.info("第一轮：设定角色扮演场景");
            ObjectNode request1 = createBasicChatRequest(
                "请你扮演一位有10年经验的Java技术架构师，我是一名初级开发者，想向你请教技术问题。");

            JsonNode response1 = apiClient.createChatCompletion(request1)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response1, "角色设定响应不应为空");
            validateBasicResponse(response1);
            String roleResponse = response1.get("content").get(0).get("text").asText();
            assertTrue(roleResponse.contains("架构师") || roleResponse.contains("Java") || roleResponse.contains("经验"),
                    "回复应确认角色扮演");

            conversationHistory.add(request1.get("messages").get(0));
            conversationHistory.add(createAssistantMessage(response1));

            waitForSeconds(1);

            // 第二轮：技术咨询
            log.info("第二轮：技术咨询");
            ObjectNode request2 = createConversationRequest(conversationHistory,
                "作为一名初级开发者，我应该如何系统性地学习微服务架构？有什么推荐的学习路径吗？");

            JsonNode response2 = apiClient.createChatCompletion(request2)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response2, "技术咨询响应不应为空");
            validateBasicResponse(response2);
            String advice = response2.get("content").get(0).get("text").asText();

            assertTrue(advice.length() > 200, "建议应足够详细");
            assertTrue(advice.contains("微服务") || advice.contains("学习") || advice.contains("架构"),
                    "建议应与微服务学习相关");

            conversationHistory.add(request2.get("messages").get(0));
            conversationHistory.add(createAssistantMessage(response2));

            waitForSeconds(1);

            // 第三轮：具体问题
            log.info("第三轮：具体技术问题");
            ObjectNode request3 = createConversationRequest(conversationHistory,
                "在微服务架构中，如何处理服务之间的通信和数据一致性问题？");

            JsonNode response3 = apiClient.createChatCompletion(request3)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response3, "具体问题响应不应为空");
            validateBasicResponse(response3);
            String solution = response3.get("content").get(0).get("text").asText();

            assertTrue(solution.contains("通信") || solution.contains("一致性") || solution.contains("服务"),
                    "解答应针对通信和一致性问题");

            log.info("✅ 角色扮演对话测试通过 - 架构师角色保持一致");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("角色扮演对话测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("上下文截断测试")
    void testContextTruncation() {
        long startTime = System.currentTimeMillis();
        String testName = "上下文截断";

        try {
            log.info("🚀 开始上下文截断测试");

            List<JsonNode> conversationHistory = new ArrayList<>();

            // 构建一个较长的对话历史
            for (int i = 1; i <= 6; i++) {
                log.info("对话轮次 {}", i);

                ObjectNode request;
                if (i == 1) {
                    request = createBasicChatRequest("我们来讨论一个持续性的话题：人工智能的发展历史。");
                } else {
                    request = createConversationRequest(conversationHistory,
                        String.format("请继续这个话题，谈谈第%d个重要发展阶段", i));
                }

                JsonNode response = apiClient.createChatCompletion(request)
                        .block(Duration.ofSeconds(config.getTimeoutSeconds()));

                assertNotNull(response, String.format("第%d轮响应不应为空", i));
                validateBasicResponse(response);

                conversationHistory.add(request.get("messages").get(0));
                conversationHistory.add(createAssistantMessage(response));

                // 验证AI仍然能够理解对话主题
                String reply = response.get("content").get(0).get("text").asText();
                assertTrue(reply.length() > 20, String.format("第%d轮回复应有一定内容", i));

                waitForSeconds(1);
            }

            // 最终测试：询问最开始的讨论内容
            log.info("最终测试：询问对话开始的内容");
            ObjectNode finalRequest = createConversationRequest(conversationHistory,
                "我们最开始讨论的话题是什么？请总结一下我们整个对话的内容。");

            JsonNode finalResponse = apiClient.createChatCompletion(finalRequest)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(finalResponse, "最终响应不应为空");
            validateBasicResponse(finalResponse);
            String finalReply = finalResponse.get("content").get(0).get("text").asText();

            assertTrue(finalReply.contains("人工智能") || finalReply.contains("发展") || finalReply.contains("历史"),
                    "AI应该记得对话主题: " + finalReply);

            log.info("✅ 上下文截断测试通过 - 长对话上下文保持良好");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("上下文截断测试失败: " + e.getMessage());
        }
    }

    /**
     * 创建包含对话历史的请求
     */
    private ObjectNode createConversationRequest(List<JsonNode> history, String newMessage) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", "claude-3-5-sonnet-20241022");
        request.put("max_tokens", 1000);

        ArrayNode messages = objectMapper.createArrayNode();

        // 添加历史对话
        for (JsonNode message : history) {
            messages.add(message);
        }

        // 添加新的用户消息
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", newMessage);
        messages.add(userMessage);

        request.set("messages", messages);
        return request;
    }

    /**
     * 创建助手消息节点
     */
    private JsonNode createAssistantMessage(JsonNode response) {
        ObjectNode assistantMessage = objectMapper.createObjectNode();
        assistantMessage.put("role", "assistant");

        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode contentItem = objectMapper.createObjectNode();
        contentItem.put("type", "text");
        contentItem.put("text", response.get("content").get(0).get("text").asText());
        content.add(contentItem);

        assistantMessage.set("content", content);
        return assistantMessage;
    }
}