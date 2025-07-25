package com.example.aifloatingball.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.*
import android.widget.Toast
import com.example.aifloatingball.adblock.AdBlockFilter
import java.io.ByteArrayInputStream

/**
 * 增强的WebViewClient，集成AdBlock功能和多tab支持
 */
class EnhancedWebViewClient(
    private val adBlockFilter: AdBlockFilter,
    private val onPageLoadListener: PageLoadListener? = null,
    private val onUrlChangeListener: UrlChangeListener? = null
) : WebViewClient() {
    
    companion object {
        private const val TAG = "EnhancedWebViewClient"
    }
    
    interface PageLoadListener {
        fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?)
        fun onPageFinished(view: WebView?, url: String?)
        fun onProgressChanged(view: WebView?, newProgress: Int)
    }
    
    interface UrlChangeListener {
        fun onUrlChanged(view: WebView?, url: String?)
    }
    
    private var blockedRequestsCount = 0
    
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        
        // 检查是否应该拦截这个请求
        if (adBlockFilter.shouldBlock(url)) {
            blockedRequestsCount++
            Log.d(TAG, "Blocked request: $url (Total blocked: $blockedRequestsCount)")
            
            // 返回空响应来拦截请求
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream("".toByteArray())
            )
        }
        
        return super.shouldInterceptRequest(view, request)
    }
    
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        
        Log.d(TAG, "Page started loading: $url")
        
        // 重置拦截计数
        blockedRequestsCount = 0
        
        // 优化页面加载
        view?.settings?.apply {
            // 先阻止图片加载，加快页面呈现
            blockNetworkImage = true
            // 启用缓存
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        
        onPageLoadListener?.onPageStarted(view, url, favicon)
        onUrlChangeListener?.onUrlChanged(view, url)
    }
    
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        
        Log.d(TAG, "Page finished loading: $url (Blocked $blockedRequestsCount requests)")
        
        // 页面加载完成后启用图片加载
        view?.settings?.blockNetworkImage = false
        
        // 确保WebView可以接收触摸事件
        view?.apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        
        // 注入JavaScript来进一步清理广告元素
        injectAdBlockScript(view)
        
        onPageLoadListener?.onPageFinished(view, url)
    }
    
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        
        Log.e(TAG, "WebView error: ${error.description} for ${request.url}")
        
        // 只处理主框架的错误
        if (request.isForMainFrame) {
            val errorMessage = when (error.errorCode) {
                ERROR_HOST_LOOKUP -> "无法找到服务器"
                ERROR_CONNECT -> "连接失败"
                ERROR_TIMEOUT -> "连接超时"
                ERROR_UNKNOWN -> "未知错误"
                else -> "页面加载失败"
            }
            
            // 显示错误页面
            showErrorPage(view, errorMessage, request.url.toString())
        }
    }
    
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        
        Log.d(TAG, "URL loading: $url")
        
        return when {
            // 处理特殊协议
            url.startsWith("tel:") || 
            url.startsWith("mailto:") || 
            url.startsWith("sms:") -> {
                // 由系统处理
                true
            }
            // 处理文件下载
            url.endsWith(".apk") || 
            url.endsWith(".pdf") || 
            url.endsWith(".zip") || 
            url.endsWith(".rar") -> {
                // 可以在这里添加下载处理逻辑
                false
            }
            else -> {
                // WebView继续加载
                onUrlChangeListener?.onUrlChanged(view, url)
                false
            }
        }
    }
    
    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        // 对于SSL错误，可以选择继续或取消
        // 这里选择取消以保证安全性
        handler?.cancel()
        
        view?.context?.let { context ->
            Toast.makeText(context, "SSL证书验证失败，已停止加载", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 注入JavaScript来移除广告元素
     */
    private fun injectAdBlockScript(view: WebView?) {
        view ?: return
        
        val adBlockScript = """
            javascript:(function() {
                // 移除常见的广告元素
                var adSelectors = [
                    '[id*="ad"]', '[class*="ad"]', '[id*="ads"]', '[class*="ads"]',
                    '[id*="advertisement"]', '[class*="advertisement"]',
                    '[id*="banner"]', '[class*="banner"]',
                    '[id*="popup"]', '[class*="popup"]',
                    '[id*="sponsor"]', '[class*="sponsor"]',
                    'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',
                    'iframe[src*="googlesyndication"]', 'iframe[src*="googleadservices"]'
                ];
                
                adSelectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(element) {
                            if (element && element.parentNode) {
                                element.parentNode.removeChild(element);
                            }
                        });
                    } catch(e) {
                        // 忽略错误
                    }
                });
                
                // 移除空的div元素
                var emptyDivs = document.querySelectorAll('div:empty');
                emptyDivs.forEach(function(div) {
                    if (div.offsetHeight < 10 && div.offsetWidth < 10) {
                        try {
                            div.parentNode.removeChild(div);
                        } catch(e) {
                            // 忽略错误
                        }
                    }
                });
            })();
        """.trimIndent()
        
        try {
            view.evaluateJavascript(adBlockScript, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting ad block script", e)
        }
    }
    
    /**
     * 显示错误页面
     */
    private fun showErrorPage(view: WebView?, errorMessage: String, failedUrl: String) {
        view ?: return
        
        val errorHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>页面加载失败</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        margin: 0;
                        padding: 20px;
                        background-color: #f5f5f5;
                        color: #333;
                        text-align: center;
                    }
                    .error-container {
                        max-width: 400px;
                        margin: 50px auto;
                        background: white;
                        border-radius: 8px;
                        padding: 30px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    .error-icon {
                        font-size: 48px;
                        color: #ff6b6b;
                        margin-bottom: 20px;
                    }
                    .error-title {
                        font-size: 20px;
                        font-weight: bold;
                        margin-bottom: 10px;
                        color: #333;
                    }
                    .error-message {
                        font-size: 14px;
                        color: #666;
                        margin-bottom: 20px;
                        line-height: 1.5;
                    }
                    .retry-button {
                        background-color: #007AFF;
                        color: white;
                        border: none;
                        padding: 12px 24px;
                        border-radius: 6px;
                        font-size: 16px;
                        cursor: pointer;
                        margin: 5px;
                    }
                    .retry-button:hover {
                        background-color: #0056CC;
                    }
                    .url-info {
                        font-size: 12px;
                        color: #999;
                        margin-top: 20px;
                        word-break: break-all;
                    }
                </style>
            </head>
            <body>
                <div class="error-container">
                    <div class="error-icon">⚠️</div>
                    <div class="error-title">页面加载失败</div>
                    <div class="error-message">$errorMessage</div>
                    <button class="retry-button" onclick="location.reload()">重新加载</button>
                    <button class="retry-button" onclick="history.back()">返回上页</button>
                    <div class="url-info">URL: $failedUrl</div>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }
    
    /**
     * 获取拦截的请求数量
     */
    fun getBlockedRequestsCount(): Int = blockedRequestsCount
}
