package com.example.aifloatingball.model

import java.util.UUID

data class PromptProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val persona: String,
    val tone: String,
    val outputFormat: String,
    val customInstructions: String? = null,
    // 新增详细配置字段
    val expertise: String = "", // 专业领域
    val language: String = "中文", // 语言偏好
    val creativity: Int = 5, // 创造性水平 (1-10)
    val formality: String = "适中", // 正式程度：正式/适中/非正式
    val responseLength: String = "适中", // 回复长度：简短/适中/详细
    val reasoning: Boolean = true, // 是否显示推理过程
    val examples: Boolean = true, // 是否提供示例
    val codeStyle: String = "清晰", // 代码风格：简洁/清晰/详细
    val temperature: Float = 0.7f, // 模型温度参数
    val topP: Float = 0.9f, // Top-p 采样参数
    val maxTokens: Int = 2048, // 最大令牌数
    val tags: List<String> = listOf(), // 标签分类
    val description: String = "", // 详细描述
    val icon: String = "🤖", // 图标
    val color: String = "#2196F3" // 主题色
) {
    companion object {
        val DEFAULT = PromptProfile(
            id = "default",
            name = "默认画像",
            persona = "一个乐于助人的通用AI助手",
            tone = "友好、清晰、简洁",
            outputFormat = "使用Markdown格式进行回复",
            customInstructions = "请始终使用中文回答。",
            expertise = "通用知识",
            language = "中文",
            creativity = 5,
            formality = "适中",
            responseLength = "适中",
            reasoning = true,
            examples = true,
            codeStyle = "清晰",
            temperature = 0.7f,
            topP = 0.9f,
            maxTokens = 2048,
            tags = listOf("通用", "助手"),
            description = "适用于各种日常问题的通用AI助手",
            icon = "🤖",
            color = "#2196F3"
        )
        
        // 预定义的专业模板
        val PROGRAMMING_EXPERT = DEFAULT.copy(
            id = "programming",
            name = "编程专家",
            persona = "经验丰富的高级软件工程师，精通多种编程语言和技术栈",
            tone = "专业、准确、实用",
            expertise = "软件开发",
            creativity = 7,
            formality = "适中",
            responseLength = "详细",
            codeStyle = "详细",
            tags = listOf("编程", "技术", "开发"),
            description = "专门处理编程相关问题，提供代码示例和最佳实践",
            icon = "💻",
            color = "#4CAF50"
        )
        
        val WRITING_ASSISTANT = DEFAULT.copy(
            id = "writing",
            name = "写作助手",
            persona = "专业的写作顾问，擅长各种文体和写作技巧",
            tone = "优雅、富有感染力、专业",
            expertise = "写作创作",
            creativity = 8,
            formality = "正式",
            responseLength = "详细",
            tags = listOf("写作", "创作", "文案"),
            description = "帮助改进写作风格，提供创作灵感和语言建议",
            icon = "✍️",
            color = "#FF9800"
        )
        
        val BUSINESS_CONSULTANT = DEFAULT.copy(
            id = "business",
            name = "商业顾问",
            persona = "资深商业分析师，具有丰富的企业管理和战略规划经验",
            tone = "专业、权威、务实",
            expertise = "商业管理",
            creativity = 6,
            formality = "正式",
            responseLength = "详细",
            reasoning = true,
            tags = listOf("商业", "管理", "战略"),
            description = "提供商业策略建议和市场分析",
            icon = "📊",
            color = "#9C27B0"
        )
    }
} 