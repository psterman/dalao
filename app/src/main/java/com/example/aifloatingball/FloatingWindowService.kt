package com.example.aifloatingball

import android.Manifest
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.gesture.GestureManager
import com.example.aifloatingball.menu.QuickMenuManager
import com.example.aifloatingball.search.SearchHistoryAdapter
import com.example.aifloatingball.search.SearchHistoryManager
import com.example.aifloatingball.utils.SystemSettingsHelper
import com.example.aifloatingball.web.CustomWebViewClient

class FloatingWindowService : Service(), GestureManager.GestureCallback {
    private var windowManager: WindowManager? = null
    private var floatingBallView: View? = null
    private var searchView: View? = null
    private val aiWindows = mutableListOf<WebView>()
    private lateinit var gestureManager: GestureManager
    private lateinit var quickMenuManager: QuickMenuManager
    private lateinit var systemSettingsHelper: SystemSettingsHelper
    private var screenWidth = 0
    private var screenHeight = 0
    private lateinit var searchHistoryManager: SearchHistoryManager
    private var currentEngineIndex = 0
    
    private val speechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(this)
    }
    
    private var isCardViewMode = false
    private var cardStartY = 0f
    private var cardStartX = 0f
    private var currentCardIndex = 0
    private var cardSpacing = 0  // 将在 onCreate 中初始化
    private var cardRotation = 10f
    private var cardScale = 0.9f
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        try {
            Log.d("FloatingService", "服务开始创建")
        super.onCreate()
            
            // 检查必要的权限
            if (!Settings.canDrawOverlays(this)) {
                Log.e("FloatingService", "没有悬浮窗权限")
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            
            // 启动前台服务（先启动前台服务，避免ANR）
            try {
                startForeground()
                Log.d("FloatingService", "前台服务启动成功")
            } catch (e: Exception) {
                Log.e("FloatingService", "前台服务启动失败", e)
                Toast.makeText(this, "前台服务启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            
            // 初始化窗口管理器
            try {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                if (windowManager == null) {
                    throw IllegalStateException("无法获取WindowManager服务")
                }
                Log.d("FloatingService", "窗口管理器初始化成功")
            } catch (e: Exception) {
                Log.e("FloatingService", "窗口管理器初始化失败", e)
                Toast.makeText(this, "窗口管理器初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
        
            // 获取屏幕尺寸
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = windowManager?.currentWindowMetrics
                windowMetrics?.bounds?.let {
                    screenWidth = it.width()
                    screenHeight = it.height()
                }
        } else {
                val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
            }
            
            if (screenWidth == 0 || screenHeight == 0) {
                throw IllegalStateException("无法获取屏幕尺寸")
            }
            
            Log.d("FloatingService", "屏幕尺寸: ${screenWidth}x${screenHeight}")
            
            // 初始化所有需要 Context 的变量
            cardSpacing = 60.dpToPx()
            
            // 初始化管理器和帮助类（注意初始化顺序）
            try {
        systemSettingsHelper = SystemSettingsHelper(this)
                searchHistoryManager = SearchHistoryManager(this)
                gestureManager = GestureManager(this, this)
        quickMenuManager = QuickMenuManager(this, windowManager!!, systemSettingsHelper)
                Log.d("FloatingService", "所有管理器初始化成功")
            } catch (e: Exception) {
                Log.e("FloatingService", "初始化管理器失败", e)
                throw e
            }
            
            // 创建并显示悬浮球
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    createFloatingBall()
                } catch (e: Exception) {
                    Log.e("FloatingService", "延迟创建悬浮球失败", e)
                    Toast.makeText(this, "创建悬浮球失败: ${e.message}", Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }, 500) // 延迟500ms创建悬浮球，确保系统准备就绪
            
            Log.d("FloatingService", "服务创建完成")
        } catch (e: Exception) {
            Log.e("FloatingService", "服务创建失败", e)
            Toast.makeText(this, "服务创建失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }
    
    private fun startForeground() {
        val channelId = "floating_ball_service"
        val channelName = "AI悬浮球服务"
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "用于保持悬浮球服务在后台运行"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("AI悬浮球")
                .setContentText("正在运行中...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            
            startForeground(1, notification)
            Log.d("FloatingService", "前台服务启动成功")
        } catch (e: Exception) {
            Log.e("FloatingService", "前台服务启动失败", e)
            throw e
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingService", "onStartCommand被调用")
        return START_STICKY
    }
    
    private fun createFloatingBall() {
        try {
            Log.d("FloatingService", "开始创建悬浮球")
            
            // 创建悬浮球视图
        floatingBallView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        
            if (floatingBallView == null) {
                Log.e("FloatingService", "悬浮球视图创建失败")
                throw IllegalStateException("无法创建悬浮球视图")
            }
            
            // 设置悬浮球参数
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = PixelFormat.RGBA_8888
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.START or Gravity.TOP
                x = screenWidth - 100.dpToPx()  // 初始位置在屏幕右侧
                y = screenHeight / 2            // 初始位置在屏幕中间
            }
            
            Log.d("FloatingService", "准备添加悬浮球到窗口，位置: x=${params.x}, y=${params.y}")
            
            // 添加悬浮球到窗口
            windowManager?.addView(floatingBallView, params)
            
            // 设置手势监听
            floatingBallView?.let { view ->
                gestureManager.attachToView(view)
                Log.d("FloatingService", "已设置手势监听")
            }
            
            // 设置拖动监听
            setupWindowManagement()
            
            Log.d("FloatingService", "悬浮球创建成功")
            
            // 添加显示动画
            floatingBallView?.alpha = 0f
            floatingBallView?.animate()
                ?.alpha(1f)
                ?.setDuration(500)
                ?.start()
            
        } catch (e: Exception) {
            Log.e("FloatingService", "创建悬浮球失败", e)
            Toast.makeText(this, "创建悬浮球失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }
    
    // 工具方法
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun showSearchInput() {
        searchView = LayoutInflater.from(this).inflate(R.layout.search_input_layout, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,  // 移除 FLAG_ALT_FOCUSABLE_IM
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP  // 固定在顶部
            flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()  // 清除 NOT_FOCUSABLE 标志
        }
        
        setupVoiceInput()
        
        // 获取输入框并设置焦点
        val searchInput = searchView?.findViewById<EditText>(R.id.search_input)
        searchInput?.apply {
            requestFocus()
            postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
        
        // 添加点击外部区域关闭搜索框的功能
        searchView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                windowManager?.removeView(searchView)
                searchView = null
                true
            } else {
                false
            }
        }
        
        searchView?.findViewById<ImageButton>(R.id.search_button)?.setOnClickListener {
            val query = searchInput?.text.toString()
            if (query.isNotEmpty()) {
                windowManager?.removeView(searchView)
                searchView = null
                // 显示加载提示
                Toast.makeText(this, "正在加载搜索结果...", Toast.LENGTH_SHORT).show()
                // 在后台线程中执行搜索
                Handler(Looper.getMainLooper()).post {
            performSearch(query)
                }
            }
        }
        
        windowManager?.addView(searchView, params)
    }
    
    private fun setupVoiceInput() {
        val voiceButton = searchView?.findViewById<ImageButton>(R.id.voice_input_button)
        val searchInput = searchView?.findViewById<EditText>(R.id.search_input)
        
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    searchInput?.setText(matches[0])
                }
            }
            
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(this@FloatingWindowService, "语音识别失败，请重试", Toast.LENGTH_SHORT).show()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        voiceButton?.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                Toast.makeText(this, "请在设置中授予录音权限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要搜索的内容")
        }
        
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "语音识别初始化失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun performSearch(query: String) {
        searchHistoryManager.addSearchQuery(query)
        // 清除之前的搜索结果
        aiWindows.forEach { webView ->
            val parent = webView.parent as? View
            windowManager?.removeView(parent)
        }
        aiWindows.clear()
        
        val engines = listOf(
            "https://deepseek.com/search?q=",
            "https://www.doubao.com/search?q=",
            "https://kimi.moonshot.cn/search?q="
        )
        
        // 使用协程或线程池来并行加载
        engines.forEachIndexed { index, engine ->
            Handler(Looper.getMainLooper()).postDelayed({
            createAIWindow(engine + query, index)
            }, index * 100L)  // 错开一点时间加载，避免同时创建造成卡顿
        }
    }
    
    private fun createAIWindow(url: String, index: Int) {
        val webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 启用缓存
                cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                // 启用DOM存储API
                domStorageEnabled = true
                // 启用数据库存储API
                databaseEnabled = true
                // 启用地理位置
                setGeolocationEnabled(true)
                // 设置UA
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                
                // 启用混合内容
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                // 设置默认编码
                defaultTextEncodingName = "UTF-8"
                
                // 允许文件访问
                allowFileAccess = true
                
                // 启用 JavaScript 接口
                javaScriptCanOpenWindowsAutomatically = true
            }
            
            // 设置WebView背景透明
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            // 设置WebViewClient
            webViewClient = CustomWebViewClient()
            
            // 加载URL
            loadUrl(url)
        }
        
        // 计算每个窗口的高度（屏幕高度的1/3）
        val windowHeight = screenHeight / 3
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            windowHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,  // 移除其他标志
            PixelFormat.TRANSLUCENT
        ).apply {
            // 设置窗口位置
            gravity = Gravity.TOP
            y = index * windowHeight
            
            // 添加一些边距
            horizontalMargin = 0.02f  // 屏幕宽度的2%
            
            // 设置窗口透明度
            alpha = 0.95f
        }
        
        // 添加关闭按钮
        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setOnClickListener {
                closeWindow(webView)
            }
        }
        
        // 创建一个包含WebView和关闭按钮的容器
        val container = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(closeButton, FrameLayout.LayoutParams(
                48.dpToPx(),
                48.dpToPx(),
                Gravity.TOP or Gravity.END
            ))
        }
        
        aiWindows.add(webView)
        windowManager?.addView(container, params)
        
        // 添加显示动画
        container.alpha = 0f
        container.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    private fun setupWindowManagement() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingBallView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as WindowManager.LayoutParams).x
                    initialY = (view.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun minimizeWindow(webView: WebView) {
        val parent = webView.parent as? View
        val params = parent?.layoutParams as? WindowManager.LayoutParams ?: return
        params.height = (screenHeight / 4)
        windowManager?.updateViewLayout(parent, params)
    }
    
    private fun maximizeWindow(webView: WebView) {
        val parent = webView.parent as? View
        val params = parent?.layoutParams as? WindowManager.LayoutParams ?: return
        params.height = screenHeight / 3
        windowManager?.updateViewLayout(parent, params)
    }
    
    private fun closeWindow(webView: WebView) {
        val parent = webView.parent as? View
        if (parent != null) {
            // 添加关闭动画
            parent.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    windowManager?.removeView(parent)
                    aiWindows.remove(webView)
                }
                .start()
        } else {
        windowManager?.removeView(webView)
        aiWindows.remove(webView)
        }
    }
    
    // 重复上一次搜索
    private fun repeatLastSearch() {
        searchHistoryManager.getLastQuery()?.let { query ->
            performSearch(query)
        }
    }
    
    // 切换到下一个搜索结果
    private fun switchToNextEngine() {
        if (aiWindows.isEmpty()) return
        
        currentEngineIndex = (currentEngineIndex + 1) % aiWindows.size
        bringEngineToFront(currentEngineIndex)
    }
    
    // 切换到上一个搜索结果
    private fun switchToPreviousEngine() {
        if (aiWindows.isEmpty()) return
        
        currentEngineIndex = if (currentEngineIndex > 0) {
            currentEngineIndex - 1
        } else {
            aiWindows.size - 1
        }
        bringEngineToFront(currentEngineIndex)
    }
    
    private fun bringEngineToFront(index: Int) {
        if (index < 0 || index >= aiWindows.size) return
        
        aiWindows[index].let { webView ->
            val parent = webView.parent as? View ?: return
            // 将当前窗口置于顶层
            try {
                windowManager?.removeView(parent)
                windowManager?.addView(parent, parent.layoutParams)
            
            // 添加切换动画
                parent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.window_show))
            } catch (e: Exception) {
                Log.e("FloatingService", "切换窗口失败", e)
            }
        }
    }
    
    // 关闭当前搜索结果
    private fun closeCurrentEngine() {
        if (aiWindows.isEmpty()) return
        
        aiWindows.getOrNull(currentEngineIndex)?.let { webView ->
            val animation = AnimationUtils.loadAnimation(this, R.anim.window_hide)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    windowManager?.removeView(webView)
                    aiWindows.remove(webView)
                    if (aiWindows.isNotEmpty()) {
                        currentEngineIndex = currentEngineIndex.coerceIn(0, aiWindows.size - 1)
                    }
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            webView.startAnimation(animation)
        }
    }
    
    // 显示搜索历史
    private fun showSearchHistory() {
        val history = searchHistoryManager.getSearchHistory()
        if (history.isEmpty()) {
            Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建历史记录列表视图
        val historyView = LayoutInflater.from(this).inflate(R.layout.search_history_layout, null)
        val recyclerView = historyView.findViewById<RecyclerView>(R.id.history_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val adapter = SearchHistoryAdapter(history) { query ->
            performSearch(query)
            windowManager?.removeView(historyView)
        }
        recyclerView.adapter = adapter
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        windowManager?.addView(historyView, params)
        historyView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.menu_show))
    }
    
    // GestureCallback 实现
    override fun onSingleTap() {
        try {
            Log.d("FloatingService", "单击事件触发")
            if (!isCardViewMode) {
        showSearchInput()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理单击事件失败", e)
        }
    }
    
    override fun onDoubleTap() {
        try {
            Log.d("FloatingService", "双击事件触发")
            if (!isCardViewMode) {
        repeatLastSearch()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理双击事件失败", e)
        }
    }
    
    override fun onLongPress() {
        try {
            Log.d("FloatingService", "长按事件触发")
            if (!isCardViewMode && aiWindows.isNotEmpty()) {
                cardStartY = 0f
                cardStartX = 0f
                isCardViewMode = true
                showCardView(false, 0f)
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理长按事件失败", e)
        }
    }
    
    override fun onSwipeLeft() {
        try {
            Log.d("FloatingService", "左滑事件触发")
            if (!isCardViewMode) {
        closeCurrentEngine()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理左滑事件失败", e)
        }
    }
    
    override fun onSwipeRight() {
        try {
            Log.d("FloatingService", "右滑事件触发")
            if (!isCardViewMode) {
        showSearchHistory()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理右滑事件失败", e)
        }
    }
    
    override fun onSwipeUp() {
        try {
            Log.d("FloatingService", "上滑事件触发")
            if (!isCardViewMode) {
        switchToNextEngine()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理上滑事件失败", e)
        }
    }
    
    override fun onSwipeDown() {
        try {
            Log.d("FloatingService", "下滑事件触发")
            if (!isCardViewMode) {
        switchToPreviousEngine()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理下滑事件失败", e)
        }
    }
    
    override fun onDrag(x: Float, y: Float) {
        if (isCardViewMode && aiWindows.isNotEmpty()) {
            if (cardStartY == 0f) cardStartY = y
            if (cardStartX == 0f) cardStartX = x
            
            val deltaY = y - cardStartY
            val deltaX = x - cardStartX
            
            if (Math.abs(deltaY) > Math.abs(deltaX)) {
                val progress = deltaY / (screenHeight / 4)
                showCardView(false, progress)
                currentCardIndex = ((aiWindows.size - 1) * (progress + 1) / 2).toInt()
                    .coerceIn(0, aiWindows.size - 1)
            } else {
                val progress = deltaX / (screenWidth / 4)
                showCardView(true, progress)
                currentCardIndex = ((aiWindows.size - 1) * (progress + 1) / 2).toInt()
                    .coerceIn(0, aiWindows.size - 1)
            }
        } else if (!isCardViewMode) {
        floatingBallView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = x.toInt()
            params.y = y.toInt()
                try {
            windowManager?.updateViewLayout(view, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    override fun onDragEnd(x: Float, y: Float) {
        if (isCardViewMode) {
            hideCardView()
            bringEngineToFront(currentCardIndex)
        } else {
        floatingBallView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
                val ballSize = 48.dpToPx()
                val hiddenPortion = (ballSize * 0.6f).toInt()
                
            val targetX = when {
                    x < screenWidth / 2 -> -hiddenPortion
                    else -> screenWidth - ballSize + hiddenPortion
            }
            
                val animator = ValueAnimator.ofInt(params.x, targetX)
            animator.addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                    try {
                windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
            }
            animator.duration = 200
            animator.interpolator = DecelerateInterpolator()
            animator.start()
            }
        }
    }
    
    private fun showCardView(isHorizontal: Boolean, progress: Float) {
        if (!isCardViewMode) {
            isCardViewMode = true
            // 显示所有窗口
            aiWindows.forEachIndexed { index, webView ->
                val parent = webView.parent as? View ?: return@forEachIndexed
                parent.visibility = View.VISIBLE
            }
        }

        // 更新卡片位置
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            val params = parent.layoutParams as WindowManager.LayoutParams
            
            if (isHorizontal) {
                // 水平排列
                val baseX = screenWidth / 2 - (cardSpacing * (aiWindows.size - 1) / 2)
                val targetX = baseX + (index * cardSpacing)
                val currentX = targetX + (progress * cardSpacing)
                
                params.x = currentX.toInt()
                params.y = screenHeight / 3
                
                // 添加波浪效果
                val wave = Math.sin((currentX / screenWidth) * Math.PI) * 50
                params.y += wave.toInt()
                
                // 设置缩放和旋转
                val scale = 1f - (Math.abs(index - currentCardIndex) * (1f - cardScale))
                val rotation = (index - currentCardIndex) * cardRotation
                
                parent.scaleX = scale
                parent.scaleY = scale
                parent.rotation = rotation
            } else {
                // 垂直排列
                val baseY = screenHeight / 2 - (cardSpacing * (aiWindows.size - 1) / 2)
                val targetY = baseY + (index * cardSpacing)
                val currentY = targetY + (progress * cardSpacing)
                
                params.x = screenWidth / 2 - parent.width / 2
                params.y = currentY.toInt()
                
                // 添加波浪效果
                val wave = Math.sin((currentY / screenHeight) * Math.PI) * 50
                params.x += wave.toInt()
                
                // 设置缩放和旋转
                val scale = 1f - (Math.abs(index - currentCardIndex) * (1f - cardScale))
                val rotation = (index - currentCardIndex) * cardRotation
                
                parent.scaleX = scale
                parent.scaleY = scale
                parent.rotation = rotation
            }
            
            try {
                windowManager?.updateViewLayout(parent, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideCardView() {
        isCardViewMode = false
        // 隐藏非当前窗口
        aiWindows.forEachIndexed { _, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            if (currentCardIndex != aiWindows.indexOf(webView)) {
                parent.visibility = View.GONE
            } else {
                // 重置当前窗口的位置和动画
                val params = parent.layoutParams as WindowManager.LayoutParams
                params.x = 0
                params.y = aiWindows.indexOf(webView) * (screenHeight / 3)
                parent.scaleX = 1f
                parent.scaleY = 1f
                parent.rotation = 0f
                try {
                    windowManager?.updateViewLayout(parent, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        windowManager?.let { wm ->
            floatingBallView?.let { wm.removeView(it) }
            searchView?.let { wm.removeView(it) }
            aiWindows.forEach { wm.removeView(it) }
        }
    }
} 