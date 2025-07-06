package com.example.aifloatingball.adapter

import android.content.Context
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.service.DynamicIslandService
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class NotificationAdapter(
    private val notifications: List<DynamicIslandService.NotificationInfo>,
    private val onTextSelected: (DynamicIslandService.NotificationInfo, String) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.notification_icon)
        val title: TextView = itemView.findViewById(R.id.notification_title)
        val text: TextView = itemView.findViewById(R.id.notification_text)
        val time: TextView = itemView.findViewById(R.id.notification_time)
        val keywordsContainer: LinearLayout = itemView.findViewById(R.id.keywords_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = notifications[position]
        val context = holder.itemView.context

        // 设置基本信息
        holder.title.text = notification.title
        holder.text.text = notification.text
        holder.time.text = DateUtils.getRelativeTimeSpanString(
            notification.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )

        // 设置图标
        if (notification.icon != null) {
            holder.icon.setImageBitmap(notification.icon)
        } else {
            // 设置默认图标
            holder.icon.setImageResource(R.drawable.ic_notification)
        }

        // 提取并显示关键词
        val keywords = extractSearchableKeywords(notification)
        if (keywords.isNotEmpty()) {
            holder.keywordsContainer.visibility = View.VISIBLE
            holder.keywordsContainer.removeAllViews()
            
            keywords.take(3).forEach { keyword -> // 最多显示3个关键词
                val chip = createKeywordChip(context, keyword) { selectedKeyword ->
                    onTextSelected(notification, selectedKeyword)
                }
                holder.keywordsContainer.addView(chip)
            }
        } else {
            holder.keywordsContainer.visibility = View.GONE
        }

        // 设置点击事件
        holder.itemView.setOnClickListener {
            // 点击整个通知项时，使用标题作为搜索关键词
            val searchText = if (notification.title.isNotBlank()) {
                notification.title
            } else {
                notification.text
            }
            onTextSelected(notification, searchText)
        }

        // 设置长按事件 - 显示完整内容
        holder.itemView.setOnLongClickListener {
            showFullNotificationContent(context, notification)
            true
        }
    }

    override fun getItemCount(): Int = notifications.size

    private fun extractSearchableKeywords(notification: DynamicIslandService.NotificationInfo): List<String> {
        val keywords = mutableListOf<String>()
        
        // 从标题中提取关键词
        extractKeywordsFromText(notification.title, keywords)
        
        // 从内容中提取关键词
        extractKeywordsFromText(notification.text, keywords)
        
        return keywords.distinct().filter { it.length > 1 }.take(5)
    }

    private fun extractKeywordsFromText(text: String, keywords: MutableList<String>) {
        if (text.isBlank()) return
        
        // 按常见分隔符分割
        val separators = arrayOf(" ", "：", ":", "，", ",", "。", ".", "！", "!", "？", "?", "\n")
        var currentText = text
        
        separators.forEach { separator ->
            currentText = currentText.replace(separator, "|")
        }
        
        currentText.split("|").forEach { word ->
            val cleanWord = word.trim()
            if (cleanWord.length > 1 && !isCommonWord(cleanWord)) {
                keywords.add(cleanWord)
            }
        }
    }

    private fun isCommonWord(word: String): Boolean {
        val commonWords = setOf(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "什么", "时候", "可以", "现在", "知道", "这个", "那个", "怎么", "为什么"
        )
        return word in commonWords
    }

    private fun createKeywordChip(context: Context, keyword: String, onChipClick: (String) -> Unit): Chip {
        return Chip(context).apply {
            text = keyword
            isClickable = true
            isCheckable = false
            setChipBackgroundColorResource(R.color.chip_background_color)
            setTextColor(Color.WHITE)
            textSize = 10f
            
            // 设置内边距
            val padding = (4 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            
            setOnClickListener {
                onChipClick(keyword)
            }
        }
    }

    private fun showFullNotificationContent(context: Context, notification: DynamicIslandService.NotificationInfo) {
        // 这里可以显示一个对话框显示完整的通知内容
        // 暂时使用Toast显示
        android.widget.Toast.makeText(
            context,
            "完整内容:\n标题: ${notification.title}\n内容: ${notification.text}",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
} 