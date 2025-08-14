package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine

/**
 * AI页面配置管理器
 * 负责管理不同AI页面的定制配置和API设置
 */
class AIPageConfigManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AIPageConfigManager"
        
        // DeepSeek相关配置
        const val DEEPSEEK_CUSTOM_HTML = "file:///android_asset/deepseek_chat.html"
        const val DEEPSEEK_WEB_URL = "https://chat.deepseek.com/"
        const val DEEPSEEK_CODER_URL = "https://coder.deepseek.com/"
        const val DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        
        // 其他AI配置
        const val CHATGPT_WEB_URL = "https://chat.openai.com/"
        const val CLAUDE_WEB_URL = "https://claude.ai/"
        const val GEMINI_WEB_URL = "https://gemini.google.com/"
    }
    
    private val settingsManager = SettingsManager.getInstance(context)
    
    /**
     * 获取定制的DeepSeek配置列表
     */
    fun getDeepSeekConfigs(): List<AISearchEngine> {
        return listOf(
            // DeepSeek Web版本
            AISearchEngine(
                name = "DeepSeek Chat",
                url = DEEPSEEK_WEB_URL,
                iconResId = R.drawable.ic_deepseek,
                description = "DeepSeek通用对话模型",
                searchUrl = DEEPSEEK_WEB_URL,
                customParams = mapOf(
                    "model" to "deepseek-chat",
                    "temperature" to "0.7"
                )
            ),
            
            // DeepSeek Coder版本
            AISearchEngine(
                name = "DeepSeek Coder",
                url = DEEPSEEK_CODER_URL,
                iconResId = R.drawable.ic_deepseek,
                description = "DeepSeek代码专用模型",
                searchUrl = DEEPSEEK_CODER_URL,
                customParams = mapOf(
                    "model" to "deepseek-coder",
                    "temperature" to "0.1",
                    "max_tokens" to "4096"
                )
            ),
            
            // DeepSeek 自定义HTML版本（使用本地chat.html）
            AISearchEngine(
                name = "DeepSeek (Custom)",
                url = DEEPSEEK_CUSTOM_HTML,
                iconResId = R.drawable.ic_deepseek,
                description = "自定义DeepSeek对话界面",
                searchUrl = DEEPSEEK_CUSTOM_HTML,
                isChatMode = true,
                customParams = mapOf(
                    "api_url" to getDeepSeekApiUrl(),
                    "api_key" to getDeepSeekApiKey(),
                    "model" to "deepseek-chat",
                    "use_custom_html" to "true"
                )
            ),

            // DeepSeek API版本（官方网页）
            AISearchEngine(
                name = "DeepSeek (Web)",
                url = DEEPSEEK_WEB_URL,
                iconResId = R.drawable.ic_deepseek,
                description = "DeepSeek官方网页版",
                searchUrl = DEEPSEEK_WEB_URL,
                isChatMode = false
            ),
            
            // DeepSeek Math版本
            AISearchEngine(
                name = "DeepSeek Math",
                url = DEEPSEEK_WEB_URL,
                iconResId = R.drawable.ic_deepseek,
                description = "DeepSeek数学专用模型",
                searchUrl = DEEPSEEK_WEB_URL,
                customParams = mapOf(
                    "model" to "deepseek-math",
                    "temperature" to "0.1"
                )
            )
        )
    }
    
    /**
     * 获取所有定制AI配置
     */
    fun getAllCustomAIConfigs(): List<AISearchEngine> {
        val configs = mutableListOf<AISearchEngine>()
        
        // 添加DeepSeek配置
        configs.addAll(getDeepSeekConfigs())
        
        // 添加其他AI配置
        configs.addAll(getOtherAIConfigs())
        
        return configs
    }
    
    /**
     * 获取其他AI配置
     */
    private fun getOtherAIConfigs(): List<AISearchEngine> {
        return listOf(
            // ChatGPT配置
            AISearchEngine(
                name = "ChatGPT Plus",
                url = CHATGPT_WEB_URL,
                iconResId = R.drawable.ic_chatgpt,
                description = "ChatGPT Plus版本",
                searchUrl = CHATGPT_WEB_URL,
                customParams = mapOf(
                    "model" to "gpt-4",
                    "temperature" to "0.7"
                )
            ),
            
            // Claude配置
            AISearchEngine(
                name = "Claude Pro",
                url = CLAUDE_WEB_URL,
                iconResId = R.drawable.ic_claude,
                description = "Claude Pro版本",
                searchUrl = CLAUDE_WEB_URL,
                customParams = mapOf(
                    "model" to "claude-3-opus",
                    "temperature" to "0.7"
                )
            )
        )
    }
    
    /**
     * 根据引擎键获取定制配置
     */
    fun getConfigByKey(engineKey: String): AISearchEngine? {
        Log.d(TAG, "查找引擎配置: '$engineKey'")

        // 首先在自定义配置中查找
        val customConfig = getAllCustomAIConfigs().find {
            it.name.lowercase().replace(" ", "_") == engineKey.lowercase() ||
            it.name.lowercase() == engineKey.lowercase()
        }

        if (customConfig != null) {
            Log.d(TAG, "找到自定义配置: ${customConfig.name}")
            return customConfig
        }

        // 如果没找到，在默认AI引擎中查找并检查是否需要特殊处理
        Log.d(TAG, "在默认AI引擎中查找...")
        val defaultEngine = AISearchEngine.DEFAULT_AI_ENGINES.find {
            it.name.lowercase() == engineKey.lowercase()
        }

        if (defaultEngine != null) {
            Log.d(TAG, "找到默认引擎: ${defaultEngine.name}")
            // 对于特定的引擎，返回增强的配置
            return when (defaultEngine.name) {
                "DeepSeek (API)" -> {
                    // 为DeepSeek (API)添加自定义HTML标志
                    AISearchEngine(
                        name = defaultEngine.name,
                        url = defaultEngine.url,
                        iconResId = defaultEngine.iconResId,
                        description = defaultEngine.description,
                        searchUrl = defaultEngine.searchUrl,
                        isChatMode = defaultEngine.isChatMode,
                        isEnabled = defaultEngine.isEnabled,
                        customParams = mapOf(
                            "api_url" to getDeepSeekApiUrl(),
                            "api_key" to getDeepSeekApiKey(),
                            "model" to "deepseek-chat",
                            "use_custom_html" to "true"
                        )
                    )
                }
                "ChatGPT (API)" -> {
                    // 为ChatGPT (API)添加API配置
                    AISearchEngine(
                        name = defaultEngine.name,
                        url = defaultEngine.url,
                        iconResId = defaultEngine.iconResId,
                        description = defaultEngine.description,
                        searchUrl = defaultEngine.searchUrl,
                        isChatMode = defaultEngine.isChatMode,
                        isEnabled = defaultEngine.isEnabled,
                        customParams = mapOf<String, String>(
                            "api_url" to "https://api.openai.com/v1/chat/completions",
                            "api_key" to (settingsManager.getString("chatgpt_api_key", "") ?: ""),
                            "model" to "gpt-3.5-turbo",
                            "use_custom_html" to "false"
                        )
                    )
                }
                else -> {
                    Log.d(TAG, "使用默认引擎配置: ${defaultEngine.name}")
                    defaultEngine
                }
            }
        }

        Log.d(TAG, "未找到匹配的引擎配置: '$engineKey'")
        return null
    }
    
    /**
     * 获取DeepSeek API URL
     */
    private fun getDeepSeekApiUrl(): String {
        return settingsManager.getString("deepseek_api_url", DEEPSEEK_API_URL) ?: DEEPSEEK_API_URL
    }

    /**
     * 获取DeepSeek API Key
     */
    private fun getDeepSeekApiKey(): String {
        return settingsManager.getString("deepseek_api_key", "") ?: ""
    }
    
    /**
     * 构建带参数的URL
     */
    fun buildUrlWithParams(baseUrl: String, params: Map<String, String>): String {
        if (params.isEmpty()) return baseUrl
        
        val urlBuilder = StringBuilder(baseUrl)
        if (!baseUrl.contains("?")) {
            urlBuilder.append("?")
        } else {
            urlBuilder.append("&")
        }
        
        params.entries.forEachIndexed { index, entry ->
            if (index > 0) urlBuilder.append("&")
            urlBuilder.append("${entry.key}=${entry.value}")
        }
        
        return urlBuilder.toString()
    }
    
    /**
     * 验证API配置
     */
    fun validateApiConfig(engineName: String): Boolean {
        return when (engineName.lowercase()) {
            "deepseek (api)" -> {
                val apiKey = getDeepSeekApiKey()
                val apiUrl = getDeepSeekApiUrl()
                apiKey.isNotBlank() && apiUrl.isNotBlank()
            }
            else -> true
        }
    }
    
    /**
     * 获取配置状态信息
     */
    fun getConfigStatus(engineName: String): String {
        return when (engineName.lowercase()) {
            "deepseek (api)" -> {
                if (validateApiConfig(engineName)) {
                    "API配置已设置"
                } else {
                    "需要配置API密钥"
                }
            }
            else -> "配置正常"
        }
    }

    /**
     * 测试方法：打印所有可用的AI引擎配置
     */
    fun debugPrintAllConfigs() {
        Log.d(TAG, "=== 所有自定义AI配置 ===")
        getAllCustomAIConfigs().forEach { config ->
            Log.d(TAG, "名称: ${config.name}, URL: ${config.url}, 参数: ${config.customParams}")
        }

        Log.d(TAG, "=== 所有默认AI引擎 ===")
        AISearchEngine.DEFAULT_AI_ENGINES.forEach { engine ->
            Log.d(TAG, "名称: ${engine.name}, URL: ${engine.url}, isChatMode: ${engine.isChatMode}")
        }
    }
}
