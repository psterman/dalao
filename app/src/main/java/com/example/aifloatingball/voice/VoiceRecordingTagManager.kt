package com.example.aifloatingball.voice

import android.content.Context
import android.util.Log
import com.example.aifloatingball.manager.AITagManager
import com.example.aifloatingball.model.AITag
import java.io.File

/**
 * 语音录音标签管理器
 * 负责管理录音文件并存储到AI助手tab的录音标签中
 */
class VoiceRecordingTagManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceRecordingTagManager"
        private const val RECORDING_TAG_ID = "voice_recordings"
        private const val RECORDING_TAG_NAME = "录音"
    }
    
    private val aiTagManager = AITagManager.getInstance(context)
    
    /**
     * 确保录音标签存在
     */
    fun ensureRecordingTagExists(): AITag {
        val existingTag = aiTagManager.getTag(RECORDING_TAG_ID)
        return if (existingTag != null) {
            existingTag
        } else {
            // 创建录音标签
            val newTag = aiTagManager.createTag(
                name = RECORDING_TAG_NAME,
                description = "语音录音文件",
                color = 0xFFFF5722.toInt() // 橙色
            )
            Log.d(TAG, "创建录音标签: ${newTag.name}")
            newTag
        }
    }
    
    /**
     * 保存录音文件到录音标签
     */
    fun saveRecordingToTag(recordingFile: File, transcription: String? = null): Boolean {
        return try {
            // 确保标签存在
            ensureRecordingTagExists()
            
            // 将录音文件信息保存到标签
            // 这里可以扩展为保存到数据库或SharedPreferences
            val recordingInfo = RecordingInfo(
                filePath = recordingFile.absolutePath,
                fileName = recordingFile.name,
                fileSize = recordingFile.length(),
                duration = 0, // 可以从MediaRecorder获取
                transcription = transcription,
                createdAt = System.currentTimeMillis()
            )
            
            // 保存录音信息（可以扩展为使用数据库）
            saveRecordingInfo(recordingInfo)
            
            Log.d(TAG, "录音文件已保存到标签: ${recordingFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存录音文件到标签失败", e)
            false
        }
    }
    
    /**
     * 获取所有录音文件
     */
    fun getAllRecordings(): List<RecordingInfo> {
        return loadAllRecordingInfos()
    }
    
    /**
     * 删除录音文件
     */
    fun deleteRecording(recordingInfo: RecordingInfo): Boolean {
        return try {
            val file = File(recordingInfo.filePath)
            if (file.exists()) {
                file.delete()
            }
            removeRecordingInfo(recordingInfo)
            Log.d(TAG, "录音文件已删除: ${recordingInfo.fileName}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "删除录音文件失败", e)
            false
        }
    }
    
    /**
     * 录音信息数据类
     */
    data class RecordingInfo(
        val filePath: String,
        val fileName: String,
        val fileSize: Long,
        val duration: Long,
        val transcription: String? = null,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 保存录音信息（简化实现，使用SharedPreferences）
     */
    private fun saveRecordingInfo(info: RecordingInfo) {
        val prefs = context.getSharedPreferences("voice_recordings", Context.MODE_PRIVATE)
        val recordings = loadAllRecordingInfos().toMutableList()
        recordings.add(info)
        
        val json = recordings.joinToString("|") { recording ->
            "${recording.filePath};${recording.fileName};${recording.fileSize};${recording.duration};${recording.transcription ?: ""};${recording.createdAt}"
        }
        
        prefs.edit().putString("recordings", json).apply()
    }
    
    /**
     * 加载所有录音信息
     */
    private fun loadAllRecordingInfos(): List<RecordingInfo> {
        val prefs = context.getSharedPreferences("voice_recordings", Context.MODE_PRIVATE)
        val json = prefs.getString("recordings", "") ?: ""
        
        if (json.isEmpty()) {
            return emptyList()
        }
        
        return json.split("|").mapNotNull { recordStr ->
            val parts = recordStr.split(";")
            if (parts.size >= 6) {
                RecordingInfo(
                    filePath = parts[0],
                    fileName = parts[1],
                    fileSize = parts[2].toLongOrNull() ?: 0,
                    duration = parts[3].toLongOrNull() ?: 0,
                    transcription = if (parts[4].isNotEmpty()) parts[4] else null,
                    createdAt = parts[5].toLongOrNull() ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        }
    }
    
    /**
     * 删除录音信息
     */
    private fun removeRecordingInfo(info: RecordingInfo) {
        val prefs = context.getSharedPreferences("voice_recordings", Context.MODE_PRIVATE)
        val recordings = loadAllRecordingInfos().toMutableList()
        recordings.removeAll { it.filePath == info.filePath }
        
        val json = recordings.joinToString("|") { recording ->
            "${recording.filePath};${recording.fileName};${recording.fileSize};${recording.duration};${recording.transcription ?: ""};${recording.createdAt}"
        }
        
        prefs.edit().putString("recordings", json).apply()
    }
}





