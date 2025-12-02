package com.example.aifloatingball.utils

import android.os.Build

/**
 * WebView相关常量
 */
object WebViewConstants {
    
    /**
     * 统一的移动端User-Agent
     * 使用真实的移动端Chrome User-Agent，包含设备信息和Android版本
     * 格式：Mozilla/5.0 (Linux; Android {版本}; {设备型号}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/{版本} Mobile Safari/537.36
     */
    val MOBILE_USER_AGENT: String
        get() {
            val androidVersion = Build.VERSION.RELEASE
            val deviceModel = Build.MODEL.replace(" ", "_")
            // 使用最新的Chrome移动版版本号
            val chromeVersion = "131.0.0.0"
            return "Mozilla/5.0 (Linux; Android $androidVersion; $deviceModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
        }
    
    /**
     * 标准移动端User-Agent（不包含设备型号，更通用）
     * 使用最新的Chrome移动版User-Agent，确保网站识别为移动设备
     */
    const val MOBILE_USER_AGENT_STANDARD = "Mozilla/5.0 (Linux; Android 13; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    
    /**
     * 桌面版User-Agent（备用）
     */
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    
    /**
     * 默认使用移动端User-Agent
     */
    val DEFAULT_USER_AGENT: String
        get() = MOBILE_USER_AGENT_STANDARD
    
    /**
     * 移动端优化的WebView设置
     */
    object MobileSettings {
        const val TEXT_ZOOM = 100
        const val MINIMUM_FONT_SIZE = 8
        const val INITIAL_SCALE = 100
    }
}


