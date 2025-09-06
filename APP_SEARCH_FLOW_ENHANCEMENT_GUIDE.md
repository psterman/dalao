# 应用搜索流程增强功能指南

## 功能概述

灵动岛搜索功能现已增强，支持更智能的应用搜索执行逻辑和状态切换功能。

## 新增功能

### 1. 智能搜索执行逻辑
**功能**: 根据应用是否支持URL scheme，采用不同的搜索执行策略

**行为**:
- **支持URL scheme的应用**: 直接跳转到应用内搜索页面
- **不支持URL scheme的应用**: 复制搜索文本到剪贴板，直接打开应用

### 2. 灵动岛状态切换功能
**功能**: 当用户进入其他应用时，灵动岛从横条变成圆球

**行为**:
- **进入其他应用**: 灵动岛自动切换到圆球状态
- **点击圆球**: 恢复灵动岛横条状态

## 技术实现

### 1. 智能搜索执行逻辑

#### 修改前的问题
- 所有应用都显示对话框，需要用户确认
- 不支持URL scheme的应用也需要用户手动操作

#### 修改后的解决方案
```kotlin
private fun handleSearchWithSelectedApp(query: String, appInfo: AppInfo) {
    if (appInfo.urlScheme != null) {
        // 有URL scheme，直接跳转到APP搜索结果页面
        val intent = createUrlSchemeIntent(appInfo.urlScheme, query)
        startActivity(intent)
        hideContentAndSwitchToBall() // 切换到圆球状态
    } else {
        // 没有URL scheme，直接复制文本并打开应用
        handleAppWithoutUrlScheme(query, appInfo)
    }
}

private fun handleAppWithoutUrlScheme(query: String, appInfo: AppInfo) {
    // 复制搜索文本到剪贴板
    copyTextToClipboard(query)
    
    // 启动应用
    val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
    startActivity(launchIntent)
    
    // 显示提示信息
    Toast.makeText(this, "已复制「$query」到剪贴板，请在${appInfo.label}中粘贴搜索", Toast.LENGTH_LONG).show()
    
    // 切换到圆球状态
    hideContentAndSwitchToBall()
}
```

### 2. 灵动岛状态切换功能

#### 圆球状态实现
```kotlin
private fun hideContentAndSwitchToBall() {
    // 隐藏搜索面板和配置面板
    hideAppSearchResults()
    hideConfigPanel()
    
    // 隐藏键盘
    hideKeyboard()
    
    // 切换到圆球状态
    transitionToBallState()
}

private fun transitionToBallState() {
    // 设置搜索模式为非活跃状态
    isSearchModeActive = false
    
    // 清理展开的视图
    cleanupExpandedViews()
    
    // 动画切换到圆球状态
    animatingIslandView?.animate()
        ?.alpha(0f)
        ?.setDuration(300)
        ?.withEndAction {
            switchToBallMode()
        }
        ?.start()
}

private fun switchToBallMode() {
    // 隐藏当前的灵动岛视图
    animatingIslandView?.visibility = View.GONE
    
    // 创建圆球视图
    createBallView()
    
    // 设置点击监听器
    setupBallClickListener()
}
```

#### 圆球视图创建
```kotlin
private fun createBallView() {
    ballView = View(this).apply {
        background = ContextCompat.getDrawable(this@DynamicIslandService, R.drawable.ic_launcher_foreground)
        val ballSize = (48 * resources.displayMetrics.density).toInt() // 48dp
        layoutParams = FrameLayout.LayoutParams(ballSize, ballSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = statusBarHeight + 20 // 状态栏下方20dp
        }
        visibility = View.VISIBLE
        alpha = 0f
    }
    
    // 添加到窗口容器
    windowContainerView?.addView(ballView)
    
    // 显示圆球动画
    ballView?.animate()
        ?.alpha(1f)
        ?.setDuration(300)
        ?.start()
}
```

#### 恢复灵动岛状态
```kotlin
private fun restoreIslandState() {
    // 隐藏圆球
    ballView?.animate()
        ?.alpha(0f)
        ?.setDuration(200)
        ?.withEndAction {
            windowContainerView?.removeView(ballView)
            ballView = null
        }
        ?.start()
    
    // 恢复灵动岛视图
    animatingIslandView?.visibility = View.VISIBLE
    animatingIslandView?.alpha = 0f
    animatingIslandView?.animate()
        ?.alpha(1f)
        ?.setDuration(300)
        ?.start()
}
```

