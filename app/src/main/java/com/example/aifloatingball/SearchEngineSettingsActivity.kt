package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.CategoryAdapter
import com.example.aifloatingball.adapter.GenericSearchEngineAdapter
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory

class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerViewCategories: RecyclerView
    private lateinit var recyclerViewSearchEngines: RecyclerView
    private lateinit var searchEngineAdapter: GenericSearchEngineAdapter<SearchEngine>
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var settingsManager: SettingsManager
    private lateinit var disableAllButton: Button
    private val enabledEngines = mutableSetOf<String>()
    private val allEngines = SearchEngine.DEFAULT_ENGINES
    private var currentCategory: SearchEngineCategory = SearchEngineCategory.GENERAL
    private var itemTouchHelper: ItemTouchHelper? = null
    private var currentEngines: MutableList<SearchEngine> = mutableListOf()

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

        setupCategories()
        setupSearchEngines()
        setupDisableAllButton()

        // Initial load
        filterEnginesByCategory(SearchEngineCategory.GENERAL)
    }

    private fun setupCategories() {
        recyclerViewCategories = findViewById(R.id.recyclerViewCategories)
        recyclerViewCategories.layoutManager = LinearLayoutManager(this)
        val categories = SearchEngineCategory.values().toList()
        categoryAdapter = CategoryAdapter(categories) { category ->
            filterEnginesByCategory(category)
        }
        recyclerViewCategories.adapter = categoryAdapter
    }

    private fun setupSearchEngines() {
        recyclerViewSearchEngines = findViewById(R.id.recyclerViewSearchEngines)
        recyclerViewSearchEngines.layoutManager = LinearLayoutManager(this)
        searchEngineAdapter = GenericSearchEngineAdapter(
            context = this,
            engines = mutableListOf(), // Initially empty
            enabledEngines = enabledEngines,
            onEngineToggled = { engineName, isEnabled -> 
                if (isEnabled) {
                    enabledEngines.add(engineName)
                } else {
                    enabledEngines.remove(engineName)
                }
                settingsManager.saveEnabledSearchEngines(enabledEngines)
                sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
            },
            onOrderChanged = { orderedList ->
                // 保存排序顺序
                settingsManager.saveSearchEngineOrder(
                    currentCategory.name,
                    orderedList.map { it.name }
                )
            }
        )
        recyclerViewSearchEngines.adapter = searchEngineAdapter
        
        // 设置拖拽排序
        setupDragAndDrop()
    }
    
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                searchEngineAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }
        }
        
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerViewSearchEngines)
    }
    
    private fun setupDisableAllButton() {
        disableAllButton = findViewById(R.id.btnDisableAll)
        disableAllButton.setOnClickListener {
            // 显示确认对话框
            AlertDialog.Builder(this)
                .setTitle("确认关闭")
                .setMessage("确定要关闭当前分类下的所有搜索引擎吗？")
                .setPositiveButton("确定") { _, _ ->
                    // 关闭当前分类下的所有引擎
                    currentEngines.forEach { engine ->
                        enabledEngines.remove(engine.name)
                    }
                    settingsManager.saveEnabledSearchEngines(enabledEngines)
                    sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
                    
                    // 更新适配器
                    searchEngineAdapter.updateEngines(currentEngines)
                    Toast.makeText(this, "已关闭所有引擎", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun filterEnginesByCategory(category: SearchEngineCategory) {
        currentCategory = category
        val filteredEngines = allEngines.filter { it.category == category }.toMutableList()
        
        // 加载保存的排序顺序
        val savedOrder = settingsManager.getSearchEngineOrder(
            category.name,
            filteredEngines.map { it.name }
        )
        val orderedEngines = mutableListOf<SearchEngine>()
        // 先按保存的顺序排列
        savedOrder.forEach { engineName ->
            filteredEngines.find { it.name == engineName }?.let { orderedEngines.add(it) }
        }
        // 添加未在排序中的新引擎
        filteredEngines.forEach { engine ->
            if (!orderedEngines.any { it.name == engine.name }) {
                orderedEngines.add(engine)
            }
        }
        
        currentEngines.clear()
        currentEngines.addAll(orderedEngines)
        searchEngineAdapter.updateEngines(currentEngines)
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