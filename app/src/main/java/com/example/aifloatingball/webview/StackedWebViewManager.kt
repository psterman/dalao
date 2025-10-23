package com.example.aifloatingball.webview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Matrix
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import com.example.aifloatingball.utils.WebViewConstants
import com.example.aifloatingball.utils.WebViewMobileOptimizer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

/**
 * WebView叠加堆叠管理器
 * 实现WebView垂直堆叠效果，用户通过左右滑动翻阅下方的WebView
 */
class StackedWebViewManager(
    private val context: Context,
    private val container: FrameLayout
) {
    
    companion object {
        private const val TAG = "StackedWebViewManager"
        private const val MAX_WEBVIEWS = 8
        private const val SWIPE_THRESHOLD = 80f // 滑动阈值
        private const val ANIMATION_DURATION = 400L // 动画持续时间
        private const val STACK_OFFSET_Y = 20f // 堆叠Y轴偏移
        private const val STACK_OFFSET_X = 15f // 堆叠X轴偏移
        private const val STACK_SCALE_FACTOR = 0.95f // 堆叠缩放因子
        private const val STACK_ROTATION = 2f // 堆叠旋转角度
    }
    
    // WebView数据类
    data class StackedWebViewData(
        val id: Long,
        val webView: WebView,
        var url: String? = null,
        var title: String = "新页面",
        var stackIndex: Int = 0,
        var isActive: Boolean = false
    )
    
    // 核心组件
    private val webViews = mutableListOf<StackedWebViewData>()
    private var currentIndex = 0
    private var isAnimating = false
    
    // 触摸处理
    private var initialX = 0f
    private var initialY = 0f
    private var isHorizontalSwipe = false
    private var lastSwipeDirection = 0 // 1: 右滑, -1: 左滑
    
    // 监听器
    private var onWebViewChangeListener: OnWebViewChangeListener? = null
    
    /**
     * WebView变化监听器
     */
    interface OnWebViewChangeListener {
        fun onWebViewAdded(webViewData: StackedWebViewData, index: Int)
        fun onWebViewRemoved(webViewData: StackedWebViewData, index: Int)
        fun onWebViewSwitched(webViewData: StackedWebViewData, index: Int)
        fun onStackChanged(fromIndex: Int, toIndex: Int)
    }
    
    init {
        setupTouchHandling()
    }
    
    /**
     * 设置触摸处理
     */
    private fun setupTouchHandling() {
        container.setOnTouchListener { view, event ->
            handleTouchEvent(view, event)
        }
    }
    
    /**
     * 处理触摸事件
     */
    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        if (isAnimating || webViews.isEmpty()) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                isHorizontalSwipe = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = abs(event.x - initialX)
                val deltaY = abs(event.y - initialY)
                
                // 判断是否为水平滑动
                if (deltaX > SWIPE_THRESHOLD && deltaX > deltaY) {
                    isHorizontalSwipe = true
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                if (isHorizontalSwipe) {
                    val deltaX = event.x - initialX
                    val deltaY = event.y - initialY
                    
                    // 根据滑动方向切换WebView
                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > SWIPE_THRESHOLD) {
                        if (deltaX > 0) {
                            // 向右滑动，显示上一个WebView
                            showPreviousWebView()
                            lastSwipeDirection = 1
                        } else {
                            // 向左滑动，显示下一个WebView
                            showNextWebView()
                            lastSwipeDirection = -1
                        }
                    }
                }
                isHorizontalSwipe = false
                return true
            }
        }
        return false
    }
    
    /**
     * 添加新的WebView到堆栈
     */
    fun addWebView(url: String? = null): StackedWebViewData {
        if (webViews.size >= MAX_WEBVIEWS) {
            Log.w(TAG, "已达到最大WebView数量限制: $MAX_WEBVIEWS")
            return webViews.last()
        }
        
        val webView = createWebView()
        val webViewData = StackedWebViewData(
            id = System.currentTimeMillis(),
            webView = webView,
            url = url,
            title = url ?: "新页面",
            stackIndex = webViews.size,
            isActive = webViews.isEmpty() // 第一个WebView默认为活跃状态
        )
        
        // 设置WebView回调
        setupWebViewCallbacks(webView, webViewData)
        
        // 添加到堆栈
        webViews.add(webViewData)
        addWebViewToStack(webViewData)
        
        // 如果是第一个WebView，设置为当前活跃
        if (webViews.size == 1) {
            currentIndex = 0
            updateStackLayout()
        }
        
        // 加载URL
        url?.let { webView.loadUrl(it) }
        
        onWebViewChangeListener?.onWebViewAdded(webViewData, webViewData.stackIndex)
        Log.d(TAG, "添加新WebView到堆栈: $url")
        
        return webViewData
    }
    
    /**
     * 创建WebView
     */
    private fun createWebView(): WebView {
        return WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // 应用移动端优化设置
            WebViewMobileOptimizer.applyMobileOptimizations(this, context)
            
            // 设置WebView回调
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "页面开始加载: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "页面加载完成: $url")
                    
                    // 更新标题
                    view?.title?.let { title ->
                        updateWebViewTitle(view, title)
                    }
                }
            }
            
            // 设置WebChromeClient
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let { updateWebViewTitle(view!!, it) }
                }
            }
        }
    }
    
    /**
     * 设置WebView回调
     */
    private fun setupWebViewCallbacks(webView: WebView, webViewData: StackedWebViewData) {
        // 可以在这里添加更多的WebView回调处理
    }
    
    /**
     * 将WebView添加到堆栈
     */
    private fun addWebViewToStack(webViewData: StackedWebViewData) {
        container.addView(webViewData.webView)
        Log.d(TAG, "WebView添加到堆栈，索引: ${webViewData.stackIndex}")
    }
    
    /**
     * 显示下一个WebView
     */
    private fun showNextWebView() {
        if (currentIndex < webViews.size - 1 && !isAnimating) {
            val fromIndex = currentIndex
            val toIndex = currentIndex + 1
            
            animateStackTransition(fromIndex, toIndex, false)
            Log.d(TAG, "显示下一个WebView: $fromIndex -> $toIndex")
        }
    }
    
    /**
     * 显示上一个WebView
     */
    private fun showPreviousWebView() {
        if (currentIndex > 0 && !isAnimating) {
            val fromIndex = currentIndex
            val toIndex = currentIndex - 1
            
            animateStackTransition(fromIndex, toIndex, true)
            Log.d(TAG, "显示上一个WebView: $fromIndex -> $toIndex")
        }
    }
    
    /**
     * 动画切换WebView堆栈
     */
    private fun animateStackTransition(fromIndex: Int, toIndex: Int, isReversing: Boolean) {
        if (isAnimating) return
        
        isAnimating = true
        val fromWebView = webViews[fromIndex].webView
        val toWebView = webViews[toIndex].webView
        
        // 设置动画参数
        val containerWidth = container.width
        val containerHeight = container.height
        
        // 计算动画起始和结束位置
        val fromStartX = fromWebView.translationX
        val toStartX = toWebView.translationX
        
        val fromEndX = if (isReversing) containerWidth.toFloat() else -containerWidth.toFloat()
        val toEndX = if (isReversing) -containerWidth.toFloat() else containerWidth.toFloat()
        
        // 创建水平滑动动画
        val fromAnimator = ValueAnimator.ofFloat(fromStartX, fromEndX).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animation ->
                fromWebView.translationX = animation.animatedValue as Float
                // 添加透明度变化
                val progress = animation.animatedFraction
                fromWebView.alpha = 1f - progress * 0.3f
            }
        }
        
        val toAnimator = ValueAnimator.ofFloat(toStartX, toEndX).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animation ->
                toWebView.translationX = animation.animatedValue as Float
                // 添加透明度变化
                val progress = animation.animatedFraction
                toWebView.alpha = 0.7f + progress * 0.3f
            }
        }
        
        // 启动动画
        fromAnimator.start()
        toAnimator.start()
        
        // 动画完成后更新状态
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 重置translationX
            fromWebView.translationX = 0f
            toWebView.translationX = 0f
            
            // 更新当前索引
            currentIndex = toIndex
            
            // 更新堆栈布局
            updateStackLayout()
            
            // 通知监听器
            onWebViewChangeListener?.onStackChanged(fromIndex, toIndex)
            onWebViewChangeListener?.onWebViewSwitched(webViews[currentIndex], currentIndex)
            
            isAnimating = false
        }, ANIMATION_DURATION)
    }
    
    /**
     * 更新堆栈布局
     */
    private fun updateStackLayout() {
        webViews.forEachIndexed { index, webViewData ->
            val webView = webViewData.webView
            val stackDepth = index - currentIndex
            
            when {
                stackDepth == 0 -> {
                    // 当前活跃的WebView
                    webView.translationX = 0f
                    webView.translationY = 0f
                    webView.scaleX = 1f
                    webView.scaleY = 1f
                    webView.rotation = 0f
                    webView.alpha = 1f
                    webView.elevation = 10f
                    webViewData.isActive = true
                }
                stackDepth > 0 -> {
                    // 下方的WebView
                    val depth = minOf(stackDepth, 3) // 最多显示3层
                    webView.translationX = STACK_OFFSET_X * depth
                    webView.translationY = STACK_OFFSET_Y * depth
                    webView.scaleX = STACK_SCALE_FACTOR.pow(depth)
                    webView.scaleY = STACK_SCALE_FACTOR.pow(depth)
                    webView.rotation = STACK_ROTATION * depth
                    webView.alpha = 1f - (depth * 0.1f)
                    webView.elevation = 10f - depth
                    webViewData.isActive = false
                }
                stackDepth < 0 -> {
                    // 上方的WebView（隐藏）
                    webView.translationX = 0f
                    webView.translationY = 0f
                    webView.scaleX = 1f
                    webView.scaleY = 1f
                    webView.rotation = 0f
                    webView.alpha = 0f
                    webView.elevation = 0f
                    webViewData.isActive = false
                }
            }
        }
        
        Log.d(TAG, "更新堆栈布局，当前索引: $currentIndex")
    }
    
    /**
     * 更新WebView标题
     */
    private fun updateWebViewTitle(webView: WebView, title: String) {
        val webViewData = webViews.find { it.webView == webView }
        webViewData?.let {
            it.title = title
            Log.d(TAG, "更新WebView标题: $title")
        }
    }
    
    /**
     * 获取当前WebView
     */
    fun getCurrentWebView(): StackedWebViewData? {
        return webViews.getOrNull(currentIndex)
    }
    
    /**
     * 获取所有WebView
     */
    fun getAllWebViews(): List<StackedWebViewData> = webViews.toList()
    
    /**
     * 移除WebView
     */
    fun removeWebView(index: Int) {
        if (index < 0 || index >= webViews.size) return
        
        val webViewData = webViews.removeAt(index)
        container.removeView(webViewData.webView)
        
        // 调整当前索引
        if (currentIndex >= webViews.size) {
            currentIndex = webViews.size - 1
        } else if (currentIndex > index) {
            currentIndex--
        }
        
        // 更新堆栈布局
        updateStackLayout()
        
        onWebViewChangeListener?.onWebViewRemoved(webViewData, index)
        Log.d(TAG, "移除WebView: $index")
    }
    
    /**
     * 移除当前WebView
     */
    fun removeCurrentWebView() {
        removeWebView(currentIndex)
    }
    
    /**
     * 设置监听器
     */
    fun setOnWebViewChangeListener(listener: OnWebViewChangeListener) {
        this.onWebViewChangeListener = listener
    }
    
    /**
     * 获取当前堆栈信息
     */
    fun getStackInfo(): String {
        return "${currentIndex + 1}/${webViews.size}"
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        webViews.forEach { it.webView.destroy() }
        webViews.clear()
        container.removeAllViews()
    }
}
