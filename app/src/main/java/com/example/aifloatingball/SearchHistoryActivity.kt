package com.example.aifloatingball

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchHistoryAdapter
import com.example.aifloatingball.database.AppDatabase
import com.example.aifloatingball.database.SearchHistoryDao
import com.example.aifloatingball.model.SearchHistory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchHistoryActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var clearButton: FloatingActionButton
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter
    private lateinit var searchHistoryDao: SearchHistoryDao
    private var fullHistoryList: List<SearchHistory> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_history)

        searchHistoryDao = AppDatabase.getDatabase(this).searchHistoryDao()

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
        lifecycleScope.launch(Dispatchers.IO) {
            fullHistoryList = searchHistoryDao.getAllHistoryList()
                .sortedByDescending { it.timestamp }

            withContext(Dispatchers.Main) {
                filterHistory("") // Display all initially
            }
        }
    }

    private fun filterHistory(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullHistoryList
        } else {
            fullHistoryList.filter {
                it.query.contains(query, ignoreCase = true)
            }
        }
        val groupedHistory = filteredList.groupBy { it.source }
        searchHistoryAdapter.updateData(groupedHistory)
        updateEmptyView(filteredList.isEmpty())
    }

    private fun updateEmptyView(isHistoryEmpty: Boolean) {
        if (isHistoryEmpty) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            // Keep clear button if there is full history
            clearButton.visibility = if (fullHistoryList.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE
        }
    }
    
    private fun showDeleteConfirmationDialog(item: SearchHistory) {
        AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle("删除记录")
            .setMessage("您确定要删除这条搜索记录吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    searchHistoryDao.delete(item)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SearchHistoryActivity, "记录已删除", Toast.LENGTH_SHORT).show()
                        loadSearchHistory()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle("确认清除")
            .setMessage("您确定要清除所有搜索历史吗？此操作无法撤销。")
            .setPositiveButton("清除") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    searchHistoryDao.clearAll()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SearchHistoryActivity, "搜索历史已清除", Toast.LENGTH_SHORT).show()
                        loadSearchHistory()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_history_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        filterHistory(newText.orEmpty())
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 