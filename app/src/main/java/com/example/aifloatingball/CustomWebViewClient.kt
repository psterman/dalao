class CustomWebViewClient : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        // 过滤广告和无关元素
        val url = request?.url.toString()
        if (url.contains("ads") || url.contains("analytics")) {
            return WebResourceResponse("text/plain", "utf-8", null)
        }
        return super.shouldInterceptRequest(view, request)
    }
    
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 注入CSS来隐藏不需要的元素
        view?.loadUrl("""
            javascript:(function() {
                var elements = document.querySelectorAll('.ads, .logo, .banner');
                elements.forEach(function(element) {
                    element.style.display = 'none';
                });
            })()
        """.trimIndent())
    }
} 