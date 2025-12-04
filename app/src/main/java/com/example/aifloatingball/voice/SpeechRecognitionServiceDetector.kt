package com.example.aifloatingball.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 语音识别服务检测器
 * 用于检测系统语音识别服务的可用性和稳定性
 */
class SpeechRecognitionServiceDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechRecognitionServiceDetector"
        private const val MAX_DETECTION_ATTEMPTS = 3 // 最大检测次数
        private const val BIND_TIMEOUT_MS = 2000L // 绑定超时时间（毫秒）
        
        // 已知的语音识别服务包名
        private val GOOGLE_SERVICES = listOf(
            "com.google.android.googlequicksearchbox", // Google 搜索
            "com.google.android.apps.search" // Google 搜索应用
        )
        
        private val MANUFACTURER_SERVICES = mapOf(
            "xiaomi" to listOf(
                "com.xiaomi.mibrain.speech", // 小米语音服务
                "com.miui.voiceassist" // 小爱同学
            ),
            "huawei" to listOf(
                "com.huawei.vassistant", // 小艺语音助手
                "com.huawei.voiceassist" // 华为语音输入
            ),
            "oppo" to listOf(
                "com.oppo.breeno.assistant", // Breeno语音助手
                "com.coloros.assistant" // ColorOS语音助手
            ),
            "vivo" to listOf(
                "com.vivo.assistant", // Jovi语音助手
                "com.vivo.voice" // vivo语音
            ),
            "meizu" to listOf(
                "com.meizu.voiceassistant", // 魅族语音助手
                "com.meizu.speech" // 魅族语音服务
            ),
            "samsung" to listOf(
                "com.samsung.android.bixby.agent", // Bixby
                "com.samsung.android.svoice" // Samsung Voice
            )
        )
    }
    
    /**
     * 检测结果
     */
    data class DetectionResult(
        val isRecognitionAvailable: Boolean, // isRecognitionAvailable 返回值
        val isServiceBindable: Boolean, // 是否能成功绑定服务
        val servicePackageName: String?, // 检测到的服务包名
        val serviceType: ServiceType, // 服务类型
        val detectionAttempts: Int, // 检测尝试次数
        val successCount: Int, // 成功次数
        val failureCount: Int, // 失败次数
        val isStable: Boolean, // 是否稳定（多次检测结果一致）
        val recommendedAction: RecommendedAction, // 推荐操作
        val errorMessages: List<String> // 错误信息列表
    )
    
    enum class ServiceType {
        GOOGLE,           // Google 服务
        MANUFACTURER,     // 厂商服务
        UNKNOWN,          // 未知服务
        NONE              // 无服务
    }
    
    enum class RecommendedAction {
        USE_SYSTEM_SR,    // 使用系统 SpeechRecognizer
        USE_CLOUD_SDK,    // 使用云端 SDK（Aivs、科大讯飞等）
        USE_SYSTEM_INTENT, // 使用系统语音输入 Intent
        MANUAL_INPUT      // 手动输入
    }
    
    /**
     * 执行完整检测
     */
    fun detect(): DetectionResult {
        Log.d(TAG, "开始检测语音识别服务...")
        
        val errorMessages = mutableListOf<String>()
        var isRecognitionAvailable = false
        var isServiceBindable = false
        var servicePackageName: String? = null
        var serviceType = ServiceType.NONE
        var successCount = 0
        var failureCount = 0
        
        // 步骤1: 检测 isRecognitionAvailable
        val availabilityResults = mutableListOf<Boolean>()
        for (i in 1..MAX_DETECTION_ATTEMPTS) {
            try {
                val available = SpeechRecognizer.isRecognitionAvailable(context)
                availabilityResults.add(available)
                isRecognitionAvailable = available
                Log.d(TAG, "检测 $i: isRecognitionAvailable = $available")
                
                if (available) {
                    successCount++
                } else {
                    failureCount++
                    errorMessages.add("检测 $i: isRecognitionAvailable 返回 false")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检测 $i 时出错", e)
                availabilityResults.add(false)
                failureCount++
                errorMessages.add("检测 $i 异常: ${e.message}")
            }
            
            // 短暂延迟
            Thread.sleep(300)
        }
        
        // 判断 isRecognitionAvailable 是否稳定
        val isAvailabilityStable = availabilityResults.distinct().size == 1
        
        // 步骤2: 如果 isRecognitionAvailable 为 true，尝试检测服务包名和绑定
        if (isRecognitionAvailable) {
            // 检测服务包名
            servicePackageName = detectServicePackageName()
            serviceType = detectServiceType(servicePackageName)
            
            // 尝试绑定服务
            val bindResults = mutableListOf<Boolean>()
            for (i in 1..MAX_DETECTION_ATTEMPTS) {
                try {
                    val bindable = testServiceBinding()
                    bindResults.add(bindable)
                    if (bindable) {
                        successCount++
                        isServiceBindable = true
                        Log.d(TAG, "检测 $i: 服务绑定成功")
                    } else {
                        failureCount++
                        errorMessages.add("检测 $i: 服务绑定失败")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "检测 $i 绑定测试时出错", e)
                    bindResults.add(false)
                    failureCount++
                    errorMessages.add("检测 $i 绑定异常: ${e.message}")
                }
                
                // 短暂延迟
                Thread.sleep(500)
            }
            
            // 判断绑定是否稳定
            val isBindingStable = bindResults.distinct().size == 1
            val isStable = isAvailabilityStable && isBindingStable
            
            // 如果绑定不稳定或失败，标记为不可用
            if (!isBindingStable || !isServiceBindable) {
                Log.w(TAG, "服务绑定不稳定或失败，建议使用云端 SDK")
                isServiceBindable = false
            }
        } else {
            // isRecognitionAvailable 为 false，直接标记为不可用
            isServiceBindable = false
        }
        
        // 判断是否稳定（多次检测结果一致）
        val isStable = if (isRecognitionAvailable) {
            availabilityResults.distinct().size == 1
        } else {
            true // 如果不可用，认为是稳定的
        }
        
        // 确定推荐操作
        val recommendedAction = determineRecommendedAction(
            isRecognitionAvailable,
            isServiceBindable,
            serviceType,
            isStable
        )
        
        return DetectionResult(
            isRecognitionAvailable = isRecognitionAvailable,
            isServiceBindable = isServiceBindable,
            servicePackageName = servicePackageName,
            serviceType = serviceType,
            detectionAttempts = MAX_DETECTION_ATTEMPTS,
            successCount = successCount,
            failureCount = failureCount,
            isStable = isStable,
            recommendedAction = recommendedAction,
            errorMessages = errorMessages
        )
    }
    
    /**
     * 检测服务包名
     */
    private fun detectServicePackageName(): String? {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val activities = context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            
            if (activities.isNotEmpty()) {
                // 优先返回第一个匹配的服务
                val packageName = activities[0].activityInfo.packageName
                Log.d(TAG, "检测到语音识别服务包名: $packageName")
                return packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "检测服务包名时出错", e)
        }
        return null
    }
    
    /**
     * 检测服务类型
     */
    private fun detectServiceType(packageName: String?): ServiceType {
        if (packageName == null) {
            return ServiceType.NONE
        }
        
        // 检查是否是 Google 服务
        if (GOOGLE_SERVICES.any { packageName.contains(it, ignoreCase = true) }) {
            return ServiceType.GOOGLE
        }
        
        // 检查是否是厂商服务
        val manufacturer = Build.MANUFACTURER.lowercase()
        val services = MANUFACTURER_SERVICES[manufacturer] ?: emptyList()
        if (services.any { packageName.contains(it, ignoreCase = true) }) {
            return ServiceType.MANUFACTURER
        }
        
        return ServiceType.UNKNOWN
    }
    
    /**
     * 测试服务绑定
     */
    private fun testServiceBinding(): Boolean {
        var recognizer: SpeechRecognizer? = null
        var bindSuccess = false
        
        try {
            val latch = CountDownLatch(1)
            var bindResult = false
            
            // 在后台线程创建识别器
            Thread {
                try {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    if (recognizer != null) {
                        bindResult = true
                        Log.d(TAG, "服务绑定成功")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "服务绑定失败", e)
                    bindResult = false
                } finally {
                    latch.countDown()
                }
            }.start()
            
            // 等待绑定完成（最多等待2秒）
            val completed = latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            
            if (completed && bindResult) {
                bindSuccess = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "测试服务绑定时出错", e)
            bindSuccess = false
        } finally {
            // 释放识别器
            try {
                recognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "释放识别器时出错", e)
            }
        }
        
        return bindSuccess
    }
    
    /**
     * 确定推荐操作
     */
    private fun determineRecommendedAction(
        isRecognitionAvailable: Boolean,
        isServiceBindable: Boolean,
        serviceType: ServiceType,
        isStable: Boolean
    ): RecommendedAction {
        return when {
            // 如果没有服务，直接使用云端 SDK
            !isRecognitionAvailable -> {
                Log.d(TAG, "推荐操作: 使用云端 SDK（系统未提供语音识别服务）")
                RecommendedAction.USE_CLOUD_SDK
            }
            
            // 如果有服务但绑定失败或不稳定，使用云端 SDK
            !isServiceBindable || !isStable -> {
                Log.d(TAG, "推荐操作: 使用云端 SDK（服务不稳定或绑定失败）")
                RecommendedAction.USE_CLOUD_SDK
            }
            
            // 如果是 Google 服务，优先使用系统 SpeechRecognizer
            serviceType == ServiceType.GOOGLE -> {
                Log.d(TAG, "推荐操作: 使用系统 SpeechRecognizer（Google 服务）")
                RecommendedAction.USE_SYSTEM_SR
            }
            
            // 如果是厂商服务，使用系统 SpeechRecognizer
            serviceType == ServiceType.MANUFACTURER -> {
                Log.d(TAG, "推荐操作: 使用系统 SpeechRecognizer（厂商服务）")
                RecommendedAction.USE_SYSTEM_SR
            }
            
            // 其他情况，使用系统 SpeechRecognizer
            else -> {
                Log.d(TAG, "推荐操作: 使用系统 SpeechRecognizer（未知服务）")
                RecommendedAction.USE_SYSTEM_SR
            }
        }
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(): String {
        return "${Build.BRAND} ${Build.MODEL} (${Build.MANUFACTURER})"
    }
}

