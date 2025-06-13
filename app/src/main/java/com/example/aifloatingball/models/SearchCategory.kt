package com.example.aifloatingball.models

import com.example.aifloatingball.model.BaseSearchEngine

data class SearchCategory(
    val title: String,
    val engines: List<BaseSearchEngine>
)
