package com.example.aifloatingball.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MyAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_COPY_EVENT = "com.example.aifloatingball.ACTION_COPY_EVENT"
    }

    private lateinit var clipboardManager: ClipboardManager

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        notifyCopyEvent()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 使用 ClipboardManager 更可靠，此处留空
    }

    private fun notifyCopyEvent() {
        val intent = Intent(ACTION_COPY_EVENT)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
    }
} 