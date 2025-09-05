package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.aifloatingball.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppInfoManager private constructor() {

    private var appList: List<AppInfo> = emptyList()
    private var isLoaded = false

    fun loadApps(context: Context) {
        if (isLoaded) return
        CoroutineScope(Dispatchers.IO).launch {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
            
            val loadedApps = resolvedInfos.map { resolvedInfo ->
                val packageName = resolvedInfo.activityInfo.packageName
                val urlScheme = getUrlScheme(pm, packageName)
                
                // 使用与简易模式相同的图标加载策略
                val icon = try {
                    // 首先尝试从PackageManager直接加载应用图标
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    android.util.Log.w("AppInfoManager", "无法加载应用图标: $packageName", e)
                    try {
                        // 尝试从resolvedInfo加载
                        resolvedInfo.loadIcon(pm)
                    } catch (e2: Exception) {
                        android.util.Log.w("AppInfoManager", "resolvedInfo也无法加载图标: $packageName", e2)
                        // 最后使用系统默认图标
                        pm.getDefaultActivityIcon()
                    }
                }
                
                AppInfo(
                    label = resolvedInfo.loadLabel(pm).toString(),
                    packageName = packageName,
                    icon = icon,
                    urlScheme = urlScheme
                )
            }.sortedBy { it.label }
            
            withContext(Dispatchers.Main) {
                appList = loadedApps
                isLoaded = true
            }
        }
    }

    fun search(query: String): List<AppInfo> {
        if (query.isBlank() || !isLoaded) {
            return emptyList()
        }
        val lowerCaseQuery = query.toLowerCase()
        return appList.filter {
            it.label.toLowerCase().contains(lowerCaseQuery)
        }
    }
    
    fun isLoaded(): Boolean = isLoaded
    
    private fun getUrlScheme(pm: PackageManager, packageName: String): String? {
        return try {
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val activities = packageInfo.activities
            
            if (activities != null) {
                for (activityInfo in activities) {
                    val intentFilters = pm.queryIntentActivities(
                        Intent().setClassName(packageName, activityInfo.name),
                        PackageManager.GET_INTENT_FILTERS
                    )
                    
                    for (resolveInfo in intentFilters) {
                        resolveInfo.filter?.let { filter ->
                            val schemes = filter.schemesIterator()
                            while (schemes.hasNext()) {
                                val scheme = schemes.next()
                                if (scheme.isNotEmpty()) {
                                    return scheme
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: AppInfoManager? = null

        fun getInstance(): AppInfoManager {
            return instance ?: synchronized(this) {
                instance ?: AppInfoManager().also { instance = it }
            }
        }
    }
} 