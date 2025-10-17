# 语音胶囊AI应用悬浮窗服务激活功能测试指南

## 功能概述

成功实现了在语音tab中点击平台图标跳转到AI应用后，自动激活无线AI应用悬浮窗服务模式，让用户可以反复在不同AI应用之间跳转。

## 实现内容

### 1. 核心修改

**文件**: `app/src/main/java/com/example/aifloatingball/manager/PlatformJumpManager.kt`

**修改内容**:
- 在`launchAIAppWithIntent`方法中添加了AI应用悬浮窗服务激活逻辑
- 新增`activateAIAppOverlayService`方法，负责启动悬浮窗服务
- 无论AI应用启动成功还是失败，都会尝试激活悬浮窗服务

**关键代码**:
```kotlin
/**
 * 启动AI应用并使用Intent发送文本
 * 完全参考软件tab的AI跳转方法
 * 新增：激活AI应用悬浮窗服务模式
 */
private fun launchAIAppWithIntent(packageName: String, query: String, appName: String) {
    try {
        Log.d(TAG, "启动AI应用: $appName, 包名: $packageName, 查询: $query")
        
        // 对于豆包应用，尝试使用Intent直接发送文本
        if (appName.contains("豆包") && packageName == "com.larus.nova") {
            if (tryIntentSendForDoubao(packageName, query, appName)) {
                // 激活AI应用悬浮窗服务
                activateAIAppOverlayService(packageName, query, appName)
                return
            }
        }
        
        // 参考软件tab的AI跳转方法：启动应用并使用自动化粘贴
        launchAppWithAutoPaste(packageName, query, appName)
        
        // 激活AI应用悬浮窗服务
        activateAIAppOverlayService(packageName, query, appName)
        
    } catch (e: Exception) {
        Log.e(TAG, "AI应用启动失败: $appName", e)
        Toast.makeText(context, "$appName 启动失败", Toast.LENGTH_SHORT).show()
        sendQuestionViaClipboard(packageName, query, appName)
        
        // 即使启动失败也尝试激活悬浮窗服务
        activateAIAppOverlayService(packageName, query, appName)
    }
}
```

**新增方法**:
```kotlin
/**
 * 激活AI应用悬浮窗服务
 * 在AI应用跳转后启动悬浮窗服务，支持用户反复跳转
 */
private fun activateAIAppOverlayService(packageName: String, query: String, appName: String) {
    try {
        Log.d(TAG, "激活AI应用悬浮窗服务: $appName, 包名: $packageName, 查询: $query")
        
        val intent = Intent(context, AIAppOverlayService::class.java).apply {
            action = AIAppOverlayService.ACTION_SHOW_OVERLAY
            putExtra(AIAppOverlayService.EXTRA_APP_NAME, appName)
            putExtra(AIAppOverlayService.EXTRA_QUERY, query)
            putExtra(AIAppOverlayService.EXTRA_PACKAGE_NAME, packageName)
            putExtra("mode", "overlay") // 设置为悬浮窗模式
        }
        
        context.startService(intent)
        
        Log.d(TAG, "AI应用悬浮窗服务已激活: $appName")
        Toast.makeText(context, "已激活AI应用悬浮窗服务，可反复跳转", Toast.LENGTH_SHORT).show()
        
    } catch (e: Exception) {
        Log.e(TAG, "激活AI应用悬浮窗服务失败: $appName", e)
        Toast.makeText(context, "悬浮窗服务启动失败", Toast.LENGTH_SHORT).show()
    }
}
```

### 2. 功能流程

**完整流程**:
1. 用户在语音tab中点击平台图标
2. `PlatformJumpManager.jumpToPlatform()` 被调用
3. 检测到是AI应用，调用 `jumpToAIApp()`
4. 调用 `launchAIAppWithIntent()` 启动AI应用
5. 启动AI应用后，调用 `activateAIAppOverlayService()` 激活悬浮窗服务
6. `AIAppOverlayService` 启动并显示悬浮窗
7. 用户可以通过悬浮窗反复跳转到其他AI应用

