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
            showSearchDialog()
        }
        
        // 过滤按钮
        promptFilterButton.setOnClickListener { cycleFilterMode() }
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
     * 获取可用的场景列表（包含主分类和子分类）
     * 将"我的收藏"置顶，去掉不需要的类别
     */
    private fun getAvailableScenarios(): List<PromptCategory> {
        val excludedCategories = setOf(
            PromptCategory.UNKNOWN,
            PromptCategory.MY_UPLOADS,           // 我的上传
            PromptCategory.EXPERT_PICKS,         // 达人精选
            PromptCategory.TOP10_WEEK,           // 本周TOP10
            PromptCategory.POPULAR,              // 热门推荐
            PromptCategory.HIGH_FREQUENCY,       // 高频场景
            PromptCategory.FUNCTIONAL             // 功能分类
        )
        
        val allCategories = PromptCategory.values().filter { 
            it !in excludedCategories 
        }
        
        // 将"我的收藏"置顶
        val myCollections = allCategories.find { it == PromptCategory.MY_COLLECTIONS }
        val others = allCategories.filter { it != PromptCategory.MY_COLLECTIONS }
        
        return if (myCollections != null) {
            listOf(myCollections) + others
        } else {
            allCategories
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
        val dlg = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("编辑场景")
            .setSingleChoiceItems(names, index) { _, which -> index = which }
            .setNeutralButton("上移", null)
            .setNegativeButton("退出", null)
            .setPositiveButton("更多", null)
            .create()
        dlg.setOnShowListener {
            val up = dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            val more = dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            up.setOnClickListener {
                if (index > 0) {
                    scenarioAdapter.moveScenario(index, index - 1)
                    index -= 1
                }
            }
            more.setOnClickListener {
                val options = arrayOf("下移", "重命名", "删除")
                val sub = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setItems(options) { _, which2 ->
                        when (which2) {
                            0 -> { // 下移
                                if (index < scenarioItems.size - 1) {
                                    scenarioAdapter.moveScenario(index, index + 1)
                                    index += 1
                                }
                            }
                            1 -> {
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
                                        if (newName.isNotEmpty()) scenarioAdapter.renameScenario(index, newName)
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            }
                            2 -> scenarioAdapter.deleteScenario(index)
                        }
                    }
                    .create()
                sub.show()
            }
        }
        dlg.show()
    }
}


