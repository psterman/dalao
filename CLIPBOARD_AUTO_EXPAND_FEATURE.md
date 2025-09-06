# 复制文字自动展开功能

## 功能描述

实现用户复制文字时灵动岛自动展开的功能。当用户在任何应用中复制文字时，灵动岛会自动检测剪贴板变化，并展开搜索模式，将复制的文字自动填入搜索框，方便用户快速进行搜索。

## 功能特性

### 1. 智能剪贴板监听
- **实时监听**: 监听系统剪贴板变化事件
- **防抖处理**: 1秒防抖时间，避免频繁触发
- **内容过滤**: 智能过滤无效内容（太短、纯数字、纯符号等）
- **重复检测**: 避免重复处理相同内容

### 2. 自动展开机制
- **圆球状态**: 如果当前是圆球状态，先恢复灵动岛再展开搜索
- **紧凑状态**: 直接展开搜索模式
- **搜索状态**: 如果已经是搜索状态，直接填入内容

### 3. 用户体验优化
- **Toast提示**: 显示检测到的复制内容（截断长文本）
- **自动搜索**: 填入内容后自动触发搜索
- **状态保持**: 保持灵动岛的当前状态和交互逻辑

## 技术实现

### 1. 剪贴板监听器初始化

**位置**: `onCreate()` 方法中调用 `initClipboardListener()`

```kotlin
private fun initClipboardListener() {
    try {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (isClipboardAutoExpandEnabled) {
                handleClipboardChange()
            }
        }
        
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        updateLastClipboardContent()
        
        Log.d(TAG, "剪贴板监听器初始化成功")
    } catch (e: Exception) {
        Log.e(TAG, "剪贴板监听器初始化失败", e)
    }
}
```

### 2. 剪贴板变化处理

**核心方法**: `handleClipboardChange()`

```kotlin
private fun handleClipboardChange() {
    try {
        val currentTime = System.currentTimeMillis()
        
        // 防抖处理：如果距离上次变化时间太短，忽略
        if (currentTime - lastClipboardChangeTime < clipboardChangeDebounceTime) {
            Log.d(TAG, "剪贴板变化过于频繁，忽略此次变化")
            return
        }
        
        val currentContent = getCurrentClipboardContent()
        
        // 检查是否有新内容且内容不为空
        if (currentContent != null && 
            currentContent.isNotEmpty() && 
            currentContent != lastClipboardContent &&
            isValidClipboardContent(currentContent)) {
            
            Log.d(TAG, "检测到剪贴板内容变化: $currentContent")
            
            // 更新时间和内容
            lastClipboardChangeTime = currentTime
            lastClipboardContent = currentContent
            
            // 自动展开灵动岛
            autoExpandForClipboard(currentContent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "处理剪贴板变化失败", e)
    }
}
```

### 3. 内容有效性检查

**过滤规则**:
- 长度少于2个字符的内容
- 纯数字内容（可能是验证码）
- 纯符号内容
- 长度超过500字符的内容

```kotlin
private fun isValidClipboardContent(content: String): Boolean {
    // 过滤掉太短的内容（少于2个字符）
    if (content.length < 2) {
        return false
    }
    
    // 过滤掉纯数字内容（可能是验证码等）
    if (content.matches(Regex("^\\d+$"))) {
        return false
    }
    
    // 过滤掉纯符号内容
    if (content.matches(Regex("^[^\\p{L}\\p{N}]+$"))) {
        return false
    }
    
    // 过滤掉太长的内容（超过500字符）
    if (content.length > 500) {
        return false
    }
    
    return true
}
```

### 4. 自动展开逻辑

**圆球状态处理**:
```kotlin
private fun autoExpandForClipboard(content: String) {
    try {
        Log.d(TAG, "为剪贴板内容自动展开灵动岛: $content")
        
        // 如果当前是圆球状态，先恢复灵动岛状态
        if (ballView != null && ballView?.visibility == View.VISIBLE) {
            restoreIslandState()
            // 等待恢复动画完成后再展开搜索
            windowContainerView?.postDelayed({
                expandIslandForClipboard(content)
            }, 500)
        } else {
            // 直接展开搜索
            expandIslandForClipboard(content)
        }
    } catch (e: Exception) {
        Log.e(TAG, "自动展开灵动岛失败", e)
    }
}
```

**搜索模式展开**:
```kotlin
private fun expandIslandForClipboard(content: String) {
    try {
        // 展开搜索模式
        if (!isSearchModeActive) {
            expandIsland()
        }
        
        // 将剪贴板内容设置到搜索框
        searchInput?.setText(content)
        
        // 触发搜索
        searchInput?.let { input ->
            // 模拟用户输入，触发搜索
            input.dispatchTouchEvent(android.view.MotionEvent.obtain(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                android.view.MotionEvent.ACTION_DOWN,
                0f, 0f, 0
            ))
        }
        
        // 显示Toast提示
        val displayContent = if (content.length > 20) {
            content.substring(0, 20) + "..."
        } else {
            content
        }
        Toast.makeText(this, "检测到复制内容：$displayContent", Toast.LENGTH_SHORT).show()
        
        Log.d(TAG, "剪贴板内容已设置到搜索框: $content")
    } catch (e: Exception) {
        Log.e(TAG, "展开搜索模式失败", e)
    }
}
```

