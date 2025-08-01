package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.*
import com.example.aifloatingball.service.ContentService
import com.example.aifloatingball.service.ContentServiceFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 通用内容订阅管理器
 */
class ContentSubscriptionManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ContentSubscriptionManager"
        private const val KEY_SUBSCRIBED_CREATORS = "subscribed_creators"
        private const val KEY_CACHED_CONTENTS = "cached_contents"
        private const val KEY_PLATFORM_CONFIGS = "platform_configs"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        
        @Volatile
        private var INSTANCE: ContentSubscriptionManager? = null
        
        fun getInstance(context: Context): ContentSubscriptionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContentSubscriptionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val settingsManager = SettingsManager.getInstance(context)
    private val gson = Gson()
    
    // 内存缓存
    private val subscribedCreators = ConcurrentHashMap<String, Creator>() // key: platform_uid
    private val cachedContents = ConcurrentHashMap<String, List<Content>>() // key: platform_uid
    private val platformConfigs = ConcurrentHashMap<ContentPlatform, SubscriptionConfig>()
    
    // 监听器
    private val contentUpdateListeners = mutableListOf<(ContentPlatform, List<Content>) -> Unit>()
    private val subscriptionChangeListeners = mutableListOf<(ContentPlatform, List<Creator>) -> Unit>()
    
    init {
        loadSubscribedCreators()
        loadCachedContents()
        loadPlatformConfigs()
    }
    
    /**
     * 添加内容更新监听器
     */
    fun addContentUpdateListener(listener: (ContentPlatform, List<Content>) -> Unit) {
        contentUpdateListeners.add(listener)
    }
    
    /**
     * 移除内容更新监听器
     */
    fun removeContentUpdateListener(listener: (ContentPlatform, List<Content>) -> Unit) {
        contentUpdateListeners.remove(listener)
    }
    
    /**
     * 添加订阅变化监听器
     */
    fun addSubscriptionChangeListener(listener: (ContentPlatform, List<Creator>) -> Unit) {
        subscriptionChangeListeners.add(listener)
    }
    
    /**
     * 移除订阅变化监听器
     */
    fun removeSubscriptionChangeListener(listener: (ContentPlatform, List<Creator>) -> Unit) {
        subscriptionChangeListeners.remove(listener)
    }
    
    /**
     * 订阅创作者
     */
    suspend fun subscribeCreator(platform: ContentPlatform, uid: String): Result<Creator> {
        return try {
            val service = ContentServiceFactory.getService(platform)
                ?: return Result.failure(Exception("Platform not supported: ${platform.displayName}"))
            
            // 获取创作者信息
            val creatorResult = service.getCreatorInfo(uid)
            if (creatorResult.isFailure) {
                return Result.failure(creatorResult.exceptionOrNull() ?: Exception("Failed to get creator info"))
            }
            
            val creator = creatorResult.getOrNull()!!
            val key = "${platform.platformId}_$uid"
            
            subscribedCreators[key] = creator
            saveSubscribedCreators()
            
            // 立即获取一次内容
            updateCreatorContents(platform, uid)
            
            // 通知订阅变化
            notifySubscriptionChange(platform)
            
            Log.d(TAG, "Successfully subscribed to creator: ${creator.name} on ${platform.displayName}")
            Result.success(creator)
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to creator: $uid on ${platform.displayName}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 取消订阅创作者
     */
    fun unsubscribeCreator(platform: ContentPlatform, uid: String) {
        val key = "${platform.platformId}_$uid"
        subscribedCreators.remove(key)
        cachedContents.remove(key)
        
        saveSubscribedCreators()
        saveCachedContents()
        
        // 通知变化
        notifySubscriptionChange(platform)
        notifyContentUpdate(platform)
        
        Log.d(TAG, "Unsubscribed from creator: $uid on ${platform.displayName}")
    }
    
    /**
     * 获取指定平台的订阅创作者列表
     */
    fun getSubscribedCreators(platform: ContentPlatform): List<Creator> {
        return subscribedCreators.values.filter { it.platform == platform }
            .sortedByDescending { it.subscribeTime }
    }
    
    /**
     * 获取所有订阅的创作者
     */
    fun getAllSubscribedCreators(): Map<ContentPlatform, List<Creator>> {
        return subscribedCreators.values.groupBy { it.platform }
    }
    
    /**
     * 获取指定平台的所有内容
     */
    fun getPlatformContents(platform: ContentPlatform): List<Content> {
        return cachedContents.values.flatten()
            .filter { it.platform == platform }
            .sortedByDescending { it.publishTime }
            .take(100) // 限制最多100条
    }
    
    /**
     * 获取指定创作者的内容
     */
    fun getCreatorContents(platform: ContentPlatform, uid: String): List<Content> {
        val key = "${platform.platformId}_$uid"
        return cachedContents[key] ?: emptyList()
    }
    
    /**
     * 获取所有平台的混合内容
     */
    fun getAllContents(): List<Content> {
        return cachedContents.values.flatten()
            .sortedByDescending { it.publishTime }
            .take(200) // 限制最多200条
    }
    
    /**
     * 更新指定平台的所有内容
     */
    suspend fun updatePlatformContents(platform: ContentPlatform): Result<Unit> {
        return try {
            val creators = getSubscribedCreators(platform)
            val jobs = creators.map { creator ->
                CoroutineScope(Dispatchers.IO).async {
                    updateCreatorContents(platform, creator.uid)
                }
            }
            
            jobs.awaitAll()
            
            val config = getPlatformConfig(platform)
            setPlatformConfig(platform, config.copy())
            
            notifyContentUpdate(platform)
            
            Log.d(TAG, "Successfully updated all contents for ${platform.displayName}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating platform contents for ${platform.displayName}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新所有平台的内容
     */
    suspend fun updateAllContents(): Result<Unit> {
        return try {
            val platforms = getAllSubscribedCreators().keys
            val jobs = platforms.map { platform ->
                CoroutineScope(Dispatchers.IO).async {
                    updatePlatformContents(platform)
                }
            }
            
            jobs.awaitAll()
            
            settingsManager.putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            
            Log.d(TAG, "Successfully updated all platform contents")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating all contents", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新指定创作者的内容
     */
    private suspend fun updateCreatorContents(platform: ContentPlatform, uid: String) {
        try {
            val service = ContentServiceFactory.getService(platform) ?: return
            val result = service.getCreatorContents(uid)
            
            if (result.isSuccess) {
                val contents = result.getOrNull() ?: emptyList()
                val key = "${platform.platformId}_$uid"
                val config = getPlatformConfig(platform)
                
                cachedContents[key] = contents.take(config.maxContentCount)
                
                // 更新创作者的最后更新时间
                subscribedCreators[key]?.let { creator ->
                    subscribedCreators[key] = creator.copy(lastUpdateTime = System.currentTimeMillis())
                }
                
                Log.d(TAG, "Updated contents for creator $uid on ${platform.displayName}: ${contents.size} items")
            } else {
                Log.w(TAG, "Failed to update contents for creator $uid on ${platform.displayName}: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contents for creator $uid on ${platform.displayName}", e)
        }
    }
    
    /**
     * 获取平台配置
     */
    fun getPlatformConfig(platform: ContentPlatform): SubscriptionConfig {
        return platformConfigs[platform] ?: SubscriptionConfig(platform)
    }
    
    /**
     * 设置平台配置
     */
    fun setPlatformConfig(platform: ContentPlatform, config: SubscriptionConfig) {
        platformConfigs[platform] = config
        savePlatformConfigs()
    }
    
    /**
     * 检查是否需要更新
     */
    fun shouldUpdate(platform: ContentPlatform): Boolean {
        val config = getPlatformConfig(platform)
        val lastUpdateTime = settingsManager.getLong(KEY_LAST_UPDATE_TIME, 0)
        return System.currentTimeMillis() - lastUpdateTime > config.updateInterval
    }
    
    /**
     * 搜索创作者
     */
    suspend fun searchCreators(platform: ContentPlatform, keyword: String): Result<List<Creator>> {
        val service = ContentServiceFactory.getService(platform)
            ?: return Result.failure(Exception("Platform not supported: ${platform.displayName}"))
        
        return service.searchCreators(keyword)
    }
    
    // 数据持久化方法
    private fun saveSubscribedCreators() {
        try {
            val json = gson.toJson(subscribedCreators.values.toList())
            settingsManager.putString(KEY_SUBSCRIBED_CREATORS, json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscribed creators", e)
        }
    }
    
    private fun loadSubscribedCreators() {
        try {
            val json = settingsManager.getString(KEY_SUBSCRIBED_CREATORS, "") ?: ""
            if (json.isNotEmpty()) {
                val type = object : TypeToken<List<Creator>>() {}.type
                val creators: List<Creator> = gson.fromJson(json, type)
                subscribedCreators.clear()
                creators.forEach { creator ->
                    val key = "${creator.platform.platformId}_${creator.uid}"
                    subscribedCreators[key] = creator
                }
                Log.d(TAG, "Loaded ${creators.size} subscribed creators")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subscribed creators", e)
        }
    }
    
    private fun saveCachedContents() {
        try {
            val allContents = cachedContents.values.flatten()
            val json = gson.toJson(allContents)
            settingsManager.putString(KEY_CACHED_CONTENTS, json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cached contents", e)
        }
    }
    
    private fun loadCachedContents() {
        try {
            val json = settingsManager.getString(KEY_CACHED_CONTENTS, "") ?: ""
            if (json.isNotEmpty()) {
                val type = object : TypeToken<List<Content>>() {}.type
                val contents: List<Content> = gson.fromJson(json, type)
                
                cachedContents.clear()
                contents.groupBy { "${it.platform.platformId}_${it.creatorUid}" }.forEach { (key, contentList) ->
                    cachedContents[key] = contentList
                }
                
                Log.d(TAG, "Loaded ${contents.size} cached contents")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached contents", e)
        }
    }
    
    private fun savePlatformConfigs() {
        try {
            val json = gson.toJson(platformConfigs.values.toList())
            settingsManager.putString(KEY_PLATFORM_CONFIGS, json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving platform configs", e)
        }
    }
    
    private fun loadPlatformConfigs() {
        try {
            val json = settingsManager.getString(KEY_PLATFORM_CONFIGS, "") ?: ""
            if (json.isNotEmpty()) {
                val type = object : TypeToken<List<SubscriptionConfig>>() {}.type
                val configs: List<SubscriptionConfig> = gson.fromJson(json, type)
                platformConfigs.clear()
                configs.forEach { config ->
                    platformConfigs[config.platform] = config
                }
                Log.d(TAG, "Loaded ${configs.size} platform configs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading platform configs", e)
        }
    }
    
    // 通知方法
    private fun notifyContentUpdate(platform: ContentPlatform) {
        val contents = getPlatformContents(platform)
        contentUpdateListeners.forEach { listener ->
            try {
                listener(platform, contents)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying content update listener", e)
            }
        }
        saveCachedContents()
    }
    
    private fun notifySubscriptionChange(platform: ContentPlatform) {
        val creators = getSubscribedCreators(platform)
        subscriptionChangeListeners.forEach { listener ->
            try {
                listener(platform, creators)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying subscription change listener", e)
            }
        }
    }
}
