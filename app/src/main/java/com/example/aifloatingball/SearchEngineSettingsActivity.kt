package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.EngineAdapter
import com.example.aifloatingball.model.SearchEngine

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
        val engines = settingsManager.getEngineOrder().map { aiEngine ->
            SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
        }
        engineAdapter = EngineAdapter(
            engines
        ) { engine ->
            // 启动悬浮窗服务并传递搜索引擎信息
            val intent = Intent(this, FloatingWindowService::class.java).apply {
                putExtra("ENGINE_NAME", engine.name)
                putExtra("ENGINE_URL", engine.url)
                putExtra("ENGINE_ICON", engine.iconResId)
                putExtra("SHOULD_OPEN_URL", true)
            }
            startService(intent)
            finish() // 关闭设置页面
        }
        
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