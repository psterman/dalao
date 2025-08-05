package com.example.aifloatingball

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactType
import com.google.android.material.button.MaterialButton

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
        const val EXTRA_CONTACT = "extra_contact"
    }

    private lateinit var backButton: ImageButton
    private lateinit var contactNameText: TextView
    private lateinit var contactStatusText: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var messagesRecyclerView: RecyclerView

    private var currentContact: ChatContact? = null
    private val messages = mutableListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initializeViews()
        loadContactData()
        setupListeners()
        loadInitialMessages()
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
        // TODO: 设置消息适配器
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
                ContactType.RSS -> {
                    contactStatusText.setTextColor(getColor(R.color.rss_icon_color))
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

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrBlank()
            }
        })
    }

    private fun loadInitialMessages() {
        currentContact?.let { contact ->
            // 添加一些示例消息
            when (contact.type) {
                ContactType.AI -> {
                    messages.add(ChatMessage("你好！我是${contact.name}，有什么可以帮助你的吗？", false, System.currentTimeMillis() - 3600000))
                    messages.add(ChatMessage("我想了解一下人工智能的发展趋势", true, System.currentTimeMillis() - 1800000))
                    messages.add(ChatMessage("人工智能正在快速发展，主要趋势包括：\n1. 大语言模型的突破\n2. 多模态AI的发展\n3. AI在医疗、教育等领域的应用\n4. 更注重AI的安全性和伦理问题", false, System.currentTimeMillis() - 900000))
                }
                ContactType.RSS -> {
                    messages.add(ChatMessage("欢迎订阅${contact.name}！", false, System.currentTimeMillis() - 3600000))
                    messages.add(ChatMessage("最新文章：人工智能技术突破性进展", false, System.currentTimeMillis() - 1800000))
                    messages.add(ChatMessage("最新文章：5G网络建设加速推进", false, System.currentTimeMillis() - 900000))
                }
            }
        }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString().trim()
        if (messageText.isNotEmpty()) {
            // 添加用户消息
            messages.add(ChatMessage(messageText, true, System.currentTimeMillis()))
            
            // 清空输入框
            messageInput.text.clear()
            
            // 模拟回复
            currentContact?.let { contact ->
                when (contact.type) {
                    ContactType.AI -> {
                        // 模拟AI回复
                        val responses = listOf(
                            "我理解您的问题，让我为您详细解答...",
                            "这是一个很有趣的问题，我的看法是...",
                            "根据我的分析，建议您考虑以下几个方面...",
                            "我可以为您提供更多相关信息..."
                        )
                        val response = responses.random()
                        
                        // 延迟显示回复，模拟网络请求
                        messageInput.postDelayed({
                            messages.add(ChatMessage(response, false, System.currentTimeMillis()))
                            // TODO: 更新RecyclerView
                        }, 1000)
                    }
                    ContactType.RSS -> {
                        // RSS订阅的回复
                        val responses = listOf(
                            "已收到您的订阅请求，我们会定期推送最新资讯。",
                            "感谢您的关注，最新内容已为您推送。",
                            "您的订阅已确认，我们会及时更新内容。"
                        )
                        val response = responses.random()
                        
                        messageInput.postDelayed({
                            messages.add(ChatMessage(response, false, System.currentTimeMillis()))
                            // TODO: 更新RecyclerView
                        }, 500)
                    }
                }
            }
            
            Toast.makeText(this, "消息已发送", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 聊天消息数据类
     */
    data class ChatMessage(
        val content: String,
        val isFromUser: Boolean,
        val timestamp: Long
    )
} 