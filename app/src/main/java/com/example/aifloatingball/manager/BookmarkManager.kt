package com.example.aifloatingball.manager

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.aifloatingball.model.Bookmark
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * 书签管理器
 * 负责书签的增删改查和导入导出
 */
class BookmarkManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "BookmarkManager"
        private const val PREF_NAME = "bookmarks"
        private const val KEY_BOOKMARKS = "bookmarks_list"
        private const val FAVICON_DIR = "favicons"
        
        @Volatile
        private var instance: BookmarkManager? = null
        
        fun getInstance(context: Context): BookmarkManager {
            return instance ?: synchronized(this) {
                instance ?: BookmarkManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 确保 favicon 目录存在
    private val faviconDir: File by lazy {
        File(context.filesDir, FAVICON_DIR).also { it.mkdirs() }
    }
    
    /**
     * 获取所有书签
     */
    fun getAllBookmarks(): List<Bookmark> {
        val json = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        val type = object : TypeToken<List<Bookmark>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    /**
     * 按文件夹分组获取书签
     */
    fun getBookmarksByFolder(): Map<String, List<Bookmark>> {
        return getAllBookmarks().groupBy { it.folder }
    }
    
    /**
     * 根据 URL 检查书签是否存在
     */
    fun isBookmarkExist(url: String): Boolean {
        return getAllBookmarks().any { it.url == url }
    }
    
    /**
     * 根据 ID 查找书签
     */
    fun getBookmarkById(id: String): Bookmark? {
        return getAllBookmarks().find { it.id == id }
    }
    
    /**
     * 添加书签
     */
    fun addBookmark(bookmark: Bookmark, favicon: Bitmap? = null): Boolean {
        val bookmarks = getAllBookmarks().toMutableList()
        
        // 检查是否已有相同 URL 的书签
        val existingIndex = bookmarks.indexOfFirst { it.url == bookmark.url }
        if (existingIndex >= 0) {
            // 更新现有书签
            bookmarks[existingIndex] = bookmark
        } else {
            // 添加新书签
            bookmarks.add(bookmark)
        }
        
        // 保存书签列表
        val result = saveBookmarks(bookmarks)
        
        // 保存图标
        favicon?.let { saveFavicon(bookmark.id, it) }
        
        return result
    }
    
    /**
     * 更新书签
     */
    fun updateBookmark(bookmark: Bookmark, favicon: Bitmap? = null): Boolean {
        val bookmarks = getAllBookmarks().toMutableList()
        val index = bookmarks.indexOfFirst { it.id == bookmark.id }
        
        if (index >= 0) {
            bookmarks[index] = bookmark
            favicon?.let { saveFavicon(bookmark.id, it) }
            return saveBookmarks(bookmarks)
        }
        
        return false
    }
    
    /**
     * 删除书签
     */
    fun deleteBookmark(bookmarkId: String): Boolean {
        val bookmarks = getAllBookmarks().toMutableList()
        val removed = bookmarks.removeIf { it.id == bookmarkId }
        
        if (removed) {
            // 删除相关的 favicon
            val faviconFile = File(faviconDir, "favicon_$bookmarkId.png")
            if (faviconFile.exists()) {
                faviconFile.delete()
            }
            
            return saveBookmarks(bookmarks)
        }
        
        return false
    }
    
    /**
     * 清空所有书签
     */
    fun clearAllBookmarks(): Boolean {
        // 删除所有 favicon 文件
        faviconDir.listFiles()?.forEach { it.delete() }
        
        // 清空书签列表
        return prefs.edit().putString(KEY_BOOKMARKS, "[]").commit()
    }
    
    /**
     * 保存 favicon 图标
     */
    fun saveFavicon(bookmarkId: String, favicon: Bitmap): Boolean {
        return try {
            val file = File(faviconDir, "favicon_$bookmarkId.png")
            val outputStream = FileOutputStream(file)
            favicon.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: IOException) {
            Log.e(TAG, "保存 favicon 失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取 favicon 文件
     */
    fun getFaviconFile(bookmarkId: String): File? {
        val file = File(faviconDir, "favicon_$bookmarkId.png")
        return if (file.exists()) file else null
    }
    
    /**
     * 保存书签列表
     */
    private fun saveBookmarks(bookmarks: List<Bookmark>): Boolean {
        val json = gson.toJson(bookmarks)
        return prefs.edit().putString(KEY_BOOKMARKS, json).commit()
    }
    
    /**
     * 获取所有书签文件夹
     */
    fun getAllFolders(): List<String> {
        return getAllBookmarks()
            .map { it.folder }
            .distinct()
            .sortedWith(compareBy(
                // 确保"默认"文件夹始终排在第一位
                { if (it == "默认") 0 else 1 },
                { it }
            ))
    }
    
    /**
     * 重命名书签文件夹
     */
    fun renameFolder(oldName: String, newName: String): Boolean {
        if (oldName == newName) return true
        
        val bookmarks = getAllBookmarks().toMutableList()
        var changed = false
        
        for (i in bookmarks.indices) {
            if (bookmarks[i].folder == oldName) {
                bookmarks[i] = bookmarks[i].copy(folder = newName)
                changed = true
            }
        }
        
        return if (changed) saveBookmarks(bookmarks) else true
    }
} 