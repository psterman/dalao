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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import com.example.aifloatingball.R
import com.example.aifloatingball.tts.TTSManager
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
class FileReaderActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "FileReaderActivity"
        private const val EXTRA_FILE_URI = "file_uri"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_FILE_NAME = "file_name"
        
        /**
         * 启动文件阅读器
         */
        fun start(context: Activity, fileUri: Uri, fileName: String? = null) {
            val intent = Intent(context, FileReaderActivity::class.java).apply {
                putExtra(EXTRA_FILE_URI, fileUri.toString())
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
            }
            context.startActivity(intent)
        }
        
        /**
         * 启动文件阅读器（使用文件路径）
         */
        fun startWithPath(context: Activity, filePath: String, fileName: String? = null) {
            val intent = Intent(context, FileReaderActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
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
    
    // 功能菜单
    private lateinit var menuContainer: LinearLayout
    private lateinit var functionMenu: View
    private lateinit var menuCatalog: LinearLayout
    private lateinit var menuBookmark: LinearLayout
    private lateinit var menuHighlight: LinearLayout
    private lateinit var menuNote: LinearLayout
    private lateinit var menuSettings: LinearLayout
    private lateinit var menuTTS: LinearLayout
    private lateinit var menuAutoRead: LinearLayout
    private lateinit var menuShare: LinearLayout
    private lateinit var ttsIcon: ImageView
    private lateinit var autoReadIcon: ImageView
    private lateinit var ttsText: TextView
    private lateinit var autoReadText: TextView
    
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
        
        // 设置全屏模式，但保留状态栏空间（用于顶部信息栏）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        
        setContentView(R.layout.activity_file_reader)
        
        // 初始化数据管理器
        dataManager = ReaderDataManager(this)
        settings = dataManager.getSettings()
        
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
        // 功能菜单
        menuContainer = findViewById(R.id.menuContainer)
        functionMenu = findViewById(R.id.functionMenu)
        menuCatalog = findViewById(R.id.menuCatalog)
        menuBookmark = findViewById(R.id.menuBookmark)
        menuHighlight = findViewById(R.id.menuHighlight)
        menuNote = findViewById(R.id.menuNote)
        menuSettings = findViewById(R.id.menuSettings)
        menuTTS = findViewById(R.id.menuTTS)
        menuAutoRead = findViewById(R.id.menuAutoRead)
        menuShare = findViewById(R.id.menuShare)
        ttsIcon = findViewById(R.id.ttsIcon)
        autoReadIcon = findViewById(R.id.autoReadIcon)
        ttsText = findViewById(R.id.ttsText)
        autoReadText = findViewById(R.id.autoReadText)
        
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
                return true
            }
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // 单击：显示/隐藏菜单
                if (!isMenuVisible) {
                    showMenu()
                } else {
                    hideMenu()
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
        
        webView.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event) ?: false
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
        
        // 功能菜单按钮
        menuCatalog.setOnClickListener { 
            hideMenu()
            showCatalogDialog()
        }
        menuBookmark.setOnClickListener { 
            hideMenu()
            showBookmarkManager()
        }
        menuHighlight.setOnClickListener { 
            hideMenu()
            showHighlightDialog()
        }
        menuNote.setOnClickListener { 
            hideMenu()
            showNoteDialog()
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
        menuShare.setOnClickListener { 
            hideMenu()
            shareContent()
        }
    }
    
    /**
     * JavaScript接口，用于WebView和Android交互
     */
    inner class WebAppInterface {
        @JavascriptInterface
        fun onTextSelected(selectedText: String, startOffset: Int, endOffset: Int) {
            runOnUiThread {
                showTextSelectionDialog(selectedText, startOffset, endOffset)
            }
        }
        
        @JavascriptInterface
        fun onTextSelectedSimple(selectedText: String) {
            runOnUiThread {
                // 简化版本，用于兼容
                showTextSelectionDialog(selectedText, 0, selectedText.length)
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
                
                withContext(Dispatchers.Main) {
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
                    
                    Log.d(TAG, "准备显示页面: currentPageIndex=$currentPageIndex, totalPages=$totalPages")
                    
                    // 显示当前页（可能是上次阅读的位置）
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
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
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
                </style>
            </head>
            <body>
                <div style="white-space: pre-wrap; word-wrap: break-word;">$text</div>
                <script>
                    // 文本选择监听
                    var lastSelection = '';
                    document.addEventListener('selectionchange', function() {
                        var selection = window.getSelection();
                        var selectedText = selection.toString();
                        if (selectedText.length > 0 && selectedText !== lastSelection) {
                            lastSelection = selectedText;
                            var range = selection.getRangeAt(0);
                            var container = range.commonAncestorContainer;
                            
                            // 计算选中文本在页面中的位置
                            var startOffset = 0;
                            var endOffset = 0;
                            
                            // 获取选中文本在body中的位置
                            var walker = document.createTreeWalker(
                                document.body,
                                NodeFilter.SHOW_TEXT,
                                null,
                                false
                            );
                            
                            var node;
                            var foundStart = false;
                            var foundEnd = false;
                            
                            while (node = walker.nextNode()) {
                                if (node === range.startContainer || node.contains(range.startContainer)) {
                                    startOffset = range.startOffset;
                                    if (node !== range.startContainer) {
                                        // 需要计算前面的文本长度
                                        var textBefore = node.textContent.substring(0, range.startOffset);
                                        startOffset = textBefore.length;
                                    }
                                    foundStart = true;
                                }
                                
                                if (node === range.endContainer || node.contains(range.endContainer)) {
                                    endOffset = range.endOffset;
                                    if (node !== range.endContainer) {
                                        var textBefore = node.textContent.substring(0, range.endOffset);
                                        endOffset = textBefore.length;
                                    }
                                    foundEnd = true;
                                    if (foundStart) break;
                                }
                                
                                if (!foundStart && !foundEnd) {
                                    startOffset += node.textContent.length;
                                    endOffset += node.textContent.length;
                                } else if (foundStart && !foundEnd) {
                                    endOffset += node.textContent.length;
                                }
                            }
                            
                            // 调用Android接口
                            if (typeof Android !== 'undefined' && Android.onTextSelected) {
                                Android.onTextSelected(selectedText, startOffset, endOffset);
                            } else if (typeof Android !== 'undefined' && Android.onTextSelectedSimple) {
                                Android.onTextSelectedSimple(selectedText);
                            }
                        }
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
     * 应用高亮（支持不同颜色）
     */
    private fun applyHighlights(text: String, pageIndex: Int): String {
        val highlights = dataManager.getHighlights(filePath)
            .filter { it.pageIndex == pageIndex }
            .sortedByDescending { it.startPosition } // 从后往前处理，避免位置偏移
        
        var result = escapeHtml(text)
        highlights.forEach { highlight ->
            val start = highlight.startPosition
            val end = highlight.endPosition.coerceAtMost(result.length)
            if (start >= 0 && start < result.length && end > start) {
                val before = result.substring(0, start)
                val highlighted = result.substring(start, end)
                val after = result.substring(end)
                // 使用内联样式支持不同颜色
                val color = highlight.color.takeIf { it.isNotEmpty() } ?: "#FFEB3B"
                result = "$before<span style='background-color: $color; padding: 2px 0;'>$highlighted</span>$after"
            }
        }
        
        return result.replace("\n", "<br>")
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
        topInfoBar.visibility = View.VISIBLE
        topInfoBar.alpha = 0f
        topInfoBar.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
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
     * 显示文本选择对话框（底部弹出菜单）
     */
    private fun showTextSelectionDialog(selectedText: String, startOffset: Int, endOffset: Int) {
        if (selectedText.isBlank()) {
            return
        }
        
        // 使用底部弹出菜单，不遮挡视线
        val dialogView = layoutInflater.inflate(R.layout.dialog_text_selection, null)
        
        val selectedTextView = dialogView.findViewById<TextView>(R.id.selectedText)
        val btnHighlight = dialogView.findViewById<android.widget.Button>(R.id.btnHighlight)
        val btnNote = dialogView.findViewById<android.widget.Button>(R.id.btnNote)
        val btnCopy = dialogView.findViewById<android.widget.Button>(R.id.btnCopy)
        val btnShare = dialogView.findViewById<android.widget.Button>(R.id.btnShare)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClose)
        
        // 显示选中的文本（最多显示50个字符）
        val displayText = if (selectedText.length > 50) {
            "${selectedText.take(50)}..."
        } else {
            selectedText
        }
        selectedTextView.text = "\"$displayText\""
        
        btnHighlight.setOnClickListener {
            addHighlight(selectedText, startOffset, endOffset)
            hideTextSelectionDialog()
        }
        
        btnNote.setOnClickListener {
            addNote(selectedText, startOffset)
            hideTextSelectionDialog()
        }
        
        btnCopy.setOnClickListener {
            copyText(selectedText)
            hideTextSelectionDialog()
        }
        
        btnShare.setOnClickListener {
            shareText(selectedText)
            hideTextSelectionDialog()
        }
        
        btnClose?.setOnClickListener {
            hideTextSelectionDialog()
        }
        
        val dialog = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(dialogView)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(android.view.Gravity.BOTTOM)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        
        // 点击背景关闭
        dialogView.findViewById<View>(R.id.dialogBackground)?.setOnClickListener {
            dialog.dismiss()
        }
        
        textSelectionDialog = dialog
        dialog.show()
    }
    
    private var textSelectionDialog: android.app.Dialog? = null
    
    private fun hideTextSelectionDialog() {
        textSelectionDialog?.dismiss()
        textSelectionDialog = null
    }
    
    /**
     * 添加划线
     */
    private fun addHighlight(text: String, startOffset: Int, endOffset: Int) {
        // 在当前页添加划线，使用精确的位置信息
        val pageText = pages[currentPageIndex]
        val actualStart = startOffset.coerceIn(0, pageText.length)
        val actualEnd = endOffset.coerceIn(actualStart, pageText.length)
        
        val highlight = Highlight(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            pageIndex = currentPageIndex,
            startPosition = actualStart,
            endPosition = actualEnd,
            text = text,
            color = "#FFEB3B" // 默认黄色
        )
        dataManager.addHighlight(highlight)
        Toast.makeText(this, "已添加划线", Toast.LENGTH_SHORT).show()
        // 重新显示当前页以应用高亮
        displayPage(currentPageIndex)
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
        val note = Note(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            pageIndex = currentPageIndex,
            position = position,
            text = text,
            noteContent = ""
        )
        dataManager.addNote(note)
        showNoteEditDialog(note)
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
     * 显示设置对话框（实时预览版本）
     */
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reader_settings, null)
        
        // 字体大小滑动条
        val fontSizeSeekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.fontSizeSeekBar)
        val fontSizeText = dialogView.findViewById<TextView>(R.id.fontSizeText)
        fontSizeSeekBar.max = 24 - 12 // 12-24
        fontSizeSeekBar.progress = settings.fontSize - 12
        fontSizeText.text = "${settings.fontSize}px"
        fontSizeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.fontSize = progress + 12
                    fontSizeText.text = "${settings.fontSize}px"
                    // 实时预览
                    pages = paginateText(fullText)
                    totalPages = pages.size
                    displayPage(currentPageIndex.coerceIn(0, totalPages - 1))
                    updatePageInfo()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                dataManager.saveSettings(settings)
            }
        })
        
        // 行距滑动条
        val lineHeightSeekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.lineHeightSeekBar)
        val lineHeightText = dialogView.findViewById<TextView>(R.id.lineHeightText)
        lineHeightSeekBar.max = 8 // 1.2-2.0，步长0.1
        lineHeightSeekBar.progress = ((settings.lineHeight - 1.2f) * 10).toInt()
        lineHeightText.text = String.format("%.1f", settings.lineHeight)
        lineHeightSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.lineHeight = 1.2f + progress * 0.1f
                    lineHeightText.text = String.format("%.1f", settings.lineHeight)
                    // 实时预览
                    displayPage(currentPageIndex)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                dataManager.saveSettings(settings)
            }
        })
        
        // 边距滑动条
        val marginSeekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.marginSeekBar)
        val marginText = dialogView.findViewById<TextView>(R.id.marginText)
        marginSeekBar.max = 20 // 10-30，步长1
        marginSeekBar.progress = settings.marginHorizontal - 10
        marginText.text = "${settings.marginHorizontal}dp"
        marginSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.marginHorizontal = progress + 10
                    settings.marginVertical = progress + 10
                    marginText.text = "${settings.marginHorizontal}dp"
                    // 实时预览
                    pages = paginateText(fullText)
                    totalPages = pages.size
                    displayPage(currentPageIndex.coerceIn(0, totalPages - 1))
                    updatePageInfo()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                dataManager.saveSettings(settings)
            }
        })
        
        // 主题选择
        val themeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.themeGroup)
        when (settings.theme) {
            ReaderTheme.LIGHT -> themeGroup.check(R.id.themeLight)
            ReaderTheme.DARK -> themeGroup.check(R.id.themeDark)
            ReaderTheme.SEPIA -> themeGroup.check(R.id.themeSepia)
            ReaderTheme.GREEN -> themeGroup.check(R.id.themeGreen)
        }
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            settings.theme = when (checkedId) {
                R.id.themeLight -> ReaderTheme.LIGHT
                R.id.themeDark -> ReaderTheme.DARK
                R.id.themeSepia -> ReaderTheme.SEPIA
                R.id.themeGreen -> ReaderTheme.GREEN
                else -> ReaderTheme.LIGHT
            }
            dataManager.saveSettings(settings)
            // 更新系统UI（状态栏文字颜色）
            setupSystemUI()
            updateMenuTheme()
            // 实时预览主题变化
            displayPage(currentPageIndex)
        }
        
        AlertDialog.Builder(this)
            .setTitle("阅读设置")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, _ ->
                dataManager.saveSettings(settings)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
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
}

