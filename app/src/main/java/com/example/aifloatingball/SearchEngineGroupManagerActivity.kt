package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.manager.SearchEngineManager
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineGroup
import com.example.aifloatingball.SettingsManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/**
 * 搜索引擎组合管理活动
 * 允许用户查看、启用或禁用搜索引擎组合，并管理显示在FloatingWindowService中的快捷方式
 */
class SearchEngineGroupManagerActivity : AppCompatActivity() {
    private var recyclerView: RecyclerView? = null
    private var adapter: SearchEngineGroupAdapter? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var searchEngineManager: SearchEngineManager
    private var emptyView: TextView? = null
    private var saveButton: MaterialButton? = null
    
    // 启用的搜索引擎组合
    private val enabledGroups = mutableSetOf<String>()
    // 所有搜索引擎组合
    private var searchEngineGroups = listOf<SearchEngineGroup>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_group_manager)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "搜索引擎组合管理"
            setDisplayHomeAsUpEnabled(true)
        }
        
        // 初始化管理器
        settingsManager = SettingsManager.getInstance(this)
        searchEngineManager = SearchEngineManager.getInstance(this)
        
        // 获取启用的搜索引擎组合
        enabledGroups.addAll(settingsManager.getEnabledSearchEngineGroups())
        
        // 初始化视图并进行空值检查
        emptyView = findViewById(R.id.empty_view)
        recyclerView = findViewById(R.id.recyclerView)
        saveButton = findViewById(R.id.saveButton)

        if (recyclerView == null || emptyView == null || saveButton == null) {
            Log.e("SearchEngineGroupManagerActivity", "One or more required views are null. Check layout file.")
            Toast.makeText(this, "无法加载设置界面", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recyclerView?.layoutManager = LinearLayoutManager(this)
        
        // 加载搜索引擎组合并设置适配器
        loadSearchEngineGroups()
        setupAdapter()
        
        // 设置保存按钮
        saveButton?.setOnClickListener {
            saveSettings()
        }

        // 更新空视图状态
        updateEmptyViewVisibility()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    /**
     * 加载搜索引擎组合列表
     */
    private fun loadSearchEngineGroups() {
        searchEngineGroups = searchEngineManager.getSearchEngineGroups()
    }
    
    /**
     * 设置适配器
     */
    private fun setupAdapter() {
        adapter = SearchEngineGroupAdapter(
            searchEngineGroups,
            enabledGroups,
            onCheckChange = { groupName, isChecked ->
                if (isChecked) {
                    enabledGroups.add(groupName)
                } else {
                    enabledGroups.remove(groupName)
                }
            },
            onDeleteClick = { group, position ->
                showDeleteConfirmDialog(group, position)
            }
        )
        recyclerView?.adapter = adapter
    }
    
    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmDialog(group: SearchEngineGroup, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除搜索引擎组合")
            .setMessage("确定要删除\"${group.name}\"搜索引擎组合吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteSearchEngineGroup(group, position)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 删除搜索引擎组合
     */
    private fun deleteSearchEngineGroup(group: SearchEngineGroup, position: Int) {
        // 从SearchEngineManager中删除
        searchEngineManager.deleteSearchEngineGroup(group.name)
        
        // 从内存中移除
        val updatedGroups = searchEngineGroups.toMutableList()
        updatedGroups.removeAt(position)
        searchEngineGroups = updatedGroups
        
        // 从已启用集合中移除
        enabledGroups.remove(group.name)
        
        // 更新适配器
        adapter?.updateData(searchEngineGroups)
        
        // 显示提示
        Snackbar.make(findViewById(android.R.id.content), "已删除\"${group.name}\"", Snackbar.LENGTH_SHORT).show()
        
        // 更新空视图状态
        updateEmptyViewVisibility()
        
        // 通知FloatingWindowService更新快捷方式
        sendUpdateBroadcast()
    }
    
    /**
     * 保存设置并发送更新广播
     */
    private fun saveSettings() {
        // 保存已启用的搜索引擎组合
        settingsManager.setEnabledSearchEngineGroups(enabledGroups)
        
        // 通知FloatingWindowService更新快捷方式
        sendUpdateBroadcast()
        
        // 显示成功提示
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        
        // 返回上一页
        finish()
    }
    
    /**
     * 发送更新广播
     */
    private fun sendUpdateBroadcast() {
        val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_SHORTCUTS")
        sendBroadcast(intent)
    }
    
    /**
     * 更新空视图的可见性
     */
    private fun updateEmptyViewVisibility() {
        if (searchEngineGroups.isEmpty()) {
            emptyView?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            emptyView?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }
    }
    
    /**
     * 搜索引擎组合适配器
     */
    private class SearchEngineGroupAdapter(
        private var groups: List<SearchEngineGroup>,
        private val enabledGroups: Set<String>,
        private val onCheckChange: (String, Boolean) -> Unit,
        private val onDeleteClick: (SearchEngineGroup, Int) -> Unit
    ) : RecyclerView.Adapter<SearchEngineGroupAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox: CheckBox = view.findViewById(R.id.checkbox)
            val groupName: TextView = view.findViewById(R.id.group_name)
            val groupDescription: TextView = view.findViewById(R.id.group_description)
            val deleteButton: ImageView = view.findViewById(R.id.delete_button)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_engine_group, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val group = groups[position]
            
            // 设置名称和描述
            holder.groupName.text = group.name
            
            // 构建描述信息：显示组内引擎名称
            val engineNames = group.engines.joinToString(", ") { it.name }
            holder.groupDescription.text = "包含: $engineNames"
            
            // 设置复选框状态
            holder.checkbox.isChecked = enabledGroups.contains(group.name)
            
            // 设置复选框点击事件
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckChange(group.name, isChecked)
            }
            
            // 设置删除按钮点击事件
            holder.deleteButton.setOnClickListener {
                onDeleteClick(group, position)
            }
        }
        
        override fun getItemCount() = groups.size
        
        /**
         * 更新数据
         */
        fun updateData(newGroups: List<SearchEngineGroup>) {
            groups = newGroups
            notifyDataSetChanged()
        }
    }
} 