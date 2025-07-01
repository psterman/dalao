package com.example.aifloatingball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import com.example.aifloatingball.R
import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.app.ActivityCompat

class SimpleModeService : Service() {

    private lateinit var windowManager: WindowManager
    private var simpleModeView: View? = null
    private lateinit var inputEditText: EditText

    // Voice recognition request code
    private val VOICE_RECOGNITION_REQUEST_CODE = 1234

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showSimpleModeView()
    }

    override fun onDestroy() {
        super.onDestroy()
        simpleModeView?.let {
            windowManager.removeView(it)
        }
    }

    private fun showSimpleModeView() {
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        simpleModeView = layoutInflater.inflate(R.layout.simple_mode_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER

        simpleModeView?.let { view ->
            inputEditText = view.findViewById(R.id.simple_mode_input)
            val button1 = view.findViewById<Button>(R.id.simple_mode_ai_button_1)
            val button2 = view.findViewById<Button>(R.id.simple_mode_ai_button_2)
            val button3 = view.findViewById<Button>(R.id.simple_mode_ai_button_3)

            // Setup listeners
            setupVoiceInput()
            button1.setOnClickListener { performSearch("deepseek") }
            button2.setOnClickListener { performSearch("chatgpt") }
            button3.setOnClickListener { performSearch("kimi") }

            windowManager.addView(view, params)
        }
    }

    private fun setupVoiceInput() {
        inputEditText.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val drawableEnd = inputEditText.compoundDrawables[2]
                if (drawableEnd != null && event.rawX >= (inputEditText.right - drawableEnd.bounds.width())) {
                    startVoiceRecognition()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
        }
        try {
            // Since we are in a service, we need to start the activity with a new task flag.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // A trick to get the result back to the service
            val proxyIntent = Intent(this, VoiceRecognitionProxyActivity::class.java)
            proxyIntent.putExtra("voice_intent", intent)
            proxyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(proxyIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "您的设备不支持语音识别", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(engineKey: String) {
        val query = inputEditText.text.toString()
        if (query.isBlank()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            putExtra("query", query)
            putExtra("engine", engineKey)
            putExtra("window_count", 1) // Simple mode will only open one window
            putExtra("source", "简易模式")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)

        // Hide the simple mode view after search
        simpleModeView?.visibility = View.GONE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SHOW_VIEW") {
            simpleModeView?.visibility = View.VISIBLE
        }

        if (intent?.hasExtra("voice_result") == true) {
            val result = intent.getStringArrayListExtra("voice_result")
            if (!result.isNullOrEmpty()) {
                inputEditText.setText(result[0])
            }
        }
        return START_STICKY
    }
}

// A helper activity to capture voice recognition results and send them back to the service.
class VoiceRecognitionProxyActivity : Activity() {
    private val VOICE_RECOGNITION_REQUEST_CODE = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val voiceIntent = intent.getParcelableExtra<Intent>("voice_intent")
        startActivityForResult(voiceIntent, VOICE_RECOGNITION_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val serviceIntent = Intent(this, SimpleModeService::class.java).apply {
                putExtra("voice_result", results)
            }
            startService(serviceIntent)
        }
        finish() // Close the proxy activity
    }
} 