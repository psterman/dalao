package com.example.aifloatingball

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import java.net.URLEncoder

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
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var firstWebView: WebView? = null
    private var secondWebView: WebView? = null
    private var searchInput: EditText? = null
    private var toggleLayoutButton: ImageButton? = null
    private var dualSearchButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var resizeHandle: View? = null
    private var container: LinearLayout? = null
    private var divider: View? = null
    private var singleWindowButton: ImageButton? = null
    private var firstTitle: TextView? = null
    private var secondTitle: TextView? = null

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

    // 百度和Google的基础URL
    private val baiduBaseUrl = "https://www.baidu.com/s?wd="
    private val googleBaseUrl = "https://www.google.com/search?q="

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 创建浮动窗口
            createFloatingView()
            
            // 加载上次的窗口状态
            loadWindowState()
            
            // 初始化WebView
            setupWebViews()
            
            // 设置控件事件
            setupControls()
            
        } catch (e: Exception) {
            Log.e(TAG, "创建服务失败", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                if (isGoogle) {
                    secondWebView?.loadUrl("https://www.baidu.com")
                } else {
                    secondWebView?.loadUrl("https://www.google.com")
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
        searchInput = floatingView?.findViewById(R.id.dual_search_input)
        toggleLayoutButton = floatingView?.findViewById(R.id.btn_toggle_layout)
        dualSearchButton = floatingView?.findViewById(R.id.btn_dual_search)
        closeButton = floatingView?.findViewById(R.id.btn_dual_close)
        resizeHandle = floatingView?.findViewById(R.id.dual_resize_handle)
        divider = floatingView?.findViewById(R.id.divider)
        singleWindowButton = floatingView?.findViewById(R.id.btn_single_window)
        firstTitle = floatingView?.findViewById(R.id.first_floating_title)
        secondTitle = floatingView?.findViewById(R.id.second_floating_title)
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val defaultWidth = (screenWidth * DEFAULT_WIDTH_RATIO).toInt()
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
        // 设置第一个WebView（百度）
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
            }
            
            webViewClient = WebViewClient()
            
            // 加载百度首页
            loadUrl("https://www.baidu.com")
        }
        
        // 设置第二个WebView（谷歌）
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
            }
            
            webViewClient = WebViewClient()
            
            // 加载谷歌首页
            loadUrl("https://www.google.com")
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
    }

    private fun setupTouchListeners() {
        // 处理窗口拖动
        val titleBars = listOf(
            floatingView?.findViewById<View>(R.id.first_title_bar),
            floatingView?.findViewById<View>(R.id.second_title_bar)
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
                // 在两个搜索引擎中同时搜索相同关键词
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val baiduSearchUrl = baiduBaseUrl + encodedQuery
                val googleSearchUrl = googleBaseUrl + encodedQuery
                
                firstWebView?.loadUrl(baiduSearchUrl)
                secondWebView?.loadUrl(googleSearchUrl)
                
                // 更新标题
                firstTitle?.text = "百度: $query"
                secondTitle?.text = "Google: $query"
                
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
        // 首先保存要返回的方向值
        val orientationValue = if (isHorizontalLayout) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        
        // 设置容器方向
        container?.orientation = orientationValue
        
        // 更新分割线
        if (isHorizontalLayout) {
            // 水平分割线
            divider?.layoutParams?.width = 4
            divider?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            // 垂直分割线
            divider?.layoutParams?.width = ViewGroup.LayoutParams.MATCH_PARENT
            divider?.layoutParams?.height = 4
        }
        
        // 如果需要更新分割线布局，应用更改
        divider?.layoutParams?.let { params ->
            divider?.layoutParams = params
        }
        
        // 返回方向值
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

    override fun onDestroy() {
        try {
            windowManager.removeView(floatingView)
        } catch (e: Exception) {
            Log.e(TAG, "移除视图失败", e)
        }
        super.onDestroy()
    }
} 