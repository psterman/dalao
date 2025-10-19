package com.example.aifloatingball.tts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * TTS诊断工具
 * 用于检测和诊断TTS相关问题
 */
class TTSDiagnosticTool(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSDiagnosticTool"
    }
    
    /**
     * 执行完整的TTS诊断
     */
    fun performFullDiagnostic(): TTSDiagnosticResult {
        Log.d(TAG, "开始TTS诊断...")
        
        val result = TTSDiagnosticResult()
        
        // 1. 检查系统TTS支持
        result.systemTTSSupport = checkSystemTTSSupport()
        
        // 2. 检查TTS引擎
        result.availableEngines = getAvailableTTSEngines()
        
        // 3. 检查默认引擎
        result.defaultEngine = getDefaultTTSEngine()
        
        // 4. 检查语言支持
        result.languageSupport = checkLanguageSupport()
        
        // 5. 检查权限
        result.permissions = checkTTSPermissions()
        
        // 6. 检查系统设置
        result.systemSettings = checkSystemSettings()
        
        // 7. 生成建议
        result.suggestions = generateSuggestions(result)
        
        Log.d(TAG, "TTS诊断完成")
        return result
    }
    
    /**
     * 检查系统TTS支持
     */
    private fun checkSystemTTSSupport(): Boolean {
        return try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            activities.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "检查系统TTS支持失败", e)
            false
        }
    }
    
    /**
     * 获取可用的TTS引擎
     */
    private fun getAvailableTTSEngines(): List<String> {
        val engines = mutableListOf<String>()
        
        try {
            val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            
            for (activity in activities) {
                val packageName = activity.activityInfo.packageName
                val label = activity.loadLabel(context.packageManager).toString()
                engines.add("$label ($packageName)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取TTS引擎失败", e)
        }
        
        return engines
    }
    
    /**
     * 获取默认TTS引擎
     */
    private fun getDefaultTTSEngine(): String? {
        return try {
            // 使用反射调用getDefaultEngine方法，因为它在某些Android版本中可能不可用
            val engineClass = TextToSpeech.Engine::class.java
            val method = engineClass.getMethod("getDefaultEngine")
            method.invoke(null) as? String
        } catch (e: Exception) {
            Log.e(TAG, "获取默认TTS引擎失败", e)
            null
        }
    }
    
    /**
     * 检查语言支持
     */
    private fun checkLanguageSupport(): Map<String, Boolean> {
        val languageSupport = mutableMapOf<String, Boolean>()
        
        try {
            var testTts: TextToSpeech? = null
            testTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val testLanguages = listOf(
                        "zh" to Locale.SIMPLIFIED_CHINESE,
                        "en" to Locale.ENGLISH,
                        "ja" to Locale.JAPANESE,
                        "ko" to Locale.KOREAN
                    )
                    
                    for ((lang, locale) in testLanguages) {
                        val result = testTts?.setLanguage(locale)
                        languageSupport[lang] = result != TextToSpeech.LANG_MISSING_DATA && 
                                             result != TextToSpeech.LANG_NOT_SUPPORTED
                    }
                    
                    testTts?.shutdown()
                }
            }
            
            // 等待检测完成
            Thread.sleep(2000)
            
        } catch (e: Exception) {
            Log.e(TAG, "检查语言支持失败", e)
        }
        
        return languageSupport
    }
    
    /**
     * 检查TTS权限
     */
    private fun checkTTSPermissions(): Map<String, Boolean> {
        val permissions = mutableMapOf<String, Boolean>()
        
        val requiredPermissions = listOf(
            "android.permission.INTERNET",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO"
        )
        
        for (permission in requiredPermissions) {
            permissions[permission] = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        
        return permissions
    }
    
    /**
     * 检查系统设置
     */
    private fun checkSystemSettings(): Map<String, Any> {
        val settings = mutableMapOf<String, Any>()
        
        try {
            // 检查TTS设置
            val ttsSettings = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.TTS_DEFAULT_SYNTH
            )
            settings["tts_default_synth"] = ttsSettings ?: "未设置"
            
            // 检查TTS语言
            val ttsLanguage = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.TTS_DEFAULT_LANG
            )
            settings["tts_default_lang"] = ttsLanguage ?: "未设置"
            
            // 检查TTS国家
            val ttsCountry = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.TTS_DEFAULT_COUNTRY
            )
            settings["tts_default_country"] = ttsCountry ?: "未设置"
            
        } catch (e: Exception) {
            Log.e(TAG, "检查系统设置失败", e)
            settings["error"] = e.message ?: "未知错误"
        }
        
        return settings
    }
    
    /**
     * 生成修复建议
     */
    private fun generateSuggestions(result: TTSDiagnosticResult): List<String> {
        val suggestions = mutableListOf<String>()
        
        // 检查系统TTS支持
        if (!result.systemTTSSupport) {
            suggestions.add("设备不支持TTS功能，请检查系统版本或安装TTS引擎")
        }
        
        // 检查TTS引擎
        if (result.availableEngines.isEmpty()) {
            suggestions.add("未找到TTS引擎，请安装Google TTS或其他TTS引擎")
        }
        
        // 检查默认引擎
        if (result.defaultEngine.isNullOrEmpty()) {
            suggestions.add("未设置默认TTS引擎，请在系统设置中配置")
        }
        
        // 检查语言支持
        val hasChineseSupport = result.languageSupport["zh"] == true
        val hasEnglishSupport = result.languageSupport["en"] == true
        
        if (!hasChineseSupport && !hasEnglishSupport) {
            suggestions.add("TTS引擎不支持中文和英文，请安装支持的语言包")
        }
        
        // 检查权限
        val missingPermissions = result.permissions.filter { !it.value }.keys
        if (missingPermissions.isNotEmpty()) {
            suggestions.add("缺少必要权限: ${missingPermissions.joinToString(", ")}")
        }
        
        // 检查系统设置
        if (result.systemSettings["tts_default_synth"] == "未设置") {
            suggestions.add("系统TTS设置未配置，请前往设置->辅助功能->文字转语音进行配置")
        }
        
        return suggestions
    }
    
    /**
     * 打开TTS设置页面
     */
    fun openTTSSettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开TTS设置失败", e)
        }
    }
    
    /**
     * TTS诊断结果
     */
    data class TTSDiagnosticResult(
        var systemTTSSupport: Boolean = false,
        var availableEngines: List<String> = emptyList(),
        var defaultEngine: String? = null,
        var languageSupport: Map<String, Boolean> = emptyMap(),
        var permissions: Map<String, Boolean> = emptyMap(),
        var systemSettings: Map<String, Any> = emptyMap(),
        var suggestions: List<String> = emptyList()
    ) {
        fun isHealthy(): Boolean {
            return systemTTSSupport && 
                   availableEngines.isNotEmpty() && 
                   !defaultEngine.isNullOrEmpty() &&
                   (languageSupport["zh"] == true || languageSupport["en"] == true) &&
                   permissions.values.all { it }
        }
        
        fun getSummary(): String {
            return buildString {
                appendLine("TTS诊断结果:")
                appendLine("系统支持: ${if (systemTTSSupport) "是" else "否"}")
                appendLine("可用引擎: ${availableEngines.size}个")
                appendLine("默认引擎: ${defaultEngine ?: "未设置"}")
                appendLine("中文支持: ${if (languageSupport["zh"] == true) "是" else "否"}")
                appendLine("英文支持: ${if (languageSupport["en"] == true) "是" else "否"}")
                appendLine("权限完整: ${if (permissions.values.all { it }) "是" else "否"}")
                appendLine("建议:")
                suggestions.forEach { appendLine("- $it") }
            }
        }
    }
}
