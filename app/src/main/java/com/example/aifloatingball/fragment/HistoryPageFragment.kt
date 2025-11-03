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
import com.example.aifloatingball.adapter.HistoryEntryAdapter
import com.example.aifloatingball.model.HistoryEntry
import com.google.android.material.textfield.TextInputEditText
import java.util.*

/**
 * 历史访问页面Fragment
 */
class HistoryPageFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyLayout: LinearLayout
    // private lateinit var searchEditText: TextInputEditText // 已移除
    private lateinit var adapter: HistoryEntryAdapter
    
    private var onHistoryItemClick: ((HistoryEntry) -> Unit)? = null
    private var onHistoryMoreClick: ((HistoryEntry) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history_page, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupRecyclerView()
        setupSearch()
        loadHistoryData()
    }
    
    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.rv_history)
        emptyLayout = view.findViewById(R.id.layout_empty_history)
        // searchEditText已移除（避免与dialog中的输入框重复）
        // searchEditText = view.findViewById(R.id.et_search_history)
    }
    
    private fun setupRecyclerView() {
        val isLeftHanded = try {
            com.example.aifloatingball.SettingsManager.getInstance(requireContext()).isLeftHandedModeEnabled()
        } catch (_: Exception) { false }

        adapter = HistoryEntryAdapter(
            onItemClick = { entry ->
                onHistoryItemClick?.invoke(entry)
            },
            onMoreClick = { entry ->
                onHistoryMoreClick?.invoke(entry)
            },
            onSwipeFavorite = { entry ->
                try {
                    addToBookmarks(entry)
                } catch (e: Exception) {
                    android.util.Log.e("HistoryPageFragment", "收藏失败", e)
                }
            },
            onSwipeDelete = { entry ->
                try {
                    deleteHistoryEntryFromSharedPreferences(entry)
                } catch (e: Exception) {
                    android.util.Log.e("HistoryPageFragment", "删除失败", e)
                }
            },
            isLeftHandedMode = isLeftHanded
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    /**
     * 从 SharedPreferences 删除历史记录（与弹窗逻辑保持一致的精简版）
     */
    private fun deleteHistoryEntryFromSharedPreferences(entry: com.example.aifloatingball.model.HistoryEntry) {
        try {
            // 尝试识别搜索记录，通过 SearchHistoryManager 删除
            run {
                val isSearchItem = try {
                    entry.id.startsWith("search_") || entry.title.startsWith("搜索:") || entry.url.contains("?q=") || entry.url.contains("&q=")
                } catch (_: Exception) { false }
                if (isSearchItem) {
                    var query: String? = null
                    if (entry.title.startsWith("搜索:")) query = entry.title.removePrefix("搜索:").trim()
                    if (query.isNullOrEmpty()) {
                        try {
                            val uri = android.net.Uri.parse(entry.url)
                            query = uri.getQueryParameter("q")
                        } catch (_: Exception) {}
                    }
                    if (!query.isNullOrEmpty()) {
                        com.example.aifloatingball.SearchHistoryManager.getInstance(requireContext())
                            .removeSearchHistoryByQuery(query!!)
                        android.widget.Toast.makeText(requireContext(), "搜索记录已删除", android.widget.Toast.LENGTH_SHORT).show()
                        refreshAfterDataChanged()
                        return
                    }
                }
            }

            val sharedPrefs = requireContext().getSharedPreferences("browser_history", android.content.Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString("history_data", "[]")
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<MutableList<com.example.aifloatingball.model.HistoryEntry>>() {}.type
            val historyList: MutableList<com.example.aifloatingball.model.HistoryEntry> = try {
                gson.fromJson(historyJson, type) ?: mutableListOf()
            } catch (_: Exception) { mutableListOf() }

            val initialSize = historyList.size
            var removed = false
            if (entry.id.isNotEmpty()) {
                removed = historyList.removeAll { it.id == entry.id }
            }
            if (!removed) {
                removed = historyList.removeAll {
                    (it.url.equals(entry.url, true)) && (it.title.equals(entry.title, true))
                }
            }

            if (removed && historyList.size < initialSize) {
                sharedPrefs.edit().putString("history_data", gson.toJson(historyList)).apply()
                android.widget.Toast.makeText(requireContext(), "历史记录已删除", android.widget.Toast.LENGTH_SHORT).show()
                refreshAfterDataChanged()
            } else {
                android.widget.Toast.makeText(requireContext(), "未找到该记录", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("HistoryPageFragment", "删除历史失败", e)
            android.widget.Toast.makeText(requireContext(), "删除失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 添加到收藏（与弹窗逻辑保持一致的精简版）
     */
    private fun addToBookmarks(entry: com.example.aifloatingball.model.HistoryEntry) {
        try {
            val sharedPrefs = requireContext().getSharedPreferences("browser_bookmarks", android.content.Context.MODE_PRIVATE)
            val bookmarksJson = sharedPrefs.getString("bookmarks_data", "[]")
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<MutableList<com.example.aifloatingball.model.BookmarkEntry>>() {}.type
            val list: MutableList<com.example.aifloatingball.model.BookmarkEntry> = try {
                gson.fromJson(bookmarksJson, type) ?: mutableListOf()
            } catch (_: Exception) { mutableListOf() }

            val exists = list.indexOfFirst { it.url.equals(entry.url, true) } >= 0
            if (exists) {
                android.widget.Toast.makeText(requireContext(), "该网址已在收藏", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val bookmark = com.example.aifloatingball.model.BookmarkEntry(
                id = "bookmark_${System.currentTimeMillis()}",
                title = entry.title,
                url = entry.url,
                folder = "从历史添加",
                createTime = java.util.Date()
            )
            list.add(0, bookmark)
            sharedPrefs.edit().putString("bookmarks_data", gson.toJson(list)).apply()
            android.widget.Toast.makeText(requireContext(), "已添加到收藏", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("HistoryPageFragment", "添加收藏失败", e)
            android.widget.Toast.makeText(requireContext(), "添加失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAfterDataChanged() {
        try {
            // 简单刷新：重新加载数据（如果正式数据源不同，可替换为实际加载）
            loadHistoryData()
            updateEmptyState()
        } catch (_: Exception) {}
    }
    
    private fun setupSearch() {
        // 搜索功能已移除（避免与dialog中的输入框重复）
        // searchEditText.addTextChangedListener(object : TextWatcher {
        //     override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        //     
        //     override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        //         adapter.filterEntries(s.toString())
        //     }
        //     
        //     override fun afterTextChanged(s: Editable?) {}
        // })
    }
    
    private fun loadHistoryData() {
        // 模拟历史数据
        val mockHistory = listOf(
            HistoryEntry(
                id = "1",
                title = "GitHub - 代码托管平台",
                url = "https://github.com",
                visitTime = Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000) // 2小时前
            ),
            HistoryEntry(
                id = "2",
                title = "Stack Overflow - 程序员问答社区",
                url = "https://stackoverflow.com",
                visitTime = Date(System.currentTimeMillis() - 4 * 60 * 60 * 1000) // 4小时前
            ),
            HistoryEntry(
                id = "3",
                title = "Android 开发者官网",
                url = "https://developer.android.com",
                visitTime = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000) // 1天前
            ),
            HistoryEntry(
                id = "4",
                title = "Material Design 指南",
                url = "https://material.io",
                visitTime = Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000) // 3天前
            )
        )
        
        adapter.updateEntries(mockHistory)
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
    
    fun setOnHistoryItemClick(listener: (HistoryEntry) -> Unit) {
        onHistoryItemClick = listener
    }
    
    fun setOnHistoryMoreClick(listener: (HistoryEntry) -> Unit) {
        onHistoryMoreClick = listener
    }
}
