package com.example.aifloatingball.utils

import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.model.ChatContact

/**
 * AI服务类型工具类
 * 提供统一的AI服务类型识别和映射机制
 */
object AIServiceTypeUtils {
    
    /**
     * 智谱AI的所有可能名称变体
     */
    private val ZHIPU_AI_NAMES = setOf(
        "智谱ai", "智谱AI", "智谱清言", "zhipu", "zhipuai", "glm", "glm-4",
        "智谱", "ZhipuAI", "Zhipu AI", "GLM", "GLM-4"
    )
    
    /**
     * 智谱AI的所有可能ID模式
     */
    private val ZHIPU_AI_ID_PATTERNS = setOf(
        "zhipu", "glm", "智谱", "ai_zhipu", "zhipuai"
    )
    
    /**
     * 从ChatContact获取AI服务类型（支持智谱AI的名称和ID双重识别）
     */
    fun getAIServiceTypeFromContact(contact: ChatContact): AIServiceType? {
        return when (contact.name.lowercase()) {
            "chatgpt", "gpt" -> AIServiceType.CHATGPT
            "claude" -> AIServiceType.CLAUDE
            "gemini" -> AIServiceType.GEMINI
            "文心一言", "wenxin" -> AIServiceType.WENXIN
            "deepseek" -> AIServiceType.DEEPSEEK
            "通义千问", "qianwen" -> AIServiceType.QIANWEN
            "讯飞星火", "xinghuo" -> AIServiceType.XINGHUO
            "kimi" -> AIServiceType.KIMI
            else -> {
                // 检查智谱AI名称
                if (isZhipuAIByName(contact.name)) {
                    return AIServiceType.ZHIPU_AI
                }
                
                // 尝试从ID中识别
                when {
                    contact.id.contains("deepseek", ignoreCase = true) -> AIServiceType.DEEPSEEK
                    contact.id.contains("chatgpt", ignoreCase = true) || contact.id.contains("gpt", ignoreCase = true) -> AIServiceType.CHATGPT
                    contact.id.contains("claude", ignoreCase = true) -> AIServiceType.CLAUDE
                    contact.id.contains("gemini", ignoreCase = true) -> AIServiceType.GEMINI
                    isZhipuAIById(contact.id) -> AIServiceType.ZHIPU_AI
                    contact.id.contains("wenxin", ignoreCase = true) -> AIServiceType.WENXIN
                    contact.id.contains("qianwen", ignoreCase = true) -> AIServiceType.QIANWEN
                    contact.id.contains("xinghuo", ignoreCase = true) -> AIServiceType.XINGHUO
                    contact.id.contains("kimi", ignoreCase = true) -> AIServiceType.KIMI
                    else -> null
                }
            }
        }
    }
    
    /**
     * 从名称和ID推断AI服务类型（用于修复缺失的aiServiceType）
     */
    fun inferAIServiceType(name: String, id: String): AIServiceType? {
        val nameLower = name.lowercase()
        val idLower = id.lowercase()
        
        return when {
            // 智谱AI识别（优先检查）
            isZhipuAIByName(name) || isZhipuAIById(id) -> AIServiceType.ZHIPU_AI
            
            nameLower.contains("deepseek") || idLower.contains("deepseek") -> AIServiceType.DEEPSEEK
            nameLower.contains("chatgpt") || nameLower.contains("gpt") || 
            idLower.contains("chatgpt") || idLower.contains("gpt") -> AIServiceType.CHATGPT
            nameLower.contains("claude") || idLower.contains("claude") -> AIServiceType.CLAUDE
            nameLower.contains("gemini") || idLower.contains("gemini") -> AIServiceType.GEMINI
            nameLower.contains("文心") || nameLower.contains("wenxin") || 
            idLower.contains("wenxin") -> AIServiceType.WENXIN
            nameLower.contains("通义") || nameLower.contains("qianwen") || 
            idLower.contains("qianwen") -> AIServiceType.QIANWEN
            nameLower.contains("星火") || nameLower.contains("xinghuo") || 
            idLower.contains("xinghuo") -> AIServiceType.XINGHUO
            nameLower.contains("kimi") || idLower.contains("kimi") -> AIServiceType.KIMI
            else -> null
        }
    }
    
