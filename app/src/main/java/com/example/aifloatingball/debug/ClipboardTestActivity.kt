package com.example.aifloatingball.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.R
import com.example.aifloatingball.service.MyAccessibilityService

class ClipboardTestActivity : Activity() {
    
    companion object {
        private const val TAG = "ClipboardTestActivity"
    }
    
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private var clipboardReceiver: BroadcastReceiver? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建简单的测试界面
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // 状态显示
        statusText = TextView(this).apply {
            text = "检查无障碍服务状态..."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)
        
        // 开启无障碍服务按钮
        val enableButton = Button(this).apply {
            text = "开启无障碍服务"
            setOnClickListener { openAccessibilitySettings() }
        }
        layout.addView(enableButton)
        
        // 测试剪贴板按钮
        val testButton = Button(this).apply {
            text = "测试剪贴板监听"
            setOnClickListener { testClipboard() }
        }
        layout.addView(testButton)
        
        // 日志显示
        logText = TextView(this).apply {
            text = "等待剪贴板事件...\n"
            textSize = 12f
            setPadding(0, 16, 0, 0)
            setBackgroundColor(0xFF000000.toInt())
            setTextColor(0xFF00FF00.toInt())
        }
        layout.addView(logText)
        
        setContentView(layout)
        
        // 检查无障碍服务状态
        checkAccessibilityServiceStatus()
        
        // 注册剪贴板广播接收器
        setupClipboardReceiver()
    }
    
    private fun checkAccessibilityServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        statusText.text = if (isEnabled) {
            "✅ 无障碍服务已开启"
        } else {
            "❌ 无障碍服务未开启"
        }
        
        Log.d(TAG, "无障碍服务状态: $isEnabled")
        addLog("无障碍服务状态: $isEnabled")
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val serviceName = "${packageName}/${packageName}.service.MyAccessibilityService"
        return enabledServices?.contains(serviceName) == true
    }
    
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "打开无障碍设置失败", e)
        }
    }
    
    private fun testClipboard() {
        val testText = "测试文本 ${System.currentTimeMillis()}"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("test", testText)
        clipboard.setPrimaryClip(clip)
        
        addLog("已复制测试文本: $testText")
        Log.d(TAG, "已复制测试文本: $testText")
    }
    
    private fun setupClipboardReceiver() {
        clipboardReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MyAccessibilityService.ACTION_CLIPBOARD_CHANGED) {
                    val content = intent.getStringExtra(MyAccessibilityService.EXTRA_CLIPBOARD_CONTENT)
                    val message = "📋 收到剪贴板广播: $content"
                    addLog(message)
                    Log.d(TAG, message)
                    
                    Toast.makeText(this@ClipboardTestActivity, "剪贴板监听正常工作!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        val filter = IntentFilter(MyAccessibilityService.ACTION_CLIPBOARD_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(clipboardReceiver!!, filter)
        
        addLog("剪贴板广播接收器已注册")
        Log.d(TAG, "剪贴板广播接收器已注册")
    }
    
    private fun addLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            logText.text = "${logText.text}[$timestamp] $message\n"
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 重新检查状态
        checkAccessibilityServiceStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        clipboardReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
    }
}
