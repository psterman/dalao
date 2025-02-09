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
import kotlin.math.abs
import kotlin.math.min

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
    
    // 添加手势检测相关变量
    private var initialTouchY = 0f
    private var lastTouchY = 0f
    private var isExitGestureInProgress = false
    private val GESTURE_THRESHOLD = 100f  // 退出手势的阈值
    
    private var activeCardIndex = -1  // 当前活跃的卡片索引
    private var lastTouchX = 0f
    private var initialCardX = 0
    private var initialCardY = 0
    private var isDragging = false
    
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
            try {
                initWindowManager()
            } catch (e: Exception) {
                Log.e("FloatingService", "初始化窗口管理器失败", e)
                Toast.makeText(this, "初始化窗口管理器失败: ${e.message}", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            
            // 获取屏幕尺寸
            try {
                initScreenSize()
            } catch (e: Exception) {
                Log.e("FloatingService", "获取屏幕尺寸失败", e)
                Toast.makeText(this, "获取屏幕尺寸失败: ${e.message}", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            
            // 初始化所有管理器
            try {
                initManagers()
            } catch (e: Exception) {
                Log.e("FloatingService", "初始化管理器失败", e)
                Toast.makeText(this, "初始化管理器失败: ${e.message}", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            
            // 创建并显示悬浮球
            try {
                createFloatingBall()
            } catch (e: Exception) {
                Log.e("FloatingService", "创建悬浮球失败", e)
                Toast.makeText(this, "创建悬浮球失败: ${e.message}", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            
            // 尝试启动前台服务
            try {
                startForegroundOrNormal()
            } catch (e: Exception) {
                Log.e("FloatingService", "启动前台服务失败", e)
                // 即使前台服务启动失败，也继续运行
            }
            
            Log.d("FloatingService", "服务创建完成")
        } catch (e: Exception) {
            Log.e("FloatingService", "服务创建失败", e)
            Toast.makeText(this, "服务创建失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
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
        try {
            // 如果服务被系统杀死后重启，重新尝试启动前台服务
            if (intent?.action == "restart_foreground") {
                startForegroundOrNormal()
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "onStartCommand执行失败", e)
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
        
        // 获取输入框和搜索按钮
        val searchInput = searchView?.findViewById<EditText>(R.id.search_input)
        val searchButton = searchView?.findViewById<ImageButton>(R.id.search_button)
        
        // 设置搜索按钮点击事件
        searchButton?.setOnClickListener {
            val query = searchInput?.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) {
                performSearch(query)
                // 关闭搜索输入框
                windowManager?.removeView(root)
                searchView = null
            }
        }
        
        // 设置输入框回车键监听
        searchInput?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)
            ) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                    // 关闭搜索输入框
                    windowManager?.removeView(root)
                    searchView = null
                }
                true
            } else {
                false
            }
        }
        
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

            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            
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
                        else -> "未知错误: $error"
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
                    when (eventType) {
                        SpeechRecognizer.ERROR_AUDIO -> Log.d("VoiceInput", "音频事件")
                        SpeechRecognizer.ERROR_NETWORK -> Log.d("VoiceInput", "网络事件")
                        SpeechRecognizer.ERROR_NO_MATCH -> Log.d("VoiceInput", "无匹配事件")
                        SpeechRecognizer.ERROR_SERVER -> Log.d("VoiceInput", "服务器事件")
                        SpeechRecognizer.ERROR_CLIENT -> Log.d("VoiceInput", "客户端事件")
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Log.d("VoiceInput", "语音超时")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Log.d("VoiceInput", "权限不足")
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Log.d("VoiceInput", "识别器忙")
                        else -> Log.d("VoiceInput", "其他事件: $eventType")
                    }
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
        
        // 先清除现有的搜索窗口
        closeAllWindows()
        
        searchHistoryManager.addSearchQuery(query)
        
        val urls = listOf(
            "https://kimi.moonshot.cn",  // Kimi AI
            "https://chat.deepseek.com",  // DeepSeek
            "https://www.doubao.com"  // 豆包
        )
        
        Log.d("FloatingService", "开始创建AI助手窗口，查询词: $query")
        urls.forEachIndexed { index, url ->
            try {
                createAIWindow(url, index)
                Log.d("FloatingService", "成功创建第 ${index + 1} 个AI助手窗口")
            } catch (e: Exception) {
                Log.e("FloatingService", "创建AI助手窗口失败: ${e.message}")
            }
        }
    }
    
    private fun createAIWindow(url: String, index: Int) {
        try {
            val root = FrameLayout(this)
            
            // 创建标题栏布局
            val titleBar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                setBackgroundColor(android.graphics.Color.WHITE)
                
                // 添加Logo
                val logo = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = when (index) {
                        0 -> "K"
                        1 -> "D"
                        2 -> "豆"
                        else -> "?"
                    }
                    textSize = 16f
                    setTextColor(android.graphics.Color.parseColor("#333333"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#F0F0F0"))
                        cornerRadius = 12f
                    }
                    setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
                }
                addView(logo)
                
                // 添加标题文本
                val title = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 8.dpToPx()
                    }
                    text = when (index) {
                        0 -> "Kimi AI"
                        1 -> "DeepSeek"
                        2 -> "豆包"
                        else -> ""
                    }
                    setTextColor(android.graphics.Color.parseColor("#333333"))
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(title)
            }
            
            // 创建卡片容器
            val cardContainer = FrameLayout(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 16f
                }
                
                // 添加阴影效果
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = 8f
                }
            }
            
            // 添加标题栏到卡片容器
            cardContainer.addView(titleBar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            
            // 创建WebView并添加到卡片容器
            val webView = WebView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    topMargin = 48.dpToPx() // 标题栏高度
                }
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                    defaultTextEncodingName = "UTF-8"
                    allowFileAccess = true
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }
                
                webViewClient = CustomWebViewClient()
                loadUrl(url)
                
                // 修改触摸事件监听
                setOnTouchListener { view, event ->
                    val parent = view.parent as? View ?: return@setOnTouchListener false
                    val params = parent.layoutParams as WindowManager.LayoutParams
                    
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastTouchX = event.rawX
                            lastTouchY = event.rawY
                            initialCardX = params.x
                            initialCardY = params.y
                            activeCardIndex = index
                            isDragging = false
                            
                            // 将当前卡片提升到最上层
                            parent.elevation = 20f
                            parent.scaleX = 1.05f
                            parent.scaleY = 1.05f
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - lastTouchX
                            val deltaY = event.rawY - lastTouchY
                            
                            // 判断是否开始拖动
                            if (!isDragging && (abs(deltaX) > 20 || abs(deltaY) > 20)) {
                                isDragging = true
                            }
                            
                            if (isDragging) {
                                // 处理卡片拖动
                                params.x = (initialCardX + deltaX).toInt()
                                params.y = (initialCardY + deltaY).toInt()
                                
                                // 根据水平移动距离设置透明度（为滑动关闭做准备）
                                val alpha = 1 - min(abs(deltaX) / (screenWidth / 2), 0.6f)
                                parent.alpha = alpha
                                
                                try {
                                    windowManager?.updateViewLayout(parent, params)
                                } catch (e: Exception) {
                                    Log.e("FloatingService", "更新卡片位置失败", e)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isDragging) {
                                val deltaX = event.rawX - lastTouchX
                                
                                when {
                                    // 左滑关闭
                                    deltaX < -screenWidth / 3 -> {
                                        animateCardDismissal(parent, -screenWidth.toFloat())
                                        removeCard(index)
                                    }
                                    // 右滑隐藏
                                    deltaX > screenWidth / 3 -> {
                                        animateCardDismissal(parent, screenWidth.toFloat())
                                        hideCard(index)
                                    }
                                    else -> {
                                        // 回到原位
                                        animateCardReturn(parent)
                                    }
                                }
                            } else if (event.eventTime - event.downTime < 200) {
                                // 点击事件，切换卡片大小
                                toggleCardSize(parent, index)
                            }
                            
                            isDragging = false
                            true
                        }
                        else -> false
                    }
                }
                
                // 添加长按事件
                setOnLongClickListener {
                    showCardOptions(it, index)
                    true
                }
            }
            
            cardContainer.addView(webView)
            root.addView(cardContainer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // 窗口参数设置
            val windowHeight = (screenHeight * 2) / 3
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    windowHeight,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }.apply {
                gravity = Gravity.TOP
                y = index * (windowHeight / 3)
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
            
            Log.d("FloatingService", "成功创建AI助手窗口: $url")
        } catch (e: Exception) {
            Log.e("FloatingService", "创建AI助手窗口失败", e)
            throw e
        }
    }
    
    private fun animateCardDismissal(view: View, targetX: Float) {
        view.animate()
            .translationX(targetX)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                try {
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    Log.e("FloatingService", "移除卡片失败", e)
                }
            }
            .start()
    }

    private fun animateCardReturn(view: View) {
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun toggleCardSize(view: View, index: Int) {
        val params = view.layoutParams as WindowManager.LayoutParams
        val isExpanded = params.height > (screenHeight * 0.7f).toInt()
        
        val targetHeight = if (isExpanded) {
            (screenHeight * 0.7f).toInt()
        } else {
            (screenHeight * 0.85f).toInt()
        }
        
        view.animate()
            .scaleX(if (isExpanded) 1f else 1.05f)
            .scaleY(if (isExpanded) 1f else 1.05f)
            .setDuration(200)
            .withEndAction {
                params.height = targetHeight
                try {
                    windowManager?.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.e("FloatingService", "更新卡片大小失败", e)
                }
            }
            .start()
    }

    private fun showCardOptions(view: View, index: Int) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menu.apply {
            add("刷新").setOnMenuItemClickListener {
                refreshCard(index)
                true
            }
            add("分享").setOnMenuItemClickListener {
                shareCard(index)
                true
            }
            add("关闭").setOnMenuItemClickListener {
                removeCard(index)
                true
            }
        }
        popupMenu.show()
    }

    private fun refreshCard(index: Int) {
        aiWindows.getOrNull(index)?.reload()
    }

    private fun shareCard(index: Int) {
        aiWindows.getOrNull(index)?.let { webView ->
            val url = webView.url
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(Intent.createChooser(intent, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun removeCard(index: Int) {
        aiWindows.getOrNull(index)?.let { webView ->
            val parent = webView.parent as? View
            if (parent != null) {
                animateCardDismissal(parent, -screenWidth.toFloat())
                aiWindows.removeAt(index)
            }
        }
    }

    private fun hideCard(index: Int) {
        aiWindows.getOrNull(index)?.let { webView ->
            val parent = webView.parent as? View
            if (parent != null) {
                parent.visibility = View.GONE
            }
        }
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
            
            if (abs(deltaY) > abs(deltaX)) {
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
                val hiddenPortion = (ballSize * 0.7f).toInt()  // 增加隐藏部分
                
                // 计算目标X坐标（吸附到屏幕边缘）
                val targetX = when {
                    x < screenWidth / 2 -> -hiddenPortion  // 吸附到左边
                    else -> screenWidth - ballSize + hiddenPortion  // 吸附到右边
                }
                
                // 计算目标Y坐标（保持在屏幕内）
                val targetY = y.toInt().coerceIn(
                    0,  // 上边界
                    screenHeight - ballSize  // 下边界
                )
                
                // 创建X轴动画
                val animatorX = ValueAnimator.ofInt(params.x, targetX)
                animatorX.addUpdateListener { animation ->
                    params.x = animation.animatedValue as Int
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e("FloatingService", "更新悬浮球X位置失败", e)
                    }
                }
                
                // 创建Y轴动画
                val animatorY = ValueAnimator.ofInt(params.y, targetY)
                animatorY.addUpdateListener { animation ->
                    params.y = animation.animatedValue as Int
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e("FloatingService", "更新悬浮球Y位置失败", e)
                    }
                }
                
                // 设置动画属性
                animatorX.apply {
                    duration = 200
                    interpolator = DecelerateInterpolator()
                    start()
                }
                
                animatorY.apply {
                    duration = 200
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        }
    }
    
    private fun showSelectedCard() {
        if (currentCardIndex >= 0 && currentCardIndex < aiWindows.size) {
            val cardWidth = screenWidth - 48.dpToPx()
            val cardHeight = (screenHeight * 0.8f).toInt()
            val cardSpacing = 50.dpToPx()
            
            aiWindows.forEachIndexed { index, webView ->
                val parent = webView.parent as? View ?: return@forEachIndexed
                val params = parent.layoutParams as WindowManager.LayoutParams
                
                val distanceFromSelected = abs(index - currentCardIndex)
                val scale = if (index == currentCardIndex) 1f else 0.88f
                val alpha = if (index == currentCardIndex) 1f else 0.65f
                val elevation = if (index == currentCardIndex) 25f else 5f
                
                params.width = cardWidth
                params.height = cardHeight
                params.x = screenWidth / 2 - cardWidth / 2 + 
                          (if (index < currentCardIndex) -cardWidth - cardSpacing 
                           else if (index > currentCardIndex) cardWidth + cardSpacing else 0)
                params.y = screenHeight / 2 - cardHeight / 2 +
                          (distanceFromSelected * 60).toInt()
                
                parent.scaleX = scale
                parent.scaleY = scale
                parent.alpha = alpha
                parent.elevation = elevation
                parent.rotation = if (index == currentCardIndex) 0f 
                                else (if (index < currentCardIndex) -8f else 8f)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    parent.elevation = if (index == currentCardIndex) 25f else 10f
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    parent.translationZ = -abs(distanceFromSelected.toFloat()) * 40f
                }
                
                // 设置背景和阴影
                parent.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 16f
                }
                
                try {
                    windowManager?.updateViewLayout(parent, params)
                } catch (e: Exception) {
                    Log.e("FloatingService", "更新选中卡片位置失败", e)
                }
            }
        }
    }
    
    private fun showCardView(isHorizontal: Boolean, progress: Float) {
        if (!isCardViewMode) {
            isCardViewMode = true
            aiWindows.forEachIndexed { index, webView ->
                val parent = webView.parent as? View ?: return@forEachIndexed
                parent.visibility = View.VISIBLE
                parent.alpha = 0.95f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    parent.elevation = 8f
                }
            }
        }

        val cardWidth = screenWidth - 48.dpToPx()
        val cardHeight = (screenHeight * 0.75f).toInt()
        val cardSpacing = 50.dpToPx()
        val verticalOffset = (screenHeight * 0.12).toInt()  // 计算垂直偏移基准值
        val maxRotation = 12f
        val maxScale = 0.92f
        
        val selectedIndex = if (isHorizontal) {
            ((aiWindows.size - 1) * (progress + 1) / 2).toInt().coerceIn(0, aiWindows.size - 1)
        } else {
            ((aiWindows.size - 1) * (1 - progress)).toInt().coerceIn(0, aiWindows.size - 1)
        }
        
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            val params = parent.layoutParams as WindowManager.LayoutParams
            val distanceFromSelected = index - selectedIndex
            
            if (isHorizontal) {
                val baseX = (index - selectedIndex) * (cardWidth + cardSpacing)
                val currentX = baseX + (progress * cardSpacing * 1.5f)
                val distanceFromCenter = abs(currentX) / (screenWidth / 2.0f)
                
                val scale = maxScale - (distanceFromCenter * 0.15f).coerceIn(0f, 0.25f)
                val alpha = 1.0f - (distanceFromCenter * 0.4f).coerceIn(0f, 0.6f)
                val rotation = (distanceFromCenter * 8f * if(currentX < 0) -1 else 1).coerceIn(-8f, 8f)
                
                params.width = cardWidth
                params.height = cardHeight
                params.x = (screenWidth / 2 - cardWidth / 2 + currentX).toInt()
                params.y = (screenHeight / 2 - cardHeight / 2 + (abs(distanceFromSelected) * 20)).toInt()
                
                parent.scaleX = scale
                parent.scaleY = scale
                parent.alpha = alpha
                parent.rotation = rotation
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    parent.elevation = (20 * (1 - distanceFromCenter)).coerceIn(5f, 20f)
                    parent.translationZ = -abs(distanceFromSelected.toFloat()) * 40f
                }
            } else {
                val verticalPosition = screenHeight / 2f - cardHeight / 2f + 
                                     (distanceFromSelected * verticalOffset) + 
                                     (progress * verticalOffset)
                params.y = verticalPosition.toInt()
                
                val positionScale = maxScale - (abs(distanceFromSelected.toFloat()) * 0.08f).coerceIn(0f, 0.2f)
                val zIndex = if (distanceFromSelected > 0) {
                    25f - (distanceFromSelected.toFloat() * 6f)
                } else {
                    25f + (abs(distanceFromSelected.toFloat()) * 6f)
                }
                
                val positionAlpha = 1.0f - (abs(distanceFromSelected.toFloat()) * 0.25f).coerceIn(0f, 0.6f)
                val tiltAngle = if (distanceFromSelected > 0) {
                    -maxRotation * (1 - abs(progress) * 0.8f)
                } else {
                    maxRotation * (1 - abs(progress) * 0.8f)
                } * min(1f, abs(distanceFromSelected.toFloat()))
                
                val horizontalOffset = (distanceFromSelected * 15).toInt()
                
                params.width = cardWidth
                params.height = cardHeight
                params.x = screenWidth / 2 - cardWidth / 2 + horizontalOffset
                params.y = params.y
                
                parent.scaleX = positionScale
                parent.scaleY = positionScale
                parent.alpha = positionAlpha
                parent.rotation = tiltAngle
                parent.elevation = zIndex
                parent.translationZ = -abs(distanceFromSelected.toFloat()) * 60f
                
                parent.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = (16 * resources.displayMetrics.density)
                }
            }
            
            try {
                windowManager?.updateViewLayout(parent, params)
            } catch (e: Exception) {
                Log.e("FloatingService", "更新卡片位置失败", e)
            }
        }
    }

    private fun hideCardView() {
        isCardViewMode = false
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            val params = parent.layoutParams as WindowManager.LayoutParams
            
            // 恢复原始状态
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = (screenHeight * 2) / 3
            params.x = 0
            params.y = index * (params.height / 3)
            
            // 重置所有变换
            parent.scaleX = 1f
            parent.scaleY = 1f
            parent.alpha = 0.95f
            parent.elevation = 0f
            parent.rotation = 0f
            parent.translationZ = 0f
            
            // 移除阴影和圆角
            parent.background = null
            
            // 只显示当前选中的卡片
            parent.visibility = if (index == currentCardIndex) View.VISIBLE else View.GONE
            
            try {
                windowManager?.updateViewLayout(parent, params)
            } catch (e: Exception) {
                Log.e("FloatingService", "重置卡片位置失败", e)
            }
        }
    }
    
    private fun closeAllWindows() {
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View
            if (parent != null) {
                parent.animate()
                    .alpha(0f)
                    .translationY(-screenHeight.toFloat())
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setStartDelay((index * 50).toLong())
                    .setDuration(200)
                    .withEndAction {
                        try {
                            windowManager?.removeView(parent)
                        } catch (e: Exception) {
                            Log.e("FloatingService", "移除窗口失败", e)
                        }
                    }
                    .start()
            }
        }
        aiWindows.clear()
        isCardViewMode = false
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