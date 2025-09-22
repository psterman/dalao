package com.example.aifloatingball.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.BookmarkEntryAdapter
import com.example.aifloatingball.model.BookmarkEntry
import com.google.android.material.textfield.TextInputEditText
import java.util.*

/**
 * 收藏页面Fragment
 */
class BookmarksPageFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyLayout: LinearLayout
    private lateinit var searchEditText: TextInputEditText
    private lateinit var adapter: BookmarkEntryAdapter
    
    private var onBookmarkItemClick: ((BookmarkEntry) -> Unit)? = null
    private var onBookmarkMoreClick: ((BookmarkEntry) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bookmarks_page, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupSearch()
        loadBookmarksData()
    }
    
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_bookmarks)
        emptyLayout = view.findViewById(R.id.layout_empty_bookmarks)
        searchEditText = view.findViewById(R.id.et_search_bookmarks)
    }
    
    private fun setupRecyclerView() {
        adapter = BookmarkEntryAdapter(
            onItemClick = { entry ->
                onBookmarkItemClick?.invoke(entry)
            },
            onMoreClick = { entry ->
                onBookmarkMoreClick?.invoke(entry)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }
    
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filterEntries(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun loadBookmarksData() {
        // 模拟收藏数据
        val mockBookmarks = listOf(
            BookmarkEntry(
                id = "1",
                title = "Android 开发最佳实践",
                url = "https://developer.android.com/guide",
                folder = "开发资源",
                createTime = Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000) // 2天前
            ),
            BookmarkEntry(
                id = "2",
                title = "Kotlin 官方文档",
                url = "https://kotlinlang.org/docs",
                folder = "开发资源",
                createTime = Date(System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000) // 5天前
            ),
            BookmarkEntry(
                id = "3",
                title = "Material Design 组件库",
                url = "https://material.io/components",
                folder = "UI设计",
                createTime = Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000) // 7天前
            ),
            BookmarkEntry(
                id = "4",
                title = "GitHub 趋势项目",
                url = "https://github.com/trending",
                folder = "技术资讯",
                createTime = Date(System.currentTimeMillis() - 10 * 24 * 60 * 60 * 1000) // 10天前
            )
        )
        
        adapter.updateEntries(mockBookmarks)
        updateEmptyState()
    }
    
    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }
    }
    
    fun setOnBookmarkItemClick(listener: (BookmarkEntry) -> Unit) {
        onBookmarkItemClick = listener
    }
    
    fun setOnBookmarkMoreClick(listener: (BookmarkEntry) -> Unit) {
        onBookmarkMoreClick = listener
    }
}
