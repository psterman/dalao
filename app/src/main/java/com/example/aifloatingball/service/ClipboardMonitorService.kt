package com.example.aifloatingball.service

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.webkit.URLUtil
import com.example.aifloatingball.ClipboardDialogActivity
import com.example.aifloatingball.SettingsManager

class ClipboardMonitorService : Service() {
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var settingsManager: SettingsManager
    private var lastClipText: String? = null

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // 监听剪贴板变化
        clipboardManager.addPrimaryClipChangedListener {
            if (!settingsManager.isClipboardListenerEnabled()) return@addPrimaryClipChangedListener
            
            val clipText = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            
            // 检查内容有效性并且与上次不同
            if (!clipText.isNullOrEmpty() && clipText != lastClipText && isValidContent(clipText)) {
                lastClipText = clipText
                showClipboardDialog(clipText)
            }
        }
    }

    private fun isValidContent(content: String): Boolean {
        return when {
            // URL检查
            URLUtil.isValidUrl(content) -> true
            // 搜索词长度检查（避免太短的内容）
            content.length < 2 -> false
            // 纯数字检查（避免无意义的数字）
            content.all { it.isDigit() } -> false
            // 特殊字符检查（避免乱码或特殊符号）
            content.all { !it.isLetterOrDigit() } -> false
            // 默认允许
            else -> true
        }
    }

    private fun showClipboardDialog(content: String) {
        ClipboardDialogActivity.show(this, content)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
} 