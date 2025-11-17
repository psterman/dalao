package com.example.aifloatingball.ui.floating

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import android.widget.FrameLayout
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

    companion object {
        private const val TAG = "FloatingWindowManager"

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

    // 分层焦点管理系统
    enum class FocusLayer {
        BACKGROUND,      // 下方界面可交互，悬浮窗不获取焦点
        FLOATING_PASSIVE, // 悬浮窗可见但不主动获取焦点，允许WebView交互
        FLOATING_ACTIVE,  // 悬浮窗主动获取焦点，用于输入框交互
        FLOATING_MODAL   // 悬浮窗模态状态，阻止下方界面交互
    }

    private var currentFocusLayer = FocusLayer.BACKGROUND
    private var focusTransitionInProgress = false
    private val focusTransitionHandler = Handler(Looper.getMainLooper())
    private var pendingFocusTransition: Runnable? = null

    private var originalWindowHeight: Int = 0
    private var originalWindowY: Int = 0
    private var isKeyboardShowing: Boolean = false

    // 新增：屏幕方向相关变量
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED
    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

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
        val navigationBarHeight = getNavigationBarHeight()
        
        // 转换dp到px
        val topHandleHeight = dpToPx(TOP_DRAG_HANDLE_HEIGHT_DP)
        val bottomHandleHeight = dpToPx(BOTTOM_RESIZE_HANDLE_HEIGHT_DP)
        val topControlBarHeight = dpToPx(TOP_CONTROL_BAR_HEIGHT_DP)
        val safeMargin = dpToPx(SAFE_MARGIN_DP)
        
        // 计算安全区域
        val minY = statusBarHeight + safeMargin // 顶部：状态栏 + 安全边距
        val maxY = screenHeight - navigationBarHeight - bottomHandleHeight - safeMargin // 底部：导航栏 + 底部控制条 + 安全边距
        
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
    
    // 获取导航栏高度
    private fun getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
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
        
        // 检查是否为卡片视图模式，如果是则使用更大的窗口尺寸
        val isCardViewMode = (service as? DualFloatingWebViewService)?.let {
            it.currentViewMode == DualFloatingWebViewService.ViewMode.CARD_VIEW
        } ?: false
        
        val (widthRatio, heightRatio) = when {
            isCardViewMode -> {
                // 卡片视图模式：最大化窗口，充分利用屏幕空间，高度必须占据95%
                when (orientation) {
                    Configuration.ORIENTATION_LANDSCAPE -> Pair(0.98f, 0.95f)
                    Configuration.ORIENTATION_PORTRAIT -> Pair(1.0f, 0.95f)
                    else -> Pair(0.95f, 0.95f)
                }
            }
            orientation == Configuration.ORIENTATION_LANDSCAPE -> {
                // 横屏模式：最大化利用屏幕空间
                Pair(0.95f, 0.8f)
            }
            orientation == Configuration.ORIENTATION_PORTRAIT -> {
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
            // 初始状态：不可获取焦点，避免启动时输入法闪现
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            // 初始状态：隐藏输入法，不调整窗口大小
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
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
        // 根据当前视图模式显示不同的文本
        windowCountToggleText?.text = when (service.currentViewMode) {
            DualFloatingWebViewService.ViewMode.CARD_VIEW -> "卡片"
            DualFloatingWebViewService.ViewMode.HORIZONTAL_SCROLL -> initialCount.toString()
        }
        android.util.Log.d("FloatingWindowManager", "初始化窗口数量显示为: $initialCount, 视图模式: ${service.currentViewMode}")

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

        // 使用分层焦点管理的焦点变化监听器
        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            Log.d(TAG, "SearchInput focus changed: $hasFocus, current layer: $currentFocusLayer")
            if (!hasFocus) {
                // 输入框失去焦点，决定下一个焦点层级
                val targetLayer = decideFocusLayerForUserAction("input_focus_lost", null)

                // 延迟切换，避免快速焦点变化时的闪现
                pendingFocusTransition?.let { focusTransitionHandler.removeCallbacks(it) }
                pendingFocusTransition = Runnable {
                    if (searchInput?.isFocused != true) {
                        transitionToFocusLayer(targetLayer, "input_focus_lost")
                    }
                }
                focusTransitionHandler.postDelayed(pendingFocusTransition!!, 200)
            }
        }

        searchInput?.setOnLongClickListener {
            textSelectionManager.showEditTextSelectionMenu(it as EditText)
            true
        }

        // 简化的点击监听器，避免重复焦点操作
        searchInput?.setOnClickListener { view ->
            Log.d(TAG, "SearchInput clicked")

            // 只在必要时更新窗口焦点能力
            if (!view.isFocused) {
                updateWindowFocusabilityForInput(true)
                view.requestFocus()

                // 延迟显示键盘，避免与焦点变化冲突
                Handler(Looper.getMainLooper()).postDelayed({
                    if (view.isFocused) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                    }
                }, 50)
            }
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

        // 窗口数量按钮：单击切换视图模式，长按切换窗口数量
        // 使用自定义触摸处理，确保在两种模式下都能正常工作
        var longPressRunnable: Runnable? = null
        val longPressHandler = Handler(Looper.getMainLooper())
        val longPressDelay = 500L // 500ms长按延迟
        var initialTouchX = 0f
        var initialTouchY = 0f
        var hasLongPressed = false // 标记是否已触发长按
        
        windowCountButton?.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // 记录初始触摸位置
                    initialTouchX = event.x
                    initialTouchY = event.y
                    hasLongPressed = false
                    
                    // 启动长按检测（长按：切换窗口数量）
                    longPressRunnable = Runnable {
                        // 长按：切换窗口数量（只在横向模式下）
                        hasLongPressed = true
                        if (service.currentViewMode == DualFloatingWebViewService.ViewMode.HORIZONTAL_SCROLL) {
                            val newCount = service.toggleAndReloadWindowCount()
                            windowCountToggleText?.text = newCount.toString()
                            // 提供触觉反馈
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            }
                        }
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, longPressDelay)
                    true // 消费事件，手动处理
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    longPressRunnable = null
                    
                    // 如果是UP事件且没有触发长按，则处理单击事件（切换视图模式）
                    if (event.action == android.view.MotionEvent.ACTION_UP && !hasLongPressed) {
                        // 单击：切换视图模式
                        val newMode = service.toggleViewMode()
                        // 更新按钮图标或文本以反映当前模式
                        windowCountToggleText?.text = when (newMode) {
                            DualFloatingWebViewService.ViewMode.CARD_VIEW -> "卡片"
                            DualFloatingWebViewService.ViewMode.HORIZONTAL_SCROLL -> {
                                // 横向模式下显示窗口数量
                                service.getCurrentWindowCount().toString()
                            }
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // 如果移动距离过大，取消长按检测
                    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
                    val deltaX = kotlin.math.abs(event.x - initialTouchX)
                    val deltaY = kotlin.math.abs(event.y - initialTouchY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                    }
                    true
                }
                else -> false
            }
        }

        setupTopResizeHandle(topResizeHandle)
        setupBottomResizeHandle(bottomResizeHandle)

        // 方案核心：手动实现顶部栏的拖动，以避免事件冲突
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        topControlBar?.setOnTouchListener { _, event ->
            // 先让侧滑手势检测器处理（如果已初始化）
            swipeGestureDetector?.let { detector ->
                if (detector.onTouchEvent(event)) {
                    // 手势检测器已处理（侧滑返回），不继续处理拖动
                    return@setOnTouchListener true
                }
            }
            
            // 继续原有的拖动逻辑
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
        
        // 注意：侧滑手势检测在setupOutsideTouchHandling()中集成到_floatingView的触摸监听器

        // 新增：设置返回键监听器
        (_floatingView as? KeyEventInterceptorView)?.backPressListener = this

        // 新增：设置侧滑手势检测
        setupSwipeGestureDetection()

        // 新增：填充全局AI搜索引擎栏
        populateGlobalAIEngineIcons(globalAiContainer)
        // 新增：填充全局标准搜索引擎栏
        populateGlobalStandardEngineIcons(globalStdContainer)

        setupKeyboardManagement()
        setupWebViewFocusManagementOnScroll()
        setupOutsideTouchHandling()

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
                // 检查是否需要API配置
                if (needsApiConfiguration(engine.name)) {
                    showApiConfigurationDialog(engine.name)
                    return@createIconView
                }

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
        
        // 使用分层焦点管理的输入框触摸监听器
        searchInput?.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastClickedArea = "search_input"
                lastClickTime = System.currentTimeMillis()
                Log.d(TAG, "User clicked on search input - transitioning to active layer")

                // 切换到主动焦点层级
                val targetLayer = decideFocusLayerForUserAction("search_input_clicked", view)
                transitionToFocusLayer(targetLayer, "user_clicked_search_input")

                // 确保输入框可以获取焦点
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                view.requestFocus()

                // 延迟显示键盘，等待焦点层级切换完成
                Handler(Looper.getMainLooper()).postDelayed({
                    if (view.isFocused && currentFocusLayer == FocusLayer.FLOATING_ACTIVE) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        Log.d(TAG, "Keyboard shown after focus layer transition")
                    }
                }, 150)
            }
            false // 不消费事件，让正常的输入处理继续
        }

        // 使用分层焦点管理的WebView触摸监听器
        val webViewTouchListener = View.OnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastClickedArea = "webview"
                lastClickTime = System.currentTimeMillis()
                Log.d(TAG, "User clicked on WebView - transitioning to passive layer")

                // 记录哪个WebView被触摸了
                val touchedIndex = webViews.indexOf(view)
                if (touchedIndex != -1) {
                    lastActiveWebViewIndex = touchedIndex
                    Log.d(TAG, "Active WebView set to index: $lastActiveWebViewIndex")
                }

                // 切换到被动焦点层级，允许WebView交互
                val targetLayer = decideFocusLayerForUserAction("webview_clicked", view)
                transitionToFocusLayer(targetLayer, "user_clicked_webview")

                // 如果搜索框之前有焦点，清除它
                if (searchInput?.isFocused == true) {
                    Log.d(TAG, "Clearing search input focus for WebView interaction")
                    searchInput?.clearFocus()
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
     * 确保始终返回上一级菜单，而不是直接退出服务
     */
    override fun onBackButtonPressed(): Boolean {
        val TAG = "FloatingWindowManager"
        
        // 1. 首先检查是否有全屏卡片查看器正在显示
        val cardViewModeManager = service.getCardViewModeManager()
        if (cardViewModeManager != null) {
            val fullScreenViewer = cardViewModeManager.getFullScreenViewer()
            if (fullScreenViewer != null && fullScreenViewer.isShowing()) {
                Log.d(TAG, "全屏卡片查看器正在显示，关闭全屏")
                fullScreenViewer.dismiss()
                return true
            }
        }
        
        // 2. 检查卡片视图模式下的WebView是否可以返回
        if (service.currentViewMode == DualFloatingWebViewService.ViewMode.CARD_VIEW) {
            cardViewModeManager?.let { manager ->
                val cards = manager.getAllCards()
                for (card in cards.reversed()) {
                    if (card.webView.canGoBack()) {
                        Log.d(TAG, "卡片视图模式：返回WebView上一页")
                        card.webView.goBack()
                        return true
                    }
                }
            }
        }
        
        // 3. 检查横向滚动模式下的WebView是否可以返回
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

        // 4. 如果所有WebView都不能后退，弹出确认对话框询问是否退出
        Log.d(TAG, "所有WebView都不能后退，弹出退出确认对话框")
        showExitConfirmDialog()
        return true // 事件已处理
    }
    
    /**
     * 显示退出确认对话框
     */
    private fun showExitConfirmDialog() {
        // 检测暗色模式
        val isDarkMode = (context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 根据暗色/亮色模式选择主题
        val dialogTheme = if (isDarkMode) {
            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        } else {
            androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
        }
        
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        val builder = AlertDialog.Builder(themedContext)
            .setTitle("退出确认")
            .setMessage("是否退出搜索")
            .setPositiveButton("退出") { _, _ ->
                Log.d(TAG, "用户确认退出服务")
                service.stopSelf()
            }
            .setNegativeButton("取消", null)
        
        val dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }
    
    /**
     * 处理侧滑手势（返回上一级菜单）
     */
    fun handleSwipeBack(): Boolean {
        // 复用返回键的处理逻辑
        return onBackButtonPressed()
    }
    
    /**
     * 侧滑手势检测器
     */
    private var swipeGestureDetector: GestureDetector? = null
    
    /**
     * 设置侧滑手势检测
     */
    private fun setupSwipeGestureDetection() {
        swipeGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // 检测从屏幕左边缘向右滑动（返回手势）
                val isRightSwipe = diffX > 0 && abs(diffX) > abs(diffY)
                val isFromLeftEdge = e1.x < 50 // 从屏幕左边缘50像素内开始
                val hasEnoughVelocity = abs(velocityX) > 1000
                val hasEnoughDistance = abs(diffX) > 100
                
                if (isRightSwipe && isFromLeftEdge && hasEnoughVelocity && hasEnoughDistance) {
                    Log.d(TAG, "检测到从左侧边缘向右滑动，触发返回上一级菜单")
                    handleSwipeBack()
                    return true
                }
                
                return false
            }
        })
        
        // 注意：不在这里设置触摸监听器，而是在原有的拖动触摸监听器中集成手势检测
        // 这样可以避免覆盖原有的拖动逻辑
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
     * 获取卡片视图容器（用于卡片视图模式）
     */
    fun getCardViewContainer(): FrameLayout? {
        return _floatingView?.findViewById(R.id.card_view_container)
    }

    /**
     * 获取标签栏容器（用于卡片视图模式）
     */
    fun getTabBarContainer(): ViewGroup? {
        return _floatingView?.findViewById(R.id.tab_bar_container)
    }

    /**
     * 切换视图模式显示
     */
    fun switchViewMode(isCardViewMode: Boolean) {
        val cardViewContainer = _floatingView?.findViewById<FrameLayout>(R.id.card_view_container)
        val tabBarContainer = _floatingView?.findViewById<ViewGroup>(R.id.tab_bar_container)
        val webviewsScrollContainer = _floatingView?.findViewById<View>(R.id.webviews_scroll_container)
        val globalAiEngineScrollView = _floatingView?.findViewById<View>(R.id.global_ai_engine_scroll_view)
        val globalStandardEngineScrollView = _floatingView?.findViewById<View>(R.id.global_standard_engine_scroll_view)
        
        if (isCardViewMode) {
            // 卡片视图模式：显示卡片容器和标签栏，隐藏横向滚动容器
            cardViewContainer?.visibility = View.VISIBLE
            tabBarContainer?.visibility = View.VISIBLE
            webviewsScrollContainer?.visibility = View.GONE
            globalAiEngineScrollView?.visibility = View.GONE
            globalStandardEngineScrollView?.visibility = View.GONE
        } else {
            // 横向拖动模式：显示横向滚动容器，隐藏卡片容器和标签栏
            cardViewContainer?.visibility = View.GONE
            tabBarContainer?.visibility = View.GONE
            webviewsScrollContainer?.visibility = View.VISIBLE
            globalAiEngineScrollView?.visibility = View.VISIBLE
            globalStandardEngineScrollView?.visibility = View.VISIBLE
        }
        
        Log.d(TAG, "切换视图模式: ${if (isCardViewMode) "卡片视图" else "横向拖动"}")
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
     * 专门用于输入框的窗口焦点管理，避免输入法闪现
     */
    private fun updateWindowFocusabilityForInput(needsFocus: Boolean) {
        try {
            params?.let { layoutParams ->
                val oldFlags = layoutParams.flags
                val oldSoftInputMode = layoutParams.softInputMode

                if (needsFocus) {
                    // 需要焦点时，移除FLAG_NOT_FOCUSABLE标志，设置合适的输入法模式
                    layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                } else {
                    // 不需要焦点时，添加FLAG_NOT_FOCUSABLE标志，隐藏输入法
                    layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                }

                // 只在参数真正改变时才更新布局，避免不必要的更新
                if (oldFlags != layoutParams.flags || oldSoftInputMode != layoutParams.softInputMode) {
                    _floatingView?.let { view ->
                        windowManager?.updateViewLayout(view, layoutParams)
                    }
                    Log.d("FloatingWindowManager", "Input window focusability updated: needsFocus=$needsFocus")
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error updating input window focusability", e)
        }
    }

    /**
     * 分层焦点管理系统 - 核心方法
     * 根据用户交互意图智能切换焦点层级
     */
    private fun transitionToFocusLayer(targetLayer: FocusLayer, reason: String) {
        if (focusTransitionInProgress || currentFocusLayer == targetLayer) {
            Log.d("FloatingWindowManager", "Focus transition skipped: inProgress=$focusTransitionInProgress, same=$currentFocusLayer")
            return
        }

        Log.d("FloatingWindowManager", "Focus layer transition: $currentFocusLayer -> $targetLayer (reason: $reason)")

        // 取消待处理的焦点转换
        pendingFocusTransition?.let { focusTransitionHandler.removeCallbacks(it) }

        focusTransitionInProgress = true
        val previousLayer = currentFocusLayer
        currentFocusLayer = targetLayer

        try {
            params?.let { layoutParams ->
                when (targetLayer) {
                    FocusLayer.BACKGROUND -> {
                        // 悬浮窗完全不获取焦点，下方界面可正常交互
                        layoutParams.flags = (layoutParams.flags or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

                        // 清除输入框焦点
                        searchInput?.clearFocus()
                        hideKeyboard()
                    }

                    FocusLayer.FLOATING_PASSIVE -> {
                        // 悬浮窗可交互但不主动获取焦点，允许WebView正常工作
                        layoutParams.flags = (layoutParams.flags or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

                        // 确保WebView可以交互
                        enableWebViewInteraction()
                    }

                    FocusLayer.FLOATING_ACTIVE -> {
                        // 悬浮窗主动获取焦点，用于输入框交互
                        layoutParams.flags = (layoutParams.flags and
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

                        // 限制WebView焦点，优先保证输入框
                        restrictWebViewFocus()
                    }

                    FocusLayer.FLOATING_MODAL -> {
                        // 悬浮窗模态状态，阻止下方界面交互
                        layoutParams.flags = (layoutParams.flags and
                            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                             WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL).inv())
                        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    }
                }

                _floatingView?.let { view ->
                    windowManager?.updateViewLayout(view, layoutParams)
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error in focus layer transition", e)
            // 回滚到之前的状态
            currentFocusLayer = previousLayer
        } finally {
            // 延迟重置转换标志，避免快速连续调用
            focusTransitionHandler.postDelayed({
                focusTransitionInProgress = false
            }, 100)
        }
    }

    /**
     * 温和地隐藏软键盘，避免闪现
     */
    private fun hideKeyboard() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            searchInput?.let { input ->
                // 使用更温和的隐藏方式
                imm.hideSoftInputFromWindow(input.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                Log.d("FloatingWindowManager", "Keyboard hidden gently")
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "Error hiding keyboard", e)
        }
    }

    /**
     * 启用WebView交互，用于FLOATING_PASSIVE层级
     */
    private fun enableWebViewInteraction() {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        webViews.forEach { webView ->
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
        }

        // 通知WebView可以正常获取焦点
        sendFocusControlToWebViews(true, "passive_layer_enabled")
        Log.d("FloatingWindowManager", "WebView interaction enabled for passive layer")
    }

    /**
     * 限制WebView焦点，用于FLOATING_ACTIVE层级
     */
    private fun restrictWebViewFocus() {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        webViews.forEach { webView ->
            webView.isFocusable = false
            webView.isFocusableInTouchMode = false
        }

        // 通知WebView限制焦点获取
        sendFocusControlToWebViews(false, "active_layer_input_priority")
        Log.d("FloatingWindowManager", "WebView focus restricted for active layer")
    }

    /**
     * 智能焦点层级决策
     * 根据用户交互上下文决定应该使用哪个焦点层级
     */
    private fun decideFocusLayerForUserAction(action: String, targetView: View?): FocusLayer {
        return when (action) {
            "search_input_clicked" -> {
                // 用户点击搜索框，需要主动获取焦点
                FocusLayer.FLOATING_ACTIVE
            }
            "webview_clicked" -> {
                // 用户点击WebView，允许WebView交互但不阻止下方界面
                FocusLayer.FLOATING_PASSIVE
            }
            "container_scroll" -> {
                // 用户滚动容器，保持被动交互
                FocusLayer.FLOATING_PASSIVE
            }
            "outside_touch" -> {
                // 用户点击悬浮窗外部，回到后台层级
                FocusLayer.BACKGROUND
            }
            "input_focus_lost" -> {
                // 输入框失去焦点，根据是否有WebView交互决定
                if (hasActiveWebViewInteraction()) FocusLayer.FLOATING_PASSIVE else FocusLayer.BACKGROUND
            }
            else -> {
                // 默认保持当前层级
                currentFocusLayer
            }
        }
    }

    /**
     * 检查是否有活跃的WebView交互
     */
    private fun hasActiveWebViewInteraction(): Boolean {
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        return webViews.any { webView ->
            webView.isShown && (webView.progress < 100 || webView.canGoBack())
        }
    }

    /**
     * 设置外部触摸处理，监听用户点击悬浮窗外部的行为
     */
    private fun setupOutsideTouchHandling() {
        // 为悬浮窗添加外部触摸监听和侧滑手势检测
        _floatingView?.setOnTouchListener { view, event ->
            // 1. 先让侧滑手势检测器处理（如果已初始化）
            swipeGestureDetector?.let { detector ->
                if (detector.onTouchEvent(event)) {
                    // 手势检测器已处理（侧滑返回）
                    return@setOnTouchListener true
                }
            }
            
            // 2. 处理外部触摸事件
            when (event.action) {
                MotionEvent.ACTION_OUTSIDE -> {
                    Log.d("FloatingWindowManager", "User touched outside floating window")

                    // 用户点击了悬浮窗外部，切换到后台层级
                    val targetLayer = decideFocusLayerForUserAction("outside_touch", null)
                    transitionToFocusLayer(targetLayer, "user_touched_outside")

                    // 清除输入框焦点
                    searchInput?.clearFocus()

                    true // 消费事件
                }
                else -> false
            }
        }

        // 确保窗口可以接收外部触摸事件
        params?.flags = params?.flags?.or(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
    }

    /**
     * 公共方法：手动切换焦点层级
     * 供外部调用，用于特殊情况下的焦点管理
     */
    fun switchToFocusLayer(layer: FocusLayer, reason: String = "manual_switch") {
        transitionToFocusLayer(layer, reason)
    }

    /**
     * 公共方法：获取当前焦点层级
     */
    fun getCurrentFocusLayer(): FocusLayer {
        return currentFocusLayer
    }

    /**
     * 公共方法：检查是否正在进行焦点转换
     */
    fun isFocusTransitionInProgress(): Boolean {
        return focusTransitionInProgress
    }

    /**
     * 简化的输入框焦点确保方法，减少输入法闪现
     */
    fun ensureInputFocus() {
        try {
            searchInput?.let { input ->
                Log.d("FloatingWindowManager", "确保输入框焦点")

                // 如果输入框已经有焦点，不做任何操作
                if (input.isFocused) {
                    Log.d("FloatingWindowManager", "输入框已有焦点，无需操作")
                    return
                }

                // 简单的焦点获取
                updateWindowFocusabilityForInput(true)
                input.requestFocus()

                // 延迟显示键盘，避免与窗口更新冲突
                Handler(Looper.getMainLooper()).postDelayed({
                    if (input.isFocused) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                        Log.d("FloatingWindowManager", "输入框焦点确保完成")
                    }
                }, 100)
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
     * 简化的强制输入框焦点激活方法，减少输入法闪现
     */
    fun forceInputFocusActivation() {
        try {
            searchInput?.let { input ->
                Log.d("FloatingWindowManager", "启动简化的输入框焦点激活")

                // 如果输入框已经有焦点，不做任何操作
                if (input.isFocused) {
                    Log.d("FloatingWindowManager", "输入框已有焦点，无需强制激活")
                    return
                }

                // 简单的强制焦点获取，避免复杂的WebView操作
                updateWindowFocusabilityForInput(true)
                input.requestFocus()

                // 延迟显示键盘，避免与窗口更新冲突
                Handler(Looper.getMainLooper()).postDelayed({
                    if (input.isFocused) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
                        Log.d("FloatingWindowManager", "强制焦点激活成功")
                    } else {
                        // 如果仍然失败，再尝试一次
                        input.requestFocus()
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (input.isFocused) {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showSoftInput(input, InputMethodManager.SHOW_FORCED)
                            }
                        }, 100)
                    }
                }, 150)

                Log.d("FloatingWindowManager", "简化焦点激活完成")
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowManager", "简化焦点激活错误", e)
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
                    
                    // 更新UI显示（根据当前视图模式显示不同文本）
                    val windowCountToggleText = _floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)
                    windowCountToggleText?.text = when (service.currentViewMode) {
                        DualFloatingWebViewService.ViewMode.CARD_VIEW -> "卡片"
                        DualFloatingWebViewService.ViewMode.HORIZONTAL_SCROLL -> defaultWindowCount.toString()
                    }
                    
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

    fun setWindowFocusable(focusable: Boolean) {
        params?.let { p ->
            if (focusable) {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            }
            windowManager?.updateViewLayout(floatingView, p)
        }
    }

    /**
     * 检查AI引擎是否需要API配置
     */
    private fun needsApiConfiguration(engineName: String): Boolean {
        val settingsManager = SettingsManager.getInstance(context)
        return when (engineName.lowercase()) {
            "deepseek (api)", "deepseek (custom)" -> {
                val apiKey = settingsManager.getDeepSeekApiKey()
                apiKey.isBlank()
            }
            "chatgpt (api)" -> {
                val apiKey = settingsManager.getChatGPTApiKey()
                apiKey.isBlank()
            }
            else -> false
        }
    }

    /**
     * 显示API配置对话框
     */
    private fun showApiConfigurationDialog(engineName: String) {
        try {
            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)

            val engineDisplayName = when (engineName.lowercase()) {
                "deepseek (api)", "deepseek (custom)" -> "DeepSeek"
                "chatgpt (api)" -> "ChatGPT"
                else -> engineName
            }

            builder.setTitle("配置 $engineDisplayName API")
            builder.setMessage("要使用 $engineDisplayName AI 对话功能，需要先配置 API 密钥。\n\n配置完成后，您就可以开始使用 AI 对话功能了。")
            builder.setCancelable(true)

            // 设置按钮
            builder.setPositiveButton("去配置") { _, _ ->
                openApiSettings(engineName)
            }

            builder.setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }

            builder.setNeutralButton("稍后配置") { dialog, _ ->
                // 直接进入页面，让用户在页面内看到配置引导
                val query = searchInput?.text.toString()
                val activeIndex = determineActiveWebViewIndex()
                service.performSearchInWebView(activeIndex, query, engineName)
                dialog.dismiss()
            }

            val dialog = builder.create()
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示API配置对话框失败", e)
            // 如果对话框显示失败，直接打开设置
            openApiSettings(engineName)
        }
    }

    /**
     * 打开API设置页面
     */
    private fun openApiSettings(engineName: String) {
        try {
            val intent = Intent(context, com.example.aifloatingball.SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("highlight_section", "ai_settings")
                putExtra("highlight_engine", engineName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开API设置失败", e)
            Toast.makeText(context, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }
}