package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineCategory

/**
 * 分组显示搜索引擎的适配器
 * 每个分类标题下显示该分类的搜索引擎列表
 */
class GroupedSearchEngineAdapter(
    private val enabledEngines: MutableSet<String>,
    private val onEngineToggled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_CATEGORY_HEADER = 0
        private const val TYPE_SEARCH_ENGINE = 1
    }

    private val items = mutableListOf<Any>()

    init {
        loadGroupedData()
    }

    private fun loadGroupedData() {
        items.clear()
        
        // 按分类组织搜索引擎
        SearchEngineCategory.values().forEach { category ->
            val enginesInCategory = SearchEngine.DEFAULT_ENGINES.filter { it.category == category }
            if (enginesInCategory.isNotEmpty()) {
                // 添加分类标题
                items.add(category)
                // 添加该分类下的搜索引擎
                items.addAll(enginesInCategory)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SearchEngineCategory -> TYPE_CATEGORY_HEADER
            is SearchEngine -> TYPE_SEARCH_ENGINE
            else -> TYPE_SEARCH_ENGINE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CATEGORY_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_category_header, parent, false)
                CategoryHeaderViewHolder(view)
            }
            TYPE_SEARCH_ENGINE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_engine_with_switch, parent, false)
                SearchEngineViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryHeaderViewHolder -> {
                val category = items[position] as SearchEngineCategory
                holder.bind(category)
            }
            is SearchEngineViewHolder -> {
                val engine = items[position] as SearchEngine
                holder.bind(engine, enabledEngines.contains(engine.name)) { isEnabled ->
                    onEngineToggled(engine.name, isEnabled)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // 分类标题ViewHolder
    inner class CategoryHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryTitle: TextView = itemView.findViewById(R.id.category_title)

        fun bind(category: SearchEngineCategory) {
            categoryTitle.text = category.displayName
        }
    }

    // 搜索引擎ViewHolder
    inner class SearchEngineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val engineIcon: ImageView = itemView.findViewById(R.id.engine_icon)
        private val engineName: TextView = itemView.findViewById(R.id.engine_name)
        private val engineSwitch: Switch = itemView.findViewById(R.id.engine_switch)

        fun bind(engine: SearchEngine, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
            engineIcon.setImageResource(engine.iconResId)
            engineName.text = engine.name
            
            // 设置开关状态，先移除监听器避免触发回调
            engineSwitch.setOnCheckedChangeListener(null)
            engineSwitch.isChecked = isEnabled
            
            // 设置开关监听器
            engineSwitch.setOnCheckedChangeListener { _, checked ->
                onToggle(checked)
            }
            
            // 整个项目点击也可以切换开关
            itemView.setOnClickListener {
                engineSwitch.isChecked = !engineSwitch.isChecked
            }
        }
    }
}
