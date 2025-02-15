package com.example.aifloatingball.adapter

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AIEngine
import com.example.aifloatingball.R
import android.widget.FrameLayout

class CardLayoutAdapter(
    private val engines: List<AIEngine>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (View, Int) -> Boolean
) : RecyclerView.Adapter<CardLayoutAdapter.CardViewHolder>() {

    private var currentQuery: String = ""
    private val webViews = mutableMapOf<Int, WebView>()
    private var fullscreenCard: View? = null
    private var fullscreenPosition: Int = -1
    private var hasInitializedCards = false
    private var activePosition = -1

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val engineIcon: ImageView = view.findViewById(R.id.engine_icon)
        val engineName: TextView = view.findViewById(R.id.engine_name)
        val webView: WebView = view.findViewById(R.id.web_view)
        val contentContainer: View = view.findViewById(R.id.content_container)
        val controlBar: View = view.findViewById(R.id.control_bar)
        val minimizeButton: ImageButton = view.findViewById(R.id.btn_minimize)
        val refreshButton: ImageButton = view.findViewById(R.id.btn_refresh)
        val backButton: ImageButton = view.findViewById(R.id.btn_back)
        val forwardButton: ImageButton = view.findViewById(R.id.btn_forward)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_ai_engine, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val engine = engines[position]
        
        holder.engineIcon.setImageResource(engine.iconResId)
        holder.engineName.text = engine.name
        
        // 设置WebView
        if (!webViews.containsKey(position)) {
            setupWebView(holder.webView)
            webViews[position] = holder.webView
            
            // 确保WebView立即加载内容
            if (holder.webView.url.isNullOrEmpty() || holder.webView.url == "about:blank") {
                android.util.Log.d("CardLayoutAdapter", "Loading initial URL for position $position")
                if (currentQuery.isNotEmpty()) {
                    val url = engine.url + currentQuery
                    holder.webView.loadUrl(url)
                } else {
                    holder.webView.loadUrl(engine.url)
                }
            }
        } else {
            // 重用已存在的WebView
            val existingWebView = webViews[position]
            val parent = existingWebView?.parent as? ViewGroup
            parent?.removeView(existingWebView)
            
            // 替换布局中的WebView
            val container = holder.itemView.findViewById<FrameLayout>(R.id.content_container)
            container.removeAllViews()
            container.addView(existingWebView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
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
            activePosition = position
            onCardClick(position)
        }

        holder.itemView.setOnLongClickListener { view ->
            onCardLongClick(view, position)
        }

        // 根据是否是活跃卡片来设置可见性
        holder.contentContainer.visibility = if (position == activePosition || position == fullscreenPosition) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        holder.webView.visibility = if (position == activePosition || position == fullscreenPosition) {
            View.VISIBLE
        } else {
            View.INVISIBLE  // 使用INVISIBLE而不是GONE以保持状态
        }

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

    @Suppress("DEPRECATION")
    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT  // 修改为默认缓存模式
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
            
            // 启用DOM存储
            domStorageEnabled = true
            
            // 设置缓存模式
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            
            // 启用数据库
            databaseEnabled = true
            
            // 启用 DOM storage API
            domStorageEnabled = true
        }
        
        // 保持WebView状态
        webView.setWillNotDraw(false)
        
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
                val holder = (view?.parent?.parent?.parent as? View)?.tag as? CardViewHolder
                holder?.let { updateNavigationButtons(it) }
            }
        }
    }

    private fun updateNavigationButtons(holder: CardViewHolder) {
        holder.backButton.isEnabled = holder.webView.canGoBack()
        holder.forwardButton.isEnabled = holder.webView.canGoForward()
    }

    private fun exitFullscreen() {
        fullscreenPosition = -1
        fullscreenCard = null
        notifyDataSetChanged()
    }

    override fun getItemCount() = engines.size
} 