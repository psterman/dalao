package com.example.aifloatingball.voice

import android.content.Context
import android.util.Log
import com.example.aifloatingball.manager.AITagManager
import com.example.aifloatingball.model.AITag

/**
 * 语音文本标签管理器
 * 负责管理语音转化的文本并存储到AI助手tab的语音文本标签中
 */
class VoiceTextTagManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceTextTagManager"
        private const val VOICE_TEXT_TAG_ID = "voice_text"  // 与AITag.kt中的默认标签ID一致
        private const val VOICE_TEXT_TAG_NAME = "语音文本"
        private const val PREF_VOICE_TEXTS = "voice_texts"
    }
    
    private val aiTagManager = AITagManager.getInstance(context)
    
    /**
     * 确保语音文本标签存在
     * 注意：voice_text是默认标签，应该总是存在
     */
    fun ensureVoiceTextTagExists(): AITag {
        val existingTag = aiTagManager.getTag(VOICE_TEXT_TAG_ID)
        return if (existingTag != null) {
            Log.d(TAG, "语音文本标签已存在: ${existingTag.name}")
            existingTag
        } else {
            // 如果标签不存在，说明默认标签还没有被加载，返回一个临时标签对象
            // 注意：这里不创建新标签，因为voice_text是默认标签，应该通过初始化加载
            Log.w(TAG, "语音文本标签不存在，使用默认标签配置")
            AITag(
                id = VOICE_TEXT_TAG_ID,
                name = VOICE_TEXT_TAG_NAME,
                description = "语音转化的文本内容",
                color = 0xFF4CAF50.toInt(),
                isDefault = true
            )
        }
    }
    
    /**
     * 保存文本到语音文本标签
     */
    fun saveTextToTag(text: String): Boolean {
        return try {
            // 确保标签存在
            ensureVoiceTextTagExists()
            
            if (text.isBlank()) {
                Log.w(TAG, "文本为空，不保存")
                return false
            }
            
            // 保存文本信息
            val textInfo = VoiceTextInfo(
                text = text,
                createdAt = System.currentTimeMillis()
            )
            
            // 保存文本信息
            saveTextInfo(textInfo)
            
            Log.d(TAG, "文本已保存到标签: ${text.take(50)}...")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存文本到标签失败", e)
            false
        }
    }
    
    /**
     * 获取所有语音文本
     */
    fun getAllTexts(): List<VoiceTextInfo> {
        return loadAllTextInfos()
    }
    
    /**
     * 删除语音文本
     */
    fun deleteText(textInfo: VoiceTextInfo): Boolean {
        return try {
            removeTextInfo(textInfo)
            Log.d(TAG, "文本已删除")
            true
        } catch (e: Exception) {
            Log.e(TAG, "删除文本失败", e)
            false
        }
    }
    
    /**
     * 语音文本信息数据类
     */
    data class VoiceTextInfo(
        val text: String,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * 保存文本信息（使用SharedPreferences）
     */
    private fun saveTextInfo(info: VoiceTextInfo) {
        val prefs = context.getSharedPreferences(PREF_VOICE_TEXTS, Context.MODE_PRIVATE)
        val texts = loadAllTextInfos().toMutableList()
        texts.add(info)
        
        val json = texts.joinToString("|") { textInfo ->
            "${textInfo.text.replace("|", "\\|").replace(";", "\\;")};${textInfo.createdAt}"
        }
        
        // 使用commit()确保立即保存
        val success = prefs.edit().putString("texts", json).commit()
        if (success) {
            Log.d(TAG, "文本信息已保存到SharedPreferences，当前总数: ${texts.size}")
        } else {
            Log.e(TAG, "保存文本信息到SharedPreferences失败")
        }
    }
    
    /**
     * 加载所有文本信息
     */
    private fun loadAllTextInfos(): List<VoiceTextInfo> {
        val prefs = context.getSharedPreferences(PREF_VOICE_TEXTS, Context.MODE_PRIVATE)
        val json = prefs.getString("texts", "") ?: ""
        
        if (json.isEmpty()) {
            return emptyList()
        }
        
        return json.split("|").mapNotNull { textStr ->
            val parts = textStr.split(";")
            if (parts.size >= 2) {
                VoiceTextInfo(
                    text = parts[0].replace("\\|", "|").replace("\\;", ";"),
                    createdAt = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        }
    }
    
    /**
     * 删除文本信息
     */
    private fun removeTextInfo(info: VoiceTextInfo) {
        val prefs = context.getSharedPreferences(PREF_VOICE_TEXTS, Context.MODE_PRIVATE)
        val texts = loadAllTextInfos().toMutableList()
        texts.removeAll { it.createdAt == info.createdAt && it.text == info.text }
        
        val json = texts.joinToString("|") { textInfo ->
            "${textInfo.text.replace("|", "\\|").replace(";", "\\;")};${textInfo.createdAt}"
        }
        
        prefs.edit().putString("texts", json).apply()
    }
}

