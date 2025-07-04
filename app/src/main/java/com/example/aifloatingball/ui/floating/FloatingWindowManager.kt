package com.example.aifloatingball.ui.floating

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import android.widget.ImageView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.ui.webview.CustomWebView
import com.example.aifloatingball.utils.EngineUtil
import kotlin.math.abs
import com.bumptech.glide.Glide
import com.example.aifloatingball.manager.ChatManager
import android.util.Log
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.FaviconLoader
import android.widget.HorizontalScrollView
import com.example.aifloatingball.ui.text.TextSelectionManager
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import com.example.aifloatingball.ui.floating.KeyEventInterceptorView
import com.example.aifloatingball.ui.webview.WebViewManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.view.ContextThemeWrapper
import android.content.res.Configuration

interface WindowStateCallback {
    fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int)
}

/**
 * 浮动窗口管理器，负责创建和管理浮动窗口
 */
class FloatingWindowManager(
    private val context: Context,
    private val service: DualFloatingWebViewService,
    private val windowStateCallback: WindowStateCallback,
    private val textSelectionManager: TextSelectionManager
) : KeyEventInterceptorView.BackPressListener {
    private var windowManager: WindowManager? = null
    private var _floatingView: View? = null
    val floatingView: View?
        get() = _floatingView
    var params: WindowManager.LayoutParams? = null
        private set  // 设置为私有set，只允许通过特定方法修改
    
    private var webViewContainer: LinearLayout? = null
    private var webviewsScrollContainer: HorizontalScrollView? = null
    
    private var firstWebView: CustomWebView? = null
    private var secondWebView: CustomWebView? = null
    private var thirdWebView: CustomWebView? = null
    internal var searchInput: EditText? = null

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0.toFloat()
    private var initialTouchY: Float = 0.toFloat()
    private var initialWidth: Int = 0
    private var initialHeight: Int = 0

    private var isDragging = false
    private var isResizing = false
    private var lastDragX: Float = 0f
    private var lastDragY: Float = 0f
    private var lastActiveWebViewIndex = 0 // 追踪当前活动的WebView
    
    private var originalWindowHeight: Int = 0
    private var originalWindowY: Int = 0
    private var isKeyboardShowing: Boolean = false

    // 新增：屏幕方向相关变量
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    // 新增：窗口边界限制常量
    companion object {
        // 控制条的高度 (根据布局文件定义)
        private const val TOP_DRAG_HANDLE_HEIGHT_DP = 24
        private const val BOTTOM_RESIZE_HANDLE_HEIGHT_DP = 16
        private const val TOP_CONTROL_BAR_HEIGHT_DP = 56 // 估算的控制栏高度
        
        // 安全边距，完全移除边距以最大化利用屏幕空间
        private const val SAFE_MARGIN_DP = 0
        
        // 最小窗口尺寸 (dp)
        private const val MIN_WINDOW_WIDTH_DP = 300
        private const val MIN_WINDOW_HEIGHT_DP = 200
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(DualFloatingWebViewService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 定义搜索引擎键列表
    private val standardEngineKeys = listOf("baidu", "google", "bing", "sogou", "360", "quark", "toutiao", "zhihu", "bilibili", "douban", "weibo", "taobao", "jd", "douyin", "xiaohongshu")

    // 获取启用的AI搜索引擎列表
    private fun getEnabledAIEngineKeys(): List<String> {
        val allAIEngineKeys = AISearchEngine.DEFAULT_AI_ENGINES.map { it.name }
        val settingsManager = SettingsManager.getInstance(context)
        val enabledAIEngines: Set<String> = settingsManager.getEnabledAIEngines()
        
        // 如果没有启用任何AI引擎，则默认显示所有AI引擎
        if (enabledAIEngines.isEmpty()) {
            return allAIEngineKeys
        }

        // 直接返回已启用的key，因为保存的就是key
        return enabledAIEngines.toList().filter { it in allAIEngineKeys }
    }
    
    // 粘贴按钮相关
    private var pasteButton: View? = null
    private val pasteButtonHandler = Handler(Looper.getMainLooper())
    private var hidePasteButtonRunnable: Runnable? = null
    
    init {
        initializeWindowManager()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }
    
    /**
     * 计算安全的窗口边界，确保上下控制条始终可见和可操作
     */
    private fun calculateSafeWindowBounds(displayMetrics: android.util.DisplayMetrics, statusBarHeight: Int): WindowBounds {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 转换dp到px
        val topHandleHeight = dpToPx(TOP_DRAG_HANDLE_HEIGHT_DP)
        val bottomHandleHeight = dpToPx(BOTTOM_RESIZE_HANDLE_HEIGHT_DP)
        val topControlBarHeight = dpToPx(TOP_CONTROL_BAR_HEIGHT_DP)
        val safeMargin = dpToPx(SAFE_MARGIN_DP)
        
        // 计算安全区域
        val minY = statusBarHeight + safeMargin // 顶部：状态栏 + 安全边距
        val maxY = screenHeight - bottomHandleHeight - safeMargin // 底部：确保底部控制条可见
        
        // 最小和最大窗口尺寸
        val minWidth = dpToPx(MIN_WINDOW_WIDTH_DP)
        val minHeight = dpToPx(MIN_WINDOW_HEIGHT_DP)
        val maxWidth = screenWidth - safeMargin * 2
        val maxHeight = maxY - minY
        
        return WindowBounds(
            minX = 0, // 允许窗口左边缘与屏幕左边缘相切
            maxX = screenWidth, // 允许窗口右边缘与屏幕右边缘相切
            minY = minY,
            maxY = maxY,
            minWidth = minWidth,
            maxWidth = screenWidth, // 允许窗口占满整个屏幕宽度
            minHeight = minHeight,
            maxHeight = maxHeight
        )
    }
    
    /**
     * 窗口边界数据类
     */
    private data class WindowBounds(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minWidth: Int,
        val maxWidth: Int,
        val minHeight: Int,
        val maxHeight: Int
    )
    
    // 获取状态栏高度
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    /**
     * 根据屏幕方向计算最优窗口尺寸
     * 横屏和竖屏使用不同的比例策略，并确保上下控制条可见
     */
    private fun calculateOptimalWindowSize(displayMetrics: android.util.DisplayMetrics, statusBarHeight: Int): Pair<Int, Int> {
        val safeBounds = calculateSafeWindowBounds(displayMetrics, statusBarHeight)
        val orientation = context.resources.configuration.orientation
        
        val (widthRatio, heightRatio) = when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // 横屏模式：最大化利用屏幕空间
                Pair(0.95f, 0.8f)
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                // 竖屏模式：最大化利用屏幕空间
                Pair(1.0f, 0.75f)
            }
            else -> {
                // 未知方向，使用较大尺寸
                Pair(0.9f, 0.7f)
            }
        }
        
        // 根据比例计算理想尺寸
        val idealWidth = (safeBounds.maxWidth * widthRatio).toInt()
        val idealHeight = (safeBounds.maxHeight * heightRatio).toInt()
        
        // 应用安全边界限制
        val safeWidth = idealWidth.coerceIn(safeBounds.minWidth, safeBounds.maxWidth)
        val safeHeight = idealHeight.coerceIn(safeBounds.minHeight, safeBounds.maxHeight)
        
        val orientationText = when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "横屏"
            Configuration.ORIENTATION_PORTRAIT -> "竖屏"
            else -> "未知"
        }
        
        Log.d("FloatingWindowManager", "${orientationText}模式安全窗口尺寸: ${safeWidth}x${safeHeight} (${(widthRatio*100).toInt()}%x${(heightRatio*100).toInt()}%), 边界: Y=${safeBounds.minY}-${safeBounds.maxY}")
        
        return Pair(safeWidth, safeHeight)
    }

    private fun initializeWindowManager() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = context.resources.displayMetrics
        val statusBarHeight = getStatusBarHeight() // 获取状态栏高度

        // 记录当前屏幕信息和方向
        currentOrientation = context.resources.configuration.orientation
        lastScreenWidth = displayMetrics.widthPixels
        lastScreenHeight = displayMetrics.heightPixels
        Log.d("FloatingWindowManager", "初始化屏幕信息: 宽度=$lastScreenWidth, 高度=$lastScreenHeight, 方向=$currentOrientation")

        // 使用适配屏幕方向的窗口尺寸比例
        val (defaultWidth, defaultHeight) = calculateOptimalWindowSize(displayMetrics, statusBarHeight)
        
        // 位置：左边缘与屏幕左边缘相切，上边缘尽可能接近状态栏
        val defaultX = 0
        val defaultY = statusBarHeight 

        // 强制使用新的最大化设置，忽略保存的旧数据
        val savedX = defaultX // 始终使用左边缘贴屏幕
        val savedY = defaultY // 始终使用上边缘贴状态栏
        val savedWidth = defaultWidth // 始终使用最大化宽度
        val savedHeight = defaultHeight // 始终使用最大化高度
        
        Log.d("FloatingWindowManager", "强制使用最大化设置: ${savedWidth}x${savedHeight} at ($savedX, $savedY)")

        params = WindowManager.LayoutParams(
            savedWidth,
            savedHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            // 确保DualFloatingWebViewService显示在最上层
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
        }

        originalWindowHeight = savedHeight
        originalWindowY = savedY
        Log.d("FloatingWindowManager", "初始 originalWindowHeight: $originalWindowHeight, originalWindowY: $originalWindowY")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingWindow(): List<CustomWebView?> {
        val TAG = "FloatingWindowManager"
        Log.d(TAG, "FloatingWindowManager: 创建浮动窗口 createFloatingWindow()")
        
        // 初始化窗口管理器
        initializeWindowManager()
        
        val inflater = LayoutInflater.from(context)
        _floatingView = inflater.inflate(R.layout.layout_dual_floating_webview, null)
        
        webViewContainer = _floatingView?.findViewById(R.id.dual_webview_container)
        webviewsScrollContainer = _floatingView?.findViewById(R.id.webviews_scroll_container)
        firstWebView = _floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = _floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = _floatingView?.findViewById(R.id.third_floating_webview)
        
        searchInput = _floatingView?.findViewById<EditText>(R.id.dual_search_input)
        
        val saveEnginesButton = _floatingView?.findViewById<ImageButton>(R.id.btn_save_engines)
        val windowCountButton = _floatingView?.findViewById<ImageButton>(R.id.btn_window_count)
        val windowCountToggleText = _floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)
        val closeButton = _floatingView?.findViewById<ImageButton>(R.id.btn_dual_close)
        val bottomResizeHandle = _floatingView?.findViewById<View>(R.id.dual_resize_handle)
        val topResizeHandle = _floatingView?.findViewById<View>(R.id.top_drag_handle)
        val topControlBar = _floatingView?.findViewById<LinearLayout>(R.id.top_control_bar)

        // 新增：获取全局AI引擎容器
        val globalAiContainer = _floatingView?.findViewById<LinearLayout>(R.id.global_ai_engine_container)
        // 新增：获取全局标准引擎容器
        val globalStdContainer = _floatingView?.findViewById<LinearLayout>(R.id.global_standard_engine_container)

        val initialCount = service.getCurrentWindowCount()
        windowCountToggleText?.text = initialCount.toString()
        android.util.Log.d("FloatingWindowManager", "初始化窗口数量显示为: $initialCount")

        closeButton?.setOnClickListener {
            service.stopSelf()
        }

        searchInput?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                // 修正：通过发送 Intent 的方式来触发搜索
                val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
                    putExtra("search_query", query)
                    putExtra("engine_key", "google") // 使用默认搜索引擎
                    putExtra("search_source", "悬浮窗")
                }
                context.startService(intent)
                true
            } else {
                false
            }
        }

        // 添加焦点变化监听器，确保输入框焦点管理正确
        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 获得焦点时，确保窗口可获取焦点
                updateWindowFocusability(true)
                
                // 临时禁用WebView的焦点能力，防止抢夺焦点
                val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
                webViews.forEach { webView ->
                    webView.isFocusable = false
                    webView.isFocusableInTouchMode = false
                }
                
                // 延迟恢复WebView焦点能力
                Handler(Looper.getMainLooper()).postDelayed({
                    webViews.forEach { webView ->
                        webView.isFocusable = true
                        webView.isFocusableInTouchMode = true
                    }
                }, 500) // 延长到500ms，给输入法更多时间稳定
                
                Log.d(TAG, "SearchInput gained focus, window made focusable, WebView focus temporarily disabled")
            } else {
                // 失去焦点时，隐藏键盘并恢复窗口焦点设置
                hideKeyboard()
                updateWindowFocusability(false)
                Log.d(TAG, "SearchInput lost focus, keyboard hidden, window made non-focusable")
            }
        }

        searchInput?.setOnLongClickListener {
            textSelectionManager.showEditTextSelectionMenu(it as EditText)
            true
        }

        // 添加点击监听器，作为焦点获取的额外保障
        searchInput?.setOnClickListener { view ->
            Log.d(TAG, "SearchInput clicked, trying immediate focus")
            
            // 立即获取焦点
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            view.requestFocusFromTouch()
            
            // 确保窗口可获取焦点
            updateWindowFocusability(true)
            
            // 立即显示键盘
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
            
            // 检查焦点是否成功获取，如果失败则使用超强力模式
            Handler(Looper.getMainLooper()).postDelayed({
                if (!view.isFocused) {
                    Log.w(TAG, "Normal focus failed, switching to super aggressive mode")
                    forceInputFocusActivation()
                } else {
                    Log.d(TAG, "Normal focus succeeded")
                }
            }, 200)
        }

        saveEnginesButton?.setOnClickListener {
            val query = searchInput?.text.toString()
             // 修正：通过发送 Intent 的方式来触发搜索
            val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("engine_key", "google") // 使用默认搜索引擎
                putExtra("search_source", "悬浮窗")
            }
            context.startService(intent)
        }

        windowCountButton?.setOnClickListener {
            val newCount = service.toggleAndReloadWindowCount()
            windowCountToggleText?.text = newCount.toString()
        }

        setupTopResizeHandle(topResizeHandle)
        setupBottomResizeHandle(bottomResizeHandle)

        // 方案核心：手动实现顶部栏的拖动，以避免事件冲突
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        topControlBar?.setOnTouchListener { _, event ->
            val x = event.rawX
            val y = event.rawY
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = x
                    initialTouchY = y
                    lastDragX = x
                    lastDragY = y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(x - initialTouchX)
                    val dy = abs(y - initialTouchY)
                    // 只有在已经开始拖动，或者移动距离超过阈值时才更新
                    if (isDragging || dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        val newX = initialX + (x - initialTouchX).toInt()
                        val newY = initialY + (y - initialTouchY).toInt()
                        
                        // 应用安全边界限制
                        val displayMetrics = context.resources.displayMetrics
                        val statusBarHeight = getStatusBarHeight()
                        val safeBounds = calculateSafeWindowBounds(displayMetrics, statusBarHeight)
                        
                        params?.let { p ->
                            p.x = newX.coerceIn(safeBounds.minX, safeBounds.maxX - p.width)
                            p.y = newY.coerceIn(safeBounds.minY, safeBounds.maxY - p.height)
                            windowManager?.updateViewLayout(_floatingView, p)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        windowStateCallback.onWindowStateChanged(params?.x ?: 0, params?.y ?: 0, params?.width ?: 0, params?.height ?: 0)
                    }
                    isDragging = false
                }
            }
            // 返回 isDragging，当且仅当正在拖动时才消费事件，否则让事件传递给子视图（按钮）
            return@setOnTouchListener isDragging
        }

        // 新增：设置返回键监听器
        (_floatingView as? KeyEventInterceptorView)?.backPressListener = this

        // 新增：填充全局AI搜索引擎栏
        populateGlobalAIEngineIcons(globalAiContainer)
        // 新增：填充全局标准搜索引擎栏
        populateGlobalStandardEngineIcons(globalStdContainer)

        setupKeyboardManagement()
        setupWebViewFocusManagementOnScroll()
        
        try {
            windowManager?.addView(_floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window view", e)
        }

        return listOf(firstWebView, secondWebView, thirdWebView)
    }

    private fun determineActiveWebViewIndex(): Int {
        val container = webviewsScrollContainer ?: return lastActiveWebViewIndex

        // 获取每个WebView容器的宽度（假设它们是固定的320dp）
        val webViewWidthPx = dpToPx(320)
        if (webViewWidthPx == 0) return lastActiveWebViewIndex

        // 计算可见区域的中心点
        val scrollX = container.scrollX
        val containerWidth = container.width
        val visibleCenter = scrollX + containerWidth / 2

        // 计算哪个WebView的中心最接近可见区域的中心
        // (visibleCenter / webViewWidthPx) 会直接给出粗略的索引
        val activeIndex = (visibleCenter / webViewWidthPx).coerceIn(0, 2)

        Log.d("FloatingWindowManager", "Determined active WebView index: $activeIndex (ScrollX: $scrollX, Center: $visibleCenter)")
        lastActiveWebViewIndex = activeIndex
        return activeIndex
    }

    private fun populateGlobalAIEngineIcons(container: LinearLayout?) {
        container ?: return
        container.removeAllViews()
        val settingsManager = SettingsManager.getInstance(context)
        val enabledAIEngineKeys = settingsManager.getEnabledAIEngines()

        val enabledAIEngines = AISearchEngine.DEFAULT_AI_ENGINES.filter {
            enabledAIEngineKeys.contains(it.name)
        }

        for (engine in enabledAIEngines) {
            val iconView = createIconView(engine.name) {
                // 在当前活动的WebView中执行AI搜索
                val query = searchInput?.text.toString()
                val activeIndex = determineActiveWebViewIndex()
                service.performSearchInWebView(activeIndex, query, engine.name)
            }
            FaviconLoader.loadIcon(iconView, engine.url, engine.iconResId)
            container.addView(iconView)
        }
    }

    private fun populateGlobalStandardEngineIcons(container: LinearLayout?) {
        container ?: return
        container.removeAllViews()
        val settingsManager = SettingsManager.getInstance(context)
        val enabledEngineKeys = settingsManager.getEnabledSearchEngines()

        val enabledEngines = SearchEngine.DEFAULT_ENGINES.filter {
            enabledEngineKeys.contains(it.name)
        }

        for (engine in enabledEngines) {
            val iconView = createIconView(engine.name) {
                // 在当前活动的WebView中执行标准搜索
                val query = searchInput?.text?.toString() ?: ""
                val activeIndex = determineActiveWebViewIndex()
                service.performSearchInWebView(activeIndex, query, engine.name)
            }
            FaviconLoader.loadIcon(iconView, engine.url, engine.iconResId)
            container.addView(iconView)
        }
    }

    private fun populateEngineIconsForWebView(webViewIndex: Int, container: LinearLayout?, searchInput: EditText?) {
        container ?: return
        container.removeAllViews()
        val settingsManager = SettingsManager.getInstance(context)
        val enabledEngineKeys = settingsManager.getEnabledSearchEngines()

        val enabledEngines = SearchEngine.DEFAULT_ENGINES.filter {
            enabledEngineKeys.contains(it.name)
        }

        for (engine in enabledEngines) {
            val iconView = createIconView(engine.name) {
                val query = searchInput?.text?.toString() ?: ""
                service.performSearchInWebView(webViewIndex, query, engine.name)
            }
            FaviconLoader.loadIcon(iconView, engine.url, engine.iconResId)
            container.addView(iconView)
        }
    }

    private fun createIconView(engineKey: String, onClick: () -> Unit): ImageView {
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(36), dpToPx(36)).also {
                it.setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            scaleType = ScaleType.CENTER_CROP
            contentDescription = engineKey
            setOnClickListener { onClick() }
        }
    }

    private fun getDomainFromEngineKey(engineKey: String): String {
        // This logic is derived from EngineUtil.getEngineIconUrl's internal domain resolution
        return when(engineKey) {
            "baidu" -> "baidu.com"
            "google" -> "google.com"
            "bing" -> "bing.com"
            "sogou" -> "sogou.com"
            "360" -> "so.com"
            "quark" -> "sm.cn"
            "toutiao" -> "toutiao.com"
            "zhihu" -> "zhihu.com"
            "bilibili" -> "bilibili.com"
            "douban" -> "douban.com"
            "weibo" -> "weibo.com"
            "taobao" -> "taobao.com"
            "jd" -> "jd.com"
            "douyin" -> "douyin.com"
            "xiaohongshu" -> "xiaohongshu.com"
            // AI Engines
            "chatgpt_chat" -> "openai.com"
            "claude" -> "claude.ai"
            "gemini" -> "gemini.google.com"
            "wenxin" -> "baidu.com" // Wenxin is under baidu.com domain for icons
            "chatglm" -> "zhipuai.cn"
            "qianwen" -> "aliyun.com" // Tongyi Qianwen under aliyun.com
            "xinghuo" -> "xfyun.cn"   // Xunfei Xinghuo
            "perplexity" -> "perplexity.ai"
            "phind" -> "phind.com"
            "poe" -> "poe.com"
            "deepseek_chat" -> "deepseek.com"
            else -> "google.com" // Default domain if key is unknown
        }
    }

    private fun handleTopResize(event: MotionEvent) {
        val params = this.params ?: return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialY = params.y
                initialWidth = params.width
                initialHeight = params.height
                isResizing = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val newWidth = initialWidth + deltaX.toInt()
                    val newHeight = initialHeight - deltaY.toInt()
                    val newY = initialY + deltaY.toInt()

                    // 应用安全边界限制
                    val displayMetrics = context.resources.displayMetrics
                    val statusBarHeight = getStatusBarHeight()
                    val safeBounds = calculateSafeWindowBounds(displayMetrics, statusBarHeight)

                    var update = false
                    
                    // 检查宽度
                    val safeWidth = newWidth.coerceIn(safeBounds.minWidth, safeBounds.maxWidth)
                    if (safeWidth != params.width) {
                        params.width = safeWidth
                        update = true
                    }
                    
                    // 检查高度和位置
                    val safeHeight = newHeight.coerceIn(safeBounds.minHeight, safeBounds.maxHeight)
                    val safeY = newY.coerceIn(safeBounds.minY, safeBounds.maxY - safeHeight)
                    if (safeHeight != params.height || safeY != params.y) {
                        params.height = safeHeight
                        params.y = safeY
                        update = true
                    }
                    
                    if (update) windowManager?.updateViewLayout(_floatingView, params)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isResizing) {
                    isResizing = false
                    windowStateCallback.onWindowStateChanged(params.x, params.y, params.width, params.height)
                }
            }
        }
    }

    private fun handleBottomResize(event: MotionEvent) {
        val params = this.params ?: return
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialWidth = params.width
                initialHeight = params.height
                isResizing = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isResizing) {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val newWidth = initialWidth + deltaX.toInt()
                    val newHeight = initialHeight + deltaY.toInt()

                    // 应用安全边界限制
                    val displayMetrics = context.resources.displayMetrics
                    val statusBarHeight = getStatusBarHeight()
                    val safeBounds = calculateSafeWindowBounds(displayMetrics, statusBarHeight)

                    var update = false
                    
                    // 检查宽度
                    val safeWidth = newWidth.coerceIn(safeBounds.minWidth, safeBounds.maxWidth)
                    if (safeWidth != params.width) {
                        params.width = safeWidth
                        update = true
                    }
                    
                    // 检查高度，确保不超出底部边界
                    val maxAllowedHeight = safeBounds.maxY - params.y + safeBounds.minHeight
                    val safeHeight = newHeight.coerceIn(safeBounds.minHeight, maxAllowedHeight)
                    if (safeHeight != params.height) {
                        params.height = safeHeight
                        update = true
                    }
                    
                    if (update) windowManager?.updateViewLayout(_floatingView, params)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isResizing) {
                    isResizing = false
                    windowStateCallback.onWindowStateChanged(params.x, params.y, params.width, params.height)
                }
            }
        }
    }

    /**
     * 刷新所有WebView的搜索引擎图标
     */
    fun refreshEngineIcons() {
        Log.d("FloatingWindowManager", "刷新搜索引擎图标...")
        // 更新为刷新全局图标栏
        val globalAiContainer = _floatingView?.findViewById<LinearLayout>(R.id.global_ai_engine_container)
        populateGlobalAIEngineIcons(globalAiContainer)

        val globalStdContainer = _floatingView?.findViewById<LinearLayout>(R.id.global_standard_engine_container)
        populateGlobalStandardEngineIcons(globalStdContainer)
        Log.d("FloatingWindowManager", "搜索引擎图标刷新完毕。")
    }
    
    fun removeFloatingWindow() {
        val TAG = "FloatingWindowManager"
        Log.d(TAG, "FloatingWindowManager: 移除浮动窗口 removeFloatingWindow()")
        _floatingView?.let {
            try {
                 windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("FloatingWindowManager", "Error removing view: ${e.message}")
            }
        }
        _floatingView = null
        windowManager = null
    }

    /**
     * 设置浮动窗口的焦点状态和软键盘模式。
     * 这是修改窗口焦点和输入法行为的唯一入口。
     */
    fun setFloatingWindowFocusable(focusable: Boolean) {
        val TAG = "FloatingWindowManager"
        params?.let { p ->
            if (focusable) {
                // 移除阻止获取焦点的标志、备用输入法焦点标志和不可触摸标志
                p.flags = p.flags and (
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                ).inv()
                // 允许输入法调整窗口大小
                p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                // 当输入框失去焦点时，我们只希望隐藏键盘，而不是让整个窗口变得不可交互。
                // 移除会阻止返回键和WebView触摸事件的标志。
                p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            }
            
            try {
                _floatingView?.let { fv ->
                    windowManager?.updateViewLayout(fv, p)
                    android.util.Log.d(TAG, "窗口焦点状态更新: focusable=$focusable, softInputMode=${p.softInputMode}, flags=${p.flags}")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "更新窗口布局失败 (setFloatingWindowFocusable)", e)
            }
        }
    }

    fun updateViewLayout(view: View?, layoutParams: WindowManager.LayoutParams?) {
        val TAG = "FloatingWindowManager"
        if (view != null && layoutParams != null) {
            try {
                params = layoutParams  // 更新内部params
                windowManager?.updateViewLayout(view, layoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局失败: ${e.message}")
            }
        }
    }

    // 更新窗口Y坐标
    fun updateWindowY(y: Int) {
        params?.let { p ->
            p.y = y
            updateViewLayout(floatingView, p)
        }
    }

    // 获取当前窗口Y坐标
    fun getWindowY(): Int = params?.y ?: 0

    /**
     * 获取搜索输入框的文本
     */
    fun getSearchInputText(): String {
        return searchInput?.text?.toString()?.trim() ?: ""
    }

    fun getWebView(position: Int): CustomWebView? {
        return when (position) {
            0 -> firstWebView
            1 -> secondWebView
            2 -> thirdWebView
            else -> null
        }
    }

    /**
     * 设置键盘管理，用于在点击WebView时隐藏键盘
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardManagement() {
        val TAG = "FloatingWindowManager"
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        val scrollContainer = _floatingView?.findViewById<HorizontalScrollView>(R.id.webviews_scroll_container)

        // 记录用户最后点击的区域
        var lastClickedArea: String = "none"
        var lastClickTime: Long = 0
        
        // 添加输入框触摸监听器，记录用户点击意图
        searchInput?.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastClickedArea = "search_input"
                lastClickTime = System.currentTimeMillis()
                Log.d(TAG, "User clicked on search input - priority focus area")
                
                // 通知WebView暂时不允许页面焦点
                sendFocusControlToWebViews(false, "search_input_clicked")
                
                // 用户明确点击了搜索输入框，立即获取焦点
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.requestFocus()
                view.requestFocusFromTouch()
                
                // 确保窗口可获取焦点
                updateWindowFocusability(true)
                
                // 立即显示键盘
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                
                // 延迟检查并加强焦点保护
                Handler(Looper.getMainLooper()).postDelayed({
                    if (lastClickedArea == "search_input" && 
                        System.currentTimeMillis() - lastClickTime < 1000) {
                        // 用户最近点击的是搜索框，强制保持焦点
                        if (!view.isFocused) {
                            Log.d(TAG, "Enforcing search input focus based on user intent")
                            view.requestFocus()
                            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                        }
                    }
                }, 100)
            }
            false // 不消费事件，让正常的输入处理继续
        }

        // WebView触摸监听器 - 基于用户点击意图
        val webViewTouchListener = View.OnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastClickedArea = "webview"
                lastClickTime = System.currentTimeMillis()
                Log.d(TAG, "User clicked on WebView - allowing page focus")
                
                // 通知WebView现在允许页面焦点
                sendFocusControlToWebViews(true, "webview_clicked")
                
                // 用户点击了WebView区域，说明想与页面交互
                // 只有当用户之前焦点在搜索框时才清除搜索框焦点
                if (searchInput?.isFocused == true) {
                    Log.d(TAG, "User switched from search input to WebView interaction")
                    searchInput?.clearFocus()
                    updateWindowFocusability(false)
                }
                
                // 记录哪个WebView被触摸了
                val touchedIndex = webViews.indexOf(view)
                if (touchedIndex != -1) {
                    lastActiveWebViewIndex = touchedIndex
                    Log.d(TAG, "Active WebView set to index: $lastActiveWebViewIndex")
                }
                
                // 确保WebView可以获取焦点进行正常交互
                webViews.forEach { webView ->
                    webView.isFocusable = true
                    webView.isFocusableInTouchMode = true
                }
            }
            false // 不消费事件，允许WebView正常处理
        }

        // 将监听器应用于所有WebView
        webViews.forEach { webView ->
            webView.setOnTouchListener(webViewTouchListener)
        }
        
        // 滚动容器触摸监听器
        scrollContainer?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastClickedArea = "scroll_container"
                lastClickTime = System.currentTimeMillis()
                Log.d(TAG, "User touched scroll container")
                
                // 用户在滚动，允许正常的页面交互
                sendFocusControlToWebViews(true, "container_scroll")
                
                // 用户在滚动，如果搜索框有焦点则清除
                if (searchInput?.isFocused == true) {
                    searchInput?.clearFocus()
                    updateWindowFocusability(false)
                }
            }
            false
        }

        // 添加输入框文本变化监听器，确保输入期间焦点稳定
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // 用户正在输入，确保搜索框保持焦点
                if (lastClickedArea == "search_input" && 
                    System.currentTimeMillis() - lastClickTime < 5000) { // 5秒内的输入被认为是有意的
                    updateWindowFocusability(true)
                    // 继续告知WebView保持焦点限制
                    sendFocusControlToWebViews(false, "search_input_typing")
                }
            }
        })
    }

    /**
     * 新增：设置滚动时对WebView焦点的管理，以防止内部输入框自动激活
     */
    private fun setupWebViewFocusManagementOnScroll() {
        val TAG = "FloatingWindowManager"
        val scrollContainer = _floatingView?.findViewById<HorizontalScrollView>(R.id.webviews_scroll_container)
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        val handler = Handler(Looper.getMainLooper())
        var scrollStopRunnable: Runnable? = null
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        scrollContainer?.viewTreeObserver?.addOnScrollChangedListener {
            // 当滚动开始时，立即禁用所有WebView的焦点并隐藏键盘
            if (imm.isActive) {
                imm.hideSoftInputFromWindow(scrollContainer.windowToken, 0)
            }
            if (webViews.any { it.isFocusable }) {
                Log.d(TAG, "Scroll detected, disabling WebView focus.")
                webViews.forEach {
                    it.isFocusable = false
                    it.isFocusableInTouchMode = false
                }
            }

            // 移除上一个等待执行的"滚动停止"任务
            scrollStopRunnable?.let { handler.removeCallbacks(it) }

            // 设置一个新的"滚动停止"任务
            scrollStopRunnable = Runnable {
                Log.d(TAG, "Scroll stopped, re-enabling WebView focus.")
                // 滚动停止后，重新启用所有WebView的焦点
                webViews.forEach {
                    it.isFocusable = true
                    it.isFocusableInTouchMode = true
                }
            }
            // 250毫秒后执行，如果期间没有新的滚动事件
            handler.postDelayed(scrollStopRunnable!!, 250)
        }
    }

    /**
     * 实现 BackPressListener 接口来处理返回键事件
     */
    override fun onBackButtonPressed(): Boolean {
        val TAG = "FloatingWindowManager"
        val webViews = getXmlDefinedWebViews()
        val activeWebView = webViews.getOrNull(lastActiveWebViewIndex)

        // 检查当前活动的WebView是否可以后退
        activeWebView?.let {
            if (it.canGoBack()) {
                Log.d(TAG, "Back press handled by WebView index $lastActiveWebViewIndex")
                it.goBack()
                return true // 事件已处理
            }
        }

        // 如果没有活动的WebView或它不能后退，则检查其他WebView
        for (i in webViews.indices) {
            if (i == lastActiveWebViewIndex) continue // 已经检查过了
            val webView = webViews[i]
            if (webView?.isShown == true && webView.canGoBack()) {
                Log.d(TAG, "Back press handled by fallback WebView index $i")
                webView.goBack()
                return true // 事件已处理
            }
        }

        // 如果所有WebView都不能后退，则关闭服务
        Log.d(TAG, "No WebView can go back, stopping service.")
        service.stopSelf()
        return true // 事件已处理（通过关闭窗口）
    }

    /**
     * 新增：公共方法，用于从外部设置搜索输入框的文本
     */
    fun setSearchInputText(text: String) {
        val searchInput = floatingView?.findViewById<EditText>(R.id.dual_search_input)
        searchInput?.setText(text)
    }

    /**
     * 销毁浮动窗口
     */
    fun destroyFloatingWindow() {
        try {
            Log.d("FloatingWindowManager", "开始销毁浮动窗口")
            
            // 移除浮动窗口视图
            _floatingView?.let { view ->
                try {
                    windowManager?.removeView(view)
                    Log.d("FloatingWindowManager", "浮动窗口视图已移除")
                } catch (e: Exception) {
                    Log.e("FloatingWindowManager", "移除浮动窗口视图失败", e)
                }
            }
            
            // 清理引用
            _floatingView = null
            params = null
            windowManager = null
            
            // 清理WebView引用
            firstWebView = null
            secondWebView = null
            thirdWebView = null
            searchInput = null
            webViewContainer = null
            webviewsScrollContainer = null
            
            Log.d("FloatingWindowManager", "浮动窗口销毁完成")
            
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "销毁浮动窗口时出错", e)
        }
    }

    /**
     * 更新窗口数量
     */
    fun updateWindowCount(count: Int) {
        Log.d("FloatingWindowManager", "更新窗口数量: $count")
        
        // 根据窗口数量显示或隐藏WebView
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        webViews.forEachIndexed { index, webView ->
            val shouldBeVisible = index < count
            (webView.parent as? View)?.visibility = if (shouldBeVisible) View.VISIBLE else View.GONE
            Log.d("FloatingWindowManager", "WebView $index 可见性设置为: $shouldBeVisible")
        }
        
        // 保存新的窗口数量到SharedPreferences
        sharedPreferences.edit().putInt(DualFloatingWebViewService.KEY_WINDOW_COUNT, count).apply()
    }

    /**
     * 刷新搜索引擎
     */
    fun refreshSearchEngines() {
        Log.d("FloatingWindowManager", "刷新搜索引擎")
        refreshEngineIcons()
    }

    private fun showPasteButton() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || clipboard.primaryClip?.getItemAt(0)?.text.isNullOrBlank()) {
            return
        }

        val container = (_floatingView as? ViewGroup) ?: return
        val searchInputView = searchInput ?: return

        if (pasteButton == null) {
            val themedContext = ContextThemeWrapper(context, R.style.Theme_FloatingWindow)
            val inflater = LayoutInflater.from(themedContext)
            pasteButton = inflater.inflate(R.layout.paste_button, container, false).apply {
                setOnClickListener {
                    pasteFromClipboard()
                    hidePasteButton()
                }
            }
        }

        if (pasteButton?.parent == null) {
            container.addView(pasteButton)
        }

        pasteButton?.visibility = View.VISIBLE
        pasteButton?.alpha = 1f

        searchInputView.post {
            if (pasteButton == null) return@post

            pasteButton?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val pasteButtonHeight = pasteButton?.measuredHeight ?: 0

            var currentView: View = searchInputView
            var inputTop = 0f
            while (currentView != container) {
                inputTop += currentView.y
                currentView = currentView.parent as View
            }

            val buttonX = searchInputView.x
            val buttonY = inputTop + searchInputView.height + dpToPx(4)

            pasteButton?.x = buttonX
            pasteButton?.y = buttonY
        }

        hidePasteButtonRunnable?.let { pasteButtonHandler.removeCallbacks(it) }
        hidePasteButtonRunnable = Runnable { hidePasteButton() }
        pasteButtonHandler.postDelayed(hidePasteButtonRunnable!!, 5000)
    }

    private fun hidePasteButton() {
        hidePasteButtonRunnable?.let { pasteButtonHandler.removeCallbacks(it) }
        hidePasteButtonRunnable = null

        pasteButton?.let {
            if (it.visibility == View.VISIBLE) {
                it.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        it.visibility = View.GONE
                    }
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val textToPaste = clip.getItemAt(0).text
            if (!textToPaste.isNullOrEmpty()) {
                val currentText = searchInput?.text?.toString() ?: ""
                val selectionStart = searchInput?.selectionStart ?: 0
                val selectionEnd = searchInput?.selectionEnd ?: 0

                val newText = StringBuilder(currentText).replace(selectionStart, selectionEnd, textToPaste.toString()).toString()
                searchInput?.setText(newText)
                searchInput?.setSelection(selectionStart + textToPaste.length)
            }
        }
    }

    fun getXmlDefinedWebViews(): List<CustomWebView?> {
        return listOf(firstWebView, secondWebView, thirdWebView)
    }

    private fun setupTopResizeHandle(handle: View?) {
        handle?.setOnTouchListener { _, event ->
            handleTopResize(event)
            true
        }
    }

    private fun setupBottomResizeHandle(handle: View?) {
        handle?.setOnTouchListener { _, event ->
            handleBottomResize(event)
            true
        }
    }

    /**
     * 更新窗口焦点能力
     */
    private fun updateWindowFocusability(needsFocus: Boolean) {
        try {
            params?.let { layoutParams ->
                if (needsFocus) {
                    // 需要焦点时，移除FLAG_NOT_FOCUSABLE标志
                    layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                } else {
                    // 不需要焦点时，添加FLAG_NOT_FOCUSABLE标志
                    layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
                _floatingView?.let { view ->
                    windowManager?.updateViewLayout(view, layoutParams)
                }
                Log.d("FloatingWindowManager", "Window focusability updated: needsFocus=$needsFocus")
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error updating window focusability", e)
        }
    }

    /**
     * 隐藏软键盘
     */
    private fun hideKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            searchInput?.let { input ->
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                Log.d("FloatingWindowManager", "Keyboard hidden")
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error hiding keyboard", e)
        }
    }

    /**
     * 强制确保输入框焦点稳定
     */
    fun ensureInputFocus() {
        try {
            searchInput?.let { input ->
                Log.d("FloatingWindowManager", "强制确保输入框焦点")
                
                // 1. 先禁用所有WebView的焦点能力
                val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
                webViews.forEach { webView ->
                    webView.clearFocus()
                    webView.isFocusable = false
                    webView.isFocusableInTouchMode = false
                    
                    // 强制移除WebView内部可能的焦点
                    webView.evaluateJavascript("document.activeElement && document.activeElement.blur();", null)
                }
                
                // 2. 确保输入框可获取焦点
                input.isFocusable = true
                input.isFocusableInTouchMode = true
                
                // 3. 确保窗口可获取焦点
                updateWindowFocusability(true)
                
                // 4. 强制请求焦点
                input.requestFocus()
                input.requestFocusFromTouch()
                
                // 5. 延迟显示键盘，确保焦点已经设置
                Handler(Looper.getMainLooper()).postDelayed({
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    
                    // 如果焦点仍未获取，再次尝试
                    if (!input.isFocused) {
                        input.requestFocus()
                        input.requestFocusFromTouch()
                        Log.w("FloatingWindowManager", "Input focus retry needed")
                    }
                    
                    // 强制显示键盘
                    imm.showSoftInput(input, InputMethodManager.SHOW_FORCED)
                    
                    // 延迟恢复WebView焦点能力
                    Handler(Looper.getMainLooper()).postDelayed({
                        webViews.forEach { webView ->
                            webView.isFocusable = true
                            webView.isFocusableInTouchMode = true
                        }
                        Log.d("FloatingWindowManager", "WebView focus ability restored")
                    }, 800) // 延长到800ms
                    
                }, 150)
                
                Log.d("FloatingWindowManager", "Input focus ensured with WebView protection")
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error ensuring input focus", e)
        }
    }

    /**
     * 清除输入框焦点
     */
    fun clearInputFocus() {
        try {
            searchInput?.let { input ->
                if (input.isFocused) {
                    input.clearFocus()
                    Log.d("FloatingWindowManager", "Input focus cleared")
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error clearing input focus", e)
        }
    }

    /**
     * 智能输入框焦点激活 - 基于用户意图的焦点管理
     */
    fun forceInputFocusActivation() {
        try {
            searchInput?.let { input ->
                Log.d("FloatingWindowManager", "启动智能输入框焦点激活")
                
                // 1. 通知WebView进入焦点保护模式
                sendFocusControlToWebViews(false, "force_input_focus_start")
                
                // 2. 确保窗口可获取焦点
                updateWindowFocusability(true)
                
                // 3. 强制获取输入框焦点
                input.isFocusable = true
                input.isFocusableInTouchMode = true
                input.requestFocus()
                input.requestFocusFromTouch()
                
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_FORCED)
                
                // 4. 延迟检查焦点获取是否成功
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!input.isFocused) {
                        // 再次尝试
                        input.requestFocus()
                        imm.showSoftInput(input, InputMethodManager.SHOW_FORCED)
                        Log.d("FloatingWindowManager", "二次焦点获取尝试")
                        
                        // 如果仍然失败，通知WebView执行更强的焦点清理
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!input.isFocused) {
                                sendFocusControlToWebViews(false, "emergency_focus_clear")
                                input.requestFocus()
                                imm.showSoftInput(input, InputMethodManager.SHOW_FORCED)
                                Log.d("FloatingWindowManager", "紧急焦点清理和第三次尝试")
                            }
                        }, 200)
                    } else {
                        Log.d("FloatingWindowManager", "输入框焦点获取成功")
                    }
                }, 200)
                
                // 5. 延迟恢复正常焦点模式，但保持智能保护
                Handler(Looper.getMainLooper()).postDelayed({
                    sendFocusControlToWebViews(true, "smart_protection_active")
                    Log.d("FloatingWindowManager", "恢复智能焦点保护模式")
                }, 2000)
                
                Log.d("FloatingWindowManager", "智能输入框焦点激活完成")
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "智能焦点激活错误", e)
        }
    }

    /**
     * 向WebView发送焦点控制信号
     */
    private fun sendFocusControlToWebViews(allow: Boolean, action: String) {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        val message = """{
            "type": "focus_control",
            "allow": $allow,
            "action": "$action"
        }"""
        
        webViews.forEach { webView ->
            webView.evaluateJavascript(
                "window.postMessage($message, '*');", null
            )
        }
        Log.d("FloatingWindowManager", "Sent focus control: allow=$allow, action=$action")
    }

    /**
     * 重置窗口位置到默认值 - 恢复到用户刚安装软件时的初始状态
     * 使用简化逻辑，确保功能可靠
     */
    fun resetWindowPositionToDefault() {
        val TAG = "FloatingWindowManager"
        Log.d(TAG, "=== 开始重置窗口到安装初始状态 ===")
        
        try {
            val displayMetrics = context.resources.displayMetrics
            val statusBarHeight = getStatusBarHeight()
            val orientation = context.resources.configuration.orientation
            
            Log.d(TAG, "屏幕: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, 方向: $orientation")
            
            // 简化的默认状态计算
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // 使用最大化的比例，根据屏幕方向调整
            val (widthRatio, heightRatio) = when (orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> Pair(0.95f, 0.8f)  // 横屏：最大化利用屏幕
                Configuration.ORIENTATION_PORTRAIT -> Pair(1.0f, 0.75f)   // 竖屏：最大化利用屏幕  
                else -> Pair(0.9f, 0.7f) // 未知：较大尺寸
            }
            
            val defaultWidth = (screenWidth * widthRatio).toInt()
            val defaultHeight = ((screenHeight - statusBarHeight) * heightRatio).toInt()
            
            // 窗口左边缘与屏幕左边缘相切，上边缘紧贴状态栏
            val defaultX = 0
            val defaultY = statusBarHeight // 直接紧贴状态栏
            
            val orientationText = if (orientation == Configuration.ORIENTATION_LANDSCAPE) "横屏" else "竖屏"
            Log.d(TAG, "${orientationText}默认状态: ${defaultWidth}x${defaultHeight} at ($defaultX, $defaultY)")
            
            // 更新窗口
            params?.let { p ->
                p.width = defaultWidth
                p.height = defaultHeight
                p.x = defaultX
                p.y = defaultY
                
                // 重置窗口属性到初始状态
                p.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                p.format = android.graphics.PixelFormat.TRANSLUCENT
                p.gravity = android.view.Gravity.TOP or android.view.Gravity.START
                
                _floatingView?.let { view ->
                    // 使用简单的更新方式
                    windowManager?.updateViewLayout(view, p)
                    Log.d(TAG, "✓ 窗口布局已更新")
                    
                    // 重置所有内部状态
                    resetAllInternalStates()
                    
                    // 清除保存的旧状态
                    clearAllWindowStateFromPreferences()
                    
                    // 强制立即保存新的最大化状态
                    sharedPreferences.edit().apply {
                        putInt(DualFloatingWebViewService.KEY_WINDOW_X, defaultX)
                        putInt(DualFloatingWebViewService.KEY_WINDOW_Y, defaultY)
                        putInt(DualFloatingWebViewService.KEY_WINDOW_WIDTH, defaultWidth)
                        putInt(DualFloatingWebViewService.KEY_WINDOW_HEIGHT, defaultHeight)
                        apply()
                    }
                    
                    // 保存新的默认状态
                    windowStateCallback.onWindowStateChanged(defaultX, defaultY, defaultWidth, defaultHeight)
                    
                    // 重置窗口数量到默认值
                    val settingsManager = SettingsManager.getInstance(context)
                    val defaultWindowCount = settingsManager.getDefaultWindowCount()
                    updateWindowCount(defaultWindowCount)
                    
                    // 更新UI显示
                    _floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)?.text = defaultWindowCount.toString()
                    
                    Log.d(TAG, "=== 重置完成 === ${orientationText}: ${defaultWidth}x${defaultHeight}, 窗口数: $defaultWindowCount")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "重置窗口失败", e)
        }
    }
    
    /**
     * 计算安装时的默认窗口状态
     * 简单可靠的逻辑，不依赖复杂的边界计算
     */
    private fun calculateDefaultWindowState(
        displayMetrics: android.util.DisplayMetrics, 
        statusBarHeight: Int, 
        orientation: Int
    ): Array<Int> {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 最大化利用屏幕的默认尺寸策略
        val (widthRatio, heightRatio) = when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> Pair(0.95f, 0.8f) // 横屏：最大化利用
            Configuration.ORIENTATION_PORTRAIT -> Pair(1.0f, 0.75f)  // 竖屏：最大化利用
            else -> Pair(0.9f, 0.7f) // 未知：较大尺寸
        }
        
        val defaultWidth = (screenWidth * widthRatio).toInt()
        val defaultHeight = ((screenHeight - statusBarHeight) * heightRatio).toInt()
        
        // 窗口左边缘与屏幕左边缘相切，最大化利用空间
        val marginTop = dpToPx(0) // 无上边距，直接紧贴状态栏
        val marginBottom = dpToPx(16) // 仅保留16dp下边距，确保底部控制条可见
        
        val defaultX = 0
        val defaultY = statusBarHeight + marginTop
        
        // 确保窗口不会超出底部边界
        val maxY = screenHeight - defaultHeight - marginBottom
        val safeY = minOf(defaultY, maxY)
        
        return arrayOf(defaultWidth, defaultHeight, defaultX, safeY)
    }
    
    /**
     * 重置所有内部状态到初始值
     */
    private fun resetAllInternalStates() {
        try {
            // 重置拖拽和缩放状态
            isDragging = false
            isResizing = false
            initialX = 0
            initialY = 0
            initialTouchX = 0f
            initialTouchY = 0f
            initialWidth = 0
            initialHeight = 0
            lastDragX = 0f
            lastDragY = 0f
            
            // 重置窗口状态
            originalWindowHeight = 0
            originalWindowY = 0
            isKeyboardShowing = false
            lastActiveWebViewIndex = 0
            
            // 重置屏幕信息
            val displayMetrics = context.resources.displayMetrics
            currentOrientation = context.resources.configuration.orientation
            lastScreenWidth = displayMetrics.widthPixels
            lastScreenHeight = displayMetrics.heightPixels
            
            // 重置视图变形状态
            _floatingView?.let { view ->
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                view.rotation = 0.0f
                view.translationX = 0.0f
                view.translationY = 0.0f
                view.alpha = 1.0f
                
                // 递归重置子视图
                if (view is ViewGroup) {
                    resetChildViewStates(view)
                }
                
                // 强制重新布局
                view.requestLayout()
                view.invalidate()
            }
            
            Log.d("FloatingWindowManager", "✓ 所有内部状态已重置到初始值")
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "重置内部状态失败", e)
        }
    }
    
    /**
     * 递归重置所有子视图的变形状态
     */
    private fun resetChildViewStates(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            child?.let { childView ->
                childView.scaleX = 1.0f
                childView.scaleY = 1.0f
                childView.rotation = 0.0f
                childView.translationX = 0.0f
                childView.translationY = 0.0f
                childView.alpha = 1.0f
                
                // 如果子视图还是ViewGroup，递归处理
                if (childView is ViewGroup) {
                    resetChildViewStates(childView)
                }
            }
        }
    }
    
    /**
     * 完全清除SharedPreferences中保存的所有窗口状态数据
     * 这样可以确保用户的拖拽、缩放等历史操作状态被彻底删除
     */
    private fun clearAllWindowStateFromPreferences() {
        try {
            val editor = sharedPreferences.edit()
            
            // 清除窗口位置和尺寸
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_X)
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_Y)
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_WIDTH) 
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_HEIGHT)
            
            // 清除窗口数量（让它回到默认值）
            editor.remove(DualFloatingWebViewService.KEY_WINDOW_COUNT)
            
            // 提交更改
            editor.apply()
            
            Log.d("FloatingWindowManager", "✓ SharedPreferences中的所有窗口状态数据已清除")
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "清除SharedPreferences窗口状态失败", e)
        }
    }
    
    /**
     * 检测并处理屏幕方向变化
     * 如果检测到屏幕方向或尺寸变化，自动调整窗口到合适的尺寸和位置
     */
    fun handleOrientationChangeIfNeeded() {
        val TAG = "FloatingWindowManager"
        try {
            val displayMetrics = context.resources.displayMetrics
            val newOrientation = context.resources.configuration.orientation
            
            // 检测屏幕方向是否发生变化
            val orientationChanged = (newOrientation != currentOrientation) ||
                                   (displayMetrics.widthPixels != lastScreenWidth) ||
                                   (displayMetrics.heightPixels != lastScreenHeight)
            
            if (orientationChanged) {
                Log.d(TAG, "=== 检测到屏幕方向变化 ===")
                Log.d(TAG, "旧: ${lastScreenWidth}x${lastScreenHeight}, 方向=$currentOrientation")
                Log.d(TAG, "新: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, 方向=$newOrientation")
                
                // 更新记录的屏幕信息
                currentOrientation = newOrientation
                lastScreenWidth = displayMetrics.widthPixels
                lastScreenHeight = displayMetrics.heightPixels
                
                // 自动调整窗口到适合新方向的尺寸
                adjustWindowForOrientation()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理屏幕方向变化失败", e)
        }
    }
    
    /**
     * 根据当前屏幕方向调整窗口尺寸和位置
     * 确保上下控制条始终可见和可操作
     */
    private fun adjustWindowForOrientation() {
        val TAG = "FloatingWindowManager"
        try {
            val displayMetrics = context.resources.displayMetrics
            val statusBarHeight = getStatusBarHeight()
            val safeBounds = calculateSafeWindowBounds(displayMetrics, statusBarHeight)
            
            // 根据当前屏幕方向计算合适的窗口尺寸
            val (newWidth, newHeight) = calculateOptimalWindowSize(displayMetrics, statusBarHeight)
            
            params?.let { p ->
                // 调整尺寸，应用安全边界限制
                p.width = newWidth.coerceIn(safeBounds.minWidth, safeBounds.maxWidth)
                p.height = newHeight.coerceIn(safeBounds.minHeight, safeBounds.maxHeight)
                
                // 调整位置，确保窗口完全在安全区域内
                p.x = p.x.coerceIn(safeBounds.minX, safeBounds.maxX - p.width)
                p.y = p.y.coerceIn(safeBounds.minY, safeBounds.maxY - p.height)
                
                // 如果窗口位置仍然无效，重新居中到安全区域
                if (p.x < safeBounds.minX || p.x > safeBounds.maxX - p.width ||
                    p.y < safeBounds.minY || p.y > safeBounds.maxY - p.height) {
                    p.x = safeBounds.minX + (safeBounds.maxX - safeBounds.minX - p.width) / 2
                    p.y = safeBounds.minY + (safeBounds.maxY - safeBounds.minY - p.height) / 3
                    Log.d(TAG, "窗口位置超出安全边界，重新居中到安全区域")
                }
                
                // 更新窗口布局
                _floatingView?.let { view ->
                    windowManager?.updateViewLayout(view, p)
                    Log.d(TAG, "✓ 窗口已适配屏幕方向变化: ${p.width}x${p.height} at (${p.x}, ${p.y}), 安全边界: Y=${safeBounds.minY}-${safeBounds.maxY}")
                    
                    // 保存新的窗口状态
                    windowStateCallback.onWindowStateChanged(p.x, p.y, p.width, p.height)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "调整窗口方向失败", e)
        }
    }
} 