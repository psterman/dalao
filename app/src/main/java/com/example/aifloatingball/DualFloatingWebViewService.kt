package com.example.aifloatingball

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.FaviconManager
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
        private const val WEBVIEW_WIDTH_DP = 300 // 每个WebView的宽度
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
    private var firstTitle: TextView? = null
    private var secondTitle: TextView? = null
    private var thirdTitle: TextView? = null  // 添加第三个标题
    private var firstEngineContainer: LinearLayout? = null
    private var secondEngineContainer: LinearLayout? = null
    private var thirdEngineContainer: LinearLayout? = null  // 添加第三个引擎容器
    private var searchEnginePopupWindow: PopupWindow? = null
    private var currentEngineKey: String = "baidu"
    private var currentWebView: WebView? = null
    private var currentTitle: TextView? = null
    
    private lateinit var settingsManager: SettingsManager
    private var leftEngineKey: String = "baidu"
    private var centerEngineKey: String = "bing"  // 添加中间引擎键
    private var rightEngineKey: String = "google"

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 初始化设置管理器
            settingsManager = SettingsManager.getInstance(this)
            
            // 初始化Favicon管理器
            faviconManager = FaviconManager.getInstance(this)
            
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
            firstEngineContainer?.let { updateEngineIconStates(it, leftEngineKey) }
            secondEngineContainer?.let { updateEngineIconStates(it, centerEngineKey) }
            thirdEngineContainer?.let { updateEngineIconStates(it, rightEngineKey) }
            
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
        thirdWebView = floatingView?.findViewById(R.id.third_floating_webview)  // 添加第三个WebView
        searchInput = floatingView?.findViewById(R.id.dual_search_input)
        toggleLayoutButton = floatingView?.findViewById(R.id.btn_toggle_layout)
        dualSearchButton = floatingView?.findViewById(R.id.btn_dual_search)
        closeButton = floatingView?.findViewById(R.id.btn_dual_close)
        resizeHandle = floatingView?.findViewById(R.id.dual_resize_handle)
        divider1 = floatingView?.findViewById(R.id.divider1)  // 更新divider1
        divider2 = floatingView?.findViewById(R.id.divider2)  // 添加divider2
        singleWindowButton = floatingView?.findViewById(R.id.btn_single_window)
        firstTitle = floatingView?.findViewById(R.id.first_floating_title)
        secondTitle = floatingView?.findViewById(R.id.second_floating_title)
        thirdTitle = floatingView?.findViewById(R.id.third_floating_title)  // 添加第三个标题
        firstEngineContainer = floatingView?.findViewById(R.id.first_engine_container)
        secondEngineContainer = floatingView?.findViewById(R.id.second_engine_container)
        thirdEngineContainer = floatingView?.findViewById(R.id.third_engine_container)  // 添加第三个引擎容器
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
        // 获取窗口容器
        val container = floatingView?.findViewById<LinearLayout>(R.id.dual_webview_container)
        
        // 设置窗口数量
        val windowCount = settingsManager.getDefaultWindowCount()
        when (windowCount) {
            1 -> {
                // 单窗口模式
                floatingView?.findViewById<View>(R.id.divider1)?.visibility = View.GONE
                floatingView?.findViewById<View>(R.id.divider2)?.visibility = View.GONE
                floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.visibility = View.GONE
                floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.visibility = View.GONE
            }
            2 -> {
                // 双窗口模式
                floatingView?.findViewById<View>(R.id.divider1)?.visibility = View.VISIBLE
                floatingView?.findViewById<View>(R.id.divider2)?.visibility = View.GONE
                floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.visibility = View.VISIBLE
                floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.visibility = View.GONE
            }
            3 -> {
                // 三窗口模式
                floatingView?.findViewById<View>(R.id.divider1)?.visibility = View.VISIBLE
                floatingView?.findViewById<View>(R.id.divider2)?.visibility = View.VISIBLE
                floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.visibility = View.VISIBLE
                floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.visibility = View.VISIBLE
            }
        }

        // 配置WebView设置
        listOf(firstWebView, secondWebView, thirdWebView).forEach { webView ->
            webView?.settings?.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                allowFileAccess = true
                allowContentAccess = true
                setGeolocationEnabled(true)
                cacheMode = WebSettings.LOAD_DEFAULT
                setCacheMode(WebSettings.LOAD_DEFAULT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
        }

        // 设置搜索引擎
        setupSearchEngines()

        // 设置窗口大小和位置
        updateWindowLayout()

        // 设置拖拽和调整大小
        setupDragAndResize()
    }

    private fun setupSearchEngines() {
        // 设置左侧窗口搜索引擎
        val leftEngine = settingsManager.getLeftWindowSearchEngine()
        updateWebViewForEngine(R.id.first_floating_webview, leftEngine)
        updateEngineIcons()  // 更新所有搜索引擎图标

        // 设置中间窗口搜索引擎
        val centerEngine = settingsManager.getCenterWindowSearchEngine()
        updateWebViewForEngine(R.id.second_floating_webview, centerEngine)

        // 设置右侧窗口搜索引擎
        val rightEngine = settingsManager.getRightWindowSearchEngine()
        updateWebViewForEngine(R.id.third_floating_webview, rightEngine)
    }

    private fun updateWindowLayout() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 根据屏幕方向调整窗口大小
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val windowCount = settingsManager.getDefaultWindowCount()

        if (isLandscape && windowCount >= 2) {
            // 横屏模式下，每个窗口宽度为屏幕宽度的一半（减去分割线宽度）
            val windowWidth = (screenWidth - 4) / 2
            floatingView?.findViewById<LinearLayout>(R.id.first_title_bar)?.layoutParams?.width = windowWidth
            floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.layoutParams?.width = windowWidth
            if (windowCount == 3) {
                floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.layoutParams?.width = windowWidth
            }
        } else {
            // 竖屏模式下，使用固定宽度
            floatingView?.findViewById<LinearLayout>(R.id.first_title_bar)?.layoutParams?.width = 300.dp()
            floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.layoutParams?.width = 300.dp()
            floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.layoutParams?.width = 300.dp()
        }

        // 更新窗口位置
        val params = floatingView?.layoutParams as WindowManager.LayoutParams
        params.x = 0
        params.y = 0
        windowManager.updateViewLayout(floatingView, params)
    }

    private fun setupDragAndResize() {
        // 设置拖拽
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
                    initialY = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = true
                    true
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
                    true
                }
                else -> false
            }
        }

        // 设置调整大小
        floatingView?.findViewById<View>(R.id.dual_resize_handle)?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.width ?: 0
                    initialHeight = (floatingView?.layoutParams as? WindowManager.LayoutParams)?.height ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isResizing = true
                    true
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
                    true
                }
                else -> false
            }
        }
    }

    private fun updateWebViewForEngine(webViewId: Int, engineKey: String) {
        val webView = floatingView?.findViewById<WebView>(webViewId)
        val titleView = floatingView?.findViewById<TextView>(R.id.first_floating_title)
        val containerId = when (webViewId) {
            R.id.first_floating_webview -> R.id.first_engine_container
            R.id.second_floating_webview -> R.id.second_engine_container
            R.id.third_floating_webview -> R.id.third_engine_container
            else -> R.id.first_engine_container
        }
        
        try {
            if (webView != null && titleView != null) {
                val currentQuery = searchInput?.text?.toString()?.trim()
                
                if (!currentQuery.isNullOrEmpty()) {
                    val encodedQuery = URLEncoder.encode(currentQuery, "UTF-8")
                    val newUrl = getSearchEngineSearchUrl(engineKey, encodedQuery)
                    webView.loadUrl(newUrl)
                    titleView.text = "${getSearchEngineName(engineKey)}: $currentQuery"
                } else {
                    val homeUrl = getSearchEngineHomeUrl(engineKey)
                    webView.loadUrl(homeUrl)
                    titleView.text = getSearchEngineName(engineKey)
                    
                    // 加载并设置favicon
                    loadFaviconForEngine(webView, engineKey, containerId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换搜索引擎失败", e)
            webView?.loadUrl(getSearchEngineHomeUrl(engineKey))
            titleView?.text = getSearchEngineName(engineKey)
        }
    }

    private fun loadFaviconForEngine(webView: WebView?, engineKey: String, containerId: Int) {
        webView?.let { view ->
            val homeUrl = getSearchEngineHomeUrl(engineKey)
            faviconManager.loadFavicon(homeUrl, object : FaviconManager.FaviconCallback {
                override fun onFaviconLoaded(bitmap: Bitmap?) {
                    if (bitmap != null) {
                        // 只更新指定容器的图标
                        updateEngineIcon(containerId, bitmap)
                    }
                }
            })
        }
    }

    private fun updateEngineIcon(containerId: Int, bitmap: Bitmap) {
        val container = floatingView?.findViewById<LinearLayout>(containerId)
        container?.let { parent ->
            for (i in 0 until parent.childCount) {
                val view = parent.getChildAt(i)
                if (view is ImageButton) {
                    view.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun updateEngineIconStates(container: LinearLayout?, selectedEngineKey: String) {
        container?.let { parent ->
            for (i in 0 until parent.childCount) {
                val view = parent.getChildAt(i)
                if (view is ImageButton) {
                    // 根据容器确定当前窗口的搜索引擎
                    val currentEngineKey = when (container) {
                        firstEngineContainer -> leftEngineKey
                        secondEngineContainer -> centerEngineKey
                        thirdEngineContainer -> rightEngineKey
                        else -> selectedEngineKey
                    }
                    
                    // 设置选中状态的视觉效果
                    if (currentEngineKey == selectedEngineKey) {
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

    private fun performDualSearch() {
        val query = searchInput?.text?.toString()?.trim() ?: ""
        if (query.isNotEmpty()) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                
                // 使用左侧搜索引擎
                val leftUrl = getSearchEngineSearchUrl(leftEngineKey, encodedQuery)
                firstWebView?.loadUrl(leftUrl)
                
                // 使用中间搜索引擎
                val centerUrl = getSearchEngineSearchUrl(centerEngineKey, encodedQuery)
                secondWebView?.loadUrl(centerUrl)
                
                // 使用右侧搜索引擎
                val rightUrl = getSearchEngineSearchUrl(rightEngineKey, encodedQuery)
                thirdWebView?.loadUrl(rightUrl)
                
                // 更新标题
                firstTitle?.text = "${getSearchEngineName(leftEngineKey)}: $query"
                secondTitle?.text = "${getSearchEngineName(centerEngineKey)}: $query"
                thirdTitle?.text = "${getSearchEngineName(rightEngineKey)}: $query"
                
                // 关闭键盘
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.hideSoftInputFromWindow(searchInput?.windowToken, 0)
            } catch (e: Exception) {
                Log.e(TAG, "执行搜索失败", e)
                Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 根据搜索引擎和查询构建搜索URL
    private fun getSearchEngineSearchUrl(engineKey: String, query: String): String {
        return when(engineKey) {
            "baidu" -> "https://www.baidu.com/s?wd=$query"
            "google" -> "https://www.google.com/search?q=$query"
            "bing" -> "https://www.bing.com/search?q=$query"
            "sogou" -> "https://www.sogou.com/web?query=$query"
            "360" -> "https://www.so.com/s?q=$query"
            "quark" -> "https://quark.sm.cn/s?q=$query"
            "toutiao" -> "https://so.toutiao.com/search?keyword=$query"
            "zhihu" -> "https://www.zhihu.com/search?type=content&q=$query"
            "bilibili" -> "https://search.bilibili.com/all?keyword=$query"
            else -> "https://www.baidu.com/s?wd=$query"
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
        
        floatingView?.findViewById<LinearLayout>(R.id.dual_webview_container)?.orientation = orientationValue
        
        if (isHorizontalLayout) {
            // 设置窗口宽度为屏幕宽度
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = (screenHeight * DEFAULT_HEIGHT_RATIO).toInt()
            
            // 更新分割线
            floatingView?.findViewById<View>(R.id.divider1)?.layoutParams = LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(1, 0, 1, 0)
            }
            floatingView?.findViewById<View>(R.id.divider2)?.layoutParams = LinearLayout.LayoutParams(2, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(1, 0, 1, 0)
            }
            
            // 更新容器和WebView宽度
            val webViewWidth = if (isLandscape) {
                // 横屏时，每个WebView宽度为屏幕宽度的一半减去分割线宽度
                (screenWidth / 2) - 2
            } else {
                // 竖屏时，使用固定宽度
                300.dp()
            }
            
            // 设置每个WebView容器的宽度
            floatingView?.findViewById<LinearLayout>(R.id.first_title_bar)?.layoutParams?.width = webViewWidth
            floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.layoutParams?.width = webViewWidth
            floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.layoutParams?.width = webViewWidth
            
            // 设置容器宽度为wrap_content以启用滚动
            floatingView?.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            // 垂直布局
            windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams?.height = WindowManager.LayoutParams.MATCH_PARENT
            
            // 垂直分割线
            floatingView?.findViewById<View>(R.id.divider1)?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 1, 0, 1)
            }
            floatingView?.findViewById<View>(R.id.divider2)?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 1, 0, 1)
            }
            
            // 更新容器布局为垂直方向
            floatingView?.findViewById<LinearLayout>(R.id.first_title_bar)?.layoutParams?.width = ViewGroup.LayoutParams.MATCH_PARENT
            floatingView?.findViewById<LinearLayout>(R.id.second_title_bar)?.layoutParams?.width = ViewGroup.LayoutParams.MATCH_PARENT
            floatingView?.findViewById<LinearLayout>(R.id.third_title_bar)?.layoutParams?.width = ViewGroup.LayoutParams.MATCH_PARENT
            
            // 设置容器为match_parent
            floatingView?.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // 更新窗口布局
        try {
            windowManager.updateViewLayout(floatingView, windowParams)
        } catch (e: Exception) {
            Log.e(TAG, "更新窗口布局失败", e)
        }
        
        // 请求布局更新
        floatingView?.findViewById<View>(R.id.divider1)?.requestLayout()
        floatingView?.findViewById<View>(R.id.divider2)?.requestLayout()
        floatingView?.findViewById<LinearLayout>(R.id.dual_webview_container)?.requestLayout()
        
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
        
        // 为每个搜索引擎创建图标
        SearchEngine.DEFAULT_ENGINES.forEach { engine ->
            // 创建左侧搜索引擎图标
            createEngineIcon(engine, firstEngineContainer, true)
            // 创建中间搜索引擎图标
            createEngineIcon(engine, secondEngineContainer, false)
            // 创建右侧搜索引擎图标
            createEngineIcon(engine, thirdEngineContainer, false)
        }
    }

    private fun createEngineIcon(engine: SearchEngine, container: LinearLayout?, isLeft: Boolean) {
        val context = container?.context ?: return
        val imageButton = ImageButton(context).apply {
            // 使用搜索引擎的图标资源
            setImageResource(engine.iconResId)
            background = null
            setPadding(8, 8, 8, 8)
            
            setOnClickListener {
                val engineKey = getEngineKey(engine.name)
                
                // 根据容器确定是哪个窗口
                when (container) {
                    firstEngineContainer -> {
                        leftEngineKey = engineKey
                        settingsManager.setLeftWindowSearchEngine(engineKey)
                        updateWebViewForEngine(R.id.first_floating_webview, engineKey)
                    }
                    secondEngineContainer -> {
                        centerEngineKey = engineKey
                        settingsManager.setCenterWindowSearchEngine(engineKey)
                        updateWebViewForEngine(R.id.second_floating_webview, engineKey)
                    }
                    thirdEngineContainer -> {
                        rightEngineKey = engineKey
                        settingsManager.setRightWindowSearchEngine(engineKey)
                        updateWebViewForEngine(R.id.third_floating_webview, engineKey)
                    }
                }
                
                // 更新所有图标的状态
                updateEngineIconStates(container, engineKey)
            }
        }
        container.addView(imageButton)
    }

    private fun getEngineKey(engineName: String): String {
        return when(engineName) {
            "百度" -> "baidu"
            "Google" -> "google"
            "必应" -> "bing"
            "搜狗" -> "sogou"
            "360搜索" -> "360"
            "夸克搜索" -> "quark"
            "头条搜索" -> "toutiao"
            "知乎" -> "zhihu"
            "哔哩哔哩" -> "bilibili"
            else -> engineName.toLowerCase()
        }
    }

    private fun Int.dp(): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun getSearchEngineName(engineKey: String): String {
        return when(engineKey) {
            "baidu" -> "百度"
            "google" -> "Google"
            "bing" -> "必应"
            "sogou" -> "搜狗"
            "360" -> "360搜索"
            "quark" -> "夸克搜索"
            "toutiao" -> "头条搜索"
            "zhihu" -> "知乎"
            "bilibili" -> "哔哩哔哩"
            else -> engineKey
        }
    }

    private fun getSearchEngineHomeUrl(engineKey: String): String {
        return when(engineKey) {
            "baidu" -> "https://www.baidu.com"
            "google" -> "https://www.google.com"
            "bing" -> "https://www.bing.com"
            "sogou" -> "https://www.sogou.com"
            "360" -> "https://www.so.com"
            "quark" -> "https://quark.sm.cn"
            "toutiao" -> "https://so.toutiao.com"
            "zhihu" -> "https://www.zhihu.com"
            "bilibili" -> "https://www.bilibili.com"
            else -> "https://www.baidu.com"
        }
    }

    private fun setupControls() {
        // 设置布局切换按钮
        toggleLayoutButton?.setOnClickListener {
            isHorizontalLayout = !isHorizontalLayout
            updateLayoutOrientation()
            saveWindowState()
        }

        // 设置搜索按钮
        dualSearchButton?.setOnClickListener {
            performDualSearch()
        }

        // 设置关闭按钮
        closeButton?.setOnClickListener {
            stopSelf()
        }

        // 设置单窗口按钮
        singleWindowButton?.setOnClickListener {
            switchToSingleWindowMode()
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