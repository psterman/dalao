package com.example.aifloatingball

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.IconLoader
import java.util.Collections

class SearchEngineAdapter<T : SearchEngine>(
    private val context: Context,
    private val engines: List<T>,
    private val enabledEngines: MutableSet<String>,
    private val onEngineToggled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter<T>.ViewHolder>() {
    
    private val iconLoader = IconLoader(context)
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.engine_name)
        val iconImageView: ImageView = view.findViewById(R.id.engine_icon)
        val toggleSwitch: Switch = view.findViewById(R.id.engine_toggle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }
    
    override fun getItemCount(): Int = engines.size
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        
        // 设置搜索引擎名称
        holder.nameTextView.text = engine.name
        
        // 设置搜索引擎图标
        holder.iconImageView.setImageResource(engine.iconResId)
        
        // 从网络加载图标
        val url = if (engine is AISearchEngine) {
            engine.url
        } else {
            if (engine.url.contains("?")) {
                engine.url.substring(0, engine.url.indexOf("?"))
            } else {
                engine.url
            }
        }
        
        // 加载网站图标
        iconLoader.loadIcon(url, holder.iconImageView, engine.iconResId)
        
        // 设置切换状态
        holder.toggleSwitch.isChecked = enabledEngines.contains(engine.name)
        
        // 设置点击事件
        holder.toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enabledEngines.add(engine.name)
            } else {
                enabledEngines.remove(engine.name)
            }
            onEngineToggled(engine.name, isChecked)
        }
        
        // 允许点击项目来切换开关
        holder.itemView.setOnClickListener {
            holder.toggleSwitch.isChecked = !holder.toggleSwitch.isChecked
        }
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(engines.toMutableList(), i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(engines.toMutableList(), i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getEngines(): List<T> = engines

    fun updateEnabledEngines(newEnabledEngines: Set<String>) {
        enabledEngines.clear()
        enabledEngines.addAll(newEnabledEngines)
        notifyDataSetChanged()
    }
} 