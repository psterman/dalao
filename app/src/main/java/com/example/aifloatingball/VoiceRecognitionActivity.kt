package com.example.aifloatingball

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.service.SimpleModeService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class VoiceRecognitionActivity : Activity() {
    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
        private const val SYSTEM_VOICE_REQUEST_CODE = 1002
        private const val TAG = "VoiceRecognitionActivity"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var recognizedText = "" // 已确认的最终文本
    private var currentPartialText = "" // 当前部分识别结果（临时显示）
    private lateinit var settingsManager: SettingsManager
    
    // 界面元素
    private lateinit var micContainer: MaterialCardView
    private lateinit var micIcon: ImageView
    private lateinit var listeningText: TextView
    private lateinit var recognizedTextView: EditText
    private lateinit var doneButton: MaterialButton
    private lateinit var zoomInButton: ImageButton
    private lateinit var zoomOutButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recognition)
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化视图
        initializeViews()
        
        // 设置点击事件
        setupClickListeners()
        
        // 设置窗口属性
        setupWindowAttributes()
        
        // 启动前清空状态
        recognizedText = ""
        currentPartialText = ""
        recognizedTextView.setText("")
        
        // 检查权限并启动语音识别
        if (hasAudioPermission()) {
        startVoiceRecognition()
        } else {
            requestAudioPermission()
        }
    }

    private fun initializeViews() {
        micContainer = findViewById(R.id.micContainer)
        micIcon = findViewById(R.id.micIcon)
        listeningText = findViewById(R.id.listeningText)
        recognizedTextView = findViewById(R.id.recognizedText)
        doneButton = findViewById(R.id.doneButton)
        zoomInButton = findViewById(R.id.zoomInButton)
        zoomOutButton = findViewById(R.id.zoomOutButton)

        // Load the saved font size, or use the default
        val savedSize = settingsManager.getVoiceInputTextSize()
        recognizedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedSize)
    }
    
    private fun setupClickListeners() {
        // 设置说完了按钮点击事件
        doneButton.setOnClickListener {
            Log.d(TAG, "用户点击了'说完了'按钮")
            finishRecognition()
        }
        
        // 设置麦克风点击事件
        micContainer.setOnClickListener {
            toggleListening()
        }

        zoomInButton.setOnClickListener {
            adjustTextSize(2f) // Increase by 2sp
        }

        zoomOutButton.setOnClickListener {
            adjustTextSize(-2f) // Decrease by 2sp
        }
    }
    
    private fun setupWindowAttributes() {
        // 设置窗口背景半透明
        window.attributes = window.attributes.apply {
            dimAmount = 0.3f
        }
        
        // 设置窗口动画
        overridePendingTransition(R.anim.slide_up, 0)
    }

    private fun startVoiceRecognition() {
        // 首先检查设备是否支持语音识别
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // 如果不支持，尝试使用系统语音输入Intent
            trySystemVoiceInput()
            return
        }

        // 确保前一个识别器被释放
        releaseSpeechRecognizer()
        
        // 创建新的语音识别器
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
        
        // 准备识别意图 - 优化参数以提高识别效果和实时性
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // 使用自由格式语言模型，识别效果更好
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // 设置语言为中文
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            
            // 启用部分结果，实现实时流式显示
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // 增加最大结果数，可能提高部分结果更新频率
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            
            // 尝试优化识别参数（某些设备可能不支持，但不影响使用）
            try {
                // 设置更短的静音分段时间，让识别更频繁地返回结果
                putExtra("android.speech.extra.SEGMENTATION_SILENCE_LENGTH_MS", 300) // 300ms静音后分段
            } catch (e: Exception) {
                Log.d(TAG, "设备不支持SEGMENTATION_SILENCE_LENGTH_MS参数")
            }
            
            // 尝试设置识别超时时间（某些设备支持）
            try {
                putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2000) // 2秒静音后完成
                putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500) // 1.5秒可能完成
            } catch (e: Exception) {
                Log.d(TAG, "设备不支持超时参数")
            }
        }
        
        // 开始识别
        try {
            isListening = true
            updateListeningState(true)
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer 启动失败: ${e.message}")
            // 如果SpeechRecognizer失败，尝试系统语音输入
            trySystemVoiceInput()
        }
    }
    
    private fun toggleListening() {
        if (isListening) {
            // 如果正在监听，停止监听
            stopListening()
        } else {
            // 如果没有监听，开始监听
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
            startVoiceRecognition()
            } else {
                // 如果SpeechRecognizer不可用，尝试系统语音输入
                trySystemVoiceInput()
            }
        }
    }
    
    private fun stopListening() {
        isListening = false
        updateListeningState(false)
        speechRecognizer?.stopListening()
    }
    
    private fun updateListeningState(listening: Boolean) {
        isListening = listening
        
        // 更新UI状态
        if (listening) {
            listeningText.text = "正在倾听"
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            listeningText.text = "识别已暂停"
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                handler.post {
                    listeningText.text = "请开始说话"
                    // 清空部分识别结果，准备新的识别
                    currentPartialText = ""
                }
            }

            override fun onBeginningOfSpeech() {
                handler.post {
                    listeningText.text = "正在聆听..."
                    // 清空部分识别结果，开始新的识别
                    currentPartialText = ""
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 根据音量大小更新波形动画
                handler.post {
                    updateMicAnimation(rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                handler.post {
                    listeningText.text = "正在处理..."
                    // 清空部分识别结果，等待最终结果
                    currentPartialText = ""
                    // 如果还有已确认的文本，只显示已确认的文本
                    if (recognizedText.isNotEmpty()) {
                        recognizedTextView.setText(recognizedText)
                        recognizedTextView.setSelection(recognizedText.length)
                    }
                }
            }

            override fun onError(error: Int) {
                handler.post {
                    handleRecognitionError(error)
                }
            }

            override fun onResults(results: Bundle?) {
                handler.post {
                    processRecognitionResults(results)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                handler.post {
                    processPartialResults(partialResults)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
    
    private fun updateMicAnimation(rmsdB: Float) {
        val minScale = 1.0f
        val maxScale = 1.2f
        // Clamp and normalize the rmsdB value. Range from 0 to 10 is a reasonable expectation for speech.
        val normalizedRms = (rmsdB).coerceIn(0f, 10f) / 10f 
        val targetScale = minScale + (maxScale - minScale) * normalizedRms

        // Animate the mic container scale
        micContainer.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(150) // Smooth transition
            .start()
    }
    
    private fun adjustTextSize(adjustment: Float) {
        val currentSizeSp = recognizedTextView.textSize / resources.displayMetrics.scaledDensity
        val newSizeSp = currentSizeSp + adjustment
        // Optional: Add min/max size constraints if needed
        // val newSizeClamped = newSizeSp.coerceIn(12f, 64f)
        recognizedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp)

        // Save the new font size
        settingsManager.setVoiceInputTextSize(newSizeSp)
    }
    
    private fun processRecognitionResults(results: Bundle?) {
        try {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val newFinalText = matches[0]
                
                // 清除当前的部分识别结果
                currentPartialText = ""
                
                // 智能合并最终结果，避免重复
                recognizedText = if (recognizedText.isEmpty()) {
                    newFinalText
                } else {
                    // 检查新文本是否已经包含在已确认文本中
                    if (recognizedText.contains(newFinalText)) {
                        // 如果已包含，检查是否有新增内容
                        val newPart = newFinalText.replace(recognizedText, "").trim()
                        if (newPart.isNotEmpty()) {
                            "$recognizedText $newPart"
                        } else {
                            recognizedText // 没有新增内容
                        }
                    } else if (newFinalText.contains(recognizedText)) {
                        // 如果新文本包含已确认文本，使用新文本（可能更完整）
                        newFinalText
                    } else {
                        // 完全不同的文本，追加
                        "$recognizedText $newFinalText"
                    }
                }
            
                // 使用累积的文本更新显示（只显示已确认的文本）
                recognizedTextView.setText(recognizedText)
                recognizedTextView.setSelection(recognizedText.length)
                
                // 提示用户并立即重启识别，以实现连续听写
                listeningText.text = "请继续说话或点击'说完了'开始搜索"
                
                // 确保"说完了"按钮可用
                doneButton.isEnabled = true
                Log.d(TAG, "✓ 识别成功，当前累积文本: '$recognizedText'")
                
                // 立即重启识别，实现无缝连续识别
                handler.postDelayed({
                    if (isListening) { // 防止用户手动停止后重启
                        startVoiceRecognition()
                    }
                }, 50) // 进一步缩短到50ms，实现更快的连续识别
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理识别结果失败: ${e.message}")
            showError("处理识别结果失败")
        }
    }
    
    private fun processPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partialText = matches[0]
            
            // 更新当前部分识别结果
            if (partialText.isNotEmpty()) {
                // 检查部分结果是否有更新（避免重复更新相同内容）
                if (partialText != currentPartialText) {
                    currentPartialText = partialText
                    
                    // 智能合并：已确认的文本 + 当前部分识别结果
                    val displayText = if (recognizedText.isEmpty()) {
                        // 没有已确认文本，直接显示部分结果
                        partialText
                    } else {
                        // 有已确认文本，需要智能合并
                        if (partialText.startsWith(recognizedText)) {
                            // 部分结果以已确认文本开头，直接使用部分结果（更完整）
                            partialText
                        } else if (recognizedText.contains(partialText)) {
                            // 部分结果已包含在已确认文本中，只显示已确认文本
                            recognizedText
                        } else {
                            // 部分结果是新增内容，追加显示
                            "$recognizedText $partialText"
                        }
                    }
                    
                    // 实时更新显示文本（立即在主线程更新，实现流式显示）
                    recognizedTextView.setText(displayText)
                    recognizedTextView.setSelection(displayText.length)
                    
                    Log.d(TAG, "↻ 部分结果更新: '$partialText' → 显示: '$displayText'")
                }
            } else {
                // 部分结果为空，清空当前部分结果
                if (currentPartialText.isNotEmpty()) {
                    currentPartialText = ""
                    // 只显示已确认的文本
                    if (recognizedText.isNotEmpty()) {
                        recognizedTextView.setText(recognizedText)
                        recognizedTextView.setSelection(recognizedText.length)
                    }
                }
            }
        }
    }
    
    private fun handleRecognitionError(error: Int) {
        // 对于非致命错误，自动重启监听
        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            
            Log.d(TAG, "Non-critical error ($error), restarting listener.")
            
            // 给出清晰的反馈，但绝不覆盖用户的输入
            listeningText.text = "请再说一遍"
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))

            // 在安全延迟后重启识别过程
            handler.postDelayed({
                if (isListening) {
                    startVoiceRecognition()
                }
            }, 500) 

            return
        }

        // 对于其他致命错误，向用户显示消息并关闭活动
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "音频错误"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误"
            else -> "识别错误 (代码: $error)"
        }
        
        Log.e(TAG, "Fatal Speech Recognition Error: $errorMessage")
        showError(errorMessage)
    }

    private fun trySystemVoiceInput() {
        Log.d(TAG, "尝试使用系统语音输入Intent")
        
        // 创建系统语音识别Intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            // 检查是否有应用能处理语音识别Intent
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (activities.isNotEmpty()) {
                // 有可用的语音识别应用，启动系统语音输入
                listeningText.text = "启动系统语音输入..."
                startActivityForResult(intent, SYSTEM_VOICE_REQUEST_CODE)
            } else {
                // 没有可用的语音识别应用
                showVoiceRecognitionNotAvailableDialog()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动系统语音输入失败: ${e.message}")
            showVoiceRecognitionNotAvailableDialog()
        }
    }

    private fun showVoiceRecognitionNotAvailableDialog() {
        // 不再显示弹窗，直接切换到手动输入模式
        Log.d(TAG, "语音识别不可用，自动切换到手动输入模式")

        // 允许用户手动输入文本
        listeningText.text = "请在下方输入文本，然后点击'说完了'"
        micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        recognizedTextView.requestFocus()

        // 显示键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(recognizedTextView, InputMethodManager.SHOW_IMPLICIT)

        // 确保"说完了"按钮可用
        doneButton.isEnabled = true
        Log.d(TAG, "已切换到手动输入模式")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SYSTEM_VOICE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    val recognizedSystemText = results[0]
                    Log.d(TAG, "系统语音输入结果: $recognizedSystemText")
                    
                    // 将系统语音识别结果添加到现有文本
                    recognizedText = if (recognizedText.isEmpty()) {
                        recognizedSystemText
                    } else {
                        "$recognizedText $recognizedSystemText"
                    }
                    
                    recognizedTextView.setText(recognizedText)
                    recognizedTextView.setSelection(recognizedText.length)
                    listeningText.text = "语音输入完成，点击'说完了'开始搜索"
                    
                    // 显示绿色状态表示成功
                    micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    
                    // 更新"说完了"按钮的可见性和状态
                    doneButton.isEnabled = true
                    Log.d(TAG, "系统语音输入成功，当前文本: '$recognizedText'")
                } else {
                    listeningText.text = "未识别到语音，请重试"
                    micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                }
            } else {
                listeningText.text = "语音输入已取消"
                micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start recognition
                startVoiceRecognition()
            } else {
                // Permission denied
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), VOICE_RECOGNITION_REQUEST_CODE)
    }
    
    private fun finishRecognition() {
        val finalText = recognizedTextView.text.toString().trim()
        Log.d(TAG, "finishRecognition 被调用，文本长度: ${finalText.length}，内容: '$finalText'")
        
        if (finalText.isNotEmpty()) {
            Log.d(TAG, "识别完成，文本: $finalText")
            // 发送广播，命令SimpleModeService启动搜索并自我销毁
            triggerSearchAndDestroySimpleMode(finalText)
        } else {
            Log.d(TAG, "识别完成，但无文本。")
            Toast.makeText(this, "没有识别到文本", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun triggerSearchAndDestroySimpleMode(query: String) {
        try {
            Log.d(TAG, "准备启动语音搜索，查询: '$query'")

            // 启动DualFloatingWebViewService，默认加载百度AI对话界面（文心一言）和Google AI对话界面（Gemini）
            val serviceIntent = Intent(this, com.example.aifloatingball.service.DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("voice_search_mode", true) // 标记为语音搜索模式
                putExtra("voice_search_engines", arrayOf("百度AI", "Google AI")) // 默认两个AI对话界面：百度AI（文心一言）和Google AI（Gemini）
                putExtra("window_count", 2) // 设置窗口数量为2
                putExtra("use_card_view_mode", false) // 使用横向拖动模式，方便查看多个网页
                putExtra("search_source", "语音输入")
            }
            startService(serviceIntent)
            Log.d(TAG, "已启动DualFloatingWebViewService进行语音搜索")
            
        } catch (e: Exception) {
            Log.e(TAG, "启动语音搜索失败: ${e.message}", e)
            Toast.makeText(this, "启动搜索失败", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopSimpleModeService() {
        try {
            // 发送广播通知SimpleModeService关闭
            val closeIntent = Intent("com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE")
            sendBroadcast(closeIntent)
            Log.d(TAG, "已发送关闭简易模式广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送关闭广播失败: ${e.message}")
        }
    }
    
    private fun showError(message: String) {
        listeningText.text = message
        // 3秒后自动关闭
        handler.postDelayed({ finish() }, 3000)
    }
    
    private fun releaseSpeechRecognizer() {
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 释放语音识别器
        releaseSpeechRecognizer()
        
        // 移除所有延迟任务
        handler.removeCallbacksAndMessages(null)
    }
    
    override fun finish() {
        super.finish()
        // 设置退出动画
        overridePendingTransition(0, R.anim.slide_down)
    }
}