    /**
     * 检查名称是否匹配智谱AI
     */
    private fun isZhipuAIByName(name: String): Boolean {
        val nameLower = name.lowercase()
        return ZHIPU_AI_NAMES.any { pattern ->
            nameLower.contains(pattern.lowercase()) || nameLower == pattern.lowercase()
        }
    }
    
    /**
     * 检查ID是否匹配智谱AI
     */
    private fun isZhipuAIById(id: String): Boolean {
        val idLower = id.lowercase()
        return ZHIPU_AI_ID_PATTERNS.any { pattern ->
            idLower.contains(pattern.lowercase())
        }
    }
    
    /**
     * 获取AI服务的统一显示名称
     */
    fun getAIDisplayName(aiType: AIServiceType): String {
        return when (aiType) {
            AIServiceType.DEEPSEEK -> "DeepSeek"
            AIServiceType.CHATGPT -> "ChatGPT"
            AIServiceType.CLAUDE -> "Claude"
            AIServiceType.GEMINI -> "Gemini"
            AIServiceType.WENXIN -> "文心一言"
            AIServiceType.QIANWEN -> "通义千问"
            AIServiceType.XINGHUO -> "讯飞星火"
            AIServiceType.KIMI -> "Kimi"
            AIServiceType.ZHIPU_AI -> "智谱AI" // 统一使用"智谱AI"
            AIServiceType.TEMP_SERVICE -> "临时专线"
        }
    }
    
    /**
     * 检查是否为智谱AI服务类型
     */
    fun isZhipuAI(aiType: AIServiceType?): Boolean {
        return aiType == AIServiceType.ZHIPU_AI
    }
    
    /**
     * 检查联系人是否为智谱AI
     */
    fun isZhipuAIContact(contact: ChatContact): Boolean {
        return isZhipuAI(getAIServiceTypeFromContact(contact))
    }
    
    /**
     * 为智谱AI生成标准ID
     */
    fun generateZhipuAIId(): String {
        return "ai_zhipu_ai"
    }
    
    /**
     * 为智谱AI生成标准名称
     */
    fun getZhipuAIStandardName(): String {
        return "智谱AI"
    }
    
    /**
     * 验证智谱AI成员配置是否正确
     */
    fun validateZhipuAIMember(member: com.example.aifloatingball.model.GroupMember): Boolean {
        if (member.type != com.example.aifloatingball.model.MemberType.AI) return false
        if (member.aiServiceType != AIServiceType.ZHIPU_AI) return false
        
        val isValidName = isZhipuAIByName(member.name)
        val isValidId = isZhipuAIById(member.id)
        
        return isValidName && isValidId
    }
    
    /**
     * 获取智谱AI配置问题描述
     */
    fun getZhipuAIConfigurationIssues(member: com.example.aifloatingball.model.GroupMember): List<String> {
        val issues = mutableListOf<String>()
        
        if (member.type != com.example.aifloatingball.model.MemberType.AI) {
            issues.add("成员类型不是AI")
            return issues
        }
        
        if (member.aiServiceType != AIServiceType.ZHIPU_AI) {
            issues.add("aiServiceType不是ZHIPU_AI (当前: ${member.aiServiceType})")
        }
        
        if (!isZhipuAIByName(member.name)) {
            issues.add("名称不符合智谱AI模式 (当前: ${member.name})")
        }
        
        if (!isZhipuAIById(member.id)) {
            issues.add("ID不符合智谱AI模式 (当前: ${member.id})")
        }
        
        return issues
    }
    
    /**
     * 打印智谱AI识别调试信息
     */
    fun debugZhipuAIIdentification(name: String, id: String) {
        println("=== 智谱AI识别调试 ===")
        println("输入名称: '$name'")
        println("输入ID: '$id'")
        println("按名称识别: ${isZhipuAIByName(name)}")
        println("按ID识别: ${isZhipuAIById(id)}")
        println("推断结果: ${inferAIServiceType(name, id)}")
        println("========================")
    }
}
