package com.example.aifloatingball.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptCategory
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * 分类筛选面板对话框
 */
class CategoryFilterPanelDialog(
    context: Context,
    private val mainCategory: PromptCategory,
    private val onCategorySelected: (PromptCategory) -> Unit
) {
    
    private val dialog: Dialog
    private lateinit var backButton: ImageButton
    private lateinit var titleText: TextView
    private lateinit var chipGroup: ChipGroup
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_category_filter_panel, null)
        
        dialog = Dialog(context, R.style.FullScreenDialog)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        initViews(view, context)
    }
    
    private fun initViews(view: View, context: Context) {
        backButton = view.findViewById(R.id.category_filter_panel_back)
        titleText = view.findViewById(R.id.category_filter_panel_title)
        chipGroup = view.findViewById(R.id.subcategory_chips_container)
        
        titleText.text = mainCategory.displayName
        
        backButton.setOnClickListener { dismiss() }
        
        setupSubcategories(context)
    }
    
    private fun setupSubcategories(context: Context) {
        val subcategories = getSubcategoriesForMainCategory(mainCategory)
        
        subcategories.forEach { subcategory ->
            val chip = Chip(context).apply {
                text = subcategory.displayName
                isClickable = true
                isCheckable = false
                setChipBackgroundColorResource(R.color.ai_assistant_background_light)
                setTextColor(ContextCompat.getColorStateList(context, R.color.ai_assistant_text_primary))
                chipStrokeColor = ContextCompat.getColorStateList(context, R.color.ai_assistant_primary)
                chipStrokeWidth = 1f
                setOnClickListener {
                    onCategorySelected(subcategory)
                    dismiss()
                }
            }
            chipGroup.addView(chip)
        }
    }
    
    private fun getSubcategoriesForMainCategory(category: PromptCategory): List<PromptCategory> {
        return when (category) {
            PromptCategory.PROFESSIONAL -> listOf(
                PromptCategory.BUSINESS,
                PromptCategory.CREATIVE,
                PromptCategory.ANALYSIS,
                PromptCategory.TRANSLATION
            )
            PromptCategory.SCENARIO -> listOf(
                PromptCategory.LIFE,
                PromptCategory.ENTERTAINMENT,
                PromptCategory.EDUCATION
            )
            PromptCategory.TECHNIQUE -> listOf(
                PromptCategory.CREATIVE,
                PromptCategory.ANALYSIS,
                PromptCategory.TRANSLATION
            )
            PromptCategory.HOT -> listOf(
                PromptCategory.MASTER,
                PromptCategory.BUSINESS,
                PromptCategory.CREATIVE,
                PromptCategory.ANALYSIS
            )
            else -> emptyList()
        }
    }
    
    fun show() {
        dialog.show()
    }
    
    fun dismiss() {
        dialog.dismiss()
    }
    
    fun isShowing(): Boolean {
        return dialog.isShowing
    }
}