## 交互流程示例

### 场景1: 支持URL scheme的应用搜索
```
步骤1: 用户选中微信应用
步骤2: 用户输入"天气"
步骤3: 用户点击搜索按钮
结果: 
- 直接跳转到微信搜索"天气"页面
- 灵动岛切换到圆球状态
- 用户可以在微信中看到搜索结果
```

### 场景2: 不支持URL scheme的应用搜索
```
步骤1: 用户选中某个应用（如某个游戏）
步骤2: 用户输入"攻略"
步骤3: 用户点击搜索按钮
结果: 
- 复制"攻略"到剪贴板
- 直接打开该应用
- 灵动岛切换到圆球状态
- 用户可以在应用中粘贴搜索
```

### 场景3: 圆球状态恢复
```
步骤1: 用户进入其他应用，灵动岛变成圆球
步骤2: 用户点击圆球
结果: 
- 圆球消失
- 灵动岛横条状态恢复
- 用户可以继续使用搜索功能
```

## 用户体验改进

### 1. 更流畅的搜索体验
- **减少操作步骤**: 不需要用户手动确认
- **智能处理**: 根据应用类型自动选择最佳处理方式
- **即时反馈**: 提供清晰的操作提示

### 2. 更直观的状态管理
- **自动切换**: 进入其他应用时自动变成圆球
- **快速恢复**: 点击圆球即可恢复灵动岛状态
- **视觉反馈**: 平滑的动画过渡

### 3. 更智能的应用处理
- **URL scheme支持**: 直接跳转到应用内搜索
- **普通应用支持**: 复制文本并打开应用
- **错误处理**: 优雅处理无法启动的应用

## 测试用例

### 测试用例1: URL scheme应用搜索
```
前置条件: 选中微信应用
操作: 输入"天气"，点击搜索
预期结果: 
- 跳转到微信搜索页面
- 灵动岛变成圆球
- 显示"已跳转到微信搜索"提示
```

### 测试用例2: 普通应用搜索
```
前置条件: 选中某个不支持URL scheme的应用
操作: 输入"攻略"，点击搜索
预期结果: 
- 复制"攻略"到剪贴板
- 打开该应用
- 灵动岛变成圆球
- 显示"已复制「攻略」到剪贴板，请在[应用名]中粘贴搜索"提示
```

### 测试用例3: 圆球状态切换
```
前置条件: 灵动岛处于圆球状态
操作: 点击圆球
预期结果: 
- 圆球消失
- 灵动岛横条状态恢复
- 可以正常使用搜索功能
```

### 测试用例4: 错误处理
```
前置条件: 选中一个无法启动的应用
操作: 输入文本，点击搜索
预期结果: 
- 显示"无法启动该应用"提示
- 灵动岛保持当前状态
```

## 调试信息

### 关键日志
```bash
adb logcat | grep "DynamicIslandService"
```

**搜索执行日志**:
- `使用选中的APP进行搜索: [应用名], 查询: [内容]`
- `处理没有URL scheme的应用: [应用名], 查询: [内容]`
- `已跳转到[应用名]搜索`
- `已复制「[内容]」到剪贴板，请在[应用名]中粘贴搜索`

**状态切换日志**:
- `隐藏内容并切换到圆球状态`
- `切换到圆球状态`
- `切换到圆球模式`
- `圆球被点击，恢复灵动岛状态`
- `恢复灵动岛状态`

## 总结

新的应用搜索流程增强功能提供了：

- ✅ **智能搜索执行**: 根据应用类型自动选择最佳处理方式
- ✅ **流畅状态切换**: 进入其他应用时自动变成圆球
- ✅ **快速恢复机制**: 点击圆球即可恢复灵动岛状态
- ✅ **更好的用户体验**: 减少操作步骤，提供即时反馈
- ✅ **错误处理**: 优雅处理各种异常情况

这些功能让灵动岛搜索更加智能和用户友好，提供了更流畅的搜索体验。
