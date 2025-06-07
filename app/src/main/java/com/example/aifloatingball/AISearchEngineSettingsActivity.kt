package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adapter.AIEngineCategoryAdapter
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class AISearchEngineSettingsActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: AIEngineCategoryAdapter
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_search_engine_settings)

        settingsManager = SettingsManager.getInstance(this)

        // 设置标题栏
        supportActionBar?.apply {
            title = "AI搜索引擎管理"
            setDisplayHomeAsUpEnabled(true)
        }

        // 初始化UI组件
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        // 设置UI
        setupUI()
    }

    private fun setupUI() {
        // 1. 分类AI引擎
        val allEngines = AISearchEngine.DEFAULT_AI_ENGINES
        val apiBasedEngines = allEngines.filter { it.isChatMode }
        val searchBasedEngines = allEngines.filter { !it.isChatMode && isSearchEngine(it.name) }
        val chatPlatformEngines = allEngines.filter { !it.isChatMode && !isSearchEngine(it.name) }

        val categories = listOf(chatPlatformEngines, searchBasedEngines, apiBasedEngines)
        val categoryTitles = listOf("AI对话平台", "AI搜索引擎", "API对话")

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                sendBroadcast(Intent(DualFloatingWebViewService.ACTION_UPDATE_AI_ENGINES))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 发送广播以确保在退出时，任何更改都能被应用
        sendBroadcast(Intent(DualFloatingWebViewService.ACTION_UPDATE_AI_ENGINES))
    }
} 