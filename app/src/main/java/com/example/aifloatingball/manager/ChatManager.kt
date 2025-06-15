package com.example.aifloatingball.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import com.example.aifloatingball.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ChatMessage(val role: String, val content: String, val isLoading: Boolean = false)
data class ChatSession(
    val id: String,
    var title: String,
    val timestamp: Long,
    var messages: MutableList<ChatMessage>
)

class ChatManager(private val context: Context) {
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webViewRef: WebView? = null
    private var isDeepSeekEngine: Boolean = false
    private val gson = Gson()

    private val sessions = mutableListOf<ChatSession>()
    private var currentSessionId: String? = null

    companion object {
        private const val MAX_HISTORY_SIZE = 50 // Per session
        private const val PREFS_NAME = "ChatManagerPrefs"
        private const val KEY_SESSIONS = "chat_sessions_v2"
        private const val KEY_CURRENT_SESSION_ID = "current_session_id_v2"
    }

    init {
        loadSessions()
    }

    fun initWebView(webView: WebView, engineKey: String, initialMessage: String? = null) {
        this.webViewRef = webView
        this.isDeepSeekEngine = engineKey.startsWith("deepseek")
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }
        
        try { webView.removeJavascriptInterface("AndroidChatInterface") } catch (e: Exception) {}
        webView.addJavascriptInterface(AndroidChatInterface(), "AndroidChatInterface")
        
