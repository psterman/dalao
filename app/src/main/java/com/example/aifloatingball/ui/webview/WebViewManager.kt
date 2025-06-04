package com.example.aifloatingball.ui.webview

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.LinearLayout
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.utils.WebViewInputHelper

/**
 * WebView管理器，负责WebView的创建、加载和销毁
 */
class WebViewManager(
    private val context: Context, 
    private val xmlDefinedWebViews: List<CustomWebView?>
) {
    companion object {
        private const val TAG = "WebViewManager"
    }
    
    private val webViewFactory = WebViewFactory(context)
    val textSelectionManager = webViewFactory.textSelectionManager
    private var activeWebView: CustomWebView? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val webViewInputHelper = WebViewInputHelper(context, windowManager, null)
    
    private val webViews: List<CustomWebView> = xmlDefinedWebViews.filterNotNull()
    
    init {
        this.webViews.forEachIndexed { index, webView -> 
            Log.d(TAG, "Initializing XML defined WebView at index: $index, ID: ${webView.id}")
            webView.setTextSelectionManager(textSelectionManager)
            
            // 设置输入支持
            webViewInputHelper.prepareWebViewForInput(webView)
            
            // 设置焦点变化监听器
            webView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus && v is CustomWebView) {
                    // 更新活动WebView
                    if (activeWebView != v) {
                        activeWebView?.clearFocus()
                        activeWebView = v
                        Log.d(TAG, "WebView ${v.id} 获得焦点，设置为活动WebView")
                    }
                    
                    // 确保输入法可用
                    webViewInputHelper.ensureInputMethodAvailable(v)
                }
            }
            
            // 设置触摸监听器
            webView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (v is CustomWebView) {
                            // 更新活动WebView
                            if (activeWebView != v) {
                                activeWebView?.clearFocus()
                                activeWebView = v
                                v.requestFocus()
                                Log.d(TAG, "WebView ${v.id} 被触摸，设置为活动WebView")
                            }
                            
                            // 确保可以获取焦点
                            webViewInputHelper.toggleWindowFocusableFlag(true)
                        }
                        false
                    }
                    else -> false
                }
            }
        }
    }
    
    /**
     * 修改: 加载URL到指定索引的XML定义的WebView中
     * @return 返回被操作的 CustomWebView，如果索引有效
     */
    fun loadUrlInWebView(index: Int, url: String): CustomWebView? {
        if (index < 0 || index >= webViews.size) {
            Log.e(TAG, "loadUrlInWebView: Invalid index $index, total WebViews: ${webViews.size}")
            return null
        }
        val webView = webViews[index]
        // 增加更详细的日志，记录加载前的URL和WebView信息
        Log.d(TAG, "正在加载URL到WebView索引 $index (ID: ${webView.id}): $url")
        Log.d(TAG, "WebView状态：可见性=${webView.visibility}, 是否启用=${webView.isEnabled}, 当前URL=${webView.url}")
        
        try {
            // 确保WebView设置正确
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            
            // 加载URL
            webView.loadUrl(url)
            Log.d(TAG, "URL加载请求已发送: $url")
            
            // 设置为活动WebView
            activeWebView = webView
        } catch (e: Exception) {
            Log.e(TAG, "加载URL到WebView时出错: ${e.message}", e)
        }
        
        return webView
    }

    /**
     * 显示或隐藏指定索引的WebView及其父容器（边框FrameLayout）
     */
    fun setWebViewVisibility(index: Int, visible: Boolean) {
        if (index < 0 || index >= webViews.size) {
            Log.e(TAG, "setWebViewVisibility: Invalid index $index for ${webViews.size} WebViews.")
            return
        }
        val webView = webViews[index]
        (webView.parent as? View)?.visibility = if (visible) View.VISIBLE else View.GONE
        Log.d(TAG, "WebView索引 $index (ID: ${webView.id}) 可见性设置为: $visible")
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
     * 修改: 清除指定数量的WebView的内容，并根据需要隐藏它们
     */
    fun clearAndHideWebViews(countToKeep: Int) {
        Log.d(TAG, "clearAndHideWebViews: 保留 $countToKeep 个WebView")
        webViews.forEachIndexed { index, webView ->
            if (index < countToKeep) {
            } else {
                webView.loadUrl("about:blank")
                (webView.parent as? View)?.visibility = View.GONE
            }
        }
        if (countToKeep > 0 && webViews.isNotEmpty()) {
            activeWebView = webViews[0]
        } else {
            activeWebView = null
        }
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
     * 设置所有WebView的长按监听器
     */
    fun setupLongPressListeners() {
        webViews.forEach { webView ->
            webView.setOnLongClickListener { view ->
                // 处理长按事件
                true
            }
        }
    }

    fun setActiveWebView(webView: CustomWebView?) {
        if (webView != null && webViews.contains(webView)) {
            activeWebView?.clearFocus()
            activeWebView = webView
            webView.requestFocus()
            Log.d(TAG, "WebView ${webView.id} 被设置为活动WebView")
        }
    }
    
    fun clearActiveWebView() {
        activeWebView?.clearFocus()
        activeWebView = null
        Log.d(TAG, "清除活动WebView")
    }
} 