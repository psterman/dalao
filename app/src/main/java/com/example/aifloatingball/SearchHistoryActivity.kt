package com.example.aifloatingball

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchHistoryAdapter
import com.example.aifloatingball.database.AppDatabase
import com.example.aifloatingball.model.SearchHistory
import kotlinx.coroutines.launch

class SearchHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearButton: Button
    private lateinit var historyAdapter: SearchHistoryAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_history)

        setupViews()
        setupRecyclerView()
        observeHistory()
        setupListeners()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.history_recycler_view)
        emptyView = findViewById(R.id.empty_view)
        clearButton = findViewById(R.id.clear_history_button)
        supportActionBar?.title = "搜索历史"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        historyAdapter = SearchHistoryAdapter()
        recyclerView.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@SearchHistoryActivity)
        }
    }

    private fun observeHistory() {
        db.searchHistoryDao().getAllHistory().observe(this) { historyList ->
            toggleEmptyView(historyList)
            historyAdapter.submitList(historyList)
        }
    }

    private fun setupListeners() {
        clearButton.setOnClickListener {
            clearHistory()
        }
    }

    private fun toggleEmptyView(historyList: List<SearchHistory>) {
        if (historyList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            db.searchHistoryDao().clearAll()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 