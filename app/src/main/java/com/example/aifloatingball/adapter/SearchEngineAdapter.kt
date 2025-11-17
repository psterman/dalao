package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.SearchEngine
import com.google.android.material.switchmaterial.SwitchMaterial
import com.example.aifloatingball.model.BaseSearchEngine
import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.widget.SwitchCompat
import com.example.aifloatingball.utils.FaviconLoader

class SearchEngineAdapter(
    private var engines: MutableList<SearchEngine>,
    private val settingsManager: SettingsManager,
    private val onEngineClick: (SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.bind(engine)
    }

    override fun getItemCount(): Int = engines.size

    fun updateData(newEngines: List<SearchEngine>) {
        this.engines.clear()
        this.engines.addAll(newEngines)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.engine_name)
        private val urlTextView: TextView = itemView.findViewById(R.id.engine_url)
        private val engineSwitch: SwitchMaterial = itemView.findViewById(R.id.engine_switch)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        private val iconImageView: ImageView = itemView.findViewById(R.id.engine_icon)

        fun bind(engine: SearchEngine) {
            nameTextView.text = engine.displayName
            urlTextView.text = engine.searchUrl
            urlTextView.visibility = if (engine.isCustom) View.VISIBLE else View.GONE
            dragHandle.visibility = if (engine.isCustom) View.VISIBLE else View.INVISIBLE

            // Use FaviconLoader to load the icon
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(iconImageView, engine.url, R.drawable.ic_web_default)

            // Set switch state without triggering listener
            engineSwitch.setOnCheckedChangeListener(null)
            val enabledEngines = settingsManager.getEnabledSearchEngines()
            engineSwitch.isChecked = enabledEngines.contains(engine.name)
            
            engineSwitch.setOnCheckedChangeListener { _, isChecked ->
                val currentEnabled = settingsManager.getEnabledSearchEngines().toMutableSet()
                if (isChecked) {
                    currentEnabled.add(engine.name)
                } else {
                    currentEnabled.remove(engine.name)
                }
                settingsManager.saveEnabledSearchEngines(currentEnabled)
            }

            itemView.setOnClickListener {
                if(engine.isCustom) {
                    onEngineClick(engine)
                }
            }
        }
    }
}

/**
 * Generic Search Engine List Adapter for older implementations.
 */
class GenericSearchEngineAdapter<T : BaseSearchEngine>(
    private val context: Context,
    private var engines: MutableList<T>,
    private val enabledEngines: MutableSet<String>,
    private val onEngineToggled: (String, Boolean) -> Unit,
    private val onOrderChanged: ((List<T>) -> Unit)? = null
) : RecyclerView.Adapter<GenericSearchEngineAdapter<T>.ViewHolder>() {
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.engine_name)
        val iconImageView: ImageView = view.findViewById(R.id.engine_icon)
        val toggleSwitch: SwitchCompat = view.findViewById(R.id.engine_toggle)
        val dragHandle: ImageView? = view.findViewById(R.id.drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = engines.size
    
    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]

        // 设置搜索引擎名称
        holder.nameTextView.text = engine.displayName

        // 设置搜索引擎图标
        holder.iconImageView.setImageResource(engine.iconResId)

        // 显示开关按钮
        holder.toggleSwitch.visibility = View.VISIBLE
        holder.toggleSwitch.isChecked = enabledEngines.contains(engine.name)

        // 显示拖拽句柄
        holder.dragHandle?.visibility = View.VISIBLE

        // 设置开关监听器
        holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enabledEngines.add(engine.name)
            } else {
                enabledEngines.remove(engine.name)
            }
            onEngineToggled(engine.name, isChecked)
        }
        holder.itemView.setOnClickListener {
            holder.toggleSwitch.isChecked = !holder.toggleSwitch.isChecked
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateEngines(newEngines: List<T>) {
        this.engines.clear()
        this.engines.addAll(newEngines)
        notifyDataSetChanged()
    }

    /**
     * 移动项目位置（用于拖拽排序）
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                engines[i] = engines[i + 1].also { engines[i + 1] = engines[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                engines[i] = engines[i - 1].also { engines[i - 1] = engines[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onOrderChanged?.invoke(engines.toList())
    }

    /**
     * 获取当前引擎列表
     */
    fun getEngines(): List<T> = engines.toList()

    fun getEnabledEngines(): List<T> {
        return engines.filter { enabledEngines.contains(it.name) }
    }
} 