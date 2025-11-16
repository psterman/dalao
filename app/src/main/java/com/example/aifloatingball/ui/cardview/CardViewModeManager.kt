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
    private val container: FrameLayout,
    private val onOpenAppCallback: (() -> Unit)? = null  // 打开app时的回调，用于临时降低悬浮窗层级
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
                },
                cardViewModeManager = this@CardViewModeManager
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
     * 搜索引擎到app包名的映射
     */
    private val engineToPackageMap = mapOf(
        "douyin" to "com.ss.android.ugc.aweme",
        "kuaishou" to "com.smile.gifmaker",
        "bilibili" to "tv.danmaku.bili",
        "weibo" to "com.sina.weibo",
        "zhihu" to "com.zhihu.android",
        "xiaohongshu" to "com.xingin.xhs",
        "taobao" to "com.taobao.taobao",
        "jd" to "com.jingdong.app.mall",
        "baidu" to "com.baidu.searchbox",
        "sogou" to "com.sohu.inputmethod.sogou",
        "chatgpt_web" to "com.openai.chatgpt",
        "claude_web" to "com.anthropic.claude",
        "gemini_web" to "com.google.android.apps.bard",
        "wenxin_yiyan" to "com.baidu.wenxin",
        "tongyi_qianwen" to "com.alibaba.dingtalk",
        "kimi_web" to "com.moonshot.kimi",
        "deepseek_web" to "com.deepseek.deepseek",
        "zhipu_ai" to "com.zhipuai.zhipu",
        "xinghuo_web" to "com.iflytek.voiceassistant",
        "doubao_web" to "com.bytedance.doubao"
    )
    
    /**
     * 检查app是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 尝试跳转到app（公共方法，供外部调用）
     */
    fun tryJumpToApp(engineKey: String, query: String): Boolean {
        val packageName = engineToPackageMap[engineKey] ?: return false
        if (!isAppInstalled(packageName)) return false
        
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
            it.name == engineKey 
        }
        val engineName = searchEngine?.displayName ?: engineKey
        
        return tryJumpToAppInternal(packageName, query, engineName)
    }
    
    /**
     * 检查搜索引擎对应的app是否已安装
     */
    fun isAppInstalledForEngine(engineKey: String): Boolean {
        val packageName = engineToPackageMap[engineKey] ?: return false
        return isAppInstalled(packageName)
    }
    
    /**
     * 获取搜索引擎对应的app包名
     */
    fun getPackageNameForEngine(engineKey: String): String? {
        return engineToPackageMap[engineKey]
    }
    
    /**
     * 检查搜索引擎是否精准适配本地app
     * 精准适配的定义：搜索引擎支持网页搜索（searchUrl包含{query}）且对应的app已安装
     * 如果app已安装但搜索引擎不支持网页搜索，或者搜索引擎是AI应用，则认为没有精准适配
     */
    fun isPreciseAppAdapted(engineKey: String): Boolean {
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
            it.name == engineKey 
        } ?: return false
        
        val packageName = engineToPackageMap[engineKey] ?: return false
        if (!isAppInstalled(packageName)) return false
        
        // 检查搜索引擎是否支持网页搜索（通过检查searchUrl是否包含{query}）
        val supportsWebSearch = searchEngine.searchUrl.contains("{query}")
        
        // 如果支持网页搜索且不是AI应用，则认为精准适配
        return supportsWebSearch && !searchEngine.isAI
    }
    
    /**
     * 打开应用商店下载app
     */
    fun openAppStore(packageName: String) {
        try {
            // 首先尝试使用market://协议打开应用商店
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                android.util.Log.d(TAG, "打开应用商店: $packageName")
                return
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "无法打开应用商店（market://）", e)
        }
        
        // 如果market://不可用，尝试使用浏览器打开Google Play
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.util.Log.d(TAG, "通过浏览器打开应用商店: $packageName")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "无法打开应用商店", e)
            android.widget.Toast.makeText(context, "无法打开应用商店", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 尝试跳转到app（内部方法）
     */
    private fun tryJumpToAppInternal(packageName: String, query: String, engineName: String): Boolean {
        try {
            // 首先尝试使用URL scheme跳转（如果支持）
            val urlScheme = when (packageName) {
                "com.ss.android.ugc.aweme" -> "snssdk1128://search?keyword=${android.net.Uri.encode(query)}"
                "com.smile.gifmaker" -> "kwai://search?keyword=${android.net.Uri.encode(query)}"
                "com.sina.weibo" -> "sinaweibo://search?keyword=${android.net.Uri.encode(query)}"
                "com.zhihu.android" -> "zhihu://search?q=${android.net.Uri.encode(query)}"
                "com.xingin.xhs" -> "xhsdiscover://search?keyword=${android.net.Uri.encode(query)}"
                "com.taobao.taobao" -> "taobao://s.taobao.com?q=${android.net.Uri.encode(query)}"
                "com.jingdong.app.mall" -> "openapp.jdmobile://search?keyword=${android.net.Uri.encode(query)}"
                "com.baidu.searchbox" -> "baiduboxapp://search?keyword=${android.net.Uri.encode(query)}"
                "com.openai.chatgpt" -> "chatgpt://"
                "com.anthropic.claude" -> "claude://"
                "com.google.android.apps.bard" -> "googleassistant://"
                "com.baidu.wenxin" -> "wenxin://"
                "com.alibaba.dingtalk" -> "dingtalk://"
                "com.moonshot.kimi" -> "kimi://"
                "com.deepseek.deepseek" -> "deepseek://"
                "com.zhipuai.zhipu" -> "zhipu://"
                "com.iflytek.voiceassistant" -> "xinghuo://"
                "com.bytedance.doubao" -> "doubao://"
                else -> null
            }
            
            if (urlScheme != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urlScheme))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.`package` = packageName
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    // 在打开app之前，临时隐藏悬浮窗，让系统对话框可以显示
                    onOpenAppCallback?.invoke()
                    
                    // 延迟执行，确保悬浮窗先隐藏，系统对话框可以显示
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            context.startActivity(intent)
                            android.util.Log.d(TAG, "通过URL scheme跳转到app: $engineName")
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "跳转到app失败: $engineName", e)
                        }
                    }, 200)
                    return true
                }
            }
            
            // URL scheme失败，尝试直接启动app
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 在打开app之前，临时隐藏悬浮窗，让系统对话框可以显示
                onOpenAppCallback?.invoke()
                
                // 延迟执行，确保悬浮窗先隐藏，系统对话框可以显示
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        context.startActivity(launchIntent)
                        android.util.Log.d(TAG, "直接启动app: $engineName")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "启动app失败: $engineName", e)
                    }
                }, 200)
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "跳转到app失败: $engineName", e)
        }
        return false
    }
    
    /**
     * 执行搜索
     */
    private fun performSearch(cardData: SearchResultCardData, query: String, engineKey: String) {
        // 根据engineKey构建搜索URL
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
            it.name == engineKey 
        }
        
        // 检查是否支持网页搜索，如果不支持，尝试跳转到app
        val packageName = engineToPackageMap[engineKey]
        if (packageName != null && isAppInstalled(packageName)) {
            // 检查搜索引擎是否支持网页搜索（通过检查searchUrl是否包含{query}）
            val supportsWebSearch = searchEngine?.searchUrl?.contains("{query}") == true
            
            if (!supportsWebSearch || searchEngine?.isAI == true) {
                // 不支持网页搜索或者是AI应用，尝试跳转到app
                if (tryJumpToAppInternal(packageName, query, searchEngine?.displayName ?: engineKey)) {
                    // 跳转成功，不需要加载WebView
                    return
                }
            }
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
            fullScreenViewer = FullScreenCardViewer(context, rootContainer, this)
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
     * 获取全屏查看器（供外部调用）
     */
    fun getFullScreenViewer(): FullScreenCardViewer? {
        return fullScreenViewer
    }
    
    /**
     * 设置全屏查看器的父容器（用于在FloatingWindowManager中设置）
     */
    fun setFullScreenParentContainer(parentContainer: ViewGroup) {
        if (fullScreenViewer == null) {
            fullScreenViewer = FullScreenCardViewer(context, parentContainer, this)
        } else {
            // 如果已经创建，需要重新创建以使用新的父容器
            fullScreenViewer?.dismiss()
            fullScreenViewer = FullScreenCardViewer(context, parentContainer, this)
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

