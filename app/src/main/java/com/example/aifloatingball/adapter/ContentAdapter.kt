package com.example.aifloatingball.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.aifloatingball.R
import com.example.aifloatingball.model.Content
import com.example.aifloatingball.model.ContentType

/**
 * 通用内容适配器
 */
class ContentAdapter(
    private val context: Context,
    private var contents: List<Content> = emptyList()
) : RecyclerView.Adapter<ContentAdapter.ContentViewHolder>() {
    
    companion object {
        private const val TAG = "ContentAdapter"
    }
    
    interface OnContentClickListener {
        fun onContentClick(content: Content)
        fun onCreatorClick(content: Content)
        fun onMoreClick(content: Content, view: View)
    }
    
    private var onContentClickListener: OnContentClickListener? = null
    
    fun setOnContentClickListener(listener: OnContentClickListener) {
        this.onContentClickListener = listener
    }
    
    fun setOnContentClickListener(listener: (Content) -> Unit) {
        this.onContentClickListener = object : OnContentClickListener {
            override fun onContentClick(content: Content) = listener(content)
            override fun onCreatorClick(content: Content) = listener(content)
            override fun onMoreClick(content: Content, view: View) = listener(content)
        }
    }
    
    fun updateContents(newContents: List<Content>) {
        this.contents = newContents
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_content, parent, false)
        return ContentViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        holder.bind(contents[position])
    }
    
    override fun getItemCount(): Int = contents.size
    
    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCreatorAvatar: ImageView = itemView.findViewById(R.id.iv_creator_avatar)
        private val tvCreatorName: TextView = itemView.findViewById(R.id.tv_creator_name)
        private val tvPublishTime: TextView = itemView.findViewById(R.id.tv_publish_time)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        private val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
        private val ivCover: ImageView = itemView.findViewById(R.id.iv_cover)
        private val layoutTypeTag: LinearLayout = itemView.findViewById(R.id.layout_type_tag)
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.iv_type_icon)
        private val tvTypeText: TextView = itemView.findViewById(R.id.tv_type_text)
        private val tvStats: TextView = itemView.findViewById(R.id.tv_stats)
        private val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
        private val platformIndicator: View = itemView.findViewById(R.id.platform_indicator)
        
        fun bind(content: Content) {
            // 设置创作者头像
            Glide.with(context)
                .load(content.creatorAvatar)
                .transform(CircleCrop())
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(ivCreatorAvatar)
            
            // 设置创作者名称
            tvCreatorName.text = content.creatorName
            
            // 设置发布时间
            val timeText = DateUtils.getRelativeTimeSpanString(
                content.publishTime,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
            tvPublishTime.text = timeText
            
            // 设置标题和描述
            tvTitle.text = content.title
            tvDescription.text = content.description
            tvDescription.visibility = if (content.description.isNotEmpty()) View.VISIBLE else View.GONE
            
            // 设置封面图片
            if (content.coverUrl.isNotEmpty()) {
                ivCover.visibility = View.VISIBLE
                Glide.with(context)
                    .load(content.coverUrl)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .into(ivCover)
            } else {
                ivCover.visibility = View.GONE
            }
            
            // 设置内容类型标签
            setupTypeTag(content)
            
            // 设置统计信息
            setupStats(content)
            
            // 设置平台指示器颜色
            try {
                val color = Color.parseColor(content.platform.primaryColor)
                platformIndicator.setBackgroundColor(color)
            } catch (e: Exception) {
                platformIndicator.setBackgroundColor(Color.GRAY)
            }
            
            // 设置点击事件
            itemView.setOnClickListener {
                if (content.contentUrl.isNotEmpty()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(content.contentUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        onContentClickListener?.onContentClick(content)
                    }
                } else {
                    onContentClickListener?.onContentClick(content)
                }
            }
            
            // 创作者头像点击事件
            ivCreatorAvatar.setOnClickListener {
                onContentClickListener?.onCreatorClick(content)
            }
            
            // 更多按钮点击事件
            btnMore.setOnClickListener {
                onContentClickListener?.onMoreClick(content, it)
            }
        }
        
        private fun setupTypeTag(content: Content) {
            layoutTypeTag.visibility = View.VISIBLE
            ivTypeIcon.setImageResource(content.contentType.iconRes)
            tvTypeText.text = content.contentType.displayName
            
            // 根据内容类型设置不同的颜色
            val color = when (content.contentType) {
                ContentType.VIDEO -> Color.parseColor("#FF4081")
                ContentType.AUDIO -> Color.parseColor("#9C27B0")
                ContentType.IMAGE -> Color.parseColor("#FF9800")
                ContentType.LIVE -> Color.parseColor("#F44336")
                ContentType.ARTICLE -> Color.parseColor("#2196F3")
                else -> Color.parseColor("#607D8B")
            }
            
            ivTypeIcon.setColorFilter(color)
            tvTypeText.setTextColor(color)
        }
        
        private fun setupStats(content: Content) {
            val stats = mutableListOf<String>()
            
            // 播放/浏览量
            if (content.viewCount > 0) {
                stats.add(formatCount(content.viewCount) + when (content.contentType) {
                    ContentType.VIDEO, ContentType.LIVE -> "播放"
                    ContentType.AUDIO -> "播放"
                    else -> "浏览"
                })
            }
            
            // 点赞数
            if (content.likeCount > 0) {
                stats.add(formatCount(content.likeCount) + "赞")
            }
            
            // 评论数
            if (content.commentCount > 0) {
                stats.add(formatCount(content.commentCount) + "评论")
            }
            
            // 时长（视频/音频）
            if (content.duration > 0 && (content.contentType == ContentType.VIDEO || content.contentType == ContentType.AUDIO)) {
                stats.add(formatDuration(content.duration))
            }
            
            tvStats.text = stats.joinToString(" · ")
            tvStats.visibility = if (stats.isNotEmpty()) View.VISIBLE else View.GONE
        }
        
        private fun formatCount(count: Long): String {
            return when {
                count >= 100000000 -> String.format("%.1f亿", count / 100000000.0)
                count >= 10000 -> String.format("%.1f万", count / 10000.0)
                else -> count.toString()
            }
        }
        
        private fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            
            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
                else -> String.format("%d:%02d", minutes, secs)
            }
        }
    }
}
