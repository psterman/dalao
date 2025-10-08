package com.example.aifloatingball.adapter

import android.content.Context
import android.util.Log
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
import com.example.aifloatingball.utils.SimpleMarkdownRenderer
import com.example.aifloatingball.utils.AdvancedMarkdownRenderer
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*
import android.text.Html
import androidx.appcompat.widget.PopupMenu
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import android.app.AlertDialog

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
    private val expandedStates = mutableMapOf<String, Boolean>()
    
    // Markdown渲染器
    private val markdownRenderer = SimpleMarkdownRenderer.getInstance(context)
    private val advancedMarkdownRenderer = AdvancedMarkdownRenderer.getInstance(context)

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
     * 在顶部添加更多历史消息（用于分页加载）
     */
    fun addMessagesToTop(newMessages: List<GroupChatMessage>) {
        messages.addAll(0, newMessages)
        notifyItemRangeInserted(0, newMessages.size)
    }
    
    /**
     * 格式化AI回复内容 - 增强版文本排版
     */
    private fun formatAIContent(content: String): String {
        // 使用高级Markdown渲染器进行智能格式化
        return advancedMarkdownRenderer.getPlainText(content)
    }
    
    /**
     * 应用DeepSeek风格的格式化
     */
    private fun applyDeepSeekFormatting(content: String): String {
        var formatted = content
        
        // 清理HTML标签
        formatted = cleanHtmlTags(formatted)
        
        // 去掉表情符号
        formatted = formatted.replace("[\uD83C-\uDBFF\uDC00-\uDFFF]+".toRegex(), "")
        
        // 格式化标题
        formatted = formatHeadings(formatted)
        
        // 格式化列表
        formatted = formatLists(formatted)
        
        // 格式化代码块
        formatted = formatCodeBlocks(formatted)
        
        // 格式化强调
        formatted = formatEmphasis(formatted)
        
        // 格式化段落
        formatted = formatParagraphs(formatted)
        
        // 格式化特殊结构
        formatted = formatSpecialStructures(formatted)
        
        // 最终清理
        formatted = finalCleanup(formatted)
        
        return formatted
    }
    
    /**
     * 清理HTML标签
     */
    private fun cleanHtmlTags(content: String): String {
        var cleaned = content
        
        // 处理常见的HTML标签
        cleaned = cleaned.replace("<p[^>]*>".toRegex(), "")
        cleaned = cleaned.replace("</p>".toRegex(), "\n\n")
        cleaned = cleaned.replace("<br[^>]*>".toRegex(), "\n")
        cleaned = cleaned.replace("<strong[^>]*>(.*?)</strong>".toRegex(), "【$1】")
        cleaned = cleaned.replace("<b[^>]*>(.*?)</b>".toRegex(), "【$1】")
        cleaned = cleaned.replace("<em[^>]*>(.*?)</em>".toRegex(), "$1")
        cleaned = cleaned.replace("<i[^>]*>(.*?)</i>".toRegex(), "$1")
        cleaned = cleaned.replace("<code[^>]*>(.*?)</code>".toRegex(), "「$1」")
        cleaned = cleaned.replace("<pre[^>]*>(.*?)</pre>".toRegex(RegexOption.DOT_MATCHES_ALL), "\n「$1」\n")
        cleaned = cleaned.replace("<h[1-6][^>]*>(.*?)</h[1-6]>".toRegex(), "\n■ $1\n")
        cleaned = cleaned.replace("<ul[^>]*>".toRegex(), "")
        cleaned = cleaned.replace("</ul>".toRegex(), "\n")
        cleaned = cleaned.replace("<ol[^>]*>".toRegex(), "")
        cleaned = cleaned.replace("</ol>".toRegex(), "\n")
        cleaned = cleaned.replace("<li[^>]*>(.*?)</li>".toRegex(), "• $1\n")
        cleaned = cleaned.replace("<a[^>]*>(.*?)</a>".toRegex(), "$1")
        cleaned = cleaned.replace("<div[^>]*>".toRegex(), "")
        cleaned = cleaned.replace("</div>".toRegex(), "\n")
        cleaned = cleaned.replace("<span[^>]*>(.*?)</span>".toRegex(), "$1")
        cleaned = cleaned.replace("<[^>]+>".toRegex(), "")
        
        // 解码HTML实体
        cleaned = cleaned.replace("&amp;".toRegex(), "&")
        cleaned = cleaned.replace("&lt;".toRegex(), "<")
        cleaned = cleaned.replace("&gt;".toRegex(), ">")
        cleaned = cleaned.replace("&quot;".toRegex(), "\"")
        cleaned = cleaned.replace("&#39;".toRegex(), "'")
        cleaned = cleaned.replace("&nbsp;".toRegex(), " ")
        
        return cleaned
    }
    
    /**
     * 格式化标题 - 参考DeepSeek格式
     */
    private fun formatHeadings(content: String): String {
        var formatted = content
        
        formatted = formatted.replace("^#{6}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{5}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{4}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{3}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▫ $1\n")
        formatted = formatted.replace("^#{2}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1\n")
        formatted = formatted.replace("^#{1}\\s+(.*)$".toRegex(RegexOption.MULTILINE), "\n■ $1\n")
        
        // 处理常见的小标题格式（参考DeepSeek）
        formatted = formatted.replace("^([一二三四五六七八九十]+[、.])\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1 $2\n")
        formatted = formatted.replace("^([0-9]+[、.])\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n▪ $1 $2\n")
        
        // 处理带冒号的小标题
        formatted = formatted.replace("^([^：:]+[：:])\\s*$".toRegex(RegexOption.MULTILINE), "\n▪ $1\n")
        
        return formatted
    }
    
    /**
     * 格式化列表 - 参考DeepSeek格式
     */
    private fun formatLists(content: String): String {
        var formatted = content
        
        formatted = formatted.replace("^\\s*(\\d+)\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  $1. $2")
        formatted = formatted.replace("^\\s*([一二三四五六七八九十]+)\\.\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  $1. $2")
        formatted = formatted.replace("^\\s*[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  • $1")
        formatted = formatted.replace("^\\s{2,4}[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "    ◦ $1")
        formatted = formatted.replace("^\\s{6,8}[-*+]\\s+(.*)$".toRegex(RegexOption.MULTILINE), "      ▪ $1")
        formatted = formatted.replace("^\\s*([•·▪▫])\\s+(.*)$".toRegex(RegexOption.MULTILINE), "  • $2")
        
        return formatted
    }
    
    /**
     * 格式化代码块
     */
    private fun formatCodeBlocks(content: String): String {
        var formatted = content
        
        formatted = formatted.replace("```([\\s\\S]*?)```".toRegex()) { matchResult ->
            val code = matchResult.groupValues[1].trim()
            "\n┌─ 代码块 ─┐\n$code\n└─────────┘\n"
        }
        
        formatted = formatted.replace("`([^`]+)`".toRegex(), "「$1」")
        
        return formatted
    }
    
    /**
     * 格式化强调 - 参考DeepSeek格式
     */
    private fun formatEmphasis(content: String): String {
        var formatted = content
        
        formatted = formatted.replace("\\*\\*(.*?)\\*\\*".toRegex(), "【$1】")
        formatted = formatted.replace("__(.*?)__".toRegex(), "【$1】")
        formatted = formatted.replace("\\*(.*?)\\*".toRegex(), "$1")
        formatted = formatted.replace("_(.*?)_".toRegex(), "$1")
        formatted = formatted.replace("~~(.*?)~~".toRegex(), "~~$1~~")
        
        // 处理特殊强调格式（参考DeepSeek）
        formatted = formatted.replace("《([^》]+)》".toRegex(), "【$1】")
        formatted = formatted.replace("\"([^\"]+)\"".toRegex(), "「$1」")
        formatted = formatted.replace("'([^']+)'".toRegex(), "「$1」")
        
        return formatted
    }
    
    /**
     * 格式化段落 - 参考DeepSeek格式
     */
    private fun formatParagraphs(content: String): String {
        var formatted = content
        
        formatted = formatted.replace("([。！？])\\s*([A-Z0-9一-龯])".toRegex(), "$1\n\n$2")
        formatted = formatted.replace("([：:])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        formatted = formatted.replace("([；;])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        formatted = formatted.replace("([，,])\\s*([一-龯A-Z])".toRegex(), "$1\n$2")
        formatted = formatted.replace("(\\d+\\.\\s+[^\\n]+)\\n(\\d+\\.\\s+)".toRegex(), "$1\n\n$2")
        formatted = formatted.replace("(▪\\s+[^\\n]+)\\n([^▪\\n])".toRegex(), "$1\n$2")
        formatted = formatted.replace("\\n\\s*\\n\\s*\\n+".toRegex(), "\n\n")
        
        return formatted
    }
    
    /**
     * 格式化特殊结构 - 参考DeepSeek格式
     */
    private fun formatSpecialStructures(content: String): String {
        var formatted = content
        
        formatted = formatted.replace("^问[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n❓ 问：$1\n")
        formatted = formatted.replace("^答[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 答：$1\n")
        formatted = formatted.replace("^步骤\\s*(\\d+)[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📋 步骤$1：$2\n")
        formatted = formatted.replace("^第\\s*(\\d+)\\s*步[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📋 第$1步：$2\n")
        formatted = formatted.replace("^要点\\s*(\\d+)[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🔹 要点$1：$2\n")
        formatted = formatted.replace("^注意[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n⚠️ 注意：$1\n")
        formatted = formatted.replace("^提示[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 提示：$1\n")
        formatted = formatted.replace("^总结[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📝 总结：$1\n")
        formatted = formatted.replace("^结论[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📝 结论：$1\n")
        
        // 处理DeepSeek风格的特殊格式
        formatted = formatted.replace("^好的[，,]?\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n💡 $1\n")
        formatted = formatted.replace("^这里\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n📖 $1\n")
        formatted = formatted.replace("^核心\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n⭐ 核心$1\n")
        formatted = formatted.replace("^主要\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🔸 主要$1\n")
        formatted = formatted.replace("^特点[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n✨ 特点：$1\n")
        formatted = formatted.replace("^特色[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n✨ 特色：$1\n")
        formatted = formatted.replace("^优势[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🚀 优势：$1\n")
        formatted = formatted.replace("^优点[:：]\\s*(.*)$".toRegex(RegexOption.MULTILINE), "\n🚀 优点：$1\n")
        
        return formatted
    }
    
    /**
     * 最终清理
     */
    private fun finalCleanup(content: String): String {
        var cleaned = content
        
        cleaned = cleaned.trim()
        cleaned = cleaned.replace("\\n{3,}".toRegex(), "\n\n")
        cleaned = cleaned.replace("\\s{2,}".toRegex(), " ")
        cleaned = cleaned.replace("^\\s+$".toRegex(RegexOption.MULTILINE), "")
        
        return cleaned
    }

    /**
     * 更新AI流式回复内容
     */
    fun updateAIStreamingReply(messagePosition: Int, aiName: String, newContent: String) {
        if (messagePosition >= 0 && messagePosition < messages.size) {
            val message = messages[messagePosition]
            
            // 更新消息内容
            val updatedMessage = message.copy(content = newContent)
            messages[messagePosition] = updatedMessage
            
            // 通知适配器更新特定项
            notifyItemChanged(messagePosition)
        }
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

            // 显示回复列表
            repliesRecyclerView.visibility = View.VISIBLE
            
            // 从消息的metadata中获取AI回复数据
            val aiReplies = mutableMapOf<String, String>()
            
            // 检查是否有AI回复数据存储在metadata中
            message.metadata.forEach { (key, value) ->
                if (key.startsWith("ai_reply_")) {
                    val aiName = key.removePrefix("ai_reply_")
                    aiReplies[aiName] = value
                }
            }
            
            // 如果没有在metadata中找到AI回复，尝试从消息内容中解析
            if (aiReplies.isEmpty() && message.content.isNotEmpty()) {
                // 假设这是一个包含AI回复的消息，使用发送者名称作为AI名称
                if (message.senderType == com.example.aifloatingball.model.MemberType.AI) {
                    aiReplies[message.senderName] = message.content
                }
            }
            
            // 设置AI回复适配器
            if (aiReplies.isNotEmpty()) {
                val aiRepliesAdapter = AIRepliesAdapter(context, aiReplies, message)
                repliesRecyclerView.adapter = aiRepliesAdapter
                repliesRecyclerView.layoutManager = LinearLayoutManager(context)
            } else {
                repliesRecyclerView.visibility = View.GONE
            }
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

            // 添加调试日志
            Log.d("GroupChatAdapter", "绑定AI消息: ${message.senderName}, 内容长度: ${message.content.length}, 内容: ${message.content.take(50)}...")
            
            // 检查内容是否为空
            if (message.content.isEmpty()) {
                aiText.text = "[AI回复为空]"
                aiText.setTextColor(context.getColor(android.R.color.darker_gray))
                Log.w("GroupChatAdapter", "AI消息内容为空: ${message.senderName}, 消息ID: ${message.id}")
            } else {
                // 使用高级Markdown渲染器渲染AI回复内容
                val spannableString = advancedMarkdownRenderer.renderAIResponse(message.content)
                aiText.text = spannableString
                aiText.setTextColor(context.getColor(android.R.color.black))
            }
            
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
        
        /**
         * 更新特定AI的回复内容
         */
        fun updateAIReply(aiName: String, newContent: String) {
            val position = aiNames.indexOf(aiName)
            if (position >= 0) {
                // 更新数据
                (aiReplies as MutableMap)[aiName] = newContent
                // 通知适配器更新
                notifyItemChanged(position)
            }
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
            
            private var originalText = ""
            private var currentMessageId = ""
            private var isExpanded: Boolean
                get() = expandedStates[currentMessageId] ?: false
                set(value) {
                    expandedStates[currentMessageId] = value
                }

            fun bind(aiName: String, replyContent: String, parentMessage: GroupChatMessage) {
                this.aiName.text = aiName
                originalText = replyContent
                currentMessageId = "${parentMessage.id}_${aiName}"
                
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
                    aiReplyText.text = ""
                    aiReplyText.maxLines = Integer.MAX_VALUE
                } else {
                    aiReplyStatus.visibility = View.GONE
                    aiReplyProgress.visibility = View.GONE
                    
                    // 动态检查文本长度，决定是否显示展开/收缩按钮
                    setupExpandCollapse(processedContent)
                }
                
                // 设置操作按钮监听器
                setupOperationListeners(aiName, replyContent, parentMessage)
            }
            
            /**
             * 更新AI回复内容（用于流式回复）
             */
            fun updateReplyContent(replyContent: String) {
                originalText = replyContent
                val processedContent = processMarkdown(replyContent)
                
                if (replyContent.isEmpty()) {
                    aiReplyStatus.visibility = View.VISIBLE
                    aiReplyProgress.visibility = View.VISIBLE
                    aiReplyStatus.text = "回复中..."
                    expandCollapseButton.visibility = View.GONE
                    aiReplyText.text = ""
                } else {
                    aiReplyStatus.visibility = View.GONE
                    aiReplyProgress.visibility = View.GONE
                    
                    // 动态更新展开/收缩状态
                    setupExpandCollapse(processedContent)
                }
            }
            
            private fun setupOperationListeners(aiName: String, replyContent: String, parentMessage: GroupChatMessage) {
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
                    // 处理代码块（多行）
                    .replace(Regex("```[\\w]*\\n?([\\s\\S]*?)```"), { match ->
                        val codeContent = match.groupValues[1].trim()
                        "\n$codeContent\n"
                    })
                    // 处理行内代码
                    .replace(Regex("`([^`]+)`"), "$1")
                    // 处理粗体
                    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                    .replace(Regex("__([^_]+)__"), "$1")
                    // 处理斜体
                    .replace(Regex("\\*([^*]+)\\*"), "$1")
                    .replace(Regex("_([^_]+)_"), "$1")
                    // 处理删除线
                    .replace(Regex("~~([^~]+)~~"), "$1")
                    // 处理标题
                    .replace(Regex("^#{1,6}\\s+(.+)$", RegexOption.MULTILINE), "$1")
                    // 处理无序列表
                    .replace(Regex("^\\s*[-*+]\\s+(.+)$", RegexOption.MULTILINE), "• $1")
                    // 处理有序列表
                    .replace(Regex("^\\s*\\d+\\.\\s+(.+)$", RegexOption.MULTILINE), "$1")
                    // 处理链接
                    .replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)"), "$1")
                    // 处理引用
                    .replace(Regex("^>\\s+(.+)$", RegexOption.MULTILINE), "$1")
                    // 移除多余的空行
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()
            }
            
            private fun setupExpandCollapse(processedContent: String) {
                val lines = processedContent.split("\n")
                val lineCount = lines.size
                val charCount = processedContent.length
                
                // 智能判断是否需要展开/收缩功能
                val needsCollapse = when {
                    lineCount > 3 -> true // 超过3行
                    charCount > 150 -> true // 超过150个字符
                    lines.any { it.length > 50 } -> true // 任意一行超过50个字符
                    else -> false
                }
                
                if (needsCollapse) {
                    expandCollapseButton.visibility = View.VISIBLE
                    
                    // 设置初始状态
                    if (!isExpanded) {
                        aiReplyText.maxLines = 3
                        expandCollapseButton.text = "展开 ▼"
                    } else {
                        aiReplyText.maxLines = Integer.MAX_VALUE
                        expandCollapseButton.text = "收缩 ▲"
                    }
                    
                    aiReplyText.text = processedContent
                    
                    expandCollapseButton.setOnClickListener {
                        if (isExpanded) {
                            // 收缩
                            aiReplyText.maxLines = 3
                            expandCollapseButton.text = "展开 ▼"
                            isExpanded = false
                        } else {
                            // 展开
                            aiReplyText.maxLines = Integer.MAX_VALUE
                            expandCollapseButton.text = "收缩 ▲"
                            isExpanded = true
                        }
                        
                        // 添加动画效果
                        aiReplyText.animate()
                            .alpha(0.7f)
                            .setDuration(100)
                            .withEndAction {
                                aiReplyText.requestLayout()
                                aiReplyText.animate()
                                    .alpha(1.0f)
                                    .setDuration(100)
                                    .start()
                            }
                            .start()
                    }
                } else {
                    expandCollapseButton.visibility = View.GONE
                    aiReplyText.maxLines = Integer.MAX_VALUE
                    aiReplyText.text = processedContent
                }
            }
            
            private fun showContextMenu(aiName: String, replyContent: String, parentMessage: GroupChatMessage) {
                if (replyContent.isEmpty()) {
                    Toast.makeText(context, "回复内容为空", Toast.LENGTH_SHORT).show()
                    return
                }
                
                val popup = PopupMenu(context, aiReplyText)
                popup.menuInflater.inflate(R.menu.ai_reply_context_menu, popup.menu)
                
                // 根据回复状态动态调整菜单项
                val regenerateItem = popup.menu.findItem(R.id.action_regenerate)
                val deleteItem = popup.menu.findItem(R.id.action_delete)
                
                // 如果是正在回复中的消息，禁用某些操作
                if (replyContent == "回复中...") {
                    regenerateItem.isEnabled = false
                    deleteItem.isEnabled = false
                }
                
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_copy -> {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI回复 - $aiName", replyContent)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        R.id.action_share -> {
                            try {
                                val shareText = "来自 $aiName 的回复:\n\n$replyContent"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    putExtra(Intent.EXTRA_SUBJECT, "AI回复分享")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享AI回复"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        R.id.action_regenerate -> {
                            try {
                                actionListener?.onRegenerateAIReply(parentMessage, aiName)
                                Toast.makeText(context, "正在重新生成回复...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "重新生成失败", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        R.id.action_delete -> {
                            try {
                                // 显示确认对话框
                                android.app.AlertDialog.Builder(context)
                                    .setTitle("删除确认")
                                    .setMessage("确定要删除 $aiName 的这条回复吗？")
                                    .setPositiveButton("删除") { _, _ ->
                                        actionListener?.onDeleteAIReply(parentMessage, aiName)
                                        Toast.makeText(context, "已删除回复", Toast.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                        else -> false
                    }
                }
                
                try {
                    popup.show()
                } catch (e: Exception) {
                    Toast.makeText(context, "菜单显示失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}