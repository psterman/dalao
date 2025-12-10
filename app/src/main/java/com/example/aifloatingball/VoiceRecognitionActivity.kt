package com.example.aifloatingball

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.service.SimpleModeService
import com.example.aifloatingball.manager.UnifiedCollectionManager
import com.example.aifloatingball.model.UnifiedCollectionItem
import com.example.aifloatingball.model.CollectionType
import com.example.aifloatingball.model.Priority
import com.example.aifloatingball.model.CompletionStatus
import com.example.aifloatingball.model.EmotionTag
import com.example.aifloatingball.voice.VoiceTextTagManager
import com.example.aifloatingball.voice.SpeechRecognitionDetectionActivity
import com.example.aifloatingball.utils.VoiceLog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import android.media.AudioManager
import com.example.aifloatingball.agent.AgentActionRouter
import com.example.aifloatingball.agent.DoubaoApiService
import com.example.aifloatingball.service.MyAccessibilityService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.Manifest

class VoiceRecognitionActivity : Activity() {
    companion object {
        private const val VOICE_RECOGNITION_REQUEST_CODE = 1001
        private const val SYSTEM_VOICE_REQUEST_CODE = 1002
        private const val TAG = "VoiceRecognitionActivity"
        // 强制冷却时间（毫秒）- 防止连续识别导致 ERROR_RECOGNIZER_BUSY
        private const val COOLING_DOWN_DELAY_MS = 1000L // 1秒冷却
    }
    
    /**
     * 识别器状态机，防止连续识别导致 ERROR_RECOGNIZER_BUSY
     */
    private enum class RecognizerState {
        IDLE,           // 空闲状态，可以启动识别
        LISTENING,      // 正在监听
        COOLING_DOWN    // 冷却中，禁止启动新识别
    }

    private val handler = Handler(Looper.getMainLooper())
    // 跟踪所有待执行的 Runnable，以便在 onDestroy 中移除
    private val pendingRunnables = mutableSetOf<Runnable>()
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var recognizedText = "" // 已确认的最终文本
    private var currentPartialText = "" // 当前部分识别结果（临时显示）
    private lateinit var settingsManager: SettingsManager
    private var recognizerBusyRetryCount = 0 // 识别器忙碌重试计数
    private val MAX_RECOGNIZER_BUSY_RETRIES = 3 // 最大重试次数
    private var isXiaomiAivsService = false // 是否是小米 Aivs 服务
    private var isSavingVoiceText = false // 防抖标志：是否正在保存语音文本
    private var recognizerState = RecognizerState.IDLE // 识别器状态
    private var isRecognizerReady = false // 识别器是否已准备好（已连接到服务）
    private var connectionTimeoutRunnable: Runnable? = null // 连接超时任务
    private val CONNECTION_TIMEOUT_MS = 5000L // 连接超时时间（5秒，某些设备需要更长时间）
    private var isStartingRecognition = false // 是否正在启动识别（防止重复启动）
    private var shouldDestroyRecognizerOnRelease = false // 是否应该在释放时销毁识别器（错误情况下需要完全销毁）
    private var isPausedByUser = false // 用户是否手动暂停识别
    private var lastRecognizedText = "" // 上一次识别的文本，用于去重
    private var waveformView: com.example.aifloatingball.ui.WaveformView? = null // 波形视图
    private var currentRmsValue = 0.1f // 当前音量值（用于波形动画）

    // --- Agent Integration Properties ---
    private val mainScope = MainScope()
    private var currentPendingAgentCommand: String? = null

