package com.example.aifloatingball.model

import android.os.Parcel
import android.os.Parcelable

/**
 * 聊天联系人数据模型
 */
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
    val customData: Map<String, String> = emptyMap(),
    // 群聊相关属性
    val groupId: String? = null,
    val memberCount: Int = 0,
    val aiMembers: List<String> = emptyList()
) : Parcelable {
    
    // 自定义Parcel实现，确保反序列化时aiMembers不为null
    private constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        avatar = parcel.readString(),
        type = ContactType.valueOf(parcel.readString() ?: ContactType.AI.name),
        description = parcel.readString(),
        isOnline = parcel.readInt() == 1,
        lastMessage = parcel.readString(),
        lastMessageTime = parcel.readLong(),
        unreadCount = parcel.readInt(),
        isPinned = parcel.readInt() == 1,
        isMuted = parcel.readInt() == 1,
        customData = (parcel.readHashMap(String::class.java.classLoader) as? Map<String, String>) ?: emptyMap(),
        groupId = parcel.readString(),
        memberCount = parcel.readInt(),
        aiMembers = parcel.createStringArrayList() ?: emptyList()
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(avatar)
        parcel.writeString(type.name)
        parcel.writeString(description)
        parcel.writeInt(if (isOnline) 1 else 0)
        parcel.writeString(lastMessage)
        parcel.writeLong(lastMessageTime)
        parcel.writeInt(unreadCount)
        parcel.writeInt(if (isPinned) 1 else 0)
        parcel.writeInt(if (isMuted) 1 else 0)
        parcel.writeMap(customData)
        parcel.writeString(groupId)
        parcel.writeInt(memberCount)
        parcel.writeStringList(aiMembers)
    }
    
    override fun describeContents(): Int {
        return 0
    }
    
    companion object CREATOR : Parcelable.Creator<ChatContact> {
        override fun createFromParcel(parcel: Parcel): ChatContact {
            return ChatContact(parcel)
        }
        
        override fun newArray(size: Int): Array<ChatContact?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * 联系人类型
 */
enum class ContactType {
    AI,     // AI助手
    GROUP   // 群聊
}

/**
 * 联系人分类
 */
data class ContactCategory(
    val name: String,
    val contacts: MutableList<ChatContact>,
    val isExpanded: Boolean = true,
    val isPinned: Boolean = false
)