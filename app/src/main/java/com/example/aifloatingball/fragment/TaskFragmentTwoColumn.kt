package com.example.aifloatingball.fragment

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.aifloatingball.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.aifloatingball.R
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.adapter.PromptCommunityAdapter
import com.example.aifloatingball.adapter.TaskScenarioAdapter
import com.example.aifloatingball.adapter.UnifiedCollectionAdapter
import com.example.aifloatingball.data.PromptCommunityData
import com.example.aifloatingball.dialog.PromptExtractDialog
import com.example.aifloatingball.dialog.EditCollectionDrawer
import com.example.aifloatingball.dialog.SearchCollectionPanel
import com.example.aifloatingball.manager.UnifiedCollectionManager
import com.example.aifloatingball.model.*

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
    
    // 右侧列表（支持Prompt和统一收藏）
    private lateinit var promptRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var promptAdapter: PromptCommunityAdapter
    private lateinit var collectionAdapter: UnifiedCollectionAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var selectedScenarioTitle: TextView
    private lateinit var promptEmptyState: LinearLayout
    
    // 操作按钮
    private lateinit var promptFavoriteButton: android.widget.ImageButton
    private lateinit var promptEditButton: android.widget.ImageButton
    private lateinit var promptAddCategoryButton: android.widget.ImageButton
    private lateinit var promptSearchButton: android.widget.ImageButton
    private lateinit var promptFilterButton: android.widget.ImageButton
    private var viewModeButton: android.widget.ImageButton? = null  // 视图模式切换
    
    // 统一收藏管理器
    private lateinit var collectionManager: UnifiedCollectionManager
    
    // 当前选中的场景
    private var currentScenario: PromptCategory? = null
    private var currentCollectionType: CollectionType? = null  // 统一收藏类型
    private var isFilterMode: Boolean = false
    private var currentFilterIndex: Int = -1 // -1 表示未过滤
    private var originalPrompts: List<PromptCommunityItem> = emptyList() // 保存原始数据，用于搜索和过滤
    private var originalCollections: List<UnifiedCollectionItem> = emptyList() // 统一收藏原始数据
    
    // 视图模式
    private var currentViewMode: UnifiedCollectionAdapter.ViewMode = UnifiedCollectionAdapter.ViewMode.LIST
    
    // 排序和筛选
    private var currentSortDimension: SortDimension? = null
    private var currentSortDirection: SortDirection = SortDirection.DESC
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_task_two_column_fragment
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化统一收藏管理器
        collectionManager = UnifiedCollectionManager.getInstance(requireContext())
        
        setupViews(view)
        setupScenarioRecyclerView()
        setupPromptRecyclerView()
        setupCollectionRecyclerView()
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
        
        // 视图模式按钮（如果存在）- 当前布局中未包含此按钮
        // viewModeButton = view.findViewById(R.id.view_mode_button)
    }
    
    private fun setupScenarioRecyclerView() {
        // 获取场景列表（包含Prompt分类和统一收藏类型）
        val scenarios = getAvailableScenarios()
        scenarioItems.clear()
        scenarioItems.addAll(scenarios)
        
        scenarioAdapter = TaskScenarioAdapter(scenarioItems) { item ->
            selectScenario(item)
        }
        
        scenarioRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        scenarioRecyclerView.adapter = scenarioAdapter
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == to) return false
                val moved = scenarioItems.removeAt(from)
                scenarioItems.add(to, moved)
                scenarioAdapter.moveScenario(from, to)
                return true
            }
            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
        })
        itemTouchHelper.attachToRecyclerView(scenarioRecyclerView)
    }
    
    private fun setupPromptRecyclerView() {
        val favoriteManager = com.example.aifloatingball.manager.PromptFavoriteManager.getInstance(requireContext())
        promptAdapter = PromptCommunityAdapter(
            emptyList(),
            onItemClick = { prompt -> onPromptItemClick(prompt) },
            onLikeClick = { prompt -> {} },
            onCollectClick = { prompt -> 
                if (favoriteManager.isFavorite(prompt.id)) {
                    favoriteManager.removeFavorite(prompt.id)
                    android.widget.Toast.makeText(requireContext(), "已取消收藏", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    favoriteManager.addFavorite(prompt)
                    android.widget.Toast.makeText(requireContext(), "已收藏", android.widget.Toast.LENGTH_SHORT).show()
                }
                // 刷新列表以更新收藏状态
                currentScenario?.let { loadPromptsForScenario(it) }
            },
            onCommentClick = { prompt -> {} },
            onShareClick = { prompt -> onPromptShare(prompt) },
            favoriteManager = favoriteManager
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
            if (currentCollectionType != null) {
                // 统一收藏：显示搜索面板
                showCollectionSearchPanel()
            } else {
                // Prompt：显示简单搜索对话框
                showSearchDialog()
            }
        }
        
        // 过滤按钮
        promptFilterButton.setOnClickListener { 
            if (currentCollectionType != null) {
                // 统一收藏：显示筛选和排序对话框
                showCollectionFilterDialog()
            } else {
                // Prompt：切换过滤模式
                cycleFilterMode()
            }
        }
        
        // 编辑按钮（统一收藏时显示编辑面板）
        promptEditButton.setOnClickListener {
            if (currentCollectionType != null) {
                // 统一收藏：显示批量操作或编辑面板
                showCollectionBatchActions()
            } else {
                // Prompt：显示编辑对话框
                showEditDialog()
            }
        }
    }
    
    /**
     * 显示收藏对话框（跳转到我的收藏）
     */
    private fun showFavoriteDialog() {
        val myCollectionsItem = scenarioItems.find { it.category == PromptCategory.MY_COLLECTIONS }
        if (myCollectionsItem != null) {
            selectScenario(myCollectionsItem)
        } else {
            android.widget.Toast.makeText(requireContext(), "我的收藏", android.widget.Toast.LENGTH_SHORT).show()
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
                    scenarioRecyclerView.smoothScrollToPosition(0)
                    scenarioItems.firstOrNull()?.let { selectScenario(it) }
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
     * 获取可用的场景列表（包含Prompt分类和统一收藏类型）
     * 将"我的收藏"置顶，添加统一收藏类型标签
     */
    private fun getAvailableScenarios(): List<ScenarioItem> {
        val scenarios = mutableListOf<ScenarioItem>()
        
        // 1. 添加"我的收藏"（Prompt分类）
        scenarios.add(ScenarioItem(
            name = PromptCategory.MY_COLLECTIONS.displayName,
            category = PromptCategory.MY_COLLECTIONS
        ))
        
        // 2. 添加统一收藏类型（合并搜索历史）
        scenarios.add(ScenarioItem(
            name = CollectionType.AI_REPLY.displayName,
            collectionType = CollectionType.AI_REPLY
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.SEARCH_HISTORY.displayName,
            collectionType = CollectionType.SEARCH_HISTORY
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.WEB_BOOKMARK.displayName,
            collectionType = CollectionType.WEB_BOOKMARK
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.EBOOK_BOOKMARK.displayName,
            collectionType = CollectionType.EBOOK_BOOKMARK
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.IMAGE_COLLECTION.displayName,
            collectionType = CollectionType.IMAGE_COLLECTION
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.VIDEO_COLLECTION.displayName,
            collectionType = CollectionType.VIDEO_COLLECTION
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.READING_HIGHLIGHT.displayName,
            collectionType = CollectionType.READING_HIGHLIGHT
        ))
        scenarios.add(ScenarioItem(
            name = CollectionType.CLIPBOARD_HISTORY.displayName,
            collectionType = CollectionType.CLIPBOARD_HISTORY
        ))
        
        // 3. 添加其他Prompt分类
        val excludedCategories = setOf(
            PromptCategory.UNKNOWN,
            PromptCategory.MY_UPLOADS,
            PromptCategory.EXPERT_PICKS,
            PromptCategory.TOP10_WEEK,
            PromptCategory.POPULAR,
            PromptCategory.HIGH_FREQUENCY,
            PromptCategory.FUNCTIONAL,
            PromptCategory.MY_COLLECTIONS  // 已添加
        )
        
        val otherCategories = PromptCategory.values().filter { 
            it !in excludedCategories 
        }
        
        scenarios.addAll(otherCategories.map { 
            ScenarioItem(it.displayName, it) 
        })
        
        return scenarios
    }
    
    /**
     * 选择场景
     */
    private fun selectScenario(scenario: ScenarioItem) {
        scenarioAdapter.setSelectedScenario(scenario)
        selectedScenarioTitle.text = scenario.name
        
        // 判断是Prompt分类还是统一收藏类型
        if (scenario.collectionType != null) {
            // 统一收藏类型
            currentCollectionType = scenario.collectionType
            currentScenario = null
            loadCollectionsForType(scenario.collectionType)
        } else {
            // Prompt分类
            currentScenario = scenario.category
            currentCollectionType = null
            currentScenario?.let { loadPromptsForScenario(it) } ?: run {
                // 用户自定义分组暂无内容
                originalPrompts = emptyList()
                promptAdapter.updateData(emptyList())
                promptEmptyState.visibility = View.VISIBLE
                promptRecyclerView.visibility = View.GONE
            }
        }
    }
    
    /**
     * 加载指定场景的Prompt列表
     */
    private fun loadPromptsForScenario(scenario: PromptCategory) {
        val favoriteManager = com.example.aifloatingball.manager.PromptFavoriteManager.getInstance(requireContext())
        
        // 如果是"我的收藏"，显示收藏列表
        val prompts = if (scenario == PromptCategory.MY_COLLECTIONS) {
            favoriteManager.getFavorites()
        } else if (scenario.isMainCategory) {
            val subcategories = getSubcategoriesForMainCategory(scenario)
            PromptCommunityData.getSamplePrompts().filter { prompt ->
                prompt.category == scenario || prompt.category in subcategories
            }
        } else {
            PromptCommunityData.getPromptsByCategory(scenario)
        }
        
        // 更新收藏状态
        val promptsWithFavoriteStatus = prompts.map { prompt ->
            prompt.copy(isCollected = favoriteManager.isFavorite(prompt.id))
        }
        
        // 保存原始数据
        originalPrompts = promptsWithFavoriteStatus
        
        if (promptsWithFavoriteStatus.isEmpty()) {
            promptEmptyState.visibility = View.VISIBLE
            promptRecyclerView.visibility = View.GONE
        } else {
            promptEmptyState.visibility = View.GONE
            promptRecyclerView.visibility = View.VISIBLE
            promptAdapter.updateData(promptsWithFavoriteStatus)
        }
    }
    
    /**
     * 分享Prompt
     */
    private fun onPromptShare(prompt: PromptCommunityItem) {
        val shareText = "${prompt.title}\n\n${prompt.description}\n\n提示词：\n${prompt.content}"
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            putExtra(android.content.Intent.EXTRA_SUBJECT, prompt.title)
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "分享提示词"))
    }
    
    /**
     * 获取主分类下的所有子分类
     */
    private fun getSubcategoriesForMainCategory(mainCategory: PromptCategory): List<PromptCategory> {
        return when (mainCategory) {
            PromptCategory.MY_CONTENT -> listOf(
                PromptCategory.MY_COLLECTIONS
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
            createGroupChatFromPrompt(prompt)
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
            // 直接启动Web搜索服务（默认引擎）
            val intent = Intent(requireContext(), com.example.aifloatingball.service.DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", prompt)
            }
            requireContext().startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("TaskFragmentTwoColumn", "启动搜索失败", e)
            android.widget.Toast.makeText(requireContext(), "启动搜索失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 基于Prompt创建一个AI群组并发送消息
     */
    private fun createGroupChatFromPrompt(prompt: String) {
        try {
            val aiServices = getAvailableAIServices()
            if (aiServices.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "未配置可用的AI服务", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val title = prompt.split('\n').firstOrNull()?.take(24) ?: "AI协作群"
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            val group = groupChatManager.createGroupChat(
                name = title,
                description = "由任务Tab创建的群组：${title}",
                aiMembers = aiServices
            )
            // 发送用户消息（prompt作为首条消息）
            val userMessage = com.example.aifloatingball.model.GroupChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = prompt,
                senderId = "user",
                senderName = "用户",
                senderType = com.example.aifloatingball.model.MemberType.USER,
                timestamp = System.currentTimeMillis(),
                messageType = com.example.aifloatingball.model.GroupMessageType.TEXT
            )
            groupChatManager.addMessageToGroup(group.id, userMessage)
            // 触发AI自动回复（协程）
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    groupChatManager.sendUserMessage(group.id, prompt)
                } catch (e: Exception) {
                    android.util.Log.e("TaskFragmentTwoColumn", "发送群聊首条消息失败", e)
                }
            }

            // 打开群聊界面
            val contact = com.example.aifloatingball.model.ChatContact(
                id = group.id,
                name = group.name,
                avatar = group.avatar,
                type = com.example.aifloatingball.model.ContactType.GROUP,
                description = group.description,
                isOnline = true,
                lastMessage = "群聊已创建",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = false,
                isMuted = false,
                groupId = group.id,
                memberCount = group.members.size,
                aiMembers = group.members.filter { it.type == com.example.aifloatingball.model.MemberType.AI }.map { it.name }
            )
            val chatIntent = Intent(requireContext(), ChatActivity::class.java)
            chatIntent.putExtra(ChatActivity.EXTRA_CONTACT, contact)
            startActivity(chatIntent)
        } catch (e: Exception) {
            android.util.Log.e("TaskFragmentTwoColumn", "创建群聊失败", e)
            android.widget.Toast.makeText(requireContext(), "创建群聊失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAvailableAIServices(): List<com.example.aifloatingball.manager.AIServiceType> {
        val settings = com.example.aifloatingball.SettingsManager.getInstance(requireContext())
        val list = mutableListOf<com.example.aifloatingball.manager.AIServiceType>()
        if (settings.getDeepSeekApiKey().isNotEmpty()) list.add(com.example.aifloatingball.manager.AIServiceType.DEEPSEEK)
        if (settings.getKimiApiKey().isNotEmpty()) list.add(com.example.aifloatingball.manager.AIServiceType.KIMI)
        if (settings.getString("zhipu_ai_api_key", "")?.isNotEmpty() == true) list.add(com.example.aifloatingball.manager.AIServiceType.ZHIPU_AI)
        if (settings.getString("chatgpt_api_key", "")?.isNotEmpty() == true) list.add(com.example.aifloatingball.manager.AIServiceType.CHATGPT)
        if (settings.getString("claude_api_key", "")?.isNotEmpty() == true) list.add(com.example.aifloatingball.manager.AIServiceType.CLAUDE)
        if (settings.getQianwenApiKey().isNotEmpty()) list.add(com.example.aifloatingball.manager.AIServiceType.QIANWEN)
        if (settings.getString("xinghuo_api_key", "")?.isNotEmpty() == true) list.add(com.example.aifloatingball.manager.AIServiceType.XINGHUO)
        if (settings.getWenxinApiKey().isNotEmpty()) list.add(com.example.aifloatingball.manager.AIServiceType.WENXIN)
        if (settings.getGeminiApiKey().isNotEmpty()) list.add(com.example.aifloatingball.manager.AIServiceType.GEMINI)
        if (list.isEmpty()) list.add(com.example.aifloatingball.manager.AIServiceType.TEMP_SERVICE)
        return list
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
        
        // 创建自定义布局，包含列表和按钮
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (16 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
        }
        
        // 创建列表视图
        val listView = android.widget.ListView(requireContext())
        // 使用可变列表初始化 adapter，这样才能调用 clear() 和 addAll()
        val adapter = android.widget.ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            android.R.id.text1,
            mutableListOf<String>().apply { addAll(names.toList()) }
        )
        listView.adapter = adapter
        listView.choiceMode = android.widget.AbsListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(index, true)
        
        listView.setOnItemClickListener { _, _, position, _ ->
            index = position
        }
        
        // 更新列表的函数
        fun updateList() {
            val newNames = scenarioItems.map { it.name }
            adapter.clear()
            adapter.addAll(newNames)
            adapter.notifyDataSetChanged()
            if (index >= 0 && index < newNames.size) {
                listView.setItemChecked(index, true)
            }
        }
        
        layout.addView(listView, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        
        // 创建按钮容器
        val buttonContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        
        // 创建所有按钮
        val btnUp = android.widget.Button(requireContext()).apply {
            text = "上移"
            setOnClickListener {
                if (index > 0) {
                    scenarioAdapter.moveScenario(index, index - 1)
                    index -= 1
                    updateList()
                }
            }
        }
        
        val btnDown = android.widget.Button(requireContext()).apply {
            text = "下移"
            setOnClickListener {
                if (index < scenarioItems.size - 1) {
                    scenarioAdapter.moveScenario(index, index + 1)
                    index += 1
                    updateList()
                }
            }
        }
        
        val btnRename = android.widget.Button(requireContext()).apply {
            text = "重命名"
            setOnClickListener {
                val input = android.widget.EditText(requireContext()).apply {
                    hint = "新的名称"
                    setText(scenarioItems[index].name)
                    setSelection(text.length)
                }
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("重命名")
                    .setView(input)
                    .setPositiveButton("确定") { _, _ ->
                        val newName = input.text.toString()
                        if (newName.isNotEmpty()) {
                            scenarioAdapter.renameScenario(index, newName)
                            updateList()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        
        val btnDelete = android.widget.Button(requireContext()).apply {
            text = "删除"
        }
        
        val btnExit = android.widget.Button(requireContext()).apply {
            text = "退出"
        }
        
        // 先创建对话框，这样按钮的点击事件就可以引用 dlg
        val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("编辑场景")
            .setView(layout)
            .create()
        
        // 现在设置按钮的点击事件
        btnDelete.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除「${scenarioItems[index].name}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    val deletedIndex = index
                    scenarioAdapter.deleteScenario(deletedIndex)
                    if (deletedIndex >= scenarioItems.size && scenarioItems.isNotEmpty()) {
                        index = scenarioItems.size - 1
                    } else if (scenarioItems.isEmpty()) {
                        dlg.dismiss()
                        return@setPositiveButton
                    }
                    if (index < 0) index = 0
                    updateList()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        btnExit.setOnClickListener {
            dlg.dismiss()
        }
        
        // 设置按钮布局参数
        val buttonMargin = (4 * resources.displayMetrics.density).toInt()
        
        buttonContainer.addView(btnUp, android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { marginEnd = buttonMargin })
        
        buttonContainer.addView(btnDown, android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { marginEnd = buttonMargin })
        
        buttonContainer.addView(btnRename, android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { marginEnd = buttonMargin })
        
        buttonContainer.addView(btnDelete, android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { marginEnd = buttonMargin })
        
        buttonContainer.addView(btnExit, android.widget.LinearLayout.LayoutParams(
            0,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ))
        
        layout.addView(buttonContainer, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        
        dlg.show()
    }
    
    /**
     * 设置统一收藏RecyclerView
     */
    private fun setupCollectionRecyclerView() {
        collectionAdapter = UnifiedCollectionAdapter(
            collections = emptyList(),
            viewMode = currentViewMode,
            onItemClick = { item -> onCollectionItemClick(item) },
            onItemLongClick = { item -> onCollectionItemLongClick(item) },
            onItemSelected = { selectedIds -> onCollectionItemsSelected(selectedIds) }
        )
        
        // 默认使用列表布局管理器
        promptRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }
    
    /**
     * 加载指定类型的统一收藏
     */
    private fun loadCollectionsForType(type: CollectionType) {
        // 切换到统一收藏适配器
        promptRecyclerView.adapter = collectionAdapter
        
        // 获取该类型的所有收藏
        val collections = collectionManager.getCollectionsByType(type)
        originalCollections = collections
        
        // 应用排序
        val sortedCollections = if (currentSortDimension != null) {
            collectionManager.sortCollections(collections, currentSortDimension!!, currentSortDirection)
        } else {
            collections.sortedByDescending { it.collectedTime }
        }
        
        collectionAdapter.updateData(sortedCollections)
        
        if (sortedCollections.isEmpty()) {
            promptEmptyState.visibility = View.VISIBLE
            promptRecyclerView.visibility = View.GONE
        } else {
            promptEmptyState.visibility = View.GONE
            promptRecyclerView.visibility = View.VISIBLE
        }
    }
    
    /**
     * 统一收藏项点击
     */
    private fun onCollectionItemClick(item: UnifiedCollectionItem) {
        // 如果是电子书收藏，直接打开并恢复阅读进度
        if (item.collectionType == CollectionType.EBOOK_BOOKMARK) {
            openEbookCollection(item)
            return
        }
        
        // 其他类型显示操作菜单：编辑、分享、删除
        val options = arrayOf("编辑", "分享", "删除")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(item.title.take(50))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editCollectionItem(item)  // 编辑
                    1 -> shareCollectionItem(item)  // 分享
                    2 -> deleteCollectionItem(item)  // 删除
                }
            }
            .show()
    }
    
    /**
     * 打开电子书收藏项
     */
    private fun openEbookCollection(item: UnifiedCollectionItem) {
        try {
            val isReaderMode = item.extraData?.get("isReaderMode") == "true"
            
            if (isReaderMode) {
                // 阅读模式：打开URL并进入阅读模式
                var url = (item.extraData?.get("url") as? String) ?: item.content
                val originalUrl = (item.extraData?.get("originalUrl") as? String) ?: url
                
                // 如果URL为空，尝试自动加载上一次阅读的记录
                if (url.isEmpty()) {
                    url = loadLastReadRecord() ?: ""
                    if (url.isNotEmpty()) {
                        android.util.Log.d("TaskFragmentTwoColumn", "自动加载上一次阅读记录: $url")
                    }
                }
                
                if (url.isNotEmpty()) {
                    // 启动SimpleModeActivity并打开URL
                    val intent = android.content.Intent(requireContext(), com.example.aifloatingball.SimpleModeActivity::class.java).apply {
                        action = "OPEN_URL"
                        putExtra("url", url)
                        putExtra("enter_reader_mode", true) // 标记需要进入阅读模式
                        putExtra("auto_load_last_read", true) // 允许自动加载上一次阅读记录
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    android.widget.Toast.makeText(requireContext(), "正在打开阅读模式...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(requireContext(), "URL为空，无法打开", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                // 文件阅读器：打开文件并恢复阅读进度
                val filePath = (item.extraData?.get("filePath") as? String) ?: item.content
                val fileUri = item.extraData?.get("fileUri") as? String
                val fileName = (item.extraData?.get("fileName") as? String) ?: item.title
                
                if (filePath.isNotEmpty()) {
                    try {
                        val uri = if (fileUri != null && fileUri.isNotEmpty()) {
                            android.net.Uri.parse(fileUri)
                        } else {
                            // 从文件路径创建URI
                            val file = java.io.File(filePath)
                            if (file.exists()) {
                                com.example.aifloatingball.viewer.FileReaderActivity.startWithPath(
                                    requireActivity(),
                                    filePath,
                                    fileName
                                )
                                android.widget.Toast.makeText(requireContext(), "正在打开文件...", android.widget.Toast.LENGTH_SHORT).show()
                                return
                            } else {
                                android.widget.Toast.makeText(requireContext(), "文件不存在", android.widget.Toast.LENGTH_SHORT).show()
                                return
                            }
                        }
                        
                        com.example.aifloatingball.viewer.FileReaderActivity.start(
                            requireActivity(),
                            uri,
                            fileName
                        )
                        android.widget.Toast.makeText(requireContext(), "正在打开文件...", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("TaskFragmentTwoColumn", "打开文件失败", e)
                        android.widget.Toast.makeText(requireContext(), "打开文件失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskFragmentTwoColumn", "打开电子书收藏失败", e)
            android.widget.Toast.makeText(requireContext(), "打开失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 加载上一次阅读的记录和网址
     */
    private fun loadLastReadRecord(): String? {
        return try {
            val prefs = requireContext().getSharedPreferences("last_read_record", Context.MODE_PRIVATE)
            val url = prefs.getString("last_read_url", null)
            val lastReadTime = prefs.getLong("last_read_time", 0L)
            
            if (url != null && url.isNotEmpty() && lastReadTime > 0) {
                android.util.Log.d("TaskFragmentTwoColumn", "加载上一次阅读记录: $url, 时间: $lastReadTime")
                url
            } else {
                android.util.Log.d("TaskFragmentTwoColumn", "没有找到上一次阅读记录")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("TaskFragmentTwoColumn", "加载上一次阅读记录失败", e)
            null
        }
    }
    
    /**
     * 编辑收藏项
     */
    private fun editCollectionItem(item: UnifiedCollectionItem) {
        val editDrawer = EditCollectionDrawer.newInstance(item)
        editDrawer.setOnSaveListener { updatedItem ->
            collectionManager.updateCollection(updatedItem)
            currentCollectionType?.let { loadCollectionsForType(it) }
            android.widget.Toast.makeText(requireContext(), "已保存", android.widget.Toast.LENGTH_SHORT).show()
        }
        editDrawer.show(parentFragmentManager, "EditCollectionDrawer")
    }
    
    /**
     * 分享收藏项
     */
    private fun shareCollectionItem(item: UnifiedCollectionItem) {
        try {
            // 构建分享内容
            val shareText = buildString {
                append("【${item.title}】\n\n")
                
                // 如果是AI回复，显示问题和回复
                if (item.collectionType == CollectionType.AI_REPLY) {
                    val userQuestion = item.extraData["userQuestion"] as? String
                    val replyLength = item.extraData["replyLength"] as? Int
                    val serviceDisplayName = item.extraData["serviceDisplayName"] as? String ?: ""
                    
                    if (userQuestion != null) {
                        append("问题：$userQuestion\n\n")
                    }
                    append("AI回复：\n${item.content}\n\n")
                    if (replyLength != null) {
                        append("回复字数：$replyLength 字\n")
                    }
                    if (serviceDisplayName.isNotEmpty()) {
                        append("AI服务：$serviceDisplayName\n")
                    }
                } else {
                    append(item.content)
                }
                
                append("\n\n")
                append("收藏时间：${item.getFormattedCollectedTime()}")
                append("\n来源：${item.getSourceDisplayText()}")
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, item.title)
            }
            
            startActivity(Intent.createChooser(shareIntent, "分享收藏项"))
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "分享收藏项失败", e)
            android.widget.Toast.makeText(requireContext(), "分享失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 删除收藏项
     */
    private fun deleteCollectionItem(item: UnifiedCollectionItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除「${item.title.take(30)}」吗？")
            .setPositiveButton("删除") { _, _ ->
                val success = collectionManager.deleteCollection(item.id)
                if (success) {
                    currentCollectionType?.let { loadCollectionsForType(it) }
                    android.widget.Toast.makeText(requireContext(), "已删除", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(requireContext(), "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 统一收藏项长按
     */
    private fun onCollectionItemLongClick(item: UnifiedCollectionItem) {
        // 进入批量选择模式
        collectionAdapter.setSelectionMode(true)
        collectionAdapter.toggleSelectAll()
    }
    
    /**
     * 统一收藏项选中回调
     */
    private fun onCollectionItemsSelected(selectedIds: List<String>) {
        // 更新批量操作工具栏
        updateBatchActionBar(selectedIds.isNotEmpty())
    }
    
    /**
     * 显示统一收藏搜索面板
     */
    private fun showCollectionSearchPanel() {
        val searchPanel = SearchCollectionPanel.newInstance()
        searchPanel.setOnSearchListener { params ->
            val results = collectionManager.searchCollections(
                query = params.query,
                type = params.type ?: currentCollectionType,
                tags = params.tags,
                timeRange = params.timeRange,
                priority = params.priority,
                completionStatus = params.completionStatus,
                isEncrypted = params.isEncrypted
            )
            
            // 应用排序
            val sortedResults = if (currentSortDimension != null) {
                collectionManager.sortCollections(results, currentSortDimension!!, currentSortDirection)
            } else {
                results.sortedByDescending { it.collectedTime }
            }
            
            originalCollections = sortedResults
            collectionAdapter.updateData(sortedResults)
            
            if (sortedResults.isEmpty()) {
                promptEmptyState.visibility = View.VISIBLE
                promptRecyclerView.visibility = View.GONE
            } else {
                promptEmptyState.visibility = View.GONE
                promptRecyclerView.visibility = View.VISIBLE
            }
        }
        searchPanel.show(parentFragmentManager, "SearchCollectionPanel")
    }
    
    /**
     * 显示统一收藏筛选和排序对话框
     */
    private fun showCollectionFilterDialog() {
        val sortDimensions = SortDimension.values().map { it.displayName }.toTypedArray()
        val sortDirections = SortDirection.values().map { it.displayName }.toTypedArray()
        
        var selectedDimensionIndex = currentSortDimension?.let { 
            SortDimension.values().indexOf(it) 
        } ?: 0
        var selectedDirectionIndex = SortDirection.values().indexOf(currentSortDirection)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("筛选和排序")
            .setMessage("排序维度：${sortDimensions[selectedDimensionIndex]}\n排序方向：${sortDirections[selectedDirectionIndex]}")
            .setPositiveButton("应用") { _, _ ->
                currentSortDimension = SortDimension.values()[selectedDimensionIndex]
                currentSortDirection = SortDirection.values()[selectedDirectionIndex]
                currentCollectionType?.let { loadCollectionsForType(it) }
            }
            .setNeutralButton("清除") { _, _ ->
                currentSortDimension = null
                currentSortDirection = SortDirection.DESC
                currentCollectionType?.let { loadCollectionsForType(it) }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示统一收藏批量操作
     */
    private fun showCollectionBatchActions() {
        val selectedIds = collectionAdapter.getSelectedIds()
        
        if (selectedIds.isEmpty()) {
            // 没有选中项，显示批量操作菜单
            val options = arrayOf("全选", "视图模式", "导入", "导出")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("批量操作")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> collectionAdapter.toggleSelectAll()
                        1 -> showViewModeDialog()
                        2 -> showImportDialog()
                        3 -> showExportDialog()
                    }
                }
                .show()
        } else {
            // 有选中项，显示操作菜单
            val options = arrayOf("删除", "移动", "编辑", "取消选择")
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("已选择 ${selectedIds.size} 项")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> deleteSelectedCollections(selectedIds)
                        1 -> showMoveCollectionsDialog(selectedIds)
                        2 -> editSelectedCollections(selectedIds)
                        3 -> collectionAdapter.clearSelection()
                    }
                }
                .show()
        }
    }
    
    /**
     * 删除选中的收藏项
     */
    private fun deleteSelectedCollections(ids: List<String>) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除 ${ids.size} 个收藏项吗？")
            .setPositiveButton("删除") { _, _ ->
                val deletedCount = collectionManager.deleteCollections(ids)
                collectionAdapter.clearSelection()
                currentCollectionType?.let { loadCollectionsForType(it) }
                android.widget.Toast.makeText(requireContext(), "已删除 $deletedCount 项", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示移动收藏项对话框
     */
    private fun showMoveCollectionsDialog(ids: List<String>) {
        val types = CollectionType.values().map { it.displayName }.toTypedArray()
        var selectedIndex = currentCollectionType?.let { 
            CollectionType.values().indexOf(it) 
        } ?: 0
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("移动到")
            .setSingleChoiceItems(types, selectedIndex) { dialog, which ->
                selectedIndex = which
            }
            .setPositiveButton("移动") { dialog, _ ->
                val targetType = CollectionType.values()[selectedIndex]
                val movedCount = collectionManager.moveCollectionsToType(ids, targetType)
                collectionAdapter.clearSelection()
                currentCollectionType?.let { loadCollectionsForType(it) }
                android.widget.Toast.makeText(requireContext(), "已移动 $movedCount 项到${targetType.displayName}", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 编辑选中的收藏项（批量编辑）
     */
    private fun editSelectedCollections(ids: List<String>) {
        if (ids.size == 1) {
            // 单个编辑
            val item = collectionManager.getCollectionById(ids[0])
            item?.let {
                val editDrawer = EditCollectionDrawer.newInstance(it)
                editDrawer.setOnSaveListener { updatedItem ->
                    collectionManager.updateCollection(updatedItem)
                    currentCollectionType?.let { loadCollectionsForType(it) }
                    android.widget.Toast.makeText(requireContext(), "已保存", android.widget.Toast.LENGTH_SHORT).show()
                }
                editDrawer.show(parentFragmentManager, "EditCollectionDrawer")
            }
        } else {
            android.widget.Toast.makeText(requireContext(), "批量编辑功能开发中", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示视图模式选择对话框
     */
    private fun showViewModeDialog() {
        val modes = arrayOf("列表", "网格", "时间线")
        var selectedIndex = when (currentViewMode) {
            UnifiedCollectionAdapter.ViewMode.LIST -> 0
            UnifiedCollectionAdapter.ViewMode.GRID -> 1
            UnifiedCollectionAdapter.ViewMode.TIMELINE -> 2
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("视图模式")
            .setSingleChoiceItems(modes, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("确定") { _, _ ->
                currentViewMode = when (selectedIndex) {
                    0 -> UnifiedCollectionAdapter.ViewMode.LIST
                    1 -> UnifiedCollectionAdapter.ViewMode.GRID
                    2 -> UnifiedCollectionAdapter.ViewMode.TIMELINE
                    else -> UnifiedCollectionAdapter.ViewMode.LIST
                }
                
                // 更新布局管理器
                when (currentViewMode) {
                    UnifiedCollectionAdapter.ViewMode.LIST, UnifiedCollectionAdapter.ViewMode.TIMELINE -> {
                        promptRecyclerView.layoutManager = LinearLayoutManager(requireContext())
                    }
                    UnifiedCollectionAdapter.ViewMode.GRID -> {
                        promptRecyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
                    }
                }
                
                collectionAdapter.setViewMode(currentViewMode)
                currentCollectionType?.let { loadCollectionsForType(it) }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示导入对话框
     */
    private fun showImportDialog() {
        val options = arrayOf("从JSON导入", "从CSV导入")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("导入收藏")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFromJson()
                    1 -> importFromCsv()
                }
            }
            .show()
    }
    
    /**
     * 显示导出对话框
     */
    private fun showExportDialog() {
        val options = arrayOf("导出为JSON", "导出为CSV")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("导出收藏")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportToJson()
                    1 -> exportToCsv()
                }
            }
            .show()
    }
    
    /**
     * 从JSON导入
     */
    private fun importFromJson() {
        // 这里应该打开文件选择器，暂时使用输入框
        val input = android.widget.EditText(requireContext()).apply {
            hint = "粘贴JSON内容"
            minLines = 5
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("从JSON导入")
            .setView(input)
            .setPositiveButton("导入") { _, _ ->
                val json = input.text.toString()
                if (json.isNotEmpty()) {
                    val count = collectionManager.importFromJson(json, merge = true)
                    currentCollectionType?.let { loadCollectionsForType(it) }
                    android.widget.Toast.makeText(requireContext(), "已导入 $count 项", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 从CSV导入
     */
    private fun importFromCsv() {
        android.widget.Toast.makeText(requireContext(), "CSV导入功能开发中", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 导出为JSON
     */
    private fun exportToJson() {
        val json = collectionManager.exportToJson()
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("收藏数据", json)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(requireContext(), "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 导出为CSV
     */
    private fun exportToCsv() {
        val csv = collectionManager.exportToCsv()
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("收藏数据", csv)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(requireContext(), "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 更新批量操作工具栏
     */
    private fun updateBatchActionBar(hasSelection: Boolean) {
        // 这里可以显示/隐藏批量操作工具栏
        // 暂时使用Toast提示
        if (hasSelection) {
            val count = collectionAdapter.getSelectedIds().size
            android.widget.Toast.makeText(requireContext(), "已选择 $count 项", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}


