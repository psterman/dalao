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
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier
import java.security.cert.X509Certificate

// AI服务类型
enum class AIServiceType {
    CHATGPT,
    CLAUDE,
    GEMINI,
    WENXIN,
    DEEPSEEK,
    QIANWEN,
    XINGHUO,
    KIMI,
    ZHIPU_AI,
    TEMP_SERVICE
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
        
        // 创建信任所有证书的SSL上下文
        private fun createTrustAllSSLContext(): SSLContext {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            return sslContext
        }
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
                val apiUrl = getStringSetting("kimi_api_url", "https://api.moonshot.cn/v1/chat/completions")
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
            AIServiceType.ZHIPU_AI -> {
                val apiKey = getStringSetting("zhipu_ai_api_key", "")
                val apiUrl = getStringSetting("zhipu_ai_api_url", "https://open.bigmodel.cn/api/paas/v4/chat/completions")
                if (apiKey.isNotBlank()) {
                    AIServiceConfig(
                        type = type,
                        name = "智谱AI",
                        apiUrl = apiUrl,
                        apiKey = apiKey,
                        model = "glm-4"
                    )
                } else null
            }
            AIServiceType.TEMP_SERVICE -> {
                // 临时专线不需要API密钥，直接返回配置
                AIServiceConfig(
                    type = type,
                    name = "临时专线",
                    apiUrl = "https://818233.xyz/",
                    apiKey = "", // 临时专线不需要API密钥
                    model = "gpt-oss-20b"
                )
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
        Log.d(TAG, "AIApiManager.sendMessage 被调用")
        Log.d(TAG, "服务类型: ${serviceType.name}")
        Log.d(TAG, "消息长度: ${message.length}")
        Log.d(TAG, "对话历史长度: ${conversationHistory.size}")
        
        val config = getServiceConfig(serviceType)
        if (config == null) {
            Log.e(TAG, "API配置获取失败，服务类型: ${serviceType.name}")
            callback.onError("API密钥未配置")
            return
        }
        
        Log.d(TAG, "API配置获取成功: ${config.name}, URL: ${config.apiUrl}")
        
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
                    AIServiceType.ZHIPU_AI -> {
                        Log.d(TAG, "调用智谱AI API")
                        sendToZhupu(config, message, conversationHistory, callback)
                    }
                    AIServiceType.TEMP_SERVICE -> {
                        Log.d(TAG, "调用临时专线API")
                        sendToTempService(config, message, conversationHistory, callback)
                    }
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
                
                // 根据错误码提供更具体的错误信息和解决建议
                val errorMessage = when (responseCode) {
                    400 -> {
                        // 解析错误详情
                        val detailMessage = try {
                            val jsonObject = JSONObject(errorBody)
                            val error = jsonObject.optJSONObject("error")
                            error?.optString("message") ?: "请求参数错误"
                        } catch (e: Exception) {
                            "请求参数错误"
                        }
                        "请求参数错误：$detailMessage"
                    }
                    401 -> {
                        // 解析具体的认证错误
                        val detailMessage = try {
                            val jsonObject = JSONObject(errorBody)
                            val error = jsonObject.optJSONObject("error")
                            error?.optString("message") ?: "API密钥无效"
                        } catch (e: Exception) {
                            "API密钥无效"
                        }
                        "认证失败：$detailMessage"
                    }
                    403 -> {
                        val detailMessage = try {
                            val jsonObject = JSONObject(errorBody)
                            val error = jsonObject.optJSONObject("error")
                            error?.optString("message") ?: "权限不足"
                        } catch (e: Exception) {
                            "权限不足"
                        }
                        "权限不足：$detailMessage"
                    }
                    429 -> {
                        val detailMessage = try {
                            val jsonObject = JSONObject(errorBody)
                            val error = jsonObject.optJSONObject("error")
                            error?.optString("message") ?: "请求频率过高"
                        } catch (e: Exception) {
                            "请求频率过高"
                        }
                        "请求频率限制：$detailMessage"
                    }
                    500, 502, 503, 504 -> {
                        "DeepSeek服务器暂时不可用（错误码：$responseCode），请稍后重试"
                    }
                    else -> {
                        // 尝试解析其他错误
                        val detailMessage = try {
                            val jsonObject = JSONObject(errorBody)
                            val error = jsonObject.optJSONObject("error")
                            error?.optString("message") ?: errorBody
                        } catch (e: Exception) {
                            errorBody
                        }
                        "DeepSeek API错误（$responseCode）：$detailMessage"
                    }
                }

                callback.onError(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek请求异常", e)

            // 根据异常类型提供更具体的错误信息
            val errorMessage = when {
                e is java.net.UnknownHostException -> {
                    "网络连接失败，请检查网络连接或DNS设置"
                }
                e is java.net.SocketTimeoutException -> {
                    "请求超时，请检查网络连接或稍后重试"
                }
                e is java.net.ConnectException -> {
                    "无法连接到DeepSeek服务器，请检查网络连接"
                }
                e is javax.net.ssl.SSLException -> {
                    "SSL连接失败，请检查网络安全设置"
                }
                e.message?.contains("JSON") == true -> {
                    "服务器响应格式错误，请稍后重试"
                }
                else -> {
                    "DeepSeek请求失败：${e.message ?: "未知错误"}"
                }
            }

            callback.onError(errorMessage)
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
        try {
            Log.d(TAG, "开始发送Kimi请求")
            Log.d(TAG, "API URL: ${config.apiUrl}")
            Log.d(TAG, "模型: ${config.model}")
            Log.d(TAG, "API密钥长度: ${config.apiKey.length}")
            Log.d(TAG, "API密钥前10位: ${config.apiKey.take(10)}...")
            
            val url = URL(config.apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            // 如果是HTTPS连接，配置SSL信任
            if (connection is HttpsURLConnection) {
                val sslContext = createTrustAllSSLContext()
                connection.sslSocketFactory = sslContext.socketFactory
                connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            
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
            Log.d(TAG, "Kimi响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                var fullResponse = ""
                var line: String?
                var lineCount = 0
                
                while (reader.readLine().also { line = it } != null) {
                    lineCount++
                    Log.d(TAG, "Kimi响应行 $lineCount: $line")
                    
                    if (line!!.startsWith("data: ")) {
                        val jsonString = line!!.substring(6)
                        Log.d(TAG, "解析JSON: $jsonString")
                        
                        if (jsonString.trim() == "[DONE]") {
                            Log.d(TAG, "收到结束标记")
                            break
                        }
                        
                        try {
                            val jsonObject = JSONObject(jsonString)
                            Log.d(TAG, "JSON对象: $jsonObject")
                            
                            if (jsonObject.has("choices")) {
                                val choices = jsonObject.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    Log.d(TAG, "Choice对象: $choice")
                                    
                                    if (choice.has("delta")) {
                                        val delta = choice.getJSONObject("delta")
                                        Log.d(TAG, "Delta对象: $delta")
                                        
                                        if (delta.has("content")) {
                                            val contentChunk = delta.getString("content")
                                            Log.d(TAG, "内容块: '$contentChunk'")
                                            fullResponse += contentChunk
                                            callback.onChunkReceived(contentChunk)
                                        } else {
                                            Log.d(TAG, "Delta中没有content字段")
                                        }
                                    } else {
                                        Log.d(TAG, "Choice中没有delta字段")
                                    }
                                } else {
                                    Log.d(TAG, "Choices数组为空")
                                }
                            } else {
                                Log.d(TAG, "JSON中没有choices字段")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "解析Kimi流式响应时出错: ${e.message}")
                            Log.w(TAG, "原始JSON: $jsonString")
                        }
                    } else if (line!!.isNotEmpty()) {
                        Log.d(TAG, "非data行: $line")
                    }
                }
                reader.close()
                Log.d(TAG, "Kimi完整响应: '$fullResponse'")
                callback.onComplete(fullResponse)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Kimi API错误: $responseCode - $errorBody")
                callback.onError("Kimi API错误: $responseCode - $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kimi请求异常", e)
            val errorMessage = when {
                e is java.net.SocketTimeoutException -> {
                    "Kimi请求超时，请检查网络连接"
                }
                e is java.net.ConnectException -> {
                    "无法连接到Kimi服务器，请检查网络连接"
                }
                e is javax.net.ssl.SSLException -> {
                    "SSL连接失败，请检查网络安全设置"
                }
                e.message?.contains("JSON") == true -> {
                    "服务器响应格式错误，请稍后重试"
                }
                else -> {
                    "Kimi请求失败：${e.message ?: "未知错误"}"
                }
            }
            
            callback.onError(errorMessage)
        }
    }

    /**
     * 发送到智谱AI
     */
    private suspend fun sendToTempService(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        try {
            // 临时专线使用GET请求，将问题直接拼接到URL路径中
            // 按照服务介绍：https://818233.xyz/问题内容
            val processedMessage = processMessageForTempService(message)
            val url = URL("${config.apiUrl}$processedMessage")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                connectTimeout = 15000
                readTimeout = 45000
            }
            
            Log.d(TAG, "临时专线请求URL: $url")
            Log.d(TAG, "原始消息: $message")
            Log.d(TAG, "处理后消息: $processedMessage")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val response = inputStream.bufferedReader().use { it.readText() }
                
                Log.d(TAG, "临时专线响应成功，长度: ${response.length}")
                Log.d(TAG, "临时专线响应内容: ${response.take(300)}...")
                
                // 处理响应文本，实现流式回复并去除广告
                processTempServiceResponseStreaming(response, callback)
            } else {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.e(TAG, "临时专线请求失败: $responseCode, $errorResponse")
                callback.onError("请求失败: HTTP $responseCode")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "临时专线API调用失败", e)
            callback.onError("临时专线服务暂时不可用: ${e.message}")
        }
    }
    
    /**
     * 处理消息以适配临时专线服务
     * 按照服务介绍：空格用+替换，+用++替换，/用//替换
     */
    private fun processMessageForTempService(message: String): String {
        return try {
            // 先处理特殊字符，避免冲突
            val processed = message
                .replace("+", "++")  // + 用 ++ 替换
                .replace("/", "//")  // / 用 // 替换
                .replace(" ", "+")   // 空格用 + 替换
            
            // URL编码处理其他特殊字符
            java.net.URLEncoder.encode(processed, "UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "处理临时专线消息失败", e)
            // 如果处理失败，使用简单的空格替换
            message.replace(" ", "+")
        }
    }

    /**
     * 处理临时专线响应文本，实现流式回复并去除广告
     */
    private fun processTempServiceResponseStreaming(response: String, callback: StreamingCallback) {
        try {
            Log.d(TAG, "开始处理临时专线流式响应，原始长度: ${response.length}")
            
            // 提取AI回复内容
            val aiResponse = extractAIResponseFromHtml(response)
            
            // 如果提取失败，尝试简单的HTML清理
            val cleanedResponse = if (aiResponse.isNotEmpty()) {
                aiResponse
            } else {
                // 移除HTML标签和多余的空白字符
                response
                    .replace(Regex("<[^>]*>"), "") // 移除HTML标签
                    .replace(Regex("\\s+"), " ") // 合并多个空白字符
                    .trim()
            }
            
            Log.d(TAG, "清理后响应长度: ${cleanedResponse.length}")
            Log.d(TAG, "清理后响应内容: ${cleanedResponse.take(200)}...")
            
            // 如果响应为空或太短，返回默认消息
            if (cleanedResponse.isEmpty() || cleanedResponse.length < 3) {
                callback.onComplete("抱歉，临时专线服务暂时无法提供回复，请稍后再试。")
                return
            }
            
            // 去除广告文本
            val adFreeResponse = removeAdvertisementText(cleanedResponse)
            
            // 格式化响应文本
            val formattedResponse = formatResponseText(adFreeResponse)
            
            // 限制响应长度，避免过长的回复
            val maxLength = 2000
            val finalResponse = if (formattedResponse.length > maxLength) {
                formattedResponse.take(maxLength) + "..."
            } else {
                formattedResponse
            }
            
            // 模拟流式回复，将文本分块发送
            simulateStreamingResponse(finalResponse, callback)
            
        } catch (e: Exception) {
            Log.e(TAG, "处理临时专线流式响应失败", e)
            callback.onError("抱歉，处理回复时出现错误，请稍后再试。")
        }
    }

    /**
     * 去除广告文本
     */
    private fun removeAdvertisementText(text: String): String {
        return try {
            // 去除常见的广告文本模式
            val adPatterns = listOf(
                Regex("LLM from URL.*?818233\\.xyz.*?free service.*", RegexOption.DOT_MATCHES_ALL),
                Regex("LLM from URL.*?A free AI chat completion service.*", RegexOption.DOT_MATCHES_ALL),
                Regex("LLM from URL.*?https://818233\\.xyz.*", RegexOption.DOT_MATCHES_ALL),
                Regex("free service.*?free as in freedom.*", RegexOption.DOT_MATCHES_ALL),
                Regex("buymeacoffee\\.com.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Contact:.*?hi@818233\\.xyz.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Disclaimer:.*?not guaranteed.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Privacy policy:.*?NEVER be stored.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Fair use policy:.*?IP ban.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Usage:.*?web browser.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Example:.*?wget.*?curl.*", RegexOption.DOT_MATCHES_ALL),
                Regex("LLM in use:.*?gpt-oss-20b.*", RegexOption.DOT_MATCHES_ALL),
                Regex("Limit:.*?No chat history.*", RegexOption.DOT_MATCHES_ALL)
            )
            
            var cleanedText = text
            for (pattern in adPatterns) {
                cleanedText = cleanedText.replace(pattern, "")
            }
            
            // 清理多余的空行和空白字符
            cleanedText
                .replace(Regex("\\n\\s*\\n\\s*\\n"), "\n\n") // 合并多个空行
                .replace(Regex("\\s+"), " ") // 合并多个空格
                .trim()
                
        } catch (e: Exception) {
            Log.e(TAG, "去除广告文本失败", e)
            text
        }
    }

    /**
     * 模拟流式回复
     */
    private fun simulateStreamingResponse(text: String, callback: StreamingCallback) {
        try {
            // 将文本按句子分割，实现更自然的流式效果
            val sentences = text.split(Regex("[。！？\\.!?]"))
            var fullResponse = ""
            
            for (i in sentences.indices) {
                val sentence = sentences[i].trim()
                if (sentence.isNotEmpty()) {
                    // 添加标点符号（除了最后一个句子）
                    val chunk = if (i < sentences.size - 1) {
                        sentence + if (text.contains(sentence + "。")) "。" else if (text.contains(sentence + "！")) "！" else if (text.contains(sentence + "？")) "？" else "。"
                    } else {
                        sentence
                    }
                    
                    fullResponse += chunk
                    
                    // 发送流式数据
                    callback.onChunkReceived(chunk)
                    
                    // 添加小延迟，模拟真实流式效果
                    Thread.sleep(50)
                }
            }
            
            // 完成流式回复
            callback.onComplete(fullResponse)
            
        } catch (e: Exception) {
            Log.e(TAG, "模拟流式回复失败", e)
            callback.onComplete(text)
        }
    }

    /**
     * 处理临时专线响应文本（保留原方法用于兼容）
     */
    private fun processTempServiceResponse(response: String): String {
        try {
            Log.d(TAG, "开始处理临时专线响应，原始长度: ${response.length}")
            
            // 提取AI回复内容
            val aiResponse = extractAIResponseFromHtml(response)
            
            // 如果提取失败，尝试简单的HTML清理
            val cleanedResponse = if (aiResponse.isNotEmpty()) {
                aiResponse
            } else {
            // 移除HTML标签和多余的空白字符
                response
                .replace(Regex("<[^>]*>"), "") // 移除HTML标签
                .replace(Regex("\\s+"), " ") // 合并多个空白字符
                .trim()
            }
            
            Log.d(TAG, "清理后响应长度: ${cleanedResponse.length}")
            Log.d(TAG, "清理后响应内容: ${cleanedResponse.take(200)}...")
            
            // 如果响应为空或太短，返回默认消息
            if (cleanedResponse.isEmpty() || cleanedResponse.length < 3) {
                return "抱歉，临时专线服务暂时无法提供回复，请稍后再试。"
            }
            
            // 去除广告文本
            val adFreeResponse = removeAdvertisementText(cleanedResponse)
            
            // 格式化响应文本
            val formattedResponse = formatResponseText(adFreeResponse)
            
            // 限制响应长度，避免过长的回复
            val maxLength = 2000
            return if (formattedResponse.length > maxLength) {
                formattedResponse.take(maxLength) + "..."
            } else {
                formattedResponse
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理临时专线响应失败", e)
            return "抱歉，处理回复时出现错误，请稍后再试。"
        }
    }

    /**
     * 从HTML响应中提取AI回复内容
     */
    private fun extractAIResponseFromHtml(html: String): String {
        return try {
            // 尝试提取主要内容区域
            val patterns = listOf(
                Regex("<main[^>]*>(.*?)</main>", RegexOption.DOT_MATCHES_ALL),
                Regex("<div[^>]*class=\"[^\"]*content[^\"]*\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL),
                Regex("<div[^>]*class=\"[^\"]*response[^\"]*\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL),
                Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
            )
            
            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val content = match.groupValues[1]
                    if (content.length > 10) { // 确保内容足够长
                        return content
                            .replace(Regex("<[^>]*>"), "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                }
            }
            
            // 如果没有找到特定模式，返回空字符串
            ""
        } catch (e: Exception) {
            Log.e(TAG, "提取AI响应失败", e)
            ""
        }
    }

    /**
     * 格式化响应文本
     */
    private fun formatResponseText(text: String): String {
        return try {
            // 基本的文本格式化
            text
                .replace(Regex("\\n\\s*\\n"), "\n\n") // 合并多个空行
                .replace(Regex("\\s+"), " ") // 合并多个空格
                .trim()
        } catch (e: Exception) {
            Log.e(TAG, "格式化响应文本失败", e)
            text
        }
    }

    private suspend fun sendToZhupu(
        config: AIServiceConfig,
        message: String,
        conversationHistory: List<Map<String, String>>,
        callback: StreamingCallback
    ) {
        try {
            Log.d(TAG, "开始发送智谱AI请求")
            Log.d(TAG, "API URL: ${config.apiUrl}")
            Log.d(TAG, "模型: ${config.model}")
            Log.d(TAG, "API密钥长度: ${config.apiKey.length}")
            Log.d(TAG, "API密钥前10位: ${config.apiKey.take(10)}...")
            
            val url = URL(config.apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            // 如果是HTTPS连接，配置SSL信任
            if (connection is HttpsURLConnection) {
                val sslContext = createTrustAllSSLContext()
                connection.sslSocketFactory = sslContext.socketFactory
                connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            
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
                // 智谱AI特定参数
                put("top_p", 0.7)
                put("incremental", true)
            }
            
            val requestJson = requestBody.toString()
            Log.d(TAG, "请求体: $requestJson")
            
            connection.outputStream.use { os ->
                val input = requestJson.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "智谱AI响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                var fullResponse = ""
                var line: String?
                var lineCount = 0
                
                while (reader.readLine().also { line = it } != null) {
                    lineCount++
                    Log.d(TAG, "智谱AI响应行 $lineCount: $line")
                    
                    if (line!!.startsWith("data: ")) {
                        val jsonString = line!!.substring(6)
                        Log.d(TAG, "解析JSON: $jsonString")
                        
                        if (jsonString.trim() == "[DONE]") {
                            Log.d(TAG, "收到结束标记")
                            break
                        }
                        
                        try {
                            val jsonObject = JSONObject(jsonString)
                            Log.d(TAG, "JSON对象: $jsonObject")
                            
                            if (jsonObject.has("choices")) {
                                val choices = jsonObject.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    Log.d(TAG, "Choice对象: $choice")
                                    
                                    if (choice.has("delta")) {
                                        val delta = choice.getJSONObject("delta")
                                        Log.d(TAG, "Delta对象: $delta")
                                        
                                        if (delta.has("content")) {
                                            val contentChunk = delta.getString("content")
                                            Log.d(TAG, "内容块: '$contentChunk'")
                                            fullResponse += contentChunk
                                            callback.onChunkReceived(contentChunk)
                                        } else {
                                            Log.d(TAG, "Delta中没有content字段")
                                        }
                                    } else {
                                        Log.d(TAG, "Choice中没有delta字段")
                                    }
                                } else {
                                    Log.d(TAG, "Choices数组为空")
                                }
                            } else {
                                Log.d(TAG, "JSON中没有choices字段")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "解析智谱AI流式响应时出错: ${e.message}")
                            Log.w(TAG, "原始JSON: $jsonString")
                        }
                    } else if (line!!.isNotEmpty()) {
                        Log.d(TAG, "非data行: $line")
                    }
                }
                reader.close()
                Log.d(TAG, "智谱AI完整响应: '$fullResponse'")
                callback.onComplete(fullResponse)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "智谱AI API错误: $responseCode - $errorBody")
                callback.onError("智谱AI API错误: $responseCode - $errorBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "智谱AI请求异常", e)
            val errorMessage = when {
                e is java.net.SocketTimeoutException -> {
                    "智谱AI请求超时，请检查网络连接"
                }
                e is java.net.ConnectException -> {
                    "无法连接到智谱AI服务器，请检查网络连接"
                }
                e is javax.net.ssl.SSLException -> {
                    "SSL连接失败，请检查网络安全设置"
                }
                e.message?.contains("JSON") == true -> {
                    "服务器响应格式错误，请稍后重试"
                }
                else -> {
                    "智谱AI请求失败：${e.message ?: "未知错误"}"
                }
            }
            
            callback.onError(errorMessage)
        }
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
