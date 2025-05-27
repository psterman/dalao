package com.example.aifloatingball.manager

import android.content.Context
import android.webkit.WebView
import com.example.aifloatingball.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import java.io.*

class ChatManager(private val context: Context) {
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val chatHistory = mutableListOf<ChatMessage>()
    
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val MAX_HISTORY_SIZE = 50
        private val HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        margin: 0;
                        padding: 16px;
                        background: #f9fafb;
                        color: #1f2937;
                        line-height: 1.6;
                    }
                    #messages {
                        max-width: 100%;
                        margin: 0 auto;
                        padding-bottom: 60px;
                    }
                    .message-container {
                        display: flex;
                        flex-direction: column;
                        margin: 20px 0;
                        position: relative;
                    }
                    .message {
                        padding: 12px 16px;
                        border-radius: 12px;
                        max-width: 85%;
                        word-wrap: break-word;
                        white-space: pre-wrap;
                        position: relative;
                    }
                    .user-container {
                        align-items: flex-end;
                    }
                    .assistant-container {
                        align-items: flex-start;
                    }
                    .user {
                        background: #2563eb;
                        color: white;
                        margin-left: auto;
                    }
                    .assistant {
                        background: white;
                        border: 1px solid #e5e7eb;
                        box-shadow: 0 1px 2px rgba(0,0,0,0.05);
                    }
                    .role-label {
                        font-size: 12px;
                        color: #6b7280;
                        margin-bottom: 4px;
                        padding: 0 16px;
                    }
                    pre {
                        background: #1e1e1e;
                        color: #d4d4d4;
                        padding: 12px;
                        border-radius: 6px;
                        overflow-x: auto;
                        margin: 8px 0;
                    }
                    code {
                        font-family: 'Consolas', 'Monaco', monospace;
                        background: #1e1e1e;
                        color: #d4d4d4;
                        padding: 2px 4px;
                        border-radius: 4px;
                    }
                    p {
                        margin: 8px 0;
                    }
                    ul, ol {
                        margin: 8px 0;
                        padding-left: 20px;
                    }
                    .copy-button {
                        position: absolute;
                        top: 8px;
                        right: 8px;
                        background: rgba(255,255,255,0.9);
                        border: 1px solid #e5e7eb;
                        border-radius: 4px;
                        padding: 4px 8px;
                        font-size: 12px;
                        cursor: pointer;
                        display: none;
                    }
                    .message-container:hover .copy-button {
                        display: block;
                    }
                </style>
            </head>
            <body>
                <div id="messages"></div>
                <script>
                    function addMessage(role, content) {
                        var messagesDiv = document.getElementById('messages');
                        var container = document.createElement('div');
                        container.className = 'message-container ' + role + '-container';
                        
                        // 添加角色标签
                        var roleLabel = document.createElement('div');
                        roleLabel.className = 'role-label';
                        roleLabel.textContent = role === 'user' ? '你' : 'AI';
                        container.appendChild(roleLabel);
                        
                        // 添加消息内容
                        var messageDiv = document.createElement('div');
                        messageDiv.className = 'message ' + role;
                        
                        // 处理代码块
                        content = content.replace(/\\n/g, '\n');  // 先还原换行符
                        content = content.replace(/```([\\s\\S]*?)```/g, function(match, code) {
                            return '<pre><code>' + code.trim() + '</code></pre>';
                        });
                        
                        // 处理行内代码
                        content = content.replace(/`([^`]+)`/g, '<code>$1</code>');
                        
                        // 处理换行
                        content = content.replace(/\n/g, '<br>');
                        
                        messageDiv.innerHTML = content;
                        container.appendChild(messageDiv);
                        
                        // 添加复制按钮
                        var copyButton = document.createElement('button');
                        copyButton.className = 'copy-button';
                        copyButton.textContent = '复制';
                        copyButton.onclick = function() {
                            var textToCopy = messageDiv.textContent;
                            navigator.clipboard.writeText(textToCopy).then(function() {
                                copyButton.textContent = '已复制';
                                setTimeout(function() {
                                    copyButton.textContent = '复制';
                                }, 2000);
                            });
                        };
                        messageDiv.appendChild(copyButton);
                        
                        messagesDiv.appendChild(container);
                        window.scrollTo(0, document.body.scrollHeight);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    fun initWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        webView.loadDataWithBaseURL(null, HTML_TEMPLATE, "text/html", "UTF-8", null)
    }

    fun sendMessage(message: String, webView: WebView, isDeepSeek: Boolean) {
        // 检查API密钥是否已设置
        if (isDeepSeek) {
            if (settingsManager.getDeepSeekApiKey().isNullOrBlank()) {
                throw IllegalStateException("请先在设置页面配置 DeepSeek API 密钥")
            }
        } else {
            if (settingsManager.getChatGPTApiKey().isNullOrBlank()) {
                throw IllegalStateException("请先在设置页面配置 ChatGPT API 密钥")
            }
        }

        // 添加用户消息到历史记录
        val userMessage = ChatMessage("user", message)
        chatHistory.add(userMessage)
        
        // 更新WebView显示
        updateWebViewWithMessage(webView, "user", message)
        
        // 发送API请求
        scope.launch {
            try {
                val response = if (isDeepSeek) {
                    sendToDeepSeek(chatHistory)
                } else {
                    sendToChatGPT(chatHistory)
                }
                
                // 添加助手回复到历史记录
                val assistantMessage = ChatMessage("assistant", response)
                chatHistory.add(assistantMessage)
                
                // 更新WebView显示
                withContext(Dispatchers.Main) {
                    updateWebViewWithMessage(webView, "assistant", response)
                }
                
                // 如果历史记录太长，移除最早的消息
                if (chatHistory.size > MAX_HISTORY_SIZE) {
                    chatHistory.removeAt(0)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateWebViewWithMessage(webView, "assistant", "错误：${e.message}")
                }
            }
        }
    }

    private fun updateWebViewWithMessage(webView: WebView, role: String, content: String) {
        // 转义特殊字符
        val escapedContent = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("'", "\\'")

        val js = """
            (function() {
                addMessage('$role', "$escapedContent");
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }

    private suspend fun sendToDeepSeek(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val apiKey = settingsManager.getDeepSeekApiKey()
            if (apiKey.isEmpty()) {
                throw Exception("请先设置 DeepSeek API 密钥")
            }

            val url = URL(settingsManager.getDeepSeekApiUrl())
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
            }

            val requestBody = JSONObject().apply {
                put("model", "deepseek-chat")
                put("messages", JSONArray().apply {
                    messages.forEach { message ->
                        put(JSONObject().apply {
                            put("role", message.role)
                            put("content", message.content)
                        })
                    }
                })
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private suspend fun sendToChatGPT(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val apiKey = settingsManager.getChatGPTApiKey()
            if (apiKey.isEmpty()) {
                throw Exception("请先设置ChatGPT API密钥")
            }

            val url = URL(SettingsManager.DEFAULT_CHATGPT_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
            }

            val requestBody = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
                put("messages", JSONArray().apply {
                    messages.forEach { message ->
                        put(JSONObject().apply {
                            put("role", message.role)
                            put("content", message.content)
                        })
                    }
                })
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    fun clearHistory() {
        chatHistory.clear()
    }
} 