package com.example.aifloatingball.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.concurrent.atomic.AtomicBoolean

class MyAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "MyAccessibilityService"
        const val ACTION_CLIPBOARD_CHANGED = "com.example.aifloatingball.ACTION_CLIPBOARD_CHANGED"
        const val EXTRA_CLIPBOARD_CONTENT = "clipboard_content"
        const val ACTION_AUTO_PASTE = "com.example.aifloatingball.AUTO_PASTE"

        // 调试模式：设置为true时放宽过滤条件
        private const val DEBUG_MODE = true

        // 服务实例引用
        private var serviceInstance: MyAccessibilityService? = null

        /**
         * 获取服务实例
         */
        fun getInstance(): MyAccessibilityService? {
            return serviceInstance
        }

        /**
         * 检查无障碍服务是否正在运行
         */
        fun isRunning(): Boolean {
            return serviceInstance != null && serviceInstance?.isServiceActive?.get() == true
        }
    }

    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboardContent: String? = null
    private var lastClipboardChangeTime = 0L
    private val clipboardChangeDebounceTime = 1000L // 防抖时间：1秒

    // 添加主线程Handler和定时检查机制
    private val mainHandler = Handler(Looper.getMainLooper())
    private var periodicCheckRunnable: Runnable? = null
    private val periodicCheckInterval = 1000L // 每1秒检查一次剪贴板（更频繁）

    // 激进模式：更频繁的检查
    private var aggressiveCheckRunnable: Runnable? = null
    private val aggressiveCheckInterval = 200L // 每0.2秒检查一次（激进模式）
    private var isAggressiveModeEnabled = true // 启用激进模式

    // 自动粘贴相关变量
    private var autoPasteReceiver: BroadcastReceiver? = null
    private var pendingAutoPaste: AutoPasteRequest? = null
    private var autoPasteRetryCount = 0
    private val maxAutoPasteRetries = 5
    private val autoPasteDelay = 2000L // 2秒延迟

    // 自动粘贴请求数据类
    data class AutoPasteRequest(
        val packageName: String,
        val query: String,
        val appName: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // 超级激进模式（后台时启用）
    private var superAggressiveCheckRunnable: Runnable? = null
    private val superAggressiveCheckInterval = 50L // 每50ms检查一次（超级激进）
    private var isSuperAggressiveModeEnabled = false

    // 应用状态监控
    private var isAppInBackground = false
    private val backgroundCheckHandler = Handler(Looper.getMainLooper())
    private var backgroundCheckRunnable: Runnable? = null

    // 服务状态监控
    private val isServiceActive = AtomicBoolean(false)
    private var serviceStatusCheckRunnable: Runnable? = null
    private val serviceStatusCheckInterval = 5000L // 每5秒检查服务状态

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val currentTime = System.currentTimeMillis()
        val currentApp = getCurrentAppPackageName()
        Log.d(TAG, "🔔 剪贴板监听器触发 - 时间: $currentTime, 当前应用: $currentApp, 服务状态: ${isServiceActive.get()}")

        if (isServiceActive.get()) {
            handleClipboardChange()
        } else {
            Log.w(TAG, "⚠️ 服务未激活，尝试重新初始化")
            reinitializeService()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "🚀 无障碍服务连接中...")
        serviceInstance = this
        initializeService()
    }

    private fun initializeService() {
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)

            // 初始化当前剪贴板内容
            updateLastClipboardContent()

            // 启动定期检查机制作为备用方案
            startPeriodicClipboardCheck()

            // 启动激进模式检查（更频繁）
            if (isAggressiveModeEnabled) {
                startAggressiveClipboardCheck()
            }

            // 注册自动粘贴广播接收器
            registerAutoPasteReceiver()

            // 启动服务状态监控
            startServiceStatusCheck()

            // 启动应用状态监控
            startBackgroundMonitoring()

            // 标记服务为活跃状态
            isServiceActive.set(true)

            Log.d(TAG, "✅ 无障碍服务已连接，剪贴板监听器已初始化，定期检查已启动")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化无障碍服务失败", e)
            isServiceActive.set(false)
        }
    }

    private fun reinitializeService() {
        Log.d(TAG, "🔄 重新初始化无障碍服务...")
        try {
            // 清理旧的监听器
            if (::clipboardManager.isInitialized) {
                clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            }

            // 重新初始化
            initializeService()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重新初始化失败", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听特定的无障碍事件，作为剪贴板检查的触发器
        event?.let {
            val packageName = it.packageName?.toString() ?: "unknown"
            val eventTypeName = getEventTypeName(it.eventType)

            Log.v(TAG, "📱 无障碍事件: $eventTypeName, 应用: $packageName")

            when (it.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // 这些事件可能伴随剪贴板操作，延迟检查剪贴板
                    mainHandler.postDelayed({
                        checkClipboardChange("accessibility_event:$eventTypeName")
                    }, 300) // 缩短延迟时间
                }
                else -> {
                    // 其他事件类型暂不处理
                }
            }
        }
    }

    /**
     * 获取事件类型名称，用于调试
     */
    private fun getEventTypeName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            else -> "TYPE_$eventType"
        }
    }

    private fun handleClipboardChange() {
        checkClipboardChange("clipboard_listener")
    }

    /**
     * 检查剪贴板变化的通用方法
     * @param source 触发源，用于日志标识
     */
    private fun checkClipboardChange(source: String) {
        try {
            val currentTime = System.currentTimeMillis()

            // 防抖处理：如果距离上次变化时间太短，忽略
            if (currentTime - lastClipboardChangeTime < clipboardChangeDebounceTime) {
                Log.d(TAG, "[$source] 剪贴板变化过于频繁，忽略此次变化 (${currentTime - lastClipboardChangeTime}ms)")
                return
            }

            val currentContent = getCurrentClipboardContent()

            // 内容验证（调试模式下放宽条件）
            if (currentContent != null &&
                currentContent.isNotEmpty() &&
                currentContent != lastClipboardContent) {

                val isValid = if (DEBUG_MODE) {
                    // 调试模式：只检查基本条件
                    currentContent.length >= 1 && currentContent.trim().isNotEmpty()
                } else {
                    // 正常模式：严格验证
                    isValidClipboardContent(currentContent) && isUserGeneratedClipboard(currentContent)
                }

                if (isValid) {
                    Log.d(TAG, "✅ [$source] 检测到有效的剪贴板内容变化: ${currentContent.take(50)}${if (currentContent.length > 50) "..." else ""}")

                    // 更新时间和内容
                    lastClipboardChangeTime = currentTime
                    lastClipboardContent = currentContent

                    // 通知DynamicIslandService展开灵动岛
                    notifyClipboardChanged(currentContent)
                } else {
                    Log.d(TAG, "❌ [$source] 剪贴板内容未通过验证")
                }
            } else {
                if (currentContent == null) {
                    Log.d(TAG, "❌ [$source] 剪贴板内容为null")
                } else if (currentContent.isEmpty()) {
                    Log.d(TAG, "❌ [$source] 剪贴板内容为空")
                } else if (currentContent == lastClipboardContent) {
                    Log.d(TAG, "❌ [$source] 剪贴板内容重复")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$source] 处理剪贴板变化失败", e)
        }
    }

    private fun getCurrentClipboardContent(): String? {
        return try {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                item.text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取剪贴板内容失败", e)
            null
        }
    }
    
    /**
     * 公开方法：获取剪贴板内容
     * 供其他服务调用
     */
    fun getClipboardContent(): String? {
        return try {
            Log.d(TAG, "外部请求获取剪贴板内容")
            val content = getCurrentClipboardContent()
            Log.d(TAG, "剪贴板内容: '$content'")
            content
        } catch (e: Exception) {
            Log.e(TAG, "外部获取剪贴板内容失败", e)
            null
        }
    }

    private fun updateLastClipboardContent() {
        lastClipboardContent = getCurrentClipboardContent()
    }

    /**
     * 获取当前前台应用的包名
     */
    private fun getCurrentAppPackageName(): String {
        return try {
            val rootNode = rootInActiveWindow
            rootNode?.packageName?.toString() ?: "unknown"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    /**
     * 启动定期检查剪贴板的机制
     * 作为主要监听器的备用方案
     */
    private fun startPeriodicClipboardCheck() {
        stopPeriodicClipboardCheck() // 先停止之前的检查

        periodicCheckRunnable = object : Runnable {
            override fun run() {
                checkClipboardChange("periodic_check")
                // 继续下一次检查
                mainHandler.postDelayed(this, periodicCheckInterval)
            }
        }

        mainHandler.postDelayed(periodicCheckRunnable!!, periodicCheckInterval)
        Log.d(TAG, "✅ 定期剪贴板检查已启动，间隔: ${periodicCheckInterval}ms")
    }

    /**
     * 停止定期检查
     */
    private fun stopPeriodicClipboardCheck() {
        periodicCheckRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
            periodicCheckRunnable = null
            Log.d(TAG, "定期剪贴板检查已停止")
        }
    }

    /**
     * 启动激进模式剪贴板检查
     */
    private fun startAggressiveClipboardCheck() {
        stopAggressiveClipboardCheck() // 先停止之前的检查

        aggressiveCheckRunnable = object : Runnable {
            override fun run() {
                checkClipboardChange("aggressive_check")
                // 继续下一次检查
                mainHandler.postDelayed(this, aggressiveCheckInterval)
            }
        }

        mainHandler.postDelayed(aggressiveCheckRunnable!!, aggressiveCheckInterval)
        Log.d(TAG, "🚀 激进模式剪贴板检查已启动，间隔: ${aggressiveCheckInterval}ms")
    }

    /**
     * 停止激进模式检查
     */
    private fun stopAggressiveClipboardCheck() {
        aggressiveCheckRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
            aggressiveCheckRunnable = null
            Log.d(TAG, "激进模式剪贴板检查已停止")
        }
    }

    /**
     * 启动超级激进模式剪贴板检查（后台时使用）
     */
    private fun startSuperAggressiveClipboardCheck() {
        stopSuperAggressiveClipboardCheck()

        superAggressiveCheckRunnable = object : Runnable {
            override fun run() {
                checkClipboardChange("super_aggressive_check")
                mainHandler.postDelayed(this, superAggressiveCheckInterval)
            }
        }

        mainHandler.postDelayed(superAggressiveCheckRunnable!!, superAggressiveCheckInterval)
        Log.d(TAG, "🔥 超级激进模式剪贴板检查已启动，间隔: ${superAggressiveCheckInterval}ms")
    }

    /**
     * 停止超级激进模式检查
     */
    private fun stopSuperAggressiveClipboardCheck() {
        superAggressiveCheckRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
            superAggressiveCheckRunnable = null
            Log.d(TAG, "🛑 超级激进模式剪贴板检查已停止")
        }
    }

    /**
     * 启动应用状态监控
     */
    private fun startBackgroundMonitoring() {
        stopBackgroundMonitoring()

        backgroundCheckRunnable = object : Runnable {
            override fun run() {
                checkAppBackgroundStatus()
                backgroundCheckHandler.postDelayed(this, 1000L)
            }
        }

        backgroundCheckHandler.postDelayed(backgroundCheckRunnable!!, 1000L)
        Log.d(TAG, "🔄 应用状态监控已启动")
    }

    /**
     * 停止应用状态监控
     */
    private fun stopBackgroundMonitoring() {
        backgroundCheckRunnable?.let { runnable ->
            backgroundCheckHandler.removeCallbacks(runnable)
            backgroundCheckRunnable = null
            Log.d(TAG, "🛑 应用状态监控已停止")
        }
    }

    /**
     * 检查应用后台状态
     */
    private fun checkAppBackgroundStatus() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningTasks = activityManager.getRunningTasks(1)

            val wasInBackground = isAppInBackground
            isAppInBackground = runningTasks.isNotEmpty() &&
                !runningTasks[0].topActivity?.packageName.equals(packageName)

            if (wasInBackground != isAppInBackground) {
                Log.d(TAG, "应用状态变化: ${if (isAppInBackground) "进入后台" else "回到前台"}")

                if (isAppInBackground && !isSuperAggressiveModeEnabled) {
                    // 应用进入后台，启动超级激进模式
                    isSuperAggressiveModeEnabled = true
                    startSuperAggressiveClipboardCheck()
                    Log.d(TAG, "🔥 应用进入后台，启动超级激进监听模式")
                } else if (!isAppInBackground && isSuperAggressiveModeEnabled) {
                    // 应用回到前台，停止超级激进模式
                    isSuperAggressiveModeEnabled = false
                    stopSuperAggressiveClipboardCheck()
                    Log.d(TAG, "✅ 应用回到前台，停止超级激进监听模式")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查应用状态失败: ${e.message}")
        }
    }

    /**
     * 启动服务状态监控
     */
    private fun startServiceStatusCheck() {
        stopServiceStatusCheck() // 先停止之前的检查

        serviceStatusCheckRunnable = object : Runnable {
            override fun run() {
                checkServiceStatus()
                // 继续下一次检查
                mainHandler.postDelayed(this, serviceStatusCheckInterval)
            }
        }

        mainHandler.postDelayed(serviceStatusCheckRunnable!!, serviceStatusCheckInterval)
        Log.d(TAG, "✅ 服务状态监控已启动，间隔: ${serviceStatusCheckInterval}ms")
    }

    /**
     * 停止服务状态监控
     */
    private fun stopServiceStatusCheck() {
        serviceStatusCheckRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
            serviceStatusCheckRunnable = null
            Log.d(TAG, "服务状态监控已停止")
        }
    }

    /**
     * 检查服务状态
     */
    private fun checkServiceStatus() {
        try {
            val wasActive = isServiceActive.get()

            // 检查剪贴板管理器是否仍然有效
            val isClipboardManagerValid = ::clipboardManager.isInitialized

            if (!isClipboardManagerValid) {
                Log.w(TAG, "⚠️ 剪贴板管理器无效，尝试重新初始化")
                isServiceActive.set(false)
                reinitializeService()
                return
            }

            // 尝试获取剪贴板内容来测试服务是否正常
            try {
                getCurrentClipboardContent()
                if (!wasActive) {
                    Log.d(TAG, "✅ 服务状态恢复正常")
                    isServiceActive.set(true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 剪贴板访问异常，服务可能被限制: ${e.message}")
                if (wasActive) {
                    isServiceActive.set(false)
                    // 尝试重新初始化
                    mainHandler.postDelayed({
                        reinitializeService()
                    }, 1000)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 服务状态检查失败", e)
        }
    }

    private fun isValidClipboardContent(content: String): Boolean {
        Log.d(TAG, "验证剪贴板内容: '${content.take(50)}${if (content.length > 50) "..." else ""}' (长度: ${content.length})")

        // 过滤掉太短的内容（少于2个字符）
        if (content.length < 2) {
            Log.d(TAG, "❌ 剪贴板内容太短: ${content.length}")
            return false
        }

        // 过滤掉纯数字内容（可能是验证码等）
        if (content.matches(Regex("^\\d+$"))) {
            Log.d(TAG, "❌ 剪贴板内容为纯数字，可能是验证码")
            return false
        }

        // 过滤掉纯符号内容
        if (content.matches(Regex("^[^\\p{L}\\p{N}]+$"))) {
            Log.d(TAG, "❌ 剪贴板内容为纯符号")
            return false
        }

        // 过滤掉太长的内容（超过500字符）
        if (content.length > 500) {
            Log.d(TAG, "❌ 剪贴板内容太长: ${content.length}")
            return false
        }

        // 过滤掉空白字符（空格、换行等）
        if (content.trim().isEmpty()) {
            Log.d(TAG, "❌ 剪贴板内容为空白字符")
            return false
        }

        Log.d(TAG, "✅ 剪贴板内容通过基本验证")
        return true
    }

    private fun isUserGeneratedClipboard(content: String): Boolean {
        Log.d(TAG, "检查是否为用户生成的内容...")

        // 过滤掉常见的系统自动复制内容
        val systemPatterns = listOf(
            // URL模式（除非是搜索关键词）
            Regex("^https?://.*"),
            // 文件路径模式
            Regex("^[a-zA-Z]:\\\\.*"),
            Regex("^/.*"),
            // 包名模式
            Regex("^[a-z]+\\.[a-z]+\\.[a-z]+.*"),
            // 系统信息模式
            Regex(".*Build.*API.*"),
            Regex(".*Android.*version.*"),
            // 错误日志模式
            Regex(".*Exception.*at.*"),
            Regex(".*Error.*line.*"),
            // UUID模式
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
            // Base64模式
            Regex("^[A-Za-z0-9+/=]{20,}$")
        )

        for (pattern in systemPatterns) {
            if (pattern.matches(content)) {
                Log.d(TAG, "❌ 剪贴板内容疑似系统自动生成: ${content.take(30)}...")
                return false
            }
        }

        // 检查是否包含过多的特殊字符（可能是系统生成的token等）
        val specialCharCount = content.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ".,!?;:()[]{}\"'-_" }
        val specialCharRatio = specialCharCount.toFloat() / content.length
        if (specialCharRatio > 0.4) {
            Log.d(TAG, "❌ 剪贴板内容包含过多特殊字符，疑似系统生成 (${String.format("%.1f", specialCharRatio * 100)}%)")
            return false
        }

        Log.d(TAG, "✅ 剪贴板内容通过用户生成验证")
        return true
    }

    private fun notifyClipboardChanged(content: String) {
        val intent = Intent(ACTION_CLIPBOARD_CHANGED).apply {
            putExtra(EXTRA_CLIPBOARD_CONTENT, content)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "已发送剪贴板变化广播")
    }



    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d(TAG, "🔚 无障碍服务销毁中...")
            
            // 清除服务实例引用
            serviceInstance = null

            // 标记服务为非活跃状态
            isServiceActive.set(false)

            // 停止所有检查机制
            stopPeriodicClipboardCheck()
            stopAggressiveClipboardCheck()
            stopSuperAggressiveClipboardCheck()
            stopBackgroundMonitoring()
            stopServiceStatusCheck()

            // 移除剪贴板监听器
            if (::clipboardManager.isInitialized) {
                clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            }

            // 取消注册自动粘贴广播接收器
            unregisterAutoPasteReceiver()

            Log.d(TAG, "✅ 无障碍服务已销毁，所有监听器和检查机制已清理")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清理无障碍服务失败", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ 无障碍服务被中断")
        isServiceActive.set(false)
        // 尝试在短时间后重新初始化
        mainHandler.postDelayed({
            if (isServiceActive.get() == false) {
                Log.d(TAG, "🔄 服务中断后尝试重新初始化")
                reinitializeService()
            }
        }, 2000)
    }


    /**
     * 注册自动粘贴广播接收器
     */
    private fun registerAutoPasteReceiver() {
        try {
            autoPasteReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_AUTO_PASTE) {
                        val packageName = intent.getStringExtra("package_name") ?: ""
                        val query = intent.getStringExtra("query") ?: ""
                        val appName = intent.getStringExtra("app_name") ?: ""
                        
                        Log.d(TAG, "📨 收到自动粘贴请求: app=$appName, package=$packageName, query=$query")
                        
                        // 创建自动粘贴请求
                        pendingAutoPaste = AutoPasteRequest(packageName, query, appName)
                        autoPasteRetryCount = 0
                        
                        // 立即尝试一次，然后延迟重试
                        mainHandler.post {
                            performAutoPaste()
                        }
                        
                        // 延迟执行自动粘贴（作为重试机制）
                        mainHandler.postDelayed({
                            if (pendingAutoPaste != null) {
                                Log.d(TAG, "🔄 延迟重试自动粘贴")
                            performAutoPaste()
                            }
                        }, autoPasteDelay)
                    }
                }
            }
            
            // 同时注册普通广播和本地广播接收器
            val filter = IntentFilter(ACTION_AUTO_PASTE)
            registerReceiver(autoPasteReceiver, filter)
            
            // 也注册本地广播接收器
            LocalBroadcastManager.getInstance(this).registerReceiver(
                autoPasteReceiver!!,
                filter
            )
            
            Log.d(TAG, "✅ 自动粘贴广播接收器注册成功（普通+本地）")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册自动粘贴广播接收器失败", e)
        }
    }

    /**
     * 取消注册自动粘贴广播接收器
     */
    private fun unregisterAutoPasteReceiver() {
        try {
            autoPasteReceiver?.let {
                try {
                unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.w(TAG, "取消注册普通广播接收器失败", e)
                }
                try {
                    LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.w(TAG, "取消注册本地广播接收器失败", e)
                }
                autoPasteReceiver = null
                Log.d(TAG, "✅ 自动粘贴广播接收器已取消注册")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 取消注册自动粘贴广播接收器失败", e)
        }
    }

    /**
     * 执行自动粘贴
     */
    private fun performAutoPaste() {
        val request = pendingAutoPaste ?: return
        
        try {
            Log.d(TAG, "🚀 开始执行自动粘贴: app=${request.appName}, package=${request.packageName}, query=${request.query}")
            
            // 检查当前应用是否为目标应用（如果packageName为空，跳过检查）
            val currentPackage = getCurrentPackageName()
            if (request.packageName.isNotEmpty() && currentPackage != request.packageName) {
                Log.w(TAG, "⚠️ 当前应用($currentPackage)不是目标应用(${request.packageName})，等待中... (重试次数: $autoPasteRetryCount/$maxAutoPasteRetries)")
                
                // 重试机制
                if (autoPasteRetryCount < maxAutoPasteRetries) {
                    autoPasteRetryCount++
                    mainHandler.postDelayed({
                        performAutoPaste()
                    }, autoPasteDelay)
                    return
                } else {
                    Log.e(TAG, "❌ 自动粘贴失败：超过最大重试次数，无法匹配目标应用")
                    pendingAutoPaste = null
                    return
                }
            } else if (request.packageName.isEmpty()) {
                Log.d(TAG, "ℹ️ packageName为空，跳过应用匹配检查，直接尝试粘贴")
            } else {
                Log.d(TAG, "✅ 应用匹配成功: $currentPackage")
            }
            
            // 查找输入框并粘贴文本
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                Log.d(TAG, "🔍 已获取根节点，开始查找输入框...")
                val success = findAndPasteText(rootNode, request.query)
                if (success) {
                    Log.d(TAG, "✅✅✅ 自动粘贴成功: ${request.appName} - ${request.query}")
                    pendingAutoPaste = null
                    autoPasteRetryCount = 0
                } else {
                    Log.w(TAG, "❌ 自动粘贴失败: 未找到输入框 (重试次数: $autoPasteRetryCount/$maxAutoPasteRetries)")
                    // 重试
                    if (autoPasteRetryCount < maxAutoPasteRetries) {
                        autoPasteRetryCount++
                        mainHandler.postDelayed({
                            performAutoPaste()
                        }, autoPasteDelay)
                    } else {
                        Log.e(TAG, "❌ 自动粘贴最终失败：超过最大重试次数，无法找到输入框")
                        pendingAutoPaste = null
                    }
                }
            } else {
                Log.w(TAG, "❌ 无法获取根节点，可能应用还未完全加载")
                // 如果无法获取根节点，也进行重试
                if (autoPasteRetryCount < maxAutoPasteRetries) {
                    autoPasteRetryCount++
                    mainHandler.postDelayed({
                        performAutoPaste()
                    }, autoPasteDelay)
                } else {
                    Log.e(TAG, "❌ 自动粘贴最终失败：无法获取根节点")
                pendingAutoPaste = null
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 执行自动粘贴异常", e)
            e.printStackTrace()
            pendingAutoPaste = null
        }
    }

    /**
     * 查找输入框并粘贴文本
     */
    private fun findAndPasteText(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            Log.d(TAG, "🔍 开始查找输入框，目标文本: $text")
            
            // 方法1：查找EditText类型的输入框
            val editTextNodes = node.findAccessibilityNodeInfosByText("")
                .filter { it.isEditable && it.className == "android.widget.EditText" }
            
            if (editTextNodes.isNotEmpty()) {
                val editText = editTextNodes.first()
                Log.d(TAG, "📝 找到EditText输入框: ${editText.text}")
                
                // 点击输入框获得焦点
                editText.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(200) // 等待焦点切换
                
                // 设置文本
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val success = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                if (success) {
                    Log.d(TAG, "✅ 文本已粘贴到EditText输入框: $text")
                    return true
                }
            }
            
            // 方法2：查找所有可编辑的节点
            val editableNodes = node.findAccessibilityNodeInfosByText("")
                .filter { it.isEditable }
            
            if (editableNodes.isNotEmpty()) {
                val editableNode = editableNodes.first()
                Log.d(TAG, "📝 找到可编辑节点: ${editableNode.className}")
                
                editableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(200) // 等待焦点切换
                
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                
                if (success) {
                    Log.d(TAG, "✅ 文本已粘贴到可编辑节点: $text")
                    return true
                }
            }
            
            // 方法3：查找包含特定关键词的输入框（针对AI应用）
            val aiInputKeywords = listOf("输入", "问题", "消息", "聊天", "搜索", "query", "message", "input")
            for (keyword in aiInputKeywords) {
                val keywordNodes = node.findAccessibilityNodeInfosByText(keyword)
                    .filter { it.isEditable || it.parent?.isEditable == true }
                
                if (keywordNodes.isNotEmpty()) {
                    val targetNode = if (keywordNodes.first().isEditable) {
                        keywordNodes.first()
                    } else {
                        keywordNodes.first().parent
                    }
                    
                    if (targetNode != null) {
                        Log.d(TAG, "📝 通过关键词'$keyword'找到输入框: ${targetNode.className}")
                        
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Thread.sleep(200)
                        
                        val arguments = android.os.Bundle()
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        
                        if (success) {
                            Log.d(TAG, "✅ 文本已粘贴到关键词输入框: $text")
                            return true
                        }
                    }
                }
            }
            
            // 方法4：查找具有特定contentDescription的输入框
            val inputNodes = node.findAccessibilityNodeInfosByText("")
                .filter { 
                    it.contentDescription?.toString()?.contains("输入", ignoreCase = true) == true ||
                    it.contentDescription?.toString()?.contains("问题", ignoreCase = true) == true ||
                    it.contentDescription?.toString()?.contains("消息", ignoreCase = true) == true
                }
                .filter { it.isEditable || it.parent?.isEditable == true }
            
            if (inputNodes.isNotEmpty()) {
                val targetNode = if (inputNodes.first().isEditable) {
                    inputNodes.first()
                } else {
                    inputNodes.first().parent
                }
                
                if (targetNode != null) {
                    Log.d(TAG, "📝 通过contentDescription找到输入框: ${targetNode.contentDescription}")
                    
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Thread.sleep(200)
                    
                    val arguments = android.os.Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    
                    if (success) {
                        Log.d(TAG, "✅ 文本已粘贴到contentDescription输入框: $text")
                        return true
                    }
                }
            }
            
            Log.w(TAG, "❌ 未找到合适的输入框")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 查找并粘贴文本失败", e)
            return false
        }
    }

    /**
     * 获取当前应用包名
     */
    private fun getCurrentPackageName(): String? {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningTasks = activityManager.getRunningTasks(1)
            if (runningTasks.isNotEmpty()) {
                runningTasks[0].topActivity?.packageName
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前应用包名失败", e)
            null
        }
    }
}