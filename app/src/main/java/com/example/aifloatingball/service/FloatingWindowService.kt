package com.example.aifloatingball.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.preference.PreferenceManager
import android.view.WindowManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.preference.SearchEngineListPreference
import com.example.aifloatingball.HomeActivity
import android.graphics.BitmapFactory
import com.example.aifloatingball.FloatingWebViewService
import com.example.aifloatingball.DualFloatingWebViewService
import com.example.aifloatingball.model.SearchEngineShortcut
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.math.abs
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.FrameLayout
import com.example.aifloatingball.manager.SearchEngineManager
import android.app.AlertDialog
import com.example.aifloatingball.utils.EngineUtil
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.SettingsManager
import android.view.ContextThemeWrapper
import android.view.View.OnLongClickListener
import android.view.MotionEvent.ACTION_UP
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import android.content.ClipboardManager
import android.content.ClipData
import android.text.TextUtils
import android.os.Build.VERSION_CODES
import android.os.Build.VERSION

class FloatingWindowService : Service() {
    // 添加TAG常量
    companion object {
        private const val TAG = "FloatingWindowService"
    }
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastClickTime: Long = 0
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var isMenuVisible = false // 记录菜单是否可见
    
    // 初始化长按检测
    private var longPressRunnable: Runnable = Runnable {
        // 长按时的操作，比如显示设置菜单
        openSettings()
    }
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "FloatingBallChannel"
    private val DOUBLE_CLICK_TIME = 300L
    private val MENU_MARGIN = 20 // 菜单与屏幕边缘的距离
    private val screenWidth by lazy { windowManager?.defaultDisplay?.width ?: 0 }
    private val screenHeight by lazy { windowManager?.defaultDisplay?.height ?: 0 }
    
    // 添加搜索引擎快捷方式相关变量
    private var shortcutsContainer: LinearLayout? = null
    private var searchInput: EditText? = null
    private var searchEngineShortcuts: List<SearchEngineShortcut> = emptyList()
    
    // 搜索模式相关变量
    private var isAIMode: Boolean = false
    private var searchModeToggle: com.google.android.material.button.MaterialButton? = null
    private var aiEnginesContainer: LinearLayout? = null
    private var regularEnginesContainer: LinearLayout? = null
    private var savedCombosContainer: LinearLayout? = null
    private var searchContainer: LinearLayout? = null
    
    // AI和普通搜索引擎列表
    private var aiSearchEngines: List<AISearchEngine> = emptyList()
    private var regularSearchEngines: List<SearchEngine> = emptyList()
    private lateinit var settingsManager: SettingsManager
    
    // 广播接收器，用于接收搜索引擎快捷方式更新通知
    private val shortcutsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("FloatingWindowService", "收到快捷方式更新广播")
            if (intent?.action == "com.example.aifloatingball.ACTION_UPDATE_SHORTCUTS") {
                // 立即重新加载和显示搜索引擎快捷方式
                loadSearchEngineShortcuts()
                // 显示提示
                Toast.makeText(this@FloatingWindowService, "搜索引擎快捷方式已更新", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 初始化设置管理器
        settingsManager = SettingsManager.getInstance(this)
        
        // 加载搜索模式设置
        isAIMode = settingsManager.isDefaultAIMode()
        
        initializeWindowManager()
        createFloatingWindow()
        initializeViews()
        
        // 确保自定义菜单布局已创建
        createTextSelectionMenuLayout()
        
        setupSearchInput()
        loadSearchEngineShortcuts()
        
        // 加载AI和普通搜索引擎
        loadSearchEngines()
        
        // 注册广播接收器
        registerReceiver(shortcutsUpdateReceiver, IntentFilter("com.example.aifloatingball.ACTION_UPDATE_SHORTCUTS"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Ball Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating ball service running"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("AI Floating Ball")
        .setContentText("Tap to open settings")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(
            this,
            0,
            Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        ))
        .build()

    private fun initializeWindowManager() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val size = prefs.getInt("ball_size", 100)
        val alphaValue = prefs.getFloat("ball_alpha", 0.8f)
        
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("last_x", 0)
            y = prefs.getInt("last_y", 100)
            this.alpha = alphaValue
        }
    }

    private fun createFloatingWindow() {
        // 使用 Material 主题包装 LayoutInflater
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext).cloneInContext(themedContext)
        floatingView = inflater.inflate(R.layout.floating_ball_layout, null)
        
        // 初始化容器
        shortcutsContainer = floatingView?.findViewById(R.id.search_shortcuts_container)
        savedCombosContainer = floatingView?.findViewById(R.id.saved_combos_container)
        aiEnginesContainer = floatingView?.findViewById(R.id.ai_engines_container)
        regularEnginesContainer = floatingView?.findViewById(R.id.regular_engines_container)
        searchContainer = floatingView?.findViewById(R.id.search_container)
        searchInput = floatingView?.findViewById(R.id.search_input)
        searchModeToggle = floatingView?.findViewById(R.id.search_mode_toggle)
        
        // 获取悬浮球图标
        val floatingBallIcon = floatingView?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floating_ball_icon)
        
        // 设置搜索容器背景为不透明
        searchContainer?.setBackgroundResource(R.drawable.search_container_background)
        
