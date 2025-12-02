package com.example.aifloatingball.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * AI标签数据模型
 */
@Parcelize
data class AITag(
    val id: String,
    val name: String,
    val description: String = "",
    val color: Int = 0,
    val isDefault: Boolean = false,
    val aiContactIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable

/**
 * AI标签管理器
 */
object AITagManager {
    
    /**
     * 获取默认标签
     */
    fun getDefaultTags(): List<AITag> {
        return listOf(
            AITag(
                id = "ai_assistant",
                name = "AI助手",
                description = "置顶的AI助手，优先显示和搜索",
                color = 0xFF2196F3.toInt(),
                isDefault = true
            ),
            AITag(
                id = "voice_text",
                name = "语音文本",
                description = "语音转化的文本内容",
                color = 0xFF4CAF50.toInt(),
                isDefault = true
            ),
            AITag(
                id = "custom_1",
                name = "编程助手",
                description = "专注于编程相关的AI助手",
                color = 0xFF4CAF50.toInt(),
                isDefault = false
            ),
            AITag(
                id = "custom_2", 
                name = "写作助手",
                description = "专注于写作和文案的AI助手",
                color = 0xFFFF9800.toInt(),
                isDefault = false
            ),
            AITag(
                id = "custom_3",
                name = "翻译助手", 
                description = "多语言翻译AI助手",
                color = 0xFF9C27B0.toInt(),
                isDefault = false
            )
        )
    }
    
    /**
     * 根据AI名称自动分类到合适的标签
     */
    fun autoCategorizeAI(aiName: String): String {
        val lowerName = aiName.lowercase()
        
        return when {
            // 临时专线直接分类到AI助手
            lowerName.contains("临时专线") || lowerName.contains("tempservice") || 
            lowerName.contains("temp_service") -> "ai_assistant"
            
            // 编程相关
            lowerName.contains("chatgpt") || lowerName.contains("claude") || 
            lowerName.contains("gemini") || lowerName.contains("copilot") -> "custom_1"
            
            // 写作相关  
            lowerName.contains("文心") || lowerName.contains("写作") || 
            lowerName.contains("文案") -> "custom_2"
            
            // 翻译相关
            lowerName.contains("翻译") || lowerName.contains("translate") -> "custom_3"
            
            // 默认分类到AI助手
            else -> "ai_assistant"
        }
    }
    
    /**
     * 获取标签的显示名称
     */
    fun getTagDisplayName(tagId: String): String {
        return when (tagId) {
            "ai_assistant" -> "AI助手"
            "custom_1" -> "编程助手"
            "custom_2" -> "写作助手"
            "custom_3" -> "翻译助手"
            else -> tagId
        }
    }
}
