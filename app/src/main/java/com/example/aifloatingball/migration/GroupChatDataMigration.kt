package com.example.aifloatingball.migration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.manager.GroupChatManager
import com.example.aifloatingball.manager.UnifiedGroupChatManager
import com.example.aifloatingball.model.GroupChat
import com.example.aifloatingball.model.GroupChatMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 群聊数据迁移工具
 * 负责从旧的 GroupChatManager 迁移数据到新的 UnifiedGroupChatManager
 */
class GroupChatDataMigration private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "GroupChatDataMigration"
        private const val MIGRATION_PREFS = "group_chat_migration"
        private const val KEY_MIGRATION_COMPLETED = "migration_completed"
        private const val KEY_MIGRATION_VERSION = "migration_version"
        private const val CURRENT_MIGRATION_VERSION = 1
        
        // 旧的 GroupChatManager 存储键
        private const val OLD_PREFS_NAME = "group_chat_prefs"
        private const val OLD_KEY_GROUP_CHATS = "group_chats"
        private const val OLD_KEY_GROUP_MESSAGES = "group_messages_"
        
        @Volatile
        private var INSTANCE: GroupChatDataMigration? = null
        
        fun getInstance(context: Context): GroupChatDataMigration {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GroupChatDataMigration(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
    private val oldPrefs: SharedPreferences = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 检查是否需要迁移
     */
    fun needsMigration(): Boolean {
        val migrationCompleted = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETED, false)
        val migrationVersion = migrationPrefs.getInt(KEY_MIGRATION_VERSION, 0)
        
        // 如果迁移未完成或版本不匹配，则需要迁移
        val needsMigration = !migrationCompleted || migrationVersion < CURRENT_MIGRATION_VERSION
        
        Log.d(TAG, "检查迁移状态: 已完成=$migrationCompleted, 版本=$migrationVersion, 需要迁移=$needsMigration")
        return needsMigration
    }
    
    /**
     * 执行数据迁移
     */
    suspend fun performMigration(): MigrationResult {
        Log.d(TAG, "开始执行群聊数据迁移")
        
        try {
            // 1. 检查旧数据是否存在
            if (!hasOldData()) {
                Log.d(TAG, "没有发现旧数据，跳过迁移")
                markMigrationCompleted()
                return MigrationResult.SUCCESS_NO_DATA
            }
            
            // 2. 加载旧数据
            val oldGroupChats = loadOldGroupChats()
            val oldMessages = loadOldMessages(oldGroupChats.keys)
            
            Log.d(TAG, "加载旧数据: ${oldGroupChats.size} 个群聊, ${oldMessages.size} 个消息组")
            
            // 3. 获取新的统一管理器
            val unifiedManager = UnifiedGroupChatManager.getInstance(context)
            
            // 4. 迁移群聊数据
            var migratedGroupChats = 0
            var migratedMessages = 0
            
            oldGroupChats.forEach { (groupId, groupChat) ->
                try {
                    // 检查新管理器中是否已存在该群聊
                    if (unifiedManager.getGroupChat(groupId) == null) {
                        // 迁移群聊基本信息
                        migrateGroupChat(unifiedManager, groupChat)
                        migratedGroupChats++
                        
                        // 迁移群聊消息
                        oldMessages[groupId]?.let { messages ->
                            migrateGroupMessages(unifiedManager, groupId, messages)
                            migratedMessages += messages.size
                        }
                    } else {
                        Log.d(TAG, "群聊 $groupId 已存在于新管理器中，跳过迁移")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "迁移群聊 $groupId 时发生错误", e)
                }
            }
            
            // 5. 验证迁移结果
            val verificationResult = verifyMigration(oldGroupChats, oldMessages)
            
            if (verificationResult) {
                // 6. 标记迁移完成
                markMigrationCompleted()
                
                // 7. 可选：备份旧数据
                backupOldData()
                
                Log.d(TAG, "数据迁移成功完成: 迁移了 $migratedGroupChats 个群聊和 $migratedMessages 条消息")
                return MigrationResult.SUCCESS
            } else {
                Log.e(TAG, "数据迁移验证失败")
                return MigrationResult.VERIFICATION_FAILED
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "数据迁移过程中发生错误", e)
            return MigrationResult.ERROR
        }
    }
    
    /**
     * 检查是否存在旧数据
     */
    private fun hasOldData(): Boolean {
        val hasGroupChats = oldPrefs.contains(OLD_KEY_GROUP_CHATS)
        val hasMessages = oldPrefs.all.keys.any { it.startsWith(OLD_KEY_GROUP_MESSAGES) }
        
        return hasGroupChats || hasMessages
    }
    
    /**
     * 加载旧的群聊数据
     */
    private fun loadOldGroupChats(): Map<String, GroupChat> {
        try {
            val json = oldPrefs.getString(OLD_KEY_GROUP_CHATS, null)
            if (json != null) {
                val type = object : TypeToken<Map<String, GroupChat>>() {}.type
                return gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载旧群聊数据失败", e)
        }
        return emptyMap()
    }
    
    /**
     * 加载旧的消息数据
     */
    private fun loadOldMessages(groupIds: Set<String>): Map<String, List<GroupChatMessage>> {
        val messages = mutableMapOf<String, List<GroupChatMessage>>()
        
        groupIds.forEach { groupId ->
            try {
                val json = oldPrefs.getString(OLD_KEY_GROUP_MESSAGES + groupId, null)
                if (json != null) {
                    val type = object : TypeToken<List<GroupChatMessage>>() {}.type
                    val groupMessages: List<GroupChatMessage> = gson.fromJson(json, type)
                    messages[groupId] = groupMessages
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载群聊 $groupId 的旧消息数据失败", e)
            }
        }
        
        return messages
    }
    
    /**
     * 迁移单个群聊
     */
    private suspend fun migrateGroupChat(unifiedManager: UnifiedGroupChatManager, groupChat: GroupChat) {
        try {
            Log.d(TAG, "迁移群聊: ${groupChat.name} (ID: ${groupChat.id})")
            
            // 使用 UnifiedGroupChatManager 的导入方法
            unifiedManager.importGroupChat(groupChat)
            
            Log.d(TAG, "群聊迁移成功: ${groupChat.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "迁移群聊失败: ${groupChat.id}", e)
            throw e
        }
    }
    
    /**
     * 迁移群聊消息
     */
    private suspend fun migrateGroupMessages(
        unifiedManager: UnifiedGroupChatManager,
        groupId: String,
        messages: List<GroupChatMessage>
    ) {
        try {
            Log.d(TAG, "迁移群聊消息: $groupId, ${messages.size} 条消息")
            
            // 使用 UnifiedGroupChatManager 的导入消息方法
            unifiedManager.importGroupMessages(groupId, messages)
            
            Log.d(TAG, "群聊消息迁移成功: $groupId")
            
        } catch (e: Exception) {
            Log.e(TAG, "迁移群聊消息失败: $groupId", e)
            throw e
        }
    }
    
    /**
     * 验证迁移结果
     */
    private fun verifyMigration(
        oldGroupChats: Map<String, GroupChat>,
        oldMessages: Map<String, List<GroupChatMessage>>
    ): Boolean {
        try {
            val unifiedManager = UnifiedGroupChatManager.getInstance(context)
            val newGroupChats = unifiedManager.getAllGroupChats()
            
            // 验证群聊数量
            if (newGroupChats.size < oldGroupChats.size) {
                Log.e(TAG, "验证失败: 新群聊数量 ${newGroupChats.size} < 旧群聊数量 ${oldGroupChats.size}")
                return false
            }
            
            // 验证每个群聊是否存在
            oldGroupChats.forEach { (groupId, oldGroupChat) ->
                val newGroupChat = unifiedManager.getGroupChat(groupId)
                if (newGroupChat == null) {
                    Log.e(TAG, "验证失败: 群聊 $groupId 在新管理器中不存在")
                    return false
                }
                
                // 验证群聊基本信息
                if (newGroupChat.name != oldGroupChat.name) {
                    Log.e(TAG, "验证失败: 群聊 $groupId 名称不匹配")
                    return false
                }
            }
            
            // 验证消息数量
            oldMessages.forEach { (groupId, oldGroupMessages) ->
                val newMessages = unifiedManager.getGroupMessages(groupId)
                if (newMessages.size < oldGroupMessages.size) {
                    Log.e(TAG, "验证失败: 群聊 $groupId 消息数量不匹配")
                    return false
                }
            }
            
            Log.d(TAG, "迁移验证成功")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "迁移验证过程中发生错误", e)
            return false
        }
    }
    
    /**
     * 标记迁移完成
     */
    private fun markMigrationCompleted() {
        migrationPrefs.edit()
            .putBoolean(KEY_MIGRATION_COMPLETED, true)
            .putInt(KEY_MIGRATION_VERSION, CURRENT_MIGRATION_VERSION)
            .putLong("migration_timestamp", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "标记迁移完成")
    }
    
    /**
     * 备份旧数据
     */
    private fun backupOldData() {
        try {
            val backupPrefs = context.getSharedPreferences("${OLD_PREFS_NAME}_backup", Context.MODE_PRIVATE)
            val editor = backupPrefs.edit()
            
            // 复制所有旧数据到备份
            oldPrefs.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            
            editor.putLong("backup_timestamp", System.currentTimeMillis())
            editor.apply()
            
            Log.d(TAG, "旧数据备份完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "备份旧数据失败", e)
        }
    }
    
    /**
     * 清理旧数据（可选，谨慎使用）
     */
    fun cleanupOldData() {
        try {
            oldPrefs.edit().clear().apply()
            Log.d(TAG, "旧数据清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理旧数据失败", e)
        }
    }
    
    /**
     * 获取迁移状态
     */
    fun getMigrationStatus(): MigrationStatus {
        val completed = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETED, false)
        val version = migrationPrefs.getInt(KEY_MIGRATION_VERSION, 0)
        val timestamp = migrationPrefs.getLong("migration_timestamp", 0)
        
        return MigrationStatus(
            completed = completed,
            version = version,
            timestamp = timestamp,
            hasOldData = hasOldData()
        )
    }
}

/**
 * 迁移结果枚举
 */
enum class MigrationResult {
    SUCCESS,                // 迁移成功
    SUCCESS_NO_DATA,       // 成功，但没有数据需要迁移
    ERROR,                 // 迁移过程中发生错误
    VERIFICATION_FAILED    // 迁移验证失败
}

/**
 * 迁移状态数据类
 */
data class MigrationStatus(
    val completed: Boolean,
    val version: Int,
    val timestamp: Long,
    val hasOldData: Boolean
)