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
    
    // 标签切换回调
    private var onTabSwitchCallback: ((Int) -> Unit)? = null

    /**
     * 搜索结果卡片数据
     */
    data class SearchResultCardData(
        val id: String,
        val webView: CustomWebView? = null, // 可以为null，用于占位卡片
        val searchQuery: String,
        val engineKey: String,
        val engineName: String,
        var title: String = "",
        var url: String = "",
        var previewImage: String? = null,
        var isExpanded: Boolean = false,
        var tag: String? = null, // 标签，用于分类管理
        var isPlaceholder: Boolean = false, // 是否为占位卡片（用于需要直接跳转app的搜索引擎）
        var packageName: String? = null // app包名（用于占位卡片）
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
     * 设置左右滑动切换标签的手势识别
     * 在卡片区域（RecyclerView）和容器边缘都可以触发标签切换
     */
    private fun setupSwipeGesture() {
        var startX = 0f
        var startY = 0f
        var isSwipeDetected = false
        val edgeThreshold = dpToPx(50) // 边缘区域阈值（转px）
        val minSwipeDistance = dpToPx(100) // 最小滑动距离（转px）
        
        // 在RecyclerView上添加手势识别（卡片区域）
        recyclerView?.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        isSwipeDetected = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val deltaX = e.x - startX
                        val deltaY = e.y - startY
                        val viewWidth = rv.width
                        val isNearEdge = startX < edgeThreshold || startX > viewWidth - edgeThreshold
                        
                        // 检测是否为水平滑动（水平距离大于垂直距离，且水平距离大于阈值）
                        // 在边缘区域更容易触发，在中间区域需要更大的滑动距离
                        val threshold = if (isNearEdge) dpToPx(80) else dpToPx(150)
                        if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > threshold) {
                            isSwipeDetected = true
                            // 阻止RecyclerView的滚动，优先处理标签切换
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (isSwipeDetected) {
                            val deltaX = e.x - startX
                            // 如果滑动距离足够大，切换标签
                            if (Math.abs(deltaX) > minSwipeDistance) {
                                val direction = if (deltaX > 0) -1 else 1 // 向右滑动切换到上一个标签，向左滑动切换到下一个标签
                                switchTabBySwipe(direction)
                                isSwipeDetected = false
                                rv.parent?.requestDisallowInterceptTouchEvent(false)
                                return true // 消费事件，阻止卡片点击
                            }
                        }
                        isSwipeDetected = false
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        isSwipeDetected = false
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return false // 不拦截，让RecyclerView正常处理
            }
        })
        
        // 在容器边缘也添加手势识别（作为备用）
        container.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isSwipeDetected = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - startX
                    val deltaY = event.y - startY
                    val viewWidth = view.width
                    val isNearEdge = startX < edgeThreshold || startX > viewWidth - edgeThreshold
                    
                    // 只在边缘区域检测滑动
                    if (isNearEdge) {
                        val threshold = dpToPx(80)
                        if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > threshold) {
                            isSwipeDetected = true
                            recyclerView?.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (isSwipeDetected) {
                        val deltaX = event.x - startX
                        if (Math.abs(deltaX) > minSwipeDistance) {
                            val direction = if (deltaX > 0) -1 else 1
                            switchTabBySwipe(direction)
                            isSwipeDetected = false
                            recyclerView?.parent?.requestDisallowInterceptTouchEvent(false)
                            return@setOnTouchListener true
                        }
                    }
                    isSwipeDetected = false
                    recyclerView?.parent?.requestDisallowInterceptTouchEvent(false)
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    isSwipeDetected = false
                    recyclerView?.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }
    
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * 设置标签切换回调
     */
    fun setOnTabSwitchCallback(callback: (Int) -> Unit) {
        onTabSwitchCallback = callback
    }
    
    /**
     * 处理左右滑动切换标签
     * @param direction -1: 向左滑动（下一个标签）, 1: 向右滑动（上一个标签）
     */
    fun switchTabBySwipe(direction: Int) {
        onTabSwitchCallback?.invoke(direction)
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
        
        // RecyclerView创建后，设置手势识别
        setupSwipeGesture()
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
        // 先检查是否需要创建占位卡片
        val packageName = engineToPackageMap[engineKey]
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }
        val supportsWebSearch = searchEngine?.searchUrl?.contains("{query}") == true
        val needsPlaceholder = packageName != null && isAppInstalled(packageName) && 
            (directAppJumpEngines.contains(engineKey) || !supportsWebSearch)
        
        val webView = if (!needsPlaceholder) {
            WebViewFactory(context).createWebView()
        } else {
            null // 占位卡片不需要WebView
        }
        
        val cardData = SearchResultCardData(
            id = System.currentTimeMillis().toString(),
            webView = webView,
            searchQuery = query,
            engineKey = engineKey,
            engineName = engineName,
            title = "$engineName - $query",
            tag = tag,
            isPlaceholder = needsPlaceholder,
            packageName = if (needsPlaceholder) packageName else null
        )
        
        cardDataList.add(cardData)
        adapter?.notifyItemInserted(cardDataList.size - 1)
        
        // 执行搜索（对于占位卡片，performSearch会处理）
        if (!needsPlaceholder) {
            performSearch(cardData, query, engineKey)
        } else {
            // 占位卡片直接显示
            val placeholderSearchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }
            cardData.title = "${placeholderSearchEngine?.displayName ?: engineName} - 点击打开应用"
            adapter?.notifyItemChanged(cardDataList.indexOf(cardData))
        }
        
        Log.d(TAG, "添加搜索结果卡片: $engineName - $query, isPlaceholder=$needsPlaceholder")
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
     * 包含所有变体，确保精准识别
     */
    private val engineToPackageMap = mapOf(
        // 抖音及其变体
        "douyin" to "com.ss.android.ugc.aweme",
        "douyin_live" to "com.ss.android.ugc.aweme",
        "douyin_short" to "com.ss.android.ugc.aweme",
        "douyin_movie" to "com.ss.android.ugc.aweme",
        "douyin_food" to "com.ss.android.ugc.aweme",
        "douyin_travel" to "com.ss.android.ugc.aweme",
        "douyin_fashion" to "com.ss.android.ugc.aweme",
        "douyin_handmake" to "com.ss.android.ugc.aweme",
        "douyin_emotion" to "com.ss.android.ugc.aweme",
        "douyin_furniture" to "com.ss.android.ugc.aweme",
        "douyin_funny" to "com.ss.android.ugc.aweme",
        "douyin_wallpaper" to "com.ss.android.ugc.aweme",
        "douyin_photo" to "com.ss.android.ugc.aweme",
        "douyin_home" to "com.ss.android.ugc.aweme",
        "douyin_car" to "com.ss.android.ugc.aweme",
        "douyin_music" to "com.ss.android.ugc.aweme",
        "douyin_paint" to "com.ss.android.ugc.aweme",
        "douyin_fitness" to "com.ss.android.ugc.aweme",
        "douyin_job" to "com.ss.android.ugc.aweme",
        "douyin_avatar" to "com.ss.android.ugc.aweme",
        "douyin_book" to "com.ss.android.ugc.aweme",
        "douyin_game" to "com.ss.android.ugc.aweme",
        "douyin_tech" to "com.ss.android.ugc.aweme",
        // 快手及其变体
        "kuaishou" to "com.smile.gifmaker",
        "kuaishou_live" to "com.smile.gifmaker",
        "kuaishou_short" to "com.smile.gifmaker",
        "kuaishou_movie" to "com.smile.gifmaker",
        "kuaishou_food" to "com.smile.gifmaker",
        "kuaishou_travel" to "com.smile.gifmaker",
        "kuaishou_fashion" to "com.smile.gifmaker",
        "kuaishou_handmake" to "com.smile.gifmaker",
        "kuaishou_emotion" to "com.smile.gifmaker",
        "kuaishou_funny" to "com.smile.gifmaker",
        "kuaishou_music" to "com.smile.gifmaker",
        "kuaishou_paint" to "com.smile.gifmaker",
        "kuaishou_fitness" to "com.smile.gifmaker",
        "kuaishou_job" to "com.smile.gifmaker",
        "kuaishou_game" to "com.smile.gifmaker",
        "kuaishou_tech" to "com.smile.gifmaker",
        // B站及其变体
        "bilibili" to "tv.danmaku.bili",
        "bilibili_live" to "tv.danmaku.bili",
        "bilibili_short" to "tv.danmaku.bili",
        "bilibili_movie" to "tv.danmaku.bili",
        "bilibili_food" to "tv.danmaku.bili",
        "bilibili_travel" to "tv.danmaku.bili",
        "bilibili_fashion" to "tv.danmaku.bili",
        "bilibili_handmake" to "tv.danmaku.bili",
        "bilibili_emotion" to "tv.danmaku.bili",
        "bilibili_furniture" to "tv.danmaku.bili",
        "bilibili_funny" to "tv.danmaku.bili",
        "bilibili_wallpaper" to "tv.danmaku.bili",
        "bilibili_photo" to "tv.danmaku.bili",
        "bilibili_home" to "tv.danmaku.bili",
        "bilibili_car" to "tv.danmaku.bili",
        "bilibili_music" to "tv.danmaku.bili",
        "bilibili_paint" to "tv.danmaku.bili",
        "bilibili_fitness" to "tv.danmaku.bili",
        "bilibili_job" to "tv.danmaku.bili",
        "bilibili_book" to "tv.danmaku.bili",
        "bilibili_game" to "tv.danmaku.bili",
        "bilibili_tech" to "tv.danmaku.bili",
        // 小红书及其变体（所有变体都映射到同一个包名，但网页模式不可用，直接跳转app）
        "xiaohongshu" to "com.xingin.xhs",
        "xiaohongshu_video" to "com.xingin.xhs",
        "xiaohongshu_food" to "com.xingin.xhs",
        "xiaohongshu_travel" to "com.xingin.xhs",
        "xiaohongshu_fashion" to "com.xingin.xhs",
        "xiaohongshu_handmake" to "com.xingin.xhs",
        "xiaohongshu_funny" to "com.xingin.xhs",
        "xiaohongshu_wallpaper" to "com.xingin.xhs",
        "xiaohongshu_photo" to "com.xingin.xhs",
        "xiaohongshu_home" to "com.xingin.xhs",
        "xiaohongshu_car" to "com.xingin.xhs",
        "xiaohongshu_emotion" to "com.xingin.xhs",
        "xiaohongshu_furniture" to "com.xingin.xhs",
        "xiaohongshu_game" to "com.xingin.xhs",
        "xiaohongshu_tech" to "com.xingin.xhs",
        "xiaohongshu_paint" to "com.xingin.xhs",
        "xiaohongshu_fitness" to "com.xingin.xhs",
        "xiaohongshu_job" to "com.xingin.xhs",
        "xiaohongshu_avatar" to "com.xingin.xhs",
        "xiaohongshu_book" to "com.xingin.xhs",
        // 其他应用
        "weibo" to "com.sina.weibo",
        "weibo_video" to "com.sina.weibo",
        "weibo_movie" to "com.sina.weibo",
        "weibo_food" to "com.sina.weibo",
        "weibo_travel" to "com.sina.weibo",
        "weibo_fashion" to "com.sina.weibo",
        "weibo_handmake" to "com.sina.weibo",
        "weibo_emotion" to "com.sina.weibo",
        "weibo_furniture" to "com.sina.weibo",
        "weibo_funny" to "com.sina.weibo",
        "weibo_photo" to "com.sina.weibo",
        "weibo_music" to "com.sina.weibo",
        "weibo_paint" to "com.sina.weibo",
        "weibo_fitness" to "com.sina.weibo",
        "weibo_job" to "com.sina.weibo",
        "weibo_book" to "com.sina.weibo",
        "weibo_game" to "com.sina.weibo",
        "weibo_tech" to "com.sina.weibo",
        "weibo_car" to "com.sina.weibo",
        "zhihu" to "com.zhihu.android",
        "zhihu_movie" to "com.zhihu.android",
        "zhihu_food" to "com.zhihu.android",
        "zhihu_travel" to "com.zhihu.android",
        "zhihu_fashion" to "com.zhihu.android",
        "zhihu_emotion" to "com.zhihu.android",
        "zhihu_furniture" to "com.zhihu.android",
        "zhihu_photo" to "com.zhihu.android",
        "zhihu_fitness" to "com.zhihu.android",
        "zhihu_job" to "com.zhihu.android",
        "zhihu_book" to "com.zhihu.android",
        "zhihu_tech" to "com.zhihu.android",
        "zhihu_car" to "com.zhihu.android",
        "taobao" to "com.taobao.taobao",
        "taobao_search" to "com.taobao.taobao",
        "taobao_home" to "com.taobao.taobao",
        "taobao_furniture" to "com.taobao.taobao",
        "taobao_short" to "com.taobao.taobao",
        "taobao_live" to "com.taobao.taobao",
        "jd" to "com.jingdong.app.mall",
        "jd_search" to "com.jingdong.app.mall",
        "jd_home" to "com.jingdong.app.mall",
        "jd_furniture" to "com.jingdong.app.mall",
        "jd_car" to "com.jingdong.app.mall",
        "jd_tech" to "com.jingdong.app.mall",
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
     * 需要直接跳转app而不在卡片内打开的搜索引擎列表
     * 这些搜索引擎的网页模式不可用或体验差，应该直接跳转到app
     */
    private val directAppJumpEngines = setOf(
        "xiaohongshu", "xiaohongshu_video", "xiaohongshu_food", "xiaohongshu_travel",
        "xiaohongshu_fashion", "xiaohongshu_handmake", "xiaohongshu_funny",
        "xiaohongshu_wallpaper", "xiaohongshu_photo", "xiaohongshu_home",
        "xiaohongshu_car", "xiaohongshu_emotion", "xiaohongshu_furniture",
        "xiaohongshu_game", "xiaohongshu_tech", "xiaohongshu_paint",
        "xiaohongshu_fitness", "xiaohongshu_job", "xiaohongshu_avatar",
        "xiaohongshu_book"
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
     * 打开app并尝试搜索，如果不支持intent搜索则复制到剪贴板
     * @param engineKey 搜索引擎key
     * @param query 搜索关键词
     * @param packageName app包名
     */
    fun openAppWithSearch(engineKey: String, query: String, packageName: String) {
        try {
            var intentHandled = false
            
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
                "tv.danmaku.bili" -> "bilibili://search?keyword=${android.net.Uri.encode(query)}"
                else -> null
            }
            
            if (urlScheme != null) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urlScheme))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.`package` = packageName
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    intentHandled = true
                    // 在打开app之前，临时隐藏悬浮窗，让系统对话框可以显示
                    onOpenAppCallback?.invoke()
                    
                    // 延迟执行，确保悬浮窗先隐藏，系统对话框可以显示
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            context.startActivity(intent)
                            android.util.Log.d(TAG, "通过URL scheme跳转到app并搜索: $packageName, query=$query")
                            android.widget.Toast.makeText(context, "已打开${getEngineName(engineKey)}并搜索", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "跳转到app失败: $packageName", e)
                            // 如果URL scheme失败，继续尝试其他方法
                            copyToClipboardAndOpenApp(engineKey, query, packageName)
                        }
                    }, 200)
                    return
                }
            }
            
            // URL scheme不支持或失败，尝试使用ACTION_SEARCH intent
            if (!intentHandled) {
                try {
                    val searchIntent = android.content.Intent(android.content.Intent.ACTION_SEARCH).apply {
                        `package` = packageName
                        putExtra(android.app.SearchManager.QUERY, query)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    if (searchIntent.resolveActivity(context.packageManager) != null) {
                        intentHandled = true
                        onOpenAppCallback?.invoke()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try {
                                context.startActivity(searchIntent)
                                android.util.Log.d(TAG, "通过ACTION_SEARCH跳转到app并搜索: $packageName, query=$query")
                                android.widget.Toast.makeText(context, "已打开${getEngineName(engineKey)}并搜索", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "ACTION_SEARCH跳转失败: $packageName", e)
                                // 如果ACTION_SEARCH失败，继续尝试其他方法
                                copyToClipboardAndOpenApp(engineKey, query, packageName)
                            }
                        }, 200)
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.d(TAG, "ACTION_SEARCH不支持: $packageName", e)
                }
            }
            
            // 如果intent搜索不支持，将文本复制到剪贴板并打开app
            if (!intentHandled) {
                copyToClipboardAndOpenApp(engineKey, query, packageName)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "打开app并搜索失败: $packageName", e)
            // 即使失败也复制到剪贴板
            copyToClipboardAndOpenApp(engineKey, query, packageName)
        }
    }
    
    /**
     * 复制搜索关键词到剪贴板并打开app
     */
    private fun copyToClipboardAndOpenApp(engineKey: String, query: String, packageName: String) {
        try {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("搜索关键词", query)
            clipboard.setPrimaryClip(clip)
            
            // 打开app
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                
                onOpenAppCallback?.invoke()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        context.startActivity(launchIntent)
                        android.util.Log.d(TAG, "打开app并复制搜索关键词到剪贴板: $packageName, query=$query")
                        android.widget.Toast.makeText(context, "已复制搜索关键词到剪贴板，请粘贴到${getEngineName(engineKey)}", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "打开app失败: $packageName", e)
                        android.widget.Toast.makeText(context, "已复制搜索关键词到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, 200)
            } else {
                android.widget.Toast.makeText(context, "已复制搜索关键词到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "复制到剪贴板失败", e)
        }
    }
    
    /**
     * 获取搜索引擎名称
     */
    private fun getEngineName(engineKey: String): String {
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
            it.name == engineKey 
        }
        return searchEngine?.displayName ?: engineKey
    }
    
    /**
     * 执行搜索
     */
    private fun performSearch(cardData: SearchResultCardData, query: String, engineKey: String) {
        // 首先检查是否是AI引擎（使用AISearchEngine）
        // 使用AIPageConfigManager获取增强配置（包含API配置）
        val aiPageConfigManager = com.example.aifloatingball.manager.AIPageConfigManager(context)
        val aiConfig = aiPageConfigManager.getConfigByKey(engineKey)
        
        // 如果是AI引擎且使用自定义HTML，则加载HTML文件
        if (aiConfig != null && aiConfig.isChatMode) {
            val useCustomHtml = aiConfig.customParams["use_custom_html"] == "true"
            val isAssetUrl = aiConfig.url.startsWith("file:///android_asset/")
            
            if (useCustomHtml && isAssetUrl) {
                // 使用自定义HTML页面
                Log.d(TAG, "卡片视图模式：加载AI引擎自定义HTML: ${aiConfig.name}, url=${aiConfig.url}")
                loadAICustomHtmlInCard(cardData, query, aiConfig)
                return
            }
        }
        
        // 如果AIPageConfigManager没找到，尝试直接从DEFAULT_AI_ENGINES查找
        if (aiConfig == null) {
            val aiSearchEngine = com.example.aifloatingball.model.AISearchEngine.DEFAULT_AI_ENGINES.find { 
                it.name == engineKey || it.name.contains(engineKey, ignoreCase = true)
            }
            
            // 如果是AI引擎且使用自定义HTML，则加载HTML文件
            if (aiSearchEngine != null && aiSearchEngine.isChatMode) {
                val useCustomHtml = aiSearchEngine.customParams["use_custom_html"] == "true"
                val isAssetUrl = aiSearchEngine.url.startsWith("file:///android_asset/")
                
                if (useCustomHtml && isAssetUrl) {
                    // 使用自定义HTML页面
                    Log.d(TAG, "卡片视图模式：加载AI引擎自定义HTML: ${aiSearchEngine.name}, url=${aiSearchEngine.url}")
                    loadAICustomHtmlInCard(cardData, query, aiSearchEngine)
                    return
                }
            }
        }
        
        // 根据engineKey构建搜索URL（普通搜索引擎）
        val searchEngine = com.example.aifloatingball.model.SearchEngine.DEFAULT_ENGINES.find { 
            it.name == engineKey 
        }
        
        // 检查是否支持网页搜索，如果不支持，创建占位卡片
        val packageName = engineToPackageMap[engineKey]
        if (packageName != null && isAppInstalled(packageName)) {
            // 特殊处理：小红书等搜索引擎的网页模式不可用，创建占位卡片
            if (directAppJumpEngines.contains(engineKey)) {
                // 创建占位卡片，显示"在小红书打开"提示
                Log.d(TAG, "小红书等搜索引擎创建占位卡片: $engineKey")
                cardData.isPlaceholder = true
                cardData.packageName = packageName
                cardData.title = "${searchEngine?.displayName ?: engineKey} - 点击打开应用"
                cardData.url = ""
                // 不加载WebView，直接显示占位卡片
                adapter?.notifyItemChanged(cardDataList.indexOf(cardData))
                return
            }
            
            // 检查搜索引擎是否支持网页搜索（通过检查searchUrl是否包含{query}）
            val supportsWebSearch = searchEngine?.searchUrl?.contains("{query}") == true
            
            if (!supportsWebSearch || searchEngine?.isAI == true) {
                // 不支持网页搜索或者是AI应用，创建占位卡片
                Log.d(TAG, "不支持网页搜索的搜索引擎创建占位卡片: $engineKey")
                cardData.isPlaceholder = true
                cardData.packageName = packageName
                cardData.title = "${searchEngine?.displayName ?: engineKey} - 点击打开应用"
                cardData.url = ""
                // 不加载WebView，直接显示占位卡片
                adapter?.notifyItemChanged(cardDataList.indexOf(cardData))
                return
            }
        }
        
        val searchUrl = if (searchEngine != null) {
            searchEngine.getSearchUrl(query)
        } else if (aiConfig != null) {
            // 如果是AI引擎但不用自定义HTML，使用AI引擎的URL
            aiConfig.getSearchUrl(query)
        } else {
            // 默认使用Google搜索
            "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        }
        
        // 设置WebViewClient，包含广告拦截功能
        val webViewForSearch = cardData.webView ?: return
        webViewForSearch.webViewClient = object : android.webkit.WebViewClient() {
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
        webViewForSearch.webChromeClient = object : android.webkit.WebChromeClient() {
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
        
        webViewForSearch.loadUrl(searchUrl)
        cardData.url = searchUrl
    }
    
    /**
     * 在卡片中加载AI自定义HTML
     */
    private fun loadAICustomHtmlInCard(
        cardData: SearchResultCardData,
        query: String,
        config: com.example.aifloatingball.model.AISearchEngine
    ) {
        Log.d(TAG, "在卡片中加载AI自定义HTML: ${config.name}, url=${config.url}")
        
        val webView = cardData.webView ?: run {
            Log.e(TAG, "WebView为null，无法加载AI自定义HTML")
            return
        }
        
        // 确保WebView设置允许加载asset文件
        webView.settings.apply {
            allowFileAccess = true
            allowContentAccess = true
            javaScriptEnabled = true
            domStorageEnabled = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }
        }
        
        // 根据配置确定AI服务类型
        val aiServiceType = getAIServiceTypeFromConfig(config)
        Log.d(TAG, "AI服务类型: $aiServiceType")
        
        // 创建AndroidChatInterface实例
        val chatInterface = com.example.aifloatingball.webview.AndroidChatInterface(
            context,
            object : com.example.aifloatingball.webview.AndroidChatInterface.WebViewCallback {
                override fun onMessageReceived(message: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val escapedMessage = message.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
                        val jsCode = """
                            (function() {
                                try {
                                    if (typeof appendToResponse === 'function') {
                                        appendToResponse('$escapedMessage');
                                    } else if (typeof appendMessage === 'function') {
                                        appendMessage('assistant', '$escapedMessage');
                                    } else if (typeof updateMessage === 'function') {
                                        updateMessage('assistant', '$escapedMessage');
                                    }
                                } catch(e) {
                                    console.error('Error updating message: ' + e.message);
                                }
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(jsCode, null)
                    }
                }
                
                override fun onMessageCompleted(fullMessage: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Log.d(TAG, "AI回复完成: ${fullMessage.take(100)}...")
                        val jsCode = """
                            (function() {
                                try {
                                    if (typeof completeResponse === 'function') {
                                        completeResponse();
                                    }
                                } catch(e) {
                                    console.error('Error completing response: ' + e.message);
                                }
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(jsCode, null)
                    }
                }
                
                override fun onNewChatStarted() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Log.d(TAG, "新对话开始")
                    }
                }
                
                override fun onSessionDeleted(sessionId: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Log.d(TAG, "会话已删除: $sessionId")
                    }
                }
            },
            aiServiceType
        )
        
        // 添加JavaScript接口
        try {
            webView.removeJavascriptInterface("AndroidChatInterface")
        } catch (e: Exception) {
            // 忽略错误
        }
        webView.addJavascriptInterface(chatInterface, "AndroidChatInterface")
        
        // 设置WebViewClient
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cardData.title = view?.title ?: cardData.title
                cardData.url = url ?: config.url
                
                // 如果有查询，延迟发送到页面并自动发送
                if (query.isNotBlank()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val escapedQuery = query.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
                        val jsCode = """
                            (function() {
                                try {
                                    var messageInput = document.getElementById('messageInput');
                                    if (messageInput) {
                                        if (messageInput.tagName === 'TEXTAREA' || messageInput.tagName === 'INPUT') {
                                            messageInput.value = '$escapedQuery';
                                            var inputEvent = new Event('input', { bubbles: true });
                                            messageInput.dispatchEvent(inputEvent);
                                        } else {
                                            messageInput.textContent = '$escapedQuery';
                                        }
                                        
                                        if (typeof updateSendButton === 'function') {
                                            updateSendButton();
                                        }
                                        
                                        if (typeof adjustTextareaHeight === 'function') {
                                            adjustTextareaHeight();
                                        }
                                        
                                        setTimeout(function() {
                                            if (typeof sendMessage === 'function') {
                                                sendMessage();
                                            } else {
                                                var sendButton = document.getElementById('send-button');
                                                if (sendButton && !sendButton.disabled) {
                                                    sendButton.click();
                                                }
                                            }
                                        }, 100);
                                    } else {
                                        console.error('messageInput element not found');
                                    }
                                } catch(e) {
                                    console.error('Error auto-sending message: ' + e.message);
                                }
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(jsCode, null)
                    }, 2000)
                }
                
                adapter?.notifyItemChanged(cardDataList.indexOf(cardData))
            }
        }
        
        // 加载HTML页面
        Log.d(TAG, "开始加载HTML页面: ${config.url}")
        webView.loadUrl(config.url)
        cardData.url = config.url
    }
    
    /**
     * 根据配置获取AI服务类型
     */
    private fun getAIServiceTypeFromConfig(config: com.example.aifloatingball.model.AISearchEngine): com.example.aifloatingball.manager.AIServiceType {
        return when {
            config.name.contains("临时专线", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.TEMP_SERVICE
            config.name.contains("DeepSeek", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.DEEPSEEK
            config.name.contains("ChatGPT", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.CHATGPT
            config.name.contains("Claude", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.CLAUDE
            config.name.contains("通义千问", ignoreCase = true) || config.name.contains("Qianwen", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.QIANWEN
            config.name.contains("智谱", ignoreCase = true) || config.name.contains("Zhipu", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.ZHIPU_AI
            config.name.contains("文心一言", ignoreCase = true) || config.name.contains("Wenxin", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.WENXIN
            config.name.contains("Gemini", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.GEMINI
            config.name.contains("Kimi", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.KIMI
            config.name.contains("讯飞星火", ignoreCase = true) || config.name.contains("Xinghuo", ignoreCase = true) -> com.example.aifloatingball.manager.AIServiceType.XINGHUO
            config.url.contains("deepseek_chat.html") -> com.example.aifloatingball.manager.AIServiceType.DEEPSEEK
            config.url.contains("chatgpt_chat.html") -> {
                // 根据配置的API URL判断
                when {
                    config.customParams["api_url"]?.contains("openai.com") == true -> com.example.aifloatingball.manager.AIServiceType.CHATGPT
                    config.customParams["api_url"]?.contains("baidubce.com") == true -> com.example.aifloatingball.manager.AIServiceType.WENXIN
                    config.customParams["api_url"]?.contains("googleapis.com") == true -> com.example.aifloatingball.manager.AIServiceType.GEMINI
                    config.customParams["api_url"]?.contains("moonshot.cn") == true -> com.example.aifloatingball.manager.AIServiceType.KIMI
                    config.customParams["api_url"]?.contains("xf-yun.com") == true -> com.example.aifloatingball.manager.AIServiceType.XINGHUO
                    config.customParams["api_url"]?.contains("818233.xyz") == true -> com.example.aifloatingball.manager.AIServiceType.TEMP_SERVICE
                    else -> com.example.aifloatingball.manager.AIServiceType.CHATGPT
                }
            }
            config.url.contains("claude_chat.html") -> com.example.aifloatingball.manager.AIServiceType.CLAUDE
            config.url.contains("qianwen_chat.html") -> com.example.aifloatingball.manager.AIServiceType.QIANWEN
            config.url.contains("zhipu_chat.html") -> com.example.aifloatingball.manager.AIServiceType.ZHIPU_AI
            else -> com.example.aifloatingball.manager.AIServiceType.DEEPSEEK
        }
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
            cardData.webView?.destroy() // 占位卡片可能没有WebView
            cardDataList.removeAt(index)
            adapter?.notifyItemRemoved(index)
        }
    }

    /**
     * 清除所有卡片
     */
    fun clearAllCards() {
        cardDataList.forEach { it.webView?.destroy() } // 占位卡片可能没有WebView
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

