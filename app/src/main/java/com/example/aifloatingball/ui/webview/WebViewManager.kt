package com.example.aifloatingball.ui.webview

import android.content.Context
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import com.example.aifloatingball.ui.text.TextSelectionManager

/**
 * WebView管理器，负责WebView的创建、加载和销毁
 */
class WebViewManager(
    private val context: Context, 
    private val container: LinearLayout?
) {
    companion object {
        private const val TAG = "WebViewManager"
    }
    
    private val webViews = mutableListOf<CustomWebView>()
    private val webViewFactory = WebViewFactory(context)
    private val textSelectionManager = webViewFactory.getTextSelectionManager()
    private var activeWebView: CustomWebView? = null
    
    init {
        // 监听容器的触摸事件，以识别当前活动的WebView
        container?.setOnTouchListener { _, event ->
            for (webView in webViews) {
                val location = IntArray(2)
                webView.getLocationOnScreen(location)
                val left = location[0]
                val top = location[1]
                val right = left + webView.width
                val bottom = top + webView.height
                
                if (event.rawX >= left && event.rawX <= right && 
                    event.rawY >= top && event.rawY <= bottom) {
                    if (activeWebView != webView) {
                        Log.d(TAG, "切换活动WebView")
                        activeWebView = webView
                    }
                    break
                }
            }
            false
        }
    }
    
    /**
     * 添加一个新WebView并加载URL
     */
    fun addWebView(url: String): CustomWebView {
        val webView = webViewFactory.createWebView()
        
        // 确保WebView使用相同的文本选择管理器
        webView.setTextSelectionManager(textSelectionManager)
        
        webViews.add(webView)
        container?.addView(webView)
        webView.loadUrl(url)
        
        // 设置为活动WebView
        activeWebView = webView
        
        // 设置WebView的焦点变化监听器
        webView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus && v is CustomWebView) {
                activeWebView = v
                Log.d(TAG, "WebView获得焦点，设置为活动WebView")
            }
        }
        
        Log.d(TAG, "添加WebView并加载URL: $url")
        return webView
    }
    
    /**
     * 获取当前活动的WebView
     */
    fun getActiveWebView(): CustomWebView? {
        return activeWebView
    }
    
    /**
     * 获取WebView列表
     */
    fun getWebViews(): List<CustomWebView> = webViews
    
    /**
     * 清除所有WebView
     */
    fun clearWebViews() {
        Log.d(TAG, "清除所有WebView")
        webViews.forEach { it.destroy() }
        webViews.clear()
        container?.removeAllViews()
        activeWebView = null
    }
    
    /**
     * 销毁所有WebView
     */
    fun destroyAll() {
        Log.d(TAG, "销毁所有WebView")
        webViews.forEach { it.destroy() }
        activeWebView = null
    }
    
    /**
     * 根据索引获取WebView
     */
    fun getWebViewAt(index: Int): CustomWebView? {
        return if (index >= 0 && index < webViews.size) webViews[index] else null
    }
    
    /**
     * 获取WebView数量
     */
    fun getWebViewCount(): Int = webViews.size
    
    /**
     * 获取文本选择管理器
     */
    fun getTextSelectionManager(): TextSelectionManager = textSelectionManager
    
    /**
     * 设置所有WebView的长按监听器
     */
    fun setupLongPressListeners() {
        for (webView in webViews) {
            webView.setOnLongClickListener { view ->
                if (view is WebView) {
                    activeWebView = webView
                    Log.d(TAG, "WebView长按，设置为活动WebView")
                }
                false // 返回false以允许默认的长按处理
            }
        }
    }
} 