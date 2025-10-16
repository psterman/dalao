# 语音胶囊三段式布局实现指南

## 🎯 功能概述

成功实现了参考锤子胶囊激活的搜索模式，为简易模式的语音tab添加了三段式布局功能：

### 三段式布局结构

1. **第一段 - 语音输入文本编辑区域**
   - 显示用户语音识别后的文本
   - 支持用户编辑文本内容
   - 提供丰富的操作按钮（文档、星标、附件等）
   - 紫色渐变背景，符合锤子胶囊设计风格

2. **第二段 - 网络搜索结果区域**
   - 自动加载FloatingWebViewService进行网页搜索
   - 集成百度搜索界面
   - 支持多种搜索标签页（AI+、综合、笔记、视频、图片）
   - 实时显示搜索结果

3. **第三段 - AI应用图标区域**
   - 显示默认AI应用图标
   - 支持点击跳转到AI应用
   - 集成AIAppOverlayService悬浮窗功能
   - 提供更多AI应用选择

## ✅ 实现完成

### 1. 布局文件

#### 1.1 三段式胶囊布局
- **文件**: `voice_capsule_layout.xml`
- **功能**: 完整的三段式布局实现
- **特性**: 响应式设计、Material Design风格

#### 1.2 样式资源
- **颜色**: `voice_capsule_colors.xml`
- **按钮背景**: `voice_capsule_search_button_background.xml`
- **标签下划线**: `voice_capsule_tab_underline.xml`
- **应用图标背景**: `voice_capsule_app_icon_background.xml`

#### 1.3 图标资源
- 完整的操作图标集（关闭、文档、星标、播放等）
- AI应用图标（5个预设AI应用）
- 搜索相关图标（百度logo、过滤器等）

### 2. 功能实现

#### 2.1 自动切换逻辑
- 语音识别完成后自动切换到三段式布局
- 保持原始语音布局作为备选
- 支持退出胶囊模式返回原始布局

#### 2.2 网络搜索集成
- 自动启动FloatingWebViewService
- 支持百度搜索引擎
- 实时搜索状态更新

#### 2.3 AI应用跳转
- 集成AIAppOverlayService悬浮窗功能
- 支持多种AI应用启动
- 提供更多AI应用选择对话框

### 3. 技术特性

#### 3.1 布局切换
```kotlin
private fun switchToVoiceCapsuleLayout(text: String) {
    // 隐藏原始语音布局
    findViewById<LinearLayout>(R.id.voice_original_layout)?.visibility = View.GONE
    
    // 显示胶囊布局
    voiceCapsuleLayout?.visibility = View.VISIBLE
    
    // 设置识别文本并自动搜索
    voiceCapsuleTextInput?.setText(text)
    voiceCapsuleSearchInput?.setText(text)
    performCapsuleWebSearch()
}
```

#### 3.2 网络搜索
```kotlin
private fun performCapsuleWebSearch() {
    val serviceIntent = Intent(this, FloatingWebViewService::class.java).apply {
        putExtra("query", query)
        putExtra("search_engine", "baidu")
    }
    startService(serviceIntent)
}
```

#### 3.3 AI应用启动
```kotlin
private fun launchAIApp(packageName: String, appName: String, query: String) {
    val overlayIntent = Intent(this, AIAppOverlayService::class.java).apply {
        putExtra(AIAppOverlayService.EXTRA_APP_NAME, appName)
        putExtra(AIAppOverlayService.EXTRA_QUERY, query)
        putExtra(AIAppOverlayService.EXTRA_PACKAGE_NAME, packageName)
    }
    startService(overlayIntent)
}
```

## 🚀 使用方法

### 1. 语音输入
1. 进入简易模式
2. 点击语音tab
3. 进行语音输入
4. 语音识别完成后自动切换到三段式布局

### 2. 三段式布局操作
1. **第一段**: 编辑识别文本，使用各种操作按钮
2. **第二段**: 查看网络搜索结果，切换搜索标签
3. **第三段**: 点击AI应用图标跳转到对应应用

### 3. 退出胶囊模式
- 点击第一段右上角的关闭按钮
- 自动返回原始语音布局

## 🔧 配置选项

### 1. 搜索引擎配置
```kotlin
// 在performCapsuleWebSearch()中修改
putExtra("search_engine", "baidu") // 可改为其他搜索引擎
```

### 2. AI应用配置
```kotlin
// 在showMoreAIAppsDialog()中添加更多AI应用
val apps = listOf(
    "ChatGPT" to "com.openai.chatgpt",
    "Claude" to "com.anthropic.claude",
    // 添加更多AI应用...
)
```

### 3. 布局样式配置
- 修改`voice_capsule_colors.xml`调整颜色主题
- 修改`voice_capsule_layout.xml`调整布局结构
- 修改图标资源文件调整视觉效果

## 📱 兼容性

- **最低Android版本**: API 21 (Android 5.0)
- **目标Android版本**: API 34 (Android 14)
- **权限要求**: 
  - `SYSTEM_ALERT_WINDOW` (悬浮窗权限)
  - `RECORD_AUDIO` (录音权限)
  - `INTERNET` (网络权限)

## 🎨 设计特色

1. **锤子胶囊风格**: 参考锤子手机胶囊激活的搜索模式设计
2. **三段式布局**: 清晰的功能分区，提升用户体验
3. **Material Design**: 遵循Google Material Design设计规范
4. **响应式设计**: 适配不同屏幕尺寸和分辨率
5. **流畅动画**: 平滑的布局切换和交互反馈

## 🔍 故障排除

### 1. 布局不显示
- 检查`voice_capsule_layout.xml`是否正确包含
- 确认`initializeVoiceCapsuleLayout()`是否被调用

### 2. 搜索功能不工作
- 检查FloatingWebViewService是否正常运行
- 确认网络权限是否已授予

### 3. AI应用跳转失败
- 检查AIAppOverlayService是否已注册
- 确认悬浮窗权限是否已授予

## 📈 未来优化

1. **更多搜索引擎支持**: 集成Google、必应等搜索引擎
2. **自定义AI应用**: 允许用户自定义AI应用列表
3. **搜索历史**: 添加搜索历史记录功能
4. **语音控制**: 支持语音控制三段式布局操作
5. **主题定制**: 提供多种视觉主题选择

## 📝 总结

三段式语音胶囊布局成功实现了锤子胶囊激活搜索模式的核心功能，为用户提供了更加直观和高效的语音搜索体验。通过合理的布局设计和功能集成，显著提升了简易模式语音tab的实用性和用户体验。

