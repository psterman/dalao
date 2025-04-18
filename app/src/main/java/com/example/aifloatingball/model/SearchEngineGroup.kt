package com.example.aifloatingball.model

data class SearchEngineGroup(
    val id: Long = 0,
    val name: String,
    val engines: List<SearchEngine>,
    val createdAt: Long = System.currentTimeMillis(),
    val order: Int = 0
) 