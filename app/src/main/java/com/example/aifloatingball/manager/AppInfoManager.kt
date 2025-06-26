package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            
            val loadedApps = resolvedInfos.map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
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