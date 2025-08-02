package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.LocalDynamic
import com.example.aifloatingball.model.SubscribedUser
import com.example.aifloatingball.service.BilibiliApiService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * B站动态管理器
 */
class BilibiliDynamicManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BilibiliDynamicManager"
        private const val KEY_SUBSCRIBED_USERS = "bilibili_subscribed_users"
        private const val KEY_CACHED_DYNAMICS = "bilibili_cached_dynamics"
        private const val KEY_LAST_UPDATE_TIME = "bilibili_last_update_time"
        private const val UPDATE_INTERVAL = 12 * 60 * 60 * 1000L // 12小时
        
        @Volatile
        private var INSTANCE: BilibiliDynamicManager? = null
        
        fun getInstance(context: Context): BilibiliDynamicManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BilibiliDynamicManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val settingsManager = SettingsManager.getInstance(context)
    private val apiService = BilibiliApiService.getInstance(context)
    private val gson = Gson()
    
    // 内存缓存
    private val subscribedUsers = ConcurrentHashMap<Long, SubscribedUser>()
    private val cachedDynamics = ConcurrentHashMap<Long, List<LocalDynamic>>()
    
    // 监听器
    private val dynamicsUpdateListeners = mutableListOf<(List<LocalDynamic>) -> Unit>()
    private val subscriptionChangeListeners = mutableListOf<(List<SubscribedUser>) -> Unit>()
    
    init {
        loadSubscribedUsers()
        loadCachedDynamics()
    }
    
    /**
     * 添加动态更新监听器
     */
    fun addDynamicsUpdateListener(listener: (List<LocalDynamic>) -> Unit) {
        dynamicsUpdateListeners.add(listener)
    }
    
    /**
     * 移除动态更新监听器
     */
    fun removeDynamicsUpdateListener(listener: (List<LocalDynamic>) -> Unit) {
        dynamicsUpdateListeners.remove(listener)
    }
    
    /**
     * 添加订阅变化监听器
     */
    fun addSubscriptionChangeListener(listener: (List<SubscribedUser>) -> Unit) {
        subscriptionChangeListeners.add(listener)
    }
    
    /**
     * 移除订阅变化监听器
     */
    fun removeSubscriptionChangeListener(listener: (List<SubscribedUser>) -> Unit) {
        subscriptionChangeListeners.remove(listener)
    }
    
    /**
     * 订阅用户
     */
    suspend fun subscribeUser(uid: Long): Result<SubscribedUser> {
        return try {
            // 获取用户信息
            val userResult = apiService.getUserInfo(uid)
            if (userResult.isFailure) {
                return Result.failure(userResult.exceptionOrNull() ?: Exception("Failed to get user info"))
            }
            
            val user = userResult.getOrNull()!!
            val subscribedUser = SubscribedUser(
                uid = user. mid,
                name = user.name,
                avatar = user.face,
                signature = user.sign,
                subscribeTime = System.currentTimeMillis()
            )
            
            subscribedUsers[uid] = subscribedUser
            saveSubscribedUsers()
            
            // 立即获取一次动态
            updateUserDynamics(uid)
            
            // 通知订阅变化
            notifySubscriptionChange()
            
            Log.d(TAG, "Successfully subscribed to user: ${user.name} (${uid})")
            Result.success(subscribedUser)
        } catch (e: Exception) {
            Log.e(TAG, "Error subscribing to user: $uid", e)
            Result.failure(e)
        }
    }
    
    /**
     * 取消订阅用户
     */
    fun unsubscribeUser(uid: Long) {
        subscribedUsers.remove(uid)
        cachedDynamics.remove(uid)
        saveSubscribedUsers()
        saveCachedDynamics()
        
        // 通知订阅变化和动态更新
        notifySubscriptionChange()
        notifyDynamicsUpdate()
        
        Log.d(TAG, "Unsubscribed from user: $uid")
    }
    
    /**
     * 获取订阅的用户列表
     */
    fun getSubscribedUsers(): List<SubscribedUser> {
        return subscribedUsers.values.toList().sortedByDescending { it.subscribeTime }
    }
    
    /**
     * 获取所有缓存的动态
     */
    fun getAllDynamics(): List<LocalDynamic> {
        return cachedDynamics.values.flatten()
            .sortedByDescending { it.timestamp }
            .take(100) // 限制最多100条
    }
    
    /**
     * 获取指定用户的动态
     */
    fun getUserDynamics(uid: Long): List<LocalDynamic> {
        return cachedDynamics[uid] ?: emptyList()
    }
    
    /**
     * 更新所有订阅用户的动态
     */
    suspend fun updateAllDynamics(): Result<Unit> {
        return try {
            val jobs = subscribedUsers.keys.map { uid ->
                CoroutineScope(Dispatchers.IO).async {
                    updateUserDynamics(uid)
                }
            }
            
            jobs.awaitAll()
            
            settingsManager.putLong(KEY_LAST_UPDATE_TIME, System.currentTimeMillis())
            notifyDynamicsUpdate()
            
            Log.d(TAG, "Successfully updated all dynamics")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating all dynamics", e)
            Result.failure(e)
        }
    }
    
    /**
     * 更新指定用户的动态
     */
    private suspend fun updateUserDynamics(uid: Long) {
        try {
            val result = apiService.getUserDynamics(uid)
            if (result.isSuccess) {
                val dynamics = result.getOrNull() ?: emptyList()
                cachedDynamics[uid] = dynamics.take(20) // 每个用户最多缓存20条动态
                
                // 更新用户的最后更新时间
                subscribedUsers[uid]?.let { user ->
                    subscribedUsers[uid] = user.copy(lastUpdateTime = System.currentTimeMillis())
                }
                
                Log.d(TAG, "Updated dynamics for user $uid: ${dynamics.size} items")
            } else {
                Log.w(TAG, "Failed to update dynamics for user $uid: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating dynamics for user $uid", e)
        }
    }
    
    /**
     * 检查是否需要更新
     */
    fun shouldUpdate(): Boolean {
        val lastUpdateTime = settingsManager.getLong(KEY_LAST_UPDATE_TIME, 0)
        return System.currentTimeMillis() - lastUpdateTime > UPDATE_INTERVAL
    }
    
    /**
     * 获取下次更新时间
     */
    fun getNextUpdateTime(): Long {
        val lastUpdateTime = settingsManager.getLong(KEY_LAST_UPDATE_TIME, 0)
        return lastUpdateTime + UPDATE_INTERVAL
    }
    
    /**
     * 保存订阅用户列表
     */
    private fun saveSubscribedUsers() {
        try {
            val json = gson.toJson(subscribedUsers.values.toList())
            settingsManager.putString(KEY_SUBSCRIBED_USERS, json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving subscribed users", e)
        }
    }
    
    /**
     * 加载订阅用户列表
     */
    private fun loadSubscribedUsers() {
        try {
            val json = settingsManager.getString(KEY_SUBSCRIBED_USERS, "") ?: ""
            if (json.isNotEmpty()) {
                val type = object : TypeToken<List<SubscribedUser>>() {}.type
                val users: List<SubscribedUser> = gson.fromJson(json, type)
                subscribedUsers.clear()
                users.forEach { user ->
                    subscribedUsers[user.uid] = user
                }
                Log.d(TAG, "Loaded ${users.size} subscribed users")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subscribed users", e)
        }
    }
    
    /**
     * 保存缓存的动态
     */
    private fun saveCachedDynamics() {
        try {
            val allDynamics = cachedDynamics.values.flatten()
            val json = gson.toJson(allDynamics)
            settingsManager.putString(KEY_CACHED_DYNAMICS, json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cached dynamics", e)
        }
    }
    
    /**
     * 加载缓存的动态
     */
    private fun loadCachedDynamics() {
        try {
            val json = settingsManager.getString(KEY_CACHED_DYNAMICS, "") ?: ""
            if (json.isNotEmpty()) {
                val type = object : TypeToken<List<LocalDynamic>>() {}.type
                val dynamics: List<LocalDynamic> = gson.fromJson(json, type)
                
                cachedDynamics.clear()
                dynamics.groupBy { it.uid }.forEach { (uid, userDynamics) ->
                    cachedDynamics[uid] = userDynamics
                }
                
                Log.d(TAG, "Loaded ${dynamics.size} cached dynamics")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached dynamics", e)
        }
    }
    
    /**
     * 通知动态更新
     */
    private fun notifyDynamicsUpdate() {
        val allDynamics = getAllDynamics()
        dynamicsUpdateListeners.forEach { listener ->
            try {
                listener(allDynamics)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying dynamics update listener", e)
            }
        }
        saveCachedDynamics()
    }
    
    /**
     * 通知订阅变化
     */
    private fun notifySubscriptionChange() {
        val users = getSubscribedUsers()
        subscriptionChangeListeners.forEach { listener ->
            try {
                listener(users)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying subscription change listener", e)
            }
        }
    }
}
