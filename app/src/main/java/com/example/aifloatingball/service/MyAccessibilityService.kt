package com.example.aifloatingball.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MyAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "MyAccessibilityService"
        const val ACTION_CLIPBOARD_CHANGED = "com.example.aifloatingball.ACTION_CLIPBOARD_CHANGED"
        const val EXTRA_CLIPBOARD_CONTENT = "clipboard_content"

        // 调试模式：设置为true时放宽过滤条件
        private const val DEBUG_MODE = true
    }

    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboardContent: String? = null
    private var lastClipboardChangeTime = 0L
    private val clipboardChangeDebounceTime = 1000L // 防抖时间：1秒

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)

            // 初始化当前剪贴板内容
            updateLastClipboardContent()

            Log.d(TAG, "无障碍服务已连接，剪贴板监听器已初始化")
        } catch (e: Exception) {
            Log.e(TAG, "初始化无障碍服务失败", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 使用 ClipboardManager 更可靠，此处留空
    }

    private fun handleClipboardChange() {
        try {
            val currentTime = System.currentTimeMillis()

            // 防抖处理：如果距离上次变化时间太短，忽略
            if (currentTime - lastClipboardChangeTime < clipboardChangeDebounceTime) {
                Log.d(TAG, "剪贴板变化过于频繁，忽略此次变化 (${currentTime - lastClipboardChangeTime}ms)")
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
                    Log.d(TAG, "✅ 检测到有效的剪贴板内容变化: ${currentContent.take(50)}${if (currentContent.length > 50) "..." else ""}")

                    // 更新时间和内容
                    lastClipboardChangeTime = currentTime
                    lastClipboardContent = currentContent

                    // 通知DynamicIslandService展开灵动岛
                    notifyClipboardChanged(currentContent)
                } else {
                    Log.d(TAG, "❌ 剪贴板内容未通过验证")
                }
            } else {
                if (currentContent == null) {
                    Log.d(TAG, "❌ 剪贴板内容为null")
                } else if (currentContent.isEmpty()) {
                    Log.d(TAG, "❌ 剪贴板内容为空")
                } else if (currentContent == lastClipboardContent) {
                    Log.d(TAG, "❌ 剪贴板内容重复")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理剪贴板变化失败", e)
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

    private fun updateLastClipboardContent() {
        lastClipboardContent = getCurrentClipboardContent()
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

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "无障碍服务已销毁，剪贴板监听器已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理无障碍服务失败", e)
        }
    }
}