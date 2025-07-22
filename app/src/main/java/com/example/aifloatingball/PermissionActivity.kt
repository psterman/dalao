package com.example.aifloatingball

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import androidx.core.content.ContextCompat
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.view.WindowManager
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.SettingsManager

class PermissionActivity : AppCompatActivity() {
    
    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1
        private const val PERMISSIONS_REQUEST_CODE = 2
        private const val TAG = "PermissionActivity"
    }
    
    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "已获取悬浮窗权限，继续检查其他权限")
        } else {
            Log.w(TAG, "用户拒绝了悬浮窗权限，但继续运行")
        }
        checkOtherPermissions()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口完全透明
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawable(null)
        
        // 立即检查权限
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        // 简化权限检查，直接检查其他权限，不强制要求悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission not granted, but continuing")
        }
        checkOtherPermissions()
    }
    
    private fun checkOtherPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        when {
            permissionsToRequest.isEmpty() -> {
                // 所有权限都已授予，启动服务
                startCorrectServiceAndFinish()
            }
            else -> {
                // 请求缺少的权限
                ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toTypedArray(),
                    PERMISSIONS_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Log.d(TAG, "Overlay permission granted")
            } else {
                Log.w(TAG, "Overlay permission denied, but continuing")
            }
            checkOtherPermissions()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                startCorrectServiceAndFinish()
            } else {
                // 即使有权限未授予也启动服务，服务会相应地禁用相关功能
                startCorrectServiceAndFinish()
                Toast.makeText(this, "部分功能可能无法使用", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startCorrectServiceAndFinish() {
        try {
            val settingsManager = SettingsManager.getInstance(this)
            val displayMode = settingsManager.getDisplayMode()
            
            if (displayMode == "floating_ball") {
                val serviceIntent = Intent(this, FloatingWindowService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            } else if (displayMode == "dynamic_island") {
                val serviceIntent = Intent(this, DynamicIslandService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            }
            
            // 启动设置界面
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
            
            // 关闭权限Activity
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "启动服务或Activity失败", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish() // 即使失败也要关闭，避免卡在透明界面
        }
    }
    
    private fun isServiceRunning(): Boolean {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // 使用 UsageStatsManager 替代已弃用的 getRunningServices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val time = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 1000 * 60 * 60 * 24,  // 查询最近24小时
                    time
                )
                if (stats == null || stats.isEmpty()) {
                    Log.w(TAG, "无法获取应用使用统计信息，可能需要额外权限")
                    // 如果无法获取使用统计，回退到传统方法
                    @Suppress("DEPRECATION")
                    val services = activityManager.getRunningServices(Int.MAX_VALUE)
                    return services.any { it.service.className == FloatingWindowService::class.java.name }
                }
                return stats.any { it.packageName == packageName }
            } else {
                @Suppress("DEPRECATION")
                val services = activityManager.getRunningServices(Int.MAX_VALUE)
                return services.any { it.service.className == FloatingWindowService::class.java.name }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查服务状态失败", e)
            return false
        }
    }
} 