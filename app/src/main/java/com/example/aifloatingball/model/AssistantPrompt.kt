package com.example.aifloatingball.model

data class AssistantPrompt(
    val name: String,
    val description: String,
    val prompt: String
)

data class AssistantCategory(
    val name: String,
    val assistants: List<AssistantPrompt>,
    var isExpanded: Boolean = false
) 