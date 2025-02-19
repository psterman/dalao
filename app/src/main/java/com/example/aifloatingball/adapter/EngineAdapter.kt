package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AIEngine
import com.example.aifloatingball.R

class EngineAdapter(
    private val engines: MutableList<AIEngine>,
    private var enabledEngines: Set<String>,
    private val selectionListener: OnEngineSelectionListener
) : RecyclerView.Adapter<EngineAdapter.ViewHolder>() {

    private val selectedEngines = enabledEngines.toMutableSet()

    interface OnEngineSelectionListener {
        fun onEngineSelectionChanged(selectedEngines: Set<String>)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.engine_icon)
        val name: TextView = view.findViewById(R.id.engine_name)
        val checkbox: CheckBox = view.findViewById(R.id.engine_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        
        holder.icon.setImageResource(engine.iconResId)
        holder.name.text = engine.name
        
        // 显示复选框
        holder.checkbox.visibility = View.VISIBLE
        
        // 设置复选框状态
        holder.checkbox.isChecked = selectedEngines.contains(engine.name)
        
        // 复选框点击事件
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedEngines.add(engine.name)
            } else {
                selectedEngines.remove(engine.name)
            }
            selectionListener.onEngineSelectionChanged(selectedEngines)
        }
    }

    override fun getItemCount() = engines.size

    fun getEngines(): List<AIEngine> = engines
} 