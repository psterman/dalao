package com.example.aifloatingball

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.FaviconManager
import com.example.aifloatingball.utils.EngineUtil
import com.example.aifloatingball.utils.IconLoader
import java.net.URLEncoder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.example.aifloatingball.manager.SearchEngineManager

class DualFloatingWebViewService : Service() {
    companion object {
        private const val TAG = "DualFloatingWebViewService"
        private const val PREFS_NAME = "dual_floating_window_prefs"
        private const val KEY_WINDOW_WIDTH = "window_width"
        private const val KEY_WINDOW_HEIGHT = "window_height"
        private const val KEY_WINDOW_X = "window_x"
        private const val KEY_WINDOW_Y = "window_y"
        private const val KEY_IS_HORIZONTAL = "is_horizontal"
        private const val DEFAULT_WIDTH_RATIO = 0.95f
        private const val DEFAULT_HEIGHT_RATIO = 0.7f
        private const val MIN_WIDTH_DP = 300
        private const val MIN_HEIGHT_DP = 400
        private const val WEBVIEW_WIDTH_DP = 300 // 每个WebView的宽度
        
        // 前台服务通知相关常量
        private const val CHANNEL_ID = "dual_webview_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var firstWebView: WebView? = null
    private var secondWebView: WebView? = null
    private var thirdWebView: WebView? = null  // 添加第三个WebView
    private var searchInput: EditText? = null
    private var toggleLayoutButton: ImageButton? = null
    private var dualSearchButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var resizeHandle: View? = null
    private var container: LinearLayout? = null
    private var divider1: View? = null  // 重命名为divider1
    private var divider2: View? = null  // 添加divider2
    private var singleWindowButton: ImageButton? = null
    private var windowCountToggleView: TextView? = null // 添加窗口计数切换按钮
    private var firstTitle: TextView? = null
    private var secondTitle: TextView? = null
    private var thirdTitle: TextView? = null  // 添加第三个标题
    private var firstEngineContainer: LinearLayout? = null
    private var secondEngineContainer: LinearLayout? = null
    private var thirdEngineContainer: LinearLayout? = null  // 添加第三个引擎容器
    private var firstAIEngineContainer: LinearLayout? = null
    private var secondAIEngineContainer: LinearLayout? = null
    private var thirdAIEngineContainer: LinearLayout? = null  // 添加AI引擎容器
    private var firstEngineToggle: ImageButton? = null
    private var secondEngineToggle: ImageButton? = null
    private var thirdEngineToggle: ImageButton? = null
    private var firstAIScrollContainer: HorizontalScrollView? = null
    private var secondAIScrollContainer: HorizontalScrollView? = null
    private var thirdAIScrollContainer: HorizontalScrollView? = null
    private var currentEngineKey: String = "baidu"
    private var currentWebView: WebView? = null
    private var currentTitle: TextView? = null
    private var saveButton: ImageButton? = null // 保存按钮
    
    private lateinit var settingsManager: SettingsManager
    private var leftEngineKey: String = "baidu"
    private var centerEngineKey: String = "bing"  // 添加中间引擎键
    private var rightEngineKey: String = "google"
    private var windowCount: Int = 2 // 默认窗口数量

    private var isHorizontalLayout = true // 默认为水平布局

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialWidth = 0
    private var initialHeight = 0
    private var isMoving = false
    private var isResizing = false

    private val sharedPrefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private lateinit var faviconManager: FaviconManager
    private lateinit var iconLoader: IconLoader

    /**
     * 扩展函数将dp转换为px
     */
    private fun Int.dpToPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            // 创建并启动前台服务
            startForeground()
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 初始化设置管理器
            settingsManager = SettingsManager.getInstance(this)
            
            // 初始化Favicon管理器
            faviconManager = FaviconManager.getInstance(this)
            
            // 初始化IconLoader
            iconLoader = IconLoader(this)
            iconLoader.cleanupOldCache()
            
            // 从设置中获取用户设置的窗口数量
            windowCount = settingsManager.getDefaultWindowCount()
            
            // 从设置中获取用户设置的搜索引擎
            leftEngineKey = settingsManager.getLeftWindowSearchEngine()
            centerEngineKey = settingsManager.getCenterWindowSearchEngine()
            rightEngineKey = settingsManager.getRightWindowSearchEngine()
            
            // 创建浮动窗口
            createFloatingView()
            
            // 加载上次的窗口状态
            loadWindowState()
            
            // 初始化WebView
            setupWebViews()
            
            // 设置控件事件
            setupControls()
            
            // 初始化搜索引擎图标
            updateEngineIcons()
            
            // 更新所有搜索引擎图标状态
            firstEngineContainer?.let { updateEngineIconStates(it, leftEngineKey, false) }
            secondEngineContainer?.let { updateEngineIconStates(it, centerEngineKey, false) }
            thirdEngineContainer?.let { updateEngineIconStates(it, rightEngineKey, false) }
            
            // 根据窗口数量设置视图可见性
            updateViewVisibilityByWindowCount()
            
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
                description = "多窗口浏览服务，用于支持同时查看多个网页"
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
            .setContentTitle("多窗口浏览器")
            .setContentText("正在运行多窗口浏览服务")
            .setSmallIcon(R.drawable.ic_search)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "UPDATE_BALL_SIZE" -> {
                val size = intent.getIntExtra("size", 50)
                updateFloatingBallSize(size)
            }
            "UPDATE_MENU_LAYOUT" -> {
                val layout = intent.getStringExtra("layout") ?: "mixed"
                updateMenuLayout(layout)
            }
            "LOAD_SEARCH_ENGINE_GROUP" -> {
                // 新增: 从Intent中获取组名并加载搜索引擎组合
                val groupName = intent.getStringExtra("group_name")
                if (!groupName.isNullOrEmpty()) {
                    loadSearchEngineGroup(groupName)
                }
            }
            else -> {
        // 从Intent中获取窗口数量设置（如果有）
        intent?.getIntExtra("window_count", -1)?.let { count ->
            if (count > 0) {
                // 更新窗口数量
                windowCount = count
                // 根据窗口数量更新视图可见性
                updateViewVisibilityByWindowCount()
            }
        }
        
        // 获取查询参数并执行搜索
        intent?.getStringExtra("search_query")?.let { query ->
            if (query.isNotEmpty()) {
                // 将搜索查询显示在搜索框中
                searchInput?.setText(query)
                
                // 延迟执行搜索，确保WebView已完全初始化
                Handler(Looper.getMainLooper()).postDelayed({
                    performDualSearch()
                }, 500)
            }
        }
        
        // 获取URL参数
        intent?.getStringExtra("url")?.let { url ->
            if (url.isNotEmpty()) {
                // 加载URL到第一个WebView
                firstWebView?.loadUrl(url)
                
                // 根据URL确定搜索引擎
                val isGoogle = url.contains("google.com")
                val isBaidu = url.contains("baidu.com")
                
                // 如果是百度或谷歌，加载另一个搜索引擎
                if (windowCount >= 2) {
                    if (isGoogle) {
                        secondWebView?.loadUrl("https://www.baidu.com")
                    } else {
                        secondWebView?.loadUrl("https://www.google.com")
                    }
                }
                
                // 如果是三窗口模式，加载第三个窗口
                if (windowCount >= 3) {
                    if (isGoogle || isBaidu) {
                        thirdWebView?.loadUrl("https://www.bing.com")
                    } else {
                        thirdWebView?.loadUrl("https://www.zhihu.com")
                            }
                        }
                    }
                }
            }
        }
        
