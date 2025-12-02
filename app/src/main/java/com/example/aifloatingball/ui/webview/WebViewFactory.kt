package com.example.aifloatingball.ui.webview

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.aifloatingball.utils.WebViewConstants
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.text.TextSelectionManager
import android.content.Context.WINDOW_SERVICE
import android.view.WindowManager
import android.widget.LinearLayout

/**
 * WebView工厂，负责创建和配置WebView实例
 */
class WebViewFactory(private val context: Context) {
    
    companion object {
        private const val TAG = "WebViewFactory"
        // 移动版Chrome User-Agent，适合移动端优化的网站
        // 使用标准移动端User-Agent（编译时常量）
        private const val MOBILE_USER_AGENT = WebViewConstants.MOBILE_USER_AGENT_STANDARD
        // 桌面版Chrome User-Agent，用于避免搜索引擎重定向到移动应用
        private const val DESKTOP_USER_AGENT = WebViewConstants.DESKTOP_USER_AGENT
        // 智能User-Agent，默认使用移动端（使用标准移动端User-Agent）
        private const val SMART_USER_AGENT = WebViewConstants.MOBILE_USER_AGENT_STANDARD
        
        // 检测是否在语音胶囊模式中的方法
        private fun isInVoiceCapsuleMode(): Boolean {
            return try {
                // 通过检查当前Activity的类名来判断是否在语音胶囊模式
                val activityClass = Class.forName("com.example.aifloatingball.SimpleModeActivity")
                val method = activityClass.getDeclaredMethod("isVoiceCapsuleModeActive")
                method.isAccessible = true
                val instance = activityClass.getDeclaredField("INSTANCE").get(null)
                method.invoke(instance) as? Boolean ?: false
            } catch (e: Exception) {
                Log.d(TAG, "无法检测语音胶囊模式状态: ${e.message}")
                false
            }
        }
    }
    
    val textSelectionManager: TextSelectionManager by lazy {
        val windowManager = context.getSystemService(
            WINDOW_SERVICE) as WindowManager
        TextSelectionManager(context, windowManager)
    }
    
    /**
     * 创建配置好的WebView实例
     */
    fun createWebView(): CustomWebView {
        Log.d(TAG, "创建新的CustomWebView")
        
        return CustomWebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // 设置智能User-Agent：搜索引擎使用移动端，其他网站使用桌面版
                userAgentString = SMART_USER_AGENT
                Log.d(TAG, "Set User-Agent to: $SMART_USER_AGENT")
                
                // 允许访问本地文件（asset文件）
                allowFileAccess = true
                allowContentAccess = true
                
                // 允许从file:// URL访问其他file:// URL（用于加载asset中的资源）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                @Suppress("DEPRECATION")
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            
            // 确保WebView可获取焦点和触摸焦点，这对于输入法激活至关重要
            isFocusable = true
            isFocusableInTouchMode = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    Log.d(TAG, "CustomWebViewClient shouldOverrideUrlLoading: $url")

                    if (url != null) {
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
                            // 检查是否在语音胶囊模式中，如果是则阻止外部浏览器打开
                            isInVoiceCapsuleMode() -> {
                                Log.d(TAG, "语音胶囊模式：阻止外部浏览器打开，在WebView内加载: $url")
                                view?.loadUrl(url)
                                true
                            }
                            else -> false
                        }
                    }
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    Log.d(TAG, "CustomWebViewClient shouldOverrideUrlLoading (legacy): $url")

