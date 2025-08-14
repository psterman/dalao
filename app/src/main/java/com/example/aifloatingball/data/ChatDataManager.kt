package com.example.aifloatingball.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.manager.AIServiceType
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * 统一的聊天数据管理器
 * 用于简易模式和悬浮球模式的多AI引擎对话数据共享
 * 支持按AI引擎类型分别保存数据
 */
class ChatDataManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ChatDataManager"
        private const val PREFS_NAME = "chat_data"
        private const val KEY_SESSIONS = "chat_sessions"
        private const val KEY_FAVORITES = "favorite_messages"
        private const val KEY_CURRENT_SESSION = "current_session_id"

        // 为不同AI引擎添加前缀
        private fun getSessionsKey(aiServiceType: AIServiceType): String {
            return "${KEY_SESSIONS}_${aiServiceType.name.lowercase()}"
        }

        private fun getFavoritesKey(aiServiceType: AIServiceType): String {
            return "${KEY_FAVORITES}_${aiServiceType.name.lowercase()}"
        }

        private fun getCurrentSessionKey(aiServiceType: AIServiceType): String {
            return "${KEY_CURRENT_SESSION}_${aiServiceType.name.lowercase()}"
        }
        
        @Volatile
        private var INSTANCE: ChatDataManager? = null
        
        fun getInstance(context: Context): ChatDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatDataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 内存缓存 - 按AI引擎类型分别存储
    private val chatSessions = mutableMapOf<AIServiceType, MutableMap<String, MutableList<ChatMessage>>>()
    private val favoriteMessages = mutableMapOf<AIServiceType, MutableList<FavoriteMessage>>()
    private val currentSessionIds = mutableMapOf<AIServiceType, String?>()
    
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val messageId: String = UUID.randomUUID().toString()
    )
    
    data class ChatSession(
        val id: String,
        val title: String,
        val messages: List<ChatMessage>,
        val createdAt: Long,
        val updatedAt: Long
    )
    
    data class FavoriteMessage(
        val sessionId: String,
        val messageId: String,
        val message: ChatMessage,
        val favoriteAt: Long = System.currentTimeMillis()
    )
    
    init {
        loadDataFromPrefs()
    }
    
    /**
     * 从SharedPreferences加载数据
     */
    private fun loadDataFromPrefs() {
        try {
            // 为每个AI引擎类型加载数据
            AIServiceType.values().forEach { aiServiceType ->
                loadDataForAIService(aiServiceType)
            }

            Log.d(TAG, "加载了所有AI引擎的聊天数据")

        } catch (e: Exception) {
            Log.e(TAG, "加载聊天数据失败", e)
        }
    }

    /**
     * 为特定AI引擎加载数据
     */
    private fun loadDataForAIService(aiServiceType: AIServiceType) {
        try {
            // 加载会话数据
            val sessionsJson = prefs.getString(getSessionsKey(aiServiceType), "{}") ?: "{}"
            val sessionsObject = JSONObject(sessionsJson)

            val aiSessions = mutableMapOf<String, MutableList<ChatMessage>>()
            sessionsObject.keys().forEach { sessionId ->
                val messagesArray = sessionsObject.getJSONArray(sessionId)
                val messages = mutableListOf<ChatMessage>()

                for (i in 0 until messagesArray.length()) {
                    val messageObj = messagesArray.getJSONObject(i)
                    val message = ChatMessage(
                        role = messageObj.getString("role"),
                        content = messageObj.getString("content"),
                        timestamp = messageObj.optLong("timestamp", System.currentTimeMillis()),
                        messageId = messageObj.optString("messageId", UUID.randomUUID().toString())
                    )
                    messages.add(message)
                }

                aiSessions[sessionId] = messages
            }
            chatSessions[aiServiceType] = aiSessions
            
            // 加载收藏数据
            val favoritesJson = prefs.getString(getFavoritesKey(aiServiceType), "[]") ?: "[]"
            val favoritesArray = JSONArray(favoritesJson)

            val aiFavorites = mutableListOf<FavoriteMessage>()
            for (i in 0 until favoritesArray.length()) {
                val favoriteObj = favoritesArray.getJSONObject(i)
                val messageObj = favoriteObj.getJSONObject("message")

                val favorite = FavoriteMessage(
                    sessionId = favoriteObj.getString("sessionId"),
                    messageId = favoriteObj.getString("messageId"),
                    message = ChatMessage(
                        role = messageObj.getString("role"),
                        content = messageObj.getString("content"),
                        timestamp = messageObj.optLong("timestamp", System.currentTimeMillis()),
                        messageId = messageObj.optString("messageId", UUID.randomUUID().toString())
                    ),
                    favoriteAt = favoriteObj.optLong("favoriteAt", System.currentTimeMillis())
                )
                aiFavorites.add(favorite)
            }
            favoriteMessages[aiServiceType] = aiFavorites

            // 加载当前会话ID
            currentSessionIds[aiServiceType] = prefs.getString(getCurrentSessionKey(aiServiceType), null)

            Log.d(TAG, "加载了 ${aiServiceType.name}: ${aiSessions.size} 个会话和 ${aiFavorites.size} 个收藏")
            
        } catch (e: Exception) {
            Log.e(TAG, "加载聊天数据失败", e)
        }
    }
    
    /**
     * 保存数据到SharedPreferences
     */
    private fun saveDataToPrefs() {
        try {
            // 为每个AI引擎类型保存数据
            AIServiceType.values().forEach { aiServiceType ->
                saveDataForAIService(aiServiceType)
            }

        } catch (e: Exception) {
            Log.e(TAG, "保存聊天数据失败", e)
        }
    }

    /**
     * 为特定AI引擎保存数据
     */
    private fun saveDataForAIService(aiServiceType: AIServiceType) {
        try {
            val editor = prefs.edit()

            // 保存会话数据
            val aiSessions = chatSessions[aiServiceType] ?: mutableMapOf()
            val sessionsObject = JSONObject()
            aiSessions.forEach { (sessionId, messages) ->
                val messagesArray = JSONArray()
                messages.forEach { message ->
                    val messageObj = JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                        put("timestamp", message.timestamp)
                        put("messageId", message.messageId)
                    }
                    messagesArray.put(messageObj)
                }
                sessionsObject.put(sessionId, messagesArray)
            }
            editor.putString(getSessionsKey(aiServiceType), sessionsObject.toString())

            // 保存收藏数据
            val aiFavorites = favoriteMessages[aiServiceType] ?: mutableListOf()
            val favoritesArray = JSONArray()
            aiFavorites.forEach { favorite ->
                val favoriteObj = JSONObject().apply {
                    put("sessionId", favorite.sessionId)
                    put("messageId", favorite.messageId)
                    put("favoriteAt", favorite.favoriteAt)
                    put("message", JSONObject().apply {
                        put("role", favorite.message.role)
                        put("content", favorite.message.content)
                        put("timestamp", favorite.message.timestamp)
                        put("messageId", favorite.message.messageId)
                    })
                }
                favoritesArray.put(favoriteObj)
            }
            editor.putString(getFavoritesKey(aiServiceType), favoritesArray.toString())

            // 保存当前会话ID
            currentSessionIds[aiServiceType]?.let {
                editor.putString(getCurrentSessionKey(aiServiceType), it)
            }

            editor.apply()

        } catch (e: Exception) {
            Log.e(TAG, "保存 ${aiServiceType.name} 聊天数据失败", e)
        }
    }
    
    /**
     * 开始新对话
     */
    fun startNewChat(aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): String {
        val sessionId = UUID.randomUUID().toString()

        // 确保AI引擎的会话映射存在
        if (!chatSessions.containsKey(aiServiceType)) {
            chatSessions[aiServiceType] = mutableMapOf()
        }

        chatSessions[aiServiceType]!![sessionId] = mutableListOf()
        currentSessionIds[aiServiceType] = sessionId
        saveDataForAIService(aiServiceType)
        Log.d(TAG, "开始新对话 (${aiServiceType.name}): $sessionId")
        return sessionId
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): String? =
        currentSessionIds[aiServiceType]

    /**
     * 设置当前会话ID
     */
    fun setCurrentSessionId(sessionId: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK) {
        val aiSessions = chatSessions[aiServiceType]
        if (aiSessions?.containsKey(sessionId) == true) {
            currentSessionIds[aiServiceType] = sessionId
            saveDataForAIService(aiServiceType)
        }
    }
    
    /**
     * 添加消息到会话
     */
    fun addMessage(sessionId: String, role: String, content: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): ChatMessage {
        val message = ChatMessage(role, content)

        // 确保AI引擎的会话映射存在
        if (!chatSessions.containsKey(aiServiceType)) {
            chatSessions[aiServiceType] = mutableMapOf()
        }

        val aiSessions = chatSessions[aiServiceType]!!
        if (!aiSessions.containsKey(sessionId)) {
            aiSessions[sessionId] = mutableListOf()
        }

        aiSessions[sessionId]?.add(message)
        saveDataForAIService(aiServiceType)

        Log.d(TAG, "添加消息到会话 (${aiServiceType.name}) $sessionId: ${role} - ${content.take(50)}")
        return message
    }
    
    /**
     * 获取会话消息
     */
    fun getMessages(sessionId: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): List<ChatMessage> {
        return chatSessions[aiServiceType]?.get(sessionId)?.toList() ?: emptyList()
    }
    
    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<ChatSession> {
        val allSessions = mutableListOf<ChatSession>()

        chatSessions.forEach { (aiServiceType, sessions) ->
            sessions.forEach { (sessionId, messages) ->
                val session = ChatSession(
                    id = sessionId,
                    title = getSessionTitle(messages),
                    messages = messages.toList(),
                    createdAt = messages.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                    updatedAt = messages.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                )
                allSessions.add(session)
            }
        }

        return allSessions.sortedByDescending { it.updatedAt }
    }
    
    /**
     * 删除会话
     */
    fun deleteSession(sessionId: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK) {
        val aiSessions = chatSessions[aiServiceType]
        aiSessions?.remove(sessionId)

        if (currentSessionIds[aiServiceType] == sessionId) {
            currentSessionIds[aiServiceType] = null
        }

        // 删除相关收藏
        val aiFavorites = favoriteMessages[aiServiceType]
        aiFavorites?.removeAll { it.sessionId == sessionId }

        saveDataForAIService(aiServiceType)
        Log.d(TAG, "删除会话 (${aiServiceType.name}): $sessionId")
    }
    
    /**
     * 收藏消息
     */
    fun favoriteMessage(sessionId: String, messageId: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK) {
        val aiSessions = chatSessions[aiServiceType] ?: return
        val messages = aiSessions[sessionId] ?: return
        val message = messages.find { it.messageId == messageId } ?: return

        // 确保收藏列表存在
        if (!favoriteMessages.containsKey(aiServiceType)) {
            favoriteMessages[aiServiceType] = mutableListOf()
        }

        val aiFavorites = favoriteMessages[aiServiceType]!!

        // 检查是否已收藏
        if (aiFavorites.any { it.sessionId == sessionId && it.messageId == messageId }) {
            return
        }

        val favorite = FavoriteMessage(sessionId, messageId, message)
        aiFavorites.add(favorite)
        saveDataForAIService(aiServiceType)
        Log.d(TAG, "收藏消息 (${aiServiceType.name}): $sessionId - $messageId")
    }
    
    /**
     * 取消收藏消息
     */
    fun unfavoriteMessage(sessionId: String, messageId: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK) {
        val aiFavorites = favoriteMessages[aiServiceType] ?: return
        aiFavorites.removeAll { it.sessionId == sessionId && it.messageId == messageId }
        saveDataForAIService(aiServiceType)
        Log.d(TAG, "取消收藏消息 (${aiServiceType.name}): $sessionId - $messageId")
    }
    
    /**
     * 获取收藏消息
     */
    fun getFavoriteMessages(aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): List<FavoriteMessage> {
        val aiFavorites = favoriteMessages[aiServiceType] ?: return emptyList()
        return aiFavorites.sortedByDescending { it.favoriteAt }
    }
    
    /**
     * 搜索会话
     */
    fun searchSessions(query: String, aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): List<ChatSession> {
        if (query.isBlank()) return getAllSessions(aiServiceType)

        return getAllSessions(aiServiceType).filter { session ->
            session.title.contains(query, ignoreCase = true) ||
            session.messages.any { it.content.contains(query, ignoreCase = true) }
        }
    }
    
    /**
     * 获取特定AI引擎的所有会话
     */
    fun getAllSessions(aiServiceType: AIServiceType = AIServiceType.DEEPSEEK): List<ChatSession> {
        val aiSessions = chatSessions[aiServiceType] ?: return emptyList()

        return aiSessions.map { (sessionId, messages) ->
            ChatSession(
                id = sessionId,
                title = getSessionTitle(messages),
                messages = messages.toList(),
                createdAt = messages.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                updatedAt = messages.lastOrNull()?.timestamp ?: System.currentTimeMillis()
            )
        }.sortedByDescending { it.updatedAt }
    }

    /**
     * 获取会话标题
     */
    private fun getSessionTitle(messages: List<ChatMessage>): String {
        val firstUserMessage = messages.find { it.role == "user" }
        return firstUserMessage?.content?.take(30) ?: "新对话"
    }
    
    /**
     * 清空所有数据
     */
    fun clearAllData() {
        chatSessions.clear()
        favoriteMessages.clear()
        currentSessionIds.clear()
        prefs.edit().clear().apply()
        Log.d(TAG, "清空所有聊天数据")
    }

    /**
     * 清空特定AI引擎的数据
     */
    fun clearDataForAIService(aiServiceType: AIServiceType) {
        chatSessions.remove(aiServiceType)
        favoriteMessages.remove(aiServiceType)
        currentSessionIds.remove(aiServiceType)
        saveDataForAIService(aiServiceType)
        Log.d(TAG, "清空 ${aiServiceType.name} 的聊天数据")
    }
}
