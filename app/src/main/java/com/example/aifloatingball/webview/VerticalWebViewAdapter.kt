package com.example.aifloatingball.webview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

/**
 * 纵向WebView适配器
 * 用于在RecyclerView中显示WebView列表
 */
class VerticalWebViewAdapter(
    private val context: Context,
    private val webViewManager: VerticalWebViewManager
) : RecyclerView.Adapter<VerticalWebViewAdapter.WebViewViewHolder>() {
    
    private val webViews = mutableListOf<VerticalWebViewManager.VerticalWebViewData>()
    
    init {
        // 监听WebView变化
        webViewManager.setOnWebViewChangeListener(object : VerticalWebViewManager.OnWebViewChangeListener {
            override fun onWebViewAdded(webViewData: VerticalWebViewManager.VerticalWebViewData, position: Int) {
                webViews.add(position, webViewData)
                notifyItemInserted(position)
            }
            
            override fun onWebViewRemoved(webViewData: VerticalWebViewManager.VerticalWebViewData, position: Int) {
                webViews.removeAt(position)
                notifyItemRemoved(position)
            }
            
            override fun onWebViewSwitched(webViewData: VerticalWebViewManager.VerticalWebViewData, position: Int) {
                notifyDataSetChanged()
            }
            
            override fun onWebViewPositionChanged(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition, toPosition)
            }
        })
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WebViewViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_vertical_webview, parent, false)
        return WebViewViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WebViewViewHolder, position: Int) {
        val webViewData = webViews[position]
        holder.bind(webViewData)
    }
    
    override fun getItemCount(): Int = webViews.size
    
    /**
     * WebView视图持有者
     */
    class WebViewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.webview_title)
        private val urlText: TextView = itemView.findViewById(R.id.webview_url)
        
        fun bind(webViewData: VerticalWebViewManager.VerticalWebViewData) {
            titleText.text = webViewData.title
            urlText.text = webViewData.url ?: "about:blank"
        }
    }
}
