package com.example.aifloatingball.webview

import android.util.Log
import android.webkit.WebView

/**
 * WebView管理器适配器
 * 将现有的WebView管理器适配到统一管理器的接口
 */
class WebViewManagerAdapter(
    private val manager: Any,
    private val name: String,
    private val unifiedManager: UnifiedWebViewManager? = null
) : UnifiedWebViewManager.WebViewManager {
    
    companion object {
        private const val TAG = "WebViewManagerAdapter"
    }
    
    override fun getCurrentWebView(): WebView? {
        return when (manager) {
            is MobileCardManager -> {
                manager.getCurrentCard()?.webView
            }
            is GestureCardWebViewManager -> {
                manager.getCurrentCard()?.webView
            }
            is PaperStackWebViewManager -> {
                manager.getCurrentTab()?.webView
            }
            is MultiPageWebViewManager -> {
                manager.getCurrentPage()?.webView
            }
            else -> {
                Log.w(TAG, "未知的WebView管理器类型: ${manager::class.simpleName}")
                null
            }
        }
    }
    
    override fun canGoBack(): Boolean {
        return when (manager) {
            is MobileCardManager -> {
                manager.getCurrentCard()?.webView?.canGoBack() ?: false
            }
            is GestureCardWebViewManager -> {
                manager.getCurrentCard()?.webView?.canGoBack() ?: false
            }
            is PaperStackWebViewManager -> {
                manager.canGoBack()
            }
            is MultiPageWebViewManager -> {
                manager.canGoBack()
            }
            else -> {
                Log.w(TAG, "未知的WebView管理器类型: ${manager::class.simpleName}")
                false
            }
        }
    }
    
    override fun goBack(): Boolean {
        return when (manager) {
            is MobileCardManager -> {
                val webView = manager.getCurrentCard()?.webView
                if (webView?.canGoBack() == true) {
                    webView.goBack()
                    true
                } else false
            }
            is GestureCardWebViewManager -> {
                val webView = manager.getCurrentCard()?.webView
                if (webView?.canGoBack() == true) {
                    webView.goBack()
                    true
                } else false
            }
            is PaperStackWebViewManager -> {
                manager.goBack()
            }
            is MultiPageWebViewManager -> {
                val webView = manager.getCurrentPage()?.webView
                if (webView?.canGoBack() == true) {
                    webView.goBack()
                    true
                } else false
            }
            else -> {
                Log.w(TAG, "未知的WebView管理器类型: ${manager::class.simpleName}")
                false
            }
        }
    }
    
    override fun canGoForward(): Boolean {
        return when (manager) {
            is MobileCardManager -> {
                manager.getCurrentCard()?.webView?.canGoForward() ?: false
            }
            is GestureCardWebViewManager -> {
                manager.getCurrentCard()?.webView?.canGoForward() ?: false
            }
            is PaperStackWebViewManager -> {
                manager.canGoForward()
            }
            is MultiPageWebViewManager -> {
                manager.canGoForward()
            }
            else -> {
                Log.w(TAG, "未知的WebView管理器类型: ${manager::class.simpleName}")
                false
            }
        }
    }
    
    override fun goForward(): Boolean {
        return when (manager) {
            is MobileCardManager -> {
                val webView = manager.getCurrentCard()?.webView
                if (webView?.canGoForward() == true) {
                    webView.goForward()
                    true
                } else false
            }
            is GestureCardWebViewManager -> {
                val webView = manager.getCurrentCard()?.webView
                if (webView?.canGoForward() == true) {
                    webView.goForward()
                    true
                } else false
            }
            is PaperStackWebViewManager -> {
                manager.goForward()
            }
            is MultiPageWebViewManager -> {
                val webView = manager.getCurrentPage()?.webView
                if (webView?.canGoForward() == true) {
                    webView.goForward()
                    true
                } else false
            }
            else -> {
                Log.w(TAG, "未知的WebView管理器类型: ${manager::class.simpleName}")
                false
            }
        }
    }
    
    override fun getName(): String = name
    
    /**
     * 添加URL到历史记录
     */
    fun addToHistory(url: String) {
        unifiedManager?.addToHistory(url)
    }
}
