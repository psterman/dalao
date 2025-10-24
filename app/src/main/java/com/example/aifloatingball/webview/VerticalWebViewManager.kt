package com.example.aifloatingball.webview

import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.utils.WebViewConstants
import com.example.aifloatingball.utils.WebViewMobileOptimizer
import kotlin.math.abs

/**
 * 纵向WebView组合管理器
 * 支持多个WebView垂直排列，用户可以通过左右滑动来上下切换WebView位置
 */
class VerticalWebViewManager(
    private val context: Context,
    private val container: FrameLayout
) {
    
    companion object {
        private const val TAG = "VerticalWebViewManager"
        private const val MAX_WEBVIEWS = 5
        private const val SWIPE_THRESHOLD = 100f // 滑动阈值
        private const val ANIMATION_DURATION = 300L // 动画持续时间
    }
    
    // WebView数据类
    data class VerticalWebViewData(
        val id: Long,
        val webView: WebView,
        var url: String? = null,
        var title: String = "新页面",
        var position: Int = 0
    )
    
    // 核心组件
    private val webViews = mutableListOf<VerticalWebViewData>()
    private var currentPosition = 0
    private var mainLayout: LinearLayout? = null
    
    // 触摸处理
    private var initialX = 0f
    private var initialY = 0f
    private var isHorizontalSwipe = false
    private var isAnimating = false
    
    // 监听器
    private var onWebViewChangeListener: OnWebViewChangeListener? = null
    
    /**
     * WebView变化监听器
     */
    interface OnWebViewChangeListener {
        fun onWebViewAdded(webViewData: VerticalWebViewData, position: Int)
        fun onWebViewRemoved(webViewData: VerticalWebViewData, position: Int)
        fun onWebViewSwitched(webViewData: VerticalWebViewData, position: Int)
        fun onWebViewPositionChanged(fromPosition: Int, toPosition: Int)
    }
    
    init {
        setupMainLayout()
        setupTouchHandling()
    }
    
    /**
     * 设置主布局
     */
    private fun setupMainLayout() {
        mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(mainLayout)
        Log.d(TAG, "主布局设置完成")
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
                    
                    // 根据滑动方向切换WebView位置
                    if (abs(deltaX) > abs(deltaY) && abs(deltaX) > SWIPE_THRESHOLD) {
                        if (deltaX > 0) {
                            // 向右滑动，向上移动WebView
                            moveWebViewUp()
                        } else {
                            // 向左滑动，向下移动WebView
                            moveWebViewDown()
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
     * 添加新的WebView
     */
    fun addWebView(url: String? = null): VerticalWebViewData {
        if (webViews.size >= MAX_WEBVIEWS) {
            Log.w(TAG, "已达到最大WebView数量限制: $MAX_WEBVIEWS")
            return webViews.last()
        }
        
        val webView = createWebView()
        val webViewData = VerticalWebViewData(
            id = System.currentTimeMillis(),
            webView = webView,
            url = url,
            title = url ?: "新页面",
            position = webViews.size
        )
        
        // 设置WebView回调
        setupWebViewCallbacks(webView, webViewData)
        
        // 添加到列表和布局
        webViews.add(webViewData)
        addWebViewToLayout(webViewData)
        
        // 加载URL
        url?.let { webView.loadUrl(it) }
        
        onWebViewChangeListener?.onWebViewAdded(webViewData, webViewData.position)
        Log.d(TAG, "添加新WebView: $url")
        
        return webViewData
    }
    
    /**
     * 创建WebView
     */
    private fun createWebView(): WebView {
        return WebView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                weight = 1f
            }
            
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
    private fun setupWebViewCallbacks(webView: WebView, webViewData: VerticalWebViewData) {
        // 可以在这里添加更多的WebView回调处理
    }
    
    /**
     * 将WebView添加到布局
     */
    private fun addWebViewToLayout(webViewData: VerticalWebViewData) {
        mainLayout?.addView(webViewData.webView)
        Log.d(TAG, "WebView添加到布局，位置: ${webViewData.position}")
    }
    
    /**
     * 向上移动WebView
     */
    private fun moveWebViewUp() {
        if (currentPosition > 0 && !isAnimating) {
            val fromPosition = currentPosition
            val toPosition = currentPosition - 1
            
            animateWebViewPositionChange(fromPosition, toPosition)
            Log.d(TAG, "向上移动WebView: $fromPosition -> $toPosition")
        }
    }
    
    /**
     * 向下移动WebView
     */
    private fun moveWebViewDown() {
        if (currentPosition < webViews.size - 1 && !isAnimating) {
            val fromPosition = currentPosition
            val toPosition = currentPosition + 1
            
            animateWebViewPositionChange(fromPosition, toPosition)
            Log.d(TAG, "向下移动WebView: $fromPosition -> $toPosition")
        }
    }
    
    /**
     * 动画切换WebView位置
     */
    private fun animateWebViewPositionChange(fromPosition: Int, toPosition: Int) {
        if (isAnimating) return
        
        isAnimating = true
        val fromWebView = webViews[fromPosition].webView
        val toWebView = webViews[toPosition].webView
        
        val containerHeight = container.height
        val fromStartY = fromWebView.translationY
        val toStartY = toWebView.translationY
        
        val fromEndY = if (fromPosition < toPosition) -containerHeight.toFloat() else containerHeight.toFloat()
        val toEndY = if (fromPosition < toPosition) containerHeight.toFloat() else -containerHeight.toFloat()
        
        // 创建动画
        val fromAnimator = ValueAnimator.ofFloat(fromStartY, fromEndY).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animation ->
                fromWebView.translationY = animation.animatedValue as Float
            }
        }
        
        val toAnimator = ValueAnimator.ofFloat(toStartY, toEndY).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animation ->
                toWebView.translationY = animation.animatedValue as Float
            }
        }
        
        // 启动动画
        fromAnimator.start()
        toAnimator.start()
        
        // 动画完成后更新位置
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 重置translationY
            fromWebView.translationY = 0f
            toWebView.translationY = 0f
            
            // 更新WebView在布局中的位置
            swapWebViewsInLayout(fromPosition, toPosition)
            
            // 更新数据
            currentPosition = toPosition
            updateWebViewPositions()
            
            // 通知监听器
            onWebViewChangeListener?.onWebViewPositionChanged(fromPosition, toPosition)
            onWebViewChangeListener?.onWebViewSwitched(webViews[currentPosition], currentPosition)
            
            isAnimating = false
        }, ANIMATION_DURATION)
    }
    
    /**
     * 在布局中交换WebView位置
     */
    private fun swapWebViewsInLayout(fromPosition: Int, toPosition: Int) {
        mainLayout?.let { layout ->
            val fromView = layout.getChildAt(fromPosition)
            val toView = layout.getChildAt(toPosition)
            
            // 移除视图
            layout.removeViewAt(fromPosition)
            layout.removeViewAt(if (toPosition > fromPosition) toPosition - 1 else toPosition)
            
            // 重新添加视图
            if (toPosition > fromPosition) {
                layout.addView(toView, fromPosition)
                layout.addView(fromView, toPosition)
            } else {
                layout.addView(fromView, toPosition)
                layout.addView(toView, fromPosition)
            }
        }
    }
    
    /**
     * 更新WebView位置信息
     */
    private fun updateWebViewPositions() {
        webViews.forEachIndexed { index, webViewData ->
            webViewData.position = index
        }
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
    fun getCurrentWebView(): VerticalWebViewData? {
        return webViews.getOrNull(currentPosition)
    }
    
    /**
     * 获取所有WebView
     */
    fun getAllWebViews(): List<VerticalWebViewData> = webViews.toList()
    
    /**
     * 移除WebView
     */
    fun removeWebView(position: Int) {
        if (position < 0 || position >= webViews.size) return
        
        val webViewData = webViews.removeAt(position)
        mainLayout?.removeViewAt(position)
        
        // 更新位置
        updateWebViewPositions()
        
        // 调整当前位置
        if (currentPosition >= webViews.size) {
            currentPosition = webViews.size - 1
        }
        
        onWebViewChangeListener?.onWebViewRemoved(webViewData, position)
        Log.d(TAG, "移除WebView: $position")
    }
    
    /**
     * 设置监听器
     */
    fun setOnWebViewChangeListener(listener: OnWebViewChangeListener) {
        this.onWebViewChangeListener = listener
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        webViews.forEach { it.webView.destroy() }
        webViews.clear()
        mainLayout?.removeAllViews()
    }
}


