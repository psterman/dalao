# QuarterArcOperationBar 使用说明

## 功能概述

QuarterArcOperationBar 是一个高度可定制的四分之一圆弧操作栏，专为移动设备优化，支持左手模式和丰富的交互功能。

## 主要特性

### ✨ 视觉设计
- **隐藏圆弧线**：只显示按钮，界面更简洁
- **按钮均匀分布**：在90度圆弧路径上完美分布
- **Material Design风格**：符合现代设计规范
- **流畅动画**：展开/收起动画，缩放动画

### 🎯 交互功能
- **透明激活按钮**：点击展开/收起功能按钮
- **双指缩放**：调整圆弧大小（80dp-200dp）
- **长按提示**：显示按钮功能说明
- **长按配置**：激活按钮长按显示配置界面

### ⚙️ 自定义功能
- **按钮配置**：动态增删按钮，自定义功能
- **左手模式**：完美支持左右手切换
- **大小调整**：支持手势和设置界面调整
- **功能提示**：缩放和按钮操作的视觉引导

## 使用方法

### 基本使用

```kotlin
// 创建操作栏
val operationBar = QuarterArcOperationBar(context)

// 设置操作监听器
operationBar.setOnOperationListener(object : QuarterArcOperationBar.OnOperationListener {
    override fun onRefresh() { /* 刷新操作 */ }
    override fun onNextTab() { /* 切换标签 */ }
    override fun onBack() { /* 返回操作 */ }
    override fun onUndoClose() { /* 撤回关闭 */ }
})

// 设置配置监听器
operationBar.setOnConfigListener(object : QuarterArcOperationBar.OnConfigListener {
    override fun onShowConfig() {
        operationBar.showConfigDialog(supportFragmentManager)
    }
})
```

### 自定义按钮

```kotlin
// 创建自定义按钮配置
val customButtons = listOf(
    QuarterArcOperationBar.ButtonConfig(
        icon = R.drawable.ic_custom,
        action = { /* 自定义操作 */ },
        description = "自定义功能",
        isEnabled = true
    )
)

// 设置按钮配置
operationBar.setButtonConfigs(customButtons)

// 动态添加按钮
operationBar.addButton(newButtonConfig)

// 移除按钮
operationBar.removeButton("按钮描述")
```

### 调整大小和模式

```kotlin
// 设置圆弧大小
operationBar.setArcRadius(120f) // 120dp

// 设置左手模式
operationBar.setLeftHandedMode(true)

// 获取当前大小
val currentRadius = operationBar.getArcRadius()
```

## 交互说明

### 基本操作
1. **点击激活按钮**：展开/收起功能按钮
2. **点击功能按钮**：执行对应操作，自动收起
3. **双指缩放**：在展开状态下调整圆弧大小

### 高级操作
1. **长按功能按钮**：显示功能说明提示
2. **长按激活按钮**：显示配置界面
3. **配置界面**：调整大小、左手模式、按钮配置

## 配置选项

### 圆弧大小
- **范围**：80dp - 200dp
- **调整方式**：双指缩放或配置界面滑块
- **实时预览**：调整时立即生效

### 左手模式
- **右手模式**：操作栏在右下角，按钮从左上到正下分布
- **左手模式**：操作栏在左下角，按钮从正下到右上分布
- **切换动画**：平滑过渡动画

### 按钮配置
- **预设功能**：刷新、切换标签、后退、撤回关闭
- **自定义功能**：支持添加自定义按钮
- **启用/禁用**：可以单独控制每个按钮
- **删除按钮**：支持动态删除不需要的按钮

## 提示系统

### 缩放提示
- **触发**：开始双指缩放时显示
- **内容**："双指缩放调整大小" + 缩放图标
- **消失**：缩放结束后自动隐藏

### 按钮功能提示
- **触发**：长按功能按钮500ms
- **内容**：按钮功能描述
- **样式**：黑色背景，白色文字，圆角矩形

### 配置提示
- **触发**：长按激活按钮800ms
- **内容**："长按显示配置"
- **功能**：同时打开配置对话框

## 最佳实践

1. **按钮数量**：建议2-6个按钮，保持界面简洁
2. **功能选择**：选择最常用的操作作为按钮功能
3. **大小设置**：根据屏幕大小和使用习惯调整
4. **左手模式**：根据用户习惯设置，提供切换选项
5. **提示使用**：引导用户使用长按查看功能说明

## 注意事项

- 缩放功能只在展开状态下可用
- 配置更改会立即生效
- 按钮执行后会自动收起操作栏
- 支持动态配置，无需重启应用
