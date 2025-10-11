# AI应用图标和跳转功能修复测试指南

## 问题描述

用户反馈：软件tab中的AI app相关包名和对应图标没有加入到AI回复的下方图标中，导致展示的是硬编码图标，也没有跳转到对应的AI app，请重新参考软件tab的ai跳转方法。

## 问题分析

经过检查发现以下问题：

1. **AI应用未正确识别**：
   - PlatformJumpManager中没有处理AI应用的逻辑
   - 只处理预设平台（抖音、小红书等）和通用web搜索
   - AI应用被当作普通动态应用处理，导致跳转失败

2. **图标显示问题**：
   - AI应用图标没有正确映射到包名
   - PlatformIconLoader中缺少AI应用的包名映射
   - 导致显示硬编码的默认图标

3. **跳转方法不一致**：
   - AI回复下方图标使用通用的URL scheme跳转
   - 软件tab使用专门的AI应用跳转方法（Intent发送、剪贴板等）
   - 两种方法没有统一

## 修复内容

### 1. PlatformJumpManager AI应用支持

**新增AI应用识别**：
```kotlin
/**
 * 检查是否是AI应用
 */
private fun isAIApp(appName: String): Boolean {
    val aiAppNames = listOf(
        "DeepSeek", "豆包", "ChatGPT", "Kimi", "腾讯元宝", "讯飞星火", 
        "智谱清言", "通义千问", "文小言", "Grok", "Perplexity", "Manus",
        "秘塔AI搜索", "Poe", "IMA", "纳米AI", "Gemini", "Copilot"
    )
    return aiAppNames.any { aiName -> appName.contains(aiName) }
}
```

**新增AI应用跳转逻辑**：
```kotlin
private fun jumpToDynamicApp(appName: String, query: String) {
    try {
        // 1. 检查是否是AI应用
        if (isAIApp(appName)) {
            jumpToAIApp(appName, query)
            return
        }
        
        // 2. 尝试通过包名跳转到应用
        val packageName = getPackageNameByAppName(appName)
        if (packageName != null && isAppInstalled(packageName)) {
            jumpToDynamicAppByPackage(packageName, query)
            return
        }
        
        // 3. 应用未安装或找不到包名，使用Web搜索
        jumpToWebSearchForDynamicApp(appName, query)
        
    } catch (e: Exception) {
        Log.e(TAG, "动态应用跳转失败: $appName", e)
        jumpToWebSearchForDynamicApp(appName, query)
    }
}
```

### 2. AI应用包名映射

**新增AI应用包名映射**：
```kotlin
/**
 * 获取AI应用的包名列表
 */
private fun getAIPackages(appName: String): List<String> {
    return when {
        appName.contains("DeepSeek") -> listOf("com.deepseek.chat")
        appName.contains("豆包") -> listOf("com.larus.nova")
        appName.contains("ChatGPT") -> listOf("com.openai.chatgpt")
        appName.contains("Kimi") -> listOf("com.moonshot.kimichat")
        appName.contains("腾讯元宝") -> listOf("com.tencent.hunyuan.app.chat")
        appName.contains("讯飞星火") -> listOf("com.iflytek.spark")
        appName.contains("智谱清言") -> listOf("com.zhipuai.qingyan")
        appName.contains("通义千问") -> listOf("com.aliyun.tongyi")
        appName.contains("文小言") -> listOf("com.baidu.newapp")
        appName.contains("Grok") -> listOf("ai.x.grok")
        appName.contains("Perplexity") -> listOf("ai.perplexity.app.android")
        appName.contains("Manus") -> listOf("com.manus.im.app")
        appName.contains("秘塔AI搜索") -> listOf("com.metaso")
        appName.contains("Poe") -> listOf("com.poe.android")
        appName.contains("IMA") -> listOf("com.tencent.ima")
        appName.contains("纳米AI") -> listOf("com.qihoo.namiso")
        appName.contains("Gemini") -> listOf("com.google.android.apps.gemini")
        appName.contains("Copilot") -> listOf("com.microsoft.copilot")
        else -> emptyList()
    }
}
```

### 3. 软件tab AI跳转方法集成

**参考软件tab的AI跳转方法**：
```kotlin
/**
 * 启动AI应用并使用Intent发送文本
 * 参考软件tab的AI跳转方法
 */
private fun launchAIAppWithIntent(packageName: String, query: String, appName: String) {
    try {
        Log.d(TAG, "启动AI应用: $appName, 包名: $packageName")
        
        // 方案1：尝试Intent发送
        if (tryIntentSend(packageName, query, appName)) {
            return
        }
        
        // 方案2：直接启动应用并使用剪贴板
        if (tryDirectLaunchWithClipboard(packageName, query, appName)) {
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

**Intent发送方法**：
```kotlin
/**
 * 尝试通过Intent发送文本
 */
