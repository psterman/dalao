package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.R
import com.example.aifloatingball.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 群聊数据变更事件类型
 */
enum class GroupChatEventType {
    CREATED, DELETED, UPDATED, RELOADED
}

/**
 * 群聊数据变更事件
 */
data class GroupChatDataChangeEvent(
    val type: GroupChatEventType,
    val groupId: String? = null,
    val groupChat: GroupChat? = null,
    val groupChats: List<GroupChat>? = null
)

/**
 * 群聊数据变更监听器接口
 */
interface GroupChatDataChangeListener {
    fun onGroupChatCreated(groupChat: GroupChat)
    fun onGroupChatDeleted(groupId: String)
    fun onGroupChatUpdated(groupChat: GroupChat)
    fun onGroupChatsReloaded(groupChats: List<GroupChat>)
}

/**
 * 统一群聊数据管理器
 * 基于第一性原理设计，参考ChatDataManager的成功经验
 * 实现单一数据源模式，消除多数据源同步问题
 */
class UnifiedGroupChatManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "UnifiedGroupChatManager"
        private const val PREFS_NAME = "unified_group_chat_prefs"
        private const val KEY_GROUP_CHATS = "unified_group_chats"
        private const val KEY_GROUP_MESSAGES = "unified_group_messages_"
        
        @Volatile
        private var INSTANCE: UnifiedGroupChatManager? = null
        
        fun getInstance(context: Context): UnifiedGroupChatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedGroupChatManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val aiApiManager = AIApiManager(context)
    
    // 单一数据源 - 内存缓存
    private val groupChats = ConcurrentHashMap<String, GroupChat>()
    private val groupMessages = ConcurrentHashMap<String, MutableList<GroupChatMessage>>()
    private val aiReplyStatus = ConcurrentHashMap<String, MutableMap<String, AIReplyInfo>>()
    
    // 事件监听器列表
    private val dataChangeListeners = mutableListOf<GroupChatDataChangeListener>()
    
    // 协程作用域
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        loadAllData()
        Log.d(TAG, "统一群聊数据管理器初始化完成")
    }
    
    /**
     * 添加数据变更监听器
     */
    fun addDataChangeListener(listener: GroupChatDataChangeListener) {
        synchronized(dataChangeListeners) {
            dataChangeListeners.add(listener)
        }
        Log.d(TAG, "添加数据变更监听器，当前监听器数量: ${dataChangeListeners.size}")
    }
    
    /**
     * 移除数据变更监听器
     */
    fun removeDataChangeListener(listener: GroupChatDataChangeListener) {
        synchronized(dataChangeListeners) {
            dataChangeListeners.remove(listener)
        }
        Log.d(TAG, "移除数据变更监听器，当前监听器数量: ${dataChangeListeners.size}")
    }
    
    /**
     * 通知数据变更事件
     */
    private fun notifyDataChanged(event: GroupChatDataChangeEvent) {
        Log.d(TAG, "通知数据变更事件: ${event.type}, groupId: ${event.groupId}")
        
        // 在主线程中通知监听器
        CoroutineScope(Dispatchers.Main).launch {
            synchronized(dataChangeListeners) {
                dataChangeListeners.forEach { listener ->
                    try {
                        when (event.type) {
                            GroupChatEventType.CREATED -> {
                                event.groupChat?.let { listener.onGroupChatCreated(it) }
                            }
                            GroupChatEventType.DELETED -> {
                                event.groupId?.let { listener.onGroupChatDeleted(it) }
                            }
                            GroupChatEventType.UPDATED -> {
                                event.groupChat?.let { listener.onGroupChatUpdated(it) }
                            }
                            GroupChatEventType.RELOADED -> {
                                event.groupChats?.let { listener.onGroupChatsReloaded(it) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "通知监听器时发生错误", e)
                    }
                }
            }
        }
    }
    
    /**
     * 创建新群聊
     */
    fun createGroupChat(
        name: String,
        description: String? = null,
        aiMembers: List<AIServiceType>,
        settings: GroupSettings = GroupSettings()
    ): GroupChat {
        val groupId = UUID.randomUUID().toString()
        
        // 创建群成员列表
        val members = mutableListOf<GroupMember>()
        
        // 添加用户作为群主
        members.add(
            GroupMember(
                id = "user",
                name = "用户",
                type = MemberType.USER,
                role = MemberRole.OWNER
            )
        )
        
        // 添加AI成员
        aiMembers.forEach { aiType ->
            members.add(
                GroupMember(
                    id = "ai_${aiType.name.lowercase()}",
                    name = getAIDisplayName(aiType),
                    type = MemberType.AI,
                    aiServiceType = aiType,
                    role = MemberRole.MEMBER
                )
            )
        }
        
        val groupChat = GroupChat(
            id = groupId,
            name = name,
            description = description,
            members = members,
            settings = settings
        )
        
        // 保存到单一数据源
        groupChats[groupId] = groupChat
        groupMessages[groupId] = mutableListOf()
        
        // 持久化保存
        saveAllData()
        
        // 添加系统消息
        addSystemMessage(groupId, "群聊创建成功，欢迎大家！")
        
        // 通知数据变更
        notifyDataChanged(
            GroupChatDataChangeEvent(
                type = GroupChatEventType.CREATED,
                groupId = groupId,
                groupChat = groupChat
            )
        )
        
        Log.d(TAG, "创建群聊成功: $name, ID: $groupId")
        return groupChat
    }
    
    /**
     * 删除群聊
     */
    fun deleteGroupChat(groupId: String): Boolean {
        val groupChat = groupChats[groupId]
        if (groupChat == null) {
            Log.w(TAG, "尝试删除不存在的群聊: $groupId")
            return false
        }
        
        // 从单一数据源移除
        groupChats.remove(groupId)
        groupMessages.remove(groupId)
        aiReplyStatus.remove(groupId)
        
        // 持久化保存
        saveAllData()
        
        // 删除消息数据
        prefs.edit().remove(KEY_GROUP_MESSAGES + groupId).apply()
        
        // 通知数据变更
        notifyDataChanged(
            GroupChatDataChangeEvent(
                type = GroupChatEventType.DELETED,
                groupId = groupId
            )
        )
        
        Log.d(TAG, "删除群聊成功: ${groupChat.name}, ID: $groupId")
        return true
    }
    
    /**
     * 获取所有群聊
     */
    fun getAllGroupChats(): List<GroupChat> {
        return groupChats.values.toList()
    }
    
    /**
     * 获取群聊联系人列表（用于SimpleModeActivity显示）
     */
    fun getGroupChatContacts(): List<ChatContact> {
        return groupChats.values.map { groupChat ->
            ChatContact(
                id = "group_${groupChat.id}",
                name = groupChat.name,
                type = ContactType.GROUP,
                groupId = groupChat.id,
                description = groupChat.description ?: "群聊",
                avatar = null // 群聊头像通过适配器动态设置
            )
        }
    }
    
    /**
     * 根据ID获取群聊
     */
    fun getGroupChat(groupId: String): GroupChat? {
        return groupChats[groupId]
    }
    
    /**
     * 更新群聊信息
     */
    fun updateGroupChat(groupChat: GroupChat) {
        groupChats[groupChat.id] = groupChat
        saveAllData()
        
        // 通知数据变更
        notifyDataChanged(
            GroupChatDataChangeEvent(
                type = GroupChatEventType.UPDATED,
                groupId = groupChat.id,
                groupChat = groupChat
            )
        )
        
        Log.d(TAG, "更新群聊成功: ${groupChat.name}, ID: ${groupChat.id}")
    }
    
    /**
     * 重新加载所有数据
     */
    fun reloadAllData() {
        loadAllData()
        
        // 通知数据变更
        notifyDataChanged(
            GroupChatDataChangeEvent(
                type = GroupChatEventType.RELOADED,
                groupChats = getAllGroupChats()
            )
        )
        
        Log.d(TAG, "重新加载所有群聊数据完成")
    }
    
    /**
     * 获取群聊消息
     */
    fun getGroupMessages(groupId: String): List<GroupChatMessage> {
        return groupMessages[groupId]?.toList() ?: emptyList()
    }
    
    /**
     * 添加系统消息
     */
    private fun addSystemMessage(groupId: String, content: String) {
        val systemMessage = GroupChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            senderId = "system",
            senderName = "系统",
            senderType = MemberType.AI // 使用AI类型代替SYSTEM
        )
        
        val messages = groupMessages[groupId] ?: mutableListOf()
        messages.add(systemMessage)
        groupMessages[groupId] = messages
        
        saveGroupMessages(groupId)
    }
    
    /**
     * 获取AI显示名称
     */
    private fun getAIDisplayName(aiType: AIServiceType): String {
        return when (aiType) {
            AIServiceType.DEEPSEEK -> "DeepSeek"
            AIServiceType.CHATGPT -> "ChatGPT"
            AIServiceType.CLAUDE -> "Claude"
            AIServiceType.GEMINI -> "Gemini"
            AIServiceType.WENXIN -> "文心一言"
            AIServiceType.QIANWEN -> "通义千问"
            AIServiceType.XINGHUO -> "讯飞星火"
            AIServiceType.KIMI -> "Kimi"
            AIServiceType.ZHIPU_AI -> "智谱清言"
        }
    }
    
    /**
     * 加载所有数据
     */
    private fun loadAllData() {
        try {
            // 加载群聊数据
            val groupChatsJson = prefs.getString(KEY_GROUP_CHATS, null)
            if (groupChatsJson != null) {
                val type = object : TypeToken<Map<String, GroupChat>>() {}.type
                val loadedGroupChats: Map<String, GroupChat> = gson.fromJson(groupChatsJson, type)
                groupChats.clear()
                groupChats.putAll(loadedGroupChats)
                
                // 为每个群聊加载消息
                loadedGroupChats.keys.forEach { groupId ->
                    loadGroupMessages(groupId)
                }
                
                Log.d(TAG, "加载群聊数据成功，共 ${groupChats.size} 个群聊")
            } else {
                Log.d(TAG, "没有找到群聊数据")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载群聊数据失败", e)
        }
    }
    
    /**
     * 保存所有数据
     */
    private fun saveAllData() {
        try {
            val json = gson.toJson(groupChats)
            prefs.edit().putString(KEY_GROUP_CHATS, json).apply()
            Log.d(TAG, "保存群聊数据成功，共 ${groupChats.size} 个群聊")
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊数据失败", e)
        }
    }
    
    /**
     * 保存群聊消息
     */
    private fun saveGroupMessages(groupId: String) {
        try {
            val messages = groupMessages[groupId]
            if (messages != null) {
                val json = gson.toJson(messages)
                prefs.edit().putString(KEY_GROUP_MESSAGES + groupId, json).apply()
                Log.d(TAG, "保存群聊消息成功: $groupId，共 ${messages.size} 条消息")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊消息失败: $groupId", e)
        }
    }
    
    /**
     * 加载群聊消息
     */
    private fun loadGroupMessages(groupId: String) {
        try {
            val json = prefs.getString(KEY_GROUP_MESSAGES + groupId, null)
            if (json != null) {
                val type = object : TypeToken<List<GroupChatMessage>>() {}.type
                val loadedMessages: List<GroupChatMessage> = gson.fromJson(json, type)
                groupMessages[groupId] = loadedMessages.toMutableList()
                Log.d(TAG, "加载群聊消息成功: $groupId，共 ${loadedMessages.size} 条消息")
            } else {
                groupMessages[groupId] = mutableListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载群聊消息失败: $groupId", e)
            groupMessages[groupId] = mutableListOf()
        }
    }
    
    /**
     * 导入群聊数据（用于数据迁移）
     */
    suspend fun importGroupChat(groupChat: GroupChat) {
        withContext(Dispatchers.IO) {
            try {
                // 检查群聊是否已存在
                if (groupChats.containsKey(groupChat.id)) {
                    Log.w(TAG, "群聊 ${groupChat.id} 已存在，跳过导入")
                    return@withContext
                }
                
                // 添加到内存缓存
                groupChats[groupChat.id] = groupChat
                
                // 保存到持久化存储
                saveAllData()
                
                Log.d(TAG, "成功导入群聊: ${groupChat.name} (ID: ${groupChat.id})")
                
                // 通知监听器
                notifyDataChanged(
                    GroupChatDataChangeEvent(
                        type = GroupChatEventType.CREATED,
                        groupId = groupChat.id,
                        groupChat = groupChat
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "导入群聊失败: ${groupChat.id}", e)
                throw e
            }
        }
    }
    
    /**
     * 导入群聊消息（用于数据迁移）
     */
    suspend fun importGroupMessages(groupId: String, messages: List<GroupChatMessage>) {
        withContext(Dispatchers.IO) {
            try {
                // 检查群聊是否存在
                if (!groupChats.containsKey(groupId)) {
                    Log.e(TAG, "无法导入消息：群聊 $groupId 不存在")
                    return@withContext
                }
                
                // 获取现有消息
                val existingMessages = groupMessages[groupId]?.toMutableList() ?: mutableListOf()
                
                // 过滤重复消息（基于消息ID或时间戳）
                val newMessages = messages.filter { newMessage ->
                    existingMessages.none { existing -> 
                        existing.id == newMessage.id || 
                        (existing.timestamp == newMessage.timestamp && existing.content == newMessage.content)
                    }
                }
                
                if (newMessages.isNotEmpty()) {
                    // 添加新消息
                    existingMessages.addAll(newMessages)
                    
                    // 按时间戳排序
                    existingMessages.sortBy { it.timestamp }
                    
                    // 更新内存缓存
                    groupMessages[groupId] = existingMessages
                    
                    // 保存到持久化存储
                    saveGroupMessages(groupId)
                    
                    Log.d(TAG, "成功导入 ${newMessages.size} 条消息到群聊 $groupId")
                } else {
                    Log.d(TAG, "群聊 $groupId 没有新消息需要导入")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "导入群聊消息失败: $groupId", e)
                throw e
            }
        }
    }
    
    /**
     * 批量导入群聊数据（用于数据迁移）
     */
    suspend fun importGroupChatsWithMessages(
        groupChatsData: Map<String, GroupChat>,
        messagesData: Map<String, List<GroupChatMessage>>
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始批量导入: ${groupChatsData.size} 个群聊")
                
                // 导入群聊
                groupChatsData.forEach { (_, groupChat) ->
                    importGroupChat(groupChat)
                }
                
                // 导入消息
                messagesData.forEach { (groupId, messages) ->
                    importGroupMessages(groupId, messages)
                }
                
                Log.d(TAG, "批量导入完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "批量导入失败", e)
                throw e
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        managerScope.cancel()
        dataChangeListeners.clear()
        Log.d(TAG, "统一群聊数据管理器资源清理完成")
    }
    
    /**
     * 调试方法：打印当前数据状态
     */
    fun debugPrintDataState() {
        Log.d(TAG, "=== 统一群聊数据管理器状态 ===")
        Log.d(TAG, "群聊数量: ${groupChats.size}")
        Log.d(TAG, "监听器数量: ${dataChangeListeners.size}")
        groupChats.forEach { (id, groupChat) ->
            Log.d(TAG, "群聊: ${groupChat.name} (ID: $id), 成员数: ${groupChat.members.size}")
        }
        Log.d(TAG, "=== 状态打印完成 ===")
    }
}