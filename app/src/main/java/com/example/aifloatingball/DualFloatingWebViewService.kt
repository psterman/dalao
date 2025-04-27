package com.example.aifloatingball

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.ActionMode
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.manager.SearchEngineManager
import com.example.aifloatingball.manager.TextSelectionManager
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.EngineUtil
import com.example.aifloatingball.utils.FaviconManager
import com.example.aifloatingball.utils.IconLoader
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class DualFloatingWebViewService : Service() {
    companion object {
        var isRunning = false
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
    private var switchToNormalButton: ImageButton? = null // 添加切换按钮
    
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

    private var textSelectionMenuView: View? = null
    private var textSelectionManager: TextSelectionManager? = null
    private var currentSelectedText: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 添加自动隐藏菜单的Handler
    private val menuAutoHideHandler = Handler(Looper.getMainLooper())

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
        isRunning = true
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
            
            // 设置WebView的文本选择功能
            enableTextSelectionOnWebViews()
            
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
        
        // 使用动态方法查找切换按钮，避免编译错误
        val btnSwitchId = resources.getIdentifier("btn_switch_normal", "id", packageName)
        if (btnSwitchId != 0) {
            switchToNormalButton = floatingView?.findViewById(btnSwitchId)
            Log.d(TAG, "通过资源ID查找到切换按钮: $btnSwitchId")
        } else {
            // 如果没有找到ID，尝试其他可能的ID
            val alternativeIds = arrayOf(
                "btn_switch", 
                "btn_switch_mode", 
                "btn_mode"
            )
            
            for (alternativeId in alternativeIds) {
                val id = resources.getIdentifier(alternativeId, "id", packageName)
                if (id != 0) {
                    switchToNormalButton = floatingView?.findViewById(id)
                    if (switchToNormalButton != null) {
                        Log.d(TAG, "通过替代ID找到切换按钮: $alternativeId")
                        break
                    }
                }
            }
        }
        
        // 如果仍未找到，尝试动态创建按钮
        if (switchToNormalButton == null) {
            Log.e(TAG, "无法找到切换按钮，尝试动态创建")
            
            // 查找顶部工具栏
            val topBar = floatingView?.findViewById<ViewGroup>(R.id.top_control_bar)
            if (topBar != null) {
                // 创建新的按钮
                val newButton = ImageButton(this).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setImageResource(R.drawable.ic_switch_mode)
                    background = getDrawable(R.drawable.circle_ripple)
                    contentDescription = "切换到普通模式"
                    setPadding(8, 8, 8, 8)
                }
                
                // 添加到工具栏
                topBar.addView(newButton)
                switchToNormalButton = newButton
                Log.d(TAG, "已动态创建并添加切换按钮")
            } else {
                Log.e(TAG, "无法找到顶部工具栏，切换按钮将无法使用")
            }
        }
    }

    /**
     * 创建窗口布局参数
     */
    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        // 获取屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val defaultWidth = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
        val defaultHeight = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
        
        // FLAG_NOT_FOCUSABLE参数允许窗口与下层窗口交互，但需要在输入时切换为可获取焦点
        // FLAG_ALT_FOCUSABLE_IM参数防止输入法遮挡，在需要输入时将其移除
        // 其他参数设置悬浮窗行为
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                defaultWidth,
                defaultHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                defaultWidth,
                defaultHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }
        
        params.gravity = Gravity.CENTER
        params.windowAnimations = android.R.style.Animation_Dialog
        
        // 启用接收触摸事件的标志
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        
        return params
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
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        
        webViews.forEach { webView ->
            // 配置WebView设置
            webView.settings.apply {
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
                setDefaultZoom(WebSettings.ZoomDensity.MEDIUM)
                userAgentString = userAgentString + " Mobile"
            }
            
            // 设置触摸事件监听
            webView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 记录触摸开始位置和时间
                        view.tag = event
                    }
                    MotionEvent.ACTION_UP -> {
                        // 获取DOWN事件
                        val downEvent = view.tag as? MotionEvent
                        if (downEvent != null) {
                            // 计算是否为长按
                            val duration = event.eventTime - downEvent.downTime
                            if (duration >= ViewConfiguration.getLongPressTimeout()) {
                                // 长按时间已达到，处理长按事件
                                handleLongPress(webView, event)
                            }
                        }
                    }
                }
                // 返回false让事件继续传递给WebView内部处理
                false
            }

            // 设置WebView客户端
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "开始加载页面: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "页面加载完成: $url")
                    view?.let { 
                        // 启用文本选择
                        enableTextSelectionMode(it)
                        
                        // 注册JavaScript接口
                        it.addJavascriptInterface(
                            TextSelectionJavaScriptInterface(it),
                            "textSelectionCallback"
                        )
                    }
                }
            }
        }

        // 初始化WebView首页
        firstWebView?.let {
            val leftHomeUrl = EngineUtil.getSearchEngineHomeUrl(leftEngineKey)
            it.loadUrl(leftHomeUrl)
            firstTitle?.text = EngineUtil.getSearchEngineName(leftEngineKey, false)
        }
        
        secondWebView?.let {
            val centerHomeUrl = EngineUtil.getSearchEngineHomeUrl(centerEngineKey)
            it.loadUrl(centerHomeUrl)
            secondTitle?.text = EngineUtil.getSearchEngineName(centerEngineKey, false)
        }
        
        thirdWebView?.let {
            val rightHomeUrl = EngineUtil.getSearchEngineHomeUrl(rightEngineKey)
            it.loadUrl(rightHomeUrl)
            thirdTitle?.text = EngineUtil.getSearchEngineName(rightEngineKey, false)
        }
    }

    // 初始化文本选择功能
    private fun initTextSelection(webView: WebView) {
        // 创建文本选择管理器
        if (textSelectionManager == null) {
            textSelectionManager = TextSelectionManager(
                context = this,
                webView = webView,
                windowManager = windowManager,
                onSelectionChanged = { selectedText: String ->
                    currentSelectedText = selectedText
                    Log.d(TAG, "选中文本变化: $selectedText")
                    
                    if (selectedText.isNotEmpty()) {
                        // 获取WebView中心点位置作为菜单显示位置
                        val location = IntArray(2)
                        webView.getLocationOnScreen(location)
                        val centerX = location[0] + webView.width / 2
                        val centerY = location[1] + webView.height / 2
                        
                        // 显示选择菜单
                        showTextSelectionMenu(webView, centerX, centerY)
                    }
                }
            )
        }
        
        // 注入JavaScript增强文本选择能力
        textSelectionManager?.injectSelectionJavaScript()
        
        // 启用文本选择模式
        enableTextSelectionMode(webView)
    }

    private fun handleLongPress(webView: WebView, event: MotionEvent) {
        // 获取相对于WebView的坐标
        val x = event.x.toInt()
        val y = event.y.toInt()
        
        Log.d(TAG, "处理长按事件: WebView相对坐标($x, $y)")
        
        // 清除先前的选择
        textSelectionManager?.clearSelection()
        
        // 创建文本选择管理器
        textSelectionManager = TextSelectionManager(
            context = this,
            webView = webView,
            windowManager = windowManager,
            onSelectionChanged = { selectedText: String ->
                currentSelectedText = selectedText
                if (selectedText.isNotEmpty()) {
                    // 显示菜单
                    val screenX = event.rawX.toInt()
                    val screenY = event.rawY.toInt()
                    showTextSelectionMenu(webView, screenX, screenY)
                }
            }
        )
        
        // 触发震动反馈
        try {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
            Log.d(TAG, "震动反馈已触发")
        } catch (e: Exception) {
            Log.e(TAG, "震动反馈失败: ${e.message}")
        }
        
        // 使用更可靠的方法选择文本
        val script = """
            (function() {
                try {
                    // 设置选择样式
                    var style = document.createElement('style');
                    style.textContent = `
                        * {
                            -webkit-user-select: text !important;
                            user-select: text !important;
                        }
                        ::selection {
                            background: rgba(33, 150, 243, 0.4) !important;
                        }
                    `;
                    document.head.appendChild(style);
                    
                    // 获取点击位置的元素
                    var clickElement = document.elementFromPoint($x, $y);
                    if (!clickElement) return "no-element";
                    
                    // 确保元素可选
                    clickElement.style.webkitUserSelect = 'text';
                    clickElement.style.userSelect = 'text';
                    
                    // 尝试选取一个单词
                    var wordScript = `
                        var selection = window.getSelection();
                        selection.removeAllRanges();
                        
                        var range = document.caretRangeFromPoint($x, $y);
                        if (range) {
                            selection.addRange(range);
                            selection.modify('extend', 'forward', 'word');
                            
                            // 如果选择为空，尝试向后选择一个单词
                            if (selection.toString().trim() === '') {
                                selection.modify('move', 'backward', 'word');
                                selection.modify('extend', 'forward', 'word');
                            }
                            
                            // 如果仍然为空，尝试选择父元素的一部分
                            if (selection.toString().trim() === '') {
                                selection.removeAllRanges();
                                var elem = document.elementFromPoint($x, $y);
                                var range = document.createRange();
                                range.selectNodeContents(elem);
                                selection.addRange(range);
                            }
                        }
                        return selection.toString();
                    `;
                    
                    // 执行选择
                    var wordResult = eval(wordScript);
                    
                    // 如果选择成功获取选择范围
                    var selection = window.getSelection();
                    if (selection.rangeCount > 0 && selection.toString().trim().length > 0) {
                        var range = selection.getRangeAt(0);
                        var rects = range.getClientRects();
                        
                        if (rects.length > 0) {
                            var firstRect = rects[0];
                            var lastRect = rects[rects.length - 1];
                            
                            // 保护选择不被清除
                            document.addEventListener('mousedown', function(e) {
                                e.stopPropagation();
                            }, true);
                            document.addEventListener('touchstart', function(e) {
                                e.stopPropagation();
                            }, true);
                            
                            return JSON.stringify({
                                text: selection.toString(),
                                left: {
                                    x: firstRect.left + window.scrollX,
                                    y: firstRect.bottom + window.scrollY
                                },
                                right: {
                                    x: lastRect.right + window.scrollX,
                                    y: lastRect.bottom + window.scrollY
                                }
                            });
                        }
                    }
                    
                    return "no-selection";
                } catch (e) {
                    console.error("选择错误:", e);
                    return "error:" + e.toString();
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "JavaScript选择结果: $result")
            
            if (result != "null" && !result.startsWith("\"no-") && !result.startsWith("\"error:")) {
                try {
                    val jsonStr = result.replace("\\\"", "\"")
                        .replace("^\"|\"$".toRegex(), "")
                    val json = JSONObject(jsonStr)
                    
                    val selectedText = json.getString("text")
                    currentSelectedText = selectedText
                    
                    if (selectedText.isNotEmpty()) {
                        val leftPos = json.getJSONObject("left")
                        val rightPos = json.getJSONObject("right")
                        
                        // 确保在主线程显示选择柄
                        mainHandler.post {
                            // 显示选择柄
                            textSelectionManager?.showSelectionHandles(
                                leftPos.getInt("x"),
                                leftPos.getInt("y"),
                                rightPos.getInt("x"),
                                rightPos.getInt("y")
                            )
                            
                            // 防止选择消失
                            preventSelectionLoss(webView)
                        }
                        
                        Log.d(TAG, "成功选中文本: $selectedText")
                    }
                            } catch (e: Exception) {
                    Log.e(TAG, "解析选择结果失败: ${e.message}")
                }
            } else {
                Log.e(TAG, "未能选中文本: $result")
            }
        }
    }

    // 防止选择丢失
    private fun preventSelectionLoss(webView: WebView) {
        val script = """
            (function() {
                // 保存当前选择
                var savedSelection = window.getSelection().toString();
                
                // 禁用可能清除选择的事件
                document.addEventListener('touchstart', function(e) {
                    e.stopPropagation();
                }, true);
                
                document.addEventListener('mousedown', function(e) {
                    e.stopPropagation();
                }, true);
                
                // 禁用长按菜单
                document.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                }, true);
                
                return "selection-protected";
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "选择保护结果: $result")
        }
    }

    // 显示文本选择菜单
    private fun showTextSelectionMenu(webView: WebView, x: Int, y: Int) {
        Log.d(TAG, "显示文本选择菜单: ($x, $y)")
        
        // 如果菜单已显示，先移除
        hideTextSelectionMenu()
        
        // 加载菜单布局
        val menuView = LayoutInflater.from(this).inflate(R.layout.text_selection_menu, null)
        
        // 设置菜单按钮点击事件
        menuView.findViewById<View>(R.id.action_copy).setOnClickListener {
            Log.d(TAG, "复制选中文本")
            webView.evaluateJavascript("javascript:window.TextSelection.getSelectedText()", { selectedText ->
                if (selectedText != "null" && selectedText.isNotEmpty()) {
                    val text = JSONObject(selectedText).optString("text", "")
                    if (text.isNotEmpty()) {
                        copyToClipboard(text)
                        Toast.makeText(this, "已复制文本", Toast.LENGTH_SHORT).show()
                    }
                }
                hideTextSelectionMenu()
                webView.evaluateJavascript("javascript:window.TextSelection.clearSelection()", null)
            })
        }
        
        menuView.findViewById<View>(R.id.action_cut).setOnClickListener {
            Log.d(TAG, "剪切选中文本")
            webView.evaluateJavascript("javascript:window.TextSelection.getSelectedText()", { selectedText ->
                if (selectedText != "null" && selectedText.isNotEmpty()) {
                    val text = JSONObject(selectedText).optString("text", "")
                    if (text.isNotEmpty()) {
                        copyToClipboard(text)
                        Toast.makeText(this, "已剪切文本", Toast.LENGTH_SHORT).show()
                        // 清除选中的文本
                        webView.evaluateJavascript(
                            "javascript:document.execCommand('delete', false, null)", null
                        )
                    }
                }
                hideTextSelectionMenu()
                webView.evaluateJavascript("javascript:window.TextSelection.clearSelection()", null)
            })
        }
        
        menuView.findViewById<View>(R.id.action_paste).setOnClickListener {
            Log.d(TAG, "粘贴文本")
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                val item = clipboardManager.primaryClip?.getItemAt(0)
                val pasteText = item?.text?.toString() ?: ""
                if (pasteText.isNotEmpty()) {
                    // 使用JavaScript执行粘贴操作
                    val escapedText = URLEncoder.encode(pasteText, "UTF-8")
                    webView.evaluateJavascript(
                        "javascript:document.execCommand('insertText', false, decodeURIComponent('$escapedText'))",
                        null
                    )
                }
            }
            hideTextSelectionMenu()
            webView.evaluateJavascript("javascript:window.TextSelection.clearSelection()", null)
        }
        
        menuView.findViewById<View>(R.id.action_select_all).setOnClickListener {
            Log.d(TAG, "全选")
            webView.evaluateJavascript("javascript:document.execCommand('selectAll', false, null)", null)
        }
        
        // 计算菜单显示位置
        val position = calculateMenuPosition(webView, x, y, menuView)
        
        // 设置菜单布局参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.x = position.x
            this.y = position.y
            gravity = Gravity.START or Gravity.TOP
        }
        
        // 添加菜单视图到窗口
        windowManager.addView(menuView, params)
        textSelectionMenuView = menuView
        
        // 添加动画效果
        menuView.alpha = 0f
        menuView.scaleX = 0.8f
        menuView.scaleY = 0.8f
        
        menuView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
            
        // 添加自动隐藏计时器
        menuAutoHideHandler.removeCallbacksAndMessages(null)
        menuAutoHideHandler.postDelayed({
            if (textSelectionMenuView != null) {
                hideTextSelectionMenu()
                webView.evaluateJavascript("javascript:window.TextSelection.clearSelection()", null)
            }
        }, 8000) // 8秒后自动隐藏
    }
    
    private fun hideTextSelectionMenu() {
        textSelectionMenuView?.let {
            try {
                it.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        try {
                            windowManager.removeView(it)
        } catch (e: Exception) {
                            Log.e(TAG, "移除菜单视图失败", e)
                        }
                        textSelectionMenuView = null
                    }
                    .start()
            } catch (e: Exception) {
                Log.e(TAG, "隐藏文本选择菜单失败", e)
                try {
                    windowManager.removeView(it)
                } catch (e2: Exception) {
                    Log.e(TAG, "移除菜单视图失败", e2)
                }
                textSelectionMenuView = null
            }
        }
        menuAutoHideHandler.removeCallbacksAndMessages(null)
    }

    // 计算菜单显示位置
    private fun calculateMenuPosition(webView: WebView, x: Int, y: Int, menuView: View): Point {
        // 测量菜单视图大小
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        val menuWidth = menuView.measuredWidth
        val menuHeight = menuView.measuredHeight
        
        // 获取WebView在屏幕上的位置
        val webViewLocation = IntArray(2)
        webView.getLocationOnScreen(webViewLocation)
        
        // 屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 计算菜单位置，优先显示在选择点上方
        // x坐标：优先水平居中于点击位置，但不超出屏幕边界
        val menuX = max(0, min(x - menuWidth / 2, screenWidth - menuWidth))
        
        // y坐标：优先在点击位置上方45dp，如果太靠上则放在点击位置下方25dp
        val offsetUp = dpToPx(45)
        val offsetDown = dpToPx(25)
        
        var menuY = y - menuHeight - offsetUp
        
        // 如果太靠上，或者与WebView顶部太近，放到触摸点下方
        if (menuY < 0 || (y - webViewLocation[1]) < menuHeight) {
            menuY = y + offsetDown
        }
        
        // 确保不超出屏幕底部
        if (menuY + menuHeight > screenHeight) {
            menuY = screenHeight - menuHeight - dpToPx(10)
        }
        
        Log.d(TAG, "菜单位置: ($menuX, $menuY), 屏幕: ${screenWidth}x${screenHeight}, 菜单: ${menuWidth}x${menuHeight}")
        return Point(menuX, menuY)
    }
    
    // 辅助方法：dp转像素
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    // 剪贴板操作
    private fun copyToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("selected text", text)
        clipboardManager.setPrimaryClip(clipData)
        Log.d(TAG, "已复制文本: ${text.take(20)}${if (text.length > 20) "..." else ""}")
    }
    
    private fun pasteToWebView(webView: WebView) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboardManager.hasPrimaryClip()) {
            val clipData = clipboardManager.primaryClip
            val clipText = clipData?.getItemAt(0)?.text?.toString() ?: ""
            
            if (clipText.isNotEmpty()) {
                // 使用JavaScript插入文本
                val escapedText = clipText.replace("\\", "\\\\").replace("'", "\\'")
                    .replace("\n", "\\n").replace("\r", "\\r")
                
                webView.evaluateJavascript(
                    "javascript:(function() { document.execCommand('insertText', false, '$escapedText'); })();",
                    null
                )
            }
                    } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
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
                // 使用半透明背景
                setBackgroundColor(Color.parseColor("#22000000"))
                setPadding(2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService), 
                           2.dpToPx(this@DualFloatingWebViewService))
                
                // 优化布局参数，确保内容不被截断
                // 不直接设置布局参数，而是保留原有的layoutParams类型
                val existingParams = layoutParams
                if (existingParams is LinearLayout.LayoutParams) {
                    existingParams.width = LinearLayout.LayoutParams.MATCH_PARENT
                    existingParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
                }
            }
        }
        
        // 优化水平滚动视图，确保能够滚动查看所有图标
        val scrollContainers = listOf(
            firstAIScrollContainer,
            secondAIScrollContainer,
            thirdAIScrollContainer
        )
        
        scrollContainers.forEach { scrollView ->
            scrollView?.apply {
                // 设置滚动视图的样式
                setBackgroundColor(Color.parseColor("#11000000"))
                setPadding(1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService), 
                          1.dpToPx(this@DualFloatingWebViewService))
                
                // 显示水平滚动条，帮助用户理解可以滚动
                isHorizontalScrollBarEnabled = true
                
                // 根据父容器类型设置正确的LayoutParams
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
        
        // 单独调整AI容器的初始可见性
        firstAIScrollContainer?.visibility = View.GONE
        secondAIScrollContainer?.visibility = View.GONE
        thirdAIScrollContainer?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            // Dismiss any open popup
            textSelectionMenuView?.let {
                try {
                    it.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(150)
                        .setInterpolator(AccelerateInterpolator())
                        .withEndAction {
                            try {
                                windowManager.removeView(it)
                            } catch (e: Exception) {
                                Log.e(TAG, "移除菜单视图失败", e)
                            }
                            textSelectionMenuView = null
                        }
                        .start()
                } catch (e: Exception) {
                    Log.e(TAG, "隐藏文本选择菜单失败", e)
                    try {
                        windowManager.removeView(it)
                    } catch (e2: Exception) {
                        Log.e(TAG, "移除菜单视图失败", e2)
                    }
                    textSelectionMenuView = null
                }
            }
            menuAutoHideHandler.removeCallbacksAndMessages(null)
            
            windowManager.removeView(floatingView)
            
            // 清理IconLoader缓存
            if (::iconLoader.isInitialized) {
                iconLoader.clearCache()
            }
            
            // 停止前台服务
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "移除视图失败", e)
        }
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
                        view.setBackgroundResource(R.drawable.icon_background_selected)
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
     * 切换到普通模式
     */
    private fun switchToNormalMode() {
        try {
            // 启动 HomeActivity
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                // 如果当前有URL，传递给 HomeActivity
                when (windowCount) {
                    1 -> firstWebView?.url?.let { putExtra("url", it) }
                    2 -> {
                        // 获取第一个和第二个WebView的URL
                        val urls = mutableListOf<String>()
                        firstWebView?.url?.let { urls.add(it) }
                        secondWebView?.url?.let { urls.add(it) }
                        putExtra("urls", urls.toTypedArray())
                    }
                    else -> {
                        // 获取所有WebView的URL
                        val urls = mutableListOf<String>()
                        firstWebView?.url?.let { urls.add(it) }
                        secondWebView?.url?.let { urls.add(it) }
                        thirdWebView?.url?.let { urls.add(it) }
                        putExtra("urls", urls.toTypedArray())
                    }
                }
            }
            startActivity(intent)
            
            // 显示切换提示
            Toast.makeText(this, "正在切换到普通浏览模式", Toast.LENGTH_SHORT).show()
            
            // 停止当前服务
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "切换到普通模式失败: ${e.message}")
            Toast.makeText(this, "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 为所有WebView启用文本选择功能
     */
    private fun enableTextSelectionOnWebViews() {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        
        webViews.forEach { webView ->
            webView.isLongClickable = true
            
            // 设置长按监听器
            webView.setOnLongClickListener { view ->
                val event = view.tag as? MotionEvent ?: return@setOnLongClickListener false
                
                // 清除之前的选择
                textSelectionManager?.clearSelection()
                
                // 创建新的选择管理器
                textSelectionManager = TextSelectionManager(
                    context = this@DualFloatingWebViewService,
                    webView = webView,
                    windowManager = windowManager,
                    onSelectionChanged = { selectedText: String ->  // 显式指定参数类型
                        currentSelectedText = selectedText
                        if (selectedText.isNotEmpty()) {
                            showTextSelectionMenu(webView, event.rawX.toInt(), event.rawY.toInt())
                        }
                    }
                )
                
                // 开始选择
                textSelectionManager?.startSelection(
                    event.x.toInt(),
                    event.y.toInt()
                )
                
                true
            }
            
            // 保存触摸事件
            webView.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.tag = event
                    }
                    MotionEvent.ACTION_UP -> {
                        // 处理长按事件
                        val downEvent = view.tag as? MotionEvent
                        if (downEvent != null && 
                            event.eventTime - downEvent.downTime >= ViewConfiguration.getLongPressTimeout()) {
                            handleLongPress(webView, event)
                        }
                    }
                }
                false
            }
            
            // 启用文本选择
            enableWebViewTextSelection(webView)
        }
    }

    /**
     * 切换窗口是否可获得焦点的标志，用于处理输入法
     * @param focusable 是否可获得焦点
     */
    private fun toggleWindowFocusableFlag(focusable: Boolean) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (focusable) {
                // 移除FLAG_NOT_FOCUSABLE标志，使窗口可以获取焦点
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                // 添加FLAG_NOT_FOCUSABLE标志，使窗口不可获取焦点
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            
            // 应用新参数
            windowManager.updateViewLayout(floatingView, params)
            
            // 如果切换到可获取焦点，则请求焦点
            if (focusable) {
                floatingView?.requestFocus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换窗口焦点状态失败: ${e.message}")
        }
    }

    private fun setupControls() {
        // 设置窗口切换按钮
        toggleLayoutButton?.setOnClickListener {
            isHorizontalLayout = !isHorizontalLayout
            updateLayoutOrientation()
        }

        // 设置搜索按钮
        dualSearchButton?.setOnClickListener {
            performDualSearch()
        }

        // 设置关闭按钮
        closeButton?.setOnClickListener {
            stopSelf()
        }

        // 设置搜索输入框
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performDualSearch()
                true
            } else {
                false
            }
        }

        // 设置保存按钮
        saveButton?.setOnClickListener {
            saveCurrentSearchEngines()
        }

        // 设置切换按钮
        switchToNormalButton?.setOnClickListener {
            switchToNormalMode()
        }
    }

    private fun updateLayoutOrientation() {
        container?.orientation = if (isHorizontalLayout) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        
        // 更新布局参数
        updateLayoutParams()
        
        // 保存布局方向
        sharedPrefs.edit().putBoolean(KEY_IS_HORIZONTAL, isHorizontalLayout).apply()
    }

    private fun getSearchUrl(engineKey: String, query: String): String {
        return when(engineKey.lowercase()) {
            "baidu" -> "https://www.baidu.com/s?wd=$query"
            "google" -> "https://www.google.com/search?q=$query"
            "bing" -> "https://www.bing.com/search?q=$query"
            "sogou" -> "https://www.sogou.com/web?query=$query"
            "360" -> "https://www.so.com/s?q=$query"
            "zhihu" -> "https://www.zhihu.com/search?q=$query"
            "bilibili" -> "https://search.bilibili.com/all?keyword=$query"
            "weibo" -> "https://s.weibo.com/weibo?q=$query"
            "douban" -> "https://www.douban.com/search?q=$query"
            "taobao" -> "https://s.taobao.com/search?q=$query"
            "jd" -> "https://search.jd.com/Search?keyword=$query"
            "douyin" -> "https://www.douyin.com/search/$query"
            "xiaohongshu" -> "https://www.xiaohongshu.com/search_result?keyword=$query"
            else -> "https://www.baidu.com/s?wd=$query"
        }
    }

    private fun performDualSearch() {
        val query = searchInput?.text?.toString() ?: return
        if (query.isEmpty()) return

        // 执行搜索
        when (windowCount) {
            1 -> {
                firstWebView?.let { performSearch(it, query, getSearchUrl(leftEngineKey, query)) }
            }
            2 -> {
                firstWebView?.let { performSearch(it, query, getSearchUrl(leftEngineKey, query)) }
                secondWebView?.let { performSearch(it, query, getSearchUrl(centerEngineKey, query)) }
            }
            else -> {
                firstWebView?.let { performSearch(it, query, getSearchUrl(leftEngineKey, query)) }
                secondWebView?.let { performSearch(it, query, getSearchUrl(centerEngineKey, query)) }
                thirdWebView?.let { performSearch(it, query, getSearchUrl(rightEngineKey, query)) }
            }
        }

        // 隐藏输入法
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchInput?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun updateEngineIcons() {
        // 更新第一个窗口的图标
        firstEngineContainer?.let { updateEngineIconStates(it, leftEngineKey, false) }
        
        // 更新第二个窗口的图标
        secondEngineContainer?.let { updateEngineIconStates(it, centerEngineKey, false) }
        
        // 更新第三个窗口的图标
        thirdEngineContainer?.let { updateEngineIconStates(it, rightEngineKey, false) }
    }

    private fun updateEngineIconStates(container: LinearLayout, selectedEngine: String, isAI: Boolean) {
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            if (view is ImageView) {
                val engineName = view.tag as? String
                if (engineName == selectedEngine) {
                    view.setBackgroundResource(R.drawable.icon_background_selected)
                } else {
                    view.setBackgroundResource(R.drawable.icon_background)
                }
            }
        }
    }

    private fun enableWebViewTextSelection(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        
        // 注入JavaScript以启用文本选择
        val script = """
            (function() {
                document.documentElement.style.webkitUserSelect = 'text';
                document.documentElement.style.userSelect = 'text';
                
                document.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                });
                
                document.addEventListener('selectionchange', function() {
                    var selection = window.getSelection();
                    if (selection.toString().length > 0) {
                        window.textSelectionCallback.onSelectionChanged(selection.toString());
                    }
                });
            })();
        """.trimIndent()
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(TextSelectionJavaScriptInterface(webView), "textSelectionCallback")
        
        webView.evaluateJavascript(script, null)
    }

    private fun enableTextSelectionMode(webView: WebView) {
        val script = """
            (function() {
                // 创建并应用样式，确保文本可选
                var styleEl = document.createElement('style');
                styleEl.textContent = `
                    * {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }
                    ::selection {
                        background: rgba(33, 150, 243, 0.4) !important;
                    }
                `;
                document.head.appendChild(styleEl);
                
                // 禁用页面自身的选择菜单
                document.documentElement.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                    return false;
                }, true);
                
                // 针对特定元素优化选择行为
                var elements = document.querySelectorAll('p, div, span, h1, h2, h3, h4, h5, h6, article, section');
                for (var i = 0; i < elements.length; i++) {
                    elements[i].style.webkitUserSelect = 'text';
                    elements[i].style.userSelect = 'text';
                }
                
                // 重置可能干扰选择的行为
                var resetElements = document.querySelectorAll('*[ontouchstart], *[oncontextmenu]');
                for (var i = 0; i < resetElements.length; i++) {
                    resetElements[i].ontouchstart = null;
                    resetElements[i].oncontextmenu = null;
                }
                
                console.log('文本选择模式已启用');
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script, null)
    }

    // 文本选择JavaScript接口
    private inner class TextSelectionJavaScriptInterface(private val webView: WebView) {
        @JavascriptInterface
        fun onSelectionChanged(
            selectedText: String,
            leftX: Float = 0f,
            leftY: Float = 0f,
            rightX: Float = 0f,
            rightY: Float = 0f
        ) {
            mainHandler.post {
                currentSelectedText = selectedText
                if (selectedText.isNotEmpty()) {
                    // 转换为相对于WebView的坐标
                    textSelectionManager?.showSelectionHandles(
                        leftX.toInt(),
                        leftY.toInt(),
                        rightX.toInt(),
                        rightY.toInt()
                    )
                }
            }
        }
    }
} 