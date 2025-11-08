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
    
    /**
     * 从旧的BookmarkEntry系统迁移数据到新的Bookmark系统
     * 只在首次启动时调用一次，迁移完成后会标记，避免重复迁移
     */
    fun migrateFromBookmarkEntry(): Int {
        try {
            // 检查是否已经迁移过
            val migrationKey = "bookmark_migration_completed"
            if (prefs.getBoolean(migrationKey, false)) {
                Log.d(TAG, "书签数据已迁移，跳过")
                return 0
            }
            
            // 读取旧的BookmarkEntry数据
            val oldPrefs = context.getSharedPreferences("browser_bookmarks", Context.MODE_PRIVATE)
            val oldBookmarksJson = oldPrefs.getString("bookmarks_data", "[]") ?: "[]"
            
            if (oldBookmarksJson.isEmpty() || oldBookmarksJson == "[]") {
                // 没有旧数据，标记为已迁移
                prefs.edit().putBoolean(migrationKey, true).apply()
                Log.d(TAG, "没有旧书签数据需要迁移")
                return 0
            }
            
            // 解析旧数据
            val gson = Gson()
            val type = object : TypeToken<List<com.example.aifloatingball.model.BookmarkEntry>>() {}.type
            val oldBookmarks = try {
                gson.fromJson<List<com.example.aifloatingball.model.BookmarkEntry>>(oldBookmarksJson, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "解析旧书签数据失败", e)
                emptyList()
            }
            
            if (oldBookmarks.isEmpty()) {
                // 没有有效数据，标记为已迁移
                prefs.edit().putBoolean(migrationKey, true).apply()
                Log.d(TAG, "旧书签数据为空，无需迁移")
                return 0
            }
            
            // 获取现有书签（避免重复）
            val existingBookmarks = getAllBookmarks()
            val existingUrls = existingBookmarks.map { it.url.lowercase() }.toSet()
            
            // 转换为新格式并去重
            val newBookmarks = oldBookmarks
                .map { com.example.aifloatingball.model.Bookmark.fromBookmarkEntry(it) }
                .filter { !existingUrls.contains(it.url.lowercase()) } // 过滤重复URL
            
            if (newBookmarks.isEmpty()) {
                // 没有新数据需要添加，标记为已迁移
                prefs.edit().putBoolean(migrationKey, true).apply()
                Log.d(TAG, "所有旧书签已存在，无需迁移")
                return 0
            }
            
            // 合并到现有书签列表
            val allBookmarks = existingBookmarks.toMutableList()
            allBookmarks.addAll(newBookmarks)
            
            // 保存
            val success = saveBookmarks(allBookmarks)
            
            if (success) {
                // 标记迁移完成
                prefs.edit().putBoolean(migrationKey, true).apply()
                Log.d(TAG, "书签数据迁移成功: ${newBookmarks.size} 条")
                return newBookmarks.size
            } else {
                Log.e(TAG, "保存迁移后的书签数据失败")
                return 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "书签数据迁移失败", e)
            return 0
        }
    }
    
    /**
     * 批量添加书签（用于导入等场景）
     */
    fun addBookmarks(bookmarks: List<Bookmark>, skipDuplicates: Boolean = true): Int {
        val existingBookmarks = getAllBookmarks().toMutableList()
        val existingUrls = if (skipDuplicates) {
            existingBookmarks.map { it.url.lowercase() }.toSet()
        } else {
            emptySet()
        }
        
        var addedCount = 0
        bookmarks.forEach { bookmark ->
            if (!skipDuplicates || !existingUrls.contains(bookmark.url.lowercase())) {
                existingBookmarks.add(bookmark)
                addedCount++
            }
        }
        
        if (addedCount > 0) {
            saveBookmarks(existingBookmarks)
        }
        
        return addedCount
    }
    
    /**
     * 批量删除书签
     */
    fun deleteBookmarks(bookmarkIds: List<String>): Int {
        val bookmarks = getAllBookmarks().toMutableList()
        var deletedCount = 0
        
        bookmarkIds.forEach { id ->
            val removed = bookmarks.removeIf { it.id == id }
            if (removed) {
                deletedCount++
                // 删除相关的 favicon
                val faviconFile = File(faviconDir, "favicon_$id.png")
                if (faviconFile.exists()) {
                    faviconFile.delete()
                }
            }
        }
        
        if (deletedCount > 0) {
            saveBookmarks(bookmarks)
        }
        
        return deletedCount
    }
    
    /**
     * 批量移动书签到指定文件夹
     */
    fun moveBookmarksToFolder(bookmarkIds: List<String>, targetFolder: String): Int {
        val bookmarks = getAllBookmarks().toMutableList()
        var movedCount = 0
        
        bookmarkIds.forEach { id ->
            val index = bookmarks.indexOfFirst { it.id == id }
            if (index >= 0) {
                bookmarks[index] = bookmarks[index].copy(folder = targetFolder)
                movedCount++
            }
        }
        
        if (movedCount > 0) {
            saveBookmarks(bookmarks)
        }
        
        return movedCount
    }
    
    /**
     * 搜索书签（按标题、URL、描述、标签）
     */
    fun searchBookmarks(query: String): List<Bookmark> {
        if (query.isBlank()) {
            return getAllBookmarks()
        }
        
        val lowerQuery = query.lowercase()
        return getAllBookmarks().filter {
            it.title.contains(lowerQuery, ignoreCase = true) ||
            it.url.contains(lowerQuery, ignoreCase = true) ||
            it.description?.contains(lowerQuery, ignoreCase = true) == true ||
            it.tags.any { tag -> tag.contains(lowerQuery, ignoreCase = true) } ||
            it.folder.contains(lowerQuery, ignoreCase = true)
        }
    }
} 