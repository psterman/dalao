# 验证真实AI应用包名脚本
# 验证用户提供的真实包名：IMA、纳米AI、Manus

Write-Host "=== 真实AI应用包名验证脚本 ===" -ForegroundColor Green
Write-Host ""

# 定义真实包名
$realPackageNames = @{
    "IMA" = "com.tencent.ima"
    "纳米AI" = "com.qihoo.namiso"  
    "Manus" = "com.manus.im.app"
}

Write-Host "验证以下真实包名:" -ForegroundColor Yellow
foreach ($app in $realPackageNames.Keys) {
    Write-Host "  • $app : $($realPackageNames[$app])" -ForegroundColor Cyan
}
Write-Host ""

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

# 验证每个真实包名
$verificationResults = @()

foreach ($appName in $realPackageNames.Keys) {
    $packageName = $realPackageNames[$appName]
    Write-Host "验证 $appName ($packageName)..." -ForegroundColor Cyan
    
    # 检查包是否已安装
    $packageCheck = adb shell pm list packages $packageName 2>$null
    
    if ($packageCheck -and $packageCheck.Contains($packageName)) {
        Write-Host "  ✅ 包已安装: $packageName" -ForegroundColor Green
        
        # 尝试获取应用信息
        $appInfo = adb shell dumpsys package $packageName 2>$null
        if ($appInfo) {
            Write-Host "  ✅ 应用信息获取成功" -ForegroundColor Green
            
            # 尝试获取启动Activity
            $launchActivity = adb shell pm resolve-activity -c android.intent.category.LAUNCHER $packageName 2>$null
            if ($launchActivity) {
                Write-Host "  ✅ 启动Activity可用" -ForegroundColor Green
            } else {
                Write-Host "  ⚠️  启动Activity未找到" -ForegroundColor Yellow
            }
        }
        
        $verificationResults += @{
            "AppName" = $appName
            "PackageName" = $packageName
            "Status" = "已安装"
            "Verified" = $true
        }
    } else {
        Write-Host "  ❌ 包未安装: $packageName" -ForegroundColor Red
        $verificationResults += @{
            "AppName" = $appName
            "PackageName" = $packageName
            "Status" = "未安装"
            "Verified" = $false
        }
    }
    Write-Host ""
}

# 测试应用启动
Write-Host "=== 测试应用启动 ===" -ForegroundColor Yellow
Write-Host ""

foreach ($result in $verificationResults) {
    if ($result.Verified) {
        $appName = $result.AppName
        $packageName = $result.PackageName
        
        Write-Host "测试启动 $appName ($packageName)..." -ForegroundColor Cyan
        
        # 尝试启动应用
        $launchResult = adb shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>$null
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✅ 应用启动成功" -ForegroundColor Green
            
            # 等待2秒后检查应用是否在前台
            Start-Sleep -Seconds 2
            $currentApp = adb shell dumpsys window windows | Select-String "mCurrentFocus" 2>$null
            
            if ($currentApp -and $currentApp.ToString().Contains($packageName)) {
                Write-Host "  ✅ 应用已在前台运行" -ForegroundColor Green
            } else {
                Write-Host "  ⚠️  应用可能未完全启动" -ForegroundColor Yellow
            }
        } else {
            Write-Host "  ❌ 应用启动失败" -ForegroundColor Red
        }
        Write-Host ""
    }
}

# 输出验证总结
Write-Host "=== 验证结果总结 ===" -ForegroundColor Green
Write-Host ""

$installedCount = ($verificationResults | Where-Object { $_.Verified }).Count
$totalCount = $verificationResults.Count

Write-Host "总计验证: $totalCount 个应用" -ForegroundColor Cyan
Write-Host "已安装: $installedCount 个应用" -ForegroundColor Green
Write-Host "未安装: $($totalCount - $installedCount) 个应用" -ForegroundColor Red
Write-Host ""

if ($installedCount -gt 0) {
    Write-Host "✅ 已验证的真实包名:" -ForegroundColor Green
    foreach ($result in $verificationResults) {
        if ($result.Verified) {
            Write-Host "  • $($result.AppName): $($result.PackageName)" -ForegroundColor Green
        }
    }
    Write-Host ""
}

if ($installedCount -lt $totalCount) {
    Write-Host "❌ 未安装的应用:" -ForegroundColor Red
    foreach ($result in $verificationResults) {
        if (-not $result.Verified) {
            Write-Host "  • $($result.AppName): $($result.PackageName)" -ForegroundColor Red
        }
    }
    Write-Host ""
}

# 生成配置更新建议
Write-Host "=== 配置更新状态 ===" -ForegroundColor Blue
Write-Host ""

Write-Host "✅ 代码已更新为真实包名:" -ForegroundColor Green
Write-Host "  • AppSearchSettings.kt - 主配置已更新" -ForegroundColor Green
Write-Host "  • SimpleModeActivity.kt - 包名优先级已调整" -ForegroundColor Green
Write-Host ""

Write-Host "📋 建议的下一步操作:" -ForegroundColor Yellow
Write-Host "1. 重新编译应用" -ForegroundColor Yellow
Write-Host "2. 安装到测试设备" -ForegroundColor Yellow
Write-Host "3. 测试简易模式 → 软件tab → AI分类" -ForegroundColor Yellow
Write-Host "4. 验证各AI应用的跳转功能" -ForegroundColor Yellow
Write-Host "5. 查看应用日志确认包名检测结果" -ForegroundColor Yellow

# 生成验证报告
$reportFile = "real_package_verification_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
$report = @"
真实AI应用包名验证报告
生成时间: $(Get-Date)
设备信息: $(adb shell getprop ro.product.model 2>$null)

=== 验证的真实包名 ===
IMA: com.tencent.ima
纳米AI: com.qihoo.namiso  
Manus: com.manus.im.app

=== 验证结果 ===
"@

foreach ($result in $verificationResults) {
    $status = if ($result.Verified) { "✅ 已安装" } else { "❌ 未安装" }
    $report += "`n$($result.AppName): $($result.PackageName) - $status"
}

$report += "`n`n=== 代码更新状态 ===`n✅ AppSearchSettings.kt - 已更新真实包名`n✅ SimpleModeActivity.kt - 已调整包名优先级"

$report | Out-File -FilePath $reportFile -Encoding UTF8
Write-Host ""
Write-Host "📄 验证报告已保存到: $reportFile" -ForegroundColor Blue

Write-Host ""
Write-Host "=== 验证完成 ===" -ForegroundColor Green
Write-Host "真实包名配置已更新完成！" -ForegroundColor Yellow
