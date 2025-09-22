package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.BookmarkEntry

/**
 * 收藏页面适配器
 */
class BookmarkEntryAdapter(
    private var entries: List<BookmarkEntry> = emptyList(),
    private val onItemClick: (BookmarkEntry) -> Unit = {},
    private val onMoreClick: (BookmarkEntry) -> Unit = {}
) : RecyclerView.Adapter<BookmarkEntryAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivBookmarkIcon: ImageView = itemView.findViewById(R.id.iv_bookmark_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_title)
        val tvUrl: TextView = itemView.findViewById(R.id.tv_url)
        val tvFolder: TextView = itemView.findViewById(R.id.tv_folder)
        val btnMore: ImageButton = itemView.findViewById(R.id.btn_more)
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
        
        // 设置收藏图标
        entry.favicon?.let {
            // TODO: 加载favicon
        } ?: run {
            holder.ivBookmarkIcon.setImageResource(R.drawable.ic_bookmark)
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
