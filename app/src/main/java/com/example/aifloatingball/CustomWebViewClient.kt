package com.example.aifloatingball

import android.graphics.Bitmap
import android.webkit.*
import android.widget.Toast
import java.io.ByteArrayInputStream

class CustomWebViewClient : WebViewClient() {
    
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        // 过滤广告和无关内容
        if (url.contains("ads") || url.contains("analytics") || url.contains("tracker")) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream("".toByteArray())
            )
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // 页面开始加载时的处理
        view.settings.blockNetworkImage = true  // 先阻止图片加载，加快页面呈现
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        view.settings.blockNetworkImage = false  // 允许图片加载
        
        // 注入自定义 CSS 优化页面显示
        val css = """
            javascript:(function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = '
                    .ad, .advertisement, .banner { display: none !important; }
                    body { font-size: 16px; line-height: 1.6; }
                    article, .content { max-width: 100% !important; }
                ';
                document.head.appendChild(style);
            })()
        """.trimIndent()
        view.loadUrl(css)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        // 处理加载错误
        if (request.isForMainFrame) {
            view.loadUrl("about:blank")  // 清空页面
            Toast.makeText(view.context, "页面加载失败，请检查网络连接", Toast.LENGTH_SHORT).show()
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        // 处理特殊链接
        return when {
            url.startsWith("tel:") || url.startsWith("mailto:") -> true  // 由系统处理
            else -> false  // WebView 继续加载
        }
    }
} 