package com.example.aifloatingball

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.util.*

class VoiceRecognitionActivity : Activity() {
    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
    }

    private lateinit var voiceWaveImageView: ImageView
    private lateinit var voiceHintTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.voice_recognition_dialog)
        
        // 初始化视图
        voiceWaveImageView = findViewById(R.id.voiceWaveImageView)
        voiceHintTextView = findViewById(R.id.voiceHintTextView)
        
        // 启动动画
        startWaveAnimation()
        
        // 启动语音识别
        startVoiceRecognition()
    }

    private fun startWaveAnimation() {
        val drawable = voiceWaveImageView.drawable
        if (drawable is AnimatedVectorDrawable) {
            drawable.start()
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
            Toast.makeText(this, "无法启动语音识别", Toast.LENGTH_SHORT).show()
            finish()
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
                        
                        // 发送广播给 FloatingWindowService
                        val resultIntent = Intent("com.example.aifloatingball.ACTION_VOICE_RESULT")
                        resultIntent.putExtra("result", recognizedText)
                        sendBroadcast(resultIntent)
                        
                        // 添加延迟以显示识别结果
                        voiceHintTextView.postDelayed({
                            finish()
                        }, 1000)
                    }
                }
                RESULT_CANCELED -> {
                    voiceHintTextView.text = "已取消"
                    voiceHintTextView.postDelayed({
                        finish()
                    }, 1000)
                }
                else -> {
                    voiceHintTextView.text = "识别失败"
                    voiceHintTextView.postDelayed({
                        finish()
                    }, 1000)
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        // 添加退出动画
        overridePendingTransition(0, R.anim.voice_dialog_exit)
    }
} 