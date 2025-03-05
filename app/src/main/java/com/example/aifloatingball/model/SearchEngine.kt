package com.example.aifloatingball.model

open class SearchEngine(
    val name: String,
    val url: String,
    val iconResId: Int,
    val description: String = ""
) {
    open fun getSearchUrl(query: String): String {
        return if (url.contains("{query}")) {
            url.replace("{query}", android.net.Uri.encode(query))
        } else {
            url
        }
    }
} 