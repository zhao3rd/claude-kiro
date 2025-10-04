package org.yanhuang.ai.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具调用功能E2E测试
 * 验证Claude API的工具定义、调用和响应处理
 */
@EnabledIfEnvironmentVariable(named = "CLAUDE_API_KEY", matches = ".*[^\\s].*")
public class ToolCallE2ETest extends BaseE2ETest {

    @Test
    @DisplayName("基础工具调用 - 搜索功能")
    void testBasicToolCall() {
        long startTime = System.currentTimeMillis();
        String testName = "基础工具调用";

        try {
            log.info("🚀 开始基础工具调用测试");

            String toolName = "web_search";
            String toolDescription = "在互联网上搜索信息";
            String userMessage = "请帮我搜索一下最新的AI技术发展趋势";

            ObjectNode request = createToolCallRequest(userMessage, toolName, toolDescription);

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "工具调用响应不应为空");
            validateToolCallResponse(response, toolName);

            // 验证工具调用的具体内容
            JsonNode content = response.get("content");
            JsonNode toolUse = null;
            for (JsonNode contentItem : content) {
                if ("tool_use".equals(contentItem.get("type").asText())) {
                    toolUse = contentItem;
                    break;
                }
            }

            assertNotNull(toolUse, "应找到tool_use节点");
            assertTrue(toolUse.has("id"), "工具调用应包含ID");
            assertTrue(toolUse.has("input"), "工具调用应包含输入参数");

            JsonNode input = toolUse.get("input");
            assertTrue(input.has("query"), "输入应包含查询参数");
            String query = input.get("query").asText();
            assertFalse(query.trim().isEmpty(), "查询参数不应为空");

            log.info("✅ 工具调用成功 - 工具ID: {}, 查询: {}",
                    toolUse.get("id").asText(), query);

            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("基础工具调用测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("复杂工具调用 - 多参数函数")
    void testComplexToolCall() {
        long startTime = System.currentTimeMillis();
        String testName = "复杂工具调用";

        try {
            log.info("🚀 开始复杂工具调用测试");

            String toolName = "data_analysis";
            String toolDescription = "分析数据并生成报告";
            String userMessage = "请分析这个月的销售数据：产品A销量1500，产品B销量2300，产品C销量800";

            // 创建复杂工具定义
            ObjectNode request = createBasicChatRequest(userMessage);

            var tools = objectMapper.createArrayNode();
            var tool = objectMapper.createObjectNode();
            tool.put("type", "function");

            var function = objectMapper.createObjectNode();
            function.put("name", toolName);
            function.put("description", toolDescription);

            var parameters = objectMapper.createObjectNode();
            parameters.put("type", "object");

            var properties = objectMapper.createObjectNode();

            // 多个参数定义
            var dataParam = objectMapper.createObjectNode();
            dataParam.put("type", "object");
            dataParam.put("description", "要分析的数据");
            properties.set("data", dataParam);

            var reportTypeParam = objectMapper.createObjectNode();
            reportTypeParam.put("type", "string");
            reportTypeParam.put("description", "报告类型");
            reportTypeParam.put("enum", objectMapper.createArrayNode().add("summary").add("detailed"));
            properties.set("report_type", reportTypeParam);

            var chartParam = objectMapper.createObjectNode();
            chartParam.put("type", "boolean");
            chartParam.put("description", "是否生成图表");
            properties.set("include_chart", chartParam);

            parameters.set("properties", properties);
            parameters.putArray("required").add("data");

            function.set("parameters", parameters);
            tool.set("function", function);
            tools.add(tool);
            request.set("tools", tools);

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "复杂工具调用响应不应为空");
            validateToolCallResponse(response, toolName);

            // 验证复杂参数
            JsonNode content = response.get("content");
            JsonNode toolUse = null;
            for (JsonNode contentItem : content) {
                if ("tool_use".equals(contentItem.get("type").asText())) {
                    toolUse = contentItem;
                    break;
                }
            }

            JsonNode input = toolUse.get("input");
            assertTrue(input.has("data"), "输入应包含data参数");

            log.info("✅ 复杂工具调用成功 - 参数数量: {}", input.size());
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("复杂工具调用测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("多工具选择调用")
    void testMultipleToolsCall() {
        long startTime = System.currentTimeMillis();
        String testName = "多工具选择";

        try {
            log.info("🚀 开始多工具选择测试");

            String userMessage = "我需要了解今天的天气情况，然后搜索附近的餐厅";

            // 创建多个工具
            ObjectNode request = createBasicChatRequest(userMessage);

            var tools = objectMapper.createArrayNode();

            // 工具1：天气查询
            var weatherTool = objectMapper.createObjectNode();
            weatherTool.put("type", "function");
            var weatherFunction = objectMapper.createObjectNode();
            weatherFunction.put("name", "get_weather");
            weatherFunction.put("description", "获取指定地点的天气信息");
            var weatherParams = objectMapper.createObjectNode();
            weatherParams.put("type", "object");
            var weatherProps = objectMapper.createObjectNode();
            weatherProps.put("location", objectMapper.createObjectNode()
                    .put("type", "string")
                    .put("description", "城市名称"));
            weatherParams.set("properties", weatherProps);
            weatherParams.putArray("required").add("location");
            weatherFunction.set("parameters", weatherParams);
            weatherTool.set("function", weatherFunction);
            tools.add(weatherTool);

            // 工具2：餐厅搜索
            var restaurantTool = objectMapper.createObjectNode();
            restaurantTool.put("type", "function");
            var restaurantFunction = objectMapper.createObjectNode();
            restaurantFunction.put("name", "search_restaurants");
            restaurantFunction.put("description", "搜索附近的餐厅");
            var restaurantParams = objectMapper.createObjectNode();
            restaurantParams.put("type", "object");
            var restaurantProps = objectMapper.createObjectNode();
            restaurantProps.put("cuisine", objectMapper.createObjectNode()
                    .put("type", "string")
                    .put("description", "菜系类型"));
            restaurantProps.put("location", objectMapper.createObjectNode()
                    .put("type", "string")
                    .put("description", "地点"));
            restaurantParams.set("properties", restaurantProps);
            restaurantFunction.set("parameters", restaurantParams);
            restaurantTool.set("function", restaurantFunction);
            tools.add(restaurantTool);

            request.set("tools", tools);

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "多工具调用响应不应为空");
            validateBasicResponse(response);

            // 验证AI选择了合适的工具
            JsonNode content = response.get("content");
            boolean hasToolCall = false;
            String selectedTool = null;

            for (JsonNode contentItem : content) {
                if ("tool_use".equals(contentItem.get("type").asText())) {
                    hasToolCall = true;
                    selectedTool = contentItem.get("name").asText();
                    break;
                }
            }

            assertTrue(hasToolCall, "应该有工具调用");
            assertTrue(selectedTool.equals("get_weather") || selectedTool.equals("search_restaurants"),
                    "应该选择其中一个工具: " + selectedTool);

            log.info("✅ 多工具选择成功 - AI选择了: {}", selectedTool);
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("多工具选择测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("工具调用流式响应")
    void testToolCallStreaming() {
        long startTime = System.currentTimeMillis();
        String testName = "工具调用流式";

        try {
            log.info("🚀 开始工具调用流式测试");

            String toolName = "calculator";
            String toolDescription = "执行数学计算";
            String userMessage = "请计算 (123 + 456) * 2 的结果";

            ObjectNode request = createToolCallRequest(userMessage, toolName, toolDescription);

            StepVerifier.create(apiClient.createChatCompletionStream(request))
                    .expectNextMatches(event -> {
                        log.debug("流式工具事件1: {}", event);
                        return event.has("type") &&
                               ("message_start".equals(event.get("type").asText()) ||
                                "content_block_start".equals(event.get("type").asText()));
                    })
                    .expectNextMatches(event -> {
                        log.debug("流式工具事件2: {}", event);
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        log.debug("流式工具事件3: {}", event);
                        // 可能是工具调用开始
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        log.debug("流式工具事件4: {}", event);
                        return event.has("type");
                    })
                    .expectNextMatches(event -> {
                        log.debug("流式工具事件5: {}", event);
                        return event.has("type");
                    })
                    .expectComplete()
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ 工具调用流式测试完成");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("工具调用流式测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("工具调用错误处理")
    void testToolCallErrorHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "工具调用错误处理";

        try {
            log.info("🚀 开始工具调用错误处理测试");

            // 创建无效的工具定义
            ObjectNode request = createBasicChatRequest("请使用这个工具");

            var tools = objectMapper.createArrayNode();
            var invalidTool = objectMapper.createObjectNode();
            invalidTool.put("type", "function");

            var function = objectMapper.createObjectNode();
            function.put("name", ""); // 空名称 - 无效
            function.put("description", "");
            // 缺少必需的parameters字段

            invalidTool.set("function", function);
            tools.add(invalidTool);
            request.set("tools", tools);

            StepVerifier.create(apiClient.createToolCall(request))
                    .expectErrorMatches(throwable -> {
                        log.info("✅ 成功捕获工具调用错误: {}", throwable.getMessage());
                        return throwable instanceof RuntimeException ||
                               throwable.getMessage().contains("Invalid") ||
                               throwable.getMessage().contains("400");
                    })
                    .verify(Duration.ofSeconds(config.getTimeoutSeconds()));

            log.info("✅ 工具调用错误处理测试完成");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("工具调用错误处理测试失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("工具调用响应处理")
    void testToolCallResponseHandling() {
        long startTime = System.currentTimeMillis();
        String testName = "工具调用响应处理";

        try {
            log.info("🚀 开始工具调用响应处理测试");

            // 模拟工具调用后的响应处理
            String toolName = "get_current_time";
            String toolDescription = "获取当前时间";
            String userMessage = "请告诉我现在几点了";

            ObjectNode request = createToolCallRequest(userMessage, toolName, toolDescription);

            JsonNode response = apiClient.createToolCall(request)
                    .block(Duration.ofSeconds(config.getTimeoutSeconds()));

            assertNotNull(response, "工具调用响应不应为空");

            // 验证响应格式符合Claude API规范
            assertTrue(response.has("id"), "响应应有ID");
            assertTrue(response.has("type"), "响应应有类型");
            assertEquals("message", response.get("type").asText(), "类型应为message");

            log.info("✅ 工具调用响应处理测试完成");
            logPerformanceMetrics(testName, startTime);
            logTestCompletion(testName);

        } catch (Exception e) {
            logTestError(testName, e);
            fail("工具调用响应处理测试失败: " + e.getMessage());
        }
    }
}