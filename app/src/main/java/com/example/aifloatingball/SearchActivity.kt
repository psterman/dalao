package com.example.aifloatingball

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.BaseSearchEngine
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.utils.WebViewConstants
import com.example.aifloatingball.download.EnhancedDownloadManager
import com.example.aifloatingball.download.DownloadManagerActivity
import com.example.aifloatingball.view.LetterIndexBar
import net.sourceforge.pinyin4j.PinyinHelper
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.webkit.URLUtil
import android.app.AlertDialog
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.appbar.AppBarLayout
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.graphics.Color
import androidx.appcompat.widget.SwitchCompat
import android.content.res.Resources
import com.example.aifloatingball.service.FloatingWindowService
import android.view.WindowManager
import android.graphics.PixelFormat
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.webview.PaperStackWebViewManager
import com.example.aifloatingball.service.FloatingVideoPlayerService
import com.example.aifloatingball.video.SystemOverlayVideoManager
import com.example.aifloatingball.video.FloatingVideoPlayerManager
import com.example.aifloatingball.webview.VideoDetectionBridge
import com.example.aifloatingball.R

class SearchActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var webView: WebView
    private lateinit var letterIndexBar: LetterIndexBar
    
    // 纸堆WebView相关
    private var paperStackManager: PaperStackWebViewManager? = null
    private var paperStackContainer: FrameLayout? = null
    private var paperStackControls: LinearLayout? = null
    private var paperCountText: TextView? = null
    private var addPaperButton: ImageButton? = null
    private var closeAllPapersButton: ImageButton? = null
    private var paperStackHint: TextView? = null
    private var isPaperStackMode = false
    
    // StackedCardPreview相关
    private var stackedCardPreview: com.example.aifloatingball.views.StackedCardPreview? = null
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private lateinit var closeButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var engineList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var searchInput: EditText
    private lateinit var searchEngineButton: ImageButton
    private lateinit var clearSearchButton: ImageButton
    private lateinit var faviconButton: ImageButton
    private lateinit var inputFavicon: ImageView
    private var currentSearchEngine: BaseSearchEngine? = null
    
    // 保存当前URL和标题，用于输入框显示
    private var currentDisplayUrl: String? = null
    private var currentDisplayTitle: String? = null
    private var isInputFocused = false
    
    // 下载提示按钮相关
    private lateinit var downloadIndicatorContainer: FrameLayout
    private lateinit var downloadIndicatorButton: ImageButton
    
    // 悬浮视频播放器相关
    private var isVideoPlayerActive = false
    private val videoDetectionHandler = Handler(Looper.getMainLooper())
    private var videoDetectionRunnable: Runnable? = null
    private var pendingVideoUrl: String? = null
    private var pendingPageUrl: String? = null
    
    // 视频接管相关
    private lateinit var systemOverlayVideoManager: SystemOverlayVideoManager
    private lateinit var floatingVideoManager: FloatingVideoPlayerManager
    
    // 悬浮窗权限请求结果处理器
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 检查权限是否已授予
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Log.d("SearchActivity", "悬浮窗权限已授予，启动悬浮播放器")
                // 权限已授予，启动之前待启动的悬浮播放器
                pendingVideoUrl?.let { videoUrl ->
                    pendingPageUrl?.let { pageUrl ->
                        // 获取当前WebView（可能是普通模式或纸堆模式）
                        val currentWebView = if (isPaperStackMode && paperStackManager != null) {
                            paperStackManager?.getCurrentTab()?.webView
                        } else {
                            webView
                        }
                        launchFloatingVideoPlayer(videoUrl, pageUrl, currentWebView)
                    }
                }
            } else {
                Log.w("SearchActivity", "用户拒绝了悬浮窗权限")
                Toast.makeText(
                    this,
                    "需要悬浮窗权限才能显示悬浮视频播放器",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        // 清除待启动的信息
        pendingVideoUrl = null
        pendingPageUrl = null
    }
    private lateinit var downloadProgressText: TextView
    private var activeDownloadCount = 0
    
    // 修改搜索引擎集合的类型定义
    private val searchEngines = mutableListOf<BaseSearchEngine>()
    private var modeToastView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Add flags to track receiver registration
    private var isSettingsReceiverRegistered = false
    private var isLayoutThemeReceiverRegistered = false
    
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentScale = 1f
    private var isScaling = false
    private var initialSpan = 0f
    private val MIN_SCALE_SPAN = 20f  // 降低触发阈值，让缩放更容易触发
    private val SCALE_VELOCITY_THRESHOLD = 0.02f  // 降低速度阈值，让缩放更灵敏
    private var lastScaleFactor = 1f
    private lateinit var gestureOverlay: View
    private lateinit var gestureHintView: TextView
    private var gestureHintHandler = Handler(Looper.getMainLooper())
    private var lastGestureHintRunnable: Runnable? = null
    
    // 自维护的URL历史栈，保证在WebView历史缺失时仍可回退
    private val urlBackStack: ArrayDeque<String> = ArrayDeque()
    
    // 当通过 StackedCardPreview 选择卡片时，等待纸堆完成切换后再隐藏预览，避免先露出首个页面闪烁
    private var pendingHideStackedPreview = false
    // 标识是否正在通过自维护历史恢复，避免在onPageFinished再次入栈
    private var isRestoringFromHistory = false
    
    // 手势状态追踪
    private var lastTapTime = 0L
    private var lastTapCount = 0
    private val DOUBLE_TAP_TIMEOUT = 300L
    private var isTwoFingerTap = false

    // 跟踪触摸点数量
    private var touchCount = 0
    private var lastTouchTime = 0L
    private val DOUBLE_TAP_TIMEOUT_TOUCH = 300L // 双指轻点的时间窗口
    
    // 两指上滑关闭相关变量
    private var twoFingerStartY = 0f
    private var twoFingerStartTime = 0L
    private var isTwoFingerSwipe = false
    private val TWO_FINGER_SWIPE_THRESHOLD = 100f // 两指上滑距离阈值（dp转px）
    private val TWO_FINGER_SWIPE_VELOCITY_THRESHOLD = 500f // 两指上滑速度阈值
    
    private var searchLayout: FrameLayout? = null
    private var searchHistorySwitch: SwitchCompat? = null
    private var autoPasteSwitch: SwitchCompat? = null
    
    private lateinit var rootLayout: View
    
    // 更新开关状态
    private fun updateSwitchStates() {
        try {
            // 更新搜索历史开关状态
            searchHistorySwitch?.isChecked = settingsManager.getBoolean("search_history_enabled", true)
            searchHistorySwitch?.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.putBoolean("search_history_enabled", isChecked)
            }
            
            // 更新自动粘贴开关状态
            autoPasteSwitch?.isChecked = settingsManager.isAutoPasteEnabled()
            autoPasteSwitch?.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setAutoPasteEnabled(isChecked)
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "更新开关状态时出错", e)
        }
    }
    
    // 添加搜索模式变更接收器
    private val searchModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 记录收到的所有广播，帮助调试
            Log.d("SearchActivity", "【广播接收器】收到广播: ${intent?.action}")
            Log.d("SearchActivity", "【广播接收器】Intent extras: ${intent?.extras?.keySet()?.joinToString()}")
            
            try {
                // 检查是否是测试广播
                if (intent?.getBooleanExtra("test", false) == true) {
                    Log.d("SearchActivity", "【广播接收器】收到测试广播，接收器工作正常")
                    Toast.makeText(context, "搜索模式广播接收器工作正常", Toast.LENGTH_SHORT).show()
                    return
                }
                
                // 处理搜索模式变更广播
                if (intent?.action == "com.example.aifloatingball.SEARCH_MODE_CHANGED") {
                    val isAIMode = intent.getBooleanExtra("is_ai_mode", false)
                    Log.d("SearchActivity", "【广播接收器】收到搜索模式变更广播: ${if (isAIMode) "AI模式" else "普通模式"}")
                    
                    // 获取当前设置中的模式
                    val currentMode = settingsManager.isDefaultAIMode()
                    Log.d("SearchActivity", "【广播接收器】当前设置中的模式: ${if (currentMode) "AI模式" else "普通模式"}")
                    
                    // 确保设置和广播一致
                    if (isAIMode != currentMode) {
                        Log.d("SearchActivity", "【广播接收器】广播模式与设置不一致，使用广播中的模式")
                    }
                    
                    // 显示模式变更提示
                    showModeChangeToast(isAIMode)
                    
                    // 强制刷新搜索引擎列表
                    searchEngines.clear()
                    loadSearchEngines(forceRefresh = true)
                    
                    // 刷新字母索引栏和搜索引擎列表
                    val currentLetter = letterTitle.text?.toString()?.firstOrNull() ?: 'A'
                    Log.d("SearchActivity", "【广播接收器】重新初始化字母索引栏和引擎列表，当前字母: $currentLetter")
                    
                    // 重新初始化字母索引栏
                    runOnUiThread {
                        try {
                            // 更新字母标题
                            letterTitle.text = currentLetter.toString()
                            
                            // 加载与当前字母匹配的搜索引擎
                            showSearchEnginesByLetter(currentLetter)
                            
                            Log.d("SearchActivity", "【广播接收器】UI更新完成，模式: ${if (isAIMode) "AI模式" else "普通模式"}")
                        } catch (e: Exception) {
                            Log.e("SearchActivity", "【广播接收器】更新UI时出错", e)
                            Toast.makeText(context, "更新搜索引擎列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                // 记录接收器中的任何异常
                Log.e("SearchActivity", "【广播接收器】处理广播时出错", e)
                Toast.makeText(context, "处理模式变更广播失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    companion object {
        private const val TAG = "SearchActivity"
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private val NORMAL_SEARCH_ENGINES = SearchEngine.DEFAULT_ENGINES
    }
    
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_SETTINGS_CHANGED &&
                intent.getBooleanExtra(Constants.EXTRA_LEFT_HANDED_MODE_CHANGED, false)) {
                updateLayoutForHandedness()
            }
        }
    }
    
    private val layoutThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.LAYOUT_THEME_CHANGED") {
                // 更新 WebView 的主题
                updateWebViewTheme()
                // 更新字母索引栏和搜索引擎面板的主题
                updateLetterIndexBarTheme()
                updateEngineListTheme()
                // 重新加载当前页面以应用新主题
                webView.reload()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查是否需要使用悬浮窗模式
        if (intent.getBooleanExtra("use_floating_window", false)) {
            val url = intent.getStringExtra("url")
            if (!url.isNullOrEmpty()) {
                // 使用字母列表的悬浮窗方式打开URL
                openInLetterListFloatingWindow(url)
                finish() // 关闭当前Activity
                return
            }
        }
        
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        
        setContentView(R.layout.activity_search)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // Initialize rootLayout
        rootLayout = findViewById(android.R.id.content)
        
        // 尝试查找布局和开关
        searchLayout = findViewById(R.id.webview_container) as? FrameLayout
        searchHistorySwitch = findViewById<SwitchCompat>(R.id.search_history_switch)
        autoPasteSwitch = findViewById<SwitchCompat>(R.id.auto_paste_switch)
        
        // 更新开关状态
        updateSwitchStates()
        
        try {
            // Initialize views and gesture detectors
            initViews()
            initGestureDetectors()
            
            // Register receivers
            registerReceivers()
            
            // Setup UI components
            setupWebView()
            setupLetterIndexBar()
            setupDrawer()
            updateLayoutForHandedness()

            // 检查是否是通过"应用更改"按钮启动的
            val shouldRefreshMode = intent.getBooleanExtra("refresh_mode", false)
            if (shouldRefreshMode) {
                // 从Intent获取AI模式设置
                val isAIMode = intent.getBooleanExtra("is_ai_mode", settingsManager.isDefaultAIMode())
                Log.d("SearchActivity", "通过Intent启动并刷新，设置模式为: ${if (isAIMode) "AI模式" else "普通模式"}")
                
                // 确保设置与Intent一致
                if (isAIMode != settingsManager.isDefaultAIMode()) {
                    Log.d("SearchActivity", "Intent中的模式与设置不一致，更新设置")
                    settingsManager.setDefaultAIMode(isAIMode)
                }
                
                // 显示模式切换提示
                showModeChangeToast(isAIMode)
            }

            // Load default search engine if opened from floating ball
            if (intent.getBooleanExtra("from_floating_ball", false)) {
                loadDefaultSearchEngine()
            }

            // Apply initial themes
            updateLetterIndexBarTheme()
            updateEngineListTheme()
            
            // 在初始化完成后加载搜索引擎列表
            loadSearchEngines(forceRefresh = true)

            // Check clipboard after initialization
            checkClipboard()
            
            // 默认加载搜索引擎主页（保留横移系统的主页功能）
            loadSearchEngineHomepage()
            
            // 打印日志，记录启动状态
            val isAIMode = settingsManager.isDefaultAIMode()
            Log.d("SearchActivity", "SearchActivity启动完成，当前模式: ${if (isAIMode) "AI模式" else "普通模式"}")
        } catch (e: Exception) {
            Log.e("SearchActivity", "Error initializing views", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        webView = findViewById(R.id.web_view)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)
        closeButton = findViewById(R.id.btn_close)
        menuButton = findViewById(R.id.btn_menu)
        appBarLayout = findViewById(R.id.appbar)
        engineList = findViewById(R.id.engine_list)
        previewEngineList = findViewById(R.id.preview_engine_list)
        previewEngineList.orientation = LinearLayout.VERTICAL
        progressBar = findViewById(R.id.progress_bar)
        gestureHintView = findViewById(R.id.gesture_hint)
        
        // Initialize search views
        searchInput = findViewById(R.id.search_input)
        searchEngineButton = findViewById(R.id.btn_search_engine)
        clearSearchButton = findViewById(R.id.btn_clear_search)
        faviconButton = findViewById(R.id.btn_favicon)
        inputFavicon = findViewById(R.id.input_favicon)

        // Initialize download indicator views
        downloadIndicatorContainer = findViewById(R.id.download_indicator_container)
        downloadIndicatorButton = findViewById(R.id.btn_download_indicator)
        downloadProgressText = findViewById(R.id.download_progress_text)

        // Initialize paper stack views
        val paperStackLayout = findViewById<View>(R.id.paper_stack_layout)
        paperStackContainer = paperStackLayout.findViewById(R.id.paper_stack_container)
        paperStackControls = paperStackLayout.findViewById(R.id.paper_stack_controls)
        paperCountText = paperStackLayout.findViewById(R.id.paper_count_text)
        addPaperButton = paperStackLayout.findViewById(R.id.btn_add_paper)
        closeAllPapersButton = paperStackLayout.findViewById(R.id.btn_close_all_papers)
        paperStackHint = paperStackLayout.findViewById(R.id.paper_stack_hint)

        // Initialize StackedCardPreview
        stackedCardPreview = findViewById(R.id.stacked_card_preview)
        
        // 确保StackedCardPreview正确初始化
        initializeStackedCardPreview()

        // 初始化时隐藏进度条
        progressBar.visibility = View.GONE
        gestureHintView.visibility = View.GONE

        // 设置基本点击事件
        setupBasicClickListeners()
        
        // 设置favicon按钮点击事件
        setupFaviconButton()
        
        // 设置搜索相关事件
        setupSearchViews()

        setupEngineList()
        
        // 获取当前搜索模式
        val isAIMode = settingsManager.isDefaultAIMode()
        Log.d("SearchActivity", "初始化视图，当前搜索模式: ${if (isAIMode) "AI模式" else "普通模式"}")
        
        // 强制更新搜索引擎列表
        loadSearchEngines(forceRefresh = true)
        
        // Initialize letter index bar - 始终使用普通搜索引擎列表以满足类型要求
        letterIndexBar.engines = NORMAL_SEARCH_ENGINES.map { it as SearchEngine }
        Log.d("SearchActivity", "字母索引栏设置了 ${NORMAL_SEARCH_ENGINES.size} 个搜索引擎")
        
        // Set initial search engine
        currentSearchEngine = NORMAL_SEARCH_ENGINES.firstOrNull()
        updateSearchEngineIcon()
    }

    private fun initGestureDetectors() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                
                val distanceX = e2.x - e1.x
                val distanceY = e2.y - e1.y
                
                // 检测水平滑动
                if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(velocityX) > 1000) {
                    if (distanceX > 0) {
                        // 右滑回退：统一使用handleBackNavigation，确保返回上一浏览界面
                        val handled = handleBackNavigation()
                        if (handled) {
                            showGestureHint("返回上一页")
                            return true
                        }
                        // 无可回退时交给默认：结束当前Activity（不跳转主页）
                        finish()
                        return true
                    } else if (distanceX < 0 && webView.canGoForward()) {
                        showGestureHint("前进下一页")
                        webView.goForward()
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 获取屏幕高度和点击位置
                val screenHeight = webView.height
                val tapY = e.y

                // 判断点击位置是在屏幕上半部分还是下半部分
                val scrollToTop = tapY < screenHeight / 2

                webView.evaluateJavascript("""
                    (function() {
                        window.scrollTo({
                            top: ${if (scrollToTop) "0" else "document.documentElement.scrollHeight"},
                            behavior: 'smooth'
                        });
                        return '${if (scrollToTop) "top" else "bottom"}';
                    })()
                """) { result ->
                    val destination = result?.replace("\"", "") ?: "top"
                    showGestureHint(if (destination == "top") "返回顶部" else "滚动到底部")
                }
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var baseScale = 1f
            private var lastSpan = 0f
            
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                baseScale = webView.scale
                lastSpan = detector.currentSpan
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // 计算手指间距离的变化比例
                val spanRatio = detector.currentSpan / lastSpan
                lastSpan = detector.currentSpan
                
                // 使用比例计算新的缩放值，并添加阻尼效果
                val dampingFactor = 0.8f // 阻尼系数，使缩放更平滑
                val scaleFactor = 1f + (spanRatio - 1f) * dampingFactor
                
                val newScale = baseScale * scaleFactor
                
                // 限制缩放范围并应用缩放
                if (newScale in 0.1f..5.0f) {
                    webView.setInitialScale((newScale * 100).toInt())
                    baseScale = newScale
                    
                    // 只在缩放比例变化显著时显示提示
                    if (Math.abs(newScale - currentScale) > 0.02f) {
                        showGestureHint("缩放: ${(newScale * 100).toInt()}%")
                        currentScale = newScale
                    }
                    return true
                }
                return false
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                baseScale = webView.scale
            }
        })
    }

    // 长按相关变量
    
    /**
     * 调试触摸事件流向
     */
    private fun debugTouchEventFlow(event: MotionEvent, source: String) {
        Log.d("SearchActivity", "🔍 触摸事件调试 [$source]: action=${event.action}, x=${event.x}, y=${event.y}")
        
        // 检查StackedCardPreview状态
        val previewVisible = stackedCardPreview?.visibility == View.VISIBLE
        val previewElevation = stackedCardPreview?.elevation ?: 0f
        val previewClickable = stackedCardPreview?.isClickable ?: false
        
        Log.d("SearchActivity", "🔍 StackedCardPreview状态: visible=$previewVisible, elevation=$previewElevation, clickable=$previewClickable")
        
        // 检查纸堆模式状态
        val paperStackVisible = isPaperStackMode && paperStackManager != null
        Log.d("SearchActivity", "🔍 纸堆模式状态: visible=$paperStackVisible")
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)

        // 调试触摸事件
        debugTouchEventFlow(ev, "dispatchTouchEvent")

        // 如果抽屉已经打开，优先让抽屉处理触摸事件
        if (drawerLayout.isDrawerOpen(GravityCompat.START) || drawerLayout.isDrawerOpen(GravityCompat.END)) {
            return super.dispatchTouchEvent(ev)
        }

        // 如果StackedCardPreview正在显示，将事件交由其自身处理，避免被 Activity 吞掉
        if (stackedCardPreview?.visibility == View.VISIBLE) {
            Log.d("SearchActivity", "🔒 StackedCardPreview可见，转交触摸事件: ${ev.action}")
            return stackedCardPreview?.dispatchTouchEvent(ev) ?: true
        }

        // 去掉长按激活StackedCardPreview功能，确保tab区域长期可以使用手势
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTapCount = 1
                lastTapTime = System.currentTimeMillis()
                isTwoFingerTap = false
            }
            MotionEvent.ACTION_UP -> {
                if (isTwoFingerTap &&
                    System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT &&
                    !isScaling) {
                    // 双指轻点刷新
                    showGestureHint("正在刷新页面")
                    webView.reload()
                    return true
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 2) {
                    lastTapCount = 2
                    isTwoFingerTap = true
                    // 记录两指起始位置和时间，用于检测上滑手势
                    twoFingerStartY = (ev.getY(0) + ev.getY(1)) / 2f
                    twoFingerStartTime = System.currentTimeMillis()
                    isTwoFingerSwipe = false
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (ev.pointerCount == 2 && isTwoFingerSwipe) {
                    // 两指上滑完成，关闭页面
                    val endY = (ev.getY(0) + ev.getY(1)) / 2f
                    val distance = twoFingerStartY - endY // 向上滑动，distance为正
                    val time = System.currentTimeMillis() - twoFingerStartTime
                    val velocity = if (time > 0) (distance / time) * 1000 else 0f
                    
                    // 转换为像素
                    val density = resources.displayMetrics.density
                    val thresholdPx = TWO_FINGER_SWIPE_THRESHOLD * density
                    
                    if (distance > thresholdPx && velocity > TWO_FINGER_SWIPE_VELOCITY_THRESHOLD) {
                        showGestureHint("正在关闭页面...")
                        // 添加淡出动画
                        window.decorView.animate()
                            .alpha(0f)
                            .translationY(-window.decorView.height.toFloat())
                            .setDuration(300)
                            .withEndAction {
                                finish()
                                overridePendingTransition(0, 0)
                            }
                            .start()
                        return true
                    }
                    isTwoFingerSwipe = false
                }
            }
        }

        // 如果是纸堆模式且StackedCardPreview不可见，处理纸堆的触摸事件
        if (isPaperStackMode && paperStackManager != null && stackedCardPreview?.visibility != View.VISIBLE) {
            val handled = paperStackManager?.onTouchEvent(ev) ?: false
            if (handled) {
                Log.d("SearchActivity", "纸堆触摸事件已处理: ${ev.action}")
                return true
            }
        }

        // 处理两指上滑手势（在缩放之前检测）
        if (ev.pointerCount == 2 && ev.actionMasked == MotionEvent.ACTION_MOVE) {
            val currentY = (ev.getY(0) + ev.getY(1)) / 2f
            val distance = twoFingerStartY - currentY // 向上滑动，distance为正
            val density = resources.displayMetrics.density
            val thresholdPx = TWO_FINGER_SWIPE_THRESHOLD * density
            
            // 检测是否满足上滑条件（向上滑动且距离足够）
            if (distance > thresholdPx * 0.3f && !isScaling) {
                isTwoFingerSwipe = true
                // 显示提示
                val progress = (distance / (thresholdPx * 2f)).coerceIn(0f, 1f)
                if (progress > 0.5f) {
                    showGestureHint("继续上滑关闭页面")
                }
            }
        }
        
        // 处理缩放手势
        scaleGestureDetector.onTouchEvent(ev)

        // 如果是双指操作或正在缩放，不传递给 WebView
        if (ev.pointerCount > 1 || isScaling) {
            return true
        }

        // 只有在抽屉关闭时才处理单指手势（滑动导航等）
        if (!drawerLayout.isDrawerOpen(GravityCompat.START) && !drawerLayout.isDrawerOpen(GravityCompat.END)) {
            gestureDetector.onTouchEvent(ev)
        }

        // 对于单指操作，传递给父类处理
        return super.dispatchTouchEvent(ev)
    }

    private fun showGestureHint(message: String) {
        // 取消之前的提示
        lastGestureHintRunnable?.let { gestureHintHandler.removeCallbacks(it) }
        
        // 显示新提示
        gestureHintView.text = message
        gestureHintView.alpha = 1f
        gestureHintView.visibility = View.VISIBLE
        
        // 创建淡出动画
        gestureHintView.animate()
            .alpha(0f)
            .setDuration(1000)
            .setStartDelay(500)
            .withEndAction {
                gestureHintView.visibility = View.GONE
            }
            .start()
        
        // 设置自动隐藏
        lastGestureHintRunnable = Runnable {
            gestureHintView.visibility = View.GONE
        }
        gestureHintHandler.postDelayed(lastGestureHintRunnable!!, 1500)
    }

    private fun setupBasicClickListeners() {
        // 设置菜单按钮点击事件：如果有网页加载，立即激活StackedCardPreview
        menuButton.setOnClickListener {
            // 检查是否有纸堆标签页可以显示（有网页加载）
            val paperStackTabs = paperStackManager?.getAllTabs() ?: emptyList()
            if (paperStackTabs.isNotEmpty()) {
                // 有标签页（有网页加载），立即激活StackedCardPreview
                Log.d("SearchActivity", "点击菜单按钮立即激活StackedCardPreview")
                activateStackedCardPreview()
            } else {
                // 没有标签页，显示菜单选项
                Log.d("SearchActivity", "没有标签页，显示菜单选项")
                showMenuOptions()
            }
        }

        // 设置关闭按钮点击事件
        closeButton.setOnClickListener {
            finish()
        }
        
        // 设置下载提示按钮点击事件
        downloadIndicatorButton.setOnClickListener {
            openDownloadManager()
        }
        
        // 设置纸堆相关按钮点击事件
        setupPaperStackClickListeners()
    }

    
    /**
     * 设置纸堆相关按钮的点击事件
     */
    private fun setupPaperStackClickListeners() {
        addPaperButton?.setOnClickListener {
            addNewTab()
        }
        
        closeAllPapersButton?.setOnClickListener {
            closeAllTabs()
        }
    }
    
    /**
     * 设置favicon按钮
     */
    private fun setupFaviconButton() {
        Log.d("SearchActivity", "setupFaviconButton: 初始化favicon按钮")
        
        // 添加调试：检查按钮是否在布局中
        faviconButton.post {
            Log.d("SearchActivity", "favicon按钮初始化完成, visibility=${faviconButton.visibility}, width=${faviconButton.width}, height=${faviconButton.height}, parent=${faviconButton.parent}")
            Log.d("SearchActivity", "favicon按钮位置 - x: ${faviconButton.x}, y: ${faviconButton.y}")
        }
        
        faviconButton.setOnClickListener {
            Log.d("SearchActivity", "favicon按钮被点击")
            showWebsiteInfoMenu()
        }
        
        // 初始状态：显示favicon按钮（常驻显示）
        faviconButton.visibility = View.VISIBLE
        faviconButton.setImageResource(android.R.drawable.ic_menu_search)
        
        // 初始状态：显示输入框内的图标（常驻显示）
        inputFavicon.visibility = View.VISIBLE
        inputFavicon.setImageResource(android.R.drawable.ic_menu_search)
    }
    
    /**
     * 更新favicon按钮图标
     */
    private fun updateFaviconButton(icon: Bitmap?, url: String?) {
        // 确保按钮始终可见（常驻显示）
        faviconButton.visibility = View.VISIBLE
        
        // 检查是否有有效URL
        val hasValidUrl = url != null && url.isNotEmpty() && url != "about:blank"
        
        if (icon != null) {
            // 有favicon，设置图标
            faviconButton.setImageBitmap(icon)
            // 同时更新输入框内的图标
            inputFavicon.setImageBitmap(icon)
            Log.d("SearchActivity", "更新favicon按钮: 使用Bitmap图标, URL: $url")
        } else if (hasValidUrl && url != null) {
            // 没有favicon但有效URL，显示默认图标并尝试加载
            faviconButton.setImageResource(android.R.drawable.ic_menu_search)
            inputFavicon.setImageResource(android.R.drawable.ic_menu_search)
            Log.d("SearchActivity", "更新favicon按钮: 显示默认图标, URL: $url")
            // 尝试从URL加载favicon（异步加载，不会阻塞UI）
            loadFaviconFromUrl(url)
        } else {
            // 没有有效URL，显示默认图标（按钮常驻）
            faviconButton.setImageResource(android.R.drawable.ic_menu_search)
            inputFavicon.setImageResource(android.R.drawable.ic_menu_search)
            Log.d("SearchActivity", "更新favicon按钮: 无URL，显示默认图标")
        }
    }
    
    /**
     * 从URL加载favicon
     */
    private fun loadFaviconFromUrl(url: String) {
        if (url.isEmpty() || url == "about:blank") {
            Log.d("SearchActivity", "loadFaviconFromUrl: 无效URL，跳过加载")
            return
        }
        
        Log.d("SearchActivity", "loadFaviconFromUrl: 开始加载favicon, URL: $url")
        
        // 确保按钮可见（使用默认图标）
        faviconButton.visibility = View.VISIBLE
        if (faviconButton.drawable == null) {
            faviconButton.setImageResource(android.R.drawable.ic_menu_search)
        }
        
        // 确保输入框内的图标可见（使用默认图标）
        inputFavicon.visibility = View.VISIBLE
        if (inputFavicon.drawable == null) {
            inputFavicon.setImageResource(android.R.drawable.ic_menu_search)
        }
        
        // 使用FaviconLoader加载favicon（异步加载）
        com.example.aifloatingball.utils.FaviconLoader.loadFavicon(faviconButton, url)
        
        // 同时为输入框内的图标创建加载任务
        com.example.aifloatingball.utils.FaviconLoader.loadFavicon(inputFavicon, url)
    }
    
    /**
     * 显示网站信息菜单（参考Alook的实现）
     */
    private fun showWebsiteInfoMenu() {
        // 尝试多种方式获取URL - 优先从WebView获取，因为tab.url可能未更新
        val currentWebView = if (isPaperStackMode) {
            paperStackManager?.getCurrentTab()?.webView ?: webView
        } else {
            webView
        }
        
        var currentUrl = currentWebView.url
        
        // 如果WebView的URL为空，尝试从搜索框获取
        if (currentUrl.isNullOrEmpty() || currentUrl == "about:blank") {
            val searchText = searchInput.text.toString().trim()
            if (searchText.isNotEmpty() && (searchText.startsWith("http://") || searchText.startsWith("https://"))) {
                currentUrl = searchText
            }
        }
        
        val currentTitle = if (isPaperStackMode) {
            paperStackManager?.getCurrentTab()?.title ?: currentWebView.title
        } else {
            currentWebView.title
        }
        
        // 添加调试日志
        Log.d("SearchActivity", "showWebsiteInfoMenu - URL: $currentUrl, Title: $currentTitle, isPaperStackMode: $isPaperStackMode")
        
        // 如果仍然没有URL，使用默认值或显示提示
        if (currentUrl.isNullOrEmpty() || currentUrl == "about:blank") {
            // 即使没有URL，也显示对话框，但显示提示信息
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_website_info, null)
            
            val websiteUrl = dialogView.findViewById<TextView>(R.id.website_url)
            val websiteName = dialogView.findViewById<TextView>(R.id.website_name)
            val certificateInfo = dialogView.findViewById<LinearLayout>(R.id.certificate_info)
            
            websiteUrl.text = "当前没有已加载的网页"
            websiteName.text = "请先加载网页"
            certificateInfo.visibility = View.GONE
            
            // 禁用菜单项
            dialogView.findViewById<LinearLayout>(R.id.menu_copy_title).isEnabled = false
            dialogView.findViewById<LinearLayout>(R.id.menu_website_settings).isEnabled = false
            dialogView.findViewById<LinearLayout>(R.id.menu_page_zoom).isEnabled = false
            dialogView.findViewById<LinearLayout>(R.id.menu_active_extensions).isEnabled = false
            dialogView.findViewById<LinearLayout>(R.id.menu_add_to_desktop).isEnabled = false
            dialogView.findViewById<LinearLayout>(R.id.menu_get_full_text).isEnabled = false
            
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
            return
        }
        
        // 创建自定义对话框（参考Alook的样式）
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_website_info, null)
        
        // 设置网站信息
        val websiteUrl = dialogView.findViewById<TextView>(R.id.website_url)
        val websiteName = dialogView.findViewById<TextView>(R.id.website_name)
        val certificateInfo = dialogView.findViewById<LinearLayout>(R.id.certificate_info)
        val certIssuedTo = dialogView.findViewById<TextView>(R.id.cert_issued_to)
        val certIssuedBy = dialogView.findViewById<TextView>(R.id.cert_issued_by)
        val certExpiresOn = dialogView.findViewById<TextView>(R.id.cert_expires_on)
        
        websiteUrl.text = currentUrl
        websiteName.text = currentTitle ?: extractDomain(currentUrl)
        
        // 获取证书信息（仅在HTTPS时显示）
        if (currentUrl.startsWith("https://")) {
            certificateInfo.visibility = View.VISIBLE
            // 显示加载中
            certIssuedTo.text = "Issued to: 正在获取..."
            certIssuedBy.text = "Issued by: 正在获取..."
            certExpiresOn.text = "Expires on: 正在获取..."
            
            getCertificateInfo(currentUrl) { issuedTo, issuedBy, expiresOn ->
                runOnUiThread {
                    certIssuedTo.text = "Issued to: $issuedTo"
                    certIssuedBy.text = "Issued by: $issuedBy"
                    certExpiresOn.text = "Expires on: $expiresOn"
                }
            }
        } else {
            certificateInfo.visibility = View.GONE
        }
        
        // 设置页面缩放显示
        val webViewToUse = if (isPaperStackMode) {
            paperStackManager?.getCurrentTab()?.webView ?: webView
        } else {
            webView
        }
        val currentZoom = webViewToUse.settings.textZoom
        val zoomText = dialogView.findViewById<TextView>(R.id.zoom_text)
        val zoomPercent = dialogView.findViewById<TextView>(R.id.zoom_percent)
        zoomText.text = "页面缩放:${currentZoom}%"
        zoomPercent.text = "${currentZoom}%"
        
        // 创建并显示对话框
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 设置菜单项点击事件（点击后关闭对话框）
        dialogView.findViewById<LinearLayout>(R.id.menu_copy_title).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("标题", currentTitle ?: "")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "标题已复制", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialogView.findViewById<LinearLayout>(R.id.menu_website_settings).setOnClickListener {
            dialog.dismiss()
            showWebsiteSettingsDialog(currentUrl)
        }
        
        dialogView.findViewById<LinearLayout>(R.id.menu_page_zoom).setOnClickListener {
            dialog.dismiss()
            showPageZoomDialog()
        }
        
        dialogView.findViewById<LinearLayout>(R.id.menu_active_extensions).setOnClickListener {
            Toast.makeText(this, "主动扩展功能待实现", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialogView.findViewById<LinearLayout>(R.id.menu_add_to_desktop).setOnClickListener {
            addToDesktop(currentUrl, currentTitle ?: "")
            dialog.dismiss()
        }
        
        dialogView.findViewById<LinearLayout>(R.id.menu_get_full_text).setOnClickListener {
            extractFullText()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * 获取SSL证书信息
     */
    private fun getCertificateInfo(url: String, callback: (String, String, String) -> Unit) {
        // 在新线程中获取证书信息
        Thread {
            try {
                val uri = java.net.URI(url)
                val host = uri.host ?: run {
                    callback("无法获取", "无法获取", "无法获取")
                    return@Thread
                }
                val port = uri.port.takeIf { it > 0 } ?: 443
                
                try {
                    val socketFactory = javax.net.ssl.SSLSocketFactory.getDefault()
                    val socket = socketFactory.createSocket(host, port) as javax.net.ssl.SSLSocket
                    socket.use {
                        it.startHandshake()
                        
                        val session = it.session
                        val certificates = session.peerCertificates
                        
                        if (certificates.isNotEmpty()) {
                            val cert = certificates[0] as java.security.cert.X509Certificate
                            
                            val issuedTo = cert.subjectDN.name
                            val issuedBy = cert.issuerDN.name
                            val expiresOn = cert.notAfter
                            
                            val dateFormat = java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.getDefault())
                            val expiresOnStr = dateFormat.format(expiresOn)
                            
                            callback(issuedTo, issuedBy, expiresOnStr)
                        } else {
                            callback("未知", "未知", "未知")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SearchActivity", "获取证书信息失败: ${e.message}", e)
                    callback("无法获取", "无法获取", "无法获取")
                }
            } catch (e: Exception) {
                Log.e("SearchActivity", "解析URL失败: ${e.message}", e)
                callback("无法获取", "无法获取", "无法获取")
            }
        }.start()
    }
    
    /**
     * 提取域名
     */
    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }
    
    /**
     * 显示网站设置对话框
     */
    private fun showWebsiteSettingsDialog(url: String) {
        val domain = extractDomain(url)
        val message = "网站: $domain\n\n可以在这里设置网站权限、Cookie等设置"
        
        AlertDialog.Builder(this)
            .setTitle("网站设置")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 显示页面缩放对话框
     */
    private fun showPageZoomDialog() {
        val zoomOptions = arrayOf("50%", "75%", "100%", "125%", "150%", "200%")
        val webViewToUse = if (isPaperStackMode) {
            paperStackManager?.getCurrentTab()?.webView ?: webView
        } else {
            webView
        }
        val currentZoom = webViewToUse.settings.textZoom
        val currentIndex = when {
            currentZoom <= 50 -> 0
            currentZoom <= 75 -> 1
            currentZoom <= 100 -> 2
            currentZoom <= 125 -> 3
            currentZoom <= 150 -> 4
            else -> 5
        }
        
        AlertDialog.Builder(this)
            .setTitle("页面缩放")
            .setSingleChoiceItems(zoomOptions, currentIndex) { dialog, which ->
                val zoomPercent = when (which) {
                    0 -> 50
                    1 -> 75
                    2 -> 100
                    3 -> 125
                    4 -> 150
                    5 -> 200
                    else -> 100
                }
                webViewToUse.settings.textZoom = zoomPercent
                Toast.makeText(this, "缩放已设置为 $zoomPercent%", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 添加到桌面
     */
    private fun addToDesktop(url: String, title: String) {
        Toast.makeText(this, "添加到桌面功能待实现", Toast.LENGTH_SHORT).show()
        // TODO: 实现添加到桌面功能
    }
    
    /**
     * 提取网页全部文字
     */
    private fun extractFullText() {
        val webViewToUse = if (isPaperStackMode) {
            paperStackManager?.getCurrentTab()?.webView ?: webView
        } else {
            webView
        }
        
        webViewToUse.evaluateJavascript("(function() { return document.body.innerText; })()") { result ->
            val text = result?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("网页文字", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@SearchActivity, "网页文字已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SearchActivity, "无法获取网页文字", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 从标签页更新favicon按钮
     */
    private fun updateFaviconFromTab(tab: PaperStackWebViewManager.WebViewTab) {
        // 优先使用WebView的实际URL，因为它更准确反映当前加载的页面
        val webViewUrl = tab.webView.url
        val actualUrl = if (!webViewUrl.isNullOrEmpty() && webViewUrl != "about:blank") {
            webViewUrl
        } else if (tab.url.isNotEmpty() && tab.url != "about:blank") {
            // 如果WebView的URL无效，使用tab.url（预设URL）
            tab.url
        } else {
            null
        }
        
        Log.d("SearchActivity", "updateFaviconFromTab: 更新favicon, tab.url=${tab.url}, webView.url=${webViewUrl}, actualUrl=${actualUrl}, Title=${tab.title}")
        
        // 确保按钮始终可见（常驻显示）
        faviconButton.visibility = View.VISIBLE
        
        // 尝试从WebView获取favicon
        val favicon = tab.webView.favicon
        if (favicon != null && actualUrl != null) {
            Log.d("SearchActivity", "updateFaviconFromTab: 使用WebView的favicon")
            updateFaviconButton(favicon, actualUrl)
        } else if (actualUrl != null) {
            // 如果没有favicon，尝试从URL加载
            Log.d("SearchActivity", "updateFaviconFromTab: WebView没有favicon，从URL加载: $actualUrl")
            loadFaviconFromUrl(actualUrl)
        } else {
            // 没有有效URL，显示默认图标（按钮常驻）
            faviconButton.setImageResource(android.R.drawable.ic_menu_search)
            Log.d("SearchActivity", "updateFaviconFromTab: 无有效URL，显示默认图标")
        }
    }
    
    /**
     * 初始化纸堆WebView管理器
     */
    private fun initializePaperStackManager() {
        paperStackContainer?.let { container ->
            Log.d("SearchActivity", "初始化纸堆WebView管理器")
            paperStackManager = PaperStackWebViewManager(this, container)
            
            // 设置监听器
            paperStackManager?.setOnTabCreatedListener { tab ->
                Log.d("SearchActivity", "标签页创建完成: ${tab.title}, URL: ${tab.url}")
                // 同步更新StackedCardPreview数据
                syncAllCardSystems()
                // 如果是当前标签页，立即更新favicon按钮（即使WebView还没加载完，也显示默认图标）
                val currentTab = paperStackManager?.getCurrentTab()
                if (currentTab?.id == tab.id) {
                    Log.d("SearchActivity", "标签页创建完成，更新favicon按钮")
                    updateFaviconFromTab(tab)
                }
            }
            
            paperStackManager?.setOnTabSwitchedListener { tab, index ->
                updatePaperCountText()
                // 更新搜索框URL（优先使用WebView的实际URL）
                val actualUrl = tab.webView.url ?: tab.url
                if (actualUrl != null && actualUrl.isNotEmpty() && actualUrl != "about:blank") {
                    // 获取标题（优先使用WebView的title，然后是tab的title）
                    val title = tab.webView.title ?: tab.title
                    currentDisplayUrl = actualUrl
                    currentDisplayTitle = title ?: extractDomain(actualUrl)
                    // 更新显示
                    runOnUiThread {
                        updateSearchBoxDisplay()
                    }
                }
                // 同步更新StackedCardPreview数据
                syncAllCardSystems()
                // 更新favicon按钮
                updateFaviconFromTab(tab)
                Log.d("SearchActivity", "切换到标签页: $index, 标题: ${tab.title}, tab.url=${tab.url}, webView.url=${tab.webView.url}, actualUrl=$actualUrl")
            }
            
            // 设置favicon监听器
            paperStackManager?.setOnFaviconReceivedListener { tab, favicon ->
                // 如果当前标签页是这个tab，更新favicon按钮
                val currentTab = paperStackManager?.getCurrentTab()
                if (currentTab?.id == tab.id) {
                    // 优先使用WebView的实际URL
                    val actualUrl = tab.webView.url ?: tab.url
                    Log.d("SearchActivity", "收到favicon更新: tab=${tab.title}, URL=${actualUrl}")
                    updateFaviconButton(favicon, actualUrl)
                }
            }
            
            // 设置标题更新监听器
            paperStackManager?.setOnTitleReceivedListener { tab, title ->
                // 如果当前标签页是这个tab，更新输入框显示
                val currentTab = paperStackManager?.getCurrentTab()
                if (currentTab?.id == tab.id) {
                    val actualUrl = tab.webView.url ?: tab.url
                    if (actualUrl != null && actualUrl.isNotEmpty() && actualUrl != "about:blank") {
                        currentDisplayUrl = actualUrl
                        currentDisplayTitle = title ?: tab.title ?: extractDomain(actualUrl)
                        // 如果输入框没有焦点，更新显示
                        if (!isInputFocused) {
                            runOnUiThread {
                                updateSearchBoxDisplay()
                            }
                        }
                        Log.d("SearchActivity", "收到标题更新: tab=${tab.title}, title=$title, URL=$actualUrl")
                    }
                }
            }
            
            // 添加默认标签页（百度首页）
            addDefaultTab()
            
            // 添加标签页后，立即更新favicon按钮
            paperStackManager?.getCurrentTab()?.let { tab ->
                updateFaviconFromTab(tab)
            }
        }
    }
    
    /**
     * 添加默认标签页（百度首页）
     */
    private fun addDefaultTab() {
        val defaultUrl = "https://www.baidu.com"
        val defaultTitle = "百度首页"
        Log.d("SearchActivity", "添加默认标签页，URL: $defaultUrl, 标题: $defaultTitle")
        
        val newTab = paperStackManager?.addTab(defaultUrl, defaultTitle)
        
        if (newTab != null) {
            updatePaperCountText()
            showPaperStackControls()
            hidePaperStackHint()
            Log.d("SearchActivity", "添加默认标签页成功，当前数量: ${paperStackManager?.getTabCount()}")
            
            // 延迟更新favicon按钮，确保WebView已经初始化
            handler.postDelayed({
                updateFaviconFromTab(newTab)
            }, 500)
        } else {
            Log.e("SearchActivity", "添加默认标签页失败")
        }
    }
    
    /**
     * 添加新的标签页
     */
    private fun addNewTab() {
        val currentUrl = webView.url ?: "https://www.baidu.com"
        val currentTitle = webView.title ?: "新标签页"
        Log.d("SearchActivity", "添加新标签页，URL: $currentUrl, 标题: $currentTitle")
        
        val newTab = paperStackManager?.addTab(currentUrl, currentTitle)
        
        if (newTab != null) {
            updatePaperCountText()
            showPaperStackControls()
            hidePaperStackHint()
            Log.d("SearchActivity", "添加新标签页成功，当前数量: ${paperStackManager?.getTabCount()}")
        } else {
            Log.e("SearchActivity", "添加新标签页失败")
        }
    }
    
    /**
     * 关闭所有标签页
     */
    private fun closeAllTabs() {
        paperStackManager?.cleanup()
        hidePaperStackControls()
        showPaperStackHint()
        Log.d("SearchActivity", "关闭所有标签页")
    }
    
    /**
     * 更新标签页计数文本
     */
    private fun updatePaperCountText() {
        val count = paperStackManager?.getTabCount() ?: 0
        val currentIndex = 1 // 当前标签页索引（从1开始）
        paperCountText?.text = "$currentIndex / $count"
    }
    
    /**
     * 显示纸堆控制面板
     */
    private fun showPaperStackControls() {
        paperStackControls?.visibility = View.VISIBLE
    }
    
    /**
     * 隐藏纸堆控制面板
     */
    private fun hidePaperStackControls() {
        paperStackControls?.visibility = View.GONE
    }
    
    /**
     * 显示纸堆提示
     */
    private fun showPaperStackHint() {
        paperStackHint?.visibility = View.VISIBLE
    }
    
    /**
     * 隐藏纸堆提示
     */
    private fun hidePaperStackHint() {
        paperStackHint?.visibility = View.GONE
    }
    
    /**
     * 初始化StackedCardPreview
     */
    private fun initializeStackedCardPreview() {
        try {
            stackedCardPreview?.let { preview ->
                Log.d("SearchActivity", "🔧 初始化StackedCardPreview，建立结实隔膜")
                
                // 设置初始状态为隐藏
                preview.visibility = View.GONE
                
                // 建立结实的触摸隔膜
                preview.isClickable = true
                preview.isFocusable = true
                preview.isFocusableInTouchMode = true
                preview.isEnabled = true
                
                // 设置触摸事件处理，确保完全拦截
                preview.setOnTouchListener { _, event ->
                    Log.d("SearchActivity", "🔒 StackedCardPreview触摸隔膜拦截: ${event.action}")
                    val handled = preview.onTouchEvent(event)
                    Log.d("SearchActivity", "🔒 StackedCardPreview触摸处理结果: $handled")
                    true // 强制返回true，建立隔膜
                }
                
                Log.d("SearchActivity", "🔧 StackedCardPreview初始化完成，隔膜已建立")
            } ?: run {
                Log.e("SearchActivity", "StackedCardPreview未找到")
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "初始化StackedCardPreview失败", e)
        }
    }
    
    /**
     * 同步所有卡片系统的数据
     */
    private fun syncAllCardSystems() {
        try {
            // 获取纸堆系统中的所有标签页
            val paperStackTabs = paperStackManager?.getAllTabs() ?: emptyList()
            
            Log.d("SearchActivity", "开始同步卡片系统数据，纸堆标签页数量: ${paperStackTabs.size}")
            
            if (paperStackTabs.isEmpty()) {
                Log.d("SearchActivity", "没有标签页，清空StackedCardPreview")
                stackedCardPreview?.setWebViewCards(emptyList(), 0)
                return
            }
            
            // 获取当前激活的标签页索引
            val currentTabIndex = paperStackManager?.let { manager ->
                val currentTab = manager.getCurrentTab()
                currentTab?.let { tab ->
                    paperStackTabs.indexOfFirst { it.id == tab.id }.takeIf { it >= 0 }
                        ?: paperStackTabs.indexOfFirst { it.url == tab.url }.takeIf { it >= 0 }
                } ?: 0
            } ?: 0
            
            Log.d("SearchActivity", "当前激活标签页索引: $currentTabIndex")
            
            // 转换为StackedCardPreview的卡片数据格式
            val cardData = paperStackTabs.mapIndexed { index, tab ->
                val title = tab.title.ifEmpty { "标签页 ${index + 1}" }
                val url = tab.url ?: "about:blank"
                val isActive = index == currentTabIndex
                
                Log.d("SearchActivity", "卡片 $index: 标题='$title', URL='$url', 是否激活=$isActive")
                
                com.example.aifloatingball.views.StackedCardPreview.WebViewCardData(
                    title = title,
                    url = url,
                    favicon = null,
                    screenshot = null
                )
            }

            Log.d(
                "SearchActivity",
                "同步卡片系统数据完成: ${cardData.size} 张卡片，当前激活索引=$currentTabIndex"
            )
            
            // 更新StackedCardPreview，确保当前激活的卡片正确显示
            stackedCardPreview?.setWebViewCards(cardData, currentTabIndex)
            
            // 如果StackedCardPreview正在显示，强制刷新
            if (stackedCardPreview?.visibility == View.VISIBLE) {
                stackedCardPreview?.invalidate()
                Log.d("SearchActivity", "StackedCardPreview正在显示，强制刷新")
            }
            
        } catch (e: Exception) {
            Log.e("SearchActivity", "同步卡片系统数据失败", e)
        }
    }
    
    /**
     * 激活StackedCardPreview预览（立即激活，无延迟）
     */
    private fun activateStackedCardPreview() {
        try {
            // 检查是否有纸堆标签页
            val paperStackTabs = paperStackManager?.getAllTabs() ?: emptyList()
            if (paperStackTabs.isEmpty()) {
                Log.d("SearchActivity", "没有纸堆标签页，无法激活StackedCardPreview")
                return
            }
            
            Log.d("SearchActivity", "激活StackedCardPreview，显示 ${paperStackTabs.size} 张卡片")
            
            // 确保StackedCardPreview已初始化
            if (stackedCardPreview == null) {
                Log.e("SearchActivity", "StackedCardPreview未初始化")
                return
            }
            
            // 立即显示StackedCardPreview（确保快速响应）
            stackedCardPreview?.visibility = View.VISIBLE
            
            // 立即建立触摸隔膜
            buildTouchBarrier()
            
            // 立即确保StackedCardPreview在最顶层
            ensureStackedCardPreviewOnTop()
            
            // 立即强制刷新显示
            stackedCardPreview?.invalidate()
            
            // 在后台异步加载内容和同步数据（不阻塞显示）
            handler.post {
                // 动态加载页面内容，确保卡片不显示白屏
                ensureCardContentLoaded()
                
                // 同步数据
                syncAllCardSystems()
                
                // 数据同步完成后再次刷新
                stackedCardPreview?.invalidate()
            }
            
            Log.d("SearchActivity", "StackedCardPreview已立即激活并显示，隔膜已建立")
            
            // 设置卡片选择监听器
            stackedCardPreview?.setOnCardSelectedListener { cardIndex ->
                Log.d("SearchActivity", "🎯 StackedCardPreview 选择卡片: $cardIndex")
                
                // 销毁触摸隔膜
                destroyTouchBarrier()
                
                // 获取当前标签页列表
                val paperStackTabs = paperStackManager?.getAllTabs() ?: emptyList()
                if (cardIndex >= 0 && cardIndex < paperStackTabs.size) {
                    val selectedCard = paperStackTabs[cardIndex]
                    Log.d("SearchActivity", "选中卡片: ${selectedCard.title}, URL: ${selectedCard.url}")
                    
                    // 在纸堆模式中，直接使用cardIndex作为标签页索引
                    // 因为PaperStackWebViewManager的tabs数组顺序与StackedCardPreview的卡片顺序一致
                    Log.d("SearchActivity", "切换到纸堆标签页索引: $cardIndex")
                    paperStackManager?.switchToTab(cardIndex)
                    
                    // 切换完成后，延迟同步数据确保一致性
                    runOnUiThread {
                        // 延迟一点时间确保切换动画完成
                        handler.postDelayed({
                            syncAllCardSystems()
                        }, 100)
                    }
                } else {
                    Log.w("SearchActivity", "无效的卡片索引: $cardIndex，标签页总数: ${paperStackTabs.size}")
                }
                
                // 隐藏StackedCardPreview
                stackedCardPreview?.visibility = View.GONE
                
                // 确保纸堆模式可见
                if (!isPaperStackMode) {
                    togglePaperStackMode()
                }
                
                Log.d("SearchActivity", "已切换到纸堆标签页: $cardIndex，隔膜已销毁")
            }
            
            // 设置卡片关闭监听器
            stackedCardPreview?.setOnCardCloseListener { url ->
                Log.d("SearchActivity", "🔗 StackedCardPreview 请求关闭卡片: $url")
                
                // 从纸堆中关闭对应的标签页
                val closed = paperStackManager?.closeTabByUrl(url) ?: false
                
                if (closed) {
                    // 同步更新数据
                    syncAllCardSystems()
                    Log.d("SearchActivity", "成功关闭纸堆标签页: $url")
                } else {
                    Log.w("SearchActivity", "关闭纸堆标签页失败: $url")
                }
            }
            
            // 设置卡片收藏监听器
            stackedCardPreview?.setOnCardFavoriteListener { index, url ->
                Log.d("SearchActivity", "⭐ StackedCardPreview 请求收藏卡片: index=$index, url=$url")
                
                // TODO: 实现收藏功能
                // 可以在这里调用收藏管理器来保存URL
                android.widget.Toast.makeText(this, "收藏功能开发中", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // 设置复制网址监听器
            stackedCardPreview?.setOnCardCopyUrlListener { index, url ->
                Log.d("SearchActivity", "📋 StackedCardPreview 请求复制网址: index=$index, url=$url")
                
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "网址已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // 设置静音监听器
            stackedCardPreview?.setOnCardMuteListener { index ->
                Log.d("SearchActivity", "🔇 StackedCardPreview 请求静音卡片: index=$index")
                
                // TODO: 实现静音功能
                // 可以在这里控制WebView的音量
                android.widget.Toast.makeText(this, "静音功能开发中", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // 设置添加到桌面监听器
            stackedCardPreview?.setOnCardAddToDesktopListener { index, url, title ->
                Log.d("SearchActivity", "🏠 StackedCardPreview 请求添加到桌面: index=$index, url=$url, title=$title")
                
                // TODO: 实现添加到桌面功能
                // 可以在这里创建桌面快捷方式
                android.widget.Toast.makeText(this, "添加到桌面功能开发中", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // 去掉激活提示
            
        } catch (e: Exception) {
            Log.e("SearchActivity", "激活StackedCardPreview失败", e)
            // 去掉错误提示的Toast
        }
    }
    
    /**
     * 确保StackedCardPreview在最顶层
     */
    private fun ensureStackedCardPreviewOnTop() {
        try {
            stackedCardPreview?.let { preview ->
                Log.d("SearchActivity", "🔝 确保StackedCardPreview在最顶层")
                
                // 设置最高层级
                preview.elevation = 9999f
                
                // 置于最前
                preview.bringToFront()
                
                // 确保父容器也将其置于最前
                val parent = preview.parent as? ViewGroup
                parent?.let {
                    it.bringChildToFront(preview)
                    it.invalidate()
                }
                
                Log.d("SearchActivity", "🔝 StackedCardPreview已置于最顶层")
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "确保StackedCardPreview在最顶层失败", e)
        }
    }
    
    /**
     * 建立触摸隔膜
     */
    private fun buildTouchBarrier() {
        try {
            stackedCardPreview?.let { preview ->
                Log.d("SearchActivity", "🔒 建立最强触摸隔膜")
                
                // 设置触摸属性
                preview.isClickable = true
                preview.isFocusable = true
                preview.isFocusableInTouchMode = true
                preview.isEnabled = true
                
                // 设置最高层级和优先级
                preview.elevation = 9999f
                preview.bringToFront()
                
                // 设置触摸拦截器 - 允许事件传递给StackedCardPreview处理
                preview.setOnTouchListener { view, event ->
                    Log.d("SearchActivity", "🔒 隔膜传递触摸事件: ${event.action}")
                    // 将触摸事件传递给StackedCardPreview处理，而不是完全拦截
                    view.onTouchEvent(event)
                }
                
                Log.d("SearchActivity", "🔒 最强触摸隔膜建立完成")
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "建立触摸隔膜失败", e)
        }
    }
    
    /**
     * 销毁触摸隔膜
     */
    private fun destroyTouchBarrier() {
        try {
            stackedCardPreview?.let { preview ->
                Log.d("SearchActivity", "🔓 销毁触摸隔膜")
                
                // 重置触摸属性
                preview.isClickable = false
                preview.isFocusable = false
                preview.isFocusableInTouchMode = false
                preview.isEnabled = false
                
                // 重置层级
                preview.elevation = 0f
                
                // 移除触摸拦截器
                preview.setOnTouchListener(null)
                
                Log.d("SearchActivity", "🔓 触摸隔膜销毁完成")
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "销毁触摸隔膜失败", e)
        }
    }
    
    /**
     * 动态加载页面内容，确保卡片不显示白屏
     */
    private fun ensureCardContentLoaded() {
        try {
            paperStackManager?.getAllTabs()?.forEach { tab ->
                val webView = tab.webView
                if (webView != null) {
                    // 检查WebView是否已加载内容
                    val currentUrl = webView.url
                    val currentTitle = webView.title
                    
                    Log.d("SearchActivity", "检查标签页内容: ${tab.title}, URL: $currentUrl")
                    
                    // 如果URL为空或标题为空，尝试重新加载
                    if (currentUrl.isNullOrEmpty() || currentTitle.isNullOrEmpty()) {
                        Log.d("SearchActivity", "标签页内容不完整，重新加载: ${tab.title}")
                        
                        // 使用保存的URL重新加载
                        val savedUrl = tab.url ?: "https://www.baidu.com"
                        webView.loadUrl(savedUrl)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "动态加载页面内容失败", e)
        }
    }
    
    /**
     * 显示菜单选项
     */
    private fun showMenuOptions() {
        val options = arrayOf(
            "卡片预览",
            "搜索引擎列表",
            if (isPaperStackMode) "切换到普通模式" else "切换到纸堆模式",
            "悬浮窗模式",
            "设置"
        )
        
        AlertDialog.Builder(this)
            .setTitle("菜单选项")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 卡片预览
                        activateStackedCardPreview()
                    }
                    1 -> {
                        // 打开搜索引擎列表
                        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
                        if (isLeftHanded) {
                            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                                drawerLayout.closeDrawer(GravityCompat.END)
                            } else {
                                drawerLayout.openDrawer(GravityCompat.END)
                            }
                        } else {
                            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                                drawerLayout.closeDrawer(GravityCompat.START)
                            } else {
                                drawerLayout.openDrawer(GravityCompat.START)
                            }
                        }
                    }
                    2 -> {
                        // 切换纸堆模式
                        togglePaperStackMode()
                    }
                    3 -> {
                        // 悬浮窗模式
                        activateMultiCardFloatingBackground()
                    }
                    4 -> {
                        // 打开设置
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
            .show()
    }
    
    /**
     * 切换纸堆模式
     */
    private fun togglePaperStackMode() {
        isPaperStackMode = !isPaperStackMode
        
        val paperStackLayout = findViewById<View>(R.id.paper_stack_layout)
        
        if (isPaperStackMode) {
            // 切换到纸堆模式
            webView.visibility = View.GONE
            paperStackLayout.visibility = View.VISIBLE
            
            if (paperStackManager == null) {
                initializePaperStackManager()
            } else {
                // 如果管理器已存在，确保显示控制按钮
                showPaperStackControls()
                updatePaperCountText()
            }
            
            Toast.makeText(this, "已切换到纸堆模式", Toast.LENGTH_SHORT).show()
        } else {
            // 切换到普通模式
            webView.visibility = View.VISIBLE
            paperStackLayout.visibility = View.GONE
            hidePaperStackControls()
            Toast.makeText(this, "已切换到普通模式", Toast.LENGTH_SHORT).show()
        }
    }

    // 激活多卡片悬浮后台系统：默认从当前URL或当前搜索引擎生成
    private fun activateMultiCardFloatingBackground() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "缺少悬浮窗权限", Toast.LENGTH_SHORT).show()
                Log.w("SearchActivity", "No overlay permission, cannot start multi-card floating background")
                return
            }

            val intent = Intent(this, DualFloatingWebViewService::class.java)

            val currentUrl = webView.url
            if (!currentUrl.isNullOrEmpty() && currentUrl != "about:blank") {
                intent.putExtra("url", currentUrl)
            } else {
                currentSearchEngine?.let { engine ->
                    val query = searchInput.text.toString().trim()
                    if (query.isEmpty()) {
                        val base = engine.url.replace("{query}", "")
                        intent.putExtra("url", base)
                    } else {
                        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                        val searchUrl = engine.url.replace("{query}", encodedQuery)
                        intent.putExtra("url", searchUrl)
                    }
                }
            }

            val windowCount = settingsManager.getDefaultWindowCount()
            intent.putExtra("window_count", windowCount)

            startService(intent)
        } catch (e: Exception) {
            Log.e("SearchActivity", "启动多卡片悬浮后台失败", e)
            Toast.makeText(this, "启动悬浮后台失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 增加这些设置来优化缩放体验
            textZoom = 100  // 确保文本缩放正常
            defaultZoom = WebSettings.ZoomDensity.MEDIUM
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            
            // 添加新的设置
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            
            // 设置缓存模式
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // 设置移动版User-Agent，确保网页加载移动版
            userAgentString = WebViewConstants.MOBILE_USER_AGENT
            
            // 允许混合内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 启用第三方 Cookie
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }
        }
        
        // 启用WebView上下文菜单
        webView.setOnLongClickListener { view ->
            val result = (view as WebView).hitTestResult
            when (result.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    showImageContextMenu(result.extra)
                    true
                }
                WebView.HitTestResult.ANCHOR_TYPE,
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    showLinkContextMenu(result.extra)
                    true
                }
                else -> false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 只显示进度条，不显示全屏加载视图
                progressBar.visibility = View.VISIBLE
                
                // 页面开始加载时隐藏悬浮窗
                try {
                    if (::floatingVideoManager.isInitialized && floatingVideoManager.isShowing()) {
                        floatingVideoManager.hide()
                    }
                    if (::systemOverlayVideoManager.isInitialized && systemOverlayVideoManager.isShowing()) {
                        systemOverlayVideoManager.hide()
                    }
                } catch (e: Exception) {
                    Log.w("SearchActivity", "隐藏悬浮窗失败（可能未初始化）", e)
                }
                
                // 获取实际URL
                val actualUrl = view?.url ?: url
                Log.d("SearchActivity", "开始加载URL: url参数=$url, view.url=${view?.url}, actualUrl=$actualUrl")
                
                // 页面开始加载时，如果有有效URL就更新搜索框并显示favicon按钮
                if (actualUrl != null && actualUrl.isNotEmpty() && actualUrl != "about:blank") {
                    // 立即更新搜索框（显示URL或域名，因为标题可能还没加载）
                    runOnUiThread {
                        currentDisplayUrl = actualUrl
                        currentDisplayTitle = null // 标题稍后会更新
                        if (!isInputFocused) {
                            // 先显示域名，等标题加载后再显示标题
                            val domain = extractDomain(actualUrl)
                            searchInput.setText("")
                            searchInput.hint = domain
                            Log.d("SearchActivity", "onPageStarted: 更新搜索框显示域名=$domain")
                        }
                    }
                    
                    Log.d("SearchActivity", "onPageStarted: 检测到有效URL=$actualUrl，显示favicon按钮")
                    runOnUiThread {
                        // 先显示默认图标
                        faviconButton.setImageResource(android.R.drawable.ic_menu_search)
                        faviconButton.visibility = View.VISIBLE
                        Log.d("SearchActivity", "onPageStarted: favicon按钮已设置为可见")
                    }
                    
                    // 如果有favicon，立即更新
                    if (favicon != null) {
                        Log.d("SearchActivity", "onPageStarted: 收到favicon，更新按钮")
                        runOnUiThread {
                            updateFaviconButton(favicon, actualUrl)
                        }
                    } else {
                        // 尝试从URL加载favicon
                        Log.d("SearchActivity", "onPageStarted: 没有favicon，从URL加载")
                        runOnUiThread {
                            loadFaviconFromUrl(actualUrl)
                        }
                    }
                } else {
                    // 无效URL，显示默认图标（按钮常驻）
                    Log.d("SearchActivity", "onPageStarted: 无效URL，显示默认图标")
                    runOnUiThread {
                        faviconButton.visibility = View.VISIBLE
                        faviconButton.setImageResource(android.R.drawable.ic_menu_search)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 隐藏进度条
                progressBar.visibility = View.GONE
                updateWebViewTheme()
                
                // 获取WebView的实际URL（优先使用view.url，因为url参数可能不准确）
                val actualUrl = view?.url ?: url
                Log.d("SearchActivity", "onPageFinished: url参数=$url, view.url=${view?.url}, actualUrl=$actualUrl, title=${view?.title}")
                
                // 页面加载完成后，重置视频播放器状态并重新检测
                isVideoPlayerActive = false
                if (view != null) {
                    // 注入新的视频检测脚本
                    injectVideoHookToWebView(view)
                    // 保留旧的检测逻辑作为备用
                    startVideoDetection(view)
                }
                
                // 更新搜索框显示当前URL
                if (actualUrl != null && actualUrl.isNotEmpty() && actualUrl != "about:blank") {
                    updateSearchBoxWithCurrentUrl(actualUrl)
                }
                
                // 更新favicon按钮（如果没有收到favicon，尝试从URL加载）
                if (url != null && url.isNotEmpty() && url != "about:blank") {
                    Log.d("SearchActivity", "onPageFinished: URL=$url，更新favicon按钮")
                    runOnUiThread {
                        // 确保按钮可见
                        if (faviconButton.visibility != View.VISIBLE) {
                            Log.d("SearchActivity", "onPageFinished: 按钮不可见，设置为可见")
                            faviconButton.setImageResource(android.R.drawable.ic_menu_search)
                            faviconButton.visibility = View.VISIBLE
                        }
                        
                        if (view?.favicon != null) {
                            Log.d("SearchActivity", "onPageFinished: 使用WebView的favicon")
                            updateFaviconButton(view.favicon, url)
                        } else {
                            Log.d("SearchActivity", "onPageFinished: 从URL加载favicon")
                            loadFaviconFromUrl(url)
                        }
                    }
                } else {
                    // 无效URL，显示默认图标（按钮常驻）
                    Log.d("SearchActivity", "onPageFinished: 无效URL=$url，显示默认图标")
                    runOnUiThread {
                        faviconButton.visibility = View.VISIBLE
                        faviconButton.setImageResource(android.R.drawable.ic_menu_search)
                    }
                }
                
                // 维护自定义历史栈
                try {
                    if (!isRestoringFromHistory && url != null && url.isNotEmpty()) {
                        val last = if (urlBackStack.isEmpty()) null else urlBackStack.last()
                        if (last == null || last != url) {
                            urlBackStack.addLast(url)
                            Log.d("SearchActivity", "历史入栈: $url (size=${urlBackStack.size})")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SearchActivity", "维护自定义历史栈失败", e)
                } finally {
                    isRestoringFromHistory = false
                }
                
                // 注入viewport meta标签确保移动版显示
                view?.evaluateJavascript("""
                    (function() {
                        try {
                            // 检查是否已有viewport meta标签
                            var viewportMeta = document.querySelector('meta[name="viewport"]');
                            if (viewportMeta) {
                                // 更新现有的viewport设置
                                viewportMeta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');
                            } else {
                                // 创建新的viewport meta标签
                                var meta = document.createElement('meta');
                                meta.name = 'viewport';
                                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                                document.head.appendChild(meta);
                            }
                            
                            // 确保页面使用移动端样式
                            document.documentElement.style.setProperty('--mobile-viewport', '1');
                            
                            console.log('SearchActivity: Viewport meta tag injected for mobile display');
                        } catch (e) {
                            console.error('SearchActivity: Failed to inject viewport meta tag:', e);
                        }
                    })();
                """.trimIndent(), null)
                
                // 修复搜狗移动端输入框输入法激活问题
                view?.evaluateJavascript("""
                    (function() {
                        try {
                            // 检测是否为搜狗移动端
                            var isSogouMobile = window.location.hostname.includes('m.sogou.com') || 
                                               window.location.hostname.includes('wap.sogou.com');
                            
                            if (isSogouMobile) {
                                console.log('SearchActivity: 检测到搜狗移动端，修复输入框');
                                
                                // 修复搜狗搜索框的输入法问题
                                var searchInputs = document.querySelectorAll('input[type="text"], input[type="search"], input[name*="query"], input[name*="keyword"]');
                                
                                searchInputs.forEach(function(input) {
                                    // 移除可能阻止输入法的事件监听器
                                    input.removeAttribute('readonly');
                                    input.removeAttribute('disabled');
                                    
                                    // 确保输入框可以正常获得焦点
                                    input.style.webkitUserSelect = 'text';
                                    input.style.userSelect = 'text';
                                    
                                    // 添加点击事件确保输入法激活
                                    input.addEventListener('click', function(e) {
                                        e.stopPropagation();
                                        this.focus();
                                        this.click();
                                    }, true);
                                    
                                    // 添加触摸事件确保输入法激活
                                    input.addEventListener('touchstart', function(e) {
                                        e.stopPropagation();
                                        this.focus();
                                    }, true);
                                    
                                    // 修复输入框的样式
                                    input.style.webkitAppearance = 'none';
                                    input.style.border = '1px solid #ccc';
                                    input.style.borderRadius = '4px';
                                    input.style.padding = '8px';
                                    input.style.fontSize = '16px';
                                    
                                    console.log('SearchActivity: 搜狗输入框已修复:', input.name || input.id || 'unnamed');
                                });
                                
                                // 修复搜狗搜索按钮
                                var searchButtons = document.querySelectorAll('button[type="submit"], input[type="submit"], .search-btn, .btn-search');
                                searchButtons.forEach(function(button) {
                                    button.addEventListener('click', function(e) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        
                                        // 获取搜索框的值
                                        var searchInput = document.querySelector('input[type="text"], input[type="search"], input[name*="query"], input[name*="keyword"]');
                                        if (searchInput && searchInput.value.trim()) {
                                            // 触发搜索
                                            var form = searchInput.closest('form');
                                            if (form) {
                                                form.submit();
                                            } else {
                                                // 如果没有表单，直接跳转
                                                var searchUrl = 'https://m.sogou.com/web?query=' + encodeURIComponent(searchInput.value.trim());
                                                window.location.href = searchUrl;
                                            }
                                        }
                                    }, true);
                                });
                            }
                        } catch (e) {
                            console.error('SearchActivity: 修复搜狗输入框失败:', e);
                        }
                    })();
                """.trimIndent(), null)
                
                Log.d("SearchActivity", "页面加载完成: $url")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                Log.d("SearchActivity", "shouldOverrideUrlLoading: $url")
                
                if (url != null) {
                    return handleSearchResultClick(view, url)
                }
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                Log.d("SearchActivity", "shouldOverrideUrlLoading (legacy): $url")
                
                if (url != null) {
                    // 处理搜索结果页面的链接点击
                    return handleSearchResultClick(view, url)
                }
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                val errorUrl = request?.url?.toString()
                val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.errorCode
                } else {
                    -1
                }
                val errorDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString()
                } else {
                    "未知错误"
                }
                
                Log.e("SearchActivity", "WebView加载错误: $errorDescription, URL: $errorUrl, ErrorCode: $errorCode")
                
                // 检查是否为 ERR_UNKNOWN_URL_SCHEME 错误，且 URL 是特殊 scheme
                if (request?.isForMainFrame == true && errorUrl != null) {
                    if (errorCode == -2 || errorDescription?.contains("ERR_UNKNOWN_URL_SCHEME") == true || 
                        errorDescription?.contains("net::ERR_UNKNOWN_URL_SCHEME") == true) {
                        // 检查是否为应用 scheme（非 http/https/file/javascript/tel/mailto/sms）
                        val lower = errorUrl.lowercase()
                        val isAppScheme = lower.contains("://") && 
                                         !lower.startsWith("http://") && 
                                         !lower.startsWith("https://") &&
                                         !lower.startsWith("file://") &&
                                         !lower.startsWith("javascript:") &&
                                         !lower.startsWith("tel:") &&
                                         !lower.startsWith("mailto:") &&
                                         !lower.startsWith("sms:")
                        
                        if (isAppScheme) {
                            // 应用 scheme 导致的错误，直接启动Intent（类似 Chrome，识别所有intent）
                            Log.d("SearchActivity", "检测到应用 scheme 错误，直接启动Intent: $errorUrl")
                            if (errorUrl.startsWith("intent://")) {
                                launchIntentUrlDirectly(errorUrl)
                            } else {
                                launchSchemeUrlDirectly(errorUrl)
                            }
                            // 不调用 super.onReceivedError，避免显示错误页面
                            return
                        }
                    }
                }
                
                super.onReceivedError(view, request, error)
                
                // 只处理主页面加载错误，忽略资源加载错误
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    
                    val finalErrorUrl = errorUrl ?: "unknown"
                    
                    Toast.makeText(this@SearchActivity, "页面加载失败", Toast.LENGTH_SHORT).show()
                    
                    // 显示更友好的错误页面
                    val errorHtml = """
                        <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1">
                                <style>
                                    body { 
                                        font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                        padding: 20px;
                                        text-align: center;
                                        color: #333;
                                        background: #f5f5f5;
                                    }
                                    .error-container {
                                        background: white;
                                        border-radius: 8px;
                                        padding: 20px;
                                        margin: 20px auto;
                                        max-width: 400px;
                                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                                    }
                                    .error-icon {
                                        font-size: 48px;
                                        margin-bottom: 16px;
                                    }
                                    .error-title {
                                        color: #d32f2f;
                                        font-size: 18px;
                                        margin-bottom: 8px;
                                    }
                                    .error-message {
                                        color: #666;
                                        font-size: 14px;
                                        line-height: 1.4;
                                    }
                                    .retry-button {
                                        background: #1976d2;
                                        color: white;
                                        border: none;
                                        padding: 8px 16px;
                                        border-radius: 4px;
                                        margin-top: 16px;
                                        cursor: pointer;
                                    }
                                </style>
                            </head>
                            <body>
                                <div class="error-container">
                                    <div class="error-icon">😕</div>
                                    <div class="error-title">页面加载失败</div>
                                    <div class="error-message">
                                        抱歉，无法加载页面。请检查网络连接后重试。
                                    </div>
                                    <button class="retry-button" onclick="window.location.reload()">
                                        重新加载
                                    </button>
                                </div>
                            </body>
                        </html>
                    """.trimIndent()
                    view?.loadData(errorHtml, "text/html", "UTF-8")
                }
            }

            override fun onReceivedError(view: WebView?, errorCode: Int,
                                       description: String?, failingUrl: String?) {
                Log.e("SearchActivity", "WebView加载错误 (legacy): $description, URL: $failingUrl, ErrorCode: $errorCode")
                
                // 检查是否为 ERR_UNKNOWN_URL_SCHEME 错误（错误代码 -2），且 URL 是特殊 scheme
                if (errorCode == -2 || description?.contains("ERR_UNKNOWN_URL_SCHEME") == true || 
                    description?.contains("net::ERR_UNKNOWN_URL_SCHEME") == true) {
                    if (failingUrl != null) {
                        val lower = failingUrl.lowercase()
                        val isAppScheme = lower.contains("://") && 
                                         !lower.startsWith("http://") && 
                                         !lower.startsWith("https://") &&
                                         !lower.startsWith("file://") &&
                                         !lower.startsWith("javascript:") &&
                                         !lower.startsWith("tel:") &&
                                         !lower.startsWith("mailto:") &&
                                         !lower.startsWith("sms:")
                        
                            if (isAppScheme) {
                                // 应用 scheme 导致的错误，直接启动Intent（类似 Chrome，识别所有intent）
                                Log.d("SearchActivity", "检测到应用 scheme 错误 (legacy)，直接启动Intent: $failingUrl")
                                if (failingUrl.startsWith("intent://")) {
                                    launchIntentUrlDirectly(failingUrl)
                                } else {
                                    launchSchemeUrlDirectly(failingUrl)
                                }
                                // 不调用 super.onReceivedError，避免显示错误页面和可能的循环
                                return
                            }
                    }
                }
                
                super.onReceivedError(view, errorCode, description, failingUrl)
                
                // 忽略资源加载错误的提示
                if (failingUrl != view?.url) {
                    return
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    // 加载完成，隐藏进度条
                    progressBar.visibility = View.GONE
                    // 页面加载完成后，注入视频检测脚本
                    if (view != null) {
                        injectVideoHookToWebView(view)
                    }
                    // 保留旧的检测逻辑作为备用
                    startVideoDetection(view)
                } else {
                    // 更新加载进度
                    if (progressBar.visibility != View.VISIBLE) {
                        progressBar.visibility = View.VISIBLE
                    }
                    progressBar.progress = newProgress
                }
            }
            
            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                // 更新favicon按钮图标
                updateFaviconButton(icon, view?.url)
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                
                // 更新标题显示
                val url = view?.url
                if (url != null && url.isNotEmpty() && url != "about:blank") {
                    currentDisplayTitle = title ?: extractDomain(url)
                    // 如果输入框没有焦点，更新显示
                    if (!isInputFocused) {
                        runOnUiThread {
                            updateSearchBoxDisplay()
                        }
                    }
                }
                
                // 如果没有收到favicon，尝试从URL加载
                if (view?.favicon == null && url != null) {
                    loadFaviconFromUrl(url)
                }
            }
        }

        // 初始化时设置主题
        updateWebViewTheme()
        
        // 初始化视频管理器
        ensureFloatingVideoManager()
    }
    
    /**
     * 确保视频管理器已初始化
     */
    private fun ensureFloatingVideoManager() {
        if (!::systemOverlayVideoManager.isInitialized) {
            systemOverlayVideoManager = SystemOverlayVideoManager(this)
        }
        if (!::floatingVideoManager.isInitialized) {
            floatingVideoManager = FloatingVideoPlayerManager(this)
            floatingVideoManager.attachIfNeeded()
        }
    }
    
    /**
     * 注入视频检测脚本到WebView
     */
    private fun injectVideoHookToWebView(webView: WebView?) {
        if (webView == null) return
        ensureFloatingVideoManager()

        try {
            // 仅注入一次
            val tag = webView.getTag(R.id.tag_video_bridge_injected)
            if (tag != true) {
                webView.addJavascriptInterface(
                    VideoDetectionBridge { url ->
                        runOnUiThread {
                            try {
                                // 停止页面内播放并隐藏原视频，避免双声道
                                webView.evaluateJavascript("(function(){var v=document.querySelector('video'); if(v){try{v.pause();v.muted=true;v.style.opacity='0';v.style.pointerEvents='none';}catch(e){}}})()", null)
                            } catch (_: Exception) {}

                            // 优先使用系统级悬浮窗播放器
                            if (systemOverlayVideoManager.canDrawOverlays()) {
                                systemOverlayVideoManager.show(url)
                            } else {
                                // 无权限则提示并使用Activity内悬浮窗作为降级
                                systemOverlayVideoManager.requestOverlayPermission()
                                floatingVideoManager.show(url)
                            }
                        }
                    },
                    "FloatingVideoBridge"
                )
                webView.setTag(R.id.tag_video_bridge_injected, true)
            }

            val js = """
                (function(){
                  if(window.__fv_injected) return; window.__fv_injected=true;
                  function getVideoUrl(v){
                    try{
                      var u = v.currentSrc || v.src || '';
                      if(!u){
                        var s = v.querySelector('source');
                        if(s) u = s.src || '';
                      }
                      if(!u){
                        var meta = document.querySelector('meta[property="og:video"],meta[property="og:video:url"],meta[name="og:video"],meta[name="og:video:url"]');
                        if(meta) u = meta.getAttribute('content')||'';
                      }
                      return u;
                    }catch(e){return ''}
                  }
                  function hook(v){
                    if(!v || v.__fv_hooked) return; v.__fv_hooked=true;
                    v.addEventListener('play', function(){
                      try{ var u=getVideoUrl(v); FloatingVideoBridge.onVideoPlay(u); }catch(e){}
                      try{ v.pause(); v.muted=true; v.style.opacity='0'; v.style.pointerEvents='none'; }catch(e){}
                    }, {passive:true});
                    // 拦截点击主动触发
                    v.addEventListener('click', function(e){
                      e.preventDefault(); e.stopPropagation();
                      try{ var u=getVideoUrl(v); FloatingVideoBridge.onVideoPlay(u); }catch(err){}
                    }, true);
                  }
                  var vids = document.querySelectorAll('video');
                  vids.forEach(hook);
                  var obs = new MutationObserver(function(){
                     try{ document.querySelectorAll('video').forEach(hook); }catch(e){}
                  });
                  obs.observe(document.documentElement, {subtree:true, childList:true});
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        } catch (e: Exception) {
            Log.e("SearchActivity", "注入视频检测脚本失败", e)
        }
    }

    private fun updateWebViewTheme() {
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.settings.forceDark = if (isDarkMode) {
                WebSettings.FORCE_DARK_ON
            } else {
                WebSettings.FORCE_DARK_OFF
            }
        } else {
            // 对于低版本 Android，在页面加载完成后注入 CSS
            if (isDarkMode) {
                webView.evaluateJavascript("""
                    (function() {
                        var darkModeStyle = document.getElementById('dark-mode-style');
                        if (!darkModeStyle) {
                            darkModeStyle = document.createElement('style');
                            darkModeStyle.id = 'dark-mode-style';
                            darkModeStyle.type = 'text/css';
                            darkModeStyle.innerHTML = `
                                :root {
                                    filter: invert(90%) hue-rotate(180deg) !important;
                                }
                                img, video, canvas, [style*="background-image"] {
                                    filter: invert(100%) hue-rotate(180deg) !important;
                                }
                                @media (prefers-color-scheme: dark) {
                                    :root {
                                        filter: none !important;
                                    }
                                    img, video, canvas, [style*="background-image"] {
                                        filter: none !important;
                                    }
                                }
                            `;
                            document.head.appendChild(darkModeStyle);
                        }
                    })()
                """.trimIndent(), null)
            } else {
                webView.evaluateJavascript("""
                    (function() {
                        var darkModeStyle = document.getElementById('dark-mode-style');
                        if (darkModeStyle) {
                            darkModeStyle.remove();
                        }
                    })()
                """.trimIndent(), null)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时更新主题
        updateWebViewTheme()
    }

    private fun setupLetterIndexBar() {
        // 获取当前搜索模式
        val isAIMode = settingsManager.isDefaultAIMode()
        Log.d("SearchActivity", "设置字母索引栏，当前模式=${if (isAIMode) "AI模式" else "普通模式"}")
        
        // 根据当前模式设置引擎列表
        try {
            // 始终使用普通搜索引擎列表，因为字母索引栏需要的是SearchEngine类型
            letterIndexBar.engines = NORMAL_SEARCH_ENGINES.map { it as SearchEngine }
            
            if (isAIMode) {
                Log.d("SearchActivity", "当前为AI模式，但字母索引栏仍使用普通搜索引擎作为数据源")
            } else {
                Log.d("SearchActivity", "字母索引栏设置为普通搜索引擎列表: ${letterIndexBar.engines?.size}个")
            }
        } catch (e: Exception) {
            // 如果加载失败，使用普通搜索引擎列表
            Log.e("SearchActivity", "设置字母索引栏引擎列表失败", e)
            letterIndexBar.engines = NORMAL_SEARCH_ENGINES.map { it as SearchEngine }
        }

        letterIndexBar.onLetterSelectedListener = object : LetterIndexBar.OnLetterSelectedListener {
            override fun onLetterSelected(view: View, letter: Char) {
                val letterStr = letter.toString()
                letterTitle.text = letterStr
                letterTitle.visibility = View.VISIBLE
                
                // 更新搜索引擎列表，显示选中字母的搜索引擎
                showSearchEnginesByLetter(letter)
            }
        }
    }

    private fun updateEngineList() {
        val isAIMode = settingsManager.isDefaultAIMode()
        val engines = if (isAIMode) {
            AISearchEngine.DEFAULT_AI_ENGINES
        } else {
            SearchEngine.DEFAULT_ENGINES
        }
        engineAdapter.updateEngines(engines)
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        val filteredEngines = searchEngines.filter { engine ->
            val firstChar = engine.name.first()
                when {
                    firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                    firstChar.uppercaseChar() == letter.uppercaseChar()
                    firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                    pinyinArray?.firstOrNull()?.firstOrNull()?.uppercaseChar() == letter.uppercaseChar()
                    }
                    else -> false
                }
            }
        engineAdapter.updateEngines(filteredEngines)
    }

    // EngineAdapter class
    private inner class EngineAdapter(
        private var engines: List<BaseSearchEngine>,
        private val onEngineSelected: (BaseSearchEngine) -> Unit
    ) : RecyclerView.Adapter<EngineAdapter.ViewHolder>() {
        
        fun updateEngines(newEngines: List<BaseSearchEngine>) {
            engines = newEngines
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_engine, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val engine = engines[position]
            holder.bind(engine)
        }
        
        override fun getItemCount() = engines.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTextView: TextView = itemView.findViewById(R.id.engine_name)
            private val descriptionTextView: TextView = itemView.findViewById(R.id.engine_description)

            fun bind(engine: BaseSearchEngine) {
                nameTextView.text = engine.displayName
                descriptionTextView.text = engine.description
                itemView.setOnClickListener { onEngineSelected(engine) }
            }
        }
    }

    private fun showEngineSettings(engine: SearchEngine) {
        val options = arrayOf("访问主页", "在悬浮窗中打开", "复制链接", "分享", "在浏览器中打开")

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${engine.name} 选项")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSearchEngine(engine)
                    1 -> {
                        // 在悬浮窗中打开
                        settingsManager.putBoolean("use_floating_mode", true)
                        openSearchEngine(engine)
                        settingsManager.putBoolean("use_floating_mode", false) // 重置为默认值
                    }
                    2 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", engine.url))
                        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${engine.name}: ${engine.url}")
                        }
                        startActivity(Intent.createChooser(intent, "分享到"))
                    }
                    4 -> {
                        val intent = Intent(Intent.ACTION_VIEW, 
                            android.net.Uri.parse(engine.url))
                        startActivity(intent)
                    }
                }
            }
            .create()
        dialog.show()
    }

    private fun loadDefaultSearchEngine() {
        val defaultEngine = settingsManager.getString(Constants.PREF_DEFAULT_SEARCH_ENGINE, "") ?: ""
        if (defaultEngine.isNotEmpty()) {
            val parts = defaultEngine.split("|")
            if (parts.size >= 2) {
                val engineName = parts[0]
                val engine = NORMAL_SEARCH_ENGINES.find { it.name == engineName }
                if (engine != null) {
                    currentSearchEngine = engine
                    updateSearchEngineIcon()
                }
            }
        }
        
        // If no valid engine was found or no default was set, use the first engine
        if (currentSearchEngine == null) {
            currentSearchEngine = NORMAL_SEARCH_ENGINES.firstOrNull()
            updateSearchEngineIcon()
        }
    }

    private fun updateSearchEngineIcon() {
        currentSearchEngine?.let { engine ->
            searchEngineButton.setImageResource(engine.iconResId)
        }
    }

    private fun openSearchEngine(engine: BaseSearchEngine) {
        currentSearchEngine = engine
        val searchText = searchInput.text.toString().trim()
        if (searchText.isNotEmpty()) {
            val searchUrl = engine.getSearchUrl(searchText)
            loadUrl(searchUrl)
        }
        drawerLayout.closeDrawer(
            if (settingsManager.isLeftHandedModeEnabled()) GravityCompat.END else GravityCompat.START
        )
    }

    private fun loadSearchEngineHomepage(engine: SearchEngine) {
        val baseUrl = engine.url.split("?")[0]
        webView.loadUrl(baseUrl)
    }

    private fun updateLayoutForHandedness() {
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        
        // 更新抽屉位置
        (drawerLayout.getChildAt(1) as? LinearLayout)?.let { drawer ->
            // 先关闭抽屉，避免切换时的动画问题
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            }
            
            // 更新抽屉位置
            drawer.layoutParams = (drawer.layoutParams as DrawerLayout.LayoutParams).apply {
                gravity = if (isLeftHanded) Gravity.END else Gravity.START
            }
        }

        // 更新菜单按钮位置
        val leftButtons = findViewById<LinearLayout>(R.id.left_buttons)
        val rightButtons = findViewById<LinearLayout>(R.id.right_buttons)
        val menuButton = findViewById<ImageButton>(R.id.btn_menu)

        // 从当前父容器中移除菜单按钮
        (menuButton.parent as? ViewGroup)?.removeView(menuButton)

        if (isLeftHanded) {
            // 左手模式：将菜单按钮添加到右侧按钮容器的开始位置
            rightButtons.addView(menuButton, 0)
        } else {
            // 右手模式：将菜单按钮添加到左侧按钮容器
            leftButtons.addView(menuButton)
        }

        // 重新测量和布局
        engineList.requestLayout()
        letterIndexBar.requestLayout()

        // 更新抽屉的锁定模式
        updateDrawerLockMode()
    }

    private fun updateDrawerLockMode() {
        if (settingsManager.isLeftHandedModeEnabled()) {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
        } else {
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        }
    }

    /**
     * 更新搜索框显示当前URL和标题
     */
    private fun updateSearchBoxWithCurrentUrl(url: String?) {
        if (url != null && url.isNotEmpty() && url != "about:blank" && ::searchInput.isInitialized) {
            try {
                runOnUiThread {
                    currentDisplayUrl = url
                    
                    // 获取标题（优先从WebView获取，因为更准确）
                    val currentWebView = if (isPaperStackMode) {
                        paperStackManager?.getCurrentTab()?.webView ?: webView
                    } else {
                        webView
                    }
                    
                    val title = currentWebView.title ?: if (isPaperStackMode) {
                        paperStackManager?.getCurrentTab()?.title
                    } else {
                        null
                    }
                    
                    currentDisplayTitle = title?.takeIf { it.isNotEmpty() } ?: extractDomain(url)
                    
                    // 更新显示
                    updateSearchBoxDisplay()
                    
                    Log.d("SearchActivity", "搜索框已更新: URL=$url, Title=$currentDisplayTitle, isPaperStackMode=$isPaperStackMode")
                }
            } catch (e: Exception) {
                Log.e("SearchActivity", "更新搜索框失败", e)
            }
        } else {
            Log.d("SearchActivity", "updateSearchBoxWithCurrentUrl: 跳过更新，url=$url, isInitialized=${::searchInput.isInitialized}")
        }
    }
    
    /**
     * 更新搜索框显示（根据焦点状态显示标题或URL）
     */
    private fun updateSearchBoxDisplay() {
        if (!::searchInput.isInitialized) {
            Log.w("SearchActivity", "updateSearchBoxDisplay: searchInput未初始化")
            return
        }
        
        try {
            if (isInputFocused) {
                // 有焦点时显示URL并选中
                if (!currentDisplayUrl.isNullOrEmpty()) {
                    searchInput.setText(currentDisplayUrl)
                    searchInput.setSelection(0, currentDisplayUrl!!.length)
                    searchInput.hint = ""
                    Log.d("SearchActivity", "updateSearchBoxDisplay: 有焦点，显示URL=$currentDisplayUrl")
                } else {
                    searchInput.setText("")
                    searchInput.hint = "搜索或输入网址"
                    Log.d("SearchActivity", "updateSearchBoxDisplay: 有焦点，但URL为空")
                }
            } else {
                // 无焦点时显示标题
                if (!currentDisplayTitle.isNullOrEmpty()) {
                    searchInput.setText("")
                    searchInput.hint = currentDisplayTitle
                    Log.d("SearchActivity", "updateSearchBoxDisplay: 无焦点，显示标题=$currentDisplayTitle")
                } else if (!currentDisplayUrl.isNullOrEmpty()) {
                    // 如果没有标题，显示URL（简化显示）
                    val domain = extractDomain(currentDisplayUrl!!)
                    searchInput.setText("")
                    searchInput.hint = domain
                    Log.d("SearchActivity", "updateSearchBoxDisplay: 无焦点，显示域名=$domain")
                } else {
                    searchInput.setText("")
                    searchInput.hint = "搜索或输入网址"
                    Log.d("SearchActivity", "updateSearchBoxDisplay: 无焦点，无URL和标题，显示默认提示")
                }
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "更新搜索框显示失败", e)
        }
    }

    override fun onBackPressed() {
        if (!handleBackNavigation()) {
            super.onBackPressed()
        }
    }
    
    /**
     * 加载搜索引擎首页
     */
    private fun loadSearchEngineHomepage() {
        currentSearchEngine?.let { engine ->
            webView.loadUrl(engine.url)
            Log.d("SearchActivity", "加载搜索引擎首页: ${engine.url}")
        }
    }
    
    /**
     * 显示图片上下文菜单
     */
    private fun showImageContextMenu(imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) return
        
        val options = arrayOf(
            "保存图片",
            "复制图片链接",
            "在新标签页中打开",
            "分享图片"
        )
        
        AlertDialog.Builder(this)
            .setTitle("图片操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> saveImage(imageUrl)
                    1 -> copyToClipboard(imageUrl)
                    2 -> openInNewTab(imageUrl)
                    3 -> shareImage(imageUrl)
                }
            }
            .show()
    }
    
    /**
     * 显示链接上下文菜单
     */
    private fun showLinkContextMenu(linkUrl: String?) {
        if (linkUrl.isNullOrEmpty()) return
        
        val options = arrayOf(
            "在新标签页中打开",
            "复制链接",
            "分享链接"
        )
        
        AlertDialog.Builder(this)
            .setTitle("链接操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openInNewTab(linkUrl)
                    1 -> copyToClipboard(linkUrl)
                    2 -> shareLink(linkUrl)
                }
            }
            .show()
    }
    
    /**
     * 保存图片
     */
    private fun saveImage(imageUrl: String) {
        try {
            // 使用增强下载管理器下载图片
            val enhancedDownloadManager = EnhancedDownloadManager(this)
            val downloadId = enhancedDownloadManager.downloadImage(imageUrl, object : EnhancedDownloadManager.DownloadCallback {
                override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                    Log.d("SearchActivity", "图片下载成功: $fileName")
                    Toast.makeText(this@SearchActivity, "图片已保存到相册", Toast.LENGTH_SHORT).show()
                    // 下载完成，减少计数
                    decrementDownloadCount()
                }
                
                override fun onDownloadFailed(downloadId: Long, reason: Int) {
                    Log.e("SearchActivity", "图片下载失败: $reason")
                    Toast.makeText(this@SearchActivity, "图片保存失败", Toast.LENGTH_SHORT).show()
                    // 下载失败，减少计数
                    decrementDownloadCount()
                }
            })
            
            if (downloadId != -1L) {
                // 开始下载，增加计数
                incrementDownloadCount()
                Toast.makeText(this, "开始保存图片", Toast.LENGTH_SHORT).show()
                Log.d("SearchActivity", "开始下载图片: $imageUrl")
            } else {
                Toast.makeText(this, "无法开始下载图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "保存图片失败", e)
            Toast.makeText(this, "保存图片失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            Log.d("SearchActivity", "复制到剪贴板: $text")
        } catch (e: Exception) {
            Log.e("SearchActivity", "复制失败", e)
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在新标签页中打开
     */
    private fun openInNewTab(url: String) {
        try {
            // 在当前WebView中打开URL
            webView.loadUrl(url)
            Log.d("SearchActivity", "在新标签页中打开: $url")
        } catch (e: Exception) {
            Log.e("SearchActivity", "打开链接失败", e)
            Toast.makeText(this, "打开链接失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 分享图片
     */
    private fun shareImage(imageUrl: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, imageUrl)
            startActivity(Intent.createChooser(shareIntent, "分享图片"))
            Log.d("SearchActivity", "分享图片: $imageUrl")
        } catch (e: Exception) {
            Log.e("SearchActivity", "分享图片失败", e)
            Toast.makeText(this, "分享图片失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 分享链接
     */
    private fun shareLink(linkUrl: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, linkUrl)
            startActivity(Intent.createChooser(shareIntent, "分享链接"))
            Log.d("SearchActivity", "分享链接: $linkUrl")
        } catch (e: Exception) {
            Log.e("SearchActivity", "分享链接失败", e)
            Toast.makeText(this, "分享链接失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 开始视频检测
     */
    private fun startVideoDetection(view: WebView?) {
        if (view == null) return
        
        // 停止之前的检测
        stopVideoDetection()
        
        // 如果已经激活了悬浮播放器，不重复检测
        if (isVideoPlayerActive) return
        
        // 延迟检测，给页面一些时间加载视频元素
        // 使用多次检测，确保能捕获到视频元素
        videoDetectionRunnable = Runnable {
            Log.d("SearchActivity", "执行第一次视频检测")
            detectAndLaunchVideoPlayer(view)
            
            // 如果第一次没检测到，延迟再检测多次（可能视频元素是动态加载的）
            if (!isVideoPlayerActive) {
                videoDetectionHandler.postDelayed({
                    if (!isVideoPlayerActive) {
                        Log.d("SearchActivity", "执行第二次视频检测（延迟2秒）")
                        detectAndLaunchVideoPlayer(view)
                        
                        // 第三次检测（延迟5秒）
                        if (!isVideoPlayerActive) {
                            videoDetectionHandler.postDelayed({
                                if (!isVideoPlayerActive) {
                                    Log.d("SearchActivity", "执行第三次视频检测（延迟5秒）")
                                    detectAndLaunchVideoPlayer(view)
                                }
                            }, 3000)
                        }
                    }
                }, 2000)
            }
        }
        Log.d("SearchActivity", "启动视频检测，延迟500ms")
        videoDetectionHandler.postDelayed(videoDetectionRunnable!!, 500)
    }
    
    /**
     * 停止视频检测
     */
    private fun stopVideoDetection() {
        videoDetectionRunnable?.let {
            videoDetectionHandler.removeCallbacks(it)
        }
        videoDetectionRunnable = null
    }
    
    /**
     * 检测并启动视频播放器
     * 参考Alook浏览器的模式：自动检测视频并替换内联播放器
     */
    private fun detectAndLaunchVideoPlayer(view: WebView) {
        if (isVideoPlayerActive) {
            Log.d("SearchActivity", "悬浮播放器已激活，跳过检测")
            return
        }
        
        Log.d("SearchActivity", "开始检测视频元素，URL: ${view.url}")
        
        try {
            // 使用JavaScript检测页面中的视频元素
            // 放宽检测条件：只要有video元素就尝试启动
            view.evaluateJavascript("""
                (function() {
                    var videos = document.querySelectorAll('video');
                    var videoInfo = null;
                    
                    console.log('检测到 ' + videos.length + ' 个video元素');
                    
                    if (videos.length > 0) {
                        var video = videos[0];
                        
                        // 放宽条件：只要有video元素就尝试提取信息
                        var hasSrc = (video.src && video.src.length > 0) || 
                                   (video.currentSrc && video.currentSrc.length > 0);
                        var hasPoster = video.poster && video.poster.length > 0;
                        var hasSource = video.querySelectorAll('source').length > 0;
                        var hasVideoTag = true; // 只要有video标签就认为有视频
                        
                        console.log('视频信息 - hasSrc: ' + hasSrc + ', hasPoster: ' + hasPoster + ', hasSource: ' + hasSource + ', readyState: ' + video.readyState);
                        
                        // 只要有video元素就尝试启动（不要求有src，因为可能通过source标签加载）
                        if (hasVideoTag) {
                            videoInfo = {
                                src: video.src || video.currentSrc || '',
                                poster: video.poster || '',
                                currentSrc: video.currentSrc || video.src || '',
                                isPlaying: !video.paused,
                                duration: video.duration || 0,
                                currentTime: video.currentTime || 0,
                                readyState: video.readyState || 0,
                                hasVideoTag: true
                            };
                            
                            console.log('提取到视频信息: ' + JSON.stringify(videoInfo));
                        }
                    }
                    
                    // 也检查iframe中的视频
                    var iframes = document.querySelectorAll('iframe');
                    console.log('检测到 ' + iframes.length + ' 个iframe元素');
                    
                    for (var i = 0; i < iframes.length; i++) {
                        var iframe = iframes[i];
                        var src = iframe.src || '';
                        
                        // 检查是否是视频网站（包括360搜索等）
                        if (src.includes('youtube.com') || 
                            src.includes('youtu.be') ||
                            src.includes('bilibili.com') ||
                            src.includes('v.qq.com') ||
                            src.includes('iqiyi.com') ||
                            src.includes('youku.com') ||
                            src.includes('douyin.com') ||
                            src.includes('kuaishou.com') ||
                            src.includes('qukanbbc') ||
                            src.includes('360.com') ||
                            src.includes('so.com')) {
                            console.log('检测到视频iframe: ' + src);
                            if (!videoInfo) {
                                videoInfo = {
                                    src: src,
                                    poster: '',
                                    currentSrc: src,
                                    isPlaying: false,
                                    duration: 0,
                                    currentTime: 0,
                                    isIframe: true
                                };
                            }
                        }
                    }
                    
                    var result = videoInfo ? JSON.stringify(videoInfo) : null;
                    console.log('检测结果: ' + result);
                    return result;
                })();
            """.trimIndent()) { result ->
                Log.d("SearchActivity", "视频检测结果: $result")
                
                if (result != null && result != "null" && result.isNotEmpty()) {
                    try {
                        // 解析视频信息
                        val videoInfoJson = result.removeSurrounding("\"")
                            .replace("\\\"", "\"")
                            .replace("\\n", "")
                            .trim()
                        
                        Log.d("SearchActivity", "解析后的视频信息JSON: $videoInfoJson")
                        
                        if (videoInfoJson.isNotEmpty() && videoInfoJson != "null") {
                            // 提取视频URL
                            val videoUrl = extractVideoUrlFromJson(videoInfoJson, view.url)
                            Log.d("SearchActivity", "提取的视频URL: $videoUrl, 页面URL: ${view.url}")
                            
                            if (videoUrl != null) {
                                // 启动悬浮播放器并隐藏原始视频
                                Log.d("SearchActivity", "准备启动悬浮播放器")
                                launchFloatingVideoPlayer(videoUrl, view.url, view)
                            } else {
                                Log.w("SearchActivity", "无法提取视频URL，尝试使用页面URL")
                                // 如果无法提取视频URL，使用页面URL
                                launchFloatingVideoPlayer(view.url, view.url, view)
                            }
                        } else {
                            Log.d("SearchActivity", "未检测到视频信息")
                        }
                    } catch (e: Exception) {
                        Log.e("SearchActivity", "解析视频信息失败", e)
                        e.printStackTrace()
                    }
                } else {
                    Log.d("SearchActivity", "JavaScript返回null或空结果")
                }
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "检测视频失败", e)
            e.printStackTrace()
        }
    }
    
    /**
     * 从JSON中提取视频URL
     */
    private fun extractVideoUrlFromJson(jsonStr: String, pageUrl: String?): String? {
        return try {
            Log.d("SearchActivity", "开始提取视频URL，JSON: $jsonStr, 页面URL: $pageUrl")
            
            // 简单解析JSON字符串
            val srcMatch = Regex("\"src\"\\s*:\\s*\"([^\"]+)\"").find(jsonStr)
            val currentSrcMatch = Regex("\"currentSrc\"\\s*:\\s*\"([^\"]+)\"").find(jsonStr)
            
            var videoUrl = srcMatch?.groupValues?.get(1) 
                ?: currentSrcMatch?.groupValues?.get(1)
            
            Log.d("SearchActivity", "提取的原始videoUrl: $videoUrl")
            
            // 如果视频URL是相对路径，转换为绝对路径
            if (videoUrl != null && videoUrl.isNotEmpty() && videoUrl != "null") {
                if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                    Log.d("SearchActivity", "使用绝对URL: $videoUrl")
                    videoUrl
                } else if (pageUrl != null) {
                    // 尝试构建绝对URL
                    val baseUrl = pageUrl.substringBeforeLast("/")
                    val absoluteUrl = "$baseUrl/$videoUrl".replace("//", "/").replace(":/", "://")
                    Log.d("SearchActivity", "构建的绝对URL: $absoluteUrl")
                    absoluteUrl
                } else {
                    Log.w("SearchActivity", "无法构建绝对URL，videoUrl为空或pageUrl为空")
                    null
                }
            } else {
                // 如果没有找到视频URL，但检测到iframe或hasVideoTag，返回页面URL
                if (jsonStr.contains("isIframe") || jsonStr.contains("hasVideoTag")) {
                    Log.d("SearchActivity", "检测到iframe或video标签，使用页面URL: $pageUrl")
                    pageUrl
                } else {
                    Log.w("SearchActivity", "未找到视频URL且未检测到iframe/video标签")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "提取视频URL失败", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 启动悬浮视频播放器
     * 在启动前会检查并申请悬浮窗权限，并隐藏原始页面的视频元素
     * 参考Alook浏览器的模式：替换内联播放器
     */
    private fun launchFloatingVideoPlayer(videoUrl: String?, pageUrl: String?, webView: WebView? = null) {
        if (isVideoPlayerActive) {
            Log.d("SearchActivity", "悬浮播放器已激活，跳过重复启动")
            return
        }
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Log.d("SearchActivity", "没有悬浮窗权限，请求权限")
                // 保存待启动的视频信息
                pendingVideoUrl = videoUrl
                pendingPageUrl = pageUrl
                
                // 请求悬浮窗权限
                requestOverlayPermission()
                return
            }
        }
        
        // 有权限，先隐藏原始页面的视频元素（替换内联播放器）
        webView?.let { view ->
            hideOriginalVideoElements(view)
        }
        
        // 启动悬浮播放器
        try {
            val intent = Intent(this, FloatingVideoPlayerService::class.java).apply {
                action = FloatingVideoPlayerService.ACTION_START_PLAYER
                putExtra(FloatingVideoPlayerService.EXTRA_VIDEO_URL, videoUrl)
                putExtra(FloatingVideoPlayerService.EXTRA_PAGE_URL, pageUrl)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            isVideoPlayerActive = true
            Log.d("SearchActivity", "已启动悬浮视频播放器: videoUrl=$videoUrl, pageUrl=$pageUrl")
            Toast.makeText(this, "视频已切换到悬浮播放器", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SearchActivity", "启动悬浮视频播放器失败", e)
            Toast.makeText(this, "启动悬浮播放器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 隐藏原始页面的视频元素
     * 参考Alook模式：替换内联播放器，隐藏原始视频元素
     */
    private fun hideOriginalVideoElements(webView: WebView) {
        try {
            webView.evaluateJavascript("""
                (function() {
                    // 隐藏所有video元素
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        video.style.display = 'none';
                        video.style.visibility = 'hidden';
                        video.style.opacity = '0';
                        video.style.position = 'absolute';
                        video.style.width = '0';
                        video.style.height = '0';
                        // 暂停原始视频（避免同时播放）
                        if (!video.paused) {
                            video.pause();
                        }
                    });
                    
                    // 隐藏包含视频的容器（如果可能识别的话）
                    var videoContainers = document.querySelectorAll('[class*="video"], [id*="video"], [class*="player"], [id*="player"]');
                    videoContainers.forEach(function(container) {
                        // 检查容器内是否有video元素
                        if (container.querySelector('video')) {
                            container.style.display = 'none';
                            container.style.visibility = 'hidden';
                        }
                    });
                    
                    // 隐藏iframe中的视频（部分网站）
                    var iframes = document.querySelectorAll('iframe');
                    iframes.forEach(function(iframe) {
                        var src = iframe.src || '';
                        if (src.includes('youtube.com') || 
                            src.includes('youtu.be') ||
                            src.includes('bilibili.com') ||
                            src.includes('v.qq.com') ||
                            src.includes('iqiyi.com') ||
                            src.includes('youku.com') ||
                            src.includes('douyin.com') ||
                            src.includes('kuaishou.com') ||
                            src.includes('qukanbbc')) {
                            iframe.style.display = 'none';
                            iframe.style.visibility = 'hidden';
                        }
                    });
                    
                    return 'hidden';
                })();
            """.trimIndent(), null)
            
            Log.d("SearchActivity", "已隐藏原始页面的视频元素")
        } catch (e: Exception) {
            Log.e("SearchActivity", "隐藏原始视频元素失败", e)
        }
    }
    
    /**
     * 恢复原始页面的视频元素（当悬浮播放器关闭时）
     */
    private fun restoreOriginalVideoElements(webView: WebView) {
        try {
            webView.evaluateJavascript("""
                (function() {
                    // 恢复所有video元素
                    var videos = document.querySelectorAll('video');
                    videos.forEach(function(video) {
                        video.style.display = '';
                        video.style.visibility = '';
                        video.style.opacity = '';
                        video.style.position = '';
                        video.style.width = '';
                        video.style.height = '';
                    });
                    
                    // 恢复视频容器
                    var videoContainers = document.querySelectorAll('[class*="video"], [id*="video"], [class*="player"], [id*="player"]');
                    videoContainers.forEach(function(container) {
                        if (container.querySelector('video')) {
                            container.style.display = '';
                            container.style.visibility = '';
                        }
                    });
                    
                    // 恢复iframe
                    var iframes = document.querySelectorAll('iframe');
                    iframes.forEach(function(iframe) {
                        var src = iframe.src || '';
                        if (src.includes('youtube.com') || 
                            src.includes('youtu.be') ||
                            src.includes('bilibili.com') ||
                            src.includes('v.qq.com') ||
                            src.includes('iqiyi.com') ||
                            src.includes('youku.com') ||
                            src.includes('douyin.com') ||
                            src.includes('kuaishou.com') ||
                            src.includes('qukanbbc')) {
                            iframe.style.display = '';
                            iframe.style.visibility = '';
                        }
                    });
                    
                    return 'restored';
                })();
            """.trimIndent(), null)
            
            Log.d("SearchActivity", "已恢复原始页面的视频元素")
        } catch (e: Exception) {
            Log.e("SearchActivity", "恢复原始视频元素失败", e)
        }
    }
    
    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // 显示说明对话框
                AlertDialog.Builder(this)
                    .setTitle("需要悬浮窗权限")
                    .setMessage("为了在浏览页面时显示悬浮视频播放器，需要授予悬浮窗权限。\n\n请在设置页面中开启「显示在其他应用的上层」权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        // 跳转到悬浮窗权限设置页面
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        overlayPermissionLauncher.launch(intent)
                    }
                    .setNegativeButton("取消") { _, _ ->
                        pendingVideoUrl = null
                        pendingPageUrl = null
                        Toast.makeText(this, "已取消启动悬浮播放器", Toast.LENGTH_SHORT).show()
                    }
                    .setCancelable(true)
                    .show()
            }
        }
    }
    
    /**
     * 停止悬浮视频播放器
     * 停止时恢复原始页面的视频元素
     */
    private fun stopFloatingVideoPlayer() {
        if (!isVideoPlayerActive) return
        
        try {
            // 恢复原始页面的视频元素
            val currentWebView = if (isPaperStackMode && paperStackManager != null) {
                paperStackManager?.getCurrentTab()?.webView
            } else {
                webView
            }
            currentWebView?.let { view ->
                restoreOriginalVideoElements(view)
            }
            
            val intent = Intent(this, FloatingVideoPlayerService::class.java).apply {
                action = FloatingVideoPlayerService.ACTION_STOP_PLAYER
            }
            stopService(intent)
            isVideoPlayerActive = false
            Log.d("SearchActivity", "已停止悬浮视频播放器")
        } catch (e: Exception) {
            Log.e("SearchActivity", "停止悬浮视频播放器失败", e)
        }
    }

    override fun onDestroy() {
        try {
            // 停止视频检测和播放器
            stopVideoDetection()
            stopFloatingVideoPlayer()
            
            // 清理系统级悬浮窗播放器
            try {
                if (::systemOverlayVideoManager.isInitialized) {
                    systemOverlayVideoManager.destroy()
                }
            } catch (e: Exception) {
                Log.e("SearchActivity", "清理系统级悬浮窗播放器失败", e)
            }
            
            // 清理Activity内悬浮窗播放器
            try {
                if (::floatingVideoManager.isInitialized && floatingVideoManager.isShowing()) {
                    floatingVideoManager.hide()
                }
            } catch (e: Exception) {
                Log.e("SearchActivity", "清理Activity内悬浮窗播放器失败", e)
            }
            
            // 清理纸堆管理器
            paperStackManager?.cleanup()
            
            if (isSettingsReceiverRegistered) {
                unregisterReceiver(settingsReceiver)
                isSettingsReceiverRegistered = false
            }
            if (isLayoutThemeReceiverRegistered) {
                unregisterReceiver(layoutThemeReceiver)
                isLayoutThemeReceiverRegistered = false
            }
            // 取消注册搜索模式变更接收器
            try {
                unregisterReceiver(searchModeReceiver)
            } catch (e: Exception) {
                // 如果接收器未注册，忽略异常
                Log.e("SearchActivity", "取消注册searchModeReceiver失败", e)
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "Error unregistering receivers", e)
        }
        super.onDestroy()
    }

    private fun updateLetterIndexBarTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        
        // 设置深色模式状态
        letterIndexBar.setDarkMode(isDarkMode)
        
        // 设置背景色
        letterIndexBar.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_background_dark
            else R.color.letter_index_background_light))
            
        // 设置字母标题的颜色和背景
        letterTitle.setTextColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_text_dark
            else R.color.letter_index_text_light))
        letterTitle.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_selected_background_dark
            else R.color.letter_index_selected_background_light))
    }

    private fun updateEngineListTheme() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        // 更新抽屉布局背景色
        (drawerLayout.getChildAt(1) as? LinearLayout)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@SearchActivity,
                if (isDarkMode) R.color.engine_list_background_dark
                else R.color.engine_list_background_light))
        }

        // 更新搜索引擎列表的背景色
        previewEngineList.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.engine_list_background_dark
            else R.color.engine_list_background_light))

        // 更新每个搜索引擎项的颜色
        for (i in 0 until previewEngineList.childCount) {
            val child = previewEngineList.getChildAt(i)
            if (child is ViewGroup) {
                // 更新引擎名称文本颜色
                child.findViewById<TextView>(R.id.engine_name)?.apply {
                    setTextColor(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.engine_name_text_dark
                        else R.color.engine_name_text_light))
                }

                // 更新引擎描述文本颜色
                child.findViewById<TextView>(R.id.engine_description)?.apply {
                    setTextColor(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.engine_description_text_dark
                        else R.color.engine_description_text_light))
                }

                // 更新图标颜色
                child.findViewById<ImageView>(R.id.engine_icon)?.apply {
                    setColorFilter(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.engine_icon_dark
                        else R.color.engine_icon_light))
                }

                // 更新整个项目的背景
                child.setBackgroundColor(ContextCompat.getColor(this,
                    if (isDarkMode) R.color.engine_list_background_dark
                    else R.color.engine_list_background_light))
            } else if (child is View && child.layoutParams.height == 1) {
                // 更新分隔线颜色
                child.setBackgroundColor(ContextCompat.getColor(this,
                    if (isDarkMode) R.color.divider_dark
                    else R.color.divider_light))
            }
        }

        // 强制重绘整个列表
        previewEngineList.invalidate()
    }

    private fun setupDrawer() {
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            
            override fun onDrawerOpened(drawerView: View) {
                updateEngineList()
            }
            
            override fun onDrawerClosed(drawerView: View) {}
            
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // 根据当前模式设置初始抽屉位置
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        (drawerLayout.getChildAt(1) as? LinearLayout)?.let { drawer ->
            drawer.layoutParams = (drawer.layoutParams as DrawerLayout.LayoutParams).apply {
                gravity = if (isLeftHanded) Gravity.END else Gravity.START
            }
        }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    private fun setupEngineList() {
        // Set layout manager
        engineList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        
        // Initialize adapter with empty list
        engineAdapter = EngineAdapter(
            engines = emptyList(),
            onEngineSelected = { engine ->
                openSearchEngine(engine)
                drawerLayout.closeDrawer(if (settingsManager.isLeftHandedModeEnabled()) GravityCompat.END else GravityCompat.START)
            }
        )
        
        // Attach adapter to RecyclerView
        engineList.adapter = engineAdapter
        
        // Load initial engines
        updateEngineAdapter()
        
        // Register for search mode changes
        settingsManager.registerOnSettingChangeListener<Boolean>("default_search_mode") { _, isAIMode ->
            // Update adapter when search mode changes
            updateEngineAdapter()
        }
        
        // 监听主题模式变化
        settingsManager.registerOnSettingChangeListener<Int>("theme_mode") { _, value ->
            updateTheme()
        }
    }
    
    private fun updateEngineAdapter() {
        val isAIMode = settingsManager.isDefaultAIMode()
        val engines = if (isAIMode) {
            AISearchEngine.DEFAULT_AI_ENGINES
        } else {
            SearchEngine.DEFAULT_ENGINES
        }
        engineAdapter.updateEngines(engines)
    }

    private fun checkClipboard() {
        if (intent.getBooleanExtra("from_floating_ball", false)) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                val clipText = clipData?.getItemAt(0)?.text?.toString()?.trim()
                
                if (!clipText.isNullOrEmpty()) {
                    showClipboardDialog(clipText)
                }
            }
        }
    }

    private fun showClipboardDialog(content: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.overlay_dialog, null)
        
        // 设置内容
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "检测到剪贴板内容"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = content
        
        // 创建对话框
        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .create()
        
        // 设置按钮点击事件
        if (URLUtil.isValidUrl(content)) {
            dialogView.findViewById<Button>(R.id.btn_open_link).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    openUrl(content)
                }
            }
        }
        
        dialogView.findViewById<Button>(R.id.btn_search).setOnClickListener {
            dialog.dismiss()
            searchContent(content)
        }
        
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        // 显示对话框
        dialog.show()
        
        // 设置自动关闭
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 5000)
    }
    
    private fun openUrl(url: String) {
        try {
            val intent = Intent(this, FullscreenWebViewActivity::class.java).apply {
                putExtra("url", url)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "打开URL失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun searchContent(query: String) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.baidu.com/s?wd=$encodedQuery"
            openUrl(searchUrl)
        } catch (e: Exception) {
            Toast.makeText(this, "搜索内容失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerReceivers() {
        try {
            // Register settings change receiver
            val settingsFilter = IntentFilter(Constants.ACTION_SETTINGS_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(settingsReceiver, settingsFilter)
            }
            isSettingsReceiverRegistered = true
            
            // Register layout theme change receiver
            val themeFilter = IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(layoutThemeReceiver, themeFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(layoutThemeReceiver, themeFilter)
            }
            isLayoutThemeReceiverRegistered = true
            
            // 注册搜索模式变更广播接收器
            val searchModeFilter = IntentFilter()
            searchModeFilter.addAction("com.example.aifloatingball.SEARCH_MODE_CHANGED")
            searchModeFilter.priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(searchModeReceiver, searchModeFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(searchModeReceiver, searchModeFilter)
            }
            
            // 发送测试广播以验证接收器是否正常工作
            val testIntent = Intent("com.example.aifloatingball.SEARCH_MODE_CHANGED")
            testIntent.putExtra("test", true)
            sendBroadcast(testIntent)
            
            Log.d("SearchActivity", "搜索模式变更广播接收器注册成功, 当前类: ${this.javaClass.name}, 包名: ${this.packageName}")
            Log.d("SearchActivity", "当前AI模式状态: ${if(settingsManager.isDefaultAIMode()) "开启" else "关闭"}")
        } catch (e: Exception) {
            Log.e("SearchActivity", "Error registering receivers", e)
        }
    }

    private fun setupSearchViews() {
        // 设置搜索引擎按钮点击事件
        searchEngineButton.setOnClickListener {
            showSearchEngineSelector()
        }

        // 设置清除按钮点击事件
        clearSearchButton.setOnClickListener {
            searchInput.setText("")
            clearSearchButton.visibility = View.GONE
            // 清除后恢复显示标题
            updateSearchBoxDisplay()
        }

        // 设置搜索框焦点监听
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            isInputFocused = hasFocus
            if (hasFocus) {
                // 获得焦点时显示URL并选中
                if (!currentDisplayUrl.isNullOrEmpty()) {
                    searchInput.setText(currentDisplayUrl)
                    searchInput.setSelection(0, currentDisplayUrl!!.length)
                    searchInput.hint = ""
                }
            } else {
                // 失去焦点时恢复显示标题
                updateSearchBoxDisplay()
            }
        }
        
        // 设置点击事件（确保点击时也能触发焦点逻辑）
        searchInput.setOnClickListener {
            if (!isInputFocused) {
                searchInput.requestFocus()
            }
        }

        // 设置搜索框文本变化监听
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                clearSearchButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
        })

        // 设置搜索动作监听
        searchInput.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val query = searchInput.text.toString().trim()
                    if (query.isNotEmpty()) {
                        performSearch(query)
                    // 隐藏键盘
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                    // 失去焦点后恢复显示标题
                    searchInput.clearFocus()
                    return@setOnEditorActionListener true
                    }
            }
                    false
                }
            }

    private fun showSearchEngineSelector() {
        val engines = NORMAL_SEARCH_ENGINES
        val engineNames = engines.map { it.name }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择搜索引擎")
            .setItems(engineNames) { _, which ->
                val selectedEngine = engines[which]
                openSearchEngine(selectedEngine)
            }
            .show()
    }

    private fun performSearch(query: String) {
        val searchEngine = currentSearchEngine ?: return
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = searchEngine.url.replace("{query}", encodedQuery)
        
        if (isPaperStackMode && paperStackManager != null) {
            // 纸堆模式：添加新标签页
            val newTab = paperStackManager?.addTab(searchUrl, "搜索结果: $query")
            if (newTab != null) {
                updatePaperCountText()
                showPaperStackControls()
                hidePaperStackHint()
                Log.d("SearchActivity", "在纸堆模式中添加搜索结果标签页: $searchUrl")
            }
        } else {
            // 普通模式：在当前WebView中加载
            webView.loadUrl(searchUrl)
        }
    }

    // 添加加载搜索引擎的方法
    private fun loadSearchEngines(forceRefresh: Boolean = false) {
        try {
            searchEngines.clear()
            val engines = if (settingsManager.isDefaultAIMode()) {
                AISearchEngine.DEFAULT_AI_ENGINES
            } else {
                SearchEngine.DEFAULT_ENGINES
            }
            searchEngines.addAll(engines)
            updateEngineAdapter()
        } catch (e: Exception) {
            Log.e("SearchActivity", "加载搜索引擎失败", e)
            Toast.makeText(this, "加载搜索引擎失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 添加模式切换提示
    private fun showModeChangeToast(isAIMode: Boolean) {
        val message = if (isAIMode) "已切换到AI搜索模式" else "已切换到普通搜索模式"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveSearchHistory(query: String, url: String) {
        try {
            val history = settingsManager.getSearchHistory().toMutableList()
            val entry = mapOf(
                "query" to query,
                "url" to url,
                "timestamp" to System.currentTimeMillis()
            )
            history.add(0, entry)
            settingsManager.saveSearchHistory(history.take(100))
            } catch (e: Exception) {
            Log.e("SearchActivity", "保存搜索历史失败", e)
        }
    }

    private fun openInLetterListFloatingWindow(url: String) {
        try {
            // 创建与字母列表长按相同的悬浮窗Intent
            val intent = Intent(this, FloatingWebViewService::class.java).apply {
                putExtra("url", url)
                putExtra("from_ai_menu", true) // 标记来源，以便特殊处理
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("SearchActivity", "打开悬浮窗失败", e)
            Toast.makeText(this, "打开悬浮窗失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 更新布局位置
    private fun updateLayoutPosition() {
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        val layoutParams = searchLayout?.layoutParams as? FrameLayout.LayoutParams
        
        layoutParams?.gravity = if (isLeftHanded) Gravity.START else Gravity.END
        searchLayout?.layoutParams = layoutParams
    }

    // 保存设置
    private fun saveSettings() {
        // 使用 SettingsManager 直接保存设置，不依赖 UI 组件
        val searchHistoryEnabled = searchHistorySwitch?.isChecked ?: settingsManager.getBoolean("search_history_enabled", true)
        val autoPasteEnabled = autoPasteSwitch?.isChecked ?: settingsManager.getBoolean("auto_paste_enabled", true)
        
        settingsManager.putBoolean("search_history_enabled", searchHistoryEnabled)
        settingsManager.putBoolean("auto_paste_enabled", autoPasteEnabled)
    }

    // 加载设置
    private fun loadSettings() {
        // 如果 UI 组件存在，则将设置值加载到它们上
        searchHistorySwitch?.isChecked = settingsManager.getBoolean("search_history_enabled", true)
        autoPasteSwitch?.isChecked = settingsManager.getBoolean("auto_paste_enabled", true)
        
        // 加载默认搜索引擎
        val defaultEngine = settingsManager.getString("default_search_engine", "baidu")
        updateDefaultSearchEngine(defaultEngine ?: "baidu")
    }

    // 更新主题
    private fun updateTheme() {
        try {
            when (settingsManager.getThemeMode()) {
                SettingsManager.THEME_MODE_SYSTEM -> {
                    // 使用默认主题
                    window.statusBarColor = getColor(R.color.colorPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorPrimaryDark)
                    rootLayout?.setBackgroundColor(getColor(R.color.colorBackground))
                }
                SettingsManager.THEME_MODE_DEFAULT -> {
                    // 使用默认主题
                    window.statusBarColor = getColor(R.color.colorPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorPrimaryDark)
                    searchLayout?.setBackgroundColor(getColor(R.color.colorBackground))
                }
                SettingsManager.THEME_MODE_LIGHT -> {
                    // 使用浅色主题
                    window.statusBarColor = getColor(R.color.colorLightPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorLightPrimaryDark)
                    searchLayout?.setBackgroundColor(getColor(R.color.colorLightBackground))
                }
                SettingsManager.THEME_MODE_DARK -> {
                    // 使用深色主题
                    window.statusBarColor = getColor(R.color.colorDarkPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorDarkPrimaryDark)
                    searchLayout?.setBackgroundColor(getColor(R.color.colorDarkBackground))
                }
            }
        } catch (e: Resources.NotFoundException) {
            // 如果颜色资源不存在，使用默认颜色
            Log.e("SearchActivity", "Error applying theme: ${e.message}")
            window.statusBarColor = Color.parseColor("#1976D2") // Default blue
            searchLayout?.setBackgroundColor(Color.WHITE)
        }
    }

    // 更新默认搜索引擎
    private fun updateDefaultSearchEngine(engine: String) {
        try {
            // 保存设置
            settingsManager.setDefaultSearchEngine(engine)
            
            // 查找匹配的搜索引擎并更新当前搜索引擎
            val searchEngine = NORMAL_SEARCH_ENGINES.find { it.name == engine }
            if (searchEngine != null) {
                currentSearchEngine = searchEngine
                updateSearchEngineIcon()
                
                // 显示提示
                Toast.makeText(this, "默认搜索引擎已设置为: ${searchEngine.name}", Toast.LENGTH_SHORT).show()
            } else {
                // 如果找不到匹配的搜索引擎，使用第一个
                currentSearchEngine = NORMAL_SEARCH_ENGINES.firstOrNull()
                updateSearchEngineIcon()
                
                // 显示错误提示
                Toast.makeText(this, "无法找到搜索引擎: $engine，已使用默认值", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "更新默认搜索引擎失败", e)
            Toast.makeText(this, "更新默认搜索引擎失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Update default search engine
    private fun updateDefaultSearchEngine() {
        val defaultEngine = settingsManager.getString(Constants.PREF_DEFAULT_SEARCH_ENGINE, Constants.DEFAULT_SEARCH_ENGINE)
        currentSearchEngine = searchEngines.find { engine ->
            when (engine) {
                is AISearchEngine -> engine.name == defaultEngine
                is SearchEngine -> engine.name == defaultEngine
                else -> false
            }
        } ?: searchEngines.firstOrNull()
        
        updateSearchEngineIcon()
    }

    // Send settings changed broadcast
    private fun notifySettingsChanged(leftHandedModeChanged: Boolean = false) {
        val intent = Intent(Constants.ACTION_SETTINGS_CHANGED).apply {
            putExtra(Constants.EXTRA_LEFT_HANDED_MODE_CHANGED, leftHandedModeChanged)
        }
        sendBroadcast(intent)
    }

    private fun loadUrl(url: String) {
        try {
            if (url == "home") {
                // 处理返回主页的特殊情况
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
                return
            }

            // 检查URL是否有效
            if (!URLUtil.isValidUrl(url)) {
                Toast.makeText(this, "无效的URL: $url", Toast.LENGTH_SHORT).show()
                return
            }

            // 加载URL
            webView.loadUrl(url)
            
            // 记录搜索历史
            if (settingsManager.getBoolean("search_history_enabled", true)) {
                saveSearchHistory(searchInput.text.toString(), url)
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "加载URL时出错: ${e.message}", e)
            Toast.makeText(this, "加载页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch() {
        val searchText = searchInput.text.toString().trim()
        if (searchText.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        currentSearchEngine?.let { engine ->
            val searchUrl = engine.getSearchUrl(searchText)
            loadUrl(searchUrl)
        } ?: run {
            Toast.makeText(this, "请先选择搜索引擎", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理搜索结果页面的链接点击
     */
    private fun handleSearchResultClick(view: WebView?, url: String): Boolean {
        Log.d("SearchActivity", "处理搜索结果点击: $url")
        
        return when {
            // 处理图片URL - 优先处理
            isImageUrl(url) -> {
                Log.d("SearchActivity", "检测到图片URL，打开图片查看器: $url")
                openImageInViewer(url)
                true
            }
            // 处理移动应用URL scheme重定向
            url.startsWith("baiduboxapp://") -> {
                Log.d("SearchActivity", "拦截百度App重定向，保持在WebView中")
                handleSearchEngineRedirect(view, url, "baidu")
                true
            }
            url.startsWith("mttbrowser://") -> {
                Log.d("SearchActivity", "拦截搜狗浏览器重定向，保持在WebView中")
                handleSearchEngineRedirect(view, url, "sogou")
                true
            }
            url.startsWith("quark://") -> {
                Log.d("SearchActivity", "拦截夸克浏览器重定向，保持在WebView中")
                handleSearchEngineRedirect(view, url, "quark")
                true
            }
            url.startsWith("ucbrowser://") -> {
                Log.d("SearchActivity", "拦截UC浏览器重定向，保持在WebView中")
                handleSearchEngineRedirect(view, url, "uc")
                true
            }
            // 处理搜索结果页面的链接
            url.contains("baidu.com/s?") && url.contains("wd=") -> {
                // 百度搜索结果页面，直接加载
                Log.d("SearchActivity", "加载百度搜索结果页面: $url")
                view?.loadUrl(url)
                true
            }
            url.contains("m.baidu.com/s?") && url.contains("wd=") -> {
                // 百度移动版搜索结果页面，直接加载
                Log.d("SearchActivity", "加载百度移动版搜索结果页面: $url")
                view?.loadUrl(url)
                true
            }
            url.contains("sogou.com/web?") && url.contains("query=") -> {
                // 搜狗搜索结果页面，直接加载
                Log.d("SearchActivity", "加载搜狗搜索结果页面: $url")
                view?.loadUrl(url)
                true
            }
            url.contains("m.sogou.com/web?") && url.contains("query=") -> {
                // 搜狗移动版搜索结果页面，直接加载
                Log.d("SearchActivity", "加载搜狗移动版搜索结果页面: $url")
                view?.loadUrl(url)
                true
            }
            // 优先处理 intent:// 链接，直接启动（类似 Chrome，让系统显示对话框）
            url.startsWith("intent://") -> {
                Log.d("SearchActivity", "检测到 intent:// 链接，直接启动Intent")
                launchIntentUrlDirectly(url)
                true
            }
            // 优先处理 clash:// 链接，直接启动（类似 Chrome，让系统显示对话框）
            url.startsWith("clash://") -> {
                Log.d("SearchActivity", "检测到 clash:// 链接，直接启动Intent")
                launchSchemeUrlDirectly(url)
                true
            }
            // 优先处理 douban:// 链接，直接启动（类似 Chrome，让系统显示对话框）
            url.startsWith("douban://") -> {
                Log.d("SearchActivity", "检测到 douban:// 链接，直接启动Intent")
                launchSchemeUrlDirectly(url)
                true
            }
            // 处理其他特殊协议
            url.startsWith("tel:") ||
            url.startsWith("mailto:") ||
            url.startsWith("sms:") -> {
                // 由系统处理
                true
            }
            // 处理其他应用URL scheme（baidumap://, amap:// 等），直接启动Intent（类似 Chrome）
            url.contains("://") && !url.startsWith("http://") && !url.startsWith("https://") && 
            !url.startsWith("file://") && !url.startsWith("javascript:") && 
            !url.startsWith("tel:") && !url.startsWith("mailto:") && !url.startsWith("sms:") -> {
                Log.d("SearchActivity", "检测到应用URL scheme: $url，直接启动Intent")
                // 尝试判断是否为 intent://
                if (url.startsWith("intent://")) {
                    launchIntentUrlDirectly(url)
                } else {
                    launchSchemeUrlDirectly(url)
                }
                true
            }
            // 处理文件下载
            url.endsWith(".apk") ||
            url.endsWith(".pdf") ||
            url.endsWith(".zip") ||
            url.endsWith(".rar") -> {
                // 可以在这里添加下载处理逻辑
                false
            }
            else -> {
                // 其他链接，在WebView中继续加载
                Log.d("SearchActivity", "在WebView中加载链接: $url")
                view?.loadUrl(url)
                true
            }
        }
    }

    /**
     * 检测是否为图片URL
     */
    private fun isImageUrl(url: String): Boolean {
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg")
        val imageDomains = listOf("image.baidu.com", "pic.sogou.com", "images.google.com", "cn.bing.com/images")
        
        // 检查文件扩展名
        val hasImageExtension = imageExtensions.any { url.lowercase().contains(it) }
        
        // 检查图片域名
        val hasImageDomain = imageDomains.any { url.contains(it) }
        
        // 检查图片相关的URL模式
        val hasImagePattern = url.contains("/image/") || 
                             url.contains("/pic/") || 
                             url.contains("/photo/") ||
                             url.contains("imgurl=") ||
                             url.contains("img=")
        
        return hasImageExtension || hasImageDomain || hasImagePattern
    }
    
    /**
     * 在图片查看器中打开图片
     */
    private fun openImageInViewer(imageUrl: String) {
        try {
            // 创建Intent打开图片
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(imageUrl), "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // 检查是否有应用可以处理图片
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                Log.d("SearchActivity", "成功打开图片查看器: $imageUrl")
                Toast.makeText(this, "正在打开图片", Toast.LENGTH_SHORT).show()
            } else {
                // 备用方案：在WebView中打开
                webView.loadUrl(imageUrl)
                Log.d("SearchActivity", "使用WebView打开图片: $imageUrl")
                Toast.makeText(this, "在浏览器中打开图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "打开图片失败", e)
            Toast.makeText(this, "打开图片失败", Toast.LENGTH_SHORT).show()
            
            // 最后备用方案：在WebView中打开
            try {
                webView.loadUrl(imageUrl)
                Toast.makeText(this, "在浏览器中打开图片", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Log.e("SearchActivity", "WebView打开图片也失败", e2)
                Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 处理应用URL scheme
     */
    private fun handleAppUrlScheme(view: WebView?, url: String) {
        try {
            val urlSchemeHandler = com.example.aifloatingball.manager.UrlSchemeHandler(this)
            urlSchemeHandler.handleUrlScheme(
                url = url,
                onSuccess = {
                    Log.d("SearchActivity", "URL scheme处理成功: $url")
                },
                onFailure = {
                    Log.w("SearchActivity", "URL scheme处理失败: $url")
                }
            )
        } catch (e: Exception) {
            Log.e("SearchActivity", "处理URL scheme时出错: $url", e)
        }
    }
    
    /**
     * 直接启动 intent:// URL（类似 Chrome，让系统显示应用选择对话框）
     */
    private fun launchIntentUrlDirectly(intentUrl: String) {
        try {
            val intent = Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // 针对 clash:// 指定优先包，确保精确拉起
            val data = intent.dataString
            if (intent.`package` == null && data != null && data.startsWith("clash://")) {
                val clashPackages = listOf("com.github.kr328.clash", "com.github.metacubex.clash")
                for (pkg in clashPackages) {
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        intent.`package` = pkg
                        break
                    } catch (_: Exception) { }
                }
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                // 直接启动，让系统显示应用选择对话框（类似 Chrome）
                startActivity(intent)
                Log.d("SearchActivity", "直接启动 intent:// 链接成功: $intentUrl")
            } else {
                // 如果没有应用可以处理，尝试 fallback URL
                val fallback = intent.getStringExtra("browser_fallback_url")
                if (!fallback.isNullOrBlank()) {
                    webView.loadUrl(fallback)
                } else {
                    Toast.makeText(this, "未找到可处理的应用", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "直接启动 intent:// 链接失败: $intentUrl", e)
            Toast.makeText(this, "打开应用失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 直接启动 scheme URL（类似 Chrome，让系统显示应用选择对话框）
     * 支持所有类型的应用scheme，自动识别并启动
     * 系统会自动匹配已安装的应用，无需手动指定包名
     */
    private fun launchSchemeUrlDirectly(schemeUrl: String) {
        try {
            val uri = Uri.parse(schemeUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // 针对 clash:// 指定优先包（特殊处理，因为可能有多个Clash版本）
            if (schemeUrl.startsWith("clash://")) {
                val clashPackages = listOf("com.github.kr328.clash", "com.github.metacubex.clash")
                for (pkg in clashPackages) {
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        intent.`package` = pkg
                        Log.d("SearchActivity", "为 clash:// 指定包名: $pkg")
                        break
                    } catch (_: Exception) { }
                }
            }
            
            // 检查是否有应用可以处理此Intent
            if (intent.resolveActivity(packageManager) != null) {
                // 直接启动，让系统显示应用选择对话框（类似 Chrome，识别所有intent）
                // 如果只有一个应用可以处理，系统会直接打开；如果有多个，系统会显示选择对话框
                startActivity(intent)
                Log.d("SearchActivity", "直接启动 scheme 链接成功: $schemeUrl")
            } else {
                // 没有应用可以处理，提示用户
                Toast.makeText(this, "未找到可处理的应用", Toast.LENGTH_SHORT).show()
                Log.w("SearchActivity", "没有应用可以处理 scheme: $schemeUrl")
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "直接启动 scheme 链接失败: $schemeUrl", e)
            Toast.makeText(this, "打开应用失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 处理搜索引擎重定向到移动应用的情况
     */
    private fun handleSearchEngineRedirect(view: WebView?, originalUrl: String, engineType: String) {
        if (view == null) return

        Log.d("SearchActivity", "处理搜索引擎重定向: $engineType, 原始URL: $originalUrl")

        try {
            // 从URL scheme中提取搜索参数
            val searchQuery = extractSearchQueryFromScheme(originalUrl, engineType)

            if (searchQuery.isNotEmpty()) {
                // 构建Web版本的搜索URL
                val webSearchUrl = buildWebSearchUrl(engineType, searchQuery)
                Log.d("SearchActivity", "重定向到Web版本: $webSearchUrl")
                view.loadUrl(webSearchUrl)
            } else {
                // 如果无法提取搜索词，重定向到搜索引擎首页
                val homepageUrl = getSearchEngineHomepage(engineType)
                Log.d("SearchActivity", "无法提取搜索词，重定向到首页: $homepageUrl")
                view.loadUrl(homepageUrl)
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "处理搜索引擎重定向失败", e)
            // 回退到搜索引擎首页
            val homepageUrl = getSearchEngineHomepage(engineType)
            view.loadUrl(homepageUrl)
        }
    }

    /**
     * 从URL scheme中提取搜索查询
     */
    private fun extractSearchQueryFromScheme(url: String, engineType: String): String {
        return try {
            when (engineType) {
                "baidu" -> {
                    // baiduboxapp://utils?action=sendIntent&minver=7.4&params=%7B...
                    val uri = android.net.Uri.parse(url)
                    val params = uri.getQueryParameter("params")
                    // 暂时返回空，让它重定向到首页
                    ""
                }
                "sogou" -> {
                    // mttbrowser://url=https://m.sogou.com/?...
                    val uri = android.net.Uri.parse(url)
                    val redirectUrl = uri.getQueryParameter("url")
                    if (!redirectUrl.isNullOrEmpty()) {
                        val redirectUri = android.net.Uri.parse(redirectUrl)
                        redirectUri.getQueryParameter("keyword") ?: redirectUri.getQueryParameter("query") ?: ""
                    } else {
                        ""
                    }
                }
                "quark" -> {
                    val uri = android.net.Uri.parse(url)
                    uri.getQueryParameter("q") ?: ""
                }
                "uc" -> {
                    val uri = android.net.Uri.parse(url)
                    uri.getQueryParameter("keyword") ?: ""
                }
                else -> ""
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "提取搜索查询失败: $url", e)
            ""
        }
    }

    /**
     * 构建Web版本的搜索URL
     */
    private fun buildWebSearchUrl(engineType: String, query: String): String {
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }

        return when (engineType) {
            "baidu" -> "https://m.baidu.com/s?wd=$encodedQuery"
            "sogou" -> "https://m.sogou.com/web?query=$encodedQuery"
            "quark" -> "https://quark.sm.cn/s?q=$encodedQuery"
            "uc" -> "https://www.uc.cn/s?wd=$encodedQuery"
            else -> "https://www.google.com/search?q=$encodedQuery"
        }
    }

    /**
     * 获取搜索引擎首页URL
     */
    private fun getSearchEngineHomepage(engineType: String): String {
        return when (engineType) {
            "baidu" -> "https://m.baidu.com"
            "sogou" -> "https://m.sogou.com"
            "quark" -> "https://quark.sm.cn"
            "uc" -> "https://www.uc.cn"
            else -> "https://www.google.com"
        }
    }
    
    /**
     * 打开下载管理器
     */
    private fun openDownloadManager() {
        try {
            val intent = Intent(this, DownloadManagerActivity::class.java)
            startActivity(intent)
            Log.d("SearchActivity", "打开下载管理器")
        } catch (e: Exception) {
            Log.e("SearchActivity", "打开下载管理器失败", e)
            Toast.makeText(this, "无法打开下载管理器", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 更新下载提示按钮状态
     */
    private fun updateDownloadIndicator() {
        try {
            if (activeDownloadCount > 0) {
                downloadIndicatorContainer.visibility = View.VISIBLE
                downloadProgressText.text = activeDownloadCount.toString()
                downloadProgressText.visibility = View.VISIBLE
                Log.d("SearchActivity", "显示下载提示按钮，活跃下载数: $activeDownloadCount")
            } else {
                downloadIndicatorContainer.visibility = View.GONE
                downloadProgressText.visibility = View.GONE
                Log.d("SearchActivity", "隐藏下载提示按钮")
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "更新下载提示按钮失败", e)
        }
    }
    
    /**
     * 增加活跃下载计数
     */
    fun incrementDownloadCount() {
        activeDownloadCount++
        updateDownloadIndicator()
        Log.d("SearchActivity", "增加下载计数，当前: $activeDownloadCount")
    }
    
    /**
     * 减少活跃下载计数
     */
    fun decrementDownloadCount() {
        if (activeDownloadCount > 0) {
            activeDownloadCount--
            updateDownloadIndicator()
            Log.d("SearchActivity", "减少下载计数，当前: $activeDownloadCount")
        }
    }
    
    /**
     * 重置下载计数
     */
    fun resetDownloadCount() {
        activeDownloadCount = 0
        updateDownloadIndicator()
        Log.d("SearchActivity", "重置下载计数")
    }
    
    // 统一处理返回逻辑：优先WebView历史；否则使用自维护栈；最后返回false
    private fun handleBackNavigation(): Boolean {
        return try {
            if (webView.canGoBack()) {
                webView.goBack()
                Log.d("SearchActivity", "使用WebView历史回退")
                true
            } else if (urlBackStack.size >= 2) {
                // 弹出当前页，回到上一历史URL
                urlBackStack.removeLast()
                val previous = if (urlBackStack.isEmpty()) null else urlBackStack.last()
                if (!previous.isNullOrEmpty()) {
                    isRestoringFromHistory = true
                    webView.loadUrl(previous)
                    Log.d("SearchActivity", "使用自定义历史回退到: $previous")
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "handleBackNavigation发生异常", e)
            false
        }
    }
} 
