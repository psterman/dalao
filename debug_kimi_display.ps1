# Kimi显示问题调试脚本
# 用于检查Kimi在灵动岛中的显示问题

Write-Host "=== Kimi显示问题调试脚本 ===" -ForegroundColor Green
Write-Host ""

# 1. 检查Kimi是否在默认启用的AI引擎中
Write-Host "1. 检查Kimi是否在默认启用的AI引擎中..." -ForegroundColor Yellow
adb logcat -c
Write-Host "已清空日志缓存，请复制文本激活灵动岛，然后按任意键继续..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# 2. 查看已启用的AI引擎列表
Write-Host ""
Write-Host "2. 查看已启用的AI引擎列表..." -ForegroundColor Yellow
adb logcat | Select-String "已启用的AI引擎" | Select-Object -First 5

# 3. 查看Kimi的映射和API密钥检查
Write-Host ""
Write-Host "3. 查看Kimi的映射和API密钥检查..." -ForegroundColor Yellow
adb logcat | Select-String -Pattern "(处理已启用的AI引擎.*Kimi|找到映射.*Kimi|检查 Kimi|Kimi API密钥)" | Select-Object -First 10

# 4. 查看最终配置的AI服务列表
Write-Host ""
Write-Host "4. 查看最终配置的AI服务列表..." -ForegroundColor Yellow
adb logcat | Select-String "最终配置好的AI服务" | Select-Object -First 3

# 5. 查看Kimi API密钥的具体内容
Write-Host ""
Write-Host "5. 查看Kimi API密钥的具体内容..." -ForegroundColor Yellow
adb logcat | Select-String "检查 Kimi API密钥" | Select-Object -First 3

# 6. 检查Kimi是否被添加到可用列表
Write-Host ""
Write-Host "6. 检查Kimi是否被添加到可用列表..." -ForegroundColor Yellow
adb logcat | Select-String "Kimi.*已配置，添加到可用列表" | Select-Object -First 3

Write-Host ""
Write-Host "=== 调试完成 ===" -ForegroundColor Green
Write-Host "如果Kimi仍然没有显示，请检查："
Write-Host "1. Kimi是否在已启用的AI引擎列表中"
Write-Host "2. Kimi的API密钥是否已配置且有效"
Write-Host "3. Kimi的映射是否正确"
Write-Host "4. API密钥验证是否通过"