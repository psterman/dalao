package com.example.aifloatingball

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.EngineAdapter
import com.example.aifloatingball.adapter.DragCallback

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_settings)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "搜索引擎排序"
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化搜索引擎列表
        engineAdapter = EngineAdapter(settingsManager.getEngineOrder().toMutableList())
        val recyclerView = findViewById<RecyclerView>(R.id.engine_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = engineAdapter
        
        // 添加拖拽排序功能
        val itemTouchHelper = ItemTouchHelper(DragCallback(engineAdapter))
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    override fun onPause() {
        super.onPause()
        // 保存排序结果
        settingsManager.saveEngineOrder(engineAdapter.getEngines())
    }
} 