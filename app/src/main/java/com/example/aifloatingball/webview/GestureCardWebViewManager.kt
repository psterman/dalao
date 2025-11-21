package com.example.aifloatingball.webview

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.GestureCardAdapter
import com.example.aifloatingball.webview.EnhancedWebViewTouchHandler
import com.example.aifloatingball.webview.WebViewContextMenuManager
import com.example.aifloatingball.webview.EnhancedMenuManager
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.download.EnhancedDownloadManager
import android.webkit.URLUtil
import android.widget.Toast


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
    

    
    // 卡片数据列表
    private val webViewCards = mutableListOf<WebViewCardData>()
    
    // 悬浮视频播放器管理器
    private val systemOverlayVideoManager: com.example.aifloatingball.video.SystemOverlayVideoManager by lazy {
        com.example.aifloatingball.video.SystemOverlayVideoManager(context)
    }
    
    // 当前卡片索引
    private var currentCardIndex = 0
    
    // 预览模式状态
    private var isPreviewMode = false
    
    // 手势检测器
    private var gestureDetector: GestureDetector? = null

    // 增强的触摸处理器
    private var touchHandler: EnhancedWebViewTouchHandler? = null

    // 上下文菜单管理器
    private val contextMenuManager: WebViewContextMenuManager by lazy {
        WebViewContextMenuManager(context)
    }

    // 文本选择管理器
    private val textSelectionManager: TextSelectionManager by lazy {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        TextSelectionManager(context, windowManager)
    }
    
    // 增强版菜单管理器
    private val enhancedMenuManager: EnhancedMenuManager by lazy {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        EnhancedMenuManager(context, windowManager)
    }

    // 增强下载管理器
    private val enhancedDownloadManager: EnhancedDownloadManager by lazy {
        EnhancedDownloadManager(context)
    }

    // 页面变化监听器
    private var onPageChangeListener: OnPageChangeListener? = null

    // WebView创建监听器
    private var onWebViewCreatedListener: ((android.webkit.WebView) -> Unit)? = null
    
    // 触摸坐标跟踪
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // SharedPreferences用于保存悬浮卡片状态
    private val sharedPreferences by lazy {
        context.getSharedPreferences("gesture_cards_state", MODE_PRIVATE)
    }

    /**
     * WebView卡片数据
     */
    data class WebViewCardData(
        val id: String,
        val webView: WebView?,
        var title: String = "新标签页",
        var url: String = "about:blank",
        var favicon: Bitmap? = null,
        var screenshot: Bitmap? = null // 🔧 修复4：保存用户最后浏览的界面截图
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
        fun onPageRefresh()

        // 横滑预览指示器相关方法
        fun onSwipePreviewStarted(cards: List<WebViewCardData>, currentIndex: Int)
        fun onSwipePreviewUpdated(position: Int, positionOffset: Float)
        fun onSwipePreviewEnded()

        // 所有卡片关闭事件
        fun onAllCardsRemoved()
    }

    init {
        setupViewPager()
        setupGestureDetector()
        setupContextMenuManager()
    }

    /**
     * 设置上下文菜单管理器
     */
    private fun setupContextMenuManager() {
        contextMenuManager.setOnNewTabListener { url, inBackground ->
            addNewCard(url)
        }
    }

    /**
     * 设置新标签页监听器（对外接口）
     */
    fun setOnNewTabListener(listener: (String, Boolean) -> Unit) {
        contextMenuManager.setOnNewTabListener(listener)
        textSelectionManager.setOnNewTabListener(listener)
        enhancedMenuManager.setOnNewTabListener(listener)
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
                },
                onCardClose = { url ->
                    // 通过URL关闭卡片
                    closeCardByUrl(url)
                }
            ).also { cardAdapter ->
                this@GestureCardWebViewManager.adapter = cardAdapter
            }
            
            // 动态控制ViewPager的横滑功能，根据触摸方向智能切换
            isUserInputEnabled = true

            // 设置页面切换监听器
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                private var isScrolling = false
                private var lastScrollTime = 0L
                
                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)
                    when (state) {
                        ViewPager2.SCROLL_STATE_DRAGGING -> {
                            isScrolling = true
                            lastScrollTime = System.currentTimeMillis()
                            // 开始拖拽时显示预览指示器
                            if (webViewCards.size > 1) {
                                showSwipePreviewIndicator()
                            }
                            Log.d(TAG, "开始拖拽切换")
                        }
                        ViewPager2.SCROLL_STATE_SETTLING -> {
                            isScrolling = true
                            lastScrollTime = System.currentTimeMillis()
                            Log.d(TAG, "正在切换中")
                        }
                        ViewPager2.SCROLL_STATE_IDLE -> {
                            isScrolling = false
                            // 拖拽结束时隐藏预览指示器
                            hideSwipePreviewIndicator()
                            Log.d(TAG, "切换完成")
                        }
                    }
                }

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                    // 更新预览指示器的位置
                    if (webViewCards.size > 1) {
                        updateSwipePreviewIndicator(position, positionOffset)
                    }
                }

                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (position < webViewCards.size) {
                        currentCardIndex = position
                        val cardData = webViewCards[position]
                        onPageChangeListener?.onCardSwitched(cardData, position)
                        Log.d(TAG, "切换到卡片: ${cardData.title}")
                    }
                }
                
                /**
                 * 检查是否正在滚动
                 */
                fun isCurrentlyScrolling(): Boolean {
                    return isScrolling || (System.currentTimeMillis() - lastScrollTime) < 500
                }
            })
        }
        
        container.addView(viewPager)
        Log.d(TAG, "设置全屏卡片ViewPager2")
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
                // 双击功能已移除
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
     * 设置WebView创建监听器
     */
    fun setOnWebViewCreatedListener(listener: (android.webkit.WebView) -> Unit) {
        onWebViewCreatedListener = listener
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
                // 使用移动版User-Agent，提供更好的移动端体验
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"

                // 优化缩放设置，减少手势冲突
                minimumFontSize = 8 // 最小字体大小
                
                // 滚动性能优化
                setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                setDatabaseEnabled(true)
                setGeolocationEnabled(true)
                setJavaScriptCanOpenWindowsAutomatically(true)
                setSupportMultipleWindows(false)
                setLayoutAlgorithm(android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)

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

            // 设置初始缩放比例
            setInitialScale(100) // 100% 初始缩放
            
            // 启用硬件加速，提升滚动性能
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            // 禁用系统默认的上下文菜单，使用我们自定义的菜单
            setLongClickable(true)
            // 显式设置空的上下文菜单监听器来禁用系统默认菜单
            setOnCreateContextMenuListener(null)

            // 额外的WebView设置来确保长按事件正确处理
            settings.apply {
                // 禁用WebView的默认上下文菜单
                setNeedInitialFocus(false)
                // 确保可以接收长按事件
                setSupportZoom(true) // 这个设置有助于长按事件的正确处理
                builtInZoomControls = false // 禁用内置缩放控件，避免干扰长按
                displayZoomControls = false // 禁用缩放控件显示
            }

            // 确保WebView可以获得焦点和接收触摸事件
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true

            // 重要：确保长按功能启用
            isLongClickable = true

            Log.d(TAG, "🔧 WebView长按设置完成: isLongClickable=${isLongClickable}, isFocusable=${isFocusable}")
            
            // 在 WebView 创建时就设置视频拦截的 JavaScript 接口
            com.example.aifloatingball.video.VideoInterceptionHelper.setupVideoInterceptionInterface(
                this,
                systemOverlayVideoManager
            )

        // 设置高级触摸处理
        setupAdvancedTouchHandling(this)
        
        // 设置长按监听器处理上下文菜单
        setOnLongClickListener { view ->
            Log.d(TAG, "🔥 WebView长按监听器被触发！")
            Log.d(TAG, "🔥 WebView类型: ${view.javaClass.simpleName}")
            Log.d(TAG, "🔥 当前线程: ${Thread.currentThread().name}")
            val result = handleWebViewLongClick(view as WebView)
            Log.d(TAG, "🔥 长按处理结果: $result")
            result
        }

        // 设置下载监听器
        setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            Log.d(TAG, "🔽 WebView下载请求: url=$url, mimeType=$mimeType, contentLength=$contentLength")
            handleDownloadRequest(url, userAgent, contentDisposition, mimeType, contentLength)
        }

            // 通知WebView创建监听器
            onWebViewCreatedListener?.invoke(this)
        }
    }

    /**
     * 设置WebView回调
     */
    private fun setupWebViewCallbacks(webView: WebView, cardData: WebViewCardData) {
        // 临时禁用 EnhancedWebViewTouchHandler 来测试长按功能
        // 创建增强的触摸处理器，传入触摸坐标更新回调
        // touchHandler = EnhancedWebViewTouchHandler(context, webView, viewPager) { x, y ->
        //     lastTouchX = x
        //     lastTouchY = y
        //     Log.d(TAG, "📍 触摸坐标更新: ($x, $y)")
        // }
        // touchHandler?.setupWebViewTouchHandling()

        // 临时使用简单的触摸监听器来跟踪坐标
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    Log.d(TAG, "📍 简单触摸坐标更新: (${event.x}, ${event.y})")
                }
            }
            false // 不拦截事件，让WebView正常处理
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
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
                
                cardData.url = url ?: "about:blank"
                onPageChangeListener?.onPageLoadingStateChanged(cardData, true)
                Log.d(TAG, "卡片开始加载: $url")

                // 根据URL动态设置User-Agent
                if (view != null && url != null) {
                    setDynamicUserAgent(view, url)
                }
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
                
                cardData.url = url ?: "about:blank"
                onPageChangeListener?.onPageLoadingStateChanged(cardData, false)
                Log.d(TAG, "卡片加载完成: $url")
                
                // 页面加载完成后优化滚动性能
                view?.evaluateJavascript("""
                    (function() {
                        // 优化滚动性能
                        document.body.style.webkitOverflowScrolling = 'touch';
                        document.body.style.overflow = 'auto';

                        // 优化触摸事件 - 但不阻止长按事件
                        // 注意：不使用 stopPropagation()，这会阻止长按菜单
                        document.addEventListener('touchstart', function(e) {
                            // 只在需要时阻止事件传播，保留长按功能
                            // e.stopPropagation(); // 移除这行，避免阻止长按事件
                        }, { passive: true });

                        document.addEventListener('touchmove', function(e) {
                            // 只阻止移动事件的传播，不影响长按
                            e.stopPropagation();
                        }, { passive: true });

                        // 优化滚动容器
                        var scrollContainer = document.body;
                        if (scrollContainer) {
                            scrollContainer.style.webkitTransform = 'translateZ(0)';
                            scrollContainer.style.transform = 'translateZ(0)';
                        }

                        console.log('WebView 触摸优化已应用，保留长按功能');
                    })();
                """.trimIndent(), null)
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
                    // 处理应用URL scheme
                    url.contains("://") && !url.startsWith("http://") && !url.startsWith("https://") -> {
                        Log.d(TAG, "检测到应用URL scheme: $url")
                        handleAppUrlScheme(view, url)
                        true
                    }
                    // 简单的广告拦截
                    url.contains("ads") || url.contains("doubleclick") -> {
                        Log.d(TAG, "拦截广告URL: $url")
                        true
                    }
                    // 处理外部链接
                    url.startsWith("http://") || url.startsWith("https://") -> {
                        // 🔧 修复：不自动创建新卡片，让用户通过长按菜单选择打开方式
                        // 如果是外部链接（不同域名），返回false让WebView正常加载，用户可以通过长按菜单选择后台打开
                        val currentUrl = cardData.url
                        if (currentUrl != null && currentUrl.startsWith("http")) {
                            try {
                                val currentDomain = java.net.URL(currentUrl).host
                                val newDomain = java.net.URL(url).host
                                
                                // 如果是不同域名的链接，不拦截，让WebView正常加载
                                // 用户可以通过长按链接选择"后台打开"来在后台创建新卡片
                                if (currentDomain != newDomain) {
                                    Log.d(TAG, "检测到外部链接，允许WebView正常加载: $url（用户可通过长按菜单选择打开方式）")
                                    return false // 不拦截，让WebView正常加载
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "解析URL域名失败", e)
                            }
                        }
                        false
                    }
                    else -> false
                }
            }
        }

        // 创建原始的 WebChromeClient
        val originalChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty()) {
                    cardData.title = title
                    onPageChangeListener?.onPageTitleChanged(cardData, title)
                    Log.d(TAG, "卡片标题更新: $title")
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                cardData.favicon = icon
                Log.d(TAG, "接收到卡片图标")
            }
        }
        
        // 使用视频拦截辅助工具创建拦截视频播放的 WebChromeClient
        // 传递 WebView 引用，以便在全屏视频时能通过 JavaScript 获取视频 URL
        webView.webChromeClient = com.example.aifloatingball.video.VideoInterceptionHelper.createVideoInterceptingChromeClient(
            systemOverlayVideoManager,
            originalChromeClient,
            webView
        )
    }

    /**
     * 为外部链接创建新卡片
     */
    private fun createNewCardForUrl(url: String) {
        try {
            // 创建新卡片
            val newCard = addNewCard(url)
            Log.d(TAG, "为外部链接创建新卡片: $url")
            
            // 通知监听器
            onPageChangeListener?.onCardAdded(newCard, webViewCards.size - 1)
        } catch (e: Exception) {
            Log.e(TAG, "创建新卡片失败", e)
        }
    }

    /**
     * 处理应用URL scheme
     */
    private fun handleAppUrlScheme(view: WebView?, url: String) {
        try {
            val context = view?.context
            if (context != null) {
                val urlSchemeHandler = com.example.aifloatingball.manager.UrlSchemeHandler(context)
                urlSchemeHandler.handleUrlScheme(
                    url = url,
                    onSuccess = {
                        Log.d(TAG, "URL scheme处理成功: $url")
                    },
                    onFailure = {
                        Log.w(TAG, "URL scheme处理失败: $url")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理URL scheme时出错: $url", e)
        }
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
    fun removeCard(index: Int) {
        if (index < 0 || index >= webViewCards.size) {
            Log.w(TAG, "无效的卡片索引: $index")
            return
        }

        val cardData = webViewCards[index]

        // 修复：不在这里销毁WebView，因为已经在外部的closeWebViewCardByUrl中处理
        // 这样避免重复销毁导致的异常
        Log.d(TAG, "准备移除卡片: ${cardData.title} (WebView销毁已在外部处理)")

        // 从列表中移除
        webViewCards.removeAt(index)
        adapter?.notifyItemRemoved(index)

        onPageChangeListener?.onCardRemoved(cardData, index)

        // 如果没有卡片了，不自动创建新卡片，让用户手动创建
        if (webViewCards.isEmpty()) {
            Log.d(TAG, "所有卡片已关闭，等待用户手动创建新卡片")
            // 通知监听器所有卡片已关闭
            onPageChangeListener?.onAllCardsRemoved()
        } else {
            // 调整当前索引
            if (currentCardIndex >= webViewCards.size) {
                currentCardIndex = webViewCards.size - 1
            }
            viewPager?.setCurrentItem(currentCardIndex, false)
        }

        // 关键修复：立即保存卡片状态，确保持久化数据被更新
        // 这样可以防止切换标签页时重新恢复被移除的卡片
        saveCardsState()

        Log.d(TAG, "移除卡片: ${cardData.title}，已更新持久化数据")
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
     * 切换预览模式（公开方法）
     */
    fun togglePreviewMode() {
        if (isPreviewMode) {
            exitPreviewMode()
        } else {
            enterPreviewMode()
        }
    }

    /**
     * 切换预览模式（私有方法）
     */
    private fun togglePreviewModeInternal() {
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
            try {
                // 检查ViewPager2是否正在滚动
                if (viewPager?.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
                    Log.w(TAG, "ViewPager2正在滚动中，延迟切换")
                    // 延迟切换，避免冲突
                    viewPager?.post {
                        safeSwitchToCard(index)
                    }
                } else {
                    safeSwitchToCard(index)
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换卡片失败", e)
            }
        }
    }

    /**
     * 安全的卡片切换方法
     */
    private fun safeSwitchToCard(index: Int) {
        try {
            // 确保ViewPager2处于可操作状态
            if (viewPager?.isAttachedToWindow == true) {
                // 先禁用用户输入，避免冲突
                viewPager?.isUserInputEnabled = false
                
                // 执行切换
                viewPager?.setCurrentItem(index, true)
                
                // 延迟恢复用户输入
                viewPager?.postDelayed({
                    viewPager?.isUserInputEnabled = true
                }, 300)
                
                Log.d(TAG, "安全切换到卡片: $index")
            } else {
                Log.w(TAG, "ViewPager2未附加到窗口，无法切换")
            }
        } catch (e: Exception) {
            Log.e(TAG, "安全切换卡片失败", e)
            // 恢复用户输入
            viewPager?.isUserInputEnabled = true
        }
    }

    /**
     * 切换到下一个卡片
     */
    fun switchToNextCard() {
        if (webViewCards.isEmpty()) return

        val currentPosition = viewPager?.currentItem ?: 0
        val nextPosition = (currentPosition + 1) % webViewCards.size

        // 使用安全的切换方法
        switchToCard(nextPosition)
        Log.d(TAG, "切换到下一个卡片: $nextPosition")
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
            cardData.webView?.destroy()
        }
        webViewCards.clear()

        // 清理ViewPager
        viewPager?.adapter = null
        container.removeView(viewPager)

        Log.d(TAG, "销毁手势卡片管理器")
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
     * 显示横滑预览指示器
     */
    private fun showSwipePreviewIndicator() {
        Log.d(TAG, "显示横滑预览指示器")
        onPageChangeListener?.onSwipePreviewStarted(webViewCards, currentCardIndex)
    }

    /**
     * 隐藏横滑预览指示器
     */
    private fun hideSwipePreviewIndicator() {
        Log.d(TAG, "隐藏横滑预览指示器")
        onPageChangeListener?.onSwipePreviewEnded()
    }

    /**
     * 更新横滑预览指示器位置
     */
    private fun updateSwipePreviewIndicator(position: Int, positionOffset: Float) {
        onPageChangeListener?.onSwipePreviewUpdated(position, positionOffset)
    }

    /**
     * 设置高级触摸处理，严格区分单指和双指操作
     * 已被EnhancedWebViewTouchHandler替代，保留作为备用
     */
    private fun setupAdvancedTouchHandling(webView: WebView) {
        // 注释掉旧的触摸处理，使用新的EnhancedWebViewTouchHandler
        /*
        var pointerCount = 0
        var isZooming = false
        var initialDistance = 0f

        webView.setOnTouchListener { view, event ->
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    pointerCount = 1
                    isZooming = false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    pointerCount = event.pointerCount
                    if (pointerCount == 2) {
                        isZooming = true
                        initialDistance = getDistance(event)
                        // 禁用ViewPager的触摸事件，避免与缩放冲突
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount >= 2 && isZooming) {
                        val currentDistance = getDistance(event)
                        val deltaDistance = kotlin.math.abs(currentDistance - initialDistance)

                        if (deltaDistance > 50) {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    } else if (pointerCount == 1 && !isZooming) {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    pointerCount = event.pointerCount - 1
                    if (pointerCount < 2) {
                        isZooming = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerCount = 0
                    isZooming = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        */
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
    
    /**
     * 保存悬浮卡片状态
     */
    fun saveCardsState() {
        try {
            Log.d(TAG, "saveCardsState: 开始保存悬浮卡片状态")
            Log.d(TAG, "saveCardsState: webViewCards.size = ${webViewCards.size}")
            
            val urls = webViewCards.mapNotNull { card ->
                Log.d(TAG, "saveCardsState: 检查卡片 - title: ${card.title}, url: ${card.url}")
                if (card.url != "about:blank" && card.url.isNotEmpty()) {
                    card.url
                } else null
            }.toSet()
            
            Log.d(TAG, "saveCardsState: 过滤后的URLs = $urls")
            Log.d(TAG, "saveCardsState: 保存到SharedPreferences")
            
            sharedPreferences.edit().putStringSet("floating_card_urls", urls).apply()
            Log.d(TAG, "保存悬浮卡片状态，URL数量: ${urls.size}")
            
            // 验证保存是否成功
            val savedUrls = sharedPreferences.getStringSet("floating_card_urls", emptySet())
            Log.d(TAG, "saveCardsState: 验证保存结果 = $savedUrls")
        } catch (e: Exception) {
            Log.e(TAG, "保存悬浮卡片状态失败", e)
        }
    }
    
    /**
     * 恢复悬浮卡片状态
     */
    fun restoreCardsState() {
        try {
            val urls = sharedPreferences.getStringSet("floating_card_urls", emptySet()) ?: emptySet()
            if (urls.isNotEmpty()) {
                Log.d(TAG, "恢复悬浮卡片状态，URL数量: ${urls.size}")
                
                // 清除现有卡片
                webViewCards.clear()
                adapter?.notifyDataSetChanged()
                
                // 为每个URL创建新卡片
                urls.forEach { url ->
                    val cardData = addNewCard(url)
                    Log.d(TAG, "恢复悬浮卡片: ${cardData.title} - $url")
                }
                
                // 如果有卡片，切换到第一张
                if (webViewCards.isNotEmpty()) {
                    switchToCard(0)
                }
            } else {
                Log.d(TAG, "没有保存的悬浮卡片状态")
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复悬浮卡片状态失败", e)
        }
    }

    /**
     * 处理WebView长按事件
     */
    private fun handleWebViewLongClick(webView: WebView): Boolean {
        val result = webView.hitTestResult
        val isSimple = isSimpleMode()

        Log.d(TAG, "🔍 WebView长按检测开始")
        Log.d(TAG, "   - HitTestResult类型: ${result.type}")
        Log.d(TAG, "   - HitTestResult内容: ${result.extra}")
        Log.d(TAG, "   - 简易模式: $isSimple")
        Log.d(TAG, "   - 触摸坐标: ($lastTouchX, $lastTouchY)")
        Log.d(TAG, "   - WebView: ${webView.javaClass.simpleName}")

        return when (result.type) {
            WebView.HitTestResult.ANCHOR_TYPE,
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                // 链接长按 - 使用增强版菜单
                val url = result.extra
                Log.d(TAG, "🔗 检测到链接长按: $url")
                if (!url.isNullOrEmpty()) {
                    Log.d(TAG, "🎯 显示增强版链接菜单: $url")
                    enhancedMenuManager.showEnhancedLinkMenu(webView, url, "", lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 增强版链接菜单显示成功")
                } else {
                    Log.w(TAG, "⚠️ 链接URL为空，显示增强版刷新菜单")
                    enhancedMenuManager.showEnhancedRefreshMenu(webView, lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 增强版刷新菜单显示成功")
                }
                true
            }
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // 图片长按 - 使用增强版菜单
                val imageUrl = result.extra
                Log.d(TAG, "🖼️ 检测到图片长按: $imageUrl")
                if (!imageUrl.isNullOrEmpty()) {
                    Log.d(TAG, "🎯 显示增强版图片菜单: $imageUrl")
                    enhancedMenuManager.showEnhancedImageMenu(webView, imageUrl, lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 增强版图片菜单显示成功")
                } else {
                    Log.w(TAG, "⚠️ 图片URL为空，显示增强版刷新菜单")
                    enhancedMenuManager.showEnhancedRefreshMenu(webView, lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 增强版刷新菜单显示成功")
                }
                true
            }
            WebView.HitTestResult.EDIT_TEXT_TYPE -> {
                // 编辑文本长按
                Log.d(TAG, "📝 检测到编辑文本长按")
                if (isSimple) {
                    // 简易模式显示文本选择菜单
                    textSelectionManager.showTextSelectionMenu(webView, lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 显示简易模式文本选择菜单")
                } else {
                    enhancedMenuManager.showEnhancedRefreshMenu(webView, lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 显示增强版刷新菜单")
                }
                true
            }
            else -> {
                // 其他情况（包括普通文本）启用文本选择或显示增强版刷新菜单
                Log.d(TAG, "📄 检测到其他类型长按，类型: ${result.type}")
                if (isSimple) {
                    // 简易模式启用文本选择功能
                    enableTextSelection(webView)
                    Log.d(TAG, "✅ 启用简易模式文本选择")
                } else {
                    enhancedMenuManager.showEnhancedRefreshMenu(webView, lastTouchX.toInt(), lastTouchY.toInt())
                    Log.d(TAG, "✅ 显示增强版刷新菜单")
                }
                true
            }
        }
    }
    
    /**
     * 启用文本选择功能
     */
    private fun enableTextSelection(webView: WebView) {
        try {
            // 启用文本选择
            webView.evaluateJavascript(
                """
                (function() {
                    document.body.style.webkitUserSelect = 'text';
                    document.body.style.userSelect = 'text';
                    document.body.style.webkitTouchCallout = 'default';

                    // 清除现有选择
                    var selection = window.getSelection();
                    selection.removeAllRanges();

                    return 'Text selection enabled';
                })();
                """.trimIndent()
            ) { result ->
                Log.d(TAG, "文本选择已启用: $result")
                // 显示提示
                android.widget.Toast.makeText(context, "已启用文本选择，请选择要复制的文本", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启用文本选择失败", e)
        }
    }

    /**
     * 检查是否为简易模式
     */
    private fun isSimpleMode(): Boolean {
        try {
            // 方法1：检查当前Activity的类名
            if (context is com.example.aifloatingball.SimpleModeActivity) {
                Log.d(TAG, "✅ 检测到SimpleModeActivity，启用简易模式")
                return true
            }
            
            // 方法2：检查SharedPreferences中的模式设置
            val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val displayMode = sharedPreferences.getString("display_mode", "floating_ball")
            if (displayMode == "simple_mode") {
                Log.d(TAG, "✅ 检测到简易模式设置，启用简易模式")
                return true
            }
            
            // 方法3：检查包名（备用方法）
            val packageName = context.packageName
            if (packageName.contains("simple") || packageName.contains("SimpleMode")) {
                Log.d(TAG, "✅ 检测到SimpleMode包名，启用简易模式")
                return true
            }
            
            Log.d(TAG, "❌ 简易模式检测：当前Activity=${context.javaClass.simpleName}, 显示模式=$displayMode")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检测简易模式失败", e)
            // 默认返回true，确保菜单功能可用
            Log.d(TAG, "⚠️ 异常情况下默认启用简易模式")
            return true
        }
    }

    /**
     * 处理下载请求
     */
    private fun handleDownloadRequest(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        Log.d(TAG, "🔽 处理下载请求: url=$url")
        Log.d(TAG, "🔽 MIME类型: $mimeType")
        Log.d(TAG, "🔽 文件大小: $contentLength bytes")

        try {
            // 检查URL是否有效
            if (!URLUtil.isValidUrl(url)) {
                Log.e(TAG, "❌ 无效的下载URL: $url")
                Toast.makeText(context, "无效的下载链接", Toast.LENGTH_SHORT).show()
                return
            }

            // 使用智能下载功能，自动根据文件类型选择合适的目录
            Log.d(TAG, "🔽 使用智能下载功能")
            enhancedDownloadManager.downloadSmart(url, object : EnhancedDownloadManager.DownloadCallback {
                override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                    Log.d(TAG, "✅ 文件下载成功: $fileName")
                    Toast.makeText(context, "文件下载完成", Toast.LENGTH_SHORT).show()
                }

                override fun onDownloadFailed(downloadId: Long, reason: Int) {
                    Log.e(TAG, "❌ 文件下载失败: $reason")
                    Toast.makeText(context, "文件下载失败", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ 下载处理失败", e)
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }




}


