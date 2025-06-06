# dalao - AI 悬浮球应用

# 浮动窗口WebView修复说明

## 问题描述

在DualFloatingWebViewService中存在以下问题：

1. 第一个和第二个WebView无法显示AI搜索引擎列表
2. 第三个WebView无法显示任何搜索引擎列表
3. 点击切换按钮无法在普通搜索引擎和AI搜索引擎之间切换

## 修复方案

### 1. 确保正确初始化搜索引擎容器

我们检查了布局文件`layout_dual_floating_webview.xml`，确认每个WebView都有对应的：
- 普通搜索引擎容器（first_engine_container, second_engine_container, third_engine_container）
- AI搜索引擎容器（first_ai_engine_container, second_ai_engine_container, third_ai_engine_container）
- AI滚动容器（first_ai_scroll_container, second_ai_scroll_container, third_ai_scroll_container）
- 引擎切换按钮（first_engine_toggle, second_engine_toggle, third_engine_toggle）

### 2. 添加详细日志跟踪

在关键位置添加了详细的日志输出，以便跟踪容器的可见性状态和点击事件的处理：
```kotlin
Log.d(TAG, "第一个窗口AI容器当前可见性: ${if (isAIVisible) "可见" else "不可见"}")
```

### 3. 专门的切换按钮刷新方法

创建了一个专门的方法`refreshEngineToggleButtons()`，重新绑定所有引擎切换按钮的点击事件：
```kotlin
/**
 * 刷新搜索引擎切换按钮的状态和事件
 */
private fun refreshEngineToggleButtons() {
    // 代码实现...
}
```

### 4. 修复点击切换逻辑

修改了点击事件处理逻辑，确保在点击切换按钮时：
- 检查AI滚动容器是否存在
- 正确切换容器可见性
- 使用动画增强交互体验
- 强制重新绘制整个视图
```kotlin
// 直接切换容器可见性
firstAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
firstEngineToggle?.setImageResource(if (isAIVisible) R.drawable.ic_ai_search else R.drawable.ic_search)

// 强制重新绘制整个视图
container?.invalidate()
container?.requestLayout()
```

### 5. 确保切换按钮优先级

确保按钮事件绑定的顺序正确，避免被覆盖：
```kotlin
// 初始化搜索引擎切换工具栏
setupSearchEngineToolbars()

// 单独设置搜索引擎切换按钮，确保不被覆盖
refreshEngineToggleButtons()
```

### 6. 增强错误处理

为可能的空引用添加了更完善的错误处理：
```kotlin
if (firstAIScrollContainer == null) {
    Log.e(TAG, "第一个窗口AI滚动容器为null")
    Toast.makeText(this, "错误：AI容器未初始化", Toast.LENGTH_SHORT).show()
    return@setOnClickListener
}
```

### 7. 在服务启动时刷新按钮状态

在onCreate方法末尾添加对refreshEngineToggleButtons的调用，确保按钮初始化后立即设置正确的点击事件：
```kotlin
override fun onCreate() {
    // 初始化代码...
    
    // 刷新引擎切换按钮，确保点击事件被正确设置
    refreshEngineToggleButtons()
}
```

## 结果

通过上述修改，现在所有三个WebView都能正确地：
1. 显示普通搜索引擎列表
2. 通过点击切换按钮显示或隐藏AI搜索引擎列表
3. 在切换时正确更新UI和图标状态
