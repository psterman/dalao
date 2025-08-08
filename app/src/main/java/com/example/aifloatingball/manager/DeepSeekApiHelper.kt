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

/**
 * DeepSeek API助手
 * 专门用于测试和修复DeepSeek API连接问题
 */
class DeepSeekApiHelper(private val context: Context) {
    
    private val settingsManager = SettingsManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    
    companion object {
        private const val TAG = "DeepSeekApiHelper"
    }
    
    /**
     * 诊断DeepSeek API问题
     */
    fun diagnoseDeepSeekApi(apiKey: String, callback: (DiagnosisResult) -> Unit) {
        scope.launch {
            try {
                Log.d(TAG, "开始诊断DeepSeek API")
                
                val result = DiagnosisResult()
                
                // 1. 检查API密钥格式
                result.apiKeyFormat = checkApiKeyFormat(apiKey)
                
                // 2. 测试网络连接
                result.networkConnection = testNetworkConnection()
                
                // 3. 测试API端点
                result.apiEndpoint = testApiEndpoint()
                
                // 4. 测试认证
                result.authentication = testAuthentication(apiKey)
                
                // 5. 测试模型列表
                result.modelList = testModelList(apiKey)
                
                // 6. 测试聊天完成
                result.chatCompletion = testChatCompletion(apiKey)
                
                // 7. 生成诊断报告
                result.generateReport()
                
                callback(result)
                
            } catch (e: Exception) {
                Log.e(TAG, "诊断DeepSeek API失败", e)
                val errorResult = DiagnosisResult()
                errorResult.error = "诊断过程出错: ${e.message}"
                callback(errorResult)
            }
        }
    }
    
    /**
     * 检查API密钥格式
     */
    private fun checkApiKeyFormat(apiKey: String): ApiKeyFormatResult {
        return try {
            val result = ApiKeyFormatResult()
            
            if (apiKey.isBlank()) {
                result.isValid = false
                result.issues.add("API密钥为空")
                return result
            }
            
            if (!apiKey.startsWith("sk-")) {
                result.isValid = false
                result.issues.add("API密钥格式错误：应该以'sk-'开头")
            }
            
            if (apiKey.length < 20) {
                result.isValid = false
                result.issues.add("API密钥长度不足：至少需要20个字符")
            }
            
            if (apiKey.length > 100) {
                result.isValid = false
                result.issues.add("API密钥长度过长：可能包含多余字符")
            }
            
            // 检查是否包含特殊字符
            val invalidChars = apiKey.filter { !it.isLetterOrDigit() && it != '-' && it != '_' }
            if (invalidChars.isNotEmpty()) {
                result.isValid = false
                result.issues.add("API密钥包含无效字符: $invalidChars")
            }
            
            result.isValid = result.issues.isEmpty()
            result
        } catch (e: Exception) {
            ApiKeyFormatResult().apply {
                isValid = false
                issues.add("检查API密钥格式时出错: ${e.message}")
            }
        }
    }
    
