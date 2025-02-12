package com.example.aifloatingball

import android.Manifest
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
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
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val MEMORY_CHECK_INTERVAL = 30000L // 30秒检查一次内存
    }

    private var windowManager: WindowManager? = null
    private var floatingBallView: View? = null
    private var searchView: View? = null
    private val aiWindows = mutableListOf<WebView>()
    private var cardsContainer: ViewGroup? = null
    private lateinit var gestureManager: GestureManager
    private lateinit var quickMenuManager: QuickMenuManager
    private lateinit var systemSettingsHelper: SystemSettingsHelper
    private var screenWidth = 0
    private var screenHeight = 0
    private lateinit var searchHistoryManager: SearchHistoryManager
    private var currentEngineIndex = 0
    private val aiEngines = AIEngineConfig.engines
    
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
    private var initialTouchX = 0f
    
    private var isSearchVisible = false
    private var voiceAnimationView: View? = null
    private var voiceSearchHandler = Handler(Looper.getMainLooper())
    private var isVoiceSearchActive = false
    private val LONG_PRESS_TIMEOUT = 2000L // 2秒长按阈值
    
    // 存储最后一次查询
    private var lastQuery: String = ""
    
    // 添加缩略图缓存相关变量
    private val thumbnailCache = mutableMapOf<Int, android.graphics.Bitmap>()
    private val thumbnailViews = mutableMapOf<Int, ImageView>()
    
    private var initialX = 0
    private var initialY = 0
    
    private lateinit var settingsManager: SettingsManager
    
    // 添加缓存相关变量
    private var cachedCardViews = mutableListOf<View>()
    private var hasInitializedCards = false
    
    private var memoryCheckHandler: Handler? = null
    private var lastMemoryUsage: Long = 0
    private var startTime: Long = 0
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        try {
            startTime = System.currentTimeMillis()
            setupMemoryMonitoring()
            Log.d("FloatingService", "服务开始创建")
            super.onCreate()
            
            // 初始化 SettingsManager
            settingsManager = SettingsManager.getInstance(this)
            
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
            val channelId = "floating_ball_service"
            val channelName = "悬浮球服务"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持悬浮球服务运行"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
                
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, PermissionActivity::class.java),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
                
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("悬浮球服务")
                .setContentText("点击管理悬浮球")
                .setSmallIcon(R.drawable.ic_floating_ball)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            
            startForeground(NOTIFICATION_ID, notification)
            Log.d("FloatingService", "前台服务启动成功")
        } catch (e: Exception) {
            Log.e("FloatingService", "启动前台服务失败: ${e.message}", e)
            Toast.makeText(this, "前台服务启动失败，将以普通服务运行", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingService", "onStartCommand被调用")
        try {
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
            aiEngines.forEach { engine ->
                val webView = createWebView()
                webView.loadUrl(engine.url)
                aiWindows.add(webView)
            }
            
            Log.d("FloatingService", "开始创建悬浮球")
            
            val inflater = LayoutInflater.from(this)
            val root = FrameLayout(this)
            floatingBallView = inflater.inflate(R.layout.floating_ball, root, true)
            
            if (floatingBallView == null) {
                Log.e("FloatingService", "悬浮球视图创建失败")
                throw IllegalStateException("无法创建悬浮球视图")
            }
            
            val params = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.RGBA_8888
                )
            }
            
            params.gravity = Gravity.START or Gravity.TOP
            params.x = screenWidth - 100.dpToPx()
            params.y = screenHeight / 2
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                root.elevation = 1000f
                root.translationZ = 1000f
            }
            
            windowManager?.addView(root, params)
            
            floatingBallView?.let { view ->
                gestureManager.attachToView(view)
                Log.d("FloatingService", "已设置手势监听")
                
                view.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            val params = v.layoutParams as WindowManager.LayoutParams
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            lastTouchX = event.rawX
                            lastTouchY = event.rawY
                            
                            voiceSearchHandler.postDelayed({
                                if (!isDragging) {
                                    startVoiceSearchMode()
                                }
                            }, LONG_PRESS_TIMEOUT)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - lastTouchX
                            val deltaY = event.rawY - lastTouchY
                            
                            if (abs(deltaX) > 5 || abs(deltaY) > 5) {
                                voiceSearchHandler.removeCallbacksAndMessages(null)
                                if (isVoiceSearchActive) {
                                    stopVoiceSearchMode()
                                }
                                isDragging = true
                            }
                            
                            if (isDragging) {
                                val params = v.layoutParams as WindowManager.LayoutParams
                                params.x += deltaX.toInt()
                                params.y += deltaY.toInt()
                                
                                params.x = params.x.coerceIn(-v.width / 3, screenWidth - v.width * 2 / 3)
                                params.y = params.y.coerceIn(0, screenHeight - v.height)
                                
                                try {
                                    windowManager?.updateViewLayout(v, params)
                                } catch (e: Exception) {
                                    Log.e("FloatingService", "更新悬浮球位置失败", e)
                                }
                            }
                            
                            lastTouchX = event.rawX
                            lastTouchY = event.rawY
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            voiceSearchHandler.removeCallbacksAndMessages(null)
                            
                            if (isVoiceSearchActive) {
                                stopVoiceSearchMode()
                                isDragging = false
                                true
                            } else {
                                val isTap = !isDragging && abs(event.rawX - initialTouchX) < 5 && 
                                          abs(event.rawY - initialTouchY) < 5
                                
                                if (isTap) {
                                    performClick()
                                }
                                isDragging = false
                                true
                            }
                        }
                        else -> false
                    }
                }
            }
            
            Log.d("FloatingService", "悬浮球创建成功")
        } catch (e: Exception) {
            Log.e("FloatingService", "创建悬浮球失败", e)
            throw e
        }
    }
    
    // 工具方法
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
    
    private fun showSearchInput() {
        val root = FrameLayout(this).apply {
            setOnClickListener {
                windowManager?.removeView(this)
                searchView = null
                isSearchVisible = false
                closeAllWindows()
            }
            
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F5F5F5"))
            }
        }
        
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setOnClickListener { }
        }
        
        val topBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundResource(android.R.drawable.btn_default)
            layoutParams = LinearLayout.LayoutParams(
                48.dpToPx(),
                48.dpToPx()
            ).apply {
                marginEnd = 16.dpToPx()
            }
            setOnClickListener {
                windowManager?.removeView(root)
                searchView = null
                isSearchVisible = false
                closeAllWindows()
            }
        }
        
        val searchContainer = LayoutInflater.from(this).inflate(R.layout.search_input_layout, null, false)
        searchContainer.apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 24.dpToPx().toFloat()
                setStroke(1, android.graphics.Color.parseColor("#E0E0E0"))
            }
            
            elevation = 4f.dpToPx()
        }
        
        topBarLayout.addView(closeButton)
        topBarLayout.addView(searchContainer)
        
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 16.dpToPx()
            }
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            isFillViewport = true
        }
        
        val cardsContainerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 8.dpToPx())
        }
        
        scrollView.addView(cardsContainerView)
        containerLayout.addView(topBarLayout)
        containerLayout.addView(scrollView)
        root.addView(containerLayout)
        
        searchView = root
        cardsContainer = cardsContainerView
        
        val searchInput = searchContainer.findViewById<EditText>(R.id.search_input)
        val searchButton = searchContainer.findViewById<ImageButton>(R.id.search_button)
        val voiceButton = searchContainer.findViewById<ImageButton>(R.id.voice_input_button)
        
        searchButton?.setOnClickListener {
            val query = searchInput?.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) {
                performSearch(query, cardsContainerView)
            }
        }
        
        voiceButton?.setOnClickListener {
            startVoiceSearchMode()
        }
        
        searchInput?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)
            ) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query, cardsContainerView)
                }
                true
            } else {
                false
            }
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            flags = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        }
        
        windowManager?.addView(root, params)
        
        searchInput?.apply {
            requestFocus()
            postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
        
        isSearchVisible = true
        
        if (!hasInitializedCards) {
            val engines = settingsManager.getEngineOrder()
            
            cardsContainer?.removeAllViews()
            aiWindows.clear()
            cachedCardViews.clear()
            
            engines.forEachIndexed { index, engine ->
                try {
                    val cardView = createAICardView(engine.url, index)
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = if (index == 0) 8.dpToPx() else 0
                        bottomMargin = 8.dpToPx()
                    }
                    cardView.layoutParams = params
                    cardsContainer?.addView(cardView)
                    cachedCardViews.add(cardView)
                    
                    cardView.alpha = 0f
                    cardView.translationY = 50f
                    cardView.animate()
                        .alpha(0.95f)
                        .translationY(0f)
                        .setDuration(300)
                        .setStartDelay((index * 50).toLong())
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                } catch (e: Exception) {
                    Log.e("FloatingService", "创建AI卡片失败: ${e.message}")
                }
            }
            hasInitializedCards = true
        } else {
            cardsContainer?.removeAllViews()
            cachedCardViews.forEachIndexed { index, cardView ->
                val params = cardView.layoutParams as? LinearLayout.LayoutParams
                params?.apply {
                    bottomMargin = 8.dpToPx()
                }
                cardsContainer?.addView(cardView)
                cardView.alpha = 0f
                cardView.translationY = 50f
                cardView.animate()
                    .alpha(0.95f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay((index * 50).toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }
    
    private fun performSearch(query: String, cardsContainer: ViewGroup) {
        try {
            if (query.isEmpty()) return
            
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("search_query", query)
            clipboard.setPrimaryClip(clip)
            
            lastQuery = query
            
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            searchView?.findFocus()?.let { view ->
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            
            searchHistoryManager.addSearchQuery(query)
            
            val engines = settingsManager.getEngineOrder()
            
            cardsContainer.removeAllViews()
            aiWindows.clear()
            
            engines.forEachIndexed { index, engine ->
                try {
                    val cardView = createAICardView(engine.url, index)
                    cardsContainer.addView(cardView)
                    
                    cardView.alpha = 0f
                    cardView.translationY = 50f
                    cardView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300)
                        .setStartDelay((index * 100).toLong())
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                } catch (e: Exception) {
                    Log.e("FloatingService", "创建AI卡片失败: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e("FloatingService", "执行搜索失败: ${e.message}")
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 添加JavaScript接口类
    private class JsInterface(private val query: String) {
        @JavascriptInterface
        fun getQuery(): String = query
    }
    
    private fun createAICardView(url: String, index: Int): View {
        val cardView = LayoutInflater.from(this).inflate(R.layout.card_ai_engine, null)
        val engine = aiEngines[index]
        
        val titleBar = cardView.findViewById<View>(R.id.title_bar)
        val contentArea = cardView.findViewById<View>(R.id.content_area)
        val webView = cardView.findViewById<WebView>(R.id.web_view)
        
        cardView.findViewById<ImageView>(R.id.engine_icon).setImageResource(engine.iconResId)
        cardView.findViewById<TextView>(R.id.engine_name).text = engine.name
        
        setupWebView(webView, engine)
        
        webView.setOnTouchListener { _, event ->
            val titleBarBottom = titleBar.bottom
            event.y > titleBarBottom
        }
        
        titleBar.elevation = 10f
        
        cardView.alpha = 0.95f
        cardView.translationY = index * 60f
        cardView.scaleX = 0.95f
        cardView.scaleY = 0.95f
        
        contentArea.layoutParams = (contentArea.layoutParams as LinearLayout.LayoutParams).apply {
            height = 0
            weight = 0f
        }
        
        titleBar.setOnClickListener {
            if (activeCardIndex == index) {
                collapseCard(index)
            } else {
                expandCard(index)
            }
        }
        
        titleBar.setOnLongClickListener {
            showCardOptions(cardView, index)
            true
        }
        
        val pasteButton = cardView.findViewById<Button>(R.id.paste_button)
        pasteButton?.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text.toString()
                webView.evaluateJavascript(
                    """
                            (function() {
                        const input = document.querySelector('textarea');
                        if (input) {
                            input.value = '$text';
                            input.dispatchEvent(new Event('input', { bubbles: true }));
                            const button = document.querySelector('button[type="submit"]');
                            if (button) button.click();
                        }
                    })()
                    """.trimIndent(),
                    null
                )
            }
        }
        
        webView.loadUrl(url)
        aiWindows.add(webView)
        
        return cardView
    }

    private fun setupWebView(webView: WebView, engine: AIEngine) {
        webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
            
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                allowContentAccess = true
                allowFileAccess = true
                
                domStorageEnabled = true
                databaseEnabled = true
                
                cacheMode = WebSettings.LOAD_NO_CACHE
                
                javaScriptCanOpenWindowsAutomatically = true
                
                defaultTextEncodingName = "UTF-8"
                
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                
            val customUA = when (engine.name) {
                "豆包" -> "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.104 Mobile Safari/537.36"
                "ChatGPT" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                else -> userAgentString + " Mobile"
            }
            userAgentString = customUA
        }

        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        webView.isVerticalScrollBarEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val css = when (engine.name) {
                    "豆包" -> """
                        .mobile-container { height: auto !important; }
                        .chat-container { height: auto !important; }
                    """
                    "ChatGPT" -> """
                        body { height: auto !important; }
                        #__next { height: auto !important; }
                    """
                    "阿里通义" -> """
                        .chat-container { min-height: 80vh !important; }
                    """
                    "Kimi" -> """
                        .conversation-container { min-height: 80vh !important; }
                    """
                    else -> ""
                }
                
                if (css.isNotEmpty()) {
                    val script = """
                        javascript:(function() {
                            var style = document.createElement('style');
                            style.type = 'text/css';
                            style.innerHTML = '$css';
                            document.head.appendChild(style);
                        })()
                    """.trimIndent()
                    view?.loadUrl(script)
                }
            }
        }
    }

    private fun expandCard(index: Int) {
        val cardView = cardsContainer?.getChildAt(index) ?: return
        activeCardIndex = index
        
        // 获取标题栏和内容区域
        val titleBar = cardView.findViewById<View>(R.id.title_bar)
        val contentArea = cardView.findViewById<View>(R.id.content_area)
        val webView = cardView.findViewById<WebView>(R.id.web_view)
        
        // 确保WebView可见
        webView.visibility = View.VISIBLE
        
        // 计算展开后的卡片高度（屏幕高度的85%）
        val expandedHeight = (screenHeight * 0.85f).toInt()
        
        // 计算卡片在屏幕中央的位置
        val centerY = (screenHeight - expandedHeight) / 2
        
        // 计算标题栏高度和卡片间距
        val titleBarHeight = titleBar.height
        val cardSpacing = 40.dpToPx()  // 减小卡片间距
        
        // 展开动画
        cardView.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(300)
            .withStartAction {
                cardView.elevation = 8f
                
                // 调整内容区域高度
                contentArea.layoutParams = (contentArea.layoutParams as LinearLayout.LayoutParams).apply {
                    height = expandedHeight - titleBarHeight
                    weight = 0f
                }
                
                // 将展开的卡片移动到屏幕中央
                val params = cardView.layoutParams as LinearLayout.LayoutParams
                params.topMargin = centerY
                cardView.layoutParams = params
            }
            .start()
        
        // 处理其他卡片的位置
        val totalCards = cardsContainer?.childCount ?: 0
        for (i in 0 until totalCards) {
            if (i != index) {
                val otherCard = cardsContainer?.getChildAt(i) ?: continue
                val otherTitleBar = otherCard.findViewById<View>(R.id.title_bar)
                val otherContentArea = otherCard.findViewById<View>(R.id.content_area)
                val otherWebView = otherCard.findViewById<WebView>(R.id.web_view)
                
                // 计算其他卡片的位置
                val targetY = if (i < index) {
                    // 上方卡片，显示在展开卡片上方
                    centerY - ((index - i) * titleBarHeight) - cardSpacing
                } else {
                    // 下方卡片，显示在展开卡片下方
                    centerY + expandedHeight + ((i - index - 1) * titleBarHeight) + cardSpacing
                }
                
                otherCard.animate()
                    .translationY(targetY.toFloat())
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .alpha(0.8f)
                    .setDuration(300)
                    .withStartAction {
                        otherCard.elevation = 4f
                        
                        // 折叠其他卡片的内容区域
                        otherContentArea.layoutParams = (otherContentArea.layoutParams as LinearLayout.LayoutParams).apply {
                            height = 0
                            weight = 0f
                        }
                        
                        // 确保其他卡片的WebView隐藏
                        otherWebView.visibility = View.GONE
                        
                        // 确保标题栏可见
                        otherTitleBar.visibility = View.VISIBLE
                }
                .start()
        }
        }
        
        // 确保所有卡片都在屏幕范围内
        val scrollView = cardsContainer?.parent as? ScrollView
        scrollView?.post {
            scrollView.smoothScrollTo(0, centerY)
        }
    }
    
    private fun collapseCard(index: Int) {
        activeCardIndex = -1
        val initialCardSpacing = 8.dpToPx()  // 设置初始紧凑间距
        
        // 恢复所有卡片的状态
        for (i in 0 until (cardsContainer?.childCount ?: 0)) {
            val card = cardsContainer?.getChildAt(i) ?: continue
            
            // 获取标题栏和内容区域
            val titleBar = card.findViewById<View>(R.id.title_bar)
            val contentArea = card.findViewById<View>(R.id.content_area)
            val webView = card.findViewById<WebView>(R.id.web_view)
            
            // 计算目标Y位置（使用初始紧凑间距）
            var targetY = 0f
            for (j in 0 until i) {
                val previousCard = cardsContainer?.getChildAt(j)
                targetY += if (j == 0) initialCardSpacing else initialCardSpacing  // 使用初始紧凑间距
            }
            
            card.animate()
                .translationY(targetY)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .alpha(0.95f)
            .setDuration(300)
                .withStartAction {
                    card.elevation = 4f
                    
                    // 恢复标题栏位置
                    titleBar.translationY = 0f
                    
                    // 完全折叠内容区域
                    contentArea.layoutParams = (contentArea.layoutParams as LinearLayout.LayoutParams).apply {
                        height = 0
                        weight = 0f
                    }
                    
                    // 确保WebView完全隐藏
                    webView.visibility = View.GONE
                    
                    // 重置卡片位置和边距
                    val params = card.layoutParams as LinearLayout.LayoutParams
                    params.topMargin = if (i == 0) initialCardSpacing else 0
                    params.bottomMargin = initialCardSpacing  // 使用初始紧凑间距
                    card.layoutParams = params
            }
                .start()
        }
        
        // 滚动到顶部
        val scrollView = cardsContainer?.parent as? ScrollView
        scrollView?.smoothScrollTo(0, 0)
    }
    
    private fun setupVoiceInput() {
        try {
        // 初始化语音识别器
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                        isVoiceSearchActive = true
                        showVoiceSearchAnimation()
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    stopVoiceSearchMode()
                        Toast.makeText(this@FloatingWindowService, "语音识别失败，请重试", Toast.LENGTH_SHORT).show()
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                            performSearch(recognizedText, cardsContainer!!)
                    }
                    stopVoiceSearchMode()
                }
                
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "语音识别初始化失败", e)
            Toast.makeText(this, "语音识别初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceSearchMode() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请授予录音权限以使用语音搜索", Toast.LENGTH_LONG).show()
            return
        }

        try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
            recognizer?.startListening(intent)
            showVoiceSearchAnimation()
        } catch (e: Exception) {
            Log.e("FloatingService", "启动语音搜索失败", e)
            Toast.makeText(this, "启动语音搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopVoiceSearchMode() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            hideVoiceSearchAnimation()
        isVoiceSearchActive = false
        } catch (e: Exception) {
            Log.e("FloatingService", "停止语音搜索失败", e)
        }
    }

    private fun showVoiceSearchAnimation() {
        // 创建语音动画视图
        if (voiceAnimationView == null) {
            voiceAnimationView = LayoutInflater.from(this).inflate(R.layout.voice_search_animation, null)
        }

        // 设置动画视图的布局参数
        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(voiceAnimationView, params)
            
            // 开始动画
            val animation = AnimationUtils.loadAnimation(this, R.anim.voice_ripple)
            voiceAnimationView?.findViewById<View>(R.id.voice_ripple)?.startAnimation(animation)
        } catch (e: Exception) {
            Log.e("FloatingService", "显示语音动画失败", e)
        }
    }

    private fun hideVoiceSearchAnimation() {
        try {
            voiceAnimationView?.let {
                windowManager?.removeView(it)
                voiceAnimationView = null
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "隐藏语音动画失败", e)
        }
    }

    private fun showCardOptions(cardView: View, index: Int) {
        val popupMenu = android.widget.PopupMenu(this, cardView)
        val menu = popupMenu.menu

        // 添加菜单项
        menu.add("移动到顶部").setOnMenuItemClickListener {
            moveCardToTop(index)
            true
        }

        menu.add("移动到底部").setOnMenuItemClickListener {
            moveCardToBottom(index)
            true
        }

        menu.add("删除").setOnMenuItemClickListener {
            removeCard(index)
            true
        }

        popupMenu.show()
    }

    private fun moveCardToTop(index: Int) {
        if (index <= 0) return
        
        val card = cardsContainer?.getChildAt(index) ?: return
        cardsContainer?.removeViewAt(index)
        cardsContainer?.addView(card, 0)
        
        // 更新引擎顺序
        val engines = settingsManager.getEngineOrder().toMutableList()
        val engine = engines.removeAt(index)
        engines.add(0, engine)
        settingsManager.saveEngineOrder(engines)
        
        // 重新排列所有卡片
        rearrangeCards()
    }

    private fun moveCardToBottom(index: Int) {
        val totalCards = cardsContainer?.childCount ?: return
        if (index >= totalCards - 1) return
        
        val card = cardsContainer?.getChildAt(index) ?: return
        cardsContainer?.removeViewAt(index)
        cardsContainer?.addView(card)
        
        // 更新引擎顺序
        val engines = settingsManager.getEngineOrder().toMutableList()
        val engine = engines.removeAt(index)
        engines.add(engine)
        settingsManager.saveEngineOrder(engines)
        
        // 重新排列所有卡片
        rearrangeCards()
    }

    private fun removeCard(index: Int) {
        val totalCards = cardsContainer?.childCount ?: return
        if (index < 0 || index >= totalCards) return
        
        cardsContainer?.removeViewAt(index)
        aiWindows.removeAt(index)
        
        // 更新引擎顺序
        val engines = settingsManager.getEngineOrder().toMutableList()
        engines.removeAt(index)
        settingsManager.saveEngineOrder(engines)
        
        // 重新排列所有卡片
        rearrangeCards()
    }

    private fun rearrangeCards() {
        val totalCards = cardsContainer?.childCount ?: return
        for (i in 0 until totalCards) {
            val card = cardsContainer?.getChildAt(i) ?: continue
            card.translationY = (i * cardSpacing).toFloat()
        }
    }

    private fun setupMemoryMonitoring() {
        memoryCheckHandler = Handler(Looper.getMainLooper())
        val memoryCheckRunnable = object : Runnable {
            override fun run() {
                checkMemoryUsage()
                memoryCheckHandler?.postDelayed(this, MEMORY_CHECK_INTERVAL)
            }
        }
        memoryCheckHandler?.post(memoryCheckRunnable)
    }

    private fun checkMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // 如果内存使用增加超过阈值，执行清理
        if (usedMemory > lastMemoryUsage * 1.5) {
            System.gc()
            Log.d("MemoryCheck", "执行内存清理")
        }
        
        lastMemoryUsage = usedMemory
    }
    
    override fun onDestroy() {
        try {
            // 停止内存监控
            memoryCheckHandler?.removeCallbacksAndMessages(null)
            
            // 清理语音识别
            recognizer?.destroy()
            
            // 移除所有视图
            windowManager?.let { wm ->
                floatingBallView?.let { wm.removeView(it) }
                searchView?.let { wm.removeView(it) }
                voiceAnimationView?.let { wm.removeView(it) }
            }
            
            // 清理WebView
                aiWindows.forEach { webView ->
                webView.stopLoading()
                webView.clearHistory()
                webView.clearCache(true)
                    webView.destroy()
                }
            aiWindows.clear()
            
            // 清理缓存
            thumbnailCache.clear()
            thumbnailViews.clear()
            cachedCardViews.clear()
            
            super.onDestroy()
        } catch (e: Exception) {
            Log.e("FloatingService", "服务销毁时发生错误", e)
        }
    }

    // 实现GestureCallback接口方法
    override fun onGestureDetected(gesture: GestureManager.Gesture) {
        when (gesture) {
            GestureManager.Gesture.SWIPE_UP -> {
                if (!isSearchVisible) {
                    showSearchInput()
                }
            }
            GestureManager.Gesture.SWIPE_DOWN -> {
                if (isSearchVisible) {
                    searchView?.let { windowManager?.removeView(it) }
                    searchView = null
                    isSearchVisible = false
                }
            }
            GestureManager.Gesture.LONG_PRESS -> {
                quickMenuManager.showQuickMenu()
            }
        }
    }

    private fun performClick() {
        if (!isSearchVisible) {
            showSearchInput()
        }
    }

    private fun closeAllWindows() {
        aiWindows.forEach { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
        }
    }

    override fun onDoubleTap() {
        // Handle double tap gesture
        if (!isSearchVisible) {
            showSearchInput()
        }
    }

    private fun createWebView(): WebView {
        return WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                allowContentAccess = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                javaScriptCanOpenWindowsAutomatically = true
                defaultTextEncodingName = "UTF-8"
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
    }

    override fun onSingleTap() {
        if (!isSearchVisible) {
            showSearchInput()
        }
    }

    override fun onLongPress() {
        quickMenuManager.showQuickMenu()
    }

    override fun onSwipeLeft() {
        // Not used in current implementation
    }

    override fun onSwipeRight() {
        // Not used in current implementation
    }

    override fun onSwipeUp() {
        if (!isSearchVisible) {
            showSearchInput()
        }
    }

    override fun onSwipeDown() {
        if (isSearchVisible) {
            searchView?.let { windowManager?.removeView(it) }
            searchView = null
            isSearchVisible = false
        }
    }

    override fun onDrag(x: Float, y: Float) {
        floatingBallView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = x.toInt()
            params.y = y.toInt()
            
            // Ensure the ball stays within screen bounds
            params.x = params.x.coerceIn(-view.width / 3, screenWidth - view.width * 2 / 3)
            params.y = params.y.coerceIn(0, screenHeight - view.height)
            
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e("FloatingService", "更新悬浮球位置失败", e)
            }
        }
    }

    override fun onDragEnd(x: Float, y: Float) {
        // Save the final position if needed
    }
} 