package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.AITag
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactCategory
import com.example.aifloatingball.SettingsManager

/**
 * AI标签管理器
 * 负责管理AI对象的标签分类、自动分类和搜索优化
 */
class AITagManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AITagManager"
        private const val PREF_AI_TAGS = "ai_tags"
        private const val PREF_AI_TAG_MAPPING = "ai_tag_mapping"
        
        @Volatile
        private var INSTANCE: AITagManager? = null
        
        fun getInstance(context: Context): AITagManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AITagManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val settingsManager = SettingsManager.getInstance(context)
    private val defaultTags = com.example.aifloatingball.model.AITagManager.getDefaultTags()
    
    /**
     * 获取所有标签
     * 合并保存的标签和默认标签，确保默认标签总是存在
     */
    fun getAllTags(): List<AITag> {
        val savedTags = loadSavedTags()
        val savedTagIds = savedTags.map { it.id }.toSet()
        
        // 合并保存的标签和默认标签（默认标签优先，如果已保存则使用保存的版本）
        val mergedTags = mutableListOf<AITag>()
        
        // 先添加默认标签（如果已保存，则使用保存的版本）
        defaultTags.forEach { defaultTag ->
            val savedTag = savedTags.find { it.id == defaultTag.id }
            mergedTags.add(savedTag ?: defaultTag)
        }
        
        // 再添加其他保存的标签（非默认标签）
        savedTags.forEach { savedTag ->
            if (savedTag.id !in defaultTags.map { it.id }) {
                mergedTags.add(savedTag)
            }
        }
        
        // 如果没有任何保存的标签，初始化并保存默认标签
        if (savedTags.isEmpty()) {
            saveTags(defaultTags)
        }
        
        return mergedTags
    }
    
    /**
     * 获取指定标签
     */
    fun getTag(tagId: String): AITag? {
        return getAllTags().find { it.id == tagId }
    }
    
    /**
     * 创建新标签
     */
    fun createTag(name: String, description: String = "", color: Int = 0): AITag {
        val newTag = AITag(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            description = description,
            color = color,
            isDefault = false
        )
        
        val currentTags = getAllTags().toMutableList()
        currentTags.add(newTag)
        saveTags(currentTags)
        
        Log.d(TAG, "创建新标签: $name")
        return newTag
    }
    
    /**
     * 更新标签
     */
    fun updateTag(tagId: String, name: String, description: String, color: Int): Boolean {
        val currentTags = getAllTags().toMutableList()
        val tagIndex = currentTags.indexOfFirst { it.id == tagId }
        
        if (tagIndex != -1) {
            val updatedTag = currentTags[tagIndex].copy(
                name = name,
                description = description,
                color = color
            )
            currentTags[tagIndex] = updatedTag
            saveTags(currentTags)
            
            Log.d(TAG, "更新标签: $name")
            return true
        }
        return false
    }
    
    /**
     * 删除标签
     */
    fun deleteTag(tagId: String): Boolean {
        val currentTags = getAllTags().toMutableList()
        val tagToDelete = currentTags.find { it.id == tagId }
        
        if (tagToDelete != null && !tagToDelete.isDefault) {
            currentTags.remove(tagToDelete)
            saveTags(currentTags)
            
            // 清理AI对象与该标签的关联
            clearTagMapping(tagId)
            
            Log.d(TAG, "删除标签: ${tagToDelete.name}")
            return true
        }
        return false
    }
    
    /**
     * 自动分类AI对象
     */
    fun autoCategorizeAI(aiContact: ChatContact): String {
        // 检查是否已有手动分类
        val manualTag = getAITag(aiContact.id)
        if (manualTag != null) {
            return manualTag
        }
        
        // 根据AI名称自动分类
        val autoTag = com.example.aifloatingball.model.AITagManager.autoCategorizeAI(aiContact.name)
        
        // 保存自动分类结果
        setAITag(aiContact.id, autoTag)
        
        Log.d(TAG, "AI对象自动分类: ${aiContact.name} -> $autoTag")
        return autoTag
    }
    
    /**
     * 手动设置AI对象的标签
     */
    fun setAITag(aiId: String, tagId: String) {
        val mapping = getAITagMapping()
        mapping[aiId] = tagId
        saveAITagMapping(mapping)
        
        Log.d(TAG, "设置AI标签: $aiId -> $tagId")
    }
    
    /**
     * 获取AI对象的标签
     */
    fun getAITag(aiId: String): String? {
        return getAITagMapping()[aiId]
    }
    
    /**
     * 获取指定标签下的所有AI对象
     */
    fun getAIsByTag(tagId: String, allContacts: List<ContactCategory>): List<ChatContact> {
        val aiContacts = allContacts.flatMap { category ->
            if (category.name == "AI助手") {
                category.contacts
            } else {
                emptyList()
            }
        }
        
        return aiContacts.filter { contact ->
            getAITag(contact.id) == tagId
        }
    }
    
    /**
     * 获取置顶的AI对象（自动分类到AI助手标签）
     */
    fun getPinnedAIs(allContacts: List<ContactCategory>): List<ChatContact> {
        val aiContacts = allContacts.flatMap { category ->
            if (category.name == "AI助手") {
                category.contacts
            } else {
                emptyList()
            }
        }
        
        // 获取置顶的AI对象
        val pinnedAIs = aiContacts.filter { it.isPinned }
        
        // 自动分类置顶的AI到AI助手标签
        pinnedAIs.forEach { ai ->
            if (getAITag(ai.id) == null) {
                setAITag(ai.id, "ai_assistant")
            }
        }
        
        return pinnedAIs
    }
    
    /**
     * 根据当前标签页智能选择AI对象进行搜索
     */
    fun getAIsForSearch(tabPosition: Int, tabName: String?, allContacts: List<ContactCategory>): List<ChatContact> {
        return when (tabPosition) {
            0 -> { // "全部"标签页 - 使用所有AI
                allContacts.flatMap { category ->
                    if (category.name == "AI助手") category.contacts else emptyList()
                }
            }
            1 -> { // "AI助手"标签页 - 只使用置顶的AI
                getPinnedAIs(allContacts)
            }
            2 -> { // "+"标签页 - 不进行搜索
                emptyList()
            }
            else -> { // 自定义标签页
                if (tabName != null && tabName != "+") {
                    // 查找对应的标签ID
                    val tagId = findTagIdByName(tabName)
                    if (tagId != null) {
                        getAIsByTag(tagId, allContacts)
                    } else {
                        // 如果找不到对应标签，返回空列表
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        }
    }
    
    /**
     * 根据标签名称查找标签ID
     */
    private fun findTagIdByName(tagName: String): String? {
        return getAllTags().find { it.name == tagName }?.id
    }
    
    /**
     * 获取AI标签映射
     */
    private fun getAITagMapping(): MutableMap<String, String> {
        val mappingStr = settingsManager.getString(PREF_AI_TAG_MAPPING, "") ?: ""
        return if (mappingStr.isNotEmpty()) {
            mappingStr.split(",").associate { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }.filter { (key, value) -> key.isNotEmpty() && value.isNotEmpty() }.toMutableMap()
        } else {
            mutableMapOf()
        }
    }
    
    /**
     * 保存AI标签映射
     */
    private fun saveAITagMapping(mapping: Map<String, String>) {
        val mappingStr = mapping.map { "${it.key}:${it.value}" }.joinToString(",")
        settingsManager.putString(PREF_AI_TAG_MAPPING, mappingStr)
    }
    
    /**
     * 清理标签映射
     */
    private fun clearTagMapping(tagId: String) {
        val mapping = getAITagMapping()
        mapping.entries.removeIf { it.value == tagId }
        saveAITagMapping(mapping)
    }
    
    /**
     * 加载保存的标签
     */
    private fun loadSavedTags(): List<AITag> {
        val tagsStr = settingsManager.getString(PREF_AI_TAGS, "") ?: ""
        return if (tagsStr.isNotEmpty()) {
            try {
                tagsStr.split("|").mapNotNull { tagStr ->
                    val parts = tagStr.split(";")
                    if (parts.size >= 6) {
                        AITag(
                            id = parts[0],
                            name = parts[1],
                            description = parts[2],
                            color = parts[3].toIntOrNull() ?: 0,
                            isDefault = parts[4].toBoolean(),
                            createdAt = parts[5].toLongOrNull() ?: System.currentTimeMillis()
                        )
                    } else null
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析保存的标签失败", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * 保存标签
     */
    private fun saveTags(tags: List<AITag>) {
        val tagsStr = tags.joinToString("|") { tag ->
            "${tag.id};${tag.name};${tag.description};${tag.color};${tag.isDefault};${tag.createdAt}"
        }
        settingsManager.putString(PREF_AI_TAGS, tagsStr)
    }
}