    /**
     * 测试网络连接
     */
    private suspend fun testNetworkConnection(): NetworkConnectionResult {
        return try {
            val result = NetworkConnectionResult()
            
            // 测试基本网络连接 - 使用正确的API端点
            val url = URL("https://api.deepseek.com/v1/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            // 401和403都表示网络连接正常，只是需要认证
            result.isConnected = responseCode in 200..299 || responseCode == 401 || responseCode == 403
            
            if (!result.isConnected) {
                result.issues.add("无法连接到DeepSeek服务器，响应码: $responseCode")
            }
            
            result
        } catch (e: Exception) {
            NetworkConnectionResult().apply {
                isConnected = false
                issues.add("网络连接测试失败: ${e.message}")
            }
        }
    }
    
    /**
     * 测试API端点
     */
    private suspend fun testApiEndpoint(): ApiEndpointResult {
        return try {
            val result = ApiEndpointResult()
            
            val url = URL("https://api.deepseek.com/v1/models")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val responseCode = connection.responseCode
            result.isAccessible = responseCode in 200..299 || responseCode == 401 || responseCode == 403
            
            if (!result.isAccessible) {
                result.issues.add("API端点不可访问，响应码: $responseCode")
            }
            
            result
        } catch (e: Exception) {
            ApiEndpointResult().apply {
                isAccessible = false
                issues.add("API端点测试失败: ${e.message}")
            }
        }
    }
    
    /**
     * 测试认证
     */
    private suspend fun testAuthentication(apiKey: String): AuthenticationResult {
        return try {
            val result = AuthenticationResult()
            
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
            
            when (responseCode) {
                200 -> {
                    result.isValid = true
                    result.message = "认证成功"
                }
                401 -> {
                    result.isValid = false
                    result.message = "认证失败：API密钥无效或已过期"
                    result.suggestions.add("请检查API密钥是否正确")
                    result.suggestions.add("请确认API密钥是否已过期")
                    result.suggestions.add("请确认API密钥是否包含多余的空格或换行符")
                }
                403 -> {
                    result.isValid = false
                    result.message = "权限不足：API密钥可能没有访问权限"
                    result.suggestions.add("请检查您的DeepSeek账户状态")
                    result.suggestions.add("请确认API密钥的权限设置")
                }
                else -> {
                    result.isValid = false
                    result.message = "认证测试失败，响应码: $responseCode"
                }
            }
            
            // 读取错误响应获取更多信息
            if (!result.isValid) {
                val errorStream = connection.errorStream
                if (errorStream != null) {
                    val errorBody = BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    result.errorDetails = errorBody
                    Log.d(TAG, "认证错误详情: $errorBody")
                }
            }
            
            result
        } catch (e: Exception) {
            AuthenticationResult().apply {
                isValid = false
                message = "认证测试异常: ${e.message}"
            }
        }
    }
    
    /**
     * 测试模型列表
     */
    private suspend fun testModelList(apiKey: String): ModelListResult {
        return try {
            val result = ModelListResult()
            
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
            
            if (responseCode == 200) {
                val response = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                
                try {
                    val jsonObject = JSONObject(response)
                    val data = jsonObject.getJSONArray("data")
                    
                    for (i in 0 until data.length()) {
                        val model = data.getJSONObject(i)
                        val modelId = model.getString("id")
                        result.availableModels.add(modelId)
                    }
                    
                    result.isAccessible = true
                    result.message = "成功获取模型列表，可用模型: ${result.availableModels.joinToString(", ")}"
                } catch (e: Exception) {
                    result.isAccessible = false
                    result.message = "解析模型列表失败: ${e.message}"
                }
            } else {
                result.isAccessible = false
                result.message = "获取模型列表失败，响应码: $responseCode"
            }
            
            result
        } catch (e: Exception) {
            ModelListResult().apply {
                isAccessible = false
                message = "测试模型列表异常: ${e.message}"
            }
        }
    }
    
    /**
     * 测试聊天完成
     */
    private suspend fun testChatCompletion(apiKey: String): ChatCompletionResult {
        return try {
            val result = ChatCompletionResult()
            
            val url = URL("https://api.deepseek.com/v1/chat/completions")
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
                put("model", "deepseek-chat")
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
            
            connection.outputStream.use { os ->
                val input = requestJson.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            
            when (responseCode) {
                200 -> {
                    result.isWorking = true
                    result.message = "聊天完成API工作正常"
                }
                400 -> {
                    result.isWorking = false
                    result.message = "请求参数错误"
                    val errorStream = connection.errorStream
                    if (errorStream != null) {
                        val errorBody = BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                        result.errorDetails = errorBody
                    }
                }
                401 -> {
                    result.isWorking = false
                    result.message = "认证失败"
                }
                403 -> {
                    result.isWorking = false
                    result.message = "权限不足"
                }
                else -> {
                    result.isWorking = false
                    result.message = "聊天完成API测试失败，响应码: $responseCode"
                }
            }
            
            result
        } catch (e: Exception) {
            ChatCompletionResult().apply {
                isWorking = false
                message = "聊天完成测试异常: ${e.message}"
            }
        }
    }
    
    /**
     * 测试不同的DeepSeek模型名称
     */
    fun testDeepSeekModels(apiKey: String, callback: (String, Boolean, String) -> Unit) {
        val models = listOf(
            "deepseek-chat",
            "deepseek-chat-33b",
            "deepseek-chat-67b",
            "deepseek-coder",
            "deepseek-coder-33b",
            "deepseek-coder-67b"
        )
        
        models.forEach { model ->
            scope.launch {
                val result = testModel(apiKey, model)
                callback(model, result.first, result.second)
            }
        }
    }
    
    /**
     * 测试DeepSeek API连接（使用您提供的API密钥）
     */
    fun testDeepSeekConnection(apiKey: String, callback: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                Log.d(TAG, "测试DeepSeek API连接")
                Log.d(TAG, "API密钥: $apiKey")
                
                // 测试获取模型列表
                val url = URL("https://api.deepseek.com/v1/models")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                    doInput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "DeepSeek API响应码: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                    Log.d(TAG, "DeepSeek API响应: $response")
                    callback(true, "连接成功")
                } else {
                    val errorStream = connection.errorStream
                    val errorBody = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "Unknown error"
                    }
                    
                    Log.e(TAG, "DeepSeek API连接失败: $responseCode - $errorBody")
                    callback(false, "连接失败: $responseCode - $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "DeepSeek API连接异常", e)
                callback(false, "连接异常: ${e.message}")
            }
        }
    }
    