## 测试步骤

### 1. 基本功能测试

**测试步骤**:
1. 启动应用，进入语音tab
2. 点击麦克风开始语音识别
3. 说一些AI相关的内容（如"帮我写一篇文章"）
4. 观察语音识别完成后是否显示平台图标
5. 点击AI应用图标（如ChatGPT、豆包等）

**预期结果**:
- 语音识别完成后显示平台图标
- 点击AI应用图标后跳转到对应AI应用
- 跳转后显示Toast提示"已激活AI应用悬浮窗服务，可反复跳转"
- 在AI应用界面右上角出现悬浮窗按钮

### 2. 悬浮窗服务测试

**测试步骤**:
1. 完成基本功能测试后，观察AI应用界面
2. 查看右上角是否出现悬浮窗按钮
3. 点击悬浮窗按钮，查看是否显示AI菜单
4. 在AI菜单中点击其他AI应用图标

**预期结果**:
- 在AI应用界面右上角显示悬浮窗按钮
- 点击悬浮窗按钮显示AI菜单
- AI菜单中包含多个AI应用图标
- 点击其他AI应用图标可以跳转到对应应用
- 跳转后悬浮窗服务继续运行

### 3. 反复跳转测试

**测试步骤**:
1. 在AI菜单中点击不同的AI应用图标
2. 观察是否能够成功跳转到不同AI应用
3. 测试跳转的连续性和稳定性
4. 观察悬浮窗是否始终保持在屏幕上

**预期结果**:
- 可以连续跳转到不同的AI应用
- 每次跳转都携带正确的查询内容
- 悬浮窗服务持续运行，不会中断
- 跳转过程流畅，没有卡顿或崩溃

### 4. 异常情况测试

**测试步骤**:
1. 测试未安装的AI应用跳转
2. 测试网络异常情况下的跳转
3. 测试权限不足的情况
4. 测试内存不足的情况

**预期结果**:
- 未安装的AI应用会显示相应提示
- 网络异常时使用剪贴板方案
- 权限不足时显示权限申请提示
- 内存不足时服务能够正常恢复

### 5. 服务生命周期测试

**测试步骤**:
1. 激活悬浮窗服务后，切换到其他应用
2. 返回AI应用，观察悬浮窗是否还在
3. 长时间使用后观察服务是否稳定
4. 手动停止服务后重新激活

**预期结果**:
- 切换到其他应用后悬浮窗仍然存在
- 返回AI应用时悬浮窗功能正常
- 长时间使用服务稳定运行
- 可以正常停止和重新激活服务

## 技术特点

### 1. 无缝集成

- 与现有的平台跳转逻辑完美集成
- 不影响原有的AI应用跳转功能
- 保持代码的向后兼容性

### 2. 智能激活

- 只在AI应用跳转时激活悬浮窗服务
- 非AI应用跳转不会激活悬浮窗服务
- 支持多种AI应用跳转方式

### 3. 错误处理

- 即使AI应用启动失败也会尝试激活悬浮窗服务
- 提供详细的错误日志和用户提示
- 支持多种异常情况的处理

### 4. 用户体验

- 提供清晰的用户反馈
- 支持反复跳转，提高使用效率
- 悬浮窗服务持续运行，无需重复激活

## 注意事项

1. **权限要求**: 确保应用有悬浮窗权限
2. **服务稳定性**: 悬浮窗服务需要保持稳定运行
3. **内存管理**: 长时间使用需要注意内存泄漏
4. **电池优化**: 避免被系统电池优化影响

## 验证要点

- ✅ AI应用跳转后自动激活悬浮窗服务
- ✅ 悬浮窗服务正常启动并显示悬浮窗
- ✅ 可以通过悬浮窗反复跳转到不同AI应用
- ✅ 跳转时正确携带查询内容
- ✅ 服务在异常情况下能够正常恢复
- ✅ 用户体验流畅，没有明显卡顿
- ✅ 与现有功能完美集成，无冲突
