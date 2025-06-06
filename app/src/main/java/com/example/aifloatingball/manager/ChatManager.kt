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
import android.os.Handler
import android.os.Looper
import android.util.Log

class ChatManager(private val context: Context) {
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val chatHistory = mutableListOf<ChatMessage>()
    private var webViewRef: WebView? = null
    
    init {
        android.util.Log.d("ChatManager", "ChatManager 实例创建， context: $context, webViewRef: $webViewRef")
    }
    
    data class ChatMessage(
        val role: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val isLoading: Boolean = false
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
                        padding: 8px;
                        background: #FFFFFF;
                        color: #2C3E50;
                        line-height: 1.6;
                    }
                    .header {
                        display: flex;
                        justify-content: center;
                        padding: 12px 0;
                        margin-bottom: 20px;
                        border-bottom: 1px solid #E0E0E0;
                    }
                    .logo {
                        height: 40px;
                        display: block;
                    }
                    .welcome-container {
                        text-align: center;
                        padding: 20px;
                        max-width: 600px;
                        margin: 0 auto 30px auto;
                    }
                    .welcome-title {
                        font-size: 24px;
                        font-weight: 600;
                        margin-bottom: 16px;
                        color: #2C3E50;
                    }
                    .welcome-text {
                        font-size: 16px;
                        color: #5D6D7E;
                        margin-bottom: 24px;
                    }
                    .example-queries {
                        background: #F8F9FA;
                        border-radius: 12px;
                        padding: 16px;
                        margin-bottom: 20px;
                    }
                    .example-title {
                        font-weight: 600;
                        margin-bottom: 12px;
                        color: #2C3E50;
                    }
                    .example-button {
                        display: inline-block;
                        background: #F1F3F4;
                        color: #2C3E50;
                        border: 1px solid #E0E0E0;
                        border-radius: 16px;
                        padding: 8px 16px;
                        margin: 4px;
                        font-size: 14px;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .example-button:hover {
                        background: #E1E5E8;
                    }
                    #messages {
                        max-width: 100%;
                        margin: 0 auto;
                        padding-bottom: 70px;
                    }
                    .message-container {
                        display: flex;
                        margin: 20px 0;
                        position: relative;
                    }
                    .avatar {
                        width: 36px;
                        height: 36px;
                        border-radius: 50%;
                        margin-right: 12px;
                        flex-shrink: 0;
                    }
                    .avatar.user {
                        background: #3498DB;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                        font-weight: bold;
                    }
                    .avatar.assistant {
                        background: #F1F3F4;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .message-content {
                        flex: 1;
                        display: flex;
                        flex-direction: column;
                        max-width: calc(100% - 60px);
                    }
                    .message {
                        padding: 12px 16px;
                        border-radius: 12px;
                        word-wrap: break-word;
                        white-space: pre-wrap;
                        position: relative;
                        max-width: 100%;
                    }
                    .user-container .message {
                        background: #EBF5FE;
                        color: #2C3E50;
                        border: 1px solid #D4E6F9;
                    }
                    .assistant-container .message {
                        background: #F8F9FA;
                        border: 1px solid #E5E7EB;
                    }
                    .role-label {
                        font-size: 14px;
                        font-weight: 500;
                        color: #5D6D7E;
                        margin-bottom: 4px;
                    }
                    pre {
                        background: #2C3E50;
                        color: #E5E7EB;
                        padding: 12px;
                        border-radius: 8px;
                        overflow-x: auto;
                        margin: 8px 0;
                    }
                    code {
                        font-family: 'Consolas', 'Monaco', monospace;
                        font-size: 14px;
                    }
                    p {
                        margin: 8px 0;
                    }
                    ul, ol {
                        margin: 8px 0;
                        padding-left: 24px;
                    }
                    .copy-button {
                        position: absolute;
                        top: 8px;
                        right: 8px;
                        background: rgba(255,255,255,0.9);
                        border: 1px solid #E5E7EB;
                        border-radius: 4px;
                        padding: 4px 8px;
                        font-size: 12px;
                        cursor: pointer;
                        display: none;
                    }
                    .message-container:hover .copy-button {
                        display: block;
                    }
                    .typing-indicator {
                        display: inline-flex;
                        align-items: center;
                        margin-left: 8px;
                    }
                    .typing-indicator span {
                        height: 8px;
                        width: 8px;
                        background-color: #3498DB;
                        border-radius: 50%;
                        display: inline-block;
                        margin: 0 2px;
                        opacity: 0.4;
                        animation: typing 1s infinite;
                    }
                    .typing-indicator span:nth-child(1) { animation-delay: 0s; }
                    .typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
                    .typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
                    @keyframes typing {
                        0% { opacity: 0.4; }
                        50% { opacity: 1; }
                        100% { opacity: 0.4; }
                    }
                    #input-area {
                        display: flex;
                        padding: 12px;
                        border-top: 1px solid #E5E7EB;
                        position: fixed;
                        bottom: 0;
                        left: 0;
                        right: 0;
                        background: #FFFFFF;
                        box-shadow: 0 -2px 10px rgba(0,0,0,0.05);
                    }
                    #message-input {
                        flex-grow: 1;
                        padding: 12px 16px;
                        border: 1px solid #D1D5DB;
                        border-radius: 24px;
                        font-size: 16px;
                        outline: none;
                        transition: border 0.2s;
                    }
                    #message-input:focus {
                        border-color: #3498DB;
                    }
                    #send-button {
                        background: #3498DB;
                        color: white;
                        border: none;
                        border-radius: 50%;
                        width: 44px;
                        height: 44px;
                        margin-left: 10px;
                        cursor: pointer;
                        font-size: 16px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="logo">
                        <svg width="160" height="40" viewBox="0 0 160 40" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M20 6L6 34H34L20 6Z" fill="#3498DB"/>
                            <text x="48" y="25" font-family="Arial" font-size="18" font-weight="bold" fill="#2C3E50">DeepSeek</text>
                        </svg>
                    </div>
                </div>
                
                <div id="welcome-section" class="welcome-container">
                    <h1 class="welcome-title">欢迎使用 DeepSeek 智能助手</h1>
                    <p class="welcome-text">DeepSeek 是一个强大的 AI 助手，可以帮助你解决各种问题、进行创意写作、提供信息或只是聊天交流。</p>
                    
                    <div class="example-queries">
                        <div class="example-title">你可以尝试以下问题：</div>
                        <div class="example-button" onclick="useExample('如何提高英语口语水平？')">如何提高英语口语水平？</div>
                        <div class="example-button" onclick="useExample('写一个关于春天的短诗')">写一个关于春天的短诗</div>
                        <div class="example-button" onclick="useExample('解释量子计算的基本原理')">解释量子计算的基本原理</div>
                        <div class="example-button" onclick="useExample('帮我写一个简单的Python爬虫程序')">帮我写一个简单的Python爬虫程序</div>
                    </div>
                </div>
                
                <div id="messages"></div>
                
                <div id="input-area">
                    <div id="message-input" contenteditable="true" placeholder="输入你的消息..." inputmode="text" data-gramm="false"></div>
                    <button id="send-button" disabled>
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" fill="white"/>
                        </svg>
                    </button>
                </div>
                
                <script>
                    let isGenerating = false;
                    const messageInput = document.getElementById('message-input');
                    const sendButton = document.getElementById('send-button');
                    const welcomeSection = document.getElementById('welcome-section');
                    
                    function hideWelcomeSection() {
                        if (welcomeSection) {
                            welcomeSection.style.display = 'none';
                        }
                    }
                    
                    function useExample(text) {
                        messageInput.textContent = text;
                        // 移除了JS层面的focus，交由Native处理
                        // messageInput.focus();
                    }
                    
                    function addMessageToUI(role, content, isComplete = true) {
                        // ... existing code ...
                    }

                    document.getElementById('send-button').addEventListener('click', function() {
                        sendMessage();
                    });

                    document.getElementById('message-input').addEventListener('keydown', function(e) {
                        if ((e.key === 'Enter' || e.keyCode === 13) && !e.shiftKey) {
                            e.preventDefault();
                            sendMessage();
                            return false;
                        }
                    });

                    function sendMessage() {
                        var messageText = messageInput.textContent.trim();
                        if (messageText) {
                            isGenerating = true;
                            addMessageToUI('user', messageText);
                            AndroidChatInterface.sendMessage(messageText);
                            messageInput.textContent = ''; // 清空输入框
                            updateSendButton();
                        }
                    }
                    
                    function appendToResponse(content) {
                        // ... existing code ...
                    }

                    function completeResponse(finalContent) {
                        // ... existing code ...
                    }

                    // 功能：更新发送按钮状态
                    function updateSendButton() {
                        const text = messageInput.textContent.trim();
                        sendButton.disabled = text === '' || isGenerating;
                    }

                    // 功能：加载聊天历史记录
                    function loadChatHistory() {
                        // ... existing code ...
                    }

                    // 功能：保存聊天历史记录
                    function saveChatHistory() {
                        // ... existing code ...
                    }

                    // 功能：渲染历史记录列表
                    function renderHistoryList() {
                        // ... existing code ...
                    }

                    // 功能：切换历史记录面板
                    historyBtn.addEventListener('click', function () {
                        historyContainer.classList.toggle('open');
                    });

                    // 功能：创建新对话
                    newChatBtn.addEventListener('click', function () {
                        startNewChat();
                    });

                    // 功能：开始新对话
                    function startNewChat() {
                        currentChatId = Date.now().toString();
                        messagesContainer.innerHTML = '';
                        const newChat = {
                            id: currentChatId,
                            title: '新对话',
                            timestamp: Date.now(),
                            messages: []
                        };
                        chatHistory.push(newChat);
                        saveChatHistory();
                        renderHistoryList();
                        historyContainer.classList.remove('open');
                        messageInput.textContent = '';
                        // 移除了JS层面的focus，交由Native处理
                        // messageInput.focus();
                    }

                    // 功能：加载指定ID的聊天
                    function loadChat(chatId) {
                        // ... existing code ...
                    }

                    // 如果没有历史记录，开始新对话
                    if (chatHistory.length === 0) {
                        startNewChat();
                    } else {
                        // 加载最近的聊天
                        const recentChat = [...chatHistory].sort((a, b) => b.timestamp - a.timestamp)[0];
                        if (recentChat) {
                            loadChat(recentChat.id);
                        }
                    }
                </script>
            </body>
            </html>
        """

        // 添加公共方法来获取HTML模板
        fun getChatHtmlTemplate(): String {
            return HTML_TEMPLATE
        }
    }

    fun initWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        
        // 确保WebView可以获得焦点并显示软键盘
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(AndroidChatInterface(), "AndroidChatInterface")
        android.util.Log.d("ChatManager", "AndroidChatInterface 已添加到 WebView")
        
        // 加载自定义 HTML 文件
        webView.loadUrl("file:///android_asset/deepseek_chat.html")
        
        // 设置网页加载完成的监听器
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 移除此处强制显示输入法的逻辑，由DualFloatingWebViewService控制
            }
        }
    }

    // Android 端与 WebView JavaScript 交互的接口
    private inner class AndroidChatInterface {
        @android.webkit.JavascriptInterface
        fun sendMessage(message: String) {
            android.util.Log.d("ChatManager", "从 JavaScript 接收到消息: $message")
            // 在主线程上处理消息发送
            Handler(Looper.getMainLooper()).post { 
                webViewRef?.let { wv ->
                    val isDeepSeek = true
                    android.util.Log.d("ChatManager", "调用 sendMessageToWebView 发送消息: $message, isDeepSeek: $isDeepSeek")
                    sendMessageToWebView(message, wv, isDeepSeek)
                } ?: android.util.Log.e("ChatManager", "webViewRef 为空，无法发送消息")
            }
        }
    }

    fun sendMessageToWebView(message: String, webView: WebView, isDeepSeek: Boolean) {
        if (isDeepSeek) {
            if (settingsManager.getDeepSeekApiKey().isNullOrBlank()) {
                throw IllegalStateException("请先在设置页面配置 DeepSeek API 密钥")
            }
        } else {
            if (settingsManager.getChatGPTApiKey().isNullOrBlank()) {
                throw IllegalStateException("请先在设置页面配置 ChatGPT API 密钥")
            }
        }

        val userMessage = ChatMessage("user", message)
        chatHistory.add(userMessage)
        
        // 不再需要更新 WebView，因为已经在 JavaScript 中处理了
        
        scope.launch {
            try {
                if (isDeepSeek) {
                    // 模拟流式响应
                    simulateStreamingResponse(webView, message)
                } else {
                    val response = sendToChatGPT(chatHistory)
                    val assistantMessage = ChatMessage("assistant", response)
                    chatHistory.add(assistantMessage)
                    
                    // 完成响应
                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("completeResponse();", null)
                    }
                }
                
                if (chatHistory.size > MAX_HISTORY_SIZE) {
                    chatHistory.removeAt(0)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMessage = "错误：${e.message}"
                    webView.evaluateJavascript("appendToResponse(\"$errorMessage\");", null)
                    webView.evaluateJavascript("completeResponse();", null)
                }
            }
        }
    }
    
    // 模拟流式响应
    private suspend fun simulateStreamingResponse(webView: WebView, message: String) {
        withContext(Dispatchers.IO) {
            try {
                val apiKey = settingsManager.getDeepSeekApiKey()
                if (apiKey.isEmpty()) {
                    throw Exception("请先设置 DeepSeek API 密钥")
                }

                // 首先发送一个"正在思考"的消息，让用户知道请求已经开始
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("appendToResponse(\"正在连接 DeepSeek...\");", null)
                }

                val url = URL(settingsManager.getDeepSeekApiUrl())
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                    connectTimeout = 30000 // 30秒连接超时
                    readTimeout = 60000 // 60秒读取超时
                }

                val requestBody = JSONObject().apply {
                    put("model", "deepseek-chat")
                    put("messages", JSONArray().apply {
                        chatHistory.forEach { message ->
                            put(JSONObject().apply {
                                put("role", message.role)
                                put("content", message.content)
                            })
                        }
                    })
                }

                // 发送请求前通知用户
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("appendToResponse(\"正在等待回复...\");", null)
                }

                // 发送请求
                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                // 获取响应
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val content = jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                // 清除之前的"正在思考"消息
                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript("currentResponseElement.innerHTML = '';", null)
                }

                // 将完整响应分成多个小块模拟流式响应
                val chunks = splitIntoChunks(content)
                val fullResponse = StringBuilder()

                for (chunk in chunks) {
                    delay(30) // 减少延迟使响应更流畅
                    fullResponse.append(chunk)
                    
                    // 转义特殊字符
                    val escapedChunk = chunk
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("'", "\\'")
                    
                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("appendToResponse(\"$escapedChunk\");", null)
                    }
                }

                // 添加到聊天历史
                val assistantMessage = ChatMessage("assistant", fullResponse.toString())
                chatHistory.add(assistantMessage)
                
                // 确保在主线程上执行UI更新
                withContext(Dispatchers.Main) {
                    try {
                        // 标记响应已完成，完成后状态会重置
                        webView.evaluateJavascript("completeResponse();", null)
                    } catch (e: Exception) {
                        android.util.Log.e("ChatManager", "完成响应时出错: ${e.message}")
                        // 强制重置状态
                        webView.evaluateJavascript("isGenerating = false; updateSendButton();", null)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatManager", "流式响应发生错误: ${e.message}")
                withContext(Dispatchers.Main) {
                    val errorMessage = "错误：${e.message ?: "未知错误"}"
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                    webView.evaluateJavascript("appendToResponse(\"$errorMessage\");", null)
                    webView.evaluateJavascript("completeResponse();", null)
                    // 确保状态重置
                    webView.evaluateJavascript("isGenerating = false; updateSendButton();", null)
                }
            }
        }
    }
    
    // 将文本分成小块以模拟流式响应
    private fun splitIntoChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        val words = text.split(" ")
        
        var currentChunk = StringBuilder()
        for (word in words) {
            if (currentChunk.length > 5) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder()
            }
            if (currentChunk.isNotEmpty()) {
                currentChunk.append(" ")
            }
            currentChunk.append(word)
        }
        
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        
        return chunks
    }

    suspend fun sendMessage(message: String, isDeepSeek: Boolean): String {
        if (isDeepSeek) {
            if (settingsManager.getDeepSeekApiKey().isNullOrBlank()) {
                throw IllegalStateException("请先在设置页面配置 DeepSeek API 密钥")
            }
        } else {
            if (settingsManager.getChatGPTApiKey().isNullOrBlank()) {
                throw IllegalStateException("请先在设置页面配置 ChatGPT API 密钥")
            }
        }

        val currentChatHistory = mutableListOf<ChatMessage>()
        currentChatHistory.add(ChatMessage("user", message))

        return if (isDeepSeek) {
            sendToDeepSeek(currentChatHistory)
        } else {
            sendToChatGPT(currentChatHistory)
        }
    }

    private fun updateWebViewWithMessage(webView: WebView, role: String, content: String) {
        val escapedContent = content
            .replace("\\", "\\\\") // Escapes backslashes
            .replace("\"", "\"") // Escapes double quotes
            .replace("\n", "\\n")   // Escapes newlines
            .replace("\r", "\\r")   // Escapes carriage returns
            .replace("'", "\\'")   // Escapes single quotes

        val js = """
            (function() {
                addMessage("$role", "$escapedContent");
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