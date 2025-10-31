package com.example.aifloatingball.webview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.example.aifloatingball.utils.WebViewConstants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 扩展函数：Float的幂运算
 */
private fun Float.pow(exponent: Int): Float {
    return this.pow(exponent.toFloat())
}

/**
 * 纸堆WebView管理器 - 重新设计版本
 * 实现真正的标签页纵向叠加效果，每个WebView作为独立标签页
 * 用户横向滑动可以切换不同标签页，每个标签页纵向叠加显示
 */
class PaperStackWebViewManager(
    private val context: Context,
    private val container: ViewGroup
) {
    companion object {
        private const val TAG = "PaperStackWebViewManager"
        private const val MAX_TABS = 8 // 最大标签页数量
        private const val TAB_OFFSET_X = 15f // 每个标签页的X轴偏移
        private const val TAB_OFFSET_Y = 10f // 每个标签页的Y轴偏移
        private const val SWIPE_THRESHOLD = 50f // 滑动阈值 - 进一步降低阈值提高响应性
        private const val SWIPE_VELOCITY_THRESHOLD = 500f // 滑动速度阈值
        private const val ANIMATION_DURATION = 350L // 动画持续时间
        private const val TAB_SHADOW_RADIUS = 15f // 标签页阴影半径
        private const val TAB_CORNER_RADIUS = 10f // 标签页圆角半径
        private const val TAB_SCALE_FACTOR = 0.96f // 标签页缩放因子
        private const val TAB_ALPHA_FACTOR = 0.15f // 标签页透明度因子
    }

    // 标签页数据类
    data class WebViewTab(
        val id: String,
        val webView: WebView,
        val title: String,
        val url: String,
        var isActive: Boolean = false,
        var stackIndex: Int = 0
    )

    private val tabs = mutableListOf<WebViewTab>()
    private var currentTabIndex = 0
    private var isAnimating = false
    private var gestureDetector: GestureDetector? = null
    private var onTabCreatedListener: ((WebViewTab) -> Unit)? = null
    private var onTabSwitchedListener: ((WebViewTab, Int) -> Unit)? = null
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwipeStarted = false
    private var swipeDirection = SwipeDirection.NONE
    private var isTextSelectionActive = false
    private var lastTouchTime = 0L
    private var touchDownTime = 0L

    init {
        setupGestureDetector()
        setupContainer()
    }

    /**
     * 设置容器
     */
    private fun setupContainer() {
        container.clipChildren = false
        container.clipToPadding = false
    }

    /**
     * 设置手势检测器
     */
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isAnimating || tabs.isEmpty()) return false
                
                val deltaX = e2.x - (e1?.x ?: 0f)
                val deltaY = e2.y - (e1?.y ?: 0f)
                
                // 检测横向滑动
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > SWIPE_THRESHOLD) {
                    if (deltaX > 0) {
                        // 右滑 - 切换到上一个标签页
                        switchToPreviousTab()
                    } else {
                        // 左滑 - 切换到下一个标签页
                        switchToNextTab()
                    }
                    return true
                }
                return false
            }
        })
    }

    /**
     * 添加新的标签页
     */
    fun addTab(url: String? = null, title: String? = null): WebViewTab {
        val tabId = "tab_${System.currentTimeMillis()}"
        val webView = PaperWebView(context)
        webView.setupWebView()
        
        // 创建标签页
        val tab = WebViewTab(
            id = tabId,
            webView = webView,
            title = title ?: "新标签页",
            url = url ?: "https://www.baidu.com",
            isActive = false,
            stackIndex = tabs.size
        )
        
        // 添加到容器
        container.addView(webView)
        tabs.add(tab)
        
        // 更新标签页位置
        updateTabPositions()
        
        // 加载URL
        webView.loadUrl(tab.url)
        
        // 通知监听器
        onTabCreatedListener?.invoke(tab)
        
        Log.d(TAG, "添加新标签页: ${tab.title}, 当前数量: ${tabs.size}")
        return tab
    }

    /**
     * 移除指定标签页
     */
    fun removeTab(tabId: String): Boolean {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return false
        
        val tab = tabs[tabIndex]
        container.removeView(tab.webView)
        tabs.removeAt(tabIndex)
        
        // 调整当前标签页索引
        if (currentTabIndex >= tabs.size) {
            currentTabIndex = max(0, tabs.size - 1)
        }
        
        // 更新标签页位置
        updateTabPositions()
        
        Log.d(TAG, "移除标签页: ${tab.title}, 当前数量: ${tabs.size}")
        return true
    }
    
    /**
     * 通过URL关闭标签页
     */
    fun closeTabByUrl(url: String): Boolean {
        val tabIndex = tabs.indexOfFirst { it.url == url }
        if (tabIndex == -1) return false
        
        val tab = tabs[tabIndex]
        container.removeView(tab.webView)
        tabs.removeAt(tabIndex)
        
        // 调整当前标签页索引
        if (currentTabIndex >= tabs.size) {
            currentTabIndex = max(0, tabs.size - 1)
        }
        
        // 更新标签页位置
        updateTabPositions()
        
        Log.d(TAG, "通过URL关闭标签页: ${tab.title}, URL: $url, 当前数量: ${tabs.size}")
        return true
    }

    /**
     * 切换到下一个标签页
     */
    fun switchToNextTab() {
        if (isAnimating || tabs.isEmpty()) return
        
        val nextIndex = (currentTabIndex + 1) % tabs.size
        if (nextIndex != currentTabIndex) {
            switchToTab(nextIndex)
        }
    }

    /**
     * 切换到上一个标签页
     */
    fun switchToPreviousTab() {
        if (isAnimating || tabs.isEmpty()) return
        
        val prevIndex = if (currentTabIndex == 0) tabs.size - 1 else currentTabIndex - 1
        if (prevIndex != currentTabIndex) {
            switchToTab(prevIndex)
        }
    }

    /**
     * 切换到指定标签页
     */
    fun switchToTab(targetIndex: Int) {
        if (isAnimating || targetIndex < 0 || targetIndex >= tabs.size || tabs.isEmpty()) {
            Log.w(TAG, "switchToTab: 无效参数或条件不满足。isAnimating=$isAnimating, targetIndex=$targetIndex, tabs.size=${tabs.size}")
            return
        }
        
        // 如果目标索引就是当前索引，不需要切换
        if (targetIndex == currentTabIndex) {
            Log.d(TAG, "目标标签页就是当前标签页，跳过切换")
            return
        }
        
        isAnimating = true
        val currentTab = tabs[currentTabIndex]
        val targetTab = tabs[targetIndex]
        
        Log.d(TAG, "开始切换标签页：从 ${currentTab.title} 到 ${targetTab.title}")
        
        // 创建动画集合
        val animatorSet = AnimatorSet()
        val animators = mutableListOf<Animator>()
        
        // 1. 当前标签页移到底部的动画
        val moveToBottomAnimator = createMoveToBottomAnimation(currentTab, tabs.size - 1)
        animators.add(moveToBottomAnimator)
        
        // 2. 目标标签页移到顶部的动画
        val moveToTopAnimator = createMoveToTopAnimation(targetTab, 0)
        animators.add(moveToTopAnimator)
        
        // 3. 其他标签页重新排列的动画
        val rearrangeAnimators = createRearrangeAnimations(currentTabIndex, targetIndex)
        animators.addAll(rearrangeAnimators)
        
        // 执行动画
        animatorSet.playTogether(animators)
        animatorSet.duration = ANIMATION_DURATION
        // 使用更平滑的插值器，让动画更自然
        animatorSet.interpolator = DecelerateInterpolator(1.5f)
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                Log.d(TAG, "标签页切换动画开始")
            }
            
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                
                // 重新排序标签页数组（只更新视觉位置，不改变数组顺序）
                reorderTabs(currentTabIndex, targetIndex)
                
                // 通知监听器
                onTabSwitchedListener?.invoke(targetTab, currentTabIndex)
                
                Log.d(TAG, "标签页切换完成，当前标签页: ${targetTab.title}, 索引: $currentTabIndex")
            }
            
            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                Log.d(TAG, "标签页切换动画被取消")
            }
        })
        
        animatorSet.start()
    }

    /**
     * 创建移到底部的动画
     */
    private fun createMoveToBottomAnimation(tab: WebViewTab, targetStackIndex: Int): Animator {
        val targetOffsetX = targetStackIndex * TAB_OFFSET_X
        val targetOffsetY = targetStackIndex * TAB_OFFSET_Y
        val targetScale = TAB_SCALE_FACTOR.pow(targetStackIndex)
        // 修复透明度计算：非激活页面保持适当透明度
        val targetAlpha = max(0.4f, 1f - (targetStackIndex * TAB_ALPHA_FACTOR))
        val targetElevation = (tabs.size - targetStackIndex + 10).toFloat()
        
        val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
        val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
        val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
        val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
        val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
        val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
        
        // 设置动画持续时间
        val duration = ANIMATION_DURATION
        animatorX.duration = duration
        animatorY.duration = duration
        animatorScaleX.duration = duration
        animatorScaleY.duration = duration
        animatorAlpha.duration = duration
        animatorElevation.duration = duration
        
        // 设置插值器
        val interpolator = DecelerateInterpolator(1.2f)
        animatorX.interpolator = interpolator
        animatorY.interpolator = interpolator
        animatorScaleX.interpolator = interpolator
        animatorScaleY.interpolator = interpolator
        animatorAlpha.interpolator = interpolator
        animatorElevation.interpolator = interpolator
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorY, animatorScaleX, animatorScaleY, animatorAlpha, animatorElevation)
        
        return animatorSet
    }

    /**
     * 创建移到顶部的动画
     */
    private fun createMoveToTopAnimation(tab: WebViewTab, targetStackIndex: Int): Animator {
        val targetOffsetX = targetStackIndex * TAB_OFFSET_X
        val targetOffsetY = targetStackIndex * TAB_OFFSET_Y
        val targetScale = TAB_SCALE_FACTOR.pow(targetStackIndex)
        // 修复透明度计算：激活页面完全不透明
        val targetAlpha = 1.0f
        val targetElevation = (tabs.size + 20).toFloat()
        
        val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
        val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
        val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
        val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
        val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
        val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
        
        // 设置动画持续时间
        val duration = ANIMATION_DURATION
        animatorX.duration = duration
        animatorY.duration = duration
        animatorScaleX.duration = duration
        animatorScaleY.duration = duration
        animatorAlpha.duration = duration
        animatorElevation.duration = duration
        
        // 设置插值器
        val interpolator = DecelerateInterpolator(1.2f)
        animatorX.interpolator = interpolator
        animatorY.interpolator = interpolator
        animatorScaleX.interpolator = interpolator
        animatorScaleY.interpolator = interpolator
        animatorAlpha.interpolator = interpolator
        animatorElevation.interpolator = interpolator
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorY, animatorScaleX, animatorScaleY, animatorAlpha, animatorElevation)
        
        return animatorSet
    }

    /**
     * 创建重新排列的动画
     */
    private fun createRearrangeAnimations(currentIndex: Int, targetIndex: Int): List<Animator> {
        val animators = mutableListOf<Animator>()
        
        tabs.forEachIndexed { index, tab ->
            if (index != currentIndex && index != targetIndex) {
                // 计算新的层叠位置：基于与目标标签页的距离
                val distanceFromTarget = abs(index - targetIndex)
                val newStackIndex = distanceFromTarget
                
                val targetOffsetX = newStackIndex * TAB_OFFSET_X
                val targetOffsetY = newStackIndex * TAB_OFFSET_Y
                val targetScale = TAB_SCALE_FACTOR.pow(newStackIndex)
                val targetAlpha = max(0.4f, 1f - (newStackIndex * TAB_ALPHA_FACTOR))
                val targetElevation = (tabs.size - newStackIndex + 10).toFloat()
                
                val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
                val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
                val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
                val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
                val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
                val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
                
                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animatorX, animatorY, animatorScaleX, animatorScaleY, animatorAlpha, animatorElevation)
                animators.add(animatorSet)
            }
        }
        
        return animators
    }

    /**
     * 重新排序标签页数组
     */
    private fun reorderTabs(currentIndex: Int, targetIndex: Int) {
        // 检查数组边界，避免越界异常
        if (tabs.isEmpty() || currentIndex < 0 || currentIndex >= tabs.size || 
            targetIndex < 0 || targetIndex >= tabs.size) {
            Log.w(TAG, "reorderTabs: 索引超出边界，跳过重新排序。tabs.size=${tabs.size}, currentIndex=$currentIndex, targetIndex=$targetIndex")
            return
        }
        
        // 如果只有一个标签页，不需要重新排序
        if (tabs.size == 1) {
            Log.d(TAG, "只有一个标签页，跳过重新排序")
            return
        }
        
        // 关键修复：在纸堆模式中，不要重新排序数组，只更新当前索引和视觉位置
        // 这样可以保持标签页的原始顺序，确保StackedCardPreview的索引对应正确
        Log.d(TAG, "纸堆模式：保持标签页数组顺序不变，更新当前索引: $currentIndex -> $targetIndex")
        
        // 更新当前标签页索引
        currentTabIndex = targetIndex
        
        // 更新所有标签页的位置
        updateTabPositions()
        
        Log.d(TAG, "纸堆模式标签页切换完成，当前激活索引: $currentTabIndex")
    }

    /**
     * 更新所有标签页的位置 - 实现真正的纵向叠加效果
     */
    private fun updateTabPositions() {
        tabs.forEachIndexed { index, tab ->
            // 计算层叠位置：当前激活的标签页在最上面，其他按距离排序
            val distanceFromCurrent = abs(index - currentTabIndex)
            val stackIndex = distanceFromCurrent
            
            val offsetX = stackIndex * TAB_OFFSET_X
            val offsetY = stackIndex * TAB_OFFSET_Y
            val scale = TAB_SCALE_FACTOR.pow(stackIndex)
            
            // 修复透明度问题：当前激活的页面完全不透明，其他页面按层级降低
            val alpha = if (index == currentTabIndex) 1.0f else max(0.4f, 1f - (stackIndex * TAB_ALPHA_FACTOR))
            
            // 设置变换属性
            tab.webView.translationX = offsetX
            tab.webView.translationY = offsetY
            tab.webView.scaleX = scale
            tab.webView.scaleY = scale
            tab.webView.alpha = alpha
            
            // 设置层级：当前激活的标签页在最上面，确保不重叠
            tab.webView.elevation = if (index == currentTabIndex) (tabs.size + 20).toFloat() else (tabs.size - stackIndex + 10).toFloat()
            
            // 更新标签页状态
            tab.isActive = (index == currentTabIndex)
            tab.stackIndex = stackIndex
            
            Log.d(TAG, "标签页 ${tab.title}: index=$index, currentTabIndex=$currentTabIndex, stackIndex=$stackIndex, offsetX=$offsetX, offsetY=$offsetY, scale=$scale, alpha=$alpha, elevation=${tab.webView.elevation}")
        }
    }

    /**
     * 获取当前标签页
     */
    fun getCurrentTab(): WebViewTab? {
        return tabs.getOrNull(currentTabIndex)
    }

    /**
     * 获取标签页数量
     */
    fun getTabCount(): Int = tabs.size

    /**
     * 获取所有标签页数据
     */
    fun getAllTabs(): List<WebViewTab> {
        return tabs.toList()
    }

    /**
     * 设置标签页创建监听器
     */
    fun setOnTabCreatedListener(listener: (WebViewTab) -> Unit) {
        onTabCreatedListener = listener
    }

    /**
     * 设置标签页切换监听器
     */
    fun setOnTabSwitchedListener(listener: (WebViewTab, Int) -> Unit) {
        onTabSwitchedListener = listener
    }

    /**
     * 清理所有标签页
     */
    fun cleanup() {
        tabs.forEach { tab ->
            container.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()
        currentTabIndex = 0
    }

    /**
     * 处理触摸事件
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果没有标签页，不处理触摸事件
        if (tabs.isEmpty()) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
                touchDownTime = System.currentTimeMillis()
                Log.d(TAG, "触摸开始: x=${event.x}, y=${event.y}")
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (!isSwipeStarted) {
                    val deltaX = abs(event.x - swipeStartX)
                    val deltaY = abs(event.y - swipeStartY)
                    
                    // 进一步降低滑动检测阈值，提高响应性
                    if (deltaX > 15f || deltaY > 15f) {
                        isSwipeStarted = true
                        // 确定滑动方向 - 优化方向判断逻辑
                        swipeDirection = if (deltaX > deltaY * 1.3f) {
                            SwipeDirection.HORIZONTAL
                        } else if (deltaY > deltaX * 1.1f) {
                            SwipeDirection.VERTICAL
                        } else {
                            SwipeDirection.NONE
                        }
                        
                        Log.d(TAG, "滑动开始: 方向=${swipeDirection}, deltaX=$deltaX, deltaY=$deltaY")
                        
                        // 如果是横向滑动，阻止WebView的滚动
                        if (swipeDirection == SwipeDirection.HORIZONTAL) {
                            return true
                        }
                    }
                } else if (swipeDirection == SwipeDirection.HORIZONTAL) {
                    // 横向滑动过程中，继续阻止WebView滚动
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                val currentTime = System.currentTimeMillis()
                val touchDuration = currentTime - touchDownTime
                
                if (isSwipeStarted && swipeDirection == SwipeDirection.HORIZONTAL) {
                    val deltaX = event.x - swipeStartX
                    val deltaY = event.y - swipeStartY
                    
                    Log.d(TAG, "滑动结束: deltaX=$deltaX, deltaY=$deltaY, 阈值=$SWIPE_THRESHOLD, 持续时间=${touchDuration}ms")
                    
                    // 检查是否满足滑动条件 - 进一步降低阈值提高响应性
                    val effectiveThreshold = if (touchDuration < 300) SWIPE_THRESHOLD * 0.5f else SWIPE_THRESHOLD * 0.7f
                    if (abs(deltaX) > effectiveThreshold && abs(deltaX) > abs(deltaY) * 1.1f) {
                        if (deltaX > 0) {
                            // 右滑 - 切换到上一个标签页
                            Log.d(TAG, "右滑检测到，切换到上一个标签页")
                            switchToPreviousTab()
                        } else {
                            // 左滑 - 切换到下一个标签页
                            Log.d(TAG, "左滑检测到，切换到下一个标签页")
                            switchToNextTab()
                        }
                        return true
                    }
                }
                
                // 重置状态
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
                lastTouchTime = currentTime
            }
            
            MotionEvent.ACTION_CANCEL -> {
                // 重置状态
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
            }
        }
        
        // 只有在非横向滑动时才传递给WebView
        return if (swipeDirection == SwipeDirection.HORIZONTAL) {
            true
        } else {
            false
        }
    }
    
    /**
     * 检查是否在文本选择区域
     */
    private fun isInTextSelectionArea(event: MotionEvent): Boolean {
        val currentTab = getCurrentTab()
        if (currentTab != null) {
            val webView = currentTab.webView
            
            // 检查触摸位置是否在文本区域内
            val hitTestResult = webView.hitTestResult
            val isInTextArea = hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                              hitTestResult.type == WebView.HitTestResult.EDIT_TEXT_TYPE ||
                              hitTestResult.type == WebView.HitTestResult.UNKNOWN_TYPE
            
            // 如果已经在文本选择状态，继续保持
            if (isTextSelectionActive) {
                return true
            }
            
            return isInTextArea
        }
        return false
    }
    
    /**
     * 滑动方向枚举
     */
    private enum class SwipeDirection {
        NONE, HORIZONTAL, VERTICAL
    }

    /**
     * 标签页WebView类
     */
    /**
     * 处理特殊 scheme URL（如 intent://、douban://、clash:// 等）
     * 直接启动Intent，让系统显示应用选择对话框（类似 Chrome）
     * @param url URL 字符串
     * @param view WebView 实例
     * @return true 表示已处理，false 表示非特殊 scheme
     */
    private fun handleSpecialSchemeUrl(url: String, view: WebView?): Boolean {
        if (url.isBlank()) {
            Log.d(TAG, "handleSpecialSchemeUrl: URL 为空")
            return false
        }
        
        val lower = url.lowercase()
        
        // 检查是否为 HTTP/HTTPS，这些应该在 WebView 中加载
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return false
        }
        
        // 检查是否为特殊 scheme
        val isSpecialScheme = when {
            lower.startsWith("intent://") -> true
            lower.startsWith("clash://") -> true
            lower.startsWith("douban://") -> true
            lower.startsWith("baidumap://") -> true
            lower.startsWith("amap://") -> true
            lower.startsWith("alipay://") -> true
            lower.startsWith("wechat://") -> true
            lower.startsWith("weixin://") -> true
            lower.startsWith("qq://") -> true
            lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://") && 
            !lower.startsWith("file://") && !lower.startsWith("javascript:") -> true
            else -> false
        }
        
        if (!isSpecialScheme) {
            return false
        }
        
        // 对于特殊 scheme，直接启动Intent（类似 Chrome，让系统显示对话框）
        Log.d(TAG, "检测到特殊 scheme URL: $url，直接启动Intent")
        
        // 在主线程启动Intent
        if (context is android.app.Activity) {
            context.runOnUiThread {
                launchSchemeUrlDirectly(url)
            }
        } else {
            // 如果不是 Activity，尝试直接启动
            try {
                launchSchemeUrlDirectly(url)
            } catch (e: Exception) {
                Log.e(TAG, "处理特殊 scheme 失败: $url", e)
            }
        }
        
        return true // 返回 true 表示已处理，阻止在 WebView 中加载
    }
    
    /**
     * 直接启动 scheme URL（类似 Chrome，让系统显示应用选择对话框）
     */
    private fun launchSchemeUrlDirectly(schemeUrl: String) {
        try {
            val packageManager = context.packageManager
            
            if (schemeUrl.startsWith("intent://")) {
                // 处理 intent:// URL
                val intent = android.content.Intent.parseUri(schemeUrl, android.content.Intent.URI_INTENT_SCHEME)
                intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 针对 clash:// 指定优先包
                val data = intent.dataString
                if (intent.`package` == null && data != null && data.startsWith("clash://")) {
                    val clashPackages = listOf("com.github.kr328.clash", "com.github.metacubex.clash")
                    for (pkg in clashPackages) {
                        try {
                            packageManager.getPackageInfo(pkg, 0)
                            intent.`package` = pkg
                            break
                        } catch (_: Exception) { }
                    }
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "直接启动 intent:// 链接成功: $schemeUrl")
                } else {
                    // 尝试 fallback URL
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (!fallback.isNullOrBlank()) {
                        Log.d(TAG, "使用 fallback URL: $fallback")
                    } else {
                        android.widget.Toast.makeText(context, "未找到可处理的应用", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // 处理普通 scheme URL
                val uri = android.net.Uri.parse(schemeUrl)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 针对 clash:// 指定优先包
                if (schemeUrl.startsWith("clash://")) {
                    val clashPackages = listOf("com.github.kr328.clash", "com.github.metacubex.clash")
                    for (pkg in clashPackages) {
                        try {
                            packageManager.getPackageInfo(pkg, 0)
                            intent.`package` = pkg
                            break
                        } catch (_: Exception) { }
                    }
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    // 直接启动，让系统显示应用选择对话框（类似 Chrome）
                    context.startActivity(intent)
                    Log.d(TAG, "直接启动 scheme 链接成功: $schemeUrl")
                } else {
                    android.widget.Toast.makeText(context, "未找到可处理的应用", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "直接启动 scheme 链接失败: $schemeUrl", e)
            android.widget.Toast.makeText(context, "打开应用失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private inner class PaperWebView(context: Context) : WebView(context) {
        var stackIndex = 0
        
        init {
            setupTabStyle()
        }
        
        private fun setupTabStyle() {
            // 设置标签页样式 - 完全透明，避免任何蒙版效果
            setBackgroundColor(Color.TRANSPARENT)
            setBackground(null)
            
            // 移除阴影和边框
            ViewCompat.setElevation(this, 0f)
            
            // 设置圆角
            clipToOutline = false
            
            // 设置初始变换
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            
            // 使用硬件加速，避免软件渲染的阴影
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        
        fun setupWebView() {
            // 设置WebView完全透明
            setBackgroundColor(Color.TRANSPARENT)
            setBackground(null)
            setLayerType(LAYER_TYPE_HARDWARE, null)
            
            // 设置触摸监听器来检测文本选择
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 检测是否在文本区域
                        val hitTestResult = hitTestResult
                        isTextSelectionActive = hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                               hitTestResult.type == WebView.HitTestResult.EDIT_TEXT_TYPE ||
                                               hitTestResult.type == WebView.HitTestResult.UNKNOWN_TYPE
                    }
                    MotionEvent.ACTION_UP -> {
                        // 延迟重置文本选择状态
                        postDelayed({
                            isTextSelectionActive = false
                        }, 1000)
                    }
                }
                false // 不拦截事件，让WebView正常处理
            }
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 性能优化
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                databaseEnabled = true
                
                // 用户代理
                userAgentString = WebViewConstants.MOBILE_USER_AGENT
                
                // 移动端优化
                textZoom = 100
                minimumFontSize = 8
                setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)
            }
            
            // 设置WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    Log.d(TAG, "PaperWebView URL加载拦截: $url")
                    
                    if (url != null) {
                        return handleSpecialSchemeUrl(url, view)
                    }
                    return false
                }
                
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    Log.d(TAG, "PaperWebView URL加载拦截 (legacy): $url")
                    
                    if (url != null) {
                        return handleSpecialSchemeUrl(url, view)
                    }
                    return false
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // 注入viewport meta标签
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                var viewportMeta = document.querySelector('meta[name="viewport"]');
                                if (viewportMeta) {
                                    viewportMeta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');
                                } else {
                                    var meta = document.createElement('meta');
                                    meta.name = 'viewport';
                                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                                    document.head.appendChild(meta);
                                }
                                document.documentElement.style.setProperty('--mobile-viewport', '1');
                            } catch (e) {
                                console.error('Failed to inject viewport meta tag:', e);
                            }
                        })();
                    """.trimIndent(), null)
                }
                
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    val errorUrl = request?.url?.toString()
                    val errorCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        error?.errorCode
                    } else {
                        -1
                    }
                    val errorDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        error?.description?.toString()
                    } else {
                        "Unknown error"
                    }
                    
                    Log.e(TAG, "PaperWebView加载错误: $errorDescription, URL: $errorUrl, ErrorCode: $errorCode")
                    
                    // 检查是否为 ERR_UNKNOWN_URL_SCHEME 错误，且 URL 是特殊 scheme
                    if (request?.isForMainFrame == true && errorUrl != null) {
                        if (errorCode == -2 || errorDescription?.contains("ERR_UNKNOWN_URL_SCHEME") == true || 
                            errorDescription?.contains("net::ERR_UNKNOWN_URL_SCHEME") == true) {
                            // 检查是否为特殊 scheme
                            val lower = errorUrl.lowercase()
                            val isSpecialScheme = lower.startsWith("intent://") || 
                                                 lower.startsWith("clash://") ||
                                                 lower.startsWith("douban://") ||
                                                 (lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://"))
                            
                            if (isSpecialScheme) {
                                // 特殊 scheme 导致的错误，直接启动Intent（类似 Chrome）
                                Log.d(TAG, "检测到特殊 scheme 错误，直接启动Intent: $errorUrl")
                                handleSpecialSchemeUrl(errorUrl, view)
                                // 不调用 super.onReceivedError，避免显示错误页面和可能的循环
                                return
                            }
                        }
                    }
                    
                    super.onReceivedError(view, request, error)
                }
                
                @Deprecated("Deprecated in Java")
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "PaperWebView加载错误 (legacy): $description, URL: $failingUrl, ErrorCode: $errorCode")
                    
                    // 检查是否为 ERR_UNKNOWN_URL_SCHEME 错误（错误代码 -2），且 URL 是特殊 scheme
                    if (errorCode == -2 || description?.contains("ERR_UNKNOWN_URL_SCHEME") == true || 
                        description?.contains("net::ERR_UNKNOWN_URL_SCHEME") == true) {
                        if (failingUrl != null) {
                            val lower = failingUrl.lowercase()
                            val isSpecialScheme = lower.startsWith("intent://") || 
                                                 lower.startsWith("clash://") ||
                                                 lower.startsWith("douban://") ||
                                                 (lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://"))
                            
                            if (isSpecialScheme) {
                                // 特殊 scheme 导致的错误，直接启动Intent（类似 Chrome）
                                Log.d(TAG, "检测到特殊 scheme 错误 (legacy)，直接启动Intent: $failingUrl")
                                handleSpecialSchemeUrl(failingUrl, view)
                                // 不调用 super.onReceivedError，避免显示错误页面和可能的循环
                                return
                            }
                        }
                    }
                    
                    super.onReceivedError(view, errorCode, description, failingUrl)
                }
            }
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 移除阴影和边框绘制，避免灰色蒙版效果
        }
    }
}
