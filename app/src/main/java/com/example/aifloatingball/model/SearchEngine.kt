package com.example.aifloatingball.model

open class SearchEngine(
    open val name: String,
    open val url: String,
    open val iconResId: Int,
    open val description: String = ""
) {
    open fun getSearchUrl(query: String): String {
        return if (url.contains("{query}")) {
            url.replace("{query}", android.net.Uri.encode(query))
        } else {
            url
        }
    }
} 