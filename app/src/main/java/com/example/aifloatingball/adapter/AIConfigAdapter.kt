package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AIConfigItem
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * AI配置适配器
 */
class AIConfigAdapter(
    private val items: MutableList<AIConfigItem>,
    private val onConfigClick: (AIConfigItem) -> Unit,
    private val onTestClick: (AIConfigItem) -> Unit
) : RecyclerView.Adapter<AIConfigAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val iconImageView: ImageView = view.findViewById(R.id.image_icon)
        val nameTextView: TextView = view.findViewById(R.id.text_name)
        val descriptionTextView: TextView = view.findViewById(R.id.text_description)
        val statusTextView: TextView = view.findViewById(R.id.text_status)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_config, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.nameTextView.text = item.displayName
        holder.descriptionTextView.text = item.description
        
        // 设置状态
        if (item.isConfigured) {
            holder.statusTextView.text = "✓ 已配置API密钥"
            holder.statusTextView.setTextColor(
                holder.itemView.context.getColor(android.R.color.holo_green_dark)
            )
        } else {
            holder.statusTextView.text = "⚠ 未配置API密钥"
            holder.statusTextView.setTextColor(
                holder.itemView.context.getColor(android.R.color.holo_red_dark)
            )
        }
        
        // 设置图标
        val iconRes = when (item.name.lowercase()) {
            "deepseek" -> R.drawable.ic_deepseek
            "chatgpt" -> R.drawable.ic_chatgpt
            "claude" -> R.drawable.ic_claude
            "智谱ai" -> R.drawable.ic_zhipu
            "通义千问" -> R.drawable.ic_qianwen
            "文心一言" -> R.drawable.ic_wenxin
            "gemini" -> R.drawable.ic_gemini
            "kimi" -> R.drawable.ic_kimi
            else -> R.drawable.ic_web_default
        }
        holder.iconImageView.setImageResource(iconRes)
        
        // 设置点击事件
        holder.cardView.setOnClickListener {
            onConfigClick(item)
        }
        
        // 长按显示测试选项
        holder.cardView.setOnLongClickListener {
            if (item.isConfigured) {
                onTestClick(item)
            }
            true
        }
    }
    
    override fun getItemCount(): Int = items.size
    
    fun updateItems(newItems: List<AIConfigItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}