package com.example.aifloatingball

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.SearchEngineAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.google.android.material.tabs.TabLayout

/**
 * 搜索引擎管理活动
 */
class SearchEngineManagerActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var tabLayout: TabLayout
    private lateinit var regularEnginesRecyclerView: RecyclerView
    private lateinit var aiEnginesRecyclerView: RecyclerView
    private lateinit var saveButton: Button

    // 保存已启用的普通搜索引擎
    private val enabledSearchEngines = mutableSetOf<String>()
    // 保存已启用的AI搜索引擎
    private val enabledAIEngines = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_manager)
        
        // 初始化SettingsManager
        settingsManager = SettingsManager.getInstance(this)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "搜索引擎管理"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 初始化已启用的搜索引擎集合
        enabledSearchEngines.addAll(settingsManager.getEnabledSearchEngines())
        enabledAIEngines.addAll(settingsManager.getEnabledEngines())
        
        // 初始化视图
        setupViews()
        
        // 加载搜索引擎列表
        loadSearchEngines()
    }

    private fun setupViews() {
        tabLayout = findViewById(R.id.tabLayout)
        regularEnginesRecyclerView = findViewById(R.id.regularEnginesRecyclerView)
        aiEnginesRecyclerView = findViewById(R.id.aiEnginesRecyclerView)
        saveButton = findViewById(R.id.saveButton)
        
        // 配置TabLayout
        tabLayout.addTab(tabLayout.newTab().setText("普通搜索"))
        tabLayout.addTab(tabLayout.newTab().setText("AI搜索"))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        regularEnginesRecyclerView.visibility = View.VISIBLE
                        aiEnginesRecyclerView.visibility = View.GONE
                    }
                    1 -> {
                        regularEnginesRecyclerView.visibility = View.GONE
                        aiEnginesRecyclerView.visibility = View.VISIBLE
                    }
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        // 配置保存按钮
        saveButton.setOnClickListener {
            saveSearchEngines()
            finish()
        }
    }

    private fun loadSearchEngines() {
        // 配置普通搜索引擎列表
        regularEnginesRecyclerView.layoutManager = LinearLayoutManager(this)
        regularEnginesRecyclerView.adapter = SearchEngineAdapter(
            this,
            SearchEngine.DEFAULT_ENGINES,
            enabledSearchEngines
        ) { engineName, isEnabled ->
            // 处理普通搜索引擎的启用/禁用
            if (isEnabled) {
                enabledSearchEngines.add(engineName)
            } else {
                enabledSearchEngines.remove(engineName)
            }
        }
        
        // 配置AI搜索引擎列表
        aiEnginesRecyclerView.layoutManager = LinearLayoutManager(this)
        aiEnginesRecyclerView.adapter = SearchEngineAdapter(
            this,
            AISearchEngine.DEFAULT_AI_ENGINES,
            enabledAIEngines
        ) { engineName, isEnabled ->
            // 处理AI搜索引擎的启用/禁用
            if (isEnabled) {
                enabledAIEngines.add(engineName)
            } else {
                enabledAIEngines.remove(engineName)
            }
        }
    }

    private fun saveSearchEngines() {
        // 保存普通搜索引擎设置
        settingsManager.setEnabledSearchEngines(enabledSearchEngines)
        
        // 保存AI搜索引擎设置
        settingsManager.saveEnabledEngines(enabledAIEngines)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 