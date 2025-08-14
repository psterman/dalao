package com.example.aifloatingball.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.data.ChatDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Android与DeepSeek HTML页面的JavaScript接口
 */
class AndroidChatInterface(
    private val context: Context,
    private val webViewCallback: WebViewCallback? = null
) {
    
    companion object {
        private const val TAG = "AndroidChatInterface"
    }
    
    interface WebViewCallback {
        fun onMessageReceived(message: String)
        fun onMessageCompleted(fullMessage: String)
        fun onNewChatStarted()
        fun onSessionDeleted(sessionId: String)
    }
    
    private val settingsManager = SettingsManager.getInstance(context)
    private val aiApiManager = AIApiManager(context)
    private val chatDataManager = ChatDataManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    
    @JavascriptInterface
    fun sendMessage(message: String) {
        Log.d(TAG, "收到消息: $message")

        // 检查API配置
        val apiKey = settingsManager.getDeepSeekApiKey()
        if (apiKey.isBlank()) {
            // API未配置，显示友好的配置引导
            showApiConfigurationGuide()
            return
        }

        // 确保有当前会话
        var currentSessionId = chatDataManager.getCurrentSessionId()
        if (currentSessionId == null) {
            currentSessionId = startNewChat()
        }

        // 添加用户消息到会话
        chatDataManager.addMessage(currentSessionId, "user", message)

        // 调用DeepSeek API
        scope.launch {
            try {
                val conversationHistory = chatDataManager.getMessages(currentSessionId)

                aiApiManager.sendMessage(
                    AIServiceType.DEEPSEEK,
                    message,
                    conversationHistory.map {
                        mapOf("role" to it.role, "content" to it.content)
                    },
                    object : AIApiManager.StreamingCallback {
                        override fun onChunkReceived(chunk: String) {
                            // 流式响应，实时更新HTML页面
                            webViewCallback?.onMessageReceived(chunk)
                        }

                        override fun onComplete(fullResponse: String) {
                            // 添加AI回复到会话
                            chatDataManager.addMessage(currentSessionId, "assistant", fullResponse)
                            // 通知WebView响应完成
                            webViewCallback?.onMessageCompleted(fullResponse)
                        }

                        override fun onError(error: String) {
                            Log.e(TAG, "API调用失败: $error")
                            handleApiError(error)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "发送消息失败", e)
                val errorMessage = "发送消息失败: ${e.message}"
                webViewCallback?.onMessageReceived(errorMessage)
            }
        }
    }
    
    @JavascriptInterface
    fun startNewChat(): String {
        val sessionId = chatDataManager.startNewChat()
        Log.d(TAG, "开始新对话: $sessionId")
        webViewCallback?.onNewChatStarted()
        return sessionId
    }
    
    @JavascriptInterface
    fun getMessages(sessionId: String): String {
        val messages = chatDataManager.getMessages(sessionId)
        val jsonArray = JSONArray()

        messages.forEach { message ->
            val jsonMessage = JSONObject().apply {
                put("role", message.role)
                put("content", message.content)
                put("timestamp", message.timestamp)
                put("messageId", message.messageId)
            }
            jsonArray.put(jsonMessage)
        }

        return jsonArray.toString()
    }
    
    @JavascriptInterface
    fun getSessions(): String {
        val sessions = chatDataManager.getAllSessions()
        val jsonArray = JSONArray()

        sessions.forEach { session ->
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("messageCount", session.messages.size)
                put("lastMessage", session.messages.lastOrNull()?.content ?: "")
                put("timestamp", session.updatedAt)
            }
            jsonArray.put(sessionJson)
        }

        return jsonArray.toString()
    }
    
    @JavascriptInterface
    fun deleteSession(sessionId: String) {
        chatDataManager.deleteSession(sessionId)
        webViewCallback?.onSessionDeleted(sessionId)
        Log.d(TAG, "删除会话: $sessionId")
    }
    
    @JavascriptInterface
    fun getInitialSession(): String? {
        var currentSessionId = chatDataManager.getCurrentSessionId()
        if (currentSessionId == null) {
            currentSessionId = startNewChat()
        }

        val messages = chatDataManager.getMessages(currentSessionId)
        val sessionJson = JSONObject().apply {
            put("id", currentSessionId)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                        put("timestamp", message.timestamp)
                        put("messageId", message.messageId)
                    })
                }
            })
        }

        return sessionJson.toString()
    }
    
    @JavascriptInterface
    fun getTheme(): String {
        return if (settingsManager.getThemeMode() == SettingsManager.THEME_MODE_DARK) {
            "dark"
        } else {
            "light"
        }
    }
    
    @JavascriptInterface
    fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("DeepSeek Chat", text)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
        }
    }
    
    @JavascriptInterface
    fun hasClipboardText(): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.hasPrimaryClip() && clipboard.primaryClip?.getItemAt(0)?.text != null
        } catch (e: Exception) {
            false
        }
    }
    
    @JavascriptInterface
    fun paste(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    @JavascriptInterface
    fun favoriteMessage(sessionId: String, messageIndex: Int) {
        val messages = chatDataManager.getMessages(sessionId)
        if (messageIndex < messages.size) {
            val message = messages[messageIndex]
            chatDataManager.favoriteMessage(sessionId, message.messageId)
            Log.d(TAG, "收藏消息: $sessionId - ${message.messageId}")
        }
    }
    
    @JavascriptInterface
    fun getFavoriteMessages(): String {
        val favorites = chatDataManager.getFavoriteMessages()
        val jsonArray = JSONArray()

        favorites.forEach { favorite ->
            val favoriteJson = JSONObject().apply {
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
            jsonArray.put(favoriteJson)
        }

        return jsonArray.toString()
    }
    
    @JavascriptInterface
    fun shareMessage(content: String) {
        // 这里可以实现分享功能
        Log.d(TAG, "分享消息: $content")
        Toast.makeText(context, "分享功能待实现", Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示API配置引导
     */
    private fun showApiConfigurationGuide() {
        val guideMessage = """
            <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 12px; margin: 10px 0; box-shadow: 0 4px 15px rgba(0,0,0,0.1);">
                <h3 style="margin: 0 0 15px 0; font-size: 18px;">🔧 需要配置 DeepSeek API</h3>
                <p style="margin: 0 0 15px 0; line-height: 1.6;">要使用 DeepSeek AI 对话功能，您需要先配置 API 密钥。</p>

                <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; margin: 15px 0;">
                    <h4 style="margin: 0 0 10px 0; font-size: 16px;">📋 配置步骤：</h4>
                    <ol style="margin: 0; padding-left: 20px; line-height: 1.8;">
                        <li>访问 <a href="https://platform.deepseek.com" style="color: #FFD700; text-decoration: underline;">DeepSeek 官网</a> 注册账号</li>
                        <li>在控制台中创建 API 密钥</li>
                        <li>点击下方按钮打开设置页面</li>
                        <li>在"AI 设置"中填入您的 API 密钥</li>
                    </ol>
                </div>

                <div style="text-align: center; margin-top: 20px;">
                    <button onclick="openApiSettings()" style="background: #FFD700; color: #333; border: none; padding: 12px 24px; border-radius: 6px; font-weight: bold; cursor: pointer; font-size: 14px;">
                        🔧 打开 API 设置
                    </button>
                </div>

                <p style="margin: 15px 0 0 0; font-size: 12px; opacity: 0.8;">
                    💡 提示：配置完成后，刷新页面即可开始使用 AI 对话功能
                </p>
            </div>
        """.trimIndent()

        webViewCallback?.onMessageReceived(guideMessage)
    }

    /**
     * 处理API错误
     */
    private fun handleApiError(error: String) {
        val errorMessage = when {
            error.contains("401") -> {
                """
                <div style="background: linear-gradient(135deg, #ff6b6b 0%, #ee5a24 100%); color: white; padding: 20px; border-radius: 12px; margin: 10px 0;">
                    <h3 style="margin: 0 0 15px 0;">🔑 API 认证失败</h3>
                    <p style="margin: 0 0 15px 0;">您的 DeepSeek API 密钥可能有问题。</p>
                    <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px;">
                        <h4 style="margin: 0 0 10px 0;">🔍 可能的原因：</h4>
                        <ul style="margin: 0; padding-left: 20px;">
                            <li>API 密钥格式不正确</li>
                            <li>API 密钥已过期或被禁用</li>
                            <li>账户余额不足</li>
                        </ul>
                    </div>
                    <div style="text-align: center; margin-top: 15px;">
                        <button onclick="openApiSettings()" style="background: white; color: #ee5a24; border: none; padding: 10px 20px; border-radius: 6px; font-weight: bold; cursor: pointer;">
                            检查 API 设置
                        </button>
                    </div>
                </div>
                """.trimIndent()
            }
            error.contains("429") -> {
                """
                <div style="background: linear-gradient(135deg, #ffa726 0%, #ff9800 100%); color: white; padding: 20px; border-radius: 12px; margin: 10px 0;">
                    <h3 style="margin: 0 0 15px 0;">⏱️ 请求频率过高</h3>
                    <p style="margin: 0;">请稍等片刻再试，或考虑升级您的 API 套餐。</p>
                </div>
                """.trimIndent()
            }
            error.contains("网络") || error.contains("连接") -> {
                """
                <div style="background: linear-gradient(135deg, #42a5f5 0%, #1976d2 100%); color: white; padding: 20px; border-radius: 12px; margin: 10px 0;">
                    <h3 style="margin: 0 0 15px 0;">🌐 网络连接问题</h3>
                    <p style="margin: 0;">请检查您的网络连接，然后重试。</p>
                </div>
                """.trimIndent()
            }
            else -> {
                """
                <div style="background: linear-gradient(135deg, #9e9e9e 0%, #616161 100%); color: white; padding: 20px; border-radius: 12px; margin: 10px 0;">
                    <h3 style="margin: 0 0 15px 0;">❌ 发生错误</h3>
                    <p style="margin: 0 0 15px 0;">$error</p>
                    <div style="text-align: center;">
                        <button onclick="openApiSettings()" style="background: white; color: #616161; border: none; padding: 10px 20px; border-radius: 6px; font-weight: bold; cursor: pointer;">
                            检查设置
                        </button>
                    </div>
                </div>
                """.trimIndent()
            }
        }

        webViewCallback?.onMessageReceived(errorMessage)
    }

    /**
     * 打开API设置页面
     */
    @JavascriptInterface
    fun openApiSettings() {
        try {
            val intent = Intent(context, com.example.aifloatingball.SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("highlight_section", "ai_settings")
            }
            context.startActivity(intent)
            Toast.makeText(context, "正在打开设置页面...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "打开设置页面失败", e)
            Toast.makeText(context, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }
    
    @JavascriptInterface
    fun searchHistory(query: String): String {
        val filteredSessions = chatDataManager.searchSessions(query)
        val jsonArray = JSONArray()

        filteredSessions.forEach { session ->
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("messageCount", session.messages.size)
                put("lastMessage", session.messages.lastOrNull()?.content ?: "")
                put("timestamp", session.updatedAt)
            }
            jsonArray.put(sessionJson)
        }

        return jsonArray.toString()
    }
}
