package com.example.aifloatingball.viewer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import com.example.aifloatingball.R
import com.example.aifloatingball.tts.TTSManager
import com.example.aifloatingball.manager.UnifiedCollectionManager
import com.example.aifloatingball.model.CollectionType
import com.example.aifloatingball.model.UnifiedCollectionItem
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*

/**
 * 增强版文件阅读器Activity
 * 参考微信读书的功能实现，支持：
 * - 分页显示、页码计算
 * - 书签、划线、笔记
 * - 目录跳转
 * - 页面设置（字体、背景、行距等）
 * - TTS听书
 * - 自动翻页
 * - 分享功能
 */
/**
 * 划线样式枚举
 */
enum class HighlightStyle {
    HIGHLIGHT,      // 高亮/填充
    UNDERLINE,      // 下划线
    WAVY_UNDERLINE  // 波浪下划线
}

class FileReaderActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "FileReaderActivity"
        private const val EXTRA_FILE_URI = "file_uri"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_FILE_NAME = "file_name"
        private const val EXTRA_PAGE_INDEX = "page_index"
        
        /**
         * 启动文件阅读器
         */
        fun start(context: Activity, fileUri: Uri, fileName: String? = null, pageIndex: Int? = null) {
            val intent = Intent(context, FileReaderActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, fileUri.toString())
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
                pageIndex?.let { putExtra(EXTRA_PAGE_INDEX, it) }
            }
            context.startActivity(intent)
        }
        
        /**
         * 启动文件阅读器（使用文件路径）
         */
        fun startWithPath(context: Activity, filePath: String, fileName: String? = null, pageIndex: Int? = null) {
            val intent = Intent(context, FileReaderActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
                pageIndex?.let { putExtra(EXTRA_PAGE_INDEX, it) }
            }
            context.startActivity(intent)
        }
    }
    
    // UI组件
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorTextView: TextView
    
    // 顶部信息栏
    private lateinit var topInfoBar: LinearLayout
    private lateinit var bookTitle: TextView
    private lateinit var bookAuthor: TextView
    private lateinit var chapterName: TextView
    
    // 底部导航栏
    private lateinit var bottomNavBar: LinearLayout
    private lateinit var btnPrevPage: ImageButton
    private lateinit var btnNextPage: ImageButton
    private lateinit var btnCatalog: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var pageInfo: TextView
    
    // 功能菜单（精简版 + 统计）
    private lateinit var menuContainer: LinearLayout
    private lateinit var functionMenu: View
    private lateinit var menuCatalog: LinearLayout
    private lateinit var menuStats: LinearLayout
    private lateinit var menuSettings: LinearLayout
    private lateinit var menuTTS: LinearLayout
    private lateinit var menuAutoRead: LinearLayout
    private lateinit var ttsIcon: ImageView
    private lateinit var autoReadIcon: ImageView
    private lateinit var ttsText: TextView
    private lateinit var autoReadText: TextView
    
    // 阅读进度
    private lateinit var readingProgressBar: SeekBar
    private lateinit var readingProgressText: TextView
    private lateinit var readingProgressPercent: TextView
    
    // 阅读统计
    private var readingStartTime: Long = 0
    private var totalReadingTime: Long = 0  // 总阅读时间（毫秒）
    private var todayReadingTime: Long = 0  // 今日阅读时间（毫秒）
    
    // 数据管理
    private lateinit var dataManager: ReaderDataManager
    private var settings: ReaderSettings = ReaderSettings()
    
    // 文件信息
    private var filePath: String = ""
    private var fileName: String = ""
    private var fileUri: Uri? = null
    private var fullText: String = ""
    
    // 分页信息
    private var pages: List<String> = emptyList()
    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0
    
    // 章节信息
    private var chapters: List<Chapter> = emptyList()
    private var currentChapterIndex: Int = 0
    
    // TTS相关
    private var ttsManager: TTSManager? = null
    private var isTTSPlaying: Boolean = false
    
    // 自动翻页
    private var isAutoReading: Boolean = false
    private val autoReadHandler = Handler(Looper.getMainLooper())
    private var autoReadRunnable: Runnable? = null
    
    // 用于延迟保存进度
    private val handler = Handler(Looper.getMainLooper())
    
    // 手势识别
    private var gestureDetector: GestureDetector? = null
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private val SWIPE_THRESHOLD = 100 // 滑动阈值（像素）
    private val SWIPE_VELOCITY_THRESHOLD = 100 // 滑动速度阈值
    
    // UI显示控制
    private var isTopBarVisible: Boolean = false
    private var isBottomBarVisible: Boolean = false
    private var isMenuVisible: Boolean = false
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置沉浸式全屏模式（隐藏状态栏和导航栏）
        // 注意：必须在 setContentView 之前设置 setDecorFitsSystemWindows
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        
        setContentView(R.layout.activity_file_reader)
        
        // 在 setContentView 之后设置 WindowInsetsController（此时 DecorView 已创建）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        
        // 初始化数据管理器
        dataManager = ReaderDataManager(this)
        settings = dataManager.getSettings()
        
        // 应用保持屏幕常亮设置
        if (settings.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // 初始化TTS
        ttsManager = TTSManager.getInstance(this)
        
        initViews()
        setupClickListeners()
        setupSystemUI()
        loadFile()
    }
    
    /**
     * 设置系统UI（状态栏）
     */
    private fun setupSystemUI() {
        // 根据主题设置状态栏颜色和文字颜色
        val isDarkTheme = settings.theme == ReaderTheme.DARK
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (isDarkTheme) {
                // 深色主题：浅色状态栏文字
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                // 浅色主题：深色状态栏文字
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        
        // 设置状态栏颜色为透明
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
        
        // 为顶部信息栏添加状态栏高度的padding，避免重叠
        topInfoBar.post {
            val statusBarHeight = getStatusBarHeight()
            topInfoBar.setPadding(
                topInfoBar.paddingLeft,
                statusBarHeight + topInfoBar.paddingTop,
                topInfoBar.paddingRight,
                topInfoBar.paddingBottom
            )
        }
        
        // 更新菜单主题
        updateMenuTheme()
    }
    
    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    /**
     * 更新菜单主题（支持暗色模式）
     */
    private fun updateMenuTheme() {
        // MaterialCardView 会自动根据主题适配背景色
        // 这里可以添加额外的主题相关逻辑
        val isDarkTheme = settings.theme == ReaderTheme.DARK
        // 菜单背景色已经通过 ?attr/colorSurface 自动适配
    }
    
    private fun initViews() {
        // 基础组件
        webView = findViewById(R.id.fileReaderWebView)
        progressBar = findViewById(R.id.fileReaderProgressBar)
        errorTextView = findViewById(R.id.fileReaderError)
        
        // 顶部信息栏
        topInfoBar = findViewById(R.id.topInfoBar)
        bookTitle = findViewById(R.id.bookTitle)
        bookAuthor = findViewById(R.id.bookAuthor)
        chapterName = findViewById(R.id.chapterName)
        val btnExit = findViewById<ImageButton>(R.id.btnExit)
        btnExit?.setOnClickListener {
            finish()
        }
        
        // 底部导航栏
        bottomNavBar = findViewById(R.id.bottomNavBar)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        btnCatalog = findViewById(R.id.btnCatalog)
        btnBookmark = findViewById(R.id.btnBookmark)
        pageInfo = findViewById(R.id.pageInfo)
        // 功能菜单（精简版 + 统计）
        menuContainer = findViewById(R.id.menuContainer)
        functionMenu = findViewById(R.id.functionMenu)
        menuCatalog = findViewById(R.id.menuCatalog)
        menuStats = findViewById(R.id.menuStats)
        menuSettings = findViewById(R.id.menuSettings)
        menuTTS = findViewById(R.id.menuTTS)
        menuAutoRead = findViewById(R.id.menuAutoRead)
        ttsIcon = findViewById(R.id.ttsIcon)
        autoReadIcon = findViewById(R.id.autoReadIcon)
        ttsText = findViewById(R.id.ttsText)
        autoReadText = findViewById(R.id.autoReadText)
        
        // 阅读进度
        readingProgressBar = findViewById<SeekBar>(R.id.readingProgressBar)
        readingProgressText = findViewById<TextView>(R.id.readingProgressText)
        readingProgressPercent = findViewById<TextView>(R.id.readingProgressPercent)
        
        // 配置WebView（优化滚动性能）
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
            // 优化滚动性能
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }
        }
        
        // 禁用系统默认的文本选择菜单（黑色菜单）
        // 注意：不设置 setOnLongClickListener，让 WebView 可以处理长按选中文字
        
        // 重写startActionMode以禁用ActionMode（系统文本选择菜单）
        // 使用反射调用 setCustomSelectionActionModeCallback（兼容不同Android版本）
        try {
            val callback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                    // 返回false，不创建ActionMode，从而禁用系统菜单
                    return false
                }
                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
            }
            // 使用反射调用方法（兼容性更好）
            val method = webView.javaClass.getMethod("setCustomSelectionActionModeCallback", android.view.ActionMode.Callback::class.java)
            method.invoke(webView, callback)
        } catch (e: Exception) {
            // 如果方法不存在或调用失败，记录警告但不影响功能
            Log.w(TAG, "无法禁用系统文本选择菜单（可能不支持此功能）", e)
        }
        
        // 启用硬件加速（优化滚动性能）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(WebAppInterface(), "Android")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                Log.d(TAG, "WebView页面加载完成: $url")
                
                // 检查页面内容
                view?.evaluateJavascript("document.body.innerText.length") { result ->
                    Log.d(TAG, "页面文本长度: $result")
                }
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                progressBar.visibility = View.GONE
                showError("加载失败: $description")
                Log.e(TAG, "WebView页面加载错误: $description, URL: $failingUrl, errorCode=$errorCode")
            }
            
            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "WebView HTTP错误: ${errorResponse?.statusCode}, ${errorResponse?.reasonPhrase}")
            }
        }
        
        // 设置手势识别（滑动翻页，点击显示菜单）
        setupGestureDetector()
        
        // 初始隐藏UI
        hideAllUI()
    }
    
    /**
     * 设置手势识别器
     */
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                touchStartX = e.x
                touchStartY = e.y
                return true // 返回true表示处理了事件，但允许其他监听器继续处理
            }
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                Log.d(TAG, "onSingleTapUp 被调用: x=${e.x}, y=${e.y}")
                
                // 如果文本选择对话框正在显示，不处理单击
                if (textSelectionDialog?.isShowing == true) {
                    Log.d(TAG, "文本选择对话框正在显示，忽略单击")
                    return false
                }
                
                // 获取屏幕宽度
                val screenWidth = resources.displayMetrics.widthPixels
                val clickX = e.x
                
                // 将屏幕分为三个区域：左30%、中40%、右30%
                when {
                    clickX < screenWidth * 0.3f -> {
                        // 左侧区域：上一页
                        if (!isMenuVisible) {
                            goToPrevPage()
                        } else {
                            hideMenu()
                        }
                    }
                    clickX > screenWidth * 0.7f -> {
                        // 右侧区域：下一页
                        if (!isMenuVisible) {
                            goToNextPage()
                        } else {
                            hideMenu()
                        }
                    }
                    else -> {
                        // 中间区域：显示/隐藏菜单
                        if (!isMenuVisible) {
                            showMenu()
                        } else {
                            hideMenu()
                        }
                    }
                }
                return true
            }
            
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // 判断滑动方向
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // 水平滑动
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 右滑：上一页
                            goToPrevPage()
                        } else {
                            // 左滑：下一页
                            goToNextPage()
                        }
                        return true
                    }
                } else {
                    // 垂直滑动
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            // 下滑：上一页
                            goToPrevPage()
                        } else {
                            // 上滑：下一页
                            goToNextPage()
                        }
                        return true
                    }
                }
                
                return false
            }
        })
        
        // 用于跟踪触摸事件，区分单击、长按和滑动
        var touchDownTime = 0L
        var touchDownX = 0f
        var touchDownY = 0f
        var isLongPress = false
        var isScrolling = false
        var hasHandledLongPress = false
        val longPressThreshold = 500L // 长按阈值：500毫秒
        val touchMoveThreshold = 30f // 移动阈值：30像素
        
        // 使用Handler延迟检测长按
        val longPressHandler = Handler(Looper.getMainLooper())
        var longPressRunnable: Runnable? = null
        
        webView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = System.currentTimeMillis()
                    touchDownX = event.x
                    touchDownY = event.y
                    isLongPress = false
                    isScrolling = false
                    hasHandledLongPress = false
                    
                    // 如果文本选择对话框已显示，先关闭它
                    if (textSelectionDialog?.isShowing == true) {
                        hideTextSelectionDialog()
                        // 清除WebView中的文本选择
                        webView.evaluateJavascript("window.getSelection().removeAllRanges();", null)
                        return@setOnTouchListener true // 拦截事件，不继续处理
                    }
                    
                    // 先让 GestureDetector 处理 DOWN 事件（必须，否则无法识别手势）
                    gestureDetector?.onTouchEvent(event)
                    
                    // 延迟检测长按
                    longPressRunnable = Runnable {
                        // 如果还在按下状态且没有移动，认为是长按
                        if (!isScrolling && !hasHandledLongPress) {
                            isLongPress = true
                            hasHandledLongPress = true
                            // 长按时，让WebView处理（返回false），这样WebView可以选中文字
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, longPressThreshold)
                    
                    // 返回false，让WebView也能接收事件（用于长按选中文字）
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算移动距离
                    val moveDistance = Math.sqrt(
                        Math.pow((event.x - touchDownX).toDouble(), 2.0) +
                        Math.pow((event.y - touchDownY).toDouble(), 2.0)
                    ).toFloat()
                    
                    if (moveDistance > touchMoveThreshold) {
                        // 移动距离大，是滑动
                        isScrolling = true
                        // 取消长按检测
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        // 让 GestureDetector 处理滑动
                        gestureDetector?.onTouchEvent(event) ?: false
                    } else {
                        // 移动距离小，可能是长按前的微动，让WebView处理
                        gestureDetector?.onTouchEvent(event) // 也让 GestureDetector 知道移动
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // 取消长按检测
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    
                    val touchDuration = System.currentTimeMillis() - touchDownTime
                    val moveDistance = Math.sqrt(
                        Math.pow((event.x - touchDownX).toDouble(), 2.0) +
                        Math.pow((event.y - touchDownY).toDouble(), 2.0)
                    ).toFloat()
                    
                    // 如果已经处理了长按，让WebView处理（可能正在选中文字）
                    if (hasHandledLongPress) {
                        false
                    } else if (moveDistance > touchMoveThreshold) {
                        // 移动距离大，是滑动，让WebView处理
                        false
                    } else if (touchDuration < longPressThreshold && moveDistance < touchMoveThreshold) {
                        // 短时间点击且移动距离小，是单击
                        // 让 GestureDetector 处理单击（显示/隐藏菜单、翻页等）
                        gestureDetector?.onTouchEvent(event) ?: false
                    } else {
                        // 其他情况，让 GestureDetector 处理
                        gestureDetector?.onTouchEvent(event) ?: false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    gestureDetector?.onTouchEvent(event) ?: false
                }
                else -> {
                    gestureDetector?.onTouchEvent(event) ?: false
                }
            }
        }
    }
    
    /**
     * 设置点击监听器
     */
    private fun setupClickListeners() {
        // 底部导航栏按钮
        btnPrevPage.setOnClickListener { goToPrevPage() }
        btnNextPage.setOnClickListener { goToNextPage() }
        btnCatalog.setOnClickListener { showCatalogDialog() }
        btnBookmark.setOnClickListener { toggleBookmark() }
        
        // 功能菜单按钮（精简版 + 统计）
        menuCatalog.setOnClickListener { 
            hideMenu()
            showCatalogDialog()
        }
        menuStats.setOnClickListener {
            hideMenu()
            showStatsDialog()
        }
        menuSettings.setOnClickListener { 
            hideMenu()
            showSettingsDialog()
        }
        menuTTS.setOnClickListener { 
            hideMenu()
            toggleTTS()
        }
        menuAutoRead.setOnClickListener { 
            hideMenu()
            toggleAutoRead()
        }
        
        // 进度条拖动监听
        readingProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    readingProgressPercent.text = "$progress%"
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val targetPage = (it.progress * totalPages / 100).coerceIn(0, totalPages - 1)
                    displayPage(targetPage)
                    updatePageInfo()
                }
            }
        })
    }
    
    /**
     * JavaScript接口，用于WebView和Android交互
     */
    inner class WebAppInterface {
        @JavascriptInterface
        fun onTextSelected(selectedText: String, startOffset: Int, endOffset: Int) {
            runOnUiThread {
                Log.d(TAG, "onTextSelected 被调用: 文本长度=${selectedText.length}, start=$startOffset, end=$endOffset")
                if (selectedText.isNotBlank()) {
                    showTextSelectionDialog(selectedText, startOffset, endOffset)
                }
            }
        }
        
        @JavascriptInterface
        fun onTextSelectedSimple(selectedText: String) {
            runOnUiThread {
                Log.d(TAG, "onTextSelectedSimple 被调用: 文本长度=${selectedText.length}")
                // 简化版本，用于兼容
                if (selectedText.isNotBlank()) {
                    showTextSelectionDialog(selectedText, 0, selectedText.length)
                }
            }
        }
        
        @JavascriptInterface
        fun onSelectionCleared() {
            runOnUiThread {
                Log.d(TAG, "onSelectionCleared 被调用")
                hideTextSelectionDialog()
            }
        }
    }
    
    private fun loadFile() {
        val fileUriStr = intent.getStringExtra(EXTRA_FILE_URI)
        val filePathStr = intent.getStringExtra(EXTRA_FILE_PATH)
        val fileNameStr = intent.getStringExtra(EXTRA_FILE_NAME)
        
        scope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                errorTextView.visibility = View.GONE
                
                val uri = when {
                    !filePathStr.isNullOrEmpty() -> {
                        filePath = filePathStr
                        Uri.fromFile(File(filePathStr))
                    }
                    !fileUriStr.isNullOrEmpty() -> {
                        val parsedUri = Uri.parse(fileUriStr)
                        // 尝试从URI获取文件路径
                        if (parsedUri.scheme == "file") {
                            filePath = parsedUri.path ?: fileUriStr
                        } else {
                            // 使用URI作为唯一标识
                            filePath = fileUriStr
                        }
                        parsedUri
                    }
                    else -> {
                        showError("未提供文件路径或URI")
                        return@launch
                    }
                }
                
                fileUri = uri
                fileName = fileNameStr ?: getFileNameFromUri(uri)
                
                // 更新顶部信息
                bookTitle.text = fileName
                bookAuthor.text = "未知作者"
                
                Log.d(TAG, "开始加载文件: $uri, 文件名: $fileName")
                
                // 根据文件扩展名选择加载方式
                val extension = getFileExtension(fileName).lowercase()
                when (extension) {
                    "txt" -> loadTextFile(uri)
                    "pdf" -> loadPdfFile(uri)
                    "epub", "mobi", "azw", "azw3", "azw4", "prc", "pdb" -> {
                        showError("电子书格式($extension)需要专门的阅读器，建议使用外部应用打开")
                    }
                    else -> {
                        showError("不支持的文件格式: $extension")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "加载文件异常", e)
                showError("加载文件失败: ${e.message}")
            }
        }
    }
    
    /**
     * 加载文本文件
     */
    private suspend fun loadTextFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            // 确保filePath已设置（用于保存进度）
            if (filePath.isEmpty() && uri.scheme == "file") {
                filePath = uri.path ?: ""
            } else if (filePath.isEmpty()) {
                // 使用URI作为唯一标识
                filePath = uri.toString()
            }
            
            val inputStream: InputStream? = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) file.inputStream() else null
                }
                "content" -> {
                    contentResolver.openInputStream(uri)
                }
                else -> null
            }
            
            // 🔧 修复：先获取文件大小，用于优化大文件处理
            val fileSize = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) file.length() else 0L
                }
                "content" -> {
                    try {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                                cursor.getLong(sizeIndex)
                            } else 0L
                        } ?: 0L
                    } catch (e: Exception) {
                        Log.w(TAG, "无法获取文件大小", e)
                        0L
                    }
                }
                else -> 0L
            }
            
            Log.d(TAG, "文件大小: ${fileSize / 1024 / 1024}MB")
            
            inputStream?.use { stream ->
                // 🔧 修复:检测文件编码并流式读取大文件
                val charset = if (fileSize > 16384) {
                    // 大文件：读取前16KB检测编码，然后流式读取
                    val sampleBytes = ByteArray(16384)
                    val bytesRead = stream.read(sampleBytes)
                    val actualSample = if (bytesRead < sampleBytes.size) {
                        sampleBytes.sliceArray(0 until bytesRead)
                    } else {
                        sampleBytes
                    }
                    val detectedCharset = detectCharset(actualSample)
                    // 重新打开流（因为已经读取了前16KB）
                    stream.close()
                    val newStream = when (uri.scheme) {
                        "file" -> File(uri.path ?: "").inputStream()
                        "content" -> contentResolver.openInputStream(uri)
                        else -> null
                    } ?: throw Exception("无法重新打开文件流")
                    
                    // 🚀 优化：使用检测到的编码流式读取，带进度反馈
                    fullText = readTextFileStreaming(
                        newStream, 
                        detectedCharset, 
                        maxSize = 50 * 1024 * 1024,
                        totalSize = fileSize
                    ) { current, total ->
                        // 在主线程更新进度
                        scope.launch(Dispatchers.Main) {
                            val progress = if (total > 0) (current * 100 / total).toInt() else 0
                            errorTextView.text = "正在加载文件... ${current / 1024 / 1024}MB / ${total / 1024 / 1024}MB ($progress%)"
                        }
                    }
                    detectedCharset
                } else {
                    // 小文件：直接读取全部内容检测编码
                    val allBytes = stream.readBytes()
                    val detectedCharset = detectCharset(allBytes)
                    fullText = String(allBytes, detectedCharset)
                    detectedCharset
                }
                
                Log.d(TAG, "读取文件内容: 长度=${fullText.length}, 编码=${charset.name()}, 前100字符=${fullText.take(100).replace("\n", "\\n")}")
                
                if (fullText.isBlank()) {
                    Log.w(TAG, "文件内容为空")
                    withContext(Dispatchers.Main) {
                        showError("文件内容为空")
                    }
                    return@withContext
                }
                
                // 🚀 优化：大文件异步分页，提升加载速度
                val textLength = fullText.length
                Log.d(TAG, "文件内容加载成功，长度=${textLength}")
                
                // 🎯 智能识别作者
                val detectedAuthor = extractAuthor(fullText)
                
                withContext(Dispatchers.Main) {
                    // 更新作者信息
                    if (detectedAuthor.isNotEmpty()) {
                        bookAuthor.text = detectedAuthor
                        Log.d(TAG, "识别到作者: $detectedAuthor")
                    } else {
                        bookAuthor.text = "未知作者"
                    }
                    
                    // 显示加载提示
                    progressBar.visibility = View.VISIBLE
                    errorTextView.text = "正在处理文件..."
                    errorTextView.visibility = View.VISIBLE
                }
                
                // 🚀 优化1：简化章节解析 - 只解析前100KB，避免大文件卡顿
                val chapterSampleSize = minOf(textLength, 100 * 1024)
                val chapterSample = fullText.substring(0, chapterSampleSize)
                parseChapters(chapterSample)
                
                // 🚀 优化2：异步分页 - 在后台线程执行
                val startTime = System.currentTimeMillis()
                pages = paginateText(fullText)
                totalPages = pages.size
                val paginationTime = System.currentTimeMillis() - startTime
                
                Log.d(TAG, "分页完成: 共 $totalPages 页，耗时 ${paginationTime}ms")
                
                withContext(Dispatchers.Main) {
                    if (totalPages == 0) {
                        Log.e(TAG, "分页失败，页面数为0")
                        showError("无法分页，文件可能为空")
                        progressBar.visibility = View.GONE
                        return@withContext
                    }
                    
                    // 加载阅读进度（必须在分页之后）
                    loadReadingProgress()
                    
                    // 如果Intent中指定了页面索引，优先使用（用于从读书划线跳转）
                    val intentPageIndex = intent.getIntExtra(EXTRA_PAGE_INDEX, -1)
                    if (intentPageIndex >= 0) {
                        currentPageIndex = intentPageIndex.coerceIn(0, totalPages - 1)
                        Log.d(TAG, "使用Intent指定的页面索引: $intentPageIndex")
                    }
                    
                    Log.d(TAG, "准备显示页面: currentPageIndex=$currentPageIndex, totalPages=$totalPages")
                    
                    // 显示当前页（可能是上次阅读的位置或Intent指定的位置）
                    val targetPage = currentPageIndex.coerceIn(0, totalPages - 1)
                    displayPage(targetPage)
                    
                    // 更新UI
                    updatePageInfo()
                    updateBookmarkButton()
                    
                    // 隐藏进度条，显示WebView
                    progressBar.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                    errorTextView.visibility = View.GONE
                    
                    Log.d(TAG, "文本文件加载成功，共 ${totalPages} 页，当前页=${targetPage + 1}，filePath=$filePath")
                    
                    // 自动收藏到AI助手的电子书收藏
                    addToEbookCollection()
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    showError("无法读取文件")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载文本文件失败", e)
            withContext(Dispatchers.Main) {
                showError("加载文本文件失败: ${e.message}")
            }
        }
    }
    
    /**
     * 分页文本
     */
    private fun paginateText(text: String): List<String> {
        val pages = mutableListOf<String>()
        val charsPerPage = calculateCharsPerPage()
        
        Log.d(TAG, "开始分页: 文本长度=${text.length}, 每页字符数=$charsPerPage")
        
        if (charsPerPage <= 0) {
            Log.e(TAG, "每页字符数无效: $charsPerPage，使用默认值1000")
            // 如果计算失败，使用默认值
            val defaultCharsPerPage = 1000
            var currentIndex = 0
            while (currentIndex < text.length) {
                val endIndex = minOf(currentIndex + defaultCharsPerPage, text.length)
                val pageText = text.substring(currentIndex, endIndex)
                pages.add(pageText)
                currentIndex = endIndex
            }
        } else {
            var currentIndex = 0
            while (currentIndex < text.length) {
                val endIndex = minOf(currentIndex + charsPerPage, text.length)
                val pageText = text.substring(currentIndex, endIndex)
                pages.add(pageText)
                currentIndex = endIndex
            }
        }
        
        Log.d(TAG, "分页完成: 共 ${pages.size} 页")
        return pages
    }
    
    /**
     * 计算每页字符数
     */
    private fun calculateCharsPerPage(): Int {
        // 根据屏幕大小和字体设置计算每页字符数
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 减去边距
        val marginHorizontalPx = (settings.marginHorizontal * displayMetrics.density).toInt()
        val marginVerticalPx = (settings.marginVertical * displayMetrics.density).toInt()
        val contentWidth = (screenWidth - marginHorizontalPx * 2).coerceAtLeast(100)
        val contentHeight = (screenHeight - marginVerticalPx * 2 - 200).coerceAtLeast(100) // 减去UI栏高度
        
        // 估算每页字符数（粗略计算）
        val fontSizePx = settings.fontSize * displayMetrics.scaledDensity
        val charsPerLine = (contentWidth / fontSizePx).coerceAtLeast(10f).toInt()
        val lineHeightPx = fontSizePx * settings.lineHeight
        val linesPerPage = (contentHeight / lineHeightPx).coerceAtLeast(5f).toInt()
        
        val charsPerPage = charsPerLine * linesPerPage
        Log.d(TAG, "计算每页字符数: 屏幕=${screenWidth}x${screenHeight}, 内容=${contentWidth}x${contentHeight}, 字体=${fontSizePx}px, 每行=$charsPerLine, 每页=$linesPerPage, 总计=$charsPerPage")
        
        // 确保至少返回一个合理的值
        return charsPerPage.coerceAtLeast(100)
    }
    
    /**
     * 显示指定页面
     */
    private fun displayPage(pageIndex: Int) {
        if (pages.isEmpty()) {
            Log.e(TAG, "页面列表为空，无法显示")
            showError("文件内容为空或加载失败")
            return
        }
        
        if (pageIndex < 0 || pageIndex >= pages.size) {
            Log.e(TAG, "页码超出范围: $pageIndex / ${pages.size}")
            return
        }
        
        currentPageIndex = pageIndex
        val pageText = pages[pageIndex]
        
        if (pageText.isBlank()) {
            Log.w(TAG, "当前页内容为空")
        }
        
        // 应用高亮
        val highlightedText = applyHighlights(pageText, pageIndex)
        
        // 生成HTML
        val htmlContent = generateHTML(highlightedText)
        
        Log.d(TAG, "显示页面 $pageIndex: 原始文本长度=${pageText.length}, 高亮后长度=${highlightedText.length}, HTML长度=${htmlContent.length}")
        
        if (htmlContent.isBlank()) {
            Log.e(TAG, "生成的HTML内容为空！")
            showError("页面内容生成失败")
            return
        }
        
        // 使用正确的MIME类型和编码
        try {
            webView.loadDataWithBaseURL(null, htmlContent, "text/html; charset=UTF-8", "UTF-8", null)
            Log.d(TAG, "WebView加载HTML成功，页面索引=$pageIndex")
        } catch (e: Exception) {
            Log.e(TAG, "WebView加载HTML失败", e)
            showError("显示页面失败: ${e.message}")
        }
        
        // 更新章节信息
        updateChapterInfo()
        
        // 保存阅读进度（延迟保存，避免频繁写入）
        handler.postDelayed({
            saveReadingProgress()
        }, 500)
    }
    
    /**
     * 生成HTML内容
     */
    private fun generateHTML(text: String): String {
        val (backgroundColor, textColor) = getThemeColors()
        
        // 根据设置选择字体家族
        val fontFamily = when (settings.fontFamily) {
            "serif" -> "Georgia, \"Times New Roman\", serif"
            "monospace" -> "\"Courier New\", Courier, monospace"
            else -> "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, \"Helvetica Neue\", Arial, sans-serif"
        }
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: $fontFamily;
                        font-size: ${settings.fontSize}px;
                        line-height: ${settings.lineHeight};
                        color: $textColor;
                        padding: ${settings.marginVertical}px ${settings.marginHorizontal}px;
                        margin: 0;
                        background-color: $backgroundColor;
                    }
                    div {
                        white-space: pre-wrap;
                        word-wrap: break-word;
                        line-height: ${settings.lineHeight};
                    }
                    .highlight {
                        padding: 2px 0;
                    }
                    /* 高亮样式 */
                    .highlight-highlight {
                        display: inline !important;
                        line-height: inherit;
                    }
                    /* 下划线样式 */
                    .highlight-underline {
                        display: inline !important;
                        text-decoration: none !important;
                        line-height: inherit;
                    }
                    /* 波浪下划线样式 - 使用SVG背景实现真实的波浪效果 */
                    .highlight-wavy_underline {
                        display: inline !important;
                        text-decoration: none !important;
                        line-height: inherit;
                    }
                    /* 确保所有高亮样式都能正确显示 */
                    span[class*="highlight-"] {
                        display: inline !important;
                        line-height: inherit;
                        vertical-align: baseline;
                    }
                </style>
            </head>
            <body>
                <div style="white-space: pre-wrap; word-wrap: break-word;">$text</div>
                <script>
                    // 禁用系统默认的文本选择菜单
                    document.addEventListener('contextmenu', function(e) {
                        e.preventDefault();
                        return false;
                    });
                    
                    // 禁用长按菜单
                    document.addEventListener('selectstart', function(e) {
                        // 允许选择，但阻止系统菜单
                    });
                    
                    // 文本选择监听 - 只在手指抬起后触发
                    var lastSelection = '';
                    var isSelecting = false;
                    
                    // 监听触摸开始，标记正在选择
                    document.addEventListener('touchstart', function() {
                        isSelecting = true;
                    }, { passive: true });
                    
                    document.addEventListener('mousedown', function() {
                        isSelecting = true;
                    });
                    
                    // 监听触摸/鼠标抬起，立即触发文本选择菜单
                    function handleSelectionEnd() {
                        isSelecting = false;
                        // 立即检查选择，不延迟
                        var selection = window.getSelection();
                        var selectedText = selection.toString();
                        if (selectedText.length > 0 && selectedText !== lastSelection) {
                            lastSelection = selectedText;
                            var range = selection.getRangeAt(0);
                            
                            // 计算选中文本在页面中的位置（基于纯文本，不包括HTML标签）
                            var startOffset = 0;
                            var endOffset = 0;
                            
                            // 获取div容器（包含文本内容）
                            var contentDiv = document.body.querySelector('div');
                            if (!contentDiv) {
                                console.error('找不到内容容器');
                                return;
                            }
                            
                            // 获取所有文本节点和BR标签（按顺序）
                            var walker = document.createTreeWalker(
                                contentDiv,
                                NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT,
                                {
                                    acceptNode: function(node) {
                                        if (node.nodeType === Node.TEXT_NODE) return NodeFilter.FILTER_ACCEPT;
                                        if (node.tagName === 'BR') return NodeFilter.FILTER_ACCEPT;
                                        return NodeFilter.FILTER_SKIP;
                                    }
                                },
                                false
                            );
                            
                            var node;
                            var currentOffset = 0;
                            var startNode = null;
                            var endNode = null;
                            
                            // 遍历所有节点，计算位置
                            while (node = walker.nextNode()) {
                                var nodeLength = 0;
                                if (node.nodeType === Node.TEXT_NODE) {
                                    nodeLength = node.textContent.length;
                                } else if (node.tagName === 'BR') {
                                    nodeLength = 1; // BR标签算作1个字符（换行符）
                                }
                                
                                // 检查起始位置
                                if (startNode == null) {
                                    // 情况1：Range起始于当前节点（文本节点）
                                    if (range.startContainer === node) {
                                        startNode = node;
                                        startOffset = currentOffset + range.startOffset;
                                    }
                                    // 情况2：Range起始于父节点，且偏移量指向当前节点
                                    else if (range.startContainer === node.parentNode) {
                                        var childIndex = Array.prototype.indexOf.call(node.parentNode.childNodes, node);
                                        if (childIndex === range.startOffset) {
                                            startNode = node;
                                            startOffset = currentOffset;
                                        }
                                    }
                                    
                                    // 如果还没找到，检查是否在当前节点范围内（仅文本节点）
                                    if (startNode == null && node.nodeType === Node.TEXT_NODE) {
                                        // 这是一个备用检查，通常上面的检查应该足够
                                        // 但为了保险，我们可以检查累加长度
                                        // 这里不做额外处理，依赖精确匹配
                                    }
                                }
                                
                                // 检查结束位置
                                if (endNode == null) {
                                    // 情况1：Range结束于当前节点（文本节点）
                                    if (range.endContainer === node) {
                                        endNode = node;
                                        endOffset = currentOffset + range.endOffset;
                                    }
                                    // 情况2：Range结束于父节点，且偏移量指向当前节点
                                    else if (range.endContainer === node.parentNode) {
                                        var childIndex = Array.prototype.indexOf.call(node.parentNode.childNodes, node);
                                        if (childIndex === range.endOffset) {
                                            endNode = node;
                                            endOffset = currentOffset;
                                        }
                                    }
                                }
                                
                                currentOffset += nodeLength;
                                
                                if (startNode != null && endNode != null) {
                                    break;
                                }
                            }
                            
                            // 如果没找到，使用备用方法：基于textContent计算位置
                            // 注意：textContent会排除HTML标签，所以位置计算基于纯文本
                            if (startOffset === 0 && endOffset === 0 || startNode == null || endNode == null) {
                                var divText = contentDiv.textContent || '';
                                var selectedText = range.toString();
                                
                                if (divText.length > 0 && selectedText.length > 0) {
                                    // 计算从div开始到range起始位置的文本长度
                                    var calculatedStartOffset = 0;
                                    try {
                                        var startRange = range.cloneRange();
                                        startRange.setStart(contentDiv, 0);
                                        startRange.setEnd(range.startContainer, range.startOffset);
                                        calculatedStartOffset = startRange.toString().length;
                                    } catch(e) {
                                        // 如果失败，尝试通过文本搜索
                                        var searchStart = 0;
                                        var foundIndex = -1;
                                        
                                        // 尝试从当前位置附近查找
                                        foundIndex = divText.indexOf(selectedText, searchStart);
                                        if (foundIndex < 0) {
                                            // 如果没找到，尝试从头查找
                                            foundIndex = divText.indexOf(selectedText);
                                        }
                                        
                                        if (foundIndex >= 0) {
                                            calculatedStartOffset = foundIndex;
                                        }
                                    }
                                    
                                    // 计算结束位置
                                    var calculatedEndOffset = calculatedStartOffset + selectedText.length;
                                    
                                    // 确保位置在有效范围内
                                    if (calculatedStartOffset >= 0 && calculatedEndOffset <= divText.length) {
                                        startOffset = calculatedStartOffset;
                                        endOffset = calculatedEndOffset;
                                    } else {
                                        // 如果计算的位置无效，使用文本搜索
                                        var foundIndex = divText.indexOf(selectedText);
                                        if (foundIndex >= 0) {
                                            startOffset = foundIndex;
                                            endOffset = foundIndex + selectedText.length;
                                        }
                                    }
                                }
                            }
                            
                            // 最终验证：确保位置有效
                            if (startOffset < 0) startOffset = 0;
                            if (endOffset <= startOffset) {
                                endOffset = startOffset + selectedText.length;
                            }
                            
                            // 立即调用Android接口，无延迟
                            if (typeof Android !== 'undefined' && Android.onTextSelected) {
                                Android.onTextSelected(selectedText, startOffset, endOffset);
                            } else if (typeof Android !== 'undefined' && Android.onTextSelectedSimple) {
                                Android.onTextSelectedSimple(selectedText);
                            }
                        }
                    }
                    
                    // 监听触摸结束
                    document.addEventListener('touchend', handleSelectionEnd, { passive: true });
                    // 监听鼠标抬起
                    document.addEventListener('mouseup', handleSelectionEnd);
                    
                    // 点击其他地方时清除选择
                    document.addEventListener('mousedown', function(e) {
                        // 延迟检查，避免与文本选择冲突
                        setTimeout(function() {
                            var selection = window.getSelection();
                            var selectedText = selection.toString();
                            // 如果点击时没有选中文本，清除选择并关闭菜单
                            if (selectedText.length === 0) {
                                selection.removeAllRanges();
                                if (typeof Android !== 'undefined' && Android.onSelectionCleared) {
                                    Android.onSelectionCleared();
                                }
                            }
                        }, 100);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * 获取主题颜色
     */
    private fun getThemeColors(): Pair<String, String> {
        return when (settings.theme) {
            ReaderTheme.LIGHT -> Pair("#FFFFFF", "#333333")
            ReaderTheme.DARK -> Pair("#1E1E1E", "#E0E0E0")
            ReaderTheme.SEPIA -> Pair("#F4ECD8", "#5C4B37")
            ReaderTheme.GREEN -> Pair("#C7EDCC", "#2D5016")
        }
    }
    
    /**
     * 构建样式字符串
     */
    private fun buildStyleString(color: String, style: HighlightStyle): String {
        return when (style) {
            HighlightStyle.HIGHLIGHT -> {
                // 高亮/填充样式：使用背景色
                "background-color: $color; padding: 2px 0; border-radius: 2px; display: inline !important;"
            }
            HighlightStyle.UNDERLINE -> {
                // 下划线样式：使用边框
                "border-bottom: 2px solid $color; padding-bottom: 1px; display: inline !important; text-decoration: none !important;"
            }
            HighlightStyle.WAVY_UNDERLINE -> {
                // 波浪下划线样式：使用SVG背景实现真实的波浪效果
                val svgColor = color.replace("#", "%23") // URL编码
                // 使用更明显的波浪效果，确保颜色正确
                val svgData = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 20 4'%3E%3Cpath d='M0,2 Q5,0 10,2 T20,2' stroke='$svgColor' fill='none' stroke-width='2'/%3E%3C/svg%3E"
                "background-image: url(\"$svgData\"); background-repeat: repeat-x; background-size: 20px 4px; background-position: bottom; padding-bottom: 4px; display: inline !important; text-decoration: none !important;"
            }
        }
    }
    
    /**
     * 应用高亮（支持不同颜色和样式）
     * 注意：位置索引基于原始文本，需要在转义前计算
     * 重要：只转义文本内容，不转义HTML标签
     */
    /**
     * 应用高亮（支持不同颜色和样式，支持重叠划线混合显示）
     * 注意：位置索引基于原始文本，需要在转义前计算
     * 重要：只转义文本内容，不转义HTML标签
     * 
     * 算法说明：
     * 1. 将所有划线位置转换为区间
     * 2. 找出所有需要高亮的区间段（使用区间分割算法）
     * 3. 对于每个区间段，找出所有覆盖它的划线
     * 4. 使用嵌套span来显示多个划线样式
     */
    private fun applyHighlights(text: String, pageIndex: Int): String {
        val highlights = dataManager.getHighlights(filePath)
            .filter { it.pageIndex == pageIndex }
            .filter { 
                // 过滤掉无效的划线
                it.startPosition >= 0 && 
                it.startPosition < text.length && 
                it.endPosition > it.startPosition &&
                it.endPosition <= text.length
            }
        
        if (highlights.isEmpty()) {
            // 没有高亮，直接转义并返回
            return escapeHtml(text).replace("\n", "<br>")
        }
        
        // 找出所有需要高亮的区间段
        val segments = mutableListOf<Pair<Int, Int>>() // (start, end)
        val highlightMap = mutableMapOf<Pair<Int, Int>, MutableList<Highlight>>() // 每个区间段对应的划线列表
        
        // 收集所有区间的端点
        val points = mutableSetOf<Int>()
        highlights.forEach { highlight ->
            points.add(highlight.startPosition)
            points.add(highlight.endPosition)
        }
        points.add(0)
        points.add(text.length)
        
        // 排序端点
        val sortedPoints = points.sorted()
        
        // 为每个区间段找出覆盖它的划线
        for (i in 0 until sortedPoints.size - 1) {
            val segmentStart = sortedPoints[i]
            val segmentEnd = sortedPoints[i + 1]
            
            if (segmentStart < segmentEnd) {
                // 找出所有覆盖这个区间段的划线
                val coveringHighlights = highlights.filter { highlight ->
                    highlight.startPosition <= segmentStart && highlight.endPosition >= segmentEnd
                }
                
                if (coveringHighlights.isNotEmpty()) {
                    val segment = Pair(segmentStart, segmentEnd)
                    segments.add(segment)
                    highlightMap[segment] = coveringHighlights.toMutableList()
                }
            }
        }
        
        // 构建HTML
        val result = StringBuilder()
        var lastIndex = 0
        
        // 处理所有区间段
        segments.forEach { segment ->
            val segmentStart = segment.first
            val segmentEnd = segment.second
            val segmentHighlights = highlightMap[segment] ?: emptyList()
            
            // 添加区间段之前的文本（转义）
            if (segmentStart > lastIndex) {
                result.append(escapeHtml(text.substring(lastIndex, segmentStart)))
            }
            
            // 获取区间段的文本（转义）
            val segmentText = escapeHtml(text.substring(segmentStart, segmentEnd))
            
            // 使用嵌套span来显示多个划线样式
            // 从外到内嵌套，确保所有样式都能显示
            var nestedText = segmentText
            segmentHighlights.forEach { highlight ->
                val color = highlight.color.takeIf { it.isNotEmpty() } ?: "#FFEB3B"
                val style = highlight.style
                val styleString = buildStyleString(color, style)
                nestedText = "<span style=\"$styleString\" class=\"highlight-${style.name.lowercase()}\">$nestedText</span>"
            }
            
            result.append(nestedText)
            lastIndex = segmentEnd
        }
        
        // 添加剩余的文本（转义）
        if (lastIndex < text.length) {
            result.append(escapeHtml(text.substring(lastIndex)))
        }
        
        return result.toString().replace("\n", "<br>")
    }
    
    /**
     * 加载PDF文件
     */
    private fun loadPdfFile(uri: Uri) {
        try {
            // 对于PDF文件，使用Google Docs Viewer或直接加载
            // 注意：Android WebView不支持直接显示PDF，需要使用外部服务或PDF库
            val pdfUrl = when (uri.scheme) {
                "file" -> {
                    // 将本地文件转换为可访问的URI
                    val file = File(uri.path ?: "")
                    if (file.exists()) {
                        // 使用FileProvider提供访问
                        try {
                            FileProvider.getUriForFile(
                                this,
                                "${packageName}.fileprovider",
                                file
                            ).toString()
                        } catch (e: Exception) {
                            // 如果FileProvider不可用，使用Google Docs Viewer
                            "https://docs.google.com/viewer?url=${Uri.fromFile(file)}&embedded=true"
                        }
                    } else {
                        null
                    }
                }
                "content" -> {
                    // Content URI，尝试使用Google Docs Viewer
                    "https://docs.google.com/viewer?url=$uri&embedded=true"
                }
                "http", "https" -> {
                    // 网络URL，直接使用Google Docs Viewer
                    "https://docs.google.com/viewer?url=$uri&embedded=true"
                }
                else -> null
            }
            
            if (pdfUrl != null) {
                webView.loadUrl(pdfUrl)
                Log.d(TAG, "PDF文件加载URL: $pdfUrl")
            } else {
                showError("无法加载PDF文件")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载PDF文件失败", e)
            showError("加载PDF文件失败: ${e.message}")
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1)
        } else {
            ""
        }
    }
    
    /**
     * 从URI获取文件名
     */
    private fun getFileNameFromUri(uri: Uri): String {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: ""
                File(path).name
            }
            "content" -> {
                // 尝试从ContentResolver获取文件名
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        uri.lastPathSegment ?: "未知文件"
                    }
                } ?: (uri.lastPathSegment ?: "未知文件")
            }
            else -> uri.lastPathSegment ?: "未知文件"
        }
    }
    
    /**
     * HTML转义
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        errorTextView.text = message
        errorTextView.visibility = View.VISIBLE
        webView.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    // ==================== UI控制 ====================
    
    /**
     * 切换UI显示/隐藏
     */
    private fun toggleUI() {
        if (isMenuVisible) {
            hideMenu()
        } else if (isTopBarVisible || isBottomBarVisible) {
            hideAllUI()
        } else {
            // 点击屏幕中间，显示功能菜单
            showMenu()
        }
    }
    
    /**
     * 显示所有UI
     */
    private fun showAllUI() {
        topInfoBar.visibility = View.VISIBLE
        bottomNavBar.visibility = View.VISIBLE
        isTopBarVisible = true
        isBottomBarVisible = true
    }
    
    /**
     * 隐藏所有UI
     */
    /**
     * 隐藏所有UI
     */
    private fun hideAllUI() {
        topInfoBar.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        functionMenu.visibility = View.GONE
        menuContainer.visibility = View.GONE
        isTopBarVisible = false
        isBottomBarVisible = false
        isMenuVisible = false
    }
    
    /**
     * 显示功能菜单（带动画）
     */
    private fun showMenu() {
        if (isMenuVisible) return
        
        isMenuVisible = true
        isTopBarVisible = true
        
        // 显示顶部信息栏（在顶部）
        // 确保顶部信息栏在最上层
        topInfoBar.bringToFront()
        topInfoBar.visibility = View.VISIBLE
        topInfoBar.alpha = 0f
        topInfoBar.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        // 确保顶部信息栏内容已更新
        updateChapterInfo()
        
        // 从底部滑入工具菜单
        menuContainer.visibility = View.VISIBLE
        functionMenu.visibility = View.VISIBLE
        
        // 确保菜单容器在底部，然后从下方滑入
        menuContainer.post {
            // 先确保菜单容器在底部位置
            val layoutParams = menuContainer.layoutParams as? CoordinatorLayout.LayoutParams
            layoutParams?.gravity = android.view.Gravity.BOTTOM
            menuContainer.layoutParams = layoutParams
            
            // 获取菜单高度
            val menuHeight = menuContainer.height
            if (menuHeight == 0) {
                // 如果高度为0，先测量
                menuContainer.measure(
                    View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
            }
            val finalHeight = menuContainer.measuredHeight.takeIf { it > 0 } ?: menuContainer.height
            
            // 设置初始位置：在屏幕底部下方（向下偏移菜单高度）
            menuContainer.translationY = finalHeight.toFloat()
            menuContainer.alpha = 0f
            
            // 滑入动画：移动到屏幕底部（translationY = 0）
            menuContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }
    
    /**
     * 隐藏功能菜单（带动画）
     */
    private fun hideMenu() {
        if (!isMenuVisible) return
        
        isMenuVisible = false
        isTopBarVisible = false
        
        // 隐藏顶部信息栏
        topInfoBar.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                topInfoBar.visibility = View.GONE
            }
            .start()
        
        // 向底部滑出工具菜单
        menuContainer.post {
            val menuHeight = menuContainer.height
            menuContainer.animate()
                .alpha(0f)
                .translationY(if (menuHeight > 0) menuHeight.toFloat() else 200f)
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    menuContainer.visibility = View.GONE
                    functionMenu.visibility = View.GONE
                    menuContainer.translationY = 0f
                }
                .start()
        }
    }
    
    // ==================== 翻页功能 ====================
    
    /**
     * 上一页
     */
    private fun goToPrevPage() {
        if (currentPageIndex > 0) {
            displayPage(currentPageIndex - 1)
            updatePageInfo()
            updateBookmarkButton()
        } else {
            Toast.makeText(this, "已经是第一页", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 下一页
     */
    private fun goToNextPage() {
        if (currentPageIndex < totalPages - 1) {
            displayPage(currentPageIndex + 1)
            updatePageInfo()
            updateBookmarkButton()
        } else {
            Toast.makeText(this, "已经是最后一页", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 更新页码信息
     */
    private fun updatePageInfo() {
        pageInfo.text = "${currentPageIndex + 1}/$totalPages"
        updateReadingProgress()  // 同时更新进度条
    }
    
    // ==================== 章节功能 ====================
    
    /**
     * 解析章节（优化版：支持传入样本文本，避免大文件全文解析）
     */
    private fun parseChapters(sampleText: String = fullText) {
        chapters = mutableListOf<Chapter>().apply {
            // 简单的章节解析：查找"第X章"、"Chapter X"等模式
            val chapterPattern = Regex("(第[\\d一二三四五六七八九十百千万]+章|Chapter\\s+\\d+|第\\d+节)")
            var chapterIndex = 0
            
            chapterPattern.findAll(sampleText).forEach { matchResult ->
                val position = matchResult.range.first
                val title = matchResult.value
                add(Chapter(
                    id = "${filePath}_chapter_${chapterIndex++}",
                    title = title,
                    pageIndex = 0, // 需要计算
                    position = position
                ))
            }
            
            // 如果没有找到章节，创建一个默认章节
            if (isEmpty()) {
                add(Chapter(
                    id = "${filePath}_chapter_0",
                    title = "正文",
                    pageIndex = 0,
                    position = 0
                ))
            }
        }
        
        // 保存章节
        dataManager.saveChapters(filePath, chapters)
    }
    
    /**
     * 更新章节信息
     */
    private fun updateChapterInfo() {
        // 找到当前页所属的章节
        val currentPosition = currentPageIndex * calculateCharsPerPage()
        val chapter = chapters.findLast { it.position <= currentPosition }
        if (chapter != null) {
            chapterName.text = chapter.title
            currentChapterIndex = chapters.indexOf(chapter)
        }
    }
    
    /**
     * 显示目录对话框
     */
    private fun showCatalogDialog() {
        if (chapters.isEmpty()) {
            Toast.makeText(this, "未找到章节", Toast.LENGTH_SHORT).show()
            return
        }
        
        val chapterTitles = chapters.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("目录")
            .setItems(chapterTitles) { _, which ->
                val chapter = chapters[which]
                // 计算章节对应的页码
                val targetPage = (chapter.position / calculateCharsPerPage()).coerceIn(0, totalPages - 1)
                displayPage(targetPage)
                updatePageInfo()
                updateBookmarkButton()
                hideAllUI()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示书签管理界面
     */
    private fun showBookmarkManager() {
        val intent = Intent(this, BookmarkManagerActivity::class.java).apply {
            putExtra(BookmarkManagerActivity.EXTRA_FILE_PATH, filePath)
        }
        startActivityForResult(intent, BookmarkManagerActivity.RESULT_BOOKMARK_SELECTED)
    }
    
    // ==================== 书签功能 ====================
    
    /**
     * 切换书签
     */
    private fun toggleBookmark() {
        val hasBookmark = dataManager.hasBookmark(filePath, currentPageIndex, 0)
        if (hasBookmark) {
            // 删除书签
            val bookmarks = dataManager.getBookmarks(filePath)
            val bookmark = bookmarks.find { it.pageIndex == currentPageIndex }
            bookmark?.let {
                dataManager.deleteBookmark(it.id)
                Toast.makeText(this, "已删除书签", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 添加书签
            val bookmark = Bookmark(
                id = UUID.randomUUID().toString(),
                filePath = filePath,
                pageIndex = currentPageIndex,
                position = 0,
                text = pages[currentPageIndex].take(50)
            )
            dataManager.addBookmark(bookmark)
            Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show()
        }
        updateBookmarkButton()
        
        // 收藏到AI助手的电子书收藏
        addToEbookCollection()
    }
    
    /**
     * 收藏到AI助手的电子书收藏
     */
    private fun addToEbookCollection() {
        try {
            val collectionManager = UnifiedCollectionManager.getInstance(this)
            
            // 检查是否已收藏
            val existingCollection = collectionManager.getAllCollections()
                .find { 
                    it.collectionType == CollectionType.EBOOK_BOOKMARK && 
                    it.extraData?.get("filePath") == filePath 
                }
            
            // 获取阅读进度
            val progress = dataManager.getProgress(filePath)
            val currentPage = progress?.currentPage ?: currentPageIndex
            val totalPages = progress?.totalPages ?: totalPages
            val progressPercent = if (totalPages > 0) {
                (currentPage * 100 / totalPages).coerceIn(0, 100)
            } else {
                0
            }
            
            // 构建标签列表
            val tags = mutableListOf<String>().apply {
                add("阅读器")
                if (fileName.endsWith(".txt", ignoreCase = true)) add("文本文件")
                if (fileName.endsWith(".pdf", ignoreCase = true)) add("PDF文件")
                if (fileName.endsWith(".epub", ignoreCase = true)) add("EPUB文件")
                if (fileName.endsWith(".mobi", ignoreCase = true)) add("MOBI文件")
            }
            
            // 创建统一收藏项
            val collectionItem = UnifiedCollectionItem(
                id = filePath.hashCode().toString(), // 使用文件路径的hash作为ID，确保唯一性
                title = fileName.ifEmpty { "未命名文档" },
                content = filePath,
                preview = "阅读进度: 第${currentPage + 1}页/共${totalPages}页 (${progressPercent}%)",
                collectionType = CollectionType.EBOOK_BOOKMARK,
                sourceLocation = "阅读器",
                sourceDetail = "文件阅读器",
                collectedTime = System.currentTimeMillis(),
                customTags = tags.distinct(),
                extraData = mapOf(
                    "filePath" to filePath,
                    "fileName" to fileName,
                    "fileUri" to (fileUri?.toString() ?: ""),
                    "currentPage" to currentPage.toString(),
                    "totalPages" to totalPages.toString(),
                    "progressPercent" to progressPercent.toString(),
                    "lastReadTime" to (progress?.lastReadTime?.toString() ?: System.currentTimeMillis().toString())
                )
            )
            
            if (existingCollection != null) {
                // 更新现有收藏（更新阅读进度）
                collectionManager.updateCollection(collectionItem)
                Log.d(TAG, "更新电子书收藏: $fileName, 进度: $progressPercent%")
            } else {
                // 添加新收藏
                collectionManager.addCollection(collectionItem)
                Toast.makeText(this, "已收藏到AI助手", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "添加电子书收藏: $fileName, 进度: $progressPercent%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "收藏到电子书收藏失败", e)
        }
    }
    
    /**
     * 更新书签按钮状态
     */
    private fun updateBookmarkButton() {
        val hasBookmark = dataManager.hasBookmark(filePath, currentPageIndex, 0)
        btnBookmark.setImageResource(
            if (hasBookmark) R.drawable.ic_bookmark_filled
            else R.drawable.ic_bookmark_border
        )
    }
    
    // ==================== 划线/笔记功能 ====================
    
    /**
 * 显示文本选择对话框（微信读书风格菜单）
 */
private fun showTextSelectionDialog(selectedText: String, startOffset: Int, endOffset: Int) {
    if (selectedText.isBlank()) {
        return
    }
    
    // 检测暗色/亮色模式
    val nightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    val isDarkMode = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    
    // 使用微信读书风格菜单布局
    val dialogView = layoutInflater.inflate(R.layout.menu_text_selection_wechat_style, null)
    
    // 应用主题颜色
    applyMenuTheme(dialogView, isDarkMode)
    
    // 检查是否已有划线（检查重叠，不仅仅是精确匹配）
    val existingHighlight = dataManager.getHighlights(filePath)
        .find { it.pageIndex == currentPageIndex && 
                // 检查是否有重叠：选中的文本与已有划线有交集
                ((it.startPosition <= startOffset && it.endPosition > startOffset) ||  // 划线包含选中的起始位置
                 (it.startPosition < endOffset && it.endPosition >= endOffset) ||      // 划线包含选中的结束位置
                 (startOffset <= it.startPosition && endOffset >= it.endPosition))     // 选中的文本包含整个划线
        }
    
    // 格式选项（高亮、下划线、波浪下划线）
    // 如果已有划线，使用已有的样式和颜色；否则使用默认值
    var selectedStyle = existingHighlight?.style ?: HighlightStyle.HIGHLIGHT
    var selectedColor = existingHighlight?.color ?: "#FFD60A"
    var previewHighlightId: String? = null // 用于临时预览的划线ID
    
    // 格式选项按钮
    val formatHighlightView = dialogView.findViewById<android.widget.FrameLayout>(R.id.format_highlight)
    val formatUnderlineView = dialogView.findViewById<android.widget.FrameLayout>(R.id.format_underline)
    val formatWavyUnderlineView = dialogView.findViewById<android.widget.FrameLayout>(R.id.format_wavy_underline)
    
    // 更新格式选项选中状态的函数（确保唯一选中）
    fun updateFormatSelection(selectedView: android.widget.FrameLayout?) {
        // 清除所有选中状态
        formatHighlightView?.isSelected = false
        formatUnderlineView?.isSelected = false
        formatWavyUnderlineView?.isSelected = false
        // 设置当前选中状态
        selectedView?.isSelected = true
    }
    
    // 根据已有划线或默认值选中格式
    val defaultFormatView = when (selectedStyle) {
        HighlightStyle.HIGHLIGHT -> formatHighlightView
        HighlightStyle.UNDERLINE -> formatUnderlineView
        HighlightStyle.WAVY_UNDERLINE -> formatWavyUnderlineView
    }
    updateFormatSelection(defaultFormatView)
    
    // 格式选项点击事件
    formatHighlightView?.setOnClickListener {
        updateFormatSelection(formatHighlightView)
        selectedStyle = HighlightStyle.HIGHLIGHT
        // 即时预览
        previewHighlightInWebView(selectedText, startOffset, endOffset, selectedColor, selectedStyle, previewHighlightId)
        previewHighlightId = "preview_${System.currentTimeMillis()}"
    }
    
    formatUnderlineView?.setOnClickListener {
        updateFormatSelection(formatUnderlineView)
        selectedStyle = HighlightStyle.UNDERLINE
        // 即时预览
        previewHighlightInWebView(selectedText, startOffset, endOffset, selectedColor, selectedStyle, previewHighlightId)
        previewHighlightId = "preview_${System.currentTimeMillis()}"
    }
    
    formatWavyUnderlineView?.setOnClickListener {
        updateFormatSelection(formatWavyUnderlineView)
        selectedStyle = HighlightStyle.WAVY_UNDERLINE
        // 即时预览
        previewHighlightInWebView(selectedText, startOffset, endOffset, selectedColor, selectedStyle, previewHighlightId)
        previewHighlightId = "preview_${System.currentTimeMillis()}"
    }
    
    // 颜色选择器
    val colorPickerContainer = dialogView.findViewById<LinearLayout>(R.id.color_picker_container)
    val colorViews = mapOf(
        R.id.color_pink to "#FF69B4",
        R.id.color_purple to "#9C27B0",
        R.id.color_light_blue to "#5AC8FA",
        R.id.color_green to "#34C759",
        R.id.color_yellow to "#FFD60A"
    )
    
    // 根据已有划线或默认值选中颜色
    val defaultColorId = when (selectedColor) {
        "#FF69B4" -> R.id.color_pink
        "#9C27B0" -> R.id.color_purple
        "#5AC8FA" -> R.id.color_light_blue
        "#34C759" -> R.id.color_green
        "#FFD60A" -> R.id.color_yellow
        else -> R.id.color_yellow
    }
    dialogView.findViewById<android.widget.FrameLayout>(defaultColorId)?.isSelected = true
    
    // 即时预览划线功能：点击颜色立即预览，延迟500ms后自动确认
    colorViews.forEach { (id, color) ->
        // 查找FrameLayout容器（因为现在颜色选择器是FrameLayout）
        val colorContainer = dialogView.findViewById<View>(id) as? android.widget.FrameLayout
        colorContainer?.setOnClickListener {
            // 取消之前的延迟确认
            previewHighlightRunnable?.let { previewHighlightHandler?.removeCallbacks(it) }
            
            // 取消所有选中状态
            colorViews.keys.forEach { viewId ->
                dialogView.findViewById<View>(viewId)?.isSelected = false
            }
            // 设置当前选中
            colorContainer.isSelected = true
            selectedColor = color
            
            // 即时预览：在WebView中临时显示划线效果（使用当前选中的格式）
            previewHighlightInWebView(selectedText, startOffset, endOffset, color, selectedStyle, previewHighlightId)
            previewHighlightId = "preview_${System.currentTimeMillis()}"
            
            // 延迟500ms后自动确认添加（给用户预览时间）
            previewHighlightHandler = Handler(Looper.getMainLooper())
            previewHighlightRunnable = Runnable {
                if (textSelectionDialog?.isShowing == true) {
                    // 清除预览
                    clearPreviewHighlight()
                    // 如果已有划线，更新它；否则添加新划线
                    if (existingHighlight != null) {
                        // 更新现有划线
                        val updatedHighlight = existingHighlight.copy(color = color, style = selectedStyle)
                        dataManager.deleteHighlight(existingHighlight.id)
                        dataManager.addHighlight(updatedHighlight)
                        syncHighlightToUnifiedCollection(updatedHighlight)
                        displayPage(currentPageIndex)
                        hideTextSelectionDialog()
                        Toast.makeText(this, "已更新划线", Toast.LENGTH_SHORT).show()
                    } else {
                        // 添加新划线（使用当前选中的格式和颜色）
                        addHighlight(selectedText, startOffset, endOffset, color, selectedStyle)
                        hideTextSelectionDialog()
                        Toast.makeText(this, "已添加划线", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            previewHighlightHandler?.postDelayed(previewHighlightRunnable!!, 500)
        }
    }
    
    // 操作菜单
    // 复制
    dialogView.findViewById<LinearLayout>(R.id.action_copy)?.setOnClickListener {
        copyText(selectedText)
        hideTextSelectionDialog()
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    // 取消划线（如果已有划线则显示）
    val removeHighlightView = dialogView.findViewById<LinearLayout>(R.id.action_remove_highlight)
    if (existingHighlight != null) {
        removeHighlightView?.visibility = View.VISIBLE
        removeHighlightView?.setOnClickListener {
            dataManager.deleteHighlight(existingHighlight.id)
            displayPage(currentPageIndex)
            hideTextSelectionDialog()
            Toast.makeText(this, "已取消划线", Toast.LENGTH_SHORT).show()
        }
        // 如果已有划线，立即显示预览效果（使用已有的样式和颜色）
        previewHighlightInWebView(selectedText, startOffset, endOffset, existingHighlight.color, existingHighlight.style, previewHighlightId)
        previewHighlightId = "preview_${System.currentTimeMillis()}"
    } else {
        removeHighlightView?.visibility = View.GONE
        // 如果没有已有划线，也显示默认预览
        previewHighlightInWebView(selectedText, startOffset, endOffset, selectedColor, selectedStyle, previewHighlightId)
        previewHighlightId = "preview_${System.currentTimeMillis()}"
    }
    
    // 写想法
    dialogView.findViewById<LinearLayout>(R.id.action_write_idea)?.setOnClickListener {
        hideTextSelectionDialog()
        addNote(selectedText, startOffset)
    }
    
    // 分享
    dialogView.findViewById<LinearLayout>(R.id.action_share)?.setOnClickListener {
        hideTextSelectionDialog()
        shareText(selectedText)
    }
    
    // 查询
    dialogView.findViewById<LinearLayout>(R.id.action_query)?.setOnClickListener {
        hideTextSelectionDialog()
        searchText(selectedText)
    }
    
    // 听当前（TTS朗读）
    dialogView.findViewById<LinearLayout>(R.id.action_listen)?.setOnClickListener {
        hideTextSelectionDialog()
        startTTSForText(selectedText)
    }
    
    
    // 如果已有对话框显示，先关闭
    textSelectionDialog?.dismiss()
    
    // 创建对话框
    val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
    dialog.setContentView(dialogView)
    
    // 获取屏幕尺寸和选中文本位置
    val screenWidth = resources.displayMetrics.widthPixels
    val screenHeight = resources.displayMetrics.heightPixels
    
    // 测量菜单大小
    dialogView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val menuWidth = dialogView.measuredWidth
    val menuHeight = dialogView.measuredHeight
    
    // 定位菜单：屏幕中心，不遮挡文本选择区域
    val params = dialog.window?.attributes
    params?.x = (screenWidth - menuWidth) / 2
    params?.y = screenHeight / 3 // 屏幕上方1/3处，避免遮挡选中文本
    dialog.window?.attributes = params
    
    dialog.window?.setLayout(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    dialog.window?.setGravity(android.view.Gravity.TOP or android.view.Gravity.START)
    dialog.setCancelable(true)
    dialog.setCanceledOnTouchOutside(true)
    
    // 添加淡入动画
    dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)
    
    // 点击对话框外部区域时关闭
    dialog.setOnDismissListener {
        // 如果有待处理的预览任务（说明用户选择了颜色但未等到自动确认），立即执行保存
        if (previewHighlightRunnable != null) {
            Log.d(TAG, "对话框关闭，立即执行待处理的划线保存")
            previewHighlightRunnable?.run()
            previewHighlightRunnable = null
            previewHighlightHandler?.removeCallbacksAndMessages(null)
        } else {
            // 只有在没有保存操作时才清除预览
            // 因为如果执行了run()，里面会处理清除和保存
            clearPreviewHighlight()
        }
        
        // 清除WebView中的文本选择
        webView.evaluateJavascript("window.getSelection().removeAllRanges();", null)
    }
    
    textSelectionDialog = dialog
    
    // 立即显示对话框
    try {
        dialog.show()
        Log.d(TAG, "文本选择菜单已显示，选中文本: ${selectedText.take(20)}...")
    } catch (e: Exception) {
        Log.e(TAG, "显示文本选择菜单失败", e)
    }
}

/**
 * 应用菜单主题（暗色/亮色模式）
 */
private fun applyMenuTheme(dialogView: View, isDarkMode: Boolean) {
    // 根据系统主题切换亮色/暗色模式
    val backgroundColor = if (isDarkMode) {
        // 暗色模式：深色背景
        android.graphics.Color.parseColor("#2C2C2E")
    } else {
        // 亮色模式：浅色背景
        android.graphics.Color.parseColor("#FFFFFF")
    }
    
    val textColor = if (isDarkMode) {
        android.graphics.Color.parseColor("#FFFFFF")
    } else {
        android.graphics.Color.parseColor("#333333")
    }
    
    val iconColor = if (isDarkMode) {
        android.graphics.Color.parseColor("#FFFFFF")
    } else {
        android.graphics.Color.parseColor("#333333")
    }
    
    val colorPickerBgColor = if (isDarkMode) {
        android.graphics.Color.parseColor("#1A1A1A")
    } else {
        android.graphics.Color.parseColor("#F5F5F5")
    }
    
    // 应用背景色
    dialogView.findViewById<LinearLayout>(R.id.format_color_picker_container)?.setBackgroundColor(backgroundColor)
    dialogView.findViewById<LinearLayout>(R.id.action_menu_container)?.setBackgroundColor(backgroundColor)
    
    // 应用颜色选择器背景色
    dialogView.findViewById<LinearLayout>(R.id.color_picker_container)?.setBackgroundColor(colorPickerBgColor)
    
    // 应用所有操作按钮的图标和文字颜色
    dialogView.findViewById<LinearLayout>(R.id.action_menu_container)?.let { container ->
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? LinearLayout
            child?.let {
                // 查找ImageView和TextView
                for (j in 0 until it.childCount) {
                    val subChild = it.getChildAt(j)
                    when (subChild) {
                        is android.widget.ImageView -> {
                            subChild.setColorFilter(iconColor)
                        }
                        is TextView -> {
                            subChild.setTextColor(textColor)
                        }
                    }
                }
            }
        }
    }
    
    // 应用格式按钮文字颜色（如果亮色模式，需要调整）
    if (!isDarkMode) {
        dialogView.findViewById<TextView>(R.id.format_highlight_text)?.setTextColor(android.graphics.Color.parseColor("#333333"))
        dialogView.findViewById<TextView>(R.id.format_underline_text)?.setTextColor(android.graphics.Color.parseColor("#333333"))
        dialogView.findViewById<View>(R.id.format_underline_line)?.background?.setTint(android.graphics.Color.parseColor("#333333"))
        dialogView.findViewById<TextView>(R.id.format_wavy_underline_text)?.setTextColor(android.graphics.Color.parseColor("#333333"))
    }
    
    // 应用箭头指示器颜色
    dialogView.findViewById<View>(R.id.arrow_indicator)?.background?.setTint(backgroundColor)
}

/**
 * 在WebView中预览划线效果（即时预览）
 */
private fun previewHighlightInWebView(text: String, startOffset: Int, endOffset: Int, color: String, style: HighlightStyle, previousPreviewId: String?) {
    try {
        // 先清除之前的预览
        clearPreviewHighlight()
        
        // 在WebView中应用预览划线（直接使用颜色值，不转义）
        val escapedColor = color
        val previewScript = """
            (function() {
                // 清除之前的预览
                var oldPreview = document.getElementById('preview_highlight');
                if (oldPreview) {
                    var parent = oldPreview.parentNode;
                    while (oldPreview.firstChild) {
                        parent.insertBefore(oldPreview.firstChild, oldPreview);
                    }
                    parent.removeChild(oldPreview);
                }
                
                // 获取body中的div容器（包含文本内容）
                var body = document.body;
                var contentDiv = body.querySelector('div');
                if (!contentDiv) {
                    console.error('找不到内容容器');
                    return;
                }
                
                // 获取所有文本节点（按顺序遍历，排除HTML标签）
                // 注意：textContent会排除HTML标签，所以位置计算基于纯文本
                // 这与文本选择时的位置计算逻辑保持一致
                var walker = document.createTreeWalker(
                    contentDiv,
                    NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT,
                    {
                        acceptNode: function(node) {
                            if (node.nodeType === Node.TEXT_NODE) return NodeFilter.FILTER_ACCEPT;
                            if (node.tagName === 'BR') return NodeFilter.FILTER_ACCEPT;
                            return NodeFilter.FILTER_SKIP;
                        }
                    },
                    false
                );
                
                var node;
                var currentOffset = 0;
                var startNode = null;
                var endNode = null;
                var startNodeOffset = 0;
                var endNodeOffset = 0;
                
                // 遍历所有文本节点和BR标签，找到对应位置的节点
                while (node = walker.nextNode()) {
                    var nodeText = '';
                    var nodeLength = 0;
                    
                    if (node.nodeType === Node.TEXT_NODE) {
                        nodeText = node.textContent || '';
                        nodeLength = nodeText.length;
                    } else if (node.tagName === 'BR') {
                        nodeLength = 1;
                    }
                    
                    // 查找起始节点
                    if (startNode == null) {
                        if (currentOffset + nodeLength > $startOffset) {
                            if (node.nodeType === Node.TEXT_NODE) {
                                startNode = node;
                                startNodeOffset = Math.max(0, $startOffset - currentOffset);
                            } else {
                                // 如果起始位置在BR上，移动到下一个节点
                                // 或者不做处理，因为BR不能作为Range的容器（通常）
                            }
                        } else {
                            currentOffset += nodeLength;
                            continue;
                        }
                    }
                    
                    // 查找结束节点
                    if (startNode != null && endNode == null) {
                        if (currentOffset + nodeLength >= $endOffset) {
                            if (node.nodeType === Node.TEXT_NODE) {
                                endNode = node;
                                endNodeOffset = Math.min(nodeLength, $endOffset - currentOffset);
                                break;
                            } else {
                                // 如果结束位置在BR上
                                currentOffset += nodeLength;
                            }
                        } else {
                            currentOffset += nodeLength;
                        }
                    }
                }
                
                if (startNode && endNode) {
                    try {
                        // 创建范围
                        var range = document.createRange();
                        range.setStart(startNode, startNodeOffset);
                        range.setEnd(endNode, endNodeOffset);
                        
                        // 创建高亮span
                        var span = document.createElement('span');
                        span.id = 'preview_highlight';
                        var styleType = '${style.name}';
                        var colorValue = '$escapedColor';
                        
                        // 清除所有可能的样式，避免冲突
                        span.style.backgroundColor = '';
                        span.style.borderBottom = '';
                        span.style.backgroundImage = '';
                        span.style.textDecoration = '';
                        
                        if (styleType === 'HIGHLIGHT') {
                            // 高亮/填充样式：使用背景色
                            span.style.backgroundColor = colorValue;
                            span.style.padding = '2px 0';
                            span.style.borderRadius = '2px';
                            span.style.display = 'inline';
                        } else if (styleType === 'UNDERLINE') {
                            // 下划线样式：使用边框
                            span.style.borderBottom = '2px solid ' + colorValue;
                            span.style.paddingBottom = '1px';
                            span.style.display = 'inline';
                            span.style.textDecoration = 'none';
                        } else if (styleType === 'WAVY_UNDERLINE') {
                            // 波浪下划线样式：使用SVG背景实现真实的波浪效果
                            var svgColor = colorValue.replace('#', '%23'); // URL编码
                            // 使用更明显的波浪效果，确保颜色正确
                            var svgData = 'data:image/svg+xml,%3Csvg xmlns=\\'http://www.w3.org/2000/svg\\' viewBox=\\'0 0 20 4\\'%3E%3Cpath d=\\'M0,2 Q5,0 10,2 T20,2\\' stroke=\\'' + svgColor + '\\' fill=\\'none\\' stroke-width=\\'2\\'/%3E%3C/svg%3E';
                            span.style.backgroundImage = 'url("' + svgData + '")';
                            span.style.backgroundRepeat = 'repeat-x';
                            span.style.backgroundSize = '20px 4px';
                            span.style.backgroundPosition = 'bottom';
                            span.style.paddingBottom = '4px';
                            span.style.display = 'inline';
                            span.style.textDecoration = 'none';
                        }
                        span.style.opacity = '0.8';
                        
                        // 使用surroundContents包装选中内容
                        try {
                            range.surroundContents(span);
                        } catch(e) {
                            // 如果surroundContents失败，使用extractContents和insertNode
                            var contents = range.extractContents();
                            span.appendChild(contents);
                            range.insertNode(span);
                        }
                    } catch(e) {
                        console.error('预览划线失败:', e);
                    }
                } else {
                    console.error('找不到文本节点: startNode=' + startNode + ', endNode=' + endNode);
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(previewScript, null)
        Log.d(TAG, "预览划线: 颜色=$color, 位置=$startOffset-$endOffset")
    } catch (e: Exception) {
        Log.e(TAG, "预览划线失败", e)
    }
}

/**
 * 清除预览划线
 */
private fun clearPreviewHighlight() {
    try {
        val clearScript = """
            (function() {
                var preview = document.getElementById('preview_highlight');
                if (preview) {
                    // 恢复原始文本
                    var parent = preview.parentNode;
                    while (preview.firstChild) {
                        parent.insertBefore(preview.firstChild, preview);
                    }
                    parent.removeChild(preview);
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(clearScript, null)
        Log.d(TAG, "清除预览划线")
    } catch (e: Exception) {
        Log.e(TAG, "清除预览划线失败", e)
    }
}

/**
 * 显示划线颜色选择器
 */
private fun showHighlightColorPicker(selectedText: String, startOffset: Int, endOffset: Int) {
    val colors = arrayOf(
        "黄色",
        "绿色",
        "蓝色",
        "红色",
        "紫色"
    )
    val colorValues = arrayOf(
        "#FFEB3B",
        "#4CAF50",
        "#2196F3",
        "#F44336",
        "#9C27B0"
    )
    
    AlertDialog.Builder(this)
        .setTitle("选择划线颜色")
        .setItems(colors) { _, which ->
            addHighlight(selectedText, startOffset, endOffset, colorValues[which])
            hideTextSelectionDialog()
            Toast.makeText(this, "已添加划线", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("取消") { _, _ ->
            hideTextSelectionDialog()
        }
        .show()
}

/**
 * 显示AI对话框
 */
private fun showAIDialog(selectedText: String) {
    val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null)
    val input = android.widget.EditText(this).apply {
        hint = "向AI提问关于这段文字..."
        setText("请解释：$selectedText")
        setSelection(text.length)
    }
    
    AlertDialog.Builder(this)
        .setTitle("AI助手")
        .setView(input)
        .setPositiveButton("提问") { _, _ ->
            val question = input.text.toString()
            if (question.isNotBlank()) {
                // TODO: 集成AI API
                Toast.makeText(this, "AI功能开发中...\n问题: $question", Toast.LENGTH_LONG).show()
            }
        }
        .setNegativeButton("取消", null)
        .show()
}

/**
 * 搜索文字
 */
private fun searchText(text: String) {
    try {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, text)
        }
        startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(this, "无法打开搜索", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 翻译文字
 */
private fun translateText(text: String) {
    try {
        // 使用Google翻译
        val url = "https://translate.google.com/?sl=auto&tl=zh-CN&text=${Uri.encode(text)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(this, "无法打开翻译", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 添加划线（带颜色和样式）
 */
private fun addHighlight(text: String, startOffset: Int, endOffset: Int, color: String = "#FFEB3B", style: HighlightStyle = HighlightStyle.HIGHLIGHT) {
    val highlight = Highlight(
        id = UUID.randomUUID().toString(),
        filePath = filePath,
        pageIndex = currentPageIndex,
        startPosition = startOffset,
        endPosition = endOffset,
        text = text,
        color = color,
        style = style
    )
    dataManager.addHighlight(highlight)
    
    // 同步到统一收藏管理器
    syncHighlightToUnifiedCollection(highlight)
    
    // 重新显示当前页以显示划线
    displayPage(currentPageIndex)
}
    
    private var textSelectionDialog: android.app.Dialog? = null
    private var previewHighlightHandler: Handler? = null
    private var previewHighlightRunnable: Runnable? = null
    
    private fun hideTextSelectionDialog() {
        // 取消预览延迟确认
        previewHighlightRunnable?.let { previewHighlightHandler?.removeCallbacks(it) }
        previewHighlightHandler = null
        previewHighlightRunnable = null
        // 清除预览划线
        clearPreviewHighlight()
        textSelectionDialog?.dismiss()
        textSelectionDialog = null
    }
    
    /**
     * 添加划线（兼容旧版本，使用默认颜色）
     */
    private fun addHighlight(text: String, startOffset: Int, endOffset: Int) {
        // 调用带颜色参数的版本，使用默认颜色
        addHighlight(text, startOffset, endOffset, "#FFEB3B")
        Toast.makeText(this, "已添加划线", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 朗读选中的文本
     */
    private fun startTTSForText(text: String) {
        try {
            if (ttsManager == null) {
                ttsManager = TTSManager.getInstance(this)
            }
            
            // 停止当前播放
            ttsManager?.stop()
            
            // 开始朗读选中文本
            ttsManager?.speak(text)
            isTTSPlaying = true
            Toast.makeText(this, "开始朗读", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "启动TTS失败", e)
            Toast.makeText(this, "朗读功能不可用", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 同步划线到统一收藏管理器
     */
    private fun syncHighlightToUnifiedCollection(highlight: Highlight) {
        try {
            val collectionManager = UnifiedCollectionManager.getInstance(this)
            
            // 获取文件名作为书名
            val bookName = fileName.ifEmpty { 
                File(filePath).nameWithoutExtension.ifEmpty { "未命名书籍" }
            }
            
            // 创建预览文本（前200字符）
            val preview = highlight.text.take(200) + if (highlight.text.length > 200) "..." else ""
            
            // 创建收藏项
            val collectionItem = UnifiedCollectionItem(
                title = highlight.text.take(50), // 标题使用划线文本的前50字符
                content = highlight.text, // 完整内容
                preview = preview,
                collectionType = CollectionType.READING_HIGHLIGHT,
                sourceLocation = "阅读器",
                sourceDetail = bookName,
                extraData = mapOf(
                    "bookName" to bookName,
                    "filePath" to filePath,
                    "pageIndex" to highlight.pageIndex,
                    "startPosition" to highlight.startPosition,
                    "endPosition" to highlight.endPosition,
                    "highlightColor" to highlight.color,
                    "highlightId" to highlight.id
                )
            )
            
            // 保存到统一收藏管理器
            val success = collectionManager.addCollection(collectionItem)
            
            if (success) {
                Log.d(TAG, "划线已同步到统一收藏管理器: 书名=$bookName, 文本长度=${highlight.text.length}")
                
                // 发送广播通知收藏更新
                val intent = android.content.Intent("com.example.aifloatingball.COLLECTION_UPDATED").apply {
                    putExtra("collection_type", CollectionType.READING_HIGHLIGHT.name)
                    putExtra("action", "add")
                    putExtra("collection_id", collectionItem.id)
                }
                sendBroadcast(intent)
            } else {
                Log.e(TAG, "同步划线到统一收藏管理器失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步划线到统一收藏管理器时发生错误", e)
        }
    }
    
    /**
     * 显示划线管理界面
     */
    private fun showHighlightDialog() {
        val intent = Intent(this, HighlightManagerActivity::class.java).apply {
            putExtra(HighlightManagerActivity.EXTRA_FILE_PATH, filePath)
        }
        startActivityForResult(intent, HighlightManagerActivity.RESULT_HIGHLIGHT_SELECTED)
    }
    
    /**
     * 添加笔记
     */
    private fun addNote(text: String, position: Int) {
        val input = android.widget.EditText(this).apply {
            hint = "输入笔记内容..."
        }
        
        AlertDialog.Builder(this)
            .setTitle("添加笔记")
            .setMessage("原文：${text.take(50)}${if (text.length > 50) "..." else ""}")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val noteContent = input.text.toString()
                val note = Note(
                    id = UUID.randomUUID().toString(),
                    filePath = filePath,
                    pageIndex = currentPageIndex,
                    position = position,
                    text = text,
                    noteContent = noteContent
                )
                dataManager.addNote(note)
                Toast.makeText(this, "笔记已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示笔记管理界面
     */
    private fun showNoteDialog() {
        val intent = Intent(this, NoteManagerActivity::class.java).apply {
            putExtra(NoteManagerActivity.EXTRA_FILE_PATH, filePath)
        }
        startActivityForResult(intent, NoteManagerActivity.RESULT_NOTE_SELECTED)
    }
    
    /**
     * 显示笔记编辑对话框
     */
    private fun showNoteEditDialog(note: Note) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_note, null)
        val originalText = dialogView.findViewById<TextView>(R.id.originalText)
        val noteInput = dialogView.findViewById<android.widget.EditText>(R.id.noteInput)
        
        originalText.text = "原文：${note.text}"
        noteInput.setText(note.noteContent)
        noteInput.hint = "输入笔记内容..."
        
        AlertDialog.Builder(this)
            .setTitle("编辑笔记")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val updatedNote = note.copy(noteContent = noteInput.text.toString())
                dataManager.updateNote(updatedNote)
                Toast.makeText(this, "笔记已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // ==================== 设置功能 ====================
    
    /**
     * 显示设置对话框（iOS风格选项式）
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reader_settings, null)
        
        // 翻页速度选项
        val speedSlow = dialogView.findViewById<TextView>(R.id.speedSlow)
        val speedMedium = dialogView.findViewById<TextView>(R.id.speedMedium)
        val speedFast = dialogView.findViewById<TextView>(R.id.speedFast)
        
        // 设置当前选中状态
        listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
        when {
            settings.pageAnimationDuration >= 1000 -> speedSlow.isSelected = true
            settings.pageAnimationDuration >= 500 -> speedMedium.isSelected = true
            else -> speedFast.isSelected = true
        }
        
        speedSlow.setOnClickListener {
            settings.pageAnimationDuration = 1000
            listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
            speedSlow.isSelected = true
            dataManager.saveSettings(settings)
        }
        speedMedium.setOnClickListener {
            settings.pageAnimationDuration = 1500
            listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
            speedMedium.isSelected = true
            dataManager.saveSettings(settings)
        }
        speedFast.setOnClickListener {
            settings.pageAnimationDuration = 0
            listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
            speedFast.isSelected = true
            dataManager.saveSettings(settings)
        }
        
        // 字体大小选项
        val fontSizeSmall = dialogView.findViewById<TextView>(R.id.fontSizeSmall)
        val fontSizeMedium = dialogView.findViewById<TextView>(R.id.fontSizeMedium)
        val fontSizeLarge = dialogView.findViewById<TextView>(R.id.fontSizeLarge)
        val fontSizeExtraLarge = dialogView.findViewById<TextView>(R.id.fontSizeExtraLarge)
        val fontSizePreview = dialogView.findViewById<TextView>(R.id.fontSizePreview)
        
        // 设置当前选中状态
        listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
        when {
            settings.fontSize <= 14 -> {
                fontSizeSmall.isSelected = true
                fontSizePreview.textSize = 14f
            }
            settings.fontSize <= 18 -> {
                fontSizeMedium.isSelected = true
                fontSizePreview.textSize = 19f
            }
            settings.fontSize <= 22 -> {
                fontSizeLarge.isSelected = true
                fontSizePreview.textSize = 24f
            }
            else -> {
                fontSizeExtraLarge.isSelected = true
                fontSizePreview.textSize = 30f
            }
        }
        
        fontSizeSmall.setOnClickListener {
            settings.fontSize = 14
            fontSizePreview.textSize = 14f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeSmall.isSelected = true
            updateContent()
        }
        fontSizeMedium.setOnClickListener {
            settings.fontSize = 18
            fontSizePreview.textSize = 19f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeMedium.isSelected = true
            updateContent()
        }
        fontSizeLarge.setOnClickListener {
            settings.fontSize = 22
            fontSizePreview.textSize = 24f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeLarge.isSelected = true
            updateContent()
        }
        fontSizeExtraLarge.setOnClickListener {
            settings.fontSize = 26
            fontSizePreview.textSize = 30f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeExtraLarge.isSelected = true
            updateContent()
        }
        
        // 字体样式选项
        val fontStyleDefault = dialogView.findViewById<TextView>(R.id.fontStyleDefault)
        val fontStyleSerif = dialogView.findViewById<TextView>(R.id.fontStyleSerif)
        val fontStyleMonospace = dialogView.findViewById<TextView>(R.id.fontStyleMonospace)
        
        listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
        when (settings.fontFamily) {
            "serif" -> fontStyleSerif.isSelected = true
            "monospace" -> fontStyleMonospace.isSelected = true
            else -> fontStyleDefault.isSelected = true
        }
        
        fontStyleDefault.setOnClickListener {
            settings.fontFamily = "sans-serif"
            listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
            fontStyleDefault.isSelected = true
            updateContent()
        }
        fontStyleSerif.setOnClickListener {
            settings.fontFamily = "serif"
            listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
            fontStyleSerif.isSelected = true
            updateContent()
        }
        fontStyleMonospace.setOnClickListener {
            settings.fontFamily = "monospace"
            listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
            fontStyleMonospace.isSelected = true
            updateContent()
        }
        
        // 其他设置
        val settingKeepScreen = dialogView.findViewById<TextView>(R.id.settingKeepScreen)
        val settingAutoMode = dialogView.findViewById<TextView>(R.id.settingAutoMode)
        
        settingKeepScreen.isSelected = settings.keepScreenOn
        settingAutoMode.isSelected = settings.isAutoReadEnabled
        
        settingKeepScreen.setOnClickListener {
            settings.keepScreenOn = !settings.keepScreenOn
            settingKeepScreen.isSelected = settings.keepScreenOn
            if (settings.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            dataManager.saveSettings(settings)
        }
        
        settingAutoMode.setOnClickListener {
            settings.isAutoReadEnabled = !settings.isAutoReadEnabled
            settingAutoMode.isSelected = settings.isAutoReadEnabled
            if (settings.isAutoReadEnabled) {
                startAutoRead()
            } else {
                stopAutoRead()
            }
            dataManager.saveSettings(settings)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    /**
     * 更新内容显示（重新分页并显示）
     */
    private fun updateContent() {
        dataManager.saveSettings(settings)
        pages = paginateText(fullText)
        totalPages = pages.size
        displayPage(currentPageIndex.coerceIn(0, totalPages - 1))
        updatePageInfo()
    }
    
    // ==================== TTS功能 ====================
    
    /**
     * 切换TTS播放
     */
    private fun toggleTTS() {
        if (isTTSPlaying) {
            stopTTS()
        } else {
            startTTS()
        }
    }
    
    /**
     * 开始TTS
     */
    private fun startTTS() {
        if (currentPageIndex >= pages.size) return
        
        val pageText = pages[currentPageIndex]
        ttsManager?.setSpeechRate(settings.ttsSpeed)
        ttsManager?.setPitch(settings.ttsPitch)
        ttsManager?.speak(pageText, "page_$currentPageIndex")
        
        isTTSPlaying = true
        ttsIcon?.setImageResource(R.drawable.ic_volume_off)
        ttsIcon?.setColorFilter(android.graphics.Color.parseColor("#F44336"))
        ttsText?.setTextColor(android.graphics.Color.parseColor("#F44336"))
        Toast.makeText(this, "开始听书", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 停止TTS
     */
    private fun stopTTS() {
        ttsManager?.stop()
        isTTSPlaying = false
        ttsIcon?.setImageResource(R.drawable.ic_volume_on)
        ttsIcon?.setColorFilter(android.graphics.Color.parseColor("#2196F3"))
        ttsText?.setTextColor(android.graphics.Color.parseColor("#2196F3"))
        Toast.makeText(this, "停止听书", Toast.LENGTH_SHORT).show()
    }
    
    // ==================== 自动翻页功能 ====================
    
    /**
     * 自动翻页方案
     */
    enum class AutoReadMode {
        SCROLL, // 平滑滚动
        PAGE_TURN // 翻页
    }
    
    private var autoReadMode: AutoReadMode = AutoReadMode.PAGE_TURN
    
    /**
     * 切换自动翻页
     */
    private fun toggleAutoRead() {
        if (isAutoReading) {
            stopAutoRead()
        } else {
            showAutoReadSettingsDialog()
        }
    }
    
    /**
     * 显示自动翻页设置对话框
     */
    private fun showAutoReadSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_auto_read_settings, null)
        
        // 翻页速度滑动条
        val speedSeekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.speedSeekBar)
        val speedText = dialogView.findViewById<TextView>(R.id.speedText)
        speedSeekBar.max = 90 // 1-10秒，步长0.1秒，共90个值
        // 将毫秒转换为秒，然后映射到0-90的范围（1秒对应90，10秒对应0）
        val currentSpeedSeconds = (settings.autoReadSpeed / 1000.0).coerceIn(1.0, 10.0)
        speedSeekBar.progress = ((10.0 - currentSpeedSeconds) * 10).toInt().coerceIn(0, 90)
        speedText.text = String.format("%.1f秒", currentSpeedSeconds)
        speedSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = 10.0 - progress / 10.0 // 1-10秒
                    settings.autoReadSpeed = (speed * 1000).toInt()
                    speedText.text = String.format("%.1f秒", speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // 翻页方案选择
        val modeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.modeGroup)
        when (autoReadMode) {
            AutoReadMode.SCROLL -> modeGroup.check(R.id.modeScroll)
            AutoReadMode.PAGE_TURN -> modeGroup.check(R.id.modePageTurn)
        }
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            autoReadMode = when (checkedId) {
                R.id.modeScroll -> AutoReadMode.SCROLL
                R.id.modePageTurn -> AutoReadMode.PAGE_TURN
                else -> AutoReadMode.PAGE_TURN
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("自动翻页设置")
            .setView(dialogView)
            .setPositiveButton("开始") { dialog, _ ->
                dataManager.saveSettings(settings)
                startAutoRead()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 开始自动翻页
     */
    private fun startAutoRead() {
        isAutoReading = true
        autoReadIcon?.setImageResource(R.drawable.ic_pause)
        autoReadIcon?.setColorFilter(android.graphics.Color.parseColor("#F44336"))
        autoReadText?.setTextColor(android.graphics.Color.parseColor("#F44336"))
        
        when (autoReadMode) {
            AutoReadMode.SCROLL -> startAutoScroll()
            AutoReadMode.PAGE_TURN -> startAutoPageTurn()
        }
        
        Toast.makeText(this, "开始自动翻页", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 开始自动翻页（翻页模式）
     */
    private fun startAutoPageTurn() {
        autoReadRunnable = object : Runnable {
            override fun run() {
                if (isAutoReading && currentPageIndex < totalPages - 1) {
                    goToNextPage()
                    autoReadHandler.postDelayed(this, settings.autoReadSpeed.toLong())
                } else {
                    stopAutoRead()
                }
            }
        }
        autoReadHandler.postDelayed(autoReadRunnable!!, settings.autoReadSpeed.toLong())
    }
    
    /**
     * 开始自动滚动（滚动模式）
     */
    private fun startAutoScroll() {
        // 使用WebView的滚动功能
        autoReadRunnable = object : Runnable {
            private var scrollPosition = 0
            override fun run() {
                if (isAutoReading) {
                    // 计算滚动距离（根据速度）
                    val scrollStep = (webView.height * 0.1).toInt() // 每次滚动10%屏幕高度
                    scrollPosition += scrollStep
                    
                    // 执行滚动
                    webView.evaluateJavascript("window.scrollTo(0, $scrollPosition);", null)
                    
                    // 检查是否到达页面底部
                    webView.evaluateJavascript("document.body.scrollHeight - window.innerHeight - window.scrollY", { result ->
                        val remaining = result?.toDoubleOrNull() ?: 0.0
                        if (remaining <= 0 && currentPageIndex < totalPages - 1) {
                            // 滚动到底部，翻到下一页
                            goToNextPage()
                            scrollPosition = 0
                        }
                    })
                    
                    autoReadHandler.postDelayed(this, (settings.autoReadSpeed / 10).toLong())
                } else {
                    stopAutoRead()
                }
            }
        }
        autoReadHandler.postDelayed(autoReadRunnable!!, (settings.autoReadSpeed / 10).toLong())
    }
    
    /**
     * 停止自动翻页
     */
    private fun stopAutoRead() {
        isAutoReading = false
        autoReadRunnable?.let { autoReadHandler.removeCallbacks(it) }
        autoReadIcon?.setImageResource(R.drawable.ic_play)
        autoReadIcon?.setColorFilter(android.graphics.Color.parseColor("#4CAF50"))
        autoReadText?.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        Toast.makeText(this, "停止自动翻页", Toast.LENGTH_SHORT).show()
    }
    
    // ==================== 分享功能 ====================
    
    /**
     * 分享内容
     */
    private fun shareContent() {
        if (currentPageIndex >= pages.size) return
        
        val pageText = pages[currentPageIndex]
        val shareText = "$fileName\n\n$pageText"
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        
        startActivity(Intent.createChooser(intent, "分享内容"))
    }
    
    /**
     * 分享文本
     */
    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "分享文本"))
    }
    
    /**
     * 复制文本
     */
    private fun copyText(text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("文本", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
    }
    
    // ==================== 阅读进度 ====================
    
    /**
     * 加载阅读进度
     */
    private fun loadReadingProgress() {
        val progress = dataManager.getProgress(filePath)
        if (progress != null && totalPages > 0) {
            // 确保页码在有效范围内
            val savedPage = progress.currentPage.coerceIn(0, totalPages - 1)
            currentPageIndex = savedPage
            Log.d(TAG, "加载阅读进度: 第${currentPageIndex + 1}页/共${totalPages}页")
        } else {
            currentPageIndex = 0
            Log.d(TAG, "未找到阅读进度，从第一页开始")
        }
    }
    
    /**
     * 保存阅读进度
     */
    private fun saveReadingProgress() {
        val progress = ReadingProgress(
            filePath = filePath,
            currentPage = currentPageIndex,
            totalPages = totalPages,
            position = currentPageIndex * calculateCharsPerPage()
        )
        dataManager.saveProgress(progress)
        
        // 同步更新到统一收藏管理系统
        updateEbookCollectionProgress()
    }
    
    /**
     * 更新电子书收藏的阅读进度
     */
    private fun updateEbookCollectionProgress() {
        try {
            val collectionManager = UnifiedCollectionManager.getInstance(this)
            val existingCollection = collectionManager.getAllCollections()
                .find { 
                    it.collectionType == CollectionType.EBOOK_BOOKMARK && 
                    it.extraData?.get("filePath") == filePath 
                }
            
            if (existingCollection != null) {
                val progressPercent = if (totalPages > 0) {
                    (currentPageIndex * 100 / totalPages).coerceIn(0, 100)
                } else {
                    0
                }
                
                val updatedItem = existingCollection.copy(
                    preview = "阅读进度: 第${currentPageIndex + 1}页/共${totalPages}页 (${progressPercent}%)",
                    extraData = existingCollection.extraData?.toMutableMap()?.apply {
                        put("currentPage", currentPageIndex.toString())
                        put("totalPages", totalPages.toString())
                        put("progressPercent", progressPercent.toString())
                        put("lastReadTime", System.currentTimeMillis().toString())
                    } ?: emptyMap()
                )
                
                collectionManager.updateCollection(updatedItem)
                Log.d(TAG, "更新电子书收藏进度: $fileName, 进度: $progressPercent%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新电子书收藏进度失败", e)
        }
    }
    
    // ==================== 生命周期 ====================
    
    override fun onBackPressed() {
        if (isMenuVisible) {
            hideMenu()
        } else if (isTopBarVisible || isBottomBarVisible) {
            hideAllUI()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 恢复时重新加载阅读进度（防止被系统杀死后丢失）
        if (totalPages > 0) {
            loadReadingProgress()
            displayPage(currentPageIndex.coerceIn(0, totalPages - 1))
            updatePageInfo()
            updateBookmarkButton()
        }
        
        // 自动收藏到AI助手的电子书收藏（首次打开时）
        addToEbookCollection()
    }
    
    override fun onPause() {
        super.onPause()
        stopTTS()
        stopAutoRead()
        // 立即保存阅读进度
        saveReadingProgress()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopTTS()
        stopAutoRead()
        // 最终保存阅读进度
        saveReadingProgress()
        scope.cancel()
        webView.destroy()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                BookmarkManagerActivity.RESULT_BOOKMARK_SELECTED -> {
                    val pageIndex = data.getIntExtra(BookmarkManagerActivity.RESULT_BOOKMARK_PAGE, -1)
                    if (pageIndex >= 0) {
                        displayPage(pageIndex)
                        updatePageInfo()
                        updateBookmarkButton()
                        hideAllUI()
                    }
                }
                HighlightManagerActivity.RESULT_HIGHLIGHT_SELECTED -> {
                    val pageIndex = data.getIntExtra(HighlightManagerActivity.RESULT_HIGHLIGHT_PAGE, -1)
                    if (pageIndex >= 0) {
                        displayPage(pageIndex)
                        updatePageInfo()
                        updateBookmarkButton()
                        hideAllUI()
                    }
                }
                NoteManagerActivity.RESULT_NOTE_SELECTED -> {
                    val pageIndex = data.getIntExtra(NoteManagerActivity.RESULT_NOTE_PAGE, -1)
                    if (pageIndex >= 0) {
                        displayPage(pageIndex)
                        updatePageInfo()
                        updateBookmarkButton()
                        hideAllUI()
                    }
                }
            }
        }
    }
    
    /**
     * 🎯 智能提取作者信息
     * 从TXT文件内容中识别作者，支持多种常见格式
     */
    private fun extractAuthor(text: String): String {
        // 只分析前5000字符，提高性能
        val sampleText = text.take(5000)
        
        // 常见的作者标识模式（按优先级排序）
        val authorPatterns = listOf(
            // 中文格式
            Regex("""作\s*者[：:]\s*([^\n\r]{1,30})"""),           // 作者：XXX
            Regex("""作\s*者[：:]\s*(.+?)(?=\n|\r|$)"""),         // 作者：XXX (到行尾)
            Regex("""著\s*者[：:]\s*([^\n\r]{1,30})"""),           // 著者：XXX
            Regex("""原\s*著[：:]\s*([^\n\r]{1,30})"""),           // 原著：XXX
            Regex("""文\s*/\s*([^\n\r]{1,30})"""),                 // 文/XXX
            Regex("""作\s*者\s+([^\n\r]{1,30})"""),                // 作者 XXX (无冒号)
            
            // 英文格式
            Regex("""(?i)author[:\s]+([^\n\r]{1,50})"""),          // Author: XXX
            Regex("""(?i)by[:\s]+([^\n\r]{1,50})"""),              // By: XXX
            Regex("""(?i)written\s+by[:\s]+([^\n\r]{1,50})"""),    // Written by: XXX
            
            // 特殊格式
            Regex("""【作者】\s*([^\n\r】]{1,30})"""),              // 【作者】XXX
            Regex("""《.+?》\s*作者[：:]\s*([^\n\r]{1,30})"""),    // 《书名》作者：XXX
            Regex("""书\s*名.+?作\s*者[：:]\s*([^\n\r]{1,30})""")  // 书名XXX 作者：XXX
        )
        
        // 尝试匹配每个模式
        for (pattern in authorPatterns) {
            val match = pattern.find(sampleText)
            if (match != null && match.groupValues.size > 1) {
                val author = match.groupValues[1].trim()
                
                // 清理作者名称
                val cleanedAuthor = cleanAuthorName(author)
                
                // 验证作者名称的合理性
                if (isValidAuthorName(cleanedAuthor)) {
                    Log.d(TAG, "通过模式 '${pattern.pattern}' 识别到作者: $cleanedAuthor")
                    return cleanedAuthor
                }
            }
        }
        
        // 如果没有找到，尝试从文件名提取
        val authorFromFileName = extractAuthorFromFileName(fileName)
        if (authorFromFileName.isNotEmpty()) {
            Log.d(TAG, "从文件名识别到作者: $authorFromFileName")
            return authorFromFileName
        }
        
        return ""
    }
    
    /**
     * 清理作者名称
     */
    private fun cleanAuthorName(author: String): String {
        return author
            .replace(Regex("""[\r\n\t]+"""), " ")  // 移除换行和制表符
            .replace(Regex("""\s+"""), " ")         // 合并多个空格
            .replace(Regex("""[【】《》\[\]()（）]+"""), "")  // 移除括号
            .replace(Regex("""^[,，、。.;；:：\s]+"""), "")   // 移除开头的标点
            .replace(Regex("""[,，、。.;；:：\s]+$"""), "")   // 移除结尾的标点
            .trim()
    }
    
    /**
     * 验证作者名称的合理性
     */
    private fun isValidAuthorName(author: String): Boolean {
        if (author.isEmpty()) return false
        if (author.length > 50) return false  // 太长不合理
        if (author.length < 2) return false   // 太短不合理
        
        // 排除一些明显不是作者的内容
        val invalidKeywords = listOf(
            "未知", "佚名", "匿名", "网络", "整理", "收集", "编辑",
            "unknown", "anonymous", "none", "n/a", "null",
            "第一章", "第1章", "chapter", "序言", "前言", "目录"
        )
        
        val lowerAuthor = author.lowercase()
        for (keyword in invalidKeywords) {
            if (lowerAuthor.contains(keyword.lowercase())) {
                return false
            }
        }
        
        // 检查是否包含合理的字符（中文、英文、数字、常见符号）
        val validPattern = Regex("""^[\u4e00-\u9fa5a-zA-Z0-9\s·\-_]+$""")
        return validPattern.matches(author)
    }
    
    /**
     * 从文件名提取作者
     * 支持格式: "书名-作者.txt", "作者-书名.txt", "《书名》作者.txt"
     */
    private fun extractAuthorFromFileName(fileName: String): String {
        // 移除扩展名
        val nameWithoutExt = fileName.substringBeforeLast(".")
        
        // 尝试各种文件名格式
        val patterns = listOf(
            Regex("""^(.+?)[_\-]\s*(.+?)$"""),           // 书名-作者 或 作者-书名
            Regex("""《.+?》\s*(.+?)$"""),                // 《书名》作者
            Regex("""^(.+?)\s*《.+?》$"""),               // 作者《书名》
            Regex("""\[(.+?)\]"""),                       // [作者]
            Regex("""【(.+?)】""")                        // 【作者】
        )
        
        for (pattern in patterns) {
            val match = pattern.find(nameWithoutExt)
            if (match != null && match.groupValues.size > 1) {
                // 对于"书名-作者"格式，尝试两个部分
                if (pattern.pattern.contains("[_\\-]")) {
                    val part1 = cleanAuthorName(match.groupValues[1])
                    val part2 = cleanAuthorName(match.groupValues[2])
                    
                    // 通常较短的是作者名
                    val author = if (part1.length < part2.length) part1 else part2
                    if (isValidAuthorName(author)) {
                        return author
                    }
                } else {
                    val author = cleanAuthorName(match.groupValues[1])
                    if (isValidAuthorName(author)) {
                        return author
                    }
                }
            }
        }
        
        return ""
    }
    
    /**
     * 检测文件编码（改进版，支持更多编码和更准确的检测）
     */
    private fun detectCharset(bytes: ByteArray): Charset {
        // 尝试检测BOM（字节顺序标记）
        if (bytes.size >= 3) {
            // UTF-8 BOM: EF BB BF
            if (bytes[0].toInt() == 0xEF && bytes[1].toInt() == 0xBB && bytes[2].toInt() == 0xBF) {
                Log.d(TAG, "检测到UTF-8 BOM")
                return StandardCharsets.UTF_8
            }
        }
        if (bytes.size >= 2) {
            // UTF-16 LE BOM: FF FE
            if (bytes[0].toInt() == 0xFF && bytes[1].toInt() == 0xFE) {
                Log.d(TAG, "检测到UTF-16LE BOM")
                return Charset.forName("UTF-16LE")
            }
            // UTF-16 BE BOM: FE FF
            if (bytes[0].toInt() == 0xFE && bytes[1].toInt() == 0xFF) {
                Log.d(TAG, "检测到UTF-16BE BOM")
                return Charset.forName("UTF-16BE")
            }
        }
        
        // 尝试常见编码（按优先级，中文编码优先）
        val charsets = listOf(
            Charset.forName("GBK"),           // 中文Windows常用
            Charset.forName("GB2312"),        // 简体中文
            StandardCharsets.UTF_8,            // UTF-8
            Charset.forName("Big5"),           // 繁体中文
            Charset.forName("GB18030"),       // 中文国家标准
            Charset.forName("ISO-8859-1"),    // 西欧
            Charset.forName("Windows-1252"),   // Windows西欧
            StandardCharsets.US_ASCII         // ASCII
        )
        
        // 读取前16KB用于检测（增加样本大小提高准确性）
        val sampleSize = minOf(bytes.size, 16384)
        val sample = bytes.sliceArray(0 until sampleSize)
        
        // 记录每个编码的得分（替换字符越少，得分越高）
        val charsetScores = mutableMapOf<Charset, Int>()
        
        for (charset in charsets) {
            try {
                // 使用REPLACE模式，允许替换字符，然后统计替换字符数量
                val decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                
                val decoded = String(sample, charset)
                
                // 统计替换字符（\uFFFD）的数量
                val replacementCharCount = decoded.count { it == '\uFFFD' }
                val totalChars = decoded.length
                
                // 计算得分：替换字符越少，得分越高
                // 如果替换字符超过5%，认为编码不匹配
                val replacementRatio = if (totalChars > 0) replacementCharCount.toFloat() / totalChars else 1f
                
                if (replacementRatio < 0.05f) { // 替换字符少于5%
                    val score = (1000 * (1 - replacementRatio)).toInt()
                    charsetScores[charset] = score
                    Log.d(TAG, "编码 ${charset.name()} 得分: $score (替换字符比例: ${(replacementRatio * 100).toInt()}%)")
                } else {
                    Log.d(TAG, "编码 ${charset.name()} 替换字符过多: ${(replacementRatio * 100).toInt()}%，跳过")
                }
            } catch (e: Exception) {
                Log.w(TAG, "检测编码 ${charset.name()} 失败", e)
                // 继续尝试下一个编码
                continue
            }
        }
        
        // 选择得分最高的编码
        if (charsetScores.isNotEmpty()) {
            val bestCharset = charsetScores.maxByOrNull { it.value }?.key
            if (bestCharset != null) {
                Log.d(TAG, "检测到最佳编码: ${bestCharset.name()} (得分: ${charsetScores[bestCharset]})")
                return bestCharset
            }
        }
        
        // 如果所有编码都失败，尝试UTF-8（最通用）
        Log.w(TAG, "无法检测编码，尝试UTF-8")
        try {
            val decoded = String(sample, StandardCharsets.UTF_8)
            val replacementRatio = decoded.count { it == '\uFFFD' }.toFloat() / decoded.length
            if (replacementRatio < 0.1f) { // UTF-8允许10%的替换字符（可能是特殊字符）
                Log.d(TAG, "使用UTF-8编码")
                return StandardCharsets.UTF_8
            }
        } catch (e: Exception) {
            Log.w(TAG, "UTF-8解码失败", e)
        }
        
        // 最后尝试GBK（中文文件最常用）
        Log.w(TAG, "所有编码检测失败，默认使用GBK")
        return try {
            Charset.forName("GBK")
        } catch (e: Exception) {
            Log.e(TAG, "GBK编码不可用，使用UTF-8", e)
            StandardCharsets.UTF_8
        }
    }
    
    /**
     * 流式读取大文件（分块读取，避免内存溢出）
     * @param onProgress 进度回调 (已读取字节数, 总字节数)
     */
    private fun readTextFileStreaming(
        inputStream: InputStream, 
        charset: Charset, 
        maxSize: Long = 50 * 1024 * 1024,
        totalSize: Long = 0,
        onProgress: ((Long, Long) -> Unit)? = null
    ): String {
        val buffer = StringBuilder()
        val reader = inputStream.bufferedReader(charset)
        val charBuffer = CharArray(8192) // 8KB缓冲区
        var totalRead = 0L
        var lastProgressUpdate = 0L
        val progressInterval = 1024 * 1024L // 每1MB更新一次进度
        
        try {
            while (true) {
                val bytesRead = reader.read(charBuffer)
                if (bytesRead == -1) break
                
                buffer.append(charBuffer, 0, bytesRead)
                totalRead += bytesRead
                
                // 🚀 优化：定期更新进度
                if (totalRead - lastProgressUpdate >= progressInterval) {
                    onProgress?.invoke(totalRead, totalSize)
                    lastProgressUpdate = totalRead
                }
                
                // 🔧 修复：限制文件大小，避免内存溢出
                if (totalRead > maxSize) {
                    Log.w(TAG, "文件过大（${totalRead}字节），只读取前${maxSize}字节")
                    buffer.append("\n\n[文件过大，已截断显示前${maxSize / 1024 / 1024}MB内容]")
                    break
                }
            }
            
            // 最终进度更新
            onProgress?.invoke(totalRead, totalSize)
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "内存不足，文件过大", e)
            // 如果已经读取了一些内容，返回部分内容
            if (buffer.isNotEmpty()) {
                buffer.append("\n\n[文件过大，内存不足，已截断显示]")
            }
            throw e
        }
        
        return buffer.toString()
    }
    
    // ==================== 统计功能 ====================
    
    /**
     * 更新阅读进度条
     */
    private fun updateReadingProgress() {
        if (totalPages > 0) {
            val progress = ((currentPageIndex + 1) * 100 / totalPages).coerceIn(0, 100)
            readingProgressBar.progress = progress
            readingProgressPercent.text = "$progress%"
        }
    }
    
    /**
     * 显示统计对话框
     */
    private fun showStatsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reading_stats, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // 计算统计数据
        val currentReadingTime = if (readingStartTime > 0) {
            System.currentTimeMillis() - readingStartTime
        } else 0
        
        val totalTime = totalReadingTime + currentReadingTime
        val todayTime = todayReadingTime + currentReadingTime
        
        // 更新UI
        dialogView.findViewById<TextView>(R.id.statsTodayTime).text = formatTime(todayTime)
        dialogView.findViewById<TextView>(R.id.statsTotalTime).text = formatTime(totalTime)
        
        val progress = if (totalPages > 0) ((currentPageIndex + 1) * 100 / totalPages) else 0
        dialogView.findViewById<TextView>(R.id.statsProgressPercent).text = "$progress%"
        dialogView.findViewById<ProgressBar>(R.id.statsProgressBar).progress = progress
        dialogView.findViewById<TextView>(R.id.statsCurrentPage).text = "第 ${currentPageIndex + 1} 页"
        dialogView.findViewById<TextView>(R.id.statsTotalPages).text = "共 $totalPages 页"
        
        // 计算阅读速度（假设每页约500字）
        val charsPerPage = 500
        val readingSpeed = if (totalTime > 0) {
            ((currentPageIndex + 1) * charsPerPage * 60000 / totalTime).toInt()
        } else 350
        dialogView.findViewById<TextView>(R.id.statsReadingSpeed).text = "${readingSpeed}字/分"
        
        // 计算剩余时间
        val remainingPages = totalPages - currentPageIndex - 1
        val remainingTime = if (readingSpeed > 0) {
            remainingPages * charsPerPage * 60 / readingSpeed
        } else 0
        dialogView.findViewById<TextView>(R.id.statsRemainingTime).text = "约${remainingTime}分钟"
        
        dialogView.findViewById<Button>(R.id.statsCloseButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    /**
     * 格式化时间显示
     */
    private fun formatTime(millis: Long): String {
        val minutes = millis / 60000
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}小时${minutes % 60}分"
            minutes > 0 -> "${minutes}分钟"
            else -> "${millis / 1000}秒"
        }
    }
    
    /**
     * 开始计时
     */
    private fun startReadingTimer() {
        readingStartTime = System.currentTimeMillis()
    }
    
    /**
     * 停止计时并保存
     */
    private fun stopReadingTimer() {
        if (readingStartTime > 0) {
            val duration = System.currentTimeMillis() - readingStartTime
            totalReadingTime += duration
            todayReadingTime += duration
            readingStartTime = 0
        }
    }
}

