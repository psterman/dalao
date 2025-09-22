package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.HistoryEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史记录适配器
 */
class HistoryEntryAdapter(
    private var entries: List<HistoryEntry> = emptyList(),
    private val onItemClick: (HistoryEntry) -> Unit = {},
    private val onMoreClick: (HistoryEntry) -> Unit = {}
) : RecyclerView.Adapter<HistoryEntryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivSiteIcon: ImageView = itemView.findViewById(R.id.iv_site_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvUrl: TextView = itemView.findViewById(R.id.tv_url)
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        
        holder.tvTitle.text = entry.title
        holder.tvUrl.text = entry.url
        holder.tvTime.text = entry.getFormattedTime()
        
        // 设置网站图标（这里可以加载favicon）
        entry.favicon?.let {
            // TODO: 加载favicon
        } ?: run {
            holder.ivSiteIcon.setImageResource(R.drawable.ic_web)
        }
        
        // 点击事件
        holder.itemView.setOnClickListener {
            onItemClick(entry)
        }
        
        holder.btnMore.setOnClickListener {
            onMoreClick(entry)
        }
    }

    override fun getItemCount(): Int = entries.size

    fun updateEntries(newEntries: List<HistoryEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    fun filterEntries(query: String) {
        val filtered = if (query.isBlank()) {
            entries
        } else {
            entries.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.url.contains(query, ignoreCase = true)
            }
        }
        updateEntries(filtered)
    }
}
