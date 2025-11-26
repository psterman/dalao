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
    
    // ==================== 关联关系管理方法 ====================
    
    /**
     * 添加关联关系（双向）
     * 
     * @param sourceId 源收藏项ID
     * @param targetId 目标收藏项ID
     * @param relationType 关联类型
     * @param note 关联备注（可选）
     * @param weight 关联权重（0-1，默认1.0）
     * @param bidirectional 是否双向关联（默认true）
     * @return 是否添加成功
     */
    fun addRelation(
        sourceId: String,
        targetId: String,
        relationType: RelationType,
        note: String? = null,
        weight: Float = 1.0f,
        bidirectional: Boolean = true
    ): Boolean {
        val collections = getAllCollections().toMutableList()
        
        val sourceIndex = collections.indexOfFirst { it.id == sourceId }
        val targetIndex = collections.indexOfFirst { it.id == targetId }
        
        if (sourceIndex < 0 || targetIndex < 0) {
            Log.e(TAG, "关联失败：源或目标收藏项不存在")
            return false
        }
        
        // 防止自关联
        if (sourceId == targetId) {
            Log.e(TAG, "关联失败：不能关联自己")
            return false
        }
        
        // 检测循环关联（可选，如果不需要可以注释掉）
        if (detectCircularRelation(sourceId, targetId)) {
            Log.w(TAG, "警告：添加此关联可能形成循环")
            // 可以选择是否阻止循环关联
            // return false
        }
        
        // 更新源收藏项
        collections[sourceIndex] = collections[sourceIndex].addRelation(targetId, relationType, note, weight)
        
        // 双向关联：更新目标收藏项
        if (bidirectional) {
            val reverseType = getReverseRelationType(relationType)
            collections[targetIndex] = collections[targetIndex].addRelation(sourceId, reverseType, note, weight)
        }
        
        return saveCollections(collections)
    }
    
    /**
     * 移除关联关系（双向）
     * 
     * @param sourceId 源收藏项ID
     * @param targetId 目标收藏项ID
     * @param bidirectional 是否双向移除（默认true）
     * @return 是否移除成功
     */
    fun removeRelation(sourceId: String, targetId: String, bidirectional: Boolean = true): Boolean {
        val collections = getAllCollections().toMutableList()
        
        val sourceIndex = collections.indexOfFirst { it.id == sourceId }
        val targetIndex = collections.indexOfFirst { it.id == targetId }
        
        if (sourceIndex < 0 || targetIndex < 0) {
            return false
        }
        
        // 移除源收藏项的关联
        collections[sourceIndex] = collections[sourceIndex].removeRelation(targetId)
        
        // 双向移除
        if (bidirectional) {
            collections[targetIndex] = collections[targetIndex].removeRelation(sourceId)
        }
        
        return saveCollections(collections)
    }
    
    /**
     * 更新关联类型
     * 
     * @param sourceId 源收藏项ID
     * @param targetId 目标收藏项ID
     * @param newType 新的关联类型
     * @param bidirectional 是否双向更新（默认true）
     * @return 是否更新成功
     */
    fun updateRelationType(
        sourceId: String,
        targetId: String,
        newType: RelationType,
        bidirectional: Boolean = true
    ): Boolean {
        val collections = getAllCollections().toMutableList()
        
        val sourceIndex = collections.indexOfFirst { it.id == sourceId }
        val targetIndex = collections.indexOfFirst { it.id == targetId }
        
        if (sourceIndex < 0 || targetIndex < 0) {
            return false
        }
        
        // 更新源收藏项的关联类型
        collections[sourceIndex] = collections[sourceIndex].updateRelationType(targetId, newType)
        
        // 双向更新
        if (bidirectional) {
            val reverseType = getReverseRelationType(newType)
            collections[targetIndex] = collections[targetIndex].updateRelationType(sourceId, reverseType)
        }
        
        return saveCollections(collections)
    }
    
    /**
     * 更新关联备注
     * 
     * @param sourceId 源收藏项ID
     * @param targetId 目标收藏项ID
     * @param note 新的备注
     * @param bidirectional 是否双向更新（默认true）
     * @return 是否更新成功
     */
    fun updateRelationNote(
        sourceId: String,
        targetId: String,
        note: String?,
        bidirectional: Boolean = true
    ): Boolean {
        val collections = getAllCollections().toMutableList()
        
        val sourceIndex = collections.indexOfFirst { it.id == sourceId }
        val targetIndex = collections.indexOfFirst { it.id == targetId }
        
        if (sourceIndex < 0 || targetIndex < 0) {
            return false
        }
        
        // 更新源收藏项的关联备注
        collections[sourceIndex] = collections[sourceIndex].updateRelationNote(targetId, note)
        
        // 双向更新
        if (bidirectional) {
            collections[targetIndex] = collections[targetIndex].updateRelationNote(sourceId, note)
        }
        
        return saveCollections(collections)
    }
    
    /**
     * 获取关联的收藏项列表
     * 
     * @param id 收藏项ID
     * @return 关联的收藏项及其关联信息列表
     */
    fun getRelatedCollections(id: String): List<Pair<UnifiedCollectionItem, CollectionRelation>> {
        val item = getCollectionById(id) ?: return emptyList()
        return item.relations.mapNotNull { relation ->
            getCollectionById(relation.targetId)?.let { relatedItem ->
                Pair(relatedItem, relation)
            }
        }
    }
    
    /**
     * 获取所有关联的收藏项（递归，可设置深度）
     * 
     * @param id 起始收藏项ID
     * @param maxDepth 最大递归深度（默认3）
     * @return 所有关联的收藏项列表
     */
    fun getAllRelatedCollections(id: String, maxDepth: Int = 3): List<UnifiedCollectionItem> {
        val result = mutableSetOf<String>()
        val queue = mutableListOf<Pair<String, Int>>(Pair(id, 0))
        
        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeAt(0)
            
            if (depth >= maxDepth || currentId in result) {
                continue
            }
            
            result.add(currentId)
            val item = getCollectionById(currentId) ?: continue
            
            item.relations.forEach { relation ->
                if (relation.targetId !in result) {
                    queue.add(Pair(relation.targetId, depth + 1))
                }
            }
        }
        
        return result.filter { it != id }.mapNotNull { getCollectionById(it) }
    }
    
    /**
     * 获取指定类型的关联
     * 
     * @param id 收藏项ID
     * @param relationType 关联类型
     * @return 关联的收藏项列表
     */
    fun getRelatedCollectionsByType(id: String, relationType: RelationType): List<UnifiedCollectionItem> {
        val item = getCollectionById(id) ?: return emptyList()
        return item.relations
            .filter { it.relationType == relationType }
            .mapNotNull { getCollectionById(it.targetId) }
    }
    
    /**
     * 删除收藏项时清理所有关联
     * 重写deleteCollection方法以支持关联清理
     */
    fun deleteCollectionWithRelations(id: String): Boolean {
        val collections = getAllCollections().toMutableList()
        
        // 移除所有指向该收藏项的关联
        collections.forEachIndexed { index, item ->
            if (item.isRelatedTo(id)) {
                collections[index] = item.removeRelation(id)
            }
        }
        
        // 删除收藏项本身
        val removed = collections.removeIf { it.id == id }
        
        if (removed) {
            return saveCollections(collections)
        }
        
        return false
    }
    
    /**
     * 检测循环关联
     * 使用DFS检测是否存在从targetId到sourceId的路径
     * 
     * @param sourceId 源ID
     * @param targetId 目标ID
     * @return 是否存在循环
     */
    fun detectCircularRelation(sourceId: String, targetId: String): Boolean {
        val visited = mutableSetOf<String>()
        val stack = mutableListOf(targetId)
        
        while (stack.isNotEmpty()) {
            val currentId = stack.removeAt(0)
            
            if (currentId == sourceId) {
                return true  // 发现循环
            }
            
            if (currentId in visited) {
                continue
            }
            
            visited.add(currentId)
            val item = getCollectionById(currentId) ?: continue
            
            item.relations.forEach { relation ->
                if (relation.targetId !in visited) {
                    stack.add(relation.targetId)
                }
            }
        }
        
        return false
    }
    
    /**
     * 获取关联路径（从sourceId到targetId的最短路径）
     * 
     * @param sourceId 源ID
     * @param targetId 目标ID
     * @param maxDepth 最大深度（默认5）
     * @return 路径ID列表，如果不存在路径则返回null
     */
    fun getRelationPath(sourceId: String, targetId: String, maxDepth: Int = 5): List<String>? {
        val queue = mutableListOf<Pair<String, List<String>>>(Pair(sourceId, listOf(sourceId)))
        val visited = mutableSetOf<String>()
        
        while (queue.isNotEmpty()) {
            val (currentId, path) = queue.removeAt(0)
            
            if (currentId == targetId) {
                return path
            }
            
            if (currentId in visited || path.size > maxDepth) {
                continue
            }
            
            visited.add(currentId)
            val item = getCollectionById(currentId) ?: continue
            
            item.relations.forEach { relation ->
                if (relation.targetId !in visited) {
                    queue.add(Pair(relation.targetId, path + relation.targetId))
                }
            }
        }
        
        return null
    }
    
    /**
     * 获取反向关联类型
     * 
     * @param type 原始关联类型
     * @return 反向关联类型
     */
    private fun getReverseRelationType(type: RelationType): RelationType {
        return when (type) {
            RelationType.PARENT -> RelationType.CHILD
            RelationType.CHILD -> RelationType.PARENT
            RelationType.PREREQUISITE -> RelationType.FOLLOW_UP
            RelationType.FOLLOW_UP -> RelationType.PREREQUISITE
            RelationType.DEPENDENCY -> RelationType.DEPENDENCY  // 依赖关系通常也是双向的
            else -> type  // 其他类型保持原样
        }
    }
    
    /**
     * 基于标签查找相关收藏项
     * 
     * @param itemId 收藏项ID
     * @param minCommonTags 最少共同标签数（默认1）
     * @return 相关收藏项列表
     */
    fun findRelatedByTags(itemId: String, minCommonTags: Int = 1): List<UnifiedCollectionItem> {
        val item = getCollectionById(itemId) ?: return emptyList()
        val itemTags = item.customTags.toSet()
        
        if (itemTags.isEmpty()) {
            return emptyList()
        }
        
        return getAllCollections().filter { other ->
            other.id != itemId && other.customTags.intersect(itemTags).size >= minCommonTags
        }.sortedByDescending { other ->
            other.customTags.intersect(itemTags).size
        }
    }
    
    /**
     * 基于内容相似度查找相关收藏项
     * 
     * @param itemId 收藏项ID
     * @param similarityThreshold 相似度阈值（0-1，默认0.3）
     * @return 相关收藏项及其相似度列表
     */
    fun findRelatedByContent(itemId: String, similarityThreshold: Float = 0.3f): List<Pair<UnifiedCollectionItem, Float>> {
        val item = getCollectionById(itemId) ?: return emptyList()
        val itemWords = extractWords(item.title + " " + item.content)
        
        return getAllCollections().mapNotNull { other ->
            if (other.id == itemId) return@mapNotNull null
            
            val otherWords = extractWords(other.title + " " + other.content)
            val similarity = calculateJaccardSimilarity(itemWords, otherWords)
            
            if (similarity >= similarityThreshold) {
                Pair(other, similarity)
            } else {
                null
            }
        }.sortedByDescending { it.second }
    }
    
    /**
     * 提取关键词
     */
    private fun extractWords(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length > 1 }
            .toSet()
    }
    
    /**
     * 计算Jaccard相似度
     */
    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Float {
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }
}

