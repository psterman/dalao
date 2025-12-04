package com.example.aifloatingball.voice

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.aifloatingball.R

/**
 * 语音识别服务检测结果展示 Activity
 */
class SpeechRecognitionDetectionActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SpeechRecognitionDetectionActivity"
    }
    
    private lateinit var detector: SpeechRecognitionServiceDetector
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var resultScrollView: ScrollView
    private lateinit var resultText: TextView
    private lateinit var closeButton: Button
    private lateinit var retryButton: Button
    
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speech_recognition_detection)
        
        detector = SpeechRecognitionServiceDetector(this)
        
        initializeViews()
        setupClickListeners()
        
        // 开始检测
        startDetection()
    }
    
    private fun initializeViews() {
        progressBar = findViewById(R.id.detectionProgressBar)
        statusText = findViewById(R.id.detectionStatusText)
        resultScrollView = findViewById(R.id.detectionResultScrollView)
        resultText = findViewById(R.id.detectionResultText)
        closeButton = findViewById(R.id.detectionCloseButton)
        retryButton = findViewById(R.id.detectionRetryButton)
    }
    
    private fun setupClickListeners() {
        closeButton.setOnClickListener {
            finish()
        }
        
        retryButton.setOnClickListener {
            startDetection()
        }
    }
    
    private fun startDetection() {
        // 重置UI
        progressBar.visibility = View.VISIBLE
        statusText.text = "正在检测语音识别服务..."
        resultText.text = ""
        resultScrollView.visibility = View.GONE
        retryButton.isEnabled = false
        closeButton.isEnabled = false
        
        // 在后台线程执行检测
        Thread {
            try {
                val result = detector.detect()
                
                // 在主线程更新UI
                handler.post {
                    displayResult(result)
                }
            } catch (e: Exception) {
                handler.post {
                    displayError(e.message ?: "检测过程中发生未知错误")
                }
            }
        }.start()
    }
    
    private fun displayResult(result: SpeechRecognitionServiceDetector.DetectionResult) {
        progressBar.visibility = View.GONE
        resultScrollView.visibility = View.VISIBLE
        retryButton.isEnabled = true
        closeButton.isEnabled = true
        
        val deviceInfo = detector.getDeviceInfo()
        
        val resultTextBuilder = StringBuilder()
        resultTextBuilder.append("📱 设备信息\n")
        resultTextBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        resultTextBuilder.append("$deviceInfo\n")
        resultTextBuilder.append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n")
        
        resultTextBuilder.append("🔍 检测结果\n")
        resultTextBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        resultTextBuilder.append("检测次数: ${result.detectionAttempts} 次\n")
        resultTextBuilder.append("成功次数: ${result.successCount} 次\n")
        resultTextBuilder.append("失败次数: ${result.failureCount} 次\n")
        resultTextBuilder.append("稳定性: ${if (result.isStable) "✅ 稳定" else "⚠️ 不稳定"}\n\n")
        
        resultTextBuilder.append("📊 服务状态\n")
        resultTextBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        resultTextBuilder.append("isRecognitionAvailable: ${if (result.isRecognitionAvailable) "✅ 是" else "❌ 否"}\n")
        resultTextBuilder.append("服务可绑定: ${if (result.isServiceBindable) "✅ 是" else "❌ 否"}\n")
        
        if (result.servicePackageName != null) {
            resultTextBuilder.append("服务包名: ${result.servicePackageName}\n")
        } else {
            resultTextBuilder.append("服务包名: 未检测到\n")
        }
        
        resultTextBuilder.append("服务类型: ${getServiceTypeText(result.serviceType)}\n\n")
        
        resultTextBuilder.append("💡 推荐操作\n")
        resultTextBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        resultTextBuilder.append("${getRecommendedActionText(result.recommendedAction)}\n\n")
        
        if (result.errorMessages.isNotEmpty()) {
            resultTextBuilder.append("⚠️ 错误信息\n")
            resultTextBuilder.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            result.errorMessages.forEachIndexed { index, error ->
                resultTextBuilder.append("${index + 1}. $error\n")
            }
        }
        
        resultText.text = resultTextBuilder.toString()
        
        // 更新状态文本
        val statusMessage = when {
            !result.isRecognitionAvailable -> "❌ 系统未提供语音识别服务"
            !result.isServiceBindable || !result.isStable -> "⚠️ 服务不稳定，建议使用云端 SDK"
            else -> "✅ 系统语音识别服务可用"
        }
        statusText.text = statusMessage
    }
    
    private fun displayError(errorMessage: String) {
        progressBar.visibility = View.GONE
        resultScrollView.visibility = View.VISIBLE
        retryButton.isEnabled = true
        closeButton.isEnabled = true
        
        statusText.text = "❌ 检测失败"
        resultText.text = "错误: $errorMessage\n\n请点击\"重新检测\"按钮重试。"
    }
    
    private fun getServiceTypeText(type: SpeechRecognitionServiceDetector.ServiceType): String {
        return when (type) {
            SpeechRecognitionServiceDetector.ServiceType.GOOGLE -> "Google 服务"
            SpeechRecognitionServiceDetector.ServiceType.MANUFACTURER -> "厂商服务"
            SpeechRecognitionServiceDetector.ServiceType.UNKNOWN -> "未知服务"
            SpeechRecognitionServiceDetector.ServiceType.NONE -> "无服务"
        }
    }
    
    private fun getRecommendedActionText(action: SpeechRecognitionServiceDetector.RecommendedAction): String {
        return when (action) {
            SpeechRecognitionServiceDetector.RecommendedAction.USE_SYSTEM_SR -> 
                "✅ 使用系统 SpeechRecognizer\n系统语音识别服务可用且稳定，建议使用系统服务。"
            SpeechRecognitionServiceDetector.RecommendedAction.USE_CLOUD_SDK -> 
                "☁️ 使用云端 SDK（Aivs、科大讯飞等）\n系统未提供语音识别服务或服务不稳定，建议使用云端 SDK。"
            SpeechRecognitionServiceDetector.RecommendedAction.USE_SYSTEM_INTENT -> 
                "📱 使用系统语音输入 Intent\n通过系统 Intent 调用语音输入。"
            SpeechRecognitionServiceDetector.RecommendedAction.MANUAL_INPUT -> 
                "⌨️ 手动输入\n语音识别不可用，请使用手动输入。"
        }
    }
}

