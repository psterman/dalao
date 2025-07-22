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
import com.example.aifloatingball.utils.FaviconLoader

/**
 * 分类搜索引擎适配器 - 用于显示单个分类下的搜索引擎列表
 */
class CategorySearchEngineAdapter(
    private var searchEngines: List<SearchEngine>,
    private val enabledEngines: MutableSet<String>,
    private val onEngineToggle: (SearchEngine, Boolean) -> Unit
) : RecyclerView.Adapter<CategorySearchEngineAdapter.SearchEngineViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchEngineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_engine_with_switch, parent, false)
        return SearchEngineViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchEngineViewHolder, position: Int) {
        val engine = searchEngines[position]
        val isEnabled = enabledEngines.contains(engine.name)
        holder.bind(engine, isEnabled) { enabled ->
            onEngineToggle(engine, enabled)
        }
    }

    override fun getItemCount(): Int = searchEngines.size

    fun updateEngines(newEngines: List<SearchEngine>) {
        searchEngines = newEngines
        notifyDataSetChanged()
    }

    inner class SearchEngineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val engineIcon: ImageView = itemView.findViewById(R.id.engine_icon)
            ?: throw IllegalStateException("engine_icon not found in item_search_engine_with_switch.xml")
        private val engineName: TextView = itemView.findViewById(R.id.engine_name)
            ?: throw IllegalStateException("engine_name not found in item_search_engine_with_switch.xml")
        private val engineSwitch: Switch = itemView.findViewById(R.id.engine_switch)
            ?: throw IllegalStateException("engine_switch not found in item_search_engine_with_switch.xml")

        fun bind(engine: SearchEngine, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
            // 使用FaviconLoader加载图标
            FaviconLoader.loadIcon(engineIcon, engine.url, engine.iconResId)

            // 显示中文名称
            engineName.text = engine.displayName

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
