package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.cardview.CardViewModeManager
import com.example.aifloatingball.ui.webview.CustomWebView

/**
 * 搜索结果卡片适配器
 * 用于RecyclerView显示搜索结果卡片
 */
class SearchResultCardAdapter(
    private val cards: MutableList<CardViewModeManager.SearchResultCardData>,
    private val onCardClick: (CardViewModeManager.SearchResultCardData) -> Unit,
    private val onCardLongClick: (CardViewModeManager.SearchResultCardData) -> Unit
) : RecyclerView.Adapter<SearchResultCardAdapter.CardViewHolder>() {

    companion object {
        private const val TAG = "SearchResultCardAdapter"
    }

    /**
     * 卡片ViewHolder
     */
    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.card_title)
        val engineView: TextView = itemView.findViewById(R.id.card_engine)
        val webViewContainer: FrameLayout = itemView.findViewById(R.id.card_webview_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        if (position >= cards.size) return
        
        val cardData = cards[position]
        val context = holder.itemView.context
        
        // 检测暗色模式
        val isDarkMode = (context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 设置标题和引擎名称，支持暗色/亮色模式
        holder.titleView.text = cardData.title.ifEmpty { cardData.searchQuery }
        holder.titleView.setTextColor(
            if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF212121.toInt()
        )
        
        // 设置标题栏背景为圆角卡片样式
        val headerBackground = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dpToPx(context, 8).toFloat()
            setColor(if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt())
        }
        holder.titleView.parent?.let { parent ->
            if (parent is ViewGroup) {
                parent.background = headerBackground
            }
        }
        
        holder.engineView.text = cardData.engineName
        
        // 设置WebView
        val webView = cardData.webView
        val parent = webView.parent as? ViewGroup
        parent?.removeView(webView)
        
        holder.webViewContainer.removeAllViews()
        holder.webViewContainer.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onCardClick(cardData)
        }
        
        holder.itemView.setOnLongClickListener {
            onCardLongClick(cardData)
            true
        }
        
        // 注意：卡片标题栏的滑动返回功能在全屏模式下实现（FullScreenCardViewer中）
        // 这里不需要添加滑动功能，因为卡片列表中的标题栏点击是打开全屏
        
        // 根据展开状态调整高度
        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        if (cardData.isExpanded) {
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            layoutParams.height = dpToPx(holder.itemView.context, 300) // 默认高度300dp
        }
        holder.itemView.layoutParams = layoutParams
    }

    override fun getItemCount(): Int = cards.size

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

