package com.example.aifloatingball.model

/**
 * AI配置项数据模型
 */
data class AIConfigItem(
    val name: String,                    // AI名称（用于内部标识）
    val displayName: String,             // 显示名称
    val description: String,             // 描述
    val apiKeyKey: String,               // API密钥在SharedPreferences中的键
    val apiUrlKey: String,               // API URL在SharedPreferences中的键
    val defaultApiUrl: String,           // 默认API URL
    val isConfigured: Boolean,           // 是否已配置
    val isCustom: Boolean = false,       // 是否为自定义AI
    val customIndex: Int = -1            // 自定义AI的索引（仅用于自定义AI）
)