# WebView堆叠功能使用说明

## 功能概述

WebView堆叠功能实现了真正的WebView叠加排列效果，用户可以通过左右滑动来翻阅下方的WebView，就像翻阅卡片一样。每个WebView都叠加在现有WebView的下方，形成3D堆叠效果。

## 核心特性

### 1. 真正的叠加排列
- **视觉叠加**：WebView在视觉上真正叠加排列，不是横向隐藏
- **3D效果**：下方的WebView有偏移、缩放、旋转和透明度变化
- **层级管理**：通过elevation和z-index管理WebView层级

### 2. 智能触摸交互
- **左右滑动**：翻阅WebView堆栈
  - 向右滑动：显示上一个WebView
  - 向左滑动：显示下一个WebView
- **智能检测**：自动识别滑动方向，避免误操作
- **流畅动画**：400ms的流畅切换动画

### 3. 3D堆叠效果
```kotlin
// 当前活跃的WebView
webView.translationX = 0f
webView.translationY = 0f
webView.scaleX = 1f
webView.scaleY = 1f
webView.rotation = 0f
webView.alpha = 1f
webView.elevation = 10f

// 下方的WebView（堆叠效果）
webView.translationX = STACK_OFFSET_X * depth
webView.translationY = STACK_OFFSET_Y * depth
webView.scaleX = STACK_SCALE_FACTOR.pow(depth)
webView.scaleY = STACK_SCALE_FACTOR.pow(depth)
webView.rotation = STACK_ROTATION * depth
webView.alpha = 1f - (depth * 0.1f)
webView.elevation = 10f - depth
```

## 技术实现

### 1. 核心组件

#### StackedWebViewManager
```kotlin
class StackedWebViewManager(
    private val context: Context,
    private val container: FrameLayout
) {
    // WebView数据管理
    data class StackedWebViewData(
        val id: Long,
        val webView: WebView,
        var url: String? = null,
        var title: String = "新页面",
        var stackIndex: Int = 0,
        var isActive: Boolean = false
    )
    
    // 触摸事件处理
    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean
    
    // 3D堆叠布局
    private fun updateStackLayout()
    
    // 动画切换
    private fun animateStackTransition(fromIndex: Int, toIndex: Int, isReversing: Boolean)
}
```

### 2. 堆叠布局算法
```kotlin
private fun updateStackLayout() {
    webViews.forEachIndexed { index, webViewData ->
        val webView = webViewData.webView
        val stackDepth = index - currentIndex
        
        when {
            stackDepth == 0 -> {
                // 当前活跃的WebView - 完全显示
                webView.translationX = 0f
                webView.translationY = 0f
                webView.scaleX = 1f
                webView.scaleY = 1f
                webView.rotation = 0f
                webView.alpha = 1f
                webView.elevation = 10f
            }
            stackDepth > 0 -> {
                // 下方的WebView - 3D堆叠效果
                val depth = minOf(stackDepth, 3) // 最多显示3层
                webView.translationX = STACK_OFFSET_X * depth
                webView.translationY = STACK_OFFSET_Y * depth
                webView.scaleX = STACK_SCALE_FACTOR.pow(depth)
                webView.scaleY = STACK_SCALE_FACTOR.pow(depth)
                webView.rotation = STACK_ROTATION * depth
                webView.alpha = 1f - (depth * 0.1f)
                webView.elevation = 10f - depth
            }
            stackDepth < 0 -> {
                // 上方的WebView - 隐藏
                webView.alpha = 0f
                webView.elevation = 0f
            }
        }
    }
}
```

### 3. 触摸处理逻辑
```kotlin
private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            initialX = event.x
            initialY = event.y
            isHorizontalSwipe = false
        }
        
        MotionEvent.ACTION_MOVE -> {
            val deltaX = abs(event.x - initialX)
            val deltaY = abs(event.y - initialY)
            
            // 判断是否为水平滑动
            if (deltaX > SWIPE_THRESHOLD && deltaX > deltaY) {
                isHorizontalSwipe = true
            }
        }
        
        MotionEvent.ACTION_UP -> {
            if (isHorizontalSwipe) {
                val deltaX = event.x - initialX
                
                if (deltaX > 0) {
                    showPreviousWebView() // 向右滑动，显示上一个
                } else {
                    showNextWebView() // 向左滑动，显示下一个
                }
            }
        }
    }
}
```

