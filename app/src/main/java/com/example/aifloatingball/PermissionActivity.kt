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

class PermissionActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1001
        private const val TAG = "PermissionActivity"
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "已获取悬浮窗权限，继续检查其他权限")
            checkAndRequestPermissions()
        } else {
            Log.e(TAG, "用户拒绝了悬浮窗权限")
            Toast.makeText(this, "需要悬浮窗权限才能使用此功能", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)
        
        Log.d(TAG, "开始检查权限")
        window.decorView.post {
            checkAndRequestPermissions()
        }
    }
    
    private fun checkAndRequestPermissions() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "请求悬浮窗权限")
                requestOverlayPermission()
                return
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    Log.d(TAG, "请求通知权限")
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSIONS_REQUEST_CODE
                    )
                    return
                }
            }
            
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
                Log.d(TAG, "请求权限: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSIONS_REQUEST_CODE)
        } else {
                Log.d(TAG, "所有权限已获取，准备启动服务")
            startFloatingService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查权限时发生错误", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "请求悬浮窗权限失败", e)
            Toast.makeText(this, "无法打开悬浮窗权限设置: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
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
                Log.d(TAG, "所有权限已获取，准备启动服务")
                startFloatingService()
            } else {
                Log.e(TAG, "部分权限被拒绝")
                val deniedPermissions = permissions.filterIndexed { index, _ -> 
                    grantResults[index] != PackageManager.PERMISSION_GRANTED 
                }
                Log.e(TAG, "被拒绝的权限: $deniedPermissions")
                Toast.makeText(this, "部分功能可能无法正常使用", Toast.LENGTH_LONG).show()
                if (Settings.canDrawOverlays(this)) {
                    startFloatingService()
                } else {
                    finish()
                }
            }
        }
    }
    
    private fun startFloatingService() {
        try {
            Log.d(TAG, "开始启动悬浮窗服务")
            
            // 检查所有必要权限
            val hasOverlayPermission = Settings.canDrawOverlays(this)
            val hasNotificationPermission = NotificationManagerCompat.from(this).areNotificationsEnabled()
            
            Log.d(TAG, "权限状态检查：" +
                "\n悬浮窗权限: $hasOverlayPermission" +
                "\n通知权限: $hasNotificationPermission")
            
            if (!hasOverlayPermission) {
                Log.e(TAG, "缺少悬浮窗权限")
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return
            }
            
            val serviceIntent = Intent(this, FloatingWindowService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "使用 startForegroundService 启动服务")
                startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "使用 startService 启动服务")
                startService(serviceIntent)
            }
            
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isServiceRunning()) {
                    Log.e(TAG, "服务启动失败，请检查系统日志")
                    Toast.makeText(this, "服务启动失败，请重试", Toast.LENGTH_LONG).show()
                } else {
                    Log.d(TAG, "服务已成功启动")
                }
                finish()
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "启动服务失败", e)
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
        finish()
        }
    }
    
    private fun isServiceRunning(): Boolean {
        try {
            // 使用 Context.ACTIVITY_SERVICE 获取 ActivityManager
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 检查服务是否在前台运行
            val foregroundServices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.getAppTasks()
                    .flatMap { it.taskInfo.topActivity?.className?.let(::listOf) ?: emptyList() }
            } else {
                @Suppress("DEPRECATION")
                manager.getRunningServices(Integer.MAX_VALUE)
                    .map { it.service.className }
            }
            
            return foregroundServices.any { it == FloatingWindowService::class.java.name }
        } catch (e: Exception) {
            Log.e(TAG, "检查服务状态失败", e)
        }
        return false
    }
} 