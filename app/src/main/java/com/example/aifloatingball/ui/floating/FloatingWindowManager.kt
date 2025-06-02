package com.example.aifloatingball.ui.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.webview.CustomWebView

/**
 * 浮动窗口管理器，负责创建和管理浮动窗口
 */
class FloatingWindowManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var webViewContainer: LinearLayout? = null
    private var mainScrollView: HorizontalScrollView? = null
    
    // 新增: 持有在XML中定义的WebView实例
    private var firstWebView: CustomWebView? = null
    private var secondWebView: CustomWebView? = null
    private var thirdWebView: CustomWebView? = null
    
    /**
     * 初始化窗口管理器
     */
    init {
        initializeWindowManager()
    }
    
    /**
     * 初始化WindowManager和布局参数
     */
    private fun initializeWindowManager() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }
    
    /**
     * 创建浮动窗口并添加到WindowManager
     */
    fun createFloatingWindow() {
        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.layout_dual_floating_webview, null)
        
        // 初始化视图
        webViewContainer = floatingView?.findViewById(R.id.dual_webview_container)
        
        // 新增: 获取在XML中定义的WebView实例
        firstWebView = floatingView?.findViewById(R.id.first_floating_webview)
        secondWebView = floatingView?.findViewById(R.id.second_floating_webview)
        thirdWebView = floatingView?.findViewById(R.id.third_floating_webview)
        
        // 找到主要的HorizontalScrollView（第一个HorizontalScrollView）
        // Note: The original logic to find mainScrollView seems a bit fragile.
        // Consider giving the HorizontalScrollView a specific ID in XML if it's critical.
        // For now, assuming dual_webview_container's parent or similar might contain it if needed for scrolling.
        // Or, if it's the HorizontalScrollView containing dual_webview_container:
        // mainScrollView = floatingView?.findViewById(R.id.id_of_horizontal_scroll_view_containing_webviews)


        // --- 获取并设置顶部控制栏按钮的监听器 ---
        val searchInput = floatingView?.findViewById<android.widget.EditText>(R.id.dual_search_input)
        val saveEnginesButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_save_engines)
        val switchNormalModeButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_switch_normal)
        val toggleLayoutButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_toggle_layout)
        val singleWindowButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_single_window)
        val windowCountButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_window_count)
        val windowCountToggleText = floatingView?.findViewById<android.widget.TextView>(R.id.window_count_toggle)
        val closeButton = floatingView?.findViewById<android.widget.ImageButton>(R.id.btn_dual_close)

        // 初始化窗口数量显示
        (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.let {
            val initialCount = it.getCurrentWindowCount()
            windowCountToggleText?.text = initialCount.toString()
            android.util.Log.d("FloatingWindowManager", "初始化窗口数量显示为: $initialCount")
        }

        // 1. 关闭按钮
        closeButton?.setOnClickListener {
            android.util.Log.d("FloatingWindowManager", "关闭按钮被点击")
            if (context is com.example.aifloatingball.service.DualFloatingWebViewService) {
                (context as com.example.aifloatingball.service.DualFloatingWebViewService).stopSelf()
            } else {
                android.util.Log.w("FloatingWindowManager", "Context is not DualFloatingWebViewService, cannot stop service directly.")
                // Fallback: just remove the window, though the service might continue.
                // removeFloatingWindow() 
            }
        }

        // 2. 搜索功能 (处理 EditText 的 IME Action)
        searchInput?.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                android.util.Log.d("FloatingWindowManager", "搜索操作触发: $query")
                // TODO: Implement search logic. Needs to call a method in DualFloatingWebViewService.
                // Example: (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.handleSearchFromInput(query)
                true 
            } else {
                false
            }
        }

        // 3. 保存搜索引擎按钮
        saveEnginesButton?.setOnClickListener {
            android.util.Log.d("FloatingWindowManager", "保存搜索引擎按钮被点击")
            val query = searchInput?.text.toString()
            if (!query.isNullOrEmpty()) {
                android.util.Log.d("FloatingWindowManager", "执行搜索 (from saveEnginesButton): $query")
                // TODO: Implement search logic, possibly reusing the one for IME_ACTION_SEARCH.
                // Example: (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.handleSearchFromInput(query)
            } else {
                // TODO: Implement actual "save engines" logic if different from search.
                 android.util.Log.d("FloatingWindowManager", "Query empty, only save engines logic if any.")
            }
        }

        // 4. 切换到普通模式按钮
        switchNormalModeButton?.setOnClickListener {
            android.util.Log.d("FloatingWindowManager", "切换到普通模式按钮被点击")
            // TODO: Implement switch to normal mode logic.
            // Example: (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.switchToNormalMode()
        }

        // 5. 切换布局按钮
        toggleLayoutButton?.setOnClickListener {
            android.util.Log.d("FloatingWindowManager", "切换布局按钮被点击")
            // TODO: Implement toggle layout logic.
            // Example: (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.toggleWebViewLayout()
        }

        // 6. 单窗口模式按钮
        singleWindowButton?.setOnClickListener {
            android.util.Log.d("FloatingWindowManager", "单窗口模式按钮被点击")
            // TODO: Implement single window mode logic.
            // Example: (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.switchToSingleWindow()
        }

        // 7. 窗口数量切换按钮
        windowCountButton?.setOnClickListener {
            android.util.Log.d("FloatingWindowManager", "窗口数量切换按钮被点击")
            (context as? com.example.aifloatingball.service.DualFloatingWebViewService)?.let {
                val newCount = it.toggleAndReloadWindowCount()
                windowCountToggleText?.text = newCount.toString()
                android.util.Log.d("FloatingWindowManager", "窗口数量已更新为: $newCount")
            }
        }
        
        windowManager?.addView(floatingView, params)
    }
    
    /**
     * 获取WebView容器
     */
    fun getWebViewContainer(): LinearLayout? = webViewContainer
    
    /**
     * 新增: 获取在XML中定义的WebView实例列表
     * 这将用于初始化WebViewManager
     */
    fun getXmlDefinedWebViews(): List<CustomWebView?> {
        return listOf(firstWebView, secondWebView, thirdWebView)
    }
    
    /**
     * 重置滚动位置
     */
    fun resetScrollPosition() {
        // 由于我们没有找到SeekBar，这里只重置HorizontalScrollView的位置
        mainScrollView?.scrollTo(0, 0)
    }
    
    /**
     * 移除浮动窗口
     */
    fun removeFloatingWindow() {
        floatingView?.let { windowManager?.removeView(it) }
    }
} 