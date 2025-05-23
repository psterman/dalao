package com.example.aifloatingball.tab

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebView

class TabPagerAdapter(private val context: Context) : RecyclerView.Adapter<TabPagerAdapter.TabViewHolder>() {
    private var tabs: List<WebViewTab> = emptyList()

    fun updateTabs(newTabs: List<WebViewTab>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        return TabViewHolder(WebView(context))
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.webView = tab.webView
        
        // 确保WebView已从其父视图中移除
        (holder.webView.parent as? ViewGroup)?.removeView(holder.webView)
        
        // 设置WebView的布局参数
        holder.webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun getItemCount(): Int = tabs.size

    class TabViewHolder(var webView: WebView) : RecyclerView.ViewHolder(webView)
} 