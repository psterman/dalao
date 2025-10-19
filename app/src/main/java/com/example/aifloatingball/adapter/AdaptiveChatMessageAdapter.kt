package com.example.aifloatingball.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.AdaptiveChatLayoutManager
import com.example.aifloatingball.utils.FaviconLoader
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * 自适应聊天消息适配器
 * 支持左右手模式和暗色/亮色模式
 */
class AdaptiveChatMessageAdapter(
    private val context: Context,
    private val messages: MutableList<ChatMessage>
) : RecyclerView.Adapter<AdaptiveChatMessageAdapter.MessageViewHolder>() {

    private val layoutManager = AdaptiveChatLayoutManager(context)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    /**
     * 消息操作监听器
     */
    interface OnMessageActionListener {
        fun onCopyMessage(message: ChatMessage)
        fun onRegenerateMessage(message: ChatMessage)
        fun onTTSSpeak(message: ChatMessage)
    }
    
    private var actionListener: OnMessageActionListener? = null
    
    fun setOnMessageActionListener(listener: OnMessageActionListener) {
        this.actionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_chat_message_adaptive, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    /**
     * 添加消息
     */
    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * 更新消息列表
     */
    fun updateMessages(newMessages: MutableList<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    /**
     * 更新消息
     */
    fun updateMessage(position: Int, message: ChatMessage) {
        if (position in 0 until messages.size) {
            messages[position] = message
            notifyItemChanged(position)
        }
    }

    /**
     * 清空消息
     */
    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        // 容器
        private val userContainer: LinearLayout = itemView.findViewById(R.id.user_message_container)
        private val aiContainer: LinearLayout = itemView.findViewById(R.id.ai_message_container)
        
        // 用户消息相关视图
        private val userLeftSpacer: View = itemView.findViewById(R.id.user_left_spacer)
        private val userRightSpacer: View = itemView.findViewById(R.id.user_right_spacer)
        private val userBubble: MaterialCardView = itemView.findViewById(R.id.user_message_bubble)
        private val userText: TextView = itemView.findViewById(R.id.user_message_text)
        private val userTime: TextView = itemView.findViewById(R.id.user_message_time)
        
        // AI消息相关视图
        private val aiLeftSpacer: View = itemView.findViewById(R.id.ai_left_spacer)
        private val aiRightSpacer: View = itemView.findViewById(R.id.ai_right_spacer)
        private val aiAvatar: ImageView = itemView.findViewById(R.id.ai_avatar)
        private val aiAvatarContainer: MaterialCardView = itemView.findViewById(R.id.ai_avatar_container)
        private val aiName: TextView = itemView.findViewById(R.id.ai_name_text)
        private val aiBubble: MaterialCardView = itemView.findViewById(R.id.ai_message_bubble)
        private val aiText: TextView = itemView.findViewById(R.id.ai_message_text)
        private val aiTime: TextView = itemView.findViewById(R.id.ai_message_time)
        private val copyButton: ImageButton = itemView.findViewById(R.id.btn_copy_message)
        private val regenerateButton: ImageButton = itemView.findViewById(R.id.btn_regenerate)
        private val ttsSpeakButton: ImageButton = itemView.findViewById(R.id.btn_tts_speak)

        fun bind(message: ChatMessage) {
            val layoutMode = layoutManager.getCurrentLayoutMode()
            val themeMode = layoutManager.getCurrentThemeMode()
            
            if (message.isUser) {
                bindUserMessage(message, layoutMode, themeMode)
            } else {
                bindAIMessage(message, layoutMode, themeMode)
            }
        }

        private fun bindUserMessage(
            message: ChatMessage, 
            layoutMode: AdaptiveChatLayoutManager.LayoutMode,
            themeMode: AdaptiveChatLayoutManager.ThemeMode
        ) {
            // 显示用户消息容器，隐藏AI消息容器
            userContainer.visibility = View.VISIBLE
            aiContainer.visibility = View.GONE
            
            // 设置消息内容
            userText.text = message.content
            userTime.text = timeFormat.format(message.timestamp)
            
            // 根据左右手模式调整布局
            when (layoutMode) {
                AdaptiveChatLayoutManager.LayoutMode.RIGHT_HANDED -> {
                    // 右手模式：用户消息在右侧
                    userLeftSpacer.visibility = View.VISIBLE
                    userRightSpacer.visibility = View.GONE
                    userLeftSpacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
                AdaptiveChatLayoutManager.LayoutMode.LEFT_HANDED -> {
                    // 左手模式：用户消息在左侧
                    userLeftSpacer.visibility = View.GONE
                    userRightSpacer.visibility = View.VISIBLE
                    userRightSpacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
            }
            
            // 应用主题颜色
            layoutManager.applyThemeColors(userBubble, themeMode)
        }

        private fun bindAIMessage(
            message: ChatMessage, 
            layoutMode: AdaptiveChatLayoutManager.LayoutMode,
            themeMode: AdaptiveChatLayoutManager.ThemeMode
        ) {
            // 显示AI消息容器，隐藏用户消息容器
            userContainer.visibility = View.GONE
            aiContainer.visibility = View.VISIBLE
            
            // 设置消息内容
            aiText.text = message.content
            aiTime.text = timeFormat.format(message.timestamp)
            aiName.text = message.aiName ?: "AI助手"

            // 设置AI头像
            aiAvatar.clearColorFilter()
            FaviconLoader.loadAIEngineIcon(aiAvatar, message.aiName ?: "AI助手", R.drawable.ic_smart_toy)
            
            // 根据左右手模式调整布局
            when (layoutMode) {
                AdaptiveChatLayoutManager.LayoutMode.RIGHT_HANDED -> {
                    // 右手模式：AI消息在左侧
                    aiLeftSpacer.visibility = View.GONE
                    aiRightSpacer.visibility = View.VISIBLE
                    aiRightSpacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    
                    // 头像在左侧
                    aiAvatarContainer.layoutParams = (aiAvatarContainer.layoutParams as LinearLayout.LayoutParams).apply {
                        marginEnd = dpToPx(12)
                        marginStart = 0
                    }
                }
                AdaptiveChatLayoutManager.LayoutMode.LEFT_HANDED -> {
                    // 左手模式：AI消息在右侧
                    aiLeftSpacer.visibility = View.VISIBLE
                    aiRightSpacer.visibility = View.GONE
                    aiLeftSpacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    
                    // 头像在右侧（需要调整容器顺序）
                    aiAvatarContainer.layoutParams = (aiAvatarContainer.layoutParams as LinearLayout.LayoutParams).apply {
                        marginStart = dpToPx(12)
                        marginEnd = 0
                    }
                }
            }
            
            // 应用主题颜色
            layoutManager.applyThemeColors(aiBubble, themeMode)
            layoutManager.applyThemeColors(aiAvatarContainer, themeMode)
            
            // 设置操作按钮监听器
            copyButton.setOnClickListener {
                actionListener?.onCopyMessage(message)
            }
            
            regenerateButton.setOnClickListener {
                actionListener?.onRegenerateMessage(message)
            }
            
            ttsSpeakButton.setOnClickListener {
                actionListener?.onTTSSpeak(message)
            }
        }
        
        private fun dpToPx(dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }
    }
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Date = Date(),
    val aiName: String? = null,
    val status: MessageStatus = MessageStatus.SENT
)

/**
 * 消息状态枚举
 */
enum class MessageStatus {
    SENDING,    // 发送中
    SENT,       // 已发送
    FAILED,     // 发送失败
    RECEIVED    // 已接收
}
