package com.example.aifloatingball.adapter

import android.view.ViewGroup
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.webview.MultiPageWebViewManager

/**
 * WebView页面适配器
 * 用于ViewPager2管理多个WebView页面
 */
class WebViewPagerAdapter(
    private val pages: MutableList<MultiPageWebViewManager.WebViewPage>,
    private val onWebViewSetup: (WebView, MultiPageWebViewManager.WebViewPage) -> Unit
) : RecyclerView.Adapter<WebViewPagerAdapter.WebViewViewHolder>() {

    /**
     * WebView ViewHolder
     */
    class WebViewViewHolder(containerView: android.view.View) : RecyclerView.ViewHolder(containerView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebViewViewHolder {
        // 创建一个容器来包装WebView，确保布局参数正确
        val container = android.widget.FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return WebViewViewHolder(container)
    }

    override fun onBindViewHolder(holder: WebViewViewHolder, position: Int) {
        if (position < pages.size) {
            val page = pages[position]

            // 移除WebView的父容器（如果有的话）
            val webView = page.webView
            (webView.parent as? ViewGroup)?.removeView(webView)

            // 设置WebView的布局参数为MATCH_PARENT
            webView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )

            // 获取容器并添加WebView
            val container = holder.itemView as android.widget.FrameLayout
            container.removeAllViews()
            container.addView(webView)

            // 设置WebView配置
            onWebViewSetup(webView, page)
        }
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemId(position: Int): Long {
        return if (position < pages.size) {
            pages[position].id.hashCode().toLong()
        } else {
            RecyclerView.NO_ID
        }
    }

    init {
        setHasStableIds(true)
    }
}
