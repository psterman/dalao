package com.example.aifloatingball

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.animation.Animator
import androidx.recyclerview.widget.GridLayoutManager
import android.net.Uri
import android.widget.PopupMenu
import org.json.JSONObject
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class SearchActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var voiceButton: ImageButton
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private var recognizer: SpeechRecognizer? = null
    private lateinit var voiceAnimationView: ImageView
    private lateinit var voiceAnimationContainer: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchAdapter: SearchEngineAdapter
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
            
            initViews()
            setupViews()
            setupRecyclerView()
            setupVoiceRecognition()
            setupClickListeners()

            // 检查是否有剪贴板文本传入
            val clipboardText = intent.getStringExtra("CLIPBOARD_TEXT")
            clipboardText?.let { text ->
                if (text.isNotBlank()) {
                    searchInput.setText(text)
                    performSearch(text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error in onCreate", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        searchInput = findViewById(R.id.search_input)
        voiceButton = findViewById(R.id.btn_voice)
        searchButton = findViewById(R.id.btn_search)
        closeButton = findViewById(R.id.btn_close)
        voiceAnimationView = findViewById(R.id.voice_animation_view)
        voiceAnimationContainer = findViewById(R.id.voice_animation_container)
        recyclerView = findViewById(R.id.search_results_recycler)
    }

    private fun setupViews() {
        try {
            val searchResultsRecycler = findViewById<RecyclerView>(R.id.search_results_recycler)
            val cardRecyclerView = findViewById<RecyclerView>(R.id.card_recycler_view)
            
            when (currentLayoutTheme) {
                "card" -> {
                    cardRecyclerView.visibility = View.VISIBLE
                    searchResultsRecycler.visibility = View.GONE
                    setupCardLayout()
                }
                else -> {
                    cardRecyclerView.visibility = View.GONE
                    searchResultsRecycler.visibility = View.VISIBLE
                    setupFoldLayout()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error setting up views", e)
            Toast.makeText(this, "视图初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupCardLayout() {
        val recyclerView = findViewById<RecyclerView>(R.id.card_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        
        val engines = settingsManager.getEngineOrder().map { aiEngine ->
            SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
        }
        val adapter = CardLayoutAdapter(engines, 
            onCardClick = { position -> 
                expandCard(position)
            },
            onCardLongClick = { view, position ->
                showCardOptions(view, position)
                true
            }
        )
        recyclerView.adapter = adapter
    }

    private fun expandCard(position: Int) {
        val adapter = findViewById<RecyclerView>(R.id.card_recycler_view).adapter as? CardLayoutAdapter
        adapter?.let {
            // 直接进入全屏模式
            it.enterFullscreen(position)
        }
    }

    private fun setupFoldLayout() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        val engines = settingsManager.getEngineOrder().map { aiEngine ->
            SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
        }
        searchAdapter = SearchEngineAdapter(engines)
        recyclerView.adapter = searchAdapter
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        val engines = settingsManager.getEngineOrder().map { aiEngine ->
            SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
        }
        searchAdapter = SearchEngineAdapter(engines)
        recyclerView.adapter = searchAdapter
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
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
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        try {
            when (currentLayoutTheme) {
                "card" -> {
                    (findViewById<RecyclerView>(R.id.card_recycler_view).adapter as? CardLayoutAdapter)?.performSearch(query)
                }
                else -> {
                    searchAdapter.performSearch(query)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error performing search", e)
            Toast.makeText(this, "搜索失败：${e.message}", Toast.LENGTH_SHORT).show()
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
                        performSearch(recognizedText)
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
        val animation = AnimationUtils.loadAnimation(this, R.anim.voice_ripple)
        voiceAnimationView.startAnimation(animation)
    }

    private fun hideVoiceSearchAnimation() {
        voiceAnimationView.clearAnimation()
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

    fun showCardOptions(view: View, position: Int) {
        val popupMenu = PopupMenu(this, view)
        val menu = popupMenu.menu

        // 获取当前的引擎列表
        val engines = settingsManager.getEngineOrder().map { aiEngine ->
            SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
        }
        val engine = engines[position]
        val webView = view.findViewById<WebView>(R.id.web_view)

        menu.add("刷新页面").setOnMenuItemClickListener {
            webView.reload()
            true
        }

        menu.add("分享").setOnMenuItemClickListener {
            webView.evaluateJavascript(
                "(function() { return { title: document.title, url: window.location.href }; })();",
                { result ->
                    try {
                        val jsonObject = JSONObject(result)
                        val title = jsonObject.getString("title")
                        val url = jsonObject.getString("url")

                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, title)
                            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                        }
                        
                        startActivity(Intent.createChooser(shareIntent, "分享 $title"))
                    } catch (e: Exception) {
                        Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            true
        }

        menu.add("全屏").setOnMenuItemClickListener {
            webView.evaluateJavascript(
                "(function() { return window.location.href; })();",
                { result ->
                    val url = result.trim('"')
                    val fullscreenIntent = Intent(this, FullscreenWebViewActivity::class.java).apply {
                        putExtra("URL", url)
                        putExtra("TITLE", engine.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fullscreenIntent)
                }
            )
            true
        }

        popupMenu.show()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        try {
            // 清理 WebViews
            if (currentLayoutTheme == "card") {
                (findViewById<RecyclerView>(R.id.card_recycler_view).adapter as? CardLayoutAdapter)?.cleanupWebViews()
            } else {
                searchAdapter.cleanupWebViews()
            }
            
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