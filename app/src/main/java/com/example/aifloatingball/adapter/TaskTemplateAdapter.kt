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
        val iconText: TextView?
        val titleText: TextView?
        val descriptionText: TextView?

        init {
            try {
                iconText = itemView.findViewById(R.id.task_icon_text)
                titleText = itemView.findViewById(R.id.task_title_text)
                descriptionText = itemView.findViewById(R.id.task_description_text)

                if (iconText == null || titleText == null || descriptionText == null) {
                    android.util.Log.e("TaskTemplateAdapter", "Some views not found: iconText=$iconText, titleText=$titleText, descriptionText=$descriptionText")
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskTemplateAdapter", "Error finding views", e)
                throw e
            }
        }
        
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
        return try {
            // 首先尝试使用原始布局
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task_template, parent, false)
            android.util.Log.d("TaskTemplateAdapter", "Successfully inflated original layout")
            TaskViewHolder(view)
        } catch (e: Exception) {
            android.util.Log.e("TaskTemplateAdapter", "Error with original layout, trying safe layout", e)
            try {
                // 如果原始布局失败，使用备用布局
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_task_template_safe, parent, false)
                android.util.Log.d("TaskTemplateAdapter", "Successfully inflated safe layout")
                TaskViewHolder(view)
            } catch (e2: Exception) {
                android.util.Log.e("TaskTemplateAdapter", "Error with safe layout too", e2)
                throw e2
            }
        }
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val template = templates[position]

        try {
            holder.iconText?.text = template.icon
            holder.titleText?.text = template.intentName
            holder.descriptionText?.text = template.description
        } catch (e: Exception) {
            android.util.Log.e("TaskTemplateAdapter", "Error binding data at position $position", e)
        }
    }

    override fun getItemCount(): Int = templates.size
} 