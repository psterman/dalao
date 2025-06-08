package com.example.aifloatingball.ui.chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.ChatManager
import com.example.aifloatingball.manager.ChatManager.ChatMessage
import com.example.aifloatingball.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeepSeekChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatManager: ChatManager
    private lateinit var settingsManager: SettingsManager
    private var engineType: String = "deepseek" // 默认使用 DeepSeek

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deepseek_chat)

        // 获取引擎类型
        engineType = intent.getStringExtra("engine_type") ?: "deepseek"
        
        // 根据引擎类型设置标题
        val chatTitle = findViewById<TextView>(R.id.chat_title)
        chatTitle.text = when(engineType) {
            "chatgpt" -> "ChatGPT 对话"
            else -> "DeepSeek 对话"
        }

        settingsManager = SettingsManager.getInstance(this)
        chatManager = ChatManager(this)

        chatRecyclerView = findViewById(R.id.chat_recycler_view)
        messageEditText = findViewById(R.id.edit_message)
        sendButton = findViewById(R.id.btn_send)
        backButton = findViewById(R.id.btn_back)

        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter

        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        // 处理初始查询
        intent.getStringExtra("initial_query")?.let { query ->
            if (query.isNotBlank()) {
                messageEditText.setText(query)
                sendMessage()
            }
        }
    }

    private fun sendMessage() {
        val messageText = messageEditText.text.toString().trim()
        if (messageText.isBlank()) {
            Toast.makeText(this, "消息不能为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查API密钥
        val isDeepSeek = engineType == "deepseek"
        if (isDeepSeek && settingsManager.getDeepSeekApiKey().isNullOrBlank()) {
            Toast.makeText(this, "请先在设置页面配置 DeepSeek API 密钥", Toast.LENGTH_LONG).show()
            addMessage(ChatMessage("assistant", "DeepSeek API 密钥未配置，请先配置。"))
            return
        } else if (!isDeepSeek && settingsManager.getChatGPTApiKey().isNullOrBlank()) {
            Toast.makeText(this, "请先在设置页面配置 ChatGPT API 密钥", Toast.LENGTH_LONG).show()
            addMessage(ChatMessage("assistant", "ChatGPT API 密钥未配置，请先配置。"))
            return
        }

        // 添加用户消息
        addMessage(ChatMessage("user", messageText))
        messageEditText.text.clear()

        // 显示加载提示
        val loadingMessage = when(engineType) {
            "chatgpt" -> "ChatGPT 正在思考..."
            else -> "DeepSeek 正在思考..."
        }
        addMessage(ChatMessage("assistant", loadingMessage, isLoading = true))
        
        // 禁用输入
        sendButton.isEnabled = false
        messageEditText.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = chatManager.sendMessageAndGetResponse(messageText, isDeepSeek, false)
                withContext(Dispatchers.Main) {
                    removeLoadingMessage()
                    addMessage(ChatMessage("assistant", response))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    removeLoadingMessage()
                    val errorPrefix = if (isDeepSeek) "DeepSeek" else "ChatGPT"
                    Toast.makeText(this@DeepSeekChatActivity, "发送消息失败: ${e.message}", Toast.LENGTH_LONG).show()
                    addMessage(ChatMessage("assistant", "$errorPrefix 回应失败: ${e.message}"))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    sendButton.isEnabled = true
                    messageEditText.isEnabled = true
                }
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        chatMessages.add(message)
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun removeLoadingMessage() {
        val lastIndex = chatMessages.size - 1
        if (lastIndex >= 0 && chatMessages[lastIndex].isLoading) {
            chatMessages.removeAt(lastIndex)
            chatAdapter.notifyItemRemoved(lastIndex)
        }
    }
} 