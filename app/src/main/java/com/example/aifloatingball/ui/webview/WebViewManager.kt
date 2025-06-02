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
    private val xmlDefinedWebViews: List<CustomWebView?>
) {
    companion object {
        private const val TAG = "WebViewManager"
    }
    
    private val webViewFactory = WebViewFactory(context)
    val textSelectionManager = webViewFactory.textSelectionManager
    private var activeWebView: CustomWebView? = null
    
    private val webViews: List<CustomWebView> = xmlDefinedWebViews.filterNotNull()
    
    init {
        this.webViews.forEachIndexed { index, webView -> 
            Log.d(TAG, "Initializing XML defined WebView at index: $index, ID: ${webView.id}")
            webView.setTextSelectionManager(textSelectionManager)
            webView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus && v is CustomWebView) {
                    activeWebView = v
                    Log.d(TAG, "WebView ${webView.id} 获得焦点，设置为活动WebView")
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
        webView.loadUrl(url)
        activeWebView = webView
        Log.d(TAG, "加载URL到WebView索引 $index (ID: ${webView.id}): $url")
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
        for (currentWebViewInstance in webViews) {
            currentWebViewInstance.setOnLongClickListener { view ->
                if (view is CustomWebView) {
                    activeWebView = view
                    Log.d(TAG, "WebView ${view.id} 长按，设置为活动WebView")
                }
                false
            }
        }
    }
} 