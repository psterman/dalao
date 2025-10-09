package com.example.aifloatingball.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.manager.AIServiceType
import kotlinx.coroutines.*
import java.util.UUID

/**
 * AI助手中心Fragment基类
 */
abstract class AIAssistantCenterFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResId(), container, false)
    }
    
    abstract fun getLayoutResId(): Int
}

/**
 * 基础信息Fragment
 */
class BasicInfoFragment : AIAssistantCenterFragment() {
    private lateinit var settingsManager: SettingsManager
    private lateinit var aiSearchModeSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_basic_info_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(requireContext())
        aiSearchModeSwitch = view.findViewById(R.id.ai_search_mode_switch)
        
        // 加载当前设置
        loadCurrentSettings()
        
        // 设置开关监听器
        setupSwitchListener()
    }
    
    private fun loadCurrentSettings() {
        val isAiSearchModeEnabled = settingsManager.getIsAIMode()
        aiSearchModeSwitch.isChecked = isAiSearchModeEnabled
    }
    
    private fun setupSwitchListener() {
        aiSearchModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setIsAIMode(isChecked)
            android.widget.Toast.makeText(
                requireContext(),
                if (isChecked) "已启用默认AI搜索模式" else "已关闭默认AI搜索模式",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

/**
 * 扩展配置Fragment
 */
class ExtendedConfigFragment : AIAssistantCenterFragment() {
    override fun getLayoutResId(): Int = R.layout.ai_assistant_extended_config_fragment
}

/**
 * AI行为Fragment
 */
class AIBehaviorFragment : AIAssistantCenterFragment() {
    override fun getLayoutResId(): Int = R.layout.ai_assistant_behavior_fragment
}

/**
 * 个性化Fragment
 */
class PersonalizationFragment : AIAssistantCenterFragment() {
    private lateinit var settingsManager: SettingsManager
    
    // 主题相关控件
    private lateinit var lightThemeRadio: android.widget.RadioButton
    private lateinit var darkThemeRadio: android.widget.RadioButton
    private lateinit var autoThemeRadio: android.widget.RadioButton
    private lateinit var themeGroup: android.widget.RadioGroup
    
    // 字体大小控件
    private lateinit var fontSizeSeekBar: android.widget.SeekBar
    
    // 通知设置控件
    private lateinit var pushNotificationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var soundNotificationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var vibrationNotificationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    // 隐私设置控件
    private lateinit var dataCollectionSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var anonymizationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_personalization_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(requireContext())
        setupViews(view)
        loadCurrentSettings()
        setupListeners()
    }
    
    private fun setupViews(view: android.view.View) {
        // 主题设置
        themeGroup = view.findViewById(R.id.theme_group)
        lightThemeRadio = view.findViewById(R.id.light_theme_radio)
        darkThemeRadio = view.findViewById(R.id.dark_theme_radio)
        autoThemeRadio = view.findViewById(R.id.auto_theme_radio)
        
        // 字体大小
        fontSizeSeekBar = view.findViewById(R.id.font_size_seekbar)
        
        // 通知设置
        pushNotificationSwitch = view.findViewById(R.id.push_notification_switch)
        soundNotificationSwitch = view.findViewById(R.id.sound_notification_switch)
        vibrationNotificationSwitch = view.findViewById(R.id.vibration_notification_switch)
        
        // 隐私设置
        dataCollectionSwitch = view.findViewById(R.id.data_collection_switch)
        anonymizationSwitch = view.findViewById(R.id.anonymization_switch)
    }
    
    private fun loadCurrentSettings() {
        // 加载主题设置
        val currentTheme = settingsManager.getThemeModeAsString()
        when (currentTheme) {
            "light" -> lightThemeRadio.isChecked = true
            "dark" -> darkThemeRadio.isChecked = true
            "auto" -> autoThemeRadio.isChecked = true
            else -> autoThemeRadio.isChecked = true
        }
        
        // 加载字体大小设置
        val fontSize = settingsManager.getFontSize()
        fontSizeSeekBar.progress = fontSize
        
        // 加载通知设置
        pushNotificationSwitch.isChecked = settingsManager.getPushNotificationEnabled()
        soundNotificationSwitch.isChecked = settingsManager.getSoundNotificationEnabled()
        vibrationNotificationSwitch.isChecked = settingsManager.getVibrationNotificationEnabled()
        
        // 加载隐私设置
        dataCollectionSwitch.isChecked = settingsManager.getDataCollectionEnabled()
        anonymizationSwitch.isChecked = settingsManager.getAnonymizationEnabled()
    }
    
    private fun setupListeners() {
        // 主题选择监听
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.light_theme_radio -> "light"
                R.id.dark_theme_radio -> "dark"
                R.id.auto_theme_radio -> "auto"
                else -> "auto"
            }
            settingsManager.setThemeModeFromString(theme)
            
            // 立即应用主题变化
            val themeMode = when (theme) {
                "light" -> SettingsManager.THEME_MODE_LIGHT
                "dark" -> SettingsManager.THEME_MODE_DARK
                "auto" -> SettingsManager.THEME_MODE_SYSTEM
                else -> SettingsManager.THEME_MODE_SYSTEM
            }
            
            // 应用主题到当前Activity
            val targetNightMode = when (themeMode) {
                SettingsManager.THEME_MODE_LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                SettingsManager.THEME_MODE_DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetNightMode)
            
            // 重新创建Activity以应用主题变化
            activity?.recreate()
            
            android.widget.Toast.makeText(requireContext(), "主题设置已保存", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 字体大小监听
        fontSizeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settingsManager.setFontSize(progress)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // 通知设置监听
        pushNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setPushNotificationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "推送通知已启用" else "推送通知已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        soundNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setSoundNotificationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "声音提醒已启用" else "声音提醒已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        vibrationNotificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setVibrationNotificationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "震动提醒已启用" else "震动提醒已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 隐私设置监听
        dataCollectionSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setDataCollectionEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "数据收集已启用" else "数据收集已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
        
        anonymizationSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setAnonymizationEnabled(isChecked)
            android.widget.Toast.makeText(requireContext(), 
                if (isChecked) "匿名化处理已启用" else "匿名化处理已关闭", 
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 任务Fragment
 */
class TaskFragment : AIAssistantCenterFragment() {
    private lateinit var taskRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var taskAdapter: com.example.aifloatingball.adapter.TaskTemplateAdapter
    private lateinit var searchInput: android.widget.EditText
    private lateinit var searchButton: android.widget.ImageButton
    
    override fun getLayoutResId(): Int = R.layout.ai_assistant_task_fragment
    
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViews(view)
        setupRecyclerView()
        setupSearch()
    }
    
    private fun setupViews(view: android.view.View) {
        taskRecyclerView = view.findViewById(R.id.ai_task_recycler_view)
        searchInput = view.findViewById(R.id.task_direct_search_input)
        searchButton = view.findViewById(R.id.task_direct_search_button)
    }
    
    private fun setupRecyclerView() {
        // 使用SimpleTaskTemplates的数据
        val templates = com.example.aifloatingball.data.SimpleTaskTemplates.templates
        taskAdapter = com.example.aifloatingball.adapter.TaskTemplateAdapter(templates) { template ->
            onTaskSelected(template)
        }
        
        // 设置网格布局管理器
        val layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
        taskRecyclerView.layoutManager = layoutManager
        taskRecyclerView.adapter = taskAdapter
        
        // 添加分割线
        val decoration = androidx.recyclerview.widget.DividerItemDecoration(requireContext(), layoutManager.orientation)
        taskRecyclerView.addItemDecoration(decoration)
    }
    
    private fun setupSearch() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                android.widget.Toast.makeText(requireContext(), "请输入搜索内容", android.widget.Toast.LENGTH_SHORT).show()
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
    
    private fun onTaskSelected(template: com.example.aifloatingball.model.PromptTemplate) {
        // 显示关键词输入弹窗
        showKeywordInputDialog(template)
    }
    
    /**
     * 显示问题输入弹窗
     */
    private fun showKeywordInputDialog(template: com.example.aifloatingball.model.PromptTemplate) {
        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle("${template.icon} ${template.intentName}")
            .setMessage("请提出您的问题，AI专家们将一起讨论并为您提供解决方案：")
            .create()
        
        // 创建输入框
        val input = android.widget.EditText(requireContext()).apply {
            hint = "请输入您的问题..."
            setPadding(32, 16, 32, 16)
            minLines = 3
            maxLines = 5
        }
        
        // 设置对话框布局
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            addView(input)
        }
        
        dialog.setView(layout)
        
        // 设置按钮
        dialog.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "创建专家群聊") { _, _ ->
            val question = input.text.toString().trim()
            if (question.isNotEmpty()) {
                createGroupChatForTask(template, question)
            } else {
                android.widget.Toast.makeText(requireContext(), "请输入您的问题", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.setButton(android.app.AlertDialog.BUTTON_NEGATIVE, "取消") { _, _ ->
            dialog.dismiss()
        }
        
        dialog.show()
        
        // 自动聚焦到输入框
        input.requestFocus()
    }
    
    /**
     * 为任务创建群聊
     */
    private fun createGroupChatForTask(template: com.example.aifloatingball.model.PromptTemplate, question: String) {
        try {
            // 获取已配置API的AI服务类型
            val availableAIServices = getAvailableAIServices()
            
            if (availableAIServices.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "没有可用的AI服务，请先配置API", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            // 创建群聊名称和描述
            val groupName = "${template.intentName} - 专家讨论"
            val groupDescription = "基于${template.intentName}任务的专家群聊，AI专家们将一起讨论您的问题"
            
            // 创建群聊
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            val groupChat = groupChatManager.createGroupChat(
                name = groupName,
                description = groupDescription,
                aiMembers = availableAIServices
            )
            
            // 发送用户问题到群聊（包含专家prompt）
            sendUserQuestionToGroup(groupChat, question, template)
            
            // 跳转到群聊界面
            val chatIntent = android.content.Intent(requireContext(), com.example.aifloatingball.ChatActivity::class.java)
            val groupContact = com.example.aifloatingball.model.ChatContact(
                id = groupChat.id,
                name = groupChat.name,
                avatar = groupChat.avatar,
                type = com.example.aifloatingball.model.ContactType.GROUP,
                description = groupChat.description,
                isOnline = true,
                lastMessage = "群聊已创建",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isPinned = false,
                isMuted = false,
                groupId = groupChat.id,
                memberCount = groupChat.members.size,
                aiMembers = groupChat.members.filter { it.type == com.example.aifloatingball.model.MemberType.AI }.map { it.name }
            )
            chatIntent.putExtra(com.example.aifloatingball.ChatActivity.EXTRA_CONTACT, groupContact)
            startActivity(chatIntent)
            
            android.widget.Toast.makeText(requireContext(), "专家群聊创建成功，AI专家们正在讨论您的问题", android.widget.Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "创建群聊失败", e)
            android.widget.Toast.makeText(requireContext(), "创建群聊失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 发送专家身份提示词到群聊
     */
    private fun sendExpertPromptToGroup(
        groupChat: com.example.aifloatingball.model.GroupChat,
        template: com.example.aifloatingball.model.PromptTemplate
    ) {
        try {
            val expertPrompt = generateExpertPrompt(template)
            
            // 创建系统消息
            val systemMessage = com.example.aifloatingball.model.GroupChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = expertPrompt,
                senderId = "system",
                senderName = "系统",
                senderType = com.example.aifloatingball.model.MemberType.AI,
                timestamp = System.currentTimeMillis(),
                messageType = com.example.aifloatingball.model.GroupMessageType.SYSTEM
            )
            
            // 添加到群聊消息列表
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            groupChatManager.addMessageToGroup(groupChat.id, systemMessage)
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "发送专家提示词失败", e)
        }
    }
    
    /**
     * 发送用户问题到群聊
     */
    private fun sendUserQuestionToGroup(
        groupChat: com.example.aifloatingball.model.GroupChat,
        question: String,
        template: com.example.aifloatingball.model.PromptTemplate
    ) {
        try {
            // 构建包含专家prompt的完整问题
            val expertPrompt = generateExpertPrompt(template)
            val contextualQuestion = buildString {
                appendLine(expertPrompt)
                appendLine()
                appendLine("用户问题：$question")
            }
            
            // 创建用户消息（包含专家prompt）
            val userMessage = com.example.aifloatingball.model.GroupChatMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = contextualQuestion,
                senderId = "user",
                senderName = "用户",
                senderType = com.example.aifloatingball.model.MemberType.USER,
                timestamp = System.currentTimeMillis(),
                messageType = com.example.aifloatingball.model.GroupMessageType.TEXT,
                metadata = mapOf("originalContent" to question)
            )
            
            // 添加到群聊消息列表
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            groupChatManager.addMessageToGroup(groupChat.id, userMessage)
            
            // 立即触发AI自动回复
            triggerAIAutoReplies(groupChat, contextualQuestion)
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "发送用户问题失败", e)
        }
    }
    
    /**
     * 获取已配置API的AI服务类型
     */
    private fun getAvailableAIServices(): List<AIServiceType> {
        val settingsManager = com.example.aifloatingball.SettingsManager.getInstance(requireContext())
        val availableServices = mutableListOf<AIServiceType>()
        
        // 检查各个AI服务的API配置
        if (settingsManager.getDeepSeekApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.DEEPSEEK)
        }
        
        if (settingsManager.getKimiApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.KIMI)
        }
        
        if (settingsManager.getString("zhipu_ai_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.ZHIPU_AI)
        }
        
        if (settingsManager.getString("chatgpt_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.CHATGPT)
        }
        
        if (settingsManager.getString("claude_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.CLAUDE)
        }
        
        if (settingsManager.getQianwenApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.QIANWEN)
        }
        
        if (settingsManager.getString("xinghuo_api_key", "")?.isNotEmpty() == true) {
            availableServices.add(AIServiceType.XINGHUO)
        }
        
        if (settingsManager.getWenxinApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.WENXIN)
        }
        
        if (settingsManager.getGeminiApiKey().isNotEmpty()) {
            availableServices.add(AIServiceType.GEMINI)
        }
        
        // 始终添加临时专线（无需API）
        availableServices.add(AIServiceType.TEMP_SERVICE)
        
        android.util.Log.d("TaskFragment", "可用AI服务: ${availableServices.map { it.name }}")
        return availableServices
    }
    
    /**
     * 触发AI自动回复
     */
    private fun triggerAIAutoReplies(
        groupChat: com.example.aifloatingball.model.GroupChat,
        question: String
    ) {
        try {
            val groupChatManager = com.example.aifloatingball.manager.GroupChatManager.getInstance(requireContext())
            
            // 使用协程异步触发AI回复
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // 调用GroupChatManager的发送用户消息方法，这会自动触发AI回复
                    groupChatManager.sendUserMessage(groupChat.id, question)
                    android.util.Log.d("TaskFragment", "已触发AI自动回复")
                } catch (e: Exception) {
                    android.util.Log.e("TaskFragment", "触发AI自动回复失败", e)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("TaskFragment", "触发AI自动回复失败", e)
        }
    }
    
    /**
     * 生成专家身份提示词
     */
    private fun generateExpertPrompt(
        template: com.example.aifloatingball.model.PromptTemplate
    ): String {
        return when (template.intentId) {
            "learn_understand" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以专业的角度一起讨论用户的问题。请从不同角度提供深入的分析和见解，相互补充和完善答案。
                
                💡 讨论要点：
                - 提供准确、专业的知识解释
                - 分享实际应用经验和案例
                - 提出不同的观点和解决方案
                - 相互补充和完善答案
                
                请开始专家讨论！
            """.trimIndent()
            
            "solve_problem" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以问题解决专家的身份一起分析用户的问题。请从不同角度提供解决方案，相互讨论和完善建议。
                
                💡 讨论要点：
                - 分析问题的根本原因
                - 提供多种解决方案
                - 分享相关经验和案例
                - 相互补充和完善建议
                
                请开始专家讨论！
            """.trimIndent()
            
            "create_content" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以内容创作专家的身份一起帮助用户。请从不同角度提供创意和指导，相互激发灵感。
                
                💡 讨论要点：
                - 提供创意灵感和方向
                - 分享创作技巧和经验
                - 讨论不同的表达方式
                - 相互补充和完善建议
                
                请开始专家讨论！
            """.trimIndent()
            
            "analyze_data" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以数据分析专家的身份一起分析用户的问题。请从不同角度提供分析方法和洞察。
                
                💡 讨论要点：
                - 提供专业的分析方法
                - 分享数据分析经验
                - 讨论不同的分析角度
                - 相互补充和完善见解
                
                请开始专家讨论！
            """.trimIndent()
            
            "translate_optimize" -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以翻译润色专家的身份一起帮助用户。请从不同角度提供翻译和优化建议。
                
                💡 讨论要点：
                - 提供准确的翻译建议
                - 分享语言表达技巧
                - 讨论不同的表达方式
                - 相互补充和完善建议
                
                请开始专家讨论！
            """.trimIndent()
            
            else -> """
                🤖 专家群聊已创建！
                
                各位AI专家，请以专业的角度一起讨论用户的问题。请从不同角度提供深入的分析和见解，相互补充和完善答案。
                
                💡 讨论要点：
                - 提供专业、准确的分析
                - 分享相关经验和案例
                - 提出不同的观点和解决方案
                - 相互补充和完善答案
                
                请开始专家讨论！
            """.trimIndent()
        }
    }
    
    private fun performSearch(query: String) {
        // 执行搜索逻辑
        android.widget.Toast.makeText(requireContext(), "搜索: $query", android.widget.Toast.LENGTH_SHORT).show()
        
        // 这里可以添加实际的搜索功能
        // 比如过滤任务模板或调用搜索API
    }
}
