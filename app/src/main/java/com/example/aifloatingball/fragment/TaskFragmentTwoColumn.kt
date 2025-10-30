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
import com.example.aifloatingball.model.ScenarioItem

/**
 * 任务Fragment - 两列布局版本
 * 左侧：Prompt任务场景分类
 * 右侧：对应场景分类中的Prompt列表
 */
class TaskFragmentTwoColumn : AIAssistantCenterFragment() {
    
    // 左侧场景分类
    private lateinit var scenarioRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var scenarioAdapter: TaskScenarioAdapter
    private val scenarioItems: MutableList<ScenarioItem> = mutableListOf()
    
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
    private var currentFilterIndex: Int = -1 // -1 表示未过滤
    private var originalPrompts: List<PromptCommunityItem> = emptyList() // 保存原始数据，用于搜索和过滤
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_task_two_column_fragment
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupScenarioRecyclerView()
        setupPromptRecyclerView()
        setupButtons()
        
        // 默认选中第一个场景
        val firstItem = scenarioItems.firstOrNull()
        firstItem?.let { selectScenario(it) }
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
        val categories = getAvailableScenarios()
        scenarioItems.clear()
        scenarioItems.addAll(categories.map { ScenarioItem(it.displayName, it) })
        
        scenarioAdapter = TaskScenarioAdapter(scenarioItems) { item ->
            selectScenario(item)
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
        promptAddCategoryButton.setOnClickListener { showAddCategoryDialog() }
        
        // 搜索按钮
        promptSearchButton.setOnClickListener {
            showSearchDialog()
        }
        
        // 过滤按钮
        promptFilterButton.setOnClickListener { cycleFilterMode() }
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
                    scenarioAdapter.addScenario(categoryName)
                    // 选中新建分组
                    val newItem = ScenarioItem(categoryName, null)
                    scenarioItems.add(newItem)
                    scenarioRecyclerView.smoothScrollToPosition(scenarioItems.lastIndex)
                    selectScenario(newItem)
                    android.widget.Toast.makeText(requireContext(), "已新增分类：$categoryName", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 切换过滤模式
     */
    private fun cycleFilterMode() {
        // 顺序：未过滤 -> 热度 -> 时间 -> 收藏 -> 未过滤
        currentFilterIndex = (currentFilterIndex + 1) % 4
        when (currentFilterIndex) {
            0 -> { // 热度
                promptFilterButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ai_assistant_primary))
                val sorted = originalPrompts.sortedByDescending { it.likeCount }
                promptAdapter.updateData(sorted)
                android.widget.Toast.makeText(requireContext(), "按热度排序", android.widget.Toast.LENGTH_SHORT).show()
            }
            1 -> { // 时间
                promptFilterButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ai_assistant_primary))
                val sorted = originalPrompts.sortedByDescending { it.publishTime }
                promptAdapter.updateData(sorted)
                android.widget.Toast.makeText(requireContext(), "按时间排序", android.widget.Toast.LENGTH_SHORT).show()
            }
            2 -> { // 收藏
                promptFilterButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ai_assistant_primary))
                val favorites = originalPrompts.filter { it.isCollected }
                promptAdapter.updateData(favorites)
                android.widget.Toast.makeText(requireContext(), "仅显示收藏", android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> { // 清除
                promptFilterButton.setColorFilter(ContextCompat.getColor(requireContext(), R.color.ai_assistant_text_secondary))
                promptAdapter.updateData(originalPrompts)
                android.widget.Toast.makeText(requireContext(), "清除过滤", android.widget.Toast.LENGTH_SHORT).show()
                currentFilterIndex = -1
            }
        }
    }
    
    // 过滤改为即时切换（cycleFilterMode），此处不再使用弹窗
    
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
    private fun selectScenario(scenario: ScenarioItem) {
        scenarioAdapter.setSelectedScenario(scenario)
        selectedScenarioTitle.text = scenario.name
        currentScenario = scenario.category
        currentScenario?.let { loadPromptsForScenario(it) } ?: run {
            // 用户自定义分组暂无内容
            originalPrompts = emptyList()
            promptAdapter.updateData(emptyList())
            promptEmptyState.visibility = View.VISIBLE
            promptRecyclerView.visibility = View.GONE
        }
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
        val names = scenarioItems.map { it.name }.toTypedArray()
        if (names.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "暂无场景可编辑", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        var index = 0
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("编辑场景")
            .setSingleChoiceItems(names, index) { _, which -> index = which }
            .setNeutralButton("上移") { _, _ -> if (index > 0) scenarioAdapter.moveScenario(index, index - 1) }
            .setNegativeButton("下移") { _, _ -> if (index < scenarioItems.size - 1) scenarioAdapter.moveScenario(index, index + 1) }
            .setPositiveButton("更多") { _, _ ->
                val options = arrayOf("重命名", "删除")
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setItems(options) { _, which2 ->
                        when (which2) {
                            0 -> {
                                val input = android.widget.EditText(requireContext()).apply { hint = "新的名称" }
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle("重命名")
                                    .setView(input)
                                    .setPositiveButton("确定") { _, _ ->
                                        val newName = input.text.toString()
                                        if (newName.isNotEmpty()) scenarioAdapter.renameScenario(index, newName)
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                            1 -> scenarioAdapter.deleteScenario(index)
                        }
                    }
                    .show()
            }
            .show()
    }
}