### 5. 功能控制接口

**启用/禁用功能**:
```kotlin
fun setClipboardAutoExpandEnabled(enabled: Boolean) {
    isClipboardAutoExpandEnabled = enabled
    Log.d(TAG, "复制文字自动展开功能已${if (enabled) "启用" else "禁用"}")
}

fun isClipboardAutoExpandEnabled(): Boolean {
    return isClipboardAutoExpandEnabled
}
```

### 6. 资源清理

**在onDestroy中清理**:
```kotlin
private fun cleanupClipboardListener() {
    try {
        clipboardListener?.let { listener ->
            clipboardManager?.removePrimaryClipChangedListener(listener)
        }
        clipboardListener = null
        clipboardManager = null
        lastClipboardContent = null
        Log.d(TAG, "剪贴板监听器已清理")
    } catch (e: Exception) {
        Log.e(TAG, "清理剪贴板监听器失败", e)
    }
}
```

## 配置参数

### 1. 防抖时间
```kotlin
private val clipboardChangeDebounceTime = 1000L // 防抖时间：1秒
```

### 2. 内容过滤规则
- **最小长度**: 2个字符
- **最大长度**: 500个字符
- **过滤纯数字**: 避免验证码等
- **过滤纯符号**: 避免无意义内容

### 3. 功能开关
```kotlin
private var isClipboardAutoExpandEnabled = true // 默认启用
```

## 用户体验

### 1. 交互流程
1. **用户复制文字** → 系统剪贴板更新
2. **检测到变化** → 验证内容有效性
3. **自动展开** → 根据当前状态展开灵动岛
4. **填入内容** → 将复制的文字填入搜索框
5. **触发搜索** → 自动执行搜索操作
6. **显示提示** → Toast显示检测到的内容

### 2. 状态处理
- **圆球状态**: 先恢复灵动岛，再展开搜索
- **紧凑状态**: 直接展开搜索模式
- **搜索状态**: 直接填入内容并触发搜索

### 3. 视觉反馈
- **Toast提示**: 显示"检测到复制内容：xxx"
- **内容截断**: 长文本显示前20个字符+省略号
- **搜索触发**: 自动执行搜索，显示结果

## 性能优化

### 1. 防抖机制
- **1秒防抖**: 避免频繁的剪贴板变化触发
- **时间记录**: 记录上次变化时间，避免重复处理

### 2. 内容过滤
- **智能过滤**: 过滤掉无效内容，减少不必要的处理
- **长度限制**: 限制处理的内容长度，避免性能问题

### 3. 资源管理
- **及时清理**: 在Service销毁时清理监听器
- **内存优化**: 避免内存泄漏，及时释放资源

## 测试验证

### 1. 基本功能测试
- **复制文字**: 在任何应用中复制文字
- **自动展开**: 验证灵动岛自动展开
- **内容填入**: 验证复制的文字填入搜索框
- **搜索触发**: 验证自动触发搜索

### 2. 状态测试
- **圆球状态**: 在圆球状态下复制文字
- **紧凑状态**: 在紧凑状态下复制文字
- **搜索状态**: 在搜索状态下复制文字

### 3. 内容过滤测试
- **短文本**: 复制1个字符的文本
- **纯数字**: 复制纯数字内容
- **纯符号**: 复制纯符号内容
- **长文本**: 复制超过500字符的文本

### 4. 防抖测试
- **快速复制**: 快速连续复制多次
- **频繁变化**: 验证防抖机制是否生效

## 预期效果

实现后的复制文字自动展开功能应该能够：

- ✅ **智能检测**: 准确检测剪贴板变化
- ✅ **自动展开**: 根据当前状态自动展开灵动岛
- ✅ **内容填入**: 将复制的文字自动填入搜索框
- ✅ **搜索触发**: 自动触发搜索操作
- ✅ **用户提示**: 提供清晰的视觉反馈
- ✅ **性能优化**: 防抖和过滤机制确保性能
- ✅ **状态管理**: 正确处理各种灵动岛状态
- ✅ **资源清理**: 避免内存泄漏

## 使用场景

### 1. 文本搜索
- 复制网页中的关键词，自动搜索
- 复制文档中的术语，快速查找
- 复制聊天记录中的内容，进行搜索

### 2. 应用跳转
- 复制应用名称，快速打开应用
- 复制联系人信息，快速查找
- 复制地址信息，快速导航

### 3. 内容分享
- 复制链接，快速分享
- 复制文本，快速发送
- 复制图片描述，快速搜索

## 总结

通过实现复制文字自动展开功能，用户现在可以：

1. **无缝体验**: 复制文字后自动展开灵动岛，无需手动操作
2. **快速搜索**: 复制的文字自动填入搜索框，立即开始搜索
3. **智能过滤**: 自动过滤无效内容，避免误触发
4. **状态感知**: 根据当前状态智能处理，提供最佳体验
5. **性能优化**: 防抖和过滤机制确保流畅运行

这个功能大大提升了灵动岛的实用性和用户体验，让搜索变得更加便捷和智能！

