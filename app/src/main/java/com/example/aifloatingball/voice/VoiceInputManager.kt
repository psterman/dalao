package com.example.aifloatingball.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * 多层级语音输入管理器
 * 支持各种国产手机的语音输入方案
 */
class VoiceInputManager(private val activity: Activity) {
    
    companion object {
        private const val TAG = "VoiceInputManager"
        const val SYSTEM_VOICE_REQUEST_CODE = 1003
        const val BRAND_VOICE_REQUEST_CODE = 1004
        const val IME_VOICE_REQUEST_CODE = 1005
    }
    
    interface VoiceInputCallback {
        fun onVoiceInputResult(text: String)
        fun onVoiceInputError(error: String)
        fun onVoiceInputCancelled()
    }

    /**
     * 语音支持检测结果
     */
    data class VoiceSupportInfo(
        val isSupported: Boolean,
        val supportLevel: SupportLevel,
        val availableMethods: List<String>,
        val recommendedMethod: String?,
        val statusMessage: String
    )

    enum class SupportLevel {
        FULL_SUPPORT,      // 完全支持（SpeechRecognizer可用）
        PARTIAL_SUPPORT,   // 部分支持（有备用方案）
        LIMITED_SUPPORT,   // 有限支持（仅手动输入）
        NO_SUPPORT         // 不支持
    }
    
    private var callback: VoiceInputCallback? = null
    
    fun setCallback(callback: VoiceInputCallback) {
        this.callback = callback
    }

    /**
     * 检测语音输入支持情况
     * 这个方法应该在UI初始化时调用，用于决定语音按钮的显示状态
     */
    fun detectVoiceSupport(): VoiceSupportInfo {
        // 检查录音权限
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // 核心检查：SpeechRecognizer是否可用
        val speechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(activity)

        return if (speechRecognizerAvailable) {
            // 支持SpeechRecognizer：显示语音按钮
            val statusMessage = if (hasRecordPermission) {
                "语音识别可用"
            } else {
                "语音识别可用（需要录音权限）"
            }

            Log.d(TAG, "SpeechRecognizer可用，显示语音按钮")

            VoiceSupportInfo(
                isSupported = true,
                supportLevel = SupportLevel.FULL_SUPPORT,
                availableMethods = listOf("SpeechRecognizer"),
                recommendedMethod = "SpeechRecognizer",
                statusMessage = statusMessage
            )
        } else {
            // 不支持SpeechRecognizer：隐藏语音按钮
            Log.d(TAG, "SpeechRecognizer不可用，隐藏语音按钮")

            VoiceSupportInfo(
                isSupported = false,
                supportLevel = SupportLevel.NO_SUPPORT,
                availableMethods = listOf("手动输入"),
                recommendedMethod = "手动输入",
                statusMessage = "设备不支持语音输入"
            )
        }
    }

