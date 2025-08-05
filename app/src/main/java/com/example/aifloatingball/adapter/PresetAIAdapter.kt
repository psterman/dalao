package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AddAIContactActivity
import com.example.aifloatingball.R
import com.google.android.material.button.MaterialButton

/**
 * 预设AI助手适配器
 */
class PresetAIAdapter(
    private val onAIClick: (AddAIContactActivity.PresetAI) -> Unit
) : RecyclerView.Adapter<PresetAIAdapter.AIViewHolder>() {

    private var presetAIs = listOf<AddAIContactActivity.PresetAI>()

    /**
     * 更新AI列表
     */
    fun updateAIs(ais: List<AddAIContactActivity.PresetAI>) {
        this.presetAIs = ais
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AIViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset_ai, parent, false)
        return AIViewHolder(view)
    }

    override fun onBindViewHolder(holder: AIViewHolder, position: Int) {
        holder.bind(presetAIs[position])
    }

    override fun getItemCount(): Int = presetAIs.size

    inner class AIViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val aiIcon: ImageView = itemView.findViewById(R.id.ai_icon)
        private val aiName: TextView = itemView.findViewById(R.id.ai_name)
        private val aiDescription: TextView = itemView.findViewById(R.id.ai_description)
        private val addButton: MaterialButton = itemView.findViewById(R.id.add_button)

        fun bind(presetAI: AddAIContactActivity.PresetAI) {
            aiName.text = presetAI.name
            aiDescription.text = presetAI.description

            addButton.setOnClickListener {
                onAIClick(presetAI)
            }

            // 整个卡片也可以点击
            itemView.setOnClickListener {
                onAIClick(presetAI)
            }
        }
    }
} 