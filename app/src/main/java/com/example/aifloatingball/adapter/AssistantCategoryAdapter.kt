package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AssistantCategory
import com.example.aifloatingball.model.AssistantPrompt
import com.google.android.material.color.MaterialColors
import com.google.android.material.card.MaterialCardView

class AssistantCategoryAdapter(
    private val categories: List<AssistantCategory>,
    private val onCategoryClick: (AssistantCategory) -> Unit,
    private val onPromptClick: (AssistantPrompt) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items: MutableList<Any> = mutableListOf()
    private val expandedPositions = mutableSetOf<Int>()

    companion object {
        private const val TYPE_CATEGORY = 0
        private const val TYPE_PROMPT = 1
    }

    init {
        // Initially, just add categories to the list
        items.addAll(categories)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AssistantCategory -> TYPE_CATEGORY
            is AssistantPrompt -> TYPE_PROMPT
            else -> throw IllegalArgumentException("Invalid type of data at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_CATEGORY -> {
                val view = inflater.inflate(R.layout.item_assistant_category, parent, false)
                CategoryViewHolder(view)
            }
            TYPE_PROMPT -> {
                val view = inflater.inflate(R.layout.item_assistant_prompt, parent, false)
                PromptViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CategoryViewHolder -> {
                val category = items[position] as AssistantCategory
                holder.bind(category)
                holder.itemView.setOnClickListener {
                    onCategoryClick(category)
                }
            }
            is PromptViewHolder -> {
                val prompt = items[position] as AssistantPrompt
                holder.bind(prompt)
                holder.itemView.setOnClickListener {
                    onPromptClick(prompt)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.category_name)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.category_card)

        fun bind(category: AssistantCategory) {
            nameTextView.text = category.name
            // We are no longer handling expansion visuals here
        }
    }

    class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.prompt_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.prompt_description)

        fun bind(prompt: AssistantPrompt) {
            nameTextView.text = prompt.name
            descriptionTextView.text = prompt.description
        }
    }
} 