package com.example.aifloatingball.video

import android.content.Context
import android.util.Log

/**
 * ExoPlayer 优化功能使用示例
 * 
 * 展示如何使用新增的缩略图预览和缓冲进度功能
 * 
 * @author AI Floating Ball
 */
class ExoPlayerOptimizationExample {
    
    companion object {
        private const val TAG = "ExoPlayerExample"
    }
    
    /**
     * 示例 1: 使用缩略图预览助手
     */
    fun exampleThumbnailPreview(context: Context) {
        // 创建缩略图预览助手
        val thumbnailHelper = ThumbnailPreviewHelper(context)
        
        // 设置视频源
        val videoUrl = "https://example.com/video.mp4"
        thumbnailHelper.setVideoSource(videoUrl)
        
        // 预加载缩略图（可选，提升性能）
        val videoDuration = 300000L // 5分钟（毫秒）
        thumbnailHelper.preloadThumbnails(videoDuration, 10)
        
        // 获取指定位置的缩略图
        val position = 60000L // 1分钟位置
        thumbnailHelper.getThumbnail(position) { bitmap ->
            if (bitmap != null) {
                Log.d(TAG, "成功获取缩略图: ${bitmap.width}x${bitmap.height}")
                // 在这里使用 bitmap，例如显示在 ImageView 中
                // imageView.setImageBitmap(bitmap)
            } else {
                Log.w(TAG, "获取缩略图失败")
            }
        }
        
        // 使用完毕后释放资源
        // thumbnailHelper.release()
    }
    
    /**
     * 示例 2: 使用 ExoPlayerManager 的新功能
     */
    fun exampleExoPlayerManager(context: Context) {
        // 创建 ExoPlayerManager
        val exoPlayerManager = ExoPlayerManager(context)
        
        // ... 初始化和设置视频源 ...
        
        // 获取缓冲百分比
        val bufferedPercent = exoPlayerManager.getBufferedPercentage()
        Log.d(TAG, "缓冲进度: $bufferedPercent%")
        
        // 获取缓冲位置（毫秒）
        val bufferedPosition = exoPlayerManager.getBufferedPosition()
        Log.d(TAG, "已缓冲到: ${bufferedPosition}ms")
        
        // 检查是否有足够的缓冲
        val currentPosition = exoPlayerManager.getCurrentPosition()
        val duration = exoPlayerManager.getDuration()
        if (bufferedPosition > currentPosition + 10000) {
            Log.d(TAG, "缓冲充足，可以流畅播放")
        } else {
            Log.d(TAG, "缓冲不足，可能会卡顿")
        }
    }
    
    /**
     * 示例 3: 完整的视频播放流程（包含优化功能）
     */
    fun exampleCompletePlayback(context: Context) {
        val videoManager = SystemOverlayVideoManager(context)
        
        // 检查悬浮窗权限
        if (!videoManager.canDrawOverlays()) {
            videoManager.requestOverlayPermission()
            return
        }
        
        // 播放视频
        val videoUrl = "https://example.com/video.mp4"
        val pageTitle = "示例视频"
        
        // 显示悬浮窗播放器
        // 缩略图预览会自动启用
        // 缓冲进度会自动显示
        videoManager.show(
            videoUrl = videoUrl,
            pageTitle = pageTitle
        )
        
        Log.d(TAG, "视频播放器已启动，缩略图预览和缓冲进度已自动启用")
        
        // 用户拖动进度条时，会自动显示缩略图预览
        // 进度条会自动显示缓冲进度（secondaryProgress）
    }
    
    /**
     * 示例 4: 监控缓冲状态
     */
    fun exampleMonitorBuffering(exoPlayerManager: ExoPlayerManager) {
        // 创建一个定时任务，定期检查缓冲状态
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkBufferingRunnable = object : Runnable {
            override fun run() {
                val bufferedPercent = exoPlayerManager.getBufferedPercentage()
                val bufferedPosition = exoPlayerManager.getBufferedPosition()
                val currentPosition = exoPlayerManager.getCurrentPosition()
                
                Log.d(TAG, "缓冲监控 - 当前位置: ${currentPosition}ms, " +
                        "缓冲位置: ${bufferedPosition}ms, " +
                        "缓冲百分比: $bufferedPercent%")
                
                // 如果缓冲不足，可以显示加载提示
                if (bufferedPosition - currentPosition < 5000) {
                    Log.w(TAG, "缓冲不足，建议显示加载提示")
                }
                
                // 继续监控
                handler.postDelayed(this, 1000) // 每秒检查一次
            }
        }
        
        // 开始监控
        handler.post(checkBufferingRunnable)
        
        // 停止监控时调用
        // handler.removeCallbacks(checkBufferingRunnable)
    }
    
    /**
     * 示例 5: 自定义缩略图预览样式
     */
    fun exampleCustomThumbnailStyle(context: Context) {
        // 注意：这个示例展示了如何在自定义实现中使用缩略图预览
        // SystemOverlayVideoManager 已经内置了缩略图预览功能
        
        val thumbnailHelper = ThumbnailPreviewHelper(context)
        val videoUrl = "https://example.com/video.mp4"
        thumbnailHelper.setVideoSource(videoUrl)
        
        // 创建自定义的预览视图
        val previewImageView = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                200, // 宽度
                150  // 高度
            )
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            
            // 设置自定义背景
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE0000000.toInt())
                cornerRadius = 12f
                setStroke(3, 0xFFFFFFFF.toInt())
            }
        }
        
        // 在拖动进度条时更新预览
        val position = 120000L // 2分钟位置
        thumbnailHelper.getThumbnail(position) { bitmap ->
            if (bitmap != null) {
                previewImageView.setImageBitmap(bitmap)
                // 显示预览视图
                previewImageView.visibility = android.view.View.VISIBLE
            }
        }
    }
    
    /**
     * 示例 6: 性能优化建议
     */
    fun examplePerformanceOptimization() {
        Log.d(TAG, """
            性能优化建议:
            
            1. 缩略图预览:
               - 使用预加载功能提升响应速度
               - 及时清理缓存，避免内存泄漏
               - 对于长视频，适当减少预加载数量
            
            2. 缓冲策略:
               - 网络较差时，可以增加缓冲时间
               - 本地视频可以减少缓冲时间
               - 根据实际情况调整缓冲参数
            
            3. 内存管理:
               - 播放器不使用时及时释放资源
               - 监控内存使用情况
               - 避免同时创建多个播放器实例
            
            4. UI 更新:
               - 所有 UI 更新确保在主线程
               - 使用 View.post 避免线程冲突
               - 减少不必要的 UI 刷新频率
        """.trimIndent())
    }
}