    // Receiver for Screen Analysis Response
    private val agentResponseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MyAccessibilityService.ACTION_REQUEST_SCREEN_ANALYSIS) {
                val screenContent = intent.getStringExtra(MyAccessibilityService.EXTRA_SCREEN_CONTENT)
                if (!screenContent.isNullOrEmpty() && currentPendingAgentCommand != null) {
                    handleAgentAnalysis(currentPendingAgentCommand!!, screenContent)
                    currentPendingAgentCommand = null // Reset
                }
            }
        }
    }
    
    // 界面元素
    private lateinit var micContainer: MaterialCardView
    private lateinit var micIcon: ImageView
    private lateinit var listeningText: TextView
    private lateinit var recognizedTextView: EditText
    private lateinit var doneButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var zoomInButton: ImageButton
    private lateinit var zoomOutButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_recognition)
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化视图
        initializeViews()
        
        // 设置点击事件
        setupClickListeners()
        
        // 设置窗口属性
        setupWindowAttributes()
        
        // 启动前清空状态
        recognizedText = ""
        currentPartialText = ""
        recognizedTextView.setText("")
        
        // 检查权限并启动语音识别
        if (hasAudioPermission()) {
            startVoiceRecognition()
        } else {
            requestAudioPermission()
        }
        
        // Register Agent Receiver
        val filter = IntentFilter(MyAccessibilityService.ACTION_REQUEST_SCREEN_ANALYSIS)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(agentResponseReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel Coroutine Scope
        mainScope.cancel()
        
        // Unregister Agent Receiver
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(agentResponseReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        
        // 停止 AnimatedVectorDrawable，防止渲染线程泄漏
        if (::micIcon.isInitialized) {
            try {
                val drawable = micIcon.drawable
                if (drawable is AnimatedVectorDrawable) {
                    drawable.stop()
                    VoiceLog.d("已停止 AnimatedVectorDrawable 动画")
                }
            } catch (e: Exception) {
                VoiceLog.e("停止 AnimatedVectorDrawable 时出错: ${e.message}", e)
            }
        }
        
        // 完全销毁语音识别器（Activity 销毁时）
        destroySpeechRecognizer()
        
        // 停止波形动画
        waveformView?.setAnimationRunning(false)
        
        // 重置状态机
        recognizerState = RecognizerState.IDLE
        isPausedByUser = false
        VoiceLog.d("识别器状态: -> IDLE（Activity 销毁）")
        
        // 移除所有待执行的 Runnable，防止内存泄漏
        pendingRunnables.forEach { runnable ->
            handler.removeCallbacks(runnable)
        }
        pendingRunnables.clear()
        
        // 移除所有延迟任务
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 处理 Agent 分析结果
     */
    private fun handleAgentAnalysis(command: String, screenContext: String) {
        VoiceLog.d("🤔 处理 Agent 分析: command=$command")
        
        mainScope.launch {
            try {
                val result = DoubaoApiService.getInstance(this@VoiceRecognitionActivity)
                    .callAgent(command, screenContext)
                
                result.onSuccess { macro ->
                    VoiceLog.d("✅ Agent 计划生成成功")
                    AgentActionRouter.executeMacro(this@VoiceRecognitionActivity, macro)
                    handler.postDelayed({ finish() }, 1500)
                }.onFailure { e ->
                    VoiceLog.e("❌ Agent 执行失败: ${e.message}", e)
                    Toast.makeText(this@VoiceRecognitionActivity, "AI 执行失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                VoiceLog.e("处理 Agent 分析异常", e)
                Toast.makeText(this@VoiceRecognitionActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun initializeViews() {
        micContainer = findViewById(R.id.micContainer)
        micIcon = findViewById(R.id.micIcon)
        listeningText = findViewById(R.id.listeningText)
        recognizedTextView = findViewById(R.id.recognizedText)
        doneButton = findViewById(R.id.doneButton)
        saveButton = findViewById(R.id.saveButton)
        zoomInButton = findViewById(R.id.zoomInButton)
        zoomOutButton = findViewById(R.id.zoomOutButton)
        
        // 尝试查找波形视图（如果布局中有）
        waveformView = findViewById(R.id.voice_waveform)

        // Load the saved font size, or use the default
        val savedSize = settingsManager.getVoiceInputTextSize()
        recognizedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedSize)
    }
    
    private fun setupClickListeners() {
        // 设置说完了按钮点击事件
        doneButton.setOnClickListener {
            VoiceLog.d("用户点击了'说完了'按钮")
            finishRecognition()
        }
        
        // 设置保存按钮点击事件
        saveButton.setOnClickListener {
            VoiceLog.d("用户点击了'保存'按钮")
            saveVoiceText()
        }
        
        // 设置麦克风点击事件
        micContainer.setOnClickListener {
            toggleListening()
        }
        
        // 设置麦克风长按事件 - 启动语音识别服务检测
        micContainer.setOnLongClickListener {
            startDetectionActivity()
            true
        }

        zoomInButton.setOnClickListener {
            adjustTextSize(2f) // Increase by 2sp
        }

        zoomOutButton.setOnClickListener {
            adjustTextSize(-2f) // Decrease by 2sp
        }
    }
    
    private fun setupWindowAttributes() {
        // 设置窗口背景半透明
        window.attributes = window.attributes.apply {
            dimAmount = 0.3f
        }
        
        // 设置窗口动画
        overridePendingTransition(R.anim.slide_up, 0)
    }

    private fun startVoiceRecognition() {
        // 防止重复启动：如果正在启动，直接返回
        if (isStartingRecognition) {
            VoiceLog.d("⚠️ 识别器正在启动中，跳过重复启动")
            return
        }
        
        // 检查识别器状态：如果正在冷却，跳过启动
        if (recognizerState == RecognizerState.COOLING_DOWN) {
            VoiceLog.d("识别器冷却中，跳过启动（防止 ERROR_RECOGNIZER_BUSY）")
            return
        }
        
        // 如果已经在监听中且识别器已准备好，不需要重复启动
        if (isListening && recognizerState == RecognizerState.LISTENING && 
            speechRecognizer != null && isRecognizerReady) {
            VoiceLog.d("⚠️ 识别器已在监听中且已准备好，跳过重复启动")
            return
        }
        
        // 如果识别器存在但未准备好，需要等待或重新创建
        if (speechRecognizer != null && !isRecognizerReady && isStartingRecognition) {
            VoiceLog.d("⚠️ 识别器存在但未准备好，等待连接...")
            // 设置连接超时，如果超时则重新创建
            setupConnectionTimeout()
            return
        }
        
        // 设置启动标志，防止重复启动
        isStartingRecognition = true
        VoiceLog.d("开始启动语音识别...")
        
        // 优先检测华为设备：华为 Mate 60 / Pura 70 系列，isRecognitionAvailable() 因 HMS ML Kit 存在返回 true
        // 但 SpeechRecognizer.createSpeechRecognizer() 可能返回 null 或调用崩溃
        if (isHuaweiDevice() && !hasHmsCore()) {
            VoiceLog.w("华为设备但未安装 HMS Core，直接使用系统语音输入（避免 createSpeechRecognizer 返回 null）")
            isStartingRecognition = false // 重置标志
            trySystemVoiceInput()
            return
        }
        
        // 首先检查设备是否支持语音识别
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // 如果不支持，尝试使用系统语音输入Intent
            isStartingRecognition = false // 重置标志
            trySystemVoiceInput()
            return
        }
        
        // 重置准备状态（确保每次启动都是干净的状态）
        isRecognizerReady = false
        
        // 设置状态为监听中
        recognizerState = RecognizerState.LISTENING
        VoiceLog.d("识别器状态: IDLE -> LISTENING")
        
        // 检测是否是小米 Aivs 服务（优先使用小米 Aivs SDK）
        isXiaomiAivsService = detectXiaomiAivsService()
        VoiceLog.d("检测到语音识别服务类型: ${if (isXiaomiAivsService) "✅ 小米 Aivs SDK（优先使用）" else "其他服务"}")
        
        // 确保前一个识别器被完全释放（重要：避免识别器忙碌错误）
        // 如果识别器存在且状态异常（未准备好），需要完全销毁并重新创建
        if (speechRecognizer != null && !isRecognizerReady) {
            // 如果识别器未准备好，说明可能有问题，需要完全销毁
            shouldDestroyRecognizerOnRelease = true
            releaseSpeechRecognizer()
        } else if (speechRecognizer != null) {
            // 如果识别器已准备好，只需要取消即可
            releaseSpeechRecognizer()
        }
        
        // 给识别器一些时间完全释放
        val releaseDelay = when {
            Build.MANUFACTURER.lowercase().contains("meizu") -> 500L // 魅族手机需要更长的释放时间
            Build.MANUFACTURER.lowercase().contains("oppo") -> 500L // OPPO手机也需要更长时间
            else -> 200L // 其他手机正常释放
        }
        
        val createRunnable = CreateRecognizerRunnable(this)
        safePostDelayed(releaseDelay, createRunnable)
    }
    
    /**
     * 创建 SpeechRecognizer，优先使用小米 Aivs SDK
     * 如果检测到小米 Aivs 服务，直接使用它；否则使用默认服务
     */
    private fun createSpeechRecognizerWithAivsPriority(): SpeechRecognizer? {
        try {
            // 优先使用小米 Aivs SDK
            if (isXiaomiAivsService) {
                val aivsService = findXiaomiAivsService()
                if (aivsService != null) {
                    VoiceLog.d("✅ 使用小米 Aivs SDK: ${aivsService.packageName}/${aivsService.className}")
                    try {
                        return SpeechRecognizer.createSpeechRecognizer(this, aivsService)
                    } catch (e: Exception) {
                        VoiceLog.e("使用小米 Aivs SDK 创建 SpeechRecognizer 失败: ${e.message}", e)
                        // 继续尝试默认方式
                    }
                } else {
                    VoiceLog.w("⚠️ 检测到小米设备但未找到 Aivs 服务组件，使用默认方式")
                }
            }
            
            // 使用默认创建方式（系统会自动选择可用的服务）
            VoiceLog.d("使用默认方式创建 SpeechRecognizer")
            return SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            VoiceLog.e("创建 SpeechRecognizer 失败: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 查找小米 Aivs 语音识别服务组件
     * 优先查找 com.xiaomi.mibrain.speech（Aivs SDK）
     */
    private fun findXiaomiAivsService(): ComponentName? {
        try {
            val pm = packageManager
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            
            // 查询所有能处理语音识别的服务
            val services = pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            // 小米 Aivs 相关的包名（按优先级排序）
            val xiaomiAivsPackages = listOf(
                "com.xiaomi.mibrain.speech",  // 小米 Aivs SDK（最高优先级）
                "com.miui.voiceassist",        // 小爱同学
                "com.xiaomi.voiceassist"       // 小米语音助手
            )
            
            // 优先查找 com.xiaomi.mibrain.speech
            for (targetPackage in xiaomiAivsPackages) {
                for (serviceInfo in services) {
                    val packageName = serviceInfo.serviceInfo.packageName
                    val className = serviceInfo.serviceInfo.name
                    
                    if (packageName.equals(targetPackage, ignoreCase = true)) {
                        VoiceLog.d("✅ 找到小米 Aivs 服务: $packageName/$className")
                        return ComponentName(packageName, className)
                    }
                }
            }
            
            // 如果没有找到精确匹配，查找包含关键词的服务
            for (serviceInfo in services) {
                val packageName = serviceInfo.serviceInfo.packageName
                val className = serviceInfo.serviceInfo.name
                
                if (xiaomiAivsPackages.any { packageName.contains(it, ignoreCase = true) }) {
                    VoiceLog.d("✅ 找到小米相关语音服务: $packageName/$className")
                    return ComponentName(packageName, className)
                }
            }
            
            VoiceLog.d("未找到小米 Aivs 服务组件")
            return null
        } catch (e: Exception) {
            VoiceLog.e("查找小米 Aivs 服务时出错: ${e.message}", e)
            return null
        }
    }
    
    private fun prepareAndStartRecognition() {
        if (speechRecognizer == null) {
            VoiceLog.e("SpeechRecognizer为null，无法启动识别")
            // 如果识别器为null，尝试重新创建
            val retryRunnable = RetryStartRecognitionRunnable(this, 500)
            safePostDelayed(500, retryRunnable)
            return
        }
        
        // 检查识别器是否已准备好（已连接到服务）
        if (!isRecognizerReady) {
            VoiceLog.w("识别器尚未准备好，等待连接...")
            // 如果还没准备好，等待 onReadyForSpeech 回调
            // 设置连接超时，如果超时未连接则重试
            setupConnectionTimeout()
            return
        }
        
        // 准备识别意图 - 优化参数以提高识别效果和实时性
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // 使用自由格式语言模型，识别效果更好
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // 设置语言为中文
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            
            // 启用部分结果，实现实时流式显示
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            
            // 增加最大结果数，可能提高部分结果更新频率
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            
            // 尝试优化识别参数（某些设备可能不支持，但不影响使用）
            try {
                // 设置更短的静音分段时间，让识别更频繁地返回结果
                putExtra("android.speech.extra.SEGMENTATION_SILENCE_LENGTH_MS", 300) // 300ms静音后分段
            } catch (e: Exception) {
                VoiceLog.d("设备不支持SEGMENTATION_SILENCE_LENGTH_MS参数")
            }
            
            // 尝试设置识别超时时间（某些设备支持）
            try {
                putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2000) // 2秒静音后完成
                putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1500) // 1.5秒可能完成
            } catch (e: Exception) {
                VoiceLog.d("设备不支持超时参数")
            }
        }
        
        // 开始识别
        try {
            // 再次检查识别器是否可用
            if (speechRecognizer == null) {
                VoiceLog.e("SpeechRecognizer在启动前变为null")
                isStartingRecognition = false
                val retryRunnable = RetryStartRecognitionRunnable(this, 500)
                safePostDelayed(500, retryRunnable)
                return
            }
            
            // 再次确认识别器已准备好（双重检查，确保连接状态）
            if (!isRecognizerReady) {
                VoiceLog.w("⚠️ 识别器在启动前未准备好，等待连接...")
                setupConnectionTimeout()
                return
            }
            
            isListening = true
            updateListeningState(true)
            
            // 启动波形动画
            waveformView?.setAnimationRunning(true)
            waveformView?.setAmplitude(0.2f) // 初始振幅
            
            // 启动识别
            speechRecognizer?.startListening(recognizerIntent)
            // 重置启动标志（识别已成功启动）
            isStartingRecognition = false
            VoiceLog.d("✅ 语音识别已启动（识别器已连接）")
        } catch (e: IllegalStateException) {
            // 捕获"not connected to the recognition service"异常
            VoiceLog.e("SpeechRecognizer 未连接: ${e.message}", e)
            // 重置准备状态和启动标志
            isRecognizerReady = false
            isStartingRecognition = false
            // 标记需要完全销毁识别器（错误情况下需要重新创建）
            shouldDestroyRecognizerOnRelease = true
            // 释放并重试
            releaseSpeechRecognizer()
            val updateRunnable = UpdateTextRunnable(
                this,
                "服务未连接，正在重试...",
                android.R.color.holo_orange_light
            )
            safePost(updateRunnable)
            // 增加重试延迟，给识别器足够时间完全释放
            val retryDelay = when {
                Build.MANUFACTURER.lowercase().contains("meizu") -> 3000L // 魅族手机延迟3秒
                Build.MANUFACTURER.lowercase().contains("oppo") -> 3000L // OPPO手机延迟3秒
                else -> 2000L // 其他手机延迟2秒
            }
            val retryRunnable = RetryStartRecognitionRunnable(this, retryDelay)
            safePostDelayed(retryDelay, retryRunnable)
        } catch (e: Exception) {
            VoiceLog.e("SpeechRecognizer 启动失败: ${e.message}", e)
            // 重置准备状态和启动标志
            isRecognizerReady = false
            isStartingRecognition = false
            // 如果SpeechRecognizer失败，尝试系统语音输入
            trySystemVoiceInput()
        }
    }
    
    /**
     * 设置连接超时机制
     * 如果识别器在指定时间内未准备好，则重试或降级
     */
    private fun setupConnectionTimeout() {
        // 取消之前的超时任务
        connectionTimeoutRunnable?.let { timeoutRunnable ->
            handler.removeCallbacks(timeoutRunnable)
            pendingRunnables.remove(timeoutRunnable)
        }
        
        // 创建新的超时任务
        connectionTimeoutRunnable = ConnectionTimeoutRunnable(this)
        connectionTimeoutRunnable?.let { timeoutRunnable ->
            pendingRunnables.add(timeoutRunnable)
            handler.postDelayed(timeoutRunnable, CONNECTION_TIMEOUT_MS)
            VoiceLog.d("已设置连接超时：${CONNECTION_TIMEOUT_MS}ms")
        }
    }
    
    private fun toggleListening() {
        if (isListening) {
            // 如果正在监听，停止监听
            stopListening()
        } else {
            // 如果没有监听，开始监听
            isPausedByUser = false // 重置暂停标志
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                startVoiceRecognition()
            } else {
                // 如果SpeechRecognizer不可用，尝试系统语音输入
                trySystemVoiceInput()
            }
        }
    }
    
    private fun stopListening() {
        isListening = false
        isPausedByUser = true // 标记为用户手动暂停
        isStartingRecognition = false // 重置启动标志
        updateListeningState(false)
        
        // 完全停止识别：先取消，再停止监听
        speechRecognizer?.apply {
            try {
                cancel() // 取消当前识别任务
                VoiceLog.d("已取消识别任务")
            } catch (e: Exception) {
                VoiceLog.w("取消识别任务时出错: ${e.message}")
            }
            try {
                stopListening() // 停止监听
                VoiceLog.d("已停止监听")
            } catch (e: Exception) {
                VoiceLog.w("停止监听时出错: ${e.message}")
            }
        }
        
        // 停止波形动画
        waveformView?.setAnimationRunning(false)
        waveformView?.setAmplitude(0.1f)
        
        // 重置状态为空闲
        recognizerState = RecognizerState.IDLE
        VoiceLog.d("识别器状态: -> IDLE（用户停止监听）")
    }
    
    private fun updateListeningState(listening: Boolean) {
        isListening = listening
        
        // 更新UI状态（简化：只更新颜色，不显示状态文本）
        if (listening) {
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return SafeRecognitionListener(this)
    }
    
    /**
     * 安全的 RecognitionListener，使用 WeakReference 避免内存泄漏
     */
    private class SafeRecognitionListener(
        activity: VoiceRecognitionActivity
    ) : RecognitionListener {
        private val activityRef = WeakReference(activity)
        
        override fun onReadyForSpeech(params: Bundle?) {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                val runnable = object : Runnable {
                    override fun run() {
                        activityRef.get()?.let { act ->
                            if (act.isDestroyed || act.isFinishing) return
                            // 标记识别器已准备好
                            act.isRecognizerReady = true
                            VoiceLog.d("✅ 识别器已准备好，可以开始识别")
                            
                            // 取消连接超时任务
                            act.connectionTimeoutRunnable?.let { timeoutRunnable ->
                                act.handler.removeCallbacks(timeoutRunnable)
                                act.pendingRunnables.remove(timeoutRunnable)
                                act.connectionTimeoutRunnable = null
                            }
                            
                            // 如果正在启动识别（isStartingRecognition = true）且状态为监听中，立即启动识别
                            if (act.isStartingRecognition && act.recognizerState == RecognizerState.LISTENING) {
                                VoiceLog.d("识别器已准备好，立即启动识别")
                                // 注意：prepareAndStartRecognition 会在成功启动后重置 isStartingRecognition
                                act.prepareAndStartRecognition()
                            } else if (act.isListening && act.recognizerState == RecognizerState.LISTENING) {
                                // 如果已经在监听状态但未启动，也尝试启动
                                VoiceLog.d("识别器已准备好，启动识别（已在监听状态）")
                                act.prepareAndStartRecognition()
                            } else {
                                // 如果不在监听状态，重置启动标志
                                VoiceLog.d("识别器已准备好，但不在监听状态，重置启动标志")
                                act.isStartingRecognition = false
                                // 移除状态文本提示，专注于语音转文本
                                act.currentPartialText = ""
                            }
                        }
                    }
                }
                activity.safePost(runnable)
            }
        }

        override fun onBeginningOfSpeech() {
            // 移除状态文本提示，专注于语音转文本功能
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                val runnable = object : Runnable {
                    override fun run() {
                        activityRef.get()?.let { act ->
                            if (act.isDestroyed || act.isFinishing) return
                            act.currentPartialText = ""
                        }
                    }
                }
                activity.safePost(runnable)
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 更新波形动画：将音量值转换为振幅（0.0-1.0）
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                val runnable = object : Runnable {
                    override fun run() {
                        activityRef.get()?.let { act ->
                            if (act.isDestroyed || act.isFinishing) return
                            // RMS值通常在-10到10之间，转换为0.0-1.0的振幅
                            // 使用平滑算法避免动画抖动
                            val normalizedRms = ((rmsdB + 10f) / 20f).coerceIn(0.0f, 1.0f)
                            // 平滑处理：使用加权平均，避免突变
                            act.currentRmsValue = act.currentRmsValue * 0.7f + normalizedRms * 0.3f
                            // 确保最小振幅，避免完全静止
                            val amplitude = act.currentRmsValue.coerceIn(0.15f, 1.0f)
                            act.waveformView?.setAmplitude(amplitude)
                        }
                    }
                }
                activity.safePost(runnable)
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                val runnable = OnEndOfSpeechRunnable(activity)
                activity.safePost(runnable)
            }
        }

        override fun onError(error: Int) {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                // 重置准备状态（错误时连接可能断开）
                activity.isRecognizerReady = false
                val runnable = HandleErrorRunnable(activity, error)
                activity.safePost(runnable)
            }
        }

        override fun onResults(results: Bundle?) {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                val runnable = ProcessResultsRunnable(activity, results)
                activity.safePost(runnable)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                val runnable = ProcessPartialResultsRunnable(activity, partialResults)
                activity.safePost(runnable)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    
    /**
     * 静态内部类：处理语音结束
     */
    private class OnEndOfSpeechRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                // 移除"正在处理"状态提示，专注于显示识别结果
                activity.currentPartialText = ""
                if (activity.recognizedText.isNotEmpty()) {
                    activity.recognizedTextView.setText(activity.recognizedText)
                    activity.recognizedTextView.setSelection(activity.recognizedText.length)
                }
            }
        }
    }
    
    /**
     * 静态内部类：处理识别错误
     */
    private class HandleErrorRunnable(
        activity: VoiceRecognitionActivity,
        private val error: Int
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.handleRecognitionError(error)
            }
        }
    }
    
    /**
     * 静态内部类：处理识别结果
     */
    private class ProcessResultsRunnable(
        activity: VoiceRecognitionActivity,
        private val results: Bundle?
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.recognizerBusyRetryCount = 0
                activity.processRecognitionResults(results)
            }
        }
    }
    
    /**
     * 静态内部类：处理部分识别结果
     */
    private class ProcessPartialResultsRunnable(
        activity: VoiceRecognitionActivity,
        private val partialResults: Bundle?
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.processPartialResults(partialResults)
            }
        }
    }
    
    
    private fun adjustTextSize(adjustment: Float) {
        val currentSizeSp = recognizedTextView.textSize / resources.displayMetrics.scaledDensity
        val newSizeSp = currentSizeSp + adjustment
        // Optional: Add min/max size constraints if needed
        // val newSizeClamped = newSizeSp.coerceIn(12f, 64f)
        recognizedTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, newSizeSp)

        // Save the new font size
        settingsManager.setVoiceInputTextSize(newSizeSp)
    }
    
    private fun processRecognitionResults(results: Bundle?) {
        try {
            if (results == null) {
                VoiceLog.w("识别结果 Bundle 为 null")
                return
            }
            
            // 如果是小米 Aivs 服务，尝试从 RecognizeResult 中提取文本
            var newFinalText: String? = null
            if (isXiaomiAivsService) {
                newFinalText = extractTextFromXiaomiAivsResult(results)
                VoiceLog.d("从小米 Aivs RecognizeResult 提取文本: $newFinalText")
            }
            
            // 如果小米 Aivs 提取失败，或不是小米 Aivs，使用标准方式
            if (newFinalText.isNullOrEmpty()) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    newFinalText = matches[0]
                }
            }
            
            if (!newFinalText.isNullOrEmpty()) {
                
                // 检查最终结果是否与当前部分结果相同或相似（避免部分结果和最终结果重复）
                if (currentPartialText.isNotEmpty()) {
                    val partialSimilarity = calculateSimilarity(currentPartialText.trim(), newFinalText.trim())
                    VoiceLog.d("检查部分结果与最终结果相似度: partial='$currentPartialText', final='$newFinalText', similarity=$partialSimilarity")
                    
                    if (partialSimilarity > 0.9f) {
                        // 部分结果与最终结果高度相似（>90%），说明是同一段话的识别
                        // 只使用最终结果，清除部分结果，避免重复
                        VoiceLog.d("⚠️ 最终结果与部分结果高度相似（${(partialSimilarity * 100).toInt()}%），使用最终结果，清除部分结果")
                        currentPartialText = ""
                        // 继续处理最终结果
                    } else if (newFinalText.trim().contains(currentPartialText.trim()) || 
                               currentPartialText.trim().contains(newFinalText.trim())) {
                        // 包含关系，使用更完整的文本
                        VoiceLog.d("最终结果与部分结果有包含关系，使用更完整的文本")
                        currentPartialText = ""
                    } else {
                        // 不同内容，清除部分结果，使用最终结果
                        VoiceLog.d("最终结果与部分结果不同，清除部分结果")
                        currentPartialText = ""
                    }
                } else {
                    // 没有部分结果，直接清除
                    currentPartialText = ""
                }
                
                // 检查是否是完全重复的内容（与上一次识别结果完全相同）
                if (newFinalText.trim() == lastRecognizedText.trim()) {
                    VoiceLog.d("⚠️ 检测到完全重复的识别结果，忽略: '$newFinalText'")
                    // 完全重复，不更新文本，直接返回
                    return
                }
                
                // 检查最终结果是否已经包含在当前显示的文本中（避免重复追加）
                val currentDisplayText = recognizedTextView.text.toString().trim()
                if (currentDisplayText.isNotEmpty()) {
                    // 检查最终结果是否与当前显示文本相同或高度相似
                    val displaySimilarity = calculateSimilarity(currentDisplayText, newFinalText.trim())
                    if (displaySimilarity > 0.95f) {
                        VoiceLog.d("⚠️ 最终结果与当前显示文本高度相似（${(displaySimilarity * 100).toInt()}%），可能是重复，忽略: '$newFinalText'")
                        // 高度相似，可能是重复，不更新
                        return
                    }
                    // 检查最终结果是否已经包含在当前显示文本中
                    if (currentDisplayText.contains(newFinalText.trim()) && 
                        newFinalText.trim().length < currentDisplayText.length * 0.8) {
                        VoiceLog.d("⚠️ 最终结果已包含在当前显示文本中，忽略: '$newFinalText'")
                        // 已包含且不是更完整的版本，不更新
                        return
                    }
                }
                
                // 智能合并最终结果，使用编辑距离算法避免重复累积
                // 注意：如果最终结果与部分结果相同，应该直接使用最终结果，而不是追加
                val mergedText = if (recognizedText.isEmpty()) {
                    // 没有已确认文本，直接使用最终结果
                    newFinalText
                } else {
                    // 先检查包含关系（快速判断）
                    val isNewContainsOld = newFinalText.contains(recognizedText)
                    val isOldContainsNew = recognizedText.contains(newFinalText)
                    
                    if (isNewContainsOld && !isOldContainsNew) {
                        // 新文本包含旧文本，使用新文本（更完整，避免重复）
                        // 例如："你好" -> "你好你好"，使用"你好你好"
                        VoiceLog.d("新文本包含旧文本，使用新文本: '$newFinalText'")
                        newFinalText
                    } else if (isOldContainsNew && !isNewContainsOld) {
                        // 旧文本包含新文本，保持旧文本（更完整）
                        // 例如："你好你好" -> "你好"，保持"你好你好"
                        VoiceLog.d("旧文本包含新文本，保持旧文本: '$recognizedText'")
                        recognizedText
                    } else {
                        // 使用编辑距离算法判断相似度
                        val similarity = calculateSimilarity(recognizedText, newFinalText)
                        VoiceLog.d("文本相似度计算: recognizedText='$recognizedText', newFinalText='$newFinalText', similarity=$similarity")
                        
                        if (similarity > 0.85f) {
                            // 相似度 >85%，认为是修正或重复，使用新文本（避免重复累积）
                            VoiceLog.d("相似度 >85%，使用新文本作为修正: '$newFinalText'")
                            newFinalText
                        } else if (similarity > 0.5f) {
                            // 相似度 50%-85%，可能是部分重复，检查是否有新增内容
                            val newWords = newFinalText.split(" ").filter { it.isNotEmpty() }
                            val oldWords = recognizedText.split(" ").filter { it.isNotEmpty() }
                            val newUniqueWords = newWords.filter { !oldWords.contains(it) }
                            
                            if (newUniqueWords.isEmpty()) {
                                // 没有新增内容，认为是重复，使用新文本
                                VoiceLog.d("相似度 50%-85% 且无新增内容，使用新文本: '$newFinalText'")
                                newFinalText
                            } else {
                                // 有新增内容，追加新词
                                VoiceLog.d("相似度 50%-85% 但有新增内容，追加: '$recognizedText' + '${newUniqueWords.joinToString(" ")}'")
                                "$recognizedText ${newUniqueWords.joinToString(" ")}"
                            }
                        } else {
                            // 相似度 <50%，认为是新内容，追加
                            VoiceLog.d("相似度 <50%，追加新文本: '$recognizedText' + '$newFinalText'")
                            "$recognizedText $newFinalText"
                        }
                    }
                }
                
                // 更新识别的文本
                recognizedText = mergedText
                lastRecognizedText = newFinalText // 保存本次识别结果，用于下次去重
            
                // 使用累积的文本更新显示（只显示已确认的文本）
                recognizedTextView.setText(recognizedText)
                recognizedTextView.setSelection(recognizedText.length)
                
                // 立即重启识别，以实现连续听写（移除状态提示）
                
                // 确保"说完了"按钮可用
                doneButton.isEnabled = true
                VoiceLog.d("✓ 识别成功，当前累积文本: '$recognizedText'")
                
                // 如果用户手动暂停了识别，不自动重启
                if (isPausedByUser) {
                    VoiceLog.d("用户已暂停识别，不自动重启")
                    return
                }
                
                // 设置冷却状态，防止连续识别导致 ERROR_RECOGNIZER_BUSY
                recognizerState = RecognizerState.COOLING_DOWN
                VoiceLog.d("识别器状态: LISTENING -> COOLING_DOWN（强制冷却 ${COOLING_DOWN_DELAY_MS}ms）")
                
                // 冷却后重启识别，实现连续听写（避免 ERROR_RECOGNIZER_BUSY）
                val coolingRunnable = CoolingDownCompleteRunnable(this)
                safePostDelayed(COOLING_DOWN_DELAY_MS, coolingRunnable)
            }
        } catch (e: Exception) {
            VoiceLog.e("处理识别结果失败: ${e.message}")
            showError("处理识别结果失败")
        }
    }
    
    private fun processPartialResults(partialResults: Bundle?) {
        if (partialResults == null) {
            return
        }
        
        // 如果是小米 Aivs 服务，尝试从 RecognizeResult 中提取文本
        var partialText: String? = null
        if (isXiaomiAivsService) {
            partialText = extractTextFromXiaomiAivsResult(partialResults)
            VoiceLog.d("从小米 Aivs 部分结果提取文本: $partialText")
        }
        
        // 如果小米 Aivs 提取失败，或不是小米 Aivs，使用标准方式
        if (partialText.isNullOrEmpty()) {
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                partialText = matches[0]
            }
        }
        
        if (!partialText.isNullOrEmpty()) {
            
            // 更新当前部分识别结果
            if (partialText.isNotEmpty()) {
                // 检查部分结果是否有更新（避免重复更新相同内容）
                if (partialText != currentPartialText) {
                    currentPartialText = partialText
                    
                    // 智能合并：已确认的文本 + 当前部分识别结果
                    // 注意：部分结果只是临时显示，不会真正追加到 recognizedText 中
                    val displayText = if (recognizedText.isEmpty()) {
                        // 没有已确认文本，直接显示部分结果
                        partialText
                    } else {
                        // 有已确认文本，需要智能合并
                        if (partialText.startsWith(recognizedText)) {
                            // 部分结果以已确认文本开头，直接使用部分结果（更完整）
                            // 例如：recognizedText="你好"，partialText="你好世界"，显示"你好世界"
                            partialText
                        } else if (recognizedText.contains(partialText)) {
                            // 部分结果已包含在已确认文本中，只显示已确认文本
                            // 例如：recognizedText="你好世界"，partialText="你好"，只显示"你好世界"
                            recognizedText
                        } else {
                            // 部分结果与已确认文本不同，检查是否是新增内容
                            // 使用相似度判断，避免重复
                            val similarity = calculateSimilarity(recognizedText, partialText)
                            if (similarity > 0.8f) {
                                // 高度相似，可能是同一段话的不同识别，只显示部分结果（更完整）
                                VoiceLog.d("部分结果与已确认文本高度相似（${(similarity * 100).toInt()}%），使用部分结果: '$partialText'")
                                partialText
                            } else {
                                // 不同内容，追加显示（但不会真正追加到 recognizedText）
                                // 例如：recognizedText="你好"，partialText="世界"，显示"你好 世界"（临时）
                                "$recognizedText $partialText"
                            }
                        }
                    }
                    
                    // 实时更新显示文本（立即在主线程更新，实现流式显示）
                    recognizedTextView.setText(displayText)
                    recognizedTextView.setSelection(displayText.length)
                    
                    VoiceLog.d("↻ 部分结果更新: '$partialText' → 显示: '$displayText'")
                }
            } else {
                // 部分结果为空，清空当前部分结果
                if (currentPartialText.isNotEmpty()) {
                    currentPartialText = ""
                    // 只显示已确认的文本
                    if (recognizedText.isNotEmpty()) {
                        recognizedTextView.setText(recognizedText)
                        recognizedTextView.setSelection(recognizedText.length)
                    }
                }
            }
        }
    }
    
    private fun handleRecognitionError(error: Int) {
        // 处理客户端错误（通常表示连接问题）
        if (error == SpeechRecognizer.ERROR_CLIENT) {
            VoiceLog.w("识别器连接错误，尝试重新连接 (重试次数: $recognizerBusyRetryCount/$MAX_RECOGNIZER_BUSY_RETRIES)")
            
            // 设置冷却状态，防止连续错误导致忙碌
            recognizerState = RecognizerState.COOLING_DOWN
            VoiceLog.d("识别器状态: LISTENING -> COOLING_DOWN（ERROR_CLIENT）")
            
            // 如果超过最大重试次数，尝试使用系统语音输入
            if (recognizerBusyRetryCount >= MAX_RECOGNIZER_BUSY_RETRIES) {
                VoiceLog.e("连接重试次数已达上限，切换到系统语音输入")
                recognizerBusyRetryCount = 0 // 重置计数器
                recognizerState = RecognizerState.IDLE // 重置状态
                isStartingRecognition = false // 重置启动标志
                val updateRunnable = UpdateTextRunnable(
                    this,
                    "连接失败，切换到系统语音输入...",
                    android.R.color.holo_orange_light
                )
                safePost(updateRunnable)
                // 标记需要完全销毁识别器
                shouldDestroyRecognizerOnRelease = true
                // 完全释放识别器
                releaseSpeechRecognizer()
                // 延迟后尝试系统语音输入
                val trySystemRunnable = TrySystemVoiceInputRunnable(this)
                safePostDelayed(1000, trySystemRunnable)
                return
            }
            
            // 增加重试计数
            recognizerBusyRetryCount++
            
            // 标记需要完全销毁识别器（连接错误需要重新创建）
            shouldDestroyRecognizerOnRelease = true
            // 完全释放识别器
            releaseSpeechRecognizer()
            
            // 更新UI提示
            val updateRunnable = UpdateTextRunnable(
                this,
                "连接失败，正在重试... ($recognizerBusyRetryCount/$MAX_RECOGNIZER_BUSY_RETRIES)",
                android.R.color.holo_orange_light
            )
            safePost(updateRunnable)
            
            // 延迟后重新启动识别（给识别器足够时间释放和重新连接）
            val retryDelay = when {
                Build.MANUFACTURER.lowercase().contains("xiaomi") -> 2000L // 小米设备延迟2秒
                Build.MANUFACTURER.lowercase().contains("meizu") -> 3000L // 魅族手机延迟3秒
                Build.MANUFACTURER.lowercase().contains("oppo") -> 3000L // OPPO手机延迟3秒
                else -> 2000L // 其他手机延迟2秒
            }
            
            // 使用冷却完成回调来重启识别
            val coolingRunnable = CoolingDownCompleteRunnable(this)
            safePostDelayed(retryDelay, coolingRunnable)
            
            return
        }
        
        // 处理识别器忙碌错误 - 这是可恢复的错误，需要先释放识别器再重试
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            VoiceLog.w("识别器忙碌，尝试释放并重试 (重试次数: $recognizerBusyRetryCount/$MAX_RECOGNIZER_BUSY_RETRIES)")
            
            // 设置冷却状态，防止立即重试导致更严重的忙碌
            recognizerState = RecognizerState.COOLING_DOWN
            VoiceLog.d("识别器状态: LISTENING -> COOLING_DOWN（ERROR_RECOGNIZER_BUSY）")
            
            // 如果超过最大重试次数，尝试使用系统语音输入
            if (recognizerBusyRetryCount >= MAX_RECOGNIZER_BUSY_RETRIES) {
                VoiceLog.e("识别器忙碌重试次数已达上限，切换到系统语音输入")
                recognizerBusyRetryCount = 0 // 重置计数器
                recognizerState = RecognizerState.IDLE // 重置状态
                isStartingRecognition = false // 重置启动标志
                val updateRunnable = UpdateTextRunnable(
                    this,
                    "识别器忙碌，切换到系统语音输入...",
                    android.R.color.holo_orange_light
                )
                safePost(updateRunnable)
                // 标记需要完全销毁识别器
                shouldDestroyRecognizerOnRelease = true
                // 完全释放识别器
                releaseSpeechRecognizer()
                // 延迟后尝试系统语音输入
                val trySystemRunnable = TrySystemVoiceInputRunnable(this)
                safePostDelayed(1000, trySystemRunnable)
                return
            }
            
            // 增加重试计数
            recognizerBusyRetryCount++
            
            // 标记需要完全销毁识别器（忙碌错误需要重新创建）
            shouldDestroyRecognizerOnRelease = true
            // 完全释放识别器
            releaseSpeechRecognizer()
            
            // 更新UI提示
            val updateRunnable = UpdateTextRunnable(
                this,
                "识别器忙碌，正在重试... ($recognizerBusyRetryCount/$MAX_RECOGNIZER_BUSY_RETRIES)",
                android.R.color.holo_orange_light
            )
            safePost(updateRunnable)
            
            // 延迟后重新启动识别（给识别器足够时间释放和冷却）
            // 魅族和OPPO设备需要更长的延迟时间
            val retryDelay = when {
                Build.MANUFACTURER.lowercase().contains("meizu") -> 3000L // 魅族手机延迟3秒
                Build.MANUFACTURER.lowercase().contains("oppo") -> 3000L // OPPO手机延迟3秒
                else -> 2000L // 其他手机延迟2秒
            }
            
            // 使用冷却完成回调来重启识别
            val coolingRunnable = CoolingDownCompleteRunnable(this)
            safePostDelayed(retryDelay, coolingRunnable)
            
            return
        }
        
        // 重置识别器忙碌重试计数（其他错误时重置）
        recognizerBusyRetryCount = 0
        
        // 对于非致命错误，自动重启监听（ERROR_CLIENT已在上面单独处理）
        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
            
            VoiceLog.d("Non-critical error ($error), restarting listener.")
            
            // 设置冷却状态，防止连续错误导致忙碌
            recognizerState = RecognizerState.COOLING_DOWN
            VoiceLog.d("识别器状态: LISTENING -> COOLING_DOWN（非致命错误）")
            
            // 更新UI颜色提示（移除状态文本）
            micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))

            // 在安全延迟后重启识别过程（使用冷却完成回调）
            val coolingRunnable = CoolingDownCompleteRunnable(this)
            safePostDelayed(500, coolingRunnable) 

            return
        }

        // 对于其他致命错误，尝试降级处理而不是直接显示错误
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "音频错误，尝试系统语音输入..."
            SpeechRecognizer.ERROR_NETWORK -> "网络错误，尝试系统语音输入..."
            SpeechRecognizer.ERROR_CLIENT -> "连接错误，尝试系统语音输入..."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
            SpeechRecognizer.ERROR_SERVER -> "服务器错误，尝试系统语音输入..."
            else -> "识别错误 (代码: $error)，尝试系统语音输入..."
        }
        
        VoiceLog.e("Fatal Speech Recognition Error: $errorMessage (代码: $error)")
        
        // 对于可以降级的错误，尝试系统语音输入
        if (error == SpeechRecognizer.ERROR_AUDIO ||
            error == SpeechRecognizer.ERROR_NETWORK ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_SERVER) {
            // 重置状态
            recognizerState = RecognizerState.IDLE
            isStartingRecognition = false
            isRecognizerReady = false
            
            // 更新UI提示
            val updateRunnable = UpdateTextRunnable(
                this,
                errorMessage,
                android.R.color.holo_orange_light
            )
            safePost(updateRunnable)
            
            // 标记需要完全销毁识别器
            shouldDestroyRecognizerOnRelease = true
            // 释放识别器
            releaseSpeechRecognizer()
            
            // 延迟后尝试系统语音输入
            val trySystemRunnable = TrySystemVoiceInputRunnable(this)
            safePostDelayed(1000, trySystemRunnable)
        } else {
            // 对于权限错误等无法降级的错误，显示错误提示
            showError(errorMessage)
        }
    }

    private fun trySystemVoiceInput() {
        VoiceLog.d("尝试使用系统语音输入Intent")
        
        // 创建系统语音识别Intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            // 检查是否有应用能处理语音识别Intent
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            
            if (activities.isNotEmpty()) {
                // 有可用的语音识别应用，启动系统语音输入
                listeningText.text = "启动系统语音输入..."
                startActivityForResult(intent, SYSTEM_VOICE_REQUEST_CODE)
            } else {
                // 没有可用的语音识别应用
                showVoiceRecognitionNotAvailableDialog()
            }
        } catch (e: Exception) {
            VoiceLog.e("启动系统语音输入失败: ${e.message}")
            showVoiceRecognitionNotAvailableDialog()
        }
    }

    private fun showVoiceRecognitionNotAvailableDialog() {
        // 不再显示弹窗，直接切换到手动输入模式
        VoiceLog.d("语音识别不可用，自动切换到手动输入模式")

        // 允许用户手动输入文本
        listeningText.text = "请在下方输入文本，然后点击'说完了'"
        micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        recognizedTextView.requestFocus()

        // 显示键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(recognizedTextView, InputMethodManager.SHOW_IMPLICIT)

        // 确保"说完了"按钮可用
        doneButton.isEnabled = true
        VoiceLog.d("已切换到手动输入模式")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SYSTEM_VOICE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    val recognizedSystemText = results[0]
                    VoiceLog.d("系统语音输入结果: $recognizedSystemText")
                    
                    // 将系统语音识别结果添加到现有文本
                    recognizedText = if (recognizedText.isEmpty()) {
                        recognizedSystemText
                    } else {
                        "$recognizedText $recognizedSystemText"
                    }
                    
                    recognizedTextView.setText(recognizedText)
                    recognizedTextView.setSelection(recognizedText.length)
                    listeningText.text = "语音输入完成，点击'说完了'开始搜索"
                    
                    // 显示绿色状态表示成功
                    micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    
                    // 更新"说完了"按钮的可见性和状态
                    doneButton.isEnabled = true
                    VoiceLog.d("系统语音输入成功，当前文本: '$recognizedText'")
                } else {
                    listeningText.text = "未识别到语音，请重试"
                    micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                }
            } else {
                listeningText.text = "语音输入已取消"
                micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start recognition
                startVoiceRecognition()
            } else {
                // Permission denied
                Toast.makeText(this, "需要录音权限才能使用语音输入", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), VOICE_RECOGNITION_REQUEST_CODE)
    }
    
    private fun finishRecognition() {
        val finalText = recognizedTextView.text.toString().trim()
        VoiceLog.d("finishRecognition 被调用，文本长度: ${finalText.length}，内容: '$finalText'")
        
        if (finalText.isNotEmpty()) {
            VoiceLog.d("识别完成，文本: $finalText")
            // 发送广播，命令SimpleModeService启动搜索并自我销毁
            triggerSearchAndDestroySimpleMode(finalText)
        } else {
            VoiceLog.d("识别完成，但无文本。")
            Toast.makeText(this, "没有识别到文本", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun triggerSearchAndDestroySimpleMode(query: String) {
        try {
            VoiceLog.d("准备启动语音搜索，查询: '$query'")

            // 启动DualFloatingWebViewService，默认加载百度AI对话界面（文心一言）和Google AI对话界面（Gemini）
            val serviceIntent = Intent(this, com.example.aifloatingball.service.DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("voice_search_mode", true) // 标记为语音搜索模式
                putExtra("voice_search_engines", arrayOf("百度AI", "Google AI")) // 默认两个AI对话界面：百度AI（文心一言）和Google AI（Gemini）
                putExtra("window_count", 2) // 设置窗口数量为2
                putExtra("use_card_view_mode", false) // 使用横向拖动模式，方便查看多个网页
                putExtra("search_source", "语音输入")
            }
            startService(serviceIntent)
            VoiceLog.d("已启动DualFloatingWebViewService进行语音搜索")
            
        } catch (e: Exception) {
            VoiceLog.e("启动语音搜索失败: ${e.message}", e)
            Toast.makeText(this, "启动搜索失败", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopSimpleModeService() {
        try {
            // 发送广播通知SimpleModeService关闭
            val closeIntent = Intent("com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE")
            sendBroadcast(closeIntent)
            VoiceLog.d("已发送关闭简易模式广播")
        } catch (e: Exception) {
            VoiceLog.e("发送关闭广播失败: ${e.message}")
        }
    }
    
    /**
     * 保存语音转文本到统一收藏系统和语音文本标签管理器
     * 添加防抖机制，防止重复调用
     */
    private fun saveVoiceText() {
        // 先检查生命周期
        if (isFinishing || isDestroyed) {
            VoiceLog.d("Activity 已销毁，忽略保存操作")
            return
        }
        
        // 防抖：如果正在保存，显示提示而不是静默忽略
        if (isSavingVoiceText) {
            VoiceLog.d("正在保存中，显示提示")
            Toast.makeText(this, "正在保存中...", Toast.LENGTH_SHORT).show()
            return
        }
        
        val text = recognizedTextView.text.toString().trim()
        
        VoiceLog.d("开始保存语音文本，文本长度: ${text.length}")
        
        // 设置防抖标志并立即禁用按钮，防止重复点击
        isSavingVoiceText = true
        saveButton.isEnabled = false // 立即禁用按钮，给用户明确反馈
        
        // 保存时先停止监听，避免状态更新覆盖保存提示
        val wasListening = isListening
        if (wasListening) {
            stopListening()
        }
        
        if (text.isEmpty()) {
            VoiceLog.w("文本为空，无法保存")
            // 使用finally确保状态重置
            try {
                val warningRunnable = ShowEmptyTextWarningRunnable(this, wasListening)
                safePost(warningRunnable)
            } finally {
                // 1秒后重置状态和按钮
                resetSaveButtonState()
            }
            return
        }
        
        try {
            // 1. 保存到统一收藏管理器
            val collectionManager = UnifiedCollectionManager.getInstance(this)
            
            // 创建预览文本（前200字符）
            val preview = text.take(200) + if (text.length > 200) "..." else ""
            
            // 创建标题（前50字符）
            val title = text.take(50) + if (text.length > 50) "..." else ""
            
            VoiceLog.d("创建收藏项: title='$title', textLength=${text.length}")
            
            // 创建收藏项
            val collectionItem = UnifiedCollectionItem(
                title = title,
                content = text, // 完整内容
                preview = preview,
                collectionType = CollectionType.VOICE_TO_TEXT,
                sourceLocation = "语音Tab",
                sourceDetail = "语音转文本",
                collectedTime = System.currentTimeMillis(),
                customTags = listOf("语音转文", "语音输入"),
                priority = Priority.NORMAL,
                completionStatus = CompletionStatus.NOT_STARTED,
                likeLevel = 0,
                emotionTag = EmotionTag.NEUTRAL,
                isEncrypted = false,
                reminderTime = null,
                extraData = mapOf(
                    "textLength" to text.length.toString(),
                    "source" to "VoiceRecognitionActivity"
                )
            )
            
            VoiceLog.d("收藏项创建完成: id=${collectionItem.id}, type=${collectionItem.collectionType?.name}")
            
            // 保存到统一收藏管理器（这是主要存储，用于AI助手tab的任务场景显示）
            val collectionSuccess = collectionManager.addCollection(collectionItem)
            VoiceLog.d("统一收藏管理器保存结果: $collectionSuccess")
            
            // 2. 同时保存到VoiceTextTagManager（供VoiceTextFragment显示，可选）
            val voiceTextTagManager = VoiceTextTagManager(this)
            val voiceTextSuccess = voiceTextTagManager.saveTextToTag(text)
            VoiceLog.d("语音文本标签管理器保存结果: $voiceTextSuccess")
            
            // 只要统一收藏管理器保存成功，就认为保存成功（这是主要存储）
            if (collectionSuccess) {
                // 立即验证保存是否成功
                val savedItem = collectionManager.getCollectionById(collectionItem.id)
                if (savedItem != null) {
                    VoiceLog.d("✅ 验证成功：收藏项已保存到统一收藏管理器")
                    VoiceLog.d("  - 保存的标题: ${savedItem.title}")
                    VoiceLog.d("  - 保存的类型: ${savedItem.collectionType?.name}")
                    VoiceLog.d("  - 保存的内容长度: ${savedItem.content.length}")
                    
                    // 验证类型是否正确 - 立即查询验证
                    val voiceToTextCollections = collectionManager.getCollectionsByType(CollectionType.VOICE_TO_TEXT)
                    VoiceLog.d("✅ 立即查询验证：当前VOICE_TO_TEXT类型收藏总数: ${voiceToTextCollections.size}")
                    if (voiceToTextCollections.isNotEmpty()) {
                        voiceToTextCollections.forEachIndexed { index, item ->
                            VoiceLog.d("  - 收藏项[$index]: id=${item.id}, title='${item.title}', type=${item.collectionType?.name}")
                        }
                    } else {
                        // 如果没有找到，检查所有收藏项
                        val allCollections = collectionManager.getAllCollections()
                        VoiceLog.d("⚠️ 未找到VOICE_TO_TEXT类型收藏，当前所有收藏项总数: ${allCollections.size}")
                        if (allCollections.isNotEmpty()) {
                            val typeCounts = allCollections.groupingBy { it.collectionType?.name ?: "null" }.eachCount()
                            VoiceLog.d("所有收藏项的类型分布:")
                            typeCounts.forEach { (typeName, count) ->
                                VoiceLog.d("  - $typeName: $count 条")
                            }
                            VoiceLog.d("所有收藏项详情:")
                            allCollections.forEachIndexed { index, item ->
                                VoiceLog.d("  - 收藏项[$index]: id=${item.id}, title='${item.title}', type=${item.collectionType?.name ?: "null"}")
                            }
                        }
                    }
                    
                    // 发送广播通知收藏更新，让AI助手tab中的语音转文标签自动刷新
                    try {
                        val intent = Intent("com.example.aifloatingball.COLLECTION_UPDATED").apply {
                            putExtra("collection_type", CollectionType.VOICE_TO_TEXT.name)
                            putExtra("action", "add")
                            putExtra("collection_id", collectionItem.id)
                            // 添加包名，确保广播能正确传递
                            setPackage(packageName)
                        }
                        sendBroadcast(intent)
                        VoiceLog.d("✅ 已发送收藏更新广播: type=${CollectionType.VOICE_TO_TEXT.name}, id=${collectionItem.id}, package=${packageName}")
                    } catch (e: Exception) {
                        VoiceLog.e("❌ 发送收藏更新广播失败", e)
                        e.printStackTrace()
                    }
                    
                    // 显示成功提示（在状态文本中显示，确保用户能看到）
                    val successRunnable = ShowSaveSuccessRunnable(this, wasListening)
                    safePost(successRunnable)
                    VoiceLog.d("✅ 语音文本保存成功，已显示提示")
                } else {
                    VoiceLog.e("❌ 验证失败：收藏项未找到，ID=${collectionItem.id}")
                    val errorRunnable = ShowSaveErrorRunnable(this, "保存失败：验证未通过", wasListening)
                    safePost(errorRunnable)
                }
            } else {
                // 统一收藏管理器保存失败
                val errorMsg = if (!voiceTextSuccess) {
                    "保存失败：两个存储系统都失败"
                } else {
                    "保存失败：统一收藏管理器失败（数据未保存到任务场景）"
                }
                VoiceLog.e("❌ 语音文本保存失败: collectionSuccess=$collectionSuccess, voiceTextSuccess=$voiceTextSuccess")
                val errorRunnable = ShowSaveErrorRunnable(this, errorMsg, wasListening)
                safePost(errorRunnable)
            }
        } catch (e: Exception) {
            VoiceLog.e("❌ 保存语音文本异常", e)
            e.printStackTrace()
            val errorMsg = "保存失败: ${e.message?.take(20) ?: "未知错误"}"
            val errorRunnable = ShowSaveErrorRunnable(this, errorMsg, wasListening)
            safePost(errorRunnable)
        } finally {
            // 无论成功失败，1秒后重置状态和按钮（缩短冷却时间，提升用户体验）
            resetSaveButtonState()
        }
    }
    
    /**
     * 重置保存按钮状态（1秒冷却）
     */
    private fun resetSaveButtonState() {
        val resetRunnable = ResetSaveButtonStateRunnable(this)
        safePostDelayed(1000, resetRunnable) // 1秒冷却足够
    }
    
    private fun showError(message: String) {
        // 不显示弹窗，只更新状态文本，提供更友好的错误提示
        VoiceLog.e("显示错误: $message")
        listeningText.text = "⚠️ $message"
        micContainer.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        
        // 不自动关闭，让用户可以选择重试或手动输入
        // 如果用户需要，可以点击麦克风重试
    }
    
    /**
     * 启动语音识别服务检测 Activity
     */
    private fun startDetectionActivity() {
        try {
            val intent = Intent(this, SpeechRecognitionDetectionActivity::class.java)
            startActivity(intent)
            VoiceLog.d("已启动语音识别服务检测")
        } catch (e: Exception) {
            VoiceLog.e("启动检测 Activity 失败", e)
            Toast.makeText(this, "启动检测失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 释放语音识别器
     * 根据 shouldDestroyRecognizerOnRelease 标志决定是复用还是完全销毁
     */
    private fun releaseSpeechRecognizer() {
        // 取消连接超时任务
        connectionTimeoutRunnable?.let { timeoutRunnable ->
            handler.removeCallbacks(timeoutRunnable)
            pendingRunnables.remove(timeoutRunnable)
            connectionTimeoutRunnable = null
        }
        
        // 重置准备状态和启动标志
        isRecognizerReady = false
        
        speechRecognizer?.apply {
            try {
                cancel() // 先取消当前识别
            } catch (e: Exception) {
                VoiceLog.w("取消识别器时出错: ${e.message}")
            }
            
            // 如果需要完全销毁（错误情况下），则销毁识别器
            if (shouldDestroyRecognizerOnRelease) {
                try {
                    destroy() // 完全销毁识别器
                    VoiceLog.d("语音识别器已完全销毁（错误恢复）")
                } catch (e: Exception) {
                    VoiceLog.w("销毁识别器时出错: ${e.message}")
                }
            } else {
                VoiceLog.d("语音识别器已取消（保持引用以便复用）")
            }
        }
        
        // 如果需要完全销毁，则设置为 null
        if (shouldDestroyRecognizerOnRelease) {
            speechRecognizer = null
        }
        
        // 重置销毁标志
        shouldDestroyRecognizerOnRelease = false
    }
    
    /**
     * 完全销毁语音识别器（仅在 Activity 销毁时调用）
     */
    private fun destroySpeechRecognizer() {
        // 取消连接超时任务
        connectionTimeoutRunnable?.let { timeoutRunnable ->
            handler.removeCallbacks(timeoutRunnable)
            pendingRunnables.remove(timeoutRunnable)
            connectionTimeoutRunnable = null
        }
        
        // 重置准备状态
        isRecognizerReady = false
        
        speechRecognizer?.apply {
            try {
                cancel() // 先取消当前识别
            } catch (e: Exception) {
                VoiceLog.w("取消识别器时出错: ${e.message}")
            }
            try {
                destroy() // 然后销毁识别器
            } catch (e: Exception) {
                VoiceLog.w("销毁识别器时出错: ${e.message}")
            }
        }
        speechRecognizer = null
        VoiceLog.d("语音识别器已完全销毁")
    }

    
    override fun finish() {
        super.finish()
        // 设置退出动画
        overridePendingTransition(0, R.anim.slide_down)
    }
    
    /**
     * 检测是否是小米 Aivs 服务
     * 简化逻辑：只要检测到小米设备和小米语音服务包，就强制使用 Aivs
     * 避免小米国际版因预装 Google App 而被误判为 Google 服务
     */
    private fun detectXiaomiAivsService(): Boolean {
        try {
            // 简化逻辑：只要检测到小米设备和小米语音服务包，就强制用 Aivs
            val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                           Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                           Build.BRAND.equals("Redmi", ignoreCase = true)
            
            val hasAivs = hasXiaomiSpeechService(this) // 只检测核心包
            
            VoiceLog.d("小米检测: isXiaomi=$isXiaomi, hasAivs=$hasAivs")
            
            val isAivs = isXiaomi && hasAivs
            
            if (isAivs) {
                VoiceLog.d("✅ 检测到小米 Aivs 服务，强制使用 Aivs（忽略 Google 服务检测）")
            } else {
                VoiceLog.d("❌ 未检测到小米 Aivs 服务: isXiaomi=$isXiaomi, hasAivs=$hasAivs")
            }
            
            return isAivs
        } catch (e: Exception) {
            VoiceLog.e("检测小米 Aivs 服务时出错: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 检查是否是强依赖 Google 的方案
     */
    private fun hasGoogleSpeechLikeService(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val googleCandidates = listOf(
            "com.google.android.googlequicksearchbox",   // Google App
            "com.google.android.tts",                    // Text-to-speech (间接参考)
            "com.google.android.apps.speechservices",    // Speech Services by Google
        )
        return googleCandidates.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * 检查小米语音服务包是否存在
     */
    private fun hasXiaomiSpeechService(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val xiaomiSpeechPackages = listOf(
            "com.xiaomi.mibrain.speech",      // 小米语音服务（Aivs）- 这是关键包
            "com.miui.voiceassist",            // 小爱同学
            "com.xiaomi.voiceassist"           // 小米语音助手
        )
        var foundPackage: String? = null
        val hasService = xiaomiSpeechPackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                foundPackage = pkg
                true
            } catch (e: Exception) {
                false
            }
        }
        if (foundPackage != null) {
            VoiceLog.d("✅ 找到小米语音服务包: $foundPackage")
        } else {
            VoiceLog.d("❌ 未找到小米语音服务包")
        }
        return hasService
    }
    
    /**
     * 从小米 Aivs 的 RecognizeResult Bundle 中提取文本
     * 小米 Aivs 可能使用不同的 key 来存储识别结果
     */
    private fun extractTextFromXiaomiAivsResult(bundle: Bundle): String? {
        try {
            // 方法1: 尝试标准的 RESULTS_RECOGNITION
            val standardResults = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!standardResults.isNullOrEmpty()) {
                VoiceLog.d("从标准 RESULTS_RECOGNITION 获取文本: ${standardResults[0]}")
                return standardResults[0]
            }
            
            // 方法2: 尝试可能的 Aivs 特定 key
            val possibleKeys = listOf(
                "results_recognition",           // 可能的变体
                "recognition_result",            // 识别结果
                "text",                          // 文本
                "transcript",                    // 转录文本
                "result",                        // 结果
                "speech_result",                 // 语音结果
                "aivs_result",                   // Aivs 结果
                "xiaomi_result"                  // 小米结果
            )
            
            for (key in possibleKeys) {
                try {
                    // 尝试作为 String 获取
                    val text = bundle.getString(key)
                    if (!text.isNullOrEmpty()) {
                        VoiceLog.d("从 key '$key' 获取文本: $text")
                        return text
                    }
                    
                    // 尝试作为 StringArrayList 获取
                    val textList = bundle.getStringArrayList(key)
                    if (!textList.isNullOrEmpty()) {
                        VoiceLog.d("从 key '$key' (ArrayList) 获取文本: ${textList[0]}")
                        return textList[0]
                    }
                } catch (e: Exception) {
                    // 忽略单个 key 的错误，继续尝试下一个
                }
            }
            
            // 方法3: 遍历所有 key，查找可能的文本内容
            VoiceLog.d("尝试遍历 Bundle 中的所有 key...")
            val allKeys = bundle.keySet()
            VoiceLog.d("Bundle 包含的 keys: $allKeys")
            
            for (key in allKeys) {
                try {
                    val value = bundle.get(key)
                    if (value is String && value.isNotEmpty() && value.length > 1) {
                        // 如果值看起来像文本（长度>1且包含中文字符或常见字符）
                        if (value.any { it.isLetterOrDigit() || it.isWhitespace() }) {
                            VoiceLog.d("从 key '$key' 找到可能的文本: $value")
                            // 如果 key 名称包含 result、text、transcript 等关键词，优先返回
                            val keyLower = key.lowercase()
                            if (keyLower.contains("result") || keyLower.contains("text") || 
                                keyLower.contains("transcript") || keyLower.contains("recognition")) {
                                VoiceLog.d("✅ 从关键 key '$key' 提取文本: $value")
                                return value
                            }
                        }
                    } else if (value is ArrayList<*> && value.isNotEmpty()) {
                        val firstItem = value[0]
                        if (firstItem is String && firstItem.isNotEmpty()) {
                            VoiceLog.d("从 key '$key' (ArrayList) 找到可能的文本: $firstItem")
                            // ArrayList 中的第一个字符串项，很可能是结果
                            return firstItem
                        }
                    }
                } catch (e: Exception) {
                    // 忽略单个 key 的错误
                    VoiceLog.w("读取 key '$key' 时出错: ${e.message}")
                }
            }
            
            VoiceLog.w("未能从小米 Aivs RecognizeResult 中提取文本，Bundle keys: $allKeys")
            return null
        } catch (e: Exception) {
            VoiceLog.e("提取小米 Aivs 文本时出错: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 安全地执行延迟任务，使用静态内部类避免内存泄漏
     */
    private fun safePostDelayed(delayMillis: Long, runnable: Runnable) {
        pendingRunnables.add(runnable)
        handler.postDelayed(runnable, delayMillis)
    }
    
    /**
     * 安全地执行任务，使用静态内部类避免内存泄漏
     */
    private fun safePost(runnable: Runnable) {
        pendingRunnables.add(runnable)
        handler.post(runnable)
    }
    
    /**
     * 静态内部类：重试启动语音识别
     */
    private class RetryStartRecognitionRunnable(
        activity: VoiceRecognitionActivity,
        private val delay: Long
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                
                // 确保启动标志已重置
                activity.isStartingRecognition = false
                
                // 如果仍在监听状态，重试启动识别
                if (activity.isListening) {
                    VoiceLog.d("重试启动语音识别（延迟: ${delay}ms）")
                    activity.startVoiceRecognition()
                } else {
                    VoiceLog.d("不再监听，跳过重试")
                }
            }
        }
    }
    
    /**
     * 静态内部类：更新UI文本
     */
    private class UpdateTextRunnable(
        activity: VoiceRecognitionActivity,
        private val text: String,
        private val colorResId: Int
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (!activity.isDestroyed && !activity.isFinishing) {
                    activity.listeningText.text = text
                    activity.micContainer.setCardBackgroundColor(
                        ContextCompat.getColor(activity, colorResId)
                    )
                }
            }
        }
    }
    
    /**
     * 静态内部类：显示Toast
     */
    private class ShowToastRunnable(
        activity: VoiceRecognitionActivity,
        private val message: String,
        private val duration: Int
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (!activity.isDestroyed && !activity.isFinishing) {
                    Toast.makeText(activity, message, duration).show()
                }
            }
        }
    }
    
    /**
     * 静态内部类：恢复状态
     */
    private class RestoreStateRunnable(
        activity: VoiceRecognitionActivity,
        private val wasListening: Boolean
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.isSavingVoiceText = false // 重置防抖标志
                activity.saveButton.isEnabled = true // 重新启用按钮
                if (wasListening && activity.isListening) {
                    activity.micContainer.setCardBackgroundColor(
                        ContextCompat.getColor(activity, android.R.color.holo_green_light)
                    )
                } else {
                    activity.listeningText.text = "识别已暂停"
                    activity.micContainer.setCardBackgroundColor(
                        ContextCompat.getColor(activity, android.R.color.darker_gray)
                    )
                }
            }
        }
    }
    
    /**
     * 静态内部类：重置保存按钮状态（1秒冷却）
     */
    private class ResetSaveButtonStateRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.isSavingVoiceText = false // 重置防抖标志
                activity.saveButton.isEnabled = true // 重新启用按钮
                VoiceLog.d("保存按钮状态已重置（1秒冷却完成）")
            }
        }
    }
    
    /**
     * 静态内部类：显示保存成功提示
     */
    private class ShowSaveSuccessRunnable(
        activity: VoiceRecognitionActivity,
        private val wasListening: Boolean
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.listeningText.text = "✅ 已保存到语音转文"
                activity.micContainer.setCardBackgroundColor(
                    ContextCompat.getColor(activity, android.R.color.holo_green_dark)
                )
                Toast.makeText(activity, "已保存到语音转文", Toast.LENGTH_LONG).show()
                
                // 缩短到1秒后恢复状态（按钮状态已在finally中重置）
                val restoreRunnable = RestoreStateRunnable(activity, wasListening)
                activity.safePostDelayed(1000, restoreRunnable)
            }
        }
    }
    
    /**
     * 静态内部类：显示保存失败提示
     */
    private class ShowSaveErrorRunnable(
        activity: VoiceRecognitionActivity,
        private val errorMsg: String,
        private val wasListening: Boolean
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.listeningText.text = "❌ $errorMsg"
                activity.micContainer.setCardBackgroundColor(
                    ContextCompat.getColor(activity, android.R.color.holo_red_dark)
                )
                Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
                
                // 缩短到1秒后恢复状态（按钮状态已在finally中重置）
                val restoreRunnable = RestoreStateRunnable(activity, wasListening)
                activity.safePostDelayed(1000, restoreRunnable)
            }
        }
    }
    
    /**
     * 静态内部类：显示空文本警告
     */
    private class ShowEmptyTextWarningRunnable(
        activity: VoiceRecognitionActivity,
        private val wasListening: Boolean
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                activity.listeningText.text = "⚠️ 没有可保存的文本"
                activity.micContainer.setCardBackgroundColor(
                    ContextCompat.getColor(activity, android.R.color.holo_orange_light)
                )
                Toast.makeText(activity, "没有可保存的文本", Toast.LENGTH_SHORT).show()
                
                // 缩短到1秒后恢复状态（按钮状态已在finally中重置）
                val restoreRunnable = RestoreStateRunnable(activity, wasListening)
                activity.safePostDelayed(1000, restoreRunnable)
            }
        }
    }
    
    /**
     * 静态内部类：完成Activity
     */
    private class FinishActivityRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (!activity.isDestroyed && !activity.isFinishing) {
                    activity.finish()
                }
            }
        }
    }
    
    /**
     * 静态内部类：尝试系统语音输入
     */
    private class TrySystemVoiceInputRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (!activity.isDestroyed && !activity.isFinishing && activity.isListening) {
                    activity.trySystemVoiceInput()
                }
            }
        }
    }
    
    /**
     * 静态内部类：冷却完成，重启识别
     */
    private class CoolingDownCompleteRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                
                // 如果用户手动暂停了识别，不自动重启
                if (activity.isPausedByUser) {
                    VoiceLog.d("用户已暂停识别，冷却完成但不重启")
                    activity.recognizerState = RecognizerState.IDLE
                    return
                }
                
                // 冷却完成，重置状态为空闲
                activity.recognizerState = RecognizerState.IDLE
                VoiceLog.d("识别器状态: COOLING_DOWN -> IDLE（冷却完成）")
                
                // 如果仍在监听状态且未暂停，重启识别
                if (activity.isListening && !activity.isPausedByUser) {
                    VoiceLog.d("冷却完成，重启语音识别")
                    activity.startVoiceRecognition()
                }
            }
        }
    }
    
    /**
     * 静态内部类：准备并启动识别
     */
    private class PrepareAndStartRecognitionRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (!activity.isDestroyed && !activity.isFinishing) {
                    activity.prepareAndStartRecognition()
                }
            }
        }
    }
    
    /**
     * 静态内部类：连接超时处理
     */
    private class ConnectionTimeoutRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                
                // 检查是否已准备好
                if (!activity.isRecognizerReady) {
                    VoiceLog.w("⚠️ 连接超时：识别器在 ${activity.CONNECTION_TIMEOUT_MS}ms 内未准备好")
                    
                    // 重置准备状态和启动标志
                    activity.isRecognizerReady = false
                    activity.isStartingRecognition = false
                    
                    // 标记需要完全销毁识别器（连接超时需要重新创建）
                    activity.shouldDestroyRecognizerOnRelease = true
                    // 释放识别器
                    activity.releaseSpeechRecognizer()
                    
                    // 更新UI提示
                    val updateRunnable = UpdateTextRunnable(
                        activity,
                        "连接超时，正在重试...",
                        android.R.color.holo_orange_light
                    )
                    activity.safePost(updateRunnable)
                    
                    // 延迟后重试（给识别器足够时间释放）
                    val retryDelay = if (activity.isXiaomiAivsService) {
                        2000L // 小米 Aivs SDK 需要更长的重试延迟
                    } else {
                        1500L // 其他服务1.5秒
                    }
                    
                    val retryRunnable = RetryStartRecognitionRunnable(activity, retryDelay)
                    activity.safePostDelayed(retryDelay, retryRunnable)
                } else {
                    VoiceLog.d("✅ 连接超时检查：识别器已准备好，无需处理")
                }
            }
        }
    }
    
    /**
     * 静态内部类：创建识别器
     */
    private class CreateRecognizerRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                try {
                    // 创建语音识别器（优先使用小米 Aivs SDK）
                    activity.speechRecognizer = activity.createSpeechRecognizerWithAivsPriority()
                    
                    // 创建识别器后必须空检测（华为 HMS 场景下可能返回 null）
                    if (activity.speechRecognizer == null) {
                        VoiceLog.w("无法创建 SpeechRecognizer（返回 null）")
                        // 华为设备特殊处理：降级到手动输入
                        if (activity.isHuaweiDevice()) {
                            VoiceLog.w("华为设备创建识别器失败，降级到手动输入模式")
                            activity.showManualInputMode()
                        } else {
                            // 其他设备尝试系统语音输入
                            activity.trySystemVoiceInput()
                        }
                        return
                    }
                    
                    // 重置准备状态
                    activity.isRecognizerReady = false
                    
                    activity.speechRecognizer?.setRecognitionListener(activity.createRecognitionListener())
                    
                    // 设置连接超时机制
                    activity.setupConnectionTimeout()
                    
                    // 设置监听状态，等待 onReadyForSpeech 回调
                    // 如果用户已暂停，不启动识别
                    if (!activity.isPausedByUser) {
                        activity.isListening = true
                        activity.updateListeningState(true)
                        activity.recognizerState = RecognizerState.LISTENING
                        // 注意：保持 isStartingRecognition = true，直到识别真正启动
                        VoiceLog.d("识别器已创建，等待服务连接（onReadyForSpeech）...")
                    } else {
                        VoiceLog.d("用户已暂停识别，不启动识别")
                        activity.isStartingRecognition = false
                        activity.recognizerState = RecognizerState.IDLE
                    }
                    
                    // 注意：不再使用固定延迟，而是等待 onReadyForSpeech 回调
                    // 如果超时未收到回调，ConnectionTimeoutRunnable 会处理
                } catch (e: Exception) {
                    VoiceLog.e("创建SpeechRecognizer失败: ${e.message}", e)
                    // 华为设备创建失败时，降级到手动输入
                    if (activity.isHuaweiDevice()) {
                        VoiceLog.w("华为设备创建识别器异常，降级到手动输入模式")
                        activity.showManualInputMode()
                    } else {
                        activity.trySystemVoiceInput()
                    }
                }
            }
        }
    }
    
    /**
     * 静态内部类：显示手动输入模式
     */
    private class ShowManualInputModeRunnable(
        activity: VoiceRecognitionActivity
    ) : Runnable {
        private val activityRef = WeakReference(activity)
        
        override fun run() {
            activityRef.get()?.let { activity ->
                if (activity.isDestroyed || activity.isFinishing) return
                
                // 重置状态
                activity.recognizerState = RecognizerState.IDLE
                
                // 更新UI提示
                activity.listeningText.text = "请在下方输入文本，然后点击'说完了'"
                activity.micContainer.setCardBackgroundColor(
                    ContextCompat.getColor(activity, android.R.color.darker_gray)
                )
                
                // 聚焦到文本输入框
                activity.recognizedTextView.requestFocus()
                
                // 显示键盘
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(activity.recognizedTextView, InputMethodManager.SHOW_IMPLICIT)
                
                // 确保"说完了"按钮可用
                activity.doneButton.isEnabled = true
                
                // 显示提示信息
                Toast.makeText(
                    activity,
                    "语音识别不可用，请手动输入文本",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * 检测是否是华为设备
     */
    private fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("huawei") || brand.contains("huawei") ||
               manufacturer.contains("honor") || brand.contains("honor")
    }
    
    /**
     * 检测是否安装了 HMS Core（华为移动服务核心）
     * 华为设备需要 HMS Core 才能正常使用 SpeechRecognizer
     */
    private fun hasHmsCore(): Boolean {
        return try {
            val pm = packageManager
            // HMS Core 的包名
            val hmsCorePackage = "com.huawei.hms"
            pm.getPackageInfo(hmsCorePackage, PackageManager.GET_ACTIVITIES)
            VoiceLog.d("✅ 检测到 HMS Core 已安装")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            VoiceLog.d("❌ 未检测到 HMS Core: ${e.message}")
            false
        } catch (e: Exception) {
            VoiceLog.w("检测 HMS Core 时出错: ${e.message}")
            false
        }
    }
    
    /**
     * 显示手动输入模式（华为设备创建识别器失败时的降级方案）
     */
    private fun showManualInputMode() {
        VoiceLog.d("切换到手动输入模式")
        
        // 重置状态
        recognizerState = RecognizerState.IDLE
        
        // 更新UI提示（使用静态内部类避免内存泄漏）
        val showManualRunnable = ShowManualInputModeRunnable(this)
        safePost(showManualRunnable)
    }
    
    /**
     * 计算两个字符串的相似度（0.0-1.0）
     * 使用编辑距离算法，相似度越高表示两个文本越相似
     * 
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return 相似度（0.0-1.0），1.0表示完全相同
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        // 简单实现：计算最长公共子序列
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) {
            return 1.0f
        }
        
        val editDistance = levenshteinDistance(longer, shorter)
        val similarity = (longer.length - editDistance).toFloat() / longer.length
        
        VoiceLog.d("相似度计算: s1='$s1' (${s1.length}), s2='$s2' (${s2.length}), editDistance=$editDistance, similarity=$similarity")
        
        return similarity.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * 计算两个字符串的编辑距离（Levenshtein Distance）
     * 编辑距离是指将一个字符串转换为另一个字符串所需的最少单字符编辑（插入、删除或替换）次数
     * 
     * @param s1 第一个字符串
     * @param s2 第二个字符串
     * @return 编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length
        
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        // 初始化第一行和第一列
        for (i in 0..m) {
            dp[i][0] = i
        }
        for (j in 0..n) {
            dp[0][j] = j
        }
        
        // 填充动态规划表
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }
        
        return dp[m][n]
    }
    
}