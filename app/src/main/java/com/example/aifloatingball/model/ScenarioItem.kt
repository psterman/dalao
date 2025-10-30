package com.example.aifloatingball.model

/**
 * 左侧任务场景项
 * name: 展示名称
 * category: 绑定的内置分类（可为空，表示用户自定义分组）
 */
data class ScenarioItem(
    var name: String,
    val category: PromptCategory? = null
)


