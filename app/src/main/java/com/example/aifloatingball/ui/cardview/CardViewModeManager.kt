package com.example.aifloatingball.ui.cardview

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.SearchResultCardAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.ui.webview.CustomWebView
import com.example.aifloatingball.ui.webview.WebViewFactory
import com.example.aifloatingball.adblock.AdBlockFilter
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * 卡片视图模式管理器
 * 管理搜索结果卡片视图，支持左右两列布局，向下滚动延伸
 */
class CardViewModeManager(
    private val context: Context,
    private val container: FrameLayout
) {
    companion object {
        private const val TAG = "CardViewModeManager"
        private const val SPAN_COUNT = 2 // 两列布局
    }

    private var recyclerView: RecyclerView? = null
    private var adapter: SearchResultCardAdapter? = null
    private val cardDataList = mutableListOf<SearchResultCardData>()
    
    // 当前选中的标签
    private var currentTag: String? = null
    
    // 卡片点击监听器
    private var onCardClickListener: OnCardClickListener? = null
    
    // 全屏查看器
    private var fullScreenViewer: FullScreenCardViewer? = null
    
    // 广告拦截器
    private val adBlockFilter = AdBlockFilter(context)

    /**
     * 搜索结果卡片数据
     */
    data class SearchResultCardData(
        val id: String,
        val webView: CustomWebView,
        val searchQuery: String,
        val engineKey: String,
        val engineName: String,
        var title: String = "",
        var url: String = "",
        var previewImage: String? = null,
        var isExpanded: Boolean = false,
        var tag: String? = null // 标签，用于分类管理
    )

    /**
     * 卡片点击监听器
     */
    interface OnCardClickListener {
        fun onCardClick(cardData: SearchResultCardData)
        fun onCardExpand(cardData: SearchResultCardData)
    }

    init {
        setupRecyclerView()
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        recyclerView = RecyclerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // 设置两列网格布局
            layoutManager = GridLayoutManager(context, SPAN_COUNT).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        // 如果卡片是展开状态，占满整行
                        return if (position < cardDataList.size && cardDataList[position].isExpanded) {
                            SPAN_COUNT
                        } else {
                            1
                        }
                    }
                }
            }
            
            // 设置适配器
            adapter = SearchResultCardAdapter(
                cards = cardDataList,
                onCardClick = { cardData ->
                    // 点击卡片显示全屏
                    showFullScreen(cardData)
                    onCardClickListener?.onCardClick(cardData)
                },
                onCardLongClick = { cardData ->
                    // 长按展开/收起卡片
                    expandCard(cardData)
                }
            ).also { cardAdapter ->
                this@CardViewModeManager.adapter = cardAdapter
            }
        }
        
        container.addView(recyclerView)
    }

    /**
     * 添加搜索结果卡片
     */
    fun addSearchResultCard(
        query: String,
        engineKey: String,
        engineName: String,
        tag: String? = null
    ): SearchResultCardData {
        val webView = WebViewFactory(context).createWebView()
        val cardData = SearchResultCardData(
            id = System.currentTimeMillis().toString(),
            webView = webView,
            searchQuery = query,
            engineKey = engineKey,
            engineName = engineName,
            title = "$engineName - $query",
            tag = tag
        )
        
        cardDataList.add(cardData)
        adapter?.notifyItemInserted(cardDataList.size - 1)
        
        // 执行搜索
        performSearch(cardData, query, engineKey)
        
        Log.d(TAG, "添加搜索结果卡片: $engineName - $query")
        return cardData
    }

    /**
     * 根据标签添加多个搜索结果卡片
     */
    fun addSearchResultsForTag(query: String, tag: String, engines: List<SearchEngine>) {
        currentTag = tag
        engines.forEach { engine ->
            addSearchResultCard(query, engine.name, engine.displayName, tag)
        }
    }

    /**
     * 执行搜索
     */
    private fun performSearch(cardData: SearchResultCardData, query: String, engineKey: String) {
        // 根据engineKey构建搜索URL
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
            it.name == engineKey 
        }
        
        val searchUrl = if (searchEngine != null) {
            searchEngine.getSearchUrl(query)
        } else {
            // 默认使用Google搜索
            "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        }
        
        // 设置WebViewClient，包含广告拦截功能
        cardData.webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                
                // 拦截广告请求
                if (adBlockFilter.shouldBlock(url)) {
                    Log.d(TAG, "拦截广告请求: $url")
                    return WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream("".toByteArray())
                    )
                }
                
                return super.shouldInterceptRequest(view, request)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cardData.title = view?.title ?: cardData.title
                cardData.url = url ?: searchUrl
                
                // 注入广告拦截和弹窗拦截脚本
                injectAdBlockScript(view)
                
                adapter?.notifyItemChanged(cardDataList.indexOf(cardData))
            }
        }
        
        // 设置WebChromeClient，拦截弹窗
        cardData.webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                // 拦截弹窗广告
                if (isAdPopup(message ?: "")) {
                    result?.cancel()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }
            
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                // 拦截确认弹窗广告
                if (isAdPopup(message ?: "")) {
                    result?.cancel()
                    return true
                }
                return super.onJsConfirm(view, url, message, result)
            }
        }
        
        cardData.webView.loadUrl(searchUrl)
        cardData.url = searchUrl
    }

    /**
     * 展开卡片（占满整行）
     */
    private fun expandCard(cardData: SearchResultCardData) {
        val index = cardDataList.indexOf(cardData)
        if (index >= 0) {
            cardData.isExpanded = !cardData.isExpanded
            adapter?.notifyItemChanged(index)
            recyclerView?.layoutManager?.let { 
                if (it is GridLayoutManager) {
                    it.spanSizeLookup.invalidateSpanIndexCache()
                }
            }
        }
    }

    /**
     * 显示全屏查看
     */
    private fun showFullScreen(cardData: SearchResultCardData) {
        if (fullScreenViewer == null) {
            // 使用根容器作为父容器，确保全屏视图在最上层
            val rootContainer = container.rootView as? ViewGroup ?: container.parent as? ViewGroup ?: container
            fullScreenViewer = FullScreenCardViewer(context, rootContainer)
        }
        fullScreenViewer?.show(cardData)
    }
    
    /**
     * 关闭全屏查看（供外部调用）
     */
    fun dismissFullScreen() {
        fullScreenViewer?.dismiss()
    }
    
    /**
     * 设置全屏查看器的父容器（用于在FloatingWindowManager中设置）
     */
    fun setFullScreenParentContainer(parentContainer: ViewGroup) {
        if (fullScreenViewer == null) {
            fullScreenViewer = FullScreenCardViewer(context, parentContainer)
        } else {
            // 如果已经创建，需要重新创建以使用新的父容器
            fullScreenViewer?.dismiss()
            fullScreenViewer = FullScreenCardViewer(context, parentContainer)
        }
    }

    /**
     * 移除卡片
     */
    fun removeCard(cardData: SearchResultCardData) {
        val index = cardDataList.indexOf(cardData)
        if (index >= 0) {
            cardData.webView.destroy()
            cardDataList.removeAt(index)
            adapter?.notifyItemRemoved(index)
        }
    }

    /**
     * 清除所有卡片
     */
    fun clearAllCards() {
        cardDataList.forEach { it.webView.destroy() }
        cardDataList.clear()
        adapter?.notifyDataSetChanged()
    }

    /**
     * 清除指定标签的卡片
     */
    fun clearCardsByTag(tag: String) {
        val toRemove = cardDataList.filter { it.tag == tag }
        toRemove.forEach { removeCard(it) }
    }

    /**
     * 设置卡片点击监听器
     */
    fun setOnCardClickListener(listener: OnCardClickListener) {
        this.onCardClickListener = listener
    }

    /**
     * 获取所有卡片
     */
    fun getAllCards(): List<SearchResultCardData> = cardDataList.toList()

    /**
     * 注入广告拦截脚本
     */
    private fun injectAdBlockScript(webView: WebView?) {
        webView ?: return
        
        val adBlockScript = """
            javascript:(function() {
                // 移除常见的广告元素
                var adSelectors = [
                    '[id*="ad"]', '[class*="ad"]', '[id*="ads"]', '[class*="ads"]',
                    '[id*="advertisement"]', '[class*="advertisement"]',
                    '[id*="banner"]', '[class*="banner"]',
                    '[id*="popup"]', '[class*="popup"]', '[id*="pop-up"]', '[class*="pop-up"]',
                    '[id*="sponsor"]', '[class*="sponsor"]',
                    'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',
                    'iframe[src*="googlesyndication"]', 'iframe[src*="googleadservices"]',
                    '.ad-container', '.ad-wrapper', '.ad-box', '.ad-content',
                    '[data-ad]', '[data-ads]', '[data-advertisement]'
                ];
                
                adSelectors.forEach(function(selector) {
                    try {
                        var elements = document.querySelectorAll(selector);
                        elements.forEach(function(element) {
                            if (element && element.parentNode) {
                                element.style.display = 'none';
                                element.remove();
                            }
                        });
                    } catch(e) {
                        // 忽略错误
                    }
                });
                
                // 拦截弹窗
                var originalAlert = window.alert;
                var originalConfirm = window.confirm;
                
                window.alert = function(message) {
                    if (message && (
                        message.indexOf('广告') !== -1 ||
                        message.indexOf('推广') !== -1 ||
                        message.indexOf('优惠') !== -1 ||
                        message.indexOf('点击') !== -1
                    )) {
                        return;
                    }
                    return originalAlert.call(window, message);
                };
                
                window.confirm = function(message) {
                    if (message && (
                        message.indexOf('广告') !== -1 ||
                        message.indexOf('推广') !== -1 ||
                        message.indexOf('优惠') !== -1
                    )) {
                        return false;
                    }
                    return originalConfirm.call(window, message);
                };
                
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
            webView.evaluateJavascript(adBlockScript, null)
        } catch (e: Exception) {
            Log.e(TAG, "注入广告拦截脚本失败", e)
        }
    }
    
    /**
     * 判断是否是广告弹窗
     */
    private fun isAdPopup(message: String): Boolean {
        val lowerMessage = message.lowercase()
        val adKeywords = listOf("广告", "推广", "优惠", "点击", "ad", "advertisement", "sponsor")
        return adKeywords.any { lowerMessage.contains(it) }
    }

    /**
     * 销毁资源
     */
    fun destroy() {
        clearAllCards()
        fullScreenViewer?.dismiss()
        fullScreenViewer = null
        recyclerView?.adapter = null
        container.removeView(recyclerView)
        recyclerView = null
    }
}

