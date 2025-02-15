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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        settingsManager = SettingsManager.getInstance(this)
        
        initViews()
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

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        searchAdapter = SearchEngineAdapter(settingsManager.getEngineOrder())
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
        searchAdapter.performSearch(query)
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

    private inner class SearchEngineAdapter(private val engines: List<AIEngine>) : 
        RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

        val webViews = mutableMapOf<Int, WebView>()  // 改用Map来存储WebView
        private var expandedPosition = -1  // 记录当前展开的位置

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val engineIcon: ImageView = view.findViewById(R.id.engine_icon)
            val engineName: TextView = view.findViewById(R.id.engine_name)
            val webViewContainer: ViewGroup = view.findViewById(R.id.webview_container)
            val titleBar: View = view.findViewById(R.id.title_bar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.card_search_engine, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val engine = engines[position]
            
            holder.engineIcon.setImageResource(engine.iconResId)
            holder.engineName.text = engine.name

            // 设置点击事件
            holder.titleBar.setOnClickListener {
                if (expandedPosition == position) {
                    // 折叠当前卡片
                    collapseCard(holder, position)
                } else {
                    // 如果有其他展开的卡片，先折叠它
                    if (expandedPosition != -1) {
                        notifyItemChanged(expandedPosition)
                    }
                    // 展开当前卡片
                    expandCard(holder, position)
                }
            }

            // 根据展开状态设置容器高度
            if (expandedPosition == position) {
                holder.webViewContainer.visibility = View.VISIBLE
                holder.webViewContainer.layoutParams.height = 600.dpToPx()
                
                // 确保WebView已创建并添加到容器中
                ensureWebViewCreated(holder, position)
            } else {
                holder.webViewContainer.visibility = View.GONE
                holder.webViewContainer.layoutParams.height = 0
                
                // 移除WebView（可选，取决于内存使用情况）
                removeWebViewIfNeeded(position)
            }
        }

        private fun expandCard(holder: ViewHolder, position: Int) {
            expandedPosition = position
            
            // 创建并设置WebView
            ensureWebViewCreated(holder, position)
            
            // 加载初始URL
            val engine = engines[position]
            webViews[position]?.let { webView ->
                webView.post {
                    webView.loadUrl(engine.url)
                }
            }
            
            // 展开动画
            holder.webViewContainer.visibility = View.VISIBLE
            val anim = ValueAnimator.ofInt(0, 600.dpToPx())
            anim.duration = 300
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener { animator ->
                holder.webViewContainer.layoutParams.height = animator.animatedValue as Int
                holder.webViewContainer.requestLayout()
            }
            anim.start()
        }

        private fun collapseCard(holder: ViewHolder, position: Int) {
            expandedPosition = -1
            
            // 折叠动画
            val anim = ValueAnimator.ofInt(holder.webViewContainer.height, 0)
            anim.duration = 300
            anim.interpolator = DecelerateInterpolator()
            anim.addUpdateListener { animator ->
                holder.webViewContainer.layoutParams.height = animator.animatedValue as Int
                holder.webViewContainer.requestLayout()
            }
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    holder.webViewContainer.visibility = View.GONE
                    // 可选：移除WebView以节省内存
                    removeWebViewIfNeeded(position)
                }
            })
            anim.start()
        }

        private fun ensureWebViewCreated(holder: ViewHolder, position: Int) {
            if (!webViews.containsKey(position)) {
                val webView = WebView(this@SearchActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                setupWebView(webView)
                webViews[position] = webView
            }
            
            val webView = webViews[position]
            if (webView?.parent != null) {
                (webView.parent as? ViewGroup)?.removeView(webView)
            }
            holder.webViewContainer.addView(webView)
        }

        private fun removeWebViewIfNeeded(position: Int) {
            webViews[position]?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                // 可选：完全销毁WebView以节省内存
                // webView.destroy()
                // webViews.remove(position)
            }
        }

        override fun getItemCount() = engines.size

        fun performSearch(query: String) {
            // 只在当前展开的WebView中执行搜索
            if (expandedPosition != -1) {
                val engine = engines[expandedPosition]
                val url = if (query.isNotEmpty()) {
                    getSearchUrl(engine, query)
                } else {
                    engine.url
                }
                webViews[expandedPosition]?.let { webView ->
                    webView.post {
                        webView.loadUrl(url)
                    }
                }
            }
        }

        private fun getSearchUrl(engine: AIEngine, query: String): String {
            return when (engine.name) {
                "Google" -> "https://www.google.com/search?q=$query"
                "Bing" -> "https://www.bing.com/search?q=$query"
                "百度" -> "https://www.baidu.com/s?wd=$query"
                "ChatGPT" -> "https://chat.openai.com/"
                "Claude" -> "https://claude.ai/"
                "文心一言" -> "https://yiyan.baidu.com/"
                "通义千问" -> "https://qianwen.aliyun.com/"
                "讯飞星火" -> "https://xinghuo.xfyun.cn/"
                "Gemini" -> "https://gemini.google.com/"
                "Copilot" -> "https://copilot.microsoft.com/"
                "豆包" -> "https://www.doubao.com/"
                else -> "https://www.google.com/search?q=$query"  // 默认使用 Google
            }
        }

        private fun setupWebView(webView: WebView) {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowContentAccess = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                javaScriptCanOpenWindowsAutomatically = true
                defaultTextEncodingName = "UTF-8"
                
                // 添加新的设置
                setGeolocationEnabled(true)
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                blockNetworkImage = false
                blockNetworkLoads = false
                
                // 设置 User Agent
                userAgentString = userAgentString.replace("; wv", "")
                
                // 添加更多必要的设置
                databaseEnabled = true
                domStorageEnabled = true
                setGeolocationEnabled(true)
                
                // 允许混合内容
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            // 设置WebViewClient以处理页面加载
            webView.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    request?.url?.let { uri ->
                        if (uri.scheme in listOf("http", "https")) {
                            view?.loadUrl(uri.toString())
                            return true
                        }
                    }
                    return true // 其他链接交给系统处理
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.visibility = View.VISIBLE
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Toast.makeText(this@SearchActivity, "加载失败: $description", Toast.LENGTH_SHORT).show()
                }
            }

            // 设置WebChromeClient以处理JavaScript对话框等
            webView.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: android.webkit.GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
        // Clean up WebViews
        searchAdapter.webViews.values.forEach { webView ->
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
        }
        searchAdapter.webViews.clear()
    }

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 1001
    }
} 