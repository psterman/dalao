package com.example.aifloatingball.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.ChatActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.PlatformIconsView
import com.example.aifloatingball.utils.AdvancedMarkdownRenderer
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天消息适配器
 */
class ChatMessageAdapter(
    private val context: android.content.Context,
    private var messages: List<ChatActivity.ChatMessage> = emptyList(),
    private val onMessageLongClick: ((ChatActivity.ChatMessage, Int) -> Unit)? = null,
    private val onRegenerateClick: ((ChatActivity.ChatMessage, Int) -> Unit)? = null,
    private val onTTSSpeak: ((ChatActivity.ChatMessage, Int) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    // 高级Markdown渲染器
    private val advancedMarkdownRenderer: AdvancedMarkdownRenderer

    companion object {
        private const val TYPE_USER_MESSAGE = 1
        private const val TYPE_AI_MESSAGE = 2
    }
    
    init {
        // 初始化渲染器
        advancedMarkdownRenderer = AdvancedMarkdownRenderer.getInstance(context)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromUser) TYPE_USER_MESSAGE else TYPE_AI_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_USER_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_message_user, parent, false)
                UserMessageViewHolder(view, onMessageLongClick)
            }
            TYPE_AI_MESSAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_message_ai, parent, false)
                AIMessageViewHolder(view, onMessageLongClick, onRegenerateClick, onTTSSpeak, advancedMarkdownRenderer)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message, position)
            is AIMessageViewHolder -> holder.bind(message, position)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun updateMessages(newMessages: List<ChatActivity.ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    fun addMessage(message: ChatActivity.ChatMessage) {
        val newMessages = messages.toMutableList()
        newMessages.add(message)
        messages = newMessages
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages.last()
            if (!lastMessage.isFromUser) {
                lastMessage.content = content
                notifyItemChanged(messages.size - 1)
            }
        }
    }

    /**
     * 高亮显示指定消息
     */
    fun highlightMessage(messageIndex: Int) {
        if (messageIndex >= 0 && messageIndex < messages.size) {
            // 通知指定位置的消息更新，触发高亮效果
            notifyItemChanged(messageIndex)
        }
    }

    /**
     * 用户消息ViewHolder
     */
    class UserMessageViewHolder(
        itemView: View,
        private val onMessageLongClick: ((ChatActivity.ChatMessage, Int) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)
        private val timeText: TextView = itemView.findViewById(R.id.time_text)
        private val messageCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.message_card)

        fun bind(message: ChatActivity.ChatMessage, position: Int) {
            Log.d("ChatMessageAdapter", "绑定用户消息: position=$position, content='${message.content}', isFromUser=${message.isFromUser}")
            messageText.text = message.content
            timeText.text = formatTime(message.timestamp)
            Log.d("ChatMessageAdapter", "用户消息文本已设置: '${messageText.text}'")
            
            // 设置长按事件
            messageCard.setOnLongClickListener {
                onMessageLongClick?.invoke(message, position)
                true
            }
        }

        private fun formatTime(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }

    /**
     * AI消息ViewHolder
     */
    class AIMessageViewHolder(
        itemView: View,
        private val onMessageLongClick: ((ChatActivity.ChatMessage, Int) -> Unit)?,
        private val onRegenerateClick: ((ChatActivity.ChatMessage, Int) -> Unit)?,
        private val onTTSSpeak: ((ChatActivity.ChatMessage, Int) -> Unit)?,
        private val advancedMarkdownRenderer: AdvancedMarkdownRenderer
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)
        private val timeText: TextView = itemView.findViewById(R.id.time_text)
        private val messageCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.message_card)
        private val regenerateButton: TextView = itemView.findViewById(R.id.regenerate_button)
        private val ttsSpeakButton: android.widget.ImageButton = itemView.findViewById(R.id.btn_tts_speak)
        private val platformIconsContainer: LinearLayout = itemView.findViewById(R.id.platform_icons_container)

        fun bind(message: ChatActivity.ChatMessage, position: Int) {
            // 处理消息内容：移除平台图标标记（如果存在）
            val actualContent = if (message.content.contains("[PLATFORM_ICONS]")) {
                // 分离内容和平台图标标记
                val contentParts = message.content.split("[PLATFORM_ICONS]")
                contentParts[0].trim()
            } else {
                // 没有标记，直接使用原内容
                message.content
            }
            
            // 使用高级Markdown渲染器渲染AI回复内容
            val spannableString = advancedMarkdownRenderer.renderAIResponse(actualContent)
            messageText.text = spannableString
            
            // 所有AI回复都显示平台图标（使用userQuery作为搜索关键词）
            // 如果userQuery为空，尝试从消息内容中提取关键词
            val searchQuery = message.userQuery?.takeIf { it.isNotEmpty() } 
                ?: extractSearchQueryFromContent(actualContent)
            showPlatformIcons(searchQuery)
            
            timeText.text = formatTime(message.timestamp)
            
            // 设置长按事件
            messageCard.setOnLongClickListener {
                onMessageLongClick?.invoke(message, position)
                true
            }
            
            // 设置重新生成按钮
            regenerateButton.setOnClickListener {
                onRegenerateClick?.invoke(message, position)
            }
            
            // 设置TTS朗读按钮
            ttsSpeakButton.setOnClickListener {
                onTTSSpeak?.invoke(message, position)
            }
            
            // 只有AI消息且不是用户消息时才显示重新生成按钮
            regenerateButton.visibility = if (!message.isFromUser && message.content.isNotEmpty()) View.VISIBLE else View.GONE
        }
        
        /**
         * 显示平台图标
         */
        private fun showPlatformIcons(query: String) {
            platformIconsContainer.removeAllViews()
            
            val platformIconsView = PlatformIconsView(itemView.context)
            platformIconsView.showRelevantPlatforms(query)
            
            platformIconsContainer.addView(platformIconsView)
            platformIconsContainer.visibility = View.VISIBLE
        }
        
        /**
         * 从消息内容中提取搜索关键词
         * 如果userQuery为空，尝试从AI回复内容中提取关键词
         */
        private fun extractSearchQueryFromContent(content: String): String {
            // 如果内容为空或太短，返回空字符串
            if (content.isBlank() || content.length < 3) {
                return ""
            }
            
            // 尝试提取前50个字符作为搜索关键词
            // 移除常见的AI回复前缀（如"根据"、"建议"等）
            val cleaned = content.trim()
                .replace(Regex("^(根据|建议|我认为|我的看法是|让我|我可以|我理解|这是一个|根据我的分析)[，。：:]"), "")
                .trim()
            
            // 取前50个字符，如果包含句号、问号、感叹号，则截取到第一个标点符号
            val firstSentence = cleaned.split(Regex("[。！？\n]")).firstOrNull()?.take(50) ?: cleaned.take(50)
            
            return firstSentence.trim()
        }

        private fun formatTime(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }
}
