package com.example.aifloatingball.model

/**
 * 搜索引擎快捷方式数据类
 * 用于在FloatingWebViewService和FloatingWindowService之间共享
 */
data class SearchEngineShortcut(
    val id: String,
    val name: String,
    val url: String,
    val domain: String
) 