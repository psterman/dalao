package com.example.aifloatingball

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import android.view.LayoutInflater
import android.view.Gravity
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import android.graphics.Color

class SearchActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private var recognizer: SpeechRecognizer? = null
    private lateinit var voiceAnimationView: LottieAnimationView
    private lateinit var voiceAnimationContainer: FrameLayout
    private lateinit var settingsManager: SettingsManager
    private var currentLayoutTheme: String = "fold"
    private val layoutThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.LAYOUT_THEME_CHANGED") {
                currentLayoutTheme = settingsManager.getLayoutTheme()
                setupViews()
            }
        }
    }
    private lateinit var letterIndexBar: com.example.aifloatingball.view.LetterIndexBar
    private lateinit var previewContainer: LinearLayout
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private var currentLetter: Char = 'A'
    private var sortedEngines: List<SearchEngine> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_search)

            settingsManager = SettingsManager.getInstance(this)
            currentLayoutTheme = settingsManager.getLayoutTheme()
            
            // 注册布局主题变更广播接收器
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    layoutThemeReceiver,
                    IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED"),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(
                    layoutThemeReceiver,
                    IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
                )
            }
        
            setupViews()
            setupVoiceRecognition()
            setupClickListeners()

            // 检查是否有剪贴板文本传入
            val clipboardText = intent.getStringExtra("CLIPBOARD_TEXT")
            clipboardText?.let { text ->
                if (text.isNotBlank()) {
                    searchInput.setText(text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error in onCreate", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            // 初始化所有视图
            searchInput = findViewById(R.id.search_input) ?: throw IllegalStateException("搜索输入框未找到")
            voiceButton = findViewById(R.id.btn_voice) ?: throw IllegalStateException("语音按钮未找到")
            searchButton = findViewById(R.id.btn_search) ?: throw IllegalStateException("搜索按钮未找到")
            closeButton = findViewById(R.id.btn_close) ?: throw IllegalStateException("关闭按钮未找到")
            voiceAnimationView = findViewById(R.id.voice_animation_view) ?: throw IllegalStateException("语音动画视图未找到")
            voiceAnimationContainer = findViewById(R.id.voice_animation_container) ?: throw IllegalStateException("语音动画容器未找到")
            letterIndexBar = findViewById(R.id.letter_index_bar) ?: throw IllegalStateException("字母索引栏未找到")
            previewContainer = findViewById(R.id.preview_container) ?: throw IllegalStateException("预览容器未找到")
            letterTitle = findViewById(R.id.letter_title) ?: throw IllegalStateException("字母标题未找到")
            previewEngineList = findViewById(R.id.preview_engine_list) ?: throw IllegalStateException("预览引擎列表未找到")

            // 初始化搜索引擎列表
            sortedEngines = settingsManager.getEngineOrder().map { aiEngine ->
                SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
            }.sortedWith(compareBy { 
                val firstChar = it.name.first().toString()
                when {
                    firstChar.matches(Regex("[A-Za-z]")) -> firstChar.uppercase()
                    firstChar.matches(Regex("[\u4e00-\u9fa5]")) -> {
                        try {
                            java.text.Collator.getInstance(java.util.Locale.CHINESE)
                                .getCollationKey(firstChar).sourceString
                        } catch (e: Exception) {
                            firstChar
                        }
                    }
                    else -> firstChar
                }
            })

            setupLetterIndexLayout()
            // 显示初始字母的搜索引擎列表
            showSearchEnginesByLetter(currentLetter)

        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error setting up views", e)
            Toast.makeText(this, "视图初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                // 启动悬浮窗服务并传入搜索内容
                val selectedEngine = sortedEngines.firstOrNull()
                selectedEngine?.let { engine ->
                    val intent = Intent(this, FloatingWindowService::class.java).apply {
                        putExtra("ENGINE_NAME", engine.name)
                        putExtra("ENGINE_URL", engine.url)
                        putExtra("ENGINE_ICON", engine.iconResId)
                        putExtra("SEARCH_QUERY", query)
                    }
                    startService(intent)
                    finish()
                }
            }
        }

        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        closeButton.setOnClickListener {
            finish()
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchButton.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun setupVoiceRecognition() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    showVoiceSearchAnimation()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        searchInput.setText(recognizedText)
                        searchButton.performClick()
                    }
                    hideVoiceSearchAnimation()
                }

                override fun onError(error: Int) {
                    hideVoiceSearchAnimation()
                    Toast.makeText(this@SearchActivity, "语音识别失败，请重试", Toast.LENGTH_SHORT).show()
                }

                // 其他必需的回调方法
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun showVoiceSearchAnimation() {
        voiceAnimationContainer.visibility = View.VISIBLE
        voiceAnimationView.visibility = View.VISIBLE
        // Use a simple animation
        voiceAnimationView.setAnimation("{ \"v\":\"5.5.7\", \"fr\":60, \"ip\":0, \"op\":180, \"w\":512, \"h\":512, \"nm\":\"Voice Animation\", \"ddd\":0, \"assets\":[], \"layers\":[{ \"ddd\":0, \"ind\":1, \"ty\":4, \"nm\":\"Circle\", \"sr\":1, \"ks\":{ \"o\":{ \"a\":1, \"k\":[{ \"i\":{ \"x\":[0.833], \"y\":[0.833] }, \"o\":{ \"x\":[0.167], \"y\":[0.167] }, \"t\":0, \"s\":[100] },{ \"t\":90, \"s\":[0] }] }, \"r\":{ \"a\":0, \"k\":0 }, \"p\":{ \"a\":0, \"k\":[256,256,0] }, \"a\":{ \"a\":0, \"k\":[0,0,0] }, \"s\":{ \"a\":1, \"k\":[{ \"i\":{ \"x\":[0.833,0.833,0.833], \"y\":[0.833,0.833,0.833] }, \"o\":{ \"x\":[0.167,0.167,0.167], \"y\":[0.167,0.167,0.167] }, \"t\":0, \"s\":[100,100,100] },{ \"t\":90, \"s\":[200,200,100] }] } }, \"ao\":0, \"shapes\":[{ \"ty\":\"gr\", \"it\":[{ \"d\":1, \"ty\":\"el\", \"s\":{ \"a\":0, \"k\":[100,100] }, \"p\":{ \"a\":0, \"k\":[0,0] }, \"nm\":\"Ellipse Path 1\" },{ \"ty\":\"st\", \"c\":{ \"a\":0, \"k\":[0.2,0.4,1,1] }, \"o\":{ \"a\":0, \"k\":100 }, \"w\":{ \"a\":0, \"k\":10 }, \"lc\":2, \"lj\":1, \"ml\":4, \"bm\":0, \"nm\":\"Stroke 1\" },{ \"ty\":\"tr\", \"p\":{ \"a\":0, \"k\":[0,0] }, \"a\":{ \"a\":0, \"k\":[0,0] }, \"s\":{ \"a\":0, \"k\":[100,100] }, \"r\":{ \"a\":0, \"k\":0 }, \"o\":{ \"a\":0, \"k\":100 }, \"sk\":{ \"a\":0, \"k\":0 }, \"sa\":{ \"a\":0, \"k\":0 }, \"nm\":\"Transform\" }], \"nm\":\"Group 1\" }], \"ip\":0, \"op\":180, \"st\":0, \"bm\":0 }], \"markers\":[] }")
        voiceAnimationView.repeatCount = LottieDrawable.INFINITE
        voiceAnimationView.playAnimation()
    }

    private fun hideVoiceSearchAnimation() {
        voiceAnimationView.cancelAnimation()
        voiceAnimationView.visibility = View.GONE
        voiceAnimationContainer.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音搜索", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupLetterIndexLayout() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            currentLetter = letter
            showSearchEnginesByLetter(letter)
        }
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        try {
            // 更新字母标题
            letterTitle.text = letter.toString()

            // 清空搜索引擎列表
            previewEngineList.removeAllViews()

            // 创建拼音转换器
            val format = HanyuPinyinOutputFormat().apply {
                toneType = HanyuPinyinToneType.WITHOUT_TONE
                caseType = HanyuPinyinCaseType.UPPERCASE
                vCharType = HanyuPinyinVCharType.WITH_V
            }

            // 查找所有匹配该字母的搜索引擎
            val matchingEngines = sortedEngines.filter { engine ->
                val firstChar = engine.name.first()
                when {
                    firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                        firstChar.toString().uppercase() == letter.toString()
                    firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                        try {
                            val pinyin = PinyinHelper.toHanyuPinyinStringArray(firstChar, format)
                            pinyin?.firstOrNull()?.firstOrNull()?.toString()?.uppercase() == letter.toString()
                        } catch (e: Exception) {
                            false
                        }
                    }
                    else -> false
                }
            }

            if (matchingEngines.isEmpty()) {
                // 如果没有匹配的搜索引擎，显示提示信息
                val noEngineText = TextView(this).apply {
                    text = "没有以 $letter 开头的搜索引擎"
                    textSize = 16f
                    setTextColor(Color.GRAY)
                    gravity = Gravity.CENTER
                    setPadding(16, 32, 16, 32)
                }
                previewEngineList.addView(noEngineText)
            } else {
                // 添加匹配的搜索引擎
                matchingEngines.forEach { engine ->
                    val engineItem = LayoutInflater.from(this).inflate(
                        R.layout.item_search_engine,
                        previewEngineList,
                        false
                    )

                    engineItem.findViewById<ImageView>(R.id.engine_icon)
                        .setImageResource(engine.iconResId)
                    engineItem.findViewById<TextView>(R.id.engine_name)
                        .text = engine.name

                    // 添加点击事件
                    engineItem.setOnClickListener {
                        val intent = Intent(this, FloatingWindowService::class.java).apply {
                            putExtra("ENGINE_NAME", engine.name)
                            putExtra("ENGINE_URL", engine.url)
                            putExtra("ENGINE_ICON", engine.iconResId)
                            putExtra("SHOULD_OPEN_URL", true)
                            val query = searchInput.text.toString().trim()
                            if (query.isNotEmpty()) {
                                putExtra("SEARCH_QUERY", query)
                            }
                        }
                        startService(intent)
                        finish()
                    }

                    previewEngineList.addView(engineItem)

                    // 在每个搜索引擎项之间添加分隔线
                    if (engine != matchingEngines.last()) {
                        val divider = View(this).apply {
                            setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1
                            ).apply {
                                setMargins(16, 0, 16, 0)
                            }
                        }
                        previewEngineList.addView(divider)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error showing search engines", e)
            Toast.makeText(this, "显示搜索引擎列表失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try {
            // 注销广播接收器
            try {
                unregisterReceiver(layoutThemeReceiver)
            } catch (e: Exception) {
                android.util.Log.e("SearchActivity", "Error unregistering receiver", e)
            }
            
            // 清理语音识别
            try {
                recognizer?.destroy()
                recognizer = null
            } catch (e: Exception) {
                android.util.Log.e("SearchActivity", "Error destroying recognizer", e)
            }
            
            super.onDestroy()
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error in onDestroy", e)
            super.onDestroy()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
    }
} 