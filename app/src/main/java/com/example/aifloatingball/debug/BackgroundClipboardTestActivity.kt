package com.example.aifloatingball.debug

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.service.MyAccessibilityService
import com.example.aifloatingball.service.ClipboardForegroundService
import java.text.SimpleDateFormat
import java.util.*

/**
 * 后台剪贴板监听突破测试工具
 * 专门测试应用在后台时的剪贴板监听能力
 */
class BackgroundClipboardTestActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BackgroundClipboardTest"
    }
    
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var clipboardManager: ClipboardManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private var testRunnable: Runnable? = null
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_clipboard_test)
        
        initViews()
        initClipboard()
        startStatusMonitoring()
        
        Log.d(TAG, "后台剪贴板测试工具启动")
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        scrollView = findViewById(R.id.scrollView)
        
        findViewById<Button>(R.id.btnStartTest).setOnClickListener {
            startBackgroundTest()
        }
        
        findViewById<Button>(R.id.btnStopTest).setOnClickListener {
            stopBackgroundTest()
        }
        
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            clearLog()
        }
        
        findViewById<Button>(R.id.btnOpenWechat).setOnClickListener {
            openApp("com.tencent.mm", "微信")
        }
        
        findViewById<Button>(R.id.btnOpenBrowser).setOnClickListener {
            openApp("com.android.chrome", "Chrome浏览器")
        }
        
        findViewById<Button>(R.id.btnSimulateCopy).setOnClickListener {
            simulateCopy()
        }
        
        findViewById<Button>(R.id.btnCheckServices).setOnClickListener {
            checkServicesStatus()
        }
    }
    
    private fun initClipboard() {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    
    private fun startStatusMonitoring() {
        statusCheckRunnable = object : Runnable {
            override fun run() {
                updateStatus()
                handler.postDelayed(this, 2000) // 每2秒更新状态
            }
        }
        handler.post(statusCheckRunnable!!)
    }
    
    private fun updateStatus() {
        val accessibilityStatus = if (MyAccessibilityService.isRunning()) "✅ 运行中" else "❌ 未运行"
        val foregroundServiceStatus = if (isServiceRunning(ClipboardForegroundService::class.java)) "✅ 运行中" else "❌ 未运行"
        val appStatus = if (isAppInBackground()) "🔥 后台模式" else "✅ 前台模式"
        
        val status = """
            📊 服务状态监控 (${dateFormat.format(Date())})
            
            🔧 无障碍服务: $accessibilityStatus
            🚀 前台服务: $foregroundServiceStatus
            📱 应用状态: $appStatus
            
            💡 测试说明:
            1. 点击"开始后台测试"启动自动测试
            2. 切换到其他应用（微信/浏览器）
            3. 复制任意文本
            4. 观察灵动岛是否展开
            5. 返回查看检测日志
        """.trimIndent()
        
        statusText.text = status
    }
    
    private fun startBackgroundTest() {
        addLog("🚀 开始后台剪贴板监听测试")
        addLog("📝 测试步骤:")
        addLog("1. 保持此界面在后台")
        addLog("2. 切换到其他应用")
        addLog("3. 复制任意文本")
        addLog("4. 观察灵动岛是否展开")
        addLog("5. 返回查看结果")
        addLog("━━━━━━━━━━━━━━━━━━━━")
        
        // 启动自动测试
        testRunnable = object : Runnable {
            override fun run() {
                performBackgroundTest()
                handler.postDelayed(this, 5000) // 每5秒执行一次测试
            }
        }
        handler.postDelayed(testRunnable!!, 2000)
    }
    
    private fun stopBackgroundTest() {
        testRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            testRunnable = null
        }
        addLog("🛑 后台测试已停止")
    }
    
    private fun performBackgroundTest() {
        if (isAppInBackground()) {
            val testContent = "后台测试-${System.currentTimeMillis()}"
            clipboardManager.setPrimaryClip(ClipData.newPlainText("test", testContent))
            addLog("🔥 [后台模式] 模拟复制: $testContent")
        } else {
            addLog("📱 [前台模式] 等待应用进入后台...")
        }
    }
    
    private fun simulateCopy() {
        val testContent = "手动测试-${dateFormat.format(Date())}"
        clipboardManager.setPrimaryClip(ClipData.newPlainText("manual_test", testContent))
        addLog("✋ 手动模拟复制: $testContent")
    }
    
    private fun checkServicesStatus() {
        addLog("🔍 检查服务状态:")
        addLog("  无障碍服务: ${if (MyAccessibilityService.isRunning()) "✅ 正常" else "❌ 异常"}")
        addLog("  前台服务: ${if (isServiceRunning(ClipboardForegroundService::class.java)) "✅ 正常" else "❌ 异常"}")
        addLog("  应用状态: ${if (isAppInBackground()) "🔥 后台" else "✅ 前台"}")
    }
    
    private fun openApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                addLog("📱 正在打开 $appName")
                addLog("💡 请在 $appName 中复制任意文本测试")
            } else {
                addLog("❌ 未找到 $appName 应用")
            }
        } catch (e: Exception) {
            addLog("❌ 打开 $appName 失败: ${e.message}")
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    private fun isAppInBackground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningTasks = activityManager.getRunningTasks(1)
            runningTasks.isNotEmpty() && 
                !runningTasks[0].topActivity?.packageName.equals(packageName)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun addLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message\n"
        
        runOnUiThread {
            logText.append(logMessage)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
        
        Log.d(TAG, message)
    }
    
    private fun clearLog() {
        logText.text = ""
        addLog("📝 日志已清空")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        statusCheckRunnable?.let { handler.removeCallbacks(it) }
        testRunnable?.let { handler.removeCallbacks(it) }
    }
}
