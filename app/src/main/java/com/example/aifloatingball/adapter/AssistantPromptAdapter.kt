package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AssistantPrompt

class AssistantPromptAdapter(
    private val prompts: List<AssistantPrompt>,
    private val onPromptClick: (AssistantPrompt) -> Unit
) : RecyclerView.Adapter<AssistantPromptAdapter.PromptViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assistant_prompt, parent, false)
        return PromptViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        val prompt = prompts[position]
        holder.bind(prompt)
        holder.itemView.setOnClickListener { onPromptClick(prompt) }
    }

    override fun getItemCount(): Int = prompts.size

    class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.prompt_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.prompt_description)

        fun bind(prompt: AssistantPrompt) {
            nameTextView.text = prompt.name
            descriptionTextView.text = prompt.description
        }
    }
} 