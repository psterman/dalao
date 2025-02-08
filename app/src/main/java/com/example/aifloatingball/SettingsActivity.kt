package com.example.aifloatingball

import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.DragCallback
import com.example.aifloatingball.adapter.EngineAdapter

class SettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化搜索引擎列表
        engineAdapter = EngineAdapter(settingsManager.getEngineOrder().toMutableList())
        val recyclerView = findViewById<RecyclerView>(R.id.engine_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = engineAdapter
        ItemTouchHelper(DragCallback(engineAdapter)).attachToRecyclerView(recyclerView)
        
        // 初始化开关
        val autoStartSwitch = findViewById<Switch>(R.id.auto_start_switch)
        autoStartSwitch.isChecked = settingsManager.getAutoStart()
        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAutoStart(isChecked)
        }
        
        // 返回按钮
        findViewById<Button>(R.id.back_button).setOnClickListener {
            // 保存搜索引擎顺序
            settingsManager.saveEngineOrder(engineAdapter.getEngines())
            finish()
        }
    }
} 