        // 初始状态下隐藏所有搜索相关元素
        searchContainer?.visibility = View.GONE
        shortcutsContainer?.visibility = View.GONE
        savedCombosContainer?.visibility = View.GONE
        aiEnginesContainer?.visibility = View.GONE
        regularEnginesContainer?.visibility = View.GONE
        searchModeToggle?.visibility = View.GONE
        
        // 设置悬浮球图标点击事件
        floatingBallIcon?.setOnClickListener {
            toggleSearchInterface()
        }
        
        // 设置悬浮球点击和拖动行为
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    // 开始长按检测
                    handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    // 如果移动距离超过阈值，取消长按和点击事件
                    if (abs(deltaX) > ViewConfiguration.get(this).scaledTouchSlop ||
                        abs(deltaY) > ViewConfiguration.get(this).scaledTouchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                        
                        // 如果菜单是可见的，先隐藏菜单
                        if (isMenuVisible) {
                            toggleSearchInterface()
                        }
                        
                        // 更新悬浮球位置
                        params?.x = initialX + deltaX
                        params?.y = initialY + deltaY
                        
                        // 限制不超出屏幕边界
                        params?.x = params?.x?.coerceIn(0, screenWidth - (params?.width ?: 0)) ?: 0
                        params?.y = params?.y?.coerceIn(0, screenHeight - (params?.height ?: 0)) ?: 0
                        
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    
                    val moved = abs(event.rawX - initialTouchX) > ViewConfiguration.get(this).scaledTouchSlop ||
                               abs(event.rawY - initialTouchY) > ViewConfiguration.get(this).scaledTouchSlop
                    
                    if (moved) {
                        // 保存新位置
                        savePosition()
                        // 检查是否需要贴边
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
        
        windowManager?.addView(floatingView, params)
    }

    private fun snapToEdge() {
        params?.let { p ->
            val isNearEdge = when {
                p.x < MENU_MARGIN -> {
                    p.x = 0
                    true
                }
                p.x > screenWidth - p.width - MENU_MARGIN -> {
                    p.x = screenWidth - p.width
                    true
                }
                else -> false
            }

            // 根据是否靠边调整透明度
            if (isNearEdge) {
                floatingView?.alpha = 0.4f  // 靠边时更透明
            } else {
                floatingView?.alpha = 1.0f  // 非靠边时完全不透明
            }

            windowManager?.updateViewLayout(floatingView, params)
            // 保存最终位置
            savePosition()
        }
    }

    private fun savePosition() {
        params?.let { p ->
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt("last_x", p.x)
                .putInt("last_y", p.y)
                .apply()
        }
    }

    private fun onDoubleClick() {
        // Toggle visibility
        floatingView?.let { view ->
            view.alpha = if (view.alpha > 0.5f) 0.3f else 1.0f
        }
    }

    private fun openSettings() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openSearchEngine() {
        val url = getSearchEngineUrl()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun getSearchEngineUrl(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val searchEngine = prefs.getString("search_engine", "baidu") ?: "baidu"
        return SearchEngineListPreference.getSearchEngineUrl(this, searchEngine)
    }
    
    // 设置搜索输入框
    private fun setupSearchInput() {
        searchInput = floatingView?.findViewById(R.id.search_input)
        val searchIcon = floatingView?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floating_ball_icon)
        searchModeToggle = floatingView?.findViewById(R.id.search_mode_toggle)
        
        searchInput?.apply {
            // 基本属性设置
            isFocusableInTouchMode = true
            isFocusable = true
            isLongClickable = true
            isCursorVisible = true
            setTextIsSelectable(true)
            
            // 设置输入框背景为透明但可触摸
            setBackgroundResource(android.R.color.transparent)
            
            // 设置长按监听器来显示自定义菜单
            setOnLongClickListener {
                showCustomTextMenu()
                true
            }
            
            // 设置触摸监听器，用于选择文本后显示自定义菜单
            setOnTouchListener { v, event ->
                if (event.action == ACTION_UP) {
                    if (hasSelection()) {
                        showCustomTextMenu()
                    }
                }
                // 调用默认的onTouchEvent处理选择等操作
                v.onTouchEvent(event)
            }
            
            // 设置输入完成动作
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = text?.toString()?.trim() ?: ""
                    if (query.isNotEmpty()) {
                        performSearch(query)
                    } else {
                        Toast.makeText(context, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    false
                }
            }
        }
        
        // 设置搜索框获得焦点和失去焦点时的行为
        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 获得焦点时：
                // 1. 允许输入法显示
                params?.flags = params?.flags?.and(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) ?: 0
                windowManager?.updateViewLayout(floatingView, params)
                
                // 2. 显示搜索模式切换按钮
                searchModeToggle?.visibility = View.VISIBLE
                
                // 3. 根据当前模式显示相应的搜索引擎容器
                if (isAIMode) {
                    aiEnginesContainer?.visibility = View.VISIBLE
                    regularEnginesContainer?.visibility = View.GONE
                } else {
                    aiEnginesContainer?.visibility = View.GONE
                    regularEnginesContainer?.visibility = View.VISIBLE
                }
                
                // 4. 显示键盘
                showKeyboard(searchInput)
            } else {
                // 失去焦点时：
                // 1. 隐藏键盘
                hideKeyboard()
                
                // 2. 隐藏搜索模式切换按钮（延迟执行，避免干扰操作）
                handler.postDelayed({
                    searchModeToggle?.visibility = View.GONE
                }, 200)
            }
        }
        
