package com.example.aifloatingball

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.CategorySearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory

/**
 * 搜索引擎管理活动 - 简易模式专用
 */
class SearchEngineManagerActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var categoryTabsContainer: LinearLayout
    private lateinit var searchEnginesRecyclerView: RecyclerView
    private lateinit var saveButton: Button
    private lateinit var categoryAdapter: CategorySearchEngineAdapter

    companion object {
        private const val TAG = "SearchEngineManager"
    }

    // 保存已启用的搜索引擎
    private val enabledSearchEngines = mutableSetOf<String>()

    // 当前选中的分类
    private var currentCategory = SearchEngineCategory.GENERAL

    // 分类标签视图映射
    private val categoryTabs = mutableMapOf<SearchEngineCategory, TextView>()

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

            Log.d(TAG, "Enabled search engines: $enabledSearchEngines")

            // 初始化视图
            setupViews()

            // 设置分类标签
            setupCategoryTabs()

            // 加载搜索引擎列表
            loadSearchEngines()

            Log.d(TAG, "SearchEngineManagerActivity initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "初始化搜索引擎管理器失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            categoryTabsContainer = findViewById(R.id.categoryTabsContainer)
            searchEnginesRecyclerView = findViewById(R.id.searchEnginesRecyclerView)
            saveButton = findViewById(R.id.saveButton)

            Log.d(TAG, "Views found successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding views", e)
            Toast.makeText(this, "找不到界面元素: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // 配置RecyclerView
        searchEnginesRecyclerView.layoutManager = LinearLayoutManager(this)

        // 初始化适配器
        categoryAdapter = CategorySearchEngineAdapter(
            emptyList(),
            enabledSearchEngines
        ) { engine, enabled ->
            if (enabled) {
                enabledSearchEngines.add(engine.name)
            } else {
                enabledSearchEngines.remove(engine.name)
            }
            Log.d(TAG, "Engine ${engine.name} toggled: $enabled")
        }

        searchEnginesRecyclerView.adapter = categoryAdapter

        // 配置保存按钮
        saveButton.setOnClickListener {
            saveSettings()
        }
    }



    private fun setupCategoryTabs() {
        // 清空现有标签
        categoryTabsContainer.removeAllViews()
        categoryTabs.clear()

        // 创建分类标签
        val categories = listOf(
            SearchEngineCategory.GENERAL,
            SearchEngineCategory.NEWS,
            SearchEngineCategory.SHOPPING,
            SearchEngineCategory.SOCIAL,
            SearchEngineCategory.VIDEO,
            SearchEngineCategory.ACADEMIC,
            SearchEngineCategory.LIFESTYLE,
            SearchEngineCategory.DEVELOPER,
            SearchEngineCategory.KNOWLEDGE,
            SearchEngineCategory.DESIGN,
            SearchEngineCategory.OTHERS
        )

        categories.forEach { category ->
            val tabView = LayoutInflater.from(this)
                .inflate(R.layout.item_category_tab_horizontal, categoryTabsContainer, false) as TextView

            tabView.text = category.displayName
            tabView.setOnClickListener {
                selectCategory(category)
            }

            categoryTabsContainer.addView(tabView)
            categoryTabs[category] = tabView
        }

        // 默认选中第一个分类
        selectCategory(SearchEngineCategory.GENERAL)
    }

    private fun selectCategory(category: SearchEngineCategory) {
        // 更新选中状态
        categoryTabs.forEach { (cat, tab) ->
            tab.isSelected = (cat == category)
            if (cat == category) {
                tab.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                tab.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            }
        }

        currentCategory = category
        loadSearchEngines()
    }

    private fun loadSearchEngines() {
        // 获取当前分类的搜索引擎
        val engines = SearchEngine.DEFAULT_ENGINES.filter { it.category == currentCategory }
        categoryAdapter.updateEngines(engines)

        Log.d(TAG, "Loaded ${engines.size} engines for category: ${currentCategory.displayName}")
    }











    private fun saveSettings() {
        try {
            // 保存已启用的搜索引擎
            settingsManager.saveEnabledSearchEngines(enabledSearchEngines)

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Settings saved successfully")

            finish()
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