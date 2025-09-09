package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import android.view.inputmethod.InputMethodManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.ChatMessageAdapter
import com.example.aifloatingball.adapter.GroupChatMessageAdapter
import com.example.aifloatingball.model.AIPrompt
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.manager.SimpleChatHistoryManager
import com.example.aifloatingball.data.ChatDataManager
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.model.ContactCategory
import com.example.aifloatingball.model.GroupChat
import com.example.aifloatingball.model.GroupChatMessage
import com.example.aifloatingball.model.GroupMessageType
import com.example.aifloatingball.model.MemberType
import com.example.aifloatingball.model.AIReplyStatus
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.manager.DeepSeekApiHelper
import com.example.aifloatingball.manager.GroupChatManager
import com.example.aifloatingball.manager.UnifiedGroupChatManager
import com.example.aifloatingball.manager.GroupChatListener
// MultiAIReplyHandler已移除，使用GroupChatManager
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.UUID

class ChatActivity : AppCompatActivity(), GroupChatListener {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_CONTACT = "extra_contact"
        private const val CONTACTS_PREFS_NAME = "chat_contacts"
        private const val KEY_SAVED_CONTACTS = "saved_contacts"
        private const val REQUEST_GROUP_SETTINGS = 1001
    }

    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var searchButton: ImageButton
    private lateinit var contactNameText: TextView
    private lateinit var contactStatusText: TextView
    private lateinit var replyStatusIndicator: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView

    // 新增按钮声明
    private lateinit var apiStatusIndicator: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var loadProfileButton: com.google.android.material.button.MaterialButton
    private lateinit var clearChatButton: com.google.android.material.button.MaterialButton
    private lateinit var exportChatButton: com.google.android.material.button.MaterialButton

    private var currentContact: ChatContact? = null
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var aiApiManager: AIApiManager
    private lateinit var deepSeekApiHelper: DeepSeekApiHelper
    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var groupMessageAdapter: GroupChatMessageAdapter
    private lateinit var promptListContainer: View
    private lateinit var promptList: LinearLayout
    private lateinit var settingsManager: SettingsManager
    private var userProfiles: List<PromptProfile> = emptyList()
    private lateinit var chatHistoryManager: SimpleChatHistoryManager
    private lateinit var chatDataManager: ChatDataManager
    private var isSending = false
    
    // 群聊相关
    private lateinit var groupChatManager: GroupChatManager
    // multiAIReplyHandler已移除，使用GroupChatManager
    private var currentGroupChat: GroupChat? = null
    private var isGroupChatMode = false
    
    // 实时数据更新相关
    private var aiChatUpdateReceiver: BroadcastReceiver? = null
    private var fileSyncHandler: Handler? = null
    private var fileSyncRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 先初始化管理器
        aiApiManager = AIApiManager(this)
        deepSeekApiHelper = DeepSeekApiHelper(this)
        chatHistoryManager = SimpleChatHistoryManager(this)
        chatDataManager = ChatDataManager.getInstance(this)
        settingsManager = SettingsManager.getInstance(this)
        groupChatManager = GroupChatManager.getInstance(this)
        // multiAIReplyHandler初始化已移除
        
        // 调试：检查存储的数据
        groupChatManager.debugCheckStoredData()

        // 加载用户档案
        userProfiles = settingsManager.getAllPromptProfiles()

        initializeViews()
        loadContactData()
        setupListeners()
        loadInitialMessages()

        // 处理从小组件传入的自动发送消息
        handleAutoSendMessage()
        
        // 注册群聊监听器
        groupChatManager.addGroupChatListener(this)
        
        // 设置实时数据更新机制
        setupRealtimeDataUpdate()
    }
    


    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchButton = findViewById(R.id.search_button)
        contactNameText = findViewById(R.id.contact_name)
        contactStatusText = findViewById(R.id.contact_status)
        replyStatusIndicator = findViewById(R.id.reply_status_indicator)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        messagesRecyclerView = findViewById(R.id.messages_recycler_view)

        // 初始化新增按钮
        apiStatusIndicator = findViewById(R.id.api_status_indicator)
        refreshButton = findViewById(R.id.refresh_button)
        settingsButton = findViewById(R.id.settings_button)
        loadProfileButton = findViewById(R.id.load_profile_button)
        clearChatButton = findViewById(R.id.clear_chat_button)
        exportChatButton = findViewById(R.id.export_chat_button)

        // 设置RecyclerView
        messagesRecyclerView.layoutManager = LinearLayoutManager(this)
        messageAdapter = ChatMessageAdapter(
            messages = messages,
            onMessageLongClick = { message, position ->
                showMessageOptionsDialog(message, position)
            },
            onRegenerateClick = { message, position ->
                regenerateMessage(message, position)
            }
        )
        
        // 初始化群聊消息适配器
        groupMessageAdapter = GroupChatMessageAdapter(
            context = this,
            messages = mutableListOf()
        )
        
        // 设置群聊消息操作监听器
        groupMessageAdapter.setOnMessageActionListener(object : GroupChatMessageAdapter.OnMessageActionListener {
            override fun onCopyMessage(message: GroupChatMessage) {
                copyGroupMessage(message)
            }
            
            override fun onRegenerateMessage(message: GroupChatMessage) {
                regenerateGroupMessage(message)
            }
            
            override fun onCopyAIReply(message: GroupChatMessage, aiName: String, replyContent: String) {
                copyAIReply(aiName, replyContent)
            }
            
            override fun onRegenerateAIReply(message: GroupChatMessage, aiName: String) {
                regenerateAIReply(message, aiName)
            }
            
            override fun onLikeAIReply(message: GroupChatMessage, aiName: String) {
                likeAIReply(message, aiName)
            }
            
            override fun onDeleteAIReply(message: GroupChatMessage, aiName: String) {
                deleteAIReply(message, aiName)
            }
        })
        
        messagesRecyclerView.adapter = messageAdapter

        // 初始化AI助手档案列表
        promptListContainer = findViewById(R.id.prompt_list_container)
        promptList = findViewById(R.id.prompt_list)
        setupPromptList()

        // 初始化API状态指示器
        updateApiStatusIndicator()
    }

    private fun loadContactData() {
        currentContact = intent.getParcelableExtra(EXTRA_CONTACT)
        currentContact?.let { contact ->
            contactNameText.text = contact.name
            
            // 检查是否为群聊模式
            isGroupChatMode = contact.type == ContactType.GROUP
            
            if (isGroupChatMode) {
                // 群聊模式
                var groupChatFound = false
                
                // 首先尝试通过groupId查找
                contact.groupId?.let { groupId ->
                    // 1. 尝试从GroupChatManager查找
                    currentGroupChat = groupChatManager.getGroupChat(groupId)
                    
                    // 2. 如果没找到，尝试从UnifiedGroupChatManager查找
                    if (currentGroupChat == null) {
                        val unifiedManager = UnifiedGroupChatManager.getInstance(this)
                        currentGroupChat = unifiedManager.getGroupChat(groupId)
                        if (currentGroupChat != null) {
                            Log.d(TAG, "从UnifiedGroupChatManager找到群聊: ${currentGroupChat!!.name}")
                        }
                    }
                    
                    if (currentGroupChat != null) {
                        // 验证AI成员配置
                        validateGroupChatAIMembers(currentGroupChat!!)
                        
                        // 测试智谱AI配置
                        testZhipuAIConfiguration()
                        
                        // 强制修复智谱AI配置
                        forceFixZhipuAIInGroupChat()
                        
                        // 输出诊断报告
                        outputZhipuAIDiagnosticReport()
                        
                        groupChatFound = true
                        contactStatusText.text = "群聊 · ${currentGroupChat!!.members.size}个成员"
                        contactStatusText.setTextColor(getColor(R.color.group_chat_color))
                    }
                }
                
                // 如果没有找到GroupChat，尝试从customData中获取group_chat_id
                if (!groupChatFound) {
                    contact.customData["group_chat_id"]?.let { groupChatId ->
                        currentGroupChat = groupChatManager.getGroupChat(groupChatId)
                        if (currentGroupChat != null) {
                            groupChatFound = true
                            contactStatusText.text = "群聊 · ${currentGroupChat!!.members.size}个成员"
                            contactStatusText.setTextColor(getColor(R.color.group_chat_color))
                        }
                    }
                }
                
                // 如果仍然没有找到，尝试创建一个新的GroupChat
                if (!groupChatFound) {
                    Log.w(TAG, "未找到群聊数据，尝试根据aiMembers重新创建")
                    if (contact.aiMembers.isNotEmpty()) {
                        currentGroupChat = createGroupChatFromContact(contact)
                        if (currentGroupChat != null) {
                            groupChatFound = true
                            contactStatusText.text = "群聊 · ${currentGroupChat!!.members.size}个成员"
                            contactStatusText.setTextColor(getColor(R.color.group_chat_color))
                        }
                    }
                }
                
                if (!groupChatFound) {
                    Log.e(TAG, "群聊数据加载失败，无法找到或创建GroupChat")
                    contactStatusText.text = "群聊 (数据加载失败)"
                    contactStatusText.setTextColor(getColor(android.R.color.holo_red_light))
                    Toast.makeText(this, "群聊数据加载失败，请重新创建群聊", Toast.LENGTH_LONG).show()
                }
                
                // 切换到群聊适配器
                messagesRecyclerView.adapter = groupMessageAdapter
            } else {
                // 切换到普通聊天适配器
                messagesRecyclerView.adapter = messageAdapter
                // 单聊模式
                contactStatusText.text = if (contact.isOnline) "在线" else "离线"
                when (contact.type) {
                    ContactType.AI -> {
                        contactStatusText.setTextColor(getColor(R.color.ai_icon_color))
                    }
                    else -> {
                        contactStatusText.setTextColor(getColor(android.R.color.secondary_text_light))
                    }
            }
        }
        }
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 工具栏按钮事件
        searchButton.setOnClickListener {
            showSearchDialog()
        }

        refreshButton.setOnClickListener {
            refreshChat()
        }

        settingsButton.setOnClickListener {
            showChatSettings()
        }

        // 发送按钮事件
        sendButton.setOnClickListener {
            if (!isSending) {
                sendMessage()
            }
        }

        // 功能按钮事件
        loadProfileButton.setOnClickListener {
            loadDefaultProfile()
        }

        clearChatButton.setOnClickListener {
            showClearChatDialog()
        }

        exportChatButton.setOnClickListener {
            exportChatHistory()
        }

        // 设置输入框焦点变化事件，显示/隐藏AI助手档案列表
        messageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && messageInput.text.isNullOrBlank()) {
                promptListContainer.visibility = View.VISIBLE
            } else {
                promptListContainer.visibility = View.GONE
            }
        }



        // 合并TextWatcher，同时处理发送按钮状态和档案列表显示
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val isEmpty = s.isNullOrBlank()
                sendButton.isEnabled = !isEmpty

                // 控制档案列表显示
                if (isEmpty && messageInput.hasFocus()) {
                    promptListContainer.visibility = View.VISIBLE
                } else {
                    promptListContainer.visibility = View.GONE
                }
            }
        })
    }

    private fun loadInitialMessages() {
        currentContact?.let { contact ->
            if (isGroupChatMode) {
                // 群聊模式：加载群聊消息
                currentGroupChat?.let { groupChat ->
                    val groupMessages = groupChatManager.getGroupMessages(groupChat.id)
                    groupMessageAdapter.updateMessages(groupMessages)
                    
                    // 滚动到底部
                    if (groupMessages.isNotEmpty()) {
                        messagesRecyclerView.post {
                            messagesRecyclerView.smoothScrollToPosition(groupMessages.size - 1)
                        }
                    }
                }
            } else {
                // 单聊模式：加载普通聊天历史记录
                // 获取AI服务类型
                val serviceType = getAIServiceType(contact)
                
                Log.d(TAG, "ChatActivity加载对话 - 联系人: ${contact.name}, ID: ${contact.id}, 服务类型: ${serviceType?.name ?: "默认"}")
                
                // 强制重新加载所有数据
                chatDataManager.forceReloadAllData()
                
                // 再次加载特定AI服务的数据
                if (serviceType != null) {
                    chatDataManager.loadDataForAIService(serviceType)
                }
                
                val unifiedMessages = if (serviceType != null) {
                    chatDataManager.getMessages(contact.id, serviceType)
                } else {
                    chatDataManager.getMessages(contact.id)
                }
                
                Log.d(TAG, "ChatActivity加载对话 - 从ChatDataManager获取到 ${unifiedMessages.size} 条消息")
                
                // 调试：检查AI联系人列表使用的相同方法
                val simpleModeMessage = getLastChatMessageFromSimpleMode(contact.name)
                Log.d(TAG, "ChatActivity调试 - 简易模式预览消息: ${simpleModeMessage.take(30)}...")
                
                if (unifiedMessages.isNotEmpty()) {
                    Log.d(TAG, "从ChatDataManager加载到 ${unifiedMessages.size} 条消息，准备合并到当前对话")
                    
                    // 获取当前messages中已有的消息内容，避免重复
                    val existingMessageContents = messages.map { "${it.content}|${it.isFromUser}|${it.timestamp}" }.toSet()
                    Log.d(TAG, "当前已有 ${messages.size} 条消息，准备合并新消息")
                    
                    messages.clear()
                    // 转换ChatDataManager.ChatMessage到ChatActivity.ChatMessage
                    unifiedMessages.forEach { unifiedMsg ->
                        val chatMsg = ChatMessage(
                            content = unifiedMsg.content,
                            isFromUser = unifiedMsg.role == "user",
                            timestamp = unifiedMsg.timestamp
                        )
                        messages.add(chatMsg)
                    }
                    
                    // 按时间戳排序确保消息顺序正确
                    messages.sortBy { it.timestamp }
                    
                    messageAdapter.updateMessages(messages.toList())
                    Log.d(TAG, "合并完成，现在共有 ${messages.size} 条消息 (${serviceType?.name ?: "默认"})")
                } else {
                    Log.d(TAG, "ChatDataManager中没有找到数据，但简易模式预览有数据，尝试强制同步")
                    
                    // 如果ChatDataManager中没有数据，但简易模式有预览，强制同步数据
                    if (simpleModeMessage.isNotEmpty()) {
                        Log.d(TAG, "检测到简易模式有数据，强制同步到ChatActivity")
                        forceSyncFromSimpleMode(contact, serviceType)
                    } else {
                        // 尝试从旧的存储中加载并迁移
                        val historyMessages = chatHistoryManager.loadMessages(contact.id)
                        if (historyMessages.isNotEmpty()) {
                            messages.clear()
                            messages.addAll(historyMessages)
                            messageAdapter.updateMessages(messages.toList())

                            // 迁移到统一存储
                            migrateMessagesToUnifiedStorage(contact.id, historyMessages, serviceType)
                        } else {
                            Log.d(TAG, "没有找到 ${contact.name} 的对话记录")
                        }
                    }
                }

                // 滚动到底部
                if (messages.isNotEmpty()) {
                    messagesRecyclerView.post {
                        messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                    }
                } else {
                    // 空分支
                }
            }
        }
    }

    /**
     * 迁移消息到统一存储
     */
    private fun migrateMessagesToUnifiedStorage(contactId: String, oldMessages: List<ChatMessage>, serviceType: AIServiceType? = null) {
        try {
            val targetServiceType = serviceType ?: AIServiceType.DEEPSEEK
            chatDataManager.setCurrentSessionId(contactId, targetServiceType)
            oldMessages.forEach { oldMsg ->
                val role = if (oldMsg.isFromUser) "user" else "assistant"
                chatDataManager.addMessage(contactId, role, oldMsg.content, targetServiceType)
            }
            Log.d(TAG, "成功迁移 ${oldMessages.size} 条消息到统一存储 (${targetServiceType.name})")
        } catch (e: Exception) {
            Log.e(TAG, "迁移消息到统一存储失败", e)
        }
    }

    /**
     * 根据联系人信息获取AI服务类型
     */
    private fun getAIServiceType(contact: ChatContact): AIServiceType? {
        val lowerName = contact.name.lowercase()
        Log.d(TAG, "getAIServiceType - 联系人名称: ${contact.name}, 小写: $lowerName")
        
        val serviceType = when (lowerName) {
            "chatgpt", "gpt" -> AIServiceType.CHATGPT
            "claude" -> AIServiceType.CLAUDE
            "gemini" -> AIServiceType.GEMINI
            "文心一言", "wenxin" -> AIServiceType.WENXIN
            "deepseek" -> AIServiceType.DEEPSEEK
            "通义千问", "qianwen" -> AIServiceType.QIANWEN
            "讯飞星火", "xinghuo" -> AIServiceType.XINGHUO
            "kimi" -> AIServiceType.KIMI
            "智谱ai", "智谱清言", "zhipu", "glm" -> AIServiceType.ZHIPU_AI
            else -> null
        }
        
        Log.d(TAG, "getAIServiceType - 映射结果: $serviceType")
        return serviceType
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isNotEmpty() && !isSending) {
            isSending = true
            sendButton.isEnabled = false // 禁用发送按钮
            // 添加用户消息
            val userMessage = ChatMessage(messageText, true, System.currentTimeMillis())
            messages.add(userMessage)
            messageAdapter.updateMessages(messages.toList())

            // 保存聊天记录到统一存储
            currentContact?.let { contact ->
                val serviceType = getAIServiceType(contact) ?: AIServiceType.DEEPSEEK
                
                // 确保会话存在，如果不存在则创建
                ensureSessionExists(contact.id, serviceType)
                
                chatDataManager.setCurrentSessionId(contact.id, serviceType)
                chatDataManager.addMessage(contact.id, "user", messageText, serviceType)

                // 同时保存到旧存储（向后兼容）
                chatHistoryManager.saveMessages(contact.id, messages)
                // 更新联系人的最后消息信息
                updateContactLastMessage(contact, messageText)
            }

            // 滚动到底部
            messagesRecyclerView.post {
                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
            }

            // 清空输入框
            messageInput.text.clear()

            // 发送到AI服务
            currentContact?.let { contact ->
                when (contact.type) {
                    ContactType.GROUP -> {
                        // 群聊模式：多AI并发回复
                        handleGroupChatMessage(messageText)
                    }
                    ContactType.AI -> {
                        // 获取AI服务类型
                        val serviceType = getAIServiceType(contact)
                        if (serviceType != null) {
                            // 检查是否有API密钥配置
                            val apiKey = getApiKeyForService(serviceType)
                            if (apiKey.isBlank()) {
                                // 没有API密钥，提示用户配置
                                val errorMessage = "请先在设置中配置${contact.name}的API密钥"
                                val errorMsg = ChatMessage(errorMessage, false, System.currentTimeMillis())
                                messages.add(errorMsg)
                                messageAdapter.updateMessages(messages.toList())

                                // 滚动到底部
                                messagesRecyclerView.post {
                                    messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                                }

                                Toast.makeText(this@ChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                                return@let
                            }

                            // 准备对话历史
                            val conversationHistory = messages.map {
                                mapOf("role" to if (it.isFromUser) "user" else "assistant", "content" to it.content)
                            }

                            // 添加AI回复占位符
                            val aiMessage = ChatMessage("正在思考中...", false, System.currentTimeMillis())
                            messages.add(aiMessage)
                            messageAdapter.updateMessages(messages.toList())

                            // 滚动到底部
                            messagesRecyclerView.post {
                                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                            }

                            // 如果是DeepSeek且遇到401错误，先测试API连接
                            if (serviceType == AIServiceType.DEEPSEEK) {
                                testDeepSeekConnection(apiKey) { success, message ->
                                    if (success) {
                                        // API连接正常，继续发送消息
                                        sendMessageToAI(serviceType, messageText, conversationHistory, aiMessage)
                                    } else {
                                        // API连接失败，显示错误信息和诊断选项
                                        runOnUiThread {
                                            aiMessage.content = "DeepSeek API连接测试失败：$message"
                                            messageAdapter.updateLastMessage(aiMessage.content)
                                            showDeepSeekErrorDialog(message)
                                        }
                                    }
                                }
                            } else {
                                // 其他AI服务直接发送消息
                                sendMessageToAI(serviceType, messageText, conversationHistory, aiMessage)
                            }
                        } else {
                            // 如果无法识别AI服务类型，使用模拟回复
                            val responses = listOf(
                                "我理解您的问题，让我为您详细解答...",
                                "这是一个很有趣的问题，我的看法是...",
                                "根据我的分析，建议您考虑以下几个方面...",
                                "我可以为您提供更多相关信息..."
                            )
                            val response = responses.random()

                            messageInput.postDelayed({
                                val aiResponse = ChatMessage(response, false, System.currentTimeMillis())
                                messages.add(aiResponse)
                                messageAdapter.updateMessages(messages.toList())

                                // 保存聊天记录
                                currentContact?.let { contact ->
                                    chatHistoryManager.saveMessages(contact.id, messages)
                                }

                                isSending = false // 重置发送状态
                        sendButton.isEnabled = true // 重新启用发送按钮

                                // 滚动到底部
                                messagesRecyclerView.post {
                                    messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                                }
                            }, 1000)
                        }
                    }
                }
            }
        }
    }

    /**
     * 根据ChatContact创建GroupChat
     */
    private fun createGroupChatFromContact(contact: ChatContact): GroupChat? {
        return try {
            Log.d(TAG, "尝试根据ChatContact重新创建GroupChat: ${contact.name}")
            
            // 从contact的aiMembers或customData中提取AI类型
            val aiServiceTypes = mutableListOf<AIServiceType>()
            
            // 如果有aiMembers，尝试转换
            if (contact.aiMembers.isNotEmpty()) {
                contact.aiMembers.forEach { aiId ->
                    val aiType = when {
                        aiId.contains("deepseek", ignoreCase = true) -> AIServiceType.DEEPSEEK
                        aiId.contains("chatgpt", ignoreCase = true) || aiId.contains("gpt", ignoreCase = true) -> AIServiceType.CHATGPT
                        aiId.contains("claude", ignoreCase = true) -> AIServiceType.CLAUDE
                        aiId.contains("gemini", ignoreCase = true) -> AIServiceType.GEMINI
                        aiId.contains("zhipu", ignoreCase = true) || aiId.contains("glm", ignoreCase = true) -> AIServiceType.ZHIPU_AI
                        aiId.contains("wenxin", ignoreCase = true) -> AIServiceType.WENXIN
                        aiId.contains("qianwen", ignoreCase = true) -> AIServiceType.QIANWEN
                        aiId.contains("xinghuo", ignoreCase = true) -> AIServiceType.XINGHUO
                        aiId.contains("kimi", ignoreCase = true) -> AIServiceType.KIMI
                        else -> null
                    }
                    aiType?.let { aiServiceTypes.add(it) }
                }
            }
            
            // 如果没有识别到AI类型，使用默认配置
            if (aiServiceTypes.isEmpty()) {
                Log.w(TAG, "无法从aiMembers识别AI类型，使用DeepSeek作为默认")
                aiServiceTypes.add(AIServiceType.DEEPSEEK)
            }
            
            // 创建GroupChat
            val groupChat = groupChatManager.createGroupChat(
                name = contact.name,
                description = contact.description ?: "从联系人恢复的群聊",
                aiMembers = aiServiceTypes
            )
            
            // 更新contact的groupId（如果customData是可变的）
            try {
                if (contact.customData is MutableMap) {
                    (contact.customData as MutableMap<String, String>)["group_chat_id"] = groupChat.id
                }
            } catch (e: Exception) {
                Log.w(TAG, "无法更新contact的customData", e)
            }
            
            Log.d(TAG, "成功创建GroupChat: ${groupChat.id}")
            groupChat
        } catch (e: Exception) {
            Log.e(TAG, "创建GroupChat失败", e)
            null
        }
    }
    
    /**
     * 处理群聊消息
     */
    private fun handleGroupChatMessage(messageText: String) {
        currentGroupChat?.let { groupChat ->
            // 使用GroupChatManager发送消息
            lifecycleScope.launch {
                try {
                    val success = groupChatManager.sendUserMessage(groupChat.id, messageText)
                    if (success) {
                        // 重新加载群聊消息
                        loadGroupChatMessages(groupChat.id)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@ChatActivity, "发送消息失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "发送失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    runOnUiThread {
                        isSending = false
                        sendButton.isEnabled = true
                    }
                }
            }
        } ?: run {
            // 没有找到群聊数据
            Toast.makeText(this, "群聊数据加载失败", Toast.LENGTH_SHORT).show()
            isSending = false
            sendButton.isEnabled = true
        }
    }
    
    /**
     * 验证群聊中AI成员的配置
     */
    private fun validateGroupChatAIMembers(groupChat: GroupChat) {
        val aiMembers = groupChat.members.filter { it.type == MemberType.AI }
        Log.d(TAG, "验证群聊 ${groupChat.name} 中的AI成员配置")
        
        aiMembers.forEach { member ->
            if (member.aiServiceType == null) {
                Log.e(TAG, "❌ AI成员 ${member.name} (ID: ${member.id}) 缺少aiServiceType")
            } else {
                Log.d(TAG, "✅ AI成员 ${member.name} (ID: ${member.id}) 配置正确: ${member.aiServiceType}")
                
                // 特别检查智谱AI的配置
                if (member.aiServiceType == AIServiceType.ZHIPU_AI) {
                    val apiKey = getApiKeyForService(AIServiceType.ZHIPU_AI)
                    if (apiKey.isBlank()) {
                        Log.e(TAG, "❌ 智谱AI API密钥未配置")
                    } else {
                        Log.d(TAG, "✅ 智谱AI API密钥已配置，长度: ${apiKey.length}")
                        Log.d(TAG, "智谱AI API密钥格式检查: ${apiKey.contains(".") && apiKey.length >= 20}")
                    }
                }
            }
        }
        
        val configuredCount = aiMembers.count { it.aiServiceType != null }
        Log.d(TAG, "群聊 ${groupChat.name} 中 $configuredCount/${aiMembers.size} 个AI成员配置正确")
    }

    /**
     * 测试智谱AI配置
     */
    private fun testZhipuAIConfiguration() {
        Log.d(TAG, "=== 测试智谱AI配置 ===")
        
        // 检查API密钥
        val apiKey = getApiKeyForService(AIServiceType.ZHIPU_AI)
        Log.d(TAG, "智谱AI API密钥: ${if (apiKey.isBlank()) "未配置" else "已配置 (长度: ${apiKey.length})"}")
        
        // 检查API密钥格式
        if (apiKey.isNotBlank()) {
            val isValidFormat = apiKey.contains(".") && apiKey.length >= 20
            Log.d(TAG, "智谱AI API密钥格式: ${if (isValidFormat) "有效" else "无效"}")
            
            if (!isValidFormat) {
                Log.e(TAG, "❌ 智谱AI API密钥格式错误！应该是 xxxxx.xxxxx 格式，长度≥20")
                return
            }
        } else {
            Log.e(TAG, "❌ 智谱AI API密钥未配置！")
            return
        }
        
        // 检查群聊中的智谱AI成员
        currentGroupChat?.let { groupChat ->
            val zhipuMembers = groupChat.members.filter { 
                it.type == MemberType.AI && it.aiServiceType == AIServiceType.ZHIPU_AI 
            }
            Log.d(TAG, "群聊中智谱AI成员数量: ${zhipuMembers.size}")
            
            zhipuMembers.forEach { member ->
                Log.d(TAG, "智谱AI成员: ${member.name} (ID: ${member.id})")
            }
        }
        
        // 测试API调用
        Log.d(TAG, "测试智谱AI API调用...")
        aiApiManager.sendMessage(
            serviceType = AIServiceType.ZHIPU_AI,
            message = "你好，请回复'测试成功'",
            conversationHistory = emptyList(),
            callback = object : AIApiManager.StreamingCallback {
                override fun onChunkReceived(chunk: String) {
                    Log.d(TAG, "智谱AI测试响应块: '$chunk'")
                }
                
                override fun onComplete(response: String) {
                    Log.d(TAG, "✅ 智谱AI测试成功，响应: '$response'")
                    if (response.contains("测试成功")) {
                        Log.d(TAG, "✅ 智谱AI API工作正常！")
                    } else {
                        Log.w(TAG, "⚠️ 智谱AI响应了，但内容可能有问题")
                    }
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "❌ 智谱AI测试失败: $error")
                    
                    // 分析错误类型
                    when {
                        error.contains("API密钥未配置") -> {
                            Log.e(TAG, "问题：API密钥未配置")
                        }
                        error.contains("401") -> {
                            Log.e(TAG, "问题：API密钥无效或已过期")
                        }
                        error.contains("403") -> {
                            Log.e(TAG, "问题：API访问被拒绝，可能是权限问题")
                        }
                        error.contains("429") -> {
                            Log.e(TAG, "问题：API调用频率超限")
                        }
                        error.contains("500") -> {
                            Log.e(TAG, "问题：智谱AI服务器内部错误")
                        }
                        else -> {
                            Log.e(TAG, "问题：未知错误 - $error")
                        }
                    }
                }
            }
        )
        
        Log.d(TAG, "=== 智谱AI配置测试完成 ===")
    }
    
    /**
     * 强制修复群聊中智谱AI的配置
     */
    private fun forceFixZhipuAIInGroupChat() {
        Log.d(TAG, "=== 强制修复群聊中智谱AI配置 ===")
        
        // 先执行全局修复
        val generalFixed = groupChatManager.fixMissingAIServiceTypes()
        val zhipuFixed = groupChatManager.fixZhipuAIMembers()
        Log.d(TAG, "全局修复结果: 一般=$generalFixed, 智谱AI专项=$zhipuFixed")
        
        currentGroupChat?.let { groupChat ->
            // 重新获取最新的群聊数据
            val latestGroupChat = groupChatManager.getGroupChat(groupChat.id)
            currentGroupChat = latestGroupChat
            
            latestGroupChat?.let { updatedGroupChat ->
                val zhipuMembers = updatedGroupChat.members.filter { 
                    it.type == MemberType.AI && it.aiServiceType == AIServiceType.ZHIPU_AI 
                }
                
                // 检查是否有疑似智谱AI但配置错误的成员
                val potentialZhipuMembers = updatedGroupChat.members.filter { member ->
                    member.type == MemberType.AI && 
                    member.aiServiceType != AIServiceType.ZHIPU_AI &&
                    com.example.aifloatingball.utils.AIServiceTypeUtils.isZhipuAIContact(
                        ChatContact(id = member.id, name = member.name, type = ContactType.AI)
                    )
                }
                
                if (potentialZhipuMembers.isNotEmpty()) {
                    Log.w(TAG, "发现 ${potentialZhipuMembers.size} 个疑似智谱AI成员配置错误")
                    potentialZhipuMembers.forEach { member ->
                        Log.w(TAG, "疑似智谱AI: ${member.name} (当前aiServiceType: ${member.aiServiceType})")
                    }
                    
                    // 再次执行智谱AI专项修复
                    val additionalFixed = groupChatManager.fixZhipuAIMembers()
                    Log.d(TAG, "追加修复了 $additionalFixed 个智谱AI成员")
                }
                
                if (zhipuMembers.isEmpty()) {
                    Log.w(TAG, "群聊中没有智谱AI成员，尝试添加...")
                    
                    // 直接尝试添加智谱AI到群聊
                    val success = groupChatManager.addAIMemberToGroup(updatedGroupChat.id, AIServiceType.ZHIPU_AI)
                    if (success) {
                        Log.d(TAG, "✅ 成功添加智谱AI到群聊")
                        // 重新加载群聊数据
                        currentGroupChat = groupChatManager.getGroupChat(updatedGroupChat.id)
                    } else {
                        Log.e(TAG, "❌ 添加智谱AI到群聊失败")
                    }
                } else {
                    Log.d(TAG, "群聊中已有 ${zhipuMembers.size} 个智谱AI成员")
                    
                    // 检查每个智谱AI成员的配置
                    zhipuMembers.forEach { member ->
                        val isStandardName = member.name == com.example.aifloatingball.utils.AIServiceTypeUtils.getZhipuAIStandardName()
                        val isStandardId = member.id == com.example.aifloatingball.utils.AIServiceTypeUtils.generateZhipuAIId()
                        
                        Log.d(TAG, "智谱AI成员: ${member.name} (ID: ${member.id})")
                        Log.d(TAG, "  标准名称: ${if (isStandardName) "✅" else "⚠️ 非标准 (${com.example.aifloatingball.utils.AIServiceTypeUtils.getZhipuAIStandardName()})"}")
                        Log.d(TAG, "  标准ID: ${if (isStandardId) "✅" else "⚠️ 非标准 (${com.example.aifloatingball.utils.AIServiceTypeUtils.generateZhipuAIId()})"}")
                        Log.d(TAG, "  aiServiceType: ${if (member.aiServiceType == AIServiceType.ZHIPU_AI) "✅" else "❌"}")
                    }
                }
            }
        }
        
        Log.d(TAG, "=== 智谱AI配置修复完成 ===")
    }
    
    /**
     * 输出智谱AI当前的问题诊断报告
     */
    private fun outputZhipuAIDiagnosticReport() {
        Log.d(TAG, "=== 智谱AI问题诊断报告 ===")
        
        // 1. 检查API密钥配置
        val apiKey = getApiKeyForService(AIServiceType.ZHIPU_AI)
        Log.d(TAG, "1. API密钥状态: ${if (apiKey.isBlank()) "❌ 未配置" else "✅ 已配置 (长度: ${apiKey.length})"}")
        
        if (apiKey.isNotBlank()) {
            val isValidFormat = apiKey.contains(".") && apiKey.length >= 20
            Log.d(TAG, "2. API密钥格式: ${if (isValidFormat) "✅ 有效" else "❌ 无效 (应该是 xxxxx.xxxxx 格式)"}")
        }
        
        // 2. 检查群聊配置
        currentGroupChat?.let { groupChat ->
            val zhipuMembers = groupChat.members.filter { 
                it.type == MemberType.AI && it.aiServiceType == AIServiceType.ZHIPU_AI 
            }
            Log.d(TAG, "3. 群聊中智谱AI成员: ${if (zhipuMembers.isEmpty()) "❌ 无智谱AI成员" else "✅ ${zhipuMembers.size}个智谱AI成员"}")
            
            zhipuMembers.forEach { member ->
                val hasServiceType = member.aiServiceType != null
                Log.d(TAG, "   - ${member.name}: ${if (hasServiceType) "✅ 配置正确" else "❌ 缺少aiServiceType"}")
            }
        } ?: Log.d(TAG, "3. 群聊状态: ❌ 当前不在群聊中")
        
        // 3. 检查GroupChatManager状态
        val groupChatManagerStatus = try {
            groupChatManager.fixMissingAIServiceTypes()
            "✅ 自动修复功能正常"
        } catch (e: Exception) {
            "❌ 自动修复功能异常: ${e.message}"
        }
        Log.d(TAG, "4. GroupChatManager状态: $groupChatManagerStatus")
        
        // 4. 总结问题
        Log.d(TAG, "=== 问题总结 ===")
        if (apiKey.isBlank()) {
            Log.e(TAG, "❌ 主要问题: 智谱AI API密钥未配置")
            Log.d(TAG, "💡 解决方案: 在设置中配置智谱AI API密钥")
        } else if (apiKey.isNotBlank() && !apiKey.contains(".")) {
            Log.e(TAG, "❌ 主要问题: 智谱AI API密钥格式错误")
            Log.d(TAG, "💡 解决方案: 检查API密钥格式，应该是 xxxxx.xxxxx 格式")
        } else {
            currentGroupChat?.let { groupChat ->
                val zhipuMembers = groupChat.members.filter { 
                    it.type == MemberType.AI && it.aiServiceType == AIServiceType.ZHIPU_AI 
                }
                if (zhipuMembers.isEmpty()) {
                    Log.e(TAG, "❌ 主要问题: 群聊中没有智谱AI成员")
                    Log.d(TAG, "💡 解决方案: 重新创建群聊并添加智谱AI，或使用自动修复功能")
                } else {
                    Log.d(TAG, "✅ 配置看起来正常，如果仍有问题，请检查网络连接和API服务状态")
                }
            }
        }
        
        Log.d(TAG, "=== 诊断报告完成 ===")
    }

    /**
     * 加载群聊消息
     */
    private fun loadGroupChatMessages(groupId: String) {
        Log.d(TAG, "开始加载群聊消息，群聊ID: $groupId")
        val groupMessages = groupChatManager.getGroupMessages(groupId)
        Log.d(TAG, "从GroupChatManager获取到 ${groupMessages.size} 条消息")
        
        groupMessageAdapter.updateMessages(groupMessages)
        Log.d(TAG, "已更新适配器，适配器项目数: ${groupMessageAdapter.itemCount}")
        
        // 滚动到底部
        messagesRecyclerView.post {
            messagesRecyclerView.smoothScrollToPosition(groupMessageAdapter.itemCount - 1)
        }
    }
    
    // getAIMemberName方法已移除，由GroupChatManager处理


    /**
     * 获取指定服务的API密钥
     */
    private fun getApiKeyForService(serviceType: AIServiceType): String {
        val settingsManager = SettingsManager.getInstance(this)
        return when (serviceType) {
            AIServiceType.CHATGPT -> settingsManager.getString("chatgpt_api_key", "") ?: ""
            AIServiceType.CLAUDE -> settingsManager.getString("claude_api_key", "") ?: ""
            AIServiceType.GEMINI -> settingsManager.getString("gemini_api_key", "") ?: ""
            AIServiceType.WENXIN -> settingsManager.getString("wenxin_api_key", "") ?: ""
            AIServiceType.DEEPSEEK -> settingsManager.getDeepSeekApiKey()
            AIServiceType.QIANWEN -> settingsManager.getString("qianwen_api_key", "") ?: ""
            AIServiceType.XINGHUO -> settingsManager.getString("xinghuo_api_key", "") ?: ""
            AIServiceType.KIMI -> settingsManager.getString("kimi_api_key", "") ?: ""
            AIServiceType.ZHIPU_AI -> settingsManager.getString("zhipu_ai_api_key", "") ?: ""
        }
    }

    /**
     * 聊天消息数据类
     */
    data class ChatMessage(
        var content: String,
        val isFromUser: Boolean,
        val timestamp: Long
    )

    /**
     * 显示DeepSeek错误对话框
     */
    private fun showDeepSeekErrorDialog(errorMessage: String) {
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("DeepSeek API连接失败")

        val message = if (errorMessage.contains("401")) {
            "检测到401认证错误，这通常表示API密钥有问题。\n\n可能的原因：\n" +
            "• API密钥格式错误\n" +
            "• API密钥已过期\n" +
            "• API密钥包含多余的空格或换行符\n" +
            "• 账户余额不足\n\n" +
            "建议使用诊断工具进行详细检查。"
        } else {
            "DeepSeek API连接失败：$errorMessage\n\n建议使用诊断工具进行详细检查。"
        }

        dialogBuilder.setMessage(message)
        dialogBuilder.setPositiveButton("开始诊断") { _, _ ->
            // 启动DeepSeek诊断活动
            val intent = Intent(this, DeepSeekDiagnosticActivity::class.java)
            startActivity(intent)
        }
        dialogBuilder.setNegativeButton("稍后处理", null)
        dialogBuilder.setNeutralButton("查看设置") { _, _ ->
            // 跳转到AI API设置
            val intent = Intent(this, AIApiSettingsActivity::class.java)
            startActivity(intent)
        }

        dialogBuilder.show()
    }

    /**
     * 测试DeepSeek API连接
     */
    private fun testDeepSeekConnection(apiKey: String, callback: (Boolean, String) -> Unit) {
        // 使用新的测试方法
        deepSeekApiHelper.testDeepSeekConnection(apiKey) { success, message ->
            if (success) {
                Log.d(TAG, "DeepSeek API连接测试成功: $message")
                callback(true, message)
            } else {
                Log.e(TAG, "DeepSeek API连接测试失败: $message")
                callback(false, message)
            }
        }
    }

    /**
     * 发送消息到AI服务
     */
    private fun sendMessageToAI(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>>,
        aiMessage: ChatMessage
    ) {
        aiApiManager.sendMessage(
            serviceType = serviceType,
            message = message,
            conversationHistory = conversationHistory,
            callback = object : AIApiManager.StreamingCallback {
                override fun onChunkReceived(chunk: String) {
                    // 更新AI回复内容
                    runOnUiThread {
                        aiMessage.content += chunk
                        val cleanedContent = cleanAndFormatAIResponse(aiMessage.content)
                        messageAdapter.updateLastMessage(cleanedContent)
                    }
                }

                override fun onComplete(fullResponse: String) {
                    runOnUiThread {
                        aiMessage.content = cleanAndFormatAIResponse(fullResponse)
                        messageAdapter.updateLastMessage(aiMessage.content)

                        // 保存AI回复到统一存储
                        currentContact?.let { contact ->
                            val serviceType = getAIServiceType(contact) ?: AIServiceType.DEEPSEEK
                            
                            // 确保会话存在，如果不存在则创建
                            ensureSessionExists(contact.id, serviceType)
                            
                            chatDataManager.addMessage(contact.id, "assistant", aiMessage.content, serviceType)

                            // 同时保存到旧存储（向后兼容）
                            chatHistoryManager.saveMessages(contact.id, messages)
                            // 更新联系人的最后消息信息
                            updateContactLastMessage(contact, aiMessage.content)
                        }

                        isSending = false // 重置发送状态
                        sendButton.isEnabled = true // 重新启用发送按钮
                        Toast.makeText(this@ChatActivity, "回复完成", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        // 根据AI服务类型和错误内容提供更具体的错误信息
                        val errorMessage = when {
                            serviceType == AIServiceType.DEEPSEEK -> {
                                when {
                                    error.contains("401") -> {
                                        "DeepSeek API认证失败，请检查API密钥是否正确。\n\n💡 建议：\n• 确认API密钥格式正确\n• 检查账户余额是否充足\n• 使用诊断工具进行详细检查"
                                    }
                                    error.contains("403") -> {
                                        "DeepSeek API权限不足，请检查API密钥权限设置。"
                                    }
                                    error.contains("429") -> {
                                        "DeepSeek API请求频率过高，请稍后重试。"
                                    }
                                    error.contains("500") -> {
                                        "DeepSeek服务器暂时不可用，请稍后重试。"
                                    }
                                    error.contains("网络") || error.contains("连接") -> {
                                        "网络连接失败，请检查网络连接后重试。"
                                    }
                                    else -> {
                                        "DeepSeek API调用失败：$error\n\n如果问题持续存在，建议使用诊断工具检查配置。"
                                    }
                                }
                            }
                            serviceType == AIServiceType.CHATGPT -> {
                                when {
                                    error.contains("401") -> "ChatGPT API认证失败，请检查API密钥。"
                                    error.contains("429") -> "ChatGPT API请求频率过高，请稍后重试。"
                                    else -> "ChatGPT API调用失败：$error"
                                }
                            }
                            serviceType == AIServiceType.CLAUDE -> {
                                when {
                                    error.contains("401") -> "Claude API认证失败，请检查API密钥。"
                                    error.contains("429") -> "Claude API请求频率过高，请稍后重试。"
                                    else -> "Claude API调用失败：$error"
                                }
                            }
                            else -> {
                                "AI服务调用失败：$error"
                            }
                        }

                        aiMessage.content = errorMessage
                        messageAdapter.updateLastMessage(aiMessage.content)
                        isSending = false // 重置发送状态
                        sendButton.isEnabled = true // 重新启用发送按钮

                        // 对于DeepSeek的特定错误，显示诊断选项
                        if (serviceType == AIServiceType.DEEPSEEK && (error.contains("401") || error.contains("403"))) {
                            showDeepSeekErrorDialog(error)
                        } else {
                            Toast.makeText(this@ChatActivity, "发送失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    /**
     * 显示消息操作对话框
     */
    private fun showMessageOptionsDialog(message: ChatActivity.ChatMessage, position: Int) {
        if (!message.isFromUser) {
            // AI消息还可以重新生成
            val aiOptions = arrayOf("复制", "分享", "重新生成", "删除")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(aiOptions) { _, which ->
                    when (which) {
                        0 -> copyMessage(message)
                        1 -> shareMessage(message)
                        2 -> regenerateMessage(message, position)
                        3 -> deleteMessage(position)
                    }
                }
                .show()
        } else {
            // 用户消息可以编辑
            val userOptions = arrayOf("复制", "分享", "编辑", "删除")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(userOptions) { _, which ->
                    when (which) {
                        0 -> copyMessage(message)
                        1 -> shareMessage(message)
                        2 -> editMessage(message, position)
                        3 -> deleteMessage(position)
                    }
                }
                .show()
        }
    }

    /**
     * 复制消息
     */
    private fun copyMessage(message: ChatActivity.ChatMessage) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("聊天消息", message.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "消息已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    /**
     * 收藏消息
     */
    private fun favoriteMessage(position: Int) {
        // currentContact?.let { contact ->
        //     lifecycleScope.launch {
        //         chatHistoryManager.favoriteMessage(contact.id, position)
        //         runOnUiThread {
        //             Toast.makeText(this@ChatActivity, "消息已收藏", Toast.LENGTH_SHORT).show()
        //         }
        //     }
        // }
        Toast.makeText(this@ChatActivity, "收藏功能暂未实现", Toast.LENGTH_SHORT).show()
    }

    /**
     * 分享消息
     */
    private fun shareMessage(message: ChatActivity.ChatMessage) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message.content)
        }
        startActivity(Intent.createChooser(shareIntent, "分享消息"))
    }

    /**
     * 编辑消息
     */
    private fun editMessage(message: ChatActivity.ChatMessage, position: Int) {
        val editInput = EditText(this).apply {
            setText(message.content)
            setSelection(text.length)
            setPadding(50, 30, 50, 30)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("编辑消息")
            .setView(editInput)
            .setPositiveButton("发送") { _, _ ->
                val newContent = editInput.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    message.content = newContent
                    messageAdapter.notifyItemChanged(position)

                    // 删除此消息之后的所有AI回复
                    val messagesToRemove = mutableListOf<ChatMessage>()
                    for (i in position + 1 until messages.size) {
                        messagesToRemove.add(messages[i])
                    }
                    messages.removeAll(messagesToRemove)
                    messageAdapter.updateMessages(messages.toList())

                    // 重新发送消息到AI
                    resendMessageToAI(newContent)

                    Toast.makeText(this, "消息已编辑并重新发送", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 重新发送消息到AI（用于编辑后发送）
     */
    private fun resendMessageToAI(messageText: String) {
        if (isSending) return

        currentContact?.let { contact ->
            when (contact.type) {
                ContactType.AI -> {
                    // 获取AI服务类型
                    val serviceType = getAIServiceType(contact)
                    if (serviceType != null) {
                        // 检查是否有API密钥配置
                        val apiKey = getApiKeyForService(serviceType)
                        if (apiKey.isBlank()) {
                            // 没有API密钥，提示用户配置
                            val errorMessage = "请先在设置中配置${contact.name}的API密钥"
                            val errorMsg = ChatMessage(errorMessage, false, System.currentTimeMillis())
                            messages.add(errorMsg)
                            messageAdapter.updateMessages(messages.toList())

                            // 滚动到底部
                            messagesRecyclerView.post {
                                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                            }

                            Toast.makeText(this@ChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                            return@let
                        }

                        // 准备对话历史
                        val conversationHistory = messages.map {
                            mapOf("role" to if (it.isFromUser) "user" else "assistant", "content" to it.content)
                        }

                        // 添加AI回复占位符
                        val aiMessage = ChatMessage("正在思考中...", false, System.currentTimeMillis())
                        messages.add(aiMessage)
                        messageAdapter.updateMessages(messages.toList())

                        // 滚动到底部
                        messagesRecyclerView.post {
                            messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                        }

                        // 发送到AI服务
                        aiApiManager.sendMessage(
                            serviceType = serviceType,
                            message = messageText,
                            conversationHistory = conversationHistory,
                            callback = object : AIApiManager.StreamingCallback {
                                override fun onChunkReceived(chunk: String) {
                                    runOnUiThread {
                                        aiMessage.content += chunk
                                        val cleanedContent = cleanAndFormatAIResponse(aiMessage.content)
                                        messageAdapter.updateLastMessage(cleanedContent)
                                    }
                                }

                                override fun onComplete(fullResponse: String) {
                                    runOnUiThread {
                                        aiMessage.content = cleanAndFormatAIResponse(fullResponse)
                                        messageAdapter.updateLastMessage(aiMessage.content)

                                        // 保存AI回复到历史记录
                                        currentContact?.let { contact ->
                                            chatHistoryManager.saveMessages(contact.id, messages)
                                            // 更新联系人的最后消息信息
                                            updateContactLastMessage(contact, aiMessage.content)
                                        }

                                        Toast.makeText(this@ChatActivity, "回复完成", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onError(error: String) {
                                    runOnUiThread {
                                        aiMessage.content = "抱歉，发生了错误：$error"
                                        messageAdapter.updateLastMessage(aiMessage.content)
                                        Toast.makeText(this@ChatActivity, "发送失败：$error", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                }
                ContactType.GROUP -> {
                    // 群聊模式下的重发逻辑
                    handleGroupChatMessage(messageText)
                }
            }
        }
    }

    /**
     * 清理和优化AI回复内容
     */
    private fun cleanAndFormatAIResponse(content: String): String {
        var cleanedContent = content

        // 清理HTML标签（特别针对智谱AI的回复）
        cleanedContent = cleanHtmlTags(cleanedContent)

        // 去掉表情符号
        cleanedContent = cleanedContent.replace("[\uD83C-\uDBFF\uDC00-\uDFFF]+".toRegex(), "")

        // 改进的markdown格式化 - 保持一定的格式但简化
        // 将 **粗体** 转换为 【粗体】
        cleanedContent = cleanedContent.replace("\\*\\*(.*?)\\*\\*".toRegex(), "【$1】")

        // 将 *斜体* 转换为普通文本
        cleanedContent = cleanedContent.replace("\\*(.*?)\\*".toRegex(), "$1")

        // 将 `代码` 转换为 「代码」
        cleanedContent = cleanedContent.replace("`(.*?)`".toRegex(), "「$1」")

        // 将 ### 标题 转换为 ■ 标题
        cleanedContent = cleanedContent.replace("#{1,6}\\s*(.*)".toRegex(), "■ $1")

        // 改进列表格式
        // 将 - 列表项 转换为 • 列表项
        cleanedContent = cleanedContent.replace("^\\s*[-*+]\\s+".toRegex(RegexOption.MULTILINE), "• ")

        // 将 1. 数字列表 转换为更清晰的数字列表
        cleanedContent = cleanedContent.replace("^\\s*\\d+\\.\\s+".toRegex(RegexOption.MULTILINE), "◆ ")

        // 处理分段和格式
        // 将多个连续的换行转换为段落分隔
        cleanedContent = cleanedContent.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")

        // 在句号后添加适当的换行（如果后面紧跟大写字母或数字）
        cleanedContent = cleanedContent.replace("([。！？])([A-Z0-9一-龯])".toRegex(), "$1\n\n$2")

        // 在冒号后添加换行（用于问答格式）
        cleanedContent = cleanedContent.replace("([：:])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")

        // 去掉首尾空白
        cleanedContent = cleanedContent.trim()

        // 确保不超过3个连续的换行
        cleanedContent = cleanedContent.replace("\\n{3,}".toRegex(), "\n\n")

        return cleanedContent
    }

    /**
     * 清理HTML标签，保留自然语言内容
     */
    private fun cleanHtmlTags(content: String): String {
        var cleanedContent = content

        // 处理常见的HTML标签，保留内容
        // 处理段落标签
        cleanedContent = cleanedContent.replace("<p[^>]*>".toRegex(), "")
        cleanedContent = cleanedContent.replace("</p>".toRegex(), "\n\n")

        // 处理换行标签
        cleanedContent = cleanedContent.replace("<br[^>]*>".toRegex(), "\n")

        // 处理强调标签
        cleanedContent = cleanedContent.replace("<strong[^>]*>(.*?)</strong>".toRegex(), "【$1】")
        cleanedContent = cleanedContent.replace("<b[^>]*>(.*?)</b>".toRegex(), "【$1】")

        // 处理斜体标签
        cleanedContent = cleanedContent.replace("<em[^>]*>(.*?)</em>".toRegex(), "$1")
        cleanedContent = cleanedContent.replace("<i[^>]*>(.*?)</i>".toRegex(), "$1")

        // 处理代码标签
        cleanedContent = cleanedContent.replace("<code[^>]*>(.*?)</code>".toRegex(), "「$1」")
        cleanedContent = cleanedContent.replace("<pre[^>]*>(.*?)</pre>".toRegex(RegexOption.DOT_MATCHES_ALL), "\n「$1」\n")

        // 处理标题标签
        cleanedContent = cleanedContent.replace("<h[1-6][^>]*>(.*?)</h[1-6]>".toRegex(), "\n■ $1\n")

        // 处理列表标签
        cleanedContent = cleanedContent.replace("<ul[^>]*>".toRegex(), "")
        cleanedContent = cleanedContent.replace("</ul>".toRegex(), "\n")
        cleanedContent = cleanedContent.replace("<ol[^>]*>".toRegex(), "")
        cleanedContent = cleanedContent.replace("</ol>".toRegex(), "\n")
        cleanedContent = cleanedContent.replace("<li[^>]*>(.*?)</li>".toRegex(), "• $1\n")

        // 处理链接标签，保留链接文本
        cleanedContent = cleanedContent.replace("<a[^>]*>(.*?)</a>".toRegex(), "$1")

        // 处理div和span标签
        cleanedContent = cleanedContent.replace("<div[^>]*>".toRegex(), "")
        cleanedContent = cleanedContent.replace("</div>".toRegex(), "\n")
        cleanedContent = cleanedContent.replace("<span[^>]*>(.*?)</span>".toRegex(), "$1")

        // 清理剩余的HTML标签
        cleanedContent = cleanedContent.replace("<[^>]+>".toRegex(), "")

        // 解码HTML实体
        cleanedContent = cleanedContent.replace("&amp;".toRegex(), "&")
        cleanedContent = cleanedContent.replace("&lt;".toRegex(), "<")
        cleanedContent = cleanedContent.replace("&gt;".toRegex(), ">")
        cleanedContent = cleanedContent.replace("&quot;".toRegex(), "\"")
        cleanedContent = cleanedContent.replace("&#39;".toRegex(), "'")
        cleanedContent = cleanedContent.replace("&nbsp;".toRegex(), " ")

        // 清理多余的空白和换行
        cleanedContent = cleanedContent.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        cleanedContent = cleanedContent.trim()

        return cleanedContent
    }

    /**
     * 加载默认画像
     */
    private fun loadDefaultProfile() {
        try {
            // 获取默认的AI助手画像
            val defaultProfiles = listOf(
                "专业助手：我是一个专业的AI助手，可以帮助您解决各种问题。",
                "创意伙伴：我是您的创意伙伴，擅长头脑风暴和创意思考。",
                "学习导师：我是您的学习导师，可以帮助您理解复杂的概念。",
                "技术专家：我是技术专家，专注于编程和技术问题的解决。"
            )

            androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
                .setTitle("选择默认画像")
                .setItems(defaultProfiles.toTypedArray()) { _, which ->
                    val selectedProfile = defaultProfiles[which]
                    messageInput.setText(selectedProfile)
                    Toast.makeText(this, "已加载默认画像", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "加载默认画像失败", e)
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示搜索对话框
     */
    private fun showSearchDialog() {
        val searchInput = EditText(this).apply {
            hint = "搜索聊天记录..."
            setPadding(50, 30, 50, 30)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("搜索聊天记录")
            .setView(searchInput)
            .setPositiveButton("搜索") { _, _ ->
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchMessages(query)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 搜索消息
     */
    private fun searchMessages(query: String) {
        currentContact?.let { contact ->
            val searchResults = chatHistoryManager.searchMessages(contact.id, query)
            if (searchResults.isNotEmpty()) {
                showSearchResults(query, searchResults)
            } else {
                Toast.makeText(this, "未找到相关消息", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 显示搜索结果
     */
    private fun showSearchResults(query: String, results: List<Pair<Int, ChatMessage>>) {
        val resultTexts = results.map { (index, message) ->
            val prefix = if (message.isFromUser) "用户" else "AI"
            val content = message.content.let {
                if (it.length > 50) it.substring(0, 50) + "..." else it
            }
            "$prefix: $content"
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("搜索结果 (${results.size}条)")
            .setItems(resultTexts) { _, which ->
                val (messageIndex, _) = results[which]
                // 滚动到对应消息
                messagesRecyclerView.smoothScrollToPosition(messageIndex)
                Toast.makeText(this, "已定位到消息", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /**
     * 设置AI助手档案列表
     */
    private fun setupPromptList() {
        userProfiles.forEach { profile ->
            val promptButton = createPromptButton(profile)
            promptList.addView(promptButton)
        }
    }

    /**
     * 创建AI助手档案按钮
     */
    private fun createPromptButton(profile: PromptProfile): TextView {
        return TextView(this).apply {
            text = "${profile.icon} ${profile.name}"
            textSize = 12f
            setTextColor(resources.getColor(R.color.simple_mode_primary_light, null))
            background = resources.getDrawable(R.drawable.prompt_button_background, null)
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = 8
            }

            setOnClickListener {
                // 构建综合的prompt
                val combinedPrompt = buildPromptFromProfile(profile)
                messageInput.setText(combinedPrompt)
                promptListContainer.visibility = View.GONE
                // 不再自动请求焦点，避免弹出键盘
            }
        }
    }

    /**
     * 根据用户档案构建prompt
     */
    private fun buildPromptFromProfile(profile: PromptProfile): String {
        val promptBuilder = StringBuilder()

        // 基础角色设定
        promptBuilder.append("你是一个${profile.persona}，")

        // 专业领域
        if (profile.expertise.isNotBlank()) {
            promptBuilder.append("专长于${profile.expertise}，")
        }

        // 语调风格
        promptBuilder.append("请以${profile.tone}的语调")

        // 正式程度
        if (profile.formality != "适中") {
            promptBuilder.append("、${profile.formality}的方式")
        }

        // 回复长度
        promptBuilder.append("，用${profile.responseLength}的篇幅")

        // 输出格式
        if (profile.outputFormat.isNotBlank()) {
            promptBuilder.append("，按照${profile.outputFormat}的格式")
        }

        promptBuilder.append("来回复我的问题。")

        // 特殊要求
        if (profile.reasoning) {
            promptBuilder.append("请在回答中展示你的推理过程。")
        }

        if (profile.examples) {
            promptBuilder.append("如果可能，请提供相关的例子。")
        }

        // 自定义指令
        if (!profile.customInstructions.isNullOrBlank()) {
            promptBuilder.append("\n\n特别要求：${profile.customInstructions}")
        }

        promptBuilder.append("\n\n我的问题是：")

        return promptBuilder.toString()
    }

    /**
     * 重新生成消息
     */
    private fun regenerateMessage(message: ChatActivity.ChatMessage, position: Int) {
        // 找到对应的用户消息
        val userMessageIndex = findUserMessageIndex(position)
        if (userMessageIndex != -1) {
            val userMessage = messages[userMessageIndex]

            // 删除AI回复
            messages.removeAt(position)
            messageAdapter.updateMessages(messages.toList())

            // 重新发送用户消息
            sendMessageToAI(userMessage.content)
        }
    }

    /**
     * 查找对应的用户消息索引
     */
    private fun findUserMessageIndex(aiMessageIndex: Int): Int {
        for (i in aiMessageIndex - 1 downTo 0) {
            if (messages[i].isFromUser) {
                return i
            }
        }
        return -1
    }

    /**
     * 发送消息到AI（用于重新生成）
     */
    private fun sendMessageToAI(messageText: String) {
        currentContact?.let { contact ->
            when (contact.type) {
                ContactType.AI -> {
                    // 获取AI服务类型
                    val serviceType = getAIServiceType(contact)
                    if (serviceType != null) {
                        // 检查是否有API密钥配置
                        val apiKey = getApiKeyForService(serviceType)
                        if (apiKey.isBlank()) {
                            // 没有API密钥，提示用户配置
                            val errorMessage = "请先在设置中配置${contact.name}的API密钥"
                            val errorMsg = ChatMessage(errorMessage, false, System.currentTimeMillis())
                            messages.add(errorMsg)
                            messageAdapter.updateMessages(messages.toList())

                            // 滚动到底部
                            messagesRecyclerView.post {
                                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                            }

                            Toast.makeText(this@ChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                            return@let
                        }

                        // 准备对话历史
                        val conversationHistory = messages.map {
                            mapOf("role" to if (it.isFromUser) "user" else "assistant", "content" to it.content)
                        }

                        // 添加AI回复占位符
                        val aiMessage = ChatMessage("正在重新生成...", false, System.currentTimeMillis())
                        messages.add(aiMessage)
                        messageAdapter.updateMessages(messages.toList())

                        // 滚动到底部
                        messagesRecyclerView.post {
                            messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                        }

                        // 发送到AI服务
                        aiApiManager.sendMessage(
                            serviceType = serviceType,
                            message = messageText,
                            conversationHistory = conversationHistory,
                            callback = object : AIApiManager.StreamingCallback {
                                override fun onChunkReceived(chunk: String) {
                                    runOnUiThread {
                                        aiMessage.content += chunk
                                        val cleanedContent = cleanAndFormatAIResponse(aiMessage.content)
                                        messageAdapter.updateLastMessage(cleanedContent)
                                    }
                                }

                                override fun onComplete(fullResponse: String) {
                                    runOnUiThread {
                                        aiMessage.content = cleanAndFormatAIResponse(fullResponse)
                                        messageAdapter.updateLastMessage(aiMessage.content)

                                        // 保存AI回复到历史记录
                                        currentContact?.let { contact ->
                                            chatHistoryManager.saveMessages(contact.id, messages)
                                            // 更新联系人的最后消息信息
                                            updateContactLastMessage(contact, aiMessage.content)
                                        }

                                        Toast.makeText(this@ChatActivity, "重新生成完成", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onError(error: String) {
                                    runOnUiThread {
                                        aiMessage.content = "重新生成失败：$error"
                                        messageAdapter.updateLastMessage(aiMessage.content)
                                        Toast.makeText(this@ChatActivity, "重新生成失败：$error", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    } else {
                        // 如果无法识别AI服务类型，使用模拟回复
                        val responses = listOf(
                            "我理解您的问题，让我为您详细解答...",
                            "这是一个很有趣的问题，我的看法是...",
                            "根据我的分析，建议您考虑以下几个方面...",
                            "我可以为您提供更多相关信息..."
                        )
                        val response = responses.random()

                        messageInput.postDelayed({
                            val aiMessage = ChatMessage(response, false, System.currentTimeMillis())
                            messages.add(aiMessage)
                            messageAdapter.updateMessages(messages.toList())

                            // 保存聊天记录
                            currentContact?.let { contact ->
                                chatHistoryManager.saveMessages(contact.id, messages)
                            }

                            // 重置发送状态（这个在重新生成中不需要重置，因为不是主发送流程）

                            // 滚动到底部
                            messagesRecyclerView.post {
                                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                            }
                        }, 1000)
                    }
                }
                ContactType.GROUP -> {
                    // 群聊模式下的重新生成逻辑已移至GroupChatManager处理
                    Toast.makeText(this@ChatActivity, "群聊模式下请使用正常发送功能", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 删除消息
     */
    private fun deleteMessage(position: Int) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除这条消息吗？")
            .setPositiveButton("删除") { _, _ ->
                // currentContact?.let { contact ->
                //     lifecycleScope.launch {
                //         chatHistoryManager.deleteMessage(contact.id, position)
                //         runOnUiThread {
                //             messages.removeAt(position)
                //             messageAdapter.updateMessages(messages)
                //             Toast.makeText(this@ChatActivity, "消息已删除", Toast.LENGTH_SHORT).show()
                //         }
                //     }
                // }
                messages.removeAt(position)
                messageAdapter.updateMessages(messages.toList())
                Toast.makeText(this@ChatActivity, "消息已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 更新联系人的最后消息信息
     */
    private fun updateContactLastMessage(contact: ChatContact, lastMessage: String) {
        try {
            Log.d(TAG, "开始更新联系人最后消息: ${contact.name} (ID: ${contact.id}) - ${lastMessage.take(50)}...")
            
            // 加载当前保存的联系人数据
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_SAVED_CONTACTS, null)
            if (json != null) {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<ContactCategory>>() {}.type
                val categories: List<ContactCategory> = gson.fromJson(json, type)

                // 查找并更新对应的联系人
                var found = false
                val updatedCategories = categories.map { category ->
                    val updatedContacts = category.contacts.map { savedContact ->
                        if (savedContact.id == contact.id) {
                            Log.d(TAG, "找到匹配的联系人: ${savedContact.name} (${savedContact.id}) -> ${contact.name} (${contact.id})")
                            found = true
                            savedContact.copy(
                                lastMessage = lastMessage,
                                lastMessageTime = System.currentTimeMillis()
                            )
                        } else {
                            savedContact
                        }
                    }.toMutableList()
                    category.copy(contacts = updatedContacts)
                }

                if (!found) {
                    Log.w(TAG, "未找到匹配的联系人: ${contact.name} (${contact.id})")
                    // 打印所有可用的联系人ID用于调试
                    categories.forEach { category ->
                        category.contacts.forEach { savedContact ->
                            Log.d(TAG, "可用联系人: ${savedContact.name} (${savedContact.id})")
                        }
                    }
                }

                // 保存更新后的数据
                val updatedJson = gson.toJson(updatedCategories)
                prefs.edit().putString(KEY_SAVED_CONTACTS, updatedJson).apply()
                Log.d(TAG, "联系人最后消息已更新: ${contact.name}")
                
                // 发送广播通知简易模式更新数据
                notifySimpleModeUpdate(contact, lastMessage)
            } else {
                Log.w(TAG, "未找到保存的联系人数据")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新联系人最后消息失败", e)
        }
    }
    
    /**
     * 确保会话存在，如果不存在则创建
     */
    private fun ensureSessionExists(sessionId: String, serviceType: AIServiceType) {
        try {
            val existingMessages = chatDataManager.getMessages(sessionId, serviceType)
            if (existingMessages.isEmpty()) {
                Log.d(TAG, "会话 $sessionId 不存在或为空，检查是否需要迁移现有数据")
                
                // 检查当前messages列表中是否有数据需要迁移到ChatDataManager
                if (messages.isNotEmpty()) {
                    Log.d(TAG, "发现 ${messages.size} 条现有消息，迁移到ChatDataManager")
                    messages.forEach { chatMsg ->
                        val role = if (chatMsg.isFromUser) "user" else "assistant"
                        chatDataManager.addMessage(sessionId, role, chatMsg.content, serviceType)
                        Log.d(TAG, "迁移消息: $role - ${chatMsg.content.take(30)}...")
                    }
                }
            } else {
                Log.d(TAG, "会话 $sessionId 已存在，包含 ${existingMessages.size} 条消息")
                
                // 检查ChatDataManager中的消息是否比当前messages列表更完整
                if (existingMessages.size > messages.size) {
                    Log.d(TAG, "ChatDataManager中有更多消息，同步到当前列表")
                    messages.clear()
                    existingMessages.forEach { unifiedMsg ->
                        val chatMsg = ChatMessage(
                            content = unifiedMsg.content,
                            isFromUser = unifiedMsg.role == "user",
                            timestamp = unifiedMsg.timestamp
                        )
                        messages.add(chatMsg)
                    }
                    messages.sortBy { it.timestamp }
                    messageAdapter.updateMessages(messages.toList())
                    Log.d(TAG, "同步完成，现在共有 ${messages.size} 条消息")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "确保会话存在失败", e)
        }
    }
    
    /**
     * 通知简易模式更新AI对话数据
     */
    private fun notifySimpleModeUpdate(contact: ChatContact, lastMessage: String) {
        try {
            Log.d(TAG, "准备发送广播通知简易模式更新: ${contact.name} (${contact.id})")
            
            val intent = Intent("com.example.aifloatingball.AI_MESSAGE_UPDATED").apply {
                putExtra("contact_id", contact.id)
                putExtra("contact_name", contact.name)
                putExtra("last_message", lastMessage)
                putExtra("last_message_time", System.currentTimeMillis())
            }
            
            Log.d(TAG, "广播Intent内容: action=${intent.action}, contact_id=${intent.getStringExtra("contact_id")}, contact_name=${intent.getStringExtra("contact_name")}")
            
            sendBroadcast(intent)
            Log.d(TAG, "已发送广播通知简易模式更新: ${contact.name}")
        } catch (e: Exception) {
            Log.e(TAG, "发送广播通知失败", e)
        }
    }

    /**
     * 更新API状态指示器
     */
    private fun updateApiStatusIndicator() {
        currentContact?.let { contact ->
            if (contact.type == ContactType.AI) {
                val serviceType = getAIServiceType(contact)
                if (serviceType != null) {
                    val apiKey = getApiKeyForService(serviceType)
                    if (apiKey.isNotBlank()) {
                        // 有API密钥，检查连接状态
                        checkApiConnectionStatus(serviceType, apiKey)
                    } else {
                        // 没有API密钥
                        setApiStatusIndicator(ApiStatus.NO_API_KEY)
                    }
                } else {
                    // 无法识别的AI服务
                    setApiStatusIndicator(ApiStatus.UNKNOWN_SERVICE)
                }
            } else {
                // 非AI联系人，隐藏状态指示器
                apiStatusIndicator.visibility = View.GONE
            }
        }
    }

    /**
     * 检查API连接状态
     */
    private fun checkApiConnectionStatus(serviceType: AIServiceType, apiKey: String) {
        setApiStatusIndicator(ApiStatus.LOADING)
        
        when (serviceType) {
            AIServiceType.DEEPSEEK -> {
                testDeepSeekConnection(apiKey) { success, message ->
                    runOnUiThread {
                        if (success) {
                            setApiStatusIndicator(ApiStatus.CONNECTED)
                        } else {
                            setApiStatusIndicator(ApiStatus.ERROR)
                        }
                    }
                }
            }
            else -> {
                // 其他AI服务暂时显示为已连接
                setApiStatusIndicator(ApiStatus.CONNECTED)
            }
        }
    }

    /**
     * 设置API状态指示器
     */
    private fun setApiStatusIndicator(status: ApiStatus) {
        val (color, text) = when (status) {
            ApiStatus.CONNECTED -> Pair(R.color.api_status_connected, "●")
            ApiStatus.ERROR -> Pair(R.color.api_status_error, "●")
            ApiStatus.LOADING -> Pair(R.color.api_status_loading, "●")
            ApiStatus.NO_API_KEY -> Pair(R.color.api_status_unknown, "!")
            ApiStatus.UNKNOWN_SERVICE -> Pair(R.color.api_status_unknown, "?")
        }
        
        apiStatusIndicator.setTextColor(getColor(color))
        apiStatusIndicator.text = text
        apiStatusIndicator.visibility = View.VISIBLE
    }

    /**
     * API状态枚举
     */
    enum class ApiStatus {
        CONNECTED,      // 已连接
        ERROR,          // 连接错误
        LOADING,        // 正在检查
        NO_API_KEY,     // 无API密钥
        UNKNOWN_SERVICE // 未知服务
    }

    /**
     * 刷新聊天
     */
    private fun refreshChat() {
        // 重新加载聊天历史
        loadInitialMessages()
        
        // 更新API状态
        updateApiStatusIndicator()
        
        Toast.makeText(this, "聊天已刷新", Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示聊天设置
     */
    private fun showChatSettings() {
        val options = if (isGroupChatMode) {
            arrayOf("群聊设置", "AI API设置", "聊天设置", "导出设置", "清空聊天记录")
        } else {
            arrayOf("AI API设置", "聊天设置", "导出设置", "清空聊天记录")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("聊天设置")
            .setItems(options) { _, which ->
                if (isGroupChatMode) {
                    when (which) {
                        0 -> {
                            // 跳转到群聊设置
                            val intent = Intent(this, GroupChatSettingsActivity::class.java)
                            intent.putExtra(GroupChatSettingsActivity.EXTRA_GROUP_CONTACT, currentContact)
                            startActivityForResult(intent, REQUEST_GROUP_SETTINGS)
                        }
                        1 -> {
                            // 跳转到AI API设置
                            val intent = Intent(this, AIApiSettingsActivity::class.java)
                            startActivity(intent)
                        }
                        2 -> {
                            // 显示聊天设置对话框
                            showChatSettingsDialog()
                        }
                        3 -> {
                            // 显示导出设置对话框
                            showExportSettingsDialog()
                        }
                        4 -> {
                            // 显示清空聊天确认对话框
                            showClearChatDialog()
                        }
                    }
                } else {
                    when (which) {
                        0 -> {
                            // 跳转到AI API设置
                            val intent = Intent(this, AIApiSettingsActivity::class.java)
                            startActivity(intent)
                        }
                        1 -> {
                            // 显示聊天设置对话框
                            showChatSettingsDialog()
                        }
                        2 -> {
                            // 显示导出设置对话框
                            showExportSettingsDialog()
                        }
                        3 -> {
                            // 显示清空聊天确认对话框
                            showClearChatDialog()
                        }
                    }
                }
            }
            .show()
    }

    /**
     * 显示聊天设置对话框
     */
    private fun showChatSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_chat_settings, null)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("聊天设置")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                // 保存设置
                saveChatSettings(view)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 保存聊天设置
     */
    private fun saveChatSettings(view: View) {
        // 这里可以添加保存聊天设置的逻辑
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示导出设置对话框
     */
    private fun showExportSettingsDialog() {
        val options = arrayOf("导出为文本文件", "导出为HTML", "导出为Markdown", "分享聊天记录")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("导出设置")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportChatAsText()
                    1 -> exportChatAsHTML()
                    2 -> exportChatAsMarkdown()
                    3 -> shareChatHistory()
                }
            }
            .show()
    }



    /**
     * 显示清空聊天确认对话框
     */
    private fun showClearChatDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_MaterialDialog)
            .setTitle("清空聊天记录")
            .setMessage("确定要清空所有聊天记录吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                clearChatHistory()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 清空聊天历史
     */
    private fun clearChatHistory() {
        currentContact?.let { contact ->
            // 清空内存中的消息
            messages.clear()
            messageAdapter.updateMessages(messages.toList())
            
            // 清空本地存储的聊天记录
            chatHistoryManager.clearMessages(contact.id)
            
            Toast.makeText(this, "聊天记录已清空", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 导出聊天历史
     */
    private fun exportChatHistory() {
        showExportSettingsDialog()
    }

    /**
     * 导出聊天为文本文件
     */
    private fun exportChatAsText() {
        currentContact?.let { contact ->
            val content = buildString {
                appendLine("聊天记录 - ${contact.name}")
                appendLine("导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("=".repeat(50))
                appendLine()
                
                messages.forEach { message ->
                    val prefix = if (message.isFromUser) "用户" else "AI"
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
                    appendLine("[$time] $prefix: ${message.content}")
                    appendLine()
                }
            }
            
            // 保存到文件并分享
            shareTextContent(content, "聊天记录.txt")
        }
    }

    /**
     * 导出聊天为HTML
     */
    private fun exportChatAsHTML() {
        currentContact?.let { contact ->
            val content = buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html>")
                appendLine("<head>")
                appendLine("<meta charset=\"UTF-8\">")
                appendLine("<title>聊天记录 - ${contact.name}</title>")
                appendLine("<style>")
                appendLine("body { font-family: Arial, sans-serif; margin: 20px; }")
                appendLine(".message { margin: 10px 0; padding: 10px; border-radius: 5px; }")
                appendLine(".user { background-color: #e3f2fd; text-align: right; }")
                appendLine(".ai { background-color: #f3e5f5; text-align: left; }")
                appendLine(".time { font-size: 12px; color: #666; }")
                appendLine("</style>")
                appendLine("</head>")
                appendLine("<body>")
                appendLine("<h1>聊天记录 - ${contact.name}</h1>")
                appendLine("<p>导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}</p>")
                
                messages.forEach { message ->
                    val cssClass = if (message.isFromUser) "user" else "ai"
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
                    appendLine("<div class=\"message $cssClass\">")
                    appendLine("<div class=\"time\">$time</div>")
                    appendLine("<div>${message.content.replace("\n", "<br>")}</div>")
                    appendLine("</div>")
                }
                
                appendLine("</body>")
                appendLine("</html>")
            }
            
            shareTextContent(content, "聊天记录.html")
        }
    }

    /**
     * 导出聊天为Markdown
     */
    private fun exportChatAsMarkdown() {
        currentContact?.let { contact ->
            val content = buildString {
                appendLine("# 聊天记录 - ${contact.name}")
                appendLine()
                appendLine("**导出时间:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine()
                appendLine("---")
                appendLine()
                
                messages.forEach { message ->
                    val prefix = if (message.isFromUser) "**用户**" else "**AI**"
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
                    appendLine("### [$time] $prefix")
                    appendLine()
                    appendLine(message.content)
                    appendLine()
                }
            }
            
            shareTextContent(content, "聊天记录.md")
        }
    }

    /**
     * 分享聊天记录
     */
    private fun shareChatHistory() {
        currentContact?.let { contact ->
            val content = buildString {
                appendLine("聊天记录 - ${contact.name}")
                appendLine("导出时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine()
                
                messages.forEach { message ->
                    val prefix = if (message.isFromUser) "用户" else "AI"
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
                    appendLine("[$time] $prefix: ${message.content}")
                    appendLine()
                }
            }
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_SUBJECT, "聊天记录 - ${contact.name}")
            }
            startActivity(Intent.createChooser(shareIntent, "分享聊天记录"))
        }
    }

    /**
     * 分享文本内容
     */
    private fun shareTextContent(content: String, filename: String) {
        try {
            // 创建临时文件
            val file = java.io.File(cacheDir, filename)
            file.writeText(content, Charsets.UTF_8)
            
            // 分享文件
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, filename)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "导出聊天记录"))
        } catch (e: Exception) {
            Log.e(TAG, "导出聊天记录失败", e)
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 移除群聊监听器
        groupChatManager.removeGroupChatListener(this)
        // 释放AI API管理器资源
        if (::aiApiManager.isInitialized) {
            aiApiManager.destroy()
        }
        // 清理实时数据更新机制
        try {
            aiChatUpdateReceiver?.let { receiver ->
                unregisterReceiver(receiver)
            }
            stopFileSyncMonitoring()
            Log.d(TAG, "ChatActivity实时数据更新机制已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理ChatActivity实时数据更新机制失败", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_GROUP_SETTINGS && resultCode == RESULT_OK) {
            // 群聊设置返回，更新联系人信息
            data?.getParcelableExtra<ChatContact>(GroupChatSettingsActivity.EXTRA_GROUP_CONTACT)?.let { updatedContact ->
                currentContact = updatedContact
                contactNameText.text = updatedContact.name
                contactStatusText.text = updatedContact.description ?: "群聊"
                
                // 如果群聊被删除，关闭当前页面
                if (data.getBooleanExtra("group_deleted", false)) {
                    Toast.makeText(this, "群聊已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 页面恢复时更新API状态
        updateApiStatusIndicator()
    }

    /**
     * 处理从小组件传入的自动发送消息
     */
    private fun handleAutoSendMessage() {
        try {
            val autoSendMessage = intent.getStringExtra("auto_send_message")
            val activateInputOnly = intent.getBooleanExtra("activate_input_only", false)
            val source = intent.getStringExtra("source")

            if (!autoSendMessage.isNullOrEmpty()) {
                Log.d(TAG, "收到自动发送消息请求: message='$autoSendMessage', source='$source'")

                // 延迟一下确保界面完全加载
                messageInput.postDelayed({
                    // 设置消息到输入框
                    messageInput.setText(autoSendMessage)

                    // 自动发送消息
                    sendMessage()

                    // 显示提示
                    if (source?.contains("桌面小组件") == true) {
                        Toast.makeText(this, "来自桌面小组件的搜索", Toast.LENGTH_SHORT).show()
                    }
                }, 500)
            } else if (activateInputOnly) {
                Log.d(TAG, "收到激活输入状态请求: source='$source'")

                // 不再自动激活输入法，只显示提示
                if (source?.contains("桌面小组件") == true) {
                    Toast.makeText(this, "请输入您的问题", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理自动发送消息失败", e)
        }
    }
    
    /**
     * 设置实时数据更新机制
     */
    private fun setupRealtimeDataUpdate() {
        try {
            // 1. 设置广播接收器
            setupAIChatUpdateReceiver()
            
            // 2. 设置文件同步监听
            startFileSyncMonitoring()
            
            Log.d(TAG, "实时数据更新机制已启动")
        } catch (e: Exception) {
            Log.e(TAG, "设置实时数据更新机制失败", e)
        }
    }
    
    /**
     * 设置AI对话更新广播接收器
     */
    private fun setupAIChatUpdateReceiver() {
        try {
            aiChatUpdateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == "com.example.aifloatingball.AI_CHAT_UPDATED") {
                        val action = intent.getStringExtra("action")
                        val serviceTypeName = intent.getStringExtra("ai_service_type")
                        val sessionId = intent.getStringExtra("session_id")
                        
                        // 如果是全局刷新广播，直接刷新当前聊天
                        if (action == "refresh_all") {
                            refreshChatMessages()
                            return
                        }
                        
                        // 检查是否是当前联系人的更新
                        currentContact?.let { contact ->
                            val currentServiceType = getAIServiceType(contact)
                            if (currentServiceType?.name == serviceTypeName && contact.id == sessionId) {
                                refreshChatMessages()
                            }
                        }
                    }
                }
            }
            
            val filter = IntentFilter("com.example.aifloatingball.AI_CHAT_UPDATED")
            registerReceiver(aiChatUpdateReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "注册ChatActivity AI对话更新广播接收器失败", e)
        }
    }
    
    /**
     * 启动文件同步监听
     */
    private fun startFileSyncMonitoring() {
        try {
            fileSyncHandler = Handler(Looper.getMainLooper())
            fileSyncRunnable = object : Runnable {
                override fun run() {
                    checkSyncFiles()
                    fileSyncHandler?.postDelayed(this, 5000) // 每5秒检查一次
                }
            }
            fileSyncHandler?.post(fileSyncRunnable!!)
            Log.d(TAG, "ChatActivity文件同步监听已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动ChatActivity文件同步监听失败", e)
        }
    }
    
    /**
     * 检查同步文件
     */
    private fun checkSyncFiles() {
        try {
            currentContact?.let { contact ->
                val serviceType = getAIServiceType(contact)
                if (serviceType != null) {
                    val serviceName = when (serviceType) {
                        AIServiceType.DEEPSEEK -> "deepseek"
                        AIServiceType.ZHIPU_AI -> "zhipu_ai"
                        AIServiceType.KIMI -> "kimi"
                        AIServiceType.CHATGPT -> "chatgpt"
                        AIServiceType.CLAUDE -> "claude"
                        AIServiceType.GEMINI -> "gemini"
                        AIServiceType.WENXIN -> "wenxin"
                        AIServiceType.QIANWEN -> "qianwen"
                        AIServiceType.XINGHUO -> "xinghuo"
                        else -> serviceType.name.lowercase()
                    }
                    
                    val syncFile = File(filesDir, "ai_sync_$serviceName.json")
                    if (syncFile.exists()) {
                        val lastModified = syncFile.lastModified()
                        val currentTime = System.currentTimeMillis()
                        
                        // 如果文件在最近10秒内被修改，说明有新的同步数据
                        if (currentTime - lastModified < 10000L) {
                            Log.d(TAG, "ChatActivity检测到同步文件更新: $serviceName")
                            processSyncFile(syncFile, serviceName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ChatActivity检查同步文件失败", e)
        }
    }
    
    /**
     * 处理同步文件
     */
    private fun processSyncFile(syncFile: File, serviceName: String) {
        try {
            val jsonContent = syncFile.readText()
            val jsonObject = JSONObject(jsonContent)
            
            val sessionId = jsonObject.getString("sessionId")
            val messagesArray = jsonObject.getJSONArray("messages")
            
            Log.d(TAG, "ChatActivity处理同步文件: 会话ID=$sessionId, 消息数=${messagesArray.length()}")
            
            // 检查是否是当前联系人的会话
            currentContact?.let { contact ->
                if (contact.id == sessionId) {
                    Log.d(TAG, "检测到当前联系人的同步文件，刷新聊天记录")
                    refreshChatMessages()
                }
            }
            
            // 删除同步文件
            syncFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "ChatActivity处理同步文件失败", e)
        }
    }
    
    /**
     * 刷新聊天消息
     */
    private fun refreshChatMessages() {
        try {
            currentContact?.let { contact ->
                if (!isGroupChatMode) {
                    // 单聊模式：重新加载消息
                    val serviceType = getAIServiceType(contact)
                    
                    // 强制重新加载所有数据
                    chatDataManager.forceReloadAllData()
                    
                    // 再次加载特定AI服务的数据
                    if (serviceType != null) {
                        chatDataManager.loadDataForAIService(serviceType)
                    }
                    
                    val unifiedMessages = if (serviceType != null) {
                        chatDataManager.getMessages(contact.id, serviceType)
                    } else {
                        chatDataManager.getMessages(contact.id)
                    }
                    
                    if (unifiedMessages.isNotEmpty()) {
                        messages.clear()
                        // 转换ChatDataManager.ChatMessage到ChatActivity.ChatMessage
                        unifiedMessages.forEach { unifiedMsg ->
                            val chatMsg = ChatMessage(
                                content = unifiedMsg.content,
                                isFromUser = unifiedMsg.role == "user",
                                timestamp = unifiedMsg.timestamp
                            )
                            messages.add(chatMsg)
                        }
                        
                        runOnUiThread {
                            messageAdapter.updateMessages(messages.toList())
                            // 滚动到底部
                            if (messages.isNotEmpty()) {
                                messagesRecyclerView.post {
                                    messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新聊天消息失败", e)
        }
    }
    
    /**
     * 调试方法：使用与SimpleModeActivity相同的逻辑获取消息
     */
    private fun getLastChatMessageFromSimpleMode(aiName: String): String {
        try {
            // 使用与灵动岛相同的ID生成逻辑
            val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
                aiName
            } else {
                aiName.lowercase()
            }
            val contactId = "ai_${processedName.replace(" ", "_")}"
            
            // 获取对应的AI服务类型
            val serviceType = when (aiName) {
                "DeepSeek" -> AIServiceType.DEEPSEEK
                "ChatGPT" -> AIServiceType.CHATGPT
                "Claude" -> AIServiceType.CLAUDE
                "Gemini" -> AIServiceType.GEMINI
                "智谱AI" -> AIServiceType.ZHIPU_AI
                "文心一言" -> AIServiceType.WENXIN
                "通义千问" -> AIServiceType.QIANWEN
                "讯飞星火" -> AIServiceType.XINGHUO
                "Kimi" -> AIServiceType.KIMI
                else -> null
            }
            
            if (serviceType != null) {
                val messages = chatDataManager.getMessages(contactId, serviceType)
                if (messages.isNotEmpty()) {
                    return messages.last().content
                }
            }
            
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "获取简易模式消息失败", e)
            return ""
        }
    }

    /**
     * 强制从SimpleModeActivity的逻辑同步数据
     */
    private fun forceSyncFromSimpleMode(contact: ChatContact, serviceType: AIServiceType?) {
        try {
            Log.d(TAG, "开始强制同步 ${contact.name} 的数据")
            
            // 使用与SimpleModeActivity相同的逻辑获取所有消息
            val processedName = if (contact.name.contains(Regex("[\\u4e00-\\u9fff]"))) {
                contact.name
            } else {
                contact.name.lowercase()
            }
            val contactId = "ai_${processedName.replace(" ", "_")}"
            
            val aiServiceType = serviceType ?: when (contact.name) {
                "DeepSeek" -> AIServiceType.DEEPSEEK
                "ChatGPT" -> AIServiceType.CHATGPT
                "Claude" -> AIServiceType.CLAUDE
                "Gemini" -> AIServiceType.GEMINI
                "智谱AI" -> AIServiceType.ZHIPU_AI
                "文心一言" -> AIServiceType.WENXIN
                "通义千问" -> AIServiceType.QIANWEN
                "讯飞星火" -> AIServiceType.XINGHUO
                "Kimi" -> AIServiceType.KIMI
                else -> AIServiceType.DEEPSEEK
            }
            
            // 再次尝试获取数据，使用更强的重新加载
            chatDataManager.forceReloadAllData()
            Thread.sleep(100) // 给一点时间让数据加载完成
            
            val allMessages = chatDataManager.getMessages(contactId, aiServiceType)
            Log.d(TAG, "强制同步 - 获取到 ${allMessages.size} 条消息")
            
            if (allMessages.isNotEmpty()) {
                messages.clear()
                allMessages.forEach { unifiedMsg ->
                    val chatMsg = ChatMessage(
                        content = unifiedMsg.content,
                        isFromUser = unifiedMsg.role == "user",
                        timestamp = unifiedMsg.timestamp
                    )
                    messages.add(chatMsg)
                }
                
                runOnUiThread {
                    messageAdapter.updateMessages(messages.toList())
                    // 滚动到底部
                    if (messages.isNotEmpty()) {
                        messagesRecyclerView.post {
                            messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                }
                
                Log.d(TAG, "强制同步成功，加载了 ${allMessages.size} 条消息")
            } else {
                Log.d(TAG, "强制同步失败，仍然没有找到数据")
            }
        } catch (e: Exception) {
            Log.e(TAG, "强制同步失败", e)
        }
    }

    /**
     * 停止文件同步监听
     */
    private fun stopFileSyncMonitoring() {
        try {
            fileSyncRunnable?.let { runnable ->
                fileSyncHandler?.removeCallbacks(runnable)
            }
            fileSyncHandler = null
            fileSyncRunnable = null
            Log.d(TAG, "ChatActivity文件同步监听已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止ChatActivity文件同步监听失败", e)
        }
    }
    
    // GroupChatListener接口实现
    override fun onMessageAdded(groupId: String, message: GroupChatMessage) {
        if (isGroupChatMode && currentGroupChat?.id == groupId) {
            runOnUiThread {
                groupMessageAdapter.addMessage(message)
                messagesRecyclerView.scrollToPosition(groupMessageAdapter.itemCount - 1)
            }
        }
    }
    
    override fun onMessageUpdated(groupId: String, messageIndex: Int, message: GroupChatMessage) {
        if (isGroupChatMode && currentGroupChat?.id == groupId) {
            runOnUiThread {
                groupMessageAdapter.updateMessage(messageIndex, message)
            }
        }
    }
    
    override fun onAIReplyStatusChanged(groupId: String, aiId: String, status: AIReplyStatus, message: String?) {
        if (isGroupChatMode && currentGroupChat?.id == groupId) {
            runOnUiThread {
                when (status) {
                    AIReplyStatus.TYPING -> {
                        // 显示正在回复状态
                        replyStatusIndicator.text = "$aiId 正在回复..."
                        replyStatusIndicator.visibility = View.VISIBLE
                    }
                    AIReplyStatus.COMPLETED -> {
                        // 隐藏回复状态
                        replyStatusIndicator.visibility = View.GONE
                    }
                    AIReplyStatus.ERROR -> {
                        // 显示错误状态
                        replyStatusIndicator.text = "$aiId 回复失败"
                        replyStatusIndicator.visibility = View.VISIBLE
                        // 3秒后隐藏
                        replyStatusIndicator.postDelayed({
                            replyStatusIndicator.visibility = View.GONE
                        }, 3000)
                    }
                    else -> {
                        // 其他状态隐藏指示器
                        replyStatusIndicator.visibility = View.GONE
                    }
                }
            }
        }
    }
    
    override fun onGroupChatUpdated(groupChat: GroupChat) {
        if (isGroupChatMode && currentGroupChat?.id == groupChat.id) {
            runOnUiThread {
                currentGroupChat = groupChat
                contactStatusText.text = "群聊 · ${groupChat.members.size}个成员"
            }
        }
    }
    
    override fun onAIReplyContentUpdated(groupId: String, messageIndex: Int, aiId: String, content: String) {
        if (isGroupChatMode && currentGroupChat?.id == groupId) {
            runOnUiThread {
                // 更新适配器中的AI回复内容
                groupMessageAdapter.updateAIStreamingReply(messageIndex, aiId, content)
            }
        }
    }
    
    // 群聊消息操作方法实现
    private fun copyGroupMessage(message: GroupChatMessage) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("群聊消息", message.content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "消息已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    private fun regenerateGroupMessage(message: GroupChatMessage) {
        if (message.messageType == GroupMessageType.TEXT && message.senderType == MemberType.USER) {
            // 重新发送用户消息，触发AI回复
            currentGroupChat?.let { groupChat ->
                lifecycleScope.launch {
                    try {
                        groupChatManager.sendUserMessage(groupChat.id, message.content)
                        Toast.makeText(this@ChatActivity, "正在重新生成回复...", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "重新生成消息失败", e)
                        Toast.makeText(this@ChatActivity, "重新生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun copyAIReply(aiName: String, replyContent: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("AI回复", replyContent)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "AI回复已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    private fun regenerateAIReply(message: GroupChatMessage, aiName: String) {
        currentGroupChat?.let { groupChat ->
            lifecycleScope.launch {
                try {
                    // 重新生成指定AI的回复
                    groupChatManager.regenerateAIReply(groupChat.id, message.id, aiName)
                    Toast.makeText(this@ChatActivity, "正在重新生成${aiName}的回复...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "重新生成AI回复失败", e)
                    Toast.makeText(this@ChatActivity, "重新生成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun likeAIReply(message: GroupChatMessage, aiName: String) {
        // 实现点赞功能
        Toast.makeText(this, "已点赞${aiName}的回复", Toast.LENGTH_SHORT).show()
        // TODO: 可以在这里添加点赞数据的持久化逻辑
    }
    
    private fun deleteAIReply(message: GroupChatMessage, aiName: String) {
        // 显示确认删除对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除AI回复")
            .setMessage("确定要删除${aiName}的回复吗？")
            .setPositiveButton("删除") { _, _ ->
                currentGroupChat?.let { groupChat ->
                    try {
                        // 暂不支持删除单个AI回复
                        Toast.makeText(this@ChatActivity, "删除功能暂未实现", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "删除AI回复失败", e)
                        Toast.makeText(this@ChatActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}