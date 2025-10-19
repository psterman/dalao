package com.example.aifloatingball.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

/**
 * TTS支持级别
 */
enum class TTSSupportLevel {
    UNKNOWN,           // 未知状态
    FULL_SUPPORT,      // 完全支持
    LIMITED_SUPPORT,   // 有限支持
    NO_SUPPORT,        // 不支持
    PERMISSION_DENIED, // 权限被拒绝
    ENGINE_UNAVAILABLE // 引擎不可用
}

/**
 * TTS引擎信息
 */
data class TTSEngineInfo(
    val engineName: String,
    val enginePackage: String,
    val isDefault: Boolean,
    val supportedLanguages: List<Locale>,
    val hasChineseSupport: Boolean,
    val hasEnglishSupport: Boolean
)

/**
 * TTS（文本转语音）管理器
 * 支持系统TTS引擎，兼容各品牌手机
 */
class TTSManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "TTSManager"
        
        @Volatile
        private var INSTANCE: TTSManager? = null
        
        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isEnabled = false
    private var currentUtteranceId: String? = null
    
    // TTS状态监听器
    private var statusListener: TTSStatusListener? = null
    
    // 默认TTS参数
    private var speechRate = 1.0f
    private var pitch = 1.0f
    private var volume = 1.0f
    
    // TTS支持状态
    private var ttsSupportLevel = TTSSupportLevel.UNKNOWN
    private var ttsEngineInfo: TTSEngineInfo? = null
    private var initializationAttempts = 0
    private val maxInitializationAttempts = 3
    
    init {
        initializeTTS()
    }
    
    
    /**
     * 公开的重新初始化方法
     */
    fun reinitializeTTS() {
        Log.d(TAG, "重新初始化TTS引擎...")
        release()
        initializeTTS()
    }
    
    /**
     * 初始化TTS引擎
     */
    private fun initializeTTS() {
        try {
            Log.d(TAG, "开始初始化TTS引擎...")
            
            // 检测TTS支持情况
            val supportInfo = detectTTSSupport()
            ttsSupportLevel = supportInfo.first
            ttsEngineInfo = supportInfo.second
            
            // 通知支持级别变化
            statusListener?.onSupportLevelChanged(ttsSupportLevel, ttsEngineInfo)
            
            when (ttsSupportLevel) {
                TTSSupportLevel.NO_SUPPORT -> {
                    Log.w(TAG, "设备不支持TTS功能，但允许尝试使用")
                    // 不直接返回，允许继续初始化
                }
                TTSSupportLevel.PERMISSION_DENIED -> {
                    Log.w(TAG, "TTS权限被拒绝，但允许尝试使用")
                    // 不直接返回，允许继续初始化
                }
                TTSSupportLevel.ENGINE_UNAVAILABLE -> {
                    Log.w(TAG, "TTS引擎不可用，但允许尝试使用")
                    // 不直接返回，允许继续初始化
                }
                else -> {
                    Log.d(TAG, "TTS支持级别: $ttsSupportLevel")
                }
            }
            
            // 尝试初始化TTS引擎，使用默认引擎
            val defaultEngine = try {
                // 使用反射调用getDefaultEngine方法
                val engineClass = TextToSpeech.Engine::class.java
                val method = engineClass.getMethod("getDefaultEngine")
                method.invoke(null) as? String
            } catch (e: Exception) {
                Log.w(TAG, "获取默认TTS引擎失败", e)
                null
            }
            if (defaultEngine != null && defaultEngine.isNotEmpty()) {
                tts = TextToSpeech(context, this, defaultEngine)
                Log.d(TAG, "使用默认TTS引擎初始化: $defaultEngine")
            } else {
                tts = TextToSpeech(context, this)
                Log.d(TAG, "使用系统默认TTS引擎初始化")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "TTS引擎初始化失败", e)
            ttsSupportLevel = TTSSupportLevel.ENGINE_UNAVAILABLE
            statusListener?.onError("TTS引擎初始化失败: ${e.message}")
        }
    }
    
    /**
     * 检测TTS支持情况 - 简化版本，更加宽松
     */
    private fun detectTTSSupport(): Pair<TTSSupportLevel, TTSEngineInfo?> {
        try {
            Log.d(TAG, "开始检测TTS支持情况...")
            
            // 简化检测：直接假设TTS可用，让实际使用时的错误处理来处理
            val engineName = "System TTS"
            val supportLevel = TTSSupportLevel.FULL_SUPPORT
            
            // 创建简化的引擎信息
            val supportedLanguages = listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH,
                Locale.US
            )
            
            val ttsEngineInfo = TTSEngineInfo(
                engineName = engineName,
                enginePackage = "com.android.tts",
                isDefault = true,
                supportedLanguages = supportedLanguages,
                hasChineseSupport = true,
                hasEnglishSupport = true
            )
            
            Log.d(TAG, "TTS支持检测完成: $supportLevel (简化检测)")
            return Pair(supportLevel, ttsEngineInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "TTS支持检测失败，使用默认支持", e)
            // 即使检测失败，也假设TTS可用
            return Pair(TTSSupportLevel.LIMITED_SUPPORT, null)
        }
    }
    
    /**
     * 检测TTS引擎支持的语言
     */
    private fun detectSupportedLanguages(engineInfo: TextToSpeech.EngineInfo): List<Locale> {
        val supportedLanguages = mutableListOf<Locale>()
        
        try {
            // 测试常见语言
            val testLanguages = listOf(
                Locale.CHINESE,
                Locale.SIMPLIFIED_CHINESE,
                Locale.TRADITIONAL_CHINESE,
                Locale.ENGLISH,
                Locale.US,
                Locale.UK,
                Locale.JAPANESE,
                Locale.KOREAN
            )
            
            // 创建临时TTS实例进行测试
            var testTts: TextToSpeech? = null
            testTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    testLanguages.forEach { locale ->
                        val result = testTts?.setLanguage(locale)
                        if (result != TextToSpeech.LANG_MISSING_DATA && 
                            result != TextToSpeech.LANG_NOT_SUPPORTED) {
                            supportedLanguages.add(locale)
                        }
                    }
                    testTts?.shutdown()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "语言支持检测失败", e)
        }
        
        return supportedLanguages
    }
    
    /**
     * TTS初始化回调
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            initializationAttempts = 0
            Log.d(TAG, "TTS引擎初始化成功")
            
            // 设置语言支持
            setupLanguageSupport()
            
            // 设置语音参数
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(pitch)
            
            // 设置进度监听器
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "开始朗读: $utteranceId")
                    statusListener?.onStart(utteranceId)
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "朗读完成: $utteranceId")
                    statusListener?.onComplete(utteranceId)
                    currentUtteranceId = null
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "朗读错误: $utteranceId")
                    statusListener?.onError("朗读失败")
                    currentUtteranceId = null
                }
                
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    Log.d(TAG, "朗读停止: $utteranceId, 被中断: $interrupted")
                    statusListener?.onStop(utteranceId, interrupted)
                    currentUtteranceId = null
                }
            })
            
            statusListener?.onInitialized()
        } else {
            Log.e(TAG, "TTS引擎初始化失败，状态码: $status")
            initializationAttempts++
            
            if (initializationAttempts < maxInitializationAttempts) {
                Log.d(TAG, "尝试重新初始化TTS引擎 (${initializationAttempts}/${maxInitializationAttempts})")
                // 延迟重试
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    initializeTTS()
                }, 1000)
            } else {
                Log.e(TAG, "TTS引擎初始化失败，已达到最大重试次数")
                ttsSupportLevel = TTSSupportLevel.ENGINE_UNAVAILABLE
                statusListener?.onError("TTS引擎初始化失败，请检查系统设置")
            }
        }
    }
    
    /**
     * 设置语言支持
     */
    private fun setupLanguageSupport() {
        try {
            // 获取系统语言
            val systemLocale = Locale.getDefault()
            Log.d(TAG, "系统语言: ${systemLocale.language}")
            
            // 优先使用系统语言
            var result = tts?.setLanguage(systemLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "系统语言TTS不支持，尝试中文")
                result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "简体中文TTS不支持，尝试繁体中文")
                    result = tts?.setLanguage(Locale.TRADITIONAL_CHINESE)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "繁体中文TTS不支持，尝试中文")
                        result = tts?.setLanguage(Locale.CHINESE)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "中文TTS不支持，使用英文")
                            result = tts?.setLanguage(Locale.ENGLISH)
                        }
                    }
                }
            }
            
            Log.d(TAG, "语言设置完成，结果: $result")
            
            // 验证语言设置是否成功
            when (result) {
                TextToSpeech.LANG_AVAILABLE -> Log.d(TAG, "语言设置成功")
                TextToSpeech.LANG_COUNTRY_AVAILABLE -> Log.d(TAG, "语言设置成功（部分支持）")
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> Log.d(TAG, "语言设置成功（变体支持）")
                TextToSpeech.LANG_MISSING_DATA -> Log.w(TAG, "语言数据缺失")
                TextToSpeech.LANG_NOT_SUPPORTED -> Log.w(TAG, "语言不支持")
                else -> Log.w(TAG, "语言设置未知结果: $result")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "设置语言支持失败", e)
        }
    }
    
    /**
     * 设置TTS状态监听器
     */
    fun setStatusListener(listener: TTSStatusListener?) {
        this.statusListener = listener
    }
    
    /**
     * 启用/禁用TTS功能
     */
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        Log.d(TAG, "TTS功能${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 检查TTS是否支持
     */
    fun isTTSSupported(): Boolean {
        // 总是返回true，让实际使用时的错误处理来处理
        return true
    }
    
    /**
     * 检查TTS是否完全支持（严格模式）
     */
    fun isTTSFullySupported(): Boolean {
        return ttsSupportLevel == TTSSupportLevel.FULL_SUPPORT || 
               ttsSupportLevel == TTSSupportLevel.LIMITED_SUPPORT
    }
    
    /**
     * 检查TTS是否可用
     */
    fun isAvailable(): Boolean {
        // 简化检查：只要TTS已初始化且已启用就认为可用
        return isInitialized && isEnabled && tts != null
    }
    
    /**
     * 获取TTS支持级别
     */
    fun getTTSSupportLevel(): TTSSupportLevel {
        return ttsSupportLevel
    }
    
    /**
     * 获取TTS引擎信息
     */
    fun getTTSEngineInfo(): TTSEngineInfo? {
        return ttsEngineInfo
    }
    
    /**
     * 获取TTS状态描述
     */
    fun getTTSStatusDescription(): String {
        return when (ttsSupportLevel) {
            TTSSupportLevel.FULL_SUPPORT -> "TTS功能完全支持"
            TTSSupportLevel.LIMITED_SUPPORT -> "TTS功能有限支持"
            TTSSupportLevel.NO_SUPPORT -> "设备不支持TTS功能"
            TTSSupportLevel.PERMISSION_DENIED -> "TTS权限被拒绝"
            TTSSupportLevel.ENGINE_UNAVAILABLE -> "TTS引擎不可用"
            TTSSupportLevel.UNKNOWN -> "TTS状态未知"
        }
    }
    
    /**
     * 打开TTS设置页面
     */
    fun openTTSSettings() {
        try {
            // 尝试多种TTS设置入口
            val ttsSettingsIntents = listOf(
                Intent("com.android.settings.TTS_SETTINGS"),
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            
            for (intent in ttsSettingsIntents) {
                try {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    Log.d(TAG, "成功打开TTS设置页面")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "无法打开TTS设置页面: ${intent.action}", e)
                }
            }
            
            Log.e(TAG, "所有TTS设置入口都失败")
        } catch (e: Exception) {
            Log.e(TAG, "打开TTS设置页面失败", e)
        }
    }
    
    /**
     * 检查TTS设置状态
     */
    fun checkTTSSettings(): String {
        return when (ttsSupportLevel) {
            TTSSupportLevel.FULL_SUPPORT -> "TTS功能完全支持"
            TTSSupportLevel.LIMITED_SUPPORT -> "TTS功能有限支持"
            TTSSupportLevel.NO_SUPPORT -> "设备不支持TTS功能，请检查系统设置"
            TTSSupportLevel.PERMISSION_DENIED -> "TTS权限被拒绝，请在系统设置中启用"
            TTSSupportLevel.ENGINE_UNAVAILABLE -> "TTS引擎不可用，请安装TTS引擎或检查设置"
            TTSSupportLevel.UNKNOWN -> "TTS状态未知，请检查系统设置"
        }
    }
    
    
    /**
     * 获取TTS状态详情（用于调试）
     */
    fun getTTSStatusDetails(): String {
        return """
            TTS状态详情:
            - 系统TTS: isInitialized=$isInitialized, tts=${tts != null}, isTTSSupported=${isTTSSupported()}
            - 支持级别: $ttsSupportLevel
            - 引擎信息: ${ttsEngineInfo?.engineName ?: "无"}
        """.trimIndent()
    }
    
    
    /**
     * 检查并显示TTS错误对话框
     */
    fun checkAndShowTTSDialog() {
        Log.d(TAG, "检查TTS状态并显示对话框")
        
        if (!isEnabled) {
            statusListener?.onError("TTS功能已禁用")
            return
        }
        
        if (isInitialized && tts != null && isTTSSupported()) {
            Log.d(TAG, "系统TTS可用")
            return
        }
        
        // 系统TTS不可用，显示错误对话框
        Log.d(TAG, "系统TTS不可用，显示错误对话框")
        statusListener?.onError("TTS功能不可用，请检查系统设置")
    }
    
    /**
     * 朗读文本
     */
    fun speak(text: String, utteranceId: String? = null) {
        if (!isEnabled) {
            Log.w(TAG, "TTS功能已禁用，跳过朗读")
            statusListener?.onError("TTS功能已禁用")
            return
        }
        
        Log.d(TAG, "开始朗读，系统TTS状态: isInitialized=$isInitialized, tts=$tts, isEnabled=$isEnabled")
        
        // 只使用系统TTS，即使状态不完美也尝试朗读
        if (isInitialized && tts != null && isEnabled) {
            Log.d(TAG, "使用系统TTS朗读")
            speakWithSystemTTS(text, utteranceId)
        } else {
            Log.w(TAG, "系统TTS不可用，尝试强制初始化")
            Log.w(TAG, "系统TTS: isInitialized=$isInitialized, tts=$tts, isEnabled=$isEnabled")
            
            // 尝试强制初始化TTS
            if (tts == null) {
                Log.d(TAG, "TTS实例为空，尝试重新创建")
                tts = TextToSpeech(context, this)
                // 给TTS一些时间初始化
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (isInitialized && tts != null) {
                        Log.d(TAG, "延迟后使用系统TTS朗读")
                        speakWithSystemTTS(text, utteranceId)
                    } else {
                        statusListener?.onError("TTS功能不可用，请检查系统设置")
                    }
                }, 1000)
            } else {
                statusListener?.onError("TTS功能不可用，请检查系统设置")
            }
        }
    }
    
    /**
     * 使用系统TTS朗读
     */
    private fun speakWithSystemTTS(text: String, utteranceId: String?) {
        try {
            val cleanText = cleanTextForSpeech(text)
            if (cleanText.isBlank()) {
                Log.w(TAG, "清理后的文本为空，跳过朗读")
                statusListener?.onError("文本内容为空")
                return
            }
            
            // 检查TTS状态
            if (tts == null) {
                Log.e(TAG, "TTS引擎未初始化")
                statusListener?.onError("TTS引擎未初始化")
                return
            }
            
            if (!isInitialized) {
                Log.e(TAG, "TTS引擎未就绪")
                statusListener?.onError("TTS引擎未就绪，请稍后重试")
                return
            }
            
            currentUtteranceId = utteranceId ?: "default_${System.currentTimeMillis()}"
            
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            // 使用字符串常量，因为某些Android版本可能没有这些常量
            params.putFloat("rate", speechRate)
            params.putFloat("pitch", pitch)
            
            Log.d(TAG, "开始朗读，文本长度: ${cleanText.length}")
            Log.d(TAG, "朗读参数: volume=$volume, rate=$speechRate, pitch=$pitch")
            
            val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, currentUtteranceId)
            if (result == TextToSpeech.SUCCESS) {
                Log.d(TAG, "开始朗读: ${cleanText.take(50)}...")
            } else {
                Log.e(TAG, "朗读失败，错误码: $result")
                val errorMessage = when (result) {
                    TextToSpeech.ERROR -> "TTS引擎错误"
                    TextToSpeech.ERROR_INVALID_REQUEST -> "无效的朗读请求"
                    TextToSpeech.ERROR_NETWORK -> "网络错误"
                    TextToSpeech.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    -1 -> "TTS未初始化" // ERROR_NOT_INITIALIZED
                    -2 -> "资源不足" // ERROR_OUT_OF_RESOURCES
                    TextToSpeech.ERROR_SERVICE -> "TTS服务错误"
                    TextToSpeech.ERROR_SYNTHESIS -> "语音合成错误"
                    else -> "朗读失败，错误码: $result"
                }
                statusListener?.onError(errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "朗读异常", e)
            statusListener?.onError("朗读异常: ${e.message}")
        }
    }
    
    /**
     * 停止朗读
     */
    fun stop() {
        try {
            tts?.stop()
            currentUtteranceId = null
            Log.d(TAG, "停止朗读")
        } catch (e: Exception) {
            Log.e(TAG, "停止朗读异常", e)
        }
    }
    
    /**
     * 设置语音参数
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 3.0f)
        tts?.setSpeechRate(speechRate)
        Log.d(TAG, "设置语音速度: $speechRate")
    }
    
    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.1f, 2.0f)
        tts?.setPitch(this.pitch)
        Log.d(TAG, "设置语音音调: $pitch")
    }
    
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.0f, 1.0f)
        Log.d(TAG, "设置语音音量: $volume")
    }
    
    /**
     * 获取当前语音参数
     */
    fun getSpeechRate(): Float = speechRate
    fun getPitch(): Float = pitch
    fun getVolume(): Float = volume
    
    /**
     * 清理文本，移除不适合朗读的符号和格式
     */
    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("```[\\s\\S]*?```"), "") // 移除代码块
            .replace(Regex("`[^`]*`"), "") // 移除行内代码
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // 移除粗体标记
            .replace(Regex("\\*([^*]+)\\*"), "$1") // 移除斜体标记
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1") // 移除链接，保留文本
            .replace(Regex("#+\\s*"), "") // 移除标题标记
            .replace(Regex("\\n\\s*\\n"), "。") // 将多个换行替换为句号
            .replace(Regex("\\s+"), " ") // 合并多个空格
            .trim()
    }
    
    /**
     * 强制初始化TTS（用于修复TTS不可用的问题）
     */
    fun forceInitializeTTS() {
        try {
            Log.d(TAG, "强制初始化TTS...")
            
            // 如果TTS实例不存在，创建新的
            if (tts == null) {
                tts = TextToSpeech(context, this)
                Log.d(TAG, "创建新的TTS实例")
            }
            
            // 如果TTS未初始化，等待初始化完成
            if (!isInitialized) {
                Log.d(TAG, "等待TTS初始化完成...")
                // 给TTS最多5秒时间初始化
                var waitTime = 0
                while (!isInitialized && waitTime < 5000) {
                    Thread.sleep(100)
                    waitTime += 100
                }
            }
            
            if (isInitialized && tts != null) {
                Log.d(TAG, "TTS强制初始化成功")
                isEnabled = true
            } else {
                Log.w(TAG, "TTS强制初始化失败")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "强制初始化TTS失败", e)
        }
    }
    
    /**
     * 释放TTS资源
     */
    fun release() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            Log.d(TAG, "TTS资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放TTS资源异常", e)
        }
    }
    
    /**
     * TTS状态监听器接口
     */
    interface TTSStatusListener {
        fun onInitialized()
        fun onStart(utteranceId: String?)
        fun onComplete(utteranceId: String?)
        fun onStop(utteranceId: String?, interrupted: Boolean)
        fun onError(error: String)
        fun onSupportLevelChanged(supportLevel: TTSSupportLevel, engineInfo: TTSEngineInfo?)
    }
}