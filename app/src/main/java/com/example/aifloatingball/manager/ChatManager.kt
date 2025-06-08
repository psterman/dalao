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

class ChatManager(private val context: Context) {
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val chatHistory = mutableListOf<ChatMessage>()
    private var webViewRef: WebView? = null
    private var isDeepSeekEngine: Boolean = false

    data class ChatMessage(val role: String, val content: String, val isLoading: Boolean = false)

    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }

    fun initWebView(webView: WebView, engineKey: String) {
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
            }
        }
    }

    private inner class AndroidChatInterface {
        @android.webkit.JavascriptInterface
        fun sendMessage(message: String) {
            scope.launch {
                handleMessageSend(message, isDeepSeekEngine)
            }
        }
    }

    fun sendMessageToWebView(message: String, webView: WebView, isDeepSeek: Boolean) {
        val escapedMessage = escapeJs(message)
        webView.evaluateJavascript("""
            (function() {
                addMessageToUI('user', '$escapedMessage');
                showTypingIndicator();
                AndroidChatInterface.sendMessage('$escapedMessage');
            })();
        """, null)
    }

    private suspend fun handleMessageSend(message: String, isDeepSeek: Boolean) {
        val apiKey = if (isDeepSeek) settingsManager.getDeepSeekApiKey() else settingsManager.getChatGPTApiKey()
        if (apiKey.isBlank()) {
            val error = "API Key for ${if (isDeepSeek) "DeepSeek" else "ChatGPT"} is not set."
            Log.e("ChatManager", error)
            withContext(Dispatchers.Main) {
                webViewRef?.evaluateJavascript("showErrorMessage('${escapeJs(error)}');", null)
            }
            return
        }

        val apiUrl = if (isDeepSeek) settingsManager.getDeepSeekApiUrl() else settingsManager.getChatGPTApiUrl()
        val model = if (isDeepSeek) "deepseek-chat" else "gpt-3.5-turbo"
        
        chatHistory.add(ChatMessage("user", message))

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

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray(chatHistory.map { JSONObject().put("role", it.role).put("content", it.content) }))
                put("stream", true)
            }.toString()

            connection.outputStream.use { it.write(requestBody.toByteArray(StandardCharsets.UTF_8)) }

            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val fullResponse = StringBuilder()
            
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data:")) {
                    val jsonData = line.substring(5).trim()
                    if (jsonData != "[DONE]") {
                        try {
                            val choice = JSONObject(jsonData).getJSONArray("choices").getJSONObject(0)
                            val delta = choice.getJSONObject("delta")
                            if (delta.has("content")) {
                                val contentChunk = delta.getString("content")
                                fullResponse.append(contentChunk)
                                withContext(Dispatchers.Main) {
                                    webViewRef?.evaluateJavascript("appendToResponse('${escapeJs(contentChunk)}');", null)
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
            chatHistory.add(assistantMessage)
            if (chatHistory.size > MAX_HISTORY_SIZE) {
                chatHistory.removeAt(0)
            }

            withContext(Dispatchers.Main) {
                webViewRef?.evaluateJavascript("completeResponse();", null)
            }
        } catch (e: Exception) {
            Log.e("ChatManager", "API call failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                webViewRef?.evaluateJavascript("showErrorMessage('Error: ${escapeJs(e.message ?: "Unknown API error")}');", null)
            }
        }
    }

    suspend fun sendMessageAndGetResponse(message: String, isDeepSeek: Boolean, isStream: Boolean = false): String {
        if (isStream) {
            return "Error: Streaming is not supported in this context."
        }

        val apiKey = if (isDeepSeek) settingsManager.getDeepSeekApiKey() else settingsManager.getChatGPTApiKey()
        if (apiKey.isBlank()) {
            val error = "API Key for ${if (isDeepSeek) "DeepSeek" else "ChatGPT"} is not set."
            Log.e("ChatManager", error)
            return "Error: $error"
        }

        val apiUrl = if (isDeepSeek) settingsManager.getDeepSeekApiUrl() else settingsManager.getChatGPTApiUrl()
        val model = if (isDeepSeek) "deepseek-chat" else "gpt-3.5-turbo"

        chatHistory.add(ChatMessage("user", message))

        return try {
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

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray(chatHistory.map { JSONObject().put("role", it.role).put("content", it.content) }))
                put("stream", false)
            }.toString()

            connection.outputStream.use { it.write(requestBody.toByteArray(StandardCharsets.UTF_8)) }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            val jsonResponse = JSONObject(response)
            val fullResponse = jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            reader.close()
            
            connection.disconnect()

            val assistantMessage = ChatMessage("assistant", fullResponse)
            chatHistory.add(assistantMessage)
            if (chatHistory.size > MAX_HISTORY_SIZE) {
                chatHistory.removeAt(0)
            }
            fullResponse
        } catch (e: Exception) {
            Log.e("ChatManager", "API call failed: ${e.message}", e)
            "Error: ${e.message ?: "Unknown API error"}"
        }
    }

    private fun chatHistoryToJson(): String {
        return JSONArray(chatHistory.map {
            JSONObject().put("role", it.role).put("content", it.content)
        }).toString()
    }

    private fun escapeJs(s: String): String {
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
    }
}