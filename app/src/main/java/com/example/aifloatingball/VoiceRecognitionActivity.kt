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
import android.speech.RecognizerIntent
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.*

class VoiceRecognitionActivity : Activity() {
    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
    }

    private var animatorSet: AnimatorSet? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private lateinit var voiceWaveImageView: ImageView
    private lateinit var voiceProgressIndicator: CircularProgressIndicator
    private lateinit var voiceHintTextView: TextView
    private lateinit var voiceStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recognition)
        
        // 初始化视图
        initializeViews()
        
        // 设置窗口透明度动画
        window.attributes = window.attributes.apply {
            dimAmount = 0.4f
        }
        
        // 启动动画
        startWaveAnimation()
        
        // 启动语音识别
        startVoiceRecognition()
    }

    private fun initializeViews() {
        voiceWaveImageView = findViewById(R.id.voiceWaveImageView)
        voiceProgressIndicator = findViewById(R.id.voiceProgressIndicator)
        voiceHintTextView = findViewById(R.id.voiceHintTextView)
        voiceStatusTextView = findViewById(R.id.voiceStatusTextView)
    }

    private fun startWaveAnimation() {
        // 启动AnimatedVectorDrawable动画
        val drawable = voiceWaveImageView.drawable
        if (drawable is AnimatedVectorDrawable) {
            drawable.start()
        }
        
        // 创建脉动动画
        createPulsingAnimation()
    }
    
    private fun createPulsingAnimation() {
        // 创建进度条动画
        val progressAnimator = ObjectAnimator.ofInt(voiceProgressIndicator, "progress", 0, 100).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
        }
        
        // 创建组合动画
        animatorSet = AnimatorSet().apply {
            play(progressAnimator)
            start()
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出您要搜索的内容")
        }

        try {
            startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE)
        } catch (e: Exception) {
            showError("无法启动语音识别")
            handler.postDelayed({ finish() }, 1500)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!results.isNullOrEmpty()) {
                        val recognizedText = results[0]
                        voiceHintTextView.text = recognizedText
                        voiceStatusTextView.text = "识别成功"
                        
                        // 发送广播给 FloatingWindowService
                        val resultIntent = Intent("com.example.aifloatingball.ACTION_VOICE_RESULT")
                        resultIntent.putExtra("result", recognizedText)
                        sendBroadcast(resultIntent)
                        
                        // 添加延迟以显示识别结果
                        handler.postDelayed({
                            finish()
                        }, 1000)
                    }
                }
                RESULT_CANCELED -> {
                    showCancelled()
                }
                else -> {
                    showError("识别失败")
                }
            }
        }
    }
    
    private fun showCancelled() {
        voiceHintTextView.text = "已取消"
        voiceStatusTextView.text = "操作已取消"
        voiceProgressIndicator.isIndeterminate = false
        voiceProgressIndicator.progress = 0
        animatorSet?.cancel()
        
        handler.postDelayed({
            finish()
        }, 1000)
    }
    
    private fun showError(message: String) {
        voiceHintTextView.text = message
        voiceStatusTextView.text = "出现错误，请重试"
        voiceProgressIndicator.isIndeterminate = false
        voiceProgressIndicator.progress = 0
        animatorSet?.cancel()
        
        // 创建错误状态的动画
        val errorAnimator = ObjectAnimator.ofArgb(
            voiceHintTextView, 
            "textColor", 
            0xFFFFFFFF.toInt(), 
            0xFFFF4444.toInt()
        ).apply {
            duration = 300
            repeatCount = 3
            repeatMode = ValueAnimator.REVERSE
        }
        
        errorAnimator.start()
        
        handler.postDelayed({
            finish()
        }, 1500)
    }

    override fun finish() {
        // 清理动画资源
        animatorSet?.cancel()
        animatorSet = null
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.voice_dialog_exit)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.voice_dialog_exit)
        }
        
        super.finish()
    }
} 