package com.example.aifloatingball.video

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * 无障碍优化辅助类
 * 
 * 功能：
 * - TalkBack 支持
 * - 内容描述
 * - 焦点导航
 * - 手势辅助
 * 
 * @author AI Floating Ball
 */
class AccessibilityHelper(private val context: Context) {
    
    private val accessibilityManager: AccessibilityManager? =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    
    /**
     * 是否启用了无障碍服务
     */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager?.isEnabled == true
    }
    
    /**
     * 是否启用了 TalkBack
     */
    fun isTalkBackEnabled(): Boolean {
        return accessibilityManager?.isTouchExplorationEnabled == true
    }
    
    /**
     * 设置播放/暂停按钮的无障碍描述
     */
    fun setupPlayPauseButton(button: ImageButton, isPlaying: Boolean) {
        val description = if (isPlaying) "暂停播放" else "开始播放"
        button.contentDescription = description
        
        // 设置自定义操作
        ViewCompat.setAccessibilityDelegate(button, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        description
                    )
                )
            }
        })
    }
    
    /**
     * 设置进度条的无障碍描述
     */
    fun setupSeekBar(seekBar: SeekBar, currentTime: String, totalTime: String) {
        seekBar.contentDescription = "播放进度: $currentTime / $totalTime"
        
        ViewCompat.setAccessibilityDelegate(seekBar, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                // 添加快进/快退操作
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
                        "快进 10 秒"
                    )
                )
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
                        "快退 10 秒"
                    )
                )
            }
            
            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: android.os.Bundle?
            ): Boolean {
                when (action) {
                    AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> {
                        // 快进 10 秒
                        val newProgress = (seekBar.progress + 10).coerceAtMost(seekBar.max)
                        seekBar.progress = newProgress
                        announceForAccessibility("快进 10 秒")
                        return true
                    }
                    AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> {
                        // 快退 10 秒
                        val newProgress = (seekBar.progress - 10).coerceAtLeast(0)
                        seekBar.progress = newProgress
                        announceForAccessibility("快退 10 秒")
                        return true
                    }
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })
    }
    
    /**
     * 设置音量按钮的无障碍描述
     */
    fun setupVolumeButton(button: ImageButton, isMuted: Boolean) {
        val description = if (isMuted) "取消静音" else "静音"
        button.contentDescription = description
    }
    
    /**
     * 设置全屏按钮的无障碍描述
     */
    fun setupFullscreenButton(button: ImageButton, isFullscreen: Boolean) {
        val description = if (isFullscreen) "退出全屏" else "进入全屏"
        button.contentDescription = description
    }
    
    /**
     * 设置播放速度按钮的无障碍描述
     */
    fun setupSpeedButton(button: View, speed: Float) {
        button.contentDescription = "播放速度: ${speed}倍"
    }
    
    /**
     * 设置循环按钮的无障碍描述
     */
    fun setupLoopButton(button: ImageButton, isLooping: Boolean) {
        val description = if (isLooping) "取消循环播放" else "循环播放"
        button.contentDescription = description
    }
    
    /**
     * 播报无障碍消息
     */
    fun announceForAccessibility(message: String) {
        if (isAccessibilityEnabled()) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.text.add(message)
            accessibilityManager?.sendAccessibilityEvent(event)
        }
    }
    
    /**
     * 设置视频容器的无障碍描述
     */
    fun setupVideoContainer(container: View, videoTitle: String) {
        container.contentDescription = "正在播放: $videoTitle"
        container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    
    /**
     * 设置控制条的无障碍描述
     */
    fun setupControlBar(controlBar: View) {
        controlBar.contentDescription = "视频播放控制"
        controlBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    
    /**
     * 播报播放状态变化
     */
    fun announcePlaybackStateChange(isPlaying: Boolean) {
        val message = if (isPlaying) "开始播放" else "暂停播放"
        announceForAccessibility(message)
    }
    
    /**
     * 播报进度变化
     */
    fun announceProgressChange(currentTime: String, totalTime: String) {
        announceForAccessibility("当前进度: $currentTime / $totalTime")
    }
    
    /**
     * 播报音量变化
     */
    fun announceVolumeChange(volume: Int) {
        announceForAccessibility("音量: $volume%")
    }
    
    /**
     * 播报亮度变化
     */
    fun announceBrightnessChange(brightness: Int) {
        announceForAccessibility("亮度: $brightness%")
    }
    
    /**
     * 播报播放速度变化
     */
    fun announceSpeedChange(speed: Float) {
        announceForAccessibility("播放速度: ${speed}倍")
    }
    
    /**
     * 播报视频切换
     */
    fun announceVideoChange(videoTitle: String) {
        announceForAccessibility("切换到: $videoTitle")
    }
    
    /**
     * 播报错误信息
     */
    fun announceError(errorMessage: String) {
        announceForAccessibility("错误: $errorMessage")
    }
    
    /**
     * 设置焦点顺序
     */
    fun setupFocusOrder(views: List<View>) {
        for (i in 0 until views.size - 1) {
            views[i].nextFocusDownId = views[i + 1].id
            views[i + 1].nextFocusUpId = views[i].id
        }
    }
    
    /**
     * 请求焦点
     */
    fun requestFocus(view: View) {
        if (isAccessibilityEnabled()) {
            view.requestFocus()
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }
}
