package com.example.aifloatingball.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.example.aifloatingball.manager.AIServiceType

/**
 * 群聊数据模型
 */
@Parcelize
data class GroupChat(
    val id: String,
    val name: String,
    val description: String? = null,
    val avatar: String? = null,
    val members: List<GroupMember>,
    val createdTime: Long = System.currentTimeMillis(),
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val settings: GroupSettings = GroupSettings()
) : Parcelable

/**
 * 群成员数据模型
 */
@Parcelize
data class GroupMember(
    val id: String,
    val name: String,
    val type: MemberType,
    val aiServiceType: AIServiceType? = null,
    val avatar: String? = null,
    val isActive: Boolean = true,
    val joinTime: Long = System.currentTimeMillis(),
    val role: MemberRole = MemberRole.MEMBER,
    val customData: Map<String, String> = emptyMap()
) : Parcelable

/**
 * 成员类型
 */
enum class MemberType {
    USER,   // 用户
    AI      // AI助手
}

/**
 * 成员角色
 */
enum class MemberRole {
    OWNER,      // 群主
    ADMIN,      // 管理员
    MEMBER      // 普通成员
}

/**
 * 群聊设置
 */
@Parcelize
data class GroupSettings(
    val allowAllMembersReply: Boolean = true,           // 允许所有成员回复
    val simultaneousReply: Boolean = true,              // 同时回复模式
    val replyDelay: Long = 0L,                         // 回复延迟（毫秒）
    val maxConcurrentReplies: Int = 5,                 // 最大并发回复数
    val enableTypingIndicator: Boolean = true,         // 显示输入指示器
    val autoSaveHistory: Boolean = true,               // 自动保存历史记录
    val customPrompt: String? = null                   // 自定义群聊提示词
) : Parcelable

/**
 * 群聊消息数据模型
 */
@Parcelize
data class GroupChatMessage(
    val id: String,
    val content: String,
    val senderId: String,
    val senderName: String,
    val senderType: MemberType,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: GroupMessageType = GroupMessageType.TEXT,
    val replyToMessageId: String? = null,
    val reactions: List<MessageReaction> = emptyList(),
    val isEdited: Boolean = false,
    val editTime: Long? = null,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable

/**
 * 群聊消息类型
 */
enum class GroupMessageType {
    TEXT,           // 文本消息
    SYSTEM,         // 系统消息
    JOIN,           // 加入群聊
    LEAVE,          // 离开群聊
    SETTINGS_CHANGE // 设置变更
}

/**
 * 消息反应
 */
@Parcelize
data class MessageReaction(
    val emoji: String,
    val userId: String,
    val userName: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

/**
 * AI回复状态
 */
enum class AIReplyStatus {
    PENDING,        // 等待回复
    TYPING,         // 正在输入
    COMPLETED,      // 回复完成
    ERROR,          // 回复错误
    TIMEOUT,        // 回复超时
    CANCELLED       // 用户取消
}

/**
 * AI回复信息
 */
@Parcelize
data class AIReplyInfo(
    val aiId: String,
    val aiName: String,
    val status: AIReplyStatus,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val errorMessage: String? = null
) : Parcelable