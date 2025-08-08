package com.example.aifloatingball.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 聊天联系人数据模型
 */
@Parcelize
data class ChatContact(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val type: ContactType,
    val description: String? = null,
    val isOnline: Boolean = false,
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val customData: Map<String, String> = emptyMap()
) : Parcelable

/**
 * 联系人类型
 */
enum class ContactType {
    AI      // AI助手
}

/**
 * 联系人分类
 */
data class ContactCategory(
    val name: String,
    val contacts: List<ChatContact>,
    val isExpanded: Boolean = true
) 