private fun tryIntentSend(packageName: String, query: String, appName: String): Boolean {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, query)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (sendIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(sendIntent)
            Toast.makeText(context, "正在向${appName}发送问题...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Intent发送成功: $appName")
            return true
        }
    } catch (e: Exception) {
        Log.d(TAG, "Intent发送失败: $appName, ${e.message}")
    }
    return false
}
```

**剪贴板方案**：
```kotlin
/**
 * 使用剪贴板发送问题
 */
private fun sendQuestionViaClipboard(packageName: String, query: String, appName: String) {
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
            Toast.makeText(context, "已复制问题到剪贴板，请在${appName}中粘贴", Toast.LENGTH_LONG).show()
            Log.d(TAG, "剪贴板方案成功: $appName")
        } else {
            Toast.makeText(context, "$appName 未安装", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "剪贴板方案失败: $appName", e)
        Toast.makeText(context, "$appName 启动失败", Toast.LENGTH_SHORT).show()
    }
}
```

### 4. PlatformIconLoader AI应用支持

**更新包名映射**：
```kotlin
// AI应用包名映射
val aiAppPackages = mapOf(
    "DeepSeek" to "com.deepseek.chat",
    "豆包" to "com.larus.nova",
    "ChatGPT" to "com.openai.chatgpt",
    "Kimi" to "com.moonshot.kimichat",
    "腾讯元宝" to "com.tencent.hunyuan.app.chat",
    "讯飞星火" to "com.iflytek.spark",
    "智谱清言" to "com.zhipuai.qingyan",
    "通义千问" to "com.aliyun.tongyi",
    "文小言" to "com.baidu.newapp",
    "Grok" to "ai.x.grok",
    "Perplexity" to "ai.perplexity.app.android",
    "Manus" to "com.manus.im.app",
    "秘塔AI搜索" to "com.metaso",
    "Poe" to "com.poe.android",
    "IMA" to "com.tencent.ima",
    "纳米AI" to "com.qihoo.namiso",
    "Gemini" to "com.google.android.apps.gemini",
    "Copilot" to "com.microsoft.copilot"
)
```

**更新网站URL映射**：
```kotlin
// AI应用网站URL
appName.contains("DeepSeek") -> "https://chat.deepseek.com"
appName.contains("豆包") -> "https://www.doubao.com"
appName.contains("ChatGPT") -> "https://chat.openai.com"
appName.contains("Kimi") -> "https://kimi.moonshot.cn"
appName.contains("腾讯元宝") -> "https://hunyuan.tencent.com"
appName.contains("讯飞星火") -> "https://xinghuo.xfyun.cn"
appName.contains("智谱清言") -> "https://chatglm.cn"
appName.contains("通义千问") -> "https://tongyi.aliyun.com"
appName.contains("文小言") -> "https://xiaoyi.baidu.com"
appName.contains("Grok") -> "https://grok.x.ai"
appName.contains("Perplexity") -> "https://www.perplexity.ai"
appName.contains("Manus") -> "https://manus.ai"
appName.contains("秘塔AI搜索") -> "https://metaso.cn"
appName.contains("Poe") -> "https://poe.com"
appName.contains("IMA") -> "https://ima.ai"
appName.contains("纳米AI") -> "https://nano.ai"
appName.contains("Gemini") -> "https://gemini.google.com"
appName.contains("Copilot") -> "https://copilot.microsoft.com"
```

## 技术实现

### 1. AI应用识别机制

**智能识别**：
- 通过应用名称包含关键词识别AI应用
- 支持中英文AI应用名称
- 覆盖主流AI应用：DeepSeek、豆包、ChatGPT、Kimi、腾讯元宝、讯飞星火、智谱清言、通义千问、文小言、Grok、Perplexity、Manus、秘塔AI搜索、Poe、IMA、纳米AI、Gemini、Copilot

**包名映射**：
- 每个AI应用对应准确的包名
- 支持多个可能的包名（如果有的话）
- 与软件tab的包名配置保持一致

### 2. 跳转方法统一

**多方案跳转**：
1. **Intent发送**：使用`Intent.ACTION_SEND`直接发送文本到AI应用
2. **直接启动+剪贴板**：启动AI应用并将问题复制到剪贴板
3. **剪贴板备用方案**：如果前两种方法失败，使用剪贴板方案

**错误处理**：
- 完善的异常捕获和用户提示
- 自动降级到剪贴板方案
- 详细的日志记录

### 3. 图标显示优化

**图标获取优先级**：
1. **真实应用图标**：从已安装应用的PackageManager获取
2. **App Store图标**：使用AppStoreIconManager获取高质量图标
3. **预设图标**：使用本地drawable资源
4. **Favicon候补**：使用FaviconLoader获取网站图标

**图标处理**：
- 使用IconProcessor统一处理图标样式
- 应用ROUNDED_SQUARE样式
- 确保图标大小和比例正确

### 4. 网站URL映射

**AI应用网站**：
- 每个AI应用对应其官方网站URL
- 用于FaviconLoader获取网站图标
- 提供Web搜索候补方案

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
   - ✅ 所有AI应用方法正确实现
   - ✅ 包名映射完整

### 2. AI应用图标测试

#### 2.1 图标显示测试
1. **进入简易模式**
   - 打开应用
   - 进入简易模式界面

2. **发送AI问题**
   - 在输入框中输入问题："你好，请介绍一下自己"
   - 发送给AI引擎

3. **检查AI回复下方图标**
   - 查看是否显示AI应用图标
   - 验证图标是否正确（不是硬编码图标）
   - 检查图标大小和样式

#### 2.2 不同AI应用测试
测试以下AI应用的图标显示：
- DeepSeek
- 豆包
- ChatGPT
- Kimi
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

### 3. AI应用跳转测试

#### 3.1 已安装AI应用测试
1. **准备测试环境**
   - 确保设备上安装了部分AI应用
   - 记录已安装的AI应用列表

2. **测试跳转功能**
   - 点击AI回复下方的AI应用图标
   - 验证是否成功跳转到对应AI应用
   - 检查问题是否正确传递

3. **测试不同跳转方案**
   - **Intent发送**：检查是否直接发送问题到AI应用
   - **剪贴板方案**：检查是否复制问题到剪贴板并启动应用
   - **错误处理**：测试跳转失败时的处理

#### 3.2 未安装AI应用测试
1. **测试Web搜索候补**
   - 点击未安装的AI应用图标
   - 验证是否跳转到Web搜索
   - 检查搜索关键词是否正确

2. **测试错误提示**
   - 验证是否显示"未安装"提示
   - 检查错误处理是否完善

### 4. 功能集成测试

#### 4.1 软件tab对比测试
1. **对比跳转方法**
   - 在软件tab中测试AI应用跳转
   - 在AI回复下方测试AI应用跳转
   - 验证两种方法是否一致

2. **对比图标显示**
   - 对比软件tab和AI回复下方的图标
   - 验证图标是否一致
   - 检查图标质量

#### 4.2 用户体验测试
1. **响应速度测试**
   - 测量图标加载时间
   - 测量跳转响应时间
   - 验证性能是否满足要求

2. **错误处理测试**
   - 测试网络异常情况
   - 测试应用未安装情况
   - 测试跳转失败情况

### 5. 兼容性测试

#### 5.1 不同设备测试
1. **不同Android版本测试**
   - 测试Android 7.0+
   - 验证API兼容性
   - 检查功能稳定性

2. **不同屏幕密度测试**
   - 测试不同DPI设备
   - 验证图标缩放正确性
   - 检查显示效果

#### 5.2 不同AI应用版本测试
1. **不同版本AI应用测试**
   - 测试不同版本的AI应用
   - 验证包名兼容性
   - 检查跳转成功率

## 预期结果

### 1. 图标显示结果
- ✅ AI应用图标正确显示（不是硬编码图标）
- ✅ 图标使用真实应用图标或高质量网络图标
- ✅ 图标样式统一，大小合适
- ✅ 图标加载速度快

### 2. 跳转功能结果
- ✅ AI应用跳转成功
- ✅ 问题正确传递到AI应用
- ✅ 支持多种跳转方案（Intent、剪贴板）
- ✅ 错误处理完善

### 3. 用户体验结果
- ✅ 跳转响应速度快
- ✅ 用户提示清晰
- ✅ 错误处理友好
- ✅ 功能稳定可靠

### 4. 兼容性结果
- ✅ 不同设备正常工作
- ✅ 不同Android版本兼容
- ✅ 不同AI应用版本支持

## 技术特点

### 1. 智能识别
- **AI应用识别**：通过名称关键词智能识别AI应用
- **包名映射**：准确的包名映射，支持多种AI应用
- **网站URL映射**：完整的网站URL映射，支持Favicon获取

### 2. 多方案跳转
- **Intent发送**：优先使用Intent直接发送文本
- **剪贴板方案**：备用的剪贴板传递方案
- **Web搜索候补**：未安装应用时的Web搜索方案

### 3. 图标优化
- **真实图标**：优先使用真实应用图标
- **高质量图标**：使用App Store高质量图标
- **统一样式**：使用IconProcessor统一处理样式

### 4. 错误处理
- **完善异常处理**：全面的异常捕获和处理
- **用户友好提示**：清晰的用户提示信息
- **自动降级**：自动降级到备用方案

## 注意事项
- 确保编译无错误
- 验证所有AI应用包名正确
- 测试不同跳转方案
- 检查图标显示质量
- 验证错误处理机制
- 测试不同设备兼容性
