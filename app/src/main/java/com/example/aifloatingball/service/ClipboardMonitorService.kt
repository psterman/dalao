package com.example.aifloatingball.service

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.URLUtil
import com.example.aifloatingball.ClipboardDialogActivity
import com.example.aifloatingball.SettingsManager

class ClipboardMonitorService : Service() {
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var settingsManager: SettingsManager
    private var lastClipText: String? = null
    private var lastClipTime: Long = 0
    private var debounceHandler: Handler? = null
    private var debounceRunnable: Runnable? = null

    companion object {
        private const val DEBOUNCE_DELAY = 1000L // 1秒防抖延迟
        private const val MIN_INTERVAL = 3000L // 最小间隔3秒
        private const val MAX_CONTENT_LENGTH = 500 // 最大内容长度
        private const val MIN_CONTENT_LENGTH = 3 // 最小内容长度
    }

    override fun onCreate() {
        super.onCreate()
        // 暂时禁用剪贴板监听功能
        // 直接返回，不设置任何监听器
        android.util.Log.d("ClipboardMonitorService", "ClipboardMonitorService disabled - not setting up listeners")

        // 停止服务
        stopSelf()
        return

        // 以下代码被暂时禁用
        /*
        settingsManager = SettingsManager.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        debounceHandler = Handler(Looper.getMainLooper())

        // 监听剪贴板变化
        clipboardManager.addPrimaryClipChangedListener {
            if (!settingsManager.isClipboardListenerEnabled()) return@addPrimaryClipChangedListener

            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()

            // 基本检查
            if (clipText.isNullOrEmpty() || clipText == lastClipText) return@addPrimaryClipChangedListener

            // 取消之前的防抖任务
            debounceRunnable?.let { debounceHandler?.removeCallbacks(it) }

            // 创建新的防抖任务
            debounceRunnable = Runnable {
                handleClipboardChange(clipText)
            }

            // 延迟执行
            debounceHandler?.postDelayed(debounceRunnable!!, DEBOUNCE_DELAY)
        }
        */
    }

    private fun handleClipboardChange(clipText: String) {
        val currentTime = System.currentTimeMillis()

        // 检查时间间隔
        if (currentTime - lastClipTime < MIN_INTERVAL) {
            return
        }

        // 检查内容有效性
        if (!isValidContent(clipText)) {
            return
        }

        // 检查是否为应用内复制（避免自己触发自己）
        if (isAppInternalCopy(clipText)) {
            return
        }

        lastClipText = clipText
        lastClipTime = currentTime
        showClipboardDialog(clipText)
    }

    private fun isValidContent(content: String): Boolean {
        return when {
            // 长度检查
            content.length < MIN_CONTENT_LENGTH -> false
            content.length > MAX_CONTENT_LENGTH -> false

            // URL检查 - URL总是有效的
            URLUtil.isValidUrl(content) -> true

            // 纯数字检查（避免无意义的数字，除非是长数字如电话号码）
            content.all { it.isDigit() } && content.length < 8 -> false

            // 特殊字符检查（避免乱码或特殊符号）
            content.all { !it.isLetterOrDigit() && !it.isWhitespace() } -> false

            // 重复字符检查（避免如"aaaaaaa"这样的内容）
            isRepeatingPattern(content) -> false

            // 常见无效内容过滤
            isCommonInvalidContent(content) -> false

            // 默认允许
            else -> true
        }
    }

    private fun isRepeatingPattern(content: String): Boolean {
        if (content.length < 4) return false

        // 检查是否为重复字符
        val firstChar = content[0]
        if (content.all { it == firstChar }) return true

        // 检查是否为重复模式（如"abcabc"）
        for (patternLength in 1..content.length / 2) {
            val pattern = content.substring(0, patternLength)
            var isRepeating = true
            for (i in patternLength until content.length step patternLength) {
                val endIndex = minOf(i + patternLength, content.length)
                if (content.substring(i, endIndex) != pattern.substring(0, endIndex - i)) {
                    isRepeating = false
                    break
                }
            }
            if (isRepeating) return true
        }

        return false
    }

    private fun isCommonInvalidContent(content: String): Boolean {
        val lowerContent = content.lowercase().trim()

        // 常见的无效内容模式
        val invalidPatterns = listOf(
            "test", "测试", "123", "abc", "aaa", "...", "---",
            "copy", "paste", "复制", "粘贴", "null", "undefined",
            "error", "错误", "loading", "加载中"
        )

        return invalidPatterns.any { lowerContent == it }
    }

    private fun isAppInternalCopy(content: String): Boolean {
        // 检查是否为应用内部复制的内容
        // 可以通过检查内容是否包含应用特有的标识来判断
        return content.contains("AI智能助手") ||
               content.contains("搜索结果") ||
               content.startsWith("http://localhost") ||
               content.startsWith("file://")
    }

    private fun showClipboardDialog(content: String) {
        ClipboardDialogActivity.show(this, content)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理防抖任务
        debounceRunnable?.let { debounceHandler?.removeCallbacks(it) }
        debounceHandler = null
        debounceRunnable = null
    }
}