    /**
     * 检测品牌特定的语音方案
     */
    private fun detectBrandSpecificMethods(): List<String> {
        val methods = mutableListOf<String>()
        val brand = getDeviceBrand()
        val manufacturer = Build.MANUFACTURER.lowercase()

        when {
            brand.contains("oppo") || manufacturer.contains("oppo") -> {
                if (isIntentAvailable("com.oppo.breeno.assistant", "com.oppo.breeno.assistant.VoiceActivity")) {
                    methods.add("Breeno语音助手")
                }
                if (isIntentAvailable("com.oppo.inputmethod", "com.oppo.inputmethod.voice.VoiceInputActivity")) {
                    methods.add("OPPO输入法语音")
                }
            }
            brand.contains("vivo") || manufacturer.contains("vivo") -> {
                if (isIntentAvailable("com.vivo.assistant", "com.vivo.assistant.VoiceActivity")) {
                    methods.add("Jovi语音助手")
                }
                if (isIntentAvailable("com.vivo.inputmethod", "com.vivo.inputmethod.voice.VoiceActivity")) {
                    methods.add("vivo输入法语音")
                }
            }
            brand.contains("xiaomi") || manufacturer.contains("xiaomi") -> {
                if (isIntentAvailable("com.miui.voiceassist", "com.miui.voiceassist.VoiceAssistActivity")) {
                    methods.add("小爱同学")
                }
                if (isIntentAvailable("com.sohu.inputmethod.sogou.xiaomi", "com.sohu.inputmethod.sogou.voice.VoiceActivity")) {
                    methods.add("搜狗输入法小米版")
                }
            }
            brand.contains("huawei") || manufacturer.contains("huawei") -> {
                if (isIntentAvailable("com.huawei.vassistant", "com.huawei.vassistant.VoiceActivity")) {
                    methods.add("小艺语音助手")
                }
                if (isIntentAvailable("com.huawei.voiceassist", "com.huawei.voiceassist.VoiceActivity")) {
                    methods.add("华为语音输入")
                }
            }
            brand.contains("honor") || manufacturer.contains("honor") -> {
                if (isIntentAvailable("com.honor.voiceassist", "com.honor.voiceassist.VoiceActivity")) {
                    methods.add("荣耀语音助手")
                }
                if (isIntentAvailable("com.honor.yoyo", "com.honor.yoyo.VoiceActivity")) {
                    methods.add("YOYO语音助手")
                }
            }
            brand.contains("oneplus") || manufacturer.contains("oneplus") -> {
                if (isIntentAvailable("com.oneplus.voiceassist", "com.oneplus.voiceassist.VoiceActivity")) {
                    methods.add("一加语音助手")
                }
            }
        }

        return methods
    }

    /**
     * 检测输入法语音方案
     */
    private fun detectIMEVoiceMethods(): List<String> {
        val methods = mutableListOf<String>()
        val commonIMEs = mapOf(
            "com.sohu.inputmethod.sogou" to "搜狗输入法语音",
            "com.baidu.input" to "百度输入法语音",
            "com.iflytek.inputmethod" to "讯飞输入法语音",
            "com.tencent.qqpinyin" to "QQ输入法语音",
            "com.google.android.inputmethod.latin" to "Google输入法语音"
        )

        for ((packageName, methodName) in commonIMEs) {
            if (isPackageInstalled(packageName)) {
                methods.add(methodName)
            }
        }

        return methods
    }

    /**
     * 检查Intent是否可用
     */
    private fun isIntentAvailable(packageName: String, className: String): Boolean {
        return try {
            val intent = Intent().setClassName(packageName, className)
            intent.resolveActivity(activity.packageManager) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 启动语音输入 - 多层级策略
     */
    fun startVoiceInput() {
        Log.d(TAG, "开始语音输入，设备: ${getDeviceInfo()}")
        
        // 第1层：尝试SpeechRecognizer
        if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            Log.d(TAG, "使用SpeechRecognizer")
            callback?.onVoiceInputError("请使用应用内置的SpeechRecognizer功能")
            return
        }
        
        // 第2层：尝试品牌特定方案
        if (tryBrandSpecificVoiceInput()) {
            Log.d(TAG, "使用品牌特定语音输入")
            return
        }
        
        // 第3层：尝试系统语音输入Intent
        if (trySystemVoiceInput()) {
            Log.d(TAG, "使用系统语音输入Intent")
            return
        }
        
        // 第4层：尝试输入法语音输入
        if (tryIMEVoiceInput()) {
            Log.d(TAG, "使用输入法语音输入")
            return
        }
        
        // 第5层：显示选择对话框
        showVoiceInputOptions()
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        return "${Build.BRAND} ${Build.MODEL} (${Build.MANUFACTURER})"
    }
    
    /**
     * 获取设备品牌
     */
    private fun getDeviceBrand(): String {
        return Build.BRAND.lowercase()
    }
    
    /**
     * 尝试品牌特定的语音输入方案
     */
    private fun tryBrandSpecificVoiceInput(): Boolean {
        val brand = getDeviceBrand()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            brand.contains("oppo") || manufacturer.contains("oppo") -> {
                tryOppoVoiceInput()
            }
            brand.contains("vivo") || manufacturer.contains("vivo") -> {
                tryVivoVoiceInput()
            }
            brand.contains("xiaomi") || manufacturer.contains("xiaomi") -> {
                tryMiuiVoiceInput()
            }
            brand.contains("huawei") || manufacturer.contains("huawei") -> {
                tryHuaweiVoiceInput()
            }
            brand.contains("honor") || manufacturer.contains("honor") -> {
                tryHonorVoiceInput()
            }
            brand.contains("oneplus") || manufacturer.contains("oneplus") -> {
                tryOnePlusVoiceInput()
            }
            else -> false
        }
    }
    
