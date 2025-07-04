package com.example.aifloatingball.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.model.AppSearchSettings
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.service.DualFloatingWebViewService
import java.net.URLEncoder

class SimpleModeService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var simpleModeView: View
    private lateinit var minimizedView: View
    private var popupWebView: View? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var minimizedParams: WindowManager.LayoutParams
    private var isWindowVisible = false
    private var isMinimized = false

    // 拖动相关变量
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragThreshold = 10f

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY" -> {
                    val query = intent.getStringExtra("search_query")
                    Log.d("SimpleModeService", "收到'搜索并销毁'广播, 查询: '$query'")

                    if (!query.isNullOrEmpty()) {
                        // 1. 切换模式，防止服务被自动重启
                        settingsManager.setDisplayMode("floating_ball")
                        Log.d("SimpleModeService", "显示模式已临时切换到 aifloatingball")

                        // 2. 启动搜索服务
                        val serviceIntent = Intent(context, DualFloatingWebViewService::class.java).apply {
                            putExtras(intent.extras ?: Bundle())
                        }
                        context?.startService(serviceIntent)
                        Log.d("SimpleModeService", "已启动 DualFloatingWebViewService")

                        // 3. 彻底停止自己
                        stopSelf()
                        Log.d("SimpleModeService", "已调用 stopSelf()")
                    }
                }
            }
        }
    }
    
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                Log.d("SimpleModeService", "Screen turned off, stopping service")
                stopSelf()
                }
                "com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE" -> {
                    Log.d("SimpleModeService", "Received close broadcast, stopping service immediately")
                    stopSelf()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("SimpleModeService", "Service created")
        
        settingsManager = SettingsManager.getInstance(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 注册命令广播接收器
        val commandFilter = IntentFilter().apply {
            addAction("com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, commandFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, commandFilter)
        }
        
        // 注册屏幕关闭监听器
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
        
        createSimpleModeWindow()
        createMinimizedWindow()
    }
    
    private fun createSimpleModeWindow() {
        val inflater = LayoutInflater.from(this)
        try {
        simpleModeView = inflater.inflate(R.layout.simple_mode_layout, null)
        } catch (e: Exception) {
            Log.e("SimpleModeService", "Error inflating simple_mode_layout", e)
            Toast.makeText(this, "加载简易模式布局失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        
        windowParams.gravity = Gravity.CENTER
        
        showWindow()
        setupViews()
    }

    private fun createMinimizedWindow() {
        val inflater = LayoutInflater.from(this)
        minimizedView = inflater.inflate(R.layout.simple_mode_minimized, null)

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        minimizedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        minimizedParams.gravity = Gravity.TOP or Gravity.START
        minimizedParams.x = screenWidth - 30
        minimizedParams.y = screenHeight / 2 - 30

        setupMinimizedView()
    }

    private fun setupMinimizedView() {
        val minimizedLayout = minimizedView.findViewById<LinearLayout>(R.id.minimized_layout)

        minimizedLayout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = minimizedParams.x
                    initialY = minimizedParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        minimizedParams.x = (initialX + deltaX).toInt()
                        minimizedParams.y = (initialY + deltaY).toInt()

                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels

                        minimizedParams.x = minimizedParams.x.coerceIn(-20, screenWidth - 20)
                        minimizedParams.y = minimizedParams.y.coerceIn(0, screenHeight - view.height)

                        try {
                            windowManager.updateViewLayout(minimizedView, minimizedParams)
                        } catch (e: Exception) {
                            Log.e("SimpleModeService", "更新最小化视图位置失败", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        restoreFromMinimized()
                    } else {
                        snapToEdge()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val centerX = screenWidth / 2

        val targetX = if (minimizedParams.x < centerX) -20 else screenWidth - 20
        minimizedParams.x = targetX

        try {
            windowManager.updateViewLayout(minimizedView, minimizedParams)
        } catch (e: Exception) {
            Log.e("SimpleModeService", "贴边动画失败", e)
        }
    }

    private fun minimizeToEdge() {
        if (isMinimized) return
        try {
            if (isWindowVisible) {
                windowManager.removeView(simpleModeView)
                isWindowVisible = false
            }
            if (!minimizedView.isAttachedToWindow) {
                windowManager.addView(minimizedView, minimizedParams)
            }
            isMinimized = true
            Log.d("SimpleModeService", "简易模式已最小化到边缘")
        } catch (e: Exception) {
            Log.e("SimpleModeService", "最小化失败", e)
        }
    }

    private fun restoreFromMinimized() {
        if (!isMinimized) return
        try {
            if (minimizedView.isAttachedToWindow) {
                windowManager.removeView(minimizedView)
            }
            isMinimized = false
            showWindow()
            Log.d("SimpleModeService", "简易模式已从最小化状态恢复")
        } catch (e: Exception) {
            Log.e("SimpleModeService", "恢复最小化失败", e)
        }
    }

    private fun showMinimizeHintIfNeeded() {
        val prefs = getSharedPreferences("simple_mode_prefs", Context.MODE_PRIVATE)
        val hasShownHint = prefs.getBoolean("minimize_hint_shown", false)
        if (!hasShownHint) {
            simpleModeView.postDelayed({
                Toast.makeText(this, "💡 提示：点击右上角 ➖ 可以最小化到边缘", Toast.LENGTH_LONG).show()
                prefs.edit().putBoolean("minimize_hint_shown", true).apply()
                simpleModeView.postDelayed({ hideMinimizeHint() }, 10000)
            }, 3000)
        } else {
            hideMinimizeHint()
        }
    }

    private fun hideMinimizeHint() {
        try {
            val hintDot = simpleModeView.findViewById<View>(R.id.minimize_hint_dot)
            hintDot?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("SimpleModeService", "隐藏提示红点失败", e)
        }
    }
    
    private fun setupViews() {
        val searchEditText = simpleModeView.findViewById<EditText>(R.id.searchEditText)
        val searchButton = simpleModeView.findViewById<ImageButton>(R.id.searchButton)
        val minimizeButton = simpleModeView.findViewById<ImageButton>(R.id.simple_mode_minimize_button)
        val closeButton = simpleModeView.findViewById<ImageButton>(R.id.simple_mode_close_button)

        val gridItem1 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_1)
        val gridItem2 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_2)
        val gridItem3 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_3)
        val gridItem4 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_4)
        val gridItem5 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_5)
        val gridItem6 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_6)
        val gridItem7 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_7)
        val gridItem8 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_8)
        val gridItem9 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_9)
        val gridItem10 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_10)
        val gridItem11 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_11)
        val gridItem12 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_12)

        val tabHome = simpleModeView.findViewById<LinearLayout>(R.id.tab_home)
        val tabSearch = simpleModeView.findViewById<LinearLayout>(R.id.tab_search)
        val tabVoice = simpleModeView.findViewById<LinearLayout>(R.id.tab_voice)
        val tabProfile = simpleModeView.findViewById<LinearLayout>(R.id.tab_profile)
        
        minimizeButton.setOnClickListener {
            Log.d("SimpleModeService", "最小化按钮点击")
            minimizeToEdge()
            hideMinimizeHint()
        }

        closeButton?.setOnClickListener {
            Log.d("SimpleModeService", "关闭按钮点击")
            stopSelf()
        }

        showMinimizeHintIfNeeded()

        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query, true)
            } else {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val query = searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query, true)
                    hideKeyboard(searchEditText)
                    return@setOnEditorActionListener true
                }
            }
            false
        }
        
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            updateWindowFocusability(hasFocus)
        }
        
        // Setup App Search Icons
        setupAppSearchIcons()

        // 设置AI引擎宫格
        setupAIEngineGridItems(searchEditText)
        gridItem12.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        
        tabHome.setOnClickListener { }
        tabSearch.setOnClickListener {
            searchEditText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        tabVoice.setOnClickListener {
            Log.d("SimpleModeService", "语音Tab点击，隐藏窗口并启动语音识别")
            hideWindow()
            val intent = Intent(this, VoiceRecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        tabProfile.setOnClickListener { Toast.makeText(this, "个人中心开发中", Toast.LENGTH_SHORT).show() }

        simpleModeView.isFocusableInTouchMode = true
        simpleModeView.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                stopSelf()
                return@setOnKeyListener true
            }
            false
        }
    }
    
    private fun setupAppSearchIcons() {
        val container = simpleModeView.findViewById<LinearLayout>(R.id.app_search_icons_container)
        container.removeAllViews()

        val appSearchSettings = AppSearchSettings.getInstance(this)
        
        // 确保抖音被启用 - 临时解决方案
        appSearchSettings.toggleAppEnabled("douyin", true)
        
        val allApps = appSearchSettings.getAppConfigs()
        Log.d("SimpleModeService", "所有应用配置数量: ${allApps.size}")
        
        val enabledApps = allApps.filter { it.isEnabled }.sortedBy { it.order }
        Log.d("SimpleModeService", "已启用应用数量: ${enabledApps.size}")
        
        enabledApps.forEach { app ->
            Log.d("SimpleModeService", "已启用应用: ${app.appName} (${app.packageName}) - 启用状态: ${app.isEnabled}")
        }

        val iconSize = (40 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

        for (appConfig in enabledApps) {
            val imageButton = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).also {
                    it.setMargins(margin, 0, margin, 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = null // Or use a transparent background
                contentDescription = appConfig.appName
                
                try {
                    val icon = packageManager.getApplicationIcon(appConfig.packageName)
                    setImageDrawable(icon)
                } catch (e: PackageManager.NameNotFoundException) {
                    setImageResource(appConfig.iconResId) // Fallback icon
                }
                
                setOnClickListener {
                    val searchEditText = simpleModeView.findViewById<EditText>(R.id.searchEditText)
                    val query = searchEditText.text.toString().trim()
                    if (query.isNotEmpty()) {
                        try {
                            // Special handling for Douyin
                            if (appConfig.packageName == "com.ss.android.ugc.aweme") {
                                Log.d("SimpleModeService", "点击抖音图标，查询词: $query")
                                showPopupWebView(query)
                            } else {
                                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                                val searchUri = appConfig.searchUrl.replace("{q}", encodedQuery)
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUri)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    setPackage(appConfig.packageName)
                                }
                                startActivity(intent)
                                minimizeToEdge()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@SimpleModeService, "操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e("SimpleModeService", "应用图标点击处理失败 - ${appConfig.appName}", e)
                        }
                    } else {
                        Toast.makeText(this@SimpleModeService, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            container.addView(imageButton)
        }
        
        // 如果没有应用图标，显示提示
        if (enabledApps.isEmpty()) {
            val textView = android.widget.TextView(this).apply {
                text = "请在设置中启用应用搜索"
                textSize = 12f
                setPadding(16, 8, 16, 8)
            }
            container.addView(textView)
            Log.d("SimpleModeService", "没有启用的应用，显示提示文本")
        }
    }

    private fun showPopupWebView(query: String) {
        Log.d("SimpleModeService", "开始显示抖音搜索弹窗，查询词: $query")
        
        if (popupWebView != null) {
            Log.d("SimpleModeService", "弹窗已存在，忽略请求")
            return
        }

        try {
            val inflater = LayoutInflater.from(this)
            val popupView = inflater.inflate(R.layout.popup_webview_layout, null)
            this.popupWebView = popupView
            Log.d("SimpleModeService", "弹窗布局加载成功")

            val webView = popupView.findViewById<WebView>(R.id.popup_webview)
            val closeButton = popupView.findViewById<ImageButton>(R.id.popup_close_button)

            // 配置WebView
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("SimpleModeService", "开始加载页面: $url")
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("SimpleModeService", "页面加载完成: $url")
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.e("SimpleModeService", "WebView加载错误: $errorCode - $description")
                }
            }

            // 构造搜索URL
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.douyin.com/search/$encodedQuery"
            Log.d("SimpleModeService", "准备加载URL: $url")
            webView.loadUrl(url)

            // 设置关闭按钮
            closeButton.setOnClickListener {
                Log.d("SimpleModeService", "用户点击关闭按钮")
                hidePopupWebView()
            }

            // 配置窗口参数
            val popupParams = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                (resources.displayMetrics.heightPixels * 0.75).toInt(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            popupParams.gravity = Gravity.CENTER

            // 添加到窗口管理器
            windowManager.addView(popupView, popupParams)
            Log.d("SimpleModeService", "抖音搜索弹窗显示成功")
            
        } catch (e: Exception) {
            Log.e("SimpleModeService", "显示抖音搜索弹窗失败", e)
            Toast.makeText(this, "显示搜索窗口失败: ${e.message}", Toast.LENGTH_LONG).show()
            
            // 清理
            popupWebView = null
        }
    }

    private fun hidePopupWebView() {
        popupWebView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            popupWebView = null
        }
    }
    
    private fun updateWindowFocusability(needsFocus: Boolean) {
        try {
            if (simpleModeView.parent == null) return
            val params = simpleModeView.layoutParams as WindowManager.LayoutParams
            if (needsFocus) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager.updateViewLayout(simpleModeView, params)
        } catch (e: Exception) {
            Log.e("SimpleModeService", "Error updating window focusability", e)
        }
    }
    
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    private fun performSearch(query: String, shouldMinimize: Boolean) {
        // 默认使用Google搜索
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        openUrlInBrowserAndMinimize(url)
    }

    private fun openUrlInBrowserAndMinimize(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            minimizeToEdge()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWindow() {
        if (!isWindowVisible && simpleModeView.parent == null) {
            windowManager.addView(simpleModeView, windowParams)
            isWindowVisible = true
        }
    }

    /**
     * 设置AI引擎宫格项
     */
    private fun setupAIEngineGridItems(searchEditText: EditText) {
        // 获取前11个AI引擎（第12个保留给设置）
        val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES.take(11)
        
        for (i in 1..11) {
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_$i", "id", packageName)
            )
            
            if (i <= aiEngines.size) {
                val aiEngine = aiEngines[i - 1]
                setupAIGridItem(gridItem, aiEngine, searchEditText, i)
        } else {
                // 如果AI引擎不够，设置为占位符
                setupPlaceholderGridItem(gridItem, i)
            }
        }
    }
    
    /**
     * 设置单个AI引擎宫格项
     */
    private fun setupAIGridItem(gridItem: LinearLayout, aiEngine: AISearchEngine, searchEditText: EditText, index: Int) {
        // 更新标题
        val titleTextView = gridItem.getChildAt(1) as? android.widget.TextView
        titleTextView?.text = aiEngine.displayName
        
        // 更新图标
        val iconTextView = gridItem.getChildAt(0) as? android.widget.TextView
        iconTextView?.text = getAIEngineIcon(aiEngine.name)
        
        // 设置点击事件
        gridItem.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            
            if (query.isNotEmpty()) {
                launchAIEngine(aiEngine, query)
            } else {
                Toast.makeText(this, "请先输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 设置占位符宫格项
     */
    private fun setupPlaceholderGridItem(gridItem: LinearLayout, index: Int) {
        val titleTextView = gridItem.getChildAt(1) as? android.widget.TextView
        titleTextView?.text = "功能${index}"
        
        val iconTextView = gridItem.getChildAt(0) as? android.widget.TextView
        iconTextView?.text = "🔧"
        
        gridItem.setOnClickListener {
            Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动AI引擎
     */
    private fun launchAIEngine(aiEngine: AISearchEngine, searchText: String) {
        Log.d("SimpleModeService", "启动AI引擎: ${aiEngine.displayName}, 搜索文本: $searchText")
        Log.d("SimpleModeService", "AI引擎详情: name=${aiEngine.name}, isChatMode=${aiEngine.isChatMode}, searchUrl=${aiEngine.searchUrl}")
        
        when {
            // API模式的AI引擎
            aiEngine.isChatMode -> {
                Log.d("SimpleModeService", "选择API聊天模式")
                launchAPIChatMode(aiEngine, searchText)
            }
            // 支持URL查询参数的AI引擎
            aiEngine.searchUrl.contains("{query}") -> {
                Log.d("SimpleModeService", "选择Web AI（带查询）模式")
                launchWebAIWithQuery(aiEngine, searchText)
            }
            // 不支持URL查询参数的AI引擎（需要工具栏）
            else -> {
                Log.d("SimpleModeService", "选择Web AI（工具栏）模式")
                launchWebAIWithToolbar(aiEngine, searchText)
            }
        }
    }
    
    /**
     * 启动API聊天模式
     */
    private fun launchAPIChatMode(aiEngine: AISearchEngine, searchText: String) {
        Log.d("SimpleModeService", "启动API聊天模式: ${aiEngine.name}")
        
        try {
            // 1. 切换模式，防止服务被自动重启
            settingsManager.setDisplayMode("floating_ball")
            Log.d("SimpleModeService", "显示模式已临时切换到 floating_ball")

            // 2. 直接启动DualFloatingWebViewService
            val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", searchText)
                putExtra("search_engine", aiEngine.name)
                putExtra("search_source", "简易模式-API聊天")
                putExtra("startTime", System.currentTimeMillis())
            }
            startService(serviceIntent)
            Log.d("SimpleModeService", "已启动 DualFloatingWebViewService")

            // 3. 最小化简易模式而不是停止
            minimizeToEdge()
            Log.d("SimpleModeService", "简易模式已最小化")
            
        } catch (e: Exception) {
            Log.e("SimpleModeService", "启动API聊天模式失败", e)
            Toast.makeText(this, "启动AI聊天失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动支持查询参数的Web AI
     */
    private fun launchWebAIWithQuery(aiEngine: AISearchEngine, searchText: String) {
        Log.d("SimpleModeService", "启动Web AI（带查询）: ${aiEngine.name}")
        
        try {
            // 1. 切换模式，防止服务被自动重启
            settingsManager.setDisplayMode("floating_ball")
            Log.d("SimpleModeService", "显示模式已临时切换到 floating_ball")

            // 2. 直接启动DualFloatingWebViewService
            val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", searchText)
                putExtra("search_engine", aiEngine.name)
                putExtra("search_source", "简易模式-Web搜索")
                putExtra("startTime", System.currentTimeMillis())
            }
            startService(serviceIntent)
            Log.d("SimpleModeService", "已启动 DualFloatingWebViewService")

            // 3. 最小化简易模式而不是停止
            minimizeToEdge()
            Log.d("SimpleModeService", "简易模式已最小化")
            
        } catch (e: Exception) {
            Log.e("SimpleModeService", "启动Web AI搜索失败", e)
            Toast.makeText(this, "启动AI搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动需要工具栏的Web AI
     */
    private fun launchWebAIWithToolbar(aiEngine: AISearchEngine, searchText: String) {
        Log.d("SimpleModeService", "启动Web AI（需工具栏）: ${aiEngine.name}")
        
        try {
            // 1. 切换模式，防止服务被自动重启
            settingsManager.setDisplayMode("floating_ball")
            Log.d("SimpleModeService", "显示模式已临时切换到 floating_ball")

            // 2. 直接启动DualFloatingWebViewService
            val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", searchText)
                putExtra("search_engine", aiEngine.name)
                putExtra("search_source", "简易模式-工具栏模式")
                putExtra("startTime", System.currentTimeMillis())
                putExtra("show_toolbar", true) // 标记需要显示工具栏
            }
            startService(serviceIntent)
            Log.d("SimpleModeService", "已启动 DualFloatingWebViewService")

            // 3. 最小化简易模式而不是停止
            minimizeToEdge()
            Log.d("SimpleModeService", "简易模式已最小化")

            // 4. 延迟启动工具栏服务
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val toolbarIntent = Intent(this, Class.forName("com.example.aifloatingball.service.WebViewToolbarService")).apply {
                        action = "com.example.aifloatingball.SHOW_TOOLBAR"
                        putExtra("search_text", searchText)
                        putExtra("ai_engine", aiEngine.displayName)
                    }
                    startService(toolbarIntent)
                    Log.d("SimpleModeService", "已启动工具栏服务")
                } catch (e: Exception) {
                    Log.e("SimpleModeService", "启动工具栏服务失败", e)
                }
            }, 3000) // 3秒延迟
            
        } catch (e: Exception) {
            Log.e("SimpleModeService", "启动Web AI（工具栏）失败", e)
            Toast.makeText(this, "启动AI搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取AI引擎图标
     */
    private fun getAIEngineIcon(engineName: String): String {
        return when (engineName.lowercase()) {
            "chatgpt" -> "🤖"
            "claude" -> "🧠"
            "gemini" -> "✨"
            "文心一言" -> "🎯"
            "智谱清言" -> "💭"
            "通义千问" -> "🔮"
            "讯飞星火" -> "⚡"
            "perplexity" -> "🔍"
            "phind" -> "🔎"
            "poe" -> "🎭"
            "天工ai" -> "🛠️"
            "秘塔ai搜索" -> "🗂️"
            "夸克ai" -> "⚛️"
            "360ai搜索" -> "🌐"
            "百度ai" -> "🐻"
            "you.com" -> "🌟"
            "brave search" -> "🦁"
            "wolframalpha" -> "🧮"
            "kimi" -> "🌙"
            "deepseek (web)" -> "🌊"
            "万知" -> "📚"
            "百小应" -> "🐼"
            "跃问" -> "🚀"
            "豆包" -> "📦"
            "cici" -> "🎀"
            "海螺" -> "🐚"
            "groq" -> "💨"
            "腾讯元宝" -> "💎"
            "商量" -> "💼"
            "devv" -> "👨‍💻"
            "huggingchat" -> "🤗"
            "纳米ai搜索" -> "🔬"
            "thinkany" -> "🤔"
            "hika" -> "⭐"
            "genspark" -> "💫"
            "grok" -> "🎯"
            "flowith" -> "🌊"
            "notebooklm" -> "📝"
            "coze" -> "🤖"
            "dify" -> "⚙️"
            "wps灵感" -> "📄"
            "lechat" -> "💬"
            "monica" -> "👩‍💼"
            "知乎" -> "🎓"
            else -> "🤖"
        }
    }

    private fun hideWindow() {
        if (isWindowVisible && simpleModeView.parent != null) {
            windowManager.removeView(simpleModeView)
            isWindowVisible = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SimpleModeService", "Service started")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideWindow()
        hidePopupWebView()
        if (minimizedView.isAttachedToWindow) {
            windowManager.removeView(minimizedView)
        }
        unregisterReceiver(commandReceiver)
        unregisterReceiver(screenOffReceiver)
        Log.d("SimpleModeService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 