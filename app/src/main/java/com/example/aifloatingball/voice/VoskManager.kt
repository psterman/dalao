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
    
    /**
     * 下载结果数据类
     * @param success 是否成功
     * @param errorType 错误类型（NETWORK_ERROR, HTTP_ERROR, VALIDATION_ERROR, IO_ERROR, UNKNOWN_ERROR）
     * @param errorMessage 错误消息
     */
    private data class DownloadResult(
        val success: Boolean, 
        val errorType: String? = null, 
        val errorMessage: String? = null
    )
    
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
        
        /**
         * 下载进度更新（可选实现）
         * @param progress 下载进度百分比 (0-100)
         * @param downloadedBytes 已下载字节数
         * @param totalBytes 总字节数
         * @param downloadSpeed 下载速度（字节/秒）
         */
        fun onDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long, downloadSpeed: Long) {
            // 默认实现，保持向后兼容
        }
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
        return validateModelFilesSimple()
    }
    
    /**
     * 验证结果数据类
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val missingFiles: List<String> = emptyList()
    )
    
    /**
     * 验证模型文件完整性（更严格的检查）
     * 检查关键文件是否存在
     * 
     * 注意：Vosk模型有两种结构：
     * 1. 小模型(vosk-model-small-cn-0.22)：使用am目录，包含final.mdl等文件
     * 2. 完整模型(vosk-model-cn-0.22)：使用graph目录，包含words.txt、HCLG.fst等文件
     * 
     * @return ValidationResult 包含验证结果和详细错误信息
     */
    private fun validateModelFiles(): ValidationResult {
        val modelDir = getModelDirectory()
        val missingFiles = mutableListOf<String>()
        
        if (!modelDir.exists() || !modelDir.isDirectory) {
            val errorMsg = "模型目录不存在: ${modelDir.absolutePath}"
            Log.e(TAG, errorMsg)
            return ValidationResult(false, errorMsg, listOf("模型目录"))
        }
        
        // 列出模型目录内容，用于调试
        val modelDirContents = modelDir.listFiles()?.map { it.name } ?: emptyList()
        Log.d(TAG, "模型目录内容: ${modelDirContents.joinToString(", ")}")
        
        // 检查conf目录及其关键文件
        val confDir = File(modelDir, "conf")
        if (!confDir.exists() || !confDir.isDirectory) {
            missingFiles.add("conf目录")
            val errorMsg = "conf目录不存在，模型目录内容: ${modelDirContents.joinToString(", ")}"
            Log.e(TAG, errorMsg)
            return ValidationResult(false, errorMsg, missingFiles)
        }
        
        // 检查conf/mfcc.conf文件（Vosk模型必需）
        val mfccConf = File(confDir, "mfcc.conf")
        if (!mfccConf.exists() || !mfccConf.isFile) {
            missingFiles.add("conf/mfcc.conf")
            val errorMsg = "conf/mfcc.conf文件不存在"
            Log.e(TAG, errorMsg)
            return ValidationResult(false, errorMsg, missingFiles)
        }
        
        // 检查文件大小是否合理（mfcc.conf应该不为空）
        if (mfccConf.length() == 0L) {
            val errorMsg = "conf/mfcc.conf文件为空（0字节）"
            Log.e(TAG, errorMsg)
            return ValidationResult(false, errorMsg, missingFiles)
        }
        
        // 检查am或graph目录
        val amDir = File(modelDir, "am")
        val graphDir = File(modelDir, "graph")
        
        val hasAm = amDir.exists() && amDir.isDirectory
        val hasGraph = graphDir.exists() && graphDir.isDirectory
        
        if (!hasAm && !hasGraph) {
            missingFiles.add("am目录")
            missingFiles.add("graph目录")
            val errorMsg = "am和graph目录都不存在，模型目录内容: ${modelDirContents.joinToString(", ")}"
            Log.e(TAG, errorMsg)
            return ValidationResult(false, errorMsg, missingFiles)
        }
        
        // 如果存在graph目录，检查关键文件
        // 注意：小模型可能没有graph目录，或者graph目录结构不同
        if (hasGraph) {
            val graphFiles = graphDir.listFiles()?.map { it.name } ?: emptyList()
            Log.d(TAG, "graph目录内容: ${graphFiles.joinToString(", ")}")
            
            // 检查graph/words.txt文件
            // 注意：小模型(vosk-model-small-cn-0.22)可能不包含words.txt，这是正常的
            val wordsTxt = File(graphDir, "words.txt")
            if (wordsTxt.exists()) {
                if (!wordsTxt.isFile) {
                    val errorMsg = "graph/words.txt不是有效文件"
                    Log.e(TAG, errorMsg)
                    return ValidationResult(false, errorMsg, missingFiles)
                }
                
                // 检查文件是否可读且大小合理
                if (wordsTxt.length() == 0L) {
                    val errorMsg = "graph/words.txt文件为空（0字节）"
                    Log.e(TAG, errorMsg)
                    return ValidationResult(false, errorMsg, missingFiles)
                }
                
                // 尝试读取文件的前几个字节，确保文件可读
                try {
                    wordsTxt.inputStream().use { it.read() }
                } catch (e: Exception) {
                    val errorMsg = "无法读取graph/words.txt文件: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    return ValidationResult(false, errorMsg, missingFiles)
                }
                
                Log.d(TAG, "graph/words.txt文件验证通过，大小: ${wordsTxt.length()} 字节")
            } else {
                // words.txt不存在，对于小模型这是允许的
                Log.d(TAG, "graph/words.txt文件不存在，这对于小模型是正常的")
            }
            
            // 检查graph/HCLG.fst文件（如果存在，应该是有效的）
            val hclgFst = File(graphDir, "HCLG.fst")
            if (hclgFst.exists() && hclgFst.length() == 0L) {
                val errorMsg = "graph/HCLG.fst文件为空（0字节）"
                Log.e(TAG, errorMsg)
                return ValidationResult(false, errorMsg, missingFiles)
            }
            
            // graph目录不为空即可
            if (graphFiles.isEmpty()) {
                val errorMsg = "graph目录为空"
                Log.e(TAG, errorMsg)
                return ValidationResult(false, errorMsg, missingFiles)
            }
        }
        
        // 如果存在am目录，检查关键文件
        if (hasAm) {
            // am目录通常包含final.mdl等文件
            val amFiles = amDir.listFiles()
            if (amFiles == null || amFiles.isEmpty()) {
                val errorMsg = "am目录为空"
                Log.e(TAG, errorMsg)
                return ValidationResult(false, errorMsg, missingFiles)
            }
            
            val amFileNames = amFiles.map { it.name }
            Log.d(TAG, "am目录内容: ${amFileNames.joinToString(", ")}")
            
            // 检查am/final.mdl文件（如果存在，应该是有效的）
            val finalMdl = File(amDir, "final.mdl")
            if (finalMdl.exists() && finalMdl.length() == 0L) {
                val errorMsg = "am/final.mdl文件为空（0字节）"
                Log.e(TAG, errorMsg)
                return ValidationResult(false, errorMsg, missingFiles)
            }
        }
        
        Log.d(TAG, "模型文件验证通过")
        return ValidationResult(true)
    }
    
    /**
     * 验证模型文件完整性（兼容旧接口）
     * @return Boolean 是否验证通过
     */
    private fun validateModelFilesSimple(): Boolean {
        return validateModelFiles().isValid
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
                val downloadResult = downloadModel()
                if (!downloadResult.success) {
                    // downloadModel() 已经通过 callback 发送了具体的错误信息，这里不需要再发送
                    // 但如果 downloadModel() 没有发送错误信息（理论上不应该发生），则发送默认错误信息
                    if (downloadResult.errorMessage != null) {
                        Log.d(TAG, "模型下载失败: ${downloadResult.errorType} - ${downloadResult.errorMessage}")
                    } else {
                        callback?.onModelStatus(false, "模型下载失败，请检查网络连接")
                    }
                    return@withContext false
                }
            }
            
            // 再次验证模型文件完整性
            val validationResult = validateModelFiles()
            if (!validationResult.isValid) {
                val errorMsg = if (validationResult.missingFiles.isNotEmpty()) {
                    "模型文件验证失败：缺少文件 ${validationResult.missingFiles.joinToString(", ")}，请重新下载"
                } else {
                    "模型文件验证失败：${validationResult.errorMessage ?: "文件可能不完整"}，请重新下载"
                }
                Log.e(TAG, "模型文件验证失败: ${validationResult.errorMessage}")
                callback?.onModelStatus(false, errorMsg)
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
            
            // 创建Model对象
            var modelCreated = false
            try {
                Log.d(TAG, "开始创建Model对象，路径: ${modelDir.absolutePath}")
                model = Model(modelDir.absolutePath)
                modelCreated = true
                Log.d(TAG, "Model对象创建成功")
            } catch (e: Exception) {
                Log.e(TAG, "创建Model对象失败", e)
                callback?.onModelStatus(false, "模型加载失败: ${e.message}")
                // 清理可能创建失败的部分
                model?.close()
                model = null
                return@withContext false
            }
            
            // 验证Model对象是否有效（防止原生层崩溃）
            if (!modelCreated || model == null) {
                Log.e(TAG, "Model对象创建失败或为null")
                callback?.onModelStatus(false, "模型对象无效")
                model?.close()
                model = null
                return@withContext false
            }
            
            // 再次验证模型文件完整性（防止在创建Model后文件被删除或损坏）
            val postValidationResult = validateModelFiles()
            if (!postValidationResult.isValid) {
                val errorMsg = if (postValidationResult.missingFiles.isNotEmpty()) {
                    "模型文件验证失败：缺少文件 ${postValidationResult.missingFiles.joinToString(", ")}，请重新下载"
                } else {
                    "模型文件验证失败：${postValidationResult.errorMessage ?: "文件可能已损坏"}，请重新下载"
                }
                Log.e(TAG, "Model创建后，模型文件验证失败: ${postValidationResult.errorMessage}")
                callback?.onModelStatus(false, errorMsg)
                model?.close()
                model = null
                // 删除可能损坏的模型目录
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                        Log.d(TAG, "已删除可能损坏的模型目录")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "删除模型目录失败", e)
                }
                return@withContext false
            }
            
            // 创建识别器（只有在Model成功创建且验证通过后才执行）
            // 注意：Recognizer的创建可能触发原生层崩溃，需要特别小心
            try {
                Log.d(TAG, "开始创建Recognizer对象，采样率: $SAMPLE_RATE Hz")
                // 确保Model对象不为null且有效
                val validModel = model
                if (validModel == null) {
                    throw IllegalStateException("Model对象为null，无法创建Recognizer")
                }
                
                recognizer = Recognizer(validModel, SAMPLE_RATE.toFloat())
                Log.d(TAG, "Recognizer对象创建成功")
            } catch (e: UnsatisfiedLinkError) {
                // 原生库链接错误
                Log.e(TAG, "创建Recognizer对象失败：原生库链接错误", e)
                callback?.onModelStatus(false, "识别器初始化失败：原生库错误，可能是模型文件不兼容")
                // 清理资源
                model?.close()
                model = null
                recognizer?.close()
                recognizer = null
                return@withContext false
            } catch (e: Exception) {
                // 其他异常（包括可能的原生崩溃导致的异常）
                Log.e(TAG, "创建Recognizer对象失败", e)
                Log.e(TAG, "异常类型: ${e.javaClass.name}, 异常消息: ${e.message}")
                callback?.onModelStatus(false, "识别器初始化失败: ${e.message ?: "未知错误"}，可能是模型文件不完整")
                // 清理资源
                model?.close()
                model = null
                recognizer?.close()
                recognizer = null
                
                // 如果Recognizer创建失败，可能是模型文件损坏，建议删除并重新下载
                Log.w(TAG, "Recognizer创建失败，建议删除模型并重新下载")
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                        Log.d(TAG, "已删除可能损坏的模型目录，请重新下载")
                    }
                } catch (deleteException: Exception) {
                    Log.e(TAG, "删除模型目录失败", deleteException)
                }
                
                return@withContext false
            } catch (e: Throwable) {
                // 捕获所有可能的错误，包括Error（虽然原生崩溃通常无法捕获）
                Log.e(TAG, "创建Recognizer对象时发生严重错误", e)
                Log.e(TAG, "错误类型: ${e.javaClass.name}, 错误消息: ${e.message}")
                callback?.onModelStatus(false, "识别器初始化失败：严重错误，请重新下载模型")
                // 清理资源
                model?.close()
                model = null
                recognizer?.close()
                recognizer = null
                
                // 删除可能损坏的模型
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                        Log.d(TAG, "已删除可能损坏的模型目录")
                    }
                } catch (deleteException: Exception) {
                    Log.e(TAG, "删除模型目录失败", deleteException)
                }
                
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
     * @return DownloadResult 包含下载结果和失败原因
     */
    private suspend fun downloadModel(): DownloadResult = withContext(Dispatchers.IO) {
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
            
            val response = try {
                client.newCall(request).execute()
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "下载模型失败: 无法解析主机地址", e)
                handler.post {
                    callback?.onModelStatus(false, "网络连接失败：无法访问服务器，请检查网络设置")
                }
                return@withContext DownloadResult(false, "NETWORK_ERROR", "无法解析主机地址")
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "下载模型失败: 连接超时", e)
                handler.post {
                    callback?.onModelStatus(false, "网络连接超时，请检查网络连接后重试")
                }
                return@withContext DownloadResult(false, "NETWORK_ERROR", "连接超时")
            } catch (e: java.io.IOException) {
                Log.e(TAG, "下载模型失败: 网络IO错误", e)
                handler.post {
                    callback?.onModelStatus(false, "网络连接错误：${e.message ?: "请检查网络连接"}")
                }
                return@withContext DownloadResult(false, "NETWORK_ERROR", e.message ?: "网络IO错误")
            }
            
            if (!response.isSuccessful) {
                Log.e(TAG, "下载模型失败: HTTP ${response.code}")
                handler.post {
                    callback?.onModelStatus(false, "下载失败：服务器返回错误 ${response.code}，请稍后重试")
                }
                return@withContext DownloadResult(false, "HTTP_ERROR", "HTTP ${response.code}")
            }
            
            val body = response.body ?: run {
                Log.e(TAG, "下载模型失败: 响应体为空")
                handler.post {
                    callback?.onModelStatus(false, "下载失败：服务器响应异常，请稍后重试")
                }
                return@withContext DownloadResult(false, "HTTP_ERROR", "响应体为空")
            }
            val contentLength = body.contentLength()
            
            val zipTotalBytes = if (contentLength > 0) contentLength else 0L
            Log.d(TAG, "开始下载模型ZIP文件，大小: ${if (zipTotalBytes > 0) zipTotalBytes / 1024 / 1024 else "未知"}MB")
            
            // 下载速度跟踪
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = 0L
            var zipDownloadedBytes = 0L // ZIP文件已下载字节数
            var extractedBytes = 0L // 解压后的文件总大小
            val speedUpdateInterval = 500L // 每500ms更新一次速度
            val progressUpdateInterval = 200L // 每200ms更新一次进度条
            
            // 先下载ZIP文件到临时文件
            val tempZipFile = File(context.cacheDir, "${currentModelName}_temp.zip")
            var isDownloadingZip = true
            
            // 下载ZIP文件
            body.byteStream().use { inputStream ->
                tempZipFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var len: Int
                    
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        outputStream.write(buffer, 0, len)
                        zipDownloadedBytes += len
                        
                        // 更新ZIP下载进度
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastUpdateTime
                        
                        if (timeDiff >= progressUpdateInterval || zipDownloadedBytes == zipTotalBytes) {
                            val bytesDiff = zipDownloadedBytes - lastDownloadedBytes
                            val downloadSpeed = if (timeDiff > 0) {
                                (bytesDiff * 1000) / timeDiff
                            } else {
                                0L
                            }
                            
                            // ZIP下载进度（0-90%）
                            val zipProgress = if (zipTotalBytes > 0) {
                                ((zipDownloadedBytes * 90) / zipTotalBytes).toInt().coerceIn(0, 90)
                            } else {
                                // 没有总大小，根据预期大小估算
                                val expectedZipSize = (getModelSize(getCurrentModelType()) * 0.7 * 1024 * 1024).toLong() // ZIP通常比解压后小30%
                                if (expectedZipSize > 0) {
                                    ((zipDownloadedBytes * 90) / expectedZipSize).toInt().coerceIn(0, 89)
                                } else {
                                    0
                                }
                            }
                            
                            val downloadedMB = zipDownloadedBytes / 1024.0 / 1024.0
                            val totalMB = if (zipTotalBytes > 0) {
                                zipTotalBytes / 1024.0 / 1024.0
                            } else {
                                (getModelSize(getCurrentModelType()) * 0.7) // ZIP压缩后大约70%大小
                            }
                            
                            Log.d(TAG, "ZIP下载进度: $zipProgress% (${String.format("%.2f", downloadedMB)}MB / ${String.format("%.2f", totalMB)}MB), 速度: ${downloadSpeed / 1024}KB/s")
                            
                            handler.post {
                                callback?.onDownloadProgress(zipProgress, zipDownloadedBytes, zipTotalBytes, downloadSpeed)
                                callback?.onModelStatus(false, "正在下载ZIP文件: $zipProgress% (${String.format("%.1f", downloadedMB)}MB / ${String.format("%.1f", totalMB)}MB)")
                            }
                            
                            if (timeDiff >= speedUpdateInterval) {
                                lastUpdateTime = currentTime
                                lastDownloadedBytes = zipDownloadedBytes
                            }
                        }
                    }
                }
            }
            
            isDownloadingZip = false
            Log.d(TAG, "ZIP文件下载完成，开始解压...")
            handler.post {
                callback?.onModelStatus(false, "ZIP文件下载完成，正在解压...")
            }
            
            // 验证ZIP文件完整性
            if (!tempZipFile.exists() || tempZipFile.length() == 0L) {
                val errorMsg = "ZIP文件下载失败或文件为空"
                Log.e(TAG, errorMsg)
                handler.post {
                    callback?.onModelStatus(false, errorMsg)
                }
                return@withContext DownloadResult(false, "IO_ERROR", errorMsg)
            }
            
            Log.d(TAG, "ZIP文件大小: ${tempZipFile.length() / 1024 / 1024}MB，开始解压...")
            
            // 解压ZIP文件
            var extractedFileCount = 0
            var failedFileCount = 0
            val criticalFiles = mutableSetOf<String>() // 记录关键文件
            val allZipEntries = mutableListOf<String>() // 记录所有ZIP条目，用于调试
            val extractedCriticalFiles = mutableSetOf<String>() // 记录已解压的关键文件
            
            try {
                // 首先使用ZipFile扫描所有条目，了解ZIP文件结构
                java.util.zip.ZipFile(tempZipFile).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        allZipEntries.add(entry.name)
                    }
                }
                
                // 分析ZIP文件结构，确定路径前缀
                var pathPrefix = ""
                if (allZipEntries.isNotEmpty()) {
                    val firstEntry = allZipEntries[0]
                    if (firstEntry.contains("/")) {
                        val parts = firstEntry.split("/")
                        if (parts.isNotEmpty() && parts[0] == currentModelName) {
                            pathPrefix = "$currentModelName/"
                            Log.d(TAG, "检测到ZIP文件使用模型名称前缀: $pathPrefix")
                        } else if (parts.isNotEmpty() && (parts[0] == "conf" || parts[0] == "graph" || parts[0] == "am")) {
                            pathPrefix = ""
                            Log.d(TAG, "检测到ZIP文件直接包含模型文件，无前缀")
                        }
                    }
                }
                
                Log.d(TAG, "ZIP文件包含 ${allZipEntries.size} 个条目")
                Log.d(TAG, "前10个条目: ${allZipEntries.take(10).joinToString(", ")}")
                
                // 查找关键文件在ZIP中的实际路径
                val wordsTxtInZip = allZipEntries.find { 
                    it.endsWith("graph/words.txt") || (it.endsWith("/words.txt") && it.contains("graph"))
                }
                val mfccConfInZip = allZipEntries.find { 
                    it.endsWith("conf/mfcc.conf") || (it.endsWith("/mfcc.conf") && it.contains("conf"))
                }
                
                Log.d(TAG, "ZIP中的graph/words.txt路径: ${wordsTxtInZip ?: "未找到"}")
                Log.d(TAG, "ZIP中的conf/mfcc.conf路径: ${mfccConfInZip ?: "未找到"}")
                
                // 现在解压文件
                tempZipFile.inputStream().use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        var entry = zipInputStream.nextEntry
                        var lastExtractedUpdateTime = System.currentTimeMillis()
                        var lastExtractedBytes = 0L
                        
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val entryName = entry.name
                                
                                // 处理路径：移除模型名称前缀（如果存在）
                                val relativePath = when {
                                    entryName.startsWith("$currentModelName/") -> {
                                        entryName.substring(currentModelName.length + 1)
                                    }
                                    entryName.startsWith(pathPrefix) && pathPrefix.isNotEmpty() -> {
                                        entryName.substring(pathPrefix.length)
                                    }
                                    else -> {
                                        entryName
                                    }
                                }
                                
                                // 记录关键文件
                                val isCritical = relativePath == "conf/mfcc.conf" || 
                                                 relativePath == "graph/words.txt" ||
                                                 relativePath.startsWith("conf/") ||
                                                 relativePath.startsWith("graph/") ||
                                                 relativePath.startsWith("am/")
                                
                                if (isCritical) {
                                    criticalFiles.add(relativePath)
                                    Log.d(TAG, "发现关键文件: ZIP路径=$entryName, 相对路径=$relativePath")
                                }
                                
                                val file = File(modelDir, relativePath)
                                val parentDir = file.parentFile
                                if (parentDir != null && !parentDir.exists()) {
                                    val created = parentDir.mkdirs()
                                    if (!created && !parentDir.exists()) {
                                        Log.e(TAG, "无法创建目录: ${parentDir.absolutePath}")
                                    }
                                }
                                
                                try {
                                    file.outputStream().use { outputStream ->
                                        val buffer = ByteArray(8192)
                                        var len: Int
                                        var fileBytes = 0L
                                        
                                        while (zipInputStream.read(buffer).also { len = it } != -1) {
                                            outputStream.write(buffer, 0, len)
                                            extractedBytes += len
                                            fileBytes += len
                                        }
                                        
                                        // 验证文件大小（如果ZIP条目有大小信息）
                                        if (entry.size > 0 && fileBytes != entry.size) {
                                            Log.w(TAG, "文件大小不匹配: $relativePath, 期望: ${entry.size}, 实际: $fileBytes")
                                            failedFileCount++
                                        } else {
                                            extractedFileCount++
                                        }
                                        
                                        // 记录关键文件已解压
                                        if (isCritical && fileBytes > 0) {
                                            extractedCriticalFiles.add(relativePath)
                                            Log.d(TAG, "关键文件已解压: $relativePath, 大小: $fileBytes 字节")
                                        }
                                        
                                        // 验证关键文件不为空
                                        if (fileBytes == 0L && (relativePath == "conf/mfcc.conf" || relativePath == "graph/words.txt")) {
                                            Log.e(TAG, "关键文件为空: $relativePath")
                                            failedFileCount++
                                        }
                                        
                                        // 验证文件是否真的存在
                                        if (isCritical && !file.exists()) {
                                            Log.e(TAG, "关键文件解压后不存在: ${file.absolutePath}")
                                            failedFileCount++
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "解压文件失败: $relativePath", e)
                                    failedFileCount++
                                }
                                
                                // 更新解压进度（90-100%）
                                val currentTime = System.currentTimeMillis()
                                val timeDiff = currentTime - lastExtractedUpdateTime
                                
                                if (timeDiff >= progressUpdateInterval) {
                                    val extractedMB = extractedBytes / 1024.0 / 1024.0
                                    val expectedSize = getModelSize(getCurrentModelType())
                                    
                                    // 解压进度从90%到100%
                                    val extractProgress = if (expectedSize > 0) {
                                        val progress = 90 + ((extractedMB / expectedSize) * 10).toInt().coerceIn(0, 10)
                                        progress.coerceIn(90, 100)
                                    } else {
                                        95 // 默认显示95%
                                    }
                                    
                                    Log.d(TAG, "解压进度: $extractProgress% (已解压: ${String.format("%.2f", extractedMB)}MB, 文件数: $extractedFileCount)")
                                    
                                    handler.post {
                                        // 使用解压后的实际大小作为totalBytes
                                        val totalExtractedBytes = (expectedSize * 1024 * 1024).toLong()
                                        callback?.onDownloadProgress(extractProgress, extractedBytes, totalExtractedBytes, 0L)
                                        callback?.onModelStatus(false, "正在解压模型: $extractProgress% (${String.format("%.1f", extractedMB)}MB / ${expectedSize}MB)")
                                    }
                                    
                                    lastExtractedUpdateTime = currentTime
                                }
                            }
                            
                            zipInputStream.closeEntry()
                            entry = zipInputStream.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解压ZIP文件失败", e)
                handler.post {
                    callback?.onModelStatus(false, "解压失败: ${e.message ?: "未知错误"}，请重新下载")
                }
                // 清理不完整的模型目录
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                    }
                } catch (cleanupException: Exception) {
                    Log.e(TAG, "清理模型目录失败", cleanupException)
                }
                return@withContext DownloadResult(false, "EXTRACTION_ERROR", e.message ?: "解压失败")
            }
            
            Log.d(TAG, "解压完成: 成功 $extractedFileCount 个文件，失败 $failedFileCount 个文件")
            Log.d(TAG, "关键文件统计: 期望 ${criticalFiles.size} 个，已解压 ${extractedCriticalFiles.size} 个")
            Log.d(TAG, "期望的关键文件: ${criticalFiles.joinToString(", ")}")
            Log.d(TAG, "已解压的关键文件: ${extractedCriticalFiles.joinToString(", ")}")
            
            // 检查关键文件是否都解压成功
            val missingCriticalFiles = criticalFiles.filter { it !in extractedCriticalFiles }
            if (missingCriticalFiles.isNotEmpty()) {
                Log.e(TAG, "关键文件未解压: ${missingCriticalFiles.joinToString(", ")}")
            }
            
            // 验证关键文件是否真的存在于文件系统中
            val missingFiles = mutableListOf<String>()
            for (criticalFile in criticalFiles) {
                val file = File(modelDir, criticalFile)
                if (!file.exists() || file.length() == 0L) {
                    missingFiles.add(criticalFile)
                    Log.e(TAG, "关键文件缺失或为空: $criticalFile, 路径: ${file.absolutePath}")
                }
            }
            
            if (missingFiles.isNotEmpty()) {
                Log.e(TAG, "解压后关键文件缺失: ${missingFiles.joinToString(", ")}")
                Log.e(TAG, "模型目录内容: ${modelDir.listFiles()?.joinToString { it.name }}")
                if (File(modelDir, "graph").exists()) {
                    Log.e(TAG, "graph目录内容: ${File(modelDir, "graph").listFiles()?.joinToString { it.name }}")
                }
            }
            
            if (failedFileCount > 0 || missingFiles.isNotEmpty()) {
                Log.w(TAG, "解压过程中有 $failedFileCount 个文件失败，${missingFiles.size} 个关键文件缺失，可能影响模型完整性")
            }
            
            // 删除临时ZIP文件
            try {
                if (tempZipFile.exists()) {
                    tempZipFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "删除临时ZIP文件失败", e)
            }
            
            // 解压完成，显示100%进度
            val expectedSize = getModelSize(getCurrentModelType())
            val totalExtractedBytes = (expectedSize * 1024 * 1024).toLong()
            val extractedMB = extractedBytes / 1024.0 / 1024.0
            
            Log.d(TAG, "模型解压完成，实际大小: ${String.format("%.2f", extractedMB)}MB，预期大小: ${expectedSize}MB")
            
            handler.post {
                callback?.onDownloadProgress(100, extractedBytes, totalExtractedBytes, 0L)
                callback?.onModelStatus(false, "解压完成，正在验证文件...")
            }
            
            Log.d(TAG, "模型下载完成，开始验证文件完整性...")
            
            // 下载完成后立即验证模型文件完整性
            val validationResult = validateModelFiles()
            if (!validationResult.isValid) {
                val errorMsg = if (validationResult.missingFiles.isNotEmpty()) {
                    "模型文件验证失败：缺少文件 ${validationResult.missingFiles.joinToString(", ")}，可能下载不完整，请重新下载"
                } else {
                    "模型文件验证失败：${validationResult.errorMessage ?: "文件可能不完整"}，请重新下载"
                }
                Log.e(TAG, "下载的模型文件验证失败: ${validationResult.errorMessage}")
                Log.e(TAG, "缺少的文件: ${validationResult.missingFiles.joinToString(", ")}")
                handler.post {
                    callback?.onModelStatus(false, errorMsg)
                }
                // 删除不完整的模型目录
                try {
                    if (modelDir.exists()) {
                        modelDir.deleteRecursively()
                        Log.d(TAG, "已删除不完整的模型目录")
                    }
                } catch (deleteException: Exception) {
                    Log.e(TAG, "删除不完整模型目录失败", deleteException)
                }
                return@withContext DownloadResult(false, "VALIDATION_ERROR", validationResult.errorMessage ?: "模型文件验证失败")
            }
            
            Log.d(TAG, "模型下载并验证完成")
            handler.post {
                callback?.onModelStatus(false, "验证完成，准备加载模型...")
            }
            return@withContext DownloadResult(true)
            
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "下载模型失败: 无法解析主机地址", e)
            handler.post {
                callback?.onModelStatus(false, "网络连接失败：无法访问服务器，请检查网络设置")
            }
            // 清理可能的部分文件
            try {
                val modelDir = getModelDirectory()
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                    Log.d(TAG, "已清理下载失败的模型目录")
                }
            } catch (cleanupException: Exception) {
                Log.e(TAG, "清理模型目录失败", cleanupException)
            }
            return@withContext DownloadResult(false, "NETWORK_ERROR", "无法解析主机地址")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "下载模型失败: 连接超时", e)
            handler.post {
                callback?.onModelStatus(false, "网络连接超时，请检查网络连接后重试")
            }
            // 清理可能的部分文件
            try {
                val modelDir = getModelDirectory()
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                    Log.d(TAG, "已清理下载失败的模型目录")
                }
            } catch (cleanupException: Exception) {
                Log.e(TAG, "清理模型目录失败", cleanupException)
            }
            return@withContext DownloadResult(false, "NETWORK_ERROR", "连接超时")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "下载模型失败: IO错误", e)
            handler.post {
                callback?.onModelStatus(false, "下载失败：${e.message ?: "IO错误"}，请检查网络连接后重试")
            }
            // 清理可能的部分文件
            try {
                val modelDir = getModelDirectory()
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                    Log.d(TAG, "已清理下载失败的模型目录")
                }
            } catch (cleanupException: Exception) {
                Log.e(TAG, "清理模型目录失败", cleanupException)
            }
            return@withContext DownloadResult(false, "IO_ERROR", e.message ?: "IO错误")
        } catch (e: Exception) {
            Log.e(TAG, "下载模型失败: 未知错误", e)
            handler.post {
                callback?.onModelStatus(false, "下载失败：${e.message ?: "未知错误"}，请稍后重试")
            }
            // 如果下载失败，尝试清理可能的部分文件
            try {
                val modelDir = getModelDirectory()
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                    Log.d(TAG, "已清理下载失败的模型目录")
                }
            } catch (cleanupException: Exception) {
                Log.e(TAG, "清理模型目录失败", cleanupException)
            }
            return@withContext DownloadResult(false, "UNKNOWN_ERROR", e.message ?: "未知错误")
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
    
    /**
     * 检测已下载的模型类型
     * @return 已下载的模型类型列表，如果没有下载任何模型则返回空列表
     */
    fun getDownloadedModelTypes(): List<ModelType> {
        val downloadedTypes = mutableListOf<ModelType>()
        val voskModelsDir = File(context.filesDir, "vosk_models")
        
        if (!voskModelsDir.exists()) {
            return downloadedTypes
        }
        
        // 检查小模型
        val smallModelDir = File(voskModelsDir, SMALL_MODEL_NAME)
        if (smallModelDir.exists() && isModelDownloadedForType(smallModelDir)) {
            downloadedTypes.add(ModelType.SMALL)
        }
        
        // 检查完整模型
        val fullModelDir = File(voskModelsDir, FULL_MODEL_NAME)
        if (fullModelDir.exists() && isModelDownloadedForType(fullModelDir)) {
            downloadedTypes.add(ModelType.FULL)
        }
        
        return downloadedTypes
    }
    
    /**
     * 检查指定模型目录是否包含有效的模型文件
     */
    private fun isModelDownloadedForType(modelDir: File): Boolean {
        if (!modelDir.exists() || !modelDir.isDirectory) {
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
        
        return hasConf && (hasAm || hasGraph)
    }
    
    /**
     * 获取指定类型模型的实际大小（MB）
     */
    fun getDownloadedModelSize(modelType: ModelType): Long {
        val modelName = when (modelType) {
            ModelType.SMALL -> SMALL_MODEL_NAME
            ModelType.FULL -> FULL_MODEL_NAME
        }
        
        val modelDir = File(context.filesDir, "vosk_models/$modelName")
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
    
    /**
     * 删除指定类型的模型
     * @return 是否删除成功
     */
    fun deleteModel(modelType: ModelType): Boolean {
        val modelName = when (modelType) {
            ModelType.SMALL -> SMALL_MODEL_NAME
            ModelType.FULL -> FULL_MODEL_NAME
        }
        
        val modelDir = File(context.filesDir, "vosk_models/$modelName")
        
        // 如果正在使用该模型，先释放
        if (currentModelName == modelName && model != null) {
            model?.close()
            model = null
            recognizer?.close()
            recognizer = null
        }
        
        // 删除模型目录
        if (modelDir.exists()) {
            val deleted = modelDir.deleteRecursively()
            Log.d(TAG, "删除模型 ${modelType.name}: $deleted")
            return deleted
        }
        
        return false
    }
}
