package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptCategory

/**
 * 任务场景分类适配器（左侧列）
 */
class TaskScenarioAdapter(
    private val scenarios: List<PromptCategory>,
    private val onScenarioClick: (PromptCategory) -> Unit
) : RecyclerView.Adapter<TaskScenarioAdapter.ScenarioViewHolder>() {

    private var selectedScenario: PromptCategory? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScenarioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ScenarioViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScenarioViewHolder, position: Int) {
        val scenario = scenarios[position]
        holder.bind(scenario, scenario == selectedScenario)
    }

    override fun getItemCount(): Int = scenarios.size

    fun setSelectedScenario(scenario: PromptCategory?) {
        val oldSelected = selectedScenario
        selectedScenario = scenario
        oldSelected?.let { notifyItemChanged(scenarios.indexOf(it)) }
        scenario?.let { notifyItemChanged(scenarios.indexOf(it)) }
    }

    inner class ScenarioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(scenario: PromptCategory, isSelected: Boolean) {
            textView.text = scenario.displayName
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
                setSelectedScenario(scenario)
                onScenarioClick(scenario)
            }
        }
    }
}

