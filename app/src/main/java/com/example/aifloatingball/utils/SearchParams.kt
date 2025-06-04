package com.example.aifloatingball.utils

/**
 * 搜索参数数据类
 */
data class SearchParams(
    val query: String = "",
    val windowCount: Int,
    val engineKey: String?
) 