package com.example.aifloatingball.webview

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * 视频检测 JavaScript Bridge
 * 
 * 用于在 WebView 中检测视频播放事件，并将视频URL和位置信息回调给原生代码。
 * 
 * @author AI Floating Ball
 */
class VideoDetectionBridge(
    private val onVideoPlayCallback: (String, Int, Int, Int, Int, String?) -> Unit
) {
    companion object {
        private const val TAG = "VideoDetectionBridge"
    }

    /**
     * JavaScript 回调：当检测到视频播放时调用
     * 
     * @param videoUrl 视频URL（可能为空）
     * @param x 视频元素在屏幕上的X坐标
     * @param y 视频元素在屏幕上的Y坐标
     * @param width 视频元素宽度
     * @param height 视频元素高度
     * @param pageTitle 网页标题（可选）
     */
    @JavascriptInterface
    fun onVideoPlay(videoUrl: String?, x: Int, y: Int, width: Int, height: Int, pageTitle: String? = null) {
        try {
            val url = videoUrl?.trim()
            if (!url.isNullOrBlank()) {
                Log.d(TAG, "检测到视频播放，URL: $url, 位置: ($x, $y), 尺寸: ${width}x${height}, 标题: $pageTitle")
                onVideoPlayCallback(url, x, y, width, height, pageTitle)
            } else {
                Log.w(TAG, "视频URL为空，忽略回调")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理视频播放回调失败", e)
        }
    }
}
