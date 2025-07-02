package com.example.aifloatingball.service

import android.app.AlertDialog
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.model.AISearchEngine

class SimpleModeService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var simpleModeView: View
    private lateinit var minimizedView: View
    private lateinit var settingsManager: SettingsManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var minimizedParams: WindowManager.LayoutParams
    private var isWindowVisible = false
    private var isMinimized = false
    
    // 拖动相关变量
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragThreshold = 10f
    
    // 三个AI引擎插槽
    private var selectedEngines = mutableListOf<String>("N/A", "N/A", "N/A")
    
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY" -> {
                    val query = intent.getStringExtra("search_query")
                    Log.d("SimpleModeService", "收到'搜索并销毁'广播, 查询: '$query'")

                    if (!query.isNullOrEmpty()) {
                        // 1. 切换模式，防止服务被自动重启
                        settingsManager.setDisplayMode("floating_ball")
                        Log.d("SimpleModeService", "显示模式已临时切换到 aifloatingball")

                        // 2. 启动搜索服务
                        val serviceIntent = Intent(context, DualFloatingWebViewService::class.java).apply {
                            putExtras(intent.extras ?: Bundle())
                        }
                        context?.startService(serviceIntent)
                        Log.d("SimpleModeService", "已启动 DualFloatingWebViewService")

                        // 3. 彻底停止自己
                        stopSelf()
                        Log.d("SimpleModeService", "已调用 stopSelf()")
                    }
                }
            }
        }
    }
    
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("SimpleModeService", "Screen turned off, stopping service")
                    stopSelf()
                }
                "com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE" -> {
                    Log.d("SimpleModeService", "Received close broadcast, stopping service immediately")
                    stopSelf()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("SimpleModeService", "Service created")
        
        settingsManager = SettingsManager.getInstance(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 注册命令广播接收器
        val commandFilter = IntentFilter().apply {
            addAction("com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, commandFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, commandFilter)
        }
        
        // 注册屏幕关闭监听器
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
        
        createSimpleModeWindow()
        createMinimizedWindow()
        initializeEngineSlots()
    }
    
    private fun createSimpleModeWindow() {
        val inflater = LayoutInflater.from(this)
        simpleModeView = inflater.inflate(R.layout.simple_mode_layout, null)
        
        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        
        windowParams.gravity = Gravity.CENTER
        
        showWindow()
        setupViews()
    }
    
    private fun createMinimizedWindow() {
        val inflater = LayoutInflater.from(this)
        minimizedView = inflater.inflate(R.layout.simple_mode_minimized, null)
        
        // 获取屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        minimizedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        
        // 初始位置设置在右边缘中央
        minimizedParams.gravity = Gravity.TOP or Gravity.START
        minimizedParams.x = screenWidth - 100 // 右边缘，露出一小部分
        minimizedParams.y = screenHeight / 2 - 50 // 垂直居中
        
        setupMinimizedView()
    }
    
    private fun setupMinimizedView() {
        val minimizedLayout = minimizedView.findViewById<LinearLayout>(R.id.minimized_layout)
        
        minimizedLayout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = minimizedParams.x
                    initialY = minimizedParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // 检查是否开始拖动
                    if (!isDragging && (kotlin.math.abs(deltaX) > dragThreshold || kotlin.math.abs(deltaY) > dragThreshold)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        minimizedParams.x = (initialX + deltaX).toInt()
                        minimizedParams.y = (initialY + deltaY).toInt()
                        
                        // 限制在屏幕边界内
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        
                        minimizedParams.x = minimizedParams.x.coerceIn(-50, screenWidth - 50)
                        minimizedParams.y = minimizedParams.y.coerceIn(0, screenHeight - view.height)
                        
                        try {
                            windowManager.updateViewLayout(minimizedView, minimizedParams)
                        } catch (e: Exception) {
                            Log.e("SimpleModeService", "更新最小化视图位置失败", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // 点击事件：恢复到完整视图
                        restoreFromMinimized()
                    } else {
                        // 拖动结束：自动贴边
                        snapToEdge()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }
    
    private fun snapToEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val centerX = screenWidth / 2
        
        // 判断应该贴到哪一边
        val targetX = if (minimizedParams.x < centerX) {
            -50 // 贴到左边，露出一部分
        } else {
            screenWidth - 50 // 贴到右边，露出一部分
        }
        
        minimizedParams.x = targetX
        
        try {
            windowManager.updateViewLayout(minimizedView, minimizedParams)
        } catch (e: Exception) {
            Log.e("SimpleModeService", "贴边动画失败", e)
        }
    }
    
    private fun minimizeToEdge() {
        if (isMinimized) return
        
        try {
            // 隐藏完整视图
            if (isWindowVisible) {
                windowManager.removeView(simpleModeView)
                isWindowVisible = false
            }
            
            // 显示最小化视图
            if (!minimizedView.isAttachedToWindow) {
                windowManager.addView(minimizedView, minimizedParams)
            }
            isMinimized = true
            
            Log.d("SimpleModeService", "简易模式已最小化到边缘")
        } catch (e: Exception) {
            Log.e("SimpleModeService", "最小化失败", e)
        }
    }
    
    private fun restoreFromMinimized() {
        if (!isMinimized) return
        
        try {
            // 隐藏最小化视图
            if (minimizedView.isAttachedToWindow) {
                windowManager.removeView(minimizedView)
            }
            isMinimized = false
            
            // 显示完整视图
            showWindow()
            
            Log.d("SimpleModeService", "简易模式已从最小化状态恢复")
        } catch (e: Exception) {
            Log.e("SimpleModeService", "恢复最小化失败", e)
        }
    }
    
    private fun showMinimizeHintIfNeeded() {
        val prefs = getSharedPreferences("simple_mode_prefs", Context.MODE_PRIVATE)
        val hasShownHint = prefs.getBoolean("minimize_hint_shown", false)
        
        if (!hasShownHint) {
            // 延迟3秒显示提示
            simpleModeView.postDelayed({
                Toast.makeText(this, "💡 提示：点击右上角 ➖ 可以最小化到边缘", Toast.LENGTH_LONG).show()
                // 标记已显示过提示
                prefs.edit().putBoolean("minimize_hint_shown", true).apply()
                
                // 10秒后自动隐藏红点
                simpleModeView.postDelayed({
                    hideMinimizeHint()
                }, 10000)
            }, 3000)
        } else {
            // 如果已经显示过提示，立即隐藏红点
            hideMinimizeHint()
        }
    }
    
    private fun hideMinimizeHint() {
        try {
            val hintDot = simpleModeView.findViewById<View>(R.id.minimize_hint_dot)
            hintDot?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("SimpleModeService", "隐藏提示红点失败", e)
        }
    }
    
    private fun setupViews() {
        val searchEditText = simpleModeView.findViewById<EditText>(R.id.searchEditText)
        val searchButton = simpleModeView.findViewById<ImageButton>(R.id.searchButton)
        val closeButton = simpleModeView.findViewById<ImageButton>(R.id.simple_mode_close_button)
        val minimizeButton = simpleModeView.findViewById<ImageButton>(R.id.simple_mode_minimize_button)
        
        // AI引擎按钮
        val aiEngine1Button = simpleModeView.findViewById<TextView>(R.id.aiEngine1Button)
        val aiEngine2Button = simpleModeView.findViewById<TextView>(R.id.aiEngine2Button)
        val aiEngine3Button = simpleModeView.findViewById<TextView>(R.id.aiEngine3Button)
        
        // 网格项目
        val gridItem1 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_1)  // 智能搜索
        val gridItem2 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_2)  // AI写作
        val gridItem3 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_3)  // AI绘画
        val gridItem4 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_4)  // 创意助手
        val gridItem5 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_5)  // 网页翻译
        val gridItem6 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_6)  // 数据分析
        val gridItem7 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_7)  // 音乐生成
        val gridItem8 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_8)  // 视频制作
        val gridItem9 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_9)  // 学习助手
        val gridItem10 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_10) // 工具箱
        val gridItem11 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_11) // 历史记录
        val gridItem12 = simpleModeView.findViewById<LinearLayout>(R.id.grid_item_12) // 设置
        
        // Tab按钮
        val tabHome = simpleModeView.findViewById<LinearLayout>(R.id.tab_home)
        val tabSearch = simpleModeView.findViewById<LinearLayout>(R.id.tab_search)
        val tabVoice = simpleModeView.findViewById<LinearLayout>(R.id.tab_voice)
        val tabProfile = simpleModeView.findViewById<LinearLayout>(R.id.tab_profile)
        
        // 关闭按钮
        closeButton.setOnClickListener {
            Log.d("SimpleModeService", "关闭按钮点击，切换到悬浮球模式并停止服务")
            // 1. 切换模式，让守护服务接管
            settingsManager.setDisplayMode("floating_ball")
            // 2. 停止自己
            stopSelf()
        }
        
        // 最小化按钮
        minimizeButton.setOnClickListener {
            Log.d("SimpleModeService", "最小化按钮点击")
            minimizeToEdge()
            // 隐藏提示红点
            hideMinimizeHint()
        }
        
        // 显示最小化功能提示
        showMinimizeHintIfNeeded()
        
        // 设置搜索功能
        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                // 统一使用新的搜索流程
                performTripleSearch(query)
            } else {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置回车键搜索
        searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val query = searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    performTripleSearch(query)
                    hideKeyboard(searchEditText)
                    return@setOnEditorActionListener true
                }
            }
            false
        }
        
        // 设置输入框焦点管理
        searchEditText.setOnFocusChangeListener { _, hasFocus ->
            updateWindowFocusability(hasFocus)
        }
        
        // AI引擎按钮点击事件
        aiEngine1Button.setOnClickListener { showAiEngineSelectionDialog(0) }
        aiEngine2Button.setOnClickListener { showAiEngineSelectionDialog(1) }
        aiEngine3Button.setOnClickListener { showAiEngineSelectionDialog(2) }
        
        // 网格项目点击事件
        gridItem1.setOnClickListener {
            val query = searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                // 统一使用新的搜索流程
                performTripleSearch(query)
            } else {
                Toast.makeText(this, "请先输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        gridItem2.setOnClickListener {
            Toast.makeText(this, "AI写作功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem3.setOnClickListener {
            Toast.makeText(this, "AI绘画功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem4.setOnClickListener {
            Toast.makeText(this, "创意助手功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem5.setOnClickListener {
            Toast.makeText(this, "网页翻译功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem6.setOnClickListener {
            Toast.makeText(this, "数据分析功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem7.setOnClickListener {
            Toast.makeText(this, "音乐生成功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem8.setOnClickListener {
            Toast.makeText(this, "视频制作功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem9.setOnClickListener {
            Toast.makeText(this, "学习助手功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem10.setOnClickListener {
            Toast.makeText(this, "工具箱功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem11.setOnClickListener {
            Toast.makeText(this, "历史记录功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        gridItem12.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        
        // Tab点击事件
        tabHome.setOnClickListener {
            Toast.makeText(this, "已在首页", Toast.LENGTH_SHORT).show()
        }
        
        tabSearch.setOnClickListener {
            searchEditText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        
        tabVoice.setOnClickListener {
            Log.d("SimpleModeService", "语音Tab点击，隐藏窗口并启动语音识别")
            hideWindow()
            val intent = Intent(this, VoiceRecognitionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        
        tabProfile.setOnClickListener {
            Toast.makeText(this, "个人中心开发中", Toast.LENGTH_SHORT).show()
        }
        
        // 支持返回键关闭
        simpleModeView.isFocusableInTouchMode = true
        simpleModeView.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                stopSelf()
                return@setOnKeyListener true
            }
            false
        }
    }
    
    private fun updateWindowFocusability(needsFocus: Boolean) {
        try {
            val params = simpleModeView.layoutParams as WindowManager.LayoutParams
            
            if (needsFocus) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            
            windowManager.updateViewLayout(simpleModeView, params)
        } catch (e: Exception) {
            Log.e("SimpleModeService", "Error updating window focusability", e)
        }
    }
    
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    private fun initializeEngineSlots() {
        Log.d("SimpleModeService", "Initializing engine slots")
        
        val enabledEngines = settingsManager.getEnabledAIEngines()
        Log.d("SimpleModeService", "Enabled engines: $enabledEngines")
        
        if (enabledEngines.isEmpty()) {
            // 如果没有启用的引擎，使用默认的前3个
            val allEngines = AISearchEngine.DEFAULT_AI_ENGINES
            selectedEngines[0] = if (allEngines.isNotEmpty()) allEngines[0].name else "perplexity"
            selectedEngines[1] = if (allEngines.size > 1) allEngines[1].name else "deepseek_api"
            selectedEngines[2] = if (allEngines.size > 2) allEngines[2].name else "gemini"
            Log.d("SimpleModeService", "Using default engines: $selectedEngines")
        } else {
            val enginesList = enabledEngines.toList()
            selectedEngines[0] = enginesList.getOrElse(0) { "perplexity" }
            selectedEngines[1] = enginesList.getOrElse(1) { "deepseek_api" }
            selectedEngines[2] = enginesList.getOrElse(2) { "gemini" }
            Log.d("SimpleModeService", "Using enabled engines: $selectedEngines")
        }
        
        updateEngineButtons()
    }
    
    private fun updateEngineButtons() {
        val allEngines = AISearchEngine.DEFAULT_AI_ENGINES
        
        val aiEngine1Button = simpleModeView.findViewById<TextView>(R.id.aiEngine1Button)
        val aiEngine2Button = simpleModeView.findViewById<TextView>(R.id.aiEngine2Button)
        val aiEngine3Button = simpleModeView.findViewById<TextView>(R.id.aiEngine3Button)
        
        aiEngine1Button.text = allEngines.find { it.name == selectedEngines[0] }?.displayName ?: selectedEngines[0]
        aiEngine2Button.text = allEngines.find { it.name == selectedEngines[1] }?.displayName ?: selectedEngines[1]
        aiEngine3Button.text = allEngines.find { it.name == selectedEngines[2] }?.displayName ?: selectedEngines[2]
        
        Log.d("SimpleModeService", "Updated button texts: ${aiEngine1Button.text}, ${aiEngine2Button.text}, ${aiEngine3Button.text}")
    }
    
    private fun showAiEngineSelectionDialog(slotIndex: Int) {
        val allEngines = AISearchEngine.DEFAULT_AI_ENGINES
        val engineNames = allEngines.map { it.displayName }.toTypedArray()
        val engineKeys = allEngines.map { it.name }
        
        if (engineNames.isEmpty()) {
            Toast.makeText(this, "没有可用的AI引擎", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentEngineKey = selectedEngines[slotIndex]
        val currentIndex = engineKeys.indexOf(currentEngineKey).takeIf { it >= 0 } ?: 0
        
        val listener = DialogInterface.OnClickListener { dialog, which ->
            selectedEngines[slotIndex] = engineKeys[which]
            updateEngineButtons()
            dialog.dismiss()
            Toast.makeText(this, "已选择: ${engineNames[which]}", Toast.LENGTH_SHORT).show()
        }
        
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("选择AI引擎")
            .setSingleChoiceItems(engineNames, currentIndex, listener)
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun performTripleSearch(query: String) {
        Log.d("SimpleModeService", "执行三窗口搜索，查询: $query")

        // 1. 切换模式，防止服务被自动重启
        settingsManager.setDisplayMode("floating_ball")
        Log.d("SimpleModeService", "显示模式已临时切换到 aifloatingball")

        // 2. 启动搜索服务
        try {
            settingsManager.setLeftWindowSearchEngine(selectedEngines[0])
            settingsManager.setDefaultSearchEngine(selectedEngines[1])
            settingsManager.setRightWindowSearchEngine(selectedEngines[2])
            
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("search_mode", "triple")
                putExtra("left_engine", selectedEngines[0])
                putExtra("center_engine", selectedEngines[1])
                putExtra("right_engine", selectedEngines[2])
            }
            startService(intent)
            Toast.makeText(this, "正在启动三窗口搜索...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SimpleModeService", "启动三窗口搜索失败", e)
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
            showWindow() // 如果启动失败，则重新显示窗口
            return
        }

        // 3. 彻底停止自己
        stopSelf()
        Log.d("SimpleModeService", "已调用 stopSelf() 完成三窗口搜索")
    }
    
    private fun hideWindow() {
        if (!isWindowVisible) return
        try {
            if (::simpleModeView.isInitialized && simpleModeView.isAttachedToWindow) {
                windowManager.removeView(simpleModeView)
                isWindowVisible = false
                Log.d("SimpleModeService", "简易模式窗口已隐藏")
            }
        } catch (e: Exception) {
            Log.e("SimpleModeService", "隐藏窗口失败", e)
        }
    }

    private fun showWindow() {
        if (isWindowVisible) return
        try {
            if (::simpleModeView.isInitialized && !simpleModeView.isAttachedToWindow) {
                windowManager.addView(simpleModeView, windowParams)
                isWindowVisible = true
                Log.d("SimpleModeService", "简易模式窗口已显示")
            }
        } catch (e: Exception) {
            Log.e("SimpleModeService", "显示窗口失败", e)
        }
    }
    
    private fun removeSimpleModeWindow() {
        try {
            if (::simpleModeView.isInitialized && simpleModeView.windowToken != null) {
                windowManager.removeView(simpleModeView)
            }
        } catch (e: Exception) {
            Log.e("SimpleModeService", "移除简易模式窗口失败", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("SimpleModeService", "服务销毁")
        // 注销广播接收器
        try {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(commandReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("SimpleModeService", "注销广播接收器失败，可能未注册")
        }
        // 确保窗口被移除
        hideWindow()
        
        // 移除最小化视图
        try {
            if (::minimizedView.isInitialized && minimizedView.isAttachedToWindow) {
                windowManager.removeView(minimizedView)
            }
        } catch (e: Exception) {
            Log.e("SimpleModeService", "移除最小化视图失败", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SimpleModeService", "服务启动 onStartCommand")
        // 使用 START_NOT_STICKY, 防止服务在被意外杀死后自动重启。
        // 我们希望服务的生命周期完全由应用的逻辑（如用户在设置中选择模式）来控制。
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 