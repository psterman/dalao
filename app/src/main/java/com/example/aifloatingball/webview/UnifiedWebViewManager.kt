package com.example.aifloatingball.webview

import android.util.Log
import android.webkit.WebView

/**
 * 统一WebView管理器
 * 协调多个WebView管理器，解决返回功能冲突问题
 */
class UnifiedWebViewManager {
    
    companion object {
        private const val TAG = "UnifiedWebViewManager"
    }
    
    private val webViewManagers = mutableListOf<WebViewManager>()
    private var currentManager: WebViewManager? = null
    private val historyManager = WebViewHistoryManager()
    
    /**
     * WebView管理器接口
     */
    interface WebViewManager {
        fun getCurrentWebView(): WebView?
        fun canGoBack(): Boolean
        fun goBack(): Boolean
        fun canGoForward(): Boolean
        fun goForward(): Boolean
        fun getName(): String
    }
    
    /**
     * 注册WebView管理器
     */
    fun registerManager(manager: WebViewManager) {
        if (!webViewManagers.contains(manager)) {
            webViewManagers.add(manager)
            Log.d(TAG, "注册WebView管理器: ${manager.getName()}")
            
            if (currentManager == null) {
                currentManager = manager
                Log.d(TAG, "设置当前管理器: ${manager.getName()}")
            }
        }
    }
    
    /**
     * 注销WebView管理器
     */
    fun unregisterManager(manager: WebViewManager) {
        webViewManagers.remove(manager)
        Log.d(TAG, "注销WebView管理器: ${manager.getName()}")
        
        if (currentManager == manager) {
            currentManager = webViewManagers.firstOrNull()
            Log.d(TAG, "重新设置当前管理器: ${currentManager?.getName()}")
        }
    }
    
    /**
     * 设置当前活动的WebView管理器
     */
    fun setCurrentManager(manager: WebViewManager) {
        if (webViewManagers.contains(manager)) {
            currentManager = manager
            Log.d(TAG, "切换当前管理器: ${manager.getName()}")
        }
    }
    
    /**
     * 获取当前活动的WebView
     */
    fun getCurrentActiveWebView(): WebView? {
        return currentManager?.getCurrentWebView()
    }
    
    /**
     * 是否可以返回
     */
    fun canGoBack(): Boolean {
        // 1. 检查当前WebView的历史记录
        val currentWebView = getCurrentActiveWebView()
        if (currentWebView?.canGoBack() == true) {
            return true
        }
        
        // 2. 检查历史记录管理器
        if (historyManager.canGoBack()) {
            return true
        }
        
        // 3. 检查其他WebView管理器
        return webViewManagers.any { it.canGoBack() }
    }
    
    /**
     * 返回上一页
     */
    fun goBack(): Boolean {
        Log.d(TAG, "执行统一返回操作")
        
        // 1. 优先使用当前WebView的历史记录
        val currentWebView = getCurrentActiveWebView()
        if (currentWebView?.canGoBack() == true) {
            val currentUrl = currentWebView.url
            Log.d(TAG, "当前URL: $currentUrl")
            
            // 检查是否是特殊页面
            if (currentUrl != null && historyManager.isSpecialPage(currentUrl)) {
                return handleSpecialPageBack(currentWebView, currentUrl)
            }
            
            // 正常返回
            currentWebView.goBack()
            Log.d(TAG, "使用当前WebView返回")
            return true
        }
        
        // 2. 使用历史记录管理器
        if (historyManager.canGoBack()) {
            val previousUrl = historyManager.goBack()
            if (previousUrl != null) {
                currentWebView?.loadUrl(previousUrl)
                Log.d(TAG, "使用历史记录管理器返回: $previousUrl")
                return true
            }
        }
        
        // 3. 检查其他WebView管理器
        for (manager in webViewManagers) {
            if (manager.canGoBack()) {
                setCurrentManager(manager)
                manager.goBack()
                Log.d(TAG, "使用其他管理器返回: ${manager.getName()}")
                return true
            }
        }
        
        Log.d(TAG, "无法返回，所有管理器都无可返回页面")
        return false
    }
    
