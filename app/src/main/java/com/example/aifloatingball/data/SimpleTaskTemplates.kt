package com.example.aifloatingball.data

import com.example.aifloatingball.model.FieldType
import com.example.aifloatingball.model.PromptField
import com.example.aifloatingball.model.PromptTemplate

/**
 * 简易模式的阶梯式任务模板
 * 这些模板帮助用户通过逐步填写来构建精准的prompt
 */
object SimpleTaskTemplates {
    
    val templates = listOf(
        // 1. 学习理解类
        PromptTemplate(
            intentId = "learn_understand",
            intentName = "我想了解",
            icon = "🎓",
            description = "深入了解某个主题或概念",
            fields = listOf(
                PromptField(
                    id = "topic",
                    question = "您想了解什么主题？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "depth",
                    question = "您希望了解的深度如何？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("基础入门", "深入理解", "专业级别", "研究级别")
                ),
                PromptField(
                    id = "focus",
                    question = "您特别关注的方面是什么？",
                    type = FieldType.TEXT_INPUT,
                    isOptional = true
                )
            ),
            finalPromptFormat = "请向我详细解释{topic}。我希望从{depth}的角度来了解，{focus}。请使用通俗易懂的语言，并提供具体的例子。",
            recommendedEngines = listOf("deepseek", "chatgpt")
        ),
        
        // 2. 问题解决类
        PromptTemplate(
            intentId = "solve_problem",
            intentName = "解决问题",
            icon = "🛠️",
            description = "寻求解决具体问题的方案",
            fields = listOf(
                PromptField(
                    id = "problem",
                    question = "您遇到了什么问题？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "context",
                    question = "这个问题发生的具体情况是？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "constraints",
                    question = "有什么限制条件或要求吗？",
                    type = FieldType.TEXT_INPUT,
                    isOptional = true
                ),
                PromptField(
                    id = "urgency",
                    question = "这个问题的紧急程度如何？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("不急", "有点急", "比较急", "非常急")
                )
            ),
            finalPromptFormat = "我遇到了这个问题：{problem}。具体情况是：{context}。{constraints}这个问题{urgency}。请为我提供详细的解决方案和具体的操作步骤。",
            recommendedEngines = listOf("deepseek", "chatgpt", "claude")
        ),
        
        // 3. 创作辅助类
        PromptTemplate(
            intentId = "creative_writing",
            intentName = "创作内容",
            icon = "✍️",
            description = "帮助您创作文章、文案或其他内容",
            fields = listOf(
                PromptField(
                    id = "content_type",
                    question = "您要创作什么类型的内容？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("文章", "工作邮件", "社交媒体文案", "演讲稿", "创意故事", "产品介绍", "其他")
                ),
                PromptField(
                    id = "topic_theme",
                    question = "主题或核心内容是什么？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "audience",
                    question = "目标受众是谁？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "tone_style",
                    question = "您希望什么样的语气风格？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("正式专业", "轻松友好", "幽默风趣", "温馨感人", "简洁有力", "文艺优雅")
                ),
                PromptField(
                    id = "length",
                    question = "大概需要多长？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("简短(100字以内)", "中等(200-500字)", "详细(500-1000字)", "长篇(1000字以上)")
                )
            ),
            finalPromptFormat = "请帮我创作一篇{content_type}，主题是：{topic_theme}。目标受众是{audience}，希望采用{tone_style}的风格，长度控制在{length}。请确保内容生动有趣，逻辑清晰。",
            recommendedEngines = listOf("chatgpt", "claude", "文心一言")
        ),
        
        // 4. 分析对比类
        PromptTemplate(
            intentId = "analyze_compare",
            intentName = "分析对比",
            icon = "⚖️",
            description = "深入分析或对比不同的选项",
            fields = listOf(
                PromptField(
                    id = "items",
                    question = "您要分析或对比什么？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "criteria",
                    question = "从哪些方面进行分析？",
                    type = FieldType.MULTIPLE_CHOICE,
                    options = listOf("功能特点", "价格成本", "优缺点", "适用场景", "性能表现", "用户体验", "发展前景", "其他")
                ),
                PromptField(
                    id = "purpose",
                    question = "分析的目的是什么？",
                    type = FieldType.TEXT_INPUT,
                    isOptional = true
                )
            ),
            finalPromptFormat = "请帮我详细分析对比{items}。重点从{criteria}这些方面进行比较。{purpose}请提供客观、全面的分析，并给出结论和建议。",
            recommendedEngines = listOf("deepseek", "claude", "chatgpt")
        ),
        
        // 5. 学习计划类
        PromptTemplate(
            intentId = "learning_plan",
            intentName = "制定计划",
            icon = "📋",
            description = "制定学习、工作或项目计划",
            fields = listOf(
                PromptField(
                    id = "goal",
                    question = "您的目标是什么？",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "timeframe",
                    question = "希望在多长时间内完成？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("1周内", "1个月内", "3个月内", "半年内", "1年内", "长期目标")
                ),
                PromptField(
                    id = "current_level",
                    question = "您目前的基础如何？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("完全零基础", "有一些了解", "有基础经验", "比较熟练", "已是专家")
                ),
                PromptField(
                    id = "constraints",
                    question = "有什么时间或资源限制吗？",
                    type = FieldType.TEXT_INPUT,
                    isOptional = true
                )
            ),
            finalPromptFormat = "请帮我制定一个详细的计划来实现这个目标：{goal}。我希望在{timeframe}完成，目前的基础是{current_level}。{constraints}请提供具体的步骤、时间安排和资源建议。",
            recommendedEngines = listOf("chatgpt", "deepseek", "claude")
        ),
        
        // 6. 翻译优化类
        PromptTemplate(
            intentId = "translate_optimize",
            intentName = "翻译润色",
            icon = "🌐",
            description = "翻译内容或优化文本表达",
            fields = listOf(
                PromptField(
                    id = "task_type",
                    question = "您需要什么帮助？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("中翻英", "英翻中", "其他语言翻译", "文本润色优化", "语法检查")
                ),
                PromptField(
                    id = "content",
                    question = "请输入需要处理的内容：",
                    type = FieldType.TEXT_INPUT
                ),
                PromptField(
                    id = "style_requirement",
                    question = "有什么特殊要求吗？",
                    type = FieldType.SINGLE_CHOICE,
                    options = listOf("正式商务", "学术论文", "日常口语", "文学优美", "简洁明了", "保持原意"),
                    isOptional = true
                )
            ),
            finalPromptFormat = "请帮我{task_type}以下内容：{content}。{style_requirement}请确保翻译/润色后的内容自然流畅，符合目标语言的表达习惯。",
            recommendedEngines = listOf("deepseek", "chatgpt", "claude", "通义千问")
        )
    )
    
    /**
     * 根据ID获取模板
     */
    fun getTemplateById(id: String): PromptTemplate? {
        return templates.find { it.intentId == id }
    }
    
    /**
     * 获取所有模板的简要信息（用于网格显示）
     */
    fun getTaskCategories(): List<Pair<String, String>> {
        return templates.map { it.icon to it.intentName }
    }
} 