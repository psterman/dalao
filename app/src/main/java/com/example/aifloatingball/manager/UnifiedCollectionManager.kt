package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.Serializable

/**
 * 统一收藏管理器
 * 负责所有收藏项的增删改查、筛选、排序、导入导出等功能
 */
class UnifiedCollectionManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "UnifiedCollectionManager"
        private const val PREFS_NAME = "unified_collections"
        private const val KEY_COLLECTIONS = "collections_list"
        private const val KEY_MIGRATION_COMPLETED = "migration_completed"
        
        @Volatile
        private var INSTANCE: UnifiedCollectionManager? = null
        
        fun getInstance(context: Context): UnifiedCollectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedCollectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 获取所有收藏项
     */
    fun getAllCollections(): List<UnifiedCollectionItem> {
        val json = prefs.getString(KEY_COLLECTIONS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<UnifiedCollectionItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "解析收藏项失败", e)
            emptyList()
        }
    }
    
    /**
     * 根据ID获取收藏项
     */
    fun getCollectionById(id: String): UnifiedCollectionItem? {
        return getAllCollections().find { it.id == id }
    }
    
    /**
     * 根据类型获取收藏项
     */
    fun getCollectionsByType(type: CollectionType): List<UnifiedCollectionItem> {
        return getAllCollections().filter { it.collectionType == type }
    }
    
    /**
     * 添加收藏项
     */
    fun addCollection(item: UnifiedCollectionItem): Boolean {
        val collections = getAllCollections().toMutableList()
        
        // 检查是否已存在相同ID的项
        val existingIndex = collections.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            // 更新现有项
            collections[existingIndex] = item.updateModifiedTime()
        } else {
            // 添加新项
            collections.add(item)
        }
        
        return saveCollections(collections)
    }
    
    /**
     * 更新收藏项
     */
    fun updateCollection(item: UnifiedCollectionItem): Boolean {
        val collections = getAllCollections().toMutableList()
        val index = collections.indexOfFirst { it.id == item.id }
        
        if (index >= 0) {
            collections[index] = item.updateModifiedTime()
            return saveCollections(collections)
        }
        
        return false
    }
    
    /**
     * 删除收藏项
     */
    fun deleteCollection(id: String): Boolean {
        val collections = getAllCollections().toMutableList()
        val removed = collections.removeIf { it.id == id }
        
        if (removed) {
            return saveCollections(collections)
        }
        
        return false
    }
    
    /**
     * 批量删除收藏项
     */
    fun deleteCollections(ids: List<String>): Int {
        val collections = getAllCollections().toMutableList()
        var deletedCount = 0
        
        ids.forEach { id ->
            if (collections.removeIf { it.id == id }) {
                deletedCount++
            }
        }
        
        if (deletedCount > 0) {
            saveCollections(collections)
        }
        
        return deletedCount
    }
    
    /**
     * 移动收藏项到其他类型
     */
    fun moveCollectionToType(id: String, newType: CollectionType): Boolean {
        val collections = getAllCollections().toMutableList()
        val index = collections.indexOfFirst { it.id == id }
        
        if (index >= 0) {
            collections[index] = collections[index].copy(
                collectionType = newType,
                modifiedTime = System.currentTimeMillis()
            )
            return saveCollections(collections)
        }
        
        return false
    }
    
    /**
     * 批量移动收藏项到其他类型
     */
    fun moveCollectionsToType(ids: List<String>, newType: CollectionType): Int {
        val collections = getAllCollections().toMutableList()
        var movedCount = 0
        
        ids.forEach { id ->
            val index = collections.indexOfFirst { it.id == id }
            if (index >= 0) {
                collections[index] = collections[index].copy(
                    collectionType = newType,
                    modifiedTime = System.currentTimeMillis()
                )
                movedCount++
            }
        }
        
        if (movedCount > 0) {
            saveCollections(collections)
        }
        
        return movedCount
    }
    
    /**
     * 搜索收藏项
     */
    fun searchCollections(
        query: String? = null,
        type: CollectionType? = null,
        tags: List<String>? = null,
        timeRange: Pair<Long, Long>? = null,
        priority: Priority? = null,
        completionStatus: CompletionStatus? = null,
        isEncrypted: Boolean? = null
    ): List<UnifiedCollectionItem> {
        var results = getAllCollections()
        
        // 文本搜索
        if (!query.isNullOrBlank()) {
            val lowerQuery = query.lowercase()
            results = results.filter {
                it.title.contains(lowerQuery, ignoreCase = true) ||
                it.content.contains(lowerQuery, ignoreCase = true) ||
                it.customTags.any { tag -> tag.contains(lowerQuery, ignoreCase = true) } ||
                it.sourceLocation.contains(lowerQuery, ignoreCase = true) ||
                it.sourceDetail?.contains(lowerQuery, ignoreCase = true) == true
            }
        }
        
        // 类型筛选
        if (type != null) {
            results = results.filter { it.collectionType == type }
        }
        
        // 标签筛选
        if (tags != null && tags.isNotEmpty()) {
            results = results.filter { item ->
                tags.any { tag -> item.customTags.contains(tag) }
            }
        }
        
        // 时间范围筛选
        if (timeRange != null) {
            val (startTime, endTime) = timeRange
            results = results.filter {
                it.collectedTime in startTime..endTime
            }
        }
        
        // 优先级筛选
        if (priority != null) {
            results = results.filter { it.priority == priority }
        }
        
        // 完成状态筛选
        if (completionStatus != null) {
            results = results.filter { it.completionStatus == completionStatus }
        }
        
        // 加密状态筛选
        if (isEncrypted != null) {
            results = results.filter { it.isEncrypted == isEncrypted }
        }
        
        return results
    }
    
    /**
     * 排序收藏项
     */
    fun sortCollections(
        collections: List<UnifiedCollectionItem>,
        dimension: SortDimension,
        direction: SortDirection
    ): List<UnifiedCollectionItem> {
        return when (dimension) {
            SortDimension.COLLECTED_TIME -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.collectedTime }
                } else {
                    collections.sortedByDescending { it.collectedTime }
                }
            }
            SortDimension.MODIFIED_TIME -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.modifiedTime }
                } else {
                    collections.sortedByDescending { it.modifiedTime }
                }
            }
            SortDimension.SOURCE_LOCATION -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.sourceLocation }
                } else {
                    collections.sortedByDescending { it.sourceLocation }
                }
            }
            SortDimension.COLLECTION_TYPE -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.collectionType.name }
                } else {
                    collections.sortedByDescending { it.collectionType.name }
                }
            }
            SortDimension.IS_ENCRYPTED -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.isEncrypted }
                } else {
                    collections.sortedByDescending { it.isEncrypted }
                }
            }
            SortDimension.PRIORITY -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.priority.value }
                } else {
                    collections.sortedByDescending { it.priority.value }
                }
            }
            SortDimension.LIKE_LEVEL -> {
                if (direction == SortDirection.ASC) {
                    collections.sortedBy { it.likeLevel }
                } else {
                    collections.sortedByDescending { it.likeLevel }
                }
            }
        }
    }
    
    /**
     * 获取所有自定义标签
     */
    fun getAllCustomTags(): List<String> {
        return getAllCollections()
            .flatMap { it.customTags }
            .distinct()
            .sorted()
    }
    
    /**
     * 导出收藏项为JSON格式
     */
    fun exportToJson(): String {
        val collections = getAllCollections()
        return gson.toJson(collections)
    }
    
    /**
     * 导出收藏项为CSV格式
     */
    fun exportToCsv(): String {
        val collections = getAllCollections()
        val sb = StringBuilder()
        
        // CSV头部
        sb.append("ID,标题,内容,收藏类型,收藏地点,收藏来源,收藏时间,修改时间,自定义标签,优先级,完成状态,喜欢程度,情感标签,加密状态,提醒时间\n")
        
        // CSV数据行
        collections.forEach { item ->
            val tags = item.customTags.joinToString(";")
            val reminderTime = item.reminderTime?.let { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: ""
            val collectedTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(item.collectedTime))
            val modifiedTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(item.modifiedTime))
            
            sb.append("\"${item.id}\",")
            sb.append("\"${escapeCsvField(item.title)}\",")
            sb.append("\"${escapeCsvField(item.content)}\",")
            sb.append("\"${item.collectionType.displayName}\",")
            sb.append("\"${escapeCsvField(item.sourceLocation)}\",")
            sb.append("\"${escapeCsvField(item.sourceDetail ?: "")}\",")
            sb.append("\"$collectedTime\",")
            sb.append("\"$modifiedTime\",")
            sb.append("\"$tags\",")
            sb.append("\"${item.priority.displayName}\",")
            sb.append("\"${item.completionStatus.displayName}\",")
            sb.append("${item.likeLevel},")
            sb.append("\"${item.emotionTag.displayName}\",")
            sb.append("${item.isEncrypted},")
            sb.append("\"$reminderTime\"\n")
        }
        
        return sb.toString()
    }
    
    /**
     * 从JSON导入收藏项
     */
    fun importFromJson(json: String, merge: Boolean = true): Int {
        return try {
            val type = object : TypeToken<List<UnifiedCollectionItem>>() {}.type
            val imported = gson.fromJson<List<UnifiedCollectionItem>>(json, type) ?: emptyList()
            
            if (merge) {
                // 合并模式：保留现有项，添加新项
                val existing = getAllCollections()
                val existingIds = existing.map { it.id }.toSet()
                val newItems = imported.filter { it.id !in existingIds }
                
                val allItems = existing + newItems
                saveCollections(allItems)
                newItems.size
            } else {
                // 覆盖模式：替换所有项
                saveCollections(imported)
                imported.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "导入JSON失败", e)
            0
        }
    }
    
    /**
     * 从CSV导入收藏项（简化版，仅支持基本字段）
     */
    fun importFromCsv(csv: String, merge: Boolean = true): Int {
        // CSV导入实现较复杂，这里提供基础框架
        // 实际使用时建议使用专门的CSV解析库
        return 0
    }
    
    /**
     * 保存收藏项列表
     */
    private fun saveCollections(collections: List<UnifiedCollectionItem>): Boolean {
        return try {
            val json = gson.toJson(collections)
            prefs.edit().putString(KEY_COLLECTIONS, json).apply()
            Log.d(TAG, "保存收藏项成功: ${collections.size} 条")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存收藏项失败", e)
            false
        }
    }
    
    /**
     * CSV字段转义
     */
    private fun escapeCsvField(field: String): String {
        return field.replace("\"", "\"\"")
            .replace("\n", " ")
            .replace("\r", " ")
    }
    
    /**
     * 检查是否已完成数据迁移
     */
    fun isMigrationCompleted(): Boolean {
        return prefs.getBoolean(KEY_MIGRATION_COMPLETED, false)
    }
    
    /**
     * 标记数据迁移完成
     */
    fun markMigrationCompleted() {
        prefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()
    }
}