    /**
     * OPPO ColorOS语音输入
     */
    private fun tryOppoVoiceInput(): Boolean {
        val intents = listOf(
            // Breeno语音助手
            Intent().setClassName("com.oppo.breeno.assistant", 
                                 "com.oppo.breeno.assistant.VoiceActivity"),
            // OPPO输入法语音
            Intent().setClassName("com.oppo.inputmethod", 
                                 "com.oppo.inputmethod.voice.VoiceInputActivity"),
            // ColorOS语音助手
            Intent().setClassName("com.coloros.assistant", 
                                 "com.coloros.assistant.VoiceActivity")
        )
        
        return tryIntents(intents, BRAND_VOICE_REQUEST_CODE)
    }
    
    /**
     * vivo FuntouchOS语音输入
     */
    private fun tryVivoVoiceInput(): Boolean {
        val intents = listOf(
            // Jovi语音助手
            Intent().setClassName("com.vivo.assistant", 
                                 "com.vivo.assistant.VoiceActivity"),
            // vivo输入法语音
            Intent().setClassName("com.vivo.inputmethod", 
                                 "com.vivo.inputmethod.voice.VoiceActivity"),
            // vivo语音助手
            Intent().setClassName("com.vivo.voice", 
                                 "com.vivo.voice.VoiceActivity")
        )
        
        return tryIntents(intents, BRAND_VOICE_REQUEST_CODE)
    }
    
    /**
     * 小米MIUI语音输入
     */
    private fun tryMiuiVoiceInput(): Boolean {
        val intents = listOf(
            // 小爱同学
            Intent().setClassName("com.miui.voiceassist", 
                                 "com.miui.voiceassist.VoiceAssistActivity"),
            // 小米语音输入
            Intent().setClassName("com.xiaomi.voiceassist", 
                                 "com.xiaomi.voiceassist.VoiceActivity"),
            // 搜狗输入法小米版
            Intent().setClassName("com.sohu.inputmethod.sogou.xiaomi", 
                                 "com.sohu.inputmethod.sogou.voice.VoiceActivity")
        )
        
        return tryIntents(intents, BRAND_VOICE_REQUEST_CODE)
    }
    
    /**
     * 华为EMUI语音输入
     */
    private fun tryHuaweiVoiceInput(): Boolean {
        val intents = listOf(
            // 小艺语音助手
            Intent().setClassName("com.huawei.vassistant", 
                                 "com.huawei.vassistant.VoiceActivity"),
            // 华为语音输入
            Intent().setClassName("com.huawei.voiceassist", 
                                 "com.huawei.voiceassist.VoiceActivity"),
            // HiVoice
            Intent().setClassName("com.huawei.hivoice", 
                                 "com.huawei.hivoice.VoiceActivity")
        )
        
        return tryIntents(intents, BRAND_VOICE_REQUEST_CODE)
    }
    
    /**
     * 荣耀MagicOS语音输入
     */
    private fun tryHonorVoiceInput(): Boolean {
        val intents = listOf(
            // 荣耀语音助手
            Intent().setClassName("com.honor.voiceassist", 
                                 "com.honor.voiceassist.VoiceActivity"),
            // YOYO语音助手
            Intent().setClassName("com.honor.yoyo", 
                                 "com.honor.yoyo.VoiceActivity")
        )
        
        return tryIntents(intents, BRAND_VOICE_REQUEST_CODE)
    }
    
