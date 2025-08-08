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
// import com.example.aifloatingball.manager.ChatHistoryManager
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.manager.DeepSeekApiHelper
import com.example.aifloatingball.SettingsManager
import com.google.android.material.button.MaterialButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_CONTACT = "extra_contact"
    }

    private lateinit var backButton: ImageButton
    private lateinit var contactNameText: TextView
    private lateinit var contactStatusText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var messagesRecyclerView: RecyclerView

    private var currentContact: ChatContact? = null
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var aiApiManager: AIApiManager
    private lateinit var deepSeekApiHelper: DeepSeekApiHelper
    private lateinit var messageAdapter: ChatMessageAdapter
    private lateinit var promptListContainer: View
    private lateinit var promptList: LinearLayout
    private val aiPrompts = AIPrompt.getDefaultPrompts()
    // private lateinit var chatHistoryManager: ChatHistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initializeViews()
        loadContactData()
        setupListeners()
        loadInitialMessages()
        
        // 初始化AI API管理器
        aiApiManager = AIApiManager(this)
        deepSeekApiHelper = DeepSeekApiHelper(this)
        // chatHistoryManager = ChatHistoryManager(this)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        contactNameText = findViewById(R.id.contact_name)
        contactStatusText = findViewById(R.id.contact_status)
        messageInput = findViewById(R.id.message_input)
        sendButton = findViewById(R.id.send_button)
        messagesRecyclerView = findViewById(R.id.messages_recycler_view)

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
        backButton.setOnClickListener {
            finish()
        }

        sendButton.setOnClickListener {
            sendMessage()
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
        // currentContact?.let { contact ->
        //     lifecycleScope.launch {
        //         val historyMessages = chatHistoryManager.loadMessages(contact.id)
        //         if (historyMessages.isNotEmpty()) {
        //             messages.clear()
        //             messages.addAll(historyMessages)
        //             messageAdapter.updateMessages(messages)
        //             
        //             // 滚动到底部
        //             messagesRecyclerView.post {
        //                 messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
        //             }
        //         }
        //     }
        // }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isNotEmpty()) {
            // 添加用户消息
            val userMessage = ChatMessage(messageText, true, System.currentTimeMillis())
            messages.add(userMessage)
            messageAdapter.addMessage(userMessage)
            
            // 保存用户消息到历史记录
            // currentContact?.let { contact ->
            //     lifecycleScope.launch {
            //         chatHistoryManager.saveMessage(contact.id, userMessage)
            //     }
            // }
            
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
                        messageAdapter.updateLastMessage(aiMessage.content)
                    }
                }
                
                override fun onComplete(fullResponse: String) {
                    runOnUiThread {
                        aiMessage.content = fullResponse
                        messageAdapter.updateLastMessage(aiMessage.content)
                        
                        // 保存AI回复到历史记录
                        // currentContact?.let { contact ->
                        //     lifecycleScope.launch {
                        //         chatHistoryManager.saveMessage(contact.id, aiMessage)
                        //     }
                        // }
                        
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
            .setPositiveButton("确定") { _, _ ->
                val newContent = editInput.text.toString().trim()
                if (newContent.isNotEmpty()) {
                    message.content = newContent
                    messageAdapter.notifyItemChanged(position)
                    Toast.makeText(this, "消息已编辑", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 设置AI助手档案列表
     */
    private fun setupPromptList() {
        aiPrompts.forEach { prompt ->
            val promptButton = createPromptButton(prompt)
            promptList.addView(promptButton)
        }
    }
    
    /**
     * 创建AI助手档案按钮
     */
    private fun createPromptButton(prompt: AIPrompt): TextView {
        return TextView(this).apply {
            text = prompt.title
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
                messageInput.setText(prompt.prompt)
                promptListContainer.visibility = View.GONE
                messageInput.requestFocus()
            }
        }
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
                                        messageAdapter.updateLastMessage(aiMessage.content)
                                    }
                                }
                                
                                override fun onComplete(fullResponse: String) {
                                    runOnUiThread {
                                        aiMessage.content = fullResponse
                                        messageAdapter.updateLastMessage(aiMessage.content)
                                        
                                        // 保存AI回复到历史记录
                                        // currentContact?.let { contact ->
                                        //     lifecycleScope.launch {
                                        //         chatHistoryManager.saveMessage(contact.id, aiMessage)
                                        //     }
                                        // }
                                        
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
    
    override fun onDestroy() {
        super.onDestroy()
        // 释放AI API管理器资源
        if (::aiApiManager.isInitialized) {
            aiApiManager.destroy()
        }
    }
} 