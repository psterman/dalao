package com.example.aifloatingball.tab

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * 增强的标签页ViewPager适配器
 */
class EnhancedTabPagerAdapter(
    private val tabManager: EnhancedTabManager
) : RecyclerView.Adapter<EnhancedTabPagerAdapter.TabViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        return TabViewHolder(parent)
    }
    
    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tabs = tabManager.getTabs()
        val tab = tabs.getOrNull(position)
        tab?.let { holder.bind(it) }
    }

    override fun getItemCount(): Int = tabManager.getTabs().size
    
    class TabViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        android.widget.FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    ) {
        private val container = itemView as ViewGroup
        
        fun bind(tab: EnhancedWebViewTab) {
            // 清除之前的WebView
            container.removeAllViews()
            
            // 添加当前标签页的WebView
            val webView = tab.webView
            
            // 如果WebView已经有父容器，先移除
            (webView.parent as? ViewGroup)?.removeView(webView)
            
            // 添加到当前容器
            container.addView(webView)
        }
    }
}

/**
 * 标签页概览适配器
 */
class EnhancedTabOverviewAdapter(
    private val tabManager: EnhancedTabManager,
    private val onTabClick: (Int) -> Unit
) : RecyclerView.Adapter<EnhancedTabOverviewAdapter.TabOverviewViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabOverviewViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return TabOverviewViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TabOverviewViewHolder, position: Int) {
        val tabs = tabManager.getTabs()
        val tab = tabs.getOrNull(position)
        tab?.let { holder.bind(it, position, onTabClick) }
    }

    override fun getItemCount(): Int = tabManager.getTabs().size
    
    class TabOverviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText = itemView.findViewById<android.widget.TextView>(android.R.id.text1)
        private val urlText = itemView.findViewById<android.widget.TextView>(android.R.id.text2)
        
        fun bind(tab: EnhancedWebViewTab, position: Int, onTabClick: (Int) -> Unit) {
            titleText.text = tab.title
            urlText.text = tab.url ?: "about:blank"
            
            itemView.setOnClickListener {
                onTabClick(position)
            }
            
            // 添加长按删除功能
            itemView.setOnLongClickListener {
                // 这里可以显示删除确认对话框
                true
            }
        }
    }
}
