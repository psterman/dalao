package com.example.aifloatingball

import android.Manifest
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
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
import androidx.core.content.ContextCompat
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
    
    private var recognizer: SpeechRecognizer? = null
    private var searchInputField: EditText? = null
    
    private var isCardViewMode = false
    private var cardStartY = 0f
    private var cardStartX = 0f
    private var currentCardIndex = 0
    private var cardSpacing = 0
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
            
            // 初始化窗口管理器
            initWindowManager()
            
            // 获取屏幕尺寸
            initScreenSize()
            
            // 初始化所有管理器
            initManagers()
            
            // 创建并显示悬浮球
            createFloatingBall()
            
            // 尝试启动前台服务
            startForegroundOrNormal()
            
            Log.d("FloatingService", "服务创建完成")
        } catch (e: Exception) {
            Log.e("FloatingService", "服务创建失败", e)
            Toast.makeText(this, "服务创建失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initWindowManager() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            if (windowManager == null) {
                throw IllegalStateException("无法获取WindowManager服务")
            }
            Log.d("FloatingService", "窗口管理器初始化成功")
        } catch (e: Exception) {
            Log.e("FloatingService", "窗口管理器初始化失败", e)
            throw e
        }
    }
    
    private fun initScreenSize() {
        try {
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
            cardSpacing = 60.dpToPx()
        } catch (e: Exception) {
            Log.e("FloatingService", "获取屏幕尺寸失败", e)
            throw e
        }
    }
    
    private fun initManagers() {
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
    }
    
    private fun startForegroundOrNormal() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = "floating_ball_service"
                val channelName = "悬浮球服务"
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持悬浮球服务运行"
                    setShowBadge(false)
                }
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                
                // 检查通知权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("FloatingService", "没有通知权限，以普通服务方式运行")
                    return
                }
                
                notificationManager?.createNotificationChannel(channel)
                
                val notification = NotificationCompat.Builder(this, channelId)
                    .setContentTitle("悬浮球服务")
                    .setContentText("点击管理悬浮球")
                    .setSmallIcon(R.drawable.ic_floating_ball)
                    .setOngoing(true)
                    .build()
                
                startForeground(NOTIFICATION_ID, notification)
                Log.d("FloatingService", "前台服务启动成功")
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "启动前台服务失败: ${e.message}", e)
            // 即使前台服务启动失败，也继续运行
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingService", "onStartCommand被调用")
        // 如果服务被系统杀死后重启，重新尝试启动前台服务
        if (intent?.action == "restart_foreground") {
            startForegroundOrNormal()
        }
        return START_STICKY
    }
    
    private fun createFloatingBall() {
        try {
            Log.d("FloatingService", "开始创建悬浮球")
            
            // 创建悬浮球视图
            val inflater = LayoutInflater.from(this)
            val root = FrameLayout(this)
            floatingBallView = inflater.inflate(R.layout.floating_ball, root, true)
            
            if (floatingBallView == null) {
                Log.e("FloatingService", "悬浮球视图创建失败")
                throw IllegalStateException("无法创建悬浮球视图")
            }
            
            // 设置悬浮球参数
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.RGBA_8888
                )
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.RGBA_8888
                )
            }
            
            params.gravity = Gravity.START or Gravity.TOP
            params.x = screenWidth - 100.dpToPx()
            params.y = screenHeight / 2
            
            // 添加悬浮球到窗口
            windowManager?.addView(root, params)
            
            // 设置手势监听
            floatingBallView?.let { view ->
                gestureManager.attachToView(view)
                Log.d("FloatingService", "已设置手势监听")
                
                // 设置点击事件
                view.setOnClickListener {
                    performClick()
                }
            }
            
            Log.d("FloatingService", "悬浮球创建成功")
        } catch (e: Exception) {
            Log.e("FloatingService", "创建悬浮球失败", e)
            Toast.makeText(this, "创建悬浮球失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 工具方法
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun showSearchInput() {
        val root = FrameLayout(this)
        searchView = LayoutInflater.from(this).inflate(R.layout.search_input_layout, root, true)
        
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
        }.apply {
            gravity = Gravity.TOP
            flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
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
        
        windowManager?.addView(root, params)
    }
    
    private fun setupVoiceInput() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.e("VoiceInput", "设备不支持语音识别")
                return
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("VoiceInput", "没有麦克风权限")
                return
            }

            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }
            
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("VoiceInput", "准备开始语音识别")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d("VoiceInput", "开始语音输入")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // 更新音量指示器
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    Log.d("VoiceInput", "接收到语音数据")
                }
                
                override fun onEndOfSpeech() {
                    Log.d("VoiceInput", "语音输入结束")
                }
                
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "未能识别语音"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                        else -> "未知错误"
                    }
                    Log.e("VoiceInput", "语音识别错误: $errorMessage")
                    Toast.makeText(this@FloatingWindowService, errorMessage, Toast.LENGTH_SHORT).show()
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val query = matches[0]
                        Log.d("VoiceInput", "识别结果: $query")
                        performSearch(query)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    Log.d("VoiceInput", "部分识别结果")
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d("VoiceInput", "识别事件: $eventType")
                }
            })
            
            searchView?.findViewById<ImageButton>(R.id.voice_input_button)?.apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    startVoiceRecognition()
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceInput", "设置语音输入失败", e)
        }
    }
    
    private fun startVoiceRecognition() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "需要麦克风权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceInput", "启动语音识别失败", e)
            Toast.makeText(this, "启动语音识别失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun performSearch(query: String) {
        if (query.isEmpty()) return
        
        searchHistoryManager.addSearchQuery(query)
        
        val urls = listOf(
            "https://www.bing.com/search?q=$query",
            "https://www.google.com/search?q=$query",
            "https://www.baidu.com/s?wd=$query"
        )
        
        urls.forEachIndexed { index, url ->
            createAIWindow(Uri.encode(url), index)
        }
    }
    
    private fun createAIWindow(url: String, index: Int) {
        val root = FrameLayout(this)
        val webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = false  // 默认禁用JavaScript
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 启用缓存
                cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // 设置默认编码
                defaultTextEncodingName = "UTF-8"
                
                // 允许文件访问
                allowFileAccess = true
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }
            
            webViewClient = CustomWebViewClient()
            loadUrl(url)
        }
        
        root.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 计算每个窗口的高度（屏幕高度的1/3）
        val windowHeight = screenHeight / 3
        
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                windowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                windowHeight,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
        }.apply {
            gravity = Gravity.TOP
            y = index * windowHeight
            horizontalMargin = 0.02f
            alpha = 0.95f
        }
        
        aiWindows.add(webView)
        windowManager?.addView(root, params)
        
        // 添加显示动画
        root.alpha = 0f
        root.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    private fun performClick() {
        if (!isCardViewMode) {
            showSearchInput()
        }
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
    
    // 显示搜索历史
    private fun showSearchHistory() {
        val history = searchHistoryManager.getSearchHistory()
        if (history.isEmpty()) {
            Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建历史记录列表视图
        val root = FrameLayout(this)
        val historyView = LayoutInflater.from(this).inflate(R.layout.search_history_layout, root, true)
        val recyclerView = historyView.findViewById<RecyclerView>(R.id.history_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val adapter = SearchHistoryAdapter(history) { query ->
            performSearch(query)
            windowManager?.removeView(root)
        }
        recyclerView.adapter = adapter
        
        val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
        }
        
        windowManager?.addView(root, params)
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
                showSearchHistory()
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
                closeAllWindows()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理左滑事件失败", e)
        }
    }
    
    private fun closeAllWindows() {
        aiWindows.forEach { webView ->
            val parent = webView.parent as? View
            if (parent != null) {
                parent.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        windowManager?.removeView(parent)
                    }
                    .start()
            }
        }
        aiWindows.clear()
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
            if (!isCardViewMode && aiWindows.isNotEmpty()) {
                cardStartY = 0f
                cardStartX = 0f
                isCardViewMode = true
                showCardView(false, 0f)
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "处理上滑事件失败", e)
        }
    }
    
    override fun onSwipeDown() {
        try {
            Log.d("FloatingService", "下滑事件触发")
            if (isCardViewMode) {
                hideCardView()
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
                    Log.e("FloatingService", "更新悬浮球位置失败", e)
                }
            }
        }
    }
    
    override fun onDragEnd(x: Float, y: Float) {
        if (isCardViewMode) {
            hideCardView()
            showSelectedCard()
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
                        Log.e("FloatingService", "更新悬浮球位置失败", e)
                    }
                }
                animator.duration = 200
                animator.interpolator = DecelerateInterpolator()
                animator.start()
            }
        }
    }
    
    private fun showSelectedCard() {
        if (currentCardIndex >= 0 && currentCardIndex < aiWindows.size) {
            val webView = aiWindows[currentCardIndex]
            val parent = webView.parent as? View ?: return
            
            // 重置当前窗口的位置和动画
            val params = parent.layoutParams as WindowManager.LayoutParams
            params.x = 0
            params.y = 0
            parent.scaleX = 1f
            parent.scaleY = 1f
            parent.rotation = 0f
            try {
                windowManager?.updateViewLayout(parent, params)
            } catch (e: Exception) {
                Log.e("FloatingService", "更新选中卡片位置失败", e)
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
        try {
            recognizer?.destroy()
            recognizer = null
            
            windowManager?.let { wm ->
                floatingBallView?.let { view ->
                    val parent = view.parent as? View
                    if (parent != null) {
                        wm.removeView(parent)
                    }
                }
                
                searchView?.let { view ->
                    val parent = view.parent as? View
                    if (parent != null) {
                        wm.removeView(parent)
                    }
                }
                
                aiWindows.forEach { webView ->
                    val parent = webView.parent as? View
                    if (parent != null) {
                        wm.removeView(parent)
                    }
                }
            }
            
            aiWindows.clear()
        } catch (e: Exception) {
            Log.e("FloatingService", "清理视图失败", e)
        }
        
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
} 