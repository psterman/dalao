package com.example.aifloatingball.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Vibrator
import android.text.TextUtils
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
import android.widget.TextView
import android.widget.Toast
import android.view.HapticFeedbackConstants
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.model.AppSearchSettings
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.service.DualFloatingWebViewService
import java.net.URLEncoder
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import android.widget.GridLayout

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

    // 添加缺失的属性
    private var currentLevel: Int = 1
    private var currentCategory: String = ""

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

        // 优化搜索框
        searchEditText.apply {
            textSize = 16f
            hint = "点击这里搜索，或选择下方分类"
            setHintTextColor(Color.parseColor("#8A8A8A"))
            background = ContextCompat.getDrawable(this@SimpleModeService, R.drawable.search_bar_background_simple)
            setPadding(48, 28, 48, 28)
            elevation = 2f
        }

        // 设置引导提示
        showWelcomeGuide()

        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query, true)
            } else {
                Toast.makeText(this, "请输入搜索内容或选择下方分类", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置宫格布局
        setupTemplateGrid()
        
        // 设置底部导航
        tabHome.setOnClickListener { 
            showMainTemplates() // 显示主模板
        }
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
        tabProfile.setOnClickListener { 
            Toast.makeText(this, "个人中心开发中", Toast.LENGTH_SHORT).show() 
        }

        setupBackButton()
    }

    private fun showWelcomeGuide() {
        val prefs = getSharedPreferences("simple_mode_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("guide_shown", false)) {
            Toast.makeText(
                this,
                getString(R.string.welcome_guide),
                Toast.LENGTH_LONG
            ).show()
            prefs.edit().putBoolean("guide_shown", true).apply()
        }
    }

    private fun setupTemplateGrid() {
        val templates = listOf(
            Triple("health", "健康养生", "👨‍⚕️"), // 更换为医生图标
            Triple("daily", "生活服务", "🏠"),
            Triple("entertainment", "休闲娱乐", "🎵"), // 更换为音乐图标，更活泼
            Triple("family", "家庭生活", "👨‍👩‍👧"),
            Triple("tech", "智能设备", "📱")
        )

        // 设置主要分类
        templates.forEachIndexed { index, (id, title, icon) ->
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_${index + 1}", "id", packageName)
            )
            setupGridItem(gridItem, title, icon) { 
                showSecondLevel(id)
                // 添加触感反馈
                gridItem.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }

        // 隐藏未使用的格子
        for (i in (templates.size + 1)..12) {
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_$i", "id", packageName)
            )
            gridItem.visibility = View.GONE
        }
    }

    private fun setupGridItem(gridItem: LinearLayout, title: String, icon: String, onClick: () -> Unit) {
        gridItem.apply {
            // 设置整体样式
            background = ContextCompat.getDrawable(context, R.drawable.grid_item_background)
            setPadding(16, 24, 16, 24)
        }

        // 设置图标
        val iconView = gridItem.getChildAt(0) as TextView
        iconView.apply {
            text = icon
            textSize = 32f // 更大的图标
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT
        }
        
        // 设置标题
        val titleView = gridItem.getChildAt(1) as TextView
        titleView.apply {
            text = title
            textSize = 18f // 更大的文字
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 12, 0, 0)
        }
        
        // 设置点击效果
        gridItem.setOnClickListener { 
            onClick()
            gridItem.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    gridItem.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    private fun showSecondLevel(categoryId: String) {
        // 显示返回提示
        showBackHint()
        
        val secondLevelTemplates = when(categoryId) {
            "health" -> listOf(
                Triple("health_issue", "不舒服找医生", "🏥"),
                Triple("daily_health", "日常保健", "💪"),
                Triple("wellness", "养生食疗", "🍵")
            )
            "daily" -> listOf(
                Triple("medical", "挂号就医", "👨‍⚕️"),
                Triple("convenience", "生活缴费", "💳"),
                Triple("government", "办事指南", "📋")
            )
            "entertainment" -> listOf(
                Triple("dance", "广场舞", "💃"),
                Triple("video", "看视频", "📺"),
                Triple("games", "棋牌游戏", "🎲")
            )
            "family" -> listOf(
                Triple("relationship", "亲子交流", "👨‍👧"),
                Triple("housework", "家务技巧", "🧹"),
                Triple("finance", "理财规划", "💰")
            )
            "tech" -> listOf(
                Triple("phone", "手机指南", "📱"),
                Triple("apps", "常用软件", "��"),
                Triple("troubleshoot", "问题解决", "🔧")
            )
            else -> emptyList()
        }

        // 更新宫格显示
        secondLevelTemplates.forEachIndexed { index, (id, title, icon) ->
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_${index + 1}", "id", packageName)
            )
            setupGridItem(gridItem, title, icon) { showThirdLevel(categoryId, id) }
            gridItem.visibility = View.VISIBLE
        }

        // 添加返回按钮
        val backGridItem = simpleModeView.findViewById<LinearLayout>(
            resources.getIdentifier("grid_item_${secondLevelTemplates.size + 1}", "id", packageName)
        )
        setupBackButton(backGridItem) { showMainTemplates() }

        // 隐藏其余格子
        for (i in (secondLevelTemplates.size + 2)..12) {
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_$i", "id", packageName)
            )
            gridItem.visibility = View.GONE
        }
    }

    private fun setupBackButton(gridItem: LinearLayout, onClick: () -> Unit) {
        gridItem.apply {
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(context, R.drawable.back_button_background)
            setPadding(16, 24, 16, 24)
        }

        // 设置返回图标
        val iconView = gridItem.getChildAt(0) as TextView
        iconView.apply {
            text = "⬅️"
            textSize = 28f
        }

        // 设置返回文字
        val titleView = gridItem.getChildAt(1) as TextView
        titleView.apply {
            text = "返回上一级"
            textSize = 16f
            setTextColor(Color.BLACK)
        }

        // 设置点击效果
        gridItem.setOnClickListener { 
            onClick()
            // 添加触感反馈
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                gridItem.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                gridItem.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    private fun showBackHint() {
        val prefs = getSharedPreferences("simple_mode_prefs", Context.MODE_PRIVATE)
        val hasShownBackHint = prefs.getBoolean("back_hint_shown", false)
        
        if (!hasShownBackHint) {
            Toast.makeText(this, 
                getString(R.string.navigation_tip, getString(R.string.back_to_previous)),
                Toast.LENGTH_SHORT).show()
            prefs.edit().putBoolean("back_hint_shown", true).apply()
        }
    }

    private fun showThirdLevel(categoryId: String, subCategoryId: String) {
        val thirdLevelTemplates = when("$categoryId:$subCategoryId") {
            "health:health_issue:headache" -> listOf(
                Triple("headache", "头痛头晕", "😵"),
                Triple("joint_pain", "关节疼痛", "🦴"),
                Triple("insomnia", "失眠多梦", "😴")
            )
            // ... 其他三级模板定义
            else -> emptyList()
        }

        // 更新宫格显示
        thirdLevelTemplates.forEachIndexed { index, (id, title, icon) ->
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_${index + 1}", "id", packageName)
            )
            setupGridItem(gridItem, title, icon) { 
                launchTemplateSearch(categoryId, subCategoryId, id)
            }
        }

        // 添加返回按钮
        val backGridItem = simpleModeView.findViewById<LinearLayout>(
            resources.getIdentifier("grid_item_${thirdLevelTemplates.size + 1}", "id", packageName)
        )
        setupGridItem(backGridItem, getString(R.string.back_button_text), "⬅️") { showSecondLevel(categoryId) }

        // 隐藏其余格子
        for (i in (thirdLevelTemplates.size + 2)..12) {
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_$i", "id", packageName)
            )
            gridItem.visibility = View.GONE
        }
    }

    private val mainTemplates = listOf(
        Triple("health", "健康养生", "❤️"),
        Triple("lifestyle", "生活服务", "🏠"),
        Triple("leisure", "休闲娱乐", "😊"),
        Triple("family", "家庭生活", "👨‍👩‍👧‍👦"),
        Triple("tech", "智能设备", "📱")
    )

    private fun showMainTemplates() {
        currentLevel = 1
        currentCategory = ""
        
        mainTemplates.forEachIndexed { index, template ->
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_${index + 1}", "id", packageName)
            )
            gridItem.visibility = View.VISIBLE
            setupGridItem(gridItem, template.second, template.third) {
                showSecondLevel(template.first)
            }
        }

        // 隐藏多余的格子
        for (i in (mainTemplates.size + 1)..12) {
            val gridItem = simpleModeView.findViewById<LinearLayout>(
                resources.getIdentifier("grid_item_$i", "id", packageName)
            )
            gridItem.visibility = View.GONE
        }
    }

    private fun launchTemplateSearch(categoryId: String, subCategoryId: String, itemId: String) {
        // 显示加载提示
        Toast.makeText(this, "正在为您准备搜索结果...", Toast.LENGTH_SHORT).show()
        
        // 根据模板ID生成搜索提示和选择合适的搜索引擎
        val (searchPrompt, engines) = getTemplateSearchConfig(categoryId, subCategoryId, itemId)
        
        // 启动搜索服务
        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            putExtra("search_query", searchPrompt)
            putExtra("search_engines", engines.toTypedArray())
            putExtra("search_source", "模板搜索")
            putExtra("show_toolbar", true)
        }
        startService(intent)
        minimizeToEdge()
    }

    private fun getTemplateSearchConfig(categoryId: String, subCategoryId: String, itemId: String): Pair<String, List<String>> {
        return when("$categoryId:$subCategoryId:$itemId") {
            "health:health_issue:headache" -> Pair(
                "头痛头晕的常见原因和缓解方法，以及需要就医的情况",
                listOf("deepseek", "douban", "zhihu")
            )
            // ... 其他模板配置
            else -> Pair("", emptyList())
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

    private fun showNavigationTip() {
        val backText = getString(R.string.back_to_previous)
        val tipText = getString(R.string.navigation_tip, backText)
        Toast.makeText(this, tipText, Toast.LENGTH_LONG).show()
    }

    private fun setupBackButton() {
        simpleModeView.findViewById<ImageButton>(R.id.back_button)?.apply {
            contentDescription = getString(R.string.back_to_previous)
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                handleBackPress()
            }
        }
    }

    private fun handleBackPress() {
        if (currentLevel > 1) {
            currentLevel--
            updateGridItems()
        } else {
            hideSimpleMode()
        }
    }

    private fun showBackTooltip() {
        Toast.makeText(
            this,
            getString(R.string.back_to_previous),
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun updateGridItems() {
        when (currentLevel) {
            1 -> showMainTemplates()
            2 -> {
                // 显示二级模板
                setupTemplateGrid()
            }
            3 -> {
                // 显示三级模板
                setupTemplateGrid()
            }
        }
    }
    
    private fun hideSimpleMode() {
        hideWindow()
    }

    private fun checkOrientation() {
        val orientation = resources.configuration.orientation
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            val gridLayout = simpleModeView.findViewById<GridLayout>(R.id.grid_layout)
            gridLayout.columnCount = 3
        }
    }
} 