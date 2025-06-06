package com.example.aifloatingball.manager

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.Charsets.UTF_8

class ChatManager(private val context: Context) {
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val chatHistory = mutableListOf<ChatMessage>()
    private var webViewRef: WebView? = null
    
    init {
        Log.d("ChatManager", "ChatManager 实例创建， context: $context, webViewRef: $webViewRef")
    }
    
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isLoading: Boolean = false
    )

    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }

    fun initWebView(webView: WebView) {
        Log.d("ChatManager", "初始化 DeepSeek 聊天界面 WebView")
        this.webViewRef = webView
        
        // 配置WebView设置
        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportMultipleWindows(false)
                builtInZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_NO_CACHE // 禁用缓存来解决部分问题
            }
            
            // 禁用长按选择，避免与输入冲突
            webView.setOnLongClickListener { true }
        } catch (e: Exception) {
            Log.e("ChatManager", "配置WebView设置失败: ${e.message}", e)
        }
        
        // 确保WebView可以获得焦点并显示软键盘
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        // webView.requestFocus() // 移除此行，由 DualFloatingWebViewService 控制
        
        // 添加JavaScript接口 - 确保移除旧的接口以避免重复添加
        try {
            webView.removeJavascriptInterface("AndroidChatInterface")
        } catch (e: Exception) {
            Log.d("ChatManager", "Previous JS Interface not found, which is fine.")
        }
        
        // 添加新的JavaScript接口
        webView.addJavascriptInterface(AndroidChatInterface(), "AndroidChatInterface")
        Log.d("ChatManager", "AndroidChatInterface 已添加到 WebView")
        
        // 加载HTML文件
        try {
            webView.loadUrl("file:///android_asset/deepseek_chat.html")
            Log.d("ChatManager", "已加载DeepSeek聊天界面HTML")
        } catch (e: Exception) {
            Log.e("ChatManager", "加载HTML内容失败: ${e.message}", e)
        }
        
        // 设置网页加载完成的监听器
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("ChatManager", "DeepSeek Chat 网页加载完成")
                Handler(Looper.getMainLooper()).postDelayed({
                    view?.requestFocus()
                    val historyJson = chatHistoryToJson()
                    view?.evaluateJavascript("loadChatHistoryFromAndroid('$historyJson');", null)
                }, 300)
            }
            
            @Deprecated("This method is deprecated in API level 23")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e("ChatManager", "WebView 加载错误: $errorCode, $description")
            }
        }
    }

    // Android 端与 WebView JavaScript 交互的接口
    private inner class AndroidChatInterface {
        @android.webkit.JavascriptInterface
        fun sendMessage(message: String) {
            Log.d("ChatManager", "JS-> sendMessage: $message")
            if (message.trim().isEmpty()) return
            Handler(Looper.getMainLooper()).post {
                webViewRef?.let { sendMessageToWebView(message, it, true) }
            }
        }

        @android.webkit.JavascriptInterface
        fun getChatHistoryJson(): String = chatHistoryToJson()

        @android.webkit.JavascriptInterface
        fun saveChatHistory(historyJson: String) {
            // Implementation to save history from JS
        }

        @android.webkit.JavascriptInterface
        fun clearChatHistory() {
            chatHistory.clear()
        }

        @android.webkit.JavascriptInterface
        fun updateAssistantMessageContent(_content: String) {
            // Handled by JS
        }

        @android.webkit.JavascriptInterface
        fun finalizeAssistantMessage() {
            // Handled by JS
        }
    }

    suspend fun sendMessage(message: String, isDeepSeek: Boolean): String {
        val userMessage = ChatMessage("user", message)
        chatHistory.add(userMessage)

        val response = try {
            if (isDeepSeek) {
                sendToDeepSeek(chatHistory)
            } else {
                sendToChatGPT(chatHistory)
            }
        } catch (e: Exception) {
            Log.e("ChatManager", "API call failed: ${e.message}", e)
            throw e
        }

        val assistantMessage = ChatMessage("assistant", response)
        chatHistory.add(assistantMessage)
        if (chatHistory.size > MAX_HISTORY_SIZE) {
            chatHistory.removeAt(0)
        }
        return response
    }

    fun sendMessageToWebView(message: String, webView: WebView, isDeepSeek: Boolean) {
        val apiKey = if (isDeepSeek) settingsManager.getDeepSeekApiKey() else settingsManager.getChatGPTApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.e("ChatManager", "API Key for ${if (isDeepSeek) "DeepSeek" else "ChatGPT"} is not set.")
            return
        }

        val userMessage = ChatMessage("user", message)
        chatHistory.add(userMessage)

        scope.launch {
            try {
                if (isDeepSeek) {
                    simulateStreamingResponse(webView)
                } else {
                    val response = sendToChatGPT(chatHistory)
                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("completeResponse('${escapeHtml(response)}');", null)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatManager", "Error in sendMessageToWebView: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = "错误: ${e.message ?: "未知错误"}"
                    webView.evaluateJavascript("showErrorMessage('${escapeHtml(errorMessage)}');", null)
                }
            }
        }
    }

    private suspend fun simulateStreamingResponse(webView: WebView) {
        val apiKey = settingsManager.getDeepSeekApiKey()
        if (apiKey.isEmpty()) throw Exception("请先设置 DeepSeek API 密钥")

        val url = URL(settingsManager.getDeepSeekApiUrl())
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
            connectTimeout = 30000
            readTimeout = 60000
        }

        val requestBody = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", JSONArray(chatHistory.map { JSONObject().put("role", it.role).put("content", it.content) }))
            put("stream", true)
        }.toString()

        connection.outputStream.use { it.write(requestBody.toByteArray(UTF_8)) }

        val reader = BufferedReader(InputStreamReader(connection.inputStream, UTF_8))
        val fullResponse = StringBuilder()
        reader.forEachLine { line ->
            if (line.startsWith("data: ")) {
                val jsonData = line.substring(6).trim()
                if (jsonData != "[DONE]") {
                    try {
                        val delta = JSONObject(jsonData).getJSONArray("choices").getJSONObject(0).getJSONObject("delta")
                        if (delta.has("content")) {
                            val contentChunk = delta.getString("content")
                            fullResponse.append(contentChunk)
                            scope.launch(Dispatchers.Main) {
                                webView.evaluateJavascript("appendToResponse('${escapeHtml(contentChunk)}');", null)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatManager", "JSON parse error: $e")
                    }
                }
            }
        }
        reader.close()
        connection.disconnect()

        val assistantMessage = ChatMessage("assistant", fullResponse.toString())
        chatHistory.add(assistantMessage)
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript("completeResponse();", null)
        }
    }

    private fun chatHistoryToJson(): String {
        return JSONArray(chatHistory.map {
            JSONObject().put("role", it.role).put("content", it.content).put("timestamp", it.timestamp)
        }).toString()
    }

    private fun escapeHtml(unsafe: String): String {
        return unsafe.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private suspend fun sendToApi(messages: List<ChatMessage>, apiKey: String, apiUrl: String, model: String): String {
        return withContext(Dispatchers.IO) {
            if (apiKey.isEmpty()) throw Exception("API Key not set for $model")

            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true

            val requestBody = JSONObject().put("model", model).put("messages", JSONArray(messages.map {
                JSONObject().put("role", it.role).put("content", it.content)
            })).toString()

            connection.outputStream.use { it.write(requestBody.toByteArray(UTF_8)) }

            val response = connection.inputStream.bufferedReader(UTF_8).use { it.readText() }
            JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private suspend fun sendToDeepSeek(messages: List<ChatMessage>): String {
        return sendToApi(messages, settingsManager.getDeepSeekApiKey(), settingsManager.getDeepSeekApiUrl(), "deepseek-chat")
    }

    private suspend fun sendToChatGPT(messages: List<ChatMessage>): String {
        return sendToApi(messages, settingsManager.getChatGPTApiKey(), SettingsManager.DEFAULT_CHATGPT_API_URL, "gpt-3.5-turbo")
    }

    fun clearHistory() {
        chatHistory.clear()
    }
}