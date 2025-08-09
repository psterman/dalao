package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.ChatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 简化版聊天历史记录管理器
 */
class SimpleChatHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "SimpleChatHistoryManager"
        private const val CHAT_HISTORY_DIR = "chat_history"
    }

    private val gson = Gson()

    /**
     * 保存聊天记录
     */
    fun saveMessages(contactId: String, messages: List<ChatActivity.ChatMessage>) {
        try {
            val historyDir = File(context.filesDir, CHAT_HISTORY_DIR)
            if (!historyDir.exists()) {
                historyDir.mkdirs()
            }

            val fileName = "${getSessionId(contactId)}_messages.json"
            val file = File(historyDir, fileName)
            val json = gson.toJson(messages)
            file.writeText(json)

            Log.d(TAG, "聊天记录已保存: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "保存聊天记录失败", e)
        }
    }

    /**
     * 加载聊天记录
     */
    fun loadMessages(contactId: String): List<ChatActivity.ChatMessage> {
        return try {
            val historyDir = File(context.filesDir, CHAT_HISTORY_DIR)
            val fileName = "${getSessionId(contactId)}_messages.json"
            val file = File(historyDir, fileName)

            if (file.exists()) {
                val json = file.readText()
                val type = object : TypeToken<List<ChatActivity.ChatMessage>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载聊天记录失败", e)
            emptyList()
        }
    }

    /**
     * 清除聊天记录
     */
    fun clearMessages(contactId: String) {
        try {
            val historyDir = File(context.filesDir, CHAT_HISTORY_DIR)
            val fileName = "${getSessionId(contactId)}_messages.json"
            val file = File(historyDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "聊天记录已清除: $fileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除聊天记录失败", e)
        }
    }

    /**
     * 搜索聊天记录
     */
    fun searchMessages(contactId: String, query: String): List<Pair<Int, ChatActivity.ChatMessage>> {
        return try {
            val messages = loadMessages(contactId)
            val results = mutableListOf<Pair<Int, ChatActivity.ChatMessage>>()

            messages.forEachIndexed { index, message ->
                if (message.content.contains(query, ignoreCase = true)) {
                    results.add(Pair(index, message))
                }
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "搜索聊天记录失败", e)
            emptyList()
        }
    }

    private fun getSessionId(contactId: String): String {
        return contactId.replace("[^a-zA-Z0-9]".toRegex(), "_")
    }
}
