package org.yanhuang.ai.e2e;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.yanhuang.ai.e2e.suite.E2ETestSuite;

/**
 * E2E测试运行器
 * 提供统一的E2E测试执行入口
 */
@Suite
@SelectClasses(E2ETestSuite.class)
public class E2ETestRunner {

    /**
     * E2E测试执行指南
     *
     * 1. 环境准备：
     *    - 设置 CLAUDE_API_KEY 环境变量
     *    - 配置 KIRO 相关环境变量（如需真实连接）
     *    - 确保有足够的Kiro调用额度（建议20+次）
     *
     * 2. 执行方式：
     *    - 运行所有E2E测试：mvn test -Dtest=E2ETestRunner
     *    - 运行特定测试类：mvn test -Dtest=ClaudeChatE2ETest
     *    - 运行带超时的测试：mvn test -Dtest=E2ETestRunner -Dmaven.test.timeout=300000
     *
     * 3. 配置选项：
     *    - 修改 src/test/resources/application-e2e.yml 中的配置
     *    - 调整 e2e.max-calls-per-batch 来控制每批测试的Kiro调用次数
     *    - 设置 e2e.skip-tests-on-quota-exceeded=true 来跳过额度不足的测试
     *
     * 4. 结果查看：
     *    - 测试报告：target/test-reports/test-report-*.json
     *    - 状态文件：target/e2e-test-state-*.json
     *    - 日志输出：控制台和 target/surefire-reports/
     *
     * 5. 故障排除：
     *    - 额度不足：检查Kiro调用配额或重置批次计数器
     *    - 网络问题：检查网络连接和防火墙设置
     *    - 认证失败：验证API密钥和访问令牌配置
     */
}