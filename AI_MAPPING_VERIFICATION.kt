/**
 * AI服务映射一致性验证脚本
 * 用于验证灵动岛和简易模式中的AI服务类型映射是否一致
 */

// 模拟灵动岛中的AI服务映射
fun getIslandAIContactId(serviceType: String): String {
    val aiName = when (serviceType) {
        "DEEPSEEK" -> "DeepSeek"
        "CHATGPT" -> "ChatGPT"
        "CLAUDE" -> "Claude"
        "GEMINI" -> "Gemini"
        "ZHIPU_AI" -> "智谱AI"
        "WENXIN" -> "文心一言"
        "QIANWEN" -> "通义千问"
        "XINGHUO" -> "讯飞星火"
        "KIMI" -> "Kimi"
        else -> serviceType
    }
    return "ai_${aiName.lowercase().replace(" ", "_")}"
}

// 模拟简易模式中的AI服务类型识别
fun getSimpleModeAIServiceType(contactName: String): String? {
    return when (contactName.lowercase()) {
        "chatgpt", "gpt" -> "CHATGPT"
        "claude" -> "CLAUDE"
        "gemini" -> "GEMINI"
        "文心一言", "wenxin" -> "WENXIN"
        "deepseek" -> "DEEPSEEK"
        "通义千问", "qianwen" -> "QIANWEN"
        "讯飞星火", "xinghuo" -> "XINGHUO"
        "kimi" -> "KIMI"
        "智谱ai", "智谱清言", "zhipu", "glm" -> "ZHIPU_AI"
        else -> null
    }
}

// 验证函数
fun verifyAIMapping() {
    val aiServices = listOf(
        "DEEPSEEK" to "DeepSeek",
        "CHATGPT" to "ChatGPT", 
        "CLAUDE" to "Claude",
        "GEMINI" to "Gemini",
        "ZHIPU_AI" to "智谱AI",
        "WENXIN" to "文心一言",
        "QIANWEN" to "通义千问",
        "XINGHUO" to "讯飞星火",
        "KIMI" to "Kimi"
    )
    
    println("AI服务映射一致性验证")
    println("==================")
    
    aiServices.forEach { (serviceType, displayName) ->
        val islandId = getIslandAIContactId(serviceType)
        val simpleModeType = getSimpleModeAIServiceType(displayName)
        
        println("服务类型: $serviceType")
        println("  显示名称: $displayName")
        println("  灵动岛ID: $islandId")
        println("  简易模式识别: $simpleModeType")
        println("  一致性: ${if (simpleModeType == serviceType) "✅ 匹配" else "❌ 不匹配"}")
        println()
    }
}

// 测试特定AI服务
fun testSpecificAIServices() {
    val testServices = listOf("DEEPSEEK", "KIMI", "ZHIPU_AI", "CHATGPT", "CLAUDE", "GEMINI")
    
    println("重点AI服务测试")
    println("=============")
    
    testServices.forEach { serviceType ->
        val islandId = getIslandAIContactId(serviceType)
        val displayName = when (serviceType) {
            "DEEPSEEK" -> "DeepSeek"
            "CHATGPT" -> "ChatGPT"
            "CLAUDE" -> "Claude"
            "GEMINI" -> "Gemini"
            "ZHIPU_AI" -> "智谱AI"
            "KIMI" -> "Kimi"
            else -> serviceType
        }
        val simpleModeType = getSimpleModeAIServiceType(displayName)
        
        println("$serviceType:")
        println("  灵动岛ID: $islandId")
        println("  简易模式类型: $simpleModeType")
        println("  状态: ${if (simpleModeType == serviceType) "✅ 正常" else "❌ 异常"}")
        println()
    }
}

// 主函数
fun main() {
    verifyAIMapping()
    testSpecificAIServices()
}

