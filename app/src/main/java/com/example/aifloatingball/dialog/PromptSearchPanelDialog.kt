package com.example.aifloatingball.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.PromptCategoryAdapter
import com.example.aifloatingball.data.PromptCommunityData
import com.example.aifloatingball.model.PromptCategory
import com.google.android.material.chip.Chip

/**
 * Prompt搜索面板对话框
 */
class PromptSearchPanelDialog(
    context: Context,
    private val onSearch: (String) -> Unit
) {
    
    private val dialog: Dialog
    private lateinit var backButton: ImageButton
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var recentSearchContainer: LinearLayout
    private lateinit var hotSearchContainer: LinearLayout
    private lateinit var categoryRecyclerView: RecyclerView
    
    private val recentSearches = mutableListOf<String>()
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_prompt_search_panel, null)
        
        dialog = Dialog(context, R.style.FullScreenDialog)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        
        initViews(view, context)
        setupRecentSearches(context)
        setupHotSearches(context)
        setupCategories(context)
        setupSearch()
    }
    
    private fun initViews(view: View, context: Context) {
        backButton = view.findViewById(R.id.search_panel_back)
        searchInput = view.findViewById(R.id.search_panel_input)
        searchButton = view.findViewById(R.id.search_panel_button)
        recentSearchContainer = view.findViewById(R.id.recent_search_chips_container)
        hotSearchContainer = view.findViewById(R.id.hot_search_chips_container)
        categoryRecyclerView = view.findViewById(R.id.category_search_recycler_view)
        
        backButton.setOnClickListener { dismiss() }
    }
    
    private fun setupRecentSearches(context: Context) {
        // 添加示例近期搜索
        val recentKeys = listOf("文案写作", "代码审查", "数据分析")
        
        recentKeys.forEach { keyword ->
            val chip = Chip(context).apply {
                text = keyword
                isClickable = true
                setChipBackgroundColorResource(R.color.ai_assistant_background_light)
                setTextColor(ContextCompat.getColorStateList(context, R.color.ai_assistant_text_primary))
                setOnClickListener {
                    performSearch(keyword)
                }
            }
            recentSearchContainer.addView(chip)
        }
    }
    
    private fun setupHotSearches(context: Context) {
        val hotKeywords = PromptCommunityData.getHotKeywords()
        
        hotKeywords.forEach { keyword ->
            val chip = Chip(context).apply {
                text = keyword
                isClickable = true
                setChipBackgroundColorResource(R.color.ai_assistant_primary_container_light)
                setTextColor(ContextCompat.getColorStateList(context, R.color.ai_assistant_primary))
                setOnClickListener {
                    performSearch(keyword)
                }
            }
            hotSearchContainer.addView(chip)
        }
    }
    
    private fun setupCategories(context: Context) {
        val categories = PromptCommunityData.getAllCategories()
        val adapter = com.example.aifloatingball.adapter.PromptCategoryAdapter(
            categories,
            onCategoryClick = { category ->
                // 点击分类，搜索该分类的Prompt
                performSearch(category.displayName)
            },
            isMainCategory = false
        )
        
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        categoryRecyclerView.layoutManager = layoutManager
        categoryRecyclerView.adapter = adapter
    }
    
    private fun setupSearch() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
        
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }
    
    private fun performSearch(query: String) {
        // 添加到搜索历史
        if (query !in recentSearches) {
            recentSearches.add(0, query)
            if (recentSearches.size > 10) {
                recentSearches.removeAt(recentSearches.size - 1)
            }
        }
        
        // 隐藏键盘
        val imm = dialog.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        
        // 执行搜索回调
        onSearch(query)
        dismiss()
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

