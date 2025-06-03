package com.example.aifloatingball.ui.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import com.example.aifloatingball.R
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.ui.webview.CustomWebView
import kotlin.math.abs

/**
 * 浮动窗口管理器，负责创建和管理浮动窗口
 */
class FloatingWindowManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    var floatingView: View? = null // 改为 var 并设为 public 或 internal 以便 service 访问
    var params: WindowManager.LayoutParams? = null // 改为 var 并设为 public 或 internal
    
    private var webViewContainer: LinearLayout? = null
    private var mainScrollView: HorizontalScrollView? = null
    
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

    // 用于拖动窗口
    private var isDragging = false
    private var lastDragX: Float = 0f
    private var lastDragY: Float = 0f
    
    init {
        initializeWindowManager()
        gestureDetector = GestureDetector(context, GestureListener())
    }
    
    private fun initializeWindowManager() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 初始大小可以根据需要调整，例如屏幕的一半
        val displayMetrics = context.resources.displayMetrics
        val initialWindowWidth = (displayMetrics.widthPixels * 0.8).toInt()
        val initialWindowHeight = (displayMetrics.heightPixels * 0.6).toInt()

        params = WindowManager.LayoutParams(
            initialWindowWidth,
            initialWindowHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            // 移除 FLAG_NOT_FOCUSABLE 以允许EditText接收焦点
            // 添加 FLAG_LAYOUT_NO_LIMITS 以允许窗口超出屏幕边界
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START //左上角对齐
            x = 100 // 初始 X 位置
            y = 100 // 初始 Y 位置
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingWindow() {
        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.layout_dual_floating_webview, null)
        
        webViewContainer = floatingView?.findViewById(R.id.dual_webview_container)
        firstWebView = floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = floatingView?.findViewById(R.id.third_floating_webview)
        
        val searchInput = floatingView?.findViewById<android.widget.EditText>(R.id.dual_search_input)
        val saveEnginesButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_save_engines)
        val switchNormalModeButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_switch_normal)
        val toggleLayoutButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_toggle_layout)
        val singleWindowButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_single_window)
        val windowCountButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_window_count)
        val windowCountToggleText = floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)
        val closeButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_dual_close)
        val resizeHandle = floatingView?.findViewById<View>(R.id.dual_resize_handle)
        val topControlBar = floatingView?.findViewById<LinearLayout>(R.id.top_control_bar)


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
            params?.flags = if (hasFocus) {
                (params?.flags ?: 0) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                (params?.flags ?: 0) or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager?.updateViewLayout(floatingView, params)
        }


        saveEnginesButton?.setOnClickListener {
            val query = searchInput?.text.toString()
             (context as? DualFloatingWebViewService)?.performSearch(query)
        }

        switchNormalModeButton?.setOnClickListener {
            // (context as? DualFloatingWebViewService)?.switchToNormalMode()
        }

        toggleLayoutButton?.setOnClickListener {
            // (context as? DualFloatingWebViewService)?.toggleWebViewLayout()
        }

        singleWindowButton?.setOnClickListener {
            // (context as? DualFloatingWebViewService)?.switchToSingleWindowMode()
        }

        windowCountButton?.setOnClickListener {
            (context as? DualFloatingWebViewService)?.let {
                val newCount = it.toggleAndReloadWindowCount()
                windowCountToggleText?.text = newCount.toString()
            }
        }

        resizeHandle?.setOnTouchListener { _, event ->
            handleResize(event)
            true // 表明事件已处理
        }

        // 为整个浮动窗口（或特定拖动区域）设置触摸监听器以实现拖动
        // 这里我们使用 topControlBar 作为拖动区域
        topControlBar?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event) // 将触摸事件传递给GestureDetector
            handleDrag(event) // 同时也处理拖动逻辑
            true // 事件已处理
        }
        
        windowManager?.addView(floatingView, params)
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
                    if (newWidth > 0 && newHeight > 0) {
                        p.width = newWidth
                        p.height = newHeight
                        windowManager?.updateViewLayout(floatingView, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // 可选：保存最终大小或执行其他操作
                }
            }
        }
    }
    
    private fun handleDrag(event: MotionEvent) {
        params?.let { p ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    lastDragX = event.rawX
                    lastDragY = event.rawY
                    initialX = p.x
                    initialY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val deltaX = event.rawX - lastDragX
                        val deltaY = event.rawY - lastDragY
                        p.x = initialX + deltaX.toInt()
                        p.y = initialY + deltaY.toInt()
                        windowManager?.updateViewLayout(floatingView, p)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    isDragging = false
                }
            }
        }
    }


    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent): Boolean {
            // ACTION_DOWN 必须返回 true 才能接收后续事件
            isDragging = false // 重置拖动状态，因为单击可能不是拖动开始
            lastDragX = e.rawX
            lastDragY = e.rawY
            params?.let {
                initialX = it.x
                initialY = it.y
            }
            return true
        }
        
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // 处理单击事件，例如让输入框获取焦点
            floatingView?.findViewById<android.widget.EditText>(R.id.dual_search_input)?.requestFocus()
            return true
        }


        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // 在 onScroll 中处理拖动逻辑更为平滑
            // e1 是 ACTION_DOWN 事件，e2 是当前 MOVE 事件
            if (e1 == null) return false // 安全检查

            isDragging = true // 标记为正在拖动
            params?.let { p ->
                // 计算方式改为基于初始点击位置和当前事件位置的差值
                p.x = initialX + (e2.rawX - e1.rawX).toInt()
                p.y = initialY + (e2.rawY - e1.rawY).toInt()
                windowManager?.updateViewLayout(floatingView, p)
            }
            return true
        }
    }

    fun getWebViewContainer(): LinearLayout? = webViewContainer
    
    fun getXmlDefinedWebViews(): List<CustomWebView?> {
        return listOf(firstWebView, secondWebView, thirdWebView)
    }
    
    fun resetScrollPosition() {
        mainScrollView?.scrollTo(0, 0)
    }
    
    fun removeFloatingWindow() {
        floatingView?.let {
            try {
                 windowManager?.removeView(it)
            } catch (e: Exception) {
                // Handle exception, e.g. if view was already removed
                android.util.Log.e("FloatingWindowManager", "Error removing view: ${e.message}")
            }
        }
        floatingView = null
        windowManager = null // 清理 windowManager 引用
    }
} 