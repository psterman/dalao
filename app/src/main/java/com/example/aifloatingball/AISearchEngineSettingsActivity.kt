package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.utils.IconLoader

class AISearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchEngineAdapter<AISearchEngine>
    private lateinit var settingsManager: SettingsManager
    private val enabledEngines = mutableSetOf<String>()
    private lateinit var iconLoader: IconLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_settings)

        // 初始化图标加载器
        iconLoader = IconLoader(this)

        // 设置标题栏
        supportActionBar?.apply {
            title = "AI搜索引擎管理"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager.getInstance(this)
        
        // 获取已启用的AI搜索引擎
        enabledEngines.addAll(settingsManager.getEnabledEngines())

        // 初始化UI
        setupUI()
    }
    
    private fun setupUI() {
        findViewById<View>(R.id.titleTextView).visibility = View.VISIBLE
        
        recyclerView = findViewById(R.id.recyclerViewSearchEngines)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 添加分隔线提升视觉效果
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        
        // 创建并设置适配器
        adapter = SearchEngineAdapter(
            context = this,
            engines = AISearchEngine.DEFAULT_AI_ENGINES,
            enabledEngines = enabledEngines,
            onEngineToggled = { engineName, isEnabled -> 
                // 处理AI搜索引擎切换
                if (isEnabled) {
                    enabledEngines.add(engineName)
                } else {
                    enabledEngines.remove(engineName)
                }
                settingsManager.saveEnabledEngines(enabledEngines)
                
                // 发送广播通知悬浮球服务更新
                sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
            }
        )
        
        // 设置适配器
        recyclerView.adapter = adapter
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
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源
        recyclerView.adapter = null
    }
} 