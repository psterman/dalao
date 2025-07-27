package com.example.aifloatingball.webview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.WebViewPagerAdapter

/**
 * 多页面WebView管理器
 * 支持在同一个容器内管理多个WebView页面，用户可以左右滑动切换页面
 */
class MultiPageWebViewManager(
    private val context: Context,
    private val container: FrameLayout,
    private val progressBar: ProgressBar
) {
    companion object {
        private const val TAG = "MultiPageWebViewManager"
        private const val MAX_PAGES = 10 // 最大页面数量
    }

    // ViewPager2用于管理多个WebView页面
    private var viewPager: ViewPager2? = null
    private var adapter: WebViewPagerAdapter? = null
    
    // 页面数据列表
    private val webViewPages = mutableListOf<WebViewPage>()
    
    // 当前页面索引
    private var currentPageIndex = 0
    
    // 页面变化监听器
    private var onPageChangeListener: OnPageChangeListener? = null
    
    /**
     * WebView页面数据类
     */
    data class WebViewPage(
        val id: String,
        val webView: WebView,
        var title: String = "新标签页",
        var url: String = "",
        var favicon: Bitmap? = null,
        var isLoading: Boolean = false
    )
    
    /**
     * 页面变化监听器接口
     */
    interface OnPageChangeListener {
        fun onPageChanged(page: WebViewPage, position: Int)
        fun onPageAdded(page: WebViewPage, position: Int)
        fun onPageRemoved(page: WebViewPage, position: Int)
        fun onPageTitleChanged(page: WebViewPage, title: String)
        fun onPageUrlChanged(page: WebViewPage, url: String)
        fun onPageLoadingStateChanged(page: WebViewPage, isLoading: Boolean)
    }

    init {
        setupViewPager()
    }

    /**
     * 设置ViewPager2
     */
    private fun setupViewPager() {
        // 创建ViewPager2
        viewPager = ViewPager2(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 3 // 预加载3个页面
        }
        
        // 创建适配器
        adapter = WebViewPagerAdapter(webViewPages) { webView, page ->
            setupWebView(webView, page)
        }
        
        viewPager?.adapter = adapter
        
        // 添加页面变化监听
        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPageIndex = position
                if (position < webViewPages.size) {
                    val page = webViewPages[position]
                    onPageChangeListener?.onPageChanged(page, position)
                    Log.d(TAG, "切换到页面: ${page.title} (${position})")
                }
            }
        })
        
        // 将ViewPager添加到容器
        container.addView(viewPager)
        
        // 创建第一个页面
        addNewPage("about:blank")
    }

    /**
     * 设置WebView配置
     */
    private fun setupWebView(webView: WebView, page: WebViewPage) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
        }

        // 设置WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // 拦截广告和恶意URL
                if (url != null && isAdOrMaliciousUrl(url)) {
                    Log.d(TAG, "拦截广告URL: $url")
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                page.isLoading = true
                page.url = url ?: ""
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
                onPageChangeListener?.onPageLoadingStateChanged(page, true)
                onPageChangeListener?.onPageUrlChanged(page, url ?: "")
                Log.d(TAG, "页面开始加载: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                page.isLoading = false
                progressBar.visibility = View.GONE
                onPageChangeListener?.onPageLoadingStateChanged(page, false)
                Log.d(TAG, "页面加载完成: $url")
            }


        }

        // 设置WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty()) {
                    page.title = title
                    onPageChangeListener?.onPageTitleChanged(page, title)
                    Log.d(TAG, "页面标题更新: $title")
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                page.favicon = icon
                Log.d(TAG, "接收到页面图标")
            }
        }
    }

    /**
     * 添加新页面
     */
    fun addNewPage(url: String = "about:blank"): WebViewPage {
        if (webViewPages.size >= MAX_PAGES) {
            Log.w(TAG, "已达到最大页面数量限制: $MAX_PAGES")
            return webViewPages.last()
        }

        val webView = WebView(context)
        val pageId = "page_${System.currentTimeMillis()}"
        val page = WebViewPage(
            id = pageId,
            webView = webView,
            title = if (url == "about:blank") "新标签页" else "加载中...",
            url = url
        )

        webViewPages.add(page)
        adapter?.notifyItemInserted(webViewPages.size - 1)
        
        // 切换到新页面
        viewPager?.setCurrentItem(webViewPages.size - 1, true)
        
        // 加载URL
        if (url != "about:blank") {
            webView.loadUrl(url)
        }
        
        onPageChangeListener?.onPageAdded(page, webViewPages.size - 1)
        Log.d(TAG, "添加新页面: $url")
        
        return page
    }

    /**
     * 关闭指定页面
     */
    fun closePage(position: Int): Boolean {
        if (position < 0 || position >= webViewPages.size || webViewPages.size <= 1) {
            return false
        }

        val page = webViewPages[position]
        page.webView.destroy()
        webViewPages.removeAt(position)
        adapter?.notifyItemRemoved(position)
        
        // 调整当前页面索引
        if (currentPageIndex >= position && currentPageIndex > 0) {
            currentPageIndex--
        }
        
        // 确保当前页面索引有效
        if (currentPageIndex >= webViewPages.size) {
            currentPageIndex = webViewPages.size - 1
        }
        
        // 切换到调整后的页面
        viewPager?.setCurrentItem(currentPageIndex, false)
        
        onPageChangeListener?.onPageRemoved(page, position)
        Log.d(TAG, "关闭页面: ${page.title}")
        
        return true
    }

    /**
     * 在当前页面加载URL
     */
    fun loadUrl(url: String) {
        if (webViewPages.isNotEmpty()) {
            val currentPage = webViewPages[currentPageIndex]
            currentPage.webView.loadUrl(url)
            Log.d(TAG, "在当前页面加载URL: $url")
        }
    }

    /**
     * 在新页面加载URL
     */
    fun loadUrlInNewPage(url: String) {
        addNewPage(url)
    }

    /**
     * 获取当前页面
     */
    fun getCurrentPage(): WebViewPage? {
        return if (currentPageIndex < webViewPages.size) {
            webViewPages[currentPageIndex]
        } else null
    }

    /**
     * 获取所有页面
     */
    fun getAllPages(): List<WebViewPage> = webViewPages.toList()

    /**
     * 切换到指定页面
     */
    fun switchToPage(position: Int) {
        if (position >= 0 && position < webViewPages.size) {
            viewPager?.setCurrentItem(position, true)
        }
    }

    /**
     * 设置页面变化监听器
     */
    fun setOnPageChangeListener(listener: OnPageChangeListener) {
        this.onPageChangeListener = listener
    }

    /**
     * 检查是否为广告或恶意URL
     */
    private fun isAdOrMaliciousUrl(url: String): Boolean {
        val adDomains = listOf(
            "googleads", "googlesyndication", "doubleclick", "adsystem",
            "amazon-adsystem", "facebook.com/tr", "google-analytics",
            "googletagmanager", "scorecardresearch", "quantserve",
            "outbrain", "taboola", "adsense"
        )
        
        return adDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }

    /**
     * 销毁所有WebView
     */
    fun destroy() {
        webViewPages.forEach { page ->
            page.webView.destroy()
        }
        webViewPages.clear()
        adapter = null
        viewPager = null
    }

    /**
     * 当前页面是否可以后退
     */
    fun canGoBack(): Boolean = getCurrentPage()?.webView?.canGoBack() == true

    /**
     * 当前页面后退
     */
    fun goBack() {
        getCurrentPage()?.webView?.goBack()
    }

    /**
     * 当前页面是否可以前进
     */
    fun canGoForward(): Boolean = getCurrentPage()?.webView?.canGoForward() == true

    /**
     * 当前页面前进
     */
    fun goForward() {
        getCurrentPage()?.webView?.goForward()
    }

    /**
     * 刷新当前页面
     */
    fun reload() {
        getCurrentPage()?.webView?.reload()
    }
}
