package com.example.aifloatingball.model

/**
 * 定义信息收集字段的类型
 */
enum class FieldType {
    TEXT_INPUT,      // 自由文本输入
    SINGLE_CHOICE,   // 单项选择
    MULTIPLE_CHOICE  // 多项选择
}

/**
 * 定义信息收集流程中的每一个步骤（字段）
 * @param id 字段的唯一标识符，如 "topic"
 * @param question 向用户展示的问题，如 "您想了解什么？"
 * @param type 字段类型
 * @param options 对于选择类字段，提供的选项列表
 * @param isOptional 此步骤是否可以跳过
 */
data class PromptField(
    val id: String,
    val question: String,
    val type: FieldType,
    val options: List<String>? = null,
    val isOptional: Boolean = false
)

/**
 * 定义一个完整的用户意图模板
 * @param intentId 意图的唯一标识符，如 "understand"
 * @param intentName 意图的显示名称，如 "我想了解"
 * @param icon 图标 (Emoji)
 * @param description 意图的简短描述
 * @param fields 完成此意图需要收集的信息字段列表
 * @param finalPromptFormat 最终生成Prompt的格式化字符串，使用 {id} 作为占位符
 * @param recommendedEngines 推荐使用的搜索引擎
 */
data class PromptTemplate(
    val intentId: String,
    val intentName: String,
    val icon: String,
    val description: String,
    val fields: List<PromptField>,
    val finalPromptFormat: String,
    val recommendedEngines: List<String> = listOf("deepseek", "google")
)

/**
 * 存储用户针对一个模板所提供的数据
 * @param templateId 对应的模板ID
 * @param collectedData 一个Map，存储每个字段ID和用户提供的答案
 */
data class UserPromptData(
    val templateId: String,
    val collectedData: MutableMap<String, Any> = mutableMapOf()
) 