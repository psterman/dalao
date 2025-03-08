package com.example.aifloatingball

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.*
import android.widget.*
import androidx.core.content.ContextCompat
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.aifloatingball.model.SearchEngine
import java.io.ByteArrayInputStream
import kotlin.math.abs

class FloatingWebViewService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var searchInput: EditText? = null
    private var progressBar: ProgressBar? = null
    private var gestureHintView: TextView? = null
    private var closeButton: ImageButton? = null
    private var expandButton: ImageButton? = null
    private var searchButton: ImageButton? = null
    private var backButton: ImageButton? = null
    private var forwardButton: ImageButton? = null
    private var refreshButton: ImageButton? = null
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false
    private var isExpanded = false
    
    // 浏览器设置状态
    private var isNoImageMode = false
    private var isDesktopMode = false
    private var isAdBlockEnabled = true
    private var isIncognitoMode = false
    
    // 手势相关变量
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentScale = 1f
    private var isScaling = false
    private var lastGestureHintRunnable: Runnable? = null
    private val gestureHintHandler = Handler(Looper.getMainLooper())
    
    private lateinit var settingsManager: SettingsManager
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化窗口管理器
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 创建悬浮窗布局
        createFloatingView()
        
        // 初始化WebView
        setupWebView()
        
        // 初始化手势检测器
        initGestureDetectors()
    }
    
    private fun createFloatingView() {
        // 加载悬浮窗布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_webview, null)
        
        // 初始化视图组件
        webView = floatingView?.findViewById(R.id.floating_webview)
        searchInput = floatingView?.findViewById(R.id.search_input)
        progressBar = floatingView?.findViewById(R.id.progress_bar)
        gestureHintView = floatingView?.findViewById(R.id.gesture_hint)
        closeButton = floatingView?.findViewById(R.id.btn_close)
        expandButton = floatingView?.findViewById(R.id.btn_expand)
        searchButton = floatingView?.findViewById(R.id.btn_search)
        backButton = floatingView?.findViewById(R.id.btn_back)
        forwardButton = floatingView?.findViewById(R.id.btn_forward)
        refreshButton = floatingView?.findViewById(R.id.btn_refresh)
        
        // 设置初始状态
        progressBar?.visibility = View.GONE
        gestureHintView?.visibility = View.GONE
        
        // 设置搜索框
        setupSearchInput()
        
        // 设置按钮点击事件
        setupButtons()
        
        // 设置拖动处理
        setupDragHandling()
        
        // 创建窗口参数
        val params = createWindowLayoutParams()
        
        // 添加到窗口
        windowManager?.addView(floatingView, params)
        
        // 应用主题
        applyTheme()
    }
    
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }
    }
    
    private fun setupWebView() {
        webView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY)
                setEnableSmoothTransition(true)
                
                // 默认加载图片
                loadsImagesAutomatically = true
                
                // 设置缓存模式
                cacheMode = WebSettings.LOAD_DEFAULT
                
                // 设置UA
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar?.visibility = View.VISIBLE
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar?.visibility = View.GONE
                    
                    // 根据无图模式状态应用滤镜
                    if (isNoImageMode) {
                        applyImageFilter()
                    } else {
                        removeImageFilter()
                    }
                    
                    // 更新导航按钮状态
                    updateNavigationButtons()
                }
                
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // 实现广告过滤
                    if (isAdBlockEnabled && request?.url?.toString()?.containsAdUrl() == true) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progressBar?.progress = newProgress
                }
            }
            
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }
    
    private fun setupSearchInput() {
        searchInput?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                performSearch(searchInput?.text.toString())
                true
            } else {
                false
            }
        }
    }
    
    private fun setupButtons() {
        // 关闭按钮
        closeButton?.setOnClickListener {
            stopSelf()
        }
        
        // 展开/收起按钮
        expandButton?.setOnClickListener {
            toggleExpandState()
        }
        
        // 搜索按钮
        searchButton?.setOnClickListener {
            performSearch(searchInput?.text.toString())
        }
        
        // 后退按钮
        backButton?.setOnClickListener {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            }
        }
        
        // 前进按钮
        forwardButton?.setOnClickListener {
            if (webView?.canGoForward() == true) {
                webView?.goForward()
            }
        }
        
        // 刷新按钮
        refreshButton?.setOnClickListener {
            webView?.reload()
        }
    }
    
    private fun setupDragHandling() {
        val dragHandle = floatingView?.findViewById<View>(R.id.drag_handle)
        
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // 如果移动距离超过阈值，则认为是拖动
                    if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                        isMoving = true
                    }
                    
                    if (isMoving && params != null) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // 如果没有移动，则视为点击
                        toggleExpandState()
                    }
                    true
                }
                else -> false
            }
        }
        
        // 设置WebView的触摸事件，使其可以接收焦点
        webView?.setOnTouchListener { v, event ->
            // 当触摸WebView时，使悬浮窗可以接收焦点
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
            if (params != null && params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager?.updateViewLayout(floatingView, params)
            }
            
            // 处理手势
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            
            // 继续传递事件给WebView
            v.onTouchEvent(event)
        }
    }
    
    private fun toggleExpandState() {
        isExpanded = !isExpanded
        
        // 更新展开/收起按钮图标
        expandButton?.setImageResource(
            if (isExpanded) R.drawable.ic_collapse else R.drawable.ic_expand
        )
        
        // 更新窗口大小
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
        if (params != null) {
            params.width = if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            params.height = if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
            
            // 展开时移除FLAG_NOT_FOCUSABLE，使WebView可以接收输入
            if (isExpanded) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            
            windowManager?.updateViewLayout(floatingView, params)
        }
        
        // 更新UI元素可见性
        updateUIForExpandState()
    }
    
    private fun updateUIForExpandState() {
        // 搜索栏
        searchInput?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        
        // 导航按钮
        val navigationBar = floatingView?.findViewById<LinearLayout>(R.id.navigation_bar)
        navigationBar?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        
        // 更新WebView大小
        val webViewParams = webView?.layoutParams
        if (webViewParams != null) {
            webViewParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            webViewParams.height = if (isExpanded) ViewGroup.LayoutParams.MATCH_PARENT else resources.getDimensionPixelSize(R.dimen.floating_webview_collapsed_height)
            webView?.layoutParams = webViewParams
        }
    }
    
    private fun initGestureDetectors() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                
                val distanceX = e2.x - e1.x
                val distanceY = e2.y - e1.y
                
                // 检测水平滑动
                if (abs(distanceX) > abs(distanceY) && abs(velocityX) > 1000) {
                    if (distanceX > 0 && webView?.canGoBack() == true) {
                        showGestureHint("返回上一页")
                        webView?.goBack()
                        return true
                    } else if (distanceX < 0 && webView?.canGoForward() == true) {
                        showGestureHint("前进下一页")
                        webView?.goForward()
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                webView?.let { webView ->
                    // 获取屏幕高度和点击位置
                    val screenHeight = webView.height
                    val tapY = e.y

                    // 判断点击位置是在屏幕上半部分还是下半部分
                    val scrollToTop = tapY < screenHeight / 2

                    webView.evaluateJavascript("""
                        (function() {
                            window.scrollTo({
                                top: ${if (scrollToTop) "0" else "document.documentElement.scrollHeight"},
                                behavior: 'smooth'
                            });
                            return '${if (scrollToTop) "top" else "bottom"}';
                        })()
                    """) { result ->
                        val destination = result?.replace("\"", "") ?: "top"
                        showGestureHint(if (destination == "top") "返回顶部" else "滚动到底部")
                    }
                }
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var baseScale = 1f
            private var lastSpan = 0f
            
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                webView?.let { baseScale = it.scale }
                lastSpan = detector.currentSpan
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // 计算手指间距离的变化比例
                val spanRatio = detector.currentSpan / lastSpan
                lastSpan = detector.currentSpan
                
                // 使用比例计算新的缩放值，并添加阻尼效果
                val dampingFactor = 0.8f // 阻尼系数，使缩放更平滑
                val scaleFactor = 1f + (spanRatio - 1f) * dampingFactor
                
                val newScale = baseScale * scaleFactor
                
                // 限制缩放范围并应用缩放
                if (newScale in 0.1f..5.0f) {
                    webView?.setInitialScale((newScale * 100).toInt())
                    baseScale = newScale
                    
                    // 只在缩放比例变化显著时显示提示
                    if (abs(newScale - currentScale) > 0.02f) {
                        showGestureHint("缩放: ${(newScale * 100).toInt()}%")
                        currentScale = newScale
                    }
                    return true
                }
                return false
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                webView?.let { baseScale = it.scale }
            }
        })
    }
    
    private fun showGestureHint(message: String) {
        // 取消之前的提示
        lastGestureHintRunnable?.let { gestureHintHandler.removeCallbacks(it) }
        
        // 显示新提示
        gestureHintView?.apply {
            text = message
            alpha = 1f
            visibility = View.VISIBLE
            
            // 创建淡出动画
            animate()
                .alpha(0f)
                .setDuration(1000)
                .setStartDelay(500)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }
        
        // 设置自动隐藏
        lastGestureHintRunnable = Runnable {
            gestureHintView?.visibility = View.GONE
        }
        gestureHintHandler.postDelayed(lastGestureHintRunnable!!, 1500)
    }
    
    private fun performSearch(query: String) {
        if (query.isNotEmpty()) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.baidu.com/s?wd=$encodedQuery"
            webView?.loadUrl(searchUrl)
            
            // 隐藏键盘
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchInput?.windowToken, 0)
        }
    }
    
    private fun openSearchEngine(engine: SearchEngine, query: String = "") {
        if (query.isEmpty()) {
            // 如果没有查询文本，直接打开搜索引擎主页
            webView?.loadUrl(engine.url.replace("{query}", "").replace("search?q=", "")
                .replace("search?query=", "")
                .replace("search?word=", "")
                .replace("s?wd=", ""))
        } else {
            // 有查询文本，使用搜索引擎进行搜索
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = engine.url.replace("{query}", encodedQuery)
            webView?.loadUrl(searchUrl)
        }
    }
    
    private fun updateNavigationButtons() {
        webView?.let { webView ->
            backButton?.isEnabled = webView.canGoBack()
            backButton?.alpha = if (webView.canGoBack()) 1.0f else 0.5f
            
            forwardButton?.isEnabled = webView.canGoForward()
            forwardButton?.alpha = if (webView.canGoForward()) 1.0f else 0.5f
        }
    }
    
    private fun applyTheme() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        // 设置背景颜色
        floatingView?.setBackgroundColor(
            ContextCompat.getColor(this, if (isDarkMode) R.color.floating_background_dark else R.color.floating_background_light)
        )
        
        // 设置文本颜色
        searchInput?.setTextColor(
            ContextCompat.getColor(this, if (isDarkMode) R.color.text_color_dark else R.color.text_color_light)
        )
        
        // 设置图标颜色
        val iconColor = ContextCompat.getColor(this, if (isDarkMode) R.color.icon_color_dark else R.color.icon_color_light)
        closeButton?.setColorFilter(iconColor)
        expandButton?.setColorFilter(iconColor)
        searchButton?.setColorFilter(iconColor)
        backButton?.setColorFilter(iconColor)
        forwardButton?.setColorFilter(iconColor)
        refreshButton?.setColorFilter(iconColor)
        
        // 设置进度条颜色
        progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (isDarkMode) R.color.progress_color_dark else R.color.progress_color_light)
        )
    }
    
    private fun applyImageFilter() {
        val js = """
            javascript:(function() {
                var css = `
                    img, picture, video, canvas {
                        filter: grayscale(100%) opacity(70%) !important;
                        transition: filter 0.3s ease-in-out !important;
                    }
                    img:hover, picture:hover, video:hover, canvas:hover {
                        filter: grayscale(0%) opacity(100%) !important;
                    }
                `;
                var style = document.createElement('style');
                style.id = 'low-image-style';
                style.type = 'text/css';
                style.appendChild(document.createTextNode(css));
                document.head.appendChild(style);
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private fun removeImageFilter() {
        val js = """
            javascript:(function() {
                var style = document.getElementById('low-image-style');
                if (style) {
                    style.remove();
                }
            })()
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }
    
    private fun String.containsAdUrl(): Boolean {
        val adPatterns = listOf(
            "ads", "analytics", "tracker", "doubleclick",
            "pagead", "banner", "popup", "stats"
        )
        return adPatterns.any { this.contains(it, ignoreCase = true) }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            // 处理传入的URL
            val url = it.getStringExtra("url")
            if (!url.isNullOrEmpty()) {
                webView?.loadUrl(url)
            }
            
            // 处理传入的搜索查询
            val query = it.getStringExtra("query")
            if (!query.isNullOrEmpty()) {
                searchInput?.setText(query)
                performSearch(query)
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        // 移除悬浮窗
        windowManager?.removeView(floatingView)
        
        // 清理WebView
        webView?.apply {
            clearHistory()
            clearCache(true)
            clearFormData()
            destroy()
        }
        
        super.onDestroy()
    }
} 