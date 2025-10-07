package org.yanhuang.ai.e2e.suite;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.yanhuang.ai.e2e.ClaudeChatE2ETest;
import org.yanhuang.ai.e2e.MultiRoundConversationE2ETest;
import org.yanhuang.ai.e2e.PerformanceAndStressE2ETest;
import org.yanhuang.ai.e2e.StreamingAndErrorE2ETest;
import org.yanhuang.ai.e2e.ToolCallE2ETest;
import org.yanhuang.ai.e2e.ToolChoiceValidationE2ETest;
import org.yanhuang.ai.e2e.McpToolE2ETest;

/**
 * E2E测试套件
 * 集成所有E2E测试类，提供完整的端到端测试覆盖
 */
@Suite
@SelectClasses({
    ClaudeChatE2ETest.class,
    ToolCallE2ETest.class,
    ToolChoiceValidationE2ETest.class,
    McpToolE2ETest.class,
    MultiRoundConversationE2ETest.class,
    StreamingAndErrorE2ETest.class,
    PerformanceAndStressE2ETest.class
})
public class E2ETestSuite {

    /**
     * 测试套件说明：
     *
     * 1. ClaudeChatE2ETest - 基础聊天功能测试
     *    - 基础聊天对话
     *    - 多轮对话 - 上下文保持
     *    - 流式响应测试
     *    - 长文本处理测试
     *    - 错误处理测试
     *
     * 2. ToolCallE2ETest - 工具调用功能测试
     *    - 基础工具调用 - 搜索功能
     *    - 复杂工具调用 - 多参数函数
     *    - 多工具选择调用
     *    - 工具调用流式响应
     *    - 工具调用错误处理
     *    - 工具调用响应处理
     *
     * 3. MultiRoundConversationE2ETest - 多轮对话测试
     *    - 长期对话上下文保持
     *    - 复杂任务分步解决
     *    - 角色扮演对话
     *    - 上下文截断测试
     *
     * 4. StreamingAndErrorE2ETest - 流式响应和错误处理测试
     *    - 流式响应完整流程
     *    - 流式响应中断恢复
     *    - 并发请求处理
     *    - 大请求处理
     *    - 网络超时处理
     *    - 无效输入处理
     *    - 资源清理测试
     *
     * 5. PerformanceAndStressE2ETest - 性能和压力测试
     *    - 响应时间基准测试
     *    - 并发负载测试
     *    - 内存使用测试
     *    - 流式性能测试
     *    - 系统稳定性测试
     *    - 资源限制测试
     *
     * 运行方式：
     * mvn test -Dtest=E2ETestSuite
     *
     * 环境要求：
     * - CLAUDE_API_KEY 环境变量
     * - KIRO相关环境变量（如需要真实连接）
     * - 足够的Kiro调用额度（建议至少15-20次调用）
     */
}