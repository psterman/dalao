package com.example.aifloatingball.service

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.*
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.example.aifloatingball.DualFloatingWebViewService
import com.example.aifloatingball.FloatingWebViewService
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.manager.SearchEngineManager
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineShortcut
import com.example.aifloatingball.preference.SearchEngineListPreference
import com.example.aifloatingball.utils.EngineUtil
import com.example.aifloatingball.utils.TextSelectionHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import com.example.aifloatingball.model.AppSearchSettings
import android.content.pm.PackageManager

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
    private var appSearchContainer: LinearLayout? = null  // 新增应用搜索容器引用
    
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

    private var textSelectionHelper: TextSelectionHelper? = null
    private var isTextSelectionActive = false
    private var isDraggingHandle = false

    private fun toggleWindowFocusableFlag(focusable: Boolean) {
        textSelectionHelper?.toggleWindowFocusableFlag(focusable)
    }

    private fun parseSelectionPositions(result: String): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        return textSelectionHelper?.parseSelectionPositions(result)
    }

    private fun positionHandle(handle: ImageView?, x: Int, y: Int) {
        textSelectionHelper?.positionHandle(handle, x, y)
    }

    private fun isOnHandle(x: Float, y: Float): Boolean {
        return textSelectionHelper?.isOnHandle(x, y) ?: false
    }

    private fun updateSelectionRange(x: Float, y: Float) {
        textSelectionHelper?.updateSelectionRange(x, y)
    }

    // 添加透明度更新的广播接收器
    private val alphaUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.ACTION_UPDATE_ALPHA") {
                val alpha = intent.getIntExtra("alpha", 85)
                updateFloatingBallAlpha(alpha)
            }
        }
    }

    // 添加更新透明度的方法
    private fun updateFloatingBallAlpha(alphaValue: Int) {
        try {
            val floatingBallIcon = floatingView?.findViewById<FloatingActionButton>(R.id.floating_ball_icon)
            floatingBallIcon?.alpha = alphaValue / 100f
            
            // 同时更新窗口参数的透明度
            params?.alpha = alphaValue / 100f
            windowManager?.updateViewLayout(floatingView, params)
            
            Log.d(TAG, "实时更新透明度: $alphaValue")
        } catch (e: Exception) {
            Log.e(TAG, "更新透明度失败: ${e.message}")
        }
    }

    // 添加应用搜索设置更新的广播接收器
    private val appSearchUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.ACTION_UPDATE_APP_SEARCH") {
                // 重新初始化应用搜索按钮
                initializeAppSearchButtons()
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
        
        // 注册点击外部关闭搜索菜单的监听器
        setupOutsideTouchListener()
        
        // 加载快捷方式但不显示它们
        loadSearchEngineShortcutsQuietly()
        
        // 加载AI和普通搜索引擎
        loadSearchEngines()
        
        // 注册广播接收器
        registerReceiver(shortcutsUpdateReceiver, IntentFilter("com.example.aifloatingball.ACTION_UPDATE_SHORTCUTS"))
        
        // 注册透明度更新的广播接收器
        registerReceiver(alphaUpdateReceiver, IntentFilter("com.example.aifloatingball.ACTION_UPDATE_ALPHA"))
        
        // 注册应用搜索设置更新的广播接收器
        registerReceiver(appSearchUpdateReceiver, 
            IntentFilter("com.example.aifloatingball.ACTION_UPDATE_APP_SEARCH"))
        
        // 确保初始时只显示悬浮球
        ensureOnlyFloatingBallVisible()
        
        // 确保悬浮球在安全区域内
        ensureBallInSafeArea()
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
        val alpha = prefs.getInt("ball_alpha", 85) / 100f
        
        // 获取状态栏高度
        val statusBarHeight = getStatusBarHeight()
        
        params = WindowManager.LayoutParams(
            WRAP_CONTENT,
            WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("last_x", 0)
            y = prefs.getInt("last_y", statusBarHeight + 20)  // 默认位置避开状态栏
            this.alpha = alpha  // 应用透明度设置
        }
    }
    
    // 获取状态栏高度的辅助方法
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
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
        
        // 获取悬浮球图标并设置透明度
        val floatingBallIcon = floatingView?.findViewById<FloatingActionButton>(R.id.floating_ball_icon)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val alpha = prefs.getInt("ball_alpha", 85) / 100f
        floatingBallIcon?.alpha = alpha

        // 设置搜索容器背景为不透明
        searchContainer?.setBackgroundResource(R.drawable.search_container_background)
        
        // 初始状态下隐藏所有搜索相关元素
        searchContainer?.visibility = View.GONE
        shortcutsContainer?.visibility = View.GONE
        savedCombosContainer?.visibility = View.GONE
        aiEnginesContainer?.visibility = View.GONE
        regularEnginesContainer?.visibility = View.GONE
        searchModeToggle?.visibility = View.GONE
        
        // 隐藏应用搜索容器
        val appSearchContainer = floatingView?.findViewById<LinearLayout>(R.id.app_search_container)
        appSearchContainer?.visibility = View.GONE

        // 完全重写触摸事件处理逻辑
        setupTouchEventHandling()

        windowManager?.addView(floatingView, params)
        isMenuVisible = false
    }

    // 设置触摸事件处理的新方法
    private fun setupTouchEventHandling() {
        // 获取悬浮球图标
        val floatingBallIcon = floatingView?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floating_ball_icon)
        
        // 定义变量
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialX = 0
        var initialY = 0
        var touchStartTime = 0L
        var isDragging = false
        var hasMoved = false
        var hasPerformedAction = false // 标记是否已执行了动作
        
        // 触摸阈值常量 - 减小移动阈值，提高拖动检测灵敏度
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop / 2
        val tapTimeout = ViewConfiguration.getTapTimeout()
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        val doubleTapTimeout = DOUBLE_CLICK_TIME // 双击检测时间窗口
        
        // 长按检测任务
        val longPressRunnable = Runnable {
            if (!hasMoved && !hasPerformedAction) {
                // 长按动作：打开设置
                Log.d(TAG, "执行长按操作：打开设置")
                hasPerformedAction = true // 标记已执行动作
                openSettings()
            }
        }

        // 双击检测任务
        val doubleTapRunnable = Runnable {
            if (!hasPerformedAction && !hasMoved) {
                // 如果超过双击时间间隔，且没有执行其他操作，视为单击
                Log.d(TAG, "单击超时：执行单击操作")
                toggleSearchInterface()
                hasPerformedAction = true
            }
        }
        
        // 为悬浮球添加触摸监听器
        floatingBallIcon?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始触摸数据
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    
                    // 重置状态
                    isDragging = false
                    hasMoved = false
                    hasPerformedAction = false
                    
                    // 取消可能的待处理任务
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(doubleTapRunnable)

                    // 设置长按检测
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                    Log.d(TAG, "触摸开始: x=$initialX, y=$initialY")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // 计算移动距离
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val distance = Math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble()).toFloat()
                    
                    // 判断是否开始拖动 - 使用更精确的距离计算
                    if (!isDragging && distance > touchSlop) {
                        isDragging = true
                        hasMoved = true
                        
                        // 取消长按检测和双击检测
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(doubleTapRunnable)
                        
                        Log.d(TAG, "开始拖动，距离: $distance")
                        
                        // 如果搜索界面已打开，先关闭它
                        if (isMenuVisible) {
                            hideSearchInterface()
                            hasPerformedAction = true // 标记已执行动作
                        }
                    }

                    // 如果正在拖动，更新悬浮球位置
                    if (isDragging) {
                        // 计算新位置
                        params?.x = (initialX + deltaX).toInt()
                        params?.y = (initialY + deltaY).toInt()
                        
                        try {
                        windowManager?.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "更新位置失败: ${e.message}")
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    handler.removeCallbacks(longPressRunnable)
                    
                    // 计算触摸时间
                    val touchDuration = System.currentTimeMillis() - touchStartTime
                    
                    // 处理事件结束操作
                    if (isDragging) {
                        // 拖动结束，保存位置
                        savePosition()
                        Log.d(TAG, "拖动结束，保存位置: x=${params?.x}, y=${params?.y}")
                    } else if (!hasMoved && !hasPerformedAction) {
                        // 没有移动且没有执行过操作，可能是点击
                        if (touchDuration < tapTimeout) {
                            val currentTime = System.currentTimeMillis()
                            
                            // 检测双击
                            if (currentTime - lastClickTime < doubleTapTimeout) {
                                // 取消单击检测
                                handler.removeCallbacks(doubleTapRunnable)
                                
                                // 执行双击操作
                                Log.d(TAG, "执行双击操作")
                                onDoubleClick()
                                hasPerformedAction = true
                            } else {
                                // 可能是单击，但需要等待确认不是双击的一部分
                                Log.d(TAG, "可能是单击，等待确认...")
                                // 设置延迟，等待可能的第二次点击
                                handler.postDelayed(doubleTapRunnable, doubleTapTimeout)
                            }
                            lastClickTime = currentTime
                        }
                    }
                    true
                }

                else -> false
            }
        }

        // 为搜索容器添加触摸监听器，防止点击传递到悬浮球
        searchContainer?.setOnTouchListener { view, event ->
            // 消费搜索容器内的触摸事件，不向下传递
            view.onTouchEvent(event)
            true
        }
    }

    // 添加一个专门保存位置的方法
    private fun savePosition() {
        try {
        params?.let { p ->
                // 获取悬浮球尺寸
                val floatingBallIcon = floatingView?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floating_ball_icon)
                val ballSize = floatingBallIcon?.width ?: 80
                
                // 获取屏幕尺寸
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                val statusBarHeight = getStatusBarHeight()
                
                // 确保悬浮球不会超出屏幕边界，并且至少有一半在屏幕内
                var safeX = p.x
                var safeY = p.y
                
                // 水平边界检查
                val halfBallSize = ballSize / 2
                if (safeX < -halfBallSize) {
                    safeX = -halfBallSize
                } else if (safeX > screenWidth - halfBallSize) {
                    safeX = screenWidth - halfBallSize
                }
                
                // 垂直边界检查，考虑状态栏
                if (safeY < statusBarHeight - halfBallSize) {
                    safeY = statusBarHeight - halfBallSize
                } else if (safeY > screenHeight - halfBallSize) {
                    safeY = screenHeight - halfBallSize
                }
                
                // 如果位置有修正，更新悬浮球位置
                if (safeX != p.x || safeY != p.y) {
                    p.x = safeX
                    p.y = safeY
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新安全位置失败: ${e.message}")
                    }
                }
                
                // 保存到SharedPreferences
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                    .putInt("last_x", safeX)
                    .putInt("last_y", safeY)
                .apply()
                Log.d(TAG, "位置已保存: x=$safeX, y=$safeY")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存位置失败: ${e.message}")
        }
    }

    // 添加确保悬浮球在安全区域的方法
    private fun ensureBallInSafeArea() {
        try {
            params?.let { p ->
                // 获取悬浮球尺寸
                val floatingBallIcon = floatingView?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floating_ball_icon)
                val ballSize = floatingBallIcon?.width ?: 80
                
                // 获取屏幕尺寸
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                val statusBarHeight = getStatusBarHeight()
                
                // 确保悬浮球不会超出屏幕边界，并且至少有一半在屏幕内
                var needUpdate = false
                
                // 水平边界检查
                val halfBallSize = ballSize / 2
                if (p.x < -halfBallSize) {
                    p.x = -halfBallSize
                    needUpdate = true
                } else if (p.x > screenWidth - halfBallSize) {
                    p.x = screenWidth - halfBallSize
                    needUpdate = true
                }
                
                // 垂直边界检查，考虑状态栏
                if (p.y < statusBarHeight - halfBallSize) {
                    p.y = statusBarHeight - halfBallSize
                    needUpdate = true
                } else if (p.y > screenHeight - halfBallSize) {
                    p.y = screenHeight - halfBallSize
                    needUpdate = true
                }
                
                // 如果位置有修正，更新悬浮球位置
                if (needUpdate) {
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                        Log.d(TAG, "悬浮球位置已调整: x=${p.x}, y=${p.y}")
                    } catch (e: Exception) {
                        Log.e(TAG, "更新安全位置失败: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "调整悬浮球位置失败: ${e.message}")
        }
    }

    // 移除贴边功能
    private fun snapToEdge() {
        // 不执行任何操作
    }

    private fun onDoubleClick() {
        try {
            // 启动DualFloatingWebViewService
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                // 使用默认设置
                putExtra("window_count", settingsManager.getDefaultWindowCount())
                
                // 设置默认搜索引擎
                putExtra("left_engine", settingsManager.getLeftWindowSearchEngine())
                putExtra("center_engine", settingsManager.getCenterWindowSearchEngine())
                putExtra("right_engine", settingsManager.getRightWindowSearchEngine())
            }
            startService(intent)
            
            Log.d(TAG, "双击启动多窗口服务")
        } catch (e: Exception) {
            Log.e(TAG, "启动多窗口服务失败: ${e.message}")
            Toast.makeText(this, "启动多窗口服务失败", Toast.LENGTH_SHORT).show()
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
                if (event.action == MotionEvent.ACTION_UP) {
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
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            // 确保view可以获取焦点
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            // 请求焦点
            view.requestFocus()
            // 显示输入法
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } catch (e: Exception) {
            Log.e(TAG, "显示键盘失败: ${e.message}")
        }
    }
    
    // 隐藏键盘
    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            floatingView?.windowToken?.let {
                imm.hideSoftInputFromWindow(it, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "隐藏键盘失败: ${e.message}")
        }
    }
    
    // 执行搜索
    private fun performSearch(query: String) {
        // 使用默认搜索引擎搜索，或者如果有快捷方式，使用第一个快捷方式
        if (searchEngineShortcuts.isNotEmpty()) {
            openSearchWithEngine(query, searchEngineShortcuts[0].searchUrl)
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
    
    // 加载已保存的搜索引擎快捷方式但不自动显示
    private fun loadSearchEngineShortcutsQuietly() {
        try {
            Log.d(TAG, "开始加载搜索引擎快捷方式（静默模式）")
            
            // 临时列表存储转换后的快捷方式
            val newShortcuts = mutableListOf<SearchEngineShortcut>()
            
            // 1. 从SearchEngineManager获取保存的搜索引擎组
            val searchEngineManager = com.example.aifloatingball.manager.SearchEngineManager.getInstance(this)
            val searchEngineGroups = searchEngineManager.getSearchEngineGroups()
            
            // 获取用户启用的搜索引擎组合 - 直接从SharedPreferences获取，而不依赖SettingsManager
            val enabledGroups = getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getStringSet("enabled_search_engine_groups", emptySet()) ?: emptySet()
            
            Log.d(TAG, "从SearchEngineManager获取到 ${searchEngineGroups.size} 个搜索引擎组，其中 ${enabledGroups.size} 个已启用")
            
            // 2. 将启用的搜索引擎组转换为快捷方式
            searchEngineGroups.forEach { group ->
                // 只添加已启用的组或者当启用列表为空时添加所有组（向后兼容）
                if ((enabledGroups.isEmpty() || enabledGroups.contains(group.name)) && group.engines.isNotEmpty()) {
                    // 使用组中的第一个搜索引擎作为主要搜索引擎
                    val primaryEngine = group.engines[0]
                    
                    // 改进URL转换逻辑，确保正确处理不同搜索引擎URL格式
                    val searchUrl = convertToSearchUrl(primaryEngine.searchUrl)
                    
                    // 创建快捷方式对象
                    val shortcut = SearchEngineShortcut(
                        id = group.name.hashCode().toString(),
                        name = group.name,
                        url = primaryEngine.url,
                        searchUrl = searchUrl,
                        domain = extractDomain(primaryEngine.url)
                    )
                    
                    newShortcuts.add(shortcut)
                    Log.d(TAG, "添加搜索引擎组快捷方式: ${shortcut.name}, URL: ${shortcut.searchUrl}")
                }
            }
            
            // 3. 从SharedPreferences获取旧的快捷方式
            val prefs = getSharedPreferences("search_engine_shortcuts", Context.MODE_PRIVATE)
            val gson = Gson()
            val shortcutsJson = prefs.getString("shortcuts", "[]")
            val type = object : TypeToken<List<SearchEngineShortcut>>() {}.type
            val oldShortcuts: List<SearchEngineShortcut> = gson.fromJson(shortcutsJson, type) ?: emptyList()
            
            // 4. 合并新旧快捷方式，避免重复（仅当启用列表为空时才考虑旧快捷方式，向后兼容）
            if (enabledGroups.isEmpty()) {
                oldShortcuts.forEach { oldShortcut ->
                    if (newShortcuts.none { it.domain == oldShortcut.domain && it.name == oldShortcut.name }) {
                        newShortcuts.add(oldShortcut)
                    }
                }
            }
            
            // 5. 添加测试快捷方式（仅当没有实际快捷方式且启用列表为空时）
            if (newShortcuts.isEmpty() && enabledGroups.isEmpty()) {
                val testShortcuts = listOf(
                    SearchEngineShortcut(
                        id = "test_baidu",
                        name = "百度",
                        url = "https://www.baidu.com",
                        searchUrl = "https://www.baidu.com/s?wd={query}",
                        domain = "baidu.com"
                    ),
                    SearchEngineShortcut(
                        id = "test_google",
                        name = "谷歌",
                        url = "https://www.google.com",
                        searchUrl = "https://www.google.com/search?q={query}",
                        domain = "google.com"
                    ),
                    SearchEngineShortcut(
                        id = "test_combo",
                        name = "百度+谷歌",
                        url = "https://www.baidu.com",
                        searchUrl = "https://www.baidu.com/s?wd={query}",
                        domain = "baidu.com"
                    )
                )
                
                newShortcuts.addAll(testShortcuts)
            }
            
            // 6. 更新成员变量
            searchEngineShortcuts = newShortcuts
            
            // 7. 保存合并后的快捷方式
            prefs.edit().putString("shortcuts", gson.toJson(newShortcuts)).apply()
            
            Log.d(TAG, "已加载 ${searchEngineShortcuts.size} 个搜索引擎快捷方式（不自动显示）")
        } catch (e: Exception) {
            Log.e(TAG, "加载搜索引擎快捷方式失败", e)
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
            "${shortcut.name}\n${shortcut.searchUrl}" 
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
        
        // 根据域名智能设置图标
        val iconResId = getIconResourceForDomain(shortcut.domain)
        
        // 设置图标，优先使用本地资源，然后尝试从网络加载
        iconView.setImageResource(iconResId)
        
        // 设置图标背景色 - 更柔和的颜色
        iconView.setBackgroundResource(R.drawable.search_item_background)
        (iconView.background as GradientDrawable).setColor(Color.parseColor("#F5F5F5"))
        
        // 设置名称
        nameView.text = shortcut.name
        nameView.setTextColor(Color.parseColor("#5F6368"))
        
        // 如果有有效的域名，尝试从网络加载图标
        if (shortcut.domain.isNotEmpty() && !shortcut.domain.equals("localhost")) {
            loadIconForDomain(shortcut.domain, iconView, iconResId)
        }
        
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
                openSearchWithEngine(query, shortcut.searchUrl)
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
        searchContainer = floatingView?.findViewById(R.id.search_container)
        appSearchContainer = floatingView?.findViewById(R.id.app_search_container)  // 初始化应用搜索容器

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
        
        // 初始化应用搜索按钮
        initializeAppSearchButtons()
        
        // 确保初始时只显示悬浮球
        ensureOnlyFloatingBallVisible()
    }
    
    // 初始化淘宝APP搜索功能
    private fun initializeTaobaoAppSearch() {
        val taobaoAppSearchButton = floatingView?.findViewById<ImageButton>(R.id.taobao_app_search_button)
        taobaoAppSearchButton?.setOnClickListener {
            val searchQuery = searchInput?.text?.toString() ?: ""
            openTaobaoApp(searchQuery)
        }
    }
    
    // 打开淘宝APP搜索页面
    private fun openTaobaoApp(query: String) {
        try {
            // 编码搜索关键词
            val encodedQuery = Uri.encode(query)
            
            // 构建淘宝搜索页面的scheme链接
            val searchUrl = "taobao://search.taobao.com/search?q=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                setPackage("com.taobao.taobao")  // 指定淘宝包名
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            
            // 成功启动后的操作
            Toast.makeText(this, "正在打开淘宝搜索: $query", Toast.LENGTH_SHORT).show()
            searchContainer?.visibility = View.GONE
            searchInput?.setText("")
            
        } catch (e: Exception) {
            Log.e(TAG, "打开淘宝APP失败: ${e.message}")
            
            // 如果无法打开APP，尝试打开网页版
            try {
                val webUrl = "https://s.taobao.com/search?q=${Uri.encode(query)}"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            } catch (e2: Exception) {
                // 如果连网页版都无法打开，提示安装APP
                Toast.makeText(this, "请确认是否已安装淘宝APP", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 初始化拼多多APP搜索功能
    private fun initializePDDAppSearch() {
        val pddAppSearchButton = floatingView?.findViewById<ImageButton>(R.id.pdd_app_search_button)
        pddAppSearchButton?.setOnClickListener {
            val searchQuery = searchInput?.text?.toString() ?: ""
            openPDDApp(searchQuery)
        }
    }
    
    // 打开拼多多APP搜索页面
    private fun openPDDApp(query: String) {
        try {
            // 编码搜索关键词
            val encodedQuery = Uri.encode(query)
            
            // 构建拼多多搜索页面的scheme链接
            val searchUrl = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                setPackage("com.xunmeng.pinduoduo")  // 指定拼多多包名
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            
            // 成功启动后的操作
            Toast.makeText(this, "正在打开拼多多搜索: $query", Toast.LENGTH_SHORT).show()
            searchContainer?.visibility = View.GONE
            searchInput?.setText("")
            
        } catch (e: Exception) {
            Log.e(TAG, "打开拼多多APP失败: ${e.message}")
            
            // 如果无法打开APP，尝试打开网页版
            try {
                val webUrl = "https://mobile.yangkeduo.com/search_result.html?search_key=${Uri.encode(query)}"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            } catch (e2: Exception) {
                // 如果连网页版都无法打开，提示安装APP
                Toast.makeText(this, "请确认是否已安装拼多多APP", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // 初始化应用搜索按钮
    private fun initializeAppSearchButtons() {
        try {
            // 获取应用搜索设置实例
            val appSearchSettings = AppSearchSettings.getInstance(this)
            
            // 获取应用搜索容器
            val appSearchContainer = floatingView?.findViewById<LinearLayout>(R.id.app_search_container)
            appSearchContainer?.removeAllViews()  // 清除现有的按钮
            
            // 获取已启用的应用配置（已按顺序排序）
            val enabledApps = appSearchSettings.getEnabledAppConfigs()
            
            // 为每个启用的应用创建搜索按钮
            enabledApps.forEach { config ->
                val button = ImageButton(this).apply {
                    id = View.generateViewId()
                    layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx()).apply {
                        setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                    }
                    background = getDrawable(R.drawable.circle_ripple)
                    contentDescription = "${config.appName}搜索"
                    setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    
                    // 设置图标
                    setImageResource(config.iconResId)
                    
                    // 设置点击事件
                    setOnClickListener {
                        val searchQuery = searchInput?.text?.toString()?.trim() ?: ""
                        if (searchQuery.isEmpty()) {
                            Toast.makeText(this@FloatingWindowService, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        
                        // 根据应用ID调用相应的搜索方法
                        when (config.appId) {
                            "wechat" -> openWechatApp(searchQuery)
                            "taobao" -> openTaobaoApp(searchQuery)
                            "pdd" -> openPDDApp(searchQuery)
                            "douyin" -> openDouyinApp(searchQuery)
                            "xiaohongshu" -> openXiaohongshuApp(searchQuery)
                        }
                    }
                }
                
                // 加载应用图标
                loadFaviconForApp(button, getDomainForApp(config.appId), config.iconResId)
                
                // 添加按钮到容器
                appSearchContainer?.addView(button)
            }
            
            Log.d(TAG, "应用搜索按钮初始化完成，共添加 ${enabledApps.size} 个按钮")
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用搜索按钮失败: ${e.message}")
        }
    }
    
    // 辅助方法：dp转px
    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }
    
    // 获取应用对应的域名
    private fun getDomainForApp(appId: String): String {
        return when (appId) {
            "wechat" -> "weixin.qq.com"
            "taobao" -> "taobao.com"
            "pdd" -> "pinduoduo.com"
            "douyin" -> "douyin.com"
            "xiaohongshu" -> "xiaohongshu.com"
            else -> ""
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
        
        // 注销广播接收器
        try {
            unregisterReceiver(shortcutsUpdateReceiver)
            unregisterReceiver(alphaUpdateReceiver)
            unregisterReceiver(appSearchUpdateReceiver)
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "注销广播接收器失败: ${e.message}")
        }
    }

    // 使用特定搜索引擎进行搜索
    private fun openSearchWithEngine(query: String, engineKey: String? = null) {
        try {
            Log.d(TAG, "启动多窗口搜索, 关键词: $query, 引擎: $engineKey")
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("window_count", 3) // 确保始终打开三个窗口
                
                // 处理特定搜索引擎组
                if (engineKey != null) {
                    val isAIEngine = engineKey.startsWith("ai_")
                    putExtra("search_engine", engineKey)
                    
                    // 设置搜索引擎组
                    if (isAIEngine) {
                        putExtra("engine_group", "ai")
            } else {
                        putExtra("engine_group", "web")
                    }
                    
                    Log.d(TAG, "使用特定搜索引擎: $engineKey, 组: ${if (isAIEngine) "ai" else "web"}")
                } else {
                    // 使用默认搜索引擎
                    val defaultEngine = settingsManager.getDefaultSearchEngine()
                    Log.d(TAG, "使用默认搜索引擎: $defaultEngine")
                    putExtra("search_engine", defaultEngine)
                }
            }
            
            startService(intent)
            searchInput?.setText("")
            setSearchModeDismiss()
            
            Log.d(TAG, "多窗口搜索服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动搜索服务失败", e)
            Toast.makeText(this, "搜索启动失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // 使用搜索引擎进行搜索
    private fun searchWithEngine(engineName: String, query: String, isAI: Boolean) {
        try {
            // 编码查询字符串，防止特殊字符问题
            val encodedQuery = Uri.encode(query)
            
            // 使用 DualFloatingWebViewService 打开搜索结果
                val intent = Intent(this, com.example.aifloatingball.DualFloatingWebViewService::class.java).apply {
                // 使用统一的参数名
                putExtra("search_query", query)
                // 修改为默认使用3个窗口
                putExtra("window_count", 3)
                
                // 设置搜索引擎
                val engineKey = if (isAI) {
                    "ai_" + EngineUtil.getEngineKey(engineName)
                } else {
                    EngineUtil.getEngineKey(engineName)
                }
                putExtra("engine_key", engineKey)
                
                // 添加查询字符串和模式信息用于记录
                putExtra("is_ai_mode", isAI)
                putExtra("engine_name", engineName)
            }
            
                startService(intent)
                
            // 清空搜索框
            searchInput?.setText("")
            
            // 隐藏搜索界面
            searchContainer?.visibility = View.GONE
            
            // 显示提示
            val mode = if (isAI) "AI模式" else "普通模式"
            Log.d(TAG, "搜索: $mode, 引擎=$engineName, 查询=$query")
            Toast.makeText(this, "使用 $engineName 搜索: $query", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "搜索失败: ${e.message}")
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
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

    // 切换搜索界面显示/隐藏
    private fun toggleSearchInterface() {
        if (isMenuVisible) {
            // 隐藏搜索界面
            Log.d(TAG, "关闭搜索界面")
            hideSearchInterface()
        } else {
            // 显示搜索界面
            Log.d(TAG, "打开搜索界面")
            showSearchInterface()
        }
    }
    
    // 新增方法：显示搜索界面
    private fun showSearchInterface() {
        try {
            // 记录当前悬浮球位置（用于关闭时恢复）
            initialX = params?.x ?: 0
            initialY = params?.y ?: 0
            
            // 获取屏幕尺寸
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            
            // 获取状态栏高度
            val statusBarHeight = getStatusBarHeight()
            
            // 先将搜索界面设为可见但完全透明
            searchContainer?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                
                // 等待布局完成后进行位置计算和动画
                post {
                    // 计算搜索界面在屏幕上方的位置
                    val topMargin = statusBarHeight + 10 // 状态栏下方10像素
                    val centerX = screenWidth / 2 - width / 2
                    
                    // 设置初始位置和缩放
                    scaleX = 0.85f
                    scaleY = 0.85f
                    
                    // 设置窗口参数
                    params?.apply {
                        x = centerX
                        y = topMargin
                        
                        // 移除FLAG_NOT_FOCUSABLE，允许获取焦点
                        flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        
                        // 设置软键盘调整模式
                        softInputMode = (
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or  // 调整大小以适应键盘
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN      // 初始时隐藏键盘
                        )
                    }
                    
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                        
                        // 应用展开动画
                        animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .withStartAction {
                                // 显示所有需要的UI元素
                                if (isAIMode) {
                                    aiEnginesContainer?.visibility = View.VISIBLE
                                    regularEnginesContainer?.visibility = View.GONE
                                } else {
                                    aiEnginesContainer?.visibility = View.GONE
                                    regularEnginesContainer?.visibility = View.VISIBLE
                                }
                                
                                savedCombosContainer?.visibility = View.VISIBLE
                                appSearchContainer?.visibility = View.VISIBLE
                                searchModeToggle?.visibility = View.VISIBLE
                            }
                            .withEndAction {
                                try {
                                    // 激活输入框和输入法
                                    searchInput?.post {
                                        searchInput?.requestFocus()
                                        showKeyboard(searchInput)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "激活输入框失败: ${e.message}")
                                }
                            }
                            .start()
                    } catch (e: Exception) {
                        Log.e(TAG, "更新搜索界面位置失败: ${e.message}")
                    }
                }
            }
            
            isMenuVisible = true
            
        } catch (e: Exception) {
            Log.e(TAG, "显示搜索界面失败: ${e.message}")
        }
    }
    
    // 修改关闭动画
    private fun hideSearchInterface() {
        try {
            searchContainer?.apply {
                // 从当前位置开始收起动画
                animate()
                    .alpha(0f)
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        // 隐藏所有搜索相关元素
                        visibility = View.GONE
                        shortcutsContainer?.visibility = View.GONE
                        aiEnginesContainer?.visibility = View.GONE
                        regularEnginesContainer?.visibility = View.GONE
                        savedCombosContainer?.visibility = View.GONE
                        searchModeToggle?.visibility = View.GONE
                        appSearchContainer?.visibility = View.GONE
                        
                        // 清空输入框内容
                        searchInput?.setText("")
                        
                        // 隐藏键盘
                        hideKeyboard()
                        
                        // 恢复悬浮球原始位置和参数
                        params?.apply {
                            x = initialX
                            y = initialY
                            
                            // 恢复原始标志
                            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            
                            // 重置软键盘模式
                            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                        }
                        
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                            Log.d(TAG, "搜索界面已关闭，悬浮球位置已恢复: x=$initialX, y=$initialY")
                            
                            // 确保悬浮球在安全区域内
                            ensureBallInSafeArea()
                        } catch (e: Exception) {
                            Log.e(TAG, "更新布局失败: ${e.message}")
                        }
                        
                        // 重置菜单状态标志
                        isMenuVisible = false
                    }
                    .start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭搜索界面失败: ${e.message}")
        }
    }
    
    // 移除不再需要的计算菜单位置方法
    private fun calculateMenuX(isAtEdge: Boolean = false): Int {
        // 不再使用，由optimallyPositionSearchInterface替代
        return params?.x ?: 0
    }
    
    private fun calculateMenuY(statusBarHeight: Int = getStatusBarHeight()): Int {
        // 不再使用，由optimallyPositionSearchInterface替代
        return params?.y ?: statusBarHeight
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
        val domain: String
        
        if (isAI && engine is com.example.aifloatingball.model.AISearchEngine) {
            name = engine.name
            iconResId = engine.iconResId
            url = engine.url
            domain = extractDomain(url)
        } else if (!isAI && engine is com.example.aifloatingball.model.SearchEngine) {
            name = engine.name
            iconResId = engine.iconResId
            url = engine.url
            domain = extractDomain(url)
        } else {
            name = "未知"
            iconResId = R.drawable.ic_search
            url = ""
            domain = ""
        }
        
        // 创建视图
        val view = View.inflate(this, R.layout.search_engine_shortcut, null)
        
        val iconView = view.findViewById<ImageView>(R.id.shortcut_icon)
        val nameView = view.findViewById<TextView>(R.id.shortcut_name)
        
        // 优先使用引擎指定的图标，如果不存在则使用域名推断
        val finalIconResId = if (iconResId != 0) {
            iconResId
        } else {
            getIconResourceForDomain(domain)
        }
        
        // 设置图标
        iconView.setImageResource(finalIconResId)
        
        // 设置图标背景色 - 更统一的风格
        val backgroundColor = if (isAI) "#EDE7F6" else "#E8F5E9"
        iconView.setBackgroundResource(R.drawable.search_item_background)
        (iconView.background as GradientDrawable).setColor(Color.parseColor(backgroundColor))
        
        // 如果有有效的域名，尝试从网络加载图标
        if (domain.isNotEmpty() && !domain.equals("localhost")) {
            loadIconForDomain(domain, iconView, finalIconResId)
        }
        
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
    
    // 使用Glide库加载网站图标
    private fun loadIconForDomain(domain: String, imageView: ImageView, fallbackResId: Int) {
        try {
            // 如果域名为空或无效，直接使用备用图标
            if (domain.isEmpty() || domain == "localhost") {
                imageView.setImageResource(fallbackResId)
                return
            }
            
            // 准备favicon URL
            val faviconUrl = "https://www.google.com/s2/favicons?sz=64&domain_url=https://$domain"
            
            // 使用Glide加载图标 - 自动处理缓存和线程
            Glide.with(applicationContext)  // 使用applicationContext避免内存泄漏
                .load(faviconUrl)
                .placeholder(fallbackResId) // 加载过程中显示
                .error(fallbackResId) // 加载失败时显示
                .fallback(fallbackResId) // URL为null时显示
                .timeout(5000) // 设置超时时间
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        try {
                            // 确保ImageView仍然有效
                            if (imageView.isAttachedToWindow) {
                                imageView.setImageDrawable(resource)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "设置图标失败: ${e.message}")
                            imageView.setImageResource(fallbackResId)
                        }
                    }
                    
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        // 当资源被清除时回调
                        try {
                            imageView.setImageResource(fallbackResId)
                        } catch (e: Exception) {
                            Log.e(TAG, "清除图标失败: ${e.message}")
                        }
                    }
                })
            
            Log.d(TAG, "请求加载图标: $domain")
        } catch (e: Exception) {
            Log.e(TAG, "图标加载初始化失败: ${e.message}")
            // 出错时直接显示备用图标
            try {
                imageView.setImageResource(fallbackResId)
            } catch (ex: Exception) {
                Log.e(TAG, "备用图标设置失败: ${ex.message}")
            }
        }
    }

    // 根据域名获取正确的图标资源
    private fun getIconResourceForDomain(domain: String): Int {
        // 尝试使用EngineUtil获取图标
        val iconRes = EngineUtil.getIconResourceByDomain(domain)
        if (iconRes != 0 && iconRes != R.drawable.ic_search) {
            return iconRes
        }
        
        // 如果EngineUtil没有找到，使用自定义映射
        return when {
            domain.contains("baidu") -> R.drawable.ic_baidu
            domain.contains("google") -> R.drawable.ic_google
            domain.contains("bing") -> R.drawable.ic_bing
            domain.contains("sougou") || domain.contains("sogou") -> R.drawable.ic_sogou
            domain.contains("360") -> R.drawable.ic_360
            domain.contains("yandex") -> R.drawable.ic_search // 使用通用搜索图标替代不存在的ic_yandex
            domain.contains("yahoo") -> R.drawable.ic_search // 使用通用搜索图标替代不存在的ic_yahoo
            domain.contains("duckduckgo") -> R.drawable.ic_duckduckgo
            domain.contains("zhihu") -> R.drawable.ic_zhihu
            domain.contains("taobao") || domain.contains("tmall") -> R.drawable.ic_taobao
            domain.contains("jd") -> R.drawable.ic_jd
            domain.contains("douyin") || domain.contains("tiktok") -> R.drawable.ic_douyin
            domain.contains("bilibili") -> R.drawable.ic_bilibili
            domain.contains("youtube") -> R.drawable.ic_search // 使用通用搜索图标替代不存在的ic_youtube
            domain.contains("chatgpt") || domain.contains("openai") -> R.drawable.ic_chatgpt
            domain.contains("claude") || domain.contains("anthropic") -> R.drawable.ic_claude
            domain.contains("gemini") || domain.contains("bard") -> R.drawable.ic_gemini
            domain.contains("wenxin") || domain.contains("baichuan") -> R.drawable.ic_wenxin
            else -> R.drawable.ic_search // 使用通用搜索图标替代不存在的ic_globe
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

    // 确保只显示悬浮球
    private fun ensureOnlyFloatingBallVisible() {
        // 确保所有搜索相关的视图都被隐藏
        searchContainer?.visibility = View.GONE
        shortcutsContainer?.visibility = View.GONE
        savedCombosContainer?.visibility = View.GONE
        aiEnginesContainer?.visibility = View.GONE
        regularEnginesContainer?.visibility = View.GONE
        searchModeToggle?.visibility = View.GONE
        appSearchContainer?.visibility = View.GONE  // 隐藏应用搜索容器
        
        // 确保菜单状态标志为关闭
        isMenuVisible = false
        
        // 使窗口不可聚焦，确保不会意外地打开输入法
        params?.flags = (params?.flags ?: 0).or(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        
        windowManager?.updateViewLayout(floatingView, params)
        
        // 加载保存的位置并更新悬浮球位置
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        params?.x = prefs.getInt("last_x", 0)
        
        // 确保初始Y坐标不覆盖状态栏
        val statusBarHeight = getStatusBarHeight()
        val savedY = prefs.getInt("last_y", statusBarHeight + 20)
        params?.y = if (savedY < statusBarHeight) statusBarHeight else savedY
        
        windowManager?.updateViewLayout(floatingView, params)
        
        // 确保悬浮球在安全区域内
        ensureBallInSafeArea()
        
        // 隐藏应用搜索容器
        val appSearchContainer = floatingView?.findViewById<LinearLayout>(R.id.app_search_container)
        appSearchContainer?.visibility = View.GONE
    }

    // 加载已保存的搜索引擎快捷方式
    private fun loadSearchEngineShortcuts() {
        // 加载快捷方式
        loadSearchEngineShortcutsQuietly()
        
        // 显示搜索引擎快捷方式
        displaySearchEngineShortcuts()
        
        // 更新搜索引擎显示
        updateSearchEngineDisplay()
        
        Log.d(TAG, "搜索引擎快捷方式已加载并显示")
    }

    // 增加变量记录外观状态
    private var isAlternateAppearance = false

    // 添加点击外部关闭搜索菜单的功能
    private fun setupOutsideTouchListener() {
        // 监听外部触摸事件
        floatingView?.let { view ->
            // 获取搜索容器和悬浮球图标
            val searchContainer = view.findViewById<LinearLayout>(R.id.search_container)
            val floatingBallIcon = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floating_ball_icon)
            
            // 监听未处理的触摸事件
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN && isMenuVisible) {
                    // 检查触摸点是否在搜索容器或悬浮球图标内
                    val isTouchOnSearchContainer = isTouchOnView(event, searchContainer)
                    val isTouchOnFloatingBall = isTouchOnView(event, floatingBallIcon)
                    
                    // 如果触摸点不在搜索容器和悬浮球图标内，关闭搜索界面
                    if (!isTouchOnSearchContainer && !isTouchOnFloatingBall) {
                        hideSearchInterface()
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
    }
    
    // 检查触摸点是否在指定视图内
    private fun isTouchOnView(event: MotionEvent, view: View?): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        
        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height
        
        return event.rawX >= left && event.rawX <= right && 
               event.rawY >= top && event.rawY <= bottom
    }

    private fun showSelectionHandles(webView: WebView) {
        // 创建开始和结束控制柄
        val startHandle = createSelectionHandle(true) as ImageView
        val endHandle = createSelectionHandle(false) as ImageView
        
        // 添加控制柄到窗口
        val handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        // 获取选择范围
        webView.evaluateJavascript("""
            (function() {
                const selection = window.getSelection();
                const range = selection.getRangeAt(0);
                const startRect = range.getBoundingClientRect();
                const endRect = range.getBoundingClientRect();
                return {
                    start: { x: startRect.left, y: startRect.top },
                    end: { x: endRect.right, y: endRect.bottom }
                };
            })();
        """) { result ->
            // 解析结果并定位控制柄
            val positions = parseSelectionPositions(result)
            positions?.let { (start, end) ->
                positionHandle(startHandle, start.first, start.second)
                positionHandle(endHandle, end.first, end.second)
            }
        }
    }

    private fun createSelectionHandle(isStart: Boolean): View {
        val handle = ImageView(this).apply {
            setImageResource(if (isStart) R.drawable.text_select_handle_left else R.drawable.text_select_handle_right)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return handle
    }

    private fun handleTextSelectionFocus() {
        // 临时允许窗口获取焦点
        toggleWindowFocusableFlag(true)
        
        // 设置定时器，在一段时间后恢复不可获取焦点状态
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isTextSelectionActive) {
                toggleWindowFocusableFlag(false)
            }
        }, 5000) // 5秒后恢复
    }

    private fun setupTouchHandling(webView: WebView) {
        webView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 检查是否在控制柄上
                    if (isOnHandle(event.x, event.y)) {
                        isDraggingHandle = true
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingHandle) {
                        // 更新选择范围
                        updateSelectionRange(event.x, event.y)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isDraggingHandle = false
                }
            }
            false
        }
    }

    private val menuAutoHideHandler = Handler(Looper.getMainLooper())

    // 初始化抖音APP搜索功能
    private fun initializeDouyinAppSearch() {
        val douyinAppSearchButton = floatingView?.findViewById<ImageButton>(R.id.douyin_app_search_button)
        douyinAppSearchButton?.setOnClickListener {
            val searchQuery = searchInput?.text?.toString() ?: ""
            openDouyinApp(searchQuery)
        }
    }
    
    // 打开抖音APP搜索页面
    private fun openDouyinApp(query: String) {
        try {
            // 编码搜索关键词
            val encodedQuery = Uri.encode(query)
            
            // 构建抖音搜索页面的scheme链接
            val searchUrl = "snssdk1128://search?keyword=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                setPackage("com.ss.android.ugc.aweme")  // 抖音包名
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            
            // 成功启动后的操作
            Toast.makeText(this, "正在打开抖音搜索: $query", Toast.LENGTH_SHORT).show()
            searchContainer?.visibility = View.GONE
            searchInput?.setText("")
            
        } catch (e: Exception) {
            Log.e(TAG, "打开抖音APP失败: ${e.message}")
            
            // 如果无法打开APP，尝试打开网页版
            try {
                // 重新编码查询字符串，确保在这个作用域中可用
                val encodedQuery = Uri.encode(query)
                val webUrl = "https://www.douyin.com/search/$encodedQuery"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            } catch (e2: Exception) {
                // 如果连网页版都无法打开，提示安装APP
                Toast.makeText(this, "请确认是否已安装抖音APP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 为APP按钮加载favicon
    private fun loadFaviconForApp(imageButton: ImageButton, domain: String, fallbackBackground: Int) {
        try {
            // 使用Google的favicon服务
            val faviconUrls = listOf(
                "https://www.google.com/s2/favicons?sz=64&domain_url=https://$domain"
            )
            
            // 使用Glide加载多个源
            val requestBuilder = Glide.with(applicationContext)
                .load(faviconUrls[0]) // 首选移动版网站图标
            
            // 添加备用URL
            faviconUrls.drop(1).forEach { url ->
                requestBuilder.error(
                    Glide.with(applicationContext)
                        .load(url)
                )
            }
            
            // 配置加载选项和转换
            requestBuilder
                .placeholder(android.R.drawable.ic_menu_search)
                .fallback(android.R.drawable.ic_menu_search)
                .timeout(3000) // 缩短超时时间以加快降级速度
                .transform(
                    // 添加圆形裁剪
                    com.bumptech.glide.load.resource.bitmap.CircleCrop(),
                    // 自定义图标处理
                    object : com.bumptech.glide.load.resource.bitmap.BitmapTransformation() {
                        override fun transform(
                            pool: com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool,
                            toTransform: android.graphics.Bitmap,
                            outWidth: Int,
                            outHeight: Int
                        ): android.graphics.Bitmap {
                            // 创建一个新的Bitmap，带白色背景
                            val result = pool.get(outWidth, outHeight, android.graphics.Bitmap.Config.ARGB_8888)
                            result.eraseColor(android.graphics.Color.WHITE)
                            
                            val canvas = android.graphics.Canvas(result)
                            val paint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                isFilterBitmap = true
                                isDither = true  // 添加抖动效果，改善渐变
                            }
                            
                            // 计算缩放比例
                            val scale = 0.7f
                            
                            val scaledWidth = toTransform.width * scale
                            val scaledHeight = toTransform.height * scale
                            val left = (outWidth - scaledWidth) / 2f
                            val top = (outHeight - scaledHeight) / 2f
                            
                            // 定义源矩形和目标矩形
                            val srcRect = android.graphics.Rect(0, 0, toTransform.width, toTransform.height)
                            val dstRect = android.graphics.RectF(
                                left, top,
                                left + scaledWidth,
                                top + scaledHeight
                            )
                            
                            // 绘制图标
                            canvas.drawBitmap(toTransform, srcRect, dstRect, paint)
                            
                            return result
                        }

                        override fun updateDiskCacheKey(messageDigest: java.security.MessageDigest) {
                            messageDigest.update("favicon_transform_v3".toByteArray())
                        }
                    }
                )
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        try {
                            if (imageButton.isAttachedToWindow) {
                                // 直接设置图标，不需要特殊处理
                                imageButton.setImageDrawable(resource)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "设置APP图标失败: ${e.message}")
                            imageButton.setImageResource(android.R.drawable.ic_menu_search)
                        }
                    }
                    
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        try {
                            imageButton.setImageResource(android.R.drawable.ic_menu_search)
                        } catch (e: Exception) {
                            Log.e(TAG, "清除APP图标失败: ${e.message}")
                        }
                    }
                })
            
            Log.d(TAG, "请求加载APP图标: $domain")
        } catch (e: Exception) {
            Log.e(TAG, "APP图标加载初始化失败: ${e.message}")
            try {
                imageButton.setImageResource(android.R.drawable.ic_menu_search)
            } catch (ex: Exception) {
                Log.e(TAG, "设置默认图标失败: ${ex.message}")
            }
        }
    }

    // 打开小红书APP搜索页面
    private fun openXiaohongshuApp(query: String) {
        try {
            // 编码搜索关键词
            val encodedQuery = Uri.encode(query)
            
            // 构建小红书搜索页面的scheme链接
            val searchUrl = "xhsdiscover://search/result?keyword=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                setPackage("com.xingin.xhs")  // 小红书包名
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            
            // 成功启动后的操作
            Toast.makeText(this, "正在打开小红书搜索: $query", Toast.LENGTH_SHORT).show()
            searchContainer?.visibility = View.GONE
            searchInput?.setText("")
            
        } catch (e: Exception) {
            Log.e(TAG, "打开小红书APP失败: ${e.message}")
            
            // 如果无法打开APP，尝试打开网页版
            try {
                val webUrl = "https://www.xiaohongshu.com/search_result?keyword=${Uri.encode(query)}"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            } catch (e2: Exception) {
                // 如果连网页版都无法打开，提示安装APP
                Toast.makeText(this, "请确认是否已安装小红书APP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 打开微信APP搜索页面
    private fun openWechatApp(query: String) {
        try {
            // 首先尝试使用基础scheme启动微信
            try {
                val baseScheme = "weixin://"
                val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(baseScheme))
                searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(searchIntent)
                
                // 启动成功后，延迟复制搜索词到剪贴板
                Handler(Looper.getMainLooper()).postDelayed({
                    // 复制搜索词到剪贴板
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("搜索关键词", query)
                    clipboard.setPrimaryClip(clip)
                    
                    Toast.makeText(this, "搜索词已复制，请在微信中粘贴搜索", Toast.LENGTH_LONG).show()
                }, 500) // 延迟500毫秒复制
                
            } catch (e: Exception) {
                Log.e(TAG, "微信scheme启动失败: ${e.message}")
                
                // 如果scheme失败，尝试直接启动微信
                val launchIntent = packageManager.getLaunchIntentForPackage("com.tencent.mm")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    
                    // 延迟复制搜索词
                    Handler(Looper.getMainLooper()).postDelayed({
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("搜索关键词", query)
                        clipboard.setPrimaryClip(clip)
                        
                        Toast.makeText(this, "搜索词已复制，请在微信中粘贴搜索", Toast.LENGTH_LONG).show()
                    }, 500)
                } else {
                    Toast.makeText(this, "未安装微信或无法启动微信", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "微信启动失败: ${e.message}")
            Toast.makeText(this, "无法启动微信", Toast.LENGTH_SHORT).show()
        }
        
        // 隐藏搜索界面
        searchContainer?.visibility = View.GONE
        searchInput?.setText("")
    }
    
    // 检查微信是否已安装
    private fun isWechatInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.tencent.mm", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }
    }
}