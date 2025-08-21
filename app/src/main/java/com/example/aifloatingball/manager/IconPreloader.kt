package com.example.aifloatingball.manager

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.utils.IconProcessor
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 图标预加载管理器
 * 用于提前加载和缓存应用图标，提升首次显示速度
 */
class IconPreloader private constructor(private val context: Context) {
    
    private val iconManager = AppIconManager.getInstance(context)
    private val iconProcessor = IconProcessor(context)
    private val preloadedIcons = ConcurrentHashMap<String, Drawable>()
    private val preloadingJobs = ConcurrentHashMap<String, Job>()
    private val preloadProgress = AtomicInteger(0)
    
    companion object {
        private const val TAG = "IconPreloader"
        private const val MAX_CONCURRENT_PRELOADS = 3 // 最大并发预加载数
        
        @Volatile
        private var instance: IconPreloader? = null
        
        fun getInstance(context: Context): IconPreloader {
            return instance ?: synchronized(this) {
                instance ?: IconPreloader(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 预加载热门应用图标
     */
    suspend fun preloadPopularApps(apps: List<AppSearchConfig>, onProgress: ((Int, Int) -> Unit)? = null) {
        Log.d(TAG, "开始预加载${apps.size}个热门应用图标")
        
        val totalApps = apps.size
        preloadProgress.set(0)
        
        // 按优先级排序 - 热门应用优先
        val sortedApps = apps.sortedBy { getPriorityScore(it) }
        
        // 分批预加载，避免同时发起太多网络请求
        sortedApps.chunked(MAX_CONCURRENT_PRELOADS).forEach { batch ->
            coroutineScope {
                val jobs = batch.map { app ->
                    async(Dispatchers.IO) {
                        preloadSingleApp(app)
                        val progress = preloadProgress.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(progress, totalApps)
                        }
                    }
                }

                // 等待当前批次完成再处理下一批
                jobs.awaitAll()
            }

            // 短暂延迟，避免API调用过于频繁
            delay(200)
        }
        
        Log.d(TAG, "预加载完成，成功缓存${preloadedIcons.size}个图标")
    }
    
    /**
     * 预加载单个应用图标
     */
    private suspend fun preloadSingleApp(app: AppSearchConfig) {
        val cacheKey = "${app.packageName}_${app.appName}"
        
        // 检查是否已经预加载
        if (preloadedIcons.containsKey(cacheKey)) {
            return
        }
        
        // 检查是否正在预加载
        preloadingJobs[cacheKey]?.let { job ->
            job.join()
            return
        }
        
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                iconManager.getAppIconAsync(
                    packageName = app.packageName,
                    appName = app.appName
                ) { downloadedIcon ->
                    if (downloadedIcon != null) {
                        // 处理图标，统一外观
                        val processedIcon = iconProcessor.processIcon(
                            downloadedIcon, 
                            IconProcessor.IconStyle.ROUNDED_SQUARE
                        )
                        
                        if (processedIcon != null) {
                            preloadedIcons[cacheKey] = processedIcon
                            Log.d(TAG, "✅ 预加载成功: ${app.appName}")
                        }
                    } else {
                        Log.d(TAG, "❌ 预加载失败: ${app.appName}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "预加载异常: ${app.appName} - ${e.message}")
            } finally {
                preloadingJobs.remove(cacheKey)
            }
        }
        
        preloadingJobs[cacheKey] = job
        // 异步等待，不阻塞调用线程
        job.join()
    }
    
    /**
     * 获取预加载的图标
     */
    fun getPreloadedIcon(packageName: String, appName: String): Drawable? {
        val cacheKey = "${packageName}_${appName}"
        return preloadedIcons[cacheKey]
    }
    
    /**
     * 检查图标是否已预加载
     */
    fun isIconPreloaded(packageName: String, appName: String): Boolean {
        val cacheKey = "${packageName}_${appName}"
        return preloadedIcons.containsKey(cacheKey)
    }
    
    /**
     * 获取应用的优先级分数 (分数越低优先级越高)
     */
    private fun getPriorityScore(app: AppSearchConfig): Int {
        return when (app.packageName) {
            // 超高优先级 - 最常用的应用
            "com.tencent.mm",                    // 微信
            "com.eg.android.AlipayGphone",       // 支付宝
            "com.tencent.qqmusic",              // QQ音乐
            "com.netease.cloudmusic"            // 网易云音乐
            -> 1
            
            // 高优先级 - 热门应用
            "me.ele",                           // 饿了么
            "com.douban.frodo",                 // 豆瓣
            "com.autonavi.minimap",             // 高德地图
            "com.baidu.BaiduMap",               // 百度地图
            "com.sdu.didi.psnger",              // 滴滴出行
            "com.taobao.taobao"                 // 淘宝
            -> 2
            
            // 中等优先级 - 常用应用
            "com.UCMobile",                     // UC浏览器
            "com.MobileTicket",                 // 12306
            "ctrip.android.view",               // 携程
            "com.Qunar"                         // 去哪儿
            -> 3
            
            // 低优先级 - 其他应用
            else -> 4
        }
    }
    
    /**
     * 预加载指定分类的应用
     */
    suspend fun preloadCategoryApps(
        apps: List<AppSearchConfig>, 
        onProgress: ((Int, Int) -> Unit)? = null
    ) {
        Log.d(TAG, "开始预加载分类应用图标: ${apps.size}个")
        
        val unloadedApps = apps.filter { app ->
            val cacheKey = "${app.packageName}_${app.appName}"
            !preloadedIcons.containsKey(cacheKey)
        }
        
        if (unloadedApps.isEmpty()) {
            Log.d(TAG, "所有图标已预加载，跳过")
            return
        }
        
        preloadPopularApps(unloadedApps, onProgress)
    }
    
    /**
     * 清理预加载缓存
     */
    fun clearPreloadCache() {
        Log.d(TAG, "清理预加载缓存: ${preloadedIcons.size}个图标")
        preloadedIcons.clear()
        
        // 取消所有正在进行的预加载任务
        preloadingJobs.values.forEach { job ->
            job.cancel()
        }
        preloadingJobs.clear()
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            preloadedCount = preloadedIcons.size,
            preloadingCount = preloadingJobs.size,
            totalMemoryUsage = estimateMemoryUsage()
        )
    }
    
    /**
     * 估算内存使用量 (KB)
     */
    private fun estimateMemoryUsage(): Long {
        // 假设每个图标平均占用 20KB (96x96 ARGB_8888)
        return preloadedIcons.size * 20L
    }
    
    /**
     * 智能预加载 - 根据用户使用习惯预加载
     */
    suspend fun smartPreload(
        recentlyUsedApps: List<AppSearchConfig>,
        frequentlyUsedApps: List<AppSearchConfig>,
        onProgress: ((Int, Int) -> Unit)? = null
    ) {
        val smartList = mutableListOf<AppSearchConfig>()
        
        // 1. 最近使用的应用 (最高优先级)
        smartList.addAll(recentlyUsedApps.take(5))
        
        // 2. 频繁使用的应用
        smartList.addAll(frequentlyUsedApps.take(10))
        
        // 3. 热门应用 (如果还没包含)
        val popularPackages = setOf(
            "com.tencent.mm", "com.eg.android.AlipayGphone", 
            "com.tencent.qqmusic", "com.netease.cloudmusic"
        )
        
        // 去重并按优先级排序
        val uniqueApps = smartList.distinctBy { it.packageName }
            .sortedBy { getPriorityScore(it) }
        
        Log.d(TAG, "智能预加载: ${uniqueApps.size}个应用")
        preloadPopularApps(uniqueApps, onProgress)
    }
    
    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val preloadedCount: Int,
        val preloadingCount: Int,
        val totalMemoryUsage: Long // KB
    )
}
