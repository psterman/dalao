package com.example.aifloatingball.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import android.widget.GridLayout
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.SearchActivity
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.data.SimpleTaskTemplates
import com.example.aifloatingball.model.PromptTemplate

class SimpleModeService : Service() {
    private lateinit var settingsManager: SettingsManager
    private var windowManager: WindowManager? = null
    private var simpleModeView: View? = null
    private var searchEditText: EditText? = null
    private var gridLayout: GridLayout? = null
    
    companion object {
        private const val TAG = "SimpleModeService"
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "simple_mode_channel"
        private var isServiceRunning = false

        fun isRunning(): Boolean = isServiceRunning
    }
    
    // 广播接收器处理搜索和关闭动作
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY" -> {
                    val query = intent.getStringExtra("search_query") ?: ""
                    performSearch(query)
                    stopSelf()
                }
                "com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE" -> {
                    stopSelf()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SimpleModeService onCreate")
        settingsManager = SettingsManager.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        isServiceRunning = true
        
        // 注册广播接收器
        val filter = IntentFilter().apply {
            addAction("com.example.aifloatingball.ACTION_SEARCH_AND_DESTROY")
            addAction("com.example.aifloatingball.ACTION_CLOSE_SIMPLE_MODE")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, filter)
        }
        
        // 显示简易模式界面
        showSimpleModeInterface()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val content = intent?.getStringExtra("search_content")
        val mode = intent?.getStringExtra("mode")
        
        if (!content.isNullOrEmpty()) {
            // 如果有搜索内容，填充到搜索框
            searchEditText?.setText(content)
            when (mode) {
                "clipboard" -> handleClipboardContent(content)
                else -> handleNormalContent(content)
            }
        }
        
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "简易模式服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持简易模式服务运行"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("简易模式正在运行")
            .setContentText("点击返回应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showSimpleModeInterface() {
        if (simpleModeView != null) {
            windowManager?.removeView(simpleModeView)
            simpleModeView = null
        }

        val inflater = LayoutInflater.from(this)
        simpleModeView = inflater.inflate(R.layout.simple_mode_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        try {
            windowManager?.addView(simpleModeView, params)
            setupSimpleModeControls()
            createFunctionGrid()
        } catch (e: Exception) {
            Log.e(TAG, "无法显示简易模式界面", e)
            Toast.makeText(this, "无法显示简易模式界面", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun setupSimpleModeControls() {
        simpleModeView?.apply {
            // 搜索框
            searchEditText = findViewById(R.id.searchEditText)
            
            // 搜索按钮
            findViewById<ImageButton>(R.id.searchButton)?.setOnClickListener {
                val query = searchEditText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    performSearch(query)
                }
            }
            
            // 最小化按钮 - 隐藏界面但不关闭服务
            findViewById<ImageButton>(R.id.simple_mode_minimize_button)?.setOnClickListener {
                simpleModeView?.visibility = View.GONE
            }
            
            // 关闭按钮 - 关闭服务
            findViewById<ImageButton>(R.id.simple_mode_close_button)?.setOnClickListener {
                stopSelf()
            }
            
            // 底部导航
            findViewById<LinearLayout>(R.id.tab_home)?.setOnClickListener {
                // 显示主界面
                simpleModeView?.visibility = View.VISIBLE
            }
            
            findViewById<LinearLayout>(R.id.tab_search)?.setOnClickListener {
                // 启动搜索Activity
                val intent = Intent(this@SimpleModeService, SearchActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            
            findViewById<LinearLayout>(R.id.tab_voice)?.setOnClickListener {
                // 启动语音识别
                val intent = Intent(this@SimpleModeService, VoiceRecognitionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            
            findViewById<LinearLayout>(R.id.tab_profile)?.setOnClickListener {
                // 启动设置页面
                val intent = Intent(this@SimpleModeService, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            
            // 搜索框回车搜索
            searchEditText?.setOnEditorActionListener { _, _, _ ->
                val query = searchEditText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    performSearch(query)
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun createFunctionGrid() {
        gridLayout = simpleModeView?.findViewById(R.id.grid_layout)
        gridLayout?.removeAllViews()
        
        // 创建阶梯式任务选项
        val tasks = SimpleTaskTemplates.templates.take(6) // 取前6个任务
        
        tasks.forEachIndexed { index, template ->
            val button = createTaskButton(template)
            gridLayout?.addView(button)
        }
    }
    
    private fun createTaskButton(template: PromptTemplate): View {
        val inflater = LayoutInflater.from(this)
        val buttonView = inflater.inflate(R.layout.simple_mode_function_button, null)
        
        val icon = buttonView.findViewById<ImageView>(R.id.function_icon)
        val text = buttonView.findViewById<TextView>(R.id.function_text)
        
        // 创建图标drawable（使用emoji转文字图标）
        text.text = template.intentName
        // 暂时使用默认图标，稍后可以根据模板创建对应图标
        icon.setImageResource(R.drawable.ic_search)
        
        buttonView.setOnClickListener {
            handleTaskSelection(template)
        }
        
        return buttonView
    }
    
    private fun handleTaskSelection(template: PromptTemplate) {
        // 暂时显示任务信息
        Toast.makeText(this, "已选择任务：${template.intentName}\n${template.description}", Toast.LENGTH_LONG).show()
        
        // TODO: 后续可以实现步骤引导界面
        // showTaskGuidanceDialog(template)
    }

    private fun handleClipboardContent(content: String) {
        when (settingsManager.getString("simple_mode_action", "search")) {
            "search" -> performSearch(content)
            "translate" -> performTranslate(content)
            "custom" -> performCustomAction(content)
            else -> Toast.makeText(this, "未配置简易模式动作", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleNormalContent(content: String) {
        when (settingsManager.getString("simple_mode_action", "search")) {
            "search" -> performSearch(content)
            "translate" -> performTranslate(content)
            "custom" -> performCustomAction(content)
            else -> Toast.makeText(this, "未配置简易模式动作", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(content: String) {
        Log.d(TAG, "执行搜索: $content")
        val intent = Intent(this, SearchActivity::class.java).apply {
            putExtra("query", content)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun performTranslate(content: String) {
        Toast.makeText(this, "翻译功能开发中: $content", Toast.LENGTH_SHORT).show()
    }

    private fun performCustomAction(content: String) {
        Toast.makeText(this, "自定义动作开发中: $content", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SimpleModeService onDestroy")
        
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }
        
        if (simpleModeView != null) {
            try {
                windowManager?.removeView(simpleModeView)
            } catch (e: Exception) {
                Log.e(TAG, "移除视图失败", e)
            }
            simpleModeView = null
        }
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
