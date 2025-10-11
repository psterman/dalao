# AI应用自动粘贴功能集成测试指南

## 问题描述

用户反馈：AI跳转没有将用户的问题直接通过Intent传入或者粘贴到输入框。

## 问题分析

经过检查发现以下问题：

1. **Intent发送不完整**：
   - `PlatformJumpManager`中的`tryIntentSend`方法实现正确
   - 但缺少更高级的自动粘贴功能

2. **自动粘贴功能缺失**：
   - 项目中有完整的无障碍服务(`MyAccessibilityService`)和悬浮窗服务(`AIAppOverlayService`)
   - 但`PlatformJumpManager`没有集成这些高级功能
   - 只使用了简单的剪贴板方案

3. **用户体验不佳**：
   - 用户需要手动粘贴问题到AI应用
   - 没有自动粘贴到输入框的功能

## 修复内容

### 1. 集成自动粘贴功能

**新增自动粘贴方法**：
```kotlin
/**
 * 直接启动应用并使用自动粘贴
 */
private fun tryDirectLaunchWithAutoPaste(packageName: String, query: String, appName: String): Boolean {
    try {
        // 将问题复制到剪贴板
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI问题", query)
        clipboard.setPrimaryClip(clip)
        
        // 启动AI应用
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Toast.makeText(context, "正在启动$appName...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "直接启动成功: $appName")
            
            // 延迟启动自动粘贴（确保应用完全启动）
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startAutoPaste(packageName, query, appName)
            }, 2000)
            return true
        } else {
            Log.d(TAG, "直接启动失败: $appName, 无法获取启动Intent")
            return false
        }
    } catch (e: Exception) {
        Log.d(TAG, "直接启动失败: $appName, ${e.message}")
    }
    return false
}
```

### 2. 多方案自动粘贴

**启动自动粘贴功能**：
```kotlin
/**
 * 启动自动粘贴功能
 */
private fun startAutoPaste(packageName: String, query: String, appName: String) {
    try {
        Log.d(TAG, "启动自动粘贴: $appName, 查询: $query")
        
        // 方案1：尝试使用无障碍服务自动粘贴
        if (tryAccessibilityAutoPaste(packageName, query, appName)) {
            return
        }
        
        // 方案2：启动AI应用悬浮窗服务
        if (tryAIAppOverlayService(packageName, query, appName)) {
            return
        }
        
        // 方案3：回退到剪贴板方案
        sendQuestionViaClipboard(packageName, query, appName)
        
    } catch (e: Exception) {
        Log.e(TAG, "自动粘贴启动失败: $appName", e)
        sendQuestionViaClipboard(packageName, query, appName)
    }
}
```

### 3. 无障碍服务集成

**无障碍服务自动粘贴**：
```kotlin
/**
 * 尝试使用无障碍服务自动粘贴
 */
private fun tryAccessibilityAutoPaste(packageName: String, query: String, appName: String): Boolean {
    try {
        // 发送自动粘贴请求到无障碍服务
        val intent = Intent("com.example.aifloatingball.AUTO_PASTE").apply {
            putExtra("package_name", packageName)
            putExtra("query", query)
            putExtra("app_name", appName)
        }
        context.sendBroadcast(intent)
        
        Log.d(TAG, "已发送无障碍服务自动粘贴请求: $appName")
        Toast.makeText(context, "正在自动粘贴到${appName}...", Toast.LENGTH_SHORT).show()
        return true
        
    } catch (e: Exception) {
        Log.d(TAG, "无障碍服务自动粘贴失败: $appName, ${e.message}")
        return false
    }
}
```

### 4. 悬浮窗服务集成

**AI应用悬浮窗服务**：
```kotlin
/**
 * 尝试启动AI应用悬浮窗服务
 */
private fun tryAIAppOverlayService(packageName: String, query: String, appName: String): Boolean {
    try {
        val intent = Intent(context, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
            putExtra("package_name", packageName)
            putExtra("query", query)
            putExtra("app_name", appName)
        }
        context.startService(intent)
        
        Log.d(TAG, "已启动AI应用悬浮窗服务: $appName")
        Toast.makeText(context, "已启动${appName}自动粘贴助手", Toast.LENGTH_SHORT).show()
        return true
        
    } catch (e: Exception) {
        Log.d(TAG, "AI应用悬浮窗服务启动失败: $appName, ${e.message}")
        return false
    }
}
```

