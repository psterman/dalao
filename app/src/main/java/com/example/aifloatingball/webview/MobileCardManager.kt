package com.example.aifloatingball.webview

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.gesture.MobileGestureManager
import com.example.aifloatingball.adapter.GestureCardAdapter


/**
 * 手机卡片管理器
 * 基于现代手机交互设计的卡片式浏览器管理系统
 */
class MobileCardManager(
    private val context: Context,
    private val container: FrameLayout
) {
    
    companion object {
        private const val TAG = "MobileCardManager"
        private const val MAX_CARDS = 20
    }
    
    // 核心组件
    private var viewPager: ViewPager2? = null
    private var adapter: GestureCardAdapter? = null
    private var gestureManager: MobileGestureManager? = null
    private var contextMenuManager: WebViewContextMenuManager? = null


    // 卡片数据 - 使用现有的数据类型
    private val webViewCards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()
    private var currentCardIndex = 0

    // 监听器
    private var cardChangeListener: OnCardChangeListener? = null
    
    /**
     * 卡片变化监听器
     */
    interface OnCardChangeListener {
        fun onCardAdded(card: GestureCardWebViewManager.WebViewCardData, position: Int)
        fun onCardRemoved(card: GestureCardWebViewManager.WebViewCardData, position: Int)
        fun onCardSwitched(card: GestureCardWebViewManager.WebViewCardData, position: Int)
        fun onAllCardsRemoved()
    }
    
    init {
        setupViewPager()
        setupGestureManager()
        setupContextMenuManager()
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
                this@MobileCardManager.adapter = cardAdapter
            }
            
            // 设置页面切换监听器
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentCardIndex = position
                    if (position < webViewCards.size) {
                        cardChangeListener?.onCardSwitched(webViewCards[position], position)
                    }
                }
            })
            
            // 启用预加载
            offscreenPageLimit = 2
        }
        
        container.addView(viewPager)
        Log.d(TAG, "ViewPager2设置完成")
    }
    
    /**
     * 设置手势管理器
     */
    private fun setupGestureManager() {
        Log.d(TAG, "设置手势管理器")

        // 简化的手势处理，直接在ViewPager上设置
        viewPager?.let { pager ->
            // 设置页面切换监听器
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentCardIndex = position
                    if (position < webViewCards.size) {
                        val cardData = webViewCards[position]
                        cardChangeListener?.onCardSwitched(cardData, position)
                        Log.d(TAG, "切换到卡片: ${cardData.title}")
                    }
                }
            })

            // 设置基本的触摸手势处理
            setupBasicGestures(pager)
        }

        Log.d(TAG, "手势管理器设置完成")
    }

    /**
     * 设置基本手势处理
     */
    private fun setupBasicGestures(pager: ViewPager2) {
        // 这里可以添加基本的手势处理逻辑
        // 比如双击缩放、长按菜单等
        Log.d(TAG, "基本手势处理设置完成")
    }

    /**
     * 设置上下文菜单管理器
     */
    private fun setupContextMenuManager() {
        contextMenuManager = WebViewContextMenuManager(context)

        // 设置新标签页监听器
        contextMenuManager?.setOnNewTabListener { url, inBackground ->
            addNewCard(url)
            Log.d(TAG, "通过上下文菜单创建新卡片: $url, 后台模式: $inBackground")
        }

        Log.d(TAG, "上下文菜单管理器设置完成")
    }


    
    /**
     * 添加新卡片
     */
    fun addNewCard(url: String = "about:blank"): GestureCardWebViewManager.WebViewCardData {
        if (webViewCards.size >= MAX_CARDS) {
            Log.w(TAG, "已达到最大卡片数量限制: $MAX_CARDS")
            return webViewCards.last()
        }
        
        val webView = createWebView()
        val cardId = "card_${System.currentTimeMillis()}"
        val cardData = GestureCardWebViewManager.WebViewCardData(
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
        
        cardChangeListener?.onCardAdded(cardData, webViewCards.size - 1)
        Log.d(TAG, "添加新卡片: $url")
        
        return cardData
    }
    
    /**
     * 创建WebView
     */
    private fun createWebView(): WebView {
        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
            }

            // 设置长按监听器
            setOnLongClickListener { view ->
                handleWebViewLongClick(this)
            }
        }
    }
    
    /**
     * 设置WebView回调
     */
    private fun setupWebViewCallbacks(webView: WebView, cardData: GestureCardWebViewManager.WebViewCardData) {
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                cardData.url = url ?: ""
                cardData.title = "加载中..."
                cardData.favicon = favicon
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cardData.url = url ?: ""
                cardData.title = view?.title ?: "未知页面"


            }
        }
    }
    
    // 手势操作方法
    private fun toggleUI() {
        // TODO: 实现UI显示/隐藏
        Log.d(TAG, "切换UI显示状态")
    }
    
    private fun animateWebViewScale(webView: WebView, targetScale: Float) {
        val scaleX = ObjectAnimator.ofFloat(webView, "scaleX", webView.scaleX, targetScale)
        val scaleY = ObjectAnimator.ofFloat(webView, "scaleY", webView.scaleY, targetScale)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
            start()
        }
    }
    
    private fun showCardPreview() {
        // TODO: 显示卡片预览
        Log.d(TAG, "显示卡片预览")
    }
    
    private fun switchToPreviousCard() {
        val prevIndex = if (currentCardIndex > 0) currentCardIndex - 1 else webViewCards.size - 1
        viewPager?.setCurrentItem(prevIndex, true)
    }
    
    private fun showAddressBar() {
        // TODO: 显示地址栏
        Log.d(TAG, "显示地址栏")
    }
    
    private fun refreshCurrentCard() {
        getCurrentWebView()?.reload()
    }
    
    private fun closeCurrentCard() {
        if (webViewCards.isNotEmpty()) {
            removeCard(currentCardIndex)
        }
    }
    
    private fun showAllCards() {
        // TODO: 显示所有卡片概览
        Log.d(TAG, "显示所有卡片")
    }
    
    private fun closeAllCards() {
        webViewCards.clear()
        adapter?.notifyDataSetChanged()
        cardChangeListener?.onAllCardsRemoved()
    }
    
    /**
     * 获取当前WebView
     */
    private fun getCurrentWebView(): WebView? {
        return if (currentCardIndex < webViewCards.size) {
            webViewCards[currentCardIndex].webView
        } else null
    }
    
    /**
     * 移除卡片
     */
    fun removeCard(position: Int) {
        if (position >= 0 && position < webViewCards.size) {
            val removedCard = webViewCards.removeAt(position)
            adapter?.notifyItemRemoved(position)
            cardChangeListener?.onCardRemoved(removedCard, position)
            
            if (webViewCards.isEmpty()) {
                cardChangeListener?.onAllCardsRemoved()
            }
        }
    }
    
    /**
     * 获取所有卡片
     */
    fun getAllCards(): List<GestureCardWebViewManager.WebViewCardData> = webViewCards.toList()

    /**
     * 获取当前卡片
     */
    fun getCurrentCard(): GestureCardWebViewManager.WebViewCardData? {
        return if (currentCardIndex < webViewCards.size) {
            webViewCards[currentCardIndex]
        } else null
    }

    /**
     * 切换到指定卡片
     */
    fun switchToCard(position: Int) {
        if (position >= 0 && position < webViewCards.size) {
            viewPager?.setCurrentItem(position, true)
        }
    }

    /**
     * 加载URL到当前卡片
     */
    fun loadUrl(url: String) {
        getCurrentCard()?.webView?.loadUrl(url)
    }



    /**
     * 切换到下一个卡片
     */
    fun switchToNextCard() {
        if (webViewCards.isEmpty()) return

        val currentPosition = viewPager?.currentItem ?: 0
        val nextPosition = (currentPosition + 1) % webViewCards.size

        viewPager?.setCurrentItem(nextPosition, true)
        Log.d(TAG, "切换到下一个卡片: $nextPosition")
    }

    /**
     * 设置监听器
     */
    fun setOnCardChangeListener(listener: OnCardChangeListener) {
        this.cardChangeListener = listener
    }
    
    /**
     * 处理WebView长按事件
     */
    private fun handleWebViewLongClick(webView: WebView): Boolean {
        val hitTestResult = webView.hitTestResult
        val url = hitTestResult.extra

        Log.d(TAG, "WebView长按检测 - 类型: ${hitTestResult.type}, URL: $url")

        when (hitTestResult.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                // 链接
                url?.let {
                    contextMenuManager?.showLinkContextMenu(it, webView.title ?: "", webView)
                }
                return true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                // 图片
                url?.let {
                    contextMenuManager?.showImageContextMenu(it, webView)
                }
                return true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // 图片链接
                url?.let {
                    contextMenuManager?.showImageLinkContextMenu(it, webView.title ?: "", webView)
                }
                return true
            }
            else -> {
                // 其他类型，显示通用菜单
                contextMenuManager?.showGeneralContextMenu(webView, webView)
                return true
            }
        }
    }


    /**
     * 清理资源
     */
    fun cleanup() {
        gestureManager?.cleanup()
        contextMenuManager?.cleanup()



        webViewCards.forEach { it.webView.destroy() }
        webViewCards.clear()
    }
}
