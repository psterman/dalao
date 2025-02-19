package com.example.aifloatingball

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.EngineAdapter

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_settings)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "搜索引擎管理"
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化搜索引擎列表
        val engines = settingsManager.getEngineOrder()
        val enabledEngines = settingsManager.getEnabledEngines()
        engineAdapter = EngineAdapter(
            engines.toMutableList(),
            enabledEngines,
            object : EngineAdapter.OnEngineSelectionListener {
                override fun onEngineSelectionChanged(selectedEngines: Set<String>) {
                    settingsManager.saveEnabledEngines(selectedEngines)
                }
            }
        )
        
        val recyclerView = findViewById<RecyclerView>(R.id.engine_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = engineAdapter
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 