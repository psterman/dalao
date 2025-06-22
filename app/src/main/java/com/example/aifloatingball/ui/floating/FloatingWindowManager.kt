package com.example.aifloatingball.ui.floating

import android.annotation.SuppressLint
import android.content.Context
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

interface WindowStateCallback {
    fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int)
}

/**
 * 浮动窗口管理器，负责创建和管理浮动窗口
 */
class FloatingWindowManager(
    private val context: Context,
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

    private val gestureDetector: GestureDetector

    private var isDragging = false
    private var lastDragX: Float = 0f
    private var lastDragY: Float = 0f
    private var lastActiveWebViewIndex = 0 // 追踪当前活动的WebView
    
    private var originalWindowHeight: Int = 0
    private var originalWindowY: Int = 0
    private var isKeyboardShowing: Boolean = false

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
        gestureDetector = GestureDetector(context, GestureListener())
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
    }
    
    // 获取状态栏高度
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    private fun initializeWindowManager() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = context.resources.displayMetrics
        val statusBarHeight = getStatusBarHeight() // 获取状态栏高度

        val defaultWidth = displayMetrics.widthPixels 
        // 计算可用屏幕高度 (减去状态栏)
        val usableScreenHeight = displayMetrics.heightPixels - statusBarHeight

        // 设定一个默认的窗口高度，例如可用高度的80%，以便可以居中
        val defaultHeight = (usableScreenHeight * 0.8f).toInt() 
        val defaultX = 0 
        // 计算默认Y坐标，使其垂直居中于可用屏幕区域
        val defaultY = statusBarHeight + (usableScreenHeight - defaultHeight) / 2 

        val savedX = sharedPreferences.getInt(DualFloatingWebViewService.KEY_WINDOW_X, defaultX)
        val savedY = sharedPreferences.getInt(DualFloatingWebViewService.KEY_WINDOW_Y, defaultY)
        val savedWidth = sharedPreferences.getInt(DualFloatingWebViewService.KEY_WINDOW_WIDTH, defaultWidth)
        val savedHeight = sharedPreferences.getInt(DualFloatingWebViewService.KEY_WINDOW_HEIGHT, defaultHeight)

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
        }

        originalWindowHeight = savedHeight
        originalWindowY = savedY
        Log.d("FloatingWindowManager", "初始 originalWindowHeight: $originalWindowHeight, originalWindowY: $originalWindowY")
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingWindow(): List<CustomWebView?> {
        Log.d(TAG, "FloatingWindowManager: 创建浮动窗口 createFloatingWindow()")
        val inflater = LayoutInflater.from(context)
        _floatingView = inflater.inflate(R.layout.layout_dual_floating_webview, null)
        
        webViewContainer = _floatingView?.findViewById(R.id.dual_webview_container)
        webviewsScrollContainer = _floatingView?.findViewById(R.id.webviews_scroll_container)
        firstWebView = _floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = _floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = _floatingView?.findViewById(R.id.third_floating_webview)
        
        searchInput = _floatingView?.findViewById<EditText>(R.id.dual_search_input)
        
        // 核心修复：实施新的、精确的焦点管理逻辑
        // 1. 移除旧的、可能冲突的监听器
        searchInput?.onFocusChangeListener = null
        
        // 2. 当用户触摸输入框时，才使窗口可聚焦以弹出键盘
        searchInput?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                setFloatingWindowFocusable(true)
            }
            // 返回false，以允许系统处理默认的触摸事件（如光标定位）
            false
        }
        
        // 3. 当输入框失去焦点时，将整个窗口恢复为不可聚焦状态
        searchInput?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showPasteButton()
            } else {
                setFloatingWindowFocusable(false)
                hidePasteButton()
            }
        }
        
        val saveEnginesButton = _floatingView?.findViewById<ImageButton>(R.id.btn_save_engines)
        val windowCountButton = _floatingView?.findViewById<ImageButton>(R.id.btn_window_count)
        val windowCountToggleText = _floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)
        val closeButton = _floatingView?.findViewById<ImageButton>(R.id.btn_dual_close)
        val resizeHandle = _floatingView?.findViewById<View>(R.id.dual_resize_handle)
        val topControlBar = _floatingView?.findViewById<LinearLayout>(R.id.top_control_bar)

        // 新增：获取全局AI引擎容器
        val globalAiContainer = _floatingView?.findViewById<LinearLayout>(R.id.global_ai_engine_container)
        // 新增：获取全局标准引擎容器
        val globalStdContainer = _floatingView?.findViewById<LinearLayout>(R.id.global_standard_engine_container)

        (context as? DualFloatingWebViewService)?.let {
            val initialCount = it.getCurrentWindowCount()
            windowCountToggleText?.text = initialCount.toString()
            android.util.Log.d("FloatingWindowManager", "初始化窗口数量显示为: $initialCount")
        }

        closeButton?.setOnClickListener {
            (context as? DualFloatingWebViewService)?.stopSelf()
        }

        searchInput?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                (context as? DualFloatingWebViewService)?.performSearch(query)
                true 
            } else {
                false
            }
        }

        searchInput?.setOnLongClickListener {
            textSelectionManager.showEditTextSelectionMenu(it as EditText)
            true
        }

        saveEnginesButton?.setOnClickListener {
            val query = searchInput?.text.toString()
             (context as? DualFloatingWebViewService)?.performSearch(query)
        }

        windowCountButton?.setOnClickListener {
            (context as? DualFloatingWebViewService)?.let {
                val newCount = it.toggleAndReloadWindowCount()
                windowCountToggleText?.text = newCount.toString()
            }
        }

        resizeHandle?.setOnTouchListener { _, event ->
            handleResize(event)
            true
        }
        
        // 移除 topControlBar 的触摸监听器，以允许子视图（按钮、输入框）接收点击事件。
        // topControlBar?.setOnTouchListener { _, event ->
        //     gestureDetector.onTouchEvent(event)
        //     handleDrag(event) 
        //     true
        // }

        // 新增：将拖动逻辑应用到 webviewsScrollContainer
        webviewsScrollContainer?.setOnTouchListener { _, event ->
            // 首先，让手势检测器处理事件
            val handledByGesture = gestureDetector.onTouchEvent(event)

            // 如果是手指抬起事件，并且之前正在拖动，就保存状态
            if (event.action == MotionEvent.ACTION_UP) {
                if (isDragging) {
                    params?.let { p ->
                        windowStateCallback.onWindowStateChanged(p.x, p.y, p.width, p.height)
                    }
                    isDragging = false // 拖动结束
                }
            }

            // 如果是拖动事件，就消费掉。否则，让子视图（WebView）处理。
            handledByGesture
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

        Log.d(TAG, "Determined active WebView index: $activeIndex (ScrollX: $scrollX, Center: $visibleCenter)")
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
                (context as? DualFloatingWebViewService)?.performSearchInWebView(activeIndex, query, engine.name)
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
                (context as? DualFloatingWebViewService)?.performSearchInWebView(activeIndex, query, engine.name)
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
                (context as? DualFloatingWebViewService)?.performSearchInWebView(webViewIndex, query, engine.name)
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

    private fun handleResize(event: MotionEvent) {
        params?.let { p ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = p.width
                    initialHeight = p.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val newWidth = initialWidth + (event.rawX - initialTouchX).toInt()
                    val newHeight = initialHeight + (event.rawY - initialTouchY).toInt()
                    if (newWidth > MIN_WIDTH && newHeight > MIN_HEIGHT) { // Assuming MIN_WIDTH/HEIGHT are defined
                        p.width = newWidth
                        p.height = newHeight
                        windowManager?.updateViewLayout(_floatingView, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    windowStateCallback.onWindowStateChanged(p.x, p.y, p.width, p.height)
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
        val webViews = listOfNotNull(firstWebView, secondWebView, thirdWebView)
        val scrollContainer = _floatingView?.findViewById<HorizontalScrollView>(R.id.webviews_scroll_container)

        // 创建一个统一的监听器来处理所有"应隐藏键盘"的交互
        val hideKeyboardListener = View.OnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (searchInput?.isFocused == true) {
                    // 当用户触摸网页区域时，清除搜索框的焦点
                    // 这将触发上面的 OnFocusChangeListener 来隐藏键盘
                    searchInput?.clearFocus()
                    Log.d(TAG, "Web content area touched, clearing search input focus.")

                    // 记录哪个WebView被触摸了
                    val touchedIndex = webViews.indexOf(view)
                    if (touchedIndex != -1) {
                        lastActiveWebViewIndex = touchedIndex
                        Log.d(TAG, "Last active WebView index set to: $lastActiveWebViewIndex")
                    }

                    // 消费此事件以防止立即开始滚动
                    return@OnTouchListener true
                }
            }
            // 允许其他事件（如滚动）正常进行
            false
        }

        // 将此监听器应用于所有WebView和其滚动容器
        webViews.forEach { webView ->
            webView.setOnTouchListener(hideKeyboardListener)
        }
        scrollContainer?.setOnTouchListener(hideKeyboardListener)
    }

    /**
     * 新增：设置滚动时对WebView焦点的管理，以防止内部输入框自动激活
     */
    private fun setupWebViewFocusManagementOnScroll() {
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
        (context as? DualFloatingWebViewService)?.stopSelf()
        return true // 事件已处理（通过关闭窗口）
    }

    /**
     * 新增：公共方法，用于从外部设置搜索输入框的文本
     */
    fun setSearchInputText(text: String) {
        val searchInput = floatingView?.findViewById<EditText>(R.id.dual_search_input)
        searchInput?.setText(text)
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
                        (_floatingView as? ViewGroup)?.removeView(it)
                        pasteButton = null
                    }.start()
            }
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pasteData = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!pasteData.isNullOrBlank()) {
            val editText = searchInput ?: return
            val start = editText.selectionStart.coerceAtLeast(0)
            val end = editText.selectionEnd.coerceAtLeast(0)
            editText.text.replace(start, end, pasteData)
            Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
        }
    }

    fun getWebViewContainer(): LinearLayout? = webViewContainer
    
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            isDragging = false
            params?.let {
                initialX = it.x
                initialY = it.y
            }
            return true // 必须返回true才能继续接收后续事件
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // 因为拖动和点击区域已经分离，所以不需要在这里处理点击逻辑
            return super.onSingleTapUp(e)
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null) return false

            // 核心逻辑：判断是垂直拖动还是水平滑动
            // 首次进入onScroll时，isDragging是false
            if (!isDragging) {
                // 只有当垂直滚动的绝对距离大于水平滚动的绝对距离时，我们才认定为拖动窗口
                if (abs(e2.rawY - e1.rawY) > abs(e2.rawX - e1.rawX)) {
                    isDragging = true
                } else {
                    // 否则，我们认为是水平滑动，不进行处理，让HorizontalScrollView接管
                    return false
                }
            }
            
            // 一旦确定是拖动状态，就更新窗口位置
            if (isDragging) {
                params?.let { p ->
                    p.x = initialX + (e2.rawX - e1.rawX).toInt()
                    p.y = initialY + (e2.rawY - e1.rawY).toInt()
                    windowManager?.updateViewLayout(_floatingView, p)
                }
                return true // 消费了拖动事件
            }

            return false
        }
    }

    fun getXmlDefinedWebViews(): List<CustomWebView?> {
        return listOf(firstWebView, secondWebView, thirdWebView)
    }

    companion object {
        private const val MIN_WIDTH = 200
        private const val MIN_HEIGHT = 200
        private const val TAG = "FloatingWindowManager"
    }
} 