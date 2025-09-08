// ID映射调试脚本
fun main() {
    println("=== AI服务ID映射调试 ===")
    
    // 模拟灵动岛中的ID生成
    fun getDynamicIslandId(serviceType: String): String {
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
    
    // 模拟简易模式中的ID生成
    fun getSimpleModeId(aiName: String): String {
        return "ai_${aiName.lowercase().replace(" ", "_")}"
    }
    
    // 测试所有AI服务
    val aiServices = listOf(
        "DEEPSEEK" to "DeepSeek",
        "KIMI" to "Kimi", 
        "ZHIPU_AI" to "智谱AI",
        "CHATGPT" to "ChatGPT",
        "CLAUDE" to "Claude",
        "GEMINI" to "Gemini"
    )
    
    println("服务类型 -> 灵动岛ID -> 简易模式ID -> 匹配状态")
    println("=" * 60)
    
    aiServices.forEach { (serviceType, aiName) ->
        val dynamicId = getDynamicIslandId(serviceType)
        val simpleId = getSimpleModeId(aiName)
        val isMatch = dynamicId == simpleId
        val status = if (isMatch) "✅" else "❌"
        
        println("$serviceType -> $dynamicId -> $simpleId -> $status")
    }
    
    println("\n=== 智谱AI特殊处理 ===")
    val zhipuDynamicId = getDynamicIslandId("ZHIPU_AI")
    val zhipuSimpleId = getSimpleModeId("智谱AI")
    println("智谱AI灵动岛ID: $zhipuDynamicId")
    println("智谱AI简易模式ID: $zhipuSimpleId")
    println("是否匹配: ${zhipuDynamicId == zhipuSimpleId}")
    
    // 检查中文字符处理
    println("\n=== 中文字符处理测试 ===")
    val chineseName = "智谱AI"
    val processedId = "ai_${chineseName.lowercase().replace(" ", "_")}"
    println("原始名称: $chineseName")
    println("处理后ID: $processedId")
    println("中文字符是否被转换: ${chineseName != chineseName.lowercase()}")
}

