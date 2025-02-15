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

class CardLayoutAdapter(
    private val engines: List<SearchEngine>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (View, Int) -> Boolean
) : RecyclerView.Adapter<CardLayoutAdapter.CardViewHolder>() {

    private val expandedPositions = mutableSetOf<Int>()
    private var currentQuery: String = ""
    private val webViews = mutableMapOf<Int, WebView>()

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.engine_title)
        val webView: WebView = itemView.findViewById(R.id.web_view)
        val optionsButton: ImageButton = itemView.findViewById(R.id.options_button)
        val contentContainer: View = itemView.findViewById(R.id.content_container)
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

        // 根据展开状态设置内容区域的可见性和高度
        if (expandedPositions.contains(position)) {
            holder.contentContainer.visibility = View.VISIBLE
            val displayMetrics = holder.itemView.context.resources.displayMetrics
            val height = (displayMetrics.heightPixels * 0.6).toInt()
            holder.contentContainer.layoutParams.height = height
            holder.webView.visibility = View.VISIBLE
            
            // 加载初始URL或搜索结果
            if (currentQuery.isNotEmpty()) {
                val url = engine.getSearchUrl(currentQuery)
                holder.webView.loadUrl(url)
            } else {
                // 如果没有搜索查询，加载默认URL
                holder.webView.loadUrl(engine.url)
            }
        } else {
            // 如果卡片被折叠，停止加载并清理WebView
            holder.webView.stopLoading()
            holder.webView.loadUrl("about:blank")
            holder.contentContainer.visibility = View.GONE
            holder.webView.visibility = View.GONE
            holder.contentContainer.layoutParams.height = 0
        }

        // 设置点击事件
        holder.itemView.setOnClickListener {
            val bindingAdapterPosition = holder.bindingAdapterPosition
            if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                toggleCard(bindingAdapterPosition)
            }
        }

        holder.optionsButton.setOnClickListener { view ->
            onCardLongClick(view, position)
        }
    }

    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 安全设置
            allowFileAccess = false
            allowContentAccess = false
            
            // 使用新的安全设置API
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }
            
            defaultTextEncodingName = "UTF-8"
            setGeolocationEnabled(false)
            
            // 启用混合内容
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 设置UA
            userAgentString = userAgentString + " Mobile"
        }
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                android.util.Log.e("WebView", "Error loading URL: $failingUrl, Error: $description")
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("WebView", "Page loaded: $url")
            }
        }
    }

    fun toggleCard(position: Int) {
        try {
            if (expandedPositions.contains(position)) {
                // 如果已经展开，则关闭
                expandedPositions.remove(position)
            } else {
                // 如果未展开，则打开
                expandedPositions.add(position)
            }

            // 更新当前点击的卡片
            notifyItemChanged(position)

            // 滚动到最近展开的卡片
            if (position in expandedPositions) {
                val recyclerView = (currentRecyclerView as? RecyclerView)
                recyclerView?.smoothScrollToPosition(position)
            }
        } catch (e: Exception) {
            android.util.Log.e("CardLayoutAdapter", "Error toggling card", e)
        }
    }

    fun isCardExpanded(position: Int) = expandedPositions.contains(position)

    fun performSearch(query: String) {
        currentQuery = query
        webViews.forEach { (position, webView) ->
            try {
                if (position in expandedPositions) {
                    val url = engines[position].getSearchUrl(query)
                    android.util.Log.d("CardLayoutAdapter", "Loading URL for position $position: $url")
                    webView.loadUrl(url)
                }
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
        expandedPositions.clear()
    }

    override fun getItemCount() = engines.size

    // 获取当前的RecyclerView
    private val currentRecyclerView: View?
        get() = try {
            webViews.values.firstOrNull()?.parent?.parent as? View
        } catch (e: Exception) {
            null
        }
} 