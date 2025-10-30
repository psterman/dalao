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
import com.example.aifloatingball.model.PromptCommunityItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Prompt社区卡片适配器
 */
class PromptCommunityAdapter(
    private var prompts: List<PromptCommunityItem>,
    private val onItemClick: (PromptCommunityItem) -> Unit,
    private val onLikeClick: (PromptCommunityItem) -> Unit,
    private val onCollectClick: (PromptCommunityItem) -> Unit,
    private val onCommentClick: (PromptCommunityItem) -> Unit,
    private val onShareClick: (PromptCommunityItem) -> Unit
) : RecyclerView.Adapter<PromptCommunityAdapter.PromptViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_community_card, parent, false)
        return PromptViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        val prompt = prompts[position]
        holder.bind(prompt)
    }

    override fun getItemCount(): Int = prompts.size
    
    fun updateData(newPrompts: List<PromptCommunityItem>) {
        prompts = newPrompts
        notifyDataSetChanged()
    }
    
    /**
     * 获取当前的Prompt列表
     */
    fun getCurrentPrompts(): List<PromptCommunityItem> {
        return prompts
    }

    inner class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.prompt_title_text)
        private val authorText: TextView = itemView.findViewById(R.id.prompt_author_text)
        private val timeText: TextView = itemView.findViewById(R.id.prompt_time_text)
        private val categoryText: TextView = itemView.findViewById(R.id.prompt_category_text)
        private val previewText: TextView = itemView.findViewById(R.id.prompt_preview_text)
        private val tagsText: TextView = itemView.findViewById(R.id.prompt_tags_text)
        
        private val likeButton: LinearLayout = itemView.findViewById(R.id.like_button)
        private val likeIcon: ImageView = itemView.findViewById(R.id.like_icon)
        private val likeCountText: TextView = itemView.findViewById(R.id.like_count_text)
        
        private val collectButton: LinearLayout = itemView.findViewById(R.id.collect_button)
        private val collectIcon: ImageView = itemView.findViewById(R.id.collect_icon)
        private val collectCountText: TextView = itemView.findViewById(R.id.collect_count_text)
        
        private val commentButton: LinearLayout = itemView.findViewById(R.id.comment_button)
        private val commentCountText: TextView = itemView.findViewById(R.id.comment_count_text)
        
        private val shareButton: ImageButton = itemView.findViewById(R.id.share_button)

        fun bind(prompt: PromptCommunityItem) {
            titleText.text = prompt.title
            authorText.text = prompt.author
            timeText.text = formatTime(prompt.publishTime)
            categoryText.text = prompt.category.displayName
            
            // 显示预览内容
            previewText.text = prompt.description.ifEmpty { prompt.content }
            
            // 显示标签
            tagsText.text = prompt.tags.joinToString(" ") { "#$it" }
            
            // 隐藏社交相关（布局中已 GONE，这里避免不必要的状态设置）
            likeButton.visibility = View.GONE
            collectButton.visibility = View.GONE
            commentButton.visibility = View.GONE
            shareButton.visibility = View.GONE
            
            // 设置点击事件
            itemView.setOnClickListener {
                onItemClick(prompt)
            }
            
            // 社交操作暂不启用
        }
        
        private fun updateLikeState(prompt: PromptCommunityItem) {
            if (prompt.isLiked) {
                likeIcon.setImageResource(R.drawable.ic_favorite_filled)
                likeIcon.setColorFilter(itemView.context.getColor(R.color.primary_red))
            } else {
                likeIcon.setImageResource(R.drawable.ic_like_outline)
                likeIcon.setColorFilter(itemView.context.getColor(R.color.ai_assistant_text_secondary))
            }
        }
        
        private fun updateCollectState(prompt: PromptCommunityItem) {
            if (prompt.isCollected) {
                collectIcon.setImageResource(R.drawable.ic_star_rate)
                collectIcon.setColorFilter(itemView.context.getColor(R.color.primary_yellow))
            } else {
                collectIcon.setImageResource(R.drawable.ic_star_outline)
                collectIcon.setColorFilter(itemView.context.getColor(R.color.ai_assistant_text_secondary))
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            return when {
                diff < 60_000 -> "刚刚"
                diff < 3600_000 -> "${diff / 60_000}分钟前"
                diff < 86400_000 -> "${diff / 3600_000}小时前"
                diff < 604800_000 -> "${diff / 86400_000}天前"
                else -> {
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    format.format(Date(timestamp))
                }
            }
        }
    }
}

