package com.example.aifloatingball.model

import android.os.Parcelable

/**
 * 搜索引擎基础接口
 */
interface BaseSearchEngine : Parcelable {
    val name: String
    val displayName: String
        get() = name
    val url: String
    val iconResId: Int
    val description: String
    val searchUrl: String
    
    /**
     * 获取搜索URL
     * @param query 搜索关键词
     * @return 完整的搜索URL
     */
    fun getSearchUrl(query: String): String
} 