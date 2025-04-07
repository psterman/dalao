package com.example.aifloatingball

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.PixelFormat
import android.net.Uri
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
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
    private var firstEngineSelector: ImageButton? = null
    private var secondEngineSelector: ImageButton? = null
    private var searchEnginePopupWindow: PopupWindow? = null
    private var currentEngineKey: String = "baidu"
    private var currentWebView: WebView? = null
    private var currentTitle: TextView? = null
    
    private lateinit var settingsManager: SettingsManager
    private var leftEngineKey: String = "baidu"
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            
            // 初始化设置管理器
            settingsManager = SettingsManager.getInstance(this)
            
            // 获取用户设置的搜索引擎
            leftEngineKey = settingsManager.getLeftWindowSearchEngine()
            rightEngineKey = settingsManager.getRightWindowSearchEngine()
            
            // 创建浮动窗口
            createFloatingView()
            
            // 加载上次的窗口状态
            loadWindowState()
            
            // 初始化WebView
            setupWebViews()
            
            // 设置控件事件
            setupControls()
            
            // 初始化搜索引擎选择器
            setupEngineSelectors()
            
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
        firstEngineSelector = floatingView?.findViewById(R.id.first_engine_selector)
        secondEngineSelector = floatingView?.findViewById(R.id.second_engine_selector)
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
            }
            
            webViewClient = WebViewClient()
            
            // 根据设置的搜索引擎加载首页
            val leftHomeUrl = getSearchEngineHomeUrl(leftEngineKey)
            loadUrl(leftHomeUrl)
            
            // 设置标题
            firstTitle?.text = getSearchEngineName(leftEngineKey)
        }
        
        // 设置第二个WebView（右侧窗口）
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
            
            // 根据设置的搜索引擎加载首页
            val rightHomeUrl = getSearchEngineHomeUrl(rightEngineKey)
            loadUrl(rightHomeUrl)
            
            // 设置标题
            secondTitle?.text = getSearchEngineName(rightEngineKey)
        }
    }

    // 获取搜索引擎首页URL
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
    
    // 获取搜索引擎名称
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
            else -> "搜索"
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
                // 对查询进行URL编码
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                
                // 使用左侧搜索引擎
                val leftUrl = getSearchEngineSearchUrl(leftEngineKey, encodedQuery)
                firstWebView?.loadUrl(leftUrl)
                
                // 使用右侧搜索引擎
                val rightUrl = getSearchEngineSearchUrl(rightEngineKey, encodedQuery)
                secondWebView?.loadUrl(rightUrl)
                
                // 更新标题
                firstTitle?.text = "${getSearchEngineName(leftEngineKey)}: $query"
                secondTitle?.text = "${getSearchEngineName(rightEngineKey)}: $query"
                
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
        // 首先保存要返回的方向值
        val orientationValue = if (isHorizontalLayout) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        
        // 设置容器方向
        container?.orientation = orientationValue
        
        // 更新分割线和窗口权重
        val params = container?.layoutParams as? LinearLayout.LayoutParams
        if (isHorizontalLayout) {
            // 水平分割线
            divider?.layoutParams = LinearLayout.LayoutParams(4, ViewGroup.LayoutParams.MATCH_PARENT)
            
            // 更新左右窗口权重
            container?.getChildAt(0)?.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            container?.getChildAt(2)?.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        } else {
            // 垂直分割线
            divider?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4)
            
            // 更新上下窗口权重
            container?.getChildAt(0)?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            container?.getChildAt(2)?.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        
        // 应用布局参数
        divider?.requestLayout()
        container?.requestLayout()
        
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

    private fun setupEngineSelectors() {
        firstEngineSelector?.setOnClickListener {
            showEngineSelector(firstWebView, firstTitle, leftEngineKey) { engine ->
                leftEngineKey = engine.name.toLowerCase()
                settingsManager.setLeftWindowSearchEngine(leftEngineKey)
                firstTitle?.text = engine.name
                loadCurrentPage(firstWebView, leftEngineKey)
            }
        }

        secondEngineSelector?.setOnClickListener {
            showEngineSelector(secondWebView, secondTitle, rightEngineKey) { engine ->
                rightEngineKey = engine.name.toLowerCase()
                settingsManager.setRightWindowSearchEngine(rightEngineKey)
                secondTitle?.text = engine.name
                loadCurrentPage(secondWebView, rightEngineKey)
            }
        }
    }

    private fun showEngineSelector(webView: WebView?, titleView: TextView?, currentEngine: String, onSelected: (SearchEngine) -> Unit) {
        currentWebView = webView
        currentTitle = titleView
        currentEngineKey = currentEngine

        // 创建搜索引擎列表视图
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DualFloatingWebViewService)
            adapter = SearchEngineAdapter(
                engines = SearchEngine.DEFAULT_ENGINES,
                onEngineSelected = { engine -> 
                    onSelected(engine)
                    searchEnginePopupWindow?.dismiss()
                }
            )
            setBackgroundColor(Color.WHITE)
        }

        // 创建 PopupWindow
        searchEnginePopupWindow = PopupWindow(recyclerView).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isFocusable = true
            elevation = 10f
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            
            // 设置动画
            animationStyle = android.R.style.Animation_Dialog
        }

        // 显示 PopupWindow
        titleView?.let { title ->
            searchEnginePopupWindow?.showAsDropDown(title)
        }
    }

    private fun loadCurrentPage(webView: WebView?, engineKey: String) {
        webView?.url?.let { currentUrl ->
            // 如果当前页面是搜索结果页面，则使用新的搜索引擎重新搜索
            val query = extractSearchQuery(currentUrl, engineKey)
            if (query != null) {
                val newUrl = getSearchEngineSearchUrl(engineKey, query)
                webView.loadUrl(newUrl)
            } else {
                // 如果不是搜索结果页面，则加载搜索引擎主页
                webView.loadUrl(getSearchEngineHomeUrl(engineKey))
            }
        }
    }

    private fun extractSearchQuery(url: String, engineKey: String): String? {
        return try {
            when (engineKey) {
                "baidu" -> {
                    val uri = Uri.parse(url)
                    uri.getQueryParameter("wd")
                }
                "google" -> {
                    val uri = Uri.parse(url)
                    uri.getQueryParameter("q")
                }
                "bing" -> {
                    val uri = Uri.parse(url)
                    uri.getQueryParameter("q")
                }
                "sogou" -> {
                    val uri = Uri.parse(url)
                    uri.getQueryParameter("query")
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取搜索关键词失败", e)
            null
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