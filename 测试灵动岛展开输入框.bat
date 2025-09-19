@echo off
chcp 65001 >nul
echo.
echo ==========================================
echo    灵动岛展开输入框功能测试
echo ==========================================
echo.

echo 📱 正在启动应用...
adb shell am start -n com.example.aifloatingball/.HomeActivity
timeout /t 3 >nul

echo.
echo 🔧 正在启动灵动岛服务...
adb shell am startservice -n com.example.aifloatingball/.service.DynamicIslandService
timeout /t 2 >nul

echo.
echo 📋 正在设置测试剪贴板内容...
adb shell am broadcast -a clipper.set -e text "测试搜索内容：人工智能发展趋势"
timeout /t 1 >nul

echo.
echo ✅ 测试环境准备完成！
echo.
echo 📖 测试步骤（已优化版本）：
echo    1. 查看灵动岛是否显示在屏幕顶部
echo    2. 点击灵动岛的展开按钮（向下箭头）
echo    3. 确认展开后显示：
echo       - 第一行：APP图标、AI图标、关闭按钮
echo       - 第二行：优化后的输入框（白色背景、绿色边框）、发送按钮
echo    4. 点击输入框，确认：
echo       - 边框变为蓝色（焦点状态）
echo       - 可以编辑内容
echo       - 文字清晰可读（深色文字）
echo    5. 点击绿色发送按钮，确认搜索面板打开
echo    6. 点击关闭按钮，确认返回灵动岛初始状态（不是白色小球）
echo    7. 确认没有重复的蓝色输入框出现
echo.
echo 🔍 监控日志（按Ctrl+C停止）：
echo.
adb logcat -s DynamicIslandService | findstr /i "展开\|输入\|发送\|搜索"
