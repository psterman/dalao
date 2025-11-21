package com.example.aifloatingball.webview

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adapter.GestureCardAdapter
import com.example.aifloatingball.gesture.MobileGestureManager
import com.example.aifloatingball.webview.EnhancedMenuManager
import com.example.aifloatingball.utils.WebViewConstants


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
    private var enhancedMenuManager: EnhancedMenuManager? = null


    // 卡片数据 - 使用现有的数据类型
    private val webViewCards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()
    private var currentCardIndex = 0

    // 监听器
    private var cardChangeListener: OnCardChangeListener? = null

    // WebView创建监听器
    private var onWebViewCreatedListener: ((android.webkit.WebView) -> Unit)? = null
    
    // 悬浮视频播放器管理器
    private val systemOverlayVideoManager: com.example.aifloatingball.video.SystemOverlayVideoManager by lazy {
        com.example.aifloatingball.video.SystemOverlayVideoManager(context)
    }
    
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
        // setupEnhancedMenuManager 需要在有WindowManager时调用
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
                onWebViewSetup = { webView: WebView, cardData: GestureCardWebViewManager.WebViewCardData ->
                    setupWebViewCallbacks(webView, cardData)
                },
                onCardClose = { url: String ->
                    // 通过URL关闭卡片
                    closeCardByUrl(url)
                }
            ).also { cardAdapter: GestureCardAdapter ->
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
     * 设置增强版菜单管理器（公共方法）
     */
    fun setupEnhancedMenuManager(windowManager: android.view.WindowManager) {
        enhancedMenuManager = EnhancedMenuManager(context, windowManager)

        // 设置新标签页监听器
        enhancedMenuManager?.setOnNewTabListener { url, inBackground ->
            addNewCard(url)
            Log.d(TAG, "通过增强版菜单创建新卡片: $url, 后台模式: $inBackground")
        }

        Log.d(TAG, "增强版菜单管理器设置完成")
    }

    /**
     * 设置WebView创建监听器
     */
    fun setOnWebViewCreatedListener(listener: (android.webkit.WebView) -> Unit) {
        onWebViewCreatedListener = listener
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
                // 使用移动版User-Agent，提供更好的移动端体验
                userAgentString = WebViewConstants.MOBILE_USER_AGENT


                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            // 设置长按监听器
            setOnLongClickListener { view ->
                handleWebViewLongClick(this)
            }

            // 设置初始缩放比例
            setInitialScale(100) // 100% 初始缩放

            // 设置高级触摸处理
            setupAdvancedTouchHandling(this)
            
            // 在 WebView 创建时就设置视频拦截的 JavaScript 接口
            com.example.aifloatingball.video.VideoInterceptionHelper.setupVideoInterceptionInterface(
                this,
                systemOverlayVideoManager
            )

            // 通知WebView创建监听器
            onWebViewCreatedListener?.invoke(this)
        }
    }
    
    /**
     * 设置WebView回调
     */
    private fun setupWebViewCallbacks(webView: WebView, cardData: GestureCardWebViewManager.WebViewCardData) {
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                
                // 页面刷新时，重新设置 JavaScript 接口（确保接口存在）
                if (view != null) {
                    try {
                        // 每次页面加载时都重新设置接口，因为页面刷新可能清除接口
                        com.example.aifloatingball.video.VideoInterceptionHelper.setupVideoInterceptionInterface(
                            view,
                            systemOverlayVideoManager
                        )
                        Log.d(TAG, "已在页面开始加载时重新设置视频拦截接口: $url")
                    } catch (e: Exception) {
                        Log.e(TAG, "重新设置视频拦截接口失败", e)
                    }
                }
                
                cardData.url = url ?: ""
                cardData.title = "加载中..."
                cardData.favicon = favicon
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // 页面加载完成时，确保 JavaScript 接口已设置并注入拦截脚本
                if (view != null) {
                    try {
                        // 再次确保接口已设置（防止页面刷新时接口被清除）
                        com.example.aifloatingball.video.VideoInterceptionHelper.setupVideoInterceptionInterface(
                            view,
                            systemOverlayVideoManager
                        )
                        
                        // 延迟注入脚本，确保 DOM 已完全加载
                        view.postDelayed({
                            try {
                                com.example.aifloatingball.video.VideoInterceptionHelper.injectVideoInterceptionScript(
                                    view,
                                    systemOverlayVideoManager
                                )
                                Log.d(TAG, "已在页面加载完成时注入视频拦截脚本: $url")
                            } catch (e: Exception) {
                                Log.e(TAG, "注入视频拦截脚本失败", e)
                            }
                        }, 100) // 延迟 100ms 确保 DOM 已准备好
                    } catch (e: Exception) {
                        Log.e(TAG, "设置视频拦截失败", e)
                    }
                }
                
                cardData.url = url ?: ""
                cardData.title = view?.title ?: "未知页面"

                // 根据URL动态设置User-Agent
                if (view != null && url != null) {
                    setDynamicUserAgent(view, url)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url == null) return false

                Log.d(TAG, "WebView URL loading: $url")
                
                // 优先检测是否为媒体URL（视频/音频），使用悬浮播放器播放
                val contentType: String? = null // 这里无法获取 Content-Type，但可以通过 URL 判断
                if (com.example.aifloatingball.video.VideoInterceptionHelper.isMediaUrl(url, contentType)) {
                    Log.d(TAG, "🎬 检测到媒体文件，使用悬浮播放器播放: $url")
                    try {
                        systemOverlayVideoManager.show(url)
                        return true // 拦截URL，不在WebView中加载
                    } catch (e: Exception) {
                        Log.e(TAG, "启动悬浮播放器失败", e)
                        // 如果悬浮播放器启动失败，继续正常流程
                    }
                }

                return when {
                    // 处理移动应用URL scheme重定向
                    url.startsWith("baiduboxapp://") -> {
                        Log.d(TAG, "拦截百度App重定向，保持在WebView中")
                        handleSearchEngineRedirect(view, url, "baidu")
                        true
                    }
                    url.startsWith("mttbrowser://") -> {
                        Log.d(TAG, "拦截搜狗浏览器重定向，保持在WebView中")
                        handleSearchEngineRedirect(view, url, "sogou")
                        true
                    }
                    url.startsWith("quark://") -> {
                        Log.d(TAG, "拦截夸克浏览器重定向，保持在WebView中")
                        handleSearchEngineRedirect(view, url, "quark")
                        true
                    }
                    url.startsWith("ucbrowser://") -> {
                        Log.d(TAG, "拦截UC浏览器重定向，保持在WebView中")
                        handleSearchEngineRedirect(view, url, "uc")
                        true
                    }
                    // 简单的广告拦截
                    url.contains("ads") || url.contains("doubleclick") -> {
                        Log.d(TAG, "拦截广告URL: $url")
                        true
                    }
                    else -> false
                }
            }
        }
        
        // 创建拦截视频播放的 WebChromeClient
        // 传递 WebView 引用，以便在全屏视频时能通过 JavaScript 获取视频 URL
        webView.webChromeClient = com.example.aifloatingball.video.VideoInterceptionHelper.createVideoInterceptingChromeClient(
            systemOverlayVideoManager,
            null,
            webView
        )
    }

    /**
     * 处理搜索引擎重定向到移动应用的情况
     */
    private fun handleSearchEngineRedirect(view: WebView?, originalUrl: String, engineType: String) {
        if (view == null) return

        Log.d(TAG, "处理搜索引擎重定向: $engineType, 原始URL: $originalUrl")

        try {
            // 从URL scheme中提取搜索参数
            val searchQuery = extractSearchQueryFromScheme(originalUrl, engineType)

            if (searchQuery.isNotEmpty()) {
                // 构建Web版本的搜索URL
                val webSearchUrl = buildWebSearchUrl(engineType, searchQuery)
                Log.d(TAG, "重定向到Web版本: $webSearchUrl")
                view.loadUrl(webSearchUrl)
            } else {
                // 如果无法提取搜索词，重定向到搜索引擎首页
                val homepageUrl = getSearchEngineHomepage(engineType)
                Log.d(TAG, "无法提取搜索词，重定向到首页: $homepageUrl")
                view.loadUrl(homepageUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理搜索引擎重定向失败", e)
            // 回退到搜索引擎首页
            val homepageUrl = getSearchEngineHomepage(engineType)
            view.loadUrl(homepageUrl)
        }
    }

    /**
     * 从URL scheme中提取搜索查询
     */
    private fun extractSearchQueryFromScheme(url: String, engineType: String): String {
        return try {
            when (engineType) {
                "baidu" -> {
                    // baiduboxapp://utils?action=sendIntent&minver=7.4&params=%7B...
                    val uri = android.net.Uri.parse(url)
                    val params = uri.getQueryParameter("params")
                    // 暂时返回空，让它重定向到首页
                    ""
                }
                "sogou" -> {
                    // mttbrowser://url=https://m.sogou.com/?...
                    val uri = android.net.Uri.parse(url)
                    val redirectUrl = uri.getQueryParameter("url")
                    if (!redirectUrl.isNullOrEmpty()) {
                        val redirectUri = android.net.Uri.parse(redirectUrl)
                        redirectUri.getQueryParameter("keyword") ?: redirectUri.getQueryParameter("query") ?: ""
                    } else {
                        ""
                    }
                }
                "quark" -> {
                    val uri = android.net.Uri.parse(url)
                    uri.getQueryParameter("q") ?: ""
                }
                "uc" -> {
                    val uri = android.net.Uri.parse(url)
                    uri.getQueryParameter("keyword") ?: ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取搜索查询失败: $url", e)
            ""
        }
    }

    /**
     * 构建Web版本的搜索URL
     */
    private fun buildWebSearchUrl(engineType: String, query: String): String {
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }

        return when (engineType) {
            "baidu" -> "https://www.baidu.com/s?wd=$encodedQuery"
            "sogou" -> "https://www.sogou.com/web?query=$encodedQuery"
            "quark" -> "https://quark.sm.cn/s?q=$encodedQuery"
            "uc" -> "https://www.uc.cn/s?wd=$encodedQuery"
            else -> "https://www.google.com/search?q=$encodedQuery"
        }
    }

    /**
     * 获取搜索引擎首页URL
     */
    private fun getSearchEngineHomepage(engineType: String): String {
        return when (engineType) {
            "baidu" -> "https://www.baidu.com"
            "sogou" -> "https://www.sogou.com"
            "quark" -> "https://quark.sm.cn"
            "uc" -> "https://www.uc.cn"
            else -> "https://www.google.com"
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
     * 通过URL关闭卡片
     */
    fun closeCardByUrl(url: String) {
        val cardIndex = webViewCards.indexOfFirst { it.url == url }
        if (cardIndex >= 0) {
            removeCard(cardIndex)
        } else {
            Log.w(TAG, "尝试关闭不存在的卡片，URL: $url")
        }
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
     * 设置ViewPager用户输入是否启用
     * 用于在遮罩层激活时禁用ViewPager的左右滑动，让遮罩层的手势优先处理
     */
    fun setViewPagerUserInputEnabled(enabled: Boolean) {
        try {
            viewPager?.isUserInputEnabled = enabled
            Log.d(TAG, "设置ViewPager用户输入: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "设置ViewPager用户输入失败", e)
        }
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
                // 链接 - 使用增强版菜单
                url?.let {
                    enhancedMenuManager?.showEnhancedLinkMenu(webView, it, webView.title ?: "", 0, 0)
                }
                return true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                // 图片 - 使用增强版菜单
                url?.let {
                    enhancedMenuManager?.showEnhancedImageMenu(webView, it, 0, 0)
                }
                return true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // 图片链接 - 使用增强版菜单
                url?.let {
                    enhancedMenuManager?.showEnhancedImageMenu(webView, it, 0, 0)
                }
                return true
            }
            else -> {
                // 其他类型，显示增强版刷新菜单
                enhancedMenuManager?.showEnhancedRefreshMenu(webView, 0, 0)
                return true
            }
        }
    }


    /**
     * 清理资源
     */
    fun cleanup() {
        gestureManager?.cleanup()
        // enhancedMenuManager会在需要时自动清理



        webViewCards.forEach { it.webView?.destroy() }
        webViewCards.clear()
    }

    /**
     * 根据URL动态设置User-Agent
     */
    private fun setDynamicUserAgent(view: WebView, url: String) {
        val shouldUseDesktopUA = when {
            // 搜索引擎使用桌面版User-Agent以避免重定向
            url.contains("baidu.com") && url.contains("/s?") -> true
            url.contains("sogou.com") && url.contains("/web?") -> true
            url.contains("bing.com") && url.contains("/search?") -> true
            url.contains("360.cn") && url.contains("/s?") -> true
            url.contains("quark.sm.cn") && url.contains("/s?") -> true
            url.contains("google.com") && url.contains("/search?") -> true
            // 其他网站使用移动版User-Agent
            else -> false
        }

        val targetUserAgent = if (shouldUseDesktopUA) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        } else {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
        }

        val currentUserAgent = view.settings.userAgentString

        if (currentUserAgent != targetUserAgent) {
            Log.d(TAG, "切换User-Agent: ${if (shouldUseDesktopUA) "桌面版" else "移动版"} for $url")
            view.settings.userAgentString = targetUserAgent
        }
    }

    /**
     * 设置高级触摸处理，严格区分单指和双指操作
     * 优化版本：解决横向滚动与标签页切换的冲突
     */
    private fun setupAdvancedTouchHandling(webView: WebView) {
        var pointerCount = 0
        var isZooming = false
        var initialDistance = 0f
        var initialX = 0f
        var initialY = 0f
        var isHorizontalScroll = false
        var scrollThreshold = 50f // 横向滚动阈值

        webView.setOnTouchListener { view, event ->
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    pointerCount = 1
                    isZooming = false
                    isHorizontalScroll = false
                    initialX = event.x
                    initialY = event.y
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    pointerCount = event.pointerCount
                    if (pointerCount == 2) {
                        isZooming = true
                        isHorizontalScroll = false
                        initialDistance = getDistance(event)
                        // 双指操作时完全禁用ViewPager
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount >= 2 && isZooming) {
                        // 双指缩放模式
                        val currentDistance = getDistance(event)
                        val deltaDistance = kotlin.math.abs(currentDistance - initialDistance)

                        if (deltaDistance > 50) {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    } else if (pointerCount == 1 && !isZooming) {
                        // 单指移动模式 - 智能判断是否为横向滚动
                        val deltaX = kotlin.math.abs(event.x - initialX)
                        val deltaY = kotlin.math.abs(event.y - initialY)
                        
                        // 如果横向移动距离大于纵向移动距离，且超过阈值，则认为是横向滚动
                        if (deltaX > scrollThreshold && deltaX > deltaY) {
                            isHorizontalScroll = true
                            // 横向滚动时禁用ViewPager，避免误触发标签页切换
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        } else if (deltaY > scrollThreshold && deltaY > deltaX) {
                            // 纵向滚动时允许ViewPager处理（用于标签页切换）
                            isHorizontalScroll = false
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    pointerCount = event.pointerCount - 1
                    if (pointerCount < 2) {
                        isZooming = false
                        // 双指操作结束后，根据当前滚动状态决定是否重新启用ViewPager
                        if (!isHorizontalScroll) {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerCount = 0
                    isZooming = false
                    isHorizontalScroll = false
                    // 触摸结束后重新允许ViewPager处理事件
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    /**
     * 计算两个触摸点之间的距离
     */
    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }
}


