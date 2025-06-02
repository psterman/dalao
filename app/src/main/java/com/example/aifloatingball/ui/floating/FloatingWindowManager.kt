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

/**
 * 浮动窗口管理器，负责创建和管理浮动窗口
 */
class FloatingWindowManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var webViewContainer: LinearLayout? = null
    private var mainScrollView: HorizontalScrollView? = null
    
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
        
        // 找到主要的HorizontalScrollView（第一个HorizontalScrollView）
        val frameLayout = floatingView?.findViewById<View>(android.R.id.content)?.parent as? ViewGroup
        if (frameLayout != null && frameLayout.childCount > 0) {
            for (i in 0 until frameLayout.childCount) {
                val child = frameLayout.getChildAt(i)
                if (child is HorizontalScrollView) {
                    mainScrollView = child
                    break
                }
            }
        }
        
        windowManager?.addView(floatingView, params)
    }
    
    /**
     * 获取WebView容器
     */
    fun getWebViewContainer(): LinearLayout? = webViewContainer
    
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