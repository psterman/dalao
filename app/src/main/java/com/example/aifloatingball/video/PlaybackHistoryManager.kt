package com.example.aifloatingball.video

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 播放历史管理器 - 实现断点续播功能
 * 
 * 功能:
 * 1. 保存视频播放进度
 * 2. 恢复上次播放位置
 * 3. 管理播放历史记录
 * 4. 自动清理过期记录
 */
class PlaybackHistoryManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    companion object {
        private const val TAG = "PlaybackHistory"
        private const val PREFS_NAME = "video_playback_history"
        private const val KEY_HISTORY_LIST = "history_list"
        private const val MAX_HISTORY_SIZE = 100 // 最多保存100条历史记录
        private const val RESUME_THRESHOLD = 5000L // 5秒，小于此时长不保存进度
        private const val COMPLETION_THRESHOLD = 0.95f // 播放超过95%视为已完成
    }
    
    /**
     * 播放历史记录数据类
     */
    data class PlaybackHistory(
        val videoUrl: String,
        val videoTitle: String,
        val position: Long,          // 播放位置（毫秒）
        val duration: Long,          // 视频总时长（毫秒）
        val timestamp: Long,         // 记录时间戳
        val thumbnail: String? = null // 缩略图URL（可选）
    ) {
        /**
         * 是否应该恢复播放（未播放完成且进度有效）
         */
        fun shouldResume(): Boolean {
            if (duration <= 0) return false
            val progress = position.toFloat() / duration
            return position > RESUME_THRESHOLD && progress < COMPLETION_THRESHOLD
        }
        
        /**
         * 获取播放进度百分比
         */
        fun getProgressPercent(): Int {
            return if (duration > 0) {
                ((position.toFloat() / duration) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
        }
    }
    
    /**
     * 保存播放进度
     */
    fun savePlaybackProgress(
        videoUrl: String,
        videoTitle: String,
        position: Long,
        duration: Long,
        thumbnail: String? = null
    ) {
        try {
            // 如果播放时间太短，不保存
            if (position < RESUME_THRESHOLD) {
                Log.d(TAG, "播放时间太短，不保存进度: $position ms")
                return
            }
            
            // 如果已播放完成，不保存进度（或保存为0）
            if (duration > 0) {
                val progress = position.toFloat() / duration
                if (progress >= COMPLETION_THRESHOLD) {
                    Log.d(TAG, "视频已播放完成，不保存进度: ${(progress * 100).toInt()}%")
                    // 可选：保存为已完成状态
                    // saveCompletedVideo(videoUrl, videoTitle, duration, thumbnail)
                    return
                }
            }
            
            val history = PlaybackHistory(
                videoUrl = videoUrl,
                videoTitle = videoTitle,
                position = position,
                duration = duration,
                timestamp = System.currentTimeMillis(),
                thumbnail = thumbnail
            )
            
            val historyList = getHistoryList().toMutableList()
            
            // 移除相同URL的旧记录
            historyList.removeAll { it.videoUrl == videoUrl }
            
            // 添加新记录到列表开头
            historyList.add(0, history)
            
            // 限制历史记录数量
            if (historyList.size > MAX_HISTORY_SIZE) {
                historyList.subList(MAX_HISTORY_SIZE, historyList.size).clear()
            }
            
            // 保存到SharedPreferences
            val json = gson.toJson(historyList)
            prefs.edit().putString(KEY_HISTORY_LIST, json).apply()
            
            Log.d(TAG, "保存播放进度: $videoTitle, 位置: ${formatTime(position)}/${formatTime(duration)}")
        } catch (e: Exception) {
            Log.e(TAG, "保存播放进度失败", e)
        }
    }
    
    /**
     * 获取视频的播放历史
     */
    fun getPlaybackHistory(videoUrl: String): PlaybackHistory? {
        return try {
            getHistoryList().firstOrNull { it.videoUrl == videoUrl }
        } catch (e: Exception) {
            Log.e(TAG, "获取播放历史失败", e)
            null
        }
    }
    
    /**
     * 获取所有播放历史（按时间倒序）
     */
    fun getHistoryList(): List<PlaybackHistory> {
        return try {
            val json = prefs.getString(KEY_HISTORY_LIST, null) ?: return emptyList()
            val type = object : TypeToken<List<PlaybackHistory>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "读取播放历史失败", e)
            emptyList()
        }
    }
    
    /**
     * 删除指定视频的播放历史
     */
    fun removeHistory(videoUrl: String) {
        try {
            val historyList = getHistoryList().toMutableList()
            historyList.removeAll { it.videoUrl == videoUrl }
            
            val json = gson.toJson(historyList)
            prefs.edit().putString(KEY_HISTORY_LIST, json).apply()
            
            Log.d(TAG, "删除播放历史: $videoUrl")
        } catch (e: Exception) {
            Log.e(TAG, "删除播放历史失败", e)
        }
    }
    
    /**
     * 清空所有播放历史
     */
    fun clearAllHistory() {
        try {
            prefs.edit().remove(KEY_HISTORY_LIST).apply()
            Log.d(TAG, "清空所有播放历史")
        } catch (e: Exception) {
            Log.e(TAG, "清空播放历史失败", e)
        }
    }
    
    /**
     * 清理过期的播放历史（超过30天）
     */
    fun cleanupOldHistory(daysToKeep: Int = 30) {
        try {
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val historyList = getHistoryList().toMutableList()
            val originalSize = historyList.size
            
            historyList.removeAll { it.timestamp < cutoffTime }
            
            if (historyList.size < originalSize) {
                val json = gson.toJson(historyList)
                prefs.edit().putString(KEY_HISTORY_LIST, json).apply()
                
                Log.d(TAG, "清理过期历史: 删除 ${originalSize - historyList.size} 条记录")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理过期历史失败", e)
        }
    }
    
    /**
     * 格式化时间显示
     */
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
