package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.PromptCommunityItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Prompt收藏管理器
 */
class PromptFavoriteManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("prompt_favorites", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_FAVORITES = "favorite_prompts"
    
    companion object {
        @Volatile
        private var instance: PromptFavoriteManager? = null
        
        fun getInstance(context: Context): PromptFavoriteManager {
            return instance ?: synchronized(this) {
                instance ?: PromptFavoriteManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 添加收藏
     */
    fun addFavorite(prompt: PromptCommunityItem) {
        val favorites = getFavorites().toMutableList()
        if (!favorites.any { it.id == prompt.id }) {
            favorites.add(prompt)
            saveFavorites(favorites)
        }
    }
    
    /**
     * 移除收藏
     */
    fun removeFavorite(promptId: String) {
        val favorites = getFavorites().toMutableList()
        favorites.removeAll { it.id == promptId }
        saveFavorites(favorites)
    }
    
    /**
     * 检查是否已收藏
     */
    fun isFavorite(promptId: String): Boolean {
        return getFavorites().any { it.id == promptId }
    }
    
    /**
     * 获取所有收藏的Prompt
     */
    fun getFavorites(): List<PromptCommunityItem> {
        val json = prefs.getString(KEY_FAVORITES, null)
        if (json == null || json.isEmpty()) {
            return emptyList()
        }
        try {
            val type = object : TypeToken<List<PromptCommunityItem>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("PromptFavoriteManager", "解析收藏列表失败", e)
            return emptyList()
        }
    }
    
    /**
     * 保存收藏列表
     */
    private fun saveFavorites(favorites: List<PromptCommunityItem>) {
        val json = gson.toJson(favorites)
        prefs.edit().putString(KEY_FAVORITES, json).apply()
    }
}

