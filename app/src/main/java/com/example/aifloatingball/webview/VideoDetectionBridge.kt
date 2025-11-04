package com.example.aifloatingball.webview

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * 视频检测 JavaScript Bridge
 * 
 * 用于在 WebView 中检测视频播放事件，并将视频URL回调给原生代码。
 * 
 * @author AI Floating Ball
 */
class VideoDetectionBridge(
    private val onVideoPlayCallback: (String) -> Unit
) {
    companion object {
        private const val TAG = "VideoDetectionBridge"
    }

    /**
     * JavaScript 回调：当检测到视频播放时调用
     * 
     * @param videoUrl 视频URL（可能为空）
     */
    @JavascriptInterface
    fun onVideoPlay(videoUrl: String?) {
        try {
            val url = videoUrl?.trim()
            if (!url.isNullOrBlank()) {
                Log.d(TAG, "检测到视频播放，URL: $url")
                onVideoPlayCallback(url)
            } else {
                Log.w(TAG, "视频URL为空，忽略回调")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理视频播放回调失败", e)
        }
    }
}
