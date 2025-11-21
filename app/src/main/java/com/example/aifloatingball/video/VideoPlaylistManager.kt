package com.example.aifloatingball.video

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 视频播放列表管理器
 * 记录近期播放的视频，支持添加、删除、获取列表等功能
 * 
 * @author AI Floating Ball
 */
class VideoPlaylistManager(private val context: Context) {
    companion object {
        private const val TAG = "VideoPlaylistManager"
        private const val PREFS_NAME = "video_playlist"
        private const val KEY_PLAYLIST = "playlist"
        private const val MAX_PLAYLIST_SIZE = 50 // 最大播放列表数量
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 视频播放记录数据类
     */
    data class VideoPlayItem(
        val url: String,
        val title: String = "",
        val thumbnail: String? = null, // 缩略图路径
        val duration: Long = 0, // 视频时长（毫秒）
        val playTime: Long = System.currentTimeMillis(), // 播放时间
        val playPosition: Long = 0 // 播放位置（毫秒）
    )
    
    /**
     * 添加视频到播放列表
     */
    fun addVideo(url: String, title: String = "", duration: Long = 0, playPosition: Long = 0) {
        try {
            val playlist = getPlaylist().toMutableList()
            
            // 移除已存在的相同URL（避免重复）
            playlist.removeAll { it.url == url }
            
            // 添加到列表开头
            val newItem = VideoPlayItem(
                url = url,
                title = title.ifBlank { extractTitleFromUrl(url) },
                duration = duration,
                playPosition = playPosition,
                playTime = System.currentTimeMillis()
            )
            playlist.add(0, newItem)
            
            // 限制列表大小
            if (playlist.size > MAX_PLAYLIST_SIZE) {
                playlist.removeAt(playlist.size - 1)
            }
            
            // 保存到SharedPreferences
            savePlaylist(playlist)
            Log.d(TAG, "已添加视频到播放列表: $url")
        } catch (e: Exception) {
            Log.e(TAG, "添加视频到播放列表失败", e)
        }
    }
    
    /**
     * 更新视频播放位置
     */
    fun updatePlayPosition(url: String, playPosition: Long) {
        try {
            val playlist = getPlaylist().toMutableList()
            val index = playlist.indexOfFirst { it.url == url }
            if (index >= 0) {
                val item = playlist[index]
                playlist[index] = item.copy(playPosition = playPosition)
                savePlaylist(playlist)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新播放位置失败", e)
        }
    }
    
    /**
     * 从播放列表移除视频
     */
    fun removeVideo(url: String) {
        try {
            val playlist = getPlaylist().toMutableList()
            playlist.removeAll { it.url == url }
            savePlaylist(playlist)
            Log.d(TAG, "已从播放列表移除视频: $url")
        } catch (e: Exception) {
            Log.e(TAG, "从播放列表移除视频失败", e)
        }
    }
    
    /**
     * 清空播放列表
     */
    fun clearPlaylist() {
        try {
            prefs.edit().remove(KEY_PLAYLIST).apply()
            Log.d(TAG, "播放列表已清空")
        } catch (e: Exception) {
            Log.e(TAG, "清空播放列表失败", e)
        }
    }
    
    /**
     * 获取播放列表
     */
    fun getPlaylist(): List<VideoPlayItem> {
        try {
            val json = prefs.getString(KEY_PLAYLIST, null)
            if (json.isNullOrBlank()) {
                return emptyList()
            }
            
            val type = object : TypeToken<List<VideoPlayItem>>() {}.type
            return gson.fromJson<List<VideoPlayItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "获取播放列表失败", e)
            return emptyList()
        }
    }
    
    /**
     * 获取最近的播放记录
     */
    fun getRecentVideos(limit: Int = 10): List<VideoPlayItem> {
        return getPlaylist().take(limit)
    }
    
    /**
     * 保存播放列表
     */
    private fun savePlaylist(playlist: List<VideoPlayItem>) {
        try {
            val json = gson.toJson(playlist)
            prefs.edit().putString(KEY_PLAYLIST, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存播放列表失败", e)
        }
    }
    
    /**
     * 从URL提取标题
     */
    private fun extractTitleFromUrl(url: String): String {
        return try {
            val fileName = url.substringAfterLast("/").substringBefore("?")
            if (fileName.isNotBlank()) {
                fileName
            } else {
                "视频 ${System.currentTimeMillis()}"
            }
        } catch (e: Exception) {
            "视频 ${System.currentTimeMillis()}"
        }
    }
}

