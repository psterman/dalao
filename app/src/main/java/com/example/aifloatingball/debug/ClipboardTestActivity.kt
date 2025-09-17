package com.example.aifloatingball.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    // 添加状态监控
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private val statusCheckInterval = 3000L // 每3秒检查一次状态
    
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

        // 测试其他应用剪贴板按钮
        val testOtherButton = Button(this).apply {
            text = "模拟其他应用复制"
            setOnClickListener { simulateOtherAppCopy() }
        }
        layout.addView(testOtherButton)

        // 清除日志按钮
        val clearLogButton = Button(this).apply {
            text = "清除日志"
            setOnClickListener { clearLog() }
        }
        layout.addView(clearLogButton)

        // 强制检查服务状态按钮
        val forceCheckButton = Button(this).apply {
            text = "强制检查服务状态"
            setOnClickListener { forceCheckServiceStatus() }
        }
        layout.addView(forceCheckButton)
        
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

        // 启动状态监控
        startStatusMonitoring()
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

        addLog("✅ 已复制测试文本: $testText")
        Log.d(TAG, "已复制测试文本: $testText")
    }

    private fun simulateOtherAppCopy() {
        val testTexts = listOf(
            "普通文本内容",
            "这是一段中文测试文本",
            "Mixed English and 中文 content",
            "https://www.example.com", // 这个应该被过滤
            "123456", // 这个应该被过滤
            "Hello World! 这是一个测试。"
        )

        val randomText = testTexts.random()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("other_app", randomText)
        clipboard.setPrimaryClip(clip)

        addLog("🔄 模拟其他应用复制: $randomText")
        Log.d(TAG, "模拟其他应用复制: $randomText")
    }

    private fun clearLog() {
        logText.text = "日志已清除\n"
        addLog("等待剪贴板事件...")
    }

    private fun forceCheckServiceStatus() {
        addLog("🔍 强制检查服务状态...")
        checkAccessibilityServiceStatus()

        // 尝试访问剪贴板来测试服务
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = clipboard.primaryClip
            val content = if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.toString() ?: "null"
            } else {
                "empty"
            }
            addLog("📋 当前剪贴板内容: ${content.take(30)}${if (content.length > 30) "..." else ""}")
        } catch (e: Exception) {
            addLog("❌ 剪贴板访问失败: ${e.message}")
        }
    }

    private fun startStatusMonitoring() {
        stopStatusMonitoring()

        statusCheckRunnable = object : Runnable {
            override fun run() {
                checkAccessibilityServiceStatus()
                statusHandler.postDelayed(this, statusCheckInterval)
            }
        }

        statusHandler.postDelayed(statusCheckRunnable!!, statusCheckInterval)
        addLog("🔄 状态监控已启动，间隔: ${statusCheckInterval}ms")
    }

    private fun stopStatusMonitoring() {
        statusCheckRunnable?.let { runnable ->
            statusHandler.removeCallbacks(runnable)
            statusCheckRunnable = null
        }
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
        stopStatusMonitoring()
        clipboardReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
    }
}
