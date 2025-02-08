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
            checkOtherPermissions()
        } else {
            Log.e(TAG, "用户拒绝了悬浮窗权限")
            Toast.makeText(this, "需要悬浮窗权限才能使用此功能", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            // 请求悬浮窗权限
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            checkOtherPermissions()
        }
    }
    
    private fun checkOtherPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        when {
            permissionsToRequest.isEmpty() -> {
                // 所有权限都已授予，启动服务
                startFloatingService()
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
                checkOtherPermissions()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能运行", Toast.LENGTH_LONG).show()
                finish()
            }
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
                startFloatingService()
            } else {
                // 即使有权限未授予也启动服务，服务会相应地禁用相关功能
                startFloatingService()
                Toast.makeText(this, "部分功能可能无法使用", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startFloatingService() {
        try {
            Log.d(TAG, "开始启动悬浮窗服务")
            
            // 检查悬浮窗权限
            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "缺少悬浮窗权限")
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions()
                return
            }
            
            val serviceIntent = Intent(this, FloatingWindowService::class.java).apply {
                action = "restart_foreground"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "使用 startForegroundService 启动服务")
                startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "使用 startService 启动服务")
                startService(serviceIntent)
            }
            
            // 延迟检查服务是否成功启动
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isServiceRunning()) {
                    Log.e(TAG, "服务启动失败")
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
        return try {
            val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == FloatingWindowService::class.java.name }
        } catch (e: Exception) {
            Log.e(TAG, "检查服务状态失败", e)
            false
        }
    }
} 