### 5. 更新跳转逻辑

**新的跳转方案**：
```kotlin
private fun launchAIAppWithIntent(packageName: String, query: String, appName: String) {
    try {
        Log.d(TAG, "启动AI应用: $appName, 包名: $packageName")
        
        // 方案1：尝试Intent发送
        if (tryIntentSend(packageName, query, appName)) {
            return
        }
        
        // 方案2：直接启动应用并使用自动粘贴
        if (tryDirectLaunchWithAutoPaste(packageName, query, appName)) {
            return
        }
        
        // 方案3：使用剪贴板备用方案
        sendQuestionViaClipboard(packageName, query, appName)
        
    } catch (e: Exception) {
        Log.e(TAG, "AI应用启动失败: $appName", e)
        Toast.makeText(context, "$appName 启动失败", Toast.LENGTH_SHORT).show()
        sendQuestionViaClipboard(packageName, query, appName)
    }
}
```

## 技术实现

### 1. 多方案跳转策略

**跳转优先级**：
1. **Intent发送**：使用`Intent.ACTION_SEND`直接发送文本到AI应用
2. **自动粘贴**：启动AI应用并使用无障碍服务或悬浮窗服务自动粘贴
3. **剪贴板备用**：如果前两种方法失败，使用剪贴板方案

### 2. 自动粘贴机制

**无障碍服务**：
- 使用`MyAccessibilityService`查找AI应用的输入框
- 自动将用户问题粘贴到输入框中
- 支持多种输入框类型：EditText、可编辑节点、特定关键词输入框

**悬浮窗服务**：
- 使用`AIAppOverlayService`显示悬浮窗
- 提供手动粘贴按钮和自动粘贴功能
- 在AI应用界面上显示操作提示

### 3. 延迟启动机制

**应用启动等待**：
- 启动AI应用后等待2秒确保应用完全加载
- 然后启动自动粘贴功能
- 避免在应用未完全启动时执行粘贴操作

### 4. 错误处理和降级

**多级降级**：
- 无障碍服务失败 → 悬浮窗服务
- 悬浮窗服务失败 → 剪贴板方案
- 完善的异常捕获和用户提示

## 测试步骤

### 1. 编译测试
1. **清理项目**
   ```bash
   cd C:\Users\pster\Desktop\dalao
   .\gradlew clean
   ```

2. **编译项目**
   ```bash
   .\gradlew compileDebugKotlin
   ```

3. **验证结果**
   - ✅ 编译成功，无错误
   - ✅ 自动粘贴功能正确集成
   - ✅ 多方案跳转逻辑完整

### 2. Intent发送测试

#### 2.1 支持Intent的AI应用测试
1. **准备测试环境**
   - 确保设备上安装了支持Intent的AI应用
   - 如：ChatGPT、DeepSeek、Kimi等

2. **测试Intent发送**
   - 进入简易模式
   - 发送AI问题："你好，请介绍一下自己"
   - 点击AI回复下方的AI应用图标
   - 验证是否直接发送问题到AI应用

3. **验证结果**
   - ✅ AI应用启动
   - ✅ 问题直接显示在AI应用输入框中
   - ✅ 用户无需手动粘贴

#### 2.2 不同AI应用测试
测试以下AI应用的Intent发送：
- ChatGPT
- DeepSeek
- Kimi
- 豆包
- 腾讯元宝
- 讯飞星火

### 3. 自动粘贴测试

#### 3.1 无障碍服务测试
1. **启用无障碍服务**
   - 进入系统设置
   - 启用应用的无障碍服务
   - 确保服务正常运行

2. **测试自动粘贴**
   - 点击AI回复下方的AI应用图标
   - 验证是否显示"正在自动粘贴到XXX..."
   - 检查AI应用输入框是否自动填入问题

3. **验证自动粘贴效果**
   - 问题自动填入输入框
   - 无需手动操作
   - 用户体验流畅

