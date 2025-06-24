package com.example.aifloatingball

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchHistoryAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SearchHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearButton: FloatingActionButton
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private lateinit var settingsManager: SettingsManager
    private var historyList = mutableListOf<Map<String, Any>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_history)

        settingsManager = SettingsManager.getInstance(this)

        // Setup ActionBar
        supportActionBar?.apply {
            title = "搜索历史"
            setDisplayHomeAsUpEnabled(true)
        }

        // Setup RecyclerView
        recyclerView = findViewById(R.id.search_history_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        clearButton = findViewById(R.id.fab_clear_history)
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupAdapter()
        loadSearchHistory()

        clearButton.setOnClickListener {
            showClearAllConfirmationDialog()
        }
    }

    private fun setupAdapter() {
        searchHistoryAdapter = SearchHistoryAdapter { itemToDelete ->
            showDeleteConfirmationDialog(itemToDelete)
        }
        recyclerView.adapter = searchHistoryAdapter
    }

    private fun loadSearchHistory() {
        val allHistory = settingsManager.getSearchHistory()
            .sortedByDescending { it["timestamp"] as? Long ?: 0L }
        
        val groupedHistory = allHistory.groupBy { it["source"] as? String ?: "未知来源" }

        historyList.clear()
        // The adapter now handles the flat list creation
        searchHistoryAdapter.updateData(groupedHistory)
        updateEmptyView()
    }

    private fun updateEmptyView() {
        val history = settingsManager.getSearchHistory()
        if (history.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            clearButton.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE
        }
    }
    
    private fun showDeleteConfirmationDialog(item: Map<String, Any>) {
        AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle("删除记录")
            .setMessage("您确定要删除这条搜索记录吗？")
            .setPositiveButton("删除") { _, _ ->
                val updatedHistory = settingsManager.getSearchHistory().toMutableList()
                updatedHistory.remove(item)
                settingsManager.saveSearchHistory(updatedHistory)
                Toast.makeText(this, "记录已删除", Toast.LENGTH_SHORT).show()
                loadSearchHistory()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle("确认清除")
            .setMessage("您确定要清除所有搜索历史吗？此操作无法撤销。")
            .setPositiveButton("清除") { _, _ ->
                settingsManager.saveSearchHistory(emptyList())
                Toast.makeText(this, "搜索历史已清除", Toast.LENGTH_SHORT).show()
                loadSearchHistory()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 