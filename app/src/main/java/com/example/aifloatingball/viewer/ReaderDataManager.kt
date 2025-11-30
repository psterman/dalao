package com.example.aifloatingball.viewer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.*

/**
 * 阅读器数据管理器
 * 负责管理书签、笔记、划线、阅读进度等数据
 */
class ReaderDataManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ReaderDataManager"
        private const val PREFS_NAME = "reader_data"
        private const val KEY_BOOKMARKS = "bookmarks"
        private const val KEY_HIGHLIGHTS = "highlights"
        private const val KEY_NOTES = "notes"
        private const val KEY_CHAPTERS = "chapters"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_SETTINGS = "settings"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 创建支持 Highlight 样式字段默认值的 Gson 实例
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Highlight::class.java, HighlightDeserializer())
        .create()
    
    /**
     * Highlight 反序列化器：处理旧数据中缺少 style 字段的情况
     */
    private class HighlightDeserializer : JsonDeserializer<Highlight> {
        @Throws(JsonParseException::class)
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Highlight {
            if (json == null || !json.isJsonObject) {
                throw JsonParseException("Invalid Highlight JSON")
            }
            
            val jsonObject = json.asJsonObject
            
            // 解析所有字段
            val id = jsonObject.get("id")?.asString ?: throw JsonParseException("Missing id field")
            val filePath = jsonObject.get("filePath")?.asString ?: throw JsonParseException("Missing filePath field")
            val pageIndex = jsonObject.get("pageIndex")?.asInt ?: throw JsonParseException("Missing pageIndex field")
            val startPosition = jsonObject.get("startPosition")?.asInt ?: throw JsonParseException("Missing startPosition field")
            val endPosition = jsonObject.get("endPosition")?.asInt ?: throw JsonParseException("Missing endPosition field")
            val text = jsonObject.get("text")?.asString ?: throw JsonParseException("Missing text field")
            val color = jsonObject.get("color")?.asString ?: "#FFEB3B"
            val timestamp = jsonObject.get("timestamp")?.asLong ?: System.currentTimeMillis()
            
            // 处理 style 字段：如果不存在或为 null，使用默认值
            val style = try {
                val styleElement = jsonObject.get("style")
                if (styleElement != null && !styleElement.isJsonNull) {
                    val styleName = styleElement.asString
                    try {
                        HighlightStyle.valueOf(styleName)
                    } catch (e: IllegalArgumentException) {
                        Log.w("HighlightDeserializer", "Invalid style value: $styleName, using default")
                        HighlightStyle.HIGHLIGHT
                    }
                } else {
                    HighlightStyle.HIGHLIGHT
                }
            } catch (e: Exception) {
                Log.w("HighlightDeserializer", "Error parsing style field, using default", e)
                HighlightStyle.HIGHLIGHT
            }
            
            return Highlight(
                id = id,
                filePath = filePath,
                pageIndex = pageIndex,
                startPosition = startPosition,
                endPosition = endPosition,
                text = text,
                color = color,
                style = style,
                timestamp = timestamp
            )
        }
    }
    
    // ==================== 书签管理 ====================
    
    /**
     * 获取文件的所有书签
     */
    fun getBookmarks(filePath: String): List<Bookmark> {
        val allBookmarks = getAllBookmarks()
        return allBookmarks.filter { it.filePath == filePath }
    }
    
    /**
     * 获取所有书签
     */
    fun getAllBookmarks(): List<Bookmark> {
        val json = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<Bookmark>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "解析书签失败", e)
            emptyList()
        }
    }
    
    /**
     * 添加书签
     */
    fun addBookmark(bookmark: Bookmark) {
        val bookmarks = getAllBookmarks().toMutableList()
        // 检查是否已存在相同位置的书签
        val existing = bookmarks.find { 
            it.filePath == bookmark.filePath && 
            it.pageIndex == bookmark.pageIndex && 
            it.position == bookmark.position 
        }
        if (existing == null) {
            bookmarks.add(bookmark)
            saveBookmarks(bookmarks)
            Log.d(TAG, "添加书签: ${bookmark.id}")
        } else {
            Log.d(TAG, "书签已存在，跳过添加")
        }
    }
    
    /**
     * 删除书签
     */
    fun deleteBookmark(bookmarkId: String) {
        val bookmarks = getAllBookmarks().toMutableList()
        bookmarks.removeAll { it.id == bookmarkId }
        saveBookmarks(bookmarks)
        Log.d(TAG, "删除书签: $bookmarkId")
    }
    
    /**
     * 检查位置是否有书签
     */
    fun hasBookmark(filePath: String, pageIndex: Int, position: Int): Boolean {
        val bookmarks = getBookmarks(filePath)
        return bookmarks.any { it.pageIndex == pageIndex && it.position == position }
    }
    
    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val json = gson.toJson(bookmarks)
        prefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }
    
    // ==================== 划线/高亮管理 ====================
    
    /**
     * 获取文件的所有划线
     */
    fun getHighlights(filePath: String): List<Highlight> {
        val allHighlights = getAllHighlights()
        return allHighlights.filter { it.filePath == filePath }
    }
    
    /**
     * 获取所有划线
     */
    fun getAllHighlights(): List<Highlight> {
        val json = prefs.getString(KEY_HIGHLIGHTS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<Highlight>>() {}.type
            val highlights = gson.fromJson<List<Highlight>>(json, type) ?: emptyList()
            
            // 自定义反序列化器已经处理了 style 字段的默认值
            // 但为了安全起见，我们仍然检查是否有 null 值（防止其他问题）
            val fixedHighlights = highlights.mapNotNull { highlight ->
                try {
                    // 确保 style 不为 null
                    if (highlight.style == null) {
                        Log.w(TAG, "发现 style 为 null 的划线数据: ${highlight.id}，已修复")
                        highlight.copy(style = HighlightStyle.HIGHLIGHT)
                    } else {
                        highlight
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理划线数据时出错: ${highlight.id}", e)
                    null // 跳过损坏的数据
                }
            }
            
            // 如果有修复的数据，保存回去
            if (fixedHighlights.size != highlights.size) {
                saveHighlights(fixedHighlights)
                Log.d(TAG, "已修复并移除了 ${highlights.size - fixedHighlights.size} 条损坏的划线数据")
            }
            
            fixedHighlights
        } catch (e: Exception) {
            Log.e(TAG, "解析划线失败", e)
            emptyList()
        }
    }
    
    /**
     * 添加划线
     */
    fun addHighlight(highlight: Highlight) {
        val highlights = getAllHighlights().toMutableList()
        // 检查是否已存在重叠的划线
        val existing = highlights.find { 
            it.filePath == highlight.filePath && 
            it.pageIndex == highlight.pageIndex &&
            ((it.startPosition <= highlight.startPosition && it.endPosition >= highlight.startPosition) ||
             (it.startPosition <= highlight.endPosition && it.endPosition >= highlight.endPosition) ||
             (highlight.startPosition <= it.startPosition && highlight.endPosition >= it.startPosition))
        }
        if (existing == null) {
            highlights.add(highlight)
            saveHighlights(highlights)
            Log.d(TAG, "添加划线: ${highlight.id}")
        } else {
            Log.d(TAG, "划线已存在，跳过添加")
        }
    }
    
    /**
     * 删除划线
     */
    fun deleteHighlight(highlightId: String) {
        val highlights = getAllHighlights().toMutableList()
        highlights.removeAll { it.id == highlightId }
        saveHighlights(highlights)
        Log.d(TAG, "删除划线: $highlightId")
    }
    
    private fun saveHighlights(highlights: List<Highlight>) {
        val json = gson.toJson(highlights)
        prefs.edit().putString(KEY_HIGHLIGHTS, json).apply()
    }
    
    // ==================== 笔记管理 ====================
    
    /**
     * 获取文件的所有笔记
     */
    fun getNotes(filePath: String): List<Note> {
        val allNotes = getAllNotes()
        return allNotes.filter { it.filePath == filePath }
    }
    
    /**
     * 获取所有笔记
     */
    fun getAllNotes(): List<Note> {
        val json = prefs.getString(KEY_NOTES, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<Note>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "解析笔记失败", e)
            emptyList()
        }
    }
    
    /**
     * 添加笔记
     */
    fun addNote(note: Note) {
        val notes = getAllNotes().toMutableList()
        notes.add(note)
        saveNotes(notes)
        Log.d(TAG, "添加笔记: ${note.id}")
    }
    
    /**
     * 更新笔记
     */
    fun updateNote(note: Note) {
        val notes = getAllNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) {
            notes[index] = note
            saveNotes(notes)
            Log.d(TAG, "更新笔记: ${note.id}")
        }
    }
    
    /**
     * 删除笔记
     */
    fun deleteNote(noteId: String) {
        val notes = getAllNotes().toMutableList()
        notes.removeAll { it.id == noteId }
        saveNotes(notes)
        Log.d(TAG, "删除笔记: $noteId")
    }
    
    private fun saveNotes(notes: List<Note>) {
        val json = gson.toJson(notes)
        prefs.edit().putString(KEY_NOTES, json).apply()
    }
    
    // ==================== 章节管理 ====================
    
    /**
     * 获取文件的章节列表
     */
    fun getChapters(filePath: String): List<Chapter> {
        val allChapters = getAllChapters()
        return allChapters.filter { it.id.startsWith(filePath) }
    }
    
    /**
     * 获取所有章节
     */
    fun getAllChapters(): List<Chapter> {
        val json = prefs.getString(KEY_CHAPTERS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<Chapter>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "解析章节失败", e)
            emptyList()
        }
    }
    
    /**
     * 保存章节列表
     */
    fun saveChapters(filePath: String, chapters: List<Chapter>) {
        val allChapters = getAllChapters().toMutableList()
        // 删除该文件的旧章节
        allChapters.removeAll { it.id.startsWith(filePath) }
        // 添加新章节
        allChapters.addAll(chapters)
        val json = gson.toJson(allChapters)
        prefs.edit().putString(KEY_CHAPTERS, json).apply()
        Log.d(TAG, "保存章节: ${chapters.size} 个")
    }
    
    // ==================== 阅读进度管理 ====================
    
    /**
     * 获取阅读进度
     */
    fun getProgress(filePath: String): ReadingProgress? {
        val json = prefs.getString("${KEY_PROGRESS}_$filePath", null)
        return try {
            if (json != null) {
                gson.fromJson(json, ReadingProgress::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析阅读进度失败", e)
            null
        }
    }
    
    /**
     * 保存阅读进度
     */
    fun saveProgress(progress: ReadingProgress) {
        val json = gson.toJson(progress)
        prefs.edit().putString("${KEY_PROGRESS}_${progress.filePath}", json).apply()
        Log.d(TAG, "保存阅读进度: ${progress.currentPage}/${progress.totalPages}")
    }
    
    // ==================== 设置管理 ====================
    
    /**
     * 获取阅读器设置
     */
    fun getSettings(): ReaderSettings {
        val json = prefs.getString(KEY_SETTINGS, null)
        return try {
            if (json != null) {
                gson.fromJson(json, ReaderSettings::class.java) ?: ReaderSettings()
            } else {
                ReaderSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析设置失败", e)
            ReaderSettings()
        }
    }
    
    /**
     * 保存阅读器设置
     */
    fun saveSettings(settings: ReaderSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString(KEY_SETTINGS, json).apply()
        Log.d(TAG, "保存阅读器设置")
    }
}

