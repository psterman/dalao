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
import android.view.WindowManager
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
        private const val SWIPE_THRESHOLD = 120f // 滑动阈值
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
        if (isAnimating || targetIndex < 0 || targetIndex >= tabs.size) return
        
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
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                
                // 重新排序标签页数组
                reorderTabs(currentTabIndex, targetIndex)
                
                // 更新当前标签页索引
                currentTabIndex = 0  // 目标标签页现在在最前面
                
                // 通知监听器
                onTabSwitchedListener?.invoke(targetTab, currentTabIndex)
                
                Log.d(TAG, "标签页切换完成，当前标签页: ${targetTab.title}")
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
        val targetAlpha = if (targetStackIndex == 0) 1.0f else max(0.3f, 1f - (targetStackIndex * TAB_ALPHA_FACTOR))
        val targetElevation = (tabs.size - targetStackIndex).toFloat()
        
        val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
        val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
        val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
        val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
        val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
        val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
        
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
        val targetAlpha = if (targetStackIndex == 0) 1.0f else max(0.3f, 1f - (targetStackIndex * TAB_ALPHA_FACTOR))
        val targetElevation = (tabs.size - targetStackIndex).toFloat()
        
        val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
        val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
        val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
        val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
        val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
        val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
        
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
                // 计算新的层叠位置
                val newStackIndex = if (index < targetIndex) {
                    // 如果目标标签页移到前面，其他标签页位置不变
                    tabs.size - 1 - index
                } else {
                    // 如果目标标签页移到前面，后面的标签页位置前移
                    tabs.size - 1 - index + 1
                }
                
                val targetOffsetX = newStackIndex * TAB_OFFSET_X
                val targetOffsetY = newStackIndex * TAB_OFFSET_Y
                val targetScale = TAB_SCALE_FACTOR.pow(newStackIndex)
                val targetAlpha = if (newStackIndex == 0) 1.0f else max(0.3f, 1f - (newStackIndex * TAB_ALPHA_FACTOR))
                val targetElevation = (tabs.size - newStackIndex).toFloat()
                
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
        // 将目标标签页移到最前面（数组的第一个位置）
        val targetTab = tabs[targetIndex]
        tabs.removeAt(targetIndex)
        tabs.add(0, targetTab)
        
        // 将当前标签页移到最后面（数组的最后一个位置）
        val currentTab = tabs[currentIndex + 1] // 因为targetTab已经移到了前面
        tabs.removeAt(currentIndex + 1)
        tabs.add(currentTab)
        
        Log.d(TAG, "重新排序完成：目标标签页移到最前，当前标签页移到最后")
    }

    /**
     * 更新所有标签页的位置 - 实现真正的纵向叠加效果
     */
    private fun updateTabPositions() {
        tabs.forEachIndexed { index, tab ->
            // 计算层叠位置：越靠后的标签页偏移越大
            val stackIndex = tabs.size - 1 - index  // 反转索引，让第一个标签页在最上面
            val offsetX = stackIndex * TAB_OFFSET_X
            val offsetY = stackIndex * TAB_OFFSET_Y
            val scale = TAB_SCALE_FACTOR.pow(stackIndex)
            // 第一个页面（stackIndex = 0）不设置透明度，保持完全不透明
            val alpha = if (stackIndex == 0) 1.0f else max(0.3f, 1f - (stackIndex * TAB_ALPHA_FACTOR))
            
            // 设置变换属性
            tab.webView.translationX = offsetX
            tab.webView.translationY = offsetY
            tab.webView.scaleX = scale
            tab.webView.scaleY = scale
            tab.webView.alpha = alpha
            
            // 设置层级：第一个标签页在最上面
            tab.webView.elevation = (tabs.size - index).toFloat()
            
            // 更新标签页状态
            tab.isActive = (index == currentTabIndex)
            tab.stackIndex = stackIndex
            
            Log.d(TAG, "标签页 ${tab.title}: stackIndex=$stackIndex, offsetX=$offsetX, offsetY=$offsetY, scale=$scale, alpha=$alpha, elevation=${tab.webView.elevation}")
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
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isSwipeStarted) {
                    val deltaX = abs(event.x - swipeStartX)
                    val deltaY = abs(event.y - swipeStartY)
                    
                    // 检查是否在文本选择区域
                    if (isInTextSelectionArea(event)) {
                        // 如果在文本选择区域，不处理滑动
                        return false
                    }
                    
                    if (deltaX > 50f || deltaY > 50f) {
                        isSwipeStarted = true
                        // 确定滑动方向
                        swipeDirection = if (deltaX > deltaY) {
                            SwipeDirection.HORIZONTAL
                        } else {
                            SwipeDirection.VERTICAL
                        }
                        
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
                if (isSwipeStarted && swipeDirection == SwipeDirection.HORIZONTAL) {
                    val deltaX = event.x - swipeStartX
                    
                    if (abs(deltaX) > SWIPE_THRESHOLD) {
                        if (deltaX > 0) {
                            switchToPreviousTab()
                        } else {
                            switchToNextTab()
                        }
                        return true
                    }
                }
            }
        }
        
        // 只有在非横向滑动时才传递给WebView
        // 对于纵向滑动，直接传递给WebView，不经过gestureDetector
        return if (swipeDirection == SwipeDirection.HORIZONTAL) {
            true
        } else {
            // 纵向滑动直接传递给WebView，避免刷新冲突
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
            
            // 设置长按菜单处理
            setOnLongClickListener { view ->
                Log.d(TAG, "🎯 PaperWebView长按事件触发")
                android.widget.Toast.makeText(context, "长按检测到！", android.widget.Toast.LENGTH_SHORT).show()
                handleWebViewLongClick(view as WebView)
                true // 拦截长按事件，阻止系统默认菜单
            }
            
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
            }
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 移除阴影和边框绘制，避免灰色蒙版效果
        }
    }

    /**
     * 处理WebView长按事件
     */
    private fun handleWebViewLongClick(webView: WebView): Boolean {
        val hitTestResult = webView.hitTestResult
        val url = hitTestResult.extra

        Log.d(TAG, "PaperStackWebView长按检测 - 类型: ${hitTestResult.type}, URL: $url")

        when (hitTestResult.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.ANCHOR_TYPE -> {
                // 链接 - 显示简单菜单
                url?.let {
                    Log.d(TAG, "🔗 显示链接菜单: $it")
                    showSimpleLinkMenu(webView, it)
                } ?: run {
                    Log.d(TAG, "🔗 链接URL为空，显示通用菜单")
                    showSimpleGeneralMenu(webView)
                }
                return true
            }
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // 图片 - 显示简单菜单
                url?.let {
                    Log.d(TAG, "🖼️ 显示图片菜单: $it")
                    showSimpleImageMenu(webView, it)
                } ?: run {
                    Log.d(TAG, "🖼️ 图片URL为空，显示通用菜单")
                    showSimpleGeneralMenu(webView)
                }
                return true
            }
            else -> {
                // 其他类型，显示通用菜单
                Log.d(TAG, "📄 显示通用菜单")
                showSimpleGeneralMenu(webView)
                return true
            }
        }
    }

    /**
     * 显示简单的链接菜单
     */
    private fun showSimpleLinkMenu(webView: WebView, url: String) {
        val items = arrayOf("在新标签页中打开", "复制链接", "分享链接", "刷新页面")
        
        android.app.AlertDialog.Builder(context)
            .setTitle("链接操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // 在新标签页中打开
                        addTab(url, "新标签页")
                        android.widget.Toast.makeText(context, "已在新标签页中打开", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // 复制链接
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("链接", url)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // 分享链接
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "分享链接"))
                    }
                    3 -> {
                        // 刷新页面
                        webView.reload()
                        android.widget.Toast.makeText(context, "页面已刷新", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示简单的图片菜单
     */
    private fun showSimpleImageMenu(webView: WebView, imageUrl: String) {
        val items = arrayOf("查看大图", "复制图片链接", "分享图片", "保存图片")
        
        android.app.AlertDialog.Builder(context)
            .setTitle("图片操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // 查看大图
                        addTab(imageUrl, "图片查看")
                        android.widget.Toast.makeText(context, "已在新标签页中打开图片", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // 复制图片链接
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("图片链接", imageUrl)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "图片链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // 分享图片
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, imageUrl)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "分享图片"))
                    }
                    3 -> {
                        // 保存图片
                        android.widget.Toast.makeText(context, "保存图片功能开发中", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示简单的通用菜单
     */
    private fun showSimpleGeneralMenu(webView: WebView) {
        val items = arrayOf("刷新页面", "重新加载", "页面信息", "新建标签页")
        
        android.app.AlertDialog.Builder(context)
            .setTitle("页面操作")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // 刷新页面
                        webView.reload()
                        android.widget.Toast.makeText(context, "页面已刷新", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // 重新加载
                        webView.loadUrl(webView.url ?: "about:blank")
                        android.widget.Toast.makeText(context, "页面已重新加载", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // 页面信息
                        val info = "URL: ${webView.url}\n标题: ${webView.title}"
                        android.app.AlertDialog.Builder(context)
                            .setTitle("页面信息")
                            .setMessage(info)
                            .setPositiveButton("确定", null)
                            .show()
                    }
                    3 -> {
                        // 新建标签页
                        addTab("https://www.baidu.com", "新标签页")
                        android.widget.Toast.makeText(context, "已创建新标签页", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