    /**
     * 测试单个模型
     */
    private suspend fun testModel(apiKey: String, model: String): Pair<Boolean, String> {
        return try {
            Log.d(TAG, "测试模型: $model")
            
            val url = URL("https://api.deepseek.com/v1/chat/completions")
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
            
            val requestJson = requestBody.toString()
            Log.d(TAG, "测试请求体: $requestJson")
            
            connection.outputStream.use { os ->
                val input = requestJson.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "模型 $model 响应码: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Pair(true, "成功")
            } else {
                val errorStream = connection.errorStream
                val errorBody = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                } else {
                    "Unknown error"
                }
                
                Pair(false, "失败: $responseCode - $errorBody")
            }
        } catch (e: Exception) {
            Pair(false, "异常: ${e.message}")
        }
    }
    
    /**
     * 获取可用的模型列表
     */
    fun getAvailableModels(apiKey: String, callback: (Boolean, List<String>, String) -> Unit) {
        scope.launch {
            try {
                val url = URL("https://api.deepseek.com/v1/models")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("User-Agent", "AI-FloatingBall/1.0")
                    doInput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "获取模型列表响应码: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                    Log.d(TAG, "模型列表响应: $response")
                    
                    try {
                        val jsonObject = JSONObject(response)
                        val data = jsonObject.getJSONArray("data")
                        val models = mutableListOf<String>()
                        
                        for (i in 0 until data.length()) {
                            val model = data.getJSONObject(i)
                            val modelId = model.getString("id")
                            models.add(modelId)
                        }
                        
                        callback(true, models, "成功获取模型列表")
                    } catch (e: Exception) {
                        Log.e(TAG, "解析模型列表失败", e)
                        callback(false, emptyList(), "解析失败: ${e.message}")
                    }
                } else {
                    val errorStream = connection.errorStream
                    val errorBody = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream, StandardCharsets.UTF_8)).use { it.readText() }
                    } else {
                        "Unknown error"
                    }
                    
                    Log.e(TAG, "获取模型列表失败: $responseCode - $errorBody")
                    callback(false, emptyList(), "获取失败: $responseCode - $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取模型列表异常", e)
                callback(false, emptyList(), "异常: ${e.message}")
            }
        }
    }
    
    /**
     * 诊断结果数据类
     */
    data class DiagnosisResult(
        var apiKeyFormat: ApiKeyFormatResult = ApiKeyFormatResult(),
        var networkConnection: NetworkConnectionResult = NetworkConnectionResult(),
        var apiEndpoint: ApiEndpointResult = ApiEndpointResult(),
        var authentication: AuthenticationResult = AuthenticationResult(),
        var modelList: ModelListResult = ModelListResult(),
        var chatCompletion: ChatCompletionResult = ChatCompletionResult(),
        var error: String? = null,
        var report: String = ""
    ) {
        fun generateReport() {
            val reportBuilder = StringBuilder()
            reportBuilder.appendLine("=== DeepSeek API 诊断报告 ===")
            reportBuilder.appendLine()
            
            // API密钥格式
            reportBuilder.appendLine("1. API密钥格式检查:")
            if (apiKeyFormat.isValid) {
                reportBuilder.appendLine("   ✓ API密钥格式正确")
            } else {
                reportBuilder.appendLine("   ✗ API密钥格式有问题:")
                apiKeyFormat.issues.forEach { issue ->
                    reportBuilder.appendLine("     - $issue")
                }
            }
            reportBuilder.appendLine()
            
            // 网络连接
            reportBuilder.appendLine("2. 网络连接检查:")
            if (networkConnection.isConnected) {
                reportBuilder.appendLine("   ✓ 网络连接正常")
            } else {
                reportBuilder.appendLine("   ✗ 网络连接有问题:")
                networkConnection.issues.forEach { issue ->
                    reportBuilder.appendLine("     - $issue")
                }
            }
            reportBuilder.appendLine()
            
            // API端点
            reportBuilder.appendLine("3. API端点检查:")
            if (apiEndpoint.isAccessible) {
                reportBuilder.appendLine("   ✓ API端点可访问")
            } else {
                reportBuilder.appendLine("   ✗ API端点不可访问:")
                apiEndpoint.issues.forEach { issue ->
                    reportBuilder.appendLine("     - $issue")
                }
            }
            reportBuilder.appendLine()
            
            // 认证
            reportBuilder.appendLine("4. 认证检查:")
            if (authentication.isValid) {
                reportBuilder.appendLine("   ✓ 认证成功")
            } else {
                reportBuilder.appendLine("   ✗ 认证失败: ${authentication.message}")
                if (authentication.suggestions.isNotEmpty()) {
                    reportBuilder.appendLine("   建议:")
                    authentication.suggestions.forEach { suggestion ->
                        reportBuilder.appendLine("     - $suggestion")
                    }
                }
                if (authentication.errorDetails.isNotEmpty()) {
                    reportBuilder.appendLine("   错误详情: ${authentication.errorDetails}")
                }
            }
            reportBuilder.appendLine()
            
            // 模型列表
            reportBuilder.appendLine("5. 模型列表检查:")
            if (modelList.isAccessible) {
                reportBuilder.appendLine("   ✓ 模型列表可访问")
                reportBuilder.appendLine("   可用模型: ${modelList.availableModels.joinToString(", ")}")
            } else {
                reportBuilder.appendLine("   ✗ 模型列表不可访问: ${modelList.message}")
            }
            reportBuilder.appendLine()
            
            // 聊天完成
            reportBuilder.appendLine("6. 聊天完成API检查:")
            if (chatCompletion.isWorking) {
                reportBuilder.appendLine("   ✓ 聊天完成API工作正常")
            } else {
                reportBuilder.appendLine("   ✗ 聊天完成API有问题: ${chatCompletion.message}")
                if (chatCompletion.errorDetails.isNotEmpty()) {
                    reportBuilder.appendLine("   错误详情: ${chatCompletion.errorDetails}")
                }
            }
            reportBuilder.appendLine()
            
            // 总结和建议
            reportBuilder.appendLine("=== 总结和建议 ===")
            val allIssues = mutableListOf<String>()
            
            if (!apiKeyFormat.isValid) allIssues.add("API密钥格式问题")
            if (!networkConnection.isConnected) allIssues.add("网络连接问题")
            if (!apiEndpoint.isAccessible) allIssues.add("API端点问题")
            if (!authentication.isValid) allIssues.add("认证问题")
            if (!modelList.isAccessible) allIssues.add("模型列表访问问题")
            if (!chatCompletion.isWorking) allIssues.add("聊天完成API问题")
            
            if (allIssues.isEmpty()) {
                reportBuilder.appendLine("✓ 所有检查都通过，DeepSeek API应该可以正常工作")
            } else {
                reportBuilder.appendLine("✗ 发现以下问题:")
                allIssues.forEach { issue ->
                    reportBuilder.appendLine("  - $issue")
                }
                reportBuilder.appendLine()
                reportBuilder.appendLine("建议按以下顺序解决问题:")
                reportBuilder.appendLine("1. 首先解决API密钥格式问题")
                reportBuilder.appendLine("2. 然后解决网络连接问题")
                reportBuilder.appendLine("3. 最后解决认证和API访问问题")
            }
            
            report = reportBuilder.toString()
        }
    }
    
    data class ApiKeyFormatResult(
        var isValid: Boolean = false,
        var issues: MutableList<String> = mutableListOf()
    )
    
    data class NetworkConnectionResult(
        var isConnected: Boolean = false,
        var issues: MutableList<String> = mutableListOf()
    )
    
    data class ApiEndpointResult(
        var isAccessible: Boolean = false,
        var issues: MutableList<String> = mutableListOf()
    )
    
    data class AuthenticationResult(
        var isValid: Boolean = false,
        var message: String = "",
        var suggestions: MutableList<String> = mutableListOf(),
        var errorDetails: String = ""
    )
    
    data class ModelListResult(
        var isAccessible: Boolean = false,
        var message: String = "",
        var availableModels: MutableList<String> = mutableListOf()
    )
    
    data class ChatCompletionResult(
        var isWorking: Boolean = false,
        var message: String = "",
        var errorDetails: String = ""
    )
}
