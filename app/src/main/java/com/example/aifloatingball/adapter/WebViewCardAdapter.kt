package com.example.aifloatingball.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.views.WebViewCard
import com.example.aifloatingball.webview.CardWebViewManager

/**
 * WebView卡片适配器
 * 用于RecyclerView管理多个WebView卡片
 */
class WebViewCardAdapter(
    private val cards: MutableList<CardWebViewManager.WebViewCardData>,
    private val onCardClick: (CardWebViewManager.WebViewCardData) -> Unit,
    private val onCardLongClick: (CardWebViewManager.WebViewCardData) -> Unit,
    private val onCardClose: (CardWebViewManager.WebViewCardData) -> Unit
) : RecyclerView.Adapter<WebViewCardAdapter.CardViewHolder>() {

    /**
     * 卡片ViewHolder
     */
    class CardViewHolder(val cardView: WebViewCard) : RecyclerView.ViewHolder(cardView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val cardView = WebViewCard(parent.context).apply {
            // 设置卡片布局参数
            val layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                400 // 固定高度，可以根据需要调整
            ).apply {
                setMargins(8, 8, 8, 8)
            }
            this.layoutParams = layoutParams
        }
        return CardViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        if (position < cards.size) {
            val cardData = cards[position]
            val cardView = holder.cardView

            // 设置卡片数据
            cardView.setWebView(cardData.webView)
            cardView.setPageTitle(cardData.title)
            cardView.setPageUrl(cardData.url)
            cardView.setFavicon(cardData.favicon)

            // 保存卡片引用到数据中
            cardData.card = cardView

            // 设置卡片为非交互模式（防止WebView拦截触摸事件）
            cardView.setInteractive(false)

            // 设置监听器
            cardView.setOnCardClickListener(object : WebViewCard.OnCardClickListener {
                override fun onCardClick(card: WebViewCard) {
                    onCardClick(cardData)
                }

                override fun onCardLongClick(card: WebViewCard) {
                    onCardLongClick(cardData)
                }
            })

            cardView.setOnCardCloseListener(object : WebViewCard.OnCardCloseListener {
                override fun onCardClose(card: WebViewCard) {
                    onCardClose(cardData)
                }
            })
        }
    }

    override fun getItemCount(): Int = cards.size

    /**
     * 更新卡片数据
     */
    fun updateCard(position: Int, cardData: CardWebViewManager.WebViewCardData) {
        if (position in 0 until cards.size) {
            cards[position] = cardData
            notifyItemChanged(position)
        }
    }

    /**
     * 添加卡片
     */
    fun addCard(cardData: CardWebViewManager.WebViewCardData) {
        cards.add(cardData)
        notifyItemInserted(cards.size - 1)
    }

    /**
     * 移除卡片
     */
    fun removeCard(position: Int) {
        if (position in 0 until cards.size) {
            cards.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    /**
     * 清空所有卡片
     */
    fun clearCards() {
        val size = cards.size
        cards.clear()
        notifyItemRangeRemoved(0, size)
    }
}
