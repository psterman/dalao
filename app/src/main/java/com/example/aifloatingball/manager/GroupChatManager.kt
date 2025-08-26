package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 群聊管理器
 * 负责管理群聊的创建、消息处理、成员管理等功能
 */
class GroupChatManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "GroupChatManager"
        private const val PREFS_NAME = "group_chat_prefs"
        private const val KEY_GROUP_CHATS = "group_chats"
        private const val KEY_GROUP_MESSAGES = "group_messages_"
        
        @Volatile
        private var INSTANCE: GroupChatManager? = null
        
        fun getInstance(context: Context): GroupChatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GroupChatManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val aiApiManager = AIApiManager(context)
    
    // 群聊数据缓存
    private val groupChats = mutableMapOf<String, GroupChat>()
    private val groupMessages = ConcurrentHashMap<String, MutableList<GroupChatMessage>>()
    private val aiReplyStatus = ConcurrentHashMap<String, MutableMap<String, AIReplyInfo>>()
    
    // 协程作用域
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        loadGroupChats()
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
        
        // 保存群聊
        groupChats[groupId] = groupChat
        groupMessages[groupId] = mutableListOf()
        saveGroupChats()
        
        // 添加系统消息
        addSystemMessage(groupId, "群聊创建成功，欢迎大家！")
        
        Log.d(TAG, "创建群聊成功: $name, ID: $groupId")
        return groupChat
    }
    
    /**
     * 获取所有群聊
     */
    fun getAllGroupChats(): List<GroupChat> {
        return groupChats.values.toList()
    }
    
    /**
     * 根据ID获取群聊
     */
    fun getGroupChat(groupId: String): GroupChat? {
        return groupChats[groupId]
    }
    
    /**
     * 获取群聊消息
     */
    fun getGroupMessages(groupId: String): List<GroupChatMessage> {
        return groupMessages[groupId]?.toList() ?: emptyList()
    }
    
    /**
     * 发送用户消息到群聊
     */
    suspend fun sendUserMessage(groupId: String, content: String): Boolean {
        val groupChat = groupChats[groupId] ?: return false
        
        // 创建用户消息
        val userMessage = GroupChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            senderId = "user",
            senderName = "用户",
            senderType = MemberType.USER
        )
        
        // 添加消息到群聊
        addMessageToGroup(groupId, userMessage)
        
        // 触发AI回复
        if (groupChat.settings.allowAllMembersReply) {
            triggerAIReplies(groupId, content, groupChat)
        }
        
        return true
    }
    
    /**
     * 触发AI成员回复
     */
    private suspend fun triggerAIReplies(groupId: String, userMessage: String, groupChat: GroupChat) {
        val aiMembers = groupChat.members.filter { it.type == MemberType.AI && it.isActive }
        
        if (aiMembers.isEmpty()) {
            Log.w(TAG, "群聊 $groupId 中没有活跃的AI成员")
            return
        }
        
        // 初始化AI回复状态
        val replyStatusMap = mutableMapOf<String, AIReplyInfo>()
        aiMembers.forEach { member ->
            replyStatusMap[member.id] = AIReplyInfo(
                aiId = member.id,
                aiName = member.name,
                status = AIReplyStatus.PENDING
            )
        }
        aiReplyStatus[groupId] = replyStatusMap
        
        // 根据设置决定回复方式
        if (groupChat.settings.simultaneousReply) {
            // 同时回复模式
            triggerSimultaneousReplies(groupId, userMessage, aiMembers, groupChat)
        } else {
            // 顺序回复模式
            triggerSequentialReplies(groupId, userMessage, aiMembers, groupChat)
        }
    }
    
    /**
     * 同时回复模式
     */
    private suspend fun triggerSimultaneousReplies(
        groupId: String,
        userMessage: String,
        aiMembers: List<GroupMember>,
        groupChat: GroupChat
    ) {
        val maxConcurrent = minOf(aiMembers.size, groupChat.settings.maxConcurrentReplies)
        
        // 使用协程并发处理AI回复
        val jobs = aiMembers.take(maxConcurrent).map { member ->
            managerScope.async {
                processAIReply(groupId, userMessage, member, groupChat)
            }
        }
        
        // 等待所有回复完成
        jobs.awaitAll()
    }
    
    /**
     * 顺序回复模式
     */
    private suspend fun triggerSequentialReplies(
        groupId: String,
        userMessage: String,
        aiMembers: List<GroupMember>,
        groupChat: GroupChat
    ) {
        for (member in aiMembers) {
            processAIReply(groupId, userMessage, member, groupChat)
            
            // 添加延迟
            if (groupChat.settings.replyDelay > 0) {
                delay(groupChat.settings.replyDelay)
            }
        }
    }
    
    /**
     * 处理单个AI的回复
     */
    private suspend fun processAIReply(
        groupId: String,
        userMessage: String,
        aiMember: GroupMember,
        groupChat: GroupChat
    ) {
        val aiServiceType = aiMember.aiServiceType ?: return
        
        try {
            // 更新状态为正在输入
            updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.TYPING)
            
            // 获取对话历史
            val conversationHistory = buildConversationHistory(groupId, aiMember.id)
            
            // 调用AI API
            val response = callAIAPI(aiServiceType, userMessage, conversationHistory, groupChat.settings.customPrompt)
            
            if (response != null) {
                // 创建AI回复消息
                val aiMessage = GroupChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = response,
                    senderId = aiMember.id,
                    senderName = aiMember.name,
                    senderType = MemberType.AI
                )
                
                // 添加消息到群聊
                addMessageToGroup(groupId, aiMessage)
                
                // 更新状态为完成
                updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.COMPLETED)
                
                Log.d(TAG, "AI ${aiMember.name} 回复成功")
            } else {
                updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.ERROR, "API调用失败")
                Log.e(TAG, "AI ${aiMember.name} 回复失败")
            }
            
        } catch (e: Exception) {
            updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.ERROR, e.message)
            Log.e(TAG, "AI ${aiMember.name} 回复异常", e)
        }
    }
    
    /**
     * 调用AI API
     */
    private suspend fun callAIAPI(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>>,
        customPrompt: String?
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 构建完整的消息内容
                val fullMessage = if (customPrompt != null) {
                    "$customPrompt\n\n$message"
                } else {
                    message
                }
                
                // 使用统一的sendMessage方法
                var response: String? = null
                val callback = object : AIApiManager.StreamingCallback {
                    override fun onChunkReceived(chunk: String) {
                        // 累积响应内容
                        if (response == null) response = ""
                        response += chunk
                    }
                    
                    override fun onComplete(fullResponse: String) {
                        response = fullResponse
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "AI API调用失败: $error")
                        response = null
                    }
                }
                
                // 调用AI API
                aiApiManager.sendMessage(serviceType, fullMessage, conversationHistory, callback)
                
                // 等待响应完成（简化处理）
                var waitTime = 0
                while (response == null && waitTime < 30000) { // 最多等待30秒
                    delay(100)
                    waitTime += 100
                }
                
                response
            } catch (e: Exception) {
                Log.e(TAG, "调用AI API失败: $serviceType", e)
                null
            }
        }
    }
    
    /**
     * 构建对话历史
     */
    private fun buildConversationHistory(groupId: String, aiId: String): List<Map<String, String>> {
        val messages = groupMessages[groupId] ?: return emptyList()
        val history = mutableListOf<Map<String, String>>()
        
        // 获取最近的对话历史（限制数量以避免过长）
        val recentMessages = messages.takeLast(20)
        
        for (message in recentMessages) {
            when (message.senderType) {
                MemberType.USER -> {
                    history.add(mapOf("role" to "user", "content" to message.content))
                }
                MemberType.AI -> {
                    if (message.senderId == aiId) {
                        history.add(mapOf("role" to "assistant", "content" to message.content))
                    }
                }
            }
        }
        
        return history
    }
    
    /**
     * 添加消息到群聊
     */
    private fun addMessageToGroup(groupId: String, message: GroupChatMessage) {
        val messages = groupMessages.getOrPut(groupId) { mutableListOf() }
        messages.add(message)
        
        // 更新群聊的最后消息信息
        groupChats[groupId]?.let { groupChat ->
            val updatedGroupChat = groupChat.copy(
                lastMessage = message.content,
                lastMessageTime = message.timestamp
            )
            groupChats[groupId] = updatedGroupChat
        }
        
        // 保存数据
        saveGroupMessages(groupId)
        saveGroupChats()
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
            senderType = MemberType.USER,
            messageType = GroupMessageType.SYSTEM
        )
        addMessageToGroup(groupId, systemMessage)
    }
    
    /**
     * 更新AI回复状态
     */
    private fun updateAIReplyStatus(
        groupId: String,
        aiId: String,
        status: AIReplyStatus,
        errorMessage: String? = null
    ) {
        val statusMap = aiReplyStatus[groupId] ?: return
        val currentInfo = statusMap[aiId] ?: return
        
        val updatedInfo = currentInfo.copy(
            status = status,
            endTime = if (status == AIReplyStatus.COMPLETED || status == AIReplyStatus.ERROR) {
                System.currentTimeMillis()
            } else null,
            errorMessage = errorMessage
        )
        
        statusMap[aiId] = updatedInfo
    }
    
    /**
     * 获取AI回复状态
     */
    fun getAIReplyStatus(groupId: String): Map<String, AIReplyInfo> {
        return aiReplyStatus[groupId]?.toMap() ?: emptyMap()
    }
    
    /**
     * 删除群聊
     */
    fun deleteGroupChat(groupId: String): Boolean {
        val removed = groupChats.remove(groupId) != null
        if (removed) {
            groupMessages.remove(groupId)
            aiReplyStatus.remove(groupId)
            saveGroupChats()
            
            // 删除消息数据
            prefs.edit().remove(KEY_GROUP_MESSAGES + groupId).apply()
        }
        return removed
    }
    
    /**
     * 更新群聊设置
     */
    fun updateGroupSettings(groupId: String, settings: GroupSettings): Boolean {
        val groupChat = groupChats[groupId] ?: return false
        val updatedGroupChat = groupChat.copy(settings = settings)
        groupChats[groupId] = updatedGroupChat
        saveGroupChats()
        return true
    }
    
    /**
     * 添加AI成员到群聊
     */
    fun addAIMemberToGroup(groupId: String, aiServiceType: AIServiceType): Boolean {
        val groupChat = groupChats[groupId] ?: return false
        
        // 检查是否已存在该AI成员
        val existingMember = groupChat.members.find { 
            it.type == MemberType.AI && it.aiServiceType == aiServiceType 
        }
        
        if (existingMember != null) {
            Log.w(TAG, "AI成员 ${aiServiceType.name} 已存在于群聊 $groupId 中")
            return false
        }
        
        // 创建新的AI成员
        val newMember = GroupMember(
            id = "ai_${aiServiceType.name.lowercase()}",
            name = getAIDisplayName(aiServiceType),
            type = MemberType.AI,
            aiServiceType = aiServiceType
        )
        
        // 更新群聊成员列表
        val updatedMembers = groupChat.members.toMutableList()
        updatedMembers.add(newMember)
        
        val updatedGroupChat = groupChat.copy(members = updatedMembers)
        groupChats[groupId] = updatedGroupChat
        saveGroupChats()
        
        // 添加系统消息
        addSystemMessage(groupId, "${newMember.name} 加入了群聊")
        
        return true
    }
    
    /**
     * 从群聊中移除AI成员
     */
    fun removeAIMemberFromGroup(groupId: String, aiServiceType: AIServiceType): Boolean {
        val groupChat = groupChats[groupId] ?: return false
        
        val memberToRemove = groupChat.members.find { 
            it.type == MemberType.AI && it.aiServiceType == aiServiceType 
        } ?: return false
        
        // 更新群聊成员列表
        val updatedMembers = groupChat.members.filter { it.id != memberToRemove.id }
        val updatedGroupChat = groupChat.copy(members = updatedMembers)
        groupChats[groupId] = updatedGroupChat
        saveGroupChats()
        
        // 添加系统消息
        addSystemMessage(groupId, "${memberToRemove.name} 离开了群聊")
        
        return true
    }
    
    /**
     * 获取AI显示名称
     */
    private fun getAIDisplayName(aiServiceType: AIServiceType): String {
        return when (aiServiceType) {
            AIServiceType.DEEPSEEK -> "DeepSeek"
            AIServiceType.CHATGPT -> "ChatGPT"
            AIServiceType.CLAUDE -> "Claude"
            AIServiceType.GEMINI -> "Gemini"
            AIServiceType.ZHIPU_AI -> "智谱AI"
            AIServiceType.WENXIN -> "文心一言"
            AIServiceType.QIANWEN -> "通义千问"
            AIServiceType.XINGHUO -> "讯飞星火"
            AIServiceType.KIMI -> "Kimi"
        }
    }
    
    /**
     * 保存群聊数据
     */
    private fun saveGroupChats() {
        try {
            val json = gson.toJson(groupChats.values.toList())
            prefs.edit().putString(KEY_GROUP_CHATS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊数据失败", e)
        }
    }
    
    /**
     * 加载群聊数据
     */
    private fun loadGroupChats() {
        try {
            val json = prefs.getString(KEY_GROUP_CHATS, null) ?: return
            val type = object : TypeToken<List<GroupChat>>() {}.type
            val loadedGroupChats: List<GroupChat> = gson.fromJson(json, type)
            
            groupChats.clear()
            loadedGroupChats.forEach { groupChat ->
                groupChats[groupChat.id] = groupChat
                loadGroupMessages(groupChat.id)
            }
            
            Log.d(TAG, "加载群聊数据成功，共 ${groupChats.size} 个群聊")
        } catch (e: Exception) {
            Log.e(TAG, "加载群聊数据失败", e)
        }
    }
    
    /**
     * 保存群聊消息
     */
    private fun saveGroupMessages(groupId: String) {
        try {
            val messages = groupMessages[groupId] ?: return
            val json = gson.toJson(messages)
            prefs.edit().putString(KEY_GROUP_MESSAGES + groupId, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊消息失败: $groupId", e)
        }
    }
    
    /**
     * 加载群聊消息
     */
    private fun loadGroupMessages(groupId: String) {
        try {
            val json = prefs.getString(KEY_GROUP_MESSAGES + groupId, null) ?: return
            val type = object : TypeToken<List<GroupChatMessage>>() {}.type
            val loadedMessages: List<GroupChatMessage> = gson.fromJson(json, type)
            
            groupMessages[groupId] = loadedMessages.toMutableList()
        } catch (e: Exception) {
            Log.e(TAG, "加载群聊消息失败: $groupId", e)
            groupMessages[groupId] = mutableListOf()
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        managerScope.cancel()
    }
}