package com.example.aifloatingball.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.adapter.AdaptiveChatMessageAdapter
import com.example.aifloatingball.adapter.ChatMessage
import com.example.aifloatingball.manager.SimpleChatHistoryManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

/**
 * 自适应对话界面示例Activity
 * 展示左右手模式和暗色/亮色模式的完整实现
 */
class AdaptiveChatActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var layoutManager: AdaptiveChatLayoutManager
    private lateinit var animationManager: ChatLayoutAnimationManager
    private lateinit var messageAdapter: AdaptiveChatMessageAdapter
    private lateinit var chatHistoryManager: SimpleChatHistoryManager

    // 当前对话的联系人ID（用于历史记录）
    private val currentContactId = "adaptive_chat_demo"
    private val messages = mutableListOf<ChatMessage>()

    // UI组件
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputArea: View
    private lateinit var messageInput: EditText
    private lateinit var sendButtonLeft: FloatingActionButton
    private lateinit var sendButtonRight: FloatingActionButton
    private lateinit var toggleButton: ImageButton
    private lateinit var toggleButtonRight: ImageButton
    
    private var isFunctionButtonsVisible = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adaptive_chat)
        
        initializeComponents()
        setupRecyclerView()
        setupInputArea()
        applyCurrentLayout()

        // 加载历史记录
        loadHistoryMessages()

        // 如果没有历史记录，添加示例消息
        if (messages.isEmpty()) {
            addSampleMessages()
        }
    }
    
    private fun initializeComponents() {
        settingsManager = SettingsManager.getInstance(this)
        layoutManager = AdaptiveChatLayoutManager(this)
        animationManager = ChatLayoutAnimationManager(this)
        chatHistoryManager = SimpleChatHistoryManager(this)
        
        // 初始化UI组件
        recyclerView = findViewById(R.id.chat_recycler_view)
        inputArea = findViewById(R.id.chat_input_root)
        messageInput = findViewById(R.id.message_input)
        sendButtonLeft = findViewById(R.id.btn_send_left)
        sendButtonRight = findViewById(R.id.btn_send_right)
        toggleButton = findViewById(R.id.btn_toggle_functions)
        toggleButtonRight = findViewById(R.id.btn_toggle_functions_right)
    }
    
    private fun setupRecyclerView() {
        val messages = mutableListOf<ChatMessage>()
        messageAdapter = AdaptiveChatMessageAdapter(this, messages)
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AdaptiveChatActivity)
            adapter = messageAdapter
        }
        
        // 设置消息操作监听器
        messageAdapter.setOnMessageActionListener(object : AdaptiveChatMessageAdapter.OnMessageActionListener {
            override fun onCopyMessage(message: ChatMessage) {
                // 复制消息到剪贴板
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("消息", message.content)
                clipboard.setPrimaryClip(clip)
                
                android.widget.Toast.makeText(this@AdaptiveChatActivity, "消息已复制", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            override fun onRegenerateMessage(message: ChatMessage) {
                // 重新生成消息
                android.widget.Toast.makeText(this@AdaptiveChatActivity, "正在重新生成...", android.widget.Toast.LENGTH_SHORT).show()
                // 这里可以添加重新生成逻辑
            }
        })
    }
    
    private fun setupInputArea() {
        // 发送按钮点击事件
        val sendClickListener = View.OnClickListener {
            sendMessage()
        }
        sendButtonLeft.setOnClickListener(sendClickListener)
        sendButtonRight.setOnClickListener(sendClickListener)
        
        // 功能切换按钮点击事件
        val toggleClickListener = View.OnClickListener {
            toggleFunctionButtons()
        }
        toggleButton.setOnClickListener(toggleClickListener)
        toggleButtonRight.setOnClickListener(toggleClickListener)
        
        // 输入框回车发送
        messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }
    
    private fun applyCurrentLayout() {
        val currentLayoutMode = layoutManager.getCurrentLayoutMode()
        val currentThemeMode = layoutManager.getCurrentThemeMode()
        
        // 应用输入区域布局
        layoutManager.applyInputAreaAdaptiveLayout(inputArea, currentLayoutMode)
        
        // 应用主题颜色
        layoutManager.applyThemeColors(inputArea, currentThemeMode)
        layoutManager.applyThemeColors(recyclerView, currentThemeMode)
    }
    
    private fun sendMessage() {
        val content = messageInput.text.toString().trim()
        if (content.isEmpty()) return

        // 添加用户消息
        val userMessage = ChatMessage(
            content = content,
            isUser = true,
            timestamp = Date()
        )
        messages.add(userMessage)
        messageAdapter.updateMessages(messages)

        // 保存历史记录
        saveMessagesToHistory()

        // 清空输入框
        messageInput.text.clear()

        // 滚动到底部
        recyclerView.scrollToPosition(messageAdapter.itemCount - 1)

        // 模拟AI回复
        simulateAIResponse(content)
    }
    
    private fun simulateAIResponse(userMessage: String) {
        // 延迟模拟AI回复
        recyclerView.postDelayed({
            val aiResponse = ChatMessage(
                content = "这是对「$userMessage」的AI回复。我理解了您的问题，这里是我的回答...",
                isUser = false,
                timestamp = Date(),
                aiName = "AI助手"
            )
            messages.add(aiResponse)
            messageAdapter.updateMessages(messages)

            // 保存历史记录
            saveMessagesToHistory()

            recyclerView.scrollToPosition(messageAdapter.itemCount - 1)
        }, 1000)
    }

    /**
     * 保存消息到历史记录
     */
    private fun saveMessagesToHistory() {
        try {
            // 将ChatMessage转换为ChatActivity.ChatMessage格式
            val historyMessages = messages.map { message ->
                com.example.aifloatingball.ChatActivity.ChatMessage(
                    content = message.content,
                    isFromUser = message.isUser,
                    timestamp = message.timestamp.time
                )
            }
            chatHistoryManager.saveMessages(currentContactId, historyMessages)
        } catch (e: Exception) {
            android.util.Log.e("AdaptiveChatActivity", "保存历史记录失败", e)
        }
    }

    /**
     * 加载历史记录
     */
    private fun loadHistoryMessages() {
        try {
            val historyMessages = chatHistoryManager.loadMessages(currentContactId)
            historyMessages.forEach { historyMessage ->
                val message = ChatMessage(
                    content = historyMessage.content,
                    isUser = historyMessage.isFromUser,
                    timestamp = Date(historyMessage.timestamp),
                    aiName = if (historyMessage.isFromUser) null else "AI助手"
                )
                messages.add(message)
                messageAdapter.updateMessages(messages)
            }
            if (messages.isNotEmpty()) {
                recyclerView.scrollToPosition(messageAdapter.itemCount - 1)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdaptiveChatActivity", "加载历史记录失败", e)
        }
    }

    private fun toggleFunctionButtons() {
        isFunctionButtonsVisible = !isFunctionButtonsVisible
        
        animationManager.animateFunctionButtonsToggle(
            inputArea.findViewById(R.id.function_buttons_scroll),
            isFunctionButtonsVisible
        ) {
            layoutManager.toggleFunctionButtonsVisibility(inputArea, isFunctionButtonsVisible)
        }
    }
    
    private fun addSampleMessages() {
        val sampleMessages = listOf(
            ChatMessage(
                content = "你好！我是AI助手，有什么可以帮助您的吗？",
                isUser = false,
                timestamp = Date(System.currentTimeMillis() - 300000),
                aiName = "AI助手"
            ),
            ChatMessage(
                content = "你好，我想了解一下左右手模式的功能",
                isUser = true,
                timestamp = Date(System.currentTimeMillis() - 240000)
            ),
            ChatMessage(
                content = "左右手模式可以根据您的使用习惯调整界面布局。右手模式下，发送按钮在右侧方便右手操作；左手模式下，发送按钮在左侧方便左手操作。同时消息气泡的位置也会相应调整。",
                isUser = false,
                timestamp = Date(System.currentTimeMillis() - 180000),
                aiName = "AI助手"
            ),
            ChatMessage(
                content = "这个功能很贴心！那暗色模式呢？",
                isUser = true,
                timestamp = Date(System.currentTimeMillis() - 120000)
            ),
            ChatMessage(
                content = "暗色模式可以在光线较暗的环境下保护您的眼睛，减少屏幕亮度对视力的影响。界面会自动调整为深色背景和浅色文字，同时保持良好的对比度确保可读性。",
                isUser = false,
                timestamp = Date(System.currentTimeMillis() - 60000),
                aiName = "AI助手"
            )
        )
        
        sampleMessages.forEach { message ->
            messages.add(message)
        }
        messageAdapter.updateMessages(messages)

        // 保存示例消息到历史记录
        saveMessagesToHistory()

        recyclerView.scrollToPosition(messageAdapter.itemCount - 1)
    }
    
    /**
     * 监听设置变化
     */
    override fun onResume() {
        super.onResume()
        
        // 检查设置是否有变化
        val currentLayoutMode = layoutManager.getCurrentLayoutMode()
        val currentThemeMode = layoutManager.getCurrentThemeMode()
        
        // 如果有变化，执行动画切换
        layoutManager.onSettingsChanged { newLayoutMode, newThemeMode ->
            if (newLayoutMode != currentLayoutMode) {
                animationManager.animateLayoutModeSwitch(
                    recyclerView,
                    inputArea,
                    currentLayoutMode == AdaptiveChatLayoutManager.LayoutMode.LEFT_HANDED,
                    newLayoutMode == AdaptiveChatLayoutManager.LayoutMode.LEFT_HANDED
                ) {
                    applyCurrentLayout()
                }
            }
            
            if (newThemeMode != currentThemeMode) {
                animationManager.animateThemeSwitch(findViewById(android.R.id.content)) {
                    applyCurrentLayout()
                }
            }
        }
    }
}
