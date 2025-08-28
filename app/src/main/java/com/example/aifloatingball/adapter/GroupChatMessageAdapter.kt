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
import android.text.Html
import androidx.appcompat.widget.PopupMenu
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast

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
        fun onDeleteAIReply(message: GroupChatMessage, aiName: String)
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
        private val userAvatar: ImageView = itemView.findViewById(R.id.user_avatar)
        private val userAvatarContainer: MaterialCardView = itemView.findViewById(R.id.user_avatar_container)
        private val userText: TextView = itemView.findViewById(R.id.user_message_text)
        private val userTime: TextView = itemView.findViewById(R.id.user_message_time)

        fun bind(message: GroupChatMessage) {
            // 显示用户消息容器，隐藏其他容器
            userContainer.visibility = View.VISIBLE
            groupAIContainer.visibility = View.GONE
            singleAIContainer.visibility = View.GONE

            userText.text = message.content
            userTime.text = timeFormat.format(message.timestamp)
            
            // 根据用户角色设置头像
            setupUserAvatar(message)
        }
        
        private fun setupUserAvatar(message: GroupChatMessage) {
            // 检查消息的metadata中是否包含用户角色信息
            val userRole = message.metadata["userRole"]
            
            when (userRole) {
                "OWNER" -> {
                    // 群主头像 - 使用皇冠图标
                    userAvatar.setImageResource(R.drawable.ic_crown)
                    userAvatar.setColorFilter(context.getColor(R.color.material_wechat_primary))
                    userAvatarContainer.setCardBackgroundColor(context.getColor(R.color.material_dialog_surface_variant))
                    userAvatarContainer.strokeColor = context.getColor(R.color.material_wechat_primary)
                    userAvatarContainer.strokeWidth = 2
                }
                "ADMIN" -> {
                    // 管理员头像 - 使用管理员图标
                    userAvatar.setImageResource(R.drawable.ic_admin_panel_settings)
                    userAvatar.setColorFilter(context.getColor(R.color.material_orange_500))
                    userAvatarContainer.setCardBackgroundColor(context.getColor(R.color.material_dialog_surface_variant))
                    userAvatarContainer.strokeColor = context.getColor(R.color.material_orange_500)
                    userAvatarContainer.strokeWidth = 2
                }
                else -> {
                    // 普通成员头像 - 使用默认人物图标
                    userAvatar.setImageResource(R.drawable.ic_person)
                    userAvatar.setColorFilter(context.getColor(R.color.material_chat_timestamp_light))
                    userAvatarContainer.setCardBackgroundColor(context.getColor(R.color.material_dialog_surface_variant))
                    userAvatarContainer.strokeColor = context.getColor(R.color.material_chat_border_light)
                    userAvatarContainer.strokeWidth = 1
                }
            }
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
            private val expandCollapseButton: TextView = itemView.findViewById(R.id.expand_collapse_button)
            
            private var isExpanded = false
            private var originalText = ""

            fun bind(aiName: String, replyContent: String, parentMessage: GroupChatMessage) {
                this.aiName.text = aiName
                originalText = replyContent
                
                // 处理Markdown格式，移除Markdown符号
                val processedContent = processMarkdown(replyContent)
                
                aiReplyTime.text = timeFormat.format(parentMessage.timestamp)

                // 设置AI头像
                FaviconLoader.loadAIEngineIcon(aiAvatar, aiName, R.drawable.ic_smart_toy)

                // 根据回复内容显示状态
                if (replyContent.isEmpty()) {
                    aiReplyStatus.visibility = View.VISIBLE
                    aiReplyProgress.visibility = View.VISIBLE
                    aiReplyStatus.text = "回复中..."
                    expandCollapseButton.visibility = View.GONE
                } else {
                    aiReplyStatus.visibility = View.GONE
                    aiReplyProgress.visibility = View.GONE
                    
                    // 检查文本长度，决定是否显示展开/收缩按钮
                    setupExpandCollapse(processedContent)
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
                
                // 设置长按菜单
                aiReplyText.setOnLongClickListener {
                    showContextMenu(aiName, replyContent, parentMessage)
                    true
                }
            }
            
            private fun processMarkdown(text: String): String {
                return text
                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // 粗体
                    .replace(Regex("\\*(.*?)\\*"), "$1") // 斜体
                    .replace(Regex("`(.*?)`"), "$1") // 行内代码
                    .replace(Regex("```[\\s\\S]*?```"), { match -> 
                        match.value.replace("```", "").trim()
                    }) // 代码块
                    .replace(Regex("^#{1,6}\\s+"), "") // 标题
                    .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "• ") // 列表
                    .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "") // 有序列表
            }
            
            private fun setupExpandCollapse(processedContent: String) {
                val lineCount = processedContent.split("\n").size
                val charCount = processedContent.length
                
                // 如果文本超过3行或150个字符，显示展开/收缩功能
                if (lineCount > 3 || charCount > 150) {
                    expandCollapseButton.visibility = View.VISIBLE
                    isExpanded = false
                    aiReplyText.maxLines = 3
                    aiReplyText.text = processedContent
                    expandCollapseButton.text = "展开"
                    
                    expandCollapseButton.setOnClickListener {
                        if (isExpanded) {
                            // 收缩
                            aiReplyText.maxLines = 3
                            expandCollapseButton.text = "展开"
                            isExpanded = false
                        } else {
                            // 展开
                            aiReplyText.maxLines = Integer.MAX_VALUE
                            expandCollapseButton.text = "收缩"
                            isExpanded = true
                        }
                    }
                } else {
                    expandCollapseButton.visibility = View.GONE
                    aiReplyText.maxLines = Integer.MAX_VALUE
                    aiReplyText.text = processedContent
                }
            }
            
            private fun showContextMenu(aiName: String, replyContent: String, parentMessage: GroupChatMessage) {
                val popup = PopupMenu(context, aiReplyText)
                popup.menuInflater.inflate(R.menu.ai_reply_context_menu, popup.menu)
                
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_copy -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("AI回复", replyContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            true
                        }
                        R.id.action_share -> {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$aiName: $replyContent")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享AI回复"))
                            true
                        }
                        R.id.action_regenerate -> {
                            actionListener?.onRegenerateAIReply(parentMessage, aiName)
                            true
                        }
                        R.id.action_delete -> {
                            actionListener?.onDeleteAIReply(parentMessage, aiName)
                            true
                        }
                        else -> false
                    }
                }
                
                popup.show()
            }
        }
    }
}