    /**
     * 是否可以前进
     */
    fun canGoForward(): Boolean {
        val currentWebView = getCurrentActiveWebView()
        if (currentWebView?.canGoForward() == true) {
            return true
        }
        
        if (historyManager.canGoForward()) {
            return true
        }
        
        return webViewManagers.any { it.canGoForward() }
    }
    
    /**
     * 前进下一页
     */
    fun goForward(): Boolean {
        Log.d(TAG, "执行统一前进操作")
        
        val currentWebView = getCurrentActiveWebView()
        if (currentWebView?.canGoForward() == true) {
            currentWebView.goForward()
            Log.d(TAG, "使用当前WebView前进")
            return true
        }
        
        if (historyManager.canGoForward()) {
            val nextUrl = historyManager.goForward()
            if (nextUrl != null) {
                currentWebView?.loadUrl(nextUrl)
                Log.d(TAG, "使用历史记录管理器前进: $nextUrl")
                return true
            }
        }
        
        for (manager in webViewManagers) {
            if (manager.canGoForward()) {
                setCurrentManager(manager)
                manager.goForward()
                Log.d(TAG, "使用其他管理器前进: ${manager.getName()}")
                return true
            }
        }
        
        Log.d(TAG, "无法前进，所有管理器都无可前进页面")
        return false
    }
    
    /**
     * 处理特殊页面返回
     */
    private fun handleSpecialPageBack(webView: WebView, url: String): Boolean {
        val strategy = historyManager.getSpecialPageBackStrategy(url)
        Log.d(TAG, "处理特殊页面返回: $url, 策略: $strategy")
        
        return when (strategy) {
            "google_search_back" -> handleGoogleSearchBack(webView)
            "baidu_search_back" -> handleBaiduSearchBack(webView)
            "weibo_back" -> handleWeiboBack(webView)
            "douyin_back" -> handleDouyinBack(webView)
            "youtube_back" -> handleYouTubeBack(webView)
            "bilibili_back" -> handleBilibiliBack(webView)
            else -> handleDefaultSpecialBack(webView)
        }
    }
    
    /**
     * Google搜索页面返回
     */
    private fun handleGoogleSearchBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("""
                if (window.history.length > 1) {
                    history.back();
                } else {
                    window.location.href = 'https://www.google.com';
                }
            """.trimIndent(), null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Google搜索返回失败", e)
            false
        }
    }
    
    /**
     * 百度搜索页面返回
     */
    private fun handleBaiduSearchBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("""
                if (window.history.length > 1) {
                    history.back();
                } else {
                    window.location.href = 'https://www.baidu.com';
                }
            """.trimIndent(), null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "百度搜索返回失败", e)
            false
        }
    }
    
    /**
     * 微博页面返回
     */
    private fun handleWeiboBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("history.back()", null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "微博返回失败", e)
            false
        }
    }
    
    /**
     * 抖音页面返回
     */
    private fun handleDouyinBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("history.back()", null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "抖音返回失败", e)
            false
        }
    }
    
    /**
     * YouTube页面返回
     */
    private fun handleYouTubeBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("history.back()", null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "YouTube返回失败", e)
            false
        }
    }
    
    /**
     * B站页面返回
     */
    private fun handleBilibiliBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("history.back()", null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "B站返回失败", e)
            false
        }
    }
    
    /**
     * 默认特殊页面返回
     */
    private fun handleDefaultSpecialBack(webView: WebView): Boolean {
        return try {
            webView.evaluateJavascript("history.back()", null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "默认特殊页面返回失败", e)
            false
        }
    }
    
    /**
     * 添加URL到历史记录
     */
    fun addToHistory(url: String) {
        historyManager.addToHistory(url)
    }
    
    /**
     * 清除历史记录
     */
    fun clearHistory() {
        historyManager.clearHistory()
    }
    
    /**
     * 获取历史记录管理器
     */
    fun getHistoryManager(): WebViewHistoryManager = historyManager
    
    /**
     * 获取所有注册的管理器
     */
    fun getRegisteredManagers(): List<WebViewManager> = webViewManagers.toList()
    
    /**
     * 获取当前管理器
     */
    fun getCurrentManager(): WebViewManager? = currentManager
}
