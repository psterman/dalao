package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.ChatActivity
import com.example.aifloatingball.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * 聊天消息适配器
 */
class ChatMessageAdapter(
    private var messages: List<ChatActivity.ChatMessage> = emptyList(),
    private val onMessageLongClick: ((ChatActivity.ChatMessage, Int) -> Unit)? = null,
    private val onRegenerateClick: ((ChatActivity.ChatMessage, Int) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER_MESSAGE = 1
        private const val TYPE_AI_MESSAGE = 2
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
                AIMessageViewHolder(view, onMessageLongClick, onRegenerateClick)
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
            messageText.text = message.content
            timeText.text = formatTime(message.timestamp)
            
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
        private val onRegenerateClick: ((ChatActivity.ChatMessage, Int) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)
        private val timeText: TextView = itemView.findViewById(R.id.time_text)
        private val messageCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.message_card)
        private val regenerateButton: TextView = itemView.findViewById(R.id.regenerate_button)

        fun bind(message: ChatActivity.ChatMessage, position: Int) {
            messageText.text = message.content
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
            
            // 只有AI消息且不是用户消息时才显示重新生成按钮
            regenerateButton.visibility = if (!message.isFromUser && message.content.isNotEmpty()) View.VISIBLE else View.GONE
        }

        private fun formatTime(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }
}
