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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.ui.webview.CustomWebView
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.app.AlertDialog
import android.view.WindowManager
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent

data class ChatMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false,
    var isFavorite: Boolean = false,
    val timestamp: Long = System.currentTimeMillis() // 为收藏排序提供方便
)

data class FavoriteMessageInfo(
    val chatId: String,
    val messageIndex: Int,
    val message: ChatMessage
)

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
    private val messageLock = Mutex()

    private val sessions = mutableListOf<ChatSession>()
    private val favorites = mutableListOf<FavoriteMessageInfo>()
    private var currentSessionId: String? = null

    companion object {
        private const val MAX_HISTORY_SIZE = 50 // Per session
        private const val PREFS_NAME = "ChatManagerPrefs"
        private const val KEY_SESSIONS = "chat_sessions_v2"
        private const val KEY_FAVORITES = "chat_favorites_v1"
        private const val KEY_CURRENT_SESSION_ID = "current_session_id_v2"
    }

    init {
        loadSessions()
        loadFavorites()
    }

    fun initWebView(webView: WebView, engineKey: String, initialMessage: String? = null) {
        this.webViewRef = webView
        this.isDeepSeekEngine = engineKey.contains("deepseek", ignoreCase = true)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
        }
        
        try { webView.removeJavascriptInterface("AndroidChatInterface") } catch (e: Exception) {}
        webView.addJavascriptInterface(AndroidChatInterface(), "AndroidChatInterface")
        
        val chatHtml = if (engineKey.contains("deepseek", ignoreCase = true)) {
            "file:///android_asset/deepseek_chat.html"
        } else {
            Log.w("ChatManager", "Engine '$engineKey' is not DeepSeek, but falling back to deepseek_chat.html as it's the only UI available.")
            "file:///android_asset/deepseek_chat.html"
        }
        webView.loadUrl(chatHtml)
        
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view == null) return

                val theme = settingsManager.getThemeModeForWeb()
                view.evaluateJavascript("setTheme('$theme')", null)

                view.evaluateJavascript("loadChatHistoryFromAndroid('${chatHistoryToJson()}');", null)
                if (!initialMessage.isNullOrBlank()) {
                    sendMessageToWebView(initialMessage, view, isDeepSeekEngine)
                }
            }
        }

        webView.webChromeClient = CustomWebChromeClient()
    }

    fun startChat(webView: WebView, engineKey: String, query: String) {
        // 首先，使用查询初始化或重新初始化WebView
        initWebView(webView, engineKey, query)

        // initWebView中的onPageFinished会在页面加载后发送初始消息
        // 所以这里不需要再调用sendMessageToWebView
    }

    private inner class CustomWebChromeClient : WebChromeClient() {
        override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            AlertDialog.Builder(context)
                .setTitle("确认")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result?.confirm()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    result?.cancel()
                }
                .setOnCancelListener {
                    result?.cancel()
                }
                .create()
                .apply {
                    // 对于服务中弹出的对话框，需要设置窗口类型
                    window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    show()
                }
            return true
        }
    }

    private inner class AndroidChatInterface {
        @android.webkit.JavascriptInterface
        fun getSessions(): String {
            val sortedSessions = sessions.sortedByDescending { it.timestamp }
            val metadata = sortedSessions.map { mapOf("id" to it.id, "title" to it.title, "timestamp" to it.timestamp) }
            return gson.toJson(metadata)
        }

        @android.webkit.JavascriptInterface
        fun searchHistory(query: String): String {
            if (query.isBlank()) {
                val sortedSessions = sessions.sortedByDescending { it.timestamp }
                val metadata = sortedSessions.map { mapOf("id" to it.id, "title" to it.title, "timestamp" to it.timestamp) }
                return gson.toJson(metadata)
            }

            val lowerCaseQuery = query.toLowerCase(Locale.ROOT)
            val filteredSessions = sessions.filter { session ->
                session.title.toLowerCase(Locale.ROOT).contains(lowerCaseQuery) ||
                session.messages.any { message -> message.content.toLowerCase(Locale.ROOT).contains(lowerCaseQuery) }
            }.sortedByDescending { it.timestamp }

            val metadata = filteredSessions.map { mapOf("id" to it.id, "title" to it.title, "timestamp" to it.timestamp) }
            return gson.toJson(metadata)
        }

        @android.webkit.JavascriptInterface
        fun getFavoriteMessages(): String {
            // Sort by most recent first
            favorites.sortByDescending { it.message.timestamp }
            return gson.toJson(favorites)
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
            val newSession = createNewSession()
            return gson.toJson(newSession)
        }

        @android.webkit.JavascriptInterface
        fun sendMessage(message: String) {
            if (currentSessionId == null) return
            val webView = webViewRef ?: return
            // 传递当前会话的ID，而不是会话对象本身
            val capturedSessionId = currentSessionId!!

            scope.launch {
                handleMessageSend(webView, message, isDeepSeekEngine, capturedSessionId)
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

        @android.webkit.JavascriptInterface
        fun getTheme(): String {
            return settingsManager.getThemeModeForWeb()
        }

        @android.webkit.JavascriptInterface
        fun hasClipboardText(): Boolean {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            return clipboard.hasPrimaryClip() &&
                    clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true &&
                    !clipboard.primaryClip?.getItemAt(0)?.text.isNullOrBlank()
        }

        @android.webkit.JavascriptInterface
        fun paste() {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val pasteData = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)
            if (!pasteData.isNullOrBlank()) {
                webViewRef?.post {
                    webViewRef?.evaluateJavascript("pasteText('${escapeJs(pasteData.toString())}')", null)
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI-Response", text)
            clipboard.setPrimaryClip(clip)
            // Optional: Show a toast or some feedback
        }

        @android.webkit.JavascriptInterface
        fun shareMessage(text: String) {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        }

        @android.webkit.JavascriptInterface
        fun favoriteMessage(chatId: String, messageIndex: Int) {
            scope.launch {
                messageLock.withLock {
                    val session = sessions.find { it.id == chatId }
                    session?.let {
                        if (messageIndex >= 0 && messageIndex < it.messages.size) {
                            val message = it.messages[messageIndex]
                            message.isFavorite = !message.isFavorite // Toggle favorite state

                            if (message.isFavorite) {
                                val favInfo = FavoriteMessageInfo(chatId, messageIndex, message)
                                // Avoid duplicates
                                if (favorites.none { f -> f.chatId == chatId && f.messageIndex == messageIndex }) {
                                    favorites.add(favInfo)
                                }
                            } else {
                                favorites.removeAll { f -> f.chatId == chatId && f.messageIndex == messageIndex }
                            }

                            saveSessions()
                            saveFavorites()
                            Log.d("ChatManager", "Toggled favorite for message at index $messageIndex in chat $chatId to ${message.isFavorite}")
                        } else {
                            Log.e("ChatManager", "Favorite failed: index out of bounds. Index: $messageIndex, Size: ${it.messages.size}")
                        }
                    } ?: run {
                        // This case can happen if the original session was deleted but the favorite remains.
                        // We just need to remove it from the favorites list.
                        favorites.removeAll { f -> f.chatId == chatId && f.messageIndex == messageIndex }
                        saveFavorites()
                        Log.d("ChatManager", "Unfavorited message from a deleted session: $chatId, index $messageIndex")
                    }
                }
            }
        }
    }

    fun sendMessageToWebView(message: String, webView: WebView, isDeepSeek: Boolean) {
        if (currentSessionId == null) {
            createNewSession()
        }
        val capturedSessionId = currentSessionId!!
        
        // Emulate the JS sendMessage function to update the UI from native code
        webView.post {
            webView.evaluateJavascript("addMessageToUI('user', '${escapeJs(message)}', true);", null)
            webView.evaluateJavascript("addMessageToUI('assistant', '', false);", null)
            webView.evaluateJavascript("showTypingIndicator();", null)
        }

        // Then, perform the actual API call.
        scope.launch {
            handleMessageSend(webView, message, isDeepSeek, capturedSessionId)
        }
    }

    private fun createNewSession(): ChatSession {
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
        return newSession
    }

    private suspend fun handleMessageSend(webView: WebView, message: String, isDeepSeek: Boolean, sessionId: String) {
        val session = sessions.find { it.id == sessionId }
        if (session == null) {
            Log.e("ChatManager", "Session not found for id: $sessionId")
            return
        }

        val apiKey = if (isDeepSeek) settingsManager.getDeepSeekApiKey() else settingsManager.getChatGPTApiKey()
        if (apiKey.isBlank()) {
            val error = "API Key for ${if (isDeepSeek) "DeepSeek" else "ChatGPT"} is not set."
            Log.e("ChatManager", error)
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript("showErrorMessage('${escapeJs(error)}');", null)
                webView.evaluateJavascript("completeResponse();", null) // 确保结束动画
            }
            return
        }

        val apiUrl = if (isDeepSeek) settingsManager.getDeepSeekApiUrl() else settingsManager.getChatGPTApiUrl()
        val model = if (isDeepSeek) "deepseek-chat" else "gpt-3.5-turbo"

        messageLock.withLock {
            session.messages.add(ChatMessage("user", message))
            if (session.messages.size == 1 && session.title == "新对话") {
                val newTitle = if (message.length > 20) message.substring(0, 20) + "..." else message
                session.title = newTitle
                Handler(Looper.getMainLooper()).post {
                    webView.evaluateJavascript("updateSessionTitle('$sessionId', '${escapeJs(newTitle)}');", null)
                }
            }
            saveSessions()
        }

        val contextMessages = session.messages.map { mapOf("role" to it.role, "content" to it.content) }
        streamResponse(webView, apiUrl, apiKey, model, contextMessages, sessionId)
    }

    private suspend fun streamResponse(webView: WebView, apiUrl: String, apiKey: String, model: String, context: List<Map<String, String>>, sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.doOutput = true
                connection.doInput = true

                val body = JSONObject()
                body.put("model", model)
                body.put("messages", JSONArray(context))
                body.put("stream", true)

                connection.outputStream.use { os ->
                    val input = body.toString().toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    var fullResponse = ""
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line!!.startsWith("data: ")) {
                            val jsonString = line!!.substring(6)
                            if (jsonString.trim() == "[DONE]") {
                                break
                            }
                            try {
                                val jsonObject = JSONObject(jsonString)
                                val delta = jsonObject.getJSONArray("choices").getJSONObject(0).getJSONObject("delta")
                                if (delta.has("content")) {
                                    val contentChunk = delta.getString("content")
                                    fullResponse += contentChunk
                                    Handler(Looper.getMainLooper()).post {
                                        webView.evaluateJavascript("appendToResponse('${escapeJs(contentChunk)}');", null)
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore json parsing errors for now
                            }
                        }
                    }
                    reader.close()
                    
                    messageLock.withLock {
                        sessions.find { it.id == sessionId }?.messages?.add(ChatMessage("assistant", fullResponse))
                        saveSessions()
                    }
                    
                    Handler(Looper.getMainLooper()).post {
                        webView.evaluateJavascript("completeResponse();", null)
                    }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    Log.e("ChatManager", "API Error: ${connection.responseCode} - $errorBody")
                    Handler(Looper.getMainLooper()).post {
                         webView.evaluateJavascript("completeResponse(); showErrorMessage('API Error: ${connection.responseCode}');", null)
                    }
                }
            } catch (e: Exception) {
                 Log.e("ChatManager", "Exception during API call", e)
                 Handler(Looper.getMainLooper()).post {
                    webView.evaluateJavascript("completeResponse(); showErrorMessage('Error: ${e.message}');", null)
                }
            }
        }
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
        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()
    }

    private fun loadSessions() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<ChatSession>>() {}.type
            try {
                sessions.clear()
                sessions.addAll(gson.fromJson(json, type))
            } catch (e: Exception) {
                Log.e("ChatManager", "Error loading sessions", e)
            }
        }
    }

    private fun saveCurrentSessionId() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_SESSION_ID, currentSessionId).apply()
    }
    
    private fun chatHistoryToJson(): String {
        return gson.toJson(sessions.map { 
            mapOf("id" to it.id, "title" to it.title, "timestamp" to it.timestamp) 
        })
    }

    private fun saveFavorites() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(favorites)
        prefs.edit().putString(KEY_FAVORITES, json).apply()
    }

    private fun loadFavorites() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_FAVORITES, null)
        if (json != null) {
            val type = object : TypeToken<MutableList<FavoriteMessageInfo>>() {}.type
            try {
                val loadedFavorites: MutableList<FavoriteMessageInfo> = gson.fromJson(json, type)
                favorites.clear()
                favorites.addAll(loadedFavorites)
            } catch (e: Exception) {
                Log.e("ChatManager", "Error loading favorites", e)
            }
        }
    }
}

