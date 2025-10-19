package com.example.aifloatingball.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * TTS兼容性测试工具
 * 用于测试不同设备和Android版本的TTS兼容性
 */
class TTSCompatibilityTester(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSCompatibilityTester"
    }
    
    /**
     * 执行兼容性测试
     */
    fun performCompatibilityTest(): TTSCompatibilityResult {
        Log.d(TAG, "开始TTS兼容性测试...")
        
        val result = TTSCompatibilityResult()
        
        // 1. 设备信息
        result.deviceInfo = getDeviceInfo()
        
        // 2. Android版本兼容性
        result.androidCompatibility = testAndroidCompatibility()
        
        // 3. TTS引擎兼容性
        result.engineCompatibility = testEngineCompatibility()
        
        // 4. 语言兼容性
        result.languageCompatibility = testLanguageCompatibility()
        
        // 5. 权限兼容性
        result.permissionCompatibility = testPermissionCompatibility()
        
        // 6. 生成兼容性报告
        result.compatibilityReport = generateCompatibilityReport(result)
        
        Log.d(TAG, "TTS兼容性测试完成")
        return result
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            brand = Build.BRAND,
            product = Build.PRODUCT
        )
    }
    
    /**
     * 测试Android版本兼容性
     */
    private fun testAndroidCompatibility(): AndroidCompatibility {
        val apiLevel = Build.VERSION.SDK_INT
        val isCompatible = apiLevel >= 21 // Android 5.0+
        
        val features = mutableMapOf<String, Boolean>()
        features["TTS_BASIC"] = apiLevel >= 21
        features["TTS_ENGINE_SELECTION"] = apiLevel >= 21
        features["TTS_LANGUAGE_SELECTION"] = apiLevel >= 21
        features["TTS_VOICE_SELECTION"] = apiLevel >= 21
        features["TTS_AUDIO_FOCUS"] = apiLevel >= 23
        features["TTS_CALLBACKS"] = apiLevel >= 21
        
        return AndroidCompatibility(
            isCompatible = isCompatible,
            apiLevel = apiLevel,
            features = features
        )
    }
    
    /**
     * 测试TTS引擎兼容性
     */
    private fun testEngineCompatibility(): EngineCompatibility {
        val engines = mutableListOf<EngineInfo>()
        var defaultEngine: String? = null
        
        try {
            // 获取默认引擎
            defaultEngine = try {
                // 使用反射调用getDefaultEngine方法
                val engineClass = TextToSpeech.Engine::class.java
                val method = engineClass.getMethod("getDefaultEngine")
                method.invoke(null) as? String
            } catch (e: Exception) {
                Log.w(TAG, "获取默认TTS引擎失败", e)
                null
            }
            
            // 获取所有可用引擎
            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            
            for (activity in activities) {
                val packageName = activity.activityInfo.packageName
                val label = activity.loadLabel(context.packageManager).toString()
                val isDefault = packageName == defaultEngine
                
                engines.add(EngineInfo(
                    packageName = packageName,
                    label = label,
                    isDefault = isDefault,
                    isInstalled = true
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取TTS引擎信息失败", e)
        }
        
        return EngineCompatibility(
            engines = engines,
            defaultEngine = defaultEngine,
            hasEngines = engines.isNotEmpty()
        )
    }
    
    /**
     * 测试语言兼容性
     */
    private fun testLanguageCompatibility(): LanguageCompatibility {
        val languageSupport = mutableMapOf<String, LanguageSupport>()
        
        try {
            var testTts: TextToSpeech? = null
            testTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val testLanguages = listOf(
                        "zh-CN" to Locale.SIMPLIFIED_CHINESE,
                        "zh-TW" to Locale.TRADITIONAL_CHINESE,
                        "en-US" to Locale.US,
                        "en-GB" to Locale.UK,
                        "ja-JP" to Locale.JAPANESE,
                        "ko-KR" to Locale.KOREAN
                    )
                    
                    for ((langCode, locale) in testLanguages) {
                        val result = testTts?.setLanguage(locale)
                        val isSupported = result != TextToSpeech.LANG_MISSING_DATA && 
                                        result != TextToSpeech.LANG_NOT_SUPPORTED
                        
                        languageSupport[langCode] = LanguageSupport(
                            languageCode = langCode,
                            locale = locale,
                            isSupported = isSupported,
                            supportLevel = when (result) {
                                TextToSpeech.LANG_AVAILABLE -> "完全支持"
                                TextToSpeech.LANG_COUNTRY_AVAILABLE -> "部分支持"
                                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "变体支持"
                                TextToSpeech.LANG_MISSING_DATA -> "数据缺失"
                                TextToSpeech.LANG_NOT_SUPPORTED -> "不支持"
                                else -> "未知"
                            }
                        )
                    }
                    
                    testTts?.shutdown()
                }
            }
            
            // 等待检测完成
            Thread.sleep(3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "测试语言兼容性失败", e)
        }
        
        return LanguageCompatibility(
            languageSupport = languageSupport,
            hasChineseSupport = languageSupport["zh-CN"]?.isSupported == true || 
                               languageSupport["zh-TW"]?.isSupported == true,
            hasEnglishSupport = languageSupport["en-US"]?.isSupported == true || 
                               languageSupport["en-GB"]?.isSupported == true
        )
    }
    
    /**
     * 测试权限兼容性
     */
    private fun testPermissionCompatibility(): PermissionCompatibility {
        val permissions = mutableMapOf<String, Boolean>()
        
        val requiredPermissions = listOf(
            "android.permission.INTERNET",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO",
            "android.permission.WAKE_LOCK"
        )
        
        for (permission in requiredPermissions) {
            permissions[permission] = context.checkSelfPermission(permission) == 
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        val allGranted = permissions.values.all { it }
        
        return PermissionCompatibility(
            permissions = permissions,
            allGranted = allGranted,
            missingPermissions = permissions.filter { !it.value }.keys.toList()
        )
    }
    
    /**
     * 生成兼容性报告
     */
    private fun generateCompatibilityReport(result: TTSCompatibilityResult): String {
        return buildString {
            appendLine("TTS兼容性测试报告")
            appendLine("=".repeat(50))
            appendLine()
            
            // 设备信息
            appendLine("设备信息:")
            appendLine("  制造商: ${result.deviceInfo.manufacturer}")
            appendLine("  型号: ${result.deviceInfo.model}")
            appendLine("  Android版本: ${result.deviceInfo.androidVersion}")
            appendLine("  API级别: ${result.deviceInfo.apiLevel}")
            appendLine()
            
            // Android兼容性
            appendLine("Android兼容性:")
            appendLine("  兼容性: ${if (result.androidCompatibility.isCompatible) "是" else "否"}")
            appendLine("  API级别: ${result.androidCompatibility.apiLevel}")
            appendLine("  支持的功能:")
            result.androidCompatibility.features.forEach { (feature, supported) ->
                appendLine("    $feature: ${if (supported) "是" else "否"}")
            }
            appendLine()
            
            // 引擎兼容性
            appendLine("TTS引擎兼容性:")
            appendLine("  可用引擎: ${result.engineCompatibility.engines.size}个")
            appendLine("  默认引擎: ${result.engineCompatibility.defaultEngine ?: "未设置"}")
            appendLine("  引擎列表:")
            result.engineCompatibility.engines.forEach { engine ->
                appendLine("    ${engine.label} (${engine.packageName}) ${if (engine.isDefault) "[默认]" else ""}")
            }
            appendLine()
            
            // 语言兼容性
            appendLine("语言兼容性:")
            appendLine("  中文支持: ${if (result.languageCompatibility.hasChineseSupport) "是" else "否"}")
            appendLine("  英文支持: ${if (result.languageCompatibility.hasEnglishSupport) "是" else "否"}")
            appendLine("  语言详情:")
            result.languageCompatibility.languageSupport.forEach { (lang, support) ->
                appendLine("    $lang: ${support.supportLevel}")
            }
            appendLine()
            
            // 权限兼容性
            appendLine("权限兼容性:")
            appendLine("  所有权限: ${if (result.permissionCompatibility.allGranted) "已授予" else "部分缺失"}")
            appendLine("  权限详情:")
            result.permissionCompatibility.permissions.forEach { (permission, granted) ->
                appendLine("    $permission: ${if (granted) "已授予" else "未授予"}")
            }
            if (result.permissionCompatibility.missingPermissions.isNotEmpty()) {
                appendLine("  缺失权限: ${result.permissionCompatibility.missingPermissions.joinToString(", ")}")
            }
            appendLine()
            
            // 总体评估
            val overallCompatibility = evaluateOverallCompatibility(result)
            appendLine("总体评估:")
            appendLine("  兼容性等级: $overallCompatibility")
            appendLine("  建议: ${getCompatibilityRecommendations(result)}")
        }
    }
    
    /**
     * 评估总体兼容性
     */
    private fun evaluateOverallCompatibility(result: TTSCompatibilityResult): String {
        var score = 0
        var maxScore = 0
        
        // Android兼容性 (20分)
        maxScore += 20
        if (result.androidCompatibility.isCompatible) score += 20
        
        // 引擎兼容性 (30分)
        maxScore += 30
        if (result.engineCompatibility.hasEngines) score += 20
        if (!result.engineCompatibility.defaultEngine.isNullOrEmpty()) score += 10
        
        // 语言兼容性 (30分)
        maxScore += 30
        if (result.languageCompatibility.hasChineseSupport) score += 15
        if (result.languageCompatibility.hasEnglishSupport) score += 15
        
        // 权限兼容性 (20分)
        maxScore += 20
        if (result.permissionCompatibility.allGranted) score += 20
        
        val percentage = (score * 100) / maxScore
        
        return when {
            percentage >= 90 -> "优秀"
            percentage >= 70 -> "良好"
            percentage >= 50 -> "一般"
            percentage >= 30 -> "较差"
            else -> "很差"
        }
    }
    
    /**
     * 获取兼容性建议
     */
    private fun getCompatibilityRecommendations(result: TTSCompatibilityResult): String {
        val recommendations = mutableListOf<String>()
        
        if (!result.androidCompatibility.isCompatible) {
            recommendations.add("Android版本过低，建议升级到Android 5.0或更高版本")
        }
        
        if (!result.engineCompatibility.hasEngines) {
            recommendations.add("未找到TTS引擎，请安装Google TTS或其他TTS引擎")
        }
        
        if (result.engineCompatibility.defaultEngine.isNullOrEmpty()) {
            recommendations.add("未设置默认TTS引擎，请在系统设置中配置")
        }
        
        if (!result.languageCompatibility.hasChineseSupport && !result.languageCompatibility.hasEnglishSupport) {
            recommendations.add("TTS引擎不支持中文和英文，请安装支持的语言包")
        }
        
        if (!result.permissionCompatibility.allGranted) {
            recommendations.add("缺少必要权限，请在应用设置中授予权限")
        }
        
        return if (recommendations.isEmpty()) {
            "TTS兼容性良好，无需额外配置"
        } else {
            recommendations.joinToString("\n")
        }
    }
    
    // 数据类定义
    data class TTSCompatibilityResult(
        var deviceInfo: DeviceInfo = DeviceInfo(),
        var androidCompatibility: AndroidCompatibility = AndroidCompatibility(),
        var engineCompatibility: EngineCompatibility = EngineCompatibility(),
        var languageCompatibility: LanguageCompatibility = LanguageCompatibility(),
        var permissionCompatibility: PermissionCompatibility = PermissionCompatibility(),
        var compatibilityReport: String = ""
    )
    
    data class DeviceInfo(
        val manufacturer: String = "",
        val model: String = "",
        val androidVersion: String = "",
        val apiLevel: Int = 0,
        val brand: String = "",
        val product: String = ""
    )
    
    data class AndroidCompatibility(
        val isCompatible: Boolean = false,
        val apiLevel: Int = 0,
        val features: Map<String, Boolean> = emptyMap()
    )
    
    data class EngineCompatibility(
        val engines: List<EngineInfo> = emptyList(),
        val defaultEngine: String? = null,
        val hasEngines: Boolean = false
    )
    
    data class EngineInfo(
        val packageName: String = "",
        val label: String = "",
        val isDefault: Boolean = false,
        val isInstalled: Boolean = false
    )
    
    data class LanguageCompatibility(
        val languageSupport: Map<String, LanguageSupport> = emptyMap(),
        val hasChineseSupport: Boolean = false,
        val hasEnglishSupport: Boolean = false
    )
    
    data class LanguageSupport(
        val languageCode: String = "",
        val locale: Locale = Locale.getDefault(),
        val isSupported: Boolean = false,
        val supportLevel: String = ""
    )
    
    data class PermissionCompatibility(
        val permissions: Map<String, Boolean> = emptyMap(),
        val allGranted: Boolean = false,
        val missingPermissions: List<String> = emptyList()
    )
}