package com.example.aifloatingball.model

/**
 * AI助手档案数据类
 */
data class AIPrompt(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false
) {
    companion object {
        /**
         * 获取默认的AI助手档案列表
         */
        fun getDefaultPrompts(): List<AIPrompt> {
            return listOf(
                AIPrompt(
                    id = "1",
                    title = "编程助手",
                    description = "帮助解决编程问题",
                    prompt = "你是一个专业的编程助手，请帮助我解决以下编程问题：",
                    category = "编程",
                    tags = listOf("编程", "代码", "开发")
                ),
                AIPrompt(
                    id = "2",
                    title = "写作助手",
                    description = "帮助改进写作内容",
                    prompt = "你是一个专业的写作助手，请帮助我改进以下内容：",
                    category = "写作",
                    tags = listOf("写作", "文案", "内容")
                ),
                AIPrompt(
                    id = "3",
                    title = "翻译助手",
                    description = "多语言翻译服务",
                    prompt = "你是一个专业的翻译助手，请将以下内容翻译：",
                    category = "翻译",
                    tags = listOf("翻译", "语言", "多语言")
                ),
                AIPrompt(
                    id = "4",
                    title = "学习助手",
                    description = "帮助学习和理解知识",
                    prompt = "你是一个专业的学习助手，请帮助我理解以下知识：",
                    category = "学习",
                    tags = listOf("学习", "教育", "知识")
                ),
                AIPrompt(
                    id = "5",
                    title = "创意助手",
                    description = "激发创意和灵感",
                    prompt = "你是一个创意助手，请帮助我激发创意和灵感：",
                    category = "创意",
                    tags = listOf("创意", "灵感", "设计")
                ),
                AIPrompt(
                    id = "6",
                    title = "分析助手",
                    description = "数据分析和洞察",
                    prompt = "你是一个数据分析助手，请帮助我分析以下数据：",
                    category = "分析",
                    tags = listOf("分析", "数据", "洞察")
                ),
                AIPrompt(
                    id = "7",
                    title = "数学助手",
                    description = "解决数学问题",
                    prompt = "你是一个数学助手，请帮助我解决以下数学问题：",
                    category = "数学",
                    tags = listOf("数学", "计算", "公式")
                ),
                AIPrompt(
                    id = "8",
                    title = "英语助手",
                    description = "英语学习和练习",
                    prompt = "你是一个英语助手，请帮助我学习英语：",
                    category = "语言",
                    tags = listOf("英语", "学习", "练习")
                ),
                AIPrompt(
                    id = "9",
                    title = "健康助手",
                    description = "健康咨询和建议",
                    prompt = "你是一个健康助手，请为我提供健康建议：",
                    category = "健康",
                    tags = listOf("健康", "医疗", "建议")
                ),
                AIPrompt(
                    id = "10",
                    title = "旅游助手",
                    description = "旅游规划和建议",
                    prompt = "你是一个旅游助手，请帮助我规划旅游：",
                    category = "旅游",
                    tags = listOf("旅游", "规划", "建议")
                )
            )
        }
    }
}
