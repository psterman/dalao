package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.SettingsManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// AI服务类型
enum class AIServiceType {
    CHATGPT,
    CLAUDE,
    GEMINI,
    WENXIN,
    DEEPSEEK,
    QIANWEN,
    XINGHUO,
    KIMI
}

/**
 * AI API管理器
 * 支持多种AI服务的API调用
 */
class AIApiManager(private val context: Context) {
    
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    companion object {
        private const val TAG = "AIApiManager"
    }
    
    /**
     * AI服务配置
     */
    data class AIServiceConfig(
        val type: AIServiceType,
        val name: String,
        val apiUrl: String,
        val apiKey: String,
        val model: String,
        val maxTokens: Int = 4096,
        val temperature: Double = 0.7
    )
    
    /**
     * 安全获取字符串设置
     */
    private fun getStringSetting(key: String, defaultValue: String): String {
        return settingsManager.getString(key, defaultValue) ?: defaultValue
    }
    
    /**
     * 流式响应回调
     */
    interface StreamingCallback {
        fun onChunkReceived(chunk: String)
        fun onComplete(fullResponse: String)
        fun onError(error: String)
    }
    
    /**
     * 获取AI服务配置
     */
    private fun getServiceConfig(type: AIServiceType): AIServiceConfig? {
        return when (type) {
            AIServiceType.CHATGPT -> {
                val apiKey = getStringSetting("chatgpt_api_key", "")
                val apiUrl = getStringSetting("chatgpt_api_url", "https://api.openai.com/v1/chat/completions")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "ChatGPT",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "gpt-3.5-turbo"
                    )
                } else null
            }
            AIServiceType.CLAUDE -> {
                val apiKey = getStringSetting("claude_api_key", "")
                val apiUrl = getStringSetting("claude_api_url", "https://api.anthropic.com/v1/messages")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "Claude",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "claude-3-sonnet-20240229"
                    )
                } else null
            }
            AIServiceType.GEMINI -> {
                val apiKey = getStringSetting("gemini_api_key", "")
                val apiUrl = getStringSetting("gemini_api_url", "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "Gemini",
                        apiUrl = "$apiUrl?key=$apiKey",
                        apiKey = apiKey,
                        model = "gemini-pro"
                    )
                } else null
            }
            AIServiceType.WENXIN -> {
                val apiKey = getStringSetting("wenxin_api_key", "")
                val secretKey = getStringSetting("wenxin_secret_key", "")
                val apiUrl = getStringSetting("wenxin_api_url", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions")
                if (apiKey.isNotBlank() && secretKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "文心一言",
                        apiUrl = "$apiUrl?access_token=${getWenxinAccessToken(apiKey, secretKey)}",
                        apiKey = apiKey,
                        model = "ernie-bot-4"
                    )
                } else null
            }
            AIServiceType.DEEPSEEK -> {
                val apiKey = getStringSetting("deepseek_api_key", "")
                val apiUrl = getStringSetting("deepseek_api_url", "https://api.deepseek.com/v1/chat/completions")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "DeepSeek",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "deepseek-chat"
                    )
                } else null
            }
            AIServiceType.QIANWEN -> {
                val apiKey = getStringSetting("qianwen_api_key", "")
                val apiUrl = getStringSetting("qianwen_api_url", "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "通义千问",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "qwen-turbo"
                    )
                } else null
            }
            AIServiceType.XINGHUO -> {
                val apiKey = getStringSetting("xinghuo_api_key", "")
                val apiUrl = getStringSetting("xinghuo_api_url", "https://spark-api.xf-yun.com/v3.1/chat")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "讯飞星火",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "spark-v3.1"
                    )
                } else null
            }
            AIServiceType.KIMI -> {
                val apiKey = getStringSetting("kimi_api_key", "")
                val apiUrl = getStringSetting("kimi_api_url", "https://kimi.moonshot.cn/api/chat-messages")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "Kimi",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "moonshot-v1-8k"
                    )
                } else null
            }
        }
    }
    
    /**
     * 发送消息到AI服务
     */
    fun sendMessage(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>> = emptyList(),
        callback: StreamingCallback
    ) {
        val config = getServiceConfig(serviceType)
        if (config == null) {
            callback.onError("API密钥未配置")
            return
        }
        
        scope.launch {
            try {
                when (config.type) {
                    AIServiceType.CHATGPT -> sendToChatGPT(config, message, conversationHistory, callback)
                    AIServiceType.CLAUDE -> sendToClaude(config, message, conversationHistory, callback)
                    AIServiceType.GEMINI -> sendToGemini(config, message, conversationHistory, callback)
                    AIServiceType.WENXIN -> sendToWenxin(config, message, conversationHistory, callback)
                    AIServiceType.DEEPSEEK -> sendToDeepSeek(config, message, conversationHistory, callback)
                    AIServiceType.QIANWEN -> sendToQianwen(config, message, conversationHistory, callback)
                    AIServiceType.XINGHUO -> sendToXinghuo(config, message, conversationHistory, callback)
                    AIServiceType.KIMI -> sendToKimi(config, message, conversationHistory, callback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送消息失败", e)
                callback.onError("发送消息失败: ${e.message}")
            }
        }
    }
    
    /**
     * 发送到ChatGPT
     */
    private suspend fun sendToChatGPT(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        val url = URL(config.apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            doOutput = true
            doInput = true
        }
        
        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().apply {
                // 添加历史对话
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    })
                }
                // 添加当前消息
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", message)
                })
            })
            put("stream", true)
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature)
        }
        
        connection.outputStream.use { os ->
            val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
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
                            callback.onChunkReceived(contentChunk)
                        }
                    } catch (e: Exception) {
                        // 忽略JSON解析错误
                    }
                }
            }
            reader.close()
            callback.onComplete(fullResponse)
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            callback.onError("API错误: ${connection.responseCode} - $errorBody")
        }
    }
    
    /**
     * 发送到Claude
     */
    private suspend fun sendToClaude(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        val url = URL(config.apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", config.apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            doOutput = true
            doInput = true
        }
        
        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", config.maxTokens)
            put("messages", JSONArray().apply {
                // 添加历史对话
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    })
                }
                // 添加当前消息
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", message)
                })
            })
        }
        
        connection.outputStream.use { os ->
            val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
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
                        if (jsonObject.has("delta") && jsonObject.getJSONObject("delta").has("content")) {
                            val contentChunk = jsonObject.getJSONObject("delta").getJSONArray("content").getJSONObject(0).getString("text")
                            fullResponse += contentChunk
                            callback.onChunkReceived(contentChunk)
                        }
                    } catch (e: Exception) {
                        // 忽略JSON解析错误
                    }
                }
            }
            reader.close()
            callback.onComplete(fullResponse)
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            callback.onError("API错误: ${connection.responseCode} - $errorBody")
        }
    }
    
    /**
     * 发送到Gemini
     */
    private suspend fun sendToGemini(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        val url = URL(config.apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            doInput = true
        }
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                // 添加历史对话和当前消息
                val allMessages = conversationHistory.toMutableList()
                allMessages.add(mapOf("role" to "user", "content" to message))
                
                allMessages.forEach { msg ->
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", msg["content"])
                            })
                        })
                    })
                }
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", config.maxTokens)
                put("temperature", config.temperature)
            })
        }
        
        connection.outputStream.use { os ->
            val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
            os.write(input, 0, input.size)
        }
        
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val response = reader.readText()
            reader.close()
            
            try {
                val jsonObject = JSONObject(response)
                val candidates = jsonObject.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        val text = parts.getJSONObject(0).getString("text")
                        callback.onChunkReceived(text)
                        callback.onComplete(text)
                    }
                }
            } catch (e: Exception) {
                callback.onError("解析响应失败: ${e.message}")
            }
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            callback.onError("API错误: ${connection.responseCode} - $errorBody")
        }
    }
    
    /**
     * 发送到文心一言
     */
    private suspend fun sendToWenxin(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        val url = URL(config.apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            doInput = true
        }
        
        val requestBody = JSONObject().apply {
            put("messages", JSONArray().apply {
                // 添加历史对话
                conversationHistory.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    })
                }
                // 添加当前消息
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", message)
                })
            })
            put("stream", true)
        }
        
        connection.outputStream.use { os ->
            val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
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
                        if (jsonObject.has("result")) {
                            val contentChunk = jsonObject.getString("result")
                            fullResponse += contentChunk
                            callback.onChunkReceived(contentChunk)
                        }
                    } catch (e: Exception) {
                        // 忽略JSON解析错误
                    }
                }
            }
            reader.close()
            callback.onComplete(fullResponse)
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            callback.onError("API错误: ${connection.responseCode} - $errorBody")
        }
    }
    
    /**
     * 发送到DeepSeek
     */
    private suspend fun sendToDeepSeek(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        try {
            Log.d(TAG, "开始发送DeepSeek请求")
            Log.d(TAG, "API URL: ${config.apiUrl}")
            Log.d(TAG, "模型: ${config.model}")
            Log.d(TAG, "API密钥长度: ${config.apiKey.length}")
            Log.d(TAG, "API密钥前10位: ${config.apiKey.take(10)}...")
            
            val url = URL(config.apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                doOutput = true
                doInput = true
                connectTimeout = 30000
                readTimeout = 60000
            }
            
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("messages", JSONArray().apply {
                    // 添加历史对话
                    conversationHistory.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg["role"])
                            put("content", msg["content"])
                        })
                    }
                    // 添加当前消息
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("stream", true)
                put("max_tokens", config.maxTokens)
                put("temperature", config.temperature)
            }
            
            val requestJson = requestBody.toString()
            Log.d(TAG, "请求体: $requestJson")
            
            connection.outputStream.use { os ->
                val input = requestJson.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "DeepSeek响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
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
                                callback.onChunkReceived(contentChunk)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "解析流式响应时出错: ${e.message}")
                        }
                    }
                }
                reader.close()
                callback.onComplete(fullResponse)
            } else {
                val errorStream = connection.errorStream
                val errorBody = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                } else {
                    "Unknown error"
                }
                
                Log.e(TAG, "DeepSeek API错误: $responseCode - $errorBody")
                
                // 根据错误码提供更具体的错误信息
                val errorMessage = when (responseCode) {
                    401 -> "API密钥无效或已过期，请检查您的DeepSeek API密钥"
                    403 -> "API密钥权限不足，请检查您的账户权限"
                    429 -> "请求频率过高，请稍后重试"
                    500 -> "DeepSeek服务器内部错误，请稍后重试"
                    else -> "DeepSeek API错误: $responseCode - $errorBody"
                }
                
                callback.onError(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek请求异常", e)
            callback.onError("DeepSeek请求失败: ${e.message}")
        }
    }
    
    /**
     * 发送到通义千问
     */
    private suspend fun sendToQianwen(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        val url = URL(config.apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            doOutput = true
            doInput = true
        }
        
        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("input", JSONObject().apply {
                put("messages", JSONArray().apply {
                    // 添加历史对话
                    conversationHistory.forEach { msg ->
                        put(JSONObject().apply {
                            put("role", msg["role"])
                            put("content", msg["content"])
                        })
                    }
                    // 添加当前消息
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
            })
        }
        
        connection.outputStream.use { os ->
            val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
            os.write(input, 0, input.size)
        }
        
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
            val response = reader.readText()
            reader.close()
            
            try {
                val jsonObject = JSONObject(response)
                val output = jsonObject.getJSONObject("output")
                val text = output.getString("text")
                callback.onChunkReceived(text)
                callback.onComplete(text)
            } catch (e: Exception) {
                callback.onError("解析响应失败: ${e.message}")
            }
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            callback.onError("API错误: ${connection.responseCode} - $errorBody")
        }
    }
    
    /**
     * 发送到讯飞星火
     */
    private suspend fun sendToXinghuo(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        // 讯飞星火需要特殊的认证和格式，这里简化处理
        callback.onError("讯飞星火API暂未实现")
    }
    
    /**
     * 发送到Kimi
     */
    private suspend fun sendToKimi(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        // Kimi API实现
        callback.onError("Kimi API暂未实现")
    }
    
    /**
     * 获取文心一言访问令牌
     */
    private fun getWenxinAccessToken(apiKey: String, secretKey: String): String {
        // 这里需要实现获取访问令牌的逻辑
        // 由于需要网络请求，这里简化处理
        return ""
    }
    
    /**
     * 测试DeepSeek API连接
     */
    fun testDeepSeekConnection(callback: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val config = getServiceConfig(AIServiceType.DEEPSEEK)
                if (config == null) {
                    callback(false, "API密钥未配置")
                    return@launch
                }
                
                Log.d(TAG, "测试DeepSeek连接")
                Log.d(TAG, "API URL: ${config.apiUrl}")
                Log.d(TAG, "API密钥: ${config.apiKey}")
                
                val url = URL(config.apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                    doOutput = true
                    doInput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                val requestBody = JSONObject().apply {
                    put("model", config.model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Hello")
                        })
                    })
                    put("max_tokens", 10)
                    put("temperature", 0.7)
                }
                
                val requestJson = requestBody.toString()
                Log.d(TAG, "测试请求体: $requestJson")
                
                connection.outputStream.use { os ->
                    val input = requestJson.toByteArray(StandardCharsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "测试响应码: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    callback(true, "连接成功")
                } else {
                    val errorStream = connection.errorStream
                    val errorBody = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "Unknown error"
                    }
                    
                    Log.e(TAG, "测试失败: $responseCode - $errorBody")
                    callback(false, "连接失败: $responseCode - $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "测试连接异常", e)
                callback(false, "连接异常: ${e.message}")
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun destroy() {
        scope.cancel()
    }
}