        return START_NOT_STICKY
    }

    private fun createFloatingView() {
        try {
            // 加载悬浮窗布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_dual_floating_webview, null)
            
            // 初始化视图组件
            initializeViews()
            
            // 创建窗口参数
            val params = createWindowLayoutParams()
            
            // 添加到窗口
            windowManager.addView(floatingView, params)
            
            // 初始化设置
            initializeSettings()
            
            // 获取用户设置的默认窗口数量
            val windowCount = settingsManager.getDefaultWindowCount()
            
            // 根据窗口数量显示或隐藏相应的窗口
            updateWindowVisibility(windowCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
        }
    }

    private fun initializeViews() {
        // 初始化视图引用
        container = floatingView?.findViewById(R.id.dual_webview_container)
        firstWebView = floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = floatingView?.findViewById(R.id.third_floating_webview)  // 添加第三个WebView
        searchInput = floatingView?.findViewById(R.id.dual_search_input)
        toggleLayoutButton = floatingView?.findViewById(R.id.btn_toggle_layout)
        dualSearchButton = floatingView?.findViewById(R.id.btn_dual_search)
        closeButton = floatingView?.findViewById(R.id.btn_dual_close)
        resizeHandle = floatingView?.findViewById(R.id.dual_resize_handle)
        divider1 = floatingView?.findViewById(R.id.divider1)  // 更新divider1
        divider2 = floatingView?.findViewById(R.id.divider2)  // 添加divider2
        singleWindowButton = floatingView?.findViewById(R.id.btn_single_window)
        windowCountToggleView = floatingView?.findViewById(R.id.window_count_toggle) // 初始化窗口计数切换视图
        firstTitle = floatingView?.findViewById(R.id.first_floating_title)
        secondTitle = floatingView?.findViewById(R.id.second_floating_title)
        thirdTitle = floatingView?.findViewById(R.id.third_floating_title)  // 添加第三个标题
        firstEngineContainer = floatingView?.findViewById(R.id.first_engine_container)
        secondEngineContainer = floatingView?.findViewById(R.id.second_engine_container)
        thirdEngineContainer = floatingView?.findViewById(R.id.third_engine_container)  // 添加第三个引擎容器
        firstAIEngineContainer = floatingView?.findViewById(R.id.first_ai_engine_container)
        secondAIEngineContainer = floatingView?.findViewById(R.id.second_ai_engine_container)
        thirdAIEngineContainer = floatingView?.findViewById(R.id.third_ai_engine_container)  // 添加AI引擎容器
        firstEngineToggle = floatingView?.findViewById(R.id.first_engine_toggle)
        secondEngineToggle = floatingView?.findViewById(R.id.second_engine_toggle)
        thirdEngineToggle = floatingView?.findViewById(R.id.third_engine_toggle)
        firstAIScrollContainer = floatingView?.findViewById(R.id.first_ai_scroll_container)
        secondAIScrollContainer = floatingView?.findViewById(R.id.second_ai_scroll_container)
        thirdAIScrollContainer = floatingView?.findViewById(R.id.third_ai_scroll_container)
        saveButton = floatingView?.findViewById(R.id.btn_save_engines) // 添加保存按钮
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Set default width to match screen width for horizontal layout
        val defaultWidth = WindowManager.LayoutParams.MATCH_PARENT
        val defaultHeight = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        
        return WindowManager.LayoutParams().apply {
            width = defaultWidth
            height = defaultHeight
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
        }
    }

    private fun saveWindowState() {
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        sharedPrefs.edit().apply {
            putInt(KEY_WINDOW_WIDTH, params.width)
            putInt(KEY_WINDOW_HEIGHT, params.height)
            putInt(KEY_WINDOW_X, params.x)
            putInt(KEY_WINDOW_Y, params.y)
            putBoolean(KEY_IS_HORIZONTAL, isHorizontalLayout)
            apply()
        }
    }

    private fun loadWindowState() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val defaultWidth = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
        val defaultHeight = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        
        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
        
        params.width = sharedPrefs.getInt(KEY_WINDOW_WIDTH, defaultWidth)
        params.height = sharedPrefs.getInt(KEY_WINDOW_HEIGHT, defaultHeight)
        params.x = sharedPrefs.getInt(KEY_WINDOW_X, 0)
        params.y = sharedPrefs.getInt(KEY_WINDOW_Y, 0)
        isHorizontalLayout = sharedPrefs.getBoolean(KEY_IS_HORIZONTAL, true)
        
        // 应用布局方向
        updateLayoutOrientation()
        
        try {
            windowManager.updateViewLayout(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "恢复窗口状态失败", e)
        }
    }

    private fun setupWebViews() {
        // 设置第一个WebView（左侧窗口）
        firstWebView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                setInitialScale(0)
                userAgentString = settings.userAgentString + " Mobile"
            }
            
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "开始加载页面: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "页面加载完成: $url")
                    view?.requestLayout()
                }
            }
            
            val leftHomeUrl = EngineUtil.getSearchEngineHomeUrl(leftEngineKey)
            loadUrl(leftHomeUrl)
            
            firstTitle?.text = EngineUtil.getSearchEngineName(leftEngineKey, false)
        }
        
        // 设置第二个WebView（中间窗口）
        secondWebView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                setInitialScale(0)
                userAgentString = settings.userAgentString + " Mobile"
            }
            
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "开始加载页面: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "页面加载完成: $url")
                    view?.requestLayout()
                }
            }
            
            val centerHomeUrl = EngineUtil.getSearchEngineHomeUrl(centerEngineKey)
            loadUrl(centerHomeUrl)
            
            secondTitle?.text = EngineUtil.getSearchEngineName(centerEngineKey, false)
        }
        
        // 设置第三个WebView（右侧窗口）
        thirdWebView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                setInitialScale(0)
                userAgentString = settings.userAgentString + " Mobile"
            }
            
            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "开始加载页面: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "页面加载完成: $url")
                    view?.requestLayout()
                }
            }
            
            val rightHomeUrl = EngineUtil.getSearchEngineHomeUrl(rightEngineKey)
            loadUrl(rightHomeUrl)
            
            thirdTitle?.text = EngineUtil.getSearchEngineName(rightEngineKey, false)
        }
    }

    private fun setupControls() {
        // 设置移动和调整大小的触摸监听
        setupTouchListeners()
        
        // 设置布局切换按钮
        toggleLayoutButton?.setOnClickListener {
            isHorizontalLayout = !isHorizontalLayout
            updateLayoutOrientation()
            saveWindowState()
        }
        
        // 设置搜索功能
        setupSearchFunctionality()
        
        // 设置关闭按钮
        closeButton?.setOnClickListener {
            stopSelf()
        }
        
        // 设置单窗口模式按钮
        singleWindowButton?.setOnClickListener {
            switchToSingleWindowMode()
        }
        
        // 设置搜索引擎类型切换按钮
        setupEngineToggleButtons()
        
        // 设置引擎容器样式
        setupEngineContainerStyle()
        
        // 设置保存按钮点击事件
        saveButton?.setOnClickListener {
            // 显示保存中的提示
            Toast.makeText(this, "正在保存搜索引擎组合...", Toast.LENGTH_SHORT).show()
            // 保存当前搜索引擎组合
            saveCurrentSearchEngines()
        }
        
        // 确保保存按钮可见
        saveButton?.visibility = View.VISIBLE

        // 窗口数量切换的点击处理函数
        val windowCountClickListener = View.OnClickListener {
            // 获取当前显示的窗口数量
            val visibleCount = when {
                thirdWebView?.visibility == View.VISIBLE -> 3
                secondWebView?.visibility == View.VISIBLE -> 2
                else -> 1
            }
            
            // 循环切换窗口数量：1 -> 2 -> 3 -> 1
            val newCount = when (visibleCount) {
                1 -> 2
                2 -> 3
                else -> 1
            }
            
            // 更新窗口可见性
            updateWindowVisibility(newCount)
            
            // 更新窗口数量
            windowCount = newCount
            
            // 保存设置
            settingsManager.setDefaultWindowCount(newCount)
            
            // 更新提示文本
            windowCountToggleView?.text = "$newCount"
        }

        // 窗口数量切换按钮 - 两个控件都使用同一个点击监听器
        floatingView?.findViewById<ImageButton>(R.id.btn_window_count)?.setOnClickListener(windowCountClickListener)
        windowCountToggleView?.setOnClickListener(windowCountClickListener)
    }

    private fun setupTouchListeners() {
        // 处理窗口拖动
        val titleBars = listOf(
            floatingView?.findViewById<View>(R.id.first_title_bar),
            floatingView?.findViewById<View>(R.id.second_title_bar),
            floatingView?.findViewById<View>(R.id.third_title_bar)
        )
        
        titleBars.forEach { titleBar ->
            titleBar?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                        initialY = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = true
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isMoving) {
                            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {
                                Log.e(TAG, "更新窗口位置失败", e)
                            }
                        }
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isMoving = false
                        saveWindowState()
                        return@setOnTouchListener true
                    }
                    else -> return@setOnTouchListener false
                }
            }
        }
        
        // 处理窗口大小调整
        resizeHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                    initialWidth = params.width
                    initialHeight = params.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isResizing = true
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing) {
                        val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
                        val minWidth = (MIN_WIDTH_DP * resources.displayMetrics.density).toInt()
                        val minHeight = (MIN_HEIGHT_DP * resources.displayMetrics.density).toInt()
                        
                        // 计算新尺寸
                        val newWidth = (initialWidth + (event.rawX - initialTouchX).toInt()).coerceAtLeast(minWidth)
                        val newHeight = (initialHeight + (event.rawY - initialTouchY).toInt()).coerceAtLeast(minHeight)
                        
                        // 应用新尺寸
                        params.width = newWidth
                        params.height = newHeight
                        
                        try {
                            windowManager.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "调整窗口大小失败", e)
                        }
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false
                    saveWindowState()
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }
    }

    private fun setupSearchFunctionality() {
        // 设置搜索输入框的回车键操作
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performDualSearch()
                return@setOnEditorActionListener true
            }
            false
        }
        
        // 设置搜索按钮点击事件
        dualSearchButton?.setOnClickListener {
            performDualSearch()
        }
    }

    private fun performDualSearch() {
        val query = searchInput?.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                
                // 使用左侧搜索引擎
                val leftUrl = EngineUtil.getSearchEngineSearchUrl(leftEngineKey, encodedQuery)
                firstWebView?.loadUrl(leftUrl)
                firstTitle?.text = "${EngineUtil.getSearchEngineName(leftEngineKey, false)}: $query"
                
                // 如果有两个或更多窗口，使用中间搜索引擎
                if (windowCount >= 2) {
                    val centerUrl = EngineUtil.getSearchEngineSearchUrl(centerEngineKey, encodedQuery)
                    secondWebView?.loadUrl(centerUrl)
                    secondTitle?.text = "${EngineUtil.getSearchEngineName(centerEngineKey, false)}: $query"
                }
                
                // 如果有三个窗口，使用右侧搜索引擎
                if (windowCount >= 3) {
                    val rightUrl = EngineUtil.getSearchEngineSearchUrl(rightEngineKey, encodedQuery)
                    thirdWebView?.loadUrl(rightUrl)
                    thirdTitle?.text = "${EngineUtil.getSearchEngineName(rightEngineKey, false)}: $query"
                }
                
                // 关闭键盘
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(searchInput?.windowToken, 0)
            } catch (e: Exception) {
                Log.e(TAG, "执行搜索失败", e)
                Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 更新布局方向
     * @return 返回设置的方向值（HORIZONTAL 或 VERTICAL）
     */
    private fun updateLayoutOrientation(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isLandscape = screenWidth > screenHeight

        val windowParams = floatingView?.layoutParams as? WindowManager.LayoutParams
        
        val orientationValue = if (isHorizontalLayout) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        
        container?.orientation = orientationValue
        
        if (isHorizontalLayout) {
            // 设置窗口宽度为屏幕宽度
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
            
            // 更新分割线
            divider1?.layoutParams = LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(1, 0, 1, 0)
            }
            divider2?.layoutParams = LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(1, 0, 1, 0)
            }
            
            // 更新容器和WebView宽度
            if (container?.childCount ?: 0 >= 5) {
                val webViewWidth = if (isLandscape) {
                    // 横屏时，根据窗口数量计算WebView宽度
                    val visibleCount = when (windowCount) {
                        1 -> 1
                        2 -> 2
                        else -> if (isLandscape) 2 else 3 // 横屏时最多显示两个，需要滚动查看第三个
                    }
                    (screenWidth / visibleCount) - (2 * Math.min(visibleCount - 1, 1))
                } else {
                    // 竖屏时，使用固定宽度
                    (WEBVIEW_WIDTH_DP * resources.displayMetrics.density).toInt()
                }
                
                // 设置每个WebView容器的宽度
                container?.getChildAt(0)?.let { firstContainer ->
                    val params = LinearLayout.LayoutParams(webViewWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                    firstContainer.layoutParams = params
                }
                
                container?.getChildAt(2)?.let { secondContainer ->
                    val params = LinearLayout.LayoutParams(webViewWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                    secondContainer.layoutParams = params
                }
                
                container?.getChildAt(4)?.let { thirdContainer ->
                    val params = LinearLayout.LayoutParams(webViewWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                    thirdContainer.layoutParams = params
                }
                
                // 设置容器宽度为wrap_content以启用滚动
                container?.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        } else {
            // 垂直布局
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = WindowManager.LayoutParams.MATCH_PARENT
            
            // 垂直分割线
            divider1?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 1, 0, 1)
            }
            divider2?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 1, 0, 1)
            }
            
            // 更新容器布局为垂直方向
            if (container?.childCount ?: 0 >= 5) {
                container?.getChildAt(0)?.let { firstContainer ->
                    val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    firstContainer.layoutParams = params
                }
                
                container?.getChildAt(2)?.let { secondContainer ->
                    val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    secondContainer.layoutParams = params
                }
                
                container?.getChildAt(4)?.let { thirdContainer ->
                    val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    thirdContainer.layoutParams = params
                }
                
                // 设置容器为match_parent
                container?.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
        
        // 更新窗口布局
        try {
            windowManager.updateViewLayout(floatingView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "更新窗口布局失败", e)
        }
        
        // 请求布局更新
        divider1?.requestLayout()
        divider2?.requestLayout()
        container?.requestLayout()
        
        return orientationValue
    }
    
    private fun switchToSingleWindowMode() {
        try {
            // 获取当前第一个WebView的URL
            val currentUrl = firstWebView?.url ?: "https://www.baidu.com"
            
            // 启动单窗口模式
            val intent = Intent(this, FloatingWebViewService::class.java).apply {
                putExtra("url", currentUrl)
            }
            startService(intent)
            
            // 关闭当前服务
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "切换到单窗口模式失败", e)
            Toast.makeText(this, "切换到单窗口模式失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEngineIcons() {
        // 清空容器
        firstEngineContainer?.removeAllViews()
        secondEngineContainer?.removeAllViews()
        thirdEngineContainer?.removeAllViews()
        firstAIEngineContainer?.removeAllViews()
        secondAIEngineContainer?.removeAllViews()
        thirdAIEngineContainer?.removeAllViews()
        
        // 为每个普通搜索引擎创建图标
        SearchEngine.DEFAULT_ENGINES.forEach { engine ->
            // 创建左侧搜索引擎图标
            createWebViewEngineIcon(engine.name, firstEngineContainer, true, false)
            // 创建中间搜索引擎图标
            createWebViewEngineIcon(engine.name, secondEngineContainer, false, false)
            // 创建右侧搜索引擎图标
            createWebViewEngineIcon(engine.name, thirdEngineContainer, false, false)
        }
        
        // 为每个AI搜索引擎创建图标
        com.example.aifloatingball.model.AISearchEngine.DEFAULT_AI_ENGINES.forEach { aiEngine ->
            // 创建左侧AI搜索引擎图标
            createWebViewEngineIcon(aiEngine.name, firstAIEngineContainer, true, true)
            // 创建中间AI搜索引擎图标
            createWebViewEngineIcon(aiEngine.name, secondAIEngineContainer, false, true)
            // 创建右侧AI搜索引擎图标
            createWebViewEngineIcon(aiEngine.name, thirdAIEngineContainer, false, true)
        }
    }

    private fun createWebViewEngineIcon(engineName: String, container: LinearLayout?, isLeft: Boolean, isAI: Boolean) {
        val context = container?.context ?: return
        val imageButton = ImageButton(context).apply {
            // 创建默认背景和圆角效果
            background = ColorDrawable(Color.parseColor("#40FFFFFF"))
            
            // 将图标尺寸从100dp减小到60dp
            val size = 30.dpToPx(context)
            // 确保使用正确的LayoutParams，因为要添加到LinearLayout
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(4, 4, 4, 4)
            }
            
            // 减少内边距使图标更大
            setPadding(4, 4, 4, 4)
            
            // 提高图片质量
            scaleType = ImageView.ScaleType.FIT_CENTER
            // 启用硬件加速以提高渲染质量
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // 获取引擎键值
            val engineKey = if (isAI) "ai_" + EngineUtil.getEngineKey(engineName) else EngineUtil.getEngineKey(engineName)
            
            // 获取对应的域名
            val domain = if (isAI) {
                when(EngineUtil.getEngineKey(engineName)) {
                    "chatgpt" -> "openai.com"
                    "claude" -> "claude.ai"
                    "gemini" -> "gemini.google.com"
                    "wenxin" -> "baidu.com"
                    "chatglm" -> "zhipuai.cn"
                    "qianwen" -> "aliyun.com"
                    "xinghuo" -> "xfyun.cn"
                    "perplexity" -> "perplexity.ai"
                    "phind" -> "phind.com"
                    "poe" -> "poe.com"
                    else -> "openai.com"
                }
            } else {
                when(EngineUtil.getEngineKey(engineName)) {
                    "baidu" -> "baidu.com"
                    "google" -> "google.com"
                    "bing" -> "bing.com"
                    "sogou" -> "sogou.com"
                    "360" -> "so.com"
                    "quark" -> "sm.cn"
                    "toutiao" -> "toutiao.com"
                    "zhihu" -> "zhihu.com"
                    "bilibili" -> "bilibili.com"
                    "douban" -> "douban.com"
                    "weibo" -> "weibo.com"
                    "taobao" -> "taobao.com"
                    "jd" -> "jd.com"
                    "douyin" -> "douyin.com"
                    "xiaohongshu" -> "xiaohongshu.com"
                    "qq" -> "qq.com"
                    "wechat" -> "qq.com"
                    else -> "google.com"
                }
            }
            
            // 设置默认图标先
            val iconResId = EngineUtil.getIconResourceByDomain(domain)
            setImageResource(if (iconResId != 0) iconResId else R.drawable.ic_search)
            
            // 使用IconLoader加载网站图标
            val url = "https://$domain"
            iconLoader.loadIcon(url, this@apply, if (iconResId != 0) iconResId else R.drawable.ic_search)
            
            // 添加选中状态显示
            when (container) {
                firstEngineContainer, firstAIEngineContainer -> {
                    val isSelected = engineKey == leftEngineKey
                    alpha = if (isSelected) 1.0f else 0.7f
                    background = ColorDrawable(if (isSelected) Color.parseColor("#33FFFFFF") else Color.parseColor("#20FFFFFF"))
                }
                secondEngineContainer, secondAIEngineContainer -> {
                    val isSelected = engineKey == centerEngineKey
                    alpha = if (isSelected) 1.0f else 0.7f
                    background = ColorDrawable(if (isSelected) Color.parseColor("#33FFFFFF") else Color.parseColor("#20FFFFFF"))
                }
                thirdEngineContainer, thirdAIEngineContainer -> {
                    val isSelected = engineKey == rightEngineKey
                    alpha = if (isSelected) 1.0f else 0.7f
                    background = ColorDrawable(if (isSelected) Color.parseColor("#33FFFFFF") else Color.parseColor("#20FFFFFF"))
                }
            }
            
            // 设置点击事件
            setOnClickListener {
                // 根据容器确定是哪个窗口
                when (container) {
                    firstEngineContainer, firstAIEngineContainer -> {
                        leftEngineKey = engineKey
                        settingsManager.setLeftWindowSearchEngine(engineKey)
                        updateWebViewForEngine(firstWebView, firstTitle, engineKey)
                        
                        // 更新普通搜索引擎和AI搜索引擎图标状态
                        updateEngineIconStates(firstEngineContainer, engineKey, false)
                        updateEngineIconStates(firstAIEngineContainer, engineKey, true)
                    }
                    secondEngineContainer, secondAIEngineContainer -> {
                        centerEngineKey = engineKey
                        settingsManager.setCenterWindowSearchEngine(engineKey)
                        updateWebViewForEngine(secondWebView, secondTitle, engineKey)
                        
                        // 更新普通搜索引擎和AI搜索引擎图标状态
                        updateEngineIconStates(secondEngineContainer, engineKey, false)
                        updateEngineIconStates(secondAIEngineContainer, engineKey, true)
                    }
                    thirdEngineContainer, thirdAIEngineContainer -> {
                        rightEngineKey = engineKey
                        settingsManager.setRightWindowSearchEngine(engineKey)
                        updateWebViewForEngine(thirdWebView, thirdTitle, engineKey)
                        
                        // 更新普通搜索引擎和AI搜索引擎图标状态
                        updateEngineIconStates(thirdEngineContainer, engineKey, false)
                        updateEngineIconStates(thirdAIEngineContainer, engineKey, true)
                    }
                }
            }
        }
        container.addView(imageButton)
    }

    private fun updateWebViewForEngine(webView: WebView?, titleView: TextView?, engineKey: String) {
        val currentQuery = searchInput?.text?.toString()?.trim()
        val isAI = engineKey.startsWith("ai_")
        val actualEngineKey = if (isAI) engineKey.substring(3) else engineKey
        
        try {
            if (!currentQuery.isNullOrEmpty()) {
                val encodedQuery = URLEncoder.encode(currentQuery, "UTF-8")
                val newUrl = if (isAI) {
                    EngineUtil.getAISearchEngineUrl(actualEngineKey, encodedQuery)
                } else {
                    EngineUtil.getSearchEngineSearchUrl(actualEngineKey, encodedQuery)
                }
                webView?.loadUrl(newUrl)
                titleView?.text = "${EngineUtil.getSearchEngineName(actualEngineKey, isAI)}: $currentQuery"
            } else {
                val homeUrl = if (isAI) {
                    EngineUtil.getAISearchEngineHomeUrl(actualEngineKey)
                } else {
                    EngineUtil.getSearchEngineHomeUrl(actualEngineKey)
                }
                webView?.loadUrl(homeUrl)
                titleView?.text = EngineUtil.getSearchEngineName(actualEngineKey, isAI)
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换搜索引擎失败", e)
            val fallbackUrl = if (isAI) {
                EngineUtil.getAISearchEngineHomeUrl(actualEngineKey)
            } else {
                EngineUtil.getSearchEngineHomeUrl(actualEngineKey)
            }
            webView?.loadUrl(fallbackUrl)
            titleView?.text = EngineUtil.getSearchEngineName(actualEngineKey, isAI)
        }
    }

    private fun updateEngineIconStates(container: LinearLayout?, selectedEngineKey: String, isAI: Boolean) {
        container?.let { parent ->
            for (i in 0 until parent.childCount) {
                val view = parent.getChildAt(i)
                if (view is ImageButton) {
                    // 根据容器确定当前窗口的搜索引擎和类型
                    val currentEngineKey = when (container) {
                        firstEngineContainer, firstAIEngineContainer -> leftEngineKey
                        secondEngineContainer, secondAIEngineContainer -> centerEngineKey
                        thirdEngineContainer, thirdAIEngineContainer -> rightEngineKey
                        else -> selectedEngineKey
                    }
                    
                    // 判断引擎类型是否匹配
                    val currentIsAI = currentEngineKey.startsWith("ai_")
                    val currentActualKey = if (currentIsAI) currentEngineKey.substring(3) else currentEngineKey
                    val selectedActualKey = if (isAI && selectedEngineKey.startsWith("ai_")) 
                                            selectedEngineKey.substring(3) 
                                           else selectedEngineKey
                    
                    // 设置选中状态的视觉效果
                    if (currentIsAI == isAI && (isAI && "ai_$selectedActualKey" == currentEngineKey || 
                                               !isAI && selectedActualKey == currentEngineKey)) {
                        view.setBackgroundColor(Color.parseColor("#1A000000"))
                        view.alpha = 1.0f
                    } else {
                        view.setBackgroundColor(Color.TRANSPARENT)
                        view.alpha = 0.5f
                    }
                }
            }
        }
    }

    /**
     * 设置搜索引擎切换按钮
     */
    private fun setupEngineToggleButtons() {
        // 第一个窗口的切换按钮
        firstEngineToggle?.setOnClickListener {
            val isAIVisible = firstAIScrollContainer?.visibility == View.VISIBLE
            
            // 切换AI和普通搜索引擎视图
            firstAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
            
            // 更新按钮图标
            firstEngineToggle?.setImageResource(
                if (isAIVisible) R.drawable.ic_search else R.drawable.ic_ai_search
            )
        }
        
        // 第二个窗口的切换按钮
        secondEngineToggle?.setOnClickListener {
            val isAIVisible = secondAIScrollContainer?.visibility == View.VISIBLE
            
            // 切换AI和普通搜索引擎视图
            secondAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
            
            // 更新按钮图标
            secondEngineToggle?.setImageResource(
                if (isAIVisible) R.drawable.ic_search else R.drawable.ic_ai_search
            )
        }
        
        // 第三个窗口的切换按钮
        thirdEngineToggle?.setOnClickListener {
            val isAIVisible = thirdAIScrollContainer?.visibility == View.VISIBLE
            
            // 切换AI和普通搜索引擎视图
            thirdAIScrollContainer?.visibility = if (isAIVisible) View.GONE else View.VISIBLE
            
            // 更新按钮图标
            thirdEngineToggle?.setImageResource(
                if (isAIVisible) R.drawable.ic_search else R.drawable.ic_ai_search
            )
        }
    }

    /**
     * 设置引擎容器样式，提高显示效果
     */
    private fun setupEngineContainerStyle() {
        // 设置背景和边距
        val containers = listOf(
            firstEngineContainer, secondEngineContainer, thirdEngineContainer,
            firstAIEngineContainer, secondAIEngineContainer, thirdAIEngineContainer
        )
        
        // 确保引擎容器的高度合适
        containers.forEach { container ->
            container?.apply {
                setBackgroundColor(Color.parseColor("#22000000"))
                setPadding(2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService))
                
                val existingParams = layoutParams
                if (existingParams is LinearLayout.LayoutParams) {
                    existingParams.width = LinearLayout.LayoutParams.MATCH_PARENT
                    existingParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                }
            }
        }
        
        val scrollContainers = listOf(
            firstAIScrollContainer,
            secondAIScrollContainer,
            thirdAIScrollContainer
        )
        
        scrollContainers.forEach { scrollView ->
            scrollView?.apply {
                setBackgroundColor(Color.parseColor("#11000000"))
                setPadding(1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService))
                
                isHorizontalScrollBarEnabled = true
                
                val parent = parent
                if (parent != null) {
                    when (parent) {
                        is LinearLayout -> {
                            val params = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams = params
                        }
                        is FrameLayout -> {
                            val params = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT
                            )
                            layoutParams = params
                        }
                    }
                }
            }
        }
        
        firstAIScrollContainer?.visibility = View.GONE
        secondAIScrollContainer?.visibility = View.GONE
        thirdAIScrollContainer?.visibility = View.GONE
    }

    override fun onDestroy() {
        try {
            windowManager.removeView(floatingView)
            
            if (::iconLoader.isInitialized) {
                iconLoader.clearCache()
            }
            
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "移除视图失败", e)
        }
        super.onDestroy()
    }

    private fun createEngineIcon(engineName: String): ImageView {
        val imageView = ImageView(this)
        // 增大图标尺寸从80dp到100dp
        val size = 25.dpToPx(this)
        val layoutParams = LinearLayout.LayoutParams(size, size)
        layoutParams.setMargins(8.dpToPx(this), 8.dpToPx(this), 8.dpToPx(this), 8.dpToPx(this))
        
        // 减小内边距使图标可以显示更大
        val padding = 8.dpToPx(this)
        imageView.setPadding(padding, padding, padding, padding)
        imageView.layoutParams = layoutParams
        
        // 设置高质量图片渲染
        imageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        
        // 设置背景
        imageView.setBackgroundResource(R.drawable.icon_background)
        
        val engineIcon = when (engineName.lowercase()) {
            "google" -> R.drawable.ic_google
            "bing" -> R.drawable.ic_bing
            "baidu" -> R.drawable.ic_baidu
            "weibo" -> R.drawable.ic_weibo
            "douban" -> R.drawable.ic_douban
            "taobao" -> R.drawable.ic_taobao
            "jd" -> R.drawable.ic_jd
            "douyin" -> R.drawable.ic_douyin
            "xiaohongshu" -> R.drawable.ic_xiaohongshu
            else -> R.drawable.ic_search
        }
        imageView.setImageResource(engineIcon)
        
        return imageView
    }

    private fun performSearch(webView: WebView, query: String, searchUrl: String) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = searchUrl.replace("{query}", encodedQuery)
            webView.loadUrl(url)
        } catch (e: Exception) {
            Log.e(TAG, "执行搜索失败: $e")
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createSelectorEngineIcon(engineName: String): ImageView {
        val imageView = ImageView(this)
        // 减小图标尺寸从90dp到55dp
        val size = 25.dpToPx(this)
        // 使用LinearLayout的LayoutParams，因为它会被添加到LinearLayout
        val layoutParams = LinearLayout.LayoutParams(size, size)
        layoutParams.setMargins(3.dpToPx(this), 3.dpToPx(this), 3.dpToPx(this), 3.dpToPx(this))
        
        // 减小内边距使图标可以显示更大
        val padding = 4.dpToPx(this)
        imageView.setPadding(padding, padding, padding, padding)
        imageView.layoutParams = layoutParams
        
        // 设置高质量图片渲染
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        // 启用硬件加速以提高渲染质量
        imageView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // 设置背景
        imageView.setBackgroundResource(R.drawable.icon_background)
        
        // 使用本地高清图标资源
        val engineIcon = when (engineName.lowercase()) {
            "google" -> R.drawable.ic_google
            "bing" -> R.drawable.ic_bing
            "baidu" -> R.drawable.ic_baidu
            "sogou" -> R.drawable.ic_sogou
            "360" -> R.drawable.ic_360
            "quark" -> R.drawable.ic_search
            "toutiao" -> R.drawable.ic_search
            "zhihu" -> R.drawable.ic_zhihu
            "bilibili" -> R.drawable.ic_bilibili
            "weibo" -> R.drawable.ic_weibo
            "douban" -> R.drawable.ic_douban
            "taobao" -> R.drawable.ic_taobao
            "jd" -> R.drawable.ic_jd
            "douyin" -> R.drawable.ic_douyin
            "xiaohongshu" -> R.drawable.ic_xiaohongshu
            "qq" -> R.drawable.ic_qq
            "wechat" -> R.drawable.ic_qq
            else -> R.drawable.ic_search
        }
        imageView.setImageResource(engineIcon)
        
        return imageView
    }

    private fun createSearchEngineSelector(searchBarLayout: LinearLayout, webView: WebView, isCenter: Boolean = false) {
        val horizontalScrollView = HorizontalScrollView(this).apply {
            // 确保使用正确的LayoutParams类型，因为我们添加到LinearLayout
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 显示滚动条以便用户了解可以滚动查看更多图标
            isHorizontalScrollBarEnabled = true
            
            // 使用背景资源或颜色而不是未定义的资源
            setBackgroundColor(android.graphics.Color.parseColor("#1A000000"))
        }

        val engineLayout = LinearLayout(this).apply {
            // 这里使用HorizontalScrollView的LayoutParams，因为它会被添加到HorizontalScrollView
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(3.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService), 
                      3.dpToPx(this@DualFloatingWebViewService), 
                      2.dpToPx(this@DualFloatingWebViewService))
            // 添加背景以增强视觉效果
            setBackgroundColor(Color.parseColor("#22000000"))
        }

        val engines = SearchEngine.DEFAULT_ENGINES

        for (engine in engines) {
            val engineIcon = createSelectorEngineIcon(engine.name)
            
            engineIcon.setOnClickListener {
                val currentEngine = engine
                val editText = searchBarLayout.findViewById<EditText>(R.id.search_edit_text)
                val searchText = editText.text.toString()
                
                // 保存选择的搜索引擎
                val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
                when (windowPosition) {
                    "left" -> settingsManager.setLeftWindowSearchEngine(currentEngine.name.lowercase())
                    "center" -> settingsManager.setCenterWindowSearchEngine(currentEngine.name.lowercase()) 
                    "right" -> settingsManager.setRightWindowSearchEngine(currentEngine.name.lowercase())
                }
                
                if (searchText.isNotEmpty()) {
                    performSearch(webView, searchText, currentEngine.url)
                }
                
                // 显示选中状态
                for (i in 0 until engineLayout.childCount) {
                    val view = engineLayout.getChildAt(i)
                    if (view is ImageView) {
                        view.setBackgroundResource(R.drawable.icon_background)
                    }
                }
                engineIcon.setBackgroundResource(R.drawable.icon_background_selected)
            }
            
            // 设置默认选中状态
            val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
            val defaultEngine = when (windowPosition) {
                "left" -> settingsManager.getLeftWindowSearchEngine()
                "center" -> settingsManager.getCenterWindowSearchEngine()
                else -> settingsManager.getRightWindowSearchEngine()
            }
            
            if (engine.name.lowercase() == defaultEngine) {
                engineIcon.setBackgroundResource(R.drawable.icon_background_selected)
            }
            
            engineLayout.addView(engineIcon)
        }

        horizontalScrollView.addView(engineLayout)
        
        // 设置水平滚动视图布局参数
        val hsvParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        hsvParams.setMargins(0, 2.dpToPx(this), 0, 2.dpToPx(this))
        
        // 将水平滚动视图添加到搜索栏布局
        searchBarLayout.addView(horizontalScrollView, hsvParams)
        
        // 将滚动位置设置为已选中的引擎
        horizontalScrollView.post {
            // 找到选中的引擎在engineLayout中的位置
            val selectedIndex = engines.indexOfFirst { 
                val windowPosition = if (isCenter) "center" else if (webView == firstWebView) "left" else "right"
                val defaultEngine = when (windowPosition) {
                    "left" -> settingsManager.getLeftWindowSearchEngine()
                    "center" -> settingsManager.getCenterWindowSearchEngine()
                    else -> settingsManager.getRightWindowSearchEngine()
                }
                it.name.lowercase() == defaultEngine
            }
            
            if (selectedIndex > 2) { // 只有当选择的引擎不在前几个时才滚动
                val childView = engineLayout.getChildAt(selectedIndex)
                horizontalScrollView.smoothScrollTo(childView.left - 30.dpToPx(this), 0)
            }
        }
    }

    private fun updateViewVisibilityByWindowCount() {
        when (windowCount) {
            1 -> {
                // 只显示第一个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.GONE
                secondWebView?.visibility = View.GONE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
                
                // 获取父容器
                val secondContainer = secondWebView?.parent as? View
                val thirdContainer = thirdWebView?.parent as? View
                secondContainer?.visibility = View.GONE
                thirdContainer?.visibility = View.GONE
            }
            2 -> {
                // 显示两个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
                
                // 获取父容器
                val secondContainer = secondWebView?.parent as? View
                val thirdContainer = thirdWebView?.parent as? View
                secondContainer?.visibility = View.VISIBLE
                thirdContainer?.visibility = View.GONE
            }
            else -> {
                // 显示全部三个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.VISIBLE
                thirdWebView?.visibility = View.VISIBLE
                
                // 获取父容器
                val secondContainer = secondWebView?.parent as? View
                val thirdContainer = thirdWebView?.parent as? View
                secondContainer?.visibility = View.VISIBLE
                thirdContainer?.visibility = View.VISIBLE
            }
        }
        
        // 更新布局
        container?.requestLayout()
    }

    // 根据窗口数量显示或隐藏窗口
    private fun updateWindowVisibility(windowCount: Int) {
        // 更新窗口数量变量
        this.windowCount = windowCount
        
        // 保存设置
        settingsManager.setDefaultWindowCount(windowCount)
        
        when (windowCount) {
            1 -> {
                // 只显示第一个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.GONE
                secondWebView?.visibility = View.GONE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
            }
            2 -> {
                // 显示两个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.GONE
                thirdWebView?.visibility = View.GONE
            }
            else -> {
                // 显示所有三个窗口
                firstWebView?.visibility = View.VISIBLE
                divider1?.visibility = View.VISIBLE
                secondWebView?.visibility = View.VISIBLE
                divider2?.visibility = View.VISIBLE
                thirdWebView?.visibility = View.VISIBLE
            }
        }
        
        // 更新窗口计数显示
        windowCountToggleView?.text = "$windowCount"
        
        // 更新布局参数
        updateLayoutParams()
    }

    private fun updateLayoutParams() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val windowParams = floatingView?.layoutParams as? WindowManager.LayoutParams
        
        if (isHorizontalLayout) {
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        } else {
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = WindowManager.LayoutParams.MATCH_PARENT
        }
        
        try {
            windowManager.updateViewLayout(floatingView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "更新窗口布局失败", e)
        }
        
        // 请求布局更新
        divider1?.requestLayout()
        divider2?.requestLayout()
        container?.requestLayout()
    }

    /**
     * 初始化设置
     */
    private fun initializeSettings() {
        // 从设置管理器获取配置
        settingsManager = SettingsManager.getInstance(this)
        
        // 获取窗口数量
        windowCount = settingsManager.getDefaultWindowCount()
        Log.d(TAG, "从设置中获取窗口数量: $windowCount")
        
        // 获取搜索引擎设置
        leftEngineKey = settingsManager.getLeftWindowSearchEngine()
        centerEngineKey = settingsManager.getCenterWindowSearchEngine()
        rightEngineKey = settingsManager.getRightWindowSearchEngine()
        Log.d(TAG, "搜索引擎设置 - 左: $leftEngineKey, 中: $centerEngineKey, 右: $rightEngineKey")
        
        // 获取布局方向
        isHorizontalLayout = sharedPrefs.getBoolean(KEY_IS_HORIZONTAL, true)
        
        // 更新窗口计数显示
        windowCountToggleView?.text = windowCount.toString()
        Log.d(TAG, "设置窗口计数显示: $windowCount")
        
        // 根据窗口数量更新视图显示
        updateViewVisibilityByWindowCount()
    }

    private fun saveCurrentSearchEngines() {
        try {
            val searchEngines = mutableListOf<SearchEngine>()
            
            // 收集所有活跃的WebView中的搜索引擎
            val activeWebViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
                .filter { it.visibility == View.VISIBLE }
            
            // 为每个WebView创建SearchEngine对象
            activeWebViews.forEachIndexed { index, webView ->
                val engineKey = when (index) {
                    0 -> leftEngineKey
                    1 -> centerEngineKey
                    2 -> rightEngineKey
                    else -> "baidu"
                }
                val isAI = engineKey.startsWith("ai_")
                val actualEngineKey = if (isAI) engineKey.substring(3) else engineKey
                
                // 获取当前URL和标题
                val url = webView.url ?: ""
                val title = webView.title ?: EngineUtil.getSearchEngineName(actualEngineKey, isAI)
                
                // 构建搜索引擎对象
                val engine = SearchEngine(
                    name = title,
                    url = convertToSearchUrl(url), // 转换为可用于搜索的URL
                    iconResId = R.drawable.ic_search,
                    description = "从浏览器保存的搜索引擎: ${EngineUtil.getSearchEngineName(actualEngineKey, isAI)}"
                )
                searchEngines.add(engine)
            }
            
            if (searchEngines.isNotEmpty()) {
                // 创建搜索引擎组合名称
                val groupName = searchEngines.joinToString(" + ") { 
                    it.name.take(10) // 截取名称，避免过长
                }
                
                // 保存搜索引擎组合
                val searchEngineManager = SearchEngineManager.getInstance(this)
                searchEngineManager.saveSearchEngineGroup(groupName, searchEngines)
                
                // 显示视觉反馈
                showSaveSuccessAnimation(saveButton)
                
                // 显示成功提示
                Toast.makeText(this, "已保存搜索引擎组合：$groupName", Toast.LENGTH_SHORT).show()
                
                // 通知 FloatingWindowService 更新快捷方式
                val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_SHORTCUTS")
                sendBroadcast(intent)
                
                // 延迟一会确保广播接收器有时间处理
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "搜索引擎快捷方式已更新")
                }, 300)
            } else {
                Toast.makeText(this, "没有可保存的搜索引擎", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存搜索引擎组合失败", e)
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 显示保存成功的动画效果
    private fun showSaveSuccessAnimation(view: View?) {
        view?.let {
            it.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }
    
    // 将当前URL转换为可用于搜索的URL
    private fun convertToSearchUrl(url: String): String {
        try {
            // 尝试从URL中提取查询参数
            val uri = Uri.parse(url)
            
            // 常见搜索引擎查询参数名称
            val queryParamNames = listOf("q", "query", "word", "wd", "text", "search")
            
            // 尝试每一种可能的查询参数
            for (paramName in queryParamNames) {
                val queryValue = uri.getQueryParameter(paramName)
                if (!queryValue.isNullOrEmpty()) {
                    // 找到查询参数，替换为查询占位符
                    return url.replace(queryValue, "{query}")
                }
            }
            
            // 如果找不到查询参数，返回原始URL
            return url
        } catch (e: Exception) {
            Log.e(TAG, "转换搜索URL失败", e)
            return url
        }
    }

    /**
     * 加载保存的搜索引擎组合
     * @param groupName 搜索引擎组合名称
     */
    private fun loadSearchEngineGroup(groupName: String) {
        try {
            // 从SearchEngineManager获取保存的搜索引擎组
            val searchEngineManager = com.example.aifloatingball.manager.SearchEngineManager.getInstance(this)
            val searchEngineGroups = searchEngineManager.getSearchEngineGroups()
            
            // 查找匹配名称的组
            val group = searchEngineGroups.find { it.name == groupName }
            
            if (group != null && group.engines.isNotEmpty()) {
                // 根据组内引擎数量决定窗口数量，限制最多3个
                windowCount = minOf(group.engines.size, 3)
                
                // 更新视图可见性以匹配引擎数量
                updateViewVisibilityByWindowCount()
                
                // 为每个WebView设置对应的搜索引擎
                group.engines.forEachIndexed { index, engine ->
                    when (index) {
                        0 -> {
                            // 设置左侧窗口
                            leftEngineKey = extractSearchEngineKey(engine.url)
                            firstWebView?.loadUrl(engine.url)
                            firstTitle?.text = engine.name
                        }
                        1 -> if (windowCount >= 2) {
                            // 设置中间窗口
                            centerEngineKey = extractSearchEngineKey(engine.url)
                            secondWebView?.loadUrl(engine.url)
                            secondTitle?.text = engine.name
                        }
                        2 -> if (windowCount >= 3) {
                            // 设置右侧窗口
                            rightEngineKey = extractSearchEngineKey(engine.url)
                            thirdWebView?.loadUrl(engine.url)
                            thirdTitle?.text = engine.name
                        }
                    }
                }
                
                // 更新搜索引擎图标状态
                updateEngineIcons()
                
                // 更新所有搜索引擎图标状态
                firstEngineContainer?.let { updateEngineIconStates(it, leftEngineKey, false) }
                secondEngineContainer?.let { updateEngineIconStates(it, centerEngineKey, false) }
                thirdEngineContainer?.let { updateEngineIconStates(it, rightEngineKey, false) }
                
                // 显示成功提示
                Toast.makeText(this, "已加载搜索引擎组合: $groupName", Toast.LENGTH_SHORT).show()
                
                Log.d(TAG, "成功加载搜索引擎组合: $groupName, 窗口数量: $windowCount")
            } else {
                Log.e(TAG, "未找到搜索引擎组合: $groupName")
                Toast.makeText(this, "未找到搜索引擎组合: $groupName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载搜索引擎组合失败", e)
            Toast.makeText(this, "加载搜索引擎组合失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从搜索引擎URL中提取搜索引擎键值
     */
    private fun extractSearchEngineKey(url: String): String {
        return when {
            url.contains("google.com") -> "google"
            url.contains("bing.com") -> "bing"
            url.contains("baidu.com") -> "baidu"
            url.contains("sogou.com") -> "sogou"
            url.contains("so.com") -> "360"
            url.contains("sm.cn") -> "quark"
            url.contains("toutiao.com") -> "toutiao"
            url.contains("zhihu.com") -> "zhihu"
            url.contains("bilibili.com") -> "bilibili"
            url.contains("weibo.com") -> "weibo"
            url.contains("douban.com") -> "douban"
            url.contains("taobao.com") -> "taobao"
            url.contains("jd.com") -> "jd"
            url.contains("douyin.com") -> "douyin"
            url.contains("xiaohongshu.com") -> "xiaohongshu"
            // AI搜索引擎
            url.contains("openai.com") -> "ai_chatgpt"
            url.contains("claude.ai") -> "ai_claude"
            url.contains("gemini.google.com") -> "ai_gemini"
            url.contains("perplexity.ai") -> "ai_perplexity"
            else -> "baidu" // 默认使用百度
        }
    }

    // 处理浮动球大小的更新
    private fun updateFloatingBallSize(size: Int) {
        // 本方法在当前DualFloatingWebViewService中不需要具体实现
        // 因为这个服务不管理浮动球本身
        Log.d(TAG, "收到更新浮动球大小请求：$size")
    }

    // 处理菜单布局更新
    private fun updateMenuLayout(layout: String) {
        // 本方法在当前DualFloatingWebViewService中不需要具体实现
        // 因为这个服务不管理菜单布局
        Log.d(TAG, "收到更新菜单布局请求：$layout")
    }
} 