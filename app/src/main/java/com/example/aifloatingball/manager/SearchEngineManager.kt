package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchEngineManager private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "search_engine_prefs"
        private const val KEY_SEARCH_GROUPS = "search_groups"
        private const val KEY_SEARCH_ENGINES = "search_engines"
        private const val MAX_GROUPS = 10

        @Volatile
        private var instance: SearchEngineManager? = null

        fun getInstance(context: Context): SearchEngineManager {
            return instance ?: synchronized(this) {
                instance ?: SearchEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 保存单个搜索引擎 - 为了兼容旧版接口
     */
    fun saveSearchEngine(searchEngine: SearchEngine): Boolean {
        try {
            val currentEngines = getSavedSearchEngines().toMutableList()
            
            // 检查引擎是否已存在
            val existingIndex = currentEngines.indexOfFirst { it.searchUrl == searchEngine.searchUrl }
            if (existingIndex != -1) {
                currentEngines[existingIndex] = searchEngine
            } else {
                currentEngines.add(searchEngine)
            }

            val json = gson.toJson(currentEngines)
            sharedPreferences.edit().putString(KEY_SEARCH_ENGINES, json).apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 获取保存的搜索引擎列表 - 为了兼容旧版接口
     */
    fun getSavedSearchEngines(): List<SearchEngine> {
        val json = sharedPreferences.getString(KEY_SEARCH_ENGINES, "[]")
        val type = object : TypeToken<List<SearchEngine>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveSearchEngineGroup(name: String, engines: List<SearchEngine>) {
        val group = SearchEngineGroup(
            id = System.currentTimeMillis(),
            name = name,
            engines = engines.toMutableList(),
            isEnabled = true
        )
        saveSearchEngineGroup(group)
    }

    fun saveSearchEngineGroup(group: SearchEngineGroup) {
        val groups = getSearchEngineGroups().toMutableList()
        
        // Remove existing group with same name if exists
        groups.removeAll { it.name == group.name }
        
        // Add new group
        if (groups.size < MAX_GROUPS) {
            groups.add(group)
            saveGroups(groups)
        }
    }

    fun getSearchEngineGroups(): List<SearchEngineGroup> {
        val json = sharedPreferences.getString(KEY_SEARCH_GROUPS, "[]")
        val type = object : TypeToken<List<SearchEngineGroup>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun deleteSearchEngineGroup(groupName: String) {
        val groups = getSearchEngineGroups().toMutableList()
        groups.removeAll { it.name == groupName }
        saveGroups(groups)
    }

    fun updateGroupOrder(groups: List<SearchEngineGroup>) {
        saveGroups(groups)
    }

    private fun saveGroups(groups: List<SearchEngineGroup>) {
        val json = gson.toJson(groups)
        sharedPreferences.edit().putString(KEY_SEARCH_GROUPS, json).apply()
    }
} 