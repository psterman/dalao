package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptTemplate

class TaskTemplateAdapter(
    private val templates: List<PromptTemplate>,
    private val onItemClick: (PromptTemplate) -> Unit
) : RecyclerView.Adapter<TaskTemplateAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconText: TextView = itemView.findViewById(R.id.task_icon_text)
        val titleText: TextView = itemView.findViewById(R.id.task_title_text)
        val descriptionText: TextView = itemView.findViewById(R.id.task_description_text)
        
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(templates[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task_template, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val template = templates[position]
        
        holder.iconText.text = template.icon
        holder.titleText.text = template.intentName
        holder.descriptionText.text = template.description
    }

    override fun getItemCount(): Int = templates.size
} 