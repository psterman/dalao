package com.example.aifloatingball.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.aifloatingball.ChatActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.adapter.PromptCommunityAdapter
import com.example.aifloatingball.adapter.TaskScenarioAdapter
import com.example.aifloatingball.data.PromptCommunityData
import com.example.aifloatingball.dialog.PromptExtractDialog
import com.example.aifloatingball.model.PromptCategory
import com.example.aifloatingball.model.PromptCommunityItem

/**
 * 任务Fragment - 两列布局版本
 * 左侧：Prompt任务场景分类
 * 右侧：对应场景分类中的Prompt列表
 */
class TaskFragmentTwoColumn : AIAssistantCenterFragment() {
    
    // 左侧场景分类
    private lateinit var scenarioRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var scenarioAdapter: TaskScenarioAdapter
    
    // 右侧Prompt列表
    private lateinit var promptRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var promptAdapter: PromptCommunityAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var selectedScenarioTitle: TextView
    private lateinit var promptEmptyState: LinearLayout
    
    // 操作按钮
    private lateinit var promptFavoriteButton: android.widget.ImageButton
    private lateinit var promptEditButton: android.widget.ImageButton
    private lateinit var promptAddCategoryButton: android.widget.ImageButton
    private lateinit var promptSearchButton: android.widget.ImageButton
    private lateinit var promptFilterButton: android.widget.ImageButton
    
    // 当前选中的场景
    private var currentScenario: PromptCategory? = null
    private var isFilterMode: Boolean = false
    private var originalPrompts: List<PromptCommunityItem> = emptyList() // 保存原始数据，用于搜索和过滤
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_task_two_column_fragment
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupScenarioRecyclerView()
        setupPromptRecyclerView()
        setupButtons()
        
