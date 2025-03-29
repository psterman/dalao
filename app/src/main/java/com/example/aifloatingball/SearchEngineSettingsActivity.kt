package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.EngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.AISearchEngine

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchEngineAdapter
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_settings)

        // 设置标题栏
        supportActionBar?.apply {
            title = "搜索引擎管理"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager.getInstance(this)

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recyclerViewSearchEngines)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 获取搜索引擎列表
        val engines = if (settingsManager.isDefaultAIMode()) {
            AISearchEngine.DEFAULT_AI_ENGINES
        } else {
            SearchEngine.NORMAL_SEARCH_ENGINES
        }

        // 获取已启用的搜索引擎
        val enabledEngines = settingsManager.getEnabledEngines()

        // 初始化适配器
        adapter = SearchEngineAdapter(engines.toMutableList(), enabledEngines.toMutableSet()) { engine, isEnabled ->
            if (isEnabled) {
                settingsManager.saveEnabledEngines(settingsManager.getEnabledEngines() + engine.name)
            } else {
                settingsManager.saveEnabledEngines(settingsManager.getEnabledEngines() - engine.name)
            }
        }

        recyclerView.adapter = adapter
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 