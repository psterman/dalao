package com.example.aifloatingball.webview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.WebViewCardAdapter
import com.example.aifloatingball.views.WebViewCard

/**
 * 卡片式多页面WebView管理器
 * 管理多个WebView卡片，支持网格布局显示
 */
class CardWebViewManager(
    private val context: Context,
    private val container: FrameLayout
) {
    companion object {
        private const val TAG = "CardWebViewManager"
        private const val MAX_CARDS = 9 // 最大卡片数量（3x3网格）
    }

    // RecyclerView用于显示卡片网格
    private var recyclerView: RecyclerView? = null
    private var adapter: WebViewCardAdapter? = null
    
    // 卡片数据列表
    private val webViewCards = mutableListOf<WebViewCardData>()
    
    // 当前全屏显示的卡片
    private var fullScreenCard: WebViewCard? = null
    private var isFullScreenMode = false
    
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
        var favicon: Bitmap? = null,
        var card: WebViewCard? = null
    )

    /**
     * 页面变化监听器接口
     */
    interface OnPageChangeListener {
        fun onCardAdded(cardData: WebViewCardData, position: Int)
        fun onCardRemoved(cardData: WebViewCardData, position: Int)
        fun onCardClicked(cardData: WebViewCardData)
        fun onFullScreenEntered(cardData: WebViewCardData)
        fun onFullScreenExited()
        fun onPageTitleChanged(cardData: WebViewCardData, title: String)
        fun onPageLoadingStateChanged(cardData: WebViewCardData, isLoading: Boolean)
    }

    init {
        setupRecyclerView()
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        recyclerView = RecyclerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // 设置网格布局管理器（2列）
            layoutManager = GridLayoutManager(context, 2)
            
            // 设置适配器
            adapter = WebViewCardAdapter(
                cards = webViewCards,
                onCardClick = { cardData ->
                    handleCardClick(cardData)
                },
                onCardLongClick = { cardData ->
                    handleCardLongClick(cardData)
                },
                onCardClose = { cardData ->
                    removeCard(cardData)
                }
            ).also { cardAdapter ->
                this@CardWebViewManager.adapter = cardAdapter
            }
        }
        
        container.addView(recyclerView)
        Log.d(TAG, "设置卡片网格RecyclerView")
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
                    cardData.card?.setPageTitle(title)
                    onPageChangeListener?.onPageTitleChanged(cardData, title)
                    Log.d(TAG, "卡片标题更新: $title")
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                cardData.favicon = icon
                cardData.card?.setFavicon(icon)
                Log.d(TAG, "接收到卡片图标")
            }
        }
    }

    /**
     * 处理卡片点击
     */
    private fun handleCardClick(cardData: WebViewCardData) {
        if (isFullScreenMode) {
            exitFullScreen()
        } else {
            enterFullScreen(cardData)
        }
        onPageChangeListener?.onCardClicked(cardData)
    }

    /**
     * 处理卡片长按
     */
    private fun handleCardLongClick(cardData: WebViewCardData) {
        // 可以实现拖拽排序等功能
        Log.d(TAG, "卡片长按: ${cardData.title}")
    }

    /**
     * 进入全屏模式
     */
    private fun enterFullScreen(cardData: WebViewCardData) {
        if (isFullScreenMode) return

        val webView = cardData.webView
        
        // 从卡片中移除WebView
        cardData.card?.let { card ->
            (webView.parent as? FrameLayout)?.removeView(webView)
        }
        
        // 创建全屏WebView卡片
        fullScreenCard = WebViewCard(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setWebView(webView)
            setPageTitle(cardData.title)
            setPageUrl(cardData.url)
            setFavicon(cardData.favicon)
            setInteractive(true)
            
            setOnCardClickListener(object : WebViewCard.OnCardClickListener {
                override fun onCardClick(card: WebViewCard) {
                    exitFullScreen()
                }
                
                override fun onCardLongClick(card: WebViewCard) {
                    // 长按可以显示操作菜单
                }
            })
        }
        
        // 隐藏网格视图
        recyclerView?.visibility = View.GONE
        
        // 显示全屏卡片
        container.addView(fullScreenCard)
        
        isFullScreenMode = true
        onPageChangeListener?.onFullScreenEntered(cardData)
        Log.d(TAG, "进入全屏模式: ${cardData.title}")
    }

    /**
     * 退出全屏模式
     */
    private fun exitFullScreen() {
        if (!isFullScreenMode || fullScreenCard == null) return

        val webView = fullScreenCard?.getWebView()
        
        // 移除全屏卡片
        container.removeView(fullScreenCard)
        
        // 将WebView放回对应的卡片
        webView?.let { wv ->
            val cardData = webViewCards.find { it.webView == wv }
            cardData?.card?.setWebView(wv)
        }
        
        // 显示网格视图
        recyclerView?.visibility = View.VISIBLE
        
        fullScreenCard = null
        isFullScreenMode = false
        onPageChangeListener?.onFullScreenExited()
        Log.d(TAG, "退出全屏模式")
    }

    /**
     * 移除卡片
     */
    fun removeCard(cardData: WebViewCardData) {
        val position = webViewCards.indexOf(cardData)
        if (position == -1) return

        // 如果是全屏模式下的卡片，先退出全屏
        if (isFullScreenMode && fullScreenCard?.getWebView() == cardData.webView) {
            exitFullScreen()
        }

        // 销毁WebView
        cardData.card?.destroy()
        cardData.webView.destroy()

        // 从列表中移除
        webViewCards.removeAt(position)
        adapter?.notifyItemRemoved(position)

        onPageChangeListener?.onCardRemoved(cardData, position)
        Log.d(TAG, "移除卡片: ${cardData.title}")
    }

    /**
     * 加载URL到指定卡片
     */
    fun loadUrl(cardData: WebViewCardData, url: String) {
        cardData.webView.loadUrl(url)
        cardData.url = url
        Log.d(TAG, "卡片加载URL: $url")
    }

    /**
     * 加载URL到当前卡片（如果有的话）或创建新卡片
     */
    fun loadUrl(url: String) {
        if (webViewCards.isEmpty()) {
            addNewCard(url)
        } else {
            // 加载到最后一个卡片
            loadUrl(webViewCards.last(), url)
        }
    }

    /**
     * 获取所有卡片
     */
    fun getAllCards(): List<WebViewCardData> = webViewCards.toList()

    /**
     * 是否可以返回
     */
    fun canGoBack(): Boolean {
        return if (isFullScreenMode) {
            fullScreenCard?.getWebView()?.canGoBack() == true
        } else {
            webViewCards.any { it.webView.canGoBack() }
        }
    }

    /**
     * 返回上一页
     */
    fun goBack(): Boolean {
        return if (isFullScreenMode) {
            fullScreenCard?.getWebView()?.let { webView ->
                if (webView.canGoBack()) {
                    webView.goBack()
                    true
                } else false
            } ?: false
        } else {
            // 在网格模式下，返回最后一个可以返回的卡片
            webViewCards.lastOrNull { it.webView.canGoBack() }?.let { cardData ->
                cardData.webView.goBack()
                true
            } ?: false
        }
    }

    /**
     * 是否处于全屏模式
     */
    fun isInFullScreenMode(): Boolean = isFullScreenMode

    /**
     * 设置页面变化监听器
     */
    fun setOnPageChangeListener(listener: OnPageChangeListener) {
        onPageChangeListener = listener
    }

    /**
     * 销毁所有卡片
     */
    fun destroy() {
        webViewCards.forEach { cardData ->
            cardData.card?.destroy()
            cardData.webView.destroy()
        }
        webViewCards.clear()
        adapter?.notifyDataSetChanged()

        fullScreenCard?.destroy()
        fullScreenCard = null

        Log.d(TAG, "销毁所有WebView卡片")
    }
}
