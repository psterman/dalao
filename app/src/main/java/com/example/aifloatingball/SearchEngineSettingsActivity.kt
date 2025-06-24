package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.CategoryAdapter
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var recyclerViewSearchEngines: RecyclerView
    private lateinit var searchEngineAdapter: SearchEngineAdapter<SearchEngine>
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var settingsManager: SettingsManager
    private val enabledEngines = mutableSetOf<String>()
    private val allEngines = SearchEngine.DEFAULT_ENGINES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_settings)

        // 设置标题栏
        supportActionBar?.apply {
            title = "搜索引擎管理"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager.getInstance(this)
        
        // 获取已启用的搜索引擎
        enabledEngines.addAll(settingsManager.getEnabledSearchEngines())

        setupCategories()
        setupSearchEngines()

        // Initial load
        filterEnginesByCategory(SearchEngineCategory.GENERAL)
    }

    private fun setupCategories() {
        recyclerViewCategories = findViewById(R.id.recyclerViewCategories)
        recyclerViewCategories.layoutManager = LinearLayoutManager(this)
        val categories = SearchEngineCategory.values().toList()
        categoryAdapter = CategoryAdapter(categories) { category ->
            filterEnginesByCategory(category)
        }
        recyclerViewCategories.adapter = categoryAdapter
    }

    private fun setupSearchEngines() {
        recyclerViewSearchEngines = findViewById(R.id.recyclerViewSearchEngines)
        recyclerViewSearchEngines.layoutManager = LinearLayoutManager(this)
        searchEngineAdapter = SearchEngineAdapter(
            context = this,
            engines = emptyList(), // Initially empty
            enabledEngines = enabledEngines,
            onEngineToggled = { engineName, isEnabled -> 
                if (isEnabled) {
                    enabledEngines.add(engineName)
                } else {
                    enabledEngines.remove(engineName)
                }
                settingsManager.saveEnabledSearchEngines(enabledEngines)
                sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
            }
        )
        recyclerViewSearchEngines.adapter = searchEngineAdapter
    }

    private fun filterEnginesByCategory(category: SearchEngineCategory) {
        val filteredEngines = allEngines.filter { it.category == category }
        searchEngineAdapter.updateEngines(filteredEngines)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // 返回按钮点击时也发送更新广播
                sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 