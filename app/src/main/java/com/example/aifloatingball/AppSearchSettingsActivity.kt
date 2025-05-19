package com.example.aifloatingball

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class AppSearchSettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppSearchAdapter
    private var appSearchList = mutableListOf<AppSearchItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_search_settings)

        // 设置标题栏
        supportActionBar?.apply {
            title = "应用搜索设置"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager.getInstance(this)
        recyclerView = findViewById(R.id.recyclerView)
        
        // 初始化应用搜索列表
        initAppSearchList()
        
        // 设置RecyclerView
        adapter = AppSearchAdapter(appSearchList) { position, isChecked ->
            onAppSearchChecked(position, isChecked)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 添加拖拽排序功能
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = source.adapterPosition
                val toPosition = target.adapterPosition
                Collections.swap(appSearchList, fromPosition, toPosition)
                adapter.notifyItemMoved(fromPosition, toPosition)
                saveAppSearchOrder()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun initAppSearchList() {
        val config = settingsManager.getAppSearchConfig()
        val enabledApps = settingsManager.getEnabledAppSearches()
        var order = settingsManager.getAppSearchOrder()

        // 确保所有配置的应用都在列表中
        val configuredApps = config.keys
        if (order.isEmpty() || !order.containsAll(configuredApps)) {
            order = configuredApps.toList()
        }

        // 根据顺序创建列表
        appSearchList.clear()
        order.forEach { id ->
            config[id]?.let { appConfig ->
                appSearchList.add(AppSearchItem(
                    id = appConfig.id,
                    name = appConfig.name,
                    iconResId = appConfig.iconResId,
                    isEnabled = enabledApps.contains(appConfig.id)
                ))
            }
        }

        // 如果列表为空，添加所有配置的应用
        if (appSearchList.isEmpty()) {
            config.values.forEach { appConfig ->
                appSearchList.add(AppSearchItem(
                    id = appConfig.id,
                    name = appConfig.name,
                    iconResId = appConfig.iconResId,
                    isEnabled = enabledApps.contains(appConfig.id)
                ))
            }
        }

        // 保存当前顺序
        saveAppSearchOrder()
    }

    private fun onAppSearchChecked(position: Int, isChecked: Boolean) {
        appSearchList[position].isEnabled = isChecked
        val enabledApps = appSearchList.filter { it.isEnabled }.map { it.id }.toSet()
        settingsManager.setEnabledAppSearches(enabledApps)
    }

    private fun saveAppSearchOrder() {
        val order = appSearchList.map { it.id }
        settingsManager.setAppSearchOrder(order)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
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

    class AppSearchAdapter(
        private val items: List<AppSearchItem>,
        private val onCheckedChange: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppSearchAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val name: TextView = view.findViewById(R.id.appName)
            val checkbox: CheckBox = view.findViewById(R.id.checkbox)
            val dragHandle: ImageView = view.findViewById(R.id.dragHandle)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_search, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageResource(item.iconResId)
            holder.name.text = item.name
            holder.checkbox.isChecked = item.isEnabled
            
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(position, isChecked)
            }
        }

        override fun getItemCount() = items.size
    }
} 