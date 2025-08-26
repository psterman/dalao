package com.example.aifloatingball.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.GroupChatMessage
import com.example.aifloatingball.model.GroupMessageType
import com.example.aifloatingball.utils.FaviconLoader
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

/**
 * 群聊消息适配器
 * 支持显示用户消息和多个AI的回复
 */
class GroupChatMessageAdapter(
    private val context: Context,
    private val messages: MutableList<GroupChatMessage> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER_MESSAGE = 1
        private const val TYPE_GROUP_AI_REPLIES = 2
        private const val TYPE_SINGLE_AI_MESSAGE = 3
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * 消息操作监听器
     */
    interface OnMessageActionListener {
        fun onCopyMessage(message: GroupChatMessage)
        fun onRegenerateMessage(message: GroupChatMessage)
        fun onCopyAIReply(message: GroupChatMessage, aiName: String, replyContent: String)
        fun onRegenerateAIReply(message: GroupChatMessage, aiName: String)
        fun onLikeAIReply(message: GroupChatMessage, aiName: String)
    }

    private var actionListener: OnMessageActionListener? = null

    fun setOnMessageActionListener(listener: OnMessageActionListener) {
        this.actionListener = listener
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when (message.senderType) {
            com.example.aifloatingball.model.MemberType.USER -> TYPE_USER_MESSAGE
            com.example.aifloatingball.model.MemberType.AI -> TYPE_SINGLE_AI_MESSAGE
            else -> TYPE_USER_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_USER_MESSAGE -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.item_group_chat_message, parent, false)
                UserMessageViewHolder(view)
            }
            TYPE_GROUP_AI_REPLIES -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.item_group_chat_message, parent, false)
                GroupAIRepliesViewHolder(view)
            }
            TYPE_SINGLE_AI_MESSAGE -> {
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.item_group_chat_message, parent, false)
                SingleAIMessageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is GroupAIRepliesViewHolder -> holder.bind(message)
            is SingleAIMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    /**
     * 添加消息
     */
    fun addMessage(message: GroupChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    /**
     * 更新消息
     */
    fun updateMessage(position: Int, message: GroupChatMessage) {
        if (position >= 0 && position < messages.size) {
            messages[position] = message
            notifyItemChanged(position)
        }
    }

    /**
     * 更新消息列表
     */
    fun updateMessages(newMessages: List<GroupChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    /**
     * 用户消息ViewHolder
     */
    inner class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: LinearLayout = itemView.findViewById(R.id.user_message_container)
        private val groupAIContainer: LinearLayout = itemView.findViewById(R.id.group_ai_replies_container)
        private val singleAIContainer: LinearLayout = itemView.findViewById(R.id.single_ai_message_container)
        private val userText: TextView = itemView.findViewById(R.id.user_message_text)
        private val userTime: TextView = itemView.findViewById(R.id.user_message_time)

        fun bind(message: GroupChatMessage) {
            // 显示用户消息容器，隐藏其他容器
            userContainer.visibility = View.VISIBLE
            groupAIContainer.visibility = View.GONE
            singleAIContainer.visibility = View.GONE

            userText.text = message.content
            userTime.text = timeFormat.format(message.timestamp)
        }
    }

    /**
     * 群聊AI回复ViewHolder
     */
    inner class GroupAIRepliesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: LinearLayout = itemView.findViewById(R.id.user_message_container)
        private val groupAIContainer: LinearLayout = itemView.findViewById(R.id.group_ai_replies_container)
        private val singleAIContainer: LinearLayout = itemView.findViewById(R.id.single_ai_message_container)
        private val repliesTitle: TextView = itemView.findViewById(R.id.group_replies_title)
        private val repliesRecyclerView: RecyclerView = itemView.findViewById(R.id.ai_replies_recycler_view)

        fun bind(message: GroupChatMessage) {
            // 显示群聊AI回复容器，隐藏其他容器
            userContainer.visibility = View.GONE
            groupAIContainer.visibility = View.VISIBLE
            singleAIContainer.visibility = View.GONE

            // 设置标题
            repliesTitle.text = "AI回复"

            // 暂时隐藏回复列表，因为数据结构需要重新设计
            repliesRecyclerView.visibility = View.GONE
        }
    }

    /**
     * 单个AI消息ViewHolder
     */
    inner class SingleAIMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: LinearLayout = itemView.findViewById(R.id.user_message_container)
        private val groupAIContainer: LinearLayout = itemView.findViewById(R.id.group_ai_replies_container)
        private val singleAIContainer: LinearLayout = itemView.findViewById(R.id.single_ai_message_container)
        private val aiAvatar: ImageView = itemView.findViewById(R.id.ai_avatar)
        private val aiName: TextView = itemView.findViewById(R.id.ai_name_text)
        private val aiText: TextView = itemView.findViewById(R.id.ai_message_text)
        private val aiTime: TextView = itemView.findViewById(R.id.ai_message_time)
        private val copyButton: ImageButton = itemView.findViewById(R.id.btn_copy_message)
        private val regenerateButton: ImageButton = itemView.findViewById(R.id.btn_regenerate)

        fun bind(message: GroupChatMessage) {
            // 显示单个AI消息容器，隐藏其他容器
            userContainer.visibility = View.GONE
            groupAIContainer.visibility = View.GONE
            singleAIContainer.visibility = View.VISIBLE

            aiText.text = message.content
            aiTime.text = timeFormat.format(message.timestamp)
            aiName.text = message.senderName

            // 设置AI头像
            FaviconLoader.loadAIEngineIcon(aiAvatar, message.senderName, R.drawable.ic_smart_toy)

            // 设置操作按钮监听器
            copyButton.setOnClickListener {
                actionListener?.onCopyMessage(message)
            }

            regenerateButton.setOnClickListener {
                actionListener?.onRegenerateMessage(message)
            }
        }
    }

    /**
     * AI回复列表适配器
     */
    inner class AIRepliesAdapter(
        private val context: Context,
        private val aiReplies: Map<String, String>,
        private val parentMessage: GroupChatMessage
    ) : RecyclerView.Adapter<AIRepliesAdapter.AIReplyViewHolder>() {

        private val aiNames = aiReplies.keys.toList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AIReplyViewHolder {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_group_ai_reply, parent, false)
            return AIReplyViewHolder(view)
        }

        override fun onBindViewHolder(holder: AIReplyViewHolder, position: Int) {
            val aiName = aiNames[position]
            val replyContent = aiReplies[aiName] ?: ""
            holder.bind(aiName, replyContent, parentMessage)
        }

        override fun getItemCount(): Int = aiNames.size

        inner class AIReplyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val aiAvatar: ImageView = itemView.findViewById(R.id.ai_avatar)
            private val aiName: TextView = itemView.findViewById(R.id.ai_name_text)
            private val aiReplyText: TextView = itemView.findViewById(R.id.ai_reply_text)
            private val aiReplyTime: TextView = itemView.findViewById(R.id.ai_reply_time)
            private val aiReplyStatus: TextView = itemView.findViewById(R.id.ai_reply_status)
            private val aiReplyProgress: ProgressBar = itemView.findViewById(R.id.ai_reply_progress)
            private val copyButton: ImageButton = itemView.findViewById(R.id.btn_copy_reply)
            private val regenerateButton: ImageButton = itemView.findViewById(R.id.btn_regenerate_reply)
            private val likeButton: ImageButton = itemView.findViewById(R.id.btn_like_reply)

            fun bind(aiName: String, replyContent: String, parentMessage: GroupChatMessage) {
                this.aiName.text = aiName
                aiReplyText.text = replyContent
                aiReplyTime.text = timeFormat.format(parentMessage.timestamp)

                // 设置AI头像
                FaviconLoader.loadAIEngineIcon(aiAvatar, aiName, R.drawable.ic_smart_toy)

                // 根据回复内容显示状态
                if (replyContent.isEmpty()) {
                    aiReplyStatus.visibility = View.VISIBLE
                    aiReplyProgress.visibility = View.VISIBLE
                    aiReplyStatus.text = "回复中..."
                } else {
                    aiReplyStatus.visibility = View.GONE
                    aiReplyProgress.visibility = View.GONE
                }

                // 设置操作按钮监听器
                copyButton.setOnClickListener {
                    actionListener?.onCopyAIReply(parentMessage, aiName, replyContent)
                }

                regenerateButton.setOnClickListener {
                    actionListener?.onRegenerateAIReply(parentMessage, aiName)
                }

                likeButton.setOnClickListener {
                    actionListener?.onLikeAIReply(parentMessage, aiName)
                }
            }
        }
    }
}