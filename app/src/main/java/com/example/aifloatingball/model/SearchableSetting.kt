package com.example.aifloatingball.model

data class SearchableSetting(
    val key: String, // The key of the Preference in the XML
    val title: CharSequence,
    val summary: CharSequence?,
    val keywords: List<String> = emptyList()
) 