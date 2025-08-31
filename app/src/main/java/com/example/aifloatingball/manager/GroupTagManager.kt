package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.ChatContact
import com.example.aifloatingball.model.ContactCategory
import com.example.aifloatingball.model.ContactType
import com.example.aifloatingball.SettingsManager

/**
 * 群聊标签管理器
 * 负责管理群聊对象的标签分类、自动分类和搜索优化
 */
class GroupTagManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "GroupTagManager"
        private const val PREF_GROUP_TAG_MAPPING = "group_tag_mapping"
        
        @Volatile
        private var INSTANCE: GroupTagManager? = null
        
        fun getInstance(context: Context): GroupTagManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GroupTagManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val settingsManager = SettingsManager.getInstance(context)
    
    /**
     * 自动分类群聊对象
     */
    fun autoCategorizeGroup(groupContact: ChatContact): String {
        // 检查是否已有手动分类
        val manualTag = getGroupTag(groupContact.id)
        if (manualTag != null) {
            return manualTag
        }
        
        // 根据群聊名称和成员自动分类
        val autoTag = when {
            groupContact.name.contains("工作", ignoreCase = true) || 
            groupContact.name.contains("项目", ignoreCase = true) -> "work_group"
            groupContact.name.contains("学习", ignoreCase = true) || 
            groupContact.name.contains("教育", ignoreCase = true) -> "study_group"
            groupContact.name.contains("娱乐", ignoreCase = true) || 
            groupContact.name.contains("游戏", ignoreCase = true) -> "entertainment_group"
            groupContact.aiMembers?.isNotEmpty() == true -> "ai_group"
            else -> "general_group"
        }
        
        // 保存自动分类结果
        setGroupTag(groupContact.id, autoTag)
        
        Log.d(TAG, "群聊对象自动分类: ${groupContact.name} -> $autoTag")
        return autoTag
    }
    
    /**
     * 手动设置群聊对象的标签
     */
    fun setGroupTag(groupId: String, tagId: String) {
        val mapping = getGroupTagMapping()
        mapping[groupId] = tagId
        saveGroupTagMapping(mapping)
        
        Log.d(TAG, "设置群聊标签: $groupId -> $tagId")
    }
    
    /**
     * 获取群聊对象的标签
     */
    fun getGroupTag(groupId: String): String? {
        return getGroupTagMapping()[groupId]
    }
    
    /**
     * 根据标签获取群聊对象
     */
    fun getGroupsByTag(tagId: String, allContacts: List<ContactCategory>): List<ChatContact> {
        val groupContacts = allContacts.flatMap { category ->
            category.contacts.filter { it.type == ContactType.GROUP }
        }
        
        return groupContacts.filter { contact ->
            getGroupTag(contact.id) == tagId
        }
    }
    
    /**
     * 获取所有群聊（用于"全部"标签页）
     */
    fun getAllGroups(allContacts: List<ContactCategory>): List<ChatContact> {
        return allContacts.flatMap { category ->
            category.contacts.filter { it.type == ContactType.GROUP }
        }
    }
    
    /**
     * 根据当前标签页智能选择群聊对象
     */
    fun getGroupsForTab(tabName: String?, allContacts: List<ContactCategory>): List<ChatContact> {
        return when (tabName) {
            "工作群聊" -> getGroupsByTag("work_group", allContacts)
            "学习群聊" -> getGroupsByTag("study_group", allContacts)
            "娱乐群聊" -> getGroupsByTag("entertainment_group", allContacts)
            "AI群聊" -> getGroupsByTag("ai_group", allContacts)
            "全部" -> getAllGroups(allContacts)
            else -> getAllGroups(allContacts)
        }
    }
    
    /**
     * 清理群聊标签映射
     */
    fun clearGroupTagMapping(groupId: String) {
        val mapping = getGroupTagMapping()
        mapping.remove(groupId)
        saveGroupTagMapping(mapping)
        
        Log.d(TAG, "清理群聊标签映射: $groupId")
    }
    
    /**
     * 获取群聊标签映射
     */
    private fun getGroupTagMapping(): MutableMap<String, String> {
        val mappingJson = settingsManager.getString(PREF_GROUP_TAG_MAPPING, "{}")
        return try {
            val mapping = mutableMapOf<String, String>()
            // 简单的JSON解析，实际项目中应使用Gson或其他JSON库
            if (mappingJson != "{}") {
                // 这里简化处理，实际应该用JSON库
                Log.d(TAG, "加载群聊标签映射: $mappingJson")
            }
            mapping
        } catch (e: Exception) {
            Log.e(TAG, "解析群聊标签映射失败", e)
            mutableMapOf()
        }
    }
    
    /**
     * 保存群聊标签映射
     */
    private fun saveGroupTagMapping(mapping: Map<String, String>) {
        try {
            // 简单的JSON序列化，实际项目中应使用Gson或其他JSON库
            val mappingJson = mapping.entries.joinToString(",", "{", "}") { 
                "\"${it.key}\":\"${it.value}\""
            }
            settingsManager.putString(PREF_GROUP_TAG_MAPPING, mappingJson)
            Log.d(TAG, "保存群聊标签映射: $mappingJson")
        } catch (e: Exception) {
            Log.e(TAG, "保存群聊标签映射失败", e)
        }
    }
    
    /**
     * 自动分类所有群聊
     */
    fun autoCategorizeAllGroups(allContacts: List<ContactCategory>) {
        val groupContacts = getAllGroups(allContacts)
        groupContacts.forEach { group ->
            if (getGroupTag(group.id) == null) {
                autoCategorizeGroup(group)
            }
        }
        Log.d(TAG, "自动分类所有群聊完成，共处理 ${groupContacts.size} 个群聊")
    }
}