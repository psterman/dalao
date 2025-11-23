package com.example.aifloatingball.video

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 播放统计管理器
 * 
 * 功能：
 * - 播放次数统计
 * - 播放时长统计
 * - 最常播放视频
 * - 播放历史分析
 * 
 * @author AI Floating Ball
 */
class PlaybackStatistics(private val context: Context) {
    
    companion object {
        private const val TAG = "PlaybackStatistics"
        private const val PREFS_NAME = "playback_statistics"
        private const val KEY_STATISTICS = "statistics_data"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 播放统计数据
     */
    data class VideoStatistics(
        val videoUrl: String,
        val videoTitle: String,
        var playCount: Int = 0,
        var totalPlayTime: Long = 0, // 总播放时长（毫秒）
        var lastPlayTime: Long = 0, // 最后播放时间
        var completionCount: Int = 0, // 完整播放次数
        var averageWatchPercentage: Float = 0f // 平均观看百分比
    )
    
    /**
     * 记录播放开始
     */
    fun recordPlayStart(videoUrl: String, videoTitle: String) {
        try {
            val stats = getStatistics()
            val videoStats = stats.find { it.videoUrl == videoUrl }
                ?: VideoStatistics(videoUrl, videoTitle).also { stats.add(it) }
            
            videoStats.playCount++
            videoStats.lastPlayTime = System.currentTimeMillis()
            
            saveStatistics(stats)
            Log.d(TAG, "记录播放开始: $videoTitle, 播放次数: ${videoStats.playCount}")
        } catch (e: Exception) {
            Log.e(TAG, "记录播放开始失败", e)
        }
    }
    
    /**
     * 记录播放时长
     */
    fun recordPlayTime(videoUrl: String, playTimeMs: Long) {
        try {
            val stats = getStatistics()
            val videoStats = stats.find { it.videoUrl == videoUrl } ?: return
            
            videoStats.totalPlayTime += playTimeMs
            
            saveStatistics(stats)
            Log.d(TAG, "记录播放时长: ${videoStats.videoTitle}, 时长: ${playTimeMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "记录播放时长失败", e)
        }
    }
    
    /**
     * 记录播放完成
     */
    fun recordPlayCompletion(videoUrl: String, watchPercentage: Float) {
        try {
            val stats = getStatistics()
            val videoStats = stats.find { it.videoUrl == videoUrl } ?: return
            
            if (watchPercentage >= 90f) {
                videoStats.completionCount++
            }
            
            // 更新平均观看百分比
            val totalWatchPercentage = videoStats.averageWatchPercentage * (videoStats.playCount - 1) + watchPercentage
            videoStats.averageWatchPercentage = totalWatchPercentage / videoStats.playCount
            
            saveStatistics(stats)
            Log.d(TAG, "记录播放完成: ${videoStats.videoTitle}, 观看百分比: $watchPercentage%")
        } catch (e: Exception) {
            Log.e(TAG, "记录播放完成失败", e)
        }
    }
    
    /**
     * 获取最常播放的视频
     */
    fun getMostPlayedVideos(limit: Int = 10): List<VideoStatistics> {
        return getStatistics()
            .sortedByDescending { it.playCount }
            .take(limit)
    }
    
    /**
     * 获取最近播放的视频
     */
    fun getRecentlyPlayedVideos(limit: Int = 10): List<VideoStatistics> {
        return getStatistics()
            .sortedByDescending { it.lastPlayTime }
            .take(limit)
    }
    
    /**
     * 获取总播放时长
     */
    fun getTotalPlayTime(): Long {
        return getStatistics().sumOf { it.totalPlayTime }
    }
    
    /**
     * 获取总播放次数
     */
    fun getTotalPlayCount(): Int {
        return getStatistics().sumOf { it.playCount }
    }
    
    /**
     * 获取视频统计信息
     */
    fun getVideoStatistics(videoUrl: String): VideoStatistics? {
        return getStatistics().find { it.videoUrl == videoUrl }
    }
    
    /**
     * 获取所有统计数据
     */
    fun getStatistics(): MutableList<VideoStatistics> {
        try {
            val json = prefs.getString(KEY_STATISTICS, null) ?: return mutableListOf()
            val jsonArray = JSONArray(json)
            val stats = mutableListOf<VideoStatistics>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                stats.add(
                    VideoStatistics(
                        videoUrl = obj.getString("videoUrl"),
                        videoTitle = obj.getString("videoTitle"),
                        playCount = obj.getInt("playCount"),
                        totalPlayTime = obj.getLong("totalPlayTime"),
                        lastPlayTime = obj.getLong("lastPlayTime"),
                        completionCount = obj.getInt("completionCount"),
                        averageWatchPercentage = obj.getDouble("averageWatchPercentage").toFloat()
                    )
                )
            }
            
            return stats
        } catch (e: Exception) {
            Log.e(TAG, "获取统计数据失败", e)
            return mutableListOf()
        }
    }
    
    /**
     * 保存统计数据
     */
    private fun saveStatistics(stats: List<VideoStatistics>) {
        try {
            val jsonArray = JSONArray()
            stats.forEach { stat ->
                val obj = JSONObject().apply {
                    put("videoUrl", stat.videoUrl)
                    put("videoTitle", stat.videoTitle)
                    put("playCount", stat.playCount)
                    put("totalPlayTime", stat.totalPlayTime)
                    put("lastPlayTime", stat.lastPlayTime)
                    put("completionCount", stat.completionCount)
                    put("averageWatchPercentage", stat.averageWatchPercentage)
                }
                jsonArray.put(obj)
            }
            
            prefs.edit().putString(KEY_STATISTICS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存统计数据失败", e)
        }
    }
    
    /**
     * 清空统计数据
     */
    fun clearStatistics() {
        prefs.edit().remove(KEY_STATISTICS).apply()
        Log.d(TAG, "统计数据已清空")
    }
    
    /**
     * 格式化播放时长
     */
    fun formatPlayTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        
        return when {
            hours > 0 -> String.format("%d小时%d分钟", hours, minutes)
            minutes > 0 -> String.format("%d分钟%d秒", minutes, seconds)
            else -> String.format("%d秒", seconds)
        }
    }
    
    /**
     * 格式化日期
     */
    fun formatDate(timeMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timeMs))
    }
    
    /**
     * 生成统计报告
     */
    fun generateReport(): String {
        val stats = getStatistics()
        val totalPlayTime = getTotalPlayTime()
        val totalPlayCount = getTotalPlayCount()
        val mostPlayed = getMostPlayedVideos(5)
        
        return buildString {
            appendLine("=== 播放统计报告 ===")
            appendLine()
            appendLine("总播放次数: $totalPlayCount")
            appendLine("总播放时长: ${formatPlayTime(totalPlayTime)}")
            appendLine("视频总数: ${stats.size}")
            appendLine()
            appendLine("最常播放的视频:")
            mostPlayed.forEachIndexed { index, video ->
                appendLine("${index + 1}. ${video.videoTitle}")
                appendLine("   播放次数: ${video.playCount}")
                appendLine("   总时长: ${formatPlayTime(video.totalPlayTime)}")
                appendLine("   完整播放: ${video.completionCount}次")
                appendLine("   平均观看: ${String.format("%.1f", video.averageWatchPercentage)}%")
                appendLine()
            }
        }
    }
}
