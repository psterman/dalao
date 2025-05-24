package com.example.aifloatingball

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebSettings
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import android.os.Build

class CardLayoutAdapter(
    private val engines: List<com.example.aifloatingball.model.SearchEngine>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (View, Int) -> Boolean
) : RecyclerView.Adapter<CardLayoutAdapter.CardViewHolder>() {

    private var currentQuery: String = ""
    private val webViews = mutableMapOf<Int, WebView>()
    private var fullscreenCard: View? = null
    private var fullscreenPosition: Int = -1
    private var hasInitializedCards = false

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.engine_title)
        val webView: WebView = itemView.findViewById(R.id.web_view)
        val optionsButton: ImageButton = itemView.findViewById(R.id.options_button)
        val contentContainer: View = itemView.findViewById(R.id.content_container)
        val controlBar: View = itemView.findViewById(R.id.control_bar)
        val minimizeButton: ImageButton = itemView.findViewById(R.id.btn_minimize)
        val refreshButton: ImageButton = itemView.findViewById(R.id.btn_refresh)
        val backButton: ImageButton = itemView.findViewById(R.id.btn_back)
        val forwardButton: ImageButton = itemView.findViewById(R.id.btn_forward)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_engine_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val engine = engines[position]
        holder.titleText.text = engine.name

        // 设置WebView
        setupWebView(holder.webView)
        
        // 保存WebView引用
        webViews[position] = holder.webView

        // 确保WebView立即加载内容
        if (holder.webView.url.isNullOrEmpty() || holder.webView.url == "about:blank") {
            android.util.Log.d("CardLayoutAdapter", "Loading initial URL for position $position")
            if (currentQuery.isNotEmpty()) {
                val url = engine.getSearchUrl(currentQuery)
                holder.webView.loadUrl(url)
            } else {
                holder.webView.loadUrl(engine.url)
            }
        }

        // 设置控制栏按钮事件
        holder.minimizeButton.setOnClickListener {
            exitFullscreen()
        }
        
        holder.refreshButton.setOnClickListener {
            holder.webView.reload()
        }
        
        holder.backButton.setOnClickListener {
            if (holder.webView.canGoBack()) {
                holder.webView.goBack()
            }
        }
        
        holder.forwardButton.setOnClickListener {
            if (holder.webView.canGoForward()) {
                holder.webView.goForward()
            }
        }

        // 设置卡片点击事件
        holder.itemView.setOnClickListener {
            onCardClick(position)
        }

        holder.itemView.setOnLongClickListener { view ->
            onCardLongClick(view, position)
        }

        // 显示所有卡片的内容
        holder.contentContainer.visibility = View.VISIBLE
        holder.webView.visibility = View.VISIBLE

        // 根据是否全屏设置视图状态
        if (position == fullscreenPosition) {
            holder.controlBar.visibility = View.VISIBLE
            
            // 设置全屏布局参数
            val params = holder.itemView.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            holder.itemView.layoutParams = params
        } else {
            holder.controlBar.visibility = View.GONE
            
            // 设置网格布局参数
            val params = holder.itemView.layoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.itemView.layoutParams = params
        }

        // 更新导航按钮状态
        updateNavigationButtons(holder)

        // 如果是第一次绑定且没有初始化过卡片，自动加载所有内容
        if (!hasInitializedCards) {
            android.util.Log.d("CardLayoutAdapter", "Initializing all cards")
            initializeAllCards()
            hasInitializedCards = true
        }
    }

    private fun initializeAllCards() {
        engines.forEachIndexed { position, engine ->
            val webView = webViews[position] ?: return@forEachIndexed
            if (webView.url.isNullOrEmpty() || webView.url == "about:blank") {
                android.util.Log.d("CardLayoutAdapter", "Auto-loading URL for position $position")
                webView.loadUrl(engine.url)
            }
        }
    }

    private fun updateNavigationButtons(holder: CardViewHolder) {
        holder.backButton.isEnabled = holder.webView.canGoBack()
        holder.forwardButton.isEnabled = holder.webView.canGoForward()
    }

    fun enterFullscreen(position: Int) {
        fullscreenPosition = position
        fullscreenCard = webViews[position]?.parent?.parent as? View
        
        // 通知RecyclerView重新布局
        notifyDataSetChanged()
        
        // 滚动到全屏卡片
        val recyclerView = currentRecyclerView
        recyclerView?.smoothScrollToPosition(position)
    }

    private fun exitFullscreen() {
        fullscreenPosition = -1
        fullscreenCard = null
        
        // 通知RecyclerView重新布局
        notifyDataSetChanged()
    }

    @Suppress("DEPRECATION")
    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 安全设置
            allowFileAccess = false
            allowContentAccess = false
            
            // 使用新的安全设置API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            
            defaultTextEncodingName = "UTF-8"
            setGeolocationEnabled(false)
            
            // 启用混合内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 设置UA
            userAgentString = "$userAgentString Mobile"
        }
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                android.util.Log.d("WebView", "Started loading: $url")
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                android.util.Log.e("WebView", "Error loading URL: $failingUrl, Error: $description")
                // 尝试重新加载
                view?.postDelayed({ view.reload() }, 1000)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("WebView", "Page loaded: $url")
                
                // 更新导航按钮状态
                val holder = (view?.parent?.parent as? View)?.tag as? CardViewHolder
                holder?.let { updateNavigationButtons(it) }
            }
        }
    }

    fun performSearch(query: String, engine: com.example.aifloatingball.model.SearchEngine? = null) {
        currentQuery = query
        
        // 如果指定了搜索引擎，先切换到对应的引擎
        engine?.let { targetEngine ->
            val position = engines.indexOfFirst { it.name == targetEngine.name }
            if (position != -1) {
                onCardClick(position)
            }
        }
        
        // 执行搜索
        webViews.forEach { (position, webView) ->
            try {
                val url = engines[position].getSearchUrl(query)
                android.util.Log.d("CardLayoutAdapter", "Loading URL for position $position: $url")
                webView.loadUrl(url)
            } catch (e: Exception) {
                android.util.Log.e("CardLayoutAdapter", "Error performing search for position $position", e)
            }
        }
    }

    fun cleanupWebViews() {
        webViews.values.forEach { webView ->
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.clearCache(true)
                webView.destroy()
            } catch (e: Exception) {
                android.util.Log.e("CardLayoutAdapter", "Error cleaning up WebView", e)
            }
        }
        webViews.clear()
        fullscreenPosition = -1
        fullscreenCard = null
    }

    override fun getItemCount() = engines.size

    // 获取当前的RecyclerView
    private val currentRecyclerView: RecyclerView?
        get() = try {
            (webViews.values.firstOrNull()?.parent?.parent as? View)?.parent as? RecyclerView
        } catch (e: Exception) {
            null
        }
} 