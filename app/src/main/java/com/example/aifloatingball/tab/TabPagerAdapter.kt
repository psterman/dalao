package com.example.aifloatingball.tab

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebView

class TabPagerAdapter(private val context: Context) : RecyclerView.Adapter<TabPagerAdapter.TabViewHolder>() {
    private var tabs: List<WebViewTab> = emptyList()

    fun updateTabs(newTabs: List<WebViewTab>) {
        tabs = newTabs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        // 创建一个FrameLayout作为WebView的容器
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return TabViewHolder(container)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        
        // 移除之前的WebView
        holder.container.removeAllViews()
        
        // 确保WebView已从其父视图中移除
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        
        // 设置WebView的布局参数
        tab.webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        // 添加WebView到容器
        holder.container.addView(tab.webView)
        
        // 确保容器可以接收触摸事件
        holder.container.isClickable = false
        holder.container.isFocusable = false
    }

    override fun getItemCount(): Int = tabs.size

    class TabViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
} 