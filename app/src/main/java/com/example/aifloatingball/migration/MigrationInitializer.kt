package com.example.aifloatingball.migration

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 迁移初始化器
 * 负责在应用启动时检查并执行必要的数据迁移
 */
class MigrationInitializer private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "MigrationInitializer"
        
        @Volatile
        private var INSTANCE: MigrationInitializer? = null
        
        fun getInstance(context: Context): MigrationInitializer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MigrationInitializer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val migrationTool = GroupChatDataMigration.getInstance(context)
    
    /**
     * 初始化迁移检查
     * 应在应用启动时调用
     */
    fun initializeMigration(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        onMigrationComplete: ((MigrationResult) -> Unit)? = null,
        onMigrationProgress: ((String) -> Unit)? = null
    ) {
        Log.d(TAG, "开始初始化迁移检查")
        
        scope.launch {
            try {
                // 检查是否需要迁移
                val needsMigration = withContext(Dispatchers.IO) {
                    migrationTool.needsMigration()
                }
                
                if (needsMigration) {
                    Log.d(TAG, "检测到需要数据迁移，开始执行迁移")
                    onMigrationProgress?.invoke("正在检查旧数据...")
                    
                    // 执行迁移
                    val result = withContext(Dispatchers.IO) {
                        migrationTool.performMigration()
                    }
                    
                    // 处理迁移结果
                    handleMigrationResult(result, onMigrationComplete, onMigrationProgress)
                    
                } else {
                    Log.d(TAG, "无需数据迁移")
                    onMigrationProgress?.invoke("数据已是最新版本")
                    onMigrationComplete?.invoke(MigrationResult.SUCCESS_NO_DATA)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "迁移初始化过程中发生错误", e)
                onMigrationProgress?.invoke("迁移过程中发生错误: ${e.message}")
                onMigrationComplete?.invoke(MigrationResult.ERROR)
            }
        }
    }
    
    /**
     * 处理迁移结果
     */
    private fun handleMigrationResult(
        result: MigrationResult,
        onMigrationComplete: ((MigrationResult) -> Unit)?,
        onMigrationProgress: ((String) -> Unit)?
    ) {
        when (result) {
            MigrationResult.SUCCESS -> {
                Log.d(TAG, "数据迁移成功完成")
                onMigrationProgress?.invoke("数据迁移成功完成")
                onMigrationComplete?.invoke(result)
            }
            
            MigrationResult.SUCCESS_NO_DATA -> {
                Log.d(TAG, "迁移完成，没有数据需要迁移")
                onMigrationProgress?.invoke("没有数据需要迁移")
                onMigrationComplete?.invoke(result)
            }
            
            MigrationResult.ERROR -> {
                Log.e(TAG, "数据迁移失败")
                onMigrationProgress?.invoke("数据迁移失败")
                onMigrationComplete?.invoke(result)
            }
            
            MigrationResult.VERIFICATION_FAILED -> {
                Log.e(TAG, "数据迁移验证失败")
                onMigrationProgress?.invoke("数据迁移验证失败")
                onMigrationComplete?.invoke(result)
            }
        }
    }
    
    /**
     * 获取迁移状态
     */
    suspend fun getMigrationStatus(): MigrationStatus {
        return withContext(Dispatchers.IO) {
            migrationTool.getMigrationStatus()
        }
    }
    
    /**
     * 强制重新迁移（用于调试或修复）
     */
    fun forceMigration(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        onMigrationComplete: ((MigrationResult) -> Unit)? = null,
        onMigrationProgress: ((String) -> Unit)? = null
    ) {
        Log.d(TAG, "强制执行数据迁移")
        
        scope.launch {
            try {
                onMigrationProgress?.invoke("正在强制执行数据迁移...")
                
                val result = withContext(Dispatchers.IO) {
                    // 重置迁移状态
                    resetMigrationStatus()
                    
                    // 执行迁移
                    migrationTool.performMigration()
                }
                
                handleMigrationResult(result, onMigrationComplete, onMigrationProgress)
                
            } catch (e: Exception) {
                Log.e(TAG, "强制迁移过程中发生错误", e)
                onMigrationProgress?.invoke("强制迁移失败: ${e.message}")
                onMigrationComplete?.invoke(MigrationResult.ERROR)
            }
        }
    }
    
    /**
     * 重置迁移状态（用于强制重新迁移）
     */
    private fun resetMigrationStatus() {
        try {
            val migrationPrefs = context.getSharedPreferences("group_chat_migration", Context.MODE_PRIVATE)
            migrationPrefs.edit()
                .putBoolean("migration_completed", false)
                .putInt("migration_version", 0)
                .apply()
            
            Log.d(TAG, "迁移状态已重置")
            
        } catch (e: Exception) {
            Log.e(TAG, "重置迁移状态失败", e)
        }
    }
    
    /**
     * 清理旧数据（谨慎使用）
     */
    fun cleanupOldData(
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "开始清理旧数据")
        
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    migrationTool.cleanupOldData()
                }
                
                Log.d(TAG, "旧数据清理完成")
                onComplete?.invoke(true)
                
            } catch (e: Exception) {
                Log.e(TAG, "清理旧数据失败", e)
                onComplete?.invoke(false)
            }
        }
    }
    
    /**
     * 检查是否有备份数据
     */
    suspend fun hasBackupData(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val backupPrefs = context.getSharedPreferences("group_chat_prefs_backup", Context.MODE_PRIVATE)
                backupPrefs.all.isNotEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "检查备份数据失败", e)
                false
            }
        }
    }
    
    /**
     * 获取详细的迁移信息
     */
    suspend fun getMigrationInfo(): MigrationInfo {
        return withContext(Dispatchers.IO) {
            try {
                val status = migrationTool.getMigrationStatus()
                val hasBackup = hasBackupData()
                val needsMigration = migrationTool.needsMigration()
                
                MigrationInfo(
                    status = status,
                    hasBackupData = hasBackup,
                    needsMigration = needsMigration,
                    lastCheckTime = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "获取迁移信息失败", e)
                MigrationInfo(
                    status = MigrationStatus(false, 0, 0, false),
                    hasBackupData = false,
                    needsMigration = false,
                    lastCheckTime = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }
    }
}

/**
 * 迁移信息数据类
 */
data class MigrationInfo(
    val status: MigrationStatus,
    val hasBackupData: Boolean,
    val needsMigration: Boolean,
    val lastCheckTime: Long,
    val error: String? = null
)