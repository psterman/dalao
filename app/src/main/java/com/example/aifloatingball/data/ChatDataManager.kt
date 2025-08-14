package com.example.aifloatingball.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * 统一的聊天数据管理器
 * 用于简易模式和悬浮球模式的DeepSeek对话数据共享
 */
class ChatDataManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ChatDataManager"
        private const val PREFS_NAME = "chat_data"
        private const val KEY_SESSIONS = "chat_sessions"
        private const val KEY_FAVORITES = "favorite_messages"
        private const val KEY_CURRENT_SESSION = "current_session_id"
        
        @Volatile
        private var INSTANCE: ChatDataManager? = null
        
        fun getInstance(context: Context): ChatDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatDataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 内存缓存
    private val chatSessions = mutableMapOf<String, MutableList<ChatMessage>>()
    private val favoriteMessages = mutableListOf<FavoriteMessage>()
    private var currentSessionId: String? = null
    
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
            // 加载会话数据
            val sessionsJson = prefs.getString(KEY_SESSIONS, "{}") ?: "{}"
            val sessionsObject = JSONObject(sessionsJson)
            
            chatSessions.clear()
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
                
                chatSessions[sessionId] = messages
            }
            
            // 加载收藏数据
            val favoritesJson = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"
            val favoritesArray = JSONArray(favoritesJson)
            
            favoriteMessages.clear()
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
                favoriteMessages.add(favorite)
            }
            
            // 加载当前会话ID
            currentSessionId = prefs.getString(KEY_CURRENT_SESSION, null)
            
            Log.d(TAG, "加载了 ${chatSessions.size} 个会话和 ${favoriteMessages.size} 个收藏")
            
        } catch (e: Exception) {
            Log.e(TAG, "加载聊天数据失败", e)
        }
    }
    
    /**
     * 保存数据到SharedPreferences
     */
    private fun saveDataToPrefs() {
        try {
            val editor = prefs.edit()
            
            // 保存会话数据
            val sessionsObject = JSONObject()
            chatSessions.forEach { (sessionId, messages) ->
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
            editor.putString(KEY_SESSIONS, sessionsObject.toString())
            
            // 保存收藏数据
            val favoritesArray = JSONArray()
            favoriteMessages.forEach { favorite ->
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
            editor.putString(KEY_FAVORITES, favoritesArray.toString())
            
            // 保存当前会话ID
            currentSessionId?.let { editor.putString(KEY_CURRENT_SESSION, it) }
            
            editor.apply()
            
        } catch (e: Exception) {
            Log.e(TAG, "保存聊天数据失败", e)
        }
    }
    
    /**
     * 开始新对话
     */
    fun startNewChat(): String {
        val sessionId = UUID.randomUUID().toString()
        chatSessions[sessionId] = mutableListOf()
        currentSessionId = sessionId
        saveDataToPrefs()
        Log.d(TAG, "开始新对话: $sessionId")
        return sessionId
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? = currentSessionId
    
    /**
     * 设置当前会话ID
     */
    fun setCurrentSessionId(sessionId: String) {
        if (chatSessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            saveDataToPrefs()
        }
    }
    
    /**
     * 添加消息到会话
     */
    fun addMessage(sessionId: String, role: String, content: String): ChatMessage {
        val message = ChatMessage(role, content)
        
        if (!chatSessions.containsKey(sessionId)) {
            chatSessions[sessionId] = mutableListOf()
        }
        
        chatSessions[sessionId]?.add(message)
        saveDataToPrefs()
        
        Log.d(TAG, "添加消息到会话 $sessionId: ${role} - ${content.take(50)}")
        return message
    }
    
    /**
     * 获取会话消息
     */
    fun getMessages(sessionId: String): List<ChatMessage> {
        return chatSessions[sessionId]?.toList() ?: emptyList()
    }
    
    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<ChatSession> {
        return chatSessions.map { (sessionId, messages) ->
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
     * 删除会话
     */
    fun deleteSession(sessionId: String) {
        chatSessions.remove(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = null
        }
        // 删除相关收藏
        favoriteMessages.removeAll { it.sessionId == sessionId }
        saveDataToPrefs()
        Log.d(TAG, "删除会话: $sessionId")
    }
    
    /**
     * 收藏消息
     */
    fun favoriteMessage(sessionId: String, messageId: String) {
        val messages = chatSessions[sessionId] ?: return
        val message = messages.find { it.messageId == messageId } ?: return
        
        // 检查是否已收藏
        if (favoriteMessages.any { it.sessionId == sessionId && it.messageId == messageId }) {
            return
        }
        
        val favorite = FavoriteMessage(sessionId, messageId, message)
        favoriteMessages.add(favorite)
        saveDataToPrefs()
        Log.d(TAG, "收藏消息: $sessionId - $messageId")
    }
    
    /**
     * 取消收藏消息
     */
    fun unfavoriteMessage(sessionId: String, messageId: String) {
        favoriteMessages.removeAll { it.sessionId == sessionId && it.messageId == messageId }
        saveDataToPrefs()
        Log.d(TAG, "取消收藏消息: $sessionId - $messageId")
    }
    
    /**
     * 获取收藏消息
     */
    fun getFavoriteMessages(): List<FavoriteMessage> {
        return favoriteMessages.sortedByDescending { it.favoriteAt }
    }
    
    /**
     * 搜索会话
     */
    fun searchSessions(query: String): List<ChatSession> {
        if (query.isBlank()) return getAllSessions()
        
        return getAllSessions().filter { session ->
            session.title.contains(query, ignoreCase = true) ||
            session.messages.any { it.content.contains(query, ignoreCase = true) }
        }
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
        currentSessionId = null
        prefs.edit().clear().apply()
        Log.d(TAG, "清空所有聊天数据")
    }
}
