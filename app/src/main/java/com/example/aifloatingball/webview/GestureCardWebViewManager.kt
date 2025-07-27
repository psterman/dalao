package com.example.aifloatingball.webview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.GestureCardAdapter
import com.example.aifloatingball.views.CardOperationBar

/**
 * 全屏手势卡片WebView管理器
 * 类似手机多任务界面，支持全屏卡片显示和手势操作
 */
class GestureCardWebViewManager(
    private val context: Context,
    private val container: FrameLayout
) {
    companion object {
        private const val TAG = "GestureCardWebViewManager"
        private const val MAX_CARDS = 10 // 最大卡片数量
    }

    // ViewPager2用于全屏卡片切换
    private var viewPager: ViewPager2? = null
    private var adapter: GestureCardAdapter? = null
    
    // 底部操作栏
    private var operationBar: CardOperationBar? = null
    
    // 卡片数据列表
    private val webViewCards = mutableListOf<WebViewCardData>()
    
    // 当前卡片索引
    private var currentCardIndex = 0
    
    // 预览模式状态
    private var isPreviewMode = false
    
    // 手势检测器
    private var gestureDetector: GestureDetector? = null
    
    // 页面变化监听器
    private var onPageChangeListener: OnPageChangeListener? = null

    /**
     * WebView卡片数据
     */
    data class WebViewCardData(
        val id: String,
        val webView: WebView,
        var title: String = "新标签页",
        var url: String = "about:blank",
        var favicon: Bitmap? = null
    )

    /**
     * 页面变化监听器接口
     */
    interface OnPageChangeListener {
        fun onCardAdded(cardData: WebViewCardData, position: Int)
        fun onCardRemoved(cardData: WebViewCardData, position: Int)
        fun onCardSwitched(cardData: WebViewCardData, position: Int)
        fun onPreviewModeChanged(isPreview: Boolean)
        fun onPageTitleChanged(cardData: WebViewCardData, title: String)
        fun onPageLoadingStateChanged(cardData: WebViewCardData, isLoading: Boolean)
        fun onGoHome()
    }

    init {
        setupViewPager()
        setupOperationBar()
        setupGestureDetector()
    }

    /**
     * 设置ViewPager2
     */
    private fun setupViewPager() {
        viewPager = ViewPager2(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // 设置适配器
            adapter = GestureCardAdapter(
                cards = webViewCards,
                onWebViewSetup = { webView, cardData ->
                    setupWebViewCallbacks(webView, cardData)
                }
            ).also { cardAdapter ->
                this@GestureCardWebViewManager.adapter = cardAdapter
            }
            
            // 设置页面切换监听器
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (position < webViewCards.size) {
                        currentCardIndex = position
                        val cardData = webViewCards[position]
                        onPageChangeListener?.onCardSwitched(cardData, position)
                        operationBar?.updateCardInfo(cardData.title, position + 1, webViewCards.size)
                        Log.d(TAG, "切换到卡片: ${cardData.title}")
                    }
                }
            })
        }
        
        container.addView(viewPager)
        Log.d(TAG, "设置全屏卡片ViewPager2")
    }

    /**
     * 设置底部操作栏
     */
    private fun setupOperationBar() {
        operationBar = CardOperationBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM
            }
            
            // 设置操作监听器
            setOnOperationListener(object : CardOperationBar.OnOperationListener {
                override fun onNewCard() {
                    addNewCard("about:blank")
                }
                
                override fun onCloseCard() {
                    if (webViewCards.isNotEmpty()) {
                        removeCard(currentCardIndex)
                    }
                }
                
                override fun onRefresh() {
                    getCurrentCard()?.webView?.reload()
                }
                
                override fun onGoBack() {
                    getCurrentCard()?.webView?.let { webView ->
                        if (webView.canGoBack()) {
                            webView.goBack()
                        }
                    }
                }

                override fun onGoHome() {
                    onPageChangeListener?.onGoHome()
                }

                override fun onPreviewToggle() {
                    togglePreviewMode()
                }
            })
        }
        
        container.addView(operationBar)
        Log.d(TAG, "设置底部操作栏")
    }

    /**
     * 设置手势检测器
     */
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val deltaY = e2.y - (e1?.y ?: 0f)
                val deltaX = e2.x - (e1?.x ?: 0f)

                // 垂直滑动优先
                if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                    if (deltaY < -200 && velocityY < -500) {
                        // 上滑进入预览模式
                        if (!isPreviewMode) {
                            enterPreviewMode()
                            return true
                        }
                    } else if (deltaY > 200 && velocityY > 500) {
                        // 下滑退出预览模式
                        if (isPreviewMode) {
                            exitPreviewMode()
                            return true
                        }
                    }
                }

                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 双击切换操作栏显示状态
                operationBar?.toggle()
                return true
            }
        })

        // 为ViewPager设置触摸监听器
        viewPager?.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event)
            false // 不拦截事件，让ViewPager正常处理左右滑动
        }
    }

    /**
     * 添加新卡片
     */
    fun addNewCard(url: String = "about:blank"): WebViewCardData {
        if (webViewCards.size >= MAX_CARDS) {
            Log.w(TAG, "已达到最大卡片数量限制: $MAX_CARDS")
            return webViewCards.last()
        }

        val webView = createWebView()
        val cardId = "card_${System.currentTimeMillis()}"
        val cardData = WebViewCardData(
            id = cardId,
            webView = webView,
            title = if (url == "about:blank") "新标签页" else "加载中...",
            url = url
        )

        // 设置WebView回调
        setupWebViewCallbacks(webView, cardData)

        webViewCards.add(cardData)
        adapter?.notifyItemInserted(webViewCards.size - 1)
        
        // 切换到新卡片
        viewPager?.setCurrentItem(webViewCards.size - 1, true)
        
        // 加载URL
        if (url != "about:blank") {
            webView.loadUrl(url)
        }
        
        onPageChangeListener?.onCardAdded(cardData, webViewCards.size - 1)
        Log.d(TAG, "添加新卡片: $url")
        
        return cardData
    }

    /**
     * 创建WebView
     */
    private fun createWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"

                // 暗色模式支持
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val isDarkMode = (context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES

                    if (isDarkMode) {
                        // 强制暗色模式
                        forceDark = android.webkit.WebSettings.FORCE_DARK_ON
                    } else {
                        // 关闭暗色模式
                        forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                    }
                }
            }

            // 设置背景色
            val isDarkMode = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

            if (isDarkMode) {
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.webview_background_dark))
            } else {
                setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.white))
            }
        }
    }

    /**
     * 设置WebView回调
     */
    private fun setupWebViewCallbacks(webView: WebView, cardData: WebViewCardData) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                cardData.url = url ?: "about:blank"
                onPageChangeListener?.onPageLoadingStateChanged(cardData, true)
                Log.d(TAG, "卡片开始加载: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cardData.url = url ?: "about:blank"
                onPageChangeListener?.onPageLoadingStateChanged(cardData, false)
                Log.d(TAG, "卡片加载完成: $url")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // 简单的广告拦截
                if (url?.contains("ads") == true || url?.contains("doubleclick") == true) {
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty()) {
                    cardData.title = title
                    onPageChangeListener?.onPageTitleChanged(cardData, title)
                    // 更新操作栏显示
                    if (webViewCards.indexOf(cardData) == currentCardIndex) {
                        operationBar?.updateCardInfo(title, currentCardIndex + 1, webViewCards.size)
                    }
                    Log.d(TAG, "卡片标题更新: $title")
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                cardData.favicon = icon
                Log.d(TAG, "接收到卡片图标")
            }
        }
    }

    /**
     * 移除卡片
     */
    fun removeCard(index: Int) {
        if (index < 0 || index >= webViewCards.size) {
            Log.w(TAG, "无效的卡片索引: $index")
            return
        }

        val cardData = webViewCards[index]

        // 销毁WebView
        cardData.webView.destroy()

        // 从列表中移除
        webViewCards.removeAt(index)
        adapter?.notifyItemRemoved(index)

        onPageChangeListener?.onCardRemoved(cardData, index)

        // 如果没有卡片了，创建一个新的
        if (webViewCards.isEmpty()) {
            addNewCard("about:blank")
        } else {
            // 调整当前索引
            if (currentCardIndex >= webViewCards.size) {
                currentCardIndex = webViewCards.size - 1
            }
            viewPager?.setCurrentItem(currentCardIndex, false)
        }

        Log.d(TAG, "移除卡片: ${cardData.title}")
    }

    /**
     * 获取当前卡片
     */
    fun getCurrentCard(): WebViewCardData? {
        return if (currentCardIndex < webViewCards.size) {
            webViewCards[currentCardIndex]
        } else null
    }

    /**
     * 加载URL到当前卡片
     */
    fun loadUrl(url: String) {
        getCurrentCard()?.webView?.loadUrl(url)
    }

    /**
     * 进入预览模式
     */
    private fun enterPreviewMode() {
        if (isPreviewMode) return

        isPreviewMode = true
        onPageChangeListener?.onPreviewModeChanged(true)

        Log.d(TAG, "进入预览模式")
    }

    /**
     * 退出预览模式
     */
    private fun exitPreviewMode() {
        if (!isPreviewMode) return

        isPreviewMode = false
        onPageChangeListener?.onPreviewModeChanged(false)

        Log.d(TAG, "退出预览模式")
    }

    /**
     * 切换预览模式
     */
    private fun togglePreviewMode() {
        if (isPreviewMode) {
            exitPreviewMode()
        } else {
            enterPreviewMode()
        }
    }

    /**
     * 获取所有卡片
     */
    fun getAllCards(): List<WebViewCardData> {
        return webViewCards.toList()
    }

    /**
     * 切换到指定卡片
     */
    fun switchToCard(index: Int) {
        if (index >= 0 && index < webViewCards.size) {
            viewPager?.setCurrentItem(index, true)
        }
    }

    /**
     * 设置页面变化监听器
     */
    fun setOnPageChangeListener(listener: OnPageChangeListener) {
        this.onPageChangeListener = listener
    }

    /**
     * 销毁管理器
     */
    fun destroy() {
        // 销毁所有WebView
        webViewCards.forEach { cardData ->
            cardData.webView.destroy()
        }
        webViewCards.clear()

        // 清理ViewPager
        viewPager?.adapter = null
        container.removeView(viewPager)

        // 清理操作栏
        container.removeView(operationBar)

        Log.d(TAG, "销毁手势卡片管理器")
    }
}
