package com.example.aifloatingball

import android.content.Intent
import android.os.Build
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
        
        // 为引擎列表添加长按菜单
        engineAdapter.setOnItemLongClickListener(object : EngineAdapter.OnItemLongClickListener {
            override fun onItemLongClick(position: Int) {
                val engine = engineAdapter.getEngines()[position]
                val popupMenu = android.widget.PopupMenu(this@SettingsActivity, recyclerView.findViewHolderForAdapterPosition(position)?.itemView)
                popupMenu.menu.add("移动到顶部").setOnMenuItemClickListener {
                    engineAdapter.moveToTop(position)
                    true
                }
                popupMenu.menu.add("移动到底部").setOnMenuItemClickListener {
                    engineAdapter.moveToBottom(position)
                    true
                }
                popupMenu.menu.add("删除").setOnMenuItemClickListener {
                    engineAdapter.removeEngine(position)
                    true
                }
                popupMenu.show()
            }
        })
        
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
            
            // 启动悬浮球服务
            val serviceIntent = Intent(this, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            finish()
        }
    }
    
    override fun onBackPressed() {
        // 处理返回键，执行与返回按钮相同的操作
        findViewById<Button>(R.id.back_button).performClick()
    }
} 