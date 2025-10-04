package org.yanhuang.ai.e2e.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 测试状态管理器
 * 负责跟踪测试执行状态、保存测试结果和管理测试会话
 */
@Component
public class TestStateManager {

    private static final Logger log = LoggerFactory.getLogger(TestStateManager.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private E2ETestConfig config;

    @Autowired
    private KiroCallCounter callCounter;

    private final Map<String, TestSession> activeSessions = new HashMap<>();
    private final AtomicInteger totalTestsRun = new AtomicInteger(0);
    private final AtomicInteger totalTestsPassed = new AtomicInteger(0);
    private final AtomicInteger totalTestsFailed = new AtomicInteger(0);
    private final AtomicInteger totalTestsSkipped = new AtomicInteger(0);

    /**
     * 开始新的测试会话
     */
    public TestSession startTestSession(String suiteName, String description) {
        String sessionId = generateSessionId();
        TestSession session = new TestSession(sessionId, suiteName, description);

        activeSessions.put(sessionId, session);

        log.info("🚀 开始测试会话: {} - {}", suiteName, description);
        log.info("会话ID: {}, 开始时间: {}", sessionId, session.getStartTime());

        // 记录Kiro调用状态
        session.setKiroCallsAtStart(callCounter.getCurrentBatchCalls());
        session.setKiroTotalCallsAtStart(callCounter.getTotalCalls());

        return session;
    }

    /**
     * 结束测试会话
     */
    public void endTestSession(String sessionId) {
        TestSession session = activeSessions.get(sessionId);
        if (session == null) {
            log.warn("测试会话不存在: {}", sessionId);
            return;
        }

        session.setEndTime(LocalDateTime.now());
        session.setKiroCallsAtEnd(callCounter.getCurrentBatchCalls());
        session.setKiroTotalCallsAtEnd(callCounter.getTotalCalls());

        // 计算统计信息
        session.setDuration(java.time.Duration.between(session.getStartTime(), session.getEndTime()));
        session.setKiroCallsUsed(session.getKiroCallsAtEnd() - session.getKiroCallsAtStart());
        session.setKiroTotalCallsUsed(session.getKiroTotalCallsAtEnd() - session.getKiroTotalCallsAtStart());

        log.info("✅ 测试会话完成: {}", session.getSuiteName());
        log.info("会话统计 - 持续时间: {}ms, Kiro调用: {}次, 测试: {}/{}/{} (通过/失败/跳过)",
                session.getDuration().toMillis(),
                session.getKiroCallsUsed(),
                session.getTestsPassed(),
                session.getTestsFailed(),
                session.getTestsSkipped());

        // 保存会话状态
        saveSessionState(session);

        activeSessions.remove(sessionId);
    }

    /**
     * 记录测试开始
     */
    public void recordTestStart(String sessionId, String testName) {
        TestSession session = activeSessions.get(sessionId);
        if (session != null) {
            TestResult testResult = new TestResult(testName, LocalDateTime.now());
            session.addTestResult(testResult);
            totalTestsRun.incrementAndGet();
        }
    }

    /**
     * 记录测试成功
     */
    public void recordTestSuccess(String sessionId, String testName, long duration, Map<String, Object> metadata) {
        TestSession session = activeSessions.get(sessionId);
        if (session != null) {
            TestResult testResult = session.getTestResults().stream()
                    .filter(tr -> tr.getTestName().equals(testName))
                    .findFirst()
                    .orElse(null);

            if (testResult != null) {
                testResult.setStatus(TestStatus.PASSED);
                testResult.setEndTime(LocalDateTime.now());
                testResult.setDuration(duration);
                testResult.setMetadata(metadata);
                totalTestsPassed.incrementAndGet();
            }
        }
    }

    /**
     * 记录测试失败
     */
    public void recordTestFailure(String sessionId, String testName, String errorMessage, Throwable throwable) {
        TestSession session = activeSessions.get(sessionId);
        if (session != null) {
            TestResult testResult = session.getTestResults().stream()
                    .filter(tr -> tr.getTestName().equals(testName))
                    .findFirst()
                    .orElse(null);

            if (testResult != null) {
                testResult.setStatus(TestStatus.FAILED);
                testResult.setEndTime(LocalDateTime.now());
                testResult.setErrorMessage(errorMessage);
                testResult.setException(throwable != null ? throwable.getClass().getSimpleName() : null);
                totalTestsFailed.incrementAndGet();
            }
        }
    }

    /**
     * 记录测试跳过
     */
    public void recordTestSkipped(String sessionId, String testName, String reason) {
        TestSession session = activeSessions.get(sessionId);
        if (session != null) {
            TestResult testResult = session.getTestResults().stream()
                    .filter(tr -> tr.getTestName().equals(testName))
                    .findFirst()
                    .orElse(null);

            if (testResult != null) {
                testResult.setStatus(TestStatus.SKIPPED);
                testResult.setEndTime(LocalDateTime.now());
                testResult.setSkipReason(reason);
                totalTestsSkipped.incrementAndGet();
            }
        }
    }

    /**
     * 获取当前测试统计
     */
    public TestStatistics getCurrentStatistics() {
        return new TestStatistics(
            totalTestsRun.get(),
            totalTestsPassed.get(),
            totalTestsFailed.get(),
            totalTestsSkipped.get(),
            callCounter.getCurrentBatchCalls(),
            callCounter.getTotalCalls(),
            callCounter.getRemainingCalls()
        );
    }

    /**
     * 生成测试报告
     */
    public void generateTestReport(String sessionId) {
        TestSession session = activeSessions.get(sessionId);
        if (session == null) {
            log.warn("无法生成报告 - 测试会话不存在: {}", sessionId);
            return;
        }

        try {
            ObjectNode report = objectMapper.createObjectNode();
            report.put("suiteName", session.getSuiteName());
            report.put("sessionId", session.getSessionId());
            report.put("description", session.getDescription());
            report.put("startTime", session.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            report.put("endTime", session.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            report.put("duration", session.getDuration().toMillis());

            // 测试统计
            ObjectNode testStats = report.putObject("testStatistics");
            testStats.put("total", session.getTestResults().size());
            testStats.put("passed", session.getTestsPassed());
            testStats.put("failed", session.getTestsFailed());
            testStats.put("skipped", session.getTestsSkipped());
            testStats.put("passRate", session.getTestResults().isEmpty() ? 0.0 :
                          (double) session.getTestsPassed() / session.getTestResults().size() * 100);

            // Kiro调用统计
            ObjectNode kiroStats = report.putObject("kiroStatistics");
            kiroStats.put("callsUsed", session.getKiroCallsUsed());
            kiroStats.put("totalCallsUsed", session.getKiroTotalCallsUsed());
            kiroStats.put("remainingCalls", callCounter.getRemainingCalls());
            kiroStats.put("maxCallsPerBatch", config.getMaxCallsPerBatch());

            // 详细测试结果
            var testResults = report.putArray("testResults");
            for (TestResult result : session.getTestResults()) {
                ObjectNode testResult = testResults.addObject();
                testResult.put("name", result.getTestName());
                testResult.put("status", result.getStatus().name());
                testResult.put("startTime", result.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                if (result.getEndTime() != null) {
                    testResult.put("endTime", result.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    testResult.put("duration", result.getDuration());
                }
                if (result.getErrorMessage() != null) {
                    testResult.put("errorMessage", result.getErrorMessage());
                }
                if (result.getSkipReason() != null) {
                    testResult.put("skipReason", result.getSkipReason());
                }
            }

            // 保存报告
            String reportFileName = String.format("test-report-%s-%s.json",
                    session.getSuiteName().replaceAll("[^a-zA-Z0-9]", "-"),
                    session.getSessionId());
            Path reportPath = Paths.get("target", "test-reports", reportFileName);

            Files.createDirectories(reportPath.getParent());
            Files.write(reportPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));

            log.info("📊 测试报告已生成: {}", reportPath.toAbsolutePath());

        } catch (IOException e) {
            log.error("生成测试报告失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理旧的状态文件
     */
    public void cleanupOldStateFiles() {
        try {
            Path stateDir = Paths.get("target");
            if (Files.exists(stateDir)) {
                Files.list(stateDir)
                        .filter(path -> path.getFileName().toString().startsWith("e2e-test-state-"))
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                                log.debug("清理旧状态文件: {}", path.getFileName());
                            } catch (IOException e) {
                                log.warn("删除状态文件失败: {}", path.getFileName());
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("清理状态文件失败: {}", e.getMessage());
        }
    }

    private String generateSessionId() {
        return "session-" + System.currentTimeMillis() + "-" +
               Integer.toHexString((int) (Math.random() * 0xFFFF));
    }

    private void saveSessionState(TestSession session) {
        try {
            ObjectNode state = objectMapper.createObjectNode();
            state.put("sessionId", session.getSessionId());
            state.put("suiteName", session.getSuiteName());
            state.put("description", session.getDescription());
            state.put("startTime", session.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            state.put("endTime", session.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            state.put("duration", session.getDuration().toMillis());
            state.put("kiroCallsUsed", session.getKiroCallsUsed());
            state.put("kiroTotalCallsUsed", session.getKiroTotalCallsUsed());
            state.put("testsPassed", session.getTestsPassed());
            state.put("testsFailed", session.getTestsFailed());
            state.put("testsSkipped", session.getTestsSkipped());

            String stateFileName = String.format("e2e-test-state-%s.json", session.getSessionId());
            Path statePath = Paths.get("target", stateFileName);

            Files.createDirectories(statePath.getParent());
            Files.write(statePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state));

            log.debug("测试会话状态已保存: {}", statePath);

        } catch (IOException e) {
            log.error("保存测试会话状态失败: {}", e.getMessage(), e);
        }
    }

    // 内部数据类
    public static class TestSession {
        private final String sessionId;
        private final String suiteName;
        private final String description;
        private final LocalDateTime startTime;
        private LocalDateTime endTime;
        private java.time.Duration duration;
        private final List<TestResult> testResults = new ArrayList<>();
        private int kiroCallsAtStart;
        private int kiroCallsAtEnd;
        private int kiroTotalCallsAtStart;
        private int kiroTotalCallsAtEnd;
        private int kiroCallsUsed;
        private int kiroTotalCallsUsed;

        public TestSession(String sessionId, String suiteName, String description) {
            this.sessionId = sessionId;
            this.suiteName = suiteName;
            this.description = description;
            this.startTime = LocalDateTime.now();
        }

        // Getters and setters
        public String getSessionId() { return sessionId; }
        public String getSuiteName() { return suiteName; }
        public String getDescription() { return description; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public java.time.Duration getDuration() { return duration; }
        public void setDuration(java.time.Duration duration) { this.duration = duration; }
        public List<TestResult> getTestResults() { return testResults; }
        public void addTestResult(TestResult result) { this.testResults.add(result); }
        public long getTestsPassed() { return testResults.stream().filter(tr -> tr.getStatus() == TestStatus.PASSED).count(); }
        public long getTestsFailed() { return testResults.stream().filter(tr -> tr.getStatus() == TestStatus.FAILED).count(); }
        public long getTestsSkipped() { return testResults.stream().filter(tr -> tr.getStatus() == TestStatus.SKIPPED).count(); }
        public int getKiroCallsAtStart() { return kiroCallsAtStart; }
        public void setKiroCallsAtStart(int kiroCallsAtStart) { this.kiroCallsAtStart = kiroCallsAtStart; }
        public int getKiroCallsAtEnd() { return kiroCallsAtEnd; }
        public void setKiroCallsAtEnd(int kiroCallsAtEnd) { this.kiroCallsAtEnd = kiroCallsAtEnd; }
        public int getKiroTotalCallsAtStart() { return kiroTotalCallsAtStart; }
        public void setKiroTotalCallsAtStart(int kiroTotalCallsAtStart) { this.kiroTotalCallsAtStart = kiroTotalCallsAtStart; }
        public int getKiroTotalCallsAtEnd() { return kiroTotalCallsAtEnd; }
        public void setKiroTotalCallsAtEnd(int kiroTotalCallsAtEnd) { this.kiroTotalCallsAtEnd = kiroTotalCallsAtEnd; }
        public int getKiroCallsUsed() { return kiroCallsUsed; }
        public void setKiroCallsUsed(int kiroCallsUsed) { this.kiroCallsUsed = kiroCallsUsed; }
        public int getKiroTotalCallsUsed() { return kiroTotalCallsUsed; }
        public void setKiroTotalCallsUsed(int kiroTotalCallsUsed) { this.kiroTotalCallsUsed = kiroTotalCallsUsed; }
    }

    public static class TestResult {
        private final String testName;
        private final LocalDateTime startTime;
        private LocalDateTime endTime;
        private long duration;
        private TestStatus status = TestStatus.RUNNING;
        private String errorMessage;
        private String exception;
        private String skipReason;
        private Map<String, Object> metadata;

        public TestResult(String testName, LocalDateTime startTime) {
            this.testName = testName;
            this.startTime = startTime;
        }

        // Getters and setters
        public String getTestName() { return testName; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        public TestStatus getStatus() { return status; }
        public void setStatus(TestStatus status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getException() { return exception; }
        public void setException(String exception) { this.exception = exception; }
        public String getSkipReason() { return skipReason; }
        public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public enum TestStatus {
        RUNNING, PASSED, FAILED, SKIPPED
    }

    public static class TestStatistics {
        private final int totalTests;
        private final int passedTests;
        private final int failedTests;
        private final int skippedTests;
        private final int currentKiroCalls;
        private final int totalKiroCalls;
        private final int remainingKiroCalls;

        public TestStatistics(int totalTests, int passedTests, int failedTests, int skippedTests,
                              int currentKiroCalls, int totalKiroCalls, int remainingKiroCalls) {
            this.totalTests = totalTests;
            this.passedTests = passedTests;
            this.failedTests = failedTests;
            this.skippedTests = skippedTests;
            this.currentKiroCalls = currentKiroCalls;
            this.totalKiroCalls = totalKiroCalls;
            this.remainingKiroCalls = remainingKiroCalls;
        }

        // Getters
        public int getTotalTests() { return totalTests; }
        public int getPassedTests() { return passedTests; }
        public int getFailedTests() { return failedTests; }
        public int getSkippedTests() { return skippedTests; }
        public int getCurrentKiroCalls() { return currentKiroCalls; }
        public int getTotalKiroCalls() { return totalKiroCalls; }
        public int getRemainingKiroCalls() { return remainingKiroCalls; }
        public double getPassRate() { return totalTests == 0 ? 0.0 : (double) passedTests / totalTests * 100; }
    }
}