        // 默认选中第一个场景
        val firstScenario = getAvailableScenarios().firstOrNull()
        firstScenario?.let { selectScenario(it) }
    }
    
    private fun setupViews(view: View) {
        // 左侧场景列表
        scenarioRecyclerView = view.findViewById(R.id.task_scenario_recycler_view)
        
        // 右侧Prompt列表
        promptRecyclerView = view.findViewById(R.id.prompt_content_recycler_view)
        swipeRefreshLayout = view.findViewById(R.id.prompt_swipe_refresh)
        selectedScenarioTitle = view.findViewById(R.id.selected_scenario_title)
        promptEmptyState = view.findViewById(R.id.prompt_empty_state)
        
        // 操作按钮
        promptFavoriteButton = view.findViewById(R.id.prompt_favorite_button)
        promptEditButton = view.findViewById(R.id.prompt_edit_button)
        promptAddCategoryButton = view.findViewById(R.id.prompt_add_category_button)
        promptSearchButton = view.findViewById(R.id.prompt_search_button)
        promptFilterButton = view.findViewById(R.id.prompt_filter_button)
    }
    
    private fun setupScenarioRecyclerView() {
        val scenarios = getAvailableScenarios()
        
        scenarioAdapter = TaskScenarioAdapter(scenarios) { scenario ->
            selectScenario(scenario)
        }
        
        scenarioRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        scenarioRecyclerView.adapter = scenarioAdapter
    }
    
    private fun setupPromptRecyclerView() {
        promptAdapter = PromptCommunityAdapter(
            emptyList(),
            onItemClick = { prompt -> onPromptItemClick(prompt) },
            onLikeClick = { prompt -> {} },
            onCollectClick = { prompt -> {} },
            onCommentClick = { prompt -> {} },
            onShareClick = { prompt -> {} }
        )
        
        promptRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        promptRecyclerView.adapter = promptAdapter
        
        // 下拉刷新
        swipeRefreshLayout.setColorSchemeResources(R.color.ai_assistant_primary)
        swipeRefreshLayout.setOnRefreshListener {
            currentScenario?.let { loadPromptsForScenario(it) }
            swipeRefreshLayout.isRefreshing = false
        }
    }
    
    private fun setupButtons() {
        // 收藏按钮
        promptFavoriteButton.setOnClickListener {
            showFavoriteDialog()
        }
        
        // 编辑按钮
        promptEditButton.setOnClickListener {
            showEditDialog()
        }
        
        // 新增分类按钮
        promptAddCategoryButton.setOnClickListener {
            showAddCategoryDialog()
        }
        
        // 搜索按钮
        promptSearchButton.setOnClickListener {
            showSearchDialog()
        }
        
        // 过滤按钮
        promptFilterButton.setOnClickListener {
            toggleFilterMode()
        }
    }
    
    /**
     * 显示收藏对话框
     */
    private fun showFavoriteDialog() {
        currentScenario?.let { scenario ->
            android.widget.Toast.makeText(requireContext(), "收藏功能：${scenario.displayName}", android.widget.Toast.LENGTH_SHORT).show()
            // TODO: 实现收藏功能，将当前场景下的所有Prompt添加到收藏列表
        } ?: run {
            android.widget.Toast.makeText(requireContext(), "请先选择一个场景", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示新增分类对话框
     */
    private fun showAddCategoryDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "输入新分类名称"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("新增分类")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val categoryName = input.text.toString()
                if (categoryName.isNotEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "新增分类：$categoryName", android.widget.Toast.LENGTH_SHORT).show()
                    // TODO: 实现新增分类功能，将新分类添加到场景列表中
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 切换过滤模式
     */
    private fun toggleFilterMode() {
        isFilterMode = !isFilterMode
        
        if (isFilterMode) {
            promptFilterButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ai_assistant_primary))
            android.widget.Toast.makeText(requireContext(), "已启用过滤模式", android.widget.Toast.LENGTH_SHORT).show()
            // TODO: 实现过滤功能，显示过滤选项（如：按热度、按时间、按标签等）
            showFilterOptionsDialog()
        } else {
            promptFilterButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ai_assistant_text_secondary))
            // 恢复原场景显示
            currentScenario?.let { loadPromptsForScenario(it) }
        }
    }
    
    /**
     * 显示过滤选项对话框
     */
    private fun showFilterOptionsDialog() {
        val filterOptions = arrayOf("按热度排序", "按时间排序", "按标签筛选", "仅显示收藏")
        var selectedIndex = -1
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择过滤方式")
            .setSingleChoiceItems(filterOptions, -1) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("确定") { _, _ ->
                when (selectedIndex) {
                    0 -> {
                        // 按热度排序
                        val sorted = originalPrompts.sortedByDescending { it.likeCount }
                        promptAdapter.updateData(sorted)
                        android.widget.Toast.makeText(requireContext(), "已按热度排序", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // 按时间排序
                        val sorted = originalPrompts.sortedByDescending { it.publishTime }
                        promptAdapter.updateData(sorted)
                        android.widget.Toast.makeText(requireContext(), "已按时间排序", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // 按标签筛选
                        android.widget.Toast.makeText(requireContext(), "标签筛选功能开发中", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        // 仅显示收藏
                        val favorites = originalPrompts.filter { it.isCollected }
                        promptAdapter.updateData(favorites)
                        android.widget.Toast.makeText(requireContext(), "已筛选收藏", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消") { _, _ ->
                toggleFilterMode() // 取消时关闭过滤模式
            }
            .setOnDismissListener {
                if (selectedIndex == -1) {
                    toggleFilterMode() // 未选择时关闭过滤模式
                }
            }
            .show()
    }
    
    /**
     * 获取可用的场景列表（包含主分类和子分类）
     */
    private fun getAvailableScenarios(): List<PromptCategory> {
        return PromptCategory.values().filter { 
            it != PromptCategory.UNKNOWN 
        }
    }
    
    /**
     * 选择场景
     */
    private fun selectScenario(scenario: PromptCategory) {
        currentScenario = scenario
        scenarioAdapter.setSelectedScenario(scenario)
        selectedScenarioTitle.text = scenario.displayName
        loadPromptsForScenario(scenario)
    }
    
    /**
     * 加载指定场景的Prompt列表
     */
    private fun loadPromptsForScenario(scenario: PromptCategory) {
        // 如果是主分类，获取其所有子分类的prompts
        val prompts = if (scenario.isMainCategory) {
            val subcategories = getSubcategoriesForMainCategory(scenario)
            PromptCommunityData.getSamplePrompts().filter { prompt ->
                prompt.category == scenario || prompt.category in subcategories
            }
        } else {
            PromptCommunityData.getPromptsByCategory(scenario)
        }
        
        // 保存原始数据
        originalPrompts = prompts
        
        if (prompts.isEmpty()) {
            promptEmptyState.visibility = View.VISIBLE
            promptRecyclerView.visibility = View.GONE
        } else {
            promptEmptyState.visibility = View.GONE
            promptRecyclerView.visibility = View.VISIBLE
            promptAdapter.updateData(prompts)
        }
    }
    
    /**
     * 获取主分类下的所有子分类
     */
    private fun getSubcategoriesForMainCategory(mainCategory: PromptCategory): List<PromptCategory> {
        return when (mainCategory) {
            PromptCategory.FUNCTIONAL -> listOf(
                PromptCategory.CREATIVE_WRITING,
                PromptCategory.DATA_ANALYSIS,
                PromptCategory.TRANSLATION_CONVERSION,
                PromptCategory.CODE_ASSISTANT,
                PromptCategory.SEO_MARKETING,
                PromptCategory.PRODUCT_DOCS,
                PromptCategory.CUSTOMER_SUPPORT,
                PromptCategory.IMAGE_GENERATION,
                PromptCategory.AUDIO_VIDEO,
                PromptCategory.LEGAL_ADVICE,
                PromptCategory.MEDICAL_HEALTH,
                PromptCategory.FINANCE_ANALYSIS
            )
            PromptCategory.HIGH_FREQUENCY -> listOf(
                PromptCategory.WORKPLACE_OFFICE,
                PromptCategory.EDUCATION_STUDY,
                PromptCategory.LIFE_SERVICE,
                PromptCategory.SOCIAL_MEDIA,
                PromptCategory.ECOMMERCE,
                PromptCategory.RESUME_INTERVIEW
            )
            PromptCategory.POPULAR -> listOf(
                PromptCategory.TOP10_WEEK,
                PromptCategory.EXPERT_PICKS
            )
            PromptCategory.MY_CONTENT -> listOf(
                PromptCategory.MY_COLLECTIONS,
                PromptCategory.MY_UPLOADS
            )
            else -> emptyList()
        }
    }
    
    /**
     * Prompt项点击 - 显示提取对话框
     */
    private fun onPromptItemClick(prompt: PromptCommunityItem) {
        PromptExtractDialog(
            requireContext(),
            prompt,
            onUseForChat = { extractedPrompt -> usePromptForChat(extractedPrompt) },
            onUseForSearch = { extractedPrompt -> usePromptForSearch(extractedPrompt) }
        ).show()
    }
    
    /**
     * 使用Prompt进行AI对话
     */
    private fun usePromptForChat(prompt: String) {
        try {
            // 使用SimpleModeActivity的聊天功能
            val intent = Intent(requireContext(), SimpleModeActivity::class.java).apply {
                putExtra("initial_message", prompt)
                putExtra("source", "task_prompt")
                putExtra("auto_switch_to_chat", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("TaskFragmentTwoColumn", "启动AI对话失败", e)
            // 如果SimpleModeActivity不存在，尝试使用ChatActivity
            try {
                val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                    putExtra("prefill_message", prompt)
                    putExtra("source", "task_prompt")
                }
                startActivity(intent)
            } catch (e2: Exception) {
                android.widget.Toast.makeText(requireContext(), "启动AI对话失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 使用Prompt进行搜索
     */
    private fun usePromptForSearch(prompt: String) {
        try {
            // 启动搜索页面
            val intent = Intent(requireContext(), SimpleModeActivity::class.java).apply {
                putExtra("search_query", prompt)
                putExtra("auto_switch_to_search", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("TaskFragmentTwoColumn", "启动搜索失败", e)
            android.widget.Toast.makeText(requireContext(), "启动搜索失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示搜索对话框
     */
    private fun showSearchDialog() {
        val searchInput = android.widget.EditText(requireContext()).apply {
            hint = "搜索提示词关键词"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("搜索提示词")
            .setView(searchInput)
            .setPositiveButton("搜索") { _, _ ->
                val query = searchInput.text.toString()
                if (query.isNotEmpty()) {
                    searchPrompts(query)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 搜索Prompt（在当前场景中搜索）
     */
    private fun searchPrompts(query: String) {
        val lowerQuery = query.lowercase()
        val results = originalPrompts.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery) ||
            it.content.lowercase().contains(lowerQuery) ||
            it.tags.any { tag -> tag.lowercase().contains(lowerQuery) }
        }
        promptAdapter.updateData(results)
        
        if (results.isEmpty()) {
            promptEmptyState.visibility = View.VISIBLE
            promptRecyclerView.visibility = View.GONE
        } else {
            promptEmptyState.visibility = View.GONE
            promptRecyclerView.visibility = View.VISIBLE
        }
    }
    
    /**
     * 显示编辑对话框（编辑当前场景的提示词）
     */
    private fun showEditDialog() {
        currentScenario?.let { scenario ->
            android.widget.Toast.makeText(requireContext(), "编辑功能：${scenario.displayName}", android.widget.Toast.LENGTH_SHORT).show()
            // 这里可以打开一个编辑器，让用户编辑该场景下的所有Prompt
        } ?: run {
            android.widget.Toast.makeText(requireContext(), "请先选择一个场景", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

