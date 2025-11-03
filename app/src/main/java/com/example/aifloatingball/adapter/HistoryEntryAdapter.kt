package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.HistoryEntry
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs

/**
 * 历史记录适配器：支持横滑显示收藏/删除按钮
 */
class HistoryEntryAdapter(
    private var entries: List<HistoryEntry> = emptyList(),
    private val onItemClick: (HistoryEntry) -> Unit = {},
    private val onMoreClick: (HistoryEntry) -> Unit = {},
    private val onSwipeFavorite: (HistoryEntry) -> Unit = {},
    private val onSwipeDelete: (HistoryEntry) -> Unit = {},
    private val isLeftHandedMode: Boolean = false
) : RecyclerView.Adapter<HistoryEntryAdapter.ViewHolder>() {

    // 内部数据，便于删除时做动画更新
    private var items: MutableList<HistoryEntry> = entries.toMutableList()
    private var currentSwipedHolder: ViewHolder? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContent: MaterialCardView = itemView.findViewById(R.id.card_content)
        val swipeBackgroundRight: LinearLayout = itemView.findViewById(R.id.swipe_background_right)
        val swipeBackgroundLeft: LinearLayout = itemView.findViewById(R.id.swipe_background_left)
        val ivSiteIcon: ImageView = itemView.findViewById(R.id.iv_site_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvUrl: TextView = itemView.findViewById(R.id.tv_url)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
        val btnSwipeFavorite: ImageButton = itemView.findViewById(R.id.btn_swipe_favorite)
        val btnSwipeDelete: ImageButton = itemView.findViewById(R.id.btn_swipe_delete)
        val btnSwipeFavoriteLeft: ImageButton = itemView.findViewById(R.id.btn_swipe_favorite_left)
        val btnSwipeDeleteLeft: ImageButton = itemView.findViewById(R.id.btn_swipe_delete_left)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]

        // 绑定数据
        holder.tvTitle.text = entry.title
        holder.tvUrl.text = entry.url
        holder.tvTime.text = entry.getFormattedTime()

        // favicon
        holder.ivSiteIcon.setImageResource(R.drawable.ic_web)
        try {
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(
                holder.ivSiteIcon, entry.url, R.drawable.ic_web
            )
        } catch (_: Exception) {
            holder.ivSiteIcon.setImageResource(R.drawable.ic_web)
        }

        // 初始状态
        holder.cardContent.translationX = 0f
        holder.swipeBackgroundRight.visibility = View.GONE
        holder.swipeBackgroundLeft.visibility = View.GONE

        // 点击事件
        holder.cardContent.setOnClickListener {
            if (abs(holder.cardContent.translationX) < 10f) onItemClick(entry)
        }
        holder.btnMore.setOnClickListener { onMoreClick(entry) }

        // 按钮触摸时禁止父级拦截
        val disallowInterceptTouch = View.OnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    var p: android.view.ViewParent? = v.parent
                    while (p != null) { p.requestDisallowInterceptTouchEvent(true); p = (p as? View)?.parent }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    var p: android.view.ViewParent? = v.parent
                    while (p != null) { p.requestDisallowInterceptTouchEvent(true); p = (p as? View)?.parent }
                    false
                }
                MotionEvent.ACTION_CANCEL -> {
                    var p: android.view.ViewParent? = v.parent
                    while (p != null) { p.requestDisallowInterceptTouchEvent(false); p = (p as? View)?.parent }
                    false
                }
                else -> false
            }
        }
        holder.btnSwipeFavorite.setOnTouchListener(disallowInterceptTouch)
        holder.btnSwipeDelete.setOnTouchListener(disallowInterceptTouch)
        holder.btnSwipeFavoriteLeft.setOnTouchListener(disallowInterceptTouch)
        holder.btnSwipeDeleteLeft.setOnTouchListener(disallowInterceptTouch)

        // 为按钮背景区域设置触摸监听器，确保按钮可以接收触摸事件
        // 背景区域的触摸监听器：禁止父视图拦截，让按钮可以接收事件
        val backgroundTouchListener = View.OnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 禁止所有父视图拦截触摸事件，让子按钮可以接收
                    var currentParent: android.view.ViewParent? = v.parent
                    while (currentParent != null) {
                        val parentToCall = currentParent
                        parentToCall.requestDisallowInterceptTouchEvent(true)
                        currentParent = (parentToCall as? View)?.parent
                    }
                    false // 不消费事件，让子按钮处理
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复事件拦截
                    var currentParent: android.view.ViewParent? = v.parent
                    while (currentParent != null) {
                        val parentToCall = currentParent
                        parentToCall.requestDisallowInterceptTouchEvent(false)
                        currentParent = (parentToCall as? View)?.parent
                    }
                    false
                }
                else -> false
            }
        }
        holder.swipeBackgroundRight.setOnTouchListener(backgroundTouchListener)
        holder.swipeBackgroundLeft.setOnTouchListener(backgroundTouchListener)

        // 按钮点击
        holder.btnSwipeFavorite.setOnClickListener {
            try { onSwipeFavorite(entry) } catch (_: Exception) {}
            resetSwipeImmediate(holder)
        }
        holder.btnSwipeDelete.setOnClickListener {
            try { onSwipeDelete(entry) } catch (_: Exception) {}
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) removeAt(pos) else resetSwipeImmediate(holder)
        }
        holder.btnSwipeFavoriteLeft.setOnClickListener {
            try { onSwipeFavorite(entry) } catch (_: Exception) {}
            resetSwipeImmediate(holder)
        }
        holder.btnSwipeDeleteLeft.setOnClickListener {
            try { onSwipeDelete(entry) } catch (_: Exception) {}
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) removeAt(pos) else resetSwipeImmediate(holder)
        }
    }

    /**
     * 处理滑动：仅允许一个方向（左手模式允许右滑，普通模式允许左滑）
     */
    fun handleSwipe(holder: ViewHolder, dx: Float, isActive: Boolean = false) {
        if (isActive && currentSwipedHolder != null && currentSwipedHolder != holder) {
            currentSwipedHolder?.let { resetSwipeImmediate(it) }
        }
        if (isActive && abs(dx) > 10f) currentSwipedHolder = holder

        val cardWidth = holder.cardContent.width.toFloat()
        if (cardWidth == 0f) return

        val maxSwipeDistance = 120f * holder.itemView.context.resources.displayMetrics.density
        val isValidSwipe = if (isLeftHandedMode) dx > 0 else dx < 0
        if (!isValidSwipe && abs(dx) > 10f) return

        val limitedDx = if (isLeftHandedMode) dx.coerceIn(0f, maxSwipeDistance) else dx.coerceIn(-maxSwipeDistance, 0f)
        val swipeDistance = abs(limitedDx)
        val showButtonThreshold = maxSwipeDistance * 0.1f

        if (isLeftHandedMode) {
            holder.cardContent.translationX = abs(limitedDx)
            holder.swipeBackgroundLeft.visibility = if (swipeDistance > showButtonThreshold) View.VISIBLE else View.GONE
            holder.swipeBackgroundRight.visibility = View.GONE
        } else {
            holder.cardContent.translationX = limitedDx
            holder.swipeBackgroundRight.visibility = if (swipeDistance > showButtonThreshold) View.VISIBLE else View.GONE
            holder.swipeBackgroundLeft.visibility = View.GONE
        }
    }

    /**
     * 处理滑动结束：吸附或还原
     */
    fun handleSwipeEnd(holder: ViewHolder, dx: Float, velocityX: Float) {
        val maxSwipeDistance = 120f * holder.itemView.context.resources.displayMetrics.density
        val swipeThreshold = maxSwipeDistance * 0.35f
        val velocityThreshold = 400f
        val currentDx = holder.cardContent.translationX
        val currentDistance = abs(currentDx)

        val shouldSnap = currentDistance > swipeThreshold || abs(velocityX) > velocityThreshold
        if (shouldSnap && currentDistance > maxSwipeDistance * 0.1f) {
            val targetX = if (isLeftHandedMode) maxSwipeDistance else -maxSwipeDistance
            holder.cardContent.animate()
                .translationX(targetX)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    if (isLeftHandedMode) {
                        holder.swipeBackgroundLeft.visibility = View.VISIBLE
                        holder.swipeBackgroundRight.visibility = View.GONE
                    } else {
                        holder.swipeBackgroundRight.visibility = View.VISIBLE
                        holder.swipeBackgroundLeft.visibility = View.GONE
                    }
                }
                .start()
            if (isLeftHandedMode) {
                holder.swipeBackgroundLeft.visibility = View.VISIBLE
                holder.swipeBackgroundRight.visibility = View.GONE
            } else {
                holder.swipeBackgroundRight.visibility = View.VISIBLE
                holder.swipeBackgroundLeft.visibility = View.GONE
            }
        } else {
            resetSwipe(holder)
            currentSwipedHolder = null
        }
    }

    private fun resetSwipeImmediate(holder: ViewHolder) {
        holder.cardContent.translationX = 0f
        holder.swipeBackgroundLeft.visibility = View.GONE
        holder.swipeBackgroundRight.visibility = View.GONE
    }

    fun resetSwipe(holder: ViewHolder) {
        if (currentSwipedHolder == holder) currentSwipedHolder = null
        holder.swipeBackgroundLeft.visibility = View.GONE
        holder.swipeBackgroundRight.visibility = View.GONE
        holder.cardContent.animate()
            .translationX(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .withEndAction {
                holder.cardContent.translationX = 0f
                holder.swipeBackgroundLeft.visibility = View.GONE
                holder.swipeBackgroundRight.visibility = View.GONE
            }
            .start()
    }

    override fun getItemCount(): Int = items.size

    fun updateEntries(newEntries: List<HistoryEntry>) {
        entries = newEntries
        items = newEntries.toMutableList()
        notifyDataSetChanged()
    }

    fun filterEntries(query: String) {
        val filtered = if (query.isBlank()) items else items.filter {
            it.title.contains(query, true) || it.url.contains(query, true)
        }
        updateEntries(filtered)
    }

    private fun removeAt(position: Int) {
        if (position < 0 || position >= items.size) return
        items.removeAt(position)
        notifyItemRemoved(position)
    }
}

