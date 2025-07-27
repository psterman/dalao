package com.example.aifloatingball.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.webview.GestureCardWebViewManager
import com.google.android.material.card.MaterialCardView

/**
 * 卡片预览覆盖层
 * 显示所有WebView卡片的缩略图，支持并列显示和快速切换
 */
class CardPreviewOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CardPreviewAdapter
    private lateinit var titleText: TextView
    private lateinit var closeButton: View
    
    private var onCardClickListener: OnCardClickListener? = null
    private var onCloseListener: OnCloseListener? = null

    /**
     * 卡片点击监听器
     */
    interface OnCardClickListener {
        fun onCardClick(cardData: GestureCardWebViewManager.WebViewCardData, position: Int)
        fun onCardClose(cardData: GestureCardWebViewManager.WebViewCardData, position: Int)
    }

    /**
     * 关闭监听器
     */
    interface OnCloseListener {
        fun onClose()
    }

    init {
        setupView()
    }

    /**
     * 设置视图
     */
    private fun setupView() {
        // 设置背景
        setBackgroundColor(0x88000000.toInt())
        isClickable = true
        
        // 加载布局
        val view = LayoutInflater.from(context).inflate(R.layout.card_preview_overlay, this, true)
        
        // 初始化组件
        recyclerView = view.findViewById(R.id.cards_recycler_view)
        titleText = view.findViewById(R.id.preview_title)
        closeButton = view.findViewById(R.id.preview_close_button)
        
        // 设置RecyclerView
        setupRecyclerView()
        
        // 设置关闭按钮
        closeButton.setOnClickListener {
            hide()
        }
        
        // 点击背景关闭
        setOnClickListener {
            hide()
        }
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        // 使用网格布局，每行2个卡片
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        
        // 初始化适配器
        adapter = CardPreviewAdapter { cardData, position, action ->
            when (action) {
                CardPreviewAdapter.Action.CLICK -> {
                    onCardClickListener?.onCardClick(cardData, position)
                    hide()
                }
                CardPreviewAdapter.Action.CLOSE -> {
                    onCardClickListener?.onCardClose(cardData, position)
                }
            }
        }
        
        recyclerView.adapter = adapter
    }

    /**
     * 显示预览
     */
    fun show(cards: List<GestureCardWebViewManager.WebViewCardData>) {
        // 更新数据
        adapter.updateCards(cards)
        titleText.text = "卡片预览 (${cards.size})"
        
        // 显示动画
        visibility = View.VISIBLE
        alpha = 0f
        scaleX = 0.8f
        scaleY = 0.8f
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(this, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(this, "scaleX", 0.8f, 1f),
            ObjectAnimator.ofFloat(this, "scaleY", 0.8f, 1f)
        )
        animatorSet.duration = 300
        animatorSet.start()
    }

    /**
     * 隐藏预览
     */
    fun hide() {
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            ObjectAnimator.ofFloat(this, "alpha", 1f, 0f),
            ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.8f),
            ObjectAnimator.ofFloat(this, "scaleY", 1f, 0.8f)
        )
        animatorSet.duration = 200
        animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                visibility = View.GONE
                onCloseListener?.onClose()
            }
        })
        animatorSet.start()
    }

    /**
     * 设置监听器
     */
    fun setOnCardClickListener(listener: OnCardClickListener) {
        this.onCardClickListener = listener
    }

    fun setOnCloseListener(listener: OnCloseListener) {
        this.onCloseListener = listener
    }

    /**
     * 卡片预览适配器
     */
    class CardPreviewAdapter(
        private val onActionListener: (GestureCardWebViewManager.WebViewCardData, Int, Action) -> Unit
    ) : RecyclerView.Adapter<CardPreviewAdapter.CardViewHolder>() {

        enum class Action {
            CLICK, CLOSE
        }

        private val cards = mutableListOf<GestureCardWebViewManager.WebViewCardData>()

        class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardView: MaterialCardView = itemView.findViewById(R.id.card_preview_item)
            val thumbnailView: ImageView = itemView.findViewById(R.id.card_thumbnail)
            val titleView: TextView = itemView.findViewById(R.id.card_title)
            val urlView: TextView = itemView.findViewById(R.id.card_url)
            val closeButton: View = itemView.findViewById(R.id.card_close_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_card_preview, parent, false)
            return CardViewHolder(view)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val cardData = cards[position]
            
            // 设置标题
            holder.titleView.text = cardData.title.let { title ->
                if (title.length > 20) title.substring(0, 20) + "..." else title
            }
            
            // 设置URL
            holder.urlView.text = cardData.url.let { url ->
                if (url.length > 30) url.substring(0, 30) + "..." else url
            }
            
            // 生成WebView缩略图
            generateThumbnail(cardData.webView, holder.thumbnailView)
            
            // 设置点击监听器
            holder.cardView.setOnClickListener {
                onActionListener(cardData, position, Action.CLICK)
            }
            
            // 设置关闭按钮
            holder.closeButton.setOnClickListener {
                onActionListener(cardData, position, Action.CLOSE)
            }
        }

        override fun getItemCount(): Int = cards.size

        /**
         * 更新卡片数据
         */
        fun updateCards(newCards: List<GestureCardWebViewManager.WebViewCardData>) {
            cards.clear()
            cards.addAll(newCards)
            notifyDataSetChanged()
        }

        /**
         * 生成WebView缩略图
         */
        private fun generateThumbnail(webView: android.webkit.WebView, imageView: ImageView) {
            try {
                // 创建缩略图
                val bitmap = Bitmap.createBitmap(
                    webView.width.coerceAtLeast(1),
                    webView.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                webView.draw(canvas)
                
                // 设置到ImageView
                imageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                // 如果生成缩略图失败，使用默认图标
                imageView.setImageResource(R.drawable.ic_web)
            }
        }
    }
}
