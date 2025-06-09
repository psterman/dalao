package com.example.aifloatingball.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 这个方法目前可以留空。
        // 它的主要目的是完成服务的注册流程。
    }

    override fun onInterrupt() {
        // 当服务被中断时调用，例如被系统强制停止。
        // 目前也可以留空。
    }
} 