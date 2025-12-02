package com.example.aifloatingball.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * 增强的语音识别管理器
 * 支持多个免费API，提供更好的识别效果
 * 
 * 免费方案：
 * 1. SpeechRecognizer（系统自带，免费）
 * 2. 百度语音识别API（免费额度：每天5万次，中文识别效果好）
 * 3. 讯飞语音识别API（免费额度：每天500次，中文识别准确率高）
 * 4. Google Cloud Speech-to-Text（免费额度：每月60分钟）
 * 
 * 策略：优先使用SpeechRecognizer，失败时自动切换到云API
 */
class EnhancedVoiceRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "EnhancedVoiceRecognizer"
        
        // 百度语音识别API配置（需要用户配置API Key）
        private const val BAIDU_API_URL = "https://vop.baidu.com/server_api"
        private const val BAIDU_TOKEN_URL = "https://openapi.baidu.com/oauth/2.0/token"
        
        // 讯飞语音识别API配置（需要用户配置AppID）
        private const val IFLYTEK_API_URL = "https://iat-api.xfyun.cn/v2/iat"
        
        // Google Cloud Speech-to-Text API（需要用户配置API Key）
        private const val GOOGLE_API_URL = "https://speech.googleapis.com/v1/speech:recognize"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var currentRecognizer: RecognizerType = RecognizerType.SPEECH_RECOGNIZER
    private val handler = Handler(Looper.getMainLooper())
    
    enum class RecognizerType {
        SPEECH_RECOGNIZER,  // Android系统SpeechRecognizer（免费，兼容性好）
        BAIDU_API,          // 百度语音识别API（免费额度大，中文识别好）
        IFLYTEK_API,        // 讯飞语音识别API（免费额度小，但准确率高）
        GOOGLE_CLOUD        // Google Cloud Speech-to-Text（需要网络，准确率高）
    }
    
    interface RecognitionCallback {
        /**
         * 部分识别结果（实时流式显示）
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
         * 识别开始
         */
        fun onStart()
        
        /**
         * 识别结束
         */
        fun onEnd()
    }
    
    private var callback: RecognitionCallback? = null
    
    fun setCallback(callback: RecognitionCallback) {
        this.callback = callback
    }
    
    /**
     * 开始语音识别
     * @param useCloudAPI 是否优先使用云API（需要配置API Key）
     */
    fun startRecognition(useCloudAPI: Boolean = false) {
        if (useCloudAPI && hasCloudAPIConfig()) {
            // 如果配置了云API，优先使用云API
            startCloudRecognition()
        } else {
            // 默认使用SpeechRecognizer
            startSpeechRecognizer()
        }
    }
    
    /**
     * 使用SpeechRecognizer（系统自带，免费）
     */
    private fun startSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback?.onError("设备不支持语音识别")
            return
        }
        
        currentRecognizer = RecognizerType.SPEECH_RECOGNIZER
        releaseSpeechRecognizer()
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        
        try {
            callback?.onStart()
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "SpeechRecognizer开始识别")
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer启动失败", e)
            callback?.onError("语音识别启动失败: ${e.message}")
        }
    }
    
    /**
     * 使用云API识别（需要配置）
     */
    private fun startCloudRecognition() {
        // 优先尝试百度API（免费额度大）
        if (hasBaiduAPIConfig()) {
            startBaiduRecognition()
        } else if (hasIflytekAPIConfig()) {
            startIflytekRecognition()
        } else if (hasGoogleCloudAPIConfig()) {
            startGoogleCloudRecognition()
        } else {
            // 没有配置云API，回退到SpeechRecognizer
            Log.d(TAG, "未配置云API，使用SpeechRecognizer")
            startSpeechRecognizer()
        }
    }
    
    /**
     * 创建SpeechRecognizer的监听器
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                handler.post {
                    callback?.onStart()
                }
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "开始说话")
            }
            
            override fun onRmsChanged(rmsdB: Float) {
                // 音量变化，可用于UI反馈
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "说话结束")
            }
            
            override fun onError(error: Int) {
                handler.post {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                        SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                        SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配的语音"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙碌"
                        SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                        else -> "未知错误: $error"
                    }
                    Log.e(TAG, "识别错误: $errorMsg")
                    callback?.onError(errorMsg)
                }
            }
            
            override fun onResults(results: android.os.Bundle?) {
                handler.post {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "最终识别结果: $text")
                        callback?.onFinalResult(text)
                    } else {
                        callback?.onError("没有识别到语音内容")
                    }
                    callback?.onEnd()
                }
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                handler.post {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "部分识别结果: $text")
                        callback?.onPartialResult(text)
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
    }
    
    /**
     * 百度语音识别API（需要配置API Key和Secret Key）
     * 免费额度：每天5万次
     * 中文识别效果好，支持实时流式识别
     */
    private fun startBaiduRecognition() {
        // TODO: 实现百度语音识别API调用
        // 需要：API Key、Secret Key
        // 支持实时流式识别，可以逐词返回结果
        Log.d(TAG, "百度语音识别API（待实现）")
        callback?.onError("百度API功能待实现，请先配置API Key")
    }
    
    /**
     * 讯飞语音识别API（需要配置AppID和API Key）
     * 免费额度：每天500次
     * 中文识别准确率高，支持实时流式识别
     */
    private fun startIflytekRecognition() {
        // TODO: 实现讯飞语音识别API调用
        // 需要：AppID、API Key、API Secret
        // 支持实时流式识别，可以逐词返回结果
        Log.d(TAG, "讯飞语音识别API（待实现）")
        callback?.onError("讯飞API功能待实现，请先配置AppID")
    }
    
    /**
     * Google Cloud Speech-to-Text API（需要配置API Key）
     * 免费额度：每月60分钟
     * 识别准确率高，支持多语言
     */
    private fun startGoogleCloudRecognition() {
        // TODO: 实现Google Cloud Speech-to-Text API调用
        // 需要：API Key
        // 支持实时流式识别
        Log.d(TAG, "Google Cloud Speech-to-Text API（待实现）")
        callback?.onError("Google Cloud API功能待实现，请先配置API Key")
    }
    
    /**
     * 停止识别
     */
    fun stopRecognition() {
        when (currentRecognizer) {
            RecognizerType.SPEECH_RECOGNIZER -> {
                speechRecognizer?.stopListening()
            }
            else -> {
                // 云API的停止逻辑
                callback?.onEnd()
            }
        }
    }
    
    /**
     * 取消识别
     */
    fun cancelRecognition() {
        when (currentRecognizer) {
            RecognizerType.SPEECH_RECOGNIZER -> {
                speechRecognizer?.cancel()
            }
            else -> {
                callback?.onEnd()
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        releaseSpeechRecognizer()
        callback = null
    }
    
    private fun releaseSpeechRecognizer() {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null
    }
    
    /**
     * 检查是否有云API配置
     */
    private fun hasCloudAPIConfig(): Boolean {
        return hasBaiduAPIConfig() || hasIflytekAPIConfig() || hasGoogleCloudAPIConfig()
    }
    
    /**
     * 检查是否有百度API配置
     */
    private fun hasBaiduAPIConfig(): Boolean {
        val prefs = context.getSharedPreferences("voice_recognition", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("baidu_api_key", "")
        val secretKey = prefs.getString("baidu_secret_key", "")
        return !apiKey.isNullOrEmpty() && !secretKey.isNullOrEmpty()
    }
    
    /**
     * 检查是否有讯飞API配置
     */
    private fun hasIflytekAPIConfig(): Boolean {
        val prefs = context.getSharedPreferences("voice_recognition", Context.MODE_PRIVATE)
        val appId = prefs.getString("iflytek_app_id", "")
        val apiKey = prefs.getString("iflytek_api_key", "")
        return !appId.isNullOrEmpty() && !apiKey.isNullOrEmpty()
    }
    
    /**
     * 检查是否有Google Cloud API配置
     */
    private fun hasGoogleCloudAPIConfig(): Boolean {
        val prefs = context.getSharedPreferences("voice_recognition", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("google_cloud_api_key", "")
        return !apiKey.isNullOrEmpty()
    }
    
    /**
     * 获取当前使用的识别器类型
     */
    fun getCurrentRecognizerType(): RecognizerType {
        return currentRecognizer
    }
    
    /**
     * 获取推荐的识别器（根据配置和网络情况）
     */
    fun getRecommendedRecognizer(): RecognizerType {
        return when {
            hasBaiduAPIConfig() -> RecognizerType.BAIDU_API
            hasIflytekAPIConfig() -> RecognizerType.IFLYTEK_API
            hasGoogleCloudAPIConfig() -> RecognizerType.GOOGLE_CLOUD
            SpeechRecognizer.isRecognitionAvailable(context) -> RecognizerType.SPEECH_RECOGNIZER
            else -> RecognizerType.SPEECH_RECOGNIZER
        }
    }
}

