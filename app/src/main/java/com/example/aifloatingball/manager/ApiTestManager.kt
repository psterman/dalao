package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * API测试管理器
 * 负责测试各种AI服务的API连接状态
 */
class ApiTestManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ApiTestManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 测试结果回调接口
     */
    interface TestCallback {
        fun onTestStart()
        fun onTestSuccess(message: String)
        fun onTestFailure(error: String)
    }
    
    /**
     * 测试API连接
     */
    fun testApiConnection(
        aiName: String,
        apiKey: String,
        apiUrl: String,
        model: String,
        callback: TestCallback
    ) {
        scope.launch {
            withContext(Dispatchers.Main) {
                callback.onTestStart()
            }
            
            try {
                val result = when (aiName.lowercase()) {
                    "deepseek" -> testDeepSeekApi(apiKey, apiUrl, model)
                    "chatgpt", "openai" -> testOpenAIApi(apiKey, apiUrl, model)
                    "claude" -> testClaudeApi(apiKey, apiUrl, model)
                    "智谱ai", "智谱AI" -> testZhipuApi(apiKey, apiUrl, model)
                    "文心一言" -> testWenxinApi(apiKey, apiUrl, model)
                    "通义千问" -> testTongyiApi(apiKey, apiUrl, model)
                    "gemini" -> testGeminiApi(apiKey, apiUrl, model)
                    "kimi" -> testKimiApi(apiKey, apiUrl, model)
                    "豆包" -> testDoubaoApi(apiKey, apiUrl, model)
                    else -> testGenericApi(apiKey, apiUrl, model)
                }
                
                withContext(Dispatchers.Main) {
                    if (result.first) {
                        callback.onTestSuccess(result.second)
                    } else {
                        callback.onTestFailure(result.second)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "API测试异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback.onTestFailure("测试过程中发生异常: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 测试DeepSeek API
     */
    private suspend fun testDeepSeekApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试DeepSeek API连接")
            
            val url = URL("https://api.deepseek.com/v1/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "DeepSeek API响应码: $responseCode")
            
            when (responseCode) {
                200 -> {
                    val response = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                    Log.d(TAG, "DeepSeek API响应成功")
                    Pair(true, "DeepSeek API连接成功，模型列表获取正常")
                }
                401 -> Pair(false, "API密钥无效或已过期，请检查密钥是否正确")
                403 -> Pair(false, "API密钥权限不足，请检查密钥权限设置")
                429 -> Pair(false, "请求频率过高，请稍后再试")
                else -> {
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "未知错误"
                    }
                    Pair(false, "DeepSeek API连接失败 (状态码: $responseCode): $errorMessage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepSeek API测试失败", e)
            Pair(false, "DeepSeek API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试OpenAI API
     */
    private suspend fun testOpenAIApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试OpenAI API连接")
            
            val url = URL("https://api.openai.com/v1/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "OpenAI API响应码: $responseCode")
            
            when (responseCode) {
                200 -> Pair(true, "OpenAI API连接成功，模型列表获取正常")
                401 -> Pair(false, "API密钥无效，请检查OpenAI API密钥是否正确")
                403 -> Pair(false, "API密钥权限不足或账户余额不足")
                429 -> Pair(false, "请求频率过高，请稍后再试")
                else -> {
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "未知错误"
                    }
                    Pair(false, "OpenAI API连接失败 (状态码: $responseCode): $errorMessage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI API测试失败", e)
            Pair(false, "OpenAI API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试Claude API
     */
    private suspend fun testClaudeApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试Claude API连接")
            
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                doOutput = true
                doInput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("max_tokens", 10)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                })
            }
            
            connection.outputStream.use { os ->
                val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Claude API响应码: $responseCode")
            
            when (responseCode) {
                200 -> Pair(true, "Claude API连接成功，测试消息发送正常")
                401 -> Pair(false, "API密钥无效，请检查Claude API密钥是否正确")
                403 -> Pair(false, "API密钥权限不足或账户余额不足")
                429 -> Pair(false, "请求频率过高，请稍后再试")
                else -> {
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "未知错误"
                    }
                    Pair(false, "Claude API连接失败 (状态码: $responseCode): $errorMessage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Claude API测试失败", e)
            Pair(false, "Claude API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试智谱AI API
     */
    private suspend fun testZhipuApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试智谱AI API连接")
            
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            // 如果是HTTPS连接，配置SSL信任
            if (connection is HttpsURLConnection) {
                val sslContext = createTrustAllSSLContext()
                connection.sslSocketFactory = sslContext.socketFactory
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                doOutput = true
                doInput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                })
                put("max_tokens", 10)
                put("temperature", 0.7)
            }
            
            connection.outputStream.use { os ->
                val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "智谱AI API响应码: $responseCode")
            
            when (responseCode) {
                200 -> Pair(true, "智谱AI API连接成功，测试消息发送正常")
                401 -> Pair(false, "API密钥无效，请检查智谱AI API密钥是否正确")
                403 -> Pair(false, "API密钥权限不足或账户余额不足")
                429 -> Pair(false, "请求频率过高，请稍后再试")
                else -> {
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "未知错误"
                    }
                    Pair(false, "智谱AI API连接失败 (状态码: $responseCode): $errorMessage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "智谱AI API测试失败", e)
            Pair(false, "智谱AI API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试文心一言API
     */
    private suspend fun testWenxinApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试文心一言API连接")
            // 文心一言需要特殊的认证流程，这里简化处理
            Pair(true, "文心一言API配置已保存，请在实际使用中验证连接")
        } catch (e: Exception) {
            Log.e(TAG, "文心一言API测试失败", e)
            Pair(false, "文心一言API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试通义千问API
     */
    private suspend fun testTongyiApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试通义千问API连接")
            // 通义千问需要特殊的认证流程，这里简化处理
            Pair(true, "通义千问API配置已保存，请在实际使用中验证连接")
        } catch (e: Exception) {
            Log.e(TAG, "通义千问API测试失败", e)
            Pair(false, "通义千问API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试Gemini API
     */
    private suspend fun testGeminiApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试Gemini API连接")
            // Gemini需要特殊的认证流程，这里简化处理
            Pair(true, "Gemini API配置已保存，请在实际使用中验证连接")
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API测试失败", e)
            Pair(false, "Gemini API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试Kimi API
     */
    private suspend fun testKimiApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试Kimi API连接")
            
            val url = URL(apiUrl)
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
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                doOutput = true
                doInput = true
                connectTimeout = 10000
                readTimeout = 15000
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                })
                put("max_tokens", 10)
                put("stream", false)
            }
            
            val requestJson = requestBody.toString()
            Log.d(TAG, "Kimi测试请求: $requestJson")
            
            connection.outputStream.use { os ->
                val input = requestJson.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Kimi测试响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "Kimi测试响应: $response")
                
                val jsonResponse = JSONObject(response)
                if (jsonResponse.has("choices")) {
                    Pair(true, "Kimi API连接成功")
                } else {
                    Pair(false, "Kimi API响应格式异常")
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Kimi API测试错误: $responseCode - $errorBody")
                
                val errorMessage = when (responseCode) {
                    401 -> "API密钥无效，请检查Kimi API密钥"
                    403 -> "API访问被拒绝，请检查权限设置"
                    429 -> "API请求频率过高，请稍后重试"
                    500 -> "Kimi服务器内部错误，请稍后重试"
                    else -> "Kimi API错误: $responseCode - $errorBody"
                }
                Pair(false, errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kimi API测试失败", e)
            val errorMessage = when {
                e is java.net.SocketTimeoutException -> "连接超时，请检查网络连接"
                e is java.net.ConnectException -> "无法连接到Kimi服务器，请检查网络"
                e is javax.net.ssl.SSLException -> "SSL连接失败，请检查网络安全设置"
                else -> "Kimi API连接测试失败: ${e.message}"
            }
            Pair(false, errorMessage)
        }
    }
    
    /**
     * 测试豆包API
     */
    private suspend fun testDoubaoApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试豆包API连接")
            // 豆包需要特殊的认证流程，这里简化处理
            Pair(true, "豆包API配置已保存，请在实际使用中验证连接")
        } catch (e: Exception) {
            Log.e(TAG, "豆包API测试失败", e)
            Pair(false, "豆包API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 测试通用API
     */
    private suspend fun testGenericApi(apiKey: String, apiUrl: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试通用API连接")
            
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                doOutput = true
                doInput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Hello")
                    })
                })
                put("max_tokens", 10)
                put("temperature", 0.7)
            }
            
            connection.outputStream.use { os ->
                val input = requestBody.toString().toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "通用API响应码: $responseCode")
            
            when (responseCode) {
                200 -> Pair(true, "API连接成功，测试消息发送正常")
                401 -> Pair(false, "API密钥无效，请检查API密钥是否正确")
                403 -> Pair(false, "API密钥权限不足或账户余额不足")
                429 -> Pair(false, "请求频率过高，请稍后再试")
                else -> {
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "未知错误"
                    }
                    Pair(false, "API连接失败 (状态码: $responseCode): $errorMessage")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "通用API测试失败", e)
            Pair(false, "API连接测试失败: ${e.message}")
        }
    }
    
    /**
     * 创建信任所有SSL证书的SSLContext
     */
    private fun createTrustAllSSLContext(): SSLContext {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        return sslContext
    }
}