package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.BookmarkEntry
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs

/**
 * 收藏页面适配器
 */
class BookmarkEntryAdapter(
    private var entries: List<BookmarkEntry> = emptyList(),
    private val onItemClick: (BookmarkEntry) -> Unit = {},
    private val onMoreClick: (BookmarkEntry) -> Unit = {},
    private val onSwipeEdit: (BookmarkEntry) -> Unit = {},
    private val onSwipeDelete: (BookmarkEntry) -> Unit = {},
    private val isLeftHandedMode: Boolean = false
) : RecyclerView.Adapter<BookmarkEntryAdapter.ViewHolder>() {
    
    // 跟踪当前滑动的ViewHolder，确保每次只滑动一个
    private var currentSwipedHolder: ViewHolder? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardContent: MaterialCardView = itemView.findViewById(R.id.card_content)
        val swipeBackgroundRight: LinearLayout = itemView.findViewById(R.id.swipe_background_right)
        val swipeBackgroundLeft: LinearLayout = itemView.findViewById(R.id.swipe_background_left)
        val ivBookmarkIcon: ImageView = itemView.findViewById(R.id.iv_bookmark_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvUrl: TextView = itemView.findViewById(R.id.tv_url)
        val tvFolder: TextView = itemView.findViewById(R.id.tv_folder)
        val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
        val btnSwipeEdit: ImageButton = itemView.findViewById(R.id.btn_swipe_edit)
        val btnSwipeDelete: ImageButton = itemView.findViewById(R.id.btn_swipe_delete)
        val btnSwipeEditLeft: ImageButton = itemView.findViewById(R.id.btn_swipe_edit_left)
        val btnSwipeDeleteLeft: ImageButton = itemView.findViewById(R.id.btn_swipe_delete_left)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bookmark_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        
        holder.tvTitle.text = entry.title
        holder.tvUrl.text = entry.url
        holder.tvFolder.text = entry.folder
        
        // 设置收藏图标（加载favicon）
        holder.ivBookmarkIcon.setImageResource(R.drawable.ic_bookmark) // 先设置默认图标
        try {
            // 使用FaviconLoader加载网站图标
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(
                holder.ivBookmarkIcon,
                entry.url,
                R.drawable.ic_bookmark
            )
        } catch (e: Exception) {
            android.util.Log.e("BookmarkEntryAdapter", "加载网站图标失败: ${entry.url}", e)
            holder.ivBookmarkIcon.setImageResource(R.drawable.ic_bookmark)
        }
        
        // 重置滑动位置
        holder.cardContent.translationX = 0f
        holder.swipeBackgroundRight.visibility = View.GONE
        holder.swipeBackgroundLeft.visibility = View.GONE
        
        // 确保按钮区域不可点击，让子按钮接收点击事件
        holder.swipeBackgroundRight.isClickable = false
        holder.swipeBackgroundRight.isFocusable = false
        holder.swipeBackgroundLeft.isClickable = false
        holder.swipeBackgroundLeft.isFocusable = false
        
        // 点击事件
        holder.cardContent.setOnClickListener {
            // 如果卡片已经滑动，不响应点击
            if (abs(holder.cardContent.translationX) < 10f) {
                onItemClick(entry)
            }
        }
        
        holder.btnMore.setOnClickListener {
            onMoreClick(entry)
        }
        
        // 右侧滑动按钮点击事件（普通模式左滑显示）
        holder.btnSwipeEdit.setOnClickListener { view ->
            android.util.Log.d("BookmarkEntryAdapter", "点击编辑按钮: ${entry.title}")
            onSwipeEdit(entry)
            resetSwipe(holder)
        }
        
        holder.btnSwipeDelete.setOnClickListener { view ->
            android.util.Log.d("BookmarkEntryAdapter", "点击删除按钮: ${entry.title}, URL: ${entry.url}")
            try {
                onSwipeDelete(entry)
            } catch (e: Exception) {
                android.util.Log.e("BookmarkEntryAdapter", "删除按钮点击异常", e)
            }
            resetSwipe(holder)
        }
        
        // 左侧滑动按钮点击事件（左撇子模式右滑显示）
        holder.btnSwipeEditLeft.setOnClickListener { view ->
            android.util.Log.d("BookmarkEntryAdapter", "点击编辑按钮(左): ${entry.title}")
            onSwipeEdit(entry)
            resetSwipe(holder)
        }
        
        holder.btnSwipeDeleteLeft.setOnClickListener { view ->
            android.util.Log.d("BookmarkEntryAdapter", "点击删除按钮(左): ${entry.title}, URL: ${entry.url}")
            try {
                onSwipeDelete(entry)
            } catch (e: Exception) {
                android.util.Log.e("BookmarkEntryAdapter", "删除按钮点击异常", e)
            }
            resetSwipe(holder)
        }
        
        // 确保按钮可以接收点击事件，不被父视图拦截
        holder.btnSwipeEdit.isClickable = true
        holder.btnSwipeEdit.isFocusable = true
        holder.btnSwipeEdit.isEnabled = true
        holder.btnSwipeDelete.isClickable = true
        holder.btnSwipeDelete.isFocusable = true
        holder.btnSwipeDelete.isEnabled = true
        holder.btnSwipeEditLeft.isClickable = true
        holder.btnSwipeEditLeft.isFocusable = true
        holder.btnSwipeEditLeft.isEnabled = true
        holder.btnSwipeDeleteLeft.isClickable = true
        holder.btnSwipeDeleteLeft.isFocusable = true
        holder.btnSwipeDeleteLeft.isEnabled = true
        
        // 确保父容器不拦截子按钮的点击事件
        holder.swipeBackgroundRight.setOnClickListener(null)
        holder.swipeBackgroundLeft.setOnClickListener(null)
        
        // 确保父容器不会拦截触摸事件
        holder.swipeBackgroundRight.setOnTouchListener { _, _ -> false } // 返回false，让事件传递给子视图
        holder.swipeBackgroundLeft.setOnTouchListener { _, _ -> false } // 返回false，让事件传递给子视图
    }
    
    /**
     * 处理滑动操作（只支持左滑，左撇子模式下右滑）
     * ItemTouchHelper中：左滑时dx < 0，右滑时dx > 0
     * 卡片移动逻辑：左滑时卡片向右移动（translationX为正），显示左侧按钮
     */
    fun handleSwipe(holder: ViewHolder, dx: Float, isActive: Boolean = false) {
        // 如果有其他正在滑动的记录，先还原它
        if (isActive && currentSwipedHolder != null && currentSwipedHolder != holder) {
            currentSwipedHolder?.let { oldHolder ->
                resetSwipeImmediate(oldHolder)
            }
        }
        
        // 如果开始新的滑动，设置为当前滑动的记录
        if (isActive && abs(dx) > 10f) {
            currentSwipedHolder = holder
        }
        
        val cardWidth = holder.cardContent.width.toFloat()
        if (cardWidth == 0f) return
        
        // 最大滑动距离（按钮区域宽度，约120dp）
        val maxSwipeDistance = 120f * holder.itemView.context.resources.displayMetrics.density
        
        // 判断是否为有效的滑动方向（普通模式只允许左滑，左撇子模式只允许右滑）
        val isValidSwipe = if (isLeftHandedMode) {
            dx > 0 // 左撇子模式：只允许右滑
        } else {
            dx < 0 // 普通模式：只允许左滑
        }
        
        if (!isValidSwipe && abs(dx) > 10f) {
            // 无效方向，不处理
            return
        }
        
        // 限制滑动距离，防止滑出屏幕
        val limitedDx = if (isLeftHandedMode) {
            dx.coerceIn(0f, maxSwipeDistance) // 左撇子模式：只允许向右滑动（正数）
        } else {
            dx.coerceIn(-maxSwipeDistance, 0f) // 普通模式：只允许向左滑动（负数）
        }
        
        // 移动卡片：左滑时卡片向左移动显示右侧按钮，右滑时卡片向右移动显示左侧按钮
        // ItemTouchHelper中：左滑时dx < 0（负数），右滑时dx > 0（正数）
        // translationX方向与dx方向一致：左滑时translationX为负数（卡片向左），右滑时translationX为正数（卡片向右）
        val swipeDistance = abs(limitedDx)
        val showButtonThreshold = maxSwipeDistance * 0.1f // 10%的滑动距离即可显示按钮
        
        // 使用动画属性设置，避免直接修改translationX导致重绘卡顿
        if (isLeftHandedMode) {
            // 左撇子模式：右滑（dx > 0），卡片向右移动（translationX为正数），显示左侧按钮
            holder.cardContent.translationX = abs(limitedDx)
            val shouldShowButtons = swipeDistance > showButtonThreshold
            if (holder.swipeBackgroundLeft.visibility != if (shouldShowButtons) View.VISIBLE else View.GONE) {
                holder.swipeBackgroundLeft.visibility = if (shouldShowButtons) View.VISIBLE else View.GONE
            }
            holder.swipeBackgroundRight.visibility = View.GONE
        } else {
            // 普通模式：左滑（dx < 0），卡片向左移动（translationX为负数），显示右侧按钮
            holder.cardContent.translationX = limitedDx  // dx已经是负数，直接使用
            val shouldShowButtons = swipeDistance > showButtonThreshold
            if (holder.swipeBackgroundRight.visibility != if (shouldShowButtons) View.VISIBLE else View.GONE) {
                holder.swipeBackgroundRight.visibility = if (shouldShowButtons) View.VISIBLE else View.GONE
            }
            holder.swipeBackgroundLeft.visibility = View.GONE
        }
    }
    
    /**
     * 处理滑动结束，自动到位或还原
     */
    fun handleSwipeEnd(holder: ViewHolder, dx: Float, velocityX: Float) {
        val maxSwipeDistance = 120f * holder.itemView.context.resources.displayMetrics.density
        val swipeThreshold = maxSwipeDistance * 0.35f // 35%作为阈值，稍微降低以提高成功率
        val velocityThreshold = 400f // 降低速度阈值
        
        val currentDx = holder.cardContent.translationX
        val currentDistance = abs(currentDx)
        
        // 判断是否需要自动到位（提高精准度）
        val shouldSnapToPosition = when {
            currentDistance > swipeThreshold -> true // 超过阈值，自动到位
            abs(velocityX) > velocityThreshold -> true // 快速滑动，自动到位
            else -> false // 否则还原
        }
        
        if (shouldSnapToPosition && currentDistance > maxSwipeDistance * 0.1f) {
            // 自动滑动到位
            val targetX = if (isLeftHandedMode) {
                maxSwipeDistance // 左撇子模式：卡片向右移动到底，显示左侧按钮
            } else {
                -maxSwipeDistance // 普通模式：卡片向左移动到底，显示右侧按钮
            }
            
            holder.cardContent.animate()
                .translationX(targetX)
                .setDuration(200) // 缩短动画时间，减少卡顿
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)) // 使用更快的减速插值器
                .withEndAction {
                    // 确保按钮在动画结束后正确显示
                    if (isLeftHandedMode) {
                        holder.swipeBackgroundLeft.visibility = View.VISIBLE
                        holder.swipeBackgroundRight.visibility = View.GONE
                    } else {
                        holder.swipeBackgroundRight.visibility = View.VISIBLE
                        holder.swipeBackgroundLeft.visibility = View.GONE
                    }
                }
                .start()
            
            // 立即显示对应的按钮（不等待动画）
            if (isLeftHandedMode) {
                holder.swipeBackgroundLeft.visibility = View.VISIBLE
                holder.swipeBackgroundRight.visibility = View.GONE
            } else {
                holder.swipeBackgroundRight.visibility = View.VISIBLE
                holder.swipeBackgroundLeft.visibility = View.GONE
            }
        } else {
            // 还原位置（确保完全还原）
            resetSwipe(holder)
            currentSwipedHolder = null
        }
    }
    
    /**
     * 立即还原滑动位置（无动画）
     */
    private fun resetSwipeImmediate(holder: ViewHolder) {
        holder.cardContent.translationX = 0f
        holder.swipeBackgroundLeft.visibility = View.GONE
        holder.swipeBackgroundRight.visibility = View.GONE
    }
    
    /**
     * 恢复滑动位置（优化回弹效果）
     */
    fun resetSwipe(holder: ViewHolder) {
        if (currentSwipedHolder == holder) {
            currentSwipedHolder = null
        }
        
        // 立即隐藏按钮
        holder.swipeBackgroundLeft.visibility = View.GONE
        holder.swipeBackgroundRight.visibility = View.GONE
        
        // 平滑回弹到原位置
        holder.cardContent.animate()
            .translationX(0f)
            .setDuration(200) // 缩短动画时间，减少卡顿
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)) // 使用更快的减速插值器
            .withEndAction {
                // 确保最终位置为0，按钮已隐藏
                holder.cardContent.translationX = 0f
                holder.swipeBackgroundLeft.visibility = View.GONE
                holder.swipeBackgroundRight.visibility = View.GONE
            }
            .start()
    }

    override fun getItemCount(): Int = entries.size

    fun updateEntries(newEntries: List<BookmarkEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    fun filterEntries(query: String) {
        val filtered = if (query.isBlank()) {
            entries
        } else {
            entries.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.url.contains(query, ignoreCase = true) ||
                it.folder.contains(query, ignoreCase = true)
            }
        }
        updateEntries(filtered)
    }
}
