# AI应用跳转和问题传递功能测试指南

## 功能描述

当用户在对话tab的AI回复下方点击AI图标时，能够将用户的原始提问再次发送给指定的AI应用，确保AI应用能够通过Intent进入提问状态，或者剪贴板已经粘贴了文本到AI应用的输入框中。

## 技术实现流程

### 1. 数据流传递

**用户提问 → AI回复 → 平台图标显示 → AI应用跳转**

1. **用户发送问题**：
   ```kotlin
   // ChatActivity.sendMessage()
   val userMessage = ChatMessage(messageText, true, System.currentTimeMillis())
   val aiMessage = ChatMessage("正在思考中...", false, System.currentTimeMillis(), messageText)
   ```

2. **AI回复处理**：
   ```kotlin
   // ChatActivity.sendMessageToAI()
   aiMessage.content = cleanAndFormatAIResponse(response) + "[PLATFORM_ICONS]"
   ```

3. **平台图标显示**：
   ```kotlin
   // ChatMessageAdapter.showPlatformIcons()
   showPlatformIcons(message.userQuery ?: "")
   ```

4. **AI应用跳转**：
   ```kotlin
   // PlatformIconsView.createPlatformIcon()
   platformJumpManager.jumpToPlatform(platform.name, query)
   ```

### 2. AI应用跳转策略

**多方案跳转机制**：

1. **方案1：Intent发送**
   ```kotlin
   private fun tryIntentSend(packageName: String, query: String, appName: String): Boolean {
       val sendIntent = Intent().apply {
           action = Intent.ACTION_SEND
           type = "text/plain"
           putExtra(Intent.EXTRA_TEXT, query)  // 用户原始问题
           setPackage(packageName)
           addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
       }
       // 直接发送问题到AI应用
   }
   ```

2. **方案2：自动粘贴**
   ```kotlin
   private fun tryDirectLaunchWithAutoPaste(packageName: String, query: String, appName: String): Boolean {
       // 将问题复制到剪贴板
       val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
       val clip = ClipData.newPlainText("AI问题", query)  // 用户原始问题
       clipboard.setPrimaryClip(clip)
       
       // 启动AI应用
       val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
       context.startActivity(launchIntent)
       
       // 延迟启动自动粘贴
       Handler(Looper.getMainLooper()).postDelayed({
           startAutoPaste(packageName, query, appName)
       }, 2000)
   }
   ```

3. **方案3：剪贴板备用**
   ```kotlin
   private fun sendQuestionViaClipboard(packageName: String, query: String, appName: String) {
       val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
       val clip = ClipData.newPlainText("AI问题", query)  // 用户原始问题
       clipboard.setPrimaryClip(clip)
       
       val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
       context.startActivity(launchIntent)
       Toast.makeText(context, "已复制问题到剪贴板，请在${appName}中粘贴", Toast.LENGTH_LONG).show()
   }
   ```

### 3. 自动粘贴功能

**无障碍服务自动粘贴**：
```kotlin
private fun tryAccessibilityAutoPaste(packageName: String, query: String, appName: String): Boolean {
    val intent = Intent("com.example.aifloatingball.AUTO_PASTE").apply {
        putExtra("package_name", packageName)
        putExtra("query", query)  // 用户原始问题
        putExtra("app_name", appName)
    }
    context.sendBroadcast(intent)
    // 发送自动粘贴请求到无障碍服务
}
```

**悬浮窗服务**：
```kotlin
private fun tryAIAppOverlayService(packageName: String, query: String, appName: String): Boolean {
    val intent = Intent(context, AIAppOverlayService::class.java).apply {
        putExtra("package_name", packageName)
        putExtra("query", query)  // 用户原始问题
        putExtra("app_name", appName)
    }
    context.startService(intent)
    // 启动AI应用悬浮窗服务
}
```

## 测试步骤

### 1. 基础功能测试

#### 1.1 进入对话tab
1. **打开应用**
   - 启动应用
   - 进入对话tab
   - 选择任意AI助手（如DeepSeek、ChatGPT等）

2. **发送测试问题**
   - 在输入框中输入："请推荐几部好看的科幻电影"
   - 点击发送按钮
   - 等待AI回复完成

3. **验证AI回复**
   - 确认AI回复下方显示了平台图标
   - 检查是否包含AI应用图标（如DeepSeek、ChatGPT等）

#### 1.2 点击AI图标测试
1. **点击AI应用图标**
   - 点击AI回复下方的AI应用图标（如DeepSeek图标）
   - 观察跳转行为

2. **验证Intent发送**
   - 如果AI应用支持Intent发送，应该直接打开AI应用
   - 用户问题应该自动填入AI应用的输入框
   - 无需用户手动操作

3. **验证自动粘贴**
   - 如果Intent发送失败，应该启动自动粘贴功能
   - 显示"正在自动粘贴到XXX..."提示
   - 问题应该自动粘贴到AI应用输入框

4. **验证剪贴板备用**
   - 如果自动粘贴失败，应该使用剪贴板方案
   - 显示"已复制问题到剪贴板，请在XXX中粘贴"提示
   - 用户需要手动粘贴问题

### 2. Intent发送测试

#### 2.1 支持Intent的AI应用测试
测试以下AI应用的Intent发送功能：

