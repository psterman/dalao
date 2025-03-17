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
import android.graphics.Color
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.util.DisplayMetrics

class FloatingWebViewService : Service() {
    companion object {
        private const val TAG = "FloatingWebViewService"
        private const val EDGE_DETECTION_THRESHOLD = 24f // dp
        private const val EDGE_SCALE_RATIO = 0.2f // 贴边后缩放到20%
        private const val ANIMATION_DURATION = 250L
    }
    
    private lateinit var windowManager: WindowManager
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
    private var floatingTitle: TextView? = null
    private var resizeHandle: View? = null
    
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
    
    private var initialWidth = 0
    private var initialHeight = 0
    private var minWidth = 0
    private var minHeight = 0
    
    private var isResizing = false
    
    // 添加贴边隐藏相关变量
    private var isHidden = false
    private var lastVisibleX = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var edgeSnapAnimator: ValueAnimator? = null
    
    // 添加变量保存原始状态
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalX = 0
    private var originalY = 0
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化窗口管理器
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // 获取屏幕尺寸
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            screenWidth = windowMetrics.bounds.width()
            screenHeight = windowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }
        
        // 创建悬浮窗布局
        createFloatingView()
        
        // 初始化WebView
        setupWebView()
        
        // 初始化手势检测器
        initGestureDetectors()
        
        // 确保布局正确应用
        Handler(Looper.getMainLooper()).postDelayed({
            floatingView?.requestLayout()
            webView?.requestLayout()
            webView?.invalidate()
        }, 100)
    }
    
    private fun createFloatingView() {
        try {
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
            floatingTitle = floatingView?.findViewById(R.id.floating_title)
            resizeHandle = floatingView?.findViewById(R.id.resize_handle)
            
            // 设置初始状态
            progressBar?.visibility = View.GONE
            gestureHintView?.visibility = View.GONE
            
            // 设置搜索框
            setupSearchInput()
            
            // 设置按钮点击事件
            setupButtons()
            
            // 设置拖动处理
            setupDragHandling()
            
            // 设置调整大小处理
            setupResizeHandling()
            
            // 获取最小尺寸
            minWidth = resources.getDimensionPixelSize(R.dimen.floating_webview_min_width)
            minHeight = resources.getDimensionPixelSize(R.dimen.floating_webview_min_height)
            
            // 创建窗口参数
            val params = createWindowLayoutParams()
            
            // 添加到窗口
            windowManager.addView(floatingView, params)
            
            // 应用主题
            applyTheme()
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
        }
    }
    
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        // 计算初始宽高（屏幕宽度的90%，高度为屏幕高度的60%）
        val initialWidth = (screenWidth * 0.9).toInt()
        val initialHeight = (screenHeight * 0.6).toInt()
        
        return WindowManager.LayoutParams(
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else initialWidth,
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else initialHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 设置初始位置为屏幕中央
            gravity = Gravity.CENTER
            x = 0
            y = 0
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
                
                // 设置移动版UA
                userAgentString = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36"
                
                // 文本缩放比例适合移动设备
                textZoom = 100
                
                // 启用数据库存储API
                databaseEnabled = true
                
                // 启用地理位置
                setGeolocationEnabled(true)
                
                // 设置默认文本编码
                defaultTextEncodingName = "UTF-8"
                
                // 允许混合内容
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                
                // 设置默认背景色为白色，避免黑屏
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    forceDark = WebSettings.FORCE_DARK_OFF
                }
            }
            
            // 设置WebView背景为白色
            setBackgroundColor(Color.WHITE)
            
            // 修改触摸事件处理
            setOnTouchListener { view, event ->
                if (isHidden) {
                    // 如果悬浮窗是隐藏状态，阻止所有触摸事件并传递给悬浮窗
                    floatingView?.onTouchEvent(event)
                    true
                } else {
                    // 正常状态下不拦截触摸事件
                    false
                }
            }

            // 修改 WebViewClient 设置，避免 null 赋值错误
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar?.visibility = View.GONE
                    
                    // 确保WebView可见
                    view?.visibility = View.VISIBLE
                    
                    // 根据无图模式状态应用滤镜
                    if (isNoImageMode) {
                        applyImageFilter()
                    } else {
                        removeImageFilter()
                    }
                    
                    // 更新导航按钮状态
                    updateNavigationButtons()
                    
                    // 根据悬浮窗状态设置网页是否可滚动
                    val js = if (isHidden) {
                        """
                        javascript:(function() {
                            document.body.style.overflow = 'hidden';
                            document.documentElement.style.overflow = 'hidden';
                        })()
                        """
                    } else {
                        """
                        javascript:(function() {
                            document.body.style.overflow = '';
                            document.documentElement.style.overflow = '';
                        })()
                        """
                    }
                    view?.evaluateJavascript(js, null)
                    
                    // 注入JavaScript以确保内容正确显示
                    val displayJs = """
                        javascript:(function() {
                            document.body.style.backgroundColor = 'white';
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (!meta) {
                                meta = document.createElement('meta');
                                meta.name = 'viewport';
                                document.head.appendChild(meta);
                            }
                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                        })()
                    """.trimIndent()
                    view?.evaluateJavascript(displayJs, null)
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
                
                // 处理所有链接在WebView内打开
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
            
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progressBar?.progress = newProgress
                }
                
                // 处理网页标题变化
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    // 更新悬浮窗标题
                    floatingTitle?.text = title ?: getString(R.string.app_name)
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
            safelyStopService()
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
        
        // 为整个悬浮窗添加触摸监听
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置
                    initialX = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false

                    // 取消任何正在进行的动画
                    edgeSnapAnimator?.cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 如果是隐藏状态，不处理移动
                    if (isHidden) {
                        return@setOnTouchListener true
                    }

                    val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                        isMoving = true
                    }
                    
                    if (isMoving && params != null) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        
                        // 限制在屏幕范围内
                        params.x = params.x.coerceIn(
                            -params.width / 3,
                            screenWidth - params.width * 2 / 3
                        )
                        params.y = params.y.coerceIn(0, screenHeight - params.height)
                        
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "更新悬浮窗位置失败", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // 如果是点击（没有移动）且是隐藏状态，恢复显示并移动到屏幕中央
                        if (isHidden) {
                            animateShowToCenter()
                            return@setOnTouchListener true
                        }
                    } else {
                        // 如果是拖动结束且不是隐藏状态，检查是否需要贴边
                        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                        if (params != null && !isHidden) {
                            checkEdgeAndSnap(params)
                        }
                    }
                    isMoving = false
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupResizeHandling() {
        resizeHandle?.setOnTouchListener { _, event ->
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始尺寸和触摸位置
                    initialWidth = params?.width ?: 0
                    initialHeight = params?.height ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isResizing = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing && params != null) {
                        // 计算新的宽高
                        val newWidth = (initialWidth + (event.rawX - initialTouchX)).toInt()
                        val newHeight = (initialHeight + (event.rawY - initialTouchY)).toInt()
                        
                        // 确保不小于最小尺寸
                        params.width = maxOf(newWidth, minWidth)
                        params.height = maxOf(newHeight, minHeight)
                        
                        // 更新布局
                        windowManager.updateViewLayout(floatingView, params)
                        
                        // 显示尺寸提示
                        showGestureHint("${params.width} x ${params.height}")
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false
                    true
                }
                else -> false
            }
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
            // 获取屏幕尺寸
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            if (isExpanded) {
                // 全屏模式 - 使用屏幕实际尺寸而不是MATCH_PARENT
                params.width = screenWidth
                params.height = screenHeight
                
                // 重置位置到屏幕左上角
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 0
                params.y = 0
                
                // 展开时移除FLAG_NOT_FOCUSABLE，使WebView可以接收输入
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                
                // 隐藏调整大小控制点
                resizeHandle?.visibility = View.GONE
                
                // 显示搜索栏和导航栏
                val searchBar = floatingView?.findViewById<LinearLayout>(R.id.search_bar)
                searchBar?.visibility = View.VISIBLE
                
                val navigationBar = floatingView?.findViewById<LinearLayout>(R.id.navigation_bar)
                navigationBar?.visibility = View.VISIBLE
            } else {
                // 恢复到默认大小
                params.width = (screenWidth * 0.9).toInt()
                params.height = (screenHeight * 0.6).toInt()
                
                // 恢复到屏幕中央
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = 0
                
                // 非全屏模式添加FLAG_NOT_FOCUSABLE
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                
                // 显示调整大小控制点
                resizeHandle?.visibility = View.VISIBLE
                
                // 隐藏搜索栏和导航栏
                val searchBar = floatingView?.findViewById<LinearLayout>(R.id.search_bar)
                searchBar?.visibility = View.GONE
                
                val navigationBar = floatingView?.findViewById<LinearLayout>(R.id.navigation_bar)
                navigationBar?.visibility = View.GONE
            }
            
            // 更新布局
            windowManager.updateViewLayout(floatingView, params)
            
            // 强制重新布局并刷新WebView
            floatingView?.requestLayout()
            webView?.requestLayout()
            webView?.invalidate()
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
        // 获取当前主题模式
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        // 获取主题颜色
        val primaryColor = ContextCompat.getColor(this, if (isDarkMode) R.color.colorPrimaryDark else R.color.colorPrimary)
        val accentColor = ContextCompat.getColor(this, R.color.colorAccent)
        val backgroundColor = ContextCompat.getColor(this, if (isDarkMode) R.color.floating_background_dark else R.color.floating_background_light)
        val textColor = ContextCompat.getColor(this, if (isDarkMode) R.color.text_color_dark else R.color.text_color_light)
        val iconColor = ContextCompat.getColor(this, if (isDarkMode) R.color.icon_color_dark else R.color.icon_color_light)
        
        // 应用到CardView背景
        val cardView = floatingView as? androidx.cardview.widget.CardView
        cardView?.setCardBackgroundColor(backgroundColor)
        cardView?.cardElevation = resources.getDimension(R.dimen.floating_card_elevation)
        cardView?.radius = resources.getDimension(R.dimen.floating_card_corner_radius)
        
        // 应用到拖动区域
        val dragHandle = floatingView?.findViewById<View>(R.id.drag_handle)
        dragHandle?.setBackgroundColor(primaryColor)
        
        // 应用到搜索栏
        val searchBar = floatingView?.findViewById<View>(R.id.search_bar)
        searchBar?.setBackgroundColor(if (isDarkMode) primaryColor else ContextCompat.getColor(this, R.color.search_bar_background))
        
        // 应用到搜索输入框
        searchInput?.setTextColor(textColor)
        searchInput?.setHintTextColor(textColor.withAlpha(0.6f))
        val searchInputBackground = searchInput?.background
        if (searchInputBackground is GradientDrawable) {
            searchInputBackground.setColor(backgroundColor.withAlpha(0.8f))
            searchInputBackground.setStroke(1, textColor.withAlpha(0.2f))
        }
        
        // 应用到导航栏
        val navigationBar = floatingView?.findViewById<View>(R.id.navigation_bar)
        navigationBar?.setBackgroundColor(if (isDarkMode) primaryColor else ContextCompat.getColor(this, R.color.navigation_bar_background))
        
        // 应用到按钮图标
        val buttons = listOf(closeButton, expandButton, searchButton, backButton, forwardButton, refreshButton)
        buttons.forEach { button ->
            button?.setColorFilter(iconColor)
            button?.background = createRippleDrawable(iconColor.withAlpha(0.1f))
        }
        
        // 应用到进度条
        progressBar?.progressTintList = android.content.res.ColorStateList.valueOf(accentColor)
        
        // 应用到WebView
        webView?.setBackgroundColor(backgroundColor)
    }
    
    // 创建水波纹效果背景
    private fun createRippleDrawable(rippleColor: Int): android.graphics.drawable.RippleDrawable? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.WHITE)
            }
            return android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor),
                null,
                mask
            )
        }
        return null
    }
    
    // 颜色透明度调整
    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt()
        return this and 0x00FFFFFF or (a shl 24)
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
                // 确保WebView可见
                webView?.visibility = View.VISIBLE
                
                // 延迟加载URL，确保WebView已经初始化完成
                Handler(Looper.getMainLooper()).postDelayed({
                    webView?.loadUrl(url)
                }, 100)
            }
            
            // 处理传入的搜索查询
            val query = it.getStringExtra("query")
            if (!query.isNullOrEmpty()) {
                searchInput?.setText(query)
                
                // 延迟执行搜索，确保WebView已经初始化完成
                Handler(Looper.getMainLooper()).postDelayed({
                    performSearch(query)
                }, 100)
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        try {
            // 1. 首先停止所有正在进行的动画
            edgeSnapAnimator?.cancel()
            
            // 2. 移除 WebView 的父视图引用
            (webView?.parent as? ViewGroup)?.removeView(webView)
            
            // 3. 安全地清理 WebView
            webView?.apply {
                // 停止加载
                stopLoading()
                
                // 清除回调 - 使用空对象而不是 null
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {}
                
                // 加载空白页面
                loadUrl("about:blank")
                
                // 清理数据
                clearHistory()
                clearCache(true)
                clearFormData()
                clearSslPreferences()
                
                // 移除所有 JavaScript 接口
                removeJavascriptInterface("android")
                
                // 销毁 WebView
                onPause()
                Handler(Looper.getMainLooper()).post {
                    destroy()
                }
            }
            
            // 4. 从窗口管理器中移除视图
            if (floatingView?.isAttachedToWindow == true) {
                try {
                    windowManager.removeView(floatingView)
                } catch (e: Exception) {
                    Log.e(TAG, "移除悬浮窗失败", e)
                }
            }
            
            // 5. 清理其他资源
            gestureHintHandler.removeCallbacksAndMessages(null)
            lastGestureHintRunnable = null
            
            // 6. 解除引用
            webView = null
            floatingView = null
            
        } catch (e: Exception) {
            Log.e(TAG, "销毁悬浮窗失败", e)
        } finally {
            // 确保调用父类的 onDestroy
            super.onDestroy()
        }
    }

    // 添加一个新的方法来安全地关闭服务
    private fun safelyStopService() {
        try {
            // 1. 如果悬浮窗处于隐藏状态，先恢复它
            if (isHidden) {
                // 取消当前动画
                edgeSnapAnimator?.cancel()
                
                // 恢复原始状态
                val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.width = originalWidth
                    params.height = originalHeight
                    params.x = originalX
                    params.y = originalY
                    try {
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复悬浮窗状态失败", e)
                    }
                }
            }
            
            // 2. 延迟一帧后停止服务
            Handler(Looper.getMainLooper()).post {
                stopSelf()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "安全停止服务失败", e)
            // 如果出现异常，直接停止服务
            stopSelf()
        }
    }

    private fun checkEdgeAndSnap(params: WindowManager.LayoutParams) {
        val edgeThresholdPx = EDGE_DETECTION_THRESHOLD * resources.displayMetrics.density
        val viewWidth = floatingView?.width ?: 0
        
        // 检查是否靠近左边缘
        if (params.x < edgeThresholdPx) {
            animateToEdge(true) // 贴左边
        } 
        // 检查是否靠近右边缘
        else if (params.x + viewWidth > screenWidth - edgeThresholdPx) {
            animateToEdge(false) // 贴右边
        }
    }
    
    private fun animateToEdge(isLeft: Boolean) {
        if (!::windowManager.isInitialized || floatingView == null) return
        
        edgeSnapAnimator?.cancel()
        
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        val startX = params.x
        val startWidth = params.width
        val startHeight = params.height
        
        // 保存原始状态（仅在首次隐藏时保存）
        if (!isHidden) {
            originalWidth = startWidth
            originalHeight = startHeight
            originalX = startX
            originalY = params.y
            
            // 记录最后可见位置，用于恢复
            lastVisibleX = startX
        }
        
        // 计算目标尺寸（缩小到原始尺寸的20%）
        val targetWidth = (originalWidth * EDGE_SCALE_RATIO).toInt().coerceAtLeast(minWidth / 2)
        val targetHeight = (originalHeight * EDGE_SCALE_RATIO).toInt().coerceAtLeast(minHeight / 2)
        
        // 计算目标位置（完全贴边）
        val targetX = if (isLeft) 0 else screenWidth - targetWidth
        
        edgeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                if (!::windowManager.isInitialized || floatingView == null) {
                    cancel()
                    return@addUpdateListener
                }

                val fraction = animator.animatedValue as Float
                
                try {
                    params.x = lerp(startX, targetX, fraction)
                    params.width = lerp(startWidth, targetWidth, fraction)
                    params.height = lerp(startHeight, targetHeight, fraction)
                    
                    windowManager.updateViewLayout(floatingView, params)
                    updateViewsAlpha(1f - fraction * 0.5f) // 减少透明度变化，保持更好的可见性
                } catch (e: Exception) {
                    Log.e(TAG, "更新悬浮窗失败", e)
                    cancel()
                }
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    // 动画开始时不立即设置隐藏状态
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!animation.isRunning) {
                        isHidden = true
                        // 禁用 WebView 的滚动
                        disableWebViewScroll()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    // 动画取消时恢复原始状态
                    try {
                        params.width = originalWidth
                        params.height = originalHeight
                        updateViewsAlpha(1f)
                        windowManager.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复原始状态失败", e)
                    }
                    isHidden = false
                    enableWebViewScroll()
                }
            })
            
            start()
        }
    }
    
    private fun animateShowToCenter() {
        if (!::windowManager.isInitialized || floatingView == null) return
        
        edgeSnapAnimator?.cancel()
        
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        val startX = params.x
        val startY = params.y
        val startWidth = params.width
        val startHeight = params.height
        
        // 使用保存的原始尺寸
        val targetWidth = originalWidth.coerceAtLeast(minWidth)
        val targetHeight = originalHeight.coerceAtLeast(minHeight)
        
        // 计算屏幕中央位置
        val targetX = (screenWidth - targetWidth) / 2
        val targetY = (screenHeight - targetHeight) / 2
        
        edgeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = OvershootInterpolator(0.8f)
            
            addUpdateListener { animator ->
                if (!::windowManager.isInitialized || floatingView == null) {
                    cancel()
                    return@addUpdateListener
                }

                val fraction = animator.animatedValue as Float
                
                try {
                    params.x = lerp(startX, targetX, fraction)
                    params.y = lerp(startY, targetY, fraction)
                    params.width = lerp(startWidth, targetWidth, fraction)
                    params.height = lerp(startHeight, targetHeight, fraction)
                    
                    windowManager.updateViewLayout(floatingView, params)
                    updateViewsAlpha(0.5f + fraction * 0.5f) // 从半透明恢复到完全不透明
                } catch (e: Exception) {
                    Log.e(TAG, "更新悬浮窗失败", e)
                    cancel()
                }
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    isHidden = false
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!animation.isRunning) {
                        isHidden = false
                        updateViewsAlpha(1f)
                        enableWebViewScroll()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    isHidden = false
                    updateViewsAlpha(1f)
                    enableWebViewScroll()
                }
            })
            
            start()
        }
    }
    
    // 添加线性插值方法
    private fun lerp(start: Int, end: Int, fraction: Float): Int {
        return start + ((end - start) * fraction).toInt()
    }
    
    // 更新内部视图的透明度
    private fun updateViewsAlpha(alpha: Float) {
        webView?.alpha = alpha
        searchInput?.alpha = alpha
        progressBar?.alpha = alpha
        closeButton?.alpha = alpha
        expandButton?.alpha = alpha
        searchButton?.alpha = alpha
        backButton?.alpha = alpha
        forwardButton?.alpha = alpha
        refreshButton?.alpha = alpha
        floatingTitle?.alpha = alpha
        
        // 保持拖动手柄始终可见，但略微透明
        floatingView?.findViewById<View>(R.id.drag_handle)?.alpha = maxOf(0.3f, alpha)
    }

    // 添加辅助方法来控制 WebView 的滚动
    private fun enableWebViewScroll() {
        webView?.evaluateJavascript("""
            (function() {
                document.body.style.overflow = '';
                document.documentElement.style.overflow = '';
            })()
        """, null)
    }

    private fun disableWebViewScroll() {
        webView?.evaluateJavascript("""
            (function() {
                document.body.style.overflow = 'hidden';
                document.documentElement.style.overflow = 'hidden';
            })()
        """, null)
    }
} 