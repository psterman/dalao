package com.example.aifloatingball.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*

/**
 * 缩略图预览助手
 * 
 * 用于在拖动进度条时生成和显示视频缩略图预览
 * 使用 LRU 缓存优化性能
 * 
 * @author AI Floating Ball
 */
class ThumbnailPreviewHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "ThumbnailPreview"
        private const val CACHE_SIZE = 20 // 缓存最多20个缩略图
        // 横屏缩略图尺寸（16:9）
        private const val THUMBNAIL_WIDTH_LANDSCAPE = 160
        private const val THUMBNAIL_HEIGHT_LANDSCAPE = 90
        // 竖屏缩略图尺寸（9:16）
        private const val THUMBNAIL_WIDTH_PORTRAIT = 90
        private const val THUMBNAIL_HEIGHT_PORTRAIT = 160
    }
    
    // 缩略图缓存（使用 LruCache）
    private val thumbnailCache = LruCache<Long, Bitmap>(CACHE_SIZE)
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // MediaMetadataRetriever
    private var retriever: MediaMetadataRetriever? = null
    
    // 当前视频URL
    private var currentVideoUrl: String? = null
    
    // 视频是否为竖屏
    private var isVideoPortrait: Boolean = false
    
    /**
     * 设置视频源
     */
    fun setVideoSource(videoUrl: String) {
        if (currentVideoUrl == videoUrl) {
            return // 相同视频，无需重新设置
        }
        
        currentVideoUrl = videoUrl
        
        // 清空缓存
        thumbnailCache.evictAll()
        
        // 释放旧的 retriever
        releaseRetriever()
        
        // 创建新的 retriever
        try {
            retriever = MediaMetadataRetriever().apply {
                setDataSource(videoUrl, HashMap())
                
                // 检测视频方向
                try {
                    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    isVideoPortrait = height > width
                    Log.d(TAG, "视频尺寸: ${width}x${height}, 是否为竖屏: $isVideoPortrait")
                } catch (e: Exception) {
                    Log.w(TAG, "无法检测视频方向", e)
                    isVideoPortrait = false
                }
            }
            Log.d(TAG, "视频源已设置: $videoUrl")
        } catch (e: Exception) {
            Log.e(TAG, "设置视频源失败: $videoUrl", e)
            retriever = null
        }
    }
    
    /**
     * 获取指定位置的缩略图
     * 
     * @param positionMs 视频位置（毫秒）
     * @param callback 回调函数，返回缩略图 Bitmap
     */
    fun getThumbnail(positionMs: Long, callback: (Bitmap?) -> Unit) {
        // 检查精确缓存
        val cached = thumbnailCache.get(positionMs)
        if (cached != null) {
            callback(cached)
            return
        }
        
        // 检查近似缓存（允许±500ms的误差）
        val tolerance = 500L
        for (i in -5..5) {
            val approximatePos = positionMs + (i * 100)
            val approximateCached = thumbnailCache.get(approximatePos)
            if (approximateCached != null && Math.abs(approximatePos - positionMs) <= tolerance) {
                // 找到近似缓存，直接使用
                callback(approximateCached)
                return
            }
        }
        
        // 异步生成缩略图
        scope.launch {
            try {
                val bitmap = generateThumbnail(positionMs)
                if (bitmap != null) {
                    // 缓存缩略图
                    thumbnailCache.put(positionMs, bitmap)
                    
                    // 回调到主线程
                    withContext(Dispatchers.Main) {
                        callback(bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "生成缩略图失败: positionMs=$positionMs", e)
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    
    /**
     * 预加载缩略图（提前生成常用位置的缩略图）
     * 
     * @param durationMs 视频总时长（毫秒）
     * @param count 预加载数量
     */
    fun preloadThumbnails(durationMs: Long, count: Int = 10) {
        if (durationMs <= 0) return
        
        scope.launch {
            try {
                val interval = durationMs / count
                for (i in 0 until count) {
                    val positionMs = i * interval
                    
                    // 检查是否已缓存
                    if (thumbnailCache.get(positionMs) != null) {
                        continue
                    }
                    
                    // 生成缩略图
                    val bitmap = generateThumbnail(positionMs)
                    if (bitmap != null) {
                        thumbnailCache.put(positionMs, bitmap)
                        Log.d(TAG, "预加载缩略图: positionMs=$positionMs")
                    }
                    
                    // 延迟一下，避免占用太多资源
                    delay(100)
                }
                Log.d(TAG, "缩略图预加载完成")
            } catch (e: Exception) {
                Log.e(TAG, "预加载缩略图失败", e)
            }
        }
    }
    
    /**
     * 生成缩略图
     * 优化：使用 OPTION_CLOSEST 提供更精确的帧提取，根据视频实际宽高比生成缩略图
     */
    private fun generateThumbnail(positionMs: Long): Bitmap? {
        val currentRetriever = retriever ?: return null
        
        return try {
            // 获取视频帧（使用 OPTION_CLOSEST 提供更精确的位置）
            val frame = currentRetriever.getFrameAtTime(
                positionMs * 1000, // 转换为微秒
                MediaMetadataRetriever.OPTION_CLOSEST // 使用最接近的帧，而不是最近的关键帧
            )
            
            if (frame != null) {
                // 计算缩略图尺寸（基于视频实际宽高比）
                val (targetWidth, targetHeight) = calculateThumbnailSize(frame.width, frame.height)
                
                // 缩放到目标尺寸
                Bitmap.createScaledBitmap(frame, targetWidth, targetHeight, true).also {
                    // 回收原始帧
                    if (it != frame) {
                        frame.recycle()
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成缩略图失败: positionMs=$positionMs", e)
            null
        }
    }
    
    /**
     * 计算缩略图尺寸
     * 根据视频实际宽高比动态计算
     */
    private fun calculateThumbnailSize(videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0) {
            // 默认使用横屏尺寸
            return Pair(THUMBNAIL_WIDTH_LANDSCAPE, THUMBNAIL_HEIGHT_LANDSCAPE)
        }
        
        // 计算视频宽高比
        val aspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
        
        // 定义最大尺寸
        val maxSize = 200
        
        // 根据宽高比计算缩略图尺寸
        return if (aspectRatio > 1.0f) {
            // 横屏视频（宽 > 高）
            val width = maxSize
            val height = (width / aspectRatio).toInt()
            Pair(width, height)
        } else {
            // 竖屏视频（高 >= 宽）
            val height = maxSize
            val width = (height * aspectRatio).toInt()
            Pair(width, height)
        }
    }

    
    /**
     * 清空缓存
     */
    fun clearCache() {
        thumbnailCache.evictAll()
        Log.d(TAG, "缩略图缓存已清空")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            // 取消所有协程
            scope.cancel()
            
            // 清空缓存
            clearCache()
            
            // 释放 retriever
            releaseRetriever()
            
            currentVideoUrl = null
            
            Log.d(TAG, "资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
    }
    
    /**
     * 释放 MediaMetadataRetriever
     */
    private fun releaseRetriever() {
        try {
            retriever?.release()
            retriever = null
        } catch (e: Exception) {
            Log.e(TAG, "释放 MediaMetadataRetriever 失败", e)
        }
    }
}
