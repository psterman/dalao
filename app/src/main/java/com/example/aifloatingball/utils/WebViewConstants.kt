package com.example.aifloatingball.utils

/**
 * WebView相关常量
 */
object WebViewConstants {
    
    /**
     * 统一的移动端User-Agent
     * 使用最新的Chrome移动版User-Agent，确保网站识别为移动设备
     */
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
    
    /**
     * 桌面版User-Agent（备用）
     */
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    
    /**
     * 默认使用移动端User-Agent
     */
    const val DEFAULT_USER_AGENT = MOBILE_USER_AGENT
    
    /**
     * 移动端优化的WebView设置
     */
    object MobileSettings {
        const val TEXT_ZOOM = 100
        const val MINIMUM_FONT_SIZE = 8
        const val INITIAL_SCALE = 100
    }
}


