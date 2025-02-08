package com.example.aifloatingball

import android.Manifest
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.gesture.GestureManager
import com.example.aifloatingball.menu.QuickMenuManager
import com.example.aifloatingball.search.SearchHistoryAdapter
import com.example.aifloatingball.search.SearchHistoryManager
import com.example.aifloatingball.utils.SystemSettingsHelper
import android.content.pm.PackageManager
import android.util.DisplayMetrics

class FloatingWindowService : Service(), GestureManager.GestureCallback {
    private var windowManager: WindowManager? = null
    private var floatingBallView: View? = null
    private var searchView: View? = null
    private val aiWindows = mutableListOf<WebView>()
    private lateinit var gestureManager: GestureManager
    private lateinit var quickMenuManager: QuickMenuManager
    private lateinit var systemSettingsHelper: SystemSettingsHelper
    private var screenWidth = 0
    private var screenHeight = 0
    private lateinit var searchHistoryManager: SearchHistoryManager
    private var currentEngineIndex = 0
    
    private val speechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(this)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 使用兼容性更好的方式获取屏幕尺寸
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display
            display?.getRealMetrics(displayMetrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)
        }
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        
        systemSettingsHelper = SystemSettingsHelper(this)
        quickMenuManager = QuickMenuManager(this, windowManager!!, systemSettingsHelper)
        gestureManager = GestureManager(this, this)
        searchHistoryManager = SearchHistoryManager(this)
        
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
        
        floatingBallView?.setOnTouchListener { view, event ->
            gestureManager.onTouch(view, event)
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
        searchHistoryManager.addSearchQuery(query)
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
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
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
    
    // 重复上一次搜索
    private fun repeatLastSearch() {
        searchHistoryManager.getLastQuery()?.let { query ->
            performSearch(query)
        }
    }
    
    // 切换到下一个搜索结果
    private fun switchToNextEngine() {
        if (aiWindows.isEmpty()) return
        
        currentEngineIndex = (currentEngineIndex + 1) % aiWindows.size
        bringEngineToFront(currentEngineIndex)
    }
    
    // 切换到上一个搜索结果
    private fun switchToPreviousEngine() {
        if (aiWindows.isEmpty()) return
        
        currentEngineIndex = if (currentEngineIndex > 0) {
            currentEngineIndex - 1
        } else {
            aiWindows.size - 1
        }
        bringEngineToFront(currentEngineIndex)
    }
    
    private fun bringEngineToFront(index: Int) {
        aiWindows.getOrNull(index)?.let { webView ->
            // 将当前窗口置于顶层
            val params = webView.layoutParams as WindowManager.LayoutParams
            windowManager?.removeView(webView)
            windowManager?.addView(webView, params)
            
            // 添加切换动画
            webView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.window_show))
        }
    }
    
    // 关闭当前搜索结果
    private fun closeCurrentEngine() {
        if (aiWindows.isEmpty()) return
        
        aiWindows.getOrNull(currentEngineIndex)?.let { webView ->
            val animation = AnimationUtils.loadAnimation(this, R.anim.window_hide)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    windowManager?.removeView(webView)
                    aiWindows.remove(webView)
                    if (aiWindows.isNotEmpty()) {
                        currentEngineIndex = currentEngineIndex.coerceIn(0, aiWindows.size - 1)
                    }
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
            webView.startAnimation(animation)
        }
    }
    
    // 显示搜索历史
    private fun showSearchHistory() {
        val history = searchHistoryManager.getSearchHistory()
        if (history.isEmpty()) {
            Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建历史记录列表视图
        val historyView = LayoutInflater.from(this).inflate(R.layout.search_history_layout, null)
        val recyclerView = historyView.findViewById<RecyclerView>(R.id.history_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val adapter = SearchHistoryAdapter(history) { query ->
            performSearch(query)
            windowManager?.removeView(historyView)
        }
        recyclerView.adapter = adapter
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        windowManager?.addView(historyView, params)
        historyView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.menu_show))
    }
    
    // GestureCallback 实现
    override fun onSingleTap() {
        showSearchInput()
    }
    
    override fun onDoubleTap() {
        repeatLastSearch()
    }
    
    override fun onLongPress() {
        floatingBallView?.let {
            val location = IntArray(2)
            it.getLocationOnScreen(location)
            quickMenuManager.showMenu(location[0], location[1])
        }
    }
    
    override fun onSwipeLeft() {
        closeCurrentEngine()
    }
    
    override fun onSwipeRight() {
        showSearchHistory()
    }
    
    override fun onSwipeUp() {
        switchToNextEngine()
    }
    
    override fun onSwipeDown() {
        switchToPreviousEngine()
    }
    
    override fun onDrag(x: Float, y: Float) {
        floatingBallView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x = x.toInt()
            params.y = y.toInt()
            windowManager?.updateViewLayout(view, params)
        }
    }
    
    override fun onDragEnd(x: Float, y: Float) {
        // 实现靠边吸附
        floatingBallView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            val targetX = when {
                x < screenWidth / 2 -> 0
                else -> screenWidth - view.width
            }
            
            val animator = ValueAnimator.ofInt(x.toInt(), targetX)
            animator.addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                windowManager?.updateViewLayout(view, params)
            }
            animator.duration = 200
            animator.interpolator = DecelerateInterpolator()
            animator.start()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        windowManager?.let { wm ->
            floatingBallView?.let { wm.removeView(it) }
            searchView?.let { wm.removeView(it) }
            aiWindows.forEach { wm.removeView(it) }
        }
    }
} 