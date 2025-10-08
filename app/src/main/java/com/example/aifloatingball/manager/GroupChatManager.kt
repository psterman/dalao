package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 群聊监听器接口
 */
interface GroupChatListener {
    fun onMessageAdded(groupId: String, message: GroupChatMessage)
    fun onMessageUpdated(groupId: String, messageIndex: Int, message: GroupChatMessage)
    fun onAIReplyStatusChanged(groupId: String, aiId: String, status: AIReplyStatus, message: String? = null)
    fun onAIReplyContentUpdated(groupId: String, messageIndex: Int, aiId: String, content: String)
    fun onGroupChatUpdated(groupChat: GroupChat)
}

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
    
    // 监听器列表
    private val groupChatListeners = mutableListOf<GroupChatListener>()
    
    // 协程作用域
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        loadGroupChats()
        // 自动修复缺少aiServiceType的AI成员
        val generalFixed = fixMissingAIServiceTypes()
        // 专门修复智谱AI成员问题
        val zhipuFixed = fixZhipuAIMembers()
        
        if (generalFixed > 0 || zhipuFixed > 0) {
            Log.d(TAG, "群聊管理器初始化完成，修复了 $generalFixed 个一般AI成员问题，$zhipuFixed 个智谱AI成员问题")
        }
    }
    
    /**
     * 添加群聊监听器
     */
    fun addGroupChatListener(listener: GroupChatListener) {
        groupChatListeners.add(listener)
    }
    
    /**
     * 移除群聊监听器
     */
    fun removeGroupChatListener(listener: GroupChatListener) {
        groupChatListeners.remove(listener)
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
        
        // 添加AI成员，确保aiServiceType正确设置
        aiMembers.forEach { aiType ->
            val aiId = if (aiType == AIServiceType.ZHIPU_AI) {
                com.example.aifloatingball.utils.AIServiceTypeUtils.generateZhipuAIId()
            } else {
                "ai_${aiType.name.lowercase()}"
            }
            
            members.add(
                GroupMember(
                    id = aiId,
                    name = getAIDisplayName(aiType),
                    type = MemberType.AI,
                    aiServiceType = aiType, // 明确设置aiServiceType
                    role = MemberRole.MEMBER
                )
            )
            
            Log.d(TAG, "添加AI成员：${getAIDisplayName(aiType)} (ID: $aiId, aiServiceType: $aiType)")
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
        
        // 同步到UnifiedGroupChatManager
        syncToUnifiedManager(groupChat)
        
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
        val messages = groupMessages[groupId]?.toList() ?: emptyList()
        Log.d(TAG, "获取群聊消息 $groupId，返回 ${messages.size} 条消息")
        return messages
    }
    
    /**
     * 发送用户消息到群聊
     */
    suspend fun sendUserMessage(groupId: String, content: String): Boolean {
        val groupChat = groupChats[groupId] ?: return false
        
        // 获取用户在群聊中的角色
        val userMember = groupChat.members.find { it.id == "user" }
        val userRole = userMember?.role?.name ?: "MEMBER"
        
        // 创建用户消息，包含角色信息
        val userMessage = GroupChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            senderId = "user",
            senderName = "用户",
            senderType = MemberType.USER,
            metadata = mapOf("userRole" to userRole)
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
     * 处理单个AI的回复（支持流式回复）
     */
    private suspend fun processAIReply(
        groupId: String,
        userMessage: String,
        aiMember: GroupMember,
        groupChat: GroupChat
    ) {
        Log.d(TAG, "开始处理AI回复: ${aiMember.name} (ID: ${aiMember.id})")
        Log.d(TAG, "AI成员详情: name=${aiMember.name}, type=${aiMember.type}, aiServiceType=${aiMember.aiServiceType}")
        
        val aiServiceType = aiMember.aiServiceType
        if (aiServiceType == null) {
            Log.e(TAG, "AI成员 ${aiMember.name} (ID: ${aiMember.id}) 缺少aiServiceType，尝试自动修复")
            
            // 尝试推断和修复aiServiceType
            val inferredType = inferAIServiceType(aiMember.name, aiMember.id)
            if (inferredType != null) {
                Log.d(TAG, "成功推断AI服务类型: $inferredType，正在修复成员配置")
                
                // 更新群聊中的成员配置
                val groupChat = groupChats[groupId]
                if (groupChat != null) {
                    val updatedMembers = groupChat.members.map { member ->
                        if (member.id == aiMember.id) {
                            if (inferredType == AIServiceType.ZHIPU_AI) {
                                // 智谱AI特殊处理，标准化名称和ID
                                member.copy(
                                    id = com.example.aifloatingball.utils.AIServiceTypeUtils.generateZhipuAIId(),
                                    name = com.example.aifloatingball.utils.AIServiceTypeUtils.getZhipuAIStandardName(),
                                    aiServiceType = inferredType
                                )
                            } else {
                                member.copy(aiServiceType = inferredType)
                            }
                        } else {
                            member
                        }
                    }
                    
                    val updatedGroupChat = groupChat.copy(members = updatedMembers)
                    groupChats[groupId] = updatedGroupChat
                    saveGroupChats()
                    
                    // 重新开始处理回复，使用修复后的成员
                    val fixedMember = updatedMembers.first { it.id == aiMember.id || it.name == aiMember.name }
                    Log.d(TAG, "AI成员修复完成，重新开始处理回复：${fixedMember.name} (aiServiceType: ${fixedMember.aiServiceType})")
                    processAIReply(groupId, userMessage, fixedMember, groupChat)
                    return
                }
            }
            
            Log.e(TAG, "无法修复AI成员 ${aiMember.name} (ID: ${aiMember.id}) 的aiServiceType")
            updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.ERROR, "AI服务类型未配置且无法自动修复")
            
            // 通知监听器错误状态
            CoroutineScope(Dispatchers.Main).launch {
                groupChatListeners.forEach { listener ->
                    listener.onAIReplyStatusChanged(groupId, aiMember.id, AIReplyStatus.ERROR, "AI服务类型未配置且无法自动修复")
                }
            }
            return
        }
        
        Log.d(TAG, "AI服务类型确认: ${aiServiceType.name}")
        
        try {
            // 更新状态为正在输入
            updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.TYPING)
            
            // 通知监听器状态变化
            CoroutineScope(Dispatchers.Main).launch {
                groupChatListeners.forEach { listener ->
                    listener.onAIReplyStatusChanged(groupId, aiMember.id, AIReplyStatus.TYPING)
                }
            }
            
            // 获取对话历史
            val conversationHistory = buildConversationHistory(groupId, aiMember.id)
            
            // 创建初始AI回复消息（空内容）
            val aiMessage = GroupChatMessage(
                id = UUID.randomUUID().toString(),
                content = "",
                senderId = aiMember.id,
                senderName = aiMember.name,
                senderType = MemberType.AI
            )
            
            // 添加空消息到群聊
            addMessageToGroup(groupId, aiMessage)
            val messageIndex = getMessageIndex(groupId, aiMessage.id)
            
            try {
                // 调用AI API（流式回复）
                callAIAPIStreaming(aiServiceType, userMessage, conversationHistory, groupChat.settings.customPrompt) { chunk, isComplete, fullResponse ->
                    if (isComplete) {
                        // 流式回复完成，更新最终内容
                        val cleanContent = removeEmojis(fullResponse)
                        val updatedMessage = aiMessage.copy(content = cleanContent)
                        updateMessageInGroup(groupId, messageIndex, updatedMessage)
                        updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.COMPLETED)
                        
                        // 保存群聊消息到持久化存储
                        saveGroupMessages(groupId)
                        
                        // 通知监听器回复完成
                        CoroutineScope(Dispatchers.Main).launch {
                            groupChatListeners.forEach { listener ->
                                listener.onAIReplyStatusChanged(groupId, aiMember.id, AIReplyStatus.COMPLETED, cleanContent)
                            }
                        }
                        
                        Log.d(TAG, "AI ${aiMember.name} 流式回复完成，内容长度: ${cleanContent.length}")
                    } else {
                        // 流式回复中，更新部分内容
                        // 获取当前消息的最新内容
                        val currentMessage = getGroupMessages(groupId).getOrNull(messageIndex)
                        val currentContent = currentMessage?.content ?: ""
                        val newContent = currentContent + removeEmojis(chunk)
                        val updatedMessage = aiMessage.copy(content = newContent)
                        updateMessageInGroup(groupId, messageIndex, updatedMessage)
                        
                        Log.d(TAG, "AI ${aiMember.name} 流式更新，当前内容长度: ${newContent.length}")
                    }
                }
                
            } catch (e: Exception) {
                val errorMessage = "API调用失败: ${e.message}"
                updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.ERROR, errorMessage)
                
                // 通知监听器错误状态
                CoroutineScope(Dispatchers.Main).launch {
                    groupChatListeners.forEach { listener ->
                        listener.onAIReplyStatusChanged(groupId, aiMember.id, AIReplyStatus.ERROR, errorMessage)
                    }
                }
                
                Log.e(TAG, "AI ${aiMember.name} 回复失败", e)
            }
            
        } catch (e: Exception) {
            val errorMessage = e.message ?: "未知错误"
            updateAIReplyStatus(groupId, aiMember.id, AIReplyStatus.ERROR, errorMessage)
            
            // 通知监听器错误状态
            CoroutineScope(Dispatchers.Main).launch {
                groupChatListeners.forEach { listener ->
                    listener.onAIReplyStatusChanged(groupId, aiMember.id, AIReplyStatus.ERROR, errorMessage)
                }
            }
            
            Log.e(TAG, "AI ${aiMember.name} 回复异常", e)
        }
    }
    
    /**
     * 调用AI API（流式回复）
     */
    private suspend fun callAIAPIStreaming(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>>,
        customPrompt: String?,
        onUpdate: (chunk: String, isComplete: Boolean, fullResponse: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始调用AI API: ${serviceType.name}")
        Log.d(TAG, "消息内容: $message")
        Log.d(TAG, "对话历史长度: ${conversationHistory.size}")
        Log.d(TAG, "自定义提示词: $customPrompt")
        
        return@withContext suspendCancellableCoroutine<Unit> { continuation ->
            try {
                // 构建完整的消息内容
                val fullMessage = if (customPrompt != null) {
                    "$customPrompt\n\n$message"
                } else {
                    message
                }
                
                Log.d(TAG, "完整消息内容: $fullMessage")
                
                var fullResponse = ""
                var isCompleted = false
                
                val callback = object : AIApiManager.StreamingCallback {
                    override fun onChunkReceived(chunk: String) {
                        Log.d(TAG, "收到AI响应块: '$chunk'")
                        if (!isCompleted) {
                            fullResponse += chunk
                            // 在主线程更新UI
                            CoroutineScope(Dispatchers.Main).launch {
                                onUpdate(chunk, false, fullResponse)
                            }
                        }
                    }
                    
                    override fun onComplete(response: String) {
                        Log.d(TAG, "AI响应完成，总长度: ${response.length}")
                        if (!isCompleted) {
                            isCompleted = true
                            fullResponse = response
                            // 在主线程更新UI
                            CoroutineScope(Dispatchers.Main).launch {
                                onUpdate("", true, fullResponse)
                            }
                            continuation.resume(Unit)
                        }
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "AI API调用失败: $error")
                        if (!isCompleted) {
                            isCompleted = true
                            continuation.resumeWithException(Exception(error))
                        }
                    }
                }
                
                // 调用AI API
                Log.d(TAG, "发送消息到AI API管理器")
                aiApiManager.sendMessage(serviceType, fullMessage, conversationHistory, callback)
                
                // 设置超时
                continuation.invokeOnCancellation {
                    Log.w(TAG, "AI API调用被取消")
                }
                
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    /**
     * 调用AI API（非流式，保持兼容性）
     */
    private suspend fun callAIAPI(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>>,
        customPrompt: String?
    ): String = withContext(Dispatchers.IO) {
        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                // 构建完整的消息内容
                val fullMessage = if (customPrompt != null) {
                    "$customPrompt\n\n$message"
                } else {
                    message
                }
                
                // 使用统一的sendMessage方法
                var response: String? = null
                var isCompleted = false
                
                val callback = object : AIApiManager.StreamingCallback {
                    override fun onChunkReceived(chunk: String) {
                        // 累积响应内容
                        if (response == null) response = ""
                        response += chunk
                    }
                    
                    override fun onComplete(fullResponse: String) {
                        if (!isCompleted) {
                            isCompleted = true
                            response = fullResponse
                            continuation.resume(fullResponse)
                        }
                    }
                    
                    override fun onError(error: String) {
                        if (!isCompleted) {
                            isCompleted = true
                            Log.e(TAG, "AI API调用失败: $error")
                            continuation.resumeWithException(Exception(error))
                        }
                    }
                }
                
                // 调用AI API
                aiApiManager.sendMessage(serviceType, fullMessage, conversationHistory, callback)
                
                // 设置超时
                continuation.invokeOnCancellation {
                    Log.w(TAG, "AI API调用被取消")
                }
                
            } catch (e: Exception) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(e)
                }
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
     * 移除文本中的表情包
     */
    private fun removeEmojis(text: String): String {
        // 移除Unicode表情符号
        val emojiPattern = "[\uD83C-\uDBFF\uDC00-\uDFFF]+".toRegex()
        return text.replace(emojiPattern, "")
            .replace(":([a-zA-Z0-9_]+):".toRegex(), "") // 移除:emoji_name:格式
            .replace("\\s+".toRegex(), " ") // 合并多个空格
            .trim()
    }
    
    /**
     * 获取消息在列表中的索引
     */
    private fun getMessageIndex(groupId: String, messageId: String): Int {
        val messages = groupMessages[groupId] ?: return -1
        return messages.indexOfFirst { it.id == messageId }
    }
    
    /**
     * 更新群聊中的消息
     */
    private fun updateMessageInGroup(groupId: String, messageIndex: Int, updatedMessage: GroupChatMessage) {
        val messages = groupMessages[groupId] ?: return
        if (messageIndex >= 0 && messageIndex < messages.size) {
            messages[messageIndex] = updatedMessage
            
            // 保存消息到持久化存储
            saveGroupMessages(groupId)
            Log.d(TAG, "消息更新并保存: 群聊=$groupId, 索引=$messageIndex, 内容长度=${updatedMessage.content.length}")
            
            // 如果是最后一条消息，更新群聊的最后消息信息
            if (messageIndex == messages.size - 1) {
                groupChats[groupId]?.let { groupChat ->
                    val updatedGroupChat = groupChat.copy(
                        lastMessage = updatedMessage.content,
                        lastMessageTime = updatedMessage.timestamp
                    )
                    groupChats[groupId] = updatedGroupChat
                    saveGroupChats()
                    
                    // 通知监听器
                    CoroutineScope(Dispatchers.Main).launch {
                        groupChatListeners.forEach { listener ->
                            listener.onMessageUpdated(groupId, messageIndex, updatedMessage)
                            listener.onGroupChatUpdated(updatedGroupChat)
                        }
                    }
                }
            } else {
                // 通知监听器消息更新
                CoroutineScope(Dispatchers.Main).launch {
                    groupChatListeners.forEach { listener ->
                        listener.onMessageUpdated(groupId, messageIndex, updatedMessage)
                    }
                }
            }
        }
    }
    
    /**
     * 添加消息到群聊（公开方法）
     */
    fun addMessageToGroup(groupId: String, message: GroupChatMessage) {
        val messages = groupMessages.getOrPut(groupId) { mutableListOf() }
        messages.add(message)
        
        // 更新群聊的最后消息信息
        groupChats[groupId]?.let { groupChat ->
            val updatedGroupChat = groupChat.copy(
                lastMessage = message.content,
                lastMessageTime = message.timestamp
            )
            groupChats[groupId] = updatedGroupChat
            
            // 通知监听器
            CoroutineScope(Dispatchers.Main).launch {
                groupChatListeners.forEach { listener ->
                    listener.onMessageAdded(groupId, message)
                    listener.onGroupChatUpdated(updatedGroupChat)
                }
            }
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
            
            // 同步删除到UnifiedGroupChatManager
            syncDeleteToUnifiedManager(groupId)
            
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
        
        // 同步更新到UnifiedGroupChatManager
        syncToUnifiedManager(updatedGroupChat)
        
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
        
        // 同步更新到UnifiedGroupChatManager
        syncToUnifiedManager(updatedGroupChat)
        
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
        
        // 同步更新到UnifiedGroupChatManager
        syncToUnifiedManager(updatedGroupChat)
        
        // 添加系统消息
        addSystemMessage(groupId, "${memberToRemove.name} 离开了群聊")
        
        return true
    }
    
    /**
     * 获取AI显示名称（使用统一工具类）
     */
    private fun getAIDisplayName(aiServiceType: AIServiceType): String {
        return com.example.aifloatingball.utils.AIServiceTypeUtils.getAIDisplayName(aiServiceType)
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
            val json = prefs.getString(KEY_GROUP_CHATS, null)
            Log.d(TAG, "尝试加载群聊数据，JSON内容: ${json?.take(200)}...")
            
            if (json == null) {
                Log.w(TAG, "没有找到群聊数据")
                return
            }
            
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
            val messages = groupMessages[groupId]
            if (messages == null) {
                Log.w(TAG, "群聊 $groupId 没有消息数据需要保存")
                return
            }
            
            val json = gson.toJson(messages)
            Log.d(TAG, "保存群聊消息 $groupId，共 ${messages.size} 条消息，JSON长度: ${json.length}")
            
            prefs.edit().putString(KEY_GROUP_MESSAGES + groupId, json).apply()
            Log.d(TAG, "群聊消息 $groupId 保存成功")
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
            Log.d(TAG, "尝试加载群聊消息 $groupId，JSON内容: ${json?.take(200)}...")
            
            if (json == null) {
                Log.w(TAG, "群聊 $groupId 没有找到消息数据")
                groupMessages[groupId] = mutableListOf()
                return
            }
            
            val type = object : TypeToken<List<GroupChatMessage>>() {}.type
            val loadedMessages: List<GroupChatMessage> = gson.fromJson(json, type)
            
            groupMessages[groupId] = loadedMessages.toMutableList()
            Log.d(TAG, "群聊 $groupId 加载消息成功，共 ${loadedMessages.size} 条消息")
            
            // 添加详细的消息内容调试
            loadedMessages.forEachIndexed { index, message ->
                Log.d(TAG, "消息[$index]: ID=${message.id}, 发送者=${message.senderName}, 类型=${message.senderType}, 内容长度=${message.content.length}")
                if (message.senderType == MemberType.AI) {
                    Log.d(TAG, "AI消息内容预览: ${message.content.take(100)}...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载群聊消息失败: $groupId", e)
            groupMessages[groupId] = mutableListOf()
        }
    }
    
    /**
     * 重新生成指定AI的回复
     */
    suspend fun regenerateAIReply(groupId: String, messageId: String, aiName: String) {
        val messages = groupMessages[groupId] ?: return
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return
        
        val message = messages[messageIndex]
        if (message.senderType != MemberType.USER) return
        
        val groupChat = groupChats[groupId] ?: return
        val aiMember = groupChat.members.find { 
            it.type == MemberType.AI && getAIDisplayName(it.aiServiceType!!) == aiName 
        } ?: return
        
        val aiServiceType = aiMember.aiServiceType!!
        val aiId = aiMember.id
        
        // 更新AI回复状态为正在回复
        updateAIReplyStatus(groupId, aiId, AIReplyStatus.TYPING, "正在重新生成回复...")
        
        try {
            // 构建对话历史
            val conversationHistory = buildConversationHistory(groupId, aiId)
            
            // 调用AI API生成新回复
            callAIAPIStreaming(
                serviceType = aiServiceType,
                message = message.content,
                conversationHistory = conversationHistory,
                customPrompt = groupChat.settings.customPrompt,
                onUpdate = { chunk: String, isComplete: Boolean, fullResponse: String ->
                    // 更新流式回复内容
                    val currentReply = message.content
                    val updatedContent = if (isComplete) fullResponse else currentReply + chunk
                    
                    val updatedMessage = message.copy(content = updatedContent)
                    messages[messageIndex] = updatedMessage
                    
                    // 通知监听器
                    CoroutineScope(Dispatchers.Main).launch {
                        groupChatListeners.forEach { listener ->
                            listener.onMessageUpdated(groupId, messageIndex, updatedMessage)
                        }
                    }
                }
            )
            
            // 更新AI回复状态为完成
            updateAIReplyStatus(groupId, aiId, AIReplyStatus.COMPLETED)
            
            // 保存消息
            saveGroupMessages(groupId)
            
            // AI回复完成后的处理在onChunkReceived中已经完成
            
        } catch (e: Exception) {
            Log.e(TAG, "重新生成AI回复失败: ${e.message}", e)
            updateAIReplyStatus(groupId, aiId, AIReplyStatus.ERROR, "重新生成失败: ${e.message}")
        }
    }
    
    /**
     * 更新群聊消息
     */
    fun updateMessage(groupId: String, messageId: String, updatedMessage: GroupChatMessage) {
        val messages = groupMessages[groupId] ?: return
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1) return
        
        messages[messageIndex] = updatedMessage
        saveGroupMessages(groupId)
        
        // 通知监听器
        CoroutineScope(Dispatchers.Main).launch {
            groupChatListeners.forEach { listener ->
                listener.onMessageUpdated(groupId, messageIndex, updatedMessage)
            }
        }
    }
    
    /**
     * 调试方法：检查SharedPreferences中的数据
     */
    fun debugCheckStoredData() {
        Log.d(TAG, "=== 调试：检查SharedPreferences中的数据 ===")
        
        // 检查群聊数据
        val groupChatsJson = prefs.getString(KEY_GROUP_CHATS, null)
        Log.d(TAG, "群聊数据存在: ${groupChatsJson != null}")
        if (groupChatsJson != null) {
            Log.d(TAG, "群聊数据长度: ${groupChatsJson.length}")
            Log.d(TAG, "群聊数据前200字符: ${groupChatsJson.take(200)}")
        }
        
        // 检查所有群聊消息数据
        val allKeys = prefs.all.keys.filter { it.startsWith(KEY_GROUP_MESSAGES) }
        Log.d(TAG, "找到 ${allKeys.size} 个群聊消息键")
        allKeys.forEach { key ->
            val messageJson = prefs.getString(key, null)
            Log.d(TAG, "消息键: $key, 数据存在: ${messageJson != null}, 长度: ${messageJson?.length ?: 0}")
        }
        
        // 检查内存中的数据
        Log.d(TAG, "内存中群聊数量: ${groupChats.size}")
        Log.d(TAG, "内存中消息群聊数量: ${groupMessages.size}")
        groupMessages.forEach { (groupId, messages) ->
            Log.d(TAG, "群聊 $groupId 内存中消息数量: ${messages.size}")
        }
        
        Log.d(TAG, "=== 调试检查完成 ===")
    }
    
    /**
     * 同步群聊数据到UnifiedGroupChatManager
     */
    private fun syncToUnifiedManager(groupChat: GroupChat) {
        try {
            val unifiedManager = UnifiedGroupChatManager.getInstance(context)
            
            // 检查群聊是否已存在
            val existingGroupChat = unifiedManager.getGroupChat(groupChat.id)
            
            if (existingGroupChat != null) {
                // 更新现有群聊
                unifiedManager.updateGroupChat(groupChat)
                Log.d(TAG, "同步更新群聊到UnifiedManager: ${groupChat.name}")
            } else {
                // 创建新群聊 - 需要导入到UnifiedManager
                managerScope.launch {
                    unifiedManager.importGroupChat(groupChat)
                    Log.d(TAG, "同步创建群聊到UnifiedManager: ${groupChat.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步群聊到UnifiedManager失败", e)
        }
    }
    
    /**
     * 同步删除群聊到UnifiedGroupChatManager
     */
    private fun syncDeleteToUnifiedManager(groupId: String) {
        try {
            val unifiedManager = UnifiedGroupChatManager.getInstance(context)
            val deleted = unifiedManager.deleteGroupChat(groupId)
            Log.d(TAG, "同步删除群聊到UnifiedManager: $groupId, 结果: $deleted")
        } catch (e: Exception) {
            Log.e(TAG, "同步删除群聊到UnifiedManager失败", e)
        }
    }
    
    /**
     * 修复群聊中缺少aiServiceType的AI成员
     */
    fun fixMissingAIServiceTypes(): Int {
        var fixedCount = 0
        
        groupChats.values.forEach { groupChat ->
            val updatedMembers = groupChat.members.map { member ->
                if (member.type == MemberType.AI && member.aiServiceType == null) {
                    // 尝试从成员名称或ID推断AI服务类型
                    val inferredType = inferAIServiceType(member.name, member.id)
                    if (inferredType != null) {
                        Log.d(TAG, "修复群聊 ${groupChat.name} 中成员 ${member.name} 的aiServiceType: $inferredType")
                        fixedCount++
                        
                        // 特别处理智谱AI，标准化其ID和名称
                        if (inferredType == AIServiceType.ZHIPU_AI) {
                            val standardId = com.example.aifloatingball.utils.AIServiceTypeUtils.generateZhipuAIId()
                            val standardName = com.example.aifloatingball.utils.AIServiceTypeUtils.getZhipuAIStandardName()
                            member.copy(
                                id = standardId,
                                name = standardName,
                                aiServiceType = inferredType
                            )
                        } else {
                            member.copy(aiServiceType = inferredType)
                        }
                    } else {
                        Log.w(TAG, "无法推断群聊 ${groupChat.name} 中成员 ${member.name} 的AI服务类型")
                        member
                    }
                } else {
                    member
                }
            }
            
            // 如果有成员被修复，更新群聊
            if (updatedMembers != groupChat.members) {
                val updatedGroupChat = groupChat.copy(members = updatedMembers)
                groupChats[groupChat.id] = updatedGroupChat
                saveGroupChats()
                
                // 同步到UnifiedGroupChatManager
                syncToUnifiedManager(updatedGroupChat)
            }
        }
        
        Log.d(TAG, "修复了 $fixedCount 个缺少aiServiceType的AI成员")
        return fixedCount
    }
    
    /**
     * 专门检查和修复智谱AI成员的问题
     */
    fun fixZhipuAIMembers(): Int {
        var fixedCount = 0
        
        groupChats.values.forEach { groupChat ->
            var hasChanges = false
            val updatedMembers = groupChat.members.map { member ->
                if (member.type == MemberType.AI) {
                    // 检查是否为智谱AI成员但aiServiceType缺失或错误
                    val isZhipuByName = com.example.aifloatingball.utils.AIServiceTypeUtils.isZhipuAIContact(
                        ChatContact(id = member.id, name = member.name, type = ContactType.AI)
                    )
                    
                    if (isZhipuByName && member.aiServiceType != AIServiceType.ZHIPU_AI) {
                        Log.d(TAG, "发现智谱AI成员需要修复: ${member.name} (ID: ${member.id})")
                        fixedCount++
                        hasChanges = true
                        
                        // 标准化智谱AI成员
                        member.copy(
                            id = com.example.aifloatingball.utils.AIServiceTypeUtils.generateZhipuAIId(),
                            name = com.example.aifloatingball.utils.AIServiceTypeUtils.getZhipuAIStandardName(),
                            aiServiceType = AIServiceType.ZHIPU_AI
                        )
                    } else {
                        member
                    }
                } else {
                    member
                }
            }
            
            // 如果有成员被修复，更新群聊
            if (hasChanges) {
                val updatedGroupChat = groupChat.copy(members = updatedMembers)
                groupChats[groupChat.id] = updatedGroupChat
                saveGroupChats()
                
                // 同步到UnifiedGroupChatManager
                syncToUnifiedManager(updatedGroupChat)
                
                Log.d(TAG, "更新群聊 ${groupChat.name} 的智谱AI成员配置")
            }
        }
        
        Log.d(TAG, "修复了 $fixedCount 个智谱AI成员配置问题")
        return fixedCount
    }
    
    /**
     * 从名称和ID推断AI服务类型（使用统一工具类）
     */
    private fun inferAIServiceType(name: String, id: String): AIServiceType? {
        return com.example.aifloatingball.utils.AIServiceTypeUtils.inferAIServiceType(name, id)
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        managerScope.cancel()
    }
}