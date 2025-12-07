package com.example.aifloatingball.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Vosk离线语音识别管理器
 * 支持中文语音转文本，无需网络连接
 * 
 * 使用说明：
 * 1. 首次使用时需要下载并解压Vosk中文模型
 * 2. 模型文件较大（约1.5GB），建议在WiFi环境下下载
 * 3. 模型下载后存储在应用私有目录，不会占用用户可见存储空间
 */
class VoskManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VoskManager"
        
        // Vosk中文模型配置
        // 最小模型：约42MB，适合移动设备，识别精度中等
        private const val SMALL_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
        private const val SMALL_MODEL_NAME = "vosk-model-small-cn-0.22"
        private const val SMALL_MODEL_SIZE_MB = 42L
        
        // 完整模型：约1.3GB，识别精度更高，适合服务器或高性能设备
        private const val FULL_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip"
        private const val FULL_MODEL_NAME = "vosk-model-cn-0.22"
        private const val FULL_MODEL_SIZE_MB = 1300L
        
        // 默认使用最小模型
        private var currentModelName = SMALL_MODEL_NAME
        private var currentModelUrl = SMALL_MODEL_URL
        
        // 音频录制参数
        private const val SAMPLE_RATE = 16000 // Vosk要求16kHz采样率
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2
        
        // 识别结果回调间隔（毫秒）
        private const val PARTIAL_RESULT_INTERVAL_MS = 500L
    }
    
    /**
     * 模型类型枚举
     */
    enum class ModelType {
        SMALL,  // 最小模型（42MB）
        FULL    // 完整模型（1.3GB）
    }
    
    interface VoskCallback {
        /**
         * 部分识别结果（实时流式输出）
         */
        fun onPartialResult(text: String)
        
        /**
         * 最终识别结果
         */
        fun onFinalResult(text: String)
        
        /**
         * 识别错误
         */
        fun onError(error: String)
        
        /**
         * 模型加载状态
         */
        fun onModelStatus(isReady: Boolean, message: String)
    }
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecognizing = false
    private var recognitionJob: Job? = null
    private var callback: VoskCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 设置回调
     */
    fun setCallback(callback: VoskCallback) {
        this.callback = callback
    }
    
    /**
     * 检查模型是否已下载
     * Vosk模型需要包含am目录和conf目录
     */
    fun isModelDownloaded(): Boolean {
        val modelDir = getModelDirectory()
        if (!modelDir.exists() || !modelDir.isDirectory) {
            Log.d(TAG, "模型目录不存在: ${modelDir.absolutePath}")
            return false
        }
        
        // 检查关键文件/目录是否存在
        val amDir = File(modelDir, "am")
        val confDir = File(modelDir, "conf")
        val graphDir = File(modelDir, "graph")
        
        // Vosk模型必须包含conf目录，以及am或graph目录之一
        val hasConf = confDir.exists() && confDir.isDirectory
        val hasAm = amDir.exists() && amDir.isDirectory
        val hasGraph = graphDir.exists() && graphDir.isDirectory
        
        val isValid = hasConf && (hasAm || hasGraph)
        
        if (!isValid) {
            Log.d(TAG, "模型文件不完整 - conf存在: $hasConf, am存在: $hasAm, graph存在: $hasGraph")
            Log.d(TAG, "模型目录内容: ${modelDir.listFiles()?.joinToString { it.name }}")
        }
        
        return isValid
    }
    
    /**
     * 验证模型文件完整性（更严格的检查）
     * 检查关键文件是否存在
     */
    private fun validateModelFiles(): Boolean {
        val modelDir = getModelDirectory()
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return false
        }
        
        // 检查conf目录及其关键文件
        val confDir = File(modelDir, "conf")
        if (!confDir.exists() || !confDir.isDirectory) {
            Log.e(TAG, "conf目录不存在")
            return false
        }
        
        // 检查conf/mfcc.conf文件（Vosk模型必需）
        val mfccConf = File(confDir, "mfcc.conf")
        if (!mfccConf.exists() || !mfccConf.isFile) {
            Log.e(TAG, "mfcc.conf文件不存在")
            return false
        }
        
        // 检查am或graph目录
        val amDir = File(modelDir, "am")
        val graphDir = File(modelDir, "graph")
        
        if (!amDir.exists() && !graphDir.exists()) {
            Log.e(TAG, "am和graph目录都不存在")
            return false
        }
        
        Log.d(TAG, "模型文件验证通过")
        return true
    }
    
    /**
     * 获取模型目录
     */
    private fun getModelDirectory(): File {
        return File(context.filesDir, "vosk_models/$currentModelName")
    }
    
    /**
     * 设置要使用的模型类型
     * @param modelType 模型类型（SMALL或FULL）
     */
    fun setModelType(modelType: ModelType) {
        when (modelType) {
            ModelType.SMALL -> {
                currentModelName = SMALL_MODEL_NAME
                currentModelUrl = SMALL_MODEL_URL
            }
            ModelType.FULL -> {
                currentModelName = FULL_MODEL_NAME
                currentModelUrl = FULL_MODEL_URL
            }
        }
        Log.d(TAG, "设置模型类型: $modelType, 模型名称: $currentModelName")
    }
    
    /**
     * 获取当前模型类型
     */
    fun getCurrentModelType(): ModelType {
        return when (currentModelName) {
            SMALL_MODEL_NAME -> ModelType.SMALL
            FULL_MODEL_NAME -> ModelType.FULL
            else -> ModelType.SMALL
        }
    }
    
    /**
     * 获取模型大小（MB）
     */
    fun getModelSize(modelType: ModelType): Long {
        return when (modelType) {
            ModelType.SMALL -> SMALL_MODEL_SIZE_MB
            ModelType.FULL -> FULL_MODEL_SIZE_MB
        }
    }
    
    /**
     * 初始化模型（异步）
     * 如果模型不存在，会自动下载最小模型
     * @param autoDownload 是否自动下载模型（默认true，自动下载最小模型）
     */
    suspend fun initializeModel(autoDownload: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDirectory()
            
            // 检查模型是否存在
            if (!isModelDownloaded()) {
                if (!autoDownload) {
                    Log.d(TAG, "模型不存在且未启用自动下载")
                    callback?.onModelStatus(false, "模型未下载，请先下载模型")
                    return@withContext false
                }
                
                Log.d(TAG, "模型不存在，开始下载最小模型（${getModelSize(getCurrentModelType())}MB）...")
                val modelSize = getModelSize(getCurrentModelType())
                callback?.onModelStatus(false, "正在下载Vosk中文模型（${modelSize}MB），请稍候...")
                
                // 下载模型
                if (!downloadModel()) {
                    callback?.onModelStatus(false, "模型下载失败，请检查网络连接")
                    return@withContext false
                }
            }
            
            // 再次验证模型文件完整性
            if (!validateModelFiles()) {
                Log.e(TAG, "模型文件验证失败，可能下载不完整")
                callback?.onModelStatus(false, "模型文件不完整，请重新下载")
                // 删除不完整的模型目录，以便重新下载
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                        Log.d(TAG, "已删除不完整的模型目录")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "删除模型目录失败", e)
                }
                return@withContext false
            }
            
            // 加载模型
            Log.d(TAG, "加载Vosk模型: ${modelDir.absolutePath}")
            callback?.onModelStatus(false, "正在加载模型...")
            
            // 先释放之前的模型（如果存在）
            model?.close()
            model = null
            recognizer?.close()
            recognizer = null
            
            try {
                model = Model(modelDir.absolutePath)
                Log.d(TAG, "Model对象创建成功")
            } catch (e: Exception) {
                Log.e(TAG, "创建Model对象失败", e)
                callback?.onModelStatus(false, "模型加载失败: ${e.message}")
                // 清理可能创建失败的部分
                model?.close()
                model = null
                return@withContext false
            }
            
            // 创建识别器（只有在Model成功创建后才执行）
            try {
                recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
                Log.d(TAG, "Recognizer对象创建成功")
            } catch (e: Exception) {
                Log.e(TAG, "创建Recognizer对象失败", e)
                callback?.onModelStatus(false, "识别器初始化失败: ${e.message}")
                // 清理资源
                model?.close()
                model = null
                recognizer?.close()
                recognizer = null
                return@withContext false
            }
            
            Log.d(TAG, "Vosk模型初始化成功")
            callback?.onModelStatus(true, "模型加载完成")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化Vosk模型失败", e)
            callback?.onModelStatus(false, "模型初始化失败: ${e.message}")
            return@withContext false
        }
    }
    
    /**
     * 下载并解压模型
     */
    private suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDirectory()
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            // 使用OkHttp下载模型
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS) // 5分钟超时
                .build()
            
            val request = okhttp3.Request.Builder()
                .url(currentModelUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "下载模型失败: ${response.code}")
                return@withContext false
            }
            
            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            
            Log.d(TAG, "开始下载模型，大小: ${contentLength / 1024 / 1024}MB")
            
            // 下载并解压
            body.byteStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    var downloadedBytes = 0L
                    
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // 移除zip文件中的顶层目录名（如果有）
                            val entryName = entry.name
                            val relativePath = if (entryName.startsWith("$currentModelName/")) {
                                entryName.substring(currentModelName.length + 1)
                            } else {
                                entryName
                            }
                            
                            val file = File(modelDir, relativePath)
                            file.parentFile?.mkdirs()
                            
                            file.outputStream().use { outputStream ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                
                                while (zipInputStream.read(buffer).also { len = it } != -1) {
                                    outputStream.write(buffer, 0, len)
                                    downloadedBytes += len
                                    
                                    // 更新进度（每10MB更新一次）
                                    if (downloadedBytes % (10 * 1024 * 1024) == 0L && contentLength > 0) {
                                        val progress = (downloadedBytes * 100 / contentLength).toInt()
                                        Log.d(TAG, "下载进度: $progress%")
                                        // 通知回调更新进度
                                        handler.post {
                                            callback?.onModelStatus(false, "正在下载模型: $progress%")
                                        }
                                    }
                                }
                            }
                        }
                        
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            }
            
            Log.d(TAG, "模型下载完成")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "下载模型失败", e)
            return@withContext false
        }
    }
    
    /**
     * 开始语音识别
     */
    fun startRecognition() {
        if (isRecognizing) {
            Log.w(TAG, "已经在识别中")
            return
        }
        
        if (model == null || recognizer == null) {
            Log.e(TAG, "模型未初始化")
            callback?.onError("模型未初始化，请先调用initializeModel()")
            return
        }
        
        try {
            // 计算缓冲区大小
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            // 创建AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败")
                callback?.onError("无法初始化音频录制")
                return
            }
            
            isRecognizing = true
            audioRecord?.startRecording()
            
            Log.d(TAG, "开始Vosk语音识别")
            
            // 在协程中处理音频数据
            recognitionJob = scope.launch {
                processAudioData(bufferSize)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动识别失败", e)
            isRecognizing = false
            callback?.onError("启动识别失败: ${e.message}")
        }
    }
    
    /**
     * 处理音频数据
     */
    private suspend fun processAudioData(bufferSize: Int) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        var lastPartialResultTime = System.currentTimeMillis()
        
        try {
            while (isRecognizing && audioRecord != null) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                
                if (readResult < 0) {
                    Log.e(TAG, "读取音频数据失败: $readResult")
                    break
                }
                
                // 将音频数据传递给识别器
                if (recognizer?.acceptWaveForm(buffer, readResult) == true) {
                    // 获取最终结果
                    val result = recognizer?.result
                    if (result != null && result.isNotEmpty()) {
                        handler.post {
                            callback?.onFinalResult(result)
                        }
                    }
                } else {
                    // 获取部分结果（实时流式输出）
                    val partialResult = recognizer?.partialResult
                    val currentTime = System.currentTimeMillis()
                    
                    // 限制部分结果回调频率，避免过于频繁
                    if (currentTime - lastPartialResultTime >= PARTIAL_RESULT_INTERVAL_MS) {
                        if (partialResult != null && partialResult.isNotEmpty()) {
                            handler.post {
                                callback?.onPartialResult(partialResult)
                            }
                            lastPartialResultTime = currentTime
                        }
                    }
                }
                
                // 避免CPU占用过高
                delay(10)
            }
            
            // 识别结束，获取最终结果
            if (isRecognizing) {
                val finalResult = recognizer?.finalResult
                if (finalResult != null) {
                    handler.post {
                        callback?.onFinalResult(finalResult)
                    }
                } else {
                    // 无最终结果，无需处理
                }
            } else {
                // 识别已停止，无需处理
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理音频数据失败", e)
            handler.post {
                callback?.onError("识别过程出错: ${e.message}")
            }
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopRecognition() {
        if (!isRecognizing) {
            return
        }
        
        isRecognizing = false
        
        try {
            recognitionJob?.cancel()
            recognitionJob = null
            
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
            
            // 获取最终结果
            val finalResult = recognizer?.finalResult
            if (finalResult != null) {
                handler.post {
                    callback?.onFinalResult(finalResult)
                }
            }
            
            Log.d(TAG, "Vosk识别已停止")
            
        } catch (e: Exception) {
            Log.e(TAG, "停止识别失败", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopRecognition()
        
        recognizer?.close()
        recognizer = null
        
        model?.close()
        model = null
        
        scope.cancel()
        
        Log.d(TAG, "VoskManager资源已释放")
    }
    
    /**
     * 检查是否正在识别
     */
    fun isRecognizing(): Boolean = isRecognizing
    
    /**
     * 检查模型是否已成功初始化（不仅仅是文件存在）
     * 只有当model和recognizer都不为null时，才认为模型已就绪
     */
    fun isModelInitialized(): Boolean {
        return model != null && recognizer != null
    }
    
    /**
     * 获取已下载模型的实际大小（MB）
     */
    fun getDownloadedModelSize(): Long {
        val modelDir = getModelDirectory()
        if (!modelDir.exists()) {
            return 0
        }
        
        var size = 0L
        modelDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        
        return size / 1024 / 1024
    }
}
