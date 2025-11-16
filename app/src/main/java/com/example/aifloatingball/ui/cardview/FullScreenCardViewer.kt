package com.example.aifloatingball.ui.cardview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.webview.CustomWebView

/**
 * 全屏卡片查看器
 * 点击卡片后全屏显示WebView内容
 */
class FullScreenCardViewer(
    private val context: Context,
    private val parentContainer: ViewGroup
) {
    companion object {
        private const val TAG = "FullScreenCardViewer"
    }

    private var fullScreenContainer: FrameLayout? = null
    private var currentCardData: CardViewModeManager.SearchResultCardData? = null
    private var isShowing = false
    private var originalWebViewParent: ViewGroup? = null
    private var originalWebViewIndex: Int = -1

    /**
     * 显示全屏卡片
     */
    fun show(cardData: CardViewModeManager.SearchResultCardData) {
        if (isShowing) {
            dismiss()
            return
        }

        currentCardData = cardData
        isShowing = true

        // 创建全屏容器
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFFFFFFFF.toInt()) // 白色背景，避免黑屏
            alpha = 0f
        }

        // 创建顶部工具栏
        val toolbar = createToolbar(container, cardData)
        
        // 设置工具栏的滑动返回功能
        var toolbarStartY = 0f
        var isToolbarDragging = false
        toolbar.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    toolbarStartY = event.y
                    isToolbarDragging = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.y - toolbarStartY
                    if (deltaY > dpToPx(50) && !isToolbarDragging) {
                        // 向下滑动超过50dp，触发返回
                        isToolbarDragging = true
                        dismiss() // 关闭全屏，返回卡片视图
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    isToolbarDragging = false
                    false
                }
                else -> false
            }
        }

        // 创建WebView容器
        val webViewContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dpToPx(56) // 为工具栏留出空间
            }
            setBackgroundColor(0xFFFFFFFF.toInt()) // 白色背景
        }

        // 将WebView添加到全屏容器
        val webView = cardData.webView
        val parent = webView.parent as? ViewGroup
        
        // 保存原始位置信息
        if (parent != null) {
            originalWebViewParent = parent
            originalWebViewIndex = parent.indexOfChild(webView)
            parent.removeView(webView)
        }
        
        // 确保WebView可见且正确设置
        webView.visibility = View.VISIBLE
        webView.setBackgroundColor(0xFFFFFFFF.toInt()) // 设置白色背景
        
        webViewContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        container.addView(toolbar)
        container.addView(webViewContainer)

        // 添加到父容器
        parentContainer.addView(container)
        fullScreenContainer = container

        // 确保WebView在添加到容器后可见并正确加载
        webView.post {
            webView.visibility = View.VISIBLE
            // 检查WebView的加载状态
            val currentUrl = webView.url
            val originalUrl = cardData.url
            
            // 如果WebView还没有加载内容或URL为空，尝试重新加载
            if (currentUrl.isNullOrEmpty() || currentUrl == "about:blank") {
                Log.d(TAG, "WebView URL为空或about:blank，尝试重新加载")
                // 重新执行搜索
                val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
                    it.name == cardData.engineKey 
                }
                if (searchEngine != null && !cardData.searchQuery.isNullOrEmpty()) {
                    val searchUrl = searchEngine.getSearchUrl(cardData.searchQuery)
                    Log.d(TAG, "重新加载URL: $searchUrl")
                    webView.loadUrl(searchUrl)
                } else if (!originalUrl.isNullOrEmpty() && originalUrl != "about:blank") {
                    // 如果有原始URL，使用原始URL
                    Log.d(TAG, "使用原始URL: $originalUrl")
                    webView.loadUrl(originalUrl)
                }
            } else {
                // 如果URL存在但页面可能是白屏，延迟检查并重新加载
                Log.d(TAG, "WebView已有URL: $currentUrl，延迟检查内容")
                // 延迟检查页面内容，如果仍然是白屏则重新加载
                webView.postDelayed({
                    // 检查页面是否真的加载了内容
                    webView.evaluateJavascript("(function(){return document.body && document.body.innerHTML.length > 0;})()") { result ->
                        val hasContent = result == "true"
                        if (!hasContent) {
                            Log.d(TAG, "页面内容为空，重新加载")
                            // 重新加载URL
                            if (!originalUrl.isNullOrEmpty() && originalUrl != "about:blank") {
                                webView.loadUrl(originalUrl)
                            } else {
                                webView.reload()
                            }
                        } else {
                            Log.d(TAG, "页面内容已加载")
                        }
                    }
                }, 500) // 延迟500ms检查
            }
        }

        // 动画显示
        animateShow(container)
    }

    /**
     * 创建工具栏
     */
    private fun createToolbar(
        parent: FrameLayout,
        cardData: CardViewModeManager.SearchResultCardData
    ): LinearLayout {
        // 检测暗色模式
        val isDarkMode = (context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply {
                gravity = Gravity.TOP
            }
            setBackgroundColor(if (isDarkMode) 0xFF1A1A1A.toInt() else 0xFFF5F5F5.toInt())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL
        }

        val webView = cardData.webView
        
        // 返回按钮
        val backButton = TextView(context).apply {
            text = "←"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { 
                if (webView.canGoBack()) {
                    webView.goBack()
                }
            }
            contentDescription = "返回"
        }
        
        // 前进按钮
        val forwardButton = TextView(context).apply {
            text = "→"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { 
                if (webView.canGoForward()) {
                    webView.goForward()
                }
            }
            contentDescription = "前进"
        }
        
        // 刷新按钮
        val refreshButton = TextView(context).apply {
            text = "⟳"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { 
                webView.reload()
            }
            contentDescription = "刷新"
        }
        
        // 标题
        val titleView = TextView(context).apply {
            text = cardData.title.ifEmpty { cardData.searchQuery }
            textSize = 14f
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
            }
        }

        // 关闭按钮
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { dismiss() }
            contentDescription = "关闭"
        }

        toolbar.addView(backButton)
        toolbar.addView(forwardButton)
        toolbar.addView(refreshButton)
        toolbar.addView(titleView)
        toolbar.addView(closeButton)

        return toolbar
    }

    /**
     * 动画显示
     */
    private fun animateShow(view: View) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val alpha = animation.animatedValue as Float
                view.alpha = alpha
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                }
            })
        }
        animator.start()
    }

    /**
     * 关闭全屏
     */
    fun dismiss() {
        val container = fullScreenContainer ?: return
        isShowing = false

        // 动画隐藏
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val alpha = animation.animatedValue as Float
                container.alpha = alpha
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 恢复WebView到原位置
                    currentCardData?.let { cardData ->
                        val webView = cardData.webView
                        val currentParent = webView.parent as? ViewGroup
                        currentParent?.removeView(webView)
                        
                        // 恢复到原始位置
                        originalWebViewParent?.let { originalParent ->
                            try {
                                // 确保WebView恢复到原来的容器中
                                val layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                                
                                if (originalWebViewIndex >= 0 && originalWebViewIndex <= originalParent.childCount) {
                                    originalParent.addView(webView, originalWebViewIndex, layoutParams)
                                } else {
                                    originalParent.addView(webView, layoutParams)
                                }
                                
                                Log.d(TAG, "WebView已恢复到原位置")
                            } catch (e: Exception) {
                                Log.e(TAG, "恢复WebView到原位置失败", e)
                                // 如果恢复失败，尝试添加到末尾
                                try {
                                    originalParent.addView(webView)
                                } catch (e2: Exception) {
                                    Log.e(TAG, "恢复WebView失败", e2)
                                }
                            }
                        }
                    }
                    
                    parentContainer.removeView(container)
                    fullScreenContainer = null
                    currentCardData = null
                    originalWebViewParent = null
                    originalWebViewIndex = -1
                }
            })
        }
        animator.start()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}