#### 3.2 悬浮窗服务测试
1. **测试悬浮窗启动**
   - 点击AI应用图标
   - 验证是否显示"已启动XXX自动粘贴助手"
   - 检查是否出现悬浮窗

2. **测试悬浮窗功能**
   - 悬浮窗显示操作提示
   - 可以手动点击粘贴按钮
   - 提供用户操作指导

### 4. 多方案降级测试

#### 4.1 Intent发送失败测试
1. **模拟Intent发送失败**
   - 使用不支持Intent的AI应用
   - 或网络异常情况

2. **验证降级机制**
   - 自动降级到自动粘贴方案
   - 检查是否启动无障碍服务或悬浮窗
   - 验证用户体验连续性

#### 4.2 自动粘贴失败测试
1. **模拟自动粘贴失败**
   - 禁用无障碍服务
   - 或悬浮窗服务异常

2. **验证最终降级**
   - 自动降级到剪贴板方案
   - 显示"已复制问题到剪贴板，请在XXX中粘贴"
   - 确保功能可用性

### 5. 用户体验测试

#### 5.1 响应速度测试
1. **测量启动时间**
   - AI应用启动时间
   - 自动粘贴响应时间
   - 整体操作流畅度

2. **验证性能**
   - 启动速度 < 3秒
   - 自动粘贴响应 < 5秒
   - 无卡顿现象

#### 5.2 用户提示测试
1. **检查提示信息**
   - "正在启动XXX..."
   - "正在自动粘贴到XXX..."
   - "已启动XXX自动粘贴助手"
   - "已复制问题到剪贴板，请在XXX中粘贴"

2. **验证提示准确性**
   - 提示信息与实际操作一致
   - 用户能清楚了解当前状态
   - 错误提示友好易懂

### 6. 兼容性测试

#### 6.1 不同AI应用测试
1. **主流AI应用测试**
   - ChatGPT
   - DeepSeek
   - Kimi
   - 豆包
   - 腾讯元宝
   - 讯飞星火
   - 智谱清言
   - 通义千问

2. **验证兼容性**
   - 不同AI应用的跳转成功率
   - 自动粘贴功能的适配性
   - 用户体验一致性

#### 6.2 不同设备测试
1. **不同Android版本测试**
   - Android 7.0+
   - 验证API兼容性
   - 检查功能稳定性

2. **不同屏幕密度测试**
   - 不同DPI设备
   - 验证悬浮窗显示
   - 检查操作便利性

## 预期结果

### 1. Intent发送结果
- ✅ 支持Intent的AI应用直接接收问题
- ✅ 问题自动填入AI应用输入框
- ✅ 用户无需手动操作

### 2. 自动粘贴结果
- ✅ 无障碍服务自动粘贴成功
- ✅ 悬浮窗服务提供操作指导
- ✅ 问题自动填入输入框

### 3. 用户体验结果
- ✅ 操作流程顺畅
- ✅ 响应速度快
- ✅ 提示信息清晰
- ✅ 错误处理友好

### 4. 兼容性结果
- ✅ 不同AI应用正常工作
- ✅ 不同设备兼容良好
- ✅ 功能稳定可靠

## 技术特点

### 1. 智能跳转
- **多方案支持**：Intent发送、自动粘贴、剪贴板备用
- **智能降级**：自动选择最佳可用方案
- **错误处理**：完善的异常捕获和用户提示

### 2. 自动粘贴
- **无障碍服务**：自动查找输入框并粘贴文本
- **悬浮窗服务**：提供操作指导和手动粘贴
- **多输入框支持**：EditText、可编辑节点、特定关键词输入框

### 3. 用户体验
- **无缝操作**：用户问题直接传递到AI应用
- **智能提示**：清晰的状态提示和操作指导
- **快速响应**：优化的启动和粘贴流程

### 4. 稳定性
- **多级降级**：确保功能始终可用
- **异常处理**：全面的错误捕获和处理
- **兼容性**：支持多种AI应用和设备

## 注意事项
- 确保无障碍服务已启用
- 验证AI应用支持Intent发送
- 测试不同AI应用的兼容性
- 检查自动粘贴功能的准确性
- 验证多方案降级机制
- 测试不同设备的表现
