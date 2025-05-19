package com.example.aifloatingball

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class AppSearchSettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppSearchAdapter
    private var appSearchList = mutableListOf<AppSearchItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_search_settings)

        settingsManager = SettingsManager.getInstance(this)

        // 设置标题栏
        supportActionBar?.apply {
            title = "应用搜索设置"
            setDisplayHomeAsUpEnabled(true)
        }

        // 初始化数据
        initData()

        // 设置RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        adapter = AppSearchAdapter(appSearchList) { position, isChecked ->
            onAppEnabledChanged(position, isChecked)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 添加拖拽排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                
                // 更新列表
                val item = appSearchList.removeAt(fromPos)
                appSearchList.add(toPos, item)
                adapter.notifyItemMoved(fromPos, toPos)
                
                // 保存新的顺序
                saveOrder()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不需要实现
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // 保存按钮
        findViewById<View>(R.id.fabSave).setOnClickListener {
            saveSettings()
            Snackbar.make(recyclerView, "设置已保存", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun initData() {
        val configs = settingsManager.getAppSearchConfig()
        val order = settingsManager.getAppSearchOrder()
        val enabled = settingsManager.getEnabledAppSearches()

        appSearchList.clear()
        order.forEach { id ->
            configs[id]?.let { config ->
                appSearchList.add(AppSearchItem(
                    id = config.id,
                    name = config.name,
                    iconResId = config.iconResId,
                    isEnabled = enabled.contains(config.id)
                ))
            }
        }
    }

    private fun onAppEnabledChanged(position: Int, isChecked: Boolean) {
        appSearchList[position].isEnabled = isChecked
        saveSettings()
    }

    private fun saveOrder() {
        val newOrder = appSearchList.map { it.id }
        settingsManager.setAppSearchOrder(newOrder)
    }

    private fun saveSettings() {
        // 保存启用状态
        val enabledApps = appSearchList.filter { it.isEnabled }.map { it.id }.toSet()
        settingsManager.setEnabledAppSearches(enabledApps)
        
        // 保存顺序
        saveOrder()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    data class AppSearchItem(
        val id: String,
        val name: String,
        val iconResId: Int,
        var isEnabled: Boolean
    )

    private class AppSearchAdapter(
        private val items: List<AppSearchItem>,
        private val onSwitchChanged: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppSearchAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.appIcon)
            val appName: TextView = view.findViewById(R.id.appName)
            val switchEnabled: SwitchMaterial = view.findViewById(R.id.switchEnabled)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_search, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.appIcon.setImageResource(item.iconResId)
            holder.appName.text = item.name
            holder.switchEnabled.isChecked = item.isEnabled
            
            holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(position, isChecked)
            }
        }

        override fun getItemCount() = items.size
    }
} 