package com.example.aifloatingball.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.BookmarkManager
import com.example.aifloatingball.model.Bookmark
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 书签适配器
 */
class BookmarkAdapter(
    private val context: Context,
    private var bookmarks: List<Bookmark>,
    private val onItemClick: (Bookmark) -> Unit,
    private val onItemLongClick: (Bookmark, View) -> Boolean
) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

    private val bookmarkManager = BookmarkManager.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val faviconView: ImageView = view.findViewById(R.id.bookmark_favicon)
        val titleView: TextView = view.findViewById(R.id.bookmark_title)
        val urlView: TextView = view.findViewById(R.id.bookmark_url)
        val dateView: TextView = view.findViewById(R.id.bookmark_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bookmark, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = bookmarks.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bookmark = bookmarks[position]
        
        // 设置标题和 URL
        holder.titleView.text = bookmark.title
        holder.urlView.text = bookmark.url
        
        // 设置添加日期
        val date = Date(bookmark.addTime)
        holder.dateView.text = dateFormat.format(date)
        
        // 设置 favicon
        val faviconFile = bookmarkManager.getFaviconFile(bookmark.id)
        if (faviconFile != null && faviconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(faviconFile.path)
                holder.faviconView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.faviconView.setImageResource(R.drawable.ic_bookmark)
            }
        } else {
            holder.faviconView.setImageResource(R.drawable.ic_bookmark)
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(bookmark)
        }
        
        // 设置长按事件
        holder.itemView.setOnLongClickListener { view ->
            onItemLongClick(bookmark, view)
        }
    }

    /**
     * 更新书签列表数据
     */
    fun updateBookmarks(newBookmarks: List<Bookmark>) {
        this.bookmarks = newBookmarks
        notifyDataSetChanged()
    }
} 