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

class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastClickTime: Long = 0
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "FloatingBallChannel"
    private val DOUBLE_CLICK_TIME = 300L
    private val SNAP_DISTANCE = 50
    private val screenWidth by lazy { windowManager?.defaultDisplay?.width ?: 0 }
    private val screenHeight by lazy { windowManager?.defaultDisplay?.height ?: 0 }
    
    // 添加搜索引擎快捷方式相关变量
    private var shortcutsContainer: LinearLayout? = null
    private var searchInput: EditText? = null
    private var searchEngineShortcuts: List<SearchEngineShortcut> = emptyList()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initializeWindowManager()
        createFloatingWindow()
        initializeViews()
        loadSearchEngineShortcuts()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("last_x", 0)
            y = prefs.getInt("last_y", 100)
            this.alpha = alphaValue
        }
    }

    private fun createFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball_layout, null)
        
        // 初始化搜索引擎快捷方式容器
        shortcutsContainer = floatingView?.findViewById(R.id.search_shortcuts_container)
        
        // 设置搜索框行为
        setupSearchInput()
        
        longPressRunnable = Runnable {
            // Handle long press
            openSettings()
        }
        
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    // Handle double click
                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME) {
                        onDoubleClick()
                        return@setOnTouchListener true
                    }
                    lastClickTime = clickTime
                    
                    // Start long press detection
                    handler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (abs(deltaX) > ViewConfiguration.get(this).scaledTouchSlop ||
                        abs(deltaY) > ViewConfiguration.get(this).scaledTouchSlop) {
                        handler.removeCallbacks(longPressRunnable!!)
                    }
                    
                    params?.x = initialX + deltaX
                    params?.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable!!)
                    
                    val moved = abs(event.rawX - initialTouchX) > ViewConfiguration.get(this).scaledTouchSlop ||
                               abs(event.rawY - initialTouchY) > ViewConfiguration.get(this).scaledTouchSlop
                    
                    if (!moved) {
                        openSearchEngine()
                    } else {
                        snapToEdge()
                        savePosition()
                    }
                }
            }
            true
        }
        windowManager?.addView(floatingView, params)
    }

    private fun snapToEdge() {
        params?.let { p ->
            when {
                p.x < SNAP_DISTANCE -> p.x = 0
                p.x > screenWidth - p.width - SNAP_DISTANCE -> p.x = screenWidth - p.width
            }
            windowManager?.updateViewLayout(floatingView, params)
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
        val searchIcon = floatingView?.findViewById<ImageView>(R.id.floating_ball_icon)
        
        // 设置输入框搜索动作
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
        
        // 设置搜索图标点击事件
        searchIcon?.setOnClickListener {
            val query = searchInput?.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            }
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
        val prefs = getSharedPreferences("search_engine_shortcuts", Context.MODE_PRIVATE)
        val gson = Gson()
        val shortcutsJson = prefs.getString("shortcuts", "[]")
        val type = object : TypeToken<List<SearchEngineShortcut>>() {}.type
        searchEngineShortcuts = gson.fromJson(shortcutsJson, type) ?: emptyList()
        
        // 显示快捷方式
        displaySearchEngineShortcuts()
    }

    // 显示搜索引擎快捷方式
    private fun displaySearchEngineShortcuts() {
        shortcutsContainer?.removeAllViews()
        
        searchEngineShortcuts.forEach { shortcut ->
            // 创建快捷方式视图
            val shortcutView = LayoutInflater.from(this).inflate(
                R.layout.item_search_engine_shortcut,
                shortcutsContainer,
                false
            )
            
            // 设置图标和名称
            val iconView = shortcutView.findViewById<ImageView>(R.id.shortcut_icon)
            val nameView = shortcutView.findViewById<TextView>(R.id.shortcut_name)
            
            // 加载favicon（如果有）
            val faviconFile = File(filesDir, "favicon_${shortcut.id}.png")
            if (faviconFile.exists()) {
                val favicon = BitmapFactory.decodeFile(faviconFile.absolutePath)
                iconView.setImageBitmap(favicon)
            } else {
                iconView.setImageResource(R.drawable.ic_web_default)
            }
            
            nameView.text = shortcut.name
            
            // 设置点击事件
            shortcutView.setOnClickListener {
                val query = searchInput?.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    openSearchWithEngine(shortcut, query)
                } else {
                    Toast.makeText(this, R.string.please_input_search_content, Toast.LENGTH_SHORT).show()
                }
            }
            
            shortcutsContainer?.addView(shortcutView)
        }
    }
    
    // 使用特定搜索引擎进行搜索
    private fun openSearchWithEngine(shortcut: SearchEngineShortcut, query: String) {
        val searchUrl = if (query.isEmpty()) {
            // 如果没有查询内容，打开搜索引擎主页
            shortcut.url.replace("{query}", "")
                .replace("search?q=", "")
                .replace("search?query=", "")
                .replace("search?word=", "")
                .replace("s?wd=", "")
        } else {
            // 有查询内容，进行搜索
            shortcut.url.replace("{query}", Uri.encode(query))
        }

        // 启动 FloatingWebViewService 来加载搜索结果
        val intent = Intent(this, FloatingWebViewService::class.java).apply {
            putExtra("url", searchUrl)
        }
        startService(intent)
    }

    private fun initializeViews() {
        // 初始化搜索引擎快捷方式容器
        shortcutsContainer = floatingView?.findViewById(R.id.shortcuts_container)
        searchInput = floatingView?.findViewById(R.id.searchInput)

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
    }
}