                    if (url != null) {
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
                            // 检查是否在语音胶囊模式中，如果是则阻止外部浏览器打开
                            isInVoiceCapsuleMode() -> {
                                Log.d(TAG, "语音胶囊模式：阻止外部浏览器打开，在WebView内加载: $url")
                                view?.loadUrl(url)
                                true
                            }
                            else -> false
                        }
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "CustomWebViewClient onPageStarted: $url")

                    // 根据URL动态设置User-Agent
                    if (view != null && url != null) {
                        setDynamicUserAgent(view, url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "CustomWebViewClient onPageFinished: $url")
                    
                    // 智能焦点管理 - 基于用户交互意图
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                console.log('Initializing smart focus management...');
                                
                                // 创建焦点协调器
                                window.focusCoordinator = {
                                    allowPageFocus: true,
                                    lastUserAction: 'none',
                                    actionTimestamp: 0
                                };
                                
                                // 监听来自应用的焦点协调信号
                                window.addEventListener('message', function(event) {
                                    if (event.data && event.data.type === 'focus_control') {
                                        window.focusCoordinator.allowPageFocus = event.data.allow;
                                        window.focusCoordinator.lastUserAction = event.data.action;
                                        window.focusCoordinator.actionTimestamp = Date.now();
                                        console.log('Focus control updated:', event.data);
                                        
                                        // 特殊处理紧急焦点清理
                                        if (event.data.action === 'emergency_focus_clear') {
                                            console.log('Emergency focus clear requested');
                                            // 立即移除所有焦点
                                            if (document.activeElement && document.activeElement !== document.body) {
                                                document.activeElement.blur();
                                            }
                                            // 暂时禁用页面交互
                                            const elements = document.querySelectorAll('button, [role="button"], input, [tabindex]');
                                            elements.forEach(function(el) {
                                                el.tabIndex = -1;
                                            });
                                            
                                            // 3秒后恢复
                                            setTimeout(function() {
                                                elements.forEach(function(el) {
                                                    if (el.getAttribute('data-original-tabindex')) {
                                                        el.tabIndex = parseInt(el.getAttribute('data-original-tabindex'));
                                                    } else {
                                                        el.removeAttribute('tabindex');
                                                    }
                                                });
                                            }, 3000);
                                        }
                                    }
                                });
                                
                                // 智能焦点拦截器 - 只在应用输入框活跃时拦截
                                function smartFocusInterceptor(e) {
                                    const now = Date.now();
                                    const timeSinceAction = now - window.focusCoordinator.actionTimestamp;
                                    
                                    // 如果用户最近点击了应用搜索框，暂时拦截页面焦点
                                    if (!window.focusCoordinator.allowPageFocus && 
                                        window.focusCoordinator.lastUserAction === 'search_input_clicked' &&
                                        timeSinceAction < 2000) { // 2秒内保护期
                                        
                                        console.log('Temporarily blocking page focus for search input priority');
                                        e.preventDefault();
                                        e.stopPropagation();
                                        if (e.target && e.target.blur) {
                                            e.target.blur();
                                        }
                                        return;
                                    }
                                    
                                    // 记录页面内的焦点活动
                                    if (e.target) {
                                        window.focusCoordinator.lastUserAction = 'page_interaction';
                                        window.focusCoordinator.actionTimestamp = now;
                                        console.log('Page element focused:', e.target.tagName, e.target.className);
                                    }
                                }
                                
                                // 添加智能焦点监听器
                                document.addEventListener('focusin', smartFocusInterceptor, true);
                                
                                // 监听用户在页面内的点击，表示用户想与页面交互
                                document.addEventListener('click', function(e) {
                                    window.focusCoordinator.lastUserAction = 'page_click';
                                    window.focusCoordinator.actionTimestamp = Date.now();
                                    window.focusCoordinator.allowPageFocus = true;
                                    console.log('User clicked on page, allowing page focus');
                                }, true);
                                
                                // 处理页面加载时的自动焦点 - 只移除真正干扰的焦点
                                function handleInitialFocus() {
                                    const problematicElements = document.querySelectorAll([
                                        '[autofocus]',
                                        'button:focus',
                                        '[role="button"]:focus'
                                    ].join(','));
                                    
                                    problematicElements.forEach(function(el) {
                                        if (el === document.activeElement) {
                                            el.blur();
                                            console.log('Removed problematic initial focus from:', el.tagName);
                                        }
                                        // 只移除自动聚焦属性，不阻止用户主动点击
                                        el.removeAttribute('autofocus');
                                    });
                                }
                                
                                // 页面加载完成后处理初始焦点
                                if (document.readyState === 'loading') {
                                    document.addEventListener('DOMContentLoaded', handleInitialFocus);
                                } else {
                                    handleInitialFocus();
                                }
                                
                                // 监听DOM变化，处理动态内容
                                const observer = new MutationObserver(function(mutations) {
                                    mutations.forEach(function(mutation) {
                                        if (mutation.type === 'childList') {
                                            mutation.addedNodes.forEach(function(node) {
                                                if (node.nodeType === 1 && node.hasAttribute && node.hasAttribute('autofocus')) {
                                                    node.removeAttribute('autofocus');
                                                    if (node === document.activeElement && 
                                                        !window.focusCoordinator.allowPageFocus) {
                                                        node.blur();
                                                    }
                                                }
                                            });
                                        }
                                    });
                                });
                                
                                observer.observe(document.body, {
                                    childList: true,
                                    subtree: true
                                });
                                
                                console.log('Smart focus management initialized');
                                
                            } catch (e) {
                                console.error('Smart focus management error:', e);
                            }
                        })();
                    """.trimIndent(), null)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.e(TAG, "CustomWebViewClient onReceivedError: ${error?.errorCode} - ${error?.description} for URL: ${request?.url}")
                    } else {
                         Log.e(TAG, "CustomWebViewClient onReceivedError (legacy) for URL: ${request?.url}")
                    }
                }
            }
            
            // 新增：设置WebChromeClient来处理JS对话框、进度、标题等，这对于输入法激活有时是必要的。
            webChromeClient = object : android.webkit.WebChromeClient() {
                // 可以根据需要在此处添加其他回调方法，例如 onProgressChanged, onReceivedTitle 等
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.setTextClassifier(TextClassifier.NO_OP)
            }
            
            layoutParams = LinearLayout.LayoutParams(
                context.resources.displayMetrics.widthPixels,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    marginEnd = context.resources.getDimensionPixelSize(R.dimen.webview_margin)
                } else {
                    rightMargin = context.resources.getDimensionPixelSize(R.dimen.webview_margin)
                }
            }
            
            setTextSelectionManager(textSelectionManager)

            setOnLongClickListener { false }

            // 应用缩放优化
            optimizeWebViewZoom(this)
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
            // metaso.cn 使用桌面版User-Agent以确保页面正常加载
            url.contains("metaso.cn") -> true
            // 其他网站使用移动版User-Agent
            else -> false
        }

        val targetUserAgent = if (shouldUseDesktopUA) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
        val currentUserAgent = view.settings.userAgentString

        if (currentUserAgent != targetUserAgent) {
            Log.d(TAG, "切换User-Agent: ${if (shouldUseDesktopUA) "桌面版" else "移动版"} for $url")
            view.settings.userAgentString = targetUserAgent
        }
    }

    /**
     * 优化WebView缩放设置，减少手势冲突
     */
    private fun optimizeWebViewZoom(webView: CustomWebView) {
        webView.settings.apply {
            // 启用缩放但优化设置
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // 设置合理的缩放范围
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                // 最小缩放比例：50%
                // 最大缩放比例：300%
                // 这样可以减少意外缩放
            }

            // 优化视口设置
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // 设置初始缩放比例
        webView.setInitialScale(100) // 100% 缩放

        // 优化触摸处理，严格区分单指和双指操作
        setupAdvancedTouchHandling(webView)
    }

    /**
     * 设置高级触摸处理，严格区分单指和双指操作
     * 优化版本：解决横向滚动与标签页切换的冲突
     */
    private fun setupAdvancedTouchHandling(webView: CustomWebView) {
        var lastTouchTime = 0L
        var pointerCount = 0
        var isZooming = false
        var initialDistance = 0f
        var initialX = 0f
        var initialY = 0f
        var isHorizontalScroll = false
        var scrollThreshold = 50f // 横向滚动阈值

        webView.setOnTouchListener { view, event ->
            val currentTime = System.currentTimeMillis()

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    pointerCount = 1
                    isZooming = false
                    isHorizontalScroll = false
                    lastTouchTime = currentTime
                    initialX = event.x
                    initialY = event.y
                    Log.d(TAG, "单指按下")
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    pointerCount = event.pointerCount
                    if (pointerCount == 2) {
                        // 双指按下，准备缩放
                        isZooming = true
                        isHorizontalScroll = false
                        initialDistance = getDistance(event)
                        Log.d(TAG, "双指按下，开始缩放模式")

                        // 双指操作时完全禁用ViewPager
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount >= 2 && isZooming) {
                        // 双指移动，处理缩放
                        val currentDistance = getDistance(event)
                        val deltaDistance = kotlin.math.abs(currentDistance - initialDistance)

                        if (deltaDistance > 50) { // 缩放阈值
                            // 确保是缩放操作，继续禁用ViewPager
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            Log.d(TAG, "双指缩放中，距离变化: $deltaDistance")
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
                            Log.d(TAG, "检测到横向滚动，禁用ViewPager")
                        } else if (deltaY > scrollThreshold && deltaY > deltaX) {
                            // 纵向滚动时允许ViewPager处理（用于标签页切换）
                            isHorizontalScroll = false
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            Log.d(TAG, "检测到纵向滚动，允许ViewPager")
                        }
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    pointerCount = event.pointerCount - 1
                    if (pointerCount < 2) {
                        // 不再是双指操作，根据当前滚动状态决定是否重新允许ViewPager
                        isZooming = false
                        if (!isHorizontalScroll) {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        Log.d(TAG, "结束缩放模式")
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerCount = 0
                    isZooming = false
                    isHorizontalScroll = false
                    // 触摸结束后重新允许ViewPager处理事件
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    Log.d(TAG, "触摸结束")
                }
            }

            // 让WebView处理触摸事件
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