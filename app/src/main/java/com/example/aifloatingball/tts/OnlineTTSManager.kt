package com.example.aifloatingball.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 在线TTS管理器
 * 当系统TTS不可用时，使用在线TTS服务作为备选方案
 */
class OnlineTTSManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "OnlineTTSManager"
        
        @Volatile
        private var INSTANCE: OnlineTTSManager? = null
        
        fun getInstance(context: Context): OnlineTTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnlineTTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // TTS服务配置
    private val ttsServices = listOf(
        TTSService(
            name = "Edge TTS",
            url = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=6A5AA1D4EAFF4E9FB7E5CCF57C5EFF3",
            synthesizeUrl = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/synthesize",
            voices = listOf(
                TTSVoice("zh-CN-XiaoxiaoNeural", "晓晓", "zh-CN", "Female"),
                TTSVoice("zh-CN-YunxiNeural", "云希", "zh-CN", "Male"),
                TTSVoice("zh-CN-YunyangNeural", "云扬", "zh-CN", "Male"),
                TTSVoice("en-US-AriaNeural", "Aria", "en-US", "Female"),
                TTSVoice("en-US-GuyNeural", "Guy", "en-US", "Male")
            )
        ),
        TTSService(
            name = "Google TTS",
            url = "https://translate.google.com/translate_tts",
            synthesizeUrl = "https://translate.google.com/translate_tts",
            voices = listOf(
                TTSVoice("zh", "中文", "zh", "Female"),
                TTSVoice("en", "English", "en", "Female")
            )
        )
    )
    
    private var currentService: TTSService? = null
    private var currentVoice: TTSVoice? = null
    private var isEnabled = false
    
    // 状态监听器
    private var statusListener: OnlineTTSStatusListener? = null
    
    /**
     * TTS服务配置
     */
    data class TTSService(
        val name: String,
        val url: String,
        val synthesizeUrl: String,
        val voices: List<TTSVoice>
    )
    
    /**
     * TTS语音配置
     */
    data class TTSVoice(
        val id: String,
        val name: String,
        val language: String,
        val gender: String
    )
    
    /**
     * 在线TTS状态监听器
     */
    interface OnlineTTSStatusListener {
        fun onInitialized()
        fun onStart(utteranceId: String?)
        fun onComplete(utteranceId: String?)
        fun onError(error: String)
        fun onServiceChanged(serviceName: String)
    }
    
    init {
        initializeOnlineTTS()
    }
    
    /**
     * 初始化在线TTS
     */
    private fun initializeOnlineTTS() {
        try {
            Log.d(TAG, "初始化在线TTS服务...")
            
            // 选择第一个可用的服务
            currentService = ttsServices.firstOrNull()
            currentVoice = currentService?.voices?.firstOrNull()
            
            if (currentService != null) {
                Log.d(TAG, "选择TTS服务: ${currentService!!.name}")
                statusListener?.onServiceChanged(currentService!!.name)
                statusListener?.onInitialized()
            } else {
                Log.w(TAG, "没有可用的在线TTS服务")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化在线TTS失败", e)
            statusListener?.onError("初始化在线TTS失败: ${e.message}")
        }
    }
    
    /**
     * 设置状态监听器
     */
    fun setStatusListener(listener: OnlineTTSStatusListener?) {
        this.statusListener = listener
    }
    
    /**
     * 启用/禁用在线TTS
     */
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        Log.d(TAG, "在线TTS功能${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 检查在线TTS是否可用
     */
    fun isAvailable(): Boolean {
        return isEnabled && currentService != null
    }
    
    /**
     * 朗读文本
     */
    fun speak(text: String, utteranceId: String? = null) {
        if (!isAvailable()) {
            Log.w(TAG, "在线TTS不可用，跳过朗读")
            statusListener?.onError("在线TTS不可用")
            return
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "文本为空，跳过朗读")
            statusListener?.onError("文本内容为空")
            return
        }
        
        scope.launch {
            try {
                Log.d(TAG, "开始在线TTS朗读: ${text.take(50)}...")
                statusListener?.onStart(utteranceId)
                
                // 尝试多次合成，增加成功率
                var audioData: ByteArray? = null
                var lastError: String? = null
                
                for (attempt in 1..3) {
                    try {
                        Log.d(TAG, "语音合成尝试 $attempt/3")
                        audioData = synthesizeSpeech(text)
                        if (audioData != null) {
                            Log.d(TAG, "语音合成成功")
                            break
                        }
                    } catch (e: Exception) {
                        lastError = e.message
                        Log.w(TAG, "语音合成尝试 $attempt 失败: ${e.message}")
                        if (attempt < 3) {
                            delay(1000) // 等待1秒后重试
                        }
                    }
                }
                
                if (audioData != null) {
                    playAudio(audioData)
                    statusListener?.onComplete(utteranceId)
                } else {
                    val errorMsg = lastError ?: "语音合成失败"
                    Log.e(TAG, "所有语音合成尝试都失败: $errorMsg")
                    statusListener?.onError("语音合成失败: $errorMsg")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "在线TTS朗读失败", e)
                statusListener?.onError("在线TTS朗读失败: ${e.message}")
            }
        }
    }
    
    /**
     * 语音合成
     */
    private suspend fun synthesizeSpeech(text: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val service = currentService ?: return@withContext null
                val voice = currentVoice ?: return@withContext null
                
                var result: ByteArray? = null
                
                when (service.name) {
                    "Edge TTS" -> {
                        result = synthesizeWithEdgeTTS(text, service, voice)
                        // 如果Edge TTS失败，尝试Google TTS
                        if (result == null) {
                            Log.w(TAG, "Edge TTS失败，尝试Google TTS")
                            val googleService = ttsServices.find { it.name == "Google TTS" }
                            val googleVoice = googleService?.voices?.firstOrNull()
                            if (googleService != null && googleVoice != null) {
                                result = synthesizeWithGoogleTTS(text, googleService, googleVoice)
                            }
                        }
                    }
                    "Google TTS" -> {
                        result = synthesizeWithGoogleTTS(text, service, voice)
                        // 如果Google TTS失败，尝试Edge TTS
                        if (result == null) {
                            Log.w(TAG, "Google TTS失败，尝试Edge TTS")
                            val edgeService = ttsServices.find { it.name == "Edge TTS" }
                            val edgeVoice = edgeService?.voices?.firstOrNull()
                            if (edgeService != null && edgeVoice != null) {
                                result = synthesizeWithEdgeTTS(text, edgeService, edgeVoice)
                            }
                        }
                    }
                    else -> null
                }
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "语音合成失败", e)
                null
            }
        }
    }
    
    /**
     * 使用Edge TTS合成语音
     */
    private suspend fun synthesizeWithEdgeTTS(text: String, service: TTSService, voice: TTSVoice): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // 清理文本，移除特殊字符
                val cleanText = text.replace(Regex("[<>\"'&]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                if (cleanText.isBlank()) {
                    Log.w(TAG, "清理后的文本为空")
                    return@withContext null
                }
                
                val requestBody = """
                    {
                        "context": {
                            "synthesis": {
                                "audio": {
                                    "metadataOptions": {
                                        "sentenceBoundaryEnabled": false,
                                        "wordBoundaryEnabled": false
                                    },
                                    "outputFormat": "audio-24khz-48kbitrate-mono-mp3"
                                }
                            }
                        },
                        "input": "$cleanText",
                        "voice": {
                            "name": "${voice.id}",
                            "shortName": "${voice.id}",
                            "gender": "${voice.gender}",
                            "locale": "${voice.language}"
                        }
                    }
                """.trimIndent()
                
                val request = Request.Builder()
                    .url(service.synthesizeUrl)
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "audio/mpeg")
                    .addHeader("Accept-Encoding", "gzip, deflate, br")
                    .addHeader("Connection", "keep-alive")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val audioData = response.body?.bytes()
                    if (audioData != null && audioData.isNotEmpty()) {
                        Log.d(TAG, "Edge TTS合成成功，音频大小: ${audioData.size} bytes")
                        audioData
                    } else {
                        Log.w(TAG, "Edge TTS返回空音频数据")
                        null
                    }
                } else {
                    Log.e(TAG, "Edge TTS请求失败: ${response.code} - ${response.message}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Edge TTS合成失败", e)
                throw e // 重新抛出异常以便重试机制处理
            }
        }
    }
    
    /**
     * 使用Google TTS合成语音
     */
    private suspend fun synthesizeWithGoogleTTS(text: String, service: TTSService, voice: TTSVoice): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // 清理文本，URL编码
                val cleanText = text.replace(Regex("[<>\"'&]"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                
                if (cleanText.isBlank()) {
                    Log.w(TAG, "清理后的文本为空")
                    return@withContext null
                }
                
                val encodedText = java.net.URLEncoder.encode(cleanText, "UTF-8")
                val url = "${service.synthesizeUrl}?ie=UTF-8&tl=${voice.language}&client=tw-ob&q=$encodedText"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .addHeader("Accept", "audio/mpeg")
                    .addHeader("Accept-Encoding", "gzip, deflate, br")
                    .addHeader("Connection", "keep-alive")
                    .build()
                
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val audioData = response.body?.bytes()
                    if (audioData != null && audioData.isNotEmpty()) {
                        Log.d(TAG, "Google TTS合成成功，音频大小: ${audioData.size} bytes")
                        audioData
                    } else {
                        Log.w(TAG, "Google TTS返回空音频数据")
                        null
                    }
                } else {
                    Log.e(TAG, "Google TTS请求失败: ${response.code} - ${response.message}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google TTS合成失败", e)
                throw e // 重新抛出异常以便重试机制处理
            }
        }
    }
    
    /**
     * 播放音频
     */
    private fun playAudio(audioData: ByteArray) {
        try {
            val mediaPlayer = MediaPlayer()
            
            // 创建临时文件来播放音频
            val tempFile = File.createTempFile("tts_audio", ".mp3", context.cacheDir)
            tempFile.writeBytes(audioData)
            
            mediaPlayer.setDataSource(tempFile.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
            
            mediaPlayer.setOnCompletionListener {
                mediaPlayer.release()
                // 删除临时文件
                tempFile.delete()
            }
            
            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "音频播放错误: what=$what, extra=$extra")
                mediaPlayer.release()
                tempFile.delete()
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "播放音频失败", e)
        }
    }
    
    /**
     * 停止朗读
     */
    fun stop() {
        try {
            // 停止所有协程
            scope.coroutineContext.cancel()
            Log.d(TAG, "停止在线TTS朗读")
        } catch (e: Exception) {
            Log.e(TAG, "停止在线TTS朗读失败", e)
        }
    }
    
    /**
     * 获取可用的TTS服务
     */
    fun getAvailableServices(): List<TTSService> {
        return ttsServices
    }
    
    /**
     * 设置TTS服务
     */
    fun setService(serviceName: String) {
        currentService = ttsServices.find { it.name == serviceName }
        currentVoice = currentService?.voices?.firstOrNull()
        Log.d(TAG, "切换到TTS服务: $serviceName")
        statusListener?.onServiceChanged(serviceName)
    }
    
    /**
     * 设置语音
     */
    fun setVoice(voiceId: String) {
        currentVoice = currentService?.voices?.find { it.id == voiceId }
        Log.d(TAG, "设置语音: ${currentVoice?.name}")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            stop()
            httpClient.dispatcher.executorService.shutdown()
            Log.d(TAG, "在线TTS资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放在线TTS资源失败", e)
        }
    }
}
