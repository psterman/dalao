package com.example.aifloatingball

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchEngineAdapter(private val engines: List<SearchEngine>) : 
    RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    private var expandedPosition = -1
    private val webViews = mutableMapOf<Int, WebView>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.engine_title)
        val contentArea: View = view.findViewById(R.id.content_container)
        val webView: WebView = view.findViewById(R.id.web_view)
        val optionsButton: ImageButton = view.findViewById(R.id.options_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_engine_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.titleText.text = engine.name

        // 设置选项按钮点击事件
        holder.optionsButton.setOnClickListener { view ->
            (view.context as? SearchActivity)?.showCardOptions(holder.itemView, position)
        }

        // 根据展开状态设置内容区域的可见性
        if (position == expandedPosition) {
            holder.contentArea.visibility = View.VISIBLE
            setupWebView(holder, position)
        } else {
            holder.contentArea.visibility = View.GONE
            holder.webView.visibility = View.GONE
        }

        // 设置卡片点击事件
        holder.itemView.setOnClickListener {
            if (expandedPosition == position) {
                // 已经展开，不做任何操作
                return@setOnClickListener
            }

            // 收起之前展开的卡片
            val previousExpanded = expandedPosition
            if (previousExpanded != -1) {
                notifyItemChanged(previousExpanded)
            }

            // 展开当前卡片
            expandedPosition = position
            setupWebView(holder, position)
            animateExpand(holder.contentArea)
        }
    }

    private fun setupWebView(holder: ViewHolder, position: Int) {
        // 将 WebView 添加到集合中
        if (!webViews.containsKey(position)) {
            webViews[position] = holder.webView
        }
        
        holder.webView.visibility = View.VISIBLE
        holder.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 添加更多安全设置
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // 设置默认编码
            defaultTextEncodingName = "UTF-8"
            
            // 启用 DOM storage API
            domStorageEnabled = true
            
            // 禁用地理位置
            setGeolocationEnabled(false)
        }
        
        // 设置 WebViewClient 来处理页面加载
        holder.webView.webViewClient = android.webkit.WebViewClient()
    }

    private fun animateExpand(contentArea: View) {
        val expandedHeight = (contentArea.context.resources.displayMetrics.heightPixels * 0.8).toInt()
        
        val anim = ValueAnimator.ofInt(0, expandedHeight)
        anim.duration = 300
        anim.interpolator = DecelerateInterpolator()
        
        anim.addUpdateListener { animator ->
            contentArea.layoutParams.height = animator.animatedValue as Int
            contentArea.requestLayout()
        }
        
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                contentArea.visibility = View.VISIBLE
            }
        })
        
        anim.start()
    }

    fun performSearch(query: String) {
        engines.forEachIndexed { position, engine ->
            try {
                val webView = webViews[position] ?: return@forEachIndexed
                val url = engine.getSearchUrl(query)
                webView.loadUrl(url)
            } catch (e: Exception) {
                android.util.Log.e("SearchEngineAdapter", "Error loading URL for engine $position", e)
            }
        }
    }

    fun expandCard(position: Int) {
        if (position != expandedPosition) {
            val previousExpanded = expandedPosition
            expandedPosition = position
            if (previousExpanded != -1) {
                notifyItemChanged(previousExpanded)
            }
            notifyItemChanged(position)
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
                android.util.Log.e("SearchEngineAdapter", "Error cleaning up WebView", e)
            }
        }
        webViews.clear()
    }

    override fun getItemCount() = engines.size
} 