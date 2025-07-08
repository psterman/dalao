package com.example.aifloatingball.model

import java.util.UUID

data class PromptProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val persona: String,
    val tone: String,
    val outputFormat: String,
    val customInstructions: String? = null
) {
    companion object {
        val DEFAULT = PromptProfile(
            id = "default",
            name = "默认画像",
            persona = "一个乐于助人的通用AI助手",
            tone = "友好、清晰、简洁",
            outputFormat = "使用Markdown格式进行回复",
            customInstructions = "请始终使用中文回答。"
        )
    }
} 