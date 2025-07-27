package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.webview.GestureCardWebViewManager

/**
 * 手势卡片适配器
 * 用于ViewPager2显示全屏WebView卡片
 */
class GestureCardAdapter(
    private val cards: MutableList<GestureCardWebViewManager.WebViewCardData>,
    private val onWebViewSetup: (WebView, GestureCardWebViewManager.WebViewCardData) -> Unit
) : RecyclerView.Adapter<GestureCardAdapter.CardViewHolder>() {

    /**
     * 卡片ViewHolder
     */
    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = itemView as FrameLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        // 创建全屏容器
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        return CardViewHolder(container)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val cardData = cards[position]
        val webView = cardData.webView
        
        // 移除WebView的旧父容器
        (webView.parent as? ViewGroup)?.removeView(webView)
        
        // 清空容器
        holder.container.removeAllViews()
        
        // 添加WebView到容器
        holder.container.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 设置WebView回调（如果需要）
        onWebViewSetup(webView, cardData)
    }

    override fun getItemCount(): Int = cards.size

    override fun onViewRecycled(holder: CardViewHolder) {
        super.onViewRecycled(holder)
        // 清理容器，避免内存泄漏
        holder.container.removeAllViews()
    }
}
