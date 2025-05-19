package com.example.aifloatingball.preference

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class SearchEnginePickerDialog(
    private val context: Context,
    private val currentValue: String,
    private val onEngineSelected: (String) -> Unit
) {
    private val settingsManager = SettingsManager.getInstance(context)
    private var selectedEngine = currentValue
    private var isAIMode = currentValue.startsWith("ai_")

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_search_engine_picker, null)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)

        // 设置标签页
        tabLayout.addTab(tabLayout.newTab().setText("普通搜索"))
        tabLayout.addTab(tabLayout.newTab().setText("AI搜索"))

        // 初始化选中的标签页
        tabLayout.selectTab(tabLayout.getTabAt(if (isAIMode) 1 else 0))

        // 设置适配器
        val adapter = SearchEngineAdapter(
            engines = getEngineList(isAIMode),
            selectedEngine = selectedEngine
        ) { engine ->
            selectedEngine = engine
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // 标签页切换监听
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                isAIMode = tab.position == 1
                adapter.updateEngines(getEngineList(isAIMode))
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // 显示对话框
        MaterialAlertDialogBuilder(context)
            .setTitle("选择搜索引擎")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                onEngineSelected(selectedEngine)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getEngineList(isAI: Boolean): List<EngineItem> {
        return if (isAI) {
            AISearchEngine.DEFAULT_AI_ENGINES.map { 
                EngineItem("ai_${it.name}", it.name, it.iconResId)
            }
        } else {
            SearchEngine.DEFAULT_ENGINES.map { 
                EngineItem(it.name, it.displayName, it.iconResId)
            }
        }
    }

    private data class EngineItem(
        val id: String,
        val name: String,
        val iconResId: Int
    )

    private inner class SearchEngineAdapter(
        private var engines: List<EngineItem>,
        private var selectedEngine: String,
        private val onItemSelected: (String) -> Unit
    ) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

        fun updateEngines(newEngines: List<EngineItem>) {
            engines = newEngines
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.engineIcon)
            val name: TextView = view.findViewById(R.id.engineName)
            val radioButton: View = view.findViewById(R.id.radioButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_engine_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val engine = engines[position]
            holder.icon.setImageResource(engine.iconResId)
            holder.name.text = engine.name
            holder.radioButton.isSelected = engine.id == selectedEngine

            holder.itemView.setOnClickListener {
                val oldSelected = selectedEngine
                selectedEngine = engine.id
                onItemSelected(engine.id)
                notifyItemChanged(engines.indexOfFirst { it.id == oldSelected })
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = engines.size
    }
} 