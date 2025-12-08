package com.example.aifloatingball.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException

/**
 * Vosk离线语音识别管理器
 * 提供完全离线的语音转文本功能
 */
class VoskVoiceRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "VoskVoiceRecognizer"
        private const val MODEL_NAME = "vosk-model-small-cn-0.22" // 中文小模型
    }
    
    interface VoskRecognitionCallback {
        fun onResult(text: String)
        fun onPartialResult(text: String)
        fun onError(error: String)
        fun onTimeout()
    }
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var callback: VoskRecognitionCallback? = null
    private var isInitialized = false
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 初始化Vosk模型
     */
    fun initialize(callback: VoskRecognitionCallback, onInitialized: (Boolean, String) -> Unit) {
        this.callback = callback
        
        scope.launch {
            try {
                Log.d(TAG, "开始初始化Vosk模型")
                
                // 初始化Vosk库
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                
                // 检查模型文件是否存在
                val modelPath = getModelPath()
                if (modelPath == null || !File(modelPath).exists()) {
                    val errorMsg = "Vosk模型文件不存在，请先下载模型到: ${getModelDirectory()}"
                    Log.e(TAG, errorMsg)
                    withContext(Dispatchers.Main) {
                        onInitialized(false, errorMsg)
                    }
                    return@launch
                }
                
                Log.d(TAG, "加载模型: $modelPath")
                model = Model(modelPath)
                
                // 创建识别器，采样率16kHz
                recognizer = Recognizer(model, 16000.0f)
                
                isInitialized = true
                Log.d(TAG, "Vosk模型初始化成功")
                
                withContext(Dispatchers.Main) {
                    onInitialized(true, "Vosk模型初始化成功")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Vosk模型初始化失败", e)
                isInitialized = false
                withContext(Dispatchers.Main) {
                    onInitialized(false, "初始化失败: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 开始语音识别
     */
    fun startListening() {
        if (!isInitialized) {
            callback?.onError("Vosk模型未初始化")
            return
        }
        
        if (isListening) {
            Log.w(TAG, "已经在监听中")
            return
        }
        
        try {
            Log.d(TAG, "开始Vosk语音识别")
            isListening = true
            
            // 使用StorageService来启动语音识别服务
            StorageService.unpack(context, MODEL_NAME, "model",
                { model: Model ->
                    this.model = model
                    val rec = Recognizer(model, 16000.0f)
                    this.recognizer = rec
                    
                    speechService = SpeechService(rec, 16000.0f)
                    speechService?.startListening(createRecognitionListener())
                    
                    Log.d(TAG, "Vosk语音识别服务已启动")
                },
                { exception: IOException ->
                    Log.e(TAG, "启动Vosk识别服务失败", exception)
                    callback?.onError("启动识别失败: ${exception.message}")
                    isListening = false
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "启动Vosk识别失败", e)
            callback?.onError("启动识别失败: ${e.message}")
            isListening = false
        }
    }
    
    /**
     * 停止语音识别
     */
    fun stopListening() {
        if (!isListening) {
            return
        }
        
        try {
            Log.d(TAG, "停止Vosk语音识别")
            speechService?.stop()
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "停止Vosk识别失败", e)
        }
    }
    
    /**
     * 创建识别监听器
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResult(result: String) {
                Log.d(TAG, "Vosk识别结果: $result")
                try {
                    // 解析JSON结果
                    val json = org.json.JSONObject(result)
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) {
                        callback?.onResult(text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析识别结果失败", e)
                }
            }
            
            override fun onPartialResult(result: String) {
                Log.d(TAG, "Vosk部分结果: $result")
                try {
                    val json = org.json.JSONObject(result)
                    val text = json.optString("partial", "")
                    if (text.isNotEmpty()) {
                        callback?.onPartialResult(text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析部分结果失败", e)
                }
            }
            
            override fun onFinalResult(result: String) {
                Log.d(TAG, "Vosk最终结果: $result")
                try {
                    val json = org.json.JSONObject(result)
                    val text = json.optString("text", "")
                    if (text.isNotEmpty()) {
                        callback?.onResult(text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析最终结果失败", e)
                }
                isListening = false
            }
            
            override fun onError(exception: Exception) {
                Log.e(TAG, "Vosk识别错误", exception)
                callback?.onError("识别错误: ${exception.message}")
                isListening = false
            }
            
            override fun onTimeout() {
                Log.d(TAG, "Vosk识别超时")
                callback?.onTimeout()
                isListening = false
            }
        }
    }
    
    /**
     * 检查模型是否已初始化
     */
    fun isModelInitialized(): Boolean {
        return isInitialized && model != null
    }
    
    /**
     * 检查是否正在监听
     */
    fun isCurrentlyListening(): Boolean {
        return isListening
    }
    
    /**
     * 获取模型路径
     */
    private fun getModelPath(): String? {
        val modelDir = getModelDirectory()
        return if (modelDir != null && File(modelDir).exists()) {
            modelDir
        } else {
            null
        }
    }
    
    /**
     * 获取模型目录
     */
    private fun getModelDirectory(): String? {
        // 尝试从assets解压的模型目录
        val externalModelDir = File(context.getExternalFilesDir(null), "model")
        if (externalModelDir.exists()) {
            return externalModelDir.absolutePath
        }
        
        // 尝试从内部存储
        val internalModelDir = File(context.filesDir, "model")
        if (internalModelDir.exists()) {
            return internalModelDir.absolutePath
        }
        
        return null
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "释放Vosk资源")
        stopListening()
        speechService?.shutdown()
        speechService = null
        recognizer = null
        model = null
        isInitialized = false
        scope.cancel()
    }
}

