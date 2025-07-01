package com.example.aifloatingball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine
import androidx.appcompat.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.widget.TextView

class SimpleModeService : Service() {

    private lateinit var windowManager: WindowManager
    private var simpleModeView: View? = null
    private lateinit var inputEditText: EditText
    private lateinit var settingsManager: SettingsManager

    private var engineSlot1: String? = null
    private var engineSlot2: String? = null
    private var engineSlot3: String? = null

    private lateinit var button1: Button
    private lateinit var button2: Button
    private lateinit var button3: Button
    private var layoutParams: WindowManager.LayoutParams? = null

    // Voice recognition request code
    private val VOICE_RECOGNITION_REQUEST_CODE = 1234

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        settingsManager = SettingsManager.getInstance(this)
        showSimpleModeView()
    }

    override fun onDestroy() {
        super.onDestroy()
        simpleModeView?.let {
            windowManager.removeView(it)
        }
    }

    private fun showSimpleModeView() {
        if (simpleModeView == null) {
            val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            simpleModeView = layoutInflater.inflate(R.layout.simple_mode_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
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
            layoutParams = params

            inputEditText = simpleModeView!!.findViewById(R.id.simple_mode_input)
            button1 = simpleModeView!!.findViewById(R.id.simple_mode_ai_button_1)
            button2 = simpleModeView!!.findViewById(R.id.simple_mode_ai_button_2)
            button3 = simpleModeView!!.findViewById(R.id.simple_mode_ai_button_3)

            initializeEngineSlots()

            button1.setOnClickListener {
                showAiEngineSelectionDialog { engineName ->
                    engineSlot1 = engineName
                    button1.text = engineName
                }
            }
            button2.setOnClickListener {
                showAiEngineSelectionDialog { engineName ->
                    engineSlot2 = engineName
                    button2.text = engineName
                }
            }
            button3.setOnClickListener {
                showAiEngineSelectionDialog { engineName ->
                    engineSlot3 = engineName
                    button3.text = engineName
                }
            }

            // Set up input field interactions
            inputEditText.setOnClickListener {
                // Make the window focusable when user wants to type
                layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                windowManager.updateViewLayout(simpleModeView, layoutParams)
                
                // Request focus and show keyboard
                inputEditText.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT)
            }

            inputEditText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    // Make the window not focusable again when EditText loses focus
                    layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(simpleModeView, layoutParams)
                }
            }

            // Handle keyboard search/enter key press
            inputEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    performTripleSearch()
                    true
                } else {
                    false
                }
            }

            inputEditText.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val drawableEnd = inputEditText.compoundDrawables[2]
                    if (drawableEnd != null && event.rawX >= (inputEditText.right - drawableEnd.bounds.width())) {
                        performTripleSearch()
                        return@setOnTouchListener true
                    }
                }
                false
            }
            windowManager.addView(simpleModeView, params)
        }
        simpleModeView?.visibility = View.VISIBLE
    }
    
    private fun initializeEngineSlots() {
        val enabledEngines = AISearchEngine.DEFAULT_AI_ENGINES.filter { it.name in settingsManager.getEnabledAIEngines() }
        
        // If no AI engines are enabled, provide default ones for Simple Mode
        val availableEngines = if (enabledEngines.isEmpty()) {
            android.util.Log.d("SimpleModeService", "No AI engines enabled, using default engines for Simple Mode")
            // Use the first few AI engines as defaults
            AISearchEngine.DEFAULT_AI_ENGINES.take(3)
        } else {
            enabledEngines
        }
        
        engineSlot1 = availableEngines.getOrNull(0)?.name ?: "ChatGPT"
        engineSlot2 = availableEngines.getOrNull(1)?.name ?: "Claude"
        engineSlot3 = availableEngines.getOrNull(2)?.name ?: "Gemini"

        android.util.Log.d("SimpleModeService", "Initialized engine slots:")
        android.util.Log.d("SimpleModeService", "Engine 1: $engineSlot1")
        android.util.Log.d("SimpleModeService", "Engine 2: $engineSlot2") 
        android.util.Log.d("SimpleModeService", "Engine 3: $engineSlot3")

        button1.text = engineSlot1
        button2.text = engineSlot2
        button3.text = engineSlot3
    }

    private fun showAiEngineSelectionDialog(onEngineSelected: (String) -> Unit) {
        val enabledEngineNames = settingsManager.getEnabledAIEngines()
        val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES.filter { it.name in enabledEngineNames }

        // If no AI engines are enabled, provide default ones for Simple Mode
        val availableEngines = if (aiEngines.isEmpty()) {
            android.util.Log.d("SimpleModeService", "No AI engines enabled in dialog, using all default engines")
            AISearchEngine.DEFAULT_AI_ENGINES
        } else {
            aiEngines
        }

        if (availableEngines.isEmpty()) {
            Toast.makeText(this, "没有可用的AI搜索引擎", Toast.LENGTH_SHORT).show()
            return
        }

        val engineNames = availableEngines.map { it.name }.toTypedArray()
        val themedContext = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)

        AlertDialog.Builder(themedContext)
            .setTitle("选择AI搜索引擎")
            .setItems(engineNames) { _, which ->
                onEngineSelected(availableEngines[which].name)
            }
            .setNegativeButton("取消", null)
            .create().apply {
                window?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    } else {
                        it.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
                    }
                }
            }.show()
    }

    private fun performTripleSearch() {
        val query = inputEditText.text.toString()
        if (query.isBlank()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate that all engine slots have valid engines
        if (engineSlot1.isNullOrBlank() || engineSlot2.isNullOrBlank() || engineSlot3.isNullOrBlank()) {
             Toast.makeText(this, "引擎配置错误，请重新选择", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide the keyboard before starting search
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(inputEditText.windowToken, 0)

        // Convert AI engine display names to engine keys that the system understands
        val engine1Key = convertDisplayNameToEngineKey(engineSlot1!!)
        val engine2Key = convertDisplayNameToEngineKey(engineSlot2!!)
        val engine3Key = convertDisplayNameToEngineKey(engineSlot3!!)

        // Set the engines for each window position
        settingsManager.setLeftWindowSearchEngine(engine1Key)
        settingsManager.setCenterWindowSearchEngine(engine2Key)
        settingsManager.setRightWindowSearchEngine(engine3Key)

        // Verify settings were saved correctly
        android.util.Log.d("SimpleModeService", "验证设置已保存:")
        android.util.Log.d("SimpleModeService", "左窗口: ${settingsManager.getLeftWindowSearchEngine()}")
        android.util.Log.d("SimpleModeService", "中窗口: ${settingsManager.getCenterWindowSearchEngine()}")  
        android.util.Log.d("SimpleModeService", "右窗口: ${settingsManager.getRightWindowSearchEngine()}")

        // Log for debugging
        android.util.Log.d("SimpleModeService", "Starting search with:")
        android.util.Log.d("SimpleModeService", "Query: $query")
        android.util.Log.d("SimpleModeService", "Engine 1: $engine1Key")
        android.util.Log.d("SimpleModeService", "Engine 2: $engine2Key")
        android.util.Log.d("SimpleModeService", "Engine 3: $engine3Key")

        simpleModeView?.visibility = View.GONE

        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            putExtra("search_query", query)
            putExtra("engine_key", engine1Key)
            putExtra("window_count", 3)
            putExtra("source", "简易模式")
            putExtra("search_source", "简易模式")
            putExtra("startTime", System.currentTimeMillis())
        }
        
        // Make sure to stop any existing instance first
        stopService(Intent(this, DualFloatingWebViewService::class.java))
        
        // Start the service
        startService(intent)
        
        Toast.makeText(this, "启动三窗口搜索...", Toast.LENGTH_SHORT).show()
    }

    private fun convertDisplayNameToEngineKey(displayName: String): String {
        // Convert AI engine display names to the keys that DualFloatingWebViewService expects
        return when (displayName.lowercase()) {
            "chatgpt" -> "chatgpt"
            "claude" -> "claude"
            "gemini" -> "gemini"
            "文心一言" -> "wenxin"
            "智谱清言" -> "chatglm"
            "通义千问" -> "qianwen"
            "讯飞星火" -> "xinghuo"
            "perplexity" -> "perplexity"
            "phind" -> "phind"
            "poe" -> "poe"
            "天工ai" -> "tiangong"
            "秘塔ai搜索" -> "metaso"
            "夸克ai" -> "quark"
            "360ai搜索" -> "360ai"
            "百度ai" -> "baiduai"
            "you.com" -> "you"
            "brave search" -> "brave"
            "wolframalpha" -> "wolfram"
            "chatgpt (api)" -> "chatgpt_chat"
            "deepseek (api)" -> "deepseek_chat"
            "kimi" -> "kimi"
            "deepseek (web)" -> "deepseek"
            "万知" -> "wanzhi"
            "百小应" -> "baixiaoying"
            "跃问" -> "yuewen"
            "豆包" -> "doubao"
            "cici" -> "cici"
            "海螺" -> "hailuo"
            "groq" -> "groq"
            "腾讯元宝" -> "yuanbao"
            else -> displayName.lowercase() // fallback to lowercase version
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_SHOW_VIEW") {
            showSimpleModeView()
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