package com.example.aifloatingball.video

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import java.io.File

/**
 * 视频预加载管理器
 * 
 * 功能：
 * - 预加载下一个视频
 * - 智能缓存策略
 * - 网络状态监听
 * - 自适应码率
 * 
 * @author AI Floating Ball
 */
@UnstableApi
class VideoPreloader(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoPreloader"
        private const val CACHE_SIZE = 100 * 1024 * 1024L // 100MB 缓存
        private const val PRELOAD_SIZE = 5 * 1024 * 1024L // 预加载 5MB
    }
    
    // 缓存实例
    private var cache: Cache? = null
    
    // 网络状态监听
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // 当前网络类型
    private var currentNetworkType: NetworkType = NetworkType.UNKNOWN
    
    enum class NetworkType {
        WIFI,
        MOBILE,
        UNKNOWN
    }
    
    /**
     * 网络状态变化监听器
     */
    interface NetworkStateListener {
        fun onNetworkChanged(networkType: NetworkType)
        fun onNetworkLost()
    }
    
    private var networkStateListener: NetworkStateListener? = null
    
    init {
        initializeCache()
        initializeNetworkMonitoring()
    }
    
    /**
     * 初始化缓存
     */
    private fun initializeCache() {
        try {
            val cacheDir = File(context.cacheDir, "video_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            cache = SimpleCache(cacheDir, evictor, androidx.media3.database.StandaloneDatabaseProvider(context))
            Log.d(TAG, "视频缓存已初始化: ${cacheDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化缓存失败", e)
        }
    }
    
    /**
     * 初始化网络监听
     */
    private fun initializeNetworkMonitoring() {
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateNetworkType()
                    Log.d(TAG, "网络已连接: $currentNetworkType")
                }
                
                override fun onLost(network: Network) {
                    currentNetworkType = NetworkType.UNKNOWN
                    networkStateListener?.onNetworkLost()
                    Log.d(TAG, "网络已断开")
                }
                
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    updateNetworkType()
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
            
            // 初始化当前网络类型
            updateNetworkType()
        } catch (e: Exception) {
            Log.e(TAG, "初始化网络监听失败", e)
        }
    }
    
    /**
     * 更新网络类型
     */
    private fun updateNetworkType() {
        try {
            val network = connectivityManager?.activeNetwork
            val capabilities = connectivityManager?.getNetworkCapabilities(network)
            
            currentNetworkType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.MOBILE
                else -> NetworkType.UNKNOWN
            }
            
            networkStateListener?.onNetworkChanged(currentNetworkType)
        } catch (e: Exception) {
            Log.e(TAG, "更新网络类型失败", e)
        }
    }
    
    /**
     * 设置网络状态监听器
     */
    fun setNetworkStateListener(listener: NetworkStateListener) {
        this.networkStateListener = listener
    }
    
    /**
     * 获取当前网络类型
     */
    fun getCurrentNetworkType(): NetworkType {
        return currentNetworkType
    }
    
    /**
     * 是否为 WiFi 网络
     */
    fun isWiFi(): Boolean {
        return currentNetworkType == NetworkType.WIFI
    }
    
    /**
     * 是否为移动网络
     */
    fun isMobile(): Boolean {
        return currentNetworkType == NetworkType.MOBILE
    }
    
    /**
     * 是否有网络连接
     */
    fun isNetworkAvailable(): Boolean {
        return currentNetworkType != NetworkType.UNKNOWN
    }
    
    /**
     * 创建缓存数据源工厂
     */
    fun createCacheDataSourceFactory(): CacheDataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)
        
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        
        return CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
    
    /**
     * 预加载视频
     */
    fun preloadVideo(videoUrl: String) {
        try {
            // 只在 WiFi 下预加载
            if (!isWiFi()) {
                Log.d(TAG, "非 WiFi 网络，跳过预加载")
                return
            }
            
            Log.d(TAG, "开始预加载视频: $videoUrl")
            
            // TODO: 实现预加载逻辑
            // 可以使用 ExoPlayer 的 CacheDataSource 预先下载部分数据
            
        } catch (e: Exception) {
            Log.e(TAG, "预加载视频失败", e)
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        return try {
            cache?.cacheSpace ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "获取缓存大小失败", e)
            0L
        }
    }
    
    /**
     * 清空缓存
     */
    fun clearCache() {
        try {
            cache?.keys?.forEach { key ->
                cache?.removeResource(key)
            }
            Log.d(TAG, "缓存已清空")
        } catch (e: Exception) {
            Log.e(TAG, "清空缓存失败", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
            cache?.release()
            Log.d(TAG, "资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
    }
    
    /**
     * 格式化缓存大小
     */
    fun formatCacheSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
