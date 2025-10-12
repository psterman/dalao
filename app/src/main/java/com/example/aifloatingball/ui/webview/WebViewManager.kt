package com.example.aifloatingball.ui.webview

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.Toast
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.utils.WebViewInputHelper
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.ui.floating.FloatingWindowManager
import com.example.aifloatingball.ui.webview.CustomWebView
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.engine.SearchEngineHandler

/**
 * WebView管理器，负责WebView的创建、加载和销毁
 */
class WebViewManager(
    private val context: Context, 
    private val xmlDefinedWebViews: List<CustomWebView?>,
    private val floatingWindowManager:  FloatingWindowManager
) : LinkMenuListener {
    companion object {
        private const val TAG = "WebViewManager"
    }
    
    private val webViewFactory = WebViewFactory(context)
    val textSelectionManager = webViewFactory.textSelectionManager
    private var activeWebView: CustomWebView? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val webViewInputHelper = WebViewInputHelper(context, windowManager, floatingWindowManager.floatingView)
    private val settingsManager = SettingsManager.getInstance(context)
    private val searchEngineHandler = SearchEngineHandler(settingsManager)
    
    private val webViews: List<CustomWebView> = xmlDefinedWebViews.filterNotNull()

    /**
     * 获取第一个WebView（用于外部访问）
     */
    fun getFirstWebView(): CustomWebView? = webViews.firstOrNull()

    /**
     * 获取所有WebView（用于外部访问）
     */
    fun getAllWebViews(): List<CustomWebView> = webViews
    
    init {
        this.webViews.forEachIndexed { index, webView -> 
            Log.d(TAG, "Initializing XML defined WebView at index: $index, ID: ${webView.id}")
            webView.setTextSelectionManager(textSelectionManager)
            webView.linkMenuListener = this // 设置链接菜单监听器
            
            // 设置输入支持
            webViewInputHelper.prepareWebViewForInput(webView)
            
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
                            
                            // 确保可以获取焦点 - 这将由 FloatingWindowManager 处理
                            // webViewInputHelper.toggleWindowFocusableFlag(true)
                        }
                        false
                    }
                    else -> false
                }
            }
        }
    }
    
    /**
     * 执行搜索
     */
    fun performSearch(query: String, engineKey: String) {
        Log.d(TAG, "performSearch: query='$query', engineKey='$engineKey'")
        
        try {
            // 使用SearchEngineHandler构建搜索URL
            val searchUrl = searchEngineHandler.getSearchUrl(query, engineKey)
            Log.d(TAG, "Generated search URL: $searchUrl")
            
            // 在活动WebView中加载搜索URL
            activeWebView?.let { webView ->
                webView.loadUrl(searchUrl)
                Log.d(TAG, "Search URL loaded in active WebView: ${webView.id}")
            } ?: run {
                // 如果没有活动WebView，使用第一个可用的WebView
                if (webViews.isNotEmpty()) {
                    val firstWebView = webViews[0]
                    firstWebView.loadUrl(searchUrl)
                    activeWebView = firstWebView
                    Log.d(TAG, "Search URL loaded in first WebView: ${firstWebView.id}")
                } else {
                    Log.e(TAG, "No WebView available for search")
                    Toast.makeText(context, "无可用的WebView进行搜索", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing search", e)
            Toast.makeText(context, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在指定WebView中执行搜索
     */
    fun performSearchInWebView(webViewIndex: Int, query: String, engineKey: String) {
        Log.d(TAG, "performSearchInWebView: index=$webViewIndex, query='$query', engineKey='$engineKey'")
        
        if (webViewIndex < 0 || webViewIndex >= webViews.size) {
            Log.e(TAG, "Invalid WebView index: $webViewIndex, total WebViews: ${webViews.size}")
            return
        }
        
        try {
            // 使用SearchEngineHandler构建搜索URL
            val searchUrl = searchEngineHandler.getSearchUrl(query, engineKey)
            Log.d(TAG, "Generated search URL for WebView $webViewIndex: $searchUrl")
            
            // 在指定WebView中加载搜索URL
            val webView = webViews[webViewIndex]
            webView.loadUrl(searchUrl)
            
            // 设置为活动WebView
            activeWebView = webView
            
            Log.d(TAG, "Search URL loaded in WebView $webViewIndex: ${webView.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing search in WebView $webViewIndex", e)
            Toast.makeText(context, "在WebView中搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在活动WebView中加载URL
     */
    fun loadUrlInActiveWebView(url: String) {
        Log.d(TAG, "loadUrlInActiveWebView: url='$url'")
        
        try {
            activeWebView?.let { webView ->
                webView.loadUrl(url)
                Log.d(TAG, "URL loaded in active WebView: ${webView.id}")
            } ?: run {
                // 如果没有活动WebView，使用第一个可用的WebView
                if (webViews.isNotEmpty()) {
                    val firstWebView = webViews[0]
                    firstWebView.loadUrl(url)
                    activeWebView = firstWebView
                    Log.d(TAG, "URL loaded in first WebView: ${firstWebView.id}")
                } else {
                    Log.e(TAG, "No WebView available for loading URL")
                    Toast.makeText(context, "无可用的WebView加载URL", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading URL in active WebView", e)
            Toast.makeText(context, "加载URL失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 在活动WebView中执行JavaScript脚本
     */
    fun executeScriptInActiveWebView(script: String) {
        Log.d(TAG, "executeScriptInActiveWebView: script='$script'")
        
        try {
            activeWebView?.let { webView ->
                webView.evaluateJavascript(script) { result ->
                    Log.d(TAG, "JavaScript executed in active WebView, result: $result")
                }
            } ?: run {
                Log.w(TAG, "No active WebView available for script execution")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing script in active WebView", e)
        }
    }
    
    /**
     * 加载多个URL到对应的WebView中
     */
    fun loadUrls(urls: List<String>) {
        urls.forEachIndexed { index, url ->
            if (index < webViews.size) {
                loadUrlInWebView(index, url)
            }
        }
    }

    /**
     * 切换到多窗口视图
     */
    fun switchToMultiWebView(windowCount: Int) {
        // 显示指定数量的WebView，隐藏其他的
        for (i in webViews.indices) {
            setWebViewVisibility(i, i < windowCount)
        }
        // 确保聊天视图(如果存在)被隐藏
        (getChatWebView()?.parent as? View)?.visibility = View.GONE
    }

    /**
     * 获取用于聊天的WebView，这里我们约定使用第一个WebView
     */
    fun getChatWebView(): CustomWebView? {
        return if (webViews.isNotEmpty()) webViews[0] else null
    }

    /**
     * 切换到单窗口聊天视图
     */
    fun switchToSingleChatView() {
        // 隐藏所有普通WebView
        for (i in webViews.indices) {
             (webViews[i].parent as? View)?.visibility = View.GONE
        }
        // 显示聊天WebView
        val chatWebView = getChatWebView()
        (chatWebView?.parent as? View)?.visibility = View.VISIBLE
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
    
    override fun onLinkLongPressed(url: String, x: Int, y: Int) {
        activeWebView?.let {
            Log.d(TAG, "Link menu triggered for URL: $url")
            textSelectionManager.showLinkMenu(it, url, x, y)
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