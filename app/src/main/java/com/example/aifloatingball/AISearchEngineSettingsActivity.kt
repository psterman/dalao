package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adapter.AIEngineCategoryAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AISearchEngineSettingsActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: AIEngineCategoryAdapter
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            android.util.Log.d("AISearchEngineSettings", "开始创建AI搜索引擎设置Activity")

            setContentView(R.layout.activity_ai_search_engine_settings)
            android.util.Log.d("AISearchEngineSettings", "成功设置内容视图")

            settingsManager = SettingsManager.getInstance(this)
            android.util.Log.d("AISearchEngineSettings", "成功初始化SettingsManager")

            // 设置标题栏
            val toolbar: MaterialToolbar? = findViewById(R.id.toolbar)
            if (toolbar == null) {
                android.util.Log.e("AISearchEngineSettings", "找不到toolbar控件")
                Toast.makeText(this, "界面加载失败：找不到标题栏", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            android.util.Log.d("AISearchEngineSettings", "成功设置工具栏")

            // 初始化UI组件
            val tabLayoutView = findViewById<TabLayout>(R.id.tabLayout)
            val viewPagerView = findViewById<ViewPager2>(R.id.viewPager)

            if (tabLayoutView == null || viewPagerView == null) {
                android.util.Log.e("AISearchEngineSettings", "找不到TabLayout或ViewPager控件")
                Toast.makeText(this, "界面加载失败：找不到必要控件", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            tabLayout = tabLayoutView
            viewPager = viewPagerView
            android.util.Log.d("AISearchEngineSettings", "成功初始化UI组件")

            // 设置UI
            setupUI()
            android.util.Log.d("AISearchEngineSettings", "AI搜索引擎设置Activity创建完成")
        } catch (e: Exception) {
            android.util.Log.e("AISearchEngineSettings", "创建AI搜索引擎设置Activity失败", e)
            Toast.makeText(this, "加载AI搜索引擎设置失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupUI() {
        // 1. 分类AI引擎
        val allEngines = AISearchEngine.DEFAULT_AI_ENGINES
        val apiBasedEngines = allEngines.filter { it.isChatMode }
        val searchBasedEngines = allEngines.filter { !it.isChatMode && isSearchEngine(it.name) }
        val chatPlatformEngines = allEngines.filter { !it.isChatMode && !isSearchEngine(it.name) }

        val allCategoryData = linkedMapOf<String, List<AISearchEngine>>()
        if (chatPlatformEngines.isNotEmpty()) {
            allCategoryData["AI对话平台"] = chatPlatformEngines
        }
        if (searchBasedEngines.isNotEmpty()) {
            allCategoryData["AI搜索引擎"] = searchBasedEngines
        }
        if (apiBasedEngines.isNotEmpty()) {
            allCategoryData["API对话"] = apiBasedEngines
        }

        val categories = allCategoryData.values.toList()
        val categoryTitles = allCategoryData.keys.toList()

        if (categories.isEmpty()) {
            tabLayout.visibility = View.GONE
            viewPager.visibility = View.GONE
            // 显示一个空状态提示
            Toast.makeText(this, "没有可用的AI搜索引擎", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 创建并设置适配器
        adapter = AIEngineCategoryAdapter(this, categories)
        viewPager.adapter = adapter

        // 3. 关联TabLayout和ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = categoryTitles[position]
        }.attach()
    }

    private fun isSearchEngine(name: String): Boolean {
        return name in listOf(
            "Perplexity", "Phind", "天工AI", "秘塔AI搜索", "夸克AI",
            "360AI搜索", "百度AI", "You.com", "Brave Search", "WolframAlpha"
        )
    }
} 