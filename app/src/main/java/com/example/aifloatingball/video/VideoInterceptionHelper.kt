package com.example.aifloatingball.video

import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.example.aifloatingball.webview.VideoDetectionBridge

/**
 * 视频拦截辅助工具类
 * 
 * 用于拦截 WebView 中的所有视频播放，并交由 SystemOverlayVideoManager 处理
 * 
 * @author AI Floating Ball
 */
object VideoInterceptionHelper {
    private const val TAG = "VideoInterceptionHelper"
    
    /**
     * 创建拦截视频播放的 WebChromeClient
     * 
     * @param systemOverlayVideoManager 系统悬浮视频播放器管理器
     * @param originalClient 原始的 WebChromeClient（可选）
     * @return 配置了视频拦截的 WebChromeClient
     */
    fun createVideoInterceptingChromeClient(
        systemOverlayVideoManager: SystemOverlayVideoManager,
        originalClient: WebChromeClient? = null,
        webView: WebView? = null
    ): WebChromeClient {
        return object : WebChromeClient() {
            /**
             * 拦截全屏视频播放
             * 当 HTML5 video 元素进入全屏模式时，会被此方法拦截
             */
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                Log.d(TAG, "🎬 检测到全屏视频播放请求")
                
                // 取消全屏播放，阻止默认行为
                callback?.onCustomViewHidden()
                
                try {
                    // 尝试从 View 中提取视频 URL
                    val videoUrl = extractVideoUrlFromView(view)
                    
                    if (!videoUrl.isNullOrBlank()) {
                        Log.d(TAG, "提取到视频URL: $videoUrl")
                        // 使用悬浮播放器播放
                        systemOverlayVideoManager.show(videoUrl)
                        return
                    }
                    
                    // 如果无法从 View 提取 URL，尝试通过 JavaScript 从页面中获取
                    val targetWebView = webView
                    if (targetWebView != null) {
                        try {
                            targetWebView.evaluateJavascript("""
                                (function() {
                                    const videos = document.querySelectorAll('video');
                                    for (let i = 0; i < videos.length; i++) {
                                        const video = videos[i];
                                        if (!video.paused || video.currentTime > 0) {
                                            const videoUrl = video.src || video.currentSrc;
                                            if (videoUrl && videoUrl.startsWith('http')) {
                                                return videoUrl;
                                            }
                                        }
                                    }
                                    return null;
                                })();
                            """.trimIndent()) { result ->
                                try {
                                    val videoUrl = result?.trim('"', '\'', ' ')
                                    if (!videoUrl.isNullOrBlank() && videoUrl != "null") {
                                        Log.d(TAG, "通过 JavaScript 获取到视频URL: $videoUrl")
                                        systemOverlayVideoManager.show(videoUrl)
                                    } else {
                                        Log.w(TAG, "无法通过 JavaScript 获取视频URL")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "处理 JavaScript 返回的视频URL失败", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "执行 JavaScript 获取视频URL失败", e)
                        }
                    } else {
                        Log.w(TAG, "WebView 引用为空，无法通过 JavaScript 获取视频URL")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "拦截全屏视频播放失败", e)
                }
                
                // 不调用原始回调，因为我们希望阻止全屏播放
                // 视频播放应该由 JavaScript 拦截脚本处理
            }
            
            /**
             * 隐藏全屏视频
             */
            override fun onHideCustomView() {
                Log.d(TAG, "全屏视频隐藏")
                originalClient?.onHideCustomView() ?: super.onHideCustomView()
            }
            
            // 代理其他方法到原始客户端
            override fun onReceivedTitle(view: WebView?, title: String?) {
                originalClient?.onReceivedTitle(view, title) ?: super.onReceivedTitle(view, title)
            }
            
            override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
                originalClient?.onReceivedIcon(view, icon) ?: super.onReceivedIcon(view, icon)
            }
            
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                originalClient?.onProgressChanged(view, newProgress) ?: super.onProgressChanged(view, newProgress)
            }
        }
    }
    
    /**
     * 为 WebView 设置视频拦截的 JavaScript 接口
     * 应该在 WebView 创建时就调用，而不是在页面加载时
     * 
     * @param webView 目标 WebView
     * @param systemOverlayVideoManager 系统悬浮视频播放器管理器
     */
    fun setupVideoInterceptionInterface(
        webView: WebView,
        systemOverlayVideoManager: SystemOverlayVideoManager
    ) {
        try {
            // 移除旧的接口（如果存在）
            try {
                webView.removeJavascriptInterface("VideoInterceptionBridge")
            } catch (e: Exception) {
                // 忽略接口不存在的异常
            }
            
            // 添加 JavaScript 接口
            val bridge = VideoDetectionBridge { videoUrl ->
                Log.d(TAG, "JavaScript 检测到视频播放: $videoUrl")
                if (!videoUrl.isNullOrBlank()) {
                    systemOverlayVideoManager.show(videoUrl)
                }
            }
            webView.addJavascriptInterface(bridge, "VideoInterceptionBridge")
            Log.d(TAG, "视频拦截 JavaScript 接口已设置")
        } catch (e: Exception) {
            Log.e(TAG, "设置视频拦截 JavaScript 接口失败", e)
        }
    }
    
    /**
     * 注入 JavaScript 代码来拦截页面内的视频播放
     * 应该在页面开始加载时就调用，而不是等到页面加载完成
     * 
     * @param webView 目标 WebView
     * @param systemOverlayVideoManager 系统悬浮视频播放器管理器（可选，如果已设置接口则不需要）
     */
    fun injectVideoInterceptionScript(
        webView: WebView,
        systemOverlayVideoManager: SystemOverlayVideoManager? = null
    ) {
        try {
            // 如果提供了 systemOverlayVideoManager，确保接口已设置
            if (systemOverlayVideoManager != null) {
                setupVideoInterceptionInterface(webView, systemOverlayVideoManager)
            }
            
            // 注入拦截脚本
            val interceptionScript = """
                (function() {
                    'use strict';
                    
                    // 全局拦截所有 video 元素的自动播放
                    function preventAutoplay() {
                        const videos = document.querySelectorAll('video');
                        videos.forEach(function(video) {
                            // 移除自动播放属性
                            if (video.hasAttribute('autoplay')) {
                                video.removeAttribute('autoplay');
                                video.autoplay = false;
                            }
                            
                            // 如果视频正在自动播放，立即暂停
                            if (!video.paused && video.readyState >= 2) {
                                const url = video.src || video.currentSrc || 
                                           (video.querySelector('source') && video.querySelector('source').src);
                                if (url && url.startsWith('http')) {
                                    video.pause();
                                    if (typeof VideoInterceptionBridge !== 'undefined') {
                                        VideoInterceptionBridge.onVideoPlay(url);
                                    }
                                }
                            }
                        });
                    }
                    
                    // 立即执行一次，阻止已存在的自动播放视频
                    preventAutoplay();
                    
                    // 拦截所有 video 元素的播放事件
                    function interceptVideoPlay() {
                        const videos = document.querySelectorAll('video');
                        videos.forEach(function(video, index) {
                            // 移除之前的监听器（避免重复添加）
                            if (video._intercepted) {
                                return;
                            }
                            video._intercepted = true;
                            
                            // 阻止视频自动播放
                            video.removeAttribute('autoplay');
                            video.autoplay = false;
                            
                            // 获取视频 URL（在加载时就获取，不等待播放）
                            const videoUrl = video.src || video.currentSrc || 
                                           (video.querySelector('source') && video.querySelector('source').src);
                            
                            // 如果视频已经有 URL，立即通知原生代码（不等待播放）
                            if (videoUrl && videoUrl.startsWith('http')) {
                                console.log('检测到视频元素，URL: ' + videoUrl);
                                
                                // 如果视频已经自动播放，立即暂停并通知
                                if (!video.paused) {
                                    video.pause();
                                    if (typeof VideoInterceptionBridge !== 'undefined') {
                                        VideoInterceptionBridge.onVideoPlay(videoUrl);
                                    }
                                }
                            }
                            
                            // 监听视频元数据加载完成（此时可以获取 URL）
                            video.addEventListener('loadedmetadata', function(e) {
                                try {
                                    const url = this.src || this.currentSrc || 
                                               (this.querySelector('source') && this.querySelector('source').src);
                                    if (url && url.startsWith('http')) {
                                        console.log('视频元数据加载完成，URL: ' + url);
                                        
                                        // 如果视频设置了自动播放，立即阻止并通知
                                        if (this.autoplay || this.hasAttribute('autoplay')) {
                                            this.pause();
                                            this.removeAttribute('autoplay');
                                            this.autoplay = false;
                                            
                                            if (typeof VideoInterceptionBridge !== 'undefined') {
                                                VideoInterceptionBridge.onVideoPlay(url);
                                            }
                                        }
                                    }
                                } catch (err) {
                                    console.error('处理视频元数据失败: ' + err);
                                }
                            }, true);
                            
                            // 监听播放事件（包括自动播放）
                            video.addEventListener('play', function(e) {
                                try {
                                    const url = this.src || this.currentSrc || 
                                               (this.querySelector('source') && this.querySelector('source').src);
                                    console.log('检测到视频播放，URL: ' + url);
                                    
                                    if (url && url.startsWith('http')) {
                                        // 立即暂停原视频播放
                                        this.pause();
                                        
                                        // 通知原生代码
                                        if (typeof VideoInterceptionBridge !== 'undefined') {
                                            VideoInterceptionBridge.onVideoPlay(url);
                                        }
                                        
                                        // 阻止默认行为
                                        e.preventDefault();
                                        e.stopPropagation();
                                        return false;
                                    }
                                } catch (err) {
                                    console.error('拦截视频播放失败: ' + err);
                                }
                            }, true);
                            
                            // 监听全屏请求
                            video.addEventListener('webkitbeginfullscreen', function(e) {
                                try {
                                    const url = this.src || this.currentSrc || 
                                               (this.querySelector('source') && this.querySelector('source').src);
                                    console.log('检测到全屏视频请求，URL: ' + url);
                                    
                                    if (url && url.startsWith('http')) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        
                                        if (typeof VideoInterceptionBridge !== 'undefined') {
                                            VideoInterceptionBridge.onVideoPlay(url);
                                        }
                                        
                                        return false;
                                    }
                                } catch (err) {
                                    console.error('拦截全屏视频失败: ' + err);
                                }
                            }, true);
                            
                            // 拦截点击事件（无论视频是否在播放都要拦截）
                            video.addEventListener('click', function(e) {
                                try {
                                    const url = this.src || this.currentSrc || 
                                               (this.querySelector('source') && this.querySelector('source').src);
                                    if (url && url.startsWith('http')) {
                                        // 无论视频是否在播放，都拦截点击
                                        this.pause();
                                        
                                        e.preventDefault();
                                        e.stopPropagation();
                                        
                                        if (typeof VideoInterceptionBridge !== 'undefined') {
                                            VideoInterceptionBridge.onVideoPlay(url);
                                        }
                                        
                                        return false;
                                    }
                                } catch (err) {
                                    console.error('拦截视频点击失败: ' + err);
                                }
                            }, true);
                            
                            // 拦截播放按钮的点击（通过父元素）
                            const playButton = video.parentElement?.querySelector('.play-button, .play-btn, [class*="play"]');
                            if (playButton) {
                                playButton.addEventListener('click', function(e) {
                                    try {
                                        const url = video.src || video.currentSrc || 
                                                   (video.querySelector('source') && video.querySelector('source').src);
                                        if (url && url.startsWith('http')) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            
                                            if (typeof VideoInterceptionBridge !== 'undefined') {
                                                VideoInterceptionBridge.onVideoPlay(url);
                                            }
                                            
                                            return false;
                                        }
                                    } catch (err) {
                                        console.error('拦截播放按钮点击失败: ' + err);
                                    }
                                }, true);
                            }
                        });
                    }
                    
                    // 立即执行拦截（不等待页面加载完成）
                    if (document.body) {
                        preventAutoplay();
                        interceptVideoPlay();
                    }
                    
                    // 监听 DOM 变化，拦截动态添加的视频元素
                    const observer = new MutationObserver(function(mutations) {
                        preventAutoplay();
                        interceptVideoPlay();
                    });
                    
                    // 立即开始观察（如果 body 存在）
                    if (document.body) {
                        observer.observe(document.body, {
                            childList: true,
                            subtree: true
                        });
                    } else if (document.documentElement) {
                        observer.observe(document.documentElement, {
                            childList: true,
                            subtree: true
                        });
                    }
                    
                    // 页面加载完成后再次拦截
                    if (document.readyState === 'complete' || document.readyState === 'interactive') {
                        preventAutoplay();
                        interceptVideoPlay();
                    } else {
                        // 立即拦截
                        preventAutoplay();
                        interceptVideoPlay();
                        
                        // 监听页面加载事件
                        window.addEventListener('load', function() {
                            preventAutoplay();
                            interceptVideoPlay();
                        }, { once: true });
                        
                        document.addEventListener('DOMContentLoaded', function() {
                            preventAutoplay();
                            interceptVideoPlay();
                        }, { once: true });
                        
                        // 使用 MutationObserver 等待 body 出现
                        const bodyObserver = new MutationObserver(function() {
                            if (document.body) {
                                preventAutoplay();
                                interceptVideoPlay();
                                observer.observe(document.body, {
                                    childList: true,
                                    subtree: true
                                });
                                bodyObserver.disconnect();
                            }
                        });
                        
                        if (document.documentElement) {
                            bodyObserver.observe(document.documentElement, {
                                childList: true,
                                subtree: true
                            });
                        }
                    }
                    
                    // 定期检查并阻止自动播放（防止某些视频绕过拦截）
                    setInterval(function() {
                        preventAutoplay();
                    }, 500);
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(interceptionScript, null)
            Log.d(TAG, "视频拦截脚本已注入")
            
        } catch (e: Exception) {
            Log.e(TAG, "注入视频拦截脚本失败", e)
        }
    }
    
    /**
     * 从 View 中提取视频 URL
     * 这是一个尝试性的方法，因为 Android 的 WebView 全屏 View 结构可能不同
     */
    private fun extractVideoUrlFromView(view: View?): String? {
        if (view == null) return null
        
        try {
            // 尝试通过反射获取视频 URL
            // 注意：这个方法可能不总是有效，因为 WebView 的内部实现可能不同
            val viewClass = view.javaClass
            val methods = viewClass.declaredMethods
            
            for (method in methods) {
                if (method.name.contains("getVideo") || method.name.contains("getSrc")) {
                    method.isAccessible = true
                    val result = method.invoke(view)
                    if (result is String && result.startsWith("http")) {
                        return result
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法通过反射提取视频URL", e)
        }
        
        return null
    }
    
    /**
     * 检测 URL 是否为媒体文件
     */
    fun isMediaUrl(url: String, contentType: String?): Boolean {
        if (url.isBlank()) return false
        
        val lowerUrl = url.lowercase()
        
        // 检测视频和音频扩展名
        val mediaExtensions = listOf(
            ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm", ".m3u8",
            ".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a", ".3gp", ".ts"
        )
        
        if (mediaExtensions.any { lowerUrl.contains(it) }) {
            return true
        }
        
        // 检测 Content-Type
        contentType?.let {
            val lowerContentType = it.lowercase()
            if (lowerContentType.startsWith("video/") ||
                lowerContentType.startsWith("audio/")) {
                return true
            }
        }
        
        // 检测常见的视频 URL 模式
        if (lowerUrl.contains("/video/") || 
            lowerUrl.contains("/media/") ||
            lowerUrl.contains("videoplayback") ||
            lowerUrl.contains("streaming")) {
            return true
        }
        
        return false
    }
}