        // 设置搜索图标点击事件
        searchIcon?.setOnClickListener {
            toggleSearchInterface()
        }
        
        // 设置搜索模式切换按钮点击事件
        searchModeToggle?.setOnClickListener {
            toggleSearchMode()
        }
    }
    
    // 显示键盘
    private fun showKeyboard(view: View?) {
        if (view == null) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        view.post {
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    // 隐藏键盘
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        floatingView?.windowToken?.let {
            imm.hideSoftInputFromWindow(it, 0)
        }
    }
    
    // 执行搜索
    private fun performSearch(query: String) {
        // 使用默认搜索引擎搜索，或者如果有快捷方式，使用第一个快捷方式
        if (searchEngineShortcuts.isNotEmpty()) {
            openSearchWithEngine(searchEngineShortcuts[0], query)
        } else {
            // 使用系统默认搜索引擎
            val searchUrl = SearchEngineListPreference.getSearchEngineUrl(this, 
                PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("search_engine", "baidu") ?: "baidu")
                .replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
            
            val intent = Intent(this, FloatingWebViewService::class.java).apply {
                putExtra("url", searchUrl)
            }
            startService(intent)
        }
        
        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(floatingView?.windowToken, 0)
    }
    
    // 加载已保存的搜索引擎快捷方式
    private fun loadSearchEngineShortcuts() {
        try {
            Log.d("FloatingWindowService", "开始加载搜索引擎快捷方式")
            
            // 临时列表存储转换后的快捷方式
            val newShortcuts = mutableListOf<SearchEngineShortcut>()
            
            // 1. 从SearchEngineManager获取保存的搜索引擎组
            val searchEngineManager = com.example.aifloatingball.manager.SearchEngineManager.getInstance(this)
            val searchEngineGroups = searchEngineManager.getSearchEngineGroups()
            
            // 获取用户启用的搜索引擎组合 - 直接从SharedPreferences获取，而不依赖SettingsManager
            val enabledGroups = getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getStringSet("enabled_search_engine_groups", emptySet()) ?: emptySet()
            
            Log.d("FloatingWindowService", "从SearchEngineManager获取到 ${searchEngineGroups.size} 个搜索引擎组，其中 ${enabledGroups.size} 个已启用")
            
            // 2. 将启用的搜索引擎组转换为快捷方式
            searchEngineGroups.forEach { group ->
                // 只添加已启用的组或者当启用列表为空时添加所有组（向后兼容）
                if ((enabledGroups.isEmpty() || enabledGroups.contains(group.name)) && group.engines.isNotEmpty()) {
                    // 使用组中的第一个搜索引擎作为主要搜索引擎
                    val primaryEngine = group.engines[0]
                    
                    // 改进URL转换逻辑，确保正确处理不同搜索引擎URL格式
                    val searchUrl = convertToSearchUrl(primaryEngine.url)
                    
                    // 创建快捷方式对象
                    val shortcut = SearchEngineShortcut(
                        id = group.name.hashCode().toString(),
                        name = group.name,
                        url = searchUrl,
                        domain = extractDomain(primaryEngine.url)
                    )
                    
                    newShortcuts.add(shortcut)
                    Log.d("FloatingWindowService", "添加搜索引擎组快捷方式: ${shortcut.name}, URL: ${shortcut.url}")
                }
            }
            
            // 3. 从SharedPreferences获取旧的快捷方式
            val prefs = getSharedPreferences("search_engine_shortcuts", Context.MODE_PRIVATE)
            val gson = Gson()
            val shortcutsJson = prefs.getString("shortcuts", "[]")
            val type = object : TypeToken<List<SearchEngineShortcut>>() {}.type
            val oldShortcuts: List<SearchEngineShortcut> = gson.fromJson(shortcutsJson, type) ?: emptyList()
            
            Log.d("FloatingWindowService", "从SharedPreferences获取到 ${oldShortcuts.size} 个已保存的快捷方式")
            
            // 4. 合并新旧快捷方式，避免重复（仅当启用列表为空时才考虑旧快捷方式，向后兼容）
            if (enabledGroups.isEmpty()) {
                oldShortcuts.forEach { oldShortcut ->
                    if (newShortcuts.none { it.domain == oldShortcut.domain && it.name == oldShortcut.name }) {
                        newShortcuts.add(oldShortcut)
                        Log.d("FloatingWindowService", "添加历史快捷方式: ${oldShortcut.name}")
                    }
                }
            }
            
            // 5. 添加测试快捷方式（仅当没有实际快捷方式且启用列表为空时）
            if (newShortcuts.isEmpty() && enabledGroups.isEmpty()) {
                Log.d("FloatingWindowService", "没有找到已保存的快捷方式，添加测试快捷方式")
                
                val testShortcuts = listOf(
                    SearchEngineShortcut(
                        id = "test_baidu",
                        name = "百度",
                        url = "https://www.baidu.com/s?wd={query}",
                        domain = "baidu.com"
                    ),
                    SearchEngineShortcut(
                        id = "test_google",
                        name = "谷歌",
                        url = "https://www.google.com/search?q={query}",
                        domain = "google.com"
                    ),
                    SearchEngineShortcut(
                        id = "test_combo",
                        name = "百度+谷歌",
                        url = "https://www.baidu.com/s?wd={query}",
                        domain = "baidu.com"
                    )
                )
                
                newShortcuts.addAll(testShortcuts)
            }
            
            // 6. 更新成员变量
            searchEngineShortcuts = newShortcuts
            
            // 7. 保存合并后的快捷方式
            prefs.edit().putString("shortcuts", gson.toJson(newShortcuts)).apply()
            
            // 8. 确保在UI线程上刷新显示
            Handler(Looper.getMainLooper()).post {
                displaySearchEngineShortcuts()
                Log.d("FloatingWindowService", "在UI线程中刷新显示快捷方式")
            }
            
            Log.d("FloatingWindowService", "已加载 ${searchEngineShortcuts.size} 个搜索引擎快捷方式")
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "加载搜索引擎快捷方式失败", e)
            e.printStackTrace()
        }
    }
    
    // 改进的URL转换方法，更可靠地处理不同的搜索引擎URL格式
    private fun convertToSearchUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            
            // 常见搜索引擎查询参数名称
            val queryParamNames = listOf("q", "query", "word", "wd", "text", "search")
            
            // 尝试每一种可能的查询参数
            for (paramName in queryParamNames) {
                val queryValue = uri.getQueryParameter(paramName)
                if (!queryValue.isNullOrEmpty()) {
                    // 找到查询参数，将整个参数值替换为{query}占位符
                    val pattern = "$paramName=$queryValue"
                    val replacement = "$paramName={query}"
                    return url.replace(pattern, replacement)
                }
            }
            
            // 如果没有找到已知的查询参数，尝试使用正则表达式
            val regex = "([?&](q|query|word|wd|text|search)=)[^&]*".toRegex()
            val result = regex.find(url)
            if (result != null) {
                return url.replace(result.value, "${result.groupValues[1]}{query}")
            }
            
            // 如果上述方法都失败，则直接追加查询参数
            return if (url.contains("?")) {
                "$url&q={query}"
            } else {
                "$url?q={query}"
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "URL转换失败: ${e.message}")
            return "$url?q={query}" // 降级处理
        }
    }

    // 从URL中提取域名
    private fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.host ?: ""
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "提取域名失败: ${e.message}")
            ""
        }
    }

    // 显示搜索引擎快捷方式
    private fun displaySearchEngineShortcuts() {
        try {
            Log.d("FloatingWindowService", "开始显示搜索引擎快捷方式")
            
            // 确保容器可用
            if (shortcutsContainer == null) {
                Log.e("FloatingWindowService", "快捷方式容器不存在")
                return
            }
            
            // 清空容器
            shortcutsContainer?.removeAllViews()
            
            // 检查是否有快捷方式可显示
            if (searchEngineShortcuts.isEmpty()) {
                Log.d("FloatingWindowService", "没有快捷方式可显示")
                shortcutsContainer?.visibility = View.GONE
                return
            }
            
            // 确保容器可见
            shortcutsContainer?.visibility = View.VISIBLE
            
            // 创建一个指示器显示快捷方式数量
            val indicatorText = TextView(this).apply {
                text = "${searchEngineShortcuts.size}个搜索引擎"
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#D32F2F"))
                    cornerRadius = 8f
                }
                textSize = 12f
                setPadding(12, 6, 12, 6)
            }
            shortcutsContainer?.addView(indicatorText)
            
            // 为每个快捷方式创建按钮
            searchEngineShortcuts.forEach { shortcut ->
                val shortcutView = createShortcutView(shortcut)
                shortcutsContainer?.addView(shortcutView)
            }
            
            // 添加一个信息按钮，用于显示所有快捷方式
            val infoButton = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_info_details)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2196F3"))
                    cornerRadius = 15f
                }
                val size = 60
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(10, 5, 10, 5)
                }
                contentDescription = "更多快捷方式信息"
                setOnClickListener {
                    showAllShortcutsDialog()
                }
            }
            shortcutsContainer?.addView(infoButton)
            
            Log.d("FloatingWindowService", "搜索引擎快捷方式显示完成，总共显示 ${shortcutsContainer?.childCount} 个视图")
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "显示快捷方式失败", e)
            Toast.makeText(this, "显示快捷方式失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 显示所有快捷方式信息对话框
    private fun showAllShortcutsDialog() {
        if (searchEngineShortcuts.isEmpty()) {
            Toast.makeText(this, "没有可用的搜索引擎快捷方式", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 构建每个快捷方式的描述文本
        val message = searchEngineShortcuts.joinToString("\n\n") { shortcut -> 
            "${shortcut.name}\n${shortcut.url}" 
        }
        
        // 创建一个简单的对话框来显示所有快捷方式
        AlertDialog.Builder(this)
            .setTitle("搜索引擎快捷方式")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setNeutralButton("删除所有") { _, _ ->
                showDeleteAllShortcutsDialog()
            }
            .show()
    }
    
    // 显示删除所有快捷方式的确认对话框
    private fun showDeleteAllShortcutsDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除所有快捷方式")
            .setMessage("确定要删除所有搜索引擎快捷方式吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                // 清空快捷方式列表
                searchEngineShortcuts = emptyList()
                
                // 保存空列表
                val gson = Gson()
                getSharedPreferences("search_engine_shortcuts", Context.MODE_PRIVATE)
                    .edit()
                    .putString("shortcuts", gson.toJson(emptyList<SearchEngineShortcut>()))
                    .apply()
                
                // 刷新显示
                displaySearchEngineShortcuts()
                
                // 显示提示
                Toast.makeText(this, "已删除所有快捷方式", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 创建单个快捷方式视图
    private fun createShortcutView(shortcut: SearchEngineShortcut): View {
        // 创建视图
        val view = View.inflate(this, R.layout.search_engine_shortcut, null)
        
        val iconView = view.findViewById<ImageView>(R.id.shortcut_icon)
        val nameView = view.findViewById<TextView>(R.id.shortcut_name)
        
        // 获取图标容器（FrameLayout）
        val iconContainer = iconView.parent as? FrameLayout
        
        // 设置图标
        val iconResId = EngineUtil.getIconResourceByDomain(shortcut.domain)
        iconView.setImageResource(iconResId)
        
        // 设置图标背景色 - 更柔和的颜色
        iconView.setBackgroundResource(R.drawable.search_item_background)
        (iconView.background as GradientDrawable).setColor(Color.parseColor("#F5F5F5"))
        
        // 设置名称
        nameView.text = shortcut.name
        nameView.setTextColor(Color.parseColor("#5F6368"))
        
        // 为多引擎快捷方式添加标记
        if (shortcut.name.contains("+") && iconContainer != null) {
            val badge = TextView(this).apply {
                text = "+"
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#F44336"))
                }
                layoutParams = FrameLayout.LayoutParams(24, 24).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, 0, 0, 0)
                }
            }
            iconContainer.addView(badge)
        }
        
        // 设置点击事件
        view.setOnClickListener {
            val query = searchInput?.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) {
                openSearchWithEngine(shortcut, query)
            } else {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置长按事件：删除快捷方式
        view.setOnLongClickListener {
            showDeleteShortcutDialog(shortcut)
            true
        }
        
        return view
    }
    
    // 显示删除快捷方式的确认对话框
    private fun showDeleteShortcutDialog(shortcut: SearchEngineShortcut) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("快捷方式: ${shortcut.name}")
            .setMessage("是否要删除此快捷方式?")
            .setPositiveButton("删除") { _, _ ->
                // 从列表中删除
                val updatedList = searchEngineShortcuts.toMutableList()
                updatedList.remove(shortcut)
                searchEngineShortcuts = updatedList
                
                // 保存更新后的列表
                val gson = Gson()
                val json = gson.toJson(searchEngineShortcuts)
                getSharedPreferences("search_engine_shortcuts", Context.MODE_PRIVATE)
                    .edit()
                    .putString("shortcuts", json)
                    .apply()
                
                // 刷新显示
                displaySearchEngineShortcuts()
                
                Toast.makeText(this, "已删除快捷方式", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun initializeViews() {
        // 初始化搜索引擎快捷方式容器
        shortcutsContainer = floatingView?.findViewById(R.id.search_shortcuts_container)
        searchInput = floatingView?.findViewById(R.id.search_input)

        // 设置搜索输入框行为
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput?.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    performSearch(query)
                } else {
                    Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }

        // 加载并显示已保存的搜索引擎快捷方式
        loadSearchEngineShortcuts()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        
        // 注销广播接收器
        try {
            unregisterReceiver(shortcutsUpdateReceiver)
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "注销广播接收器失败: ${e.message}")
        }
    }

    // 使用特定搜索引擎进行搜索
    private fun openSearchWithEngine(shortcut: SearchEngineShortcut, query: String) {
        try {
            if (shortcut.name.contains("+")) { // 如果是搜索引擎组
                val intent = Intent(this, DualFloatingWebViewService::class.java)
                intent.putExtra("SEARCH_QUERY", query)
                intent.putExtra("NUM_WINDOWS", shortcut.name.count { it == '+' } + 1)
                startService(intent)
            } else {
                val encodedQuery = Uri.encode(query)
                val searchUrl = shortcut.url.replace("{query}", encodedQuery)
                
                val intent = Intent(this, FloatingWebViewService::class.java)
                intent.putExtra("URL", searchUrl)
                startService(intent)
            }
            searchInput?.setText("")
            setSearchModeDismiss()
        } catch (e: Exception) {
            Log.e(TAG, "Error searching with engine: ${e.message}")
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 调试方法：打印所有View的ID
    private fun debugPrintAllViewIds(rootView: View?) {
        if (rootView == null) return
        try {
            Log.d("ViewDebug", "开始查找所有视图ID")
            
            fun traverseView(view: View, prefix: String) {
                val id = view.id
                val idName = if (id != View.NO_ID) resources.getResourceEntryName(id) else "NO_ID"
                Log.d("ViewDebug", "$prefix[${view.javaClass.simpleName}] ID: $idName, 可见: ${view.visibility == View.VISIBLE}")
                
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        traverseView(view.getChildAt(i), "$prefix  ")
                    }
                }
            }
            
            traverseView(rootView, "")
            Log.d("ViewDebug", "视图树遍历完成")
        } catch (e: Exception) {
            Log.e("ViewDebug", "打印视图ID失败: ${e.message}")
        }
    }

    // 切换搜索界面显示/隐藏
    private fun toggleSearchInterface() {
        if (searchContainer?.visibility == View.VISIBLE) {
            // 隐藏搜索界面
            searchContainer?.visibility = View.GONE
            shortcutsContainer?.visibility = View.GONE
            aiEnginesContainer?.visibility = View.GONE
            regularEnginesContainer?.visibility = View.GONE
            savedCombosContainer?.visibility = View.GONE
            searchModeToggle?.visibility = View.GONE
            
            // 清空输入框内容
            searchInput?.setText("")
            
            // 隐藏键盘
            hideKeyboard()
            
            // 恢复悬浮球原始位置
            params?.x = initialX
            params?.y = initialY
            
            // 恢复FLAG_NOT_FOCUSABLE标志
            params?.flags = (params?.flags ?: 0).or(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            
            windowManager?.updateViewLayout(floatingView, params)
            
            isMenuVisible = false
        } else {
            // 记录当前位置
            initialX = params?.x ?: 0
            initialY = params?.y ?: 0
            
            // 计算菜单显示位置
            val menuX = calculateMenuX()
            val menuY = calculateMenuY()
            
            // 移动悬浮球到合适位置
            params?.x = menuX
            params?.y = menuY
            
            // 修改窗口参数以允许文本操作
            params?.flags = (params?.flags ?: 0).and(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            ).and(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            ).or(
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            
            windowManager?.updateViewLayout(floatingView, params)
            
            // 显示搜索界面
            searchContainer?.visibility = View.VISIBLE
            savedCombosContainer?.visibility = View.VISIBLE
            
            // 根据当前模式显示相应的搜索引擎容器
            if (isAIMode) {
                aiEnginesContainer?.visibility = View.VISIBLE
                regularEnginesContainer?.visibility = View.GONE
            } else {
                aiEnginesContainer?.visibility = View.GONE
                regularEnginesContainer?.visibility = View.VISIBLE
            }
            
            // 显示搜索模式切换按钮
            searchModeToggle?.visibility = View.VISIBLE
            
            // 激活输入框和输入法
            searchInput?.post {
                searchInput?.requestFocus()
                showKeyboard(searchInput)
            }
            
            isMenuVisible = true
        }
    }
    
    // 计算菜单X坐标
    private fun calculateMenuX(): Int {
        val currentX = params?.x ?: 0
        val menuWidth = searchContainer?.width ?: 0
        
        // 如果靠近右边缘，向左移动
        return if (currentX + menuWidth > screenWidth - MENU_MARGIN) {
            screenWidth - menuWidth - MENU_MARGIN
        } else {
            currentX
        }
    }

    // 计算菜单Y坐标
    private fun calculateMenuY(): Int {
        val currentY = params?.y ?: 0
        val menuHeight = searchContainer?.height ?: 0
        
        // 如果靠近底部，向上移动
        return if (currentY + menuHeight > screenHeight - MENU_MARGIN) {
            screenHeight - menuHeight - MENU_MARGIN
        } else {
            currentY
        }
    }
    
    // 切换搜索模式 (AI / 普通)
    private fun toggleSearchMode() {
        isAIMode = !isAIMode
        
        // 保存搜索模式设置
        settingsManager.setDefaultAIMode(isAIMode)
        
        // 更新UI显示
        if (isAIMode) {
            aiEnginesContainer?.visibility = View.VISIBLE
            regularEnginesContainer?.visibility = View.GONE
        } else {
            aiEnginesContainer?.visibility = View.GONE
            regularEnginesContainer?.visibility = View.VISIBLE
        }
        
        // 更新搜索模式图标
        updateSearchModeIcon()
        
        // 显示切换提示
        val modeText = if (isAIMode) "AI搜索模式" else "普通搜索模式"
        Toast.makeText(this, "已切换至$modeText", Toast.LENGTH_SHORT).show()
    }
    
    // 更新搜索模式图标
    private fun updateSearchModeIcon() {
        searchModeToggle?.setIconResource(
            if (isAIMode) R.drawable.ic_ai_search
            else R.drawable.ic_search
        )
    }
    
    // 加载搜索引擎
    private fun loadSearchEngines() {
        try {
            // 加载AI搜索引擎
            aiSearchEngines = com.example.aifloatingball.model.AISearchEngine.DEFAULT_AI_ENGINES.toList()
            
            // 加载普通搜索引擎
            regularSearchEngines = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.toList()
            
            // 更新搜索引擎显示
            updateSearchEngineDisplay()
            
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "加载搜索引擎失败", e)
        }
    }
    
    // 更新搜索引擎显示
    private fun updateSearchEngineDisplay() {
        try {
            // 清空容器
            aiEnginesContainer?.removeAllViews()
            regularEnginesContainer?.removeAllViews()
            savedCombosContainer?.removeAllViews()
            
            // 1. 显示已保存的搜索引擎组合
            if (searchEngineShortcuts.isNotEmpty()) {
                savedCombosContainer?.visibility = View.VISIBLE
                
                // 创建水平滚动视图显示组合
                val horizontalScroll = HorizontalScrollView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    isHorizontalScrollBarEnabled = true
                }
                
                val comboContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(8, 8, 8, 8)
                }
                
                // 添加快捷方式
                searchEngineShortcuts.forEach { shortcut ->
                    comboContainer.addView(createShortcutView(shortcut))
                }
                
                horizontalScroll.addView(comboContainer)
                savedCombosContainer?.addView(horizontalScroll)
            } else {
                savedCombosContainer?.visibility = View.GONE
            }
            
            // 2. 设置AI搜索引擎
            aiEnginesContainer?.visibility = if (isAIMode) View.VISIBLE else View.GONE
            
            // 创建水平滚动视图显示AI搜索引擎
            val aiHorizontalScroll = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = true
            }
            
            val aiEnginesLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(8, 8, 8, 8)
            }
            
            // 添加AI搜索引擎
            aiSearchEngines.forEach { engine ->
                aiEnginesLayout.addView(createSearchEngineView(engine, true))
            }
            
            aiHorizontalScroll.addView(aiEnginesLayout)
            aiEnginesContainer?.addView(aiHorizontalScroll)
            
            // 3. 设置普通搜索引擎
            regularEnginesContainer?.visibility = if (isAIMode) View.GONE else View.VISIBLE
            
            // 创建水平滚动视图显示普通搜索引擎
            val regularHorizontalScroll = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = true
            }
            
            val regularEnginesLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(8, 8, 8, 8)
            }
            
            // 添加普通搜索引擎
            regularSearchEngines.forEach { engine ->
                regularEnginesLayout.addView(createSearchEngineView(engine, false))
            }
            
            regularHorizontalScroll.addView(regularEnginesLayout)
            regularEnginesContainer?.addView(regularHorizontalScroll)
            
            // 更新搜索模式图标
            updateSearchModeIcon()
            
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "更新搜索引擎显示失败", e)
        }
    }
    
    // 创建搜索引擎视图 - 使用统一的风格
    private fun createSearchEngineView(engine: Any, isAI: Boolean): View {
        // 获取引擎信息
        val name: String
        val iconResId: Int
        val url: String
        
        if (isAI && engine is com.example.aifloatingball.model.AISearchEngine) {
            name = engine.name
            iconResId = engine.iconResId
            url = engine.url
        } else if (!isAI && engine is com.example.aifloatingball.model.SearchEngine) {
            name = engine.name
            iconResId = engine.iconResId
            url = engine.url
        } else {
            name = "未知"
            iconResId = R.drawable.ic_search
            url = ""
        }
        
        // 创建视图
        val view = View.inflate(this, R.layout.search_engine_shortcut, null)
        
        val iconView = view.findViewById<ImageView>(R.id.shortcut_icon)
        val nameView = view.findViewById<TextView>(R.id.shortcut_name)
        
        // 设置图标
        iconView.setImageResource(iconResId)
        
        // 设置图标背景色 - 更统一的风格
        val backgroundColor = if (isAI) "#EDE7F6" else "#E8F5E9"
        iconView.setBackgroundResource(R.drawable.search_item_background)
        (iconView.background as GradientDrawable).setColor(Color.parseColor(backgroundColor))
        
        // 设置名称
        nameView.text = name
        // 根据AI/普通设置不同颜色，但更柔和
        nameView.setTextColor(if (isAI) Color.parseColor("#673AB7") else Color.parseColor("#388E3C"))
        
        // 设置点击事件
        view.setOnClickListener {
            val query = searchInput?.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) {
                searchWithEngine(name, query, isAI)
            } else {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        return view
    }
    
    // 使用搜索引擎进行搜索
    private fun searchWithEngine(engineName: String, query: String, isAI: Boolean) {
        try {
            val encodedQuery = Uri.encode(query)
            
            // 使用 DualFloatingWebViewService 打开搜索结果
            val intent = Intent(this, com.example.aifloatingball.DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("window_count", 1) // 默认单窗口
                
                // 设置搜索引擎
                if (isAI) {
                    putExtra("engine_key", "ai_" + EngineUtil.getEngineKey(engineName))
                } else {
                    putExtra("engine_key", EngineUtil.getEngineKey(engineName))
                }
            }
            
            startService(intent)
            
            // 清空搜索框
            searchInput?.setText("")
            
            // 隐藏搜索界面
            searchContainer?.visibility = View.GONE
            
            // 显示提示
            Toast.makeText(this, "使用 $engineName 搜索: $query", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "搜索失败: ${e.message}")
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 添加setSearchModeDismiss方法
    private fun setSearchModeDismiss() {
        // 隐藏搜索容器
        searchContainer?.visibility = View.GONE
        
        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        searchInput?.let { 
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    // 检查EditText是否有选中的文本
    private fun EditText.hasSelection(): Boolean {
        val selStart = selectionStart
        val selEnd = selectionEnd
        return selStart >= 0 && selEnd >= 0 && selStart != selEnd
    }

    // 显示自定义文本操作菜单
    private fun showCustomTextMenu() {
        searchInput?.let { editText ->
            // 创建自定义弹出菜单
            val popupView = LayoutInflater.from(this).inflate(R.layout.text_selection_menu, null)
            
            // 创建PopupWindow
            val popupWindow = PopupWindow(
                popupView,
                WRAP_CONTENT,
                WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            
            // 设置菜单项点击事件
            val copyButton = popupView.findViewById<TextView>(R.id.menu_copy)
            val pasteButton = popupView.findViewById<TextView>(R.id.menu_paste)
            val cutButton = popupView.findViewById<TextView>(R.id.menu_cut)
            val selectAllButton = popupView.findViewById<TextView>(R.id.menu_select_all)
            
            // 复制
            copyButton?.setOnClickListener {
                val selectedText = editText.text.toString().substring(
                    editText.selectionStart,
                    editText.selectionEnd
                )
                copyToClipboard(selectedText)
                popupWindow.dismiss()
            }
            
            // 粘贴
            pasteButton?.setOnClickListener {
                pasteFromClipboard(editText)
                popupWindow.dismiss()
            }
            
            // 剪切
            cutButton?.setOnClickListener {
                val selectedText = editText.text.toString().substring(
                    editText.selectionStart,
                    editText.selectionEnd
                )
                copyToClipboard(selectedText)
                editText.text.delete(editText.selectionStart, editText.selectionEnd)
                popupWindow.dismiss()
            }
            
            // 全选
            selectAllButton?.setOnClickListener {
                editText.selectAll()
                popupWindow.dismiss()
            }
            
            // 显示PopupWindow
            val location = IntArray(2)
            editText.getLocationOnScreen(location)
            
            // 根据是否有选中的文本来决定菜单的位置
            val xOffset = if (editText.hasSelection()) {
                val selectionMiddle = (editText.selectionStart + editText.selectionEnd) / 2
                try {
                    editText.getOffsetForPosition(
                        editText.text.toString().substring(0, selectionMiddle).width(editText.paint).toFloat(),
                        editText.lineHeight / 2f
                    )
                } catch (e: Exception) {
                    0
                }
            } else {
                editText.width / 2
            }
            
            popupWindow.showAtLocation(
                editText,
                Gravity.TOP or Gravity.START,
                location[0] + xOffset,
                location[1] - editText.height
            )
        }
    }

    // 计算字符串在给定Paint下的宽度
    private fun String.width(paint: android.text.TextPaint): Int {
        return android.text.Layout.getDesiredWidth(this, paint).toInt()
    }

    // 复制文本到剪贴板
    private fun copyToClipboard(text: String) {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("text", text)
            clipboardManager.setPrimaryClip(clipData)
            
            // 在Android 13及以上版本，系统会自动显示通知，所以我们只在低版本显示Toast
            if (VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败: ${e.message}")
        }
    }

    // 从剪贴板粘贴文本
    private fun pasteFromClipboard(editText: EditText) {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text
                    if (!TextUtils.isEmpty(text)) {
                        // 如果有选中的文本，替换选中部分
                        if (editText.hasSelection()) {
                            val start = editText.selectionStart
                            val end = editText.selectionEnd
                            editText.text.replace(start, end, text)
                        } else {
                            // 否则在当前光标位置插入
                            val start = editText.selectionStart
                            editText.text.insert(start, text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从剪贴板粘贴失败: ${e.message}")
        }
    }

    // 创建文本选择菜单布局
    private fun createTextSelectionMenuLayout() {
        try {
            // 检查布局文件是否已存在
            val layoutExists = try {
                resources.getLayout(R.layout.text_selection_menu)
                true
            } catch (e: Exception) {
                false
            }
            
            // 如果布局不存在，则动态创建
            if (!layoutExists) {
                Log.d(TAG, "创建文本选择菜单布局")
                
                // 创建布局
                val context = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#333333"))
                        cornerRadius = 8f
                    }
                    setPadding(16, 8, 16, 8)
                }
                
                // 添加操作按钮
                val textButtons = arrayOf("复制", "粘贴", "剪切", "全选")
                val ids = arrayOf(R.id.menu_copy, R.id.menu_paste, R.id.menu_cut, R.id.menu_select_all)
                
                for (i in textButtons.indices) {
                    val button = TextView(context).apply {
                        id = ids[i]
                        text = textButtons[i]
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setPadding(16, 8, 16, 8)
                        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                            setMargins(8, 0, 8, 0)
                        }
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#444444"))
                            cornerRadius = 4f
                        }
                    }
                    layout.addView(button)
                }
                
                // 动态创建布局文件
                /*
                此部分无法真正动态创建布局资源文件，仅是代码示例。
                实际应用需要在XML中创建布局文件。
                下面代码只是演示，不会真正执行。
                */
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建文本选择菜单布局失败: ${e.message}")
        }
    }
}