### 4. 动画切换效果
```kotlin
private fun animateStackTransition(fromIndex: Int, toIndex: Int, isReversing: Boolean) {
    val fromWebView = webViews[fromIndex].webView
    val toWebView = webViews[toIndex].webView
    
    // 计算动画参数
    val fromEndX = if (isReversing) containerWidth.toFloat() else -containerWidth.toFloat()
    val toEndX = if (isReversing) -containerWidth.toFloat() else containerWidth.toFloat()
    
    // 创建水平滑动动画
    val fromAnimator = ValueAnimator.ofFloat(fromStartX, fromEndX)
    val toAnimator = ValueAnimator.ofFloat(toStartX, toEndX)
    
    // 添加透明度变化
    fromAnimator.addUpdateListener { animation ->
        val progress = animation.animatedFraction
        fromWebView.alpha = 1f - progress * 0.3f
    }
    
    toAnimator.addUpdateListener { animation ->
        val progress = animation.animatedFraction
        toWebView.alpha = 0.7f + progress * 0.3f
    }
}
```

## 使用方法

### 1. 在FloatingWebViewService中使用

```kotlin
// 启用堆叠模式
isStackMode = true

// 创建堆叠WebView管理器
val container = findViewById<FrameLayout>(R.id.vertical_webview_container)
stackedWebViewManager = StackedWebViewManager(this, container)

// 添加WebView到堆栈
stackedWebViewManager?.addWebView("https://www.baidu.com")

// 切换模式
toggleWebViewMode()
```

### 2. 触摸操作
- **左右滑动**：在WebView容器区域左右滑动翻阅
- **添加WebView**：点击"+"按钮添加新WebView到堆栈底部
- **移除WebView**：移除当前活跃的WebView

### 3. 视觉效果
- **当前WebView**：完全显示，无偏移和旋转
- **下方WebView**：有偏移、缩放、旋转和透明度变化
- **上方WebView**：完全隐藏
- **最多显示3层**：避免过度堆叠影响性能

## 参数配置

### 堆叠效果参数
```kotlin
companion object {
    private const val STACK_OFFSET_Y = 20f // 堆叠Y轴偏移
    private const val STACK_OFFSET_X = 15f // 堆叠X轴偏移
    private const val STACK_SCALE_FACTOR = 0.95f // 堆叠缩放因子
    private const val STACK_ROTATION = 2f // 堆叠旋转角度
    private const val SWIPE_THRESHOLD = 80f // 滑动阈值
    private const val ANIMATION_DURATION = 400L // 动画持续时间
}
```

### 可调整参数
- **STACK_OFFSET_Y**：控制垂直偏移量
- **STACK_OFFSET_X**：控制水平偏移量
- **STACK_SCALE_FACTOR**：控制缩放比例
- **STACK_ROTATION**：控制旋转角度
- **SWIPE_THRESHOLD**：控制滑动敏感度
- **ANIMATION_DURATION**：控制动画速度

## 优势特点

### 1. 真正的叠加效果
- **视觉真实**：WebView真正叠加，不是隐藏
- **3D效果**：丰富的视觉层次感
- **直观操作**：符合用户对卡片堆叠的认知

### 2. 性能优化
- **层级限制**：最多显示3层，避免过度渲染
- **智能隐藏**：上方的WebView完全隐藏
- **内存管理**：及时清理不需要的WebView

### 3. 用户体验
- **流畅动画**：400ms的流畅切换
- **智能检测**：精确的触摸方向识别
- **状态保持**：每个WebView保持独立状态

## 测试方法

### 1. 使用测试Activity
```kotlin
// 启动测试Activity
val intent = Intent(this, StackedWebViewTestActivity::class.java)
startActivity(intent)
```

### 2. 测试场景
- **基本功能**：添加/移除WebView
- **滑动翻阅**：左右滑动切换WebView
- **3D效果**：观察堆叠的视觉效果
- **动画流畅度**：测试切换动画的流畅性

### 3. 性能测试
- **内存使用**：监控WebView内存占用
- **渲染性能**：测试3D效果的渲染性能
- **触摸响应**：测试触摸事件的响应速度

## 注意事项

1. **性能考虑**：限制最大WebView数量（8个）
2. **内存管理**：及时清理不需要的WebView
3. **触摸冲突**：避免与其他触摸事件冲突
4. **动画性能**：确保动画不影响整体性能

## 未来优化

1. **手势增强**：支持更多手势操作
2. **预览功能**：添加WebView预览缩略图
3. **分组管理**：支持WebView分组功能
4. **自定义效果**：允许用户自定义堆叠效果参数
