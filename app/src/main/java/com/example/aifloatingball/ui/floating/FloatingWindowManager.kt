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
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.ui.webview.CustomWebView
import com.example.aifloatingball.utils.EngineUtil
import kotlin.math.abs
import com.bumptech.glide.Glide

interface WindowStateCallback {
    fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int)
}

/**
 * 浮动窗口管理器，负责创建和管理浮动窗口
 */
class FloatingWindowManager(private val context: Context, private val windowStateCallback: WindowStateCallback) {
    private var windowManager: WindowManager? = null
    private var _floatingView: View? = null
    val floatingView: View?
        get() = _floatingView
    var params: WindowManager.LayoutParams? = null
    
    private var webViewContainer: LinearLayout? = null
    
    private var firstWebView: CustomWebView? = null
    private var secondWebView: CustomWebView? = null
    private var thirdWebView: CustomWebView? = null

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0.toFloat()
    private var initialTouchY: Float = 0.toFloat()
    private var initialWidth: Int = 0
    private var initialHeight: Int = 0

    private lateinit var gestureDetector: GestureDetector

    private var isDragging = false
    private var lastDragX: Float = 0f
    private var lastDragY: Float = 0f
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(DualFloatingWebViewService.PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Define engine key lists
    private val aiEngineKeys = listOf("chatgpt", "claude", "gemini", "wenxin", "chatglm", "qianwen", "xinghuo", "perplexity", "phind", "poe")
    private val standardEngineKeys = listOf("baidu", "google", "bing", "sogou", "360", "quark", "toutiao", "zhihu", "bilibili", "douban", "weibo", "taobao", "jd", "douyin", "xiaohongshu")
    
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingWindow() {
        val inflater = LayoutInflater.from(context)
        _floatingView = inflater.inflate(R.layout.layout_dual_floating_webview, null)
        
        webViewContainer = _floatingView?.findViewById(R.id.dual_webview_container)
        firstWebView = _floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = _floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = _floatingView?.findViewById(R.id.third_floating_webview)
        
        val searchInput = _floatingView?.findViewById<EditText>(R.id.dual_search_input)
        val saveEnginesButton = _floatingView?.findViewById<ImageButton>(R.id.btn_save_engines)
        val switchNormalModeButton = _floatingView?.findViewById<ImageButton>(R.id.btn_switch_normal)
        val toggleLayoutButton = _floatingView?.findViewById<ImageButton>(R.id.btn_toggle_layout)
        val singleWindowButton = _floatingView?.findViewById<ImageButton>(R.id.btn_single_window)
        val windowCountButton = _floatingView?.findViewById<ImageButton>(R.id.btn_window_count)
        val windowCountToggleText = _floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)
        val closeButton = _floatingView?.findViewById<ImageButton>(R.id.btn_dual_close)
        val resizeHandle = _floatingView?.findViewById<View>(R.id.dual_resize_handle)
        val topControlBar = _floatingView?.findViewById<LinearLayout>(R.id.top_control_bar)

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
        
        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            val currentFlags = params?.flags ?: 0
            params?.flags = if (hasFocus) {
                currentFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                currentFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            _floatingView?.let {fv -> params?.let { p -> windowManager?.updateViewLayout(fv, p) } }
        }

        saveEnginesButton?.setOnClickListener {
            val query = searchInput?.text.toString()
             (context as? DualFloatingWebViewService)?.performSearch(query)
        }

        switchNormalModeButton?.setOnClickListener { /* (context as? DualFloatingWebViewService)?.switchToNormalMode() */ }
        toggleLayoutButton?.setOnClickListener { /* (context as? DualFloatingWebViewService)?.toggleWebViewLayout() */ }
        singleWindowButton?.setOnClickListener { /* (context as? DualFloatingWebViewService)?.switchToSingleWindowMode() */ }

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
        
        topControlBar?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            handleDrag(event) 
            true
        }

        // Populate engine icons for individual WebViews (existing logic)
        val firstAiContainer = _floatingView?.findViewById<LinearLayout>(R.id.first_webview_ai_engine_container)
        val firstStdContainer = _floatingView?.findViewById<LinearLayout>(R.id.first_webview_standard_engine_container)
        populateEngineIconsForWebView(0, firstAiContainer, firstStdContainer, searchInput)

        val secondAiContainer = _floatingView?.findViewById<LinearLayout>(R.id.second_webview_ai_engine_container)
        val secondStdContainer = _floatingView?.findViewById<LinearLayout>(R.id.second_webview_standard_engine_container)
        populateEngineIconsForWebView(1, secondAiContainer, secondStdContainer, searchInput)

        val thirdAiContainer = _floatingView?.findViewById<LinearLayout>(R.id.third_webview_ai_engine_container)
        val thirdStdContainer = _floatingView?.findViewById<LinearLayout>(R.id.third_webview_standard_engine_container)
        populateEngineIconsForWebView(2, thirdAiContainer, thirdStdContainer, searchInput)
        
