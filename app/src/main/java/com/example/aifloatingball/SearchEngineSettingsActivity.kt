package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.AISearchEngine

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchEngineAdapter<SearchEngine>
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
        
        // 创建并设置适配器
        adapter = SearchEngineAdapter(
            context = this,
            engines = SearchEngine.DEFAULT_ENGINES,
            enabledEngines = enabledEngines,
            onEngineToggled = { engineName, isEnabled -> 
                // 处理搜索引擎切换
                if (isEnabled) {
                    enabledEngines.add(engineName)
                } else {
                    enabledEngines.remove(engineName)
                }
                settingsManager.setEnabledSearchEngines(enabledEngines)
                
                // 发送广播通知悬浮球服务更新菜单
                sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
            }
        )
        
        // 设置适配器
        recyclerView.adapter = adapter
        
        // 确保所有列表项中的开关控件都可见
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            for (i in 0 until recyclerView.childCount) {
                val viewHolder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
                if (viewHolder is SearchEngineAdapter<*>.ViewHolder) {
                    viewHolder.toggleSwitch.visibility = View.VISIBLE
                }
            }
        }
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