package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppCategory

/**
 * 常用AI管理器
 */
class FavoriteAIManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("favorite_ai", Context.MODE_PRIVATE)
    private val favoriteAIKey = "favorite_ai_list"
    
    companion object {
        @Volatile
        private var INSTANCE: FavoriteAIManager? = null
        
        fun getInstance(context: Context): FavoriteAIManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteAIManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 添加AI到常用列表
     */
    fun addToFavorites(appId: String) {
        val favorites = getFavoriteAIList().toMutableSet()
        favorites.add(appId)
        saveFavoriteAIList(favorites.toList())
    }
    
    /**
     * 从常用列表移除AI
     */
    fun removeFromFavorites(appId: String) {
        val favorites = getFavoriteAIList().toMutableList()
        favorites.remove(appId)
        saveFavoriteAIList(favorites)
    }
    
    /**
     * 检查AI是否在常用列表中
     */
    fun isFavorite(appId: String): Boolean {
        return getFavoriteAIList().contains(appId)
    }
    
    /**
     * 获取常用AI列表
     */
    fun getFavoriteAIList(): List<String> {
        val favoritesString = prefs.getString(favoriteAIKey, "") ?: ""
        return if (favoritesString.isEmpty()) {
            emptyList()
        } else {
            favoritesString.split(",").filter { it.isNotEmpty() }
        }
    }
    
    /**
     * 保存常用AI列表
     */
    private fun saveFavoriteAIList(favorites: List<String>) {
        prefs.edit()
            .putString(favoriteAIKey, favorites.joinToString(","))
            .apply()
    }
    
    /**
     * 对AI应用列表进行排序，常用AI排在前面
     */
    fun sortAIAppsByFavorites(apps: List<AppSearchConfig>): List<AppSearchConfig> {
        val favorites = getFavoriteAIList()
        
        return apps.sortedWith { app1, app2 ->
            val isApp1Favorite = favorites.contains(app1.appId)
            val isApp2Favorite = favorites.contains(app2.appId)
            
            when {
                isApp1Favorite && !isApp2Favorite -> -1 // app1排在前面
                !isApp1Favorite && isApp2Favorite -> 1  // app2排在前面
                else -> 0 // 保持原有顺序
            }
        }
    }
    
    /**
     * 获取常用AI的配置信息
     */
    fun getFavoriteAIConfigs(allConfigs: List<AppSearchConfig>): List<AppSearchConfig> {
        val favorites = getFavoriteAIList()
        return allConfigs.filter { it.appId in favorites }
    }
}
