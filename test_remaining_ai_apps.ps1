# 测试剩余AI应用包名验证脚本
# 专门验证IMA、纳米AI、Manus的包名配置

Write-Host "=== 剩余AI应用包名验证脚本 ===" -ForegroundColor Green
Write-Host ""

# 定义剩余AI应用包名列表
$remainingAiApps = @{
    "Manus" = @(
        "tech.butterfly.app",      # 真实包名（Google Play验证）
        "com.manus.search",
        "com.manus.app",
        "com.manus.ai"
    )
    "IMA" = @(
        "com.tencent.ima.copilot", # 推测包名（腾讯ima.copilot）
        "com.ima.ai",
        "com.ima.app",
        "com.tencent.ima"
    )
    "纳米AI" = @(
        "com.qihoo.nanoai",        # 推测包名（360纳米AI）
        "com.360.nanoai",
        "com.nanoai.app",
        "com.nano.ai"
    )
}

Write-Host "检查Android设备连接状态..." -ForegroundColor Yellow
$adbCheck = adb devices 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ ADB未找到或Android设备未连接" -ForegroundColor Red
    Write-Host "请确保:" -ForegroundColor Yellow
    Write-Host "1. 已安装Android SDK Platform Tools" -ForegroundColor Yellow
    Write-Host "2. Android设备已连接并启用USB调试" -ForegroundColor Yellow
    Write-Host "3. ADB在系统PATH中" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ ADB连接正常" -ForegroundColor Green
Write-Host ""

# 获取所有已安装的包
Write-Host "获取设备上所有已安装的应用包..." -ForegroundColor Yellow
$allPackages = adb shell pm list packages | ForEach-Object { $_.Replace("package:", "") }

if ($allPackages.Count -eq 0) {
    Write-Host "❌ 无法获取设备上的应用包列表" -ForegroundColor Red
    exit 1
}

Write-Host "✅ 成功获取 $($allPackages.Count) 个应用包" -ForegroundColor Green
Write-Host ""

# 检查每个AI应用
$foundApps = @()
$missingApps = @()

foreach ($appName in $remainingAiApps.Keys) {
    Write-Host "检查 $appName ..." -ForegroundColor Cyan
    $packages = $remainingAiApps[$appName]
    $found = $false
    
    foreach ($package in $packages) {
        if ($allPackages -contains $package) {
            Write-Host "  ✅ 找到: $package" -ForegroundColor Green
            $foundApps += @{
                "AppName" = $appName
                "PackageName" = $package
                "Status" = "已安装"
                "Priority" = if ($package -eq $packages[0]) { "主包名" } else { "备用包名" }
            }
            $found = $true
            break
        } else {
            Write-Host "  ❌ 未找到: $package" -ForegroundColor Red
        }
    }
    
    if (-not $found) {
        Write-Host "  ❌ 未找到任何包: $($packages -join ', ')" -ForegroundColor Red
        $missingApps += @{
            "AppName" = $appName
            "Packages" = $packages
            "Status" = "未安装"
        }
    }
    Write-Host ""
}

# 特殊检查：搜索可能的相关包名
Write-Host "=== 搜索可能的相关包名 ===" -ForegroundColor Yellow
Write-Host ""

$searchTerms = @("manus", "ima", "nano", "butterfly", "tencent", "qihoo", "360")

foreach ($term in $searchTerms) {
    Write-Host "搜索包含 '$term' 的包名..." -ForegroundColor Cyan
    $matchingPackages = $allPackages | Where-Object { $_ -like "*$term*" }
    
    if ($matchingPackages.Count -gt 0) {
        Write-Host "  找到 $($matchingPackages.Count) 个相关包:" -ForegroundColor Green
        foreach ($pkg in $matchingPackages) {
            Write-Host "    • $pkg" -ForegroundColor Green
        }
    } else {
        Write-Host "  ❌ 未找到包含 '$term' 的包" -ForegroundColor Red
    }
    Write-Host ""
}

# 输出总结
Write-Host "=== 检查结果总结 ===" -ForegroundColor Green
Write-Host ""

if ($foundApps.Count -gt 0) {
    Write-Host "✅ 已安装的AI应用 ($($foundApps.Count)个):" -ForegroundColor Green
    foreach ($app in $foundApps) {
        $priorityText = if ($app.Priority -eq "主包名") { " (✅ 主包名)" } else { " (⚠️ 备用包名)" }
        Write-Host "  • $($app.AppName): $($app.PackageName)$priorityText" -ForegroundColor Green
    }
    Write-Host ""
}

if ($missingApps.Count -gt 0) {
    Write-Host "❌ 未安装的AI应用 ($($missingApps.Count)个):" -ForegroundColor Red
    foreach ($app in $missingApps) {
        Write-Host "  • $($app.AppName): $($app.Packages -join ', ')" -ForegroundColor Red
    }
    Write-Host ""
}

# 生成建议
Write-Host "=== 包名配置建议 ===" -ForegroundColor Blue
Write-Host ""

foreach ($app in $foundApps) {
    if ($app.Priority -eq "备用包名") {
        Write-Host "⚠️  建议更新 $($app.AppName) 的主包名为: $($app.PackageName)" -ForegroundColor Yellow
    }
}

if ($foundApps.Count -eq 0) {
    Write-Host "💡 建议:" -ForegroundColor Yellow
    Write-Host "1. 确认这些AI应用是否已正确安装" -ForegroundColor Yellow
    Write-Host "2. 检查应用商店中的实际包名" -ForegroundColor Yellow
    Write-Host "3. 考虑使用通用的AI助手应用作为替代" -ForegroundColor Yellow
}

# 生成详细报告
$reportFile = "remaining_ai_apps_report_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
$report = @"
剩余AI应用包名验证报告
生成时间: $(Get-Date)
设备信息: $(adb shell getprop ro.product.model 2>$null)

=== 已安装的AI应用 ===
"@

foreach ($app in $foundApps) {
    $report += "`n$($app.AppName): $($app.PackageName) ($($app.Priority))"
}

$report += "`n`n=== 未安装的AI应用 ==="
foreach ($app in $missingApps) {
    $report += "`n$($app.AppName): $($app.Packages -join ', ')"
}

$report += "`n`n=== 包名配置建议 ==="
foreach ($app in $foundApps) {
    if ($app.Priority -eq "备用包名") {
        $report += "`n建议更新 $($app.AppName) 的主包名为: $($app.PackageName)"
    }
}

$report | Out-File -FilePath $reportFile -Encoding UTF8
Write-Host "📄 详细报告已保存到: $reportFile" -ForegroundColor Blue

Write-Host ""
Write-Host "=== 验证完成 ===" -ForegroundColor Green
Write-Host "请根据上述结果更新代码中的包名配置。" -ForegroundColor Yellow