    /**
     * 一加OxygenOS语音输入
     */
    private fun tryOnePlusVoiceInput(): Boolean {
        val intents = listOf(
            // 一加语音助手
            Intent().setClassName("com.oneplus.voiceassist", 
                                 "com.oneplus.voiceassist.VoiceActivity")
        )
        
        return tryIntents(intents, BRAND_VOICE_REQUEST_CODE)
    }
    
    /**
     * 尝试系统语音输入Intent
     */
    private fun trySystemVoiceInput(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        return try {
            val activities = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (activities.isNotEmpty()) {
                activity.startActivityForResult(intent, SYSTEM_VOICE_REQUEST_CODE)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "系统语音输入启动失败", e)
            false
        }
    }
    
    /**
     * 尝试输入法语音输入
     */
    private fun tryIMEVoiceInput(): Boolean {
        val commonIMEs = mapOf(
            "com.sohu.inputmethod.sogou" to "搜狗输入法",
            "com.baidu.input" to "百度输入法", 
            "com.iflytek.inputmethod" to "讯飞输入法",
            "com.tencent.qqpinyin" to "QQ输入法",
            "com.google.android.inputmethod.latin" to "Google输入法"
        )
        
        for ((packageName, imeName) in commonIMEs) {
            if (isPackageInstalled(packageName)) {
                Log.d(TAG, "发现已安装的输入法: $imeName")
                showIMEVoiceInputGuide(imeName)
                return true
            }
        }
        
        return false
    }
    
    /**
     * 显示输入法语音输入指导
     */
    private fun showIMEVoiceInputGuide(imeName: String) {
        AlertDialog.Builder(activity)
            .setTitle("使用输入法语音输入")
            .setMessage("检测到您安装了${imeName}，请按以下步骤操作：\n\n1. 点击下方的文本输入框\n2. 在弹出的输入法中找到语音按钮（通常是麦克风图标）\n3. 点击语音按钮开始录音")
            .setPositiveButton("知道了") { dialog, _ ->
                dialog.dismiss()
                // 可以在这里引导用户到输入框
                callback?.onVoiceInputError("请使用输入法的语音功能")
            }
            .show()
    }
    
    /**
     * 显示语音输入选项对话框
     */
    private fun showVoiceInputOptions() {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        
        // 系统语音输入
        if (hasSystemVoiceInput()) {
            options.add("系统语音输入")
            actions.add { trySystemVoiceInput() }
        }
        
        // 输入法语音输入
        options.add("输入法语音输入")
        actions.add { 
            showIMEVoiceInputGuide("输入法")
        }
        
        // 语音设置
        options.add("打开语音设置")
        actions.add { openVoiceSettings() }
        
        // 手动输入
        options.add("手动输入")
        actions.add { 
            callback?.onVoiceInputError("请使用手动输入")
        }
        
        AlertDialog.Builder(activity)
            .setTitle("选择语音输入方式")
            .setMessage("未找到可用的语音识别服务，请选择其他方式：")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("取消") { _, _ ->
                callback?.onVoiceInputCancelled()
            }
            .show()
    }
    
    /**
     * 检查是否有系统语音输入
     */
    private fun hasSystemVoiceInput(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return activities.isNotEmpty()
    }
    
    /**
     * 打开语音设置
     */
    private fun openVoiceSettings() {
        try {
            val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(activity, "无法打开设置", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 尝试多个Intent
     */
    private fun tryIntents(intents: List<Intent>, requestCode: Int): Boolean {
        for (intent in intents) {
            try {
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivityForResult(intent, requestCode)
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Intent启动失败: ${intent.component?.className}")
            }
        }
        return false
    }
    
    /**
     * 检查包是否已安装
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 处理Activity结果
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SYSTEM_VOICE_REQUEST_CODE,
            BRAND_VOICE_REQUEST_CODE,
            IME_VOICE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!results.isNullOrEmpty()) {
                        val recognizedText = results[0]
                        Log.d(TAG, "语音识别成功: $recognizedText")
                        callback?.onVoiceInputResult(recognizedText)
                    } else {
                        callback?.onVoiceInputError("未识别到语音")
                    }
                } else {
                    callback?.onVoiceInputCancelled()
                }
            }
        }
    }
}
