# WebView堆叠功能集成说明

## 功能概述

我已经成功将WebView堆叠功能集成到HomeActivity中，现在用户可以在主界面中体验真正的WebView叠加排列效果。

## 使用方法

### 1. 启用堆叠模式
- **长按搜索按钮**：长按搜索按钮可以切换WebView模式
- **模式切换**：在单WebView模式和堆叠模式之间切换
- **提示信息**：切换时会显示Toast提示当前模式

### 2. 堆叠模式特性
- **真正的叠加**：WebView在视觉上真正叠加排列
- **3D效果**：下方的WebView有偏移、缩放、旋转和透明度变化
- **左右滑动**：通过左右滑动翻阅WebView堆栈
- **动态添加**：每次搜索都会添加新的WebView到堆栈

### 3. 操作方式
- **搜索**：在搜索框中输入内容，点击搜索按钮
- **添加WebView**：每次搜索都会在堆栈底部添加新的WebView
- **翻阅**：左右滑动来翻阅不同的WebView
- **切换模式**：长按搜索按钮切换模式

## 技术实现

### 1. 集成到HomeActivity
```kotlin
// 堆叠WebView管理器
private var stackedWebViewManager: StackedWebViewManager? = null
private var isStackMode = false

// 设置堆叠WebView模式
private fun setupStackedWebView() {
    stackedWebViewManager = StackedWebViewManager(this, webViewContainer)
    // 设置监听器和添加初始WebView
}

// 切换WebView模式
fun toggleWebViewMode() {
    isStackMode = !isStackMode
    setupSearch()
}
```

### 2. 搜索功能集成
```kotlin
// 在performSearch方法中
if (isStackMode) {
    // 堆叠模式：添加新的WebView到堆栈
    stackedWebViewManager?.addWebView(url)
    homeContent.visibility = View.GONE
} else {
    // 单WebView模式：在现有WebView中加载
    webView.loadUrl(url)
    webView.visibility = View.VISIBLE
    homeContent.visibility = View.GONE
}
```

### 3. 用户交互
```kotlin
// 长按搜索按钮切换模式
voiceSearchButton.setOnLongClickListener {
    toggleWebViewMode()
    Toast.makeText(this, "WebView模式切换为: ${if (isStackMode) "堆叠模式" else "单WebView模式"}", Toast.LENGTH_SHORT).show()
    true
}
```

## 视觉效果

### 1. 堆叠效果
- **当前WebView**：完全显示，无偏移和旋转
- **下方WebView**：有偏移、缩放、旋转和透明度变化
- **上方WebView**：完全隐藏
- **最多显示3层**：避免过度堆叠影响性能

### 2. 动画效果
- **切换动画**：400ms的流畅水平滑动动画
- **透明度变化**：切换时WebView的透明度平滑变化
- **3D变换**：缩放、旋转、偏移的组合效果

## 测试步骤

### 1. 启用堆叠模式
1. 打开应用
2. 长按搜索按钮
3. 看到Toast提示"WebView模式切换为: 堆叠模式"

### 2. 测试搜索功能
1. 在搜索框中输入内容（如"百度"）
2. 点击搜索按钮
3. 观察WebView被添加到堆栈底部

### 3. 测试翻阅功能
1. 再次搜索不同内容（如"Google"）
2. 观察新的WebView被添加到堆栈
3. 左右滑动来翻阅不同的WebView

### 4. 观察3D效果
1. 注意下方WebView的偏移、缩放、旋转效果
2. 观察透明度变化
3. 体验流畅的切换动画

## 优势特点

### 1. 真正的叠加效果
- **视觉真实**：WebView真正叠加，不是隐藏
- **3D效果**：丰富的视觉层次感
- **直观操作**：符合用户对卡片堆叠的认知

### 2. 无缝集成
- **保持原有功能**：所有原有功能都保持不变
- **模式切换**：可以随时在两种模式间切换
- **向后兼容**：不影响现有的使用习惯

### 3. 性能优化
- **层级限制**：最多显示3层，避免过度渲染
- **智能隐藏**：上方的WebView完全隐藏
- **内存管理**：及时清理不需要的WebView

## 注意事项

1. **模式切换**：长按搜索按钮切换模式
2. **搜索行为**：堆叠模式下每次搜索都会添加新WebView
3. **翻阅操作**：左右滑动翻阅WebView堆栈
4. **性能考虑**：限制最大WebView数量（8个）

现在您可以在HomeActivity中体验真正的WebView堆叠效果了！长按搜索按钮切换到堆叠模式，然后进行搜索和翻阅操作。