        windowManager?.addView(_floatingView, params)
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
            "chatgpt" -> "openai.com"
            "claude" -> "claude.ai"
            "gemini" -> "gemini.google.com"
            "wenxin" -> "baidu.com" // Wenxin is under baidu.com domain for icons
            "chatglm" -> "zhipuai.cn"
            "qianwen" -> "aliyun.com" // Tongyi Qianwen under aliyun.com
            "xinghuo" -> "xfyun.cn"   // Xunfei Xinghuo
            "perplexity" -> "perplexity.ai"
            "phind" -> "phind.com"
            "poe" -> "poe.com"
            else -> "google.com" // Default domain if key is unknown
        }
    }

    /**
     * 填充指定WebView的搜索引擎图标
     * @param webViewIndex WebView的索引 (0, 1, 2)
     * @param aiContainer 用于AI搜索引擎图标的LinearLayout
     * @param standardContainer 用于标准搜索引擎图标的LinearLayout
     * @param searchInput EditText，用于获取当前搜索框内容，以便点击图标时进行搜索
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun populateEngineIconsForWebView(webViewIndex: Int, aiContainer: LinearLayout?, standardContainer: LinearLayout?, searchInput: EditText?) {
        val service = context as? DualFloatingWebViewService ?: return
        if (!service.isSearchEngineHandlerInitialized()) {
            android.util.Log.e("FloatingWindowManager", "SearchEngineHandler未准备好填充WebView图标")
            return
        }

        val iconSize = dpToPx(32) // 稍微小一点的图标
        val iconMargin = dpToPx(2) // 间距

        // 填充AI引擎图标
        aiContainer?.let {
            it.removeAllViews()
            for (key in aiEngineKeys) {
                val iconUrl = EngineUtil.getEngineIconUrl(key)
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        setMargins(iconMargin, 0, iconMargin, 0)
                    }
                    scaleType = ScaleType.FIT_CENTER
                    Glide.with(context)
                         .load(iconUrl)
                         .placeholder(R.drawable.ic_default_search)
                         .error(R.drawable.ic_default_search)
                         .into(this)
                    contentDescription = key
                    setOnClickListener {
                        val query = searchInput?.text.toString()
                        service.performSearchInWebView(webViewIndex, query, key)
                    }
                }
                it.addView(imageView)
            }
        }

        // 填充标准引擎图标
        standardContainer?.let {
            it.removeAllViews()
            for (key in standardEngineKeys) {
                val iconUrl = EngineUtil.getEngineIconUrl(key)
                val imageView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        setMargins(iconMargin, 0, iconMargin, 0)
                    }
                    scaleType = ScaleType.FIT_CENTER
                    Glide.with(context)
                         .load(iconUrl)
                         .placeholder(R.drawable.ic_default_search)
                         .error(R.drawable.ic_default_search)
                         .into(this)
                    contentDescription = key
                    setOnClickListener {
                        val query = searchInput?.text.toString()
                        service.performSearchInWebView(webViewIndex, query, key)
                    }
                }
                it.addView(imageView)
            }
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
    
    private fun handleDrag(event: MotionEvent) {
        params?.let { p ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true // Mark dragging started
                    lastDragX = event.rawX
                    lastDragY = event.rawY
                    // initialX and initialY are set by GestureListener.onDown
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) { // Only move if dragging
                        // Calculate delta from the point where dragging ACTUALLY started (lastDragX/Y)
                        // not from e1 (original onDown) which might be outdated if onScroll didn't happen immediately.
                        // However, for smooth drag from onScroll, e1 (initial onDown) is better.
                        // The current onScroll uses initialX/Y + (e2.rawX - e1.rawX).
                        // This separate handleDrag might be redundant if GestureListener handles it well.
                        // For now, let's keep it simple, GestureListener.onScroll handles the update.
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) { // Only save state if a drag actually occurred
                         windowStateCallback.onWindowStateChanged(p.x, p.y, p.width, p.height)
                    }
                    isDragging = false // Reset dragging state
                }
            }
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            isDragging = false 
            lastDragX = e.rawX
            lastDragY = e.rawY
            params?.let {
                initialX = it.x
                initialY = it.y
            }
            return true
        }
        
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            _floatingView?.findViewById<EditText>(R.id.dual_search_input)?.requestFocus()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (e1 == null) return false 

            isDragging = true 
            params?.let { p ->
                p.x = initialX + (e2.rawX - e1.rawX).toInt()
                p.y = initialY + (e2.rawY - e1.rawY).toInt()
                windowManager?.updateViewLayout(_floatingView, p)
            }
            return true
        }

        // It's good practice to also save state on ACTION_UP after a scroll.
        // This can be handled in the top-level onTouchEvent or here if onScroll returns true and then ACTION_UP occurs.
        // The current handleDrag's ACTION_UP handles this.
    }

    fun getWebViewContainer(): LinearLayout? = webViewContainer
    
    fun getXmlDefinedWebViews(): List<CustomWebView?> {
        return listOf(firstWebView, secondWebView, thirdWebView)
    }
    
    fun resetScrollPosition() {
        // mainScrollView?.scrollTo(0, 0) // Removed as mainScrollView is removed
    }
    
    fun removeFloatingWindow() {
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

    companion object { // Define MIN_WIDTH and MIN_HEIGHT if needed for resize
        private const val MIN_WIDTH = 200 
        private const val MIN_HEIGHT = 150
    }
} 