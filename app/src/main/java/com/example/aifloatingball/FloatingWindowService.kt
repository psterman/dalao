class FloatingWindowService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingBallView: View? = null
    private var searchView: View? = null
    private val aiWindows = mutableListOf<WebView>()
    
    private val speechRecognizer: SpeechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(this)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingBall()
    }
    
    private fun createFloatingBall() {
        floatingBallView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        floatingBallView?.setOnClickListener {
            showSearchInput()
        }
        
        windowManager?.addView(floatingBallView, params)
    }
    
    private fun showSearchInput() {
        searchView = LayoutInflater.from(this).inflate(R.layout.search_input_layout, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        
        setupVoiceInput()
        
        searchView?.findViewById<ImageButton>(R.id.search_button)?.setOnClickListener {
            val query = searchView?.findViewById<EditText>(R.id.search_input)?.text.toString()
            performSearch(query)
        }
        
        windowManager?.addView(searchView, params)
    }
    
    private fun setupVoiceInput() {
        val voiceButton = searchView?.findViewById<ImageButton>(R.id.voice_input_button)
        val searchInput = searchView?.findViewById<EditText>(R.id.search_input)
        
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    searchInput?.setText(matches[0])
                }
            }
            
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Toast.makeText(this@FloatingWindowService, "语音识别失败，请重试", Toast.LENGTH_SHORT).show()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        voiceButton?.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                Toast.makeText(this, "请在设置中授予录音权限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要搜索的内容")
        }
        
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "语音识别初始化失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun performSearch(query: String) {
        val engines = listOf(
            "https://deepseek.com/search?q=",
            "https://www.doubao.com/search?q=",
            "https://kimi.moonshot.cn/search?q="
        )
        
        engines.forEachIndexed { index, engine ->
            createAIWindow(engine + query, index)
        }
    }
    
    private fun createAIWindow(url: String, index: Int) {
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = CustomWebViewClient()
            loadUrl(url)
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        
        params.y = index * (resources.displayMetrics.heightPixels / 3)
        
        aiWindows.add(webView)
        windowManager?.addView(webView, params)
    }
    
    private fun setupWindowManagement() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingBallView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as WindowManager.LayoutParams).x
                    initialY = (view.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun minimizeWindow(webView: WebView) {
        val params = webView.layoutParams as WindowManager.LayoutParams
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager?.updateViewLayout(webView, params)
    }
    
    private fun maximizeWindow(webView: WebView) {
        val params = webView.layoutParams as WindowManager.LayoutParams
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        windowManager?.updateViewLayout(webView, params)
    }
    
    private fun closeWindow(webView: WebView) {
        windowManager?.removeView(webView)
        aiWindows.remove(webView)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        // ... 其他清理代码 ...
    }
} 