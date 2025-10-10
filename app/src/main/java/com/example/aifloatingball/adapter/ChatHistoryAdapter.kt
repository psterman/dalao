package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.ChatHistoryQuestion
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天记录适配器
 */
class ChatHistoryAdapter(
    private val onQuestionClick: (ChatHistoryQuestion) -> Unit,
    private val onFavoriteClick: (ChatHistoryQuestion) -> Unit,
    private val onDeleteClick: (ChatHistoryQuestion) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder>() {

    private var questions: List<ChatHistoryQuestion> = emptyList()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val questionContent: TextView = itemView.findViewById(R.id.tv_question_content)
        val statusChip: Chip = itemView.findViewById(R.id.chip_category)
        val timeText: TextView = itemView.findViewById(R.id.tv_time)
        val jumpButton: ImageButton = itemView.findViewById(R.id.btn_jump_to_message)
        val favoriteButton: ImageButton = itemView.findViewById(R.id.btn_favorite)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete)
        val linkIcon: ImageView = itemView.findViewById(R.id.iv_link_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = questions[position]
        
        // 设置问题内容
        holder.questionContent.text = question.content
        
        // 设置状态标签
        setStatusChip(holder.statusChip, question)
        
        // 设置时间
        holder.timeText.text = dateFormat.format(Date(question.timestamp))
        
        // 设置链接图标
        holder.linkIcon.visibility = if (question.hasLink) View.VISIBLE else View.GONE
        
        // 设置收藏按钮
        holder.favoriteButton.setImageResource(
            if (question.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
        holder.favoriteButton.setOnClickListener {
            onFavoriteClick(question)
        }
        
        // 设置删除按钮
        holder.deleteButton.setImageResource(
            if (question.isDeleted) R.drawable.ic_restore else R.drawable.ic_delete
        )
        holder.deleteButton.setOnClickListener {
            onDeleteClick(question)
        }
        
        // 设置跳转按钮
        holder.jumpButton.setOnClickListener {
            onQuestionClick(question)
        }
        
        // 整个卡片点击事件
        holder.itemView.setOnClickListener {
            onQuestionClick(question)
        }
    }

    override fun getItemCount(): Int = questions.size

    /**
     * 更新数据
     */
    fun updateQuestions(newQuestions: List<ChatHistoryQuestion>) {
        questions = newQuestions.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }

    /**
     * 设置状态标签
     */
    private fun setStatusChip(chip: Chip, question: ChatHistoryQuestion) {
        when {
            question.isDeleted -> {
                chip.text = "已删除"
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F44336")
                )
                chip.chipStrokeWidth = 1f
            }
            question.isFavorite -> {
                chip.text = "已收藏"
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FF9800")
                )
                chip.chipStrokeWidth = 1f
            }
            question.hasLink -> {
                chip.text = "包含链接"
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#2196F3")
                )
                chip.chipStrokeWidth = 1f
            }
            else -> {
                chip.text = "普通问题"
                chip.setChipBackgroundColorResource(android.R.color.transparent)
                chip.chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#9E9E9E")
                )
                chip.chipStrokeWidth = 1f
            }
        }
    }
}
