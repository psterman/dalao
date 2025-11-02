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
        adapter = HistoryEntryAdapter(
            onItemClick = { entry ->
                onHistoryItemClick?.invoke(entry)
            },
            onMoreClick = { entry ->
                onHistoryMoreClick?.invoke(entry)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
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
