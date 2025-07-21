package com.example.aifloatingball

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.GenericSearchEngineAdapter
import com.example.aifloatingball.adapter.GroupedSearchEngineAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

/**
 * 搜索引擎管理活动
 */
class SearchEngineManagerActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var tabLayout: TabLayout
    private lateinit var regularEnginesRecyclerView: RecyclerView
    private lateinit var aiEnginesRecyclerView: RecyclerView
    private lateinit var saveButton: FloatingActionButton

    companion object {
        private const val TAG = "SearchEngineManager"
    }

    // 保存已启用的普通搜索引擎
    private val enabledSearchEngines = mutableSetOf<String>()
    // 保存已启用的AI搜索引擎
    private val enabledAIEngines = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
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
            enabledAIEngines.addAll(settingsManager.getEnabledAIEngines())

            Log.d(TAG, "Enabled search engines: $enabledSearchEngines")
            Log.d(TAG, "Enabled AI engines: $enabledAIEngines")

            // 初始化视图
            setupViews()

            // 加载搜索引擎列表
            loadSearchEngines()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "初始化搜索引擎管理器失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            tabLayout = findViewById(R.id.tabLayout)
            regularEnginesRecyclerView = findViewById(R.id.regularEnginesRecyclerView)
            aiEnginesRecyclerView = findViewById(R.id.aiEnginesRecyclerView)
            saveButton = findViewById(R.id.saveButton)

            Log.d(TAG, "Views found successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding views", e)
            Toast.makeText(this, "找不到界面元素: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        
        // 移除分类RecyclerView设置，使用分组适配器

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
            saveSettings()
            finish()
        }

        // 默认显示普通搜索引擎
        regularEnginesRecyclerView.visibility = View.VISIBLE
        aiEnginesRecyclerView.visibility = View.GONE
    }



    private fun loadSearchEngines() {
        // 使用分组适配器加载所有搜索引擎
        loadGroupedSearchEngines()
        loadAIEngines()
    }

    private fun loadGroupedSearchEngines() {
        try {
            Log.d(TAG, "Loading grouped search engines...")

            // 配置普通搜索引擎列表
            regularEnginesRecyclerView.layoutManager = LinearLayoutManager(this)

            // 使用分组适配器
            val groupedAdapter = GroupedSearchEngineAdapter(
                enabledSearchEngines.toMutableSet()
            ) { engineName, isEnabled ->
                // 处理普通搜索引擎的启用/禁用
                if (isEnabled) {
                    enabledSearchEngines.add(engineName)
                } else {
                    enabledSearchEngines.remove(engineName)
                }
                Log.d(TAG, "Regular engine $engineName ${if (isEnabled) "enabled" else "disabled"}")
            }
            regularEnginesRecyclerView.adapter = groupedAdapter

            Log.d(TAG, "Grouped search engines loaded successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading grouped search engines", e)
            Toast.makeText(this, "加载搜索引擎失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAllSearchEngines() {
        try {
            Log.d(TAG, "Loading all search engines (fallback mode)...")

            // 配置普通搜索引擎列表
            regularEnginesRecyclerView.layoutManager = LinearLayoutManager(this)

            // 加载所有普通搜索引擎（不按分类过滤）
            val regularEngines = SearchEngine.DEFAULT_ENGINES.toMutableList()
            Log.d(TAG, "Total regular engines count: ${regularEngines.size}")

            val regularAdapter = GenericSearchEngineAdapter(
                this,
                regularEngines,
                enabledSearchEngines.toMutableSet()
            ) { engineName, isEnabled ->
                // 处理普通搜索引擎的启用/禁用
                if (isEnabled) {
                    enabledSearchEngines.add(engineName)
                } else {
                    enabledSearchEngines.remove(engineName)
                }
                Log.d(TAG, "Regular engine $engineName ${if (isEnabled) "enabled" else "disabled"}")
            }
            regularEnginesRecyclerView.adapter = regularAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error loading all search engines", e)
            Toast.makeText(this, "加载搜索引擎失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }





    private fun loadAIEngines() {
        try {

            Log.d(TAG, "Loading AI engines...")

            // 配置AI搜索引擎列表
            aiEnginesRecyclerView.layoutManager = LinearLayoutManager(this)

            // 创建AI搜索引擎适配器
            val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES.toMutableList()
            Log.d(TAG, "AI engines count: ${aiEngines.size}")

            val aiAdapter = GenericSearchEngineAdapter(
                this,
                aiEngines,
                enabledAIEngines.toMutableSet()
            ) { engineName, isEnabled ->
                // 处理AI搜索引擎的启用/禁用
                if (isEnabled) {
                    enabledAIEngines.add(engineName)
                } else {
                    enabledAIEngines.remove(engineName)
                }
                Log.d(TAG, "AI engine $engineName ${if (isEnabled) "enabled" else "disabled"}")
            }
            aiEnginesRecyclerView.adapter = aiAdapter

            Log.d(TAG, "AI engines loaded successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading AI engines", e)
            Toast.makeText(this, "加载AI搜索引擎失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveSettings() {
        try {
            Log.d(TAG, "Saving settings...")

            // 保存普通搜索引擎设置
            settingsManager.saveEnabledSearchEngines(enabledSearchEngines)
            Log.d(TAG, "Saved enabled search engines: $enabledSearchEngines")

            // 保存AI搜索引擎设置
            settingsManager.saveEnabledAIEngines(enabledAIEngines)
            Log.d(TAG, "Saved enabled AI engines: $enabledAIEngines")

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving settings", e)
            Toast.makeText(this, "保存设置失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(menuItem)
    }
}