        val chatHtml = when (engineKey) {
            "deepseek", "deepseek_chat" -> "file:///android_asset/deepseek_chat.html"
            else -> "file:///android_asset/chat_ui.html"
        }
        webView.loadUrl(chatHtml)
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("loadChatHistoryFromAndroid('${chatHistoryToJson()}');", null)
                if (view != null && !initialMessage.isNullOrBlank()) {
                    sendMessageToWebView(initialMessage, view, isDeepSeekEngine)
                }
            }
        }
    }

    fun startChat(webView: WebView, engineKey: String, query: String) {
        // 首先，使用查询初始化或重新初始化WebView
        initWebView(webView, engineKey, query)

        // initWebView中的onPageFinished会在页面加载后发送初始消息
        // 所以这里不需要再调用sendMessageToWebView
    }

    private inner class AndroidChatInterface {
        @android.webkit.JavascriptInterface
        fun getSessions(): String {
            val sortedSessions = sessions.sortedByDescending { it.timestamp }
            val metadata = sortedSessions.map { mapOf("id" to it.id, "title" to it.title, "timestamp" to it.timestamp) }
            return gson.toJson(metadata)
        }

        @android.webkit.JavascriptInterface
        fun getInitialSession(): String {
            val session = if (currentSessionId != null) sessions.find { it.id == currentSessionId } else sessions.firstOrNull()
            return if (session != null) {
                currentSessionId = session.id
                gson.toJson(session)
            } else {
                startNewChat() // If no session exists, create one
            }
        }

        @android.webkit.JavascriptInterface
        fun getMessages(sessionId: String): String {
            val session = sessions.find { it.id == sessionId }
            return if (session != null) {
                currentSessionId = sessionId
                saveCurrentSessionId()
                gson.toJson(session.messages)
            } else {
                "[]"
            }
        }

        @android.webkit.JavascriptInterface
        fun startNewChat(): String {
            val newSession = ChatSession(
                id = System.currentTimeMillis().toString(),
                title = "新对话",
                timestamp = System.currentTimeMillis(),
                messages = mutableListOf()
            )
            sessions.add(0, newSession)
            currentSessionId = newSession.id
            saveSessions()
            saveCurrentSessionId()
            return gson.toJson(newSession)
        }

        @android.webkit.JavascriptInterface
        fun sendMessage(message: String) {
            if (currentSessionId == null) return
            val session = sessions.find { it.id == currentSessionId } ?: return
            val webView = webViewRef ?: return

            scope.launch {
                handleMessageSend(webView, message, isDeepSeekEngine, session)
            }
        }
        
        @android.webkit.JavascriptInterface
        fun deleteSession(sessionId: String) {
            sessions.removeAll { it.id == sessionId }
            if (currentSessionId == sessionId) {
                currentSessionId = sessions.firstOrNull()?.id
                saveCurrentSessionId()
            }
            saveSessions()
        }
    }

    fun sendMessageToWebView(message: String, webView: WebView, isDeepSeek: Boolean) {
        if (currentSessionId == null) {
            Log.w("ChatManager", "Cannot send message, no current session.")
            return
        }
        val session = sessions.find { it.id == currentSessionId }
        if (session == null) {
            Log.w("ChatManager", "Cannot send message, current session not found.")
            return
        }
        
        // Emulate the JS sendMessage function to update the UI from native code
        webView.post {
            webView.evaluateJavascript("addMessageToUI('user', '${escapeJs(message)}', true);", null)
            webView.evaluateJavascript("addMessageToUI('assistant', '', false);", null)
            webView.evaluateJavascript("showTypingIndicator();", null)
        }

        // Then, perform the actual API call.
        scope.launch {
            handleMessageSend(webView, message, isDeepSeek, session)
        }
    }

    private suspend fun handleMessageSend(webView: WebView, message: String, isDeepSeek: Boolean, session: ChatSession) {
        val apiKey = if (isDeepSeek) settingsManager.getDeepSeekApiKey() else settingsManager.getChatGPTApiKey()
        if (apiKey.isBlank()) {
            val error = "API Key for ${if (isDeepSeek) "DeepSeek" else "ChatGPT"} is not set."
            Log.e("ChatManager", error)
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("showErrorMessage('${escapeJs(error)}');", null)
            }
            return
        }

        val apiUrl = if (isDeepSeek) settingsManager.getDeepSeekApiUrl() else settingsManager.getChatGPTApiUrl()
        val model = if (isDeepSeek) "deepseek-chat" else "gpt-3.5-turbo"

        session.messages.add(ChatMessage("user", message))
        if (session.title == "新对话" && session.messages.size == 1) {
            session.title = message
        }

        val contextMessages = session.messages.toMutableList()
        
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

            val requestBody = gson.toJson(mapOf("model" to model, "messages" to contextMessages, "stream" to true))
            
            connection.outputStream.use { it.write(requestBody.toByteArray(StandardCharsets.UTF_8)) }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val fullResponse = StringBuilder()
            
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data:")) {
                    val jsonData = line.substring(5).trim()
                    if (jsonData != "[DONE]") {
                        try {
                            val choice = org.json.JSONObject(jsonData).getJSONArray("choices").getJSONObject(0)
                            val delta = choice.getJSONObject("delta")
                            if (delta.has("content")) {
                                val contentChunk = delta.getString("content")
                                fullResponse.append(contentChunk)
                                            withContext(Dispatchers.Main) {
                                    webView.evaluateJavascript("appendToResponse('${escapeJs(contentChunk)}');", null)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatManager", "JSON parse error in stream: $e")
                        }
                    }
                }
            }
            reader.close()
            connection.disconnect()

            val assistantMessage = ChatMessage("assistant", fullResponse.toString())
            session.messages.add(assistantMessage)
            if (session.messages.size > MAX_HISTORY_SIZE * 2) {
                session.messages.removeAt(0)
                session.messages.removeAt(0)
            }
            saveSessions()

            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("completeResponse();", null)
                webView.evaluateJavascript("updateSessionTitle('${session.id}', '${escapeJs(session.title)}');", null)
            }
        } catch (e: Exception) {
            Log.e("ChatManager", "API call failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("showErrorMessage('Error: ${escapeJs(e.message ?: "Unknown API error")}');", null)
            }
        }
    }

    private fun chatHistoryToJson(): String {
        return gson.toJson(sessions.flatMap { it.messages })
    }

    private fun escapeJs(s: String): String {
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
    }

    private fun saveSessions() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).apply()
    }

    private fun loadSessions() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<ChatSession>>() {}.type
            try {
                val loadedSessions: MutableList<ChatSession> = gson.fromJson(json, type)
                sessions.clear()
                sessions.addAll(loadedSessions)
            } catch (e: Exception) {
                Log.e("ChatManager", "Failed to load sessions", e)
                sessions.clear()
            }
        }
        currentSessionId = prefs.getString(KEY_CURRENT_SESSION_ID, null)
        if (sessions.find { it.id == currentSessionId } == null) {
            currentSessionId = sessions.sortedByDescending { it.timestamp }.firstOrNull()?.id
        }
    }

    private fun saveCurrentSessionId() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_SESSION_ID, currentSessionId).apply()
    }
}
