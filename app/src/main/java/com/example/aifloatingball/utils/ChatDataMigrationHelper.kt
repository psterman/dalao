package com.example.aifloatingball.utils

import android.content.Context
import android.util.Log
import com.example.aifloatingball.data.ChatDataManager
import com.example.aifloatingball.manager.SimpleChatHistoryManager
import com.example.aifloatingball.ChatActivity

/**
 * 聊天数据迁移助手
 * 帮助将旧的聊天记录迁移到统一的ChatDataManager
 */
class ChatDataMigrationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "ChatDataMigrationHelper"
        private const val MIGRATION_PREFS = "chat_migration"
        private const val KEY_MIGRATION_COMPLETED = "migration_completed"
    }
    
    private val chatDataManager = ChatDataManager.getInstance(context)
    private val simpleChatHistoryManager = SimpleChatHistoryManager(context)
    private val migrationPrefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
    
    /**
     * 检查是否需要迁移数据
     */
    fun needsMigration(): Boolean {
        return !migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETED, false)
    }
    
    /**
     * 执行数据迁移
     */
    fun performMigration(contactIds: List<String> = emptyList()) {
        if (!needsMigration()) {
            Log.d(TAG, "数据迁移已完成，跳过")
            return
        }
        
        Log.d(TAG, "开始执行聊天数据迁移")
        var migratedCount = 0
        
        try {
            // 如果提供了联系人ID列表，迁移指定联系人的数据
            if (contactIds.isNotEmpty()) {
                contactIds.forEach { contactId ->
                    val migrated = migrateContactMessages(contactId)
                    if (migrated > 0) {
                        migratedCount += migrated
                        Log.d(TAG, "迁移联系人 $contactId 的 $migrated 条消息")
                    }
                }
            } else {
                // 否则尝试迁移所有可能的数据
                // 这里可以扩展为扫描所有已知的联系人ID
                Log.d(TAG, "未提供联系人ID列表，跳过自动迁移")
            }
            
            // 标记迁移完成
            migrationPrefs.edit()
                .putBoolean(KEY_MIGRATION_COMPLETED, true)
                .apply()
            
            Log.d(TAG, "数据迁移完成，共迁移 $migratedCount 条消息")
            
        } catch (e: Exception) {
            Log.e(TAG, "数据迁移失败", e)
        }
    }
    
    /**
     * 迁移单个联系人的消息
     */
    private fun migrateContactMessages(contactId: String): Int {
        try {
            // 检查统一存储中是否已有数据
            val existingMessages = chatDataManager.getMessages(contactId)
            if (existingMessages.isNotEmpty()) {
                Log.d(TAG, "联系人 $contactId 的数据已存在于统一存储中，跳过迁移")
                return 0
            }
            
            // 从旧存储加载消息
            val oldMessages = simpleChatHistoryManager.loadMessages(contactId)
            if (oldMessages.isEmpty()) {
                return 0
            }
            
            // 设置当前会话ID
            chatDataManager.setCurrentSessionId(contactId)
            
            // 迁移消息
            var migratedCount = 0
            oldMessages.forEach { oldMsg ->
                val role = if (oldMsg.isFromUser) "user" else "assistant"
                chatDataManager.addMessage(contactId, role, oldMsg.content)
                migratedCount++
            }
            
            return migratedCount
            
        } catch (e: Exception) {
            Log.e(TAG, "迁移联系人 $contactId 的消息失败", e)
            return 0
        }
    }
    
    /**
     * 验证数据一致性
     */
    fun verifyDataConsistency(contactId: String): Boolean {
        try {
            val oldMessages = simpleChatHistoryManager.loadMessages(contactId)
            val newMessages = chatDataManager.getMessages(contactId)
            
            if (oldMessages.size != newMessages.size) {
                Log.w(TAG, "联系人 $contactId 的消息数量不一致: 旧=${oldMessages.size}, 新=${newMessages.size}")
                return false
            }
            
            // 检查消息内容是否一致
            for (i in oldMessages.indices) {
                val oldMsg = oldMessages[i]
                val newMsg = newMessages[i]
                
                val expectedRole = if (oldMsg.isFromUser) "user" else "assistant"
                if (newMsg.role != expectedRole || newMsg.content != oldMsg.content) {
                    Log.w(TAG, "联系人 $contactId 的第 $i 条消息不一致")
                    return false
                }
            }
            
            Log.d(TAG, "联系人 $contactId 的数据一致性验证通过")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "验证联系人 $contactId 的数据一致性失败", e)
            return false
        }
    }
    
    /**
     * 重置迁移状态（用于测试）
     */
    fun resetMigrationStatus() {
        migrationPrefs.edit()
            .putBoolean(KEY_MIGRATION_COMPLETED, false)
            .apply()
        Log.d(TAG, "迁移状态已重置")
    }
    
    /**
     * 获取迁移统计信息
     */
    fun getMigrationStats(): MigrationStats {
        val allSessions = chatDataManager.getAllSessions()
        val totalMessages = allSessions.sumOf { it.messages.size }
        val totalSessions = allSessions.size
        
        return MigrationStats(
            totalSessions = totalSessions,
            totalMessages = totalMessages,
            migrationCompleted = !needsMigration()
        )
    }
    
    data class MigrationStats(
        val totalSessions: Int,
        val totalMessages: Int,
        val migrationCompleted: Boolean
    )
}
