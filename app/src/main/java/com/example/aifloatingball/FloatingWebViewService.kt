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
import com.example.aifloatingball.model.SearchEngineShortcut
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FloatingWebViewService : Service() {
    companion object {
        private const val TAG = "FloatingWebViewService"
        private const val PREFS_NAME = "floating_window_prefs"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
        private const val KEY_WINDOW_X = "window_x"
        private const val KEY_WINDOW_Y = "window_y"
        private const val DEFAULT_WIDTH_RATIO = 0.9f
        private const val DEFAULT_HEIGHT_RATIO = 0.6f
        private const val MIN_WIDTH_DP = 200
        private const val MIN_HEIGHT_DP = 300
        private const val EDGE_ANIMATION_DURATION = 300L
        private const val DOUBLE_TAP_TIMEOUT = 300L
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
        private const val FAVICON_SIZE_DP = 40 // 悬浮球大小
        
        // 前台服务通知相关常量
        private const val CHANNEL_ID = "webview_channel"
        private const val NOTIFICATION_ID = 1002
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
    private var saveButton: ImageButton? = null
    
    private val halfCircleWindow by lazy { HalfCircleFloatingWindow(this) }
    
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
    
    // 修改成员变量声明
    private var originalBackground: android.graphics.drawable.Drawable? = null
    
    private var lastTapTime = 0L
    private var lastWindowState: WindowState? = null
    private var searchBar: View? = null
    private var navigationBar: View? = null
    
    private var faviconView: ImageView? = null
    private var currentFavicon: Bitmap? = null
    
    private data class WindowState(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
        val isExpanded: Boolean
    )

    private val sharedPrefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveWindowState() {
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        sharedPrefs.edit().apply {
            putInt(KEY_WINDOW_WIDTH, params.width)
            putInt(KEY_WINDOW_HEIGHT, params.height)
            putInt(KEY_WINDOW_X, params.x)
            putInt(KEY_WINDOW_Y, params.y)
            apply()
        }
    }

    private fun loadWindowState(): WindowState {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val defaultWidth = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
        val defaultHeight = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()

        return WindowState(
            width = sharedPrefs.getInt(KEY_WINDOW_WIDTH, defaultWidth),
            height = sharedPrefs.getInt(KEY_WINDOW_HEIGHT, defaultHeight),
            x = sharedPrefs.getInt(KEY_WINDOW_X, 0),
            y = sharedPrefs.getInt(KEY_WINDOW_Y, 0),
            isExpanded = false
        )
    }

    private fun getMinWidth(): Int = (MIN_WIDTH_DP * resources.displayMetrics.density).toInt()
    private fun getMinHeight(): Int = (MIN_HEIGHT_DP * resources.displayMetrics.density).toInt()
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        try {
            // 创建并启动前台服务
            startForeground()
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 获取屏幕尺寸
            val displayMetrics = resources.displayMetrics
                screenWidth = displayMetrics.widthPixels
                screenHeight = displayMetrics.heightPixels
            
            // 创建浮动窗口
        createFloatingView()
        
            // 加载上次的窗口状态
            val savedState = loadWindowState()
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                // 应用保存的状态
                params.width = maxOf(savedState.width, getMinWidth())
                params.height = maxOf(savedState.height, getMinHeight())
                params.x = savedState.x
                params.y = savedState.y
                
                try {
                    windowManager.updateViewLayout(floatingView, params)
                } catch (e: Exception) {
                    Log.e(TAG, "恢复窗口状态失败", e)
                }
            }
            
            // 设置双击标题栏最大化
            setupTitleBarDoubleTap()
            
            // 确保导航栏和保存按钮可见
            saveButton?.visibility = View.VISIBLE
            navigationBar?.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Log.e(TAG, "创建服务失败", e)
            stopSelf()
        }
    }
    
    /**
     * 创建前台服务，避免系统限制服务启动
     */
    private fun startForeground() {
        // 创建通知渠道（Android 8.0+需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "浮动窗口服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "单窗口浏览服务，用于浮动查看网页"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建点击通知时的Intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建通知
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("浮动浏览器")
            .setContentText("正在运行浮动窗口服务")
            .setSmallIcon(R.drawable.ic_search)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createFloatingView() {
        try {
            // 加载悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_webview, null)
            
            // 创建favicon视图
            faviconView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    (FAVICON_SIZE_DP * resources.displayMetrics.density).toInt(),
                    (FAVICON_SIZE_DP * resources.displayMetrics.density).toInt()
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setBackgroundResource(R.drawable.favicon_background) // 需要创建圆形背景drawable
                visibility = View.GONE
            }
            
            // 初始化视图组件
            initializeViews()
            
            // 创建窗口参数
            val params = createWindowLayoutParams()
            
            // 应用最小尺寸限制
            params.width = maxOf(params.width, getMinWidth())
            params.height = maxOf(params.height, getMinHeight())
            
            // 添加到窗口
            windowManager.addView(floatingView, params)
            windowManager.addView(faviconView, createFaviconLayoutParams())
            
            // 保存初始状态
            originalWidth = params.width
            originalHeight = params.height
            originalX = params.x
            originalY = params.y
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
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
    
    private fun createFaviconLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            (FAVICON_SIZE_DP * resources.displayMetrics.density).toInt(),
            (FAVICON_SIZE_DP * resources.displayMetrics.density).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = screenHeight / 3
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
                
                // 启用文件访问
                allowFileAccess = true
                
                // 启用 DOM storage API
                domStorageEnabled = true
                
                // 设置默认字体大小
                defaultFontSize = 16
                
                // 启用 JavaScript 接口
                javaScriptCanOpenWindowsAutomatically = true
            }
            
            // 设置WebView背景为白色
            setBackgroundColor(Color.WHITE)
            
            // 设置 WebViewClient
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    progressBar?.visibility = View.VISIBLE
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar?.visibility = View.GONE
                    
                    // 确保WebView可见
                    view?.visibility = View.VISIBLE
                    
                    // 更新导航按钮状态
                    updateNavigationButtons()
                    
                    // 注入JavaScript以确保内容正确显示
                    val js = """
                        javascript:(function() {
                            document.body.style.backgroundColor = 'white';
                            var meta = document.querySelector('meta[name="viewport"]');
                            if (!meta) {
                                meta = document.createElement('meta');
                                meta.name = 'viewport';
                                document.head.appendChild(meta);
                            }
                            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';
                            
                            // 确保所有链接在WebView内打开
                            var links = document.getElementsByTagName('a');
                            for(var i = 0; i < links.length; i++) {
                                links[i].target = '_self';
                            }
                        })()
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    progressBar?.visibility = View.GONE
                    Toast.makeText(this@FloatingWebViewService, "页面加载失败: $description", Toast.LENGTH_SHORT).show()
                }
                
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        if (URLUtil.isNetworkUrl(it) || URLUtil.isAssetUrl(it) || URLUtil.isContentUrl(it)) {
                            view?.loadUrl(it)
                            return true
                        }
                    }
                    return false
                }
            }
            
            // 设置 WebChromeClient
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    progressBar?.progress = newProgress
                }
                
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    floatingTitle?.text = title
                }
            }
            
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
        
        // 保存搜索引擎快捷方式按钮
        saveButton?.setOnClickListener { view ->
            // 调用保存功能
            saveCurrentSearchEngine()
            
            // 添加点击反馈动画
                view.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
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
                        if (isHidden) {
                            halfCircleWindow.animateShowToCenter(
                                floatingView ?: return@setOnTouchListener false,
                                floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false,
                                windowManager,
                                { enableWebViewScrolling() }
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
                                        windowManager,
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
                                            saveButton?.visibility = View.VISIBLE
                                            navigationBar?.visibility = View.VISIBLE
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
            try {
                val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                        // 记录初始状态
                        initialWidth = params.width
                        initialHeight = params.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isResizing = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                        if (isResizing) {
                            // 计算新的尺寸
                            var newWidth = (initialWidth + (event.rawX - initialTouchX)).toInt()
                            var newHeight = (initialHeight + (event.rawY - initialTouchY)).toInt()
                            
                            // 应用最小尺寸限制
                            newWidth = maxOf(newWidth, getMinWidth())
                            newHeight = maxOf(newHeight, getMinHeight())
                            
                            // 应用最大尺寸限制
                            val maxWidth = screenWidth
                            val maxHeight = screenHeight
                            newWidth = minOf(newWidth, maxWidth)
                            newHeight = minOf(newHeight, maxHeight)
                            
                            // 更新布局参数
                            params.width = newWidth
                            params.height = newHeight
                            
                            try {
                        windowManager.updateViewLayout(floatingView, params)
                        
                        // 显示尺寸提示
                                showSizeHint(newWidth, newHeight)
                            } catch (e: Exception) {
                                Log.e(TAG, "更新窗口大小失败", e)
                            }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isResizing) {
                    isResizing = false
                            hideSizeHint()
                            
                            // 保存新的窗口状态
                            saveWindowState()
                        }
                    true
                }
                else -> false
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理调整大小失败", e)
                isResizing = false
                hideSizeHint()
                false
            }
        }
    }
    
    private fun showSizeHint(width: Int, height: Int) {
        gestureHintView?.apply {
            text = "${width}x${height}"
            visibility = View.VISIBLE
            
            // 移除之前的延迟隐藏
            removeCallbacks(hideHintRunnable)
            // 3秒后自动隐藏
            postDelayed(hideHintRunnable, 3000)
        }
    }

    private val hideHintRunnable = Runnable {
        hideSizeHint()
    }

    private fun hideSizeHint() {
        gestureHintView?.visibility = View.GONE
    }
    
    private fun toggleExpandState() {
        try {
        isExpanded = !isExpanded
        
            // 保存当前状态用于还原
            if (isExpanded) {
                lastWindowState = WindowState(
                    width = originalWidth,
                    height = originalHeight,
                    x = originalX,
                    y = originalY,
                    isExpanded = false
                )
            }
            
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            if (isExpanded) {
                // 全屏模式
                expandButton?.setImageResource(R.drawable.ic_collapse)
                params.width = screenWidth
                params.height = screenHeight
                params.x = 0
                params.y = 0
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                
                // 显示所有控件
                showAllControls()
            } else {
                // 还原模式
                expandButton?.setImageResource(R.drawable.ic_expand)
                lastWindowState?.let { state ->
                    params.width = state.width
                    params.height = state.height
                    params.x = state.x
                    params.y = state.y
                } ?: run {
                    // 如果没有保存状态，使用默认值
                    params.width = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
                    params.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
                    params.x = (screenWidth - params.width) / 2
                    params.y = (screenHeight - params.height) / 3
                }
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                
                // 隐藏部分控件
                hideOptionalControls()
            }
            
            // 确保尺寸不小于最小值
            params.width = maxOf(params.width, getMinWidth())
            params.height = maxOf(params.height, getMinHeight())
            
            // 更新布局
            windowManager.updateViewLayout(floatingView, params)
            
            // 保存状态
            if (!isExpanded) {
                saveWindowState()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "切换全屏状态失败", e)
            // 重置状态
            isExpanded = !isExpanded
        }
    }

    private fun showAllControls() {
        searchBar?.visibility = View.VISIBLE
        navigationBar?.visibility = View.VISIBLE
        resizeHandle?.visibility = View.GONE
        
        // 确保保存按钮可见
        saveButton?.visibility = View.VISIBLE
    }

    private fun hideOptionalControls() {
        searchBar?.visibility = View.GONE
        // 保留导航栏可见性，确保保存按钮始终可见
        // navigationBar?.visibility = View.GONE
        navigationBar?.visibility = View.VISIBLE
        resizeHandle?.visibility = View.VISIBLE
        
        // 确保保存按钮可见
        saveButton?.visibility = View.VISIBLE
    }
    
    private fun initGestureDetectors() {
        val context = this
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
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

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
        
        // 优化保存按钮样式，使其更显眼
        saveButton?.apply {
            setColorFilter(Color.parseColor("#4CAF50")) // 设置为绿色
            background = createRippleDrawable(Color.parseColor("#1A4CAF50")) // 绿色水波纹
            
            // 确保始终可见
            visibility = View.VISIBLE
            
            // 提高优先级，确保在小屏幕上也能显示
            (layoutParams as? LinearLayout.LayoutParams)?.apply {
                // 减小右边距确保有显示空间
                marginEnd = 0
                // 使用更高的比重，确保获得足够空间
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
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
                // 确保 WebView 已经初始化
                if (webView == null) {
                    initializeViews()
                }
                
                // 确保 WebView 可见
                webView?.visibility = View.VISIBLE
                
                // 延迟加载URL，确保WebView已经完全初始化完成
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d(TAG, "Loading URL: $url")
                        webView?.loadUrl(url)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load URL: $url", e)
                        Toast.makeText(this, "加载页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, 500) // 增加延迟时间到500ms
            }
            
            // 处理传入的搜索查询
            val query = it.getStringExtra("query")
            if (!query.isNullOrEmpty()) {
                searchInput?.setText(query)
                
                // 延迟执行搜索，确保WebView已经完全初始化完成
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        performSearch(query)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to perform search: $query", e)
                        Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, 500) // 增加延迟时间到500ms
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        try {
            if (floatingView != null) {
                saveWindowState()
                try {
                    windowManager.removeView(floatingView)
                } catch (e: Exception) {
                    Log.e(TAG, "移除浮动窗口失败: $e")
                }
                floatingView = null
            }
            
            // 停止前台服务
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "销毁服务失败", e)
        }
        super.onDestroy()
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
        if (isAnimating) return
        
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            halfCircleWindow.animateToEdge(
                isLeft,
                floatingView ?: return,
                params,
                windowManager,
                { disableWebViewScrolling() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建边缘动画失败", e)
            isAnimating = false
            isHidden = false
        }
    }

    private fun setupTitleBarDoubleTap() {
        val titleBar = floatingView?.findViewById<View>(R.id.title_bar)
        titleBar?.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                // 双击，切换全屏状态
                toggleExpandState()
                lastTapTime = 0
            } else {
                lastTapTime = currentTime
            }
        }
    }

    private fun hideViews() {
        // Implementation of hideViews method
    }

    private fun lerpInt(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).toInt()
    }

    private fun disableWebViewScrolling() {
        // Implementation of disableWebViewScrolling method
    }

    private fun enableWebViewScrolling() {
        // Implementation of enableWebViewScrolling method
    }

    private fun updateViewsAlpha(alpha: Float) {
        // Implementation of updateViewsAlpha method
    }

    private fun toggleWindowFocusableFlag(focusable: Boolean) {
        // Implementation of toggleWindowFocusableFlag method
    }

    private fun getEdgeThreshold(): Float {
        return DEFAULT_EDGE_THRESHOLD * resources.displayMetrics.density
    }

    private fun resetToDefaultSizeAndCenter() {
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Set default size
        params.width = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
        params.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        
        // Center the window
        params.x = (screenWidth - params.width) / 2
        params.y = (screenHeight - params.height) / 3
        
        try {
            windowManager.updateViewLayout(floatingView, params)
            saveWindowState()
        } catch (e: Exception) {
            Log.e(TAG, "重置窗口大小和位置失败", e)
        }
    }

    private fun animateShowToCenter() {
        if (isAnimating) return
        
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            halfCircleWindow.animateShowToCenter(
                floatingView ?: return,
                params,
                windowManager,
                { enableWebViewScrolling() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建恢复动画失败", e)
            isAnimating = false
            isHidden = false
        }
    }

    private fun initializeViews() {
        // Initialize all view references
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
        saveButton = floatingView?.findViewById(R.id.btn_save)
        floatingTitle = floatingView?.findViewById(R.id.floating_title)
        resizeHandle = floatingView?.findViewById(R.id.resize_handle)
        searchBar = floatingView?.findViewById(R.id.search_bar)
        navigationBar = floatingView?.findViewById(R.id.navigation_bar)

        // Setup components
        setupWebView()
        setupSearchInput()
        setupButtons()
        setupDragHandling()
        setupResizeHandling()
        initGestureDetectors()
        applyTheme()
        
        // 确保保存按钮可见
        saveButton?.visibility = View.VISIBLE
        // 确保导航栏可见
        navigationBar?.visibility = View.VISIBLE
    }

    private fun showViews() {
            webView?.visibility = View.VISIBLE
            searchInput?.visibility = View.VISIBLE
            searchButton?.visibility = View.VISIBLE
            closeButton?.visibility = View.VISIBLE
            expandButton?.visibility = View.VISIBLE
        if (!isExpanded) {
            resizeHandle?.visibility = View.VISIBLE
        }
    }

    private fun setupFaviconTouchListener() {
        faviconView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isMoving = true
                        val params = view.layoutParams as WindowManager.LayoutParams
                        params.x += deltaX.toInt()
                        params.y += deltaY.toInt()
                        
                        // 限制在屏幕范围内
                        params.x = params.x.coerceIn(0, screenWidth - view.width)
                        params.y = params.y.coerceIn(0, screenHeight - view.height)
                        
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "更新favicon位置失败", e)
                        }
                        
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // 点击时恢复悬浮窗
                        animateShowToCenter()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFaviconView() {
        currentFavicon?.let { favicon ->
            faviconView?.setImageBitmap(favicon)
        } ?: run {
            faviconView?.setImageResource(R.drawable.ic_web_default)
        }
    }

    // 添加保存当前搜索引擎为快捷方式的方法
    private fun saveCurrentSearchEngine() {
        webView?.let { webView ->
            val currentUrl = webView.url ?: return
            val title = webView.title ?: "未命名网站"
            val favicon = currentFavicon
            
            try {
                // 提取搜索引擎基础URL
                val searchEngineInfo = extractSearchEngineInfo(currentUrl)
                
                if (searchEngineInfo != null) {
                    // 保存搜索引擎信息到SharedPreferences
                    saveSearchEngineShortcut(searchEngineInfo, title, favicon)
                    
                    // 显示成功提示
                    val successMsg = getString(R.string.search_engine_saved) + ": ${searchEngineInfo.name}"
                    showSaveResultNotification(true, successMsg)
                    Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show()
                } else {
                    // 显示失败提示 - 不是搜索引擎
                    val errorMsg = getString(R.string.search_engine_save_failed) + ": 当前页面不是搜索引擎或无法识别"
                    showSaveResultNotification(false, errorMsg)
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // 显示失败提示 - 发生错误
                val errorMsg = getString(R.string.search_engine_save_failed) + ": ${e.message}"
                showSaveResultNotification(false, errorMsg)
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                Log.e(TAG, "保存搜索引擎失败", e)
            }
        }
    }
    
    // 显示保存结果通知
    private fun showSaveResultNotification(success: Boolean, message: String) {
        // 在手势提示区域显示结果
        gestureHintView?.apply {
            text = message
            // 设置背景颜色
            val backgroundColor = if (success) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            background = GradientDrawable().apply {
                setColor(backgroundColor)
                cornerRadius = 16f
                alpha = 220
            }
            visibility = View.VISIBLE
            
            // 创建动画显示效果
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    // 3秒后淡出
                    postDelayed({
                        animate()
                            .alpha(0f)
                            .setDuration(500)
                            .withEndAction { visibility = View.GONE }
                            .start()
                    }, 3000)
                }
                .start()
        }
    }

    // 提取搜索引擎信息（搜索URL模板和域名）
    private fun extractSearchEngineInfo(url: String): SearchEngineShortcut? {
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            
            // 检查是否是搜索结果页面
            val path = uri.path ?: ""
            val query = uri.getQueryParameter("q") 
                ?: uri.getQueryParameter("query")
                ?: uri.getQueryParameter("word")
                ?: uri.getQueryParameter("wd")
                ?: uri.getQueryParameter("text")
                ?: uri.getQueryParameter("search")
                ?: return null
            
            // 不同搜索引擎的模式匹配
            val searchUrlTemplate = when {
                host.contains("google") -> {
                    val baseUrl = url.substringBefore("&q=")
                    "$baseUrl&q={query}"
                }
                host.contains("baidu") -> {
                    val baseUrl = url.substringBefore("wd=")
                    "${baseUrl}wd={query}"
                }
                host.contains("bing") -> {
                    val baseUrl = url.substringBefore("q=")
                    "${baseUrl}q={query}"
                }
                host.contains("yahoo") -> {
                    val baseUrl = url.substringBefore("p=")
                    "${baseUrl}p={query}"
                }
                host.contains("duckduckgo") -> {
                    val baseUrl = url.substringBefore("q=")
                    "${baseUrl}q={query}"
                }
                host.contains("yandex") -> {
                    val baseUrl = url.substringBefore("text=")
                    "${baseUrl}text={query}"
                }
                host.contains("sogou") -> {
                    val baseUrl = url.substringBefore("query=")
                    "${baseUrl}query={query}"
                }
                else -> {
                    // 通用模式：尝试替换查询参数
                    url.replace(query, "{query}")
                }
            }
            
            return SearchEngineShortcut(
                id = System.currentTimeMillis().toString(),
                name = getSearchEngineName(host),
                url = searchUrlTemplate,
                domain = host,
                searchUrl = searchUrlTemplate
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析搜索引擎URL失败", e)
            return null
        }
    }

    // 根据域名获取搜索引擎名称
    private fun getSearchEngineName(domain: String): String {
        return when {
            domain.contains("google") -> "Google"
            domain.contains("baidu") -> "百度"
            domain.contains("bing") -> "必应"
            domain.contains("yahoo") -> "雅虎"
            domain.contains("duckduckgo") -> "DuckDuckGo"
            domain.contains("yandex") -> "Yandex"
            domain.contains("sogou") -> "搜狗"
            else -> domain.substringBefore(".").capitalize()
        }
    }

    // 保存搜索引擎快捷方式到SharedPreferences
    private fun saveSearchEngineShortcut(searchEngine: SearchEngineShortcut, title: String, favicon: Bitmap?) {
        // 获取当前已保存的快捷方式
        val prefs = getSharedPreferences("search_engine_shortcuts", Context.MODE_PRIVATE)
        val gson = Gson()
        val shortcutsJson = prefs.getString("shortcuts", "[]")
        val type = object : TypeToken<ArrayList<SearchEngineShortcut>>() {}.type
        val shortcuts = gson.fromJson<ArrayList<SearchEngineShortcut>>(shortcutsJson, type) ?: ArrayList()
        
        // 检查是否已存在相同域名的快捷方式
        val existingIndex = shortcuts.indexOfFirst { it.domain == searchEngine.domain }
        if (existingIndex >= 0) {
            // 更新已有快捷方式
            shortcuts[existingIndex] = searchEngine
        } else {
            // 添加新快捷方式
            shortcuts.add(searchEngine)
        }
        
        // 保存回SharedPreferences
        prefs.edit()
            .putString("shortcuts", gson.toJson(shortcuts))
            .apply()
        
        // 保存favicon（如果有）
        favicon?.let {
            saveFaviconForSearchEngine(searchEngine.id, it)
        }
    }

    // 保存搜索引擎的favicon
    private fun saveFaviconForSearchEngine(id: String, favicon: Bitmap) {
        try {
            // 将bitmap转换为字节数组
            val baos = ByteArrayOutputStream()
            favicon.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val bytes = baos.toByteArray()
            
            // 保存到内部存储
            val file = File(filesDir, "favicon_$id.png")
            val fos = FileOutputStream(file)
            fos.write(bytes)
            fos.close()
        } catch (e: Exception) {
            Log.e(TAG, "保存favicon失败", e)
        }
    }

    private fun parseSearchEngine(url: String): SearchEngine? {
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            val searchUrlTemplate = url.replace(
                Regex("([?&](q|query|word|wd|text|search)=)[^&]*"),
                "$1{query}"
            )
            
            return SearchEngine(
                name = getSearchEngineName(host),
                url = url,
                searchUrl = searchUrlTemplate,
                iconResId = R.drawable.ic_search,
                description = "${getSearchEngineName(host)}搜索"
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析搜索引擎URL失败", e)
            return null
        }
    }

    private fun getSearchEngineNameFromHost(host: String): String {
        return when {
            host.contains("baidu") -> "百度"
            host.contains("google") -> "谷歌"
            host.contains("bing") -> "必应"
            host.contains("sogou") -> "搜狗"
            host.contains("zhihu") -> "知乎"
            host.contains("bilibili") -> "哔哩哔哩"
            else -> host.split(".")[0].capitalize()
        }
    }
} 