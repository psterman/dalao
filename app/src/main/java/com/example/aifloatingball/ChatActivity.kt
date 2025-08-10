package com.example.aifloatingball

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.example.aifloatingball.model.AIPrompt
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.manager.SimpleChatHistoryManager
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.model.ContactCategory
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.manager.DeepSeekApiHelper
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_CONTACT = "extra_contact"
        private const val CONTACTS_PREFS_NAME = "chat_contacts"
        private const val KEY_SAVED_CONTACTS = "saved_contacts"
    }

    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var searchButton: ImageButton
    private lateinit var contactNameText: TextView
    private lateinit var contactStatusText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView

    // 新增按钮声明
    private lateinit var apiStatusIndicator: TextView
    private lateinit var refreshButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var voiceInputButton: ImageButton
    private lateinit var imageInputButton: ImageButton
    private lateinit var fileInputButton: ImageButton
    private lateinit var clearChatButton: ImageButton
    private lateinit var exportChatButton: ImageButton

    private var currentContact: ChatContact? = null
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var aiApiManager: AIApiManager
    private lateinit var deepSeekApiHelper: DeepSeekApiHelper
    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var promptListContainer: View
    private lateinit var promptList: LinearLayout
    private lateinit var settingsManager: SettingsManager
    private var userProfiles: List<PromptProfile> = emptyList()
    private lateinit var chatHistoryManager: SimpleChatHistoryManager
    private var isSending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 先初始化管理器
        aiApiManager = AIApiManager(this)
        deepSeekApiHelper = DeepSeekApiHelper(this)
        chatHistoryManager = SimpleChatHistoryManager(this)
        settingsManager = SettingsManager.getInstance(this)

        // 加载用户档案
        userProfiles = settingsManager.getAllPromptProfiles()

        initializeViews()
        loadContactData()
        setupListeners()
        loadInitialMessages()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchButton = findViewById(R.id.search_button)
        contactNameText = findViewById(R.id.contact_name)
        contactStatusText = findViewById(R.id.contact_status)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        messagesRecyclerView = findViewById(R.id.messages_recycler_view)

        // 初始化新增按钮
        apiStatusIndicator = findViewById(R.id.api_status_indicator)
        refreshButton = findViewById(R.id.refresh_button)
        settingsButton = findViewById(R.id.settings_button)
        voiceInputButton = findViewById(R.id.voice_input_button)
        imageInputButton = findViewById(R.id.image_input_button)
        fileInputButton = findViewById(R.id.file_input_button)
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
            contactStatusText.text = if (contact.isOnline) "在线" else "离线"

            // 设置标题栏颜色
            when (contact.type) {
                ContactType.AI -> {
                    contactStatusText.setTextColor(getColor(R.color.ai_icon_color))
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
        voiceInputButton.setOnClickListener {
            showVoiceInputDialog()
        }

        imageInputButton.setOnClickListener {
            showImageInputDialog()
        }

        fileInputButton.setOnClickListener {
            showFileInputDialog()
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
        // 加载聊天历史记录
        currentContact?.let { contact ->
            val historyMessages = chatHistoryManager.loadMessages(contact.id)
            if (historyMessages.isNotEmpty()) {
                messages.clear()
                messages.addAll(historyMessages)
                messageAdapter.updateMessages(messages)

                // 滚动到底部
                messagesRecyclerView.post {
                    messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isNotEmpty() && !isSending) {
            isSending = true
            sendButton.isEnabled = false // 禁用发送按钮
            // 添加用户消息
            val userMessage = ChatMessage(messageText, true, System.currentTimeMillis())
            messages.add(userMessage)
            messageAdapter.addMessage(userMessage)

            // 保存聊天记录
            currentContact?.let { contact ->
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
                                messageAdapter.addMessage(errorMsg)

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
                            messageAdapter.addMessage(aiMessage)

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
                                messageAdapter.addMessage(aiResponse)

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
     * 根据联系人信息获取AI服务类型
     */
    private fun getAIServiceType(contact: ChatContact): AIServiceType? {
        return when (contact.name.lowercase()) {
            "chatgpt", "gpt" -> AIServiceType.CHATGPT
            "claude" -> AIServiceType.CLAUDE
            "gemini" -> AIServiceType.GEMINI
            "文心一言", "wenxin" -> AIServiceType.WENXIN
            "deepseek" -> AIServiceType.DEEPSEEK
            "通义千问", "qianwen" -> AIServiceType.QIANWEN
            "讯飞星火", "xinghuo" -> AIServiceType.XINGHUO
            "kimi" -> AIServiceType.KIMI
            "智谱ai", "zhipu", "glm" -> AIServiceType.ZHIPU_AI
            else -> null
        }
    }

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
            AIServiceType.DEEPSEEK -> settingsManager.getString("deepseek_api_key", "") ?: ""
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

                        // 保存AI回复到历史记录
                        currentContact?.let { contact ->
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
                        aiMessage.content = "抱歉，发生了错误：$error"
                        messageAdapter.updateLastMessage(aiMessage.content)
                        isSending = false // 重置发送状态
                        sendButton.isEnabled = true // 重新启用发送按钮
                        Toast.makeText(this@ChatActivity, "发送失败：$error", Toast.LENGTH_LONG).show()
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
                    messageAdapter.updateMessages(messages)

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
                            messageAdapter.addMessage(errorMsg)

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
                        messageAdapter.addMessage(aiMessage)

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
            }
        }
    }

    /**
     * 清理和优化AI回复内容
     */
    private fun cleanAndFormatAIResponse(content: String): String {
        var cleanedContent = content

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
                messageInput.requestFocus()
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
            messageAdapter.updateMessages(messages)

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
                            messageAdapter.addMessage(errorMsg)

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
                        messageAdapter.addMessage(aiMessage)

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
                            messageAdapter.addMessage(aiMessage)

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
                messageAdapter.updateMessages(messages)
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
            // 加载当前保存的联系人数据
            val prefs = getSharedPreferences(CONTACTS_PREFS_NAME, MODE_PRIVATE)
            val json = prefs.getString(KEY_SAVED_CONTACTS, null)
            if (json != null) {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<ContactCategory>>() {}.type
                val categories: List<ContactCategory> = gson.fromJson(json, type)

                // 查找并更新对应的联系人
                val updatedCategories = categories.map { category ->
                    val updatedContacts = category.contacts.map { savedContact ->
                        if (savedContact.id == contact.id) {
                            savedContact.copy(
                                lastMessage = lastMessage,
                                lastMessageTime = System.currentTimeMillis()
                            )
                        } else {
                            savedContact
                        }
                    }
                    category.copy(contacts = updatedContacts)
                }

                // 保存更新后的数据
                val updatedJson = gson.toJson(updatedCategories)
                prefs.edit().putString(KEY_SAVED_CONTACTS, updatedJson).apply()
                Log.d(TAG, "联系人最后消息已更新: ${contact.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新联系人最后消息失败", e)
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
        val options = arrayOf("AI API设置", "聊天设置", "导出设置", "清空聊天记录")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("聊天设置")
            .setItems(options) { _, which ->
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
     * 显示语音输入对话框
     */
    private fun showVoiceInputDialog() {
        Toast.makeText(this, "语音输入功能开发中...", Toast.LENGTH_SHORT).show()
        // TODO: 实现语音输入功能
    }

    /**
     * 显示图片输入对话框
     */
    private fun showImageInputDialog() {
        Toast.makeText(this, "图片输入功能开发中...", Toast.LENGTH_SHORT).show()
        // TODO: 实现图片输入功能
    }

    /**
     * 显示文件输入对话框
     */
    private fun showFileInputDialog() {
        Toast.makeText(this, "文件输入功能开发中...", Toast.LENGTH_SHORT).show()
        // TODO: 实现文件输入功能
    }

    /**
     * 显示清空聊天确认对话框
     */
    private fun showClearChatDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
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
            messageAdapter.updateMessages(messages)
            
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
        // 释放AI API管理器资源
        if (::aiApiManager.isInitialized) {
            aiApiManager.destroy()
        }
    }

    override fun onResume() {
        super.onResume()
        // 页面恢复时更新API状态
        updateApiStatusIndicator()
    }
}