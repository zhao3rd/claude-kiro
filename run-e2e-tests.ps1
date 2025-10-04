# E2E测试执行脚本 (PowerShell)
# 用于claude-kiro项目的端到端测试自动化执行

param(
    [string]$TestClass = "",
    [string]$TestMethod = "",
    [int]$TimeoutSeconds = 300,
    [switch]$SkipCleanup = $false,
    [switch]$Debug = $false
)

Write-Host "=== Claude-Kiro E2E 测试执行脚本 ===" -ForegroundColor Green
Write-Host "开始时间: $(Get-Date)" -ForegroundColor Gray

# 检查Java环境
Write-Host "`n1. 检查Java环境..." -ForegroundColor Cyan
if ($env:JAVA21_HOME) {
    $javaExe = Join-Path $env:JAVA21_HOME "bin" "java.exe"
    if (Test-Path $javaExe) {
        $javaVersion = & $javaExe -version 2>&1
        Write-Host "✅ Java 21 已找到: $($javaVersion[0])" -ForegroundColor Green
    } else {
        Write-Host "❌ Java 21 未找到，请检查JAVA21_HOME环境变量" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "❌ JAVA21_HOME环境变量未设置" -ForegroundColor Red
    exit 1
}

# 检查必需的环境变量
Write-Host "`n2. 检查环境变量..." -ForegroundColor Cyan

$requiredEnvVars = @(
    "CLAUDE_API_KEY"
)

$optionalEnvVars = @(
    "KIRO_BASE_URL",
    "KIRO_PROFILE_ARN",
    "KIRO_ACCESS_TOKEN",
    "KIRO_REFRESH_TOKEN",
    "KIRO_REFRESH_URL"
)

$missingVars = @()
foreach ($var in $requiredEnvVars) {
    if ([string]::IsNullOrEmpty($env:$var)) {
        $missingVars += $var
    } else {
        Write-Host "✅ $var = [已设置]" -ForegroundColor Green
    }
}

if ($missingVars.Count -gt 0) {
    Write-Host "❌ 缺少必需的环境变量: $($missingVars -join ', ')" -ForegroundColor Red
    Write-Host "请设置以下环境变量后重新运行脚本:" -ForegroundColor Yellow
    foreach ($var in $missingVars) {
        Write-Host "  `$env:$var = 'your-value'" -ForegroundColor Gray
    }
    exit 1
}

# 显示可选环境变量状态
foreach ($var in $optionalEnvVars) {
    if ([string]::IsNullOrEmpty($env:$var)) {
        Write-Host "⚠️  $var = [未设置 - 使用默认值]" -ForegroundColor Yellow
    } else {
        Write-Host "✅ $var = [已设置]" -ForegroundColor Green
    }
}

# 检查Maven
Write-Host "`n3. 检查Maven..." -ForegroundColor Cyan
try {
    $mvnVersion = mvn --version 2>&1 | Select-Object -First 1
    Write-Host "✅ Maven 已找到: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Maven 未找到或不可用" -ForegroundColor Red
    exit 1
}

# 清理旧的测试文件
if (-not $SkipCleanup) {
    Write-Host "`n4. 清理旧的测试文件..." -ForegroundColor Cyan

    $targetDir = "target"
    if (Test-Path $targetDir) {
        Remove-Item -Path "$targetDir\test-reports\test-report-*.json" -ErrorAction SilentlyContinue -Force
        Remove-Item -Path "$targetDir\e2e-test-state-*.json" -ErrorAction SilentlyContinue -Force
        Write-Host "✅ 清理完成" -ForegroundColor Green
    }
} else {
    Write-Host "`n4. 跳过清理操作" -ForegroundColor Yellow
}

# 构建项目
Write-Host "`n5. 构建项目..." -ForegroundColor Cyan
try {
    mvn clean compile test-compile -q
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ 项目构建成功" -ForegroundColor Green
    } else {
        Write-Host "❌ 项目构建失败" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "❌ 构建过程中出现异常: $_" -ForegroundColor Red
    exit 1
}

# 确定测试目标
$testTarget = ""
if ([string]::IsNullOrEmpty($TestClass)) {
    $testTarget = "E2ETestRunner"
    Write-Host "`n6. 运行所有E2E测试..." -ForegroundColor Cyan
} else {
    if ([string]::IsNullOrEmpty($TestMethod)) {
        $testTarget = $TestClass
        Write-Host "`n6. 运行测试类: $TestClass..." -ForegroundColor Cyan
    } else {
        $testTarget = "$TestClass#$TestMethod"
        Write-Host "`n6. 运行测试方法: $TestClass.$TestMethod..." -ForegroundColor Cyan
    }
}

# 设置Maven参数
$mavenArgs = @(
    "test",
    "-Dtest=$testTarget",
    "-Dmaven.test.timeout=$($TimeoutSeconds * 1000)",
    "-DJAVA21_HOME=$env:JAVA21_HOME",
    "-e"
)

if ($Debug) {
    $mavenArgs += "-X"
    Write-Host "🐛 调试模式已启用" -ForegroundColor Yellow
}

# 执行测试
Write-Host "`n7. 执行E2E测试..." -ForegroundColor Cyan
Write-Host "测试目标: $testTarget" -ForegroundColor Gray
Write-Host "超时设置: $TimeoutSeconds 秒" -ForegroundColor Gray
Write-Host "开始时间: $(Get-Date)" -ForegroundColor Gray

$testStartTime = Get-Date

try {
    & mvn @mavenArgs
    $testExitCode = $LASTEXITCODE
} catch {
    Write-Host "❌ 测试执行异常: $_" -ForegroundColor Red
    $testExitCode = 1
}

$testEndTime = Get-Date
$testDuration = $testEndTime - $testStartTime

# 显示测试结果
Write-Host "`n=== 测试执行结果 ===" -ForegroundColor Green
Write-Host "结束时间: $(Get-Date)" -ForegroundColor Gray
Write-Host "执行时间: $($testDuration.TotalSeconds.ToString('F2')) 秒" -ForegroundColor Gray

if ($testExitCode -eq 0) {
    Write-Host "✅ 测试执行成功" -ForegroundColor Green
} else {
    Write-Host "❌ 测试执行失败 (退出代码: $testExitCode)" -ForegroundColor Red
}

# 显示生成的报告
Write-Host "`n8. 测试报告..." -ForegroundColor Cyan

$reportDir = "target\test-reports"
$stateDir = "target"

if (Test-Path $reportDir) {
    $reports = Get-ChildItem -Path $reportDir -Filter "test-report-*.json" -ErrorAction SilentlyContinue
    if ($reports.Count -gt 0) {
        Write-Host "📊 测试报告文件:" -ForegroundColor Green
        foreach ($report in $reports) {
            Write-Host "  - $($report.Name)" -ForegroundColor Gray
        }
    }
}

if (Test-Path $stateDir) {
    $states = Get-ChildItem -Path $stateDir -Filter "e2e-test-state-*.json" -ErrorAction SilentlyContinue
    if ($states.Count -gt 0) {
        Write-Host "📋 测试状态文件:" -ForegroundColor Green
        foreach ($state in $states) {
            Write-Host "  - $($state.Name)" -ForegroundColor Gray
        }
    }
}

# 显示Maven Surefire报告
$surefireDir = "target\surefire-reports"
if (Test-Path $surefireDir) {
    $surefireReports = Get-ChildItem -Path $surefireDir -Filter "*.txt" -ErrorAction SilentlyContinue
    if ($surefireReports.Count -gt 0) {
        Write-Host "📄 Maven Surefire报告:" -ForegroundColor Green
        foreach ($report in $surefireReports) {
            Write-Host "  - $($report.Name)" -ForegroundColor Gray
        }
    }
}

Write-Host "`n=== E2E测试执行完成 ===" -ForegroundColor Green

# 提供后续操作建议
Write-Host "`n📌 后续操作建议:" -ForegroundColor Yellow
Write-Host "1. 查看详细报告: Get-Content target\test-reports\test-report-*.json | ConvertFrom-Json" -ForegroundColor Gray
Write-Host "2. 检查测试日志: Get-Content target\surefire-reports\*.txt" -ForegroundColor Gray
Write-Host "3. 如需重新运行: .\run-e2e-tests.ps1 -TestClass '$TestClass'" -ForegroundColor Gray
Write-Host "4. 清理测试文件: Remove-Item target\test-reports\*, target\e2e-test-state-*" -ForegroundColor Gray

exit $testExitCode