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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.service.MyAccessibilityService

class CrossAppClipboardTestActivity : Activity() {
    
    companion object {
        private const val TAG = "CrossAppClipboardTest"
    }
    
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var instructionText: TextView
    private var clipboardReceiver: BroadcastReceiver? = null
    
    // 状态监控
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private val statusCheckInterval = 2000L // 每2秒检查一次状态
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建滚动布局
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        scrollView.addView(layout)
        
        // 标题
        val titleText = TextView(this).apply {
            text = "跨应用剪贴板监听测试"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(titleText)
        
        // 状态显示
        statusText = TextView(this).apply {
            text = "检查无障碍服务状态..."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)
        
        // 测试说明
        instructionText = TextView(this).apply {
            text = """
📋 双重监听测试步骤：
1. 确保无障碍服务已开启
2. 前台服务会自动启动（双重保障）
3. 点击下方按钮打开其他应用
4. 在目标应用中复制文本
5. 观察灵动岛是否展开
6. 返回此界面查看详细日志

🔥 新特性：前台服务 + 无障碍服务双重监听
⚠️ 重要：保持此界面在后台运行
            """.trimIndent()
            textSize = 14f
            setPadding(0, 0, 0, 16)
            setBackgroundColor(0xFFF0F0F0.toInt())
            setPadding(16, 16, 16, 16)
        }
        layout.addView(instructionText)
        
        // 按钮组
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        // 打开微信按钮
        val wechatButton = Button(this).apply {
            text = "打开微信"
            setOnClickListener { openApp("com.tencent.mm") }
        }
        buttonLayout.addView(wechatButton)
        
        // 打开浏览器按钮
        val browserButton = Button(this).apply {
            text = "打开浏览器"
            setOnClickListener { openBrowser() }
        }
        buttonLayout.addView(browserButton)
        
        layout.addView(buttonLayout)
        
        // 其他测试按钮
        val testButton = Button(this).apply {
            text = "本地测试复制"
            setOnClickListener { testLocalClipboard() }
        }
        layout.addView(testButton)
        
        val clearButton = Button(this).apply {
            text = "清除日志"
            setOnClickListener { clearLog() }
        }
        layout.addView(clearButton)
        
        // 日志显示
        logText = TextView(this).apply {
            text = "等待剪贴板事件...\n"
            textSize = 12f
            setPadding(0, 16, 0, 0)
            setBackgroundColor(0xFF000000.toInt())
            setTextColor(0xFF00FF00.toInt())
            setPadding(8, 8, 8, 8)
        }
        layout.addView(logText)
        
        setContentView(scrollView)
        
        // 初始化
        checkAccessibilityServiceStatus()
        setupClipboardReceiver()
        startStatusMonitoring()
    }
    
    private fun openApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                addLog("🚀 已打开应用: $packageName")
                addLog("💡 现在在该应用中复制文本，然后返回查看结果")
            } else {
                addLog("❌ 应用未安装: $packageName")
                Toast.makeText(this, "应用未安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            addLog("❌ 打开应用失败: ${e.message}")
        }
    }
    
    private fun openBrowser() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://www.baidu.com")
            }
            startActivity(intent)
            addLog("🌐 已打开浏览器")
            addLog("💡 在浏览器中选择并复制文字，然后返回查看结果")
        } catch (e: Exception) {
            addLog("❌ 打开浏览器失败: ${e.message}")
        }
    }
    
    private fun testLocalClipboard() {
        val testText = "跨应用测试文本 ${System.currentTimeMillis()}"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("cross_app_test", testText)
        clipboard.setPrimaryClip(clip)
        
        addLog("✅ 本地测试复制: $testText")
        Log.d(TAG, "本地测试复制: $testText")
    }
    
    private fun clearLog() {
        logText.text = "日志已清除\n"
        addLog("等待剪贴板事件...")
    }
    
    private fun checkAccessibilityServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        statusText.text = if (isEnabled) {
            "✅ 无障碍服务已开启 - 可以进行跨应用测试"
        } else {
            "❌ 无障碍服务未开启 - 请先开启服务"
        }
        
        Log.d(TAG, "无障碍服务状态: $isEnabled")
        addLog("🔍 无障碍服务状态: ${if (isEnabled) "已开启" else "未开启"}")
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val serviceName = "${packageName}/${packageName}.service.MyAccessibilityService"
        return enabledServices?.contains(serviceName) == true
    }
    
    private fun setupClipboardReceiver() {
        clipboardReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    MyAccessibilityService.ACTION_CLIPBOARD_CHANGED -> {
                        val content = intent.getStringExtra(MyAccessibilityService.EXTRA_CLIPBOARD_CONTENT)
                        val message = "🎉 无障碍服务检测成功: $content"
                        addLog(message)
                        Log.d(TAG, message)

                        runOnUiThread {
                            Toast.makeText(this@CrossAppClipboardTestActivity, "无障碍服务监听成功！", Toast.LENGTH_LONG).show()
                        }
                    }
                    com.example.aifloatingball.service.ClipboardForegroundService.ACTION_CLIPBOARD_DETECTED -> {
                        val content = intent.getStringExtra(com.example.aifloatingball.service.ClipboardForegroundService.EXTRA_CLIPBOARD_CONTENT)
                        val message = "🚀 前台服务检测成功: $content"
                        addLog(message)
                        Log.d(TAG, message)

                        runOnUiThread {
                            Toast.makeText(this@CrossAppClipboardTestActivity, "前台服务监听成功！", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(MyAccessibilityService.ACTION_CLIPBOARD_CHANGED)
            addAction(com.example.aifloatingball.service.ClipboardForegroundService.ACTION_CLIPBOARD_DETECTED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(clipboardReceiver!!, filter)

        addLog("📡 双重剪贴板广播接收器已注册（无障碍服务 + 前台服务）")
        Log.d(TAG, "双重剪贴板广播接收器已注册")
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
        addLog("🔄 状态监控已启动")
    }
    
    private fun stopStatusMonitoring() {
        statusCheckRunnable?.let { runnable ->
            statusHandler.removeCallbacks(runnable)
            statusCheckRunnable = null
        }
    }
    
    private fun addLog(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            logText.text = "${logText.text}[$timestamp] $message\n"
            
            // 自动滚动到底部
            val scrollView = logText.parent as? ScrollView
            scrollView?.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkAccessibilityServiceStatus()
        addLog("🔄 界面已恢复，继续监控...")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopStatusMonitoring()
        clipboardReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
    }
}
