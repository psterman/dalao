package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptCategory
import com.example.aifloatingball.model.ScenarioItem

/**
 * 任务场景分类适配器（左侧列）
 */
class TaskScenarioAdapter(
    private val scenarios: MutableList<ScenarioItem>,
    private val onScenarioClick: (ScenarioItem) -> Unit
) : RecyclerView.Adapter<TaskScenarioAdapter.ScenarioViewHolder>() {

    private var selectedIndex: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScenarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ScenarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScenarioViewHolder, position: Int) {
        val scenario = scenarios[position]
        holder.bind(scenario, position == selectedIndex)
    }

    override fun getItemCount(): Int = scenarios.size

    fun setSelectedScenario(item: ScenarioItem?) {
        val old = selectedIndex
        selectedIndex = item?.let { scenarios.indexOf(it) } ?: -1
        if (old >= 0) notifyItemChanged(old)
        if (selectedIndex >= 0) notifyItemChanged(selectedIndex)
    }

    fun addScenario(name: String) {
        scenarios.add(ScenarioItem(name, null))
        notifyItemInserted(scenarios.lastIndex)
    }

    fun moveScenario(from: Int, to: Int) {
        if (from !in scenarios.indices || to !in scenarios.indices) return
        val item = scenarios.removeAt(from)
        scenarios.add(to, item)
        notifyItemMoved(from, to)
        if (selectedIndex == from) selectedIndex = to
    }

    fun renameScenario(index: Int, newName: String) {
        if (index !in scenarios.indices) return
        scenarios[index].name = newName
        notifyItemChanged(index)
    }

    fun deleteScenario(index: Int) {
        if (index !in scenarios.indices) return
        scenarios.removeAt(index)
        notifyItemRemoved(index)
        if (selectedIndex == index) selectedIndex = -1
    }

    inner class ScenarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(item: ScenarioItem, isSelected: Boolean) {
            textView.text = item.name
            textView.textSize = 14f
            
            // 设置选中状态样式
            if (isSelected) {
                textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.ai_assistant_primary))
                textView.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.ai_assistant_primary_container_light))
                textView.setPadding(16, 12, 16, 12)
            } else {
                textView.setTextColor(ContextCompat.getColor(itemView.context, R.color.ai_assistant_text_primary))
                textView.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.transparent))
                textView.setPadding(16, 12, 16, 12)
            }
            
            itemView.setOnClickListener {
                setSelectedScenario(item)
                onScenarioClick(item)
            }
        }
    }
}

