package com.example.aifloatingball.views

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.example.aifloatingball.R

/**
 * WebView卡片组件
 * 显示一个WebView页面的卡片形式，包含标题、图标、关闭按钮等
 */
class WebViewCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "WebViewCard"
    }

    // UI组件
    private lateinit var titleText: TextView
    private lateinit var faviconImage: ImageView
    private lateinit var closeButton: ImageButton
    private lateinit var webViewContainer: FrameLayout
    private lateinit var overlayView: View

    // WebView相关
    private var webView: WebView? = null
    private var pageTitle: String = "新标签页"
    private var pageUrl: String = "about:blank"
    private var favicon: Bitmap? = null

    // 回调接口
    private var onCardClickListener: OnCardClickListener? = null
    private var onCardCloseListener: OnCardCloseListener? = null

    /**
     * 卡片点击监听器
     */
    interface OnCardClickListener {
        fun onCardClick(card: WebViewCard)
        fun onCardLongClick(card: WebViewCard)
    }

    /**
     * 卡片关闭监听器
     */
    interface OnCardCloseListener {
        fun onCardClose(card: WebViewCard)
    }

    init {
        initView()
        setupListeners()
    }

    /**
     * 初始化视图
     */
    private fun initView() {
        // 设置卡片属性
        radius = 16f
        cardElevation = 8f
        useCompatPadding = true

        // 加载布局
        val view = LayoutInflater.from(context).inflate(R.layout.webview_card_layout, this, true)

        // 初始化组件
        titleText = view.findViewById(R.id.card_title)
        faviconImage = view.findViewById(R.id.card_favicon)
        closeButton = view.findViewById(R.id.card_close_button)
        webViewContainer = view.findViewById(R.id.card_webview_container)
        overlayView = view.findViewById(R.id.card_overlay)

        // 设置初始状态
        updateCardInfo()
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 卡片点击事件
        setOnClickListener {
            onCardClickListener?.onCardClick(this)
        }

        // 卡片长按事件
        setOnLongClickListener {
            onCardClickListener?.onCardLongClick(this)
            true
        }

        // 关闭按钮点击事件
        closeButton.setOnClickListener {
            onCardCloseListener?.onCardClose(this)
        }
    }

    /**
     * 设置WebView
     */
    fun setWebView(webView: WebView) {
        // 移除旧的WebView
        this.webView?.let { oldWebView ->
            (oldWebView.parent as? FrameLayout)?.removeView(oldWebView)
        }

        // 设置新的WebView
        this.webView = webView
        webViewContainer.removeAllViews()
        webViewContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        Log.d(TAG, "设置WebView到卡片")
    }

    /**
     * 获取WebView
     */
    fun getWebView(): WebView? = webView

    /**
     * 设置页面标题
     */
    fun setPageTitle(title: String) {
        pageTitle = title
        updateCardInfo()
    }

    /**
     * 获取页面标题
     */
    fun getPageTitle(): String = pageTitle

    /**
     * 设置页面URL
     */
    fun setPageUrl(url: String) {
        pageUrl = url
        updateCardInfo()
    }

    /**
     * 获取页面URL
     */
    fun getPageUrl(): String = pageUrl

    /**
     * 设置页面图标
     */
    fun setFavicon(bitmap: Bitmap?) {
        favicon = bitmap
        updateCardInfo()
    }

    /**
     * 获取页面图标
     */
    fun getFavicon(): Bitmap? = favicon

    /**
     * 更新卡片信息显示
     */
    private fun updateCardInfo() {
        titleText.text = if (pageTitle.isNotEmpty()) pageTitle else "新标签页"
        
        if (favicon != null) {
            faviconImage.setImageBitmap(favicon)
        } else {
            faviconImage.setImageResource(R.drawable.ic_web)
        }
    }

    /**
     * 设置卡片点击监听器
     */
    fun setOnCardClickListener(listener: OnCardClickListener) {
        onCardClickListener = listener
    }

    /**
     * 设置卡片关闭监听器
     */
    fun setOnCardCloseListener(listener: OnCardCloseListener) {
        onCardCloseListener = listener
    }

    /**
     * 显示覆盖层（用于防止WebView拦截触摸事件）
     */
    fun showOverlay() {
        overlayView.visibility = View.VISIBLE
    }

    /**
     * 隐藏覆盖层
     */
    fun hideOverlay() {
        overlayView.visibility = View.GONE
    }

    /**
     * 设置卡片是否可交互
     */
    fun setInteractive(interactive: Boolean) {
        if (interactive) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }

    /**
     * 销毁卡片
     */
    fun destroy() {
        webView?.let { webView ->
            webViewContainer.removeView(webView)
            webView.destroy()
        }
        webView = null
        Log.d(TAG, "销毁WebView卡片")
    }
}
