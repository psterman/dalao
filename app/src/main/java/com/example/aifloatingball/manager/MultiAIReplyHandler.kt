package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 多AI并发回复处理器
 * 专门处理群聊中多个AI同时回复的逻辑
 */
class MultiAIReplyHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "MultiAIReplyHandler"
        private const val DEFAULT_TIMEOUT_MS = 30000L // 30秒超时
        private const val MAX_RETRY_COUNT = 2
    }
    
    private val aiApiManager = AIApiManager(context)
    
    // 回复状态跟踪
    private val activeReplies = ConcurrentHashMap<String, ReplySession>()
    
    /**
     * 回复会话数据
     */
    data class ReplySession(
        val groupId: String,
        val messageId: String,
        val aiMembers: List<GroupMember>,
        val userMessage: String,
        val conversationHistory: Map<String, List<Map<String, String>>>,
        val customPrompt: String?,
        val startTime: Long = System.currentTimeMillis(),
        val replyResults: ConcurrentHashMap<String, AIReplyResult> = ConcurrentHashMap(),
        val completedCount: AtomicInteger = AtomicInteger(0),
        val totalCount: Int = aiMembers.size
    )
    
    /**
     * AI回复结果
     */
    data class AIReplyResult(
        val aiId: String,
        val aiName: String,
        val success: Boolean,
        val content: String?,
        val errorMessage: String?,
        val responseTime: Long,
        val retryCount: Int = 0
    )
    
    /**
     * 回复进度回调
     */
    interface ReplyProgressCallback {
        fun onReplyStarted(aiId: String, aiName: String)
        fun onReplyCompleted(aiId: String, result: AIReplyResult)
        fun onAllRepliesCompleted(sessionId: String, results: Map<String, AIReplyResult>)
        fun onReplyTimeout(sessionId: String, timeoutAIs: List<String>)
        fun onReplyError(sessionId: String, error: Throwable)
    }
    
    /**
     * 启动多AI并发回复
     */
    suspend fun startConcurrentReplies(
        groupId: String,
        userMessage: String,
        aiMembers: List<GroupMember>,
        conversationHistory: Map<String, List<Map<String, String>>>,
        customPrompt: String? = null,
        simultaneousMode: Boolean = true,
        maxConcurrent: Int = 5,
        replyDelay: Long = 0L,
        callback: ReplyProgressCallback
    ): String {
        
        val sessionId = "session_${System.currentTimeMillis()}_${groupId}"
        val messageId = "msg_${System.currentTimeMillis()}"
        
        // 创建回复会话
        val session = ReplySession(
            groupId = groupId,
            messageId = messageId,
            aiMembers = aiMembers,
            userMessage = userMessage,
            conversationHistory = conversationHistory,
            customPrompt = customPrompt
        )
        
        activeReplies[sessionId] = session
        
        try {
            if (simultaneousMode) {
                // 同时回复模式
                handleSimultaneousReplies(sessionId, session, maxConcurrent, callback)
            } else {
                // 顺序回复模式
                handleSequentialReplies(sessionId, session, replyDelay, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理AI回复失败: $sessionId", e)
            callback.onReplyError(sessionId, e)
        } finally {
            activeReplies.remove(sessionId)
        }
        
        return sessionId
    }
    
    /**
     * 处理同时回复模式
     */
    private suspend fun handleSimultaneousReplies(
        sessionId: String,
        session: ReplySession,
        maxConcurrent: Int,
        callback: ReplyProgressCallback
    ) = withContext(Dispatchers.IO) {
        
        val semaphore = Semaphore(maxConcurrent)
        
        // 创建协程作业列表
        val jobs = session.aiMembers.map { aiMember ->
            async {
                semaphore.acquire()
                try {
                    processAIReply(sessionId, session, aiMember, callback)
                } finally {
                    semaphore.release()
                }
            }
        }
        
        // 启动超时监控
        val timeoutJob = async {
            delay(DEFAULT_TIMEOUT_MS)
            handleTimeout(sessionId, session, callback)
        }
        
        try {
            // 等待所有回复完成或超时
            val results = jobs.awaitAll()
            timeoutJob.cancel()
            
            // 通知所有回复完成
            callback.onAllRepliesCompleted(sessionId, session.replyResults.toMap())
            
        } catch (e: Exception) {
            timeoutJob.cancel()
            Log.e(TAG, "同时回复模式处理失败: $sessionId", e)
            callback.onReplyError(sessionId, e)
        }
    }
    
    /**
     * 处理顺序回复模式
     */
    private suspend fun handleSequentialReplies(
        sessionId: String,
        session: ReplySession,
        replyDelay: Long,
        callback: ReplyProgressCallback
    ) = withContext(Dispatchers.IO) {
        
        for ((index, aiMember) in session.aiMembers.withIndex()) {
            try {
                processAIReply(sessionId, session, aiMember, callback)
                
                // 添加延迟（除了最后一个）
                if (index < session.aiMembers.size - 1 && replyDelay > 0) {
                    delay(replyDelay)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "AI ${aiMember.name} 回复失败: $sessionId", e)
                
                // 记录失败结果
                val failureResult = AIReplyResult(
                    aiId = aiMember.id,
                    aiName = aiMember.name,
                    success = false,
                    content = null,
                    errorMessage = e.message,
                    responseTime = 0L
                )
                
                session.replyResults[aiMember.id] = failureResult
                callback.onReplyCompleted(aiMember.id, failureResult)
            }
        }
        
        // 通知所有回复完成
        callback.onAllRepliesCompleted(sessionId, session.replyResults.toMap())
    }
    
    /**
     * 处理单个AI回复
     */
    private suspend fun processAIReply(
        sessionId: String,
        session: ReplySession,
        aiMember: GroupMember,
        callback: ReplyProgressCallback
    ) {
        val aiServiceType = aiMember.aiServiceType ?: return
        val startTime = System.currentTimeMillis()
        
        // 通知开始回复
        callback.onReplyStarted(aiMember.id, aiMember.name)
        
        var retryCount = 0
        var lastError: Exception? = null
        
        while (retryCount <= MAX_RETRY_COUNT) {
            try {
                // 获取该AI的对话历史
                val history = session.conversationHistory[aiMember.id] ?: emptyList()
                
                // 构建完整消息
                val fullMessage = if (session.customPrompt != null) {
                    "${session.customPrompt}\n\n${session.userMessage}"
                } else {
                    session.userMessage
                }
                
                // 调用AI API
                val response = callAIAPIWithTimeout(aiServiceType, fullMessage, history)
                
                if (response != null) {
                    // 成功获取回复
                    val responseTime = System.currentTimeMillis() - startTime
                    val result = AIReplyResult(
                        aiId = aiMember.id,
                        aiName = aiMember.name,
                        success = true,
                        content = response,
                        errorMessage = null,
                        responseTime = responseTime,
                        retryCount = retryCount
                    )
                    
                    session.replyResults[aiMember.id] = result
                    session.completedCount.incrementAndGet()
                    
                    callback.onReplyCompleted(aiMember.id, result)
                    return
                    
                } else {
                    throw Exception("API返回空响应")
                }
                
            } catch (e: Exception) {
                lastError = e
                retryCount++
                
                if (retryCount <= MAX_RETRY_COUNT) {
                    Log.w(TAG, "AI ${aiMember.name} 回复失败，准备重试 ($retryCount/$MAX_RETRY_COUNT): ${e.message}")
                    delay(1000L * retryCount) // 递增延迟重试
                } else {
                    Log.e(TAG, "AI ${aiMember.name} 回复最终失败: ${e.message}")
                }
            }
        }
        
        // 所有重试都失败了
        val responseTime = System.currentTimeMillis() - startTime
        val failureResult = AIReplyResult(
            aiId = aiMember.id,
            aiName = aiMember.name,
            success = false,
            content = null,
            errorMessage = lastError?.message ?: "未知错误",
            responseTime = responseTime,
            retryCount = retryCount - 1
        )
        
        session.replyResults[aiMember.id] = failureResult
        session.completedCount.incrementAndGet()
        
        callback.onReplyCompleted(aiMember.id, failureResult)
    }
    
    /**
     * 带超时的AI API调用
     */
    private suspend fun callAIAPIWithTimeout(
        serviceType: AIServiceType,
        message: String,
        conversationHistory: List<Map<String, String>>,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): String? {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { continuation ->
                val callback = object : AIApiManager.StreamingCallback {
                    private val responseBuilder = StringBuilder()
                    
                    override fun onChunkReceived(chunk: String) {
                        responseBuilder.append(chunk)
                    }
                    
                    override fun onComplete(fullResponse: String) {
                        continuation.resume(fullResponse)
                    }
                    
                    override fun onError(error: String) {
                        continuation.resume(null)
                    }
                }
                
                aiApiManager.sendMessage(serviceType, message, conversationHistory, callback)
            }
        }
    }
    
    /**
     * 处理超时情况
     */
    private fun handleTimeout(
        sessionId: String,
        session: ReplySession,
        callback: ReplyProgressCallback
    ) {
        val timeoutAIs = mutableListOf<String>()
        
        // 找出超时的AI
        session.aiMembers.forEach { aiMember ->
            if (!session.replyResults.containsKey(aiMember.id)) {
                timeoutAIs.add(aiMember.id)
                
                // 添加超时结果
                val timeoutResult = AIReplyResult(
                    aiId = aiMember.id,
                    aiName = aiMember.name,
                    success = false,
                    content = null,
                    errorMessage = "回复超时",
                    responseTime = DEFAULT_TIMEOUT_MS
                )
                
                session.replyResults[aiMember.id] = timeoutResult
                callback.onReplyCompleted(aiMember.id, timeoutResult)
            }
        }
        
        if (timeoutAIs.isNotEmpty()) {
            callback.onReplyTimeout(sessionId, timeoutAIs)
        }
        
        // 通知所有回复完成（包括超时的）
        callback.onAllRepliesCompleted(sessionId, session.replyResults.toMap())
    }
    
    /**
     * 取消回复会话
     */
    fun cancelReplySession(sessionId: String): Boolean {
        return activeReplies.remove(sessionId) != null
    }
    
    /**
     * 获取活跃的回复会话
     */
    fun getActiveReplySessions(): List<String> {
        return activeReplies.keys.toList()
    }
    
    /**
     * 获取回复会话状态
     */
    fun getReplySessionStatus(sessionId: String): ReplySession? {
        return activeReplies[sessionId]
    }
    
    /**
     * 创建回复进度流
     */
    fun createReplyProgressFlow(sessionId: String): Flow<ReplyProgress> = flow {
        val session = activeReplies[sessionId] ?: return@flow
        
        while (session.completedCount.get() < session.totalCount) {
            val progress = ReplyProgress(
                sessionId = sessionId,
                totalCount = session.totalCount,
                completedCount = session.completedCount.get(),
                results = session.replyResults.toMap(),
                elapsedTime = System.currentTimeMillis() - session.startTime
            )
            
            emit(progress)
            delay(500) // 每500ms更新一次进度
        }
        
        // 发送最终进度
        val finalProgress = ReplyProgress(
            sessionId = sessionId,
            totalCount = session.totalCount,
            completedCount = session.completedCount.get(),
            results = session.replyResults.toMap(),
            elapsedTime = System.currentTimeMillis() - session.startTime,
            isCompleted = true
        )
        
        emit(finalProgress)
    }
    
    /**
     * 回复进度数据
     */
    data class ReplyProgress(
        val sessionId: String,
        val totalCount: Int,
        val completedCount: Int,
        val results: Map<String, AIReplyResult>,
        val elapsedTime: Long,
        val isCompleted: Boolean = false
    ) {
        val progressPercentage: Float
            get() = if (totalCount > 0) (completedCount.toFloat() / totalCount) * 100f else 0f
    }
}