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
import android.view.animation.PathInterpolator
import android.graphics.Rect
import androidx.core.graphics.ColorUtils
import android.view.animation.Interpolator
import android.graphics.drawable.Drawable
import com.example.aifloatingball.R

class FloatingWebViewService : Service() {
    companion object {
        private const val TAG = "FloatingWebViewService"
        private const val EDGE_DETECTION_THRESHOLD = 24f // 边缘检测阈值（dp）
        private const val EDGE_SCALE_RATIO = 0.2f // 贴边时缩小比例
        private const val ANIMATION_DURATION = 350L // 动画持续时间
        private const val SPRING_STIFFNESS = 300f // 弹簧刚度
        private const val SPRING_DAMPING = 0.7f // 弹簧阻尼
        private const val EDGE_NONE = 0
        private const val EDGE_LEFT = 1
        private const val EDGE_RIGHT = 2
        private const val DEFAULT_EDGE_THRESHOLD = 24f // dp
        private const val DEFAULT_CORNER_RADIUS = 16f // dp
        private const val DEFAULT_BACKGROUND_COLOR = 0xFFFFFFFF.toInt() // 白色
        private const val DEFAULT_BORDER_COLOR = 0x1A000000.toInt() // 半透明黑色
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
    
    // 添加变量跟踪最后一次按下的时间
    private var lastActionDownTime = 0L
    
    // 添加一个新的变量来保存悬浮窗的默认尺寸
    private var defaultWidth = 0
    private var defaultHeight = 0
    
    private var isAnimating = false
    private var edgePosition = EDGE_NONE
    
    // 在类的成员变量部分添加
    private lateinit var halfCircleWindow: HalfCircleFloatingWindow
    
    // 修改成员变量声明
    private var originalBackground: android.graphics.drawable.Drawable? = null
    
    private fun getEdgeThreshold(): Int {
        return try {
            resources.getDimensionPixelSize(R.dimen.edge_snap_threshold)
        } catch (e: Exception) {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    private fun getCornerRadius(): Float {
        return try {
            resources.getDimensionPixelSize(R.dimen.floating_window_corner_radius).toFloat()
        } catch (e: Exception) {
            12f * resources.displayMetrics.density
        }
    }

    private fun getBackgroundColor(): Int {
        return try {
            ContextCompat.getColor(this, R.color.floating_window_background)
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    private fun getBorderColor(): Int {
        return try {
            ContextCompat.getColor(this, R.color.floating_window_border)
        } catch (e: Exception) {
            Color.parseColor("#1A000000")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        try {
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
            
            // 设置默认尺寸
            defaultWidth = (screenWidth * 0.9f).toInt()
            defaultHeight = (screenHeight * 0.6f).toInt()
        
        // 创建悬浮窗布局
        createFloatingView()
        
        // 初始化WebView
        setupWebView()
        
        // 初始化手势检测器
        initGestureDetectors()
        
        // 确保布局正确应用
        Handler(Looper.getMainLooper()).postDelayed({
                try {
            floatingView?.requestLayout()
            webView?.requestLayout()
            webView?.invalidate()
                } catch (e: Exception) {
                    Log.e(TAG, "布局刷新失败", e)
                }
        }, 100)
            
            // 添加全局错误处理
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e(TAG, "未捕获的异常: ${throwable.message}", throwable)
                try {
                    // 尝试恢复状态
                    isHidden = false
                    
                    // 如果是在动画过程中崩溃，尝试直接恢复
                    if (edgeSnapAnimator?.isRunning == true) {
                        edgeSnapAnimator?.cancel()
                        recoverFromFailure()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "错误恢复失败", e)
                }
                
                // 调用原始的异常处理器
                Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
            }
            
            // 添加一个全局点击监听器，确保在任何情况下点击都能恢复
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    floatingView?.setOnClickListener {
                        try {
                            if (isHidden) {
                                Log.d(TAG, "通过全局点击监听器恢复悬浮窗")
                                animateShowToCenter()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "全局点击恢复失败", e)
                            // 尝试直接恢复
                            recoverFromFailure()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "设置全局点击监听器失败", e)
                }
            }, 500)
        
        halfCircleWindow = HalfCircleFloatingWindow(this)
        
        // 保存原始背景
        originalBackground = floatingView?.background
        } catch (e: Exception) {
            Log.e(TAG, "onCreate失败", e)
        }
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
        
        return WindowManager.LayoutParams(
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else defaultWidth,
            if (isExpanded) WindowManager.LayoutParams.MATCH_PARENT else defaultHeight,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // 移除 FLAG_NOT_FOCUSABLE
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
                // 如果悬浮窗是隐藏状态，所有触摸事件都应该触发恢复
                if (isHidden) {
                    if (event.action == MotionEvent.ACTION_UP) {
                        Log.d(TAG, "WebView触摸事件在隐藏状态下被拦截，触发恢复")
                        Handler(Looper.getMainLooper()).post {
                            animateShowToCenter()
                        }
                    }
                    return@setOnTouchListener true
                }
                
                // 正常状态下不拦截触摸事件
                false
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
                    
                    // 根据悬浮窗状态设置网页是否可滚动，但保留缩放功能
                    val js = if (isHidden) {
                        """
                        javascript:(function() {
                            document.body.style.overflow = 'hidden';
                            document.documentElement.style.overflow = 'hidden';
                            
                            // 确保缩放功能可用
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (meta) {
                                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                            }
                        })()
                        """
                    } else {
                        """
                        javascript:(function() {
                            document.body.style.overflow = '';
                            document.documentElement.style.overflow = '';
                            
                            // 确保缩放功能可用
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (meta) {
                                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                            }
                        })()
                        """
                    }
                    view?.evaluateJavascript(js, null)
                    
                    // 注入JavaScript以确保内容正确显示，并保留缩放功能
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
            
           
        }
    }
    
    private fun setupSearchInput() {
        searchInput?.apply {
            setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    performSearch(text.toString())
                true
            } else {
                false
                }
            }
            
            // 添加焦点变化监听器
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    // 当获取焦点时，确保输入法显示
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            
            // 添加点击监听器
            setOnClickListener {
                // 确保可以获取焦点
                isFocusableInTouchMode = true
                requestFocus()
                
                // 显示输入法
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
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
            // 确保窗口可以获取焦点
            toggleWindowFocusableFlag(true)
            
            // 让输入框获取焦点
            searchInput?.isFocusableInTouchMode = true
            searchInput?.requestFocus()
            
            // 显示输入法
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
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
        
        // 添加双击检测
        var lastClickTime = 0L
        val doubleClickTimeout = 300L // 双击超时时间（毫秒）
        
        // 为整个悬浮窗添加触摸监听
        floatingView?.setOnTouchListener { view, event ->
            try {
                // 如果当前有输入框获取焦点，且不是从输入框开始的触摸，则清除焦点
                if (searchInput?.isFocused == true && event.action == MotionEvent.ACTION_DOWN) {
                    // 检查触摸点是否在输入框区域内
                    val searchInputLocation = IntArray(2)
                    searchInput?.getLocationOnScreen(searchInputLocation)
                    val searchInputRect = Rect(
                        searchInputLocation[0],
                        searchInputLocation[1],
                        searchInputLocation[0] + (searchInput?.width ?: 0),
                        searchInputLocation[1] + (searchInput?.height ?: 0)
                    )
                    
                    // 如果触摸点不在输入框区域内，清除焦点并隐藏键盘
                    if (!searchInputRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        searchInput?.clearFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(searchInput?.windowToken, 0)
                        
                        // 临时添加 FLAG_NOT_FOCUSABLE 以便能够拖动
                        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                        if (params != null) {
                            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "更新窗口参数失败", e)
                            }
                        }
                    } else {
                        // 如果触摸在输入框区域内，让输入框处理事件
                        return@setOnTouchListener false
                    }
                }
                
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                        // 记录初始位置
                    initialX = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                        lastActionDownTime = System.currentTimeMillis()
                        
                        // 检测双击
                        val clickTime = System.currentTimeMillis()
                        if (clickTime - lastClickTime < doubleClickTimeout) {
                            // 双击检测到，重置悬浮窗到默认尺寸并居中
                            resetToDefaultSizeAndCenter()
                            lastClickTime = 0L // 重置，避免连续触发
                            return@setOnTouchListener true
                        }
                        lastClickTime = clickTime

                        // 取消任何正在进行的动画
                        edgeSnapAnimator?.cancel()
                        
                        // 如果当前是隐藏状态，点击时恢复
                        if (halfCircleWindow.isHidden()) {
                            halfCircleWindow.animateShowToCenter(
                                floatingView ?: return@setOnTouchListener false,
                                floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false,
                                windowManager ?: return@setOnTouchListener false,
                                {
                                    // 启用 WebView 滚动回调
                                    enableWebViewScrolling()
                                },
                                {
                                    // 显示所有控件回调
                                    webView?.visibility = View.VISIBLE
                                    searchInput?.visibility = View.VISIBLE
                                    searchButton?.visibility = View.VISIBLE
                                    closeButton?.visibility = View.VISIBLE
                                    expandButton?.visibility = View.VISIBLE
                                }
                            )
                            return@setOnTouchListener true
                        }
                        
                        return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                        // 如果移动距离超过阈值，标记为拖动
                        if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isMoving = true
                    }
                    
                        // 处理拖动状态
                        val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                    if (isMoving && params != null) {
                            // 更新位置
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
                                
                                // 检查是否已经离开了边缘区域
                                val edgeThresholdPx = EDGE_DETECTION_THRESHOLD * resources.displayMetrics.density
                                val isNearLeftEdge = params.x < edgeThresholdPx
                                val isNearRightEdge = params.x + params.width > screenWidth - edgeThresholdPx
                                
                                // 如果悬浮窗处于隐藏状态，但已经离开了边缘区域，立即恢复并居中
                                if (isHidden && !isNearLeftEdge && !isNearRightEdge) {
                                    Log.d(TAG, "悬浮窗已离开边缘，恢复并居中")
                                    animateShowToCenter()
                                    
                                    // 更新初始位置和触摸点，以便继续拖动
                                    initialX = params.x
                                    initialY = params.y
                                    initialTouchX = event.rawX
                                    initialTouchY = event.rawY
                                }
                                
                                // 如果是隐藏状态且正在移动，立即恢复到正常大小
                                if (halfCircleWindow.isHidden() && isMoving) {
                                    halfCircleWindow.restoreFromEdgeImmediately(
                                        floatingView ?: return@setOnTouchListener false,
                                        floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false,
                                        windowManager ?: return@setOnTouchListener false,
                                        {
                                            // 启用 WebView 滚动回调
                                            enableWebViewScrolling()
                                        },
                                        {
                                            // 显示所有控件回调
                                            webView?.visibility = View.VISIBLE
                                            searchInput?.visibility = View.VISIBLE
                                            searchButton?.visibility = View.VISIBLE
                                            closeButton?.visibility = View.VISIBLE
                                            expandButton?.visibility = View.VISIBLE
                                        }
                                    )
                                    
                                    // 更新初始位置和触摸点，以便继续拖动
                                    val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                                    initialX = params?.x ?: 0
                                    initialY = params?.y ?: 0
                                    initialTouchX = event.rawX
                                    initialTouchY = event.rawY
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "更新悬浮窗位置失败", e)
                            }
                        }
                        
                        return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                        val currentTime = System.currentTimeMillis()
                        val isClick = !isMoving && (currentTime - lastActionDownTime < 300)
                        
                        if (isClick) {
                            // 如果是点击（没有移动）且是隐藏状态，恢复显示并居中
                            if (isHidden) {
                                Log.d(TAG, "点击了缩小的悬浮窗，恢复显示并居中")
                                try {
                                    Handler(Looper.getMainLooper()).post {
                                        try {
                                            animateShowToCenter()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "恢复动画执行失败", e)
                                            // 确保状态正确
                                            isHidden = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "发送恢复消息失败", e)
                                    // 尝试直接恢复状态
                                    isHidden = false
                                }
                                return@setOnTouchListener true
                            }
                        } else if (isMoving) {
                            // 如果是拖动结束，检查是否需要贴边
                            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                            if (params != null) {
                                try {
                                    // 检查当前位置是否靠近边缘
                                    val edgeThresholdPx = EDGE_DETECTION_THRESHOLD * resources.displayMetrics.density
                                    val viewWidth = floatingView?.width ?: 0
                                    val isNearLeftEdge = params.x < edgeThresholdPx
                                    val isNearRightEdge = params.x + viewWidth > screenWidth - edgeThresholdPx
                                    
                                    // 只有在非隐藏状态下，且当前位置靠近边缘时，才执行贴边操作
                                    if (!isHidden && (isNearLeftEdge || isNearRightEdge)) {
                                        Log.d(TAG, "拖动结束，靠近边缘，执行贴边")
                                        animateToEdge(isNearLeftEdge)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "检查边缘贴边失败", e)
                                }
                            }
                        }
                        isMoving = false
                        
                        // 拖动结束后，移除 FLAG_NOT_FOCUSABLE 标志，以便输入框可以获取焦点
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
                        if (params != null) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "更新窗口参数失败", e)
                            }
                        }
                        
                        return@setOnTouchListener true
                    }
                    else -> return@setOnTouchListener false
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理触摸事件失败", e)
                return@setOnTouchListener false
            }
        }
        
        // 为WebView添加单独的点击监听，确保在缩小状态下点击WebView也能恢复
        webView?.setOnClickListener {
            try {
                if (isHidden) {
                    Log.d(TAG, "点击了WebView，恢复悬浮窗")
                    Handler(Looper.getMainLooper()).post {
                        try {
                            animateShowToCenter()
                        } catch (e: Exception) {
                            Log.e(TAG, "WebView点击恢复动画执行失败", e)
                            // 确保状态正确
                            isHidden = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebView点击处理失败", e)
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

    private fun checkEdgeAndSnap(x: Int, y: Int) {
        if (isHidden || isAnimating) return
        
        val edgeThreshold = getEdgeThreshold()
        
        // 检查是否靠近左边缘
        if (x <= edgeThreshold) {
            animateToEdge(true) // 靠左边缘
            return
        }
        
        // 检查是否靠近右边缘
        if (x >= screenWidth - edgeThreshold) {
            animateToEdge(false) // 靠右边缘
            return
        }
    }
    
    private fun animateToEdge(isLeft: Boolean) {
        try {
            // 取消之前的动画
            edgeSnapAnimator?.cancel()
            
            // 获取当前布局参数
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // 记录原始尺寸，用于恢复
            originalWidth = params.width
            originalHeight = params.height
            
            // 计算半圆形的尺寸 - 高度不变，宽度变为高度的一半
            val targetWidth = params.height / 2
            val targetHeight = params.height
            
            // 计算目标位置 - 靠边
            val targetX = if (isLeft) 0 else screenWidth - targetWidth
            val targetY = params.y
            
            // 创建动画
            edgeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = appleInterpolation()
                
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    
                    try {
                        // 更新布局参数
                        val currentParams = floatingView?.layoutParams as? WindowManager.LayoutParams
                        if (currentParams != null) {
                            // 计算当前宽度、高度和位置
                            currentParams.width = lerp(params.width, targetWidth, progress)
                            currentParams.height = lerp(params.height, targetHeight, progress)
                            currentParams.x = lerp(params.x, targetX, progress)
                            currentParams.y = lerp(params.y, targetY, progress)
                            
                            // 更新悬浮窗
                            windowManager?.updateViewLayout(floatingView, currentParams)
                            
                            // 更新视图透明度
                            updateViewsAlpha(1f - progress * 0.5f)
                            
                            // 应用半圆形状
                            applyHalfCircleShape(isLeft, progress)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "更新布局参数失败", e)
                    }
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isAnimating = true
                        isHidden = true
                        edgePosition = if (isLeft) EDGE_LEFT else EDGE_RIGHT
                        
                        // 禁用 WebView 的滚动，但保留缩放功能
                        disableWebViewScrolling()
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        if (!animation.isRunning) {
                            isAnimating = false
                            Log.d(TAG, "动画结束，悬浮窗已靠边")
                        }
                    }
                    
                    override fun onAnimationCancel(animation: Animator) {
                        isAnimating = false
                        isHidden = false
                        enableWebViewScrolling()
                    }
                })
                
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "靠边动画失败", e)
            recoverFromFailure()
        }
    }

    // 添加应用半圆形状的方法
    private fun applyHalfCircleShape(isLeft: Boolean, progress: Float) {
        try {
            // 获取悬浮窗视图
            val view = floatingView ?: return
            
            // 创建半圆形状
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            
            // 设置圆角 - 只在一侧设置圆角，形成半圆
            val cornerRadius = getCornerRadius()
            if (isLeft) {
                // 左侧靠边，右侧为半圆
                shape.cornerRadii = floatArrayOf(
                    0f, 0f,                    // 左上角
                    cornerRadius, cornerRadius, // 右上角
                    cornerRadius, cornerRadius, // 右下角
                    0f, 0f                     // 左下角
                )
            } else {
                // 右侧靠边，左侧为半圆
                shape.cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius, // 左上角
                    0f, 0f,                    // 右上角
                    0f, 0f,                    // 右下角
                    cornerRadius, cornerRadius  // 左下角
                )
            }
            
            // 设置背景颜色 - 使用当前主题颜色，并根据进度调整透明度
            val backgroundColor = getBackgroundColor()
            val alpha = (255 * (0.7f + 0.3f * (1f - progress))).toInt()
            shape.setColor(ColorUtils.setAlphaComponent(backgroundColor, alpha))
            
            shape.setStroke(2, getBorderColor())
            
            // 应用形状到视图背景
            view.background = shape
            
            // 调整内部视图的可见性
            val fadeProgress = Math.min(1f, progress * 1.5f)  // 加速淡出效果
            webView?.alpha = 1f - fadeProgress
            searchInput?.alpha = 1f - fadeProgress
            searchButton?.alpha = 1f - fadeProgress
            closeButton?.alpha = 1f - fadeProgress
            expandButton?.alpha = 1f  // 展开按钮始终可见
            
            // 当进度超过一半时，隐藏内部视图
            val visibility = if (fadeProgress > 0.5f) View.GONE else View.VISIBLE
            webView?.visibility = visibility
            searchInput?.visibility = visibility
            searchButton?.visibility = visibility
            closeButton?.visibility = visibility
            
            // 保持展开按钮可见
            expandButton?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "应用半圆形状失败", e)
        }
    }

    // 修改 animateShowToCenter 方法，从半圆形恢复
    private fun animateShowToCenter() {
        try {
            // 取消之前的动画
            edgeSnapAnimator?.cancel()
            
            // 获取当前布局参数
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // 计算目标位置和尺寸
            val targetWidth = originalWidth
            val targetHeight = originalHeight
            val targetX = (screenWidth - targetWidth) / 2
            val targetY = (screenHeight - targetHeight) / 3
            
            // 创建动画
            edgeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = appleInterpolation()
                
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    
                    try {
                        // 更新布局参数
                        val currentParams = floatingView?.layoutParams as? WindowManager.LayoutParams
                        if (currentParams != null) {
                            // 计算当前宽度、高度和位置
                            currentParams.width = lerp(params.width, targetWidth, progress)
                            currentParams.height = lerp(params.height, targetHeight, progress)
                            currentParams.x = lerp(params.x, targetX, progress)
                            currentParams.y = lerp(params.y, targetY, progress)
                            
                            // 更新悬浮窗
                            windowManager?.updateViewLayout(floatingView, currentParams)
                            
                            // 更新视图透明度
                            updateViewsAlpha(0.5f + progress * 0.5f)
                            
                            // 恢复正常形状
                            restoreNormalShape(progress)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "更新布局参数失败", e)
                    }
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isAnimating = true
                        
                        // 启用 WebView 的滚动和缩放
                        enableWebViewScrolling()
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isHidden = false
                        edgePosition = EDGE_NONE
                        
                        // 显示所有控件
                        webView?.visibility = View.VISIBLE
                        searchInput?.visibility = View.VISIBLE
                        searchButton?.visibility = View.VISIBLE
                        closeButton?.visibility = View.VISIBLE
                        expandButton?.visibility = View.VISIBLE
                        
                        enableWebViewScrolling()
                    }
                    
                    override fun onAnimationCancel(animation: Animator) {
                        isAnimating = false
                        
                        enableWebViewScrolling()
                    }
                })
                
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复动画失败", e)
            recoverFromFailure()
        }
    }

    // 添加恢复正常形状的方法
    private fun restoreNormalShape(progress: Float) {
        try {
            // 获取悬浮窗视图
            val view = floatingView ?: return
            
            // 创建正常矩形形状，带圆角
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            
            // 设置统一的圆角
            val cornerRadius = getCornerRadius()
            shape.cornerRadius = cornerRadius
            
            // 设置背景颜色
            val backgroundColor = getBackgroundColor()
            shape.setColor(backgroundColor)
            
            shape.setStroke(2, getBorderColor())
            
            // 应用形状到视图背景
            view.background = shape
            
            // 保存为原始背景
            originalBackground = shape
        } catch (e: Exception) {
            Log.e(TAG, "恢复正常形状失败", e)
        }
    }

    // 添加重置悬浮窗到默认尺寸并居中的方法
    private fun resetToDefaultSizeAndCenter() {
        if (!::windowManager.isInitialized || floatingView == null) return
        
        edgeSnapAnimator?.cancel()
        
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        
        val startX = params.x
        val startY = params.y
        val startWidth = params.width
        val startHeight = params.height
        
        // 使用默认尺寸
        val targetWidth = defaultWidth
        val targetHeight = defaultHeight
        
        // 计算屏幕中央位置
        val targetX = (screenWidth - targetWidth) / 2
        val targetY = (screenHeight - targetHeight) / 3 // 靠上1/3位置
        
        Log.d(TAG, "重置动画: 从 ($startX, $startY, $startWidth, $startHeight) 到 ($targetX, $targetY, $targetWidth, $targetHeight)")
        
        // 使用属性动画实现平滑过渡
        val resetAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                if (!::windowManager.isInitialized || floatingView == null) {
                    cancel()
                    return@addUpdateListener
                }

                val fraction = animator.animatedValue as Float
                
                try {
                    // 计算当前位置和尺寸
                    params.x = lerp(startX, targetX, fraction)
                    params.y = lerp(startY, targetY, fraction)
                    params.width = lerp(startWidth, targetWidth, fraction)
                    params.height = lerp(startHeight, targetHeight, fraction)
                    
                    windowManager.updateViewLayout(floatingView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "更新悬浮窗失败", e)
                    cancel()
                }
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    Log.d(TAG, "重置动画开始")
                    
                    if (isHidden) {
                        isHidden = false
                        
                        // 显示所有控件
                        webView?.visibility = View.VISIBLE
                        searchInput?.visibility = View.VISIBLE
                        searchButton?.visibility = View.VISIBLE
                        closeButton?.visibility = View.VISIBLE
                        expandButton?.visibility = View.VISIBLE
                        
                        enableWebViewScrolling()
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!animation.isRunning) {
                        Log.d(TAG, "重置动画结束")
                        
                        // 更新原始尺寸
                        originalWidth = targetWidth
                        originalHeight = targetHeight
                        originalX = targetX
                        originalY = targetY
                    }
                }
            })
            
            start()
        }
    }

    // 添加线性插值方法
    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + fraction * (end - start)
    }

    private fun lerp(start: Int, end: Int, fraction: Float): Int {
        return (start + fraction * (end - start)).toInt()
    }

    // 添加唯一的 disableWebViewScrolling 方法
    private fun disableWebViewScrolling() {
        webView?.evaluateJavascript("""
            (function() {
                // 禁用页面滚动
                document.body.style.overflow = 'hidden';
                
                // 保存当前缩放级别
                if (!window._originalZoom) {
                    window._originalZoom = document.documentElement.style.zoom || '100%';
                }
                
                // 禁用触摸事件
                document.addEventListener('touchmove', function(e) {
                    e.preventDefault();
                }, { passive: false });
            })();
        """, null)
    }

    // 添加唯一的 enableWebViewScrolling 方法
    private fun enableWebViewScrolling() {
        webView?.evaluateJavascript("""
            (function() {
                // 恢复页面滚动
                document.body.style.overflow = '';
                
                // 恢复原始缩放级别
                if (window._originalZoom) {
                    document.documentElement.style.zoom = window._originalZoom;
                }
                
                // 移除触摸事件限制
                document.removeEventListener('touchmove', function(e) {
                    e.preventDefault();
                }, { passive: false });
            })();
        """, null)
    }

    // 添加一个额外的恢复方法，用于在动画失败时直接恢复
    private fun recoverFromFailure() {
        try {
            // 获取当前布局参数
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // 重置状态
            isAnimating = false
            isHidden = false
            
            // 恢复默认尺寸和位置
            params.width = defaultWidth
            params.height = defaultHeight
            params.x = screenWidth / 2 - defaultWidth / 2
            params.y = screenHeight / 2 - defaultHeight / 2
            
            // 更新窗口布局
            windowManager?.updateViewLayout(floatingView, params)
            
            // 恢复正常形状
            val view = floatingView ?: return
            
            // 创建新的背景
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = getCornerRadius()
                setColor(getBackgroundColor())
                setStroke(2, getBorderColor())
            }
            view.background = shape
            
            // 启用WebView滚动
            enableWebViewScrolling()
        } catch (e: Exception) {
            Log.e(TAG, "从失败中恢复失败", e)
        }
    }

    // 添加新方法：立即从边缘状态恢复（不使用动画）
    private fun restoreFromEdgeImmediately() {
        try {
            // 取消之前的动画
            edgeSnapAnimator?.cancel()
            
            // 获取当前布局参数
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // 恢复到原始尺寸和位置
            params.width = originalWidth
            params.height = originalHeight
            params.x = (screenWidth - originalWidth) / 2
            params.y = (screenHeight - originalHeight) / 3
            
            // 更新布局
            windowManager?.updateViewLayout(floatingView, params)
            
            // 重置状态
            isHidden = false
            edgePosition = EDGE_NONE
            
            // 恢复控件可见性
            webView?.visibility = View.VISIBLE
            searchInput?.visibility = View.VISIBLE
            searchButton?.visibility = View.VISIBLE
            closeButton?.visibility = View.VISIBLE
            expandButton?.visibility = View.VISIBLE
            
            // 恢复透明度
            updateViewsAlpha(1f)
            
            // 启用WebView滚动和缩放
            enableWebViewScrolling()
            
        } catch (e: Exception) {
            Log.e(TAG, "立即恢复失败", e)
        }
    }

    // 添加一个新方法，用于重置悬浮窗到默认状态
    private fun resetToDefaultSize() {
        try {
            Log.d(TAG, "重置悬浮窗到默认尺寸")
            
            // 取消任何正在进行的动画
            edgeSnapAnimator?.cancel()
            
            // 确保状态正确
            isHidden = false
            
            // 恢复控件可见性
            searchInput?.visibility = View.VISIBLE
            backButton?.visibility = View.VISIBLE
            forwardButton?.visibility = View.VISIBLE
            refreshButton?.visibility = View.VISIBLE
            expandButton?.visibility = View.VISIBLE
            
            // 恢复原始圆角
            if (floatingView is CardView) {
                val cardView = floatingView as CardView
                cardView.radius = resources.getDimension(R.dimen.floating_card_corner_radius)
            }
            
            // 设置默认尺寸和位置
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.width = defaultWidth
                params.height = defaultHeight
                params.x = (screenWidth - defaultWidth) / 2
                params.y = (screenHeight - defaultHeight) / 2
                
                try {
                    windowManager.updateViewLayout(floatingView, params)
                    updateViewsAlpha(1f)
                    
                    // 更新原始尺寸记录
                    originalWidth = defaultWidth
                    originalHeight = defaultHeight
                    originalX = params.x
                    originalY = params.y
                } catch (e: Exception) {
                    Log.e(TAG, "更新悬浮窗布局失败", e)
                }
            }
            
            // 启用WebView滚动
            enableWebViewScrolling()
        } catch (e: Exception) {
            Log.e(TAG, "重置到默认尺寸失败", e)
        }
    }

    // 添加苹果风格的插值器
    private fun appleInterpolation(): Interpolator {
        return PathInterpolator(0.42f, 0f, 0.58f, 1f)
    }

    // 添加视图透明度更新方法
    private fun updateViewsAlpha(alpha: Float) {
        webView?.alpha = alpha
        searchInput?.alpha = alpha
        searchButton?.alpha = alpha
        closeButton?.alpha = alpha
        expandButton?.alpha = alpha
    }

    // 添加窗口焦点控制方法
    private fun toggleWindowFocusableFlag(focusable: Boolean) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (focusable) {
                // 启用窗口焦点
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                // 禁用窗口焦点
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            
            // 更新窗口布局
            windowManager?.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "切换窗口焦点状态失败", e)
        }
    }
} 