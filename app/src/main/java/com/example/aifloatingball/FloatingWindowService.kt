package com.example.aifloatingball

import android.Manifest
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        private const val REQUEST_SCREENSHOT = 1001
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
    
    private lateinit var gestureDetector: GestureDetector
    
    private var screenshotModeStartTime: Long = 0
    
    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenshotActivity.ACTION_SCREENSHOT_COMPLETED) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - screenshotModeStartTime < 1000) {
                    // 如果间隔过短，忽略该广播事件
                    Log.d("FloatingService", "截图完成广播间隔过短，忽略")
                    return
                }
                
                Log.d("FloatingService", "收到截图完成广播")
                // 立即在主线程上执行恢复
                Handler(Looper.getMainLooper()).post {
                    Log.d("FloatingService", "开始恢复悬浮球状态")
                    restoreFloatingBall()
                }
            }
        }
    }
    
    private var isScreenshotMode = false  // 添加标记位
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        try {
            startTime = System.currentTimeMillis()
            setupMemoryMonitoring()
            Log.d("FloatingService", "服务开始创建")
            super.onCreate()
            
            // 注册截图完成广播接收器
            val filter = IntentFilter(ScreenshotActivity.ACTION_SCREENSHOT_COMPLETED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(
                    screenshotReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(screenshotReceiver, filter)
            }
            Log.d("FloatingService", "广播接收器注册成功")
            
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
            
            // Initialize GestureDetector
            gestureDetector = GestureDetector(this, object : GestureDetector.OnGestureListener {
                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onShowPress(e: MotionEvent) {
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    return false
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    return false
                }

                override fun onLongPress(e: MotionEvent) {
                    floatingBallView?.let { startScreenshotMode(it) }
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    return false
                }
            })
            
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
            quickMenuManager = QuickMenuManager(this, windowManager!!)
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
            
            // 创建通知渠道
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
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
                
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("悬浮球服务")
                .setContentText("点击管理悬浮球")
                .setSmallIcon(R.drawable.ic_floating_ball)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            startForeground(NOTIFICATION_ID, notification)
            Log.d("FloatingService", "前台服务启动成功")
        } catch (e: Exception) {
            Log.e("FloatingService", "前台服务启动失败", e)
            
            // 显示错误提示
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this, 
                    "前台服务启动失败：${e.message}\n将以普通服务运行", 
                    Toast.LENGTH_LONG
                ).show()
                
                // 尝试以普通服务启动
                try {
                    startService(Intent(this, FloatingWindowService::class.java))
                } catch (serviceStartException: Exception) {
                    Log.e("FloatingService", "普通服务启动失败", serviceStartException)
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "UPDATE_BALL_SIZE" -> {
                val size = intent.getIntExtra("size", 50)
                updateFloatingBallSize(size)
            }
            else -> {
                // 获取搜索引擎信息
                val engineName = intent?.getStringExtra("ENGINE_NAME")
                val engineUrl = intent?.getStringExtra("ENGINE_URL")
                val engineIcon = intent?.getIntExtra("ENGINE_ICON", R.drawable.ic_search_engine)
                val searchQuery = intent?.getStringExtra("SEARCH_QUERY")
                val shouldOpenUrl = intent?.getBooleanExtra("SHOULD_OPEN_URL", false) ?: false

                if (engineName != null && engineUrl != null && engineIcon != null) {
                    val engine = SearchEngine(engineName, engineUrl, engineIcon)
                    
                    // 构建实际URL
                    val url = if (!searchQuery.isNullOrEmpty()) {
                        engine.getSearchUrl(searchQuery)
                    } else {
                        engineUrl
                    }

                    // 创建并显示卡片
                    val cardsContainer = createCardsContainer()
                    val cardView = createAICardView(url, currentEngineIndex)
                    cardsContainer.addView(cardView)
                    
                    // 显示卡片容器
                    windowManager?.addView(cardsContainer, createCardsContainerLayoutParams())
                    
                    // 如果需要立即打开URL，展开卡片
                    if (shouldOpenUrl) {
                        expandCard(cardView, true)
                    }
                    
                    // 保存WebView到卡片列表
                    cardView.findViewById<WebView>(R.id.web_view)?.let { webView ->
                        aiWindows.add(webView)
                        // 如果需要立即打开URL，加载页面
                        if (shouldOpenUrl) {
                            webView.loadUrl(url)
                        }
                    }
                }
            }
        }
        return START_STICKY
    }
    
    private fun updateFloatingBallSize(size: Int) {
        val dpSize = (size * resources.displayMetrics.density).toInt()
        floatingBallView?.let { view ->
            // 更新根布局参数
            val params = view.layoutParams as WindowManager.LayoutParams
            params.width = dpSize
            params.height = dpSize
            
            // 获取悬浮球图标
            val icon = view.findViewById<ImageView>(R.id.floating_ball_icon)
            
            // 更新图标布局参数
            val iconParams = icon.layoutParams
            iconParams.width = dpSize
            iconParams.height = dpSize
            icon.layoutParams = iconParams
            
            // 计算图标的内边距（保持比例）
            val iconPadding = (dpSize * 0.167).toInt() // 保持原有的8dp/48dp的比例
            icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            
            // 确保没有阴影和轮廓
            icon.elevation = 0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                icon.outlineProvider = null
            }
            
            try {
                // 更新窗口布局
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e("FloatingService", "更新悬浮球大小失败", e)
            }
        }
    }
    
    private fun createFloatingBall() {
        try {
            // 创建悬浮球视图
            floatingBallView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
            
            // 设置悬浮球布局参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            // 设置初始位置
            params.gravity = Gravity.TOP or Gravity.START
            params.x = screenWidth - 100
            params.y = screenHeight / 2
            
            // 添加触摸事件监听
            floatingBallView?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        view.performClick()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        params.x = (initialX + deltaX).toInt()
                        params.y = (initialY + deltaY).toInt()
                        windowManager?.updateViewLayout(floatingBallView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - initialTouchX) < 5 && abs(event.rawY - initialTouchY) < 5) {
                            // 这是一个点击事件
                            val intent = Intent(this, SearchWebViewActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        }
                        true
                    }
                    else -> false
                }
            }
            
            // 添加到窗口
            windowManager?.addView(floatingBallView, params)
            
        } catch (e: Exception) {
            Log.e("FloatingService", "创建悬浮球失败", e)
            Toast.makeText(this, "创建悬浮球失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }
    
    private fun startScreenshotMode(view: View) {
        if (isScreenshotMode) {
            Log.d("FloatingService", "已经在截图模式中，忽略请求")
            return
        }
        
        Log.d("FloatingService", "开始截图模式")
        
        try {
            // 保存原始状态
            val originalState = Bundle().apply {
                putFloat("originalAlpha", view.alpha)
                putFloat("originalScaleX", view.scaleX)
                putFloat("originalScaleY", view.scaleY)
                putInt("originalColor", (view.background as? GradientDrawable)?.color?.defaultColor 
                    ?: android.graphics.Color.parseColor("#2196F3"))
            }
            view.tag = originalState
            isScreenshotMode = true
            screenshotModeStartTime = System.currentTimeMillis()

            // 改变悬浮球外观为红色
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.RED)
            }
            view.background = shape
            view.alpha = 0.5f
            view.scaleX = 1.5f
            view.scaleY = 1.5f

            // 启动系统截图
            val intent = Intent(this, ScreenshotActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("REQUEST_SCREENSHOT", REQUEST_SCREENSHOT)
            }
            startActivity(intent)
            
            // 启动延迟任务，如果2秒内没有收到广播，主动恢复悬浮球状态
            Handler(Looper.getMainLooper()).postDelayed({
                if (isScreenshotMode) {
                    Log.d("FloatingService", "截图完成广播超时，主动恢复悬浮球状态")
                    restoreFloatingBall()
                }
            }, 2000)
            
            // 直接返回，不再执行后面的代码
            return
        } catch (e: Exception) {
            Log.e("FloatingService", "启动截图模式失败", e)
            isScreenshotMode = false
            view.tag = null
            restoreFloatingBall()
        }
    }
    
    private fun restoreFloatingBall() {
        if (!isScreenshotMode) {
            Log.d("FloatingService", "当前不在截图模式，忽略恢复请求")
            return
        }
        
        Log.d("FloatingService", "开始恢复悬浮球状态")
        
        floatingBallView?.let { view ->
            try {
                val originalState = view.tag as? Bundle
                if (originalState == null) {
                    Log.e("FloatingService", "找不到原始状态，使用默认值")
                    // 使用默认值恢复
                    val defaultShape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(android.graphics.Color.parseColor("#2196F3"))
                    }
                    view.background = defaultShape
                    view.alpha = 1.0f
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                    isScreenshotMode = false
                    view.tag = null
                } else {
                    // 先恢复颜色
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(originalState.getInt("originalColor"))
                    }
                    view.background = shape

                    // 渐变动画恢复
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 300
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { animator ->
                            val fraction = animator.animatedFraction
                            view.scaleX = 1.5f + (originalState.getFloat("originalScaleX") - 1.5f) * fraction
                            view.scaleY = 1.5f + (originalState.getFloat("originalScaleY") - 1.5f) * fraction
                            view.alpha = 0.5f + (originalState.getFloat("originalAlpha") - 0.5f) * fraction
                        }
                        addListener(object : android.animation.Animator.AnimatorListener {
                            override fun onAnimationStart(animation: android.animation.Animator) {}
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                // 确保最终状态完全正确
                                view.scaleX = originalState.getFloat("originalScaleX")
                                view.scaleY = originalState.getFloat("originalScaleY")
                                view.alpha = originalState.getFloat("originalAlpha")
                                isScreenshotMode = false
                                view.tag = null
                                Log.d("FloatingService", "动画完成，状态已重置，isScreenshotMode: $isScreenshotMode")
                            }
                            override fun onAnimationCancel(animation: android.animation.Animator) {
                                // 确保在动画取消时也重置状态
                                view.scaleX = originalState.getFloat("originalScaleX")
                                view.scaleY = originalState.getFloat("originalScaleY")
                                view.alpha = originalState.getFloat("originalAlpha")
                                isScreenshotMode = false
                                view.tag = null
                                Log.d("FloatingService", "动画取消，状态已重置，isScreenshotMode: $isScreenshotMode")
                            }
                            override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        })
                        start()
                    }
                }

                Log.d("FloatingService", "悬浮球状态开始恢复（动画）")
            } catch (e: Exception) {
                Log.e("FloatingService", "恢复悬浮球状态失败", e)
                // 强制恢复默认状态
                val defaultShape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#2196F3"))
                }
                view.background = defaultShape
                view.alpha = 1.0f
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                isScreenshotMode = false
                view.tag = null
                Log.d("FloatingService", "强制恢复默认状态，isScreenshotMode: $isScreenshotMode")
            }
        }
        
        isScreenshotMode = false
        Log.d("FloatingService", "悬浮球状态恢复完毕，isScreenshotMode: $isScreenshotMode")
    }
    
    // 工具方法
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
    
    private fun showSearchInput() {
        try {
            // 获取剪贴板文本
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipboardText = clipboard.primaryClip?.let { 
                if (it.itemCount > 0) it.getItemAt(0).text?.toString() 
                else null 
            }

            // 启动搜索Activity，并传递剪贴板文本
            val intent = Intent(this, SearchActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipboardText?.let { 
                    putExtra("CLIPBOARD_TEXT", it) 
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("FloatingService", "启动搜索Activity失败", e)
            Toast.makeText(this, "启动搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
        
        // 使用最新的引擎配置
        val engines = settingsManager.getEngineOrder()
        val engine = engines[index]
        
        val titleBar = cardView.findViewById<View>(R.id.title_bar)
        val contentContainer = cardView.findViewById<ViewGroup>(R.id.content_container)
        val webView = cardView.findViewById<WebView>(R.id.web_view)
        
        cardView.findViewById<ImageView>(R.id.engine_icon).setImageResource(engine.iconResId)
        cardView.findViewById<TextView>(R.id.engine_name).text = engine.name
        
        setupWebView(webView, engine)
        
        // 设置WebView的布局参数
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // 确保WebView可见
        webView.visibility = View.VISIBLE
        
        // 设置WebView的焦点
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        
        // 立即加载URL
        webView.loadUrl(url)
        
        // 设置内容容器的布局参数
        contentContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            800  // 设置一个固定高度，确保内容可见
        )
        
        // 确保内容容器可见
        contentContainer.visibility = View.VISIBLE
        
        // 设置卡片的基本样式
        cardView.alpha = 0.95f
        cardView.translationY = index * 60f
        cardView.scaleX = 0.95f
        cardView.scaleY = 0.95f
        
        // 点击标题栏展开/折叠卡片
        titleBar.setOnClickListener {
            if (contentContainer.layoutParams.height == 800) {
                // 展开到全屏
                contentContainer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            } else {
                // 折叠到默认高度
                contentContainer.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    800
                )
            }
            contentContainer.requestLayout()
        }
        
        // 添加到WebView列表
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
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 启用所有必要的功能
            allowContentAccess = true
            allowFileAccess = true
            domStorageEnabled = true
            databaseEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            
            // 设置缓存模式
            cacheMode = WebSettings.LOAD_NO_CACHE
            
            // 设置编码
            defaultTextEncodingName = "UTF-8"
            
            // 设置UA
            val customUA = when (engine.name) {
                "豆包" -> "Mozilla/5.0 (Linux; Android 11; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.104 Mobile Safari/537.36"
                "ChatGPT" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                else -> userAgentString + " Mobile"
            }
            userAgentString = customUA
        }

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.proceed()
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后，确保WebView可见
                view?.visibility = View.VISIBLE
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url?.let { view?.loadUrl(it) }
                return true
            }
        }
        
        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    // 加载完成，确保WebView可见
                    view?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun expandCard(index: Int) {
        val cardView = cardsContainer?.getChildAt(index) ?: return
        activeCardIndex = index
        
        // 获取标题栏和内容区域
        val titleBar = cardView.findViewById<View>(R.id.title_bar)
        val contentContainer = cardView.findViewById<ViewGroup>(R.id.content_container)
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
                contentContainer.layoutParams = (contentContainer.layoutParams as LinearLayout.LayoutParams).apply {
                    height = expandedHeight - titleBarHeight
                    weight = 0f
                }
                
                // 将展开的卡片移动到屏幕中央
                val cardParams = cardView.layoutParams as LinearLayout.LayoutParams
                cardParams.topMargin = centerY
                cardView.layoutParams = cardParams
            }
            .start()
        
        // 处理其他卡片的位置
        val totalCards = cardsContainer?.childCount ?: 0
        for (i in 0 until totalCards) {
            if (i != index) {
                val otherCard = cardsContainer?.getChildAt(i) ?: continue
                val otherTitleBar = otherCard.findViewById<View>(R.id.title_bar)
                val otherContentContainer = otherCard.findViewById<ViewGroup>(R.id.content_container)
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
                        otherContentContainer.layoutParams = (otherContentContainer.layoutParams as LinearLayout.LayoutParams).apply {
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
    
    private fun collapseCard(_index: Int) {
        activeCardIndex = -1
        val initialCardSpacing = 8.dpToPx()  // 设置初始紧凑间距
        
        // 恢复所有卡片的状态
        for (i in 0 until (cardsContainer?.childCount ?: 0)) {
            val card = cardsContainer?.getChildAt(i) ?: continue
            
            // 获取标题栏和内容区域
            val titleBar = card.findViewById<View>(R.id.title_bar)
            val contentContainer = card.findViewById<ViewGroup>(R.id.content_container)
            val webView = card.findViewById<WebView>(R.id.web_view)
            
            // 计算目标Y位置（使用初始紧凑间距）
            var targetY = 0f
            for (j in 0 until i) {
                targetY += initialCardSpacing  // 使用初始紧凑间距
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
                    contentContainer.layoutParams = (contentContainer.layoutParams as LinearLayout.LayoutParams).apply {
                        height = 0
                        weight = 0f
                    }
                    
                    // 确保WebView完全隐藏
                    webView.visibility = View.GONE
                    
                    // 重置卡片位置和边距
                    val cardParams = card.layoutParams as LinearLayout.LayoutParams
                    cardParams.topMargin = if (i == 0) initialCardSpacing else 0
                    cardParams.bottomMargin = initialCardSpacing  // 使用初始紧凑间距
                    card.layoutParams = cardParams
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

        // 获取当前的引擎列表
        val engines = settingsManager.getEngineOrder()
        
        // 确保索引在有效范围内
        val safeIndex = index.coerceIn(0, engines.size - 1)
        val webView = cardView.findViewById<WebView>(R.id.web_view)

        // 添加菜单项：刷新页面
        menu.add("刷新页面").setOnMenuItemClickListener {
            webView.reload()
            true
        }

        // 添加菜单项：系统分享
        menu.add("分享").setOnMenuItemClickListener {
            try {
                // 异步获取页面标题和URL
                webView.evaluateJavascript(
                    "(function() { return { title: document.title, url: window.location.href }; })();",
                    { result ->
                        try {
                            // 解析 JSON 结果
                            val jsonObject = org.json.JSONObject(result)
                            val title = jsonObject.getString("title")
                            val url = jsonObject.getString("url")

                            // 在主线程上执行分享
                            Handler(Looper.getMainLooper()).post {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TITLE, title)
                                    putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                                }
                                
                                val chooserIntent = Intent.createChooser(
                                    shareIntent, 
                                    "分享 $title"
                                )
                                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(chooserIntent)
                            }
                        } catch (e: Exception) {
                            Log.e("FloatingService", "解析分享信息失败", e)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("FloatingService", "分享失败", e)
                Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // 添加菜单项：全屏
        menu.add("全屏").setOnMenuItemClickListener {
            // 异步获取页面URL
            webView.evaluateJavascript(
                "(function() { return window.location.href; })();",
                { result ->
                    // 移除可能的引号
                    val url = result.trim('"')
                    
                    // 在主线程上启动全屏 Activity
                    Handler(Looper.getMainLooper()).post {
                        val fullscreenIntent = Intent(this, FullscreenWebViewActivity::class.java).apply {
                            putExtra("URL", url)
                            putExtra("TITLE", engines[safeIndex].name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(fullscreenIntent)
                    }
                }
            )
            true
        }

        popupMenu.show()
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
            // 注销截图完成广播接收器
            unregisterReceiver(screenshotReceiver)
            
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
        try {
            // 清理WebView
            aiWindows.forEach { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.clearCache(true)
                (webView.parent as? ViewGroup)?.removeView(webView)
            }
            aiWindows.clear()
            
            // 重置卡片状态
            cardsContainer?.removeAllViews()
            cachedCardViews.clear()
            hasInitializedCards = false
            activeCardIndex = -1
            
            // 清理搜索相关状态
            isSearchVisible = false
            searchView = null
            
            // 回收系统资源
            System.gc()
        } catch (e: Exception) {
            Log.e("FloatingService", "关闭窗口时发生错误", e)
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

    private fun createCardsContainer(): ViewGroup {
        return FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun createCardsContainerLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
        }
    }

    private fun expandCard(cardView: View, animate: Boolean = true) {
        val container = cardView.findViewById<View>(R.id.content_container)
        val controlBar = cardView.findViewById<View>(R.id.control_bar)
        
        container.visibility = View.VISIBLE
        controlBar.visibility = View.VISIBLE
        
        if (animate) {
            cardView.alpha = 0f
            cardView.scaleX = 0.8f
            cardView.scaleY = 0.8f
            
            cardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
} 