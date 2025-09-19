@echo off
chcp 65001 >nul
echo.
echo ========================================
echo    应用图标自动粘贴测试脚本
echo ========================================
echo.

echo 🎯 新功能说明：
echo    当用户展开灵动岛，点击应用图标时：
echo    1. 自动粘贴获取剪贴板最新数据
echo    2. 构建URL scheme搜索链接
echo    3. 跳转到应用的搜索结果页面
echo.

echo 🧪 测试步骤：
echo    1. 复制要搜索的文本到剪贴板
echo    2. 展开灵动岛
echo    3. 点击任意应用图标
echo    4. 观察应用是否打开并显示搜索结果
echo.

echo 📊 预期结果：
echo    ✅ 自动粘贴获取剪贴板内容成功
echo    ✅ 构建URL scheme搜索链接
echo    ✅ 跳转到应用并显示搜索结果
echo    ✅ 显示Toast提示搜索内容
echo.

echo 🔄 开始监控关键日志...
echo ----------------------------------------

adb logcat -s DynamicIslandService | findstr /C:"应用图标点击" /C:"自动粘贴" /C:"构建URL Scheme" /C:"跳转到" /C:"最终搜索内容" /C:"粘贴操作"
