# AI应用包名验证脚本
# 用于验证各个AI应用的真实包名

Write-Host "=== AI应用包名验证脚本 ===" -ForegroundColor Green
Write-Host ""

# 定义AI应用包名列表
$aiApps = @{
    "Grok" = @("ai.x.grok", "com.xai.grok")
    "Perplexity" = @("ai.perplexity.app.android", "ai.perplexity.app")
    "Poe" = @("com.poe.android", "com.quora.poe")
    "文小言" = @("com.baidu.newapp", "com.baidu.wenxiaoyan")
    "秘塔AI搜索" = @("com.metaso", "com.mita.ai")
    "纳米AI" = @("com.nanoai.app", "com.nano.ai", "com.360.nanoai")
    "Manus" = @("com.manus.search", "com.manus.app", "com.manus.ai")
    "IMA" = @("com.ima.ai", "com.ima.app", "com.tencent.ima")
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

foreach ($appName in $aiApps.Keys) {
    Write-Host "检查 $appName ..." -ForegroundColor Cyan
    $packages = $aiApps[$appName]
    $found = $false
    
    foreach ($package in $packages) {
        if ($allPackages -contains $package) {
            Write-Host "  ✅ 找到: $package" -ForegroundColor Green
            $foundApps += @{
                "AppName" = $appName
                "PackageName" = $package
                "Status" = "已安装"
            }
            $found = $true
            break
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

# 输出总结
Write-Host "=== 检查结果总结 ===" -ForegroundColor Green
Write-Host ""

if ($foundApps.Count -gt 0) {
    Write-Host "✅ 已安装的AI应用 ($($foundApps.Count)个):" -ForegroundColor Green
    foreach ($app in $foundApps) {
        Write-Host "  • $($app.AppName): $($app.PackageName)" -ForegroundColor Green
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

# 生成详细报告
$reportFile = "ai_app_check_report_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
$report = @"
AI应用包名验证报告
生成时间: $(Get-Date)
设备信息: $(adb shell getprop ro.product.model 2>$null)

=== 已安装的AI应用 ===
"@

foreach ($app in $foundApps) {
    $report += "`n$($app.AppName): $($app.PackageName)"
}

$report += "`n`n=== 未安装的AI应用 ==="
foreach ($app in $missingApps) {
    $report += "`n$($app.AppName): $($app.Packages -join ', ')"
}

$report += "`n`n=== 建议的包名配置 ==="
$report += "`n基于检查结果，建议使用以下包名配置:"

foreach ($app in $foundApps) {
    $report += "`n$($app.AppName): $($app.PackageName) (已验证)"
}

$report | Out-File -FilePath $reportFile -Encoding UTF8
Write-Host "📄 详细报告已保存到: $reportFile" -ForegroundColor Blue

# 测试应用启动
Write-Host "=== 测试应用启动 ===" -ForegroundColor Green
Write-Host ""

foreach ($app in $foundApps) {
    $packageName = $app.PackageName
    $appName = $app.AppName
    
    Write-Host "测试启动 $appName ($packageName)..." -ForegroundColor Yellow
    
    # 尝试获取启动Intent
    $launchIntent = adb shell pm resolve-activity --brief $packageName 2>$null
    if ($LASTEXITCODE -eq 0 -and $launchIntent) {
        Write-Host "  ✅ 可以启动" -ForegroundColor Green
        
        # 尝试启动应用（不等待）
        adb shell am start -n $packageName/.MainActivity 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  ✅ 启动成功" -ForegroundColor Green
        } else {
            # 尝试使用包管理器启动
            adb shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "  ✅ 通过Monkey启动成功" -ForegroundColor Green
            } else {
                Write-Host "  ⚠️  启动失败，但应用已安装" -ForegroundColor Yellow
            }
        }
    } else {
        Write-Host "  ⚠️  无法获取启动Intent" -ForegroundColor Yellow
    }
    
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "=== 验证完成 ===" -ForegroundColor Green
Write-Host "请查看上述结果，确认AI应用的真实包名配置。" -ForegroundColor Yellow
Write-Host "如果发现包名不正确，请更新代码中的配置。" -ForegroundColor Yellow
