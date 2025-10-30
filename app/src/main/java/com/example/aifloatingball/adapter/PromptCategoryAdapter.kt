package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptCategory
import com.google.android.material.chip.Chip
import java.util.*

/**
 * Prompt分类导航适配器
 * @param isMainCategory 是否为父标签（主分类）
 */
class PromptCategoryAdapter(
    private val categories: List<PromptCategory>,
    private val onCategoryClick: (PromptCategory) -> Unit,
    private val isMainCategory: Boolean = false  // 标记是否为主分类（父标签）
) : RecyclerView.Adapter<PromptCategoryAdapter.CategoryViewHolder>() {

    private var selectedCategory: PromptCategory? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_category_chip, parent, false)
        return CategoryViewHolder(view as Chip, isMainCategory)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val chip: Chip, private val isLarge: Boolean) : RecyclerView.ViewHolder(chip) {
        
        fun bind(category: PromptCategory) {
            // 根据isMainCategory调整大小，去掉图标
            if (isLarge) {
                // 父标签：更大的字号和padding，不使用图标
                chip.textSize = 15f
                chip.minHeight = 44  // 44dp高度，更紧凑
                chip.text = category.displayName  // 去掉图标
                chip.chipStartPadding = 8f  // 减少左右padding
                chip.chipEndPadding = 8f
            } else {
                // 子标签：较小的字号和padding，不使用图标
                chip.textSize = 13f
                chip.minHeight = 32  // 32dp高度，更紧凑
                chip.text = category.displayName  // 去掉图标
                chip.chipStartPadding = 6f  // 减少左右padding
                chip.chipEndPadding = 6f
            }
            
            chip.isChecked = selectedCategory == category
            chip.isCheckable = true
            
            chip.setOnClickListener {
                selectedCategory = if (selectedCategory == category) null else category
                notifyDataSetChanged()
                onCategoryClick(category)
            }
        }
    }
    
    fun setSelectedCategory(category: PromptCategory?) {
        selectedCategory = category
        notifyDataSetChanged()
    }
}

