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
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
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
import android.graphics.drawable.GradientDrawable

class FloatingWindowService : Service(), GestureManager.GestureCallback {
    companion object {
        private const val NOTIFICATION_ID = 1
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
                val channelId = "floating_ball_service"
                val channelName = "悬浮球服务"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
                
                startForeground(NOTIFICATION_ID, notification)
                Log.d("FloatingService", "前台服务启动成功")
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
            
            // 设置最高层级
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                root.elevation = 1000f  // 设置非常高的elevation确保在最上层
                root.translationZ = 1000f
            }
            
            // 添加悬浮球到窗口
            windowManager?.addView(root, params)
            
            // 设置手势监听
            floatingBallView?.let { view ->
                gestureManager.attachToView(view)
                Log.d("FloatingService", "已设置手势监听")
                
                // 设置触摸监听
                view.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // 记录初始位置
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            lastTouchX = event.rawX
                            lastTouchY = event.rawY
                            
                            // 启动长按检测
                            voiceSearchHandler.postDelayed({
                                if (!isDragging) {
                                    startVoiceSearchMode()
                                }
                            }, LONG_PRESS_TIMEOUT)
                            
                            // 展开悬浮球（如果处于收缩状态）
                            if (v.alpha < 1f) {
                                v.animate()
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                                    .start()
                            }
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val deltaX = event.rawX - lastTouchX
                            val deltaY = event.rawY - lastTouchY
                            
                            // 如果移动距离超过阈值，取消长按检测
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
                                
                                // 限制不超出屏幕
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
                            // 移除长按检测
                            voiceSearchHandler.removeCallbacksAndMessages(null)
                            
                            // 如果正在进行语音搜索，则结束语音搜索
                            if (isVoiceSearchActive) {
                                stopVoiceSearchMode()
                                isDragging = false
                                true
                            } else {
                                // 判断是否为点击事件
                                val isTap = !isDragging && abs(event.rawX - initialTouchX) < 5 && 
                                          abs(event.rawY - initialTouchY) < 5
                                
                                if (isTap) {
                    performClick()
                                } else {
                                    // 吸附到最近的边缘
                                    val params = v.layoutParams as WindowManager.LayoutParams
                                    val centerX = params.x + v.width / 2
                                    
                                    // 计算目标位置
                                    val targetX = if (centerX < screenWidth / 2) {
                                        -v.width / 3  // 左边缘，露出2/3
                                    } else {
                                        screenWidth - v.width * 2 / 3  // 右边缘，露出2/3
                                    }
                                    
                                    // 创建X轴动画
                                    val animatorX = ValueAnimator.ofInt(params.x, targetX).apply {
                                        duration = 200
                                        interpolator = DecelerateInterpolator()
                                        addUpdateListener { animation ->
                                            params.x = animation.animatedValue as Int
                                            try {
                                                windowManager?.updateViewLayout(v, params)
                                            } catch (e: Exception) {
                                                Log.e("FloatingService", "更新悬浮球位置失败", e)
                                            }
                                        }
                                    }
                                    
                                    // 创建缩放和透明度动画
                                    v.animate()
                                        .alpha(0.85f)
                                        .scaleX(0.85f)
                                        .scaleY(0.85f)
                                        .setDuration(200)
                                        .setInterpolator(DecelerateInterpolator())
                                        .start()
                                    
                                    // 启动动画
                                    animatorX.start()
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
        // 创建主容器
        val root = FrameLayout(this).apply {
            setOnClickListener {
                // 点击空白区域关闭搜索界面和卡片
                windowManager?.removeView(this)
                searchView = null
                isSearchVisible = false
                closeAllWindows()
            }
            
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F5F5F5"))  // 设置浅灰色背景
            }
        }

        // 创建一个垂直布局容器
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            
            // 防止点击事件传递到root
            setOnClickListener { }
        }
        
        // 创建顶部栏布局
        val topBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // 创建关闭按钮
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
        
        // 添加搜索框部分
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
        
        // 将关闭按钮和搜索框添加到顶部栏
        topBarLayout.addView(closeButton)
        topBarLayout.addView(searchContainer)
        
        // 创建ScrollView
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 16.dpToPx()
            }
            isVerticalScrollBarEnabled = true  // 显示滚动条
        }
        
        // 创建卡片容器
        val cardsContainerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 将卡片容器添加到ScrollView
        scrollView.addView(cardsContainerView)
        
        // 将顶部栏和ScrollView添加到主容器
        containerLayout.addView(topBarLayout)
        containerLayout.addView(scrollView)
        root.addView(containerLayout)
        
        searchView = root
        cardsContainer = cardsContainerView
        
        // 获取输入框和按钮
        val searchInput = searchContainer.findViewById<EditText>(R.id.search_input)
        val searchButton = searchContainer.findViewById<ImageButton>(R.id.search_button)
        val voiceButton = searchContainer.findViewById<ImageButton>(R.id.voice_input_button)
        
        // 设置搜索按钮点击事件
        searchButton?.setOnClickListener {
            val query = searchInput?.text?.toString()?.trim() ?: ""
            if (query.isNotEmpty()) {
                performSearch(query, cardsContainerView)
            }
        }
        
        // 设置语音按钮点击事件
        voiceButton?.setOnClickListener {
            startVoiceSearchMode()
        }
        
        // 设置输入框回车键监听
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
        
        // 设置窗口参数
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
        
        // 添加到窗口
        windowManager?.addView(root, params)
        
        // 请求输入框焦点
        searchInput?.apply {
            requestFocus()
            postDelayed({
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 200)
        }
        
        isSearchVisible = true
    }
    
    private fun performSearch(query: String, cardsContainer: ViewGroup) {
        try {
            if (query.isEmpty()) return
            
            // 保存查询内容到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("search_query", query)
            clipboard.setPrimaryClip(clip)
            
            // 保存查询内容
            lastQuery = query
            
            // 隐藏输入法
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            searchView?.findFocus()?.let { view ->
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            
            searchHistoryManager.addSearchQuery(query)
            
            // 构建基础URL（不带查询参数）
            val urls = listOf(
                "https://kimi.moonshot.cn/chat",
                "https://chat.deepseek.com/chat",
                "https://www.doubao.com/chat"
            )
            
            // 清除现有的卡片
            cardsContainer.removeAllViews()
            aiWindows.clear()
            
            // 创建新卡片
            urls.forEachIndexed { index, url ->
                try {
                    val cardView = createAICardView(url, index)
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
        // 创建卡片容器
        val cardContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.6f).toInt()
            ).apply {
                setMargins(16.dpToPx(), if (index == 0) 16.dpToPx() else 24.dpToPx(), 16.dpToPx(), 0)
            }
            
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.WHITE)
                cornerRadius = 16f.dpToPx()
                setStroke(1, android.graphics.Color.parseColor("#E0E0E0"))
            }
            
            elevation = 4f.dpToPx()
        }

        // 创建一个垂直布局来包含按钮和WebView
        val containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // 创建粘贴发送按钮
        val pasteButton = Button(this).apply {
            text = "粘贴发送"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                setMargins(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            }
            
            setOnClickListener {
                val webView = aiWindows[index]
                
                // 首先尝试获取剪贴板内容
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = clipboard.primaryClip
                val text = if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0).text.toString()
                } else {
                    ""
                }

                when {
                    url.contains("kimi.moonshot.cn") -> {
                        webView.evaluateJavascript("""
                            (function() {
                                function findTextarea() {
                                    return document.querySelector('.chat-input textarea') || 
                                           document.querySelector('.chat-input .textarea') ||
                                           document.querySelector('textarea') ||
                                           document.querySelector('[contenteditable="true"]');
                                }

                                function simulateInput(element, text) {
                                    console.log('Simulating input for Kimi...');
                                    
                                    // 聚焦元素
                                    element.focus();
                                    
                                    // 直接设置值
                                    element.value = text;
                                    
                                    // 触发多个事件
                                    const events = ['input', 'change', 'keydown', 'keyup', 'keypress'];
                                    events.forEach(eventType => {
                                        element.dispatchEvent(new Event(eventType, { bubbles: true }));
                                    });
                                    
                                    // 模拟输入事件
                                    const inputEvent = new InputEvent('input', {
                                        bubbles: true,
                                        cancelable: true,
                                        inputType: 'insertText',
                                        data: text
                                    });
                                    element.dispatchEvent(inputEvent);
                                    
                                    return true;
                                }

                                function simulateEnter(element) {
                                    console.log('Simulating enter for Kimi...');
                                    const enterEvent = new KeyboardEvent('keydown', {
                                        bubbles: true,
                                        cancelable: true,
                                        keyCode: 13,
                                        which: 13,
                                        key: 'Enter',
                                        code: 'Enter'
                                    });
                                    element.dispatchEvent(enterEvent);
                                    
                                    // 尝试点击发送按钮作为备选方案
                                    const sendButton = document.querySelector('.chat-input button[type="submit"]') ||
                                                     document.querySelector('button[type="submit"]') ||
                                                     document.querySelector('.send-button');
                                    if (sendButton) {
                                        sendButton.click();
                                    }
                                }

                                function attemptInput(text, maxAttempts = 10) {
                                    let attempts = 0;
                                    const interval = setInterval(() => {
                                        const textarea = findTextarea();
                                        if (textarea) {
                                            console.log('Found textarea for Kimi');
                                            clearInterval(interval);
                                            if (simulateInput(textarea, text)) {
                                                setTimeout(() => simulateEnter(textarea), 500);
                                            }
                                        } else {
                                            console.log('Attempt ' + (attempts + 1) + ' to find textarea');
                                            attempts++;
                                            if (attempts >= maxAttempts) {
                                                console.log('Failed to find textarea after ' + maxAttempts + ' attempts');
                                                clearInterval(interval);
                                            }
                                        }
                                    }, 500);
                                }

                                attemptInput(`${text}`);
                            })();
                        """.trimIndent(), null)
                    }
                    url.contains("chat.deepseek.com") -> {
                        webView.evaluateJavascript("""
                            (function() {
                                function findTextarea() {
                                    return document.querySelector('.overflow-hidden textarea') || 
                                           document.querySelector('.chat-input textarea') ||
                                           document.querySelector('textarea') ||
                                           document.querySelector('[contenteditable="true"]');
                                }

                                function simulateInput(element, text) {
                                    console.log('Simulating input for Deepseek...');
                                    
                                    // 聚焦元素
                                    element.focus();
                                    
                                    // 直接设置值
                                    element.value = text;
                                    
                                    // 触发多个事件
                                    const events = ['input', 'change', 'keydown', 'keyup', 'keypress'];
                                    events.forEach(eventType => {
                                        element.dispatchEvent(new Event(eventType, { bubbles: true }));
                                    });
                                    
                                    // 模拟输入事件
                                    const inputEvent = new InputEvent('input', {
                                        bubbles: true,
                                        cancelable: true,
                                        inputType: 'insertText',
                                        data: text
                                    });
                                    element.dispatchEvent(inputEvent);
                                    
                                    return true;
                                }

                                function simulateEnter(element) {
                                    console.log('Simulating enter for Deepseek...');
                                    const enterEvent = new KeyboardEvent('keydown', {
                                        bubbles: true,
                                        cancelable: true,
                                        keyCode: 13,
                                        which: 13,
                                        key: 'Enter',
                                        code: 'Enter'
                                    });
                                    element.dispatchEvent(enterEvent);
                                    
                                    // 尝试点击发送按钮作为备选方案
                                    const sendButton = document.querySelector('button[type="submit"]') ||
                                                     document.querySelector('.send-button') ||
                                                     document.querySelector('button:not([disabled])');
                                    if (sendButton) {
                                        sendButton.click();
                                    }
                                }

                                function attemptInput(text, maxAttempts = 10) {
                                    let attempts = 0;
                                    const interval = setInterval(() => {
                                        const textarea = findTextarea();
                                        if (textarea) {
                                            console.log('Found textarea for Deepseek');
                                            clearInterval(interval);
                                            if (simulateInput(textarea, text)) {
                                                setTimeout(() => simulateEnter(textarea), 500);
                                            }
                                        } else {
                                            console.log('Attempt ' + (attempts + 1) + ' to find textarea');
                                            attempts++;
                                            if (attempts >= maxAttempts) {
                                                console.log('Failed to find textarea after ' + maxAttempts + ' attempts');
                                                clearInterval(interval);
                                            }
                                        }
                                    }, 500);
                                }

                                attemptInput(`${text}`);
                            })();
                        """.trimIndent(), null)
                    }
                    url.contains("doubao.com") -> {
                        webView.evaluateJavascript("""
                            (function() {
                                function findTextarea() {
                                    return document.querySelector('.ant-input') || 
                                           document.querySelector('.chat-input') ||
                                           document.querySelector('textarea') ||
                                           document.querySelector('[contenteditable="true"]');
                                }

                                function simulateInput(element, text) {
                                    console.log('Simulating input for Doubao...');
                                    
                                    // 聚焦元素
                                    element.focus();
                                    
                                    // 直接设置值
                                    element.value = text;
                                    
                                    // 触发多个事件
                                    const events = ['input', 'change', 'keydown', 'keyup', 'keypress'];
                                    events.forEach(eventType => {
                                        element.dispatchEvent(new Event(eventType, { bubbles: true }));
                                    });
                                    
                                    // 模拟输入事件
                                    const inputEvent = new InputEvent('input', {
                                        bubbles: true,
                                        cancelable: true,
                                        inputType: 'insertText',
                                        data: text
                                    });
                                    element.dispatchEvent(inputEvent);
                                    
                                    return true;
                                }

                                function simulateEnter(element) {
                                    console.log('Simulating enter for Doubao...');
                                    const enterEvent = new KeyboardEvent('keydown', {
                                        bubbles: true,
                                        cancelable: true,
                                        keyCode: 13,
                                        which: 13,
                                        key: 'Enter',
                                        code: 'Enter'
                                    });
                                    element.dispatchEvent(enterEvent);
                                    
                                    // 尝试点击发送按钮作为备选方案
                                    const sendButton = document.querySelector('.ant-btn-primary') ||
                                                     document.querySelector('.send-button') ||
                                                     document.querySelector('button:not([disabled])');
                                    if (sendButton) {
                                        sendButton.click();
                                    }
                                }

                                function attemptInput(text, maxAttempts = 10) {
                                    let attempts = 0;
                                    const interval = setInterval(() => {
                                        const textarea = findTextarea();
                                        if (textarea) {
                                            console.log('Found textarea for Doubao');
                                            clearInterval(interval);
                                            if (simulateInput(textarea, text)) {
                                                setTimeout(() => simulateEnter(textarea), 500);
                                            }
                                        } else {
                                            console.log('Attempt ' + (attempts + 1) + ' to find textarea');
                                            attempts++;
                                            if (attempts >= maxAttempts) {
                                                console.log('Failed to find textarea after ' + maxAttempts + ' attempts');
                                                clearInterval(interval);
                                            }
                                        }
                                    }, 500);
                                }

                                attemptInput(`${text}`);
                            })();
                        """.trimIndent(), null)
                    }
                }
                
                // 显示提示
                Toast.makeText(this@FloatingWindowService, "正在发送消息...", Toast.LENGTH_SHORT).show()
            }
        }

        // 创建WebView
        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(8.dpToPx(), 0, 8.dpToPx(), 8.dpToPx())
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
                setInitialScale(100)
                
                // 允许混合内容
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                // 允许通用访问
                allowContentAccess = true
                allowFileAccess = true
                
                // 启用DOM存储API
                domStorageEnabled = true
                
                // 启用数据库存储API
                databaseEnabled = true
                
                // 设置缓存模式
                cacheMode = WebSettings.LOAD_NO_CACHE
                
                // 启用JavaScript接口
                javaScriptCanOpenWindowsAutomatically = true
                
                // 设置默认编码
                defaultTextEncodingName = "UTF-8"
                
                // 允许加载本地内容
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                
                // 设置UA
                userAgentString = userAgentString + " Mobile"
            }
            
            // 注入JavaScript接口
            addJavascriptInterface(JsInterface(lastQuery), "Android")
            
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // 在页面加载完成后，注入必要的 JavaScript
                    when {
                        url?.contains("kimi.moonshot.cn") == true -> {
                            view?.evaluateJavascript("""
                                (function() {
                                    // 监听输入框变化
                                    function setupInputListener() {
                                        const textarea = document.querySelector('.chat-input textarea, .chat-input .textarea');
                                        if (textarea) {
                                            textarea.addEventListener('input', function() {
                                                console.log('Input changed:', textarea.value);
                                            });
                                        }
                                    }
                                    
                                    // 每500ms检查一次元素是否存在
                                    let checkInterval = setInterval(() => {
                                        const textarea = document.querySelector('.chat-input textarea, .chat-input .textarea');
                                        const sendButton = document.querySelector('.chat-input button[type="submit"], .chat-input .send-button');
                                        
                                        if (textarea && sendButton) {
                                            console.log('Found Kimi elements');
                                            setupInputListener();
                                            clearInterval(checkInterval);
                                        }
                                    }, 500);
                                })();
                            """.trimIndent(), null)
                        }
                        url?.contains("chat.deepseek.com") == true -> {
                            view?.evaluateJavascript("""
                                (function() {
                                    function setupInputListener() {
                                        const textarea = document.querySelector('.overflow-hidden textarea, .chat-input textarea');
                                        if (textarea) {
                                            textarea.addEventListener('input', function() {
                                                console.log('Input changed:', textarea.value);
                                            });
                                        }
                                    }
                                    
                                    let checkInterval = setInterval(() => {
                                        const textarea = document.querySelector('.overflow-hidden textarea, .chat-input textarea');
                                        const sendButton = document.querySelector('button[type="submit"], .send-button');
                                        
                                        if (textarea && sendButton) {
                                            console.log('Found Deepseek elements');
                                            setupInputListener();
                                            clearInterval(checkInterval);
                                        }
                                    }, 500);
                                })();
                            """.trimIndent(), null)
                        }
                        url?.contains("doubao.com") == true -> {
                            view?.evaluateJavascript("""
                                (function() {
                                    function setupInputListener() {
                                        const textarea = document.querySelector('.ant-input, .chat-input');
                                        if (textarea) {
                                            textarea.addEventListener('input', function() {
                                                console.log('Input changed:', textarea.value);
                                            });
                                        }
                                    }
                                    
                                    let checkInterval = setInterval(() => {
                                        const textarea = document.querySelector('.ant-input, .chat-input');
                                        const sendButton = document.querySelector('.ant-btn-primary, .send-button');
                                        
                                        if (textarea && sendButton) {
                                            console.log('Found Doubao elements');
                                            setupInputListener();
                                            clearInterval(checkInterval);
                                        }
                                    }, 500);
                                })();
                            """.trimIndent(), null)
                        }
                    }
                }

                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.proceed()
                }
            }
            
            // 加载URL
            loadUrl(url)
        }

        // 将按钮和WebView添加到垂直布局中
        containerLayout.addView(pasteButton)
        containerLayout.addView(webView)
        
        // 将垂直布局添加到卡片容器中
        cardContainer.addView(containerLayout)
        aiWindows.add(webView)
        
        return cardContainer
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
                        cardsContainer?.let { container ->
                            performSearch(query, container)
                        }
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
    
    private fun updateOtherCards(currentIndex: Int, deltaY: Float) {
        val spacing = 60.dpToPx()
        aiWindows.forEachIndexed { index, webView ->
            if (index != currentIndex) {
                val parent = webView.parent as? View ?: return@forEachIndexed
                val params = parent.layoutParams as WindowManager.LayoutParams
                val distance = abs(index - currentIndex)
                val scale = 0.9f + (0.1f * (1f - min(distance, 2) / 2f))
                
                params.y = (screenHeight / 6 + (index * spacing) + 
                          (deltaY * (1f - min(distance, 2) / 2f))).toInt()
                
                parent.scaleX = scale
                parent.scaleY = scale
                parent.alpha = 0.7f + (0.3f * (1f - min(distance, 2) / 2f))
                
                try {
                    windowManager?.updateViewLayout(parent, params)
                } catch (e: Exception) {
                    Log.e("FloatingService", "更新其他卡片位置失败", e)
                }
            }
        }
    }
    
    private fun handleVerticalFling(index: Int, velocityY: Float) {
        val direction = if (velocityY > 0) 1 else -1
        val targetIndex = (index + direction).coerceIn(0, aiWindows.size - 1)
        if (targetIndex != index) {
            animateCardsSwap(index, targetIndex)
        }
    }
    
    private fun handleVerticalSwipe(index: Int, deltaY: Float) {
        val direction = if (deltaY > 0) 1 else -1
        val targetIndex = (index + direction).coerceIn(0, aiWindows.size - 1)
        if (targetIndex != index) {
            animateCardsSwap(index, targetIndex)
        }
    }
    
    private fun animateCardsSwap(fromIndex: Int, toIndex: Int) {
        val spacing = 60.dpToPx()
        val duration = 300L  // 减少动画时长
        
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            val params = parent.layoutParams as WindowManager.LayoutParams
            
            val targetY = screenHeight / 6 + (index * spacing)
            val scale = if (index == toIndex) 1f else 0.9f
            val alpha = if (index == toIndex) 1f else 0.7f
            val rotation = if (index == toIndex) 0f else (if (index < toIndex) 5f else -5f)  // 减小旋转角度
            
            // 使用加速减速插值器替代弹性插值器
            val interpolator = DecelerateInterpolator(1.5f)
            
            parent.animate()
                .y(targetY.toFloat())
                .scaleX(scale)
                .scaleY(scale)
                .alpha(alpha)
                .rotation(rotation)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withLayer()
                .withStartAction {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        parent.elevation = if (index == toIndex) 24f else 8f
                        parent.translationZ = if (index == toIndex) 12f else -abs(index - toIndex) * 4f
                    }
                }
                .withEndAction {
                    params.y = targetY
                    try {
                        windowManager?.updateViewLayout(parent, params)
                    } catch (e: Exception) {
                        Log.e("FloatingService", "更新卡片位置失败", e)
                    }
                }
                .start()
        }
        
        // 交换列表中的位置
        val temp = aiWindows[fromIndex]
        aiWindows[fromIndex] = aiWindows[toIndex]
        aiWindows[toIndex] = temp
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
        val isExpanded = params.height > (screenHeight * 0.75f).toInt()
        
        val targetHeight = if (isExpanded) {
            (screenHeight * 0.75f).toInt()
        } else {
            (screenHeight * 0.85f).toInt()
        }
        
        view.animate()
            .scaleX(if (isExpanded) 1f else 1.05f)
            .scaleY(if (isExpanded) 1f else 1.05f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
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

    private fun hideCardView() {
        isCardViewMode = false
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            
            // 添加退出动画
            parent.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .translationY(100f)
                .setDuration(200)
                .setStartDelay((aiWindows.size - index - 1) * 50L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                parent.visibility = View.GONE
            }
                .start()
            
            // 重置变换
            parent.elevation = 0f
            parent.translationZ = 0f
            parent.rotation = 0f
        }
    }
    
    private fun performClick() {
        if (isSearchVisible) {
            // 如果搜索框已显示，则隐藏
            searchView?.let { view ->
                val parent = view.parent as? View
                if (parent != null) {
                    windowManager?.removeView(parent)
                    searchView = null
                    isSearchVisible = false
                }
            }
            // 同时关闭所有卡片
            closeAllWindows()
        } else {
            // 显示统一的搜索界面
            showSearchInput()
        }
    }
    
    private fun startVoiceSearchMode() {
        if (isVoiceSearchActive) return
        
        isVoiceSearchActive = true
        
        // 创建波浪动画视图
        val waveView = FrameLayout(this).apply {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            
            background = object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 4f
                    color = android.graphics.Color.parseColor("#4CAF50")
                }
                
                private var animationValue = 0f
                private val waves = mutableListOf<Float>()
                private val maxWaves = 5
                private val waveInterval = 0.2f
                
                init {
                    for (i in 0 until maxWaves) {
                        waves.add(i * waveInterval)
                    }
                }
                
                override fun draw(canvas: android.graphics.Canvas) {
                    val centerX = bounds.width() / 2f
                    val centerY = bounds.height() / 2f
                    val maxRadius = kotlin.math.sqrt((centerX * centerX + centerY * centerY).toDouble()).toFloat()
                    
                    waves.forEachIndexed { index, offset ->
                        val radius = (animationValue + offset) * maxRadius
                        if (radius <= maxRadius) {
                            paint.alpha = ((1 - (radius / maxRadius)) * 255).toInt()
                            canvas.drawCircle(centerX, centerY, radius, paint)
                        }
                    }
                    
                    animationValue += 0.02f
                    if (animationValue >= 1f) {
                        animationValue = 0f
                    }
                    invalidateSelf()
                }
                
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity() = PixelFormat.TRANSLUCENT
            }
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        windowManager?.addView(waveView, params)
        voiceAnimationView = waveView
        
        // 启动语音识别
        startVoiceRecognition()
    }
    
    private fun stopVoiceSearchMode() {
        if (!isVoiceSearchActive) return
        
        isVoiceSearchActive = false
        
        // 移除波浪动画
        voiceAnimationView?.let { view ->
            windowManager?.removeView(view)
            voiceAnimationView = null
        }
        
        // 停止语音识别
        recognizer?.stopListening()
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
            cardsContainer?.let { container ->
                performSearch(query, container)
            }
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
                val index = ((aiWindows.size - 1) * (1 - progress)).toInt()
                currentCardIndex = index.coerceIn(0, aiWindows.size - 1)
            }
        } else {
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
                parent.alpha = 0f
                parent.scaleX = 0.8f
                parent.scaleY = 0.8f
                
                // 添加入场动画
                parent.animate()
                    .alpha(0.95f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setStartDelay((index * 50).toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                
                // 设置阴影和Z轴顺序
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    parent.elevation = (24f - abs(index - (aiWindows.size - 1) / 2) * 6f).coerceAtLeast(8f)
                    parent.translationZ = -abs(index - (aiWindows.size - 1) / 2) * 12f
                }
                
                // 确保每个WebView加载正确的URL
                webView.loadUrl(when (index) {
                    0 -> "https://kimi.moonshot.cn"
                    1 -> "https://chat.deepseek.com"
                    else -> "https://www.doubao.com"
                })
            }
        }

        val cardWidth = screenWidth - 48.dpToPx()
        val cardHeight = (screenHeight * 0.65f).toInt()  // 减小卡片高度
        val cardOverlap = cardWidth * 0.15f  // 卡片重叠的宽度
        val maxRotation = 5f  // 最大旋转角度
        
        // 计算中心卡片的索引
        val centerIndex = (aiWindows.size - 1) / 2
        
        aiWindows.forEachIndexed { index, webView ->
            val parent = webView.parent as? View ?: return@forEachIndexed
            val params = parent.layoutParams as WindowManager.LayoutParams
            val distanceFromCenter = index - centerIndex
            
            // 计算基础位置
            val baseX = screenWidth / 2 - cardWidth / 2 + (distanceFromCenter * (cardWidth - cardOverlap))
            
            // 应用位置和变换
                params.width = cardWidth
                params.height = cardHeight
            params.x = baseX.toInt()
            params.y = screenHeight / 2 - cardHeight / 2
            
            // 计算缩放和透明度
            val scale = 1f - (abs(distanceFromCenter) * 0.1f).coerceIn(0f, 0.2f)
            val alpha = 1f - (abs(distanceFromCenter) * 0.2f).coerceIn(0f, 0.4f)
            
            // 计算旋转角度
            val rotation = maxRotation * distanceFromCenter
            
            // 应用变换
                parent.scaleX = scale
                parent.scaleY = scale
                parent.alpha = alpha
                parent.rotation = rotation
            
            // 增强阴影效果
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                parent.elevation = (24f - abs(distanceFromCenter) * 6f).coerceAtLeast(8f)
                parent.translationZ = -abs(distanceFromCenter.toFloat()) * 12f
                
                // 为中心卡片添加更强的阴影
                if (index == centerIndex) {
                    parent.elevation = 32f
                    parent.translationZ = 16f
                }
            }
            
            // 添加卡片背景和边框
                parent.background = GradientDrawable().apply {
                    setColor(android.graphics.Color.WHITE)
                    cornerRadius = 24.dpToPx().toFloat()
                setStroke(2, android.graphics.Color.parseColor("#E0E0E0"))
            }
            
            try {
                windowManager?.updateViewLayout(parent, params)
            } catch (e: Exception) {
                Log.e("FloatingService", "更新卡片位置失败", e)
            }
        }
        
        // 如果下滑距离超过阈值，退出卡片模式
        if (progress > 0.5f) {
            hideCardView()
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
            cardsContainer = null  // 清除引用
            
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
} 