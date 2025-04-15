package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.SearchEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchEngineManager private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "search_engines"
        private const val KEY_SEARCH_ENGINES = "saved_search_engines"
        
        @Volatile
        private var instance: SearchEngineManager? = null

        fun getInstance(context: Context): SearchEngineManager {
            return instance ?: synchronized(this) {
                instance ?: SearchEngineManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun saveSearchEngine(searchEngine: SearchEngine): Boolean {
        try {
            val currentEngines = getSavedSearchEngines().toMutableList()
            
            // Check if engine already exists
            val existingIndex = currentEngines.indexOfFirst { it.url == searchEngine.url }
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

    fun clearSearchEngines() {
        sharedPreferences.edit().remove(KEY_SEARCH_ENGINES).apply()
    }
} 