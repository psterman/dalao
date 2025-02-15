package com.example.aifloatingball

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebSettings

class CardLayoutAdapter(
    private val engines: List<SearchEngine>,
    private val onCardClick: (Int) -> Unit,
    private val onCardLongClick: (View, Int) -> Boolean
) : RecyclerView.Adapter<CardLayoutAdapter.CardViewHolder>() {

    private var expandedPosition = -1
    private var lastExpandedPosition = -1
    private var currentQuery: String = ""

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.engine_title)
        val webView: WebView = itemView.findViewById(R.id.web_view)
        val optionsButton: ImageButton = itemView.findViewById(R.id.options_button)
        val contentContainer: View = itemView.findViewById(R.id.content_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_engine_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val engine = engines[position]
        holder.titleText.text = engine.name

        // 设置WebView
        holder.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        // 设置点击事件
        holder.itemView.setOnClickListener { onCardClick(position) }
        holder.itemView.setOnLongClickListener { view -> onCardLongClick(view, position) }
        holder.optionsButton.setOnClickListener { view ->
            onCardLongClick(view, position)
        }

        // 根据展开状态设置内容区域的可见性
        val isExpanded = position == expandedPosition
        holder.contentContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.itemView.isActivated = isExpanded

        // 如果卡片展开且有搜索查询，加载网页
        if (isExpanded && currentQuery.isNotEmpty()) {
            val url = engine.url.replace("%s", currentQuery)
            holder.webView.loadUrl(url)
        }
    }

    override fun getItemCount() = engines.size

    fun isCardExpanded(position: Int) = position == expandedPosition

    fun expandCard(position: Int) {
        // 保存上一个展开的位置
        lastExpandedPosition = expandedPosition
        // 设置新的展开位置
        expandedPosition = position
        
        // 通知适配器更新这两个位置的视图
        notifyItemChanged(lastExpandedPosition)
        notifyItemChanged(expandedPosition)

        // 展开动画
        val holder = getViewHolder(position)
        holder?.let {
            val contentContainer = it.contentContainer
            contentContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val targetHeight = contentContainer.measuredHeight

            // 开始时高度为0
            contentContainer.layoutParams.height = 0
            contentContainer.visibility = View.VISIBLE

            val animator = ValueAnimator.ofInt(0, targetHeight)
            animator.duration = 300
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                contentContainer.layoutParams.height = animation.animatedValue as Int
                contentContainer.requestLayout()
            }
            animator.start()
        }
    }

    fun collapseCard(position: Int) {
        val holder = getViewHolder(position)
        holder?.let {
            val contentContainer = it.contentContainer
            val initialHeight = contentContainer.height

            val animator = ValueAnimator.ofInt(initialHeight, 0)
            animator.duration = 300
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { animation ->
                contentContainer.layoutParams.height = animation.animatedValue as Int
                contentContainer.requestLayout()
            }
            animator.start()

            // 重置展开状态
            expandedPosition = -1
            notifyItemChanged(position)
        }
    }

    private fun getViewHolder(position: Int): CardViewHolder? {
        val recyclerView = currentRecyclerView
        return recyclerView?.findViewHolderForAdapterPosition(position) as? CardViewHolder
    }

    // 添加一个属性来获取当前的RecyclerView
    private val currentRecyclerView: RecyclerView?
        get() = if (itemCount > 0) {
            val firstHolder = getFirstHolder()
            firstHolder?.itemView?.parent as? RecyclerView
        } else null

    private fun getFirstHolder(): CardViewHolder? {
        return try {
            val recyclerView = currentRecyclerView
            recyclerView?.findViewHolderForAdapterPosition(0) as? CardViewHolder
        } catch (e: Exception) {
            null
        }
    }

    fun performSearch(query: String) {
        currentQuery = query
        if (expandedPosition != -1) {
            notifyItemChanged(expandedPosition)
        }
    }

    fun cleanupWebViews() {
        // 清理所有WebView
        for (i in 0 until itemCount) {
            getViewHolder(i)?.webView?.destroy()
        }
    }
} 