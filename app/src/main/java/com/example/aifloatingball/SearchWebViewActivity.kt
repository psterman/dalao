package com.example.aifloatingball

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
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
import android.view.LayoutInflater
import android.view.Gravity
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import android.graphics.Color
import android.util.Log

class SearchWebViewActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var webView: WebView
    private var recognizer: SpeechRecognizer? = null
    private lateinit var voiceAnimationView: LottieAnimationView
    private lateinit var voiceAnimationContainer: FrameLayout
    private lateinit var settingsManager: SettingsManager
    private lateinit var letterIndexBar: com.example.aifloatingball.view.LetterIndexBar
    private lateinit var previewContainer: LinearLayout
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private var currentLetter: Char = 'A'
    private var sortedEngines: List<SearchEngine> = emptyList()
    private lateinit var engineListPopup: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_search_webview)

            settingsManager = SettingsManager.getInstance(this)
            
            setupViews()
            setupWebView()
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
            android.util.Log.e("SearchWebViewActivity", "Error in onCreate", e)
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
            webView = findViewById(R.id.webview) ?: throw IllegalStateException("WebView未找到")
            voiceAnimationView = findViewById(R.id.voice_animation_view) ?: throw IllegalStateException("语音动画视图未找到")
            voiceAnimationContainer = findViewById(R.id.voice_animation_container) ?: throw IllegalStateException("语音动画容器未找到")
            letterIndexBar = findViewById(R.id.letter_index_bar) ?: throw IllegalStateException("字母索引栏未找到")
            engineListPopup = findViewById(R.id.engine_list_popup) ?: throw IllegalStateException("搜索引擎列表弹窗未找到")
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
            
            // 点击空白区域隐藏搜索引擎列表
            findViewById<View>(android.R.id.content)?.setOnClickListener {
                if (engineListPopup.visibility == View.VISIBLE) {
                    engineListPopup.visibility = View.GONE
                }
            }
            
            // 防止点击搜索引擎列表时触发隐藏
            engineListPopup.setOnClickListener { /* 不做任何处理，阻止点击事件传递 */ }

        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "视图初始化失败", e)
            Toast.makeText(this, "视图初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 启用自适应屏幕
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            
            // 允许混合内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后的处理
            }
        }
    }

    private fun performSearch(engine: SearchEngine, query: String?) {
        val url = if (query.isNullOrBlank()) {
            // 如果没有查询文本，使用搜索引擎的首页URL（去掉%s部分）
            engine.url.replace("%s", "").replace("search?q=", "")
                .replace("search?query=", "")
                .replace("search?word=", "")
                .replace("s?wd=", "")
        } else {
            // 有查询文本，使用完整的搜索URL
            engine.url.replace("%s", query)
        }
        webView.loadUrl(url)
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            // 使用当前选中的搜索引擎进行搜索
            val selectedEngine = sortedEngines.firstOrNull()
            selectedEngine?.let { engine ->
                performSearch(engine, query.ifBlank { null })
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

    private fun setupLetterIndexLayout() {
        try {
            letterIndexBar.onLetterSelectedListener = { _, letter ->
                try {
                    currentLetter = letter
                    showSearchEnginesByLetter(letter)
                    // 显示搜索引擎列表
                    engineListPopup.visibility = View.VISIBLE
                    // 震动反馈
                    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                } catch (e: Exception) {
                    Log.e("SearchWebViewActivity", "字母选择处理失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "设置字母索引布局失败", e)
        }
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        try {
            // 更新字母标题
            letterTitle.text = letter.toString()

            // 清空搜索引擎列表
            previewEngineList.removeAllViews()

            // 查找所有匹配该字母的搜索引擎
            val matchingEngines = try {
                sortedEngines.filter { engine ->
                    try {
                        val firstChar = engine.name.first()
                        when {
                            firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                                firstChar.toString().uppercase() == letter.toString()
                            firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                                try {
                                    val format = net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat().apply {
                                        toneType = net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
                                        caseType = net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.UPPERCASE
                                        vCharType = net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
                                    }
                                    val pinyin = net.sourceforge.pinyin4j.PinyinHelper.toHanyuPinyinStringArray(firstChar, format)
                                    pinyin?.firstOrNull()?.firstOrNull()?.toString()?.uppercase() == letter.toString()
                                } catch (e: Exception) {
                                    Log.e("SearchWebViewActivity", "拼音转换失败", e)
                                    false
                                }
                            }
                            else -> false
                        }
                    } catch (e: Exception) {
                        Log.e("SearchWebViewActivity", "搜索引擎名称处理失败", e)
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchWebViewActivity", "搜索引擎过滤失败", e)
                emptyList()
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
                    try {
                        val engineItem = LayoutInflater.from(this).inflate(
                            R.layout.item_search_engine,
                            previewEngineList,
                            false
                        )

                        engineItem.findViewById<ImageView>(R.id.engine_icon)?.apply {
                            setImageResource(engine.iconResId)
                        }
                        
                        engineItem.findViewById<TextView>(R.id.engine_name)?.apply {
                            text = engine.name
                        }

                        // 添加点击事件
                        engineItem.setOnClickListener {
                            try {
                                val query = searchInput.text.toString().trim()
                                performSearch(engine, query.ifBlank { null })
                                // 隐藏搜索引擎列表
                                engineListPopup.visibility = View.GONE
                            } catch (e: Exception) {
                                Log.e("SearchWebViewActivity", "搜索引擎点击处理失败", e)
                                Toast.makeText(this, "处理点击事件失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        previewEngineList.addView(engineItem)

                        // 在每个搜索引擎项之间添加分隔线
                        if (engine != matchingEngines.last()) {
                            try {
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
                            } catch (e: Exception) {
                                Log.e("SearchWebViewActivity", "添加分隔线失败", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SearchWebViewActivity", "添加搜索引擎项失败", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "显示搜索引擎列表失败", e)
            Toast.makeText(this, "显示搜索引擎列表失败：${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@SearchWebViewActivity, "语音识别失败，请重试", Toast.LENGTH_SHORT).show()
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
        voiceAnimationView.setAnimation("voice_animation.json")
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
                Toast.makeText(this, "需要语音权限才能使用语音搜索功能", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
    }

    data class SearchEngine(
        val name: String,
        val url: String,
        val iconResId: Int
    )
} 