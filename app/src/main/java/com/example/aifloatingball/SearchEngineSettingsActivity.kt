package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.AISearchEngine

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchEngineAdapter
    private lateinit var settingsManager: SettingsManager
    private val enabledEngines = mutableSetOf<String>()

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

        recyclerView = findViewById(R.id.recyclerViewSearchEngines)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = SearchEngineAdapter(
            engines = SearchEngine.DEFAULT_ENGINES,
            onEngineSelected = { engine -> 
                // 处理搜索引擎选择
                if (enabledEngines.contains(engine.name)) {
                    enabledEngines.remove(engine.name)
                } else {
                    enabledEngines.add(engine.name)
                }
                settingsManager.setEnabledSearchEngines(enabledEngines)
            }
        )
        
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