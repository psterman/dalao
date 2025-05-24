package com.example.aifloatingball.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.BaseSearchEngine
import com.example.aifloatingball.utils.IconLoader
import android.util.Log

/**
 * 搜索引擎列表适配器
 * 
 * @param context 上下文
 * @param engines 搜索引擎列表
 * @param enabledEngines 已启用的搜索引擎集合
 * @param onEngineToggled 搜索引擎切换回调
 */
class SearchEngineAdapter<T : BaseSearchEngine>(
    private val context: Context,
    private val engines: List<T>,
    private val enabledEngines: MutableSet<String>,
    private val onEngineToggled: (String, Boolean) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter<T>.ViewHolder>() {
    
    private val iconLoader = IconLoader(context)
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.engine_name)
        val iconImageView: ImageView = view.findViewById(R.id.engine_icon)
        val toggleSwitch: SwitchCompat = view.findViewById(R.id.engine_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = engines.size
    
    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        
        try {
            // 设置搜索引擎名称
            holder.nameTextView.text = engine.displayName
            
            // 设置图标
            holder.iconImageView.setImageResource(engine.iconResId)
            
            // 确保开关按钮可见
            holder.toggleSwitch.visibility = View.VISIBLE
            
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
        } catch (e: Exception) {
            Log.e("SearchEngineAdapter", "Error binding item $position: ${e.message}", e)
        }
    }

    fun updateEnabledEngines(newEnabledEngines: Set<String>) {
        enabledEngines.clear()
        enabledEngines.addAll(newEnabledEngines)
        notifyDataSetChanged()
    }
} 