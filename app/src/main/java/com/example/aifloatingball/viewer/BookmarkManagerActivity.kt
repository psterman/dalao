package com.example.aifloatingball.viewer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.google.android.material.appbar.MaterialToolbar
import java.text.SimpleDateFormat
import java.util.*

/**
 * 书签管理Activity
 */
class BookmarkManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BookmarkManagerActivity"
        const val EXTRA_FILE_PATH = "file_path"
        const val RESULT_BOOKMARK_SELECTED = 100
        const val RESULT_BOOKMARK_PAGE = "bookmark_page"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: BookmarkAdapter
    private lateinit var dataManager: ReaderDataManager
    private var filePath: String = ""
    private var bookmarks: List<Bookmark> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmark_manager)
        
        filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        dataManager = ReaderDataManager(this)
        
        initViews()
        loadBookmarks()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_delete_all -> {
                    showDeleteAllDialog()
                    true
                }
                else -> false
            }
        }
        
        adapter = BookmarkAdapter(
            bookmarks = bookmarks,
            onItemClick = { bookmark ->
                // 返回选中的书签页码
                val resultIntent = Intent().apply {
                    putExtra(RESULT_BOOKMARK_PAGE, bookmark.pageIndex)
                }
                setResult(RESULT_BOOKMARK_SELECTED, resultIntent)
                finish()
            },
            onItemLongClick = { bookmark ->
                showBookmarkOptionsDialog(bookmark)
                true
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun loadBookmarks() {
        bookmarks = dataManager.getBookmarks(filePath).sortedByDescending { it.timestamp }
        adapter.updateBookmarks(bookmarks)
        toolbar.title = "书签管理 (${bookmarks.size})"
        
        if (bookmarks.isEmpty()) {
            findViewById<TextView>(R.id.emptyText)?.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            findViewById<TextView>(R.id.emptyText)?.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showBookmarkOptionsDialog(bookmark: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle("书签操作")
            .setItems(arrayOf("跳转", "编辑", "删除")) { _, which ->
                when (which) {
                    0 -> {
                        // 跳转
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_BOOKMARK_PAGE, bookmark.pageIndex)
                        }
                        setResult(RESULT_BOOKMARK_SELECTED, resultIntent)
                        finish()
                    }
                    1 -> {
                        // 编辑
                        showEditBookmarkDialog(bookmark)
                    }
                    2 -> {
                        // 删除
                        showDeleteBookmarkDialog(bookmark)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditBookmarkDialog(bookmark: Bookmark) {
        val input = android.widget.EditText(this)
        input.setText(bookmark.text)
        input.hint = "书签备注"
        
        AlertDialog.Builder(this)
            .setTitle("编辑书签")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val updatedBookmark = bookmark.copy(text = input.text.toString())
                // 更新书签（先删除旧的，再添加新的）
                dataManager.deleteBookmark(bookmark.id)
                dataManager.addBookmark(updatedBookmark)
                loadBookmarks()
                Toast.makeText(this, "书签已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteBookmarkDialog(bookmark: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle("删除书签")
            .setMessage("确定要删除这个书签吗？\n\n${bookmark.text.take(50)}...")
            .setPositiveButton("删除") { _, _ ->
                dataManager.deleteBookmark(bookmark.id)
                loadBookmarks()
                Toast.makeText(this, "已删除书签", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteAllDialog() {
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "没有可删除的书签", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("删除所有书签")
            .setMessage("确定要删除所有 ${bookmarks.size} 个书签吗？")
            .setPositiveButton("删除") { _, _ ->
                bookmarks.forEach { bookmark ->
                    dataManager.deleteBookmark(bookmark.id)
                }
                loadBookmarks()
                Toast.makeText(this, "已删除所有书签", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 书签列表适配器
     */
    class BookmarkAdapter(
        private var bookmarks: List<Bookmark>,
        private val onItemClick: (Bookmark) -> Unit,
        private val onItemLongClick: (Bookmark) -> Unit
    ) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.bookmarkText)
            val pageInfo: TextView = itemView.findViewById(R.id.bookmarkPageInfo)
            val timeInfo: TextView = itemView.findViewById(R.id.bookmarkTimeInfo)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reader_bookmark, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bookmark = bookmarks[position]
            
            holder.textView.text = bookmark.text
            holder.pageInfo.text = "第 ${bookmark.pageIndex + 1} 页"
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            holder.timeInfo.text = dateFormat.format(Date(bookmark.timestamp))
            
            holder.itemView.setOnClickListener {
                onItemClick(bookmark)
            }
            
            holder.itemView.setOnLongClickListener {
                onItemLongClick(bookmark)
                true
            }
            
            holder.deleteButton.setOnClickListener {
                onItemLongClick(bookmark)
            }
        }
        
        override fun getItemCount(): Int = bookmarks.size
        
        fun updateBookmarks(newBookmarks: List<Bookmark>) {
            bookmarks = newBookmarks
            notifyDataSetChanged()
        }
    }
}