**DeepSeek**：
- 包名：`com.deepseek.chat`
- 测试问题："请介绍一下人工智能的发展历史"
- 预期结果：直接打开DeepSeek应用，问题自动填入输入框

**ChatGPT**：
- 包名：`com.openai.chatgpt`
- 测试问题："如何学习编程？"
- 预期结果：直接打开ChatGPT应用，问题自动填入输入框

**Kimi**：
- 包名：`com.moonshot.kimi`
- 测试问题："推荐一些好书"
- 预期结果：直接打开Kimi应用，问题自动填入输入框

#### 2.2 Intent发送失败测试
1. **模拟Intent发送失败**
   - 使用不支持Intent的AI应用
   - 或网络异常情况

2. **验证降级机制**
   - 自动降级到自动粘贴方案
   - 检查是否启动无障碍服务或悬浮窗
   - 验证用户体验连续性

### 3. 自动粘贴测试

#### 3.1 无障碍服务测试
1. **启用无障碍服务**
   - 进入系统设置 → 无障碍
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

#### 4.1 完整降级流程测试
1. **Intent发送失败**
   - 模拟Intent发送失败
   - 验证自动降级到自动粘贴

2. **自动粘贴失败**
   - 禁用无障碍服务
   - 或悬浮窗服务异常
   - 验证降级到剪贴板方案

3. **最终备用方案**
   - 验证剪贴板方案正常工作
   - 确保功能始终可用

#### 4.2 错误处理测试
1. **网络异常测试**
   - 断网情况下测试跳转
   - 验证错误提示友好

2. **应用未安装测试**
   - 卸载测试AI应用
   - 验证降级到Web搜索

3. **权限不足测试**
   - 禁用相关权限
   - 验证功能降级

### 5. 用户体验测试

#### 5.1 响应速度测试
1. **测量启动时间**
   - AI应用启动时间 < 3秒
   - 自动粘贴响应时间 < 5秒
   - 整体操作流畅度

2. **验证性能**
   - 无卡顿现象
   - 内存使用合理
   - 电池消耗正常

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
测试以下AI应用的兼容性：

**主流AI应用**：
- DeepSeek
- ChatGPT
- Kimi
- 豆包
- 腾讯元宝
- 讯飞星火
- 智谱清言
- 通义千问
- 文小言
- Grok
- Perplexity
- Manus
- 秘塔AI搜索
- Poe
- IMA
- 纳米AI
- Gemini
- Copilot

#### 6.2 不同设备测试
1. **不同Android版本测试**
   - Android 7.0+
   - 验证API兼容性
   - 检查功能稳定性

2. **不同屏幕密度测试**
   - 不同DPI设备
   - 验证悬浮窗显示
   - 检查操作便利性

### 7. 数据传递测试

#### 7.1 userQuery传递测试
1. **验证数据传递**
   - 用户问题正确传递到AI应用
   - 无数据丢失或截断
   - 特殊字符正确处理

2. **测试不同问题类型**
   - 短问题："你好"
   - 长问题："请详细解释量子计算的工作原理和应用前景"
   - 包含特殊字符的问题："@#$%^&*()"
   - 包含emoji的问题："😊请推荐一些好电影"

#### 7.2 问题内容完整性测试
1. **原始问题保持**
   - 用户原始问题完整传递
   - 无额外修改或截断
   - 保持原始格式

2. **多轮对话测试**
   - 连续发送多个问题
   - 验证每个问题都能正确传递
   - 确保问题不混淆

## 预期结果

### 1. Intent发送结果
- ✅ 支持Intent的AI应用直接接收问题
- ✅ 问题自动填入AI应用输入框
- ✅ 用户无需手动操作
- ✅ 跳转速度快（< 3秒）

### 2. 自动粘贴结果
- ✅ 无障碍服务自动粘贴成功
- ✅ 悬浮窗服务提供操作指导
- ✅ 问题自动填入输入框
- ✅ 用户体验流畅

### 3. 剪贴板备用结果
- ✅ 剪贴板方案正常工作
- ✅ 问题正确复制到剪贴板
- ✅ 用户提示清晰
- ✅ 功能始终可用

### 4. 用户体验结果
- ✅ 操作流程顺畅
- ✅ 响应速度快
- ✅ 提示信息清晰
- ✅ 错误处理友好
- ✅ 多方案无缝切换

### 5. 兼容性结果
- ✅ 不同AI应用正常工作
- ✅ 不同设备兼容良好
- ✅ 功能稳定可靠
- ✅ 数据传递完整

## 技术特点

### 1. 智能跳转
- **多方案支持**：Intent发送、自动粘贴、剪贴板备用
- **智能降级**：自动选择最佳可用方案
- **错误处理**：完善的异常捕获和用户提示

### 2. 自动粘贴
- **无障碍服务**：自动查找输入框并粘贴文本
- **悬浮窗服务**：提供操作指导和手动粘贴
- **多输入框支持**：EditText、可编辑节点、特定关键词输入框

### 3. 数据传递
- **完整传递**：用户原始问题完整传递
- **格式保持**：保持原始问题格式
- **特殊字符**：正确处理特殊字符和emoji

### 4. 用户体验
- **无缝操作**：用户问题直接传递到AI应用
- **智能提示**：清晰的状态提示和操作指导
- **快速响应**：优化的启动和粘贴流程

### 5. 稳定性
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
- 确保用户问题完整传递
- 验证特殊字符处理
- 测试多轮对话场景
