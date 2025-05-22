package com.example.aifloatingball

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class VoiceRecognitionActivity : Activity() {
    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
        private const val TAG = "VoiceRecognitionActivity"
    }

    private var animatorSet: AnimatorSet? = null
    private val handler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var recognizedText = ""
    
    // 界面元素
    private lateinit var micContainer: MaterialCardView
    private lateinit var micIcon: ImageView
    private lateinit var listeningText: TextView
    private lateinit var waveformView: View
    private lateinit var recognizedTextView: TextView
    private lateinit var doneButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recognition)
        
        // 初始化视图
        initializeViews()
        
        // 设置点击事件
        setupClickListeners()
        
        // 设置窗口属性
        setupWindowAttributes()
        
        // 启动动画
        startWaveAnimation()
        
        // 启动语音识别
        startVoiceRecognition()
    }

    private fun initializeViews() {
        micContainer = findViewById(R.id.micContainer)
        micIcon = findViewById(R.id.micIcon)
        listeningText = findViewById(R.id.listeningText)
        waveformView = findViewById(R.id.waveformView)
        recognizedTextView = findViewById(R.id.recognizedText)
        doneButton = findViewById(R.id.doneButton)
    }
    
    private fun setupClickListeners() {
        // 设置说完了按钮点击事件
        doneButton.setOnClickListener {
            finishRecognition()
        }
        
        // 设置麦克风点击事件
        micContainer.setOnClickListener {
            toggleListening()
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

    private fun startWaveAnimation() {
        try {
            // 创建波形动画
            val waveformAnimator = ObjectAnimator.ofFloat(waveformView, "amplitude", 0f, 1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            
            // 创建麦克风脉动动画
            val scaleXAnimator = ObjectAnimator.ofFloat(micContainer, "scaleX", 1f, 1.1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            
            val scaleYAnimator = ObjectAnimator.ofFloat(micContainer, "scaleY", 1f, 1.1f).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
            }
            
            // 创建组合动画
            animatorSet = AnimatorSet().apply {
                playTogether(waveformAnimator, scaleXAnimator, scaleYAnimator)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVoiceRecognition() {
        // 确保前一个识别器被释放
        releaseSpeechRecognizer()
        
        // 创建新的语音识别器
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
        
        // 准备识别意图
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        // 开始识别
        try {
            isListening = true
            updateListeningState(true)
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            showError("无法启动语音识别")
        }
    }
    
    private fun toggleListening() {
        if (isListening) {
            // 如果正在监听，停止监听
            stopListening()
        } else {
            // 如果没有监听，开始监听
            startVoiceRecognition()
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
            animatorSet?.resume()
        } else {
            listeningText.text = "识别已暂停"
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            animatorSet?.pause()
        }
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                handler.post {
                    listeningText.text = "请开始说话"
                }
            }

            override fun onBeginningOfSpeech() {
                handler.post {
                    listeningText.text = "正在聆听..."
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // 根据音量大小更新波形动画
                handler.post {
                    updateWaveformAmplitude(rmsdB)
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                handler.post {
                    listeningText.text = "正在处理..."
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
    
    private fun updateWaveformAmplitude(rmsdB: Float) {
        // 将音量转换为振幅值 (0.0-1.0)
        val amplitude = (rmsdB + 100) / 100f  // 音量范围通常在-100到0之间
        
        // 更新波形视图（需要在WaveformView中实现setAmplitude方法）
        if (waveformView is com.example.aifloatingball.ui.WaveformView) {
            (waveformView as com.example.aifloatingball.ui.WaveformView).setAmplitude(amplitude.coerceIn(0f, 1f))
        }
    }
    
    private fun processRecognitionResults(results: Bundle?) {
        try {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            recognizedText = text
            
            // 显示识别结果
            recognizedTextView.text = text
            recognizedTextView.visibility = View.VISIBLE
            
            // 更新UI状态
                listeningText.text = "识别完成，点击确认或继续说话"
                
                // 自动发送结果并结束活动
                handler.postDelayed({
                    finishRecognition()
                }, 1000) // 延迟1秒后自动发送结果
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理识别结果失败: ${e.message}")
            showError("处理识别结果失败")
        }
    }
    
    private fun processPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            
            // 显示部分识别结果
            if (text.isNotEmpty()) {
                recognizedTextView.text = text
                recognizedTextView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun handleRecognitionError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> {
                listeningText.text = "未能识别，请重试"
                recognizedTextView.text = "请清晰地说出您要搜索的内容"
                recognizedTextView.visibility = View.VISIBLE
            }
            SpeechRecognizer.ERROR_NETWORK -> {
                listeningText.text = "网络错误"
                showError("网络连接失败，请检查网络")
            }
            else -> {
                listeningText.text = "识别错误，请重试"
                showError("语音识别失败 (错误码: $error)")
            }
        }
        stopListening()
    }
    
    private fun finishRecognition() {
        try {
        if (recognizedText.isNotEmpty()) {
            // 发送广播通知悬浮球服务
            val intent = Intent("com.example.aifloatingball.ACTION_VOICE_RESULT")
            intent.putExtra("result", recognizedText)
            sendBroadcast(intent)
        
                // 添加延迟以确保广播被处理
                handler.postDelayed({
                    finish()
                }, 200)
            } else {
                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "结束识别失败: ${e.message}")
        finish()
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
        
        // 释放动画资源
        animatorSet?.apply {
            cancel()
            removeAllListeners()
        }
        
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