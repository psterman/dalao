package com.example.aifloatingball.ui.webview

/**
 * 链接长按监听器
 */
interface LinkMenuListener {
    fun onLinkLongPressed(url: String, x: Int, y: Int)
}
