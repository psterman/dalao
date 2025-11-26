package com.example.aifloatingball.service

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AI应用悬浮窗服务
 * 在AI应用启动后显示返回按钮和粘贴按钮
 */
class AIAppOverlayService : Service() {

    companion object {
        const val TAG = "AIAppOverlayService"
        const val ACTION_SHOW_OVERLAY = "com.example.aifloatingball.SHOW_AI_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.aifloatingball.HIDE_AI_OVERLAY"

        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_QUERY = "query"
        const val EXTRA_PACKAGE_NAME = "package_name"

        private var overlayView: View? = null
        private var aiMenuView: View? = null
        private var windowManager: WindowManager? = null
        private var isOverlayVisible = false
        private var isAIMenuVisible = false
    }

    private var appName: String = ""
    private var query: String = ""
    private var packageName: String = ""
    private var isSimpleMode: Boolean = false
    private var isOverlayMode: Boolean = false
    private var isIslandMode: Boolean = false // 灵动岛专用模式
    private var isSoftwareTabMode: Boolean = false // 软件tab专用模式
    private var isAiTabMode: Boolean = false // AI tab专用模式
    
    // 剪贴板监听器
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    
    // 应用切换监听相关
    private var usageStatsManager: android.app.usage.UsageStatsManager? = null
    private var appSwitchHandler: android.os.Handler? = null
    private var appSwitchRunnable: Runnable? = null
    private var currentPackageName: String? = null
    private var isAppSwitchMonitoringEnabled = true
    
    // 无限循环跳转状态管理
    private var lastSwitchTime: Long = 0
    private var switchCount: Int = 0
    private var maxSwitchCount: Int = 50 // 最大跳转次数，防止无限循环
    private var switchCooldown: Long = 200 // 进一步减少跳转冷却时间，提高响应速度
    
    // 智能弹出时机管理
    private var targetPackageName: String? = null
    private var overlayShowAttempts: Int = 0
    private var maxShowAttempts: Int = 5 // 最大尝试次数
    private var overlayShowHandler: android.os.Handler? = null
    private var overlayShowRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务以绕过Android 10+的剪贴板访问限制
        startForegroundService()
        
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                query = intent.getStringExtra(EXTRA_QUERY) ?: ""
                packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                val modeString = intent.getStringExtra("mode") ?: ""
                isSimpleMode = intent.getBooleanExtra("mode", false) || modeString == "simple"
                // overlay模式：显示简易样式但不切换到简易模式
                isOverlayMode = modeString == "overlay"
                // island模式：灵动岛专用模式，直接显示AI应用列表
                isIslandMode = modeString == "island"
                // software_tab模式：软件tab专用模式，包含返回和app按钮
                isSoftwareTabMode = modeString == "software_tab"
                // ai_tab模式：AI tab专用模式，显示AI应用列表，支持intent搜索或粘贴搜索
                isAiTabMode = modeString == "ai_tab"
                
                Log.d(TAG, "ACTION_SHOW_OVERLAY: $appName, 简易模式: $isSimpleMode, overlay模式: $isOverlayMode, island模式: $isIslandMode, software_tab模式: $isSoftwareTabMode, ai_tab模式: $isAiTabMode")
                showOverlay()
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlay()
            }
            else -> {
                // 处理从DynamicIslandService传来的参数
                appName = intent?.getStringExtra("app_name") ?: ""
                query = intent?.getStringExtra("query") ?: ""
                packageName = intent?.getStringExtra("package_name") ?: ""
                val modeString = intent?.getStringExtra("mode") ?: ""
                isSimpleMode = intent?.getBooleanExtra("mode", false) == true || modeString == "simple"
                // overlay模式：显示简易样式但不切换到简易模式
                isOverlayMode = modeString == "overlay"
                // island模式：灵动岛专用模式，直接显示AI应用列表
                isIslandMode = modeString == "island"
                // software_tab模式：软件tab专用模式，包含返回和app按钮
                isSoftwareTabMode = modeString == "software_tab"

                Log.d(TAG, "显示悬浮窗: $appName, 简易模式: $isSimpleMode, overlay模式: $isOverlayMode, island模式: $isIslandMode, software_tab模式: $isSoftwareTabMode")
                showOverlay()
            }
        }
        return START_STICKY
    }
    
    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notification = createNotification()
                startForeground(1001, notification)
                Log.d(TAG, "AIAppOverlayService已启动为前台服务")
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动前台服务失败", e)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): android.app.Notification {
        val channelId = "ai_overlay_service_channel"
        val channelName = "AI悬浮窗服务"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI应用悬浮窗服务，用于显示剪贴板预览"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI悬浮窗服务")
            .setContentText("正在显示AI应用悬浮窗")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    /**
     * 显示悬浮窗
     */
    private fun showOverlay() {
        // 如果是island模式或software_tab模式，先隐藏之前的悬浮窗（如果存在），以便重新显示新的布局
        if (isOverlayVisible && (isIslandMode || isSoftwareTabMode)) {
            Log.d(TAG, "${if (isIslandMode) "island" else "software_tab"}模式：隐藏之前的悬浮窗，准备显示新的面板")
            hideOverlay()
        } else if (isOverlayVisible) {
            Log.d(TAG, "悬浮窗已显示，跳过")
            return
        }

        try {
            Log.d(TAG, "显示AI应用悬浮窗: $appName")
            
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 创建悬浮窗布局
            overlayView = createOverlayView()
            
            // 设置窗口参数
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.END
                x = 20 // 距离右边20px
                y = 200 // 距离顶部200px
            }
            
            // 添加悬浮窗到窗口管理器
            windowManager?.addView(overlayView, layoutParams)
            isOverlayVisible = true
            
            Log.d(TAG, "AI应用悬浮窗显示成功")
            
            // 注册剪贴板监听器
            registerClipboardListener()
            
            // 启动应用切换监听
            initAppSwitchListener()
            
            // 立即更新一次剪贴板预览（确保初始状态正确）
            // android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            //     updateClipboardPreview()
            // }, 100)
            
            // 再次延迟更新，确保UI完全加载
            // android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            //     updateClipboardPreview()
            // }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗失败", e)
            Toast.makeText(this, "悬浮窗显示失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建悬浮窗视图
     */
    private fun createOverlayView(): View {
        val inflater = LayoutInflater.from(this)
        val overlayView = when {
            isIslandMode -> {
                // 灵动岛专用模式：直接显示AI应用列表
                createIslandOverlayView(inflater)
            }
            isSoftwareTabMode -> {
                // 软件tab专用模式：包含返回和app按钮
                createSoftwareTabOverlayView(inflater)
            }
            isAiTabMode -> {
                // AI tab专用模式：显示AI应用列表，支持intent搜索或粘贴搜索
                createAiTabOverlayView(inflater)
            }
            isSimpleMode || isOverlayMode -> {
                // 简易模式或overlay模式：创建简化的悬浮窗
                createSimpleOverlayView(inflater)
            }
            else -> {
                // 完整模式：使用原有布局
                inflater.inflate(R.layout.ai_app_overlay, null)
            }
        }
        
        // 设置应用名称（仅在完整模式下）
        val appNameText = overlayView.findViewById<TextView>(R.id.overlay_app_name)
        appNameText?.text = appName
        
        // 设置剪贴板预览区域（暂时隐藏）
        // setupClipboardPreview(overlayView)
        
        // 保留自动复制和AI提问功能（island模式、software_tab模式和ai_tab模式不需要）
        if (!isIslandMode && !isSoftwareTabMode && !isAiTabMode) {
            performAutoCopyAndAIQuestion()
        }
        
        if (isIslandMode) {
            // 灵动岛专用模式：直接显示AI应用列表，设置按钮和AI应用点击事件
            Log.d(TAG, "设置灵动岛专用模式：按钮和AI应用点击事件")
            try {
                setupIslandOverlayButtons(overlayView)
                setupIslandAIAppClickListeners(overlayView)
                loadIslandAIAppIcons(overlayView)
                Log.d(TAG, "✅ 灵动岛专用模式设置完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 设置灵动岛专用模式失败", e)
                e.printStackTrace()
            }
        } else if (isAiTabMode) {
            // AI tab专用模式：显示AI应用列表，支持intent搜索或粘贴搜索
            Log.d(TAG, "设置AI tab专用模式：按钮和AI应用点击事件")
            try {
                setupAiTabOverlayButtons(overlayView)
                setupAiTabAIAppClickListeners(overlayView)
                loadAiTabAIAppIcons(overlayView)
                Log.d(TAG, "✅ AI tab专用模式设置完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 设置AI tab专用模式失败", e)
                e.printStackTrace()
            }
        } else if (isSoftwareTabMode) {
            // 软件tab专用模式：设置返回、app和关闭按钮
            Log.d(TAG, "设置软件tab专用模式：按钮和功能")
            try {
                setupSoftwareTabOverlayButtons(overlayView)
                Log.d(TAG, "✅ 软件tab专用模式设置完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 设置软件tab专用模式失败", e)
                e.printStackTrace()
            }
        } else if (isSimpleMode) {
            // 简易模式的按钮设置
            val backButton = overlayView.findViewById<Button>(R.id.overlay_back_button)
            backButton?.setOnClickListener {
                Log.d(TAG, "返回按钮被点击")
                returnToDynamicIsland()
            }

            val closeButton = overlayView.findViewById<Button>(R.id.overlay_close_button)
            closeButton?.setOnClickListener {
                Log.d(TAG, "关闭按钮被点击")
                hideOverlay()
                stopSelf()
            }

            val aiButton = overlayView.findViewById<Button>(R.id.overlay_ai_button)
            aiButton?.setOnClickListener {
                Log.d(TAG, "AI按钮被点击")
                // 在显示AI菜单前先更新剪贴板内容（已注释）
                // updateClipboardPreview()
                showAIMenu()
            }
        } else if (isOverlayMode) {
            // overlay模式的按钮设置（简易样式但不切换到简易模式）
            val backButton = overlayView.findViewById<Button>(R.id.overlay_back_button)
            backButton?.setOnClickListener {
                Log.d(TAG, "返回按钮被点击")
                returnToDynamicIsland()
            }

            val closeButton = overlayView.findViewById<Button>(R.id.overlay_close_button)
            closeButton?.setOnClickListener {
                Log.d(TAG, "关闭按钮被点击")
                hideOverlay()
                stopSelf()
            }

            val aiButton = overlayView.findViewById<Button>(R.id.overlay_ai_button)
            aiButton?.setOnClickListener {
                Log.d(TAG, "AI按钮被点击")
                // 在显示AI菜单前先更新剪贴板内容（已注释）
                // updateClipboardPreview()
                showAIMenu()
            }
        } else {
            // 完整模式的按钮设置
            val backButton = overlayView.findViewById<Button>(R.id.overlay_back_button)
            backButton?.setOnClickListener {
                Log.d(TAG, "返回按钮被点击")
                hideOverlay()
                // 启动主应用
                val intent = Intent(this, com.example.aifloatingball.SimpleModeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }

            val aiButton = overlayView.findViewById<Button>(R.id.overlay_ai_button)
            aiButton?.setOnClickListener {
                Log.d(TAG, "AI按钮被点击")
                // 在显示AI菜单前先更新剪贴板内容（已注释）
                // updateClipboardPreview()
                showAIMenu()
            }

            val closeButton = overlayView.findViewById<Button>(R.id.overlay_close_button)
            closeButton?.setOnClickListener {
                Log.d(TAG, "关闭按钮被点击")
                hideOverlay()
            }
        }
        
        // 设置悬浮窗可拖拽
        setupDragListener(overlayView)

        // 应用主题适配
        applyThemeToView(overlayView)

        return overlayView
    }

    /**
     * 创建灵动岛专用悬浮窗视图（直接显示AI应用列表）
     */
    private fun createIslandOverlayView(inflater: LayoutInflater): View {
        try {
            val overlayView = inflater.inflate(R.layout.ai_island_overlay, null)
            if (overlayView == null) {
                Log.e(TAG, "创建灵动岛专用悬浮窗视图失败：布局文件加载返回null")
                throw RuntimeException("无法加载ai_island_overlay布局")
            }
            Log.d(TAG, "✅ 创建灵动岛专用悬浮窗视图成功")
            return overlayView
        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建灵动岛专用悬浮窗视图失败", e)
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * 设置灵动岛悬浮窗的按钮事件
     */
    private fun setupIslandOverlayButtons(overlayView: View) {
        // 返回按钮
        val backButton = overlayView.findViewById<Button>(R.id.ai_island_back_button)
        backButton?.setOnClickListener {
            Log.d(TAG, "灵动岛返回按钮被点击")
            returnToDynamicIsland()
        }
        
        // 关闭按钮
        val closeButton = overlayView.findViewById<Button>(R.id.ai_island_close_button)
        closeButton?.setOnClickListener {
            Log.d(TAG, "灵动岛关闭按钮被点击")
            hideOverlay()
            stopSelf()
        }
    }
    
    /**
     * 设置灵动岛AI应用点击监听器
     */
    private fun setupIslandAIAppClickListeners(overlayView: View) {
        // 定义所有AI菜单项的配置（使用island专用的ID）
        val aiIslandConfigs = listOf(
            Triple(R.id.ai_island_grok, "ai.x.grok", "Grok"),
            Triple(R.id.ai_island_perplexity, "ai.perplexity.app.android", "Perplexity"),
            Triple(R.id.ai_island_poe, "com.poe.android", "Poe"),
            Triple(R.id.ai_island_manus, "tech.butterfly.app", "Manus"),
            Triple(R.id.ai_island_ima, "com.qihoo.namiso", "纳米AI"),
            Triple(R.id.ai_island_deepseek, "com.deepseek.chat", "DeepSeek"),
            Triple(R.id.ai_island_doubao, "com.larus.nova", "豆包"),
            Triple(R.id.ai_island_chatgpt, "com.openai.chatgpt", "ChatGPT"),
            Triple(R.id.ai_island_kimi, "com.moonshot.kimichat", "Kimi"),
            Triple(R.id.ai_island_yuanbao, "com.tencent.hunyuan.app.chat", "腾讯元宝"),
            Triple(R.id.ai_island_xinghuo, "com.iflytek.spark", "讯飞星火"),
            Triple(R.id.ai_island_qingyan, "com.zhipuai.qingyan", "智谱清言"),
            Triple(R.id.ai_island_tongyi, "com.aliyun.tongyi", "通义千问"),
            Triple(R.id.ai_island_wenxiaoyan, "com.baidu.newapp", "文小言"),
            Triple(R.id.ai_island_metaso, "com.metaso", "秘塔AI搜索"),
            Triple(R.id.ai_island_gemini, "com.google.android.apps.gemini", "Gemini"),
            Triple(R.id.ai_island_copilot, "com.microsoft.copilot", "Copilot")
        )
        
        // 为每个菜单项设置点击事件
        aiIslandConfigs.forEach { (menuId, packageName, appName) ->
            val menuItem = overlayView.findViewById<View>(menuId)
            
            // 检查应用是否已安装
            val isInstalled = try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
            
            if (isInstalled) {
                // 应用已安装，设置点击事件
                menuItem.setOnClickListener {
                    Log.d(TAG, "灵动岛: ${appName} 被点击")
                    launchAIApp(packageName, appName)
                    hideOverlay()
                }
                menuItem.visibility = View.VISIBLE
                Log.d(TAG, "灵动岛: ${appName} 已安装，设置点击事件")
            } else {
                // 应用未安装，显示但禁用
                menuItem.setOnClickListener {
                    Log.d(TAG, "灵动岛: ${appName} 未安装，显示安装提示")
                    Toast.makeText(this, "${appName} 未安装，请先安装应用", Toast.LENGTH_SHORT).show()
                }
                menuItem.visibility = View.VISIBLE
                // 设置半透明效果表示未安装
                menuItem.alpha = 0.5f
                Log.d(TAG, "灵动岛: ${appName} 未安装，设置为半透明")
            }
        }
    }
    
    /**
     * 加载灵动岛AI应用的真实图标
     */
    private fun loadIslandAIAppIcons(overlayView: View) {
        CoroutineScope(Dispatchers.Main).launch {
            // AI应用配置（使用island专用的ID）
            val aiIslandApps = listOf(
                Triple("ai.x.grok", "Grok", R.id.ai_island_grok_icon),
                Triple("ai.perplexity.app.android", "Perplexity", R.id.ai_island_perplexity_icon),
                Triple("com.poe.android", "Poe", R.id.ai_island_poe_icon),
                Triple("tech.butterfly.app", "Manus", R.id.ai_island_manus_icon),
                Triple("com.qihoo.namiso", "纳米AI", R.id.ai_island_ima_icon),
                Triple("com.deepseek.chat", "DeepSeek", R.id.ai_island_deepseek_icon),
                Triple("com.larus.nova", "豆包", R.id.ai_island_doubao_icon),
                Triple("com.openai.chatgpt", "ChatGPT", R.id.ai_island_chatgpt_icon),
                Triple("com.moonshot.kimichat", "Kimi", R.id.ai_island_kimi_icon),
                Triple("com.tencent.hunyuan.app.chat", "腾讯元宝", R.id.ai_island_yuanbao_icon),
                Triple("com.iflytek.spark", "讯飞星火", R.id.ai_island_xinghuo_icon),
                Triple("com.zhipuai.qingyan", "智谱清言", R.id.ai_island_qingyan_icon),
                Triple("com.aliyun.tongyi", "通义千问", R.id.ai_island_tongyi_icon),
                Triple("com.baidu.newapp", "文小言", R.id.ai_island_wenxiaoyan_icon),
                Triple("com.metaso", "秘塔AI搜索", R.id.ai_island_metaso_icon),
                Triple("com.google.android.apps.gemini", "Gemini", R.id.ai_island_gemini_icon),
                Triple("com.microsoft.copilot", "Copilot", R.id.ai_island_copilot_icon)
            )
            
            aiIslandApps.forEach { (packageName, appName, iconViewId) ->
                val iconView = overlayView.findViewById<ImageView>(iconViewId)
                loadAppIcon(packageName, appName, iconView)
            }
        }
    }
    
    /**
     * 创建软件tab专用悬浮窗视图
     */
    private fun createSoftwareTabOverlayView(inflater: LayoutInflater): View {
        try {
            val overlayView = inflater.inflate(R.layout.ai_software_tab_overlay, null)
            if (overlayView == null) {
                Log.e(TAG, "创建软件tab专用悬浮窗视图失败：布局文件加载返回null")
                throw RuntimeException("无法加载ai_software_tab_overlay布局")
            }
            Log.d(TAG, "✅ 创建软件tab专用悬浮窗视图成功")
            return overlayView
        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建软件tab专用悬浮窗视图失败", e)
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * 设置软件tab悬浮窗的按钮事件
     */
    private fun setupSoftwareTabOverlayButtons(overlayView: View) {
        // 返回按钮
        val backButton = overlayView.findViewById<Button>(R.id.software_tab_back_button)
        backButton?.setOnClickListener {
            Log.d(TAG, "软件tab返回按钮被点击")
            returnToSoftwareTab()
        }
        
        // App按钮
        val appButton = overlayView.findViewById<Button>(R.id.software_tab_app_button)
        appButton?.setOnClickListener {
            Log.d(TAG, "软件tab App按钮被点击")
            showRecentAppsHistory(overlayView)
        }
        
        // AI按钮
        val aiButton = overlayView.findViewById<Button>(R.id.software_tab_ai_button)
        aiButton?.setOnClickListener {
            Log.d(TAG, "软件tab AI按钮被点击")
            showAiTabOverlay()
        }
        
        // 关闭按钮
        val closeButton = overlayView.findViewById<Button>(R.id.software_tab_close_button)
        closeButton?.setOnClickListener {
            Log.d(TAG, "软件tab关闭按钮被点击")
            hideOverlay()
            stopSelf()
        }
    }
    
    /**
     * 显示AI tab悬浮窗面板
     */
    private fun showAiTabOverlay() {
        try {
            Log.d(TAG, "显示AI tab悬浮窗面板（从软件tab按钮触发）")
            
            // 获取当前剪贴板内容作为查询文本
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val currentClipboard = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            
            // 隐藏当前悬浮窗
            hideOverlay()
            
            // 启动AI tab模式的悬浮窗
            val intent = Intent(this, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
                action = com.example.aifloatingball.service.AIAppOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_APP_NAME, "AI助手")
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_QUERY, currentClipboard)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_PACKAGE_NAME, "")
                putExtra("mode", "ai_tab") // 使用ai_tab模式，显示AI应用列表
            }
            startService(intent)
            
            Log.d(TAG, "AI tab悬浮窗面板服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "显示AI tab悬浮窗面板失败", e)
            e.printStackTrace()
            Toast.makeText(this, "无法显示AI助手面板: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 创建AI tab专用悬浮窗视图
     */
    private fun createAiTabOverlayView(inflater: LayoutInflater): View {
        try {
            val overlayView = inflater.inflate(R.layout.ai_tab_overlay, null)
            if (overlayView == null) {
                Log.e(TAG, "创建AI tab专用悬浮窗视图失败：布局文件加载返回null")
                throw RuntimeException("无法加载ai_tab_overlay布局")
            }
            Log.d(TAG, "✅ 创建AI tab专用悬浮窗视图成功")
            return overlayView
        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建AI tab专用悬浮窗视图失败", e)
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * 设置AI tab悬浮窗的按钮事件
     */
    private fun setupAiTabOverlayButtons(overlayView: View) {
        // 返回按钮
        val backButton = overlayView.findViewById<Button>(R.id.ai_tab_back_button)
        backButton?.setOnClickListener {
            Log.d(TAG, "AI tab返回按钮被点击")
            returnToSoftwareTabOverlay()
        }
        
        // 关闭按钮
        val closeButton = overlayView.findViewById<Button>(R.id.ai_tab_close_button)
        closeButton?.setOnClickListener {
            Log.d(TAG, "AI tab关闭按钮被点击")
            hideOverlay()
            stopSelf()
        }
    }
    
    /**
     * 返回到software_tab悬浮窗面板
     */
    private fun returnToSoftwareTabOverlay() {
        try {
            Log.d(TAG, "返回到software_tab悬浮窗面板")
            
            // 获取当前剪贴板内容作为查询文本
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val currentClipboard = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            
            // 隐藏当前悬浮窗
            hideOverlay()
            
            // 启动software_tab模式的悬浮窗
            val intent = Intent(this, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
                action = com.example.aifloatingball.service.AIAppOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_APP_NAME, "小脑助手")
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_QUERY, currentClipboard)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_PACKAGE_NAME, "")
                putExtra("mode", "software_tab") // 使用software_tab模式
            }
            startService(intent)
            
            Log.d(TAG, "software_tab悬浮窗面板已启动")
        } catch (e: Exception) {
            Log.e(TAG, "返回software_tab悬浮窗面板失败", e)
            e.printStackTrace()
            Toast.makeText(this, "返回失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 设置AI tab AI应用点击监听器
     * 支持intent搜索或粘贴搜索
     */
    private fun setupAiTabAIAppClickListeners(overlayView: View) {
        // 获取当前查询文本（从剪贴板或传入的query）
        val currentQuery = if (query.isNotEmpty()) {
            query
        } else {
            // 尝试从剪贴板获取
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            } catch (e: Exception) {
                ""
            }
        }
        
        // 定义所有AI菜单项的配置（使用ai_tab专用的ID）
        val aiTabConfigs = listOf(
            Triple(R.id.ai_tab_grok, "ai.x.grok", "Grok"),
            Triple(R.id.ai_tab_perplexity, "ai.perplexity.app.android", "Perplexity"),
            Triple(R.id.ai_tab_poe, "com.poe.android", "Poe"),
            Triple(R.id.ai_tab_manus, "tech.butterfly.app", "Manus"),
            Triple(R.id.ai_tab_ima, "com.qihoo.namiso", "纳米AI"),
            Triple(R.id.ai_tab_deepseek, "com.deepseek.chat", "DeepSeek"),
            Triple(R.id.ai_tab_doubao, "com.larus.nova", "豆包"),
            Triple(R.id.ai_tab_chatgpt, "com.openai.chatgpt", "ChatGPT"),
            Triple(R.id.ai_tab_kimi, "com.moonshot.kimichat", "Kimi"),
            Triple(R.id.ai_tab_yuanbao, "com.tencent.hunyuan.app.chat", "腾讯元宝"),
            Triple(R.id.ai_tab_xinghuo, "com.iflytek.spark", "讯飞星火"),
            Triple(R.id.ai_tab_qingyan, "com.zhipuai.qingyan", "智谱清言"),
            Triple(R.id.ai_tab_tongyi, "com.aliyun.tongyi", "通义千问"),
            Triple(R.id.ai_tab_wenxiaoyan, "com.baidu.newapp", "文小言"),
            Triple(R.id.ai_tab_metaso, "com.metaso", "秘塔AI搜索"),
            Triple(R.id.ai_tab_gemini, "com.google.android.apps.gemini", "Gemini"),
            Triple(R.id.ai_tab_copilot, "com.microsoft.copilot", "Copilot")
        )
        
        // 为每个菜单项设置点击事件
        aiTabConfigs.forEach { (menuId, packageName, appName) ->
            val menuItem = overlayView.findViewById<View>(menuId)
            
            // 检查应用是否已安装
            val isInstalled = try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
            
            if (isInstalled) {
                // 应用已安装，设置点击事件
                menuItem.setOnClickListener {
                    Log.d(TAG, "AI tab: ${appName} 被点击，查询: $currentQuery")
                    // 使用launchAIApp方法，支持intent搜索或粘贴搜索
                    launchAIApp(packageName, appName)
                    hideOverlay()
                }
                menuItem.visibility = View.VISIBLE
                Log.d(TAG, "AI tab: ${appName} 已安装，设置点击事件")
            } else {
                // 应用未安装，显示但禁用
                menuItem.setOnClickListener {
                    Log.d(TAG, "AI tab: ${appName} 未安装，显示安装提示")
                    Toast.makeText(this, "${appName} 未安装，请先安装应用", Toast.LENGTH_SHORT).show()
                }
                menuItem.visibility = View.VISIBLE
                // 设置半透明效果表示未安装
                menuItem.alpha = 0.5f
                Log.d(TAG, "AI tab: ${appName} 未安装，设置为半透明")
            }
        }
    }
    
    /**
     * 加载AI tab AI应用的真实图标
     */
    private fun loadAiTabAIAppIcons(overlayView: View) {
        CoroutineScope(Dispatchers.Main).launch {
            // AI应用配置（使用ai_tab专用的ID）
            val aiTabApps = listOf(
                Triple("ai.x.grok", "Grok", R.id.ai_tab_grok_icon),
                Triple("ai.perplexity.app.android", "Perplexity", R.id.ai_tab_perplexity_icon),
                Triple("com.poe.android", "Poe", R.id.ai_tab_poe_icon),
                Triple("tech.butterfly.app", "Manus", R.id.ai_tab_manus_icon),
                Triple("com.qihoo.namiso", "纳米AI", R.id.ai_tab_ima_icon),
                Triple("com.deepseek.chat", "DeepSeek", R.id.ai_tab_deepseek_icon),
                Triple("com.larus.nova", "豆包", R.id.ai_tab_doubao_icon),
                Triple("com.openai.chatgpt", "ChatGPT", R.id.ai_tab_chatgpt_icon),
                Triple("com.moonshot.kimichat", "Kimi", R.id.ai_tab_kimi_icon),
                Triple("com.tencent.hunyuan.app.chat", "腾讯元宝", R.id.ai_tab_yuanbao_icon),
                Triple("com.iflytek.spark", "讯飞星火", R.id.ai_tab_xinghuo_icon),
                Triple("com.zhipuai.qingyan", "智谱清言", R.id.ai_tab_qingyan_icon),
                Triple("com.aliyun.tongyi", "通义千问", R.id.ai_tab_tongyi_icon),
                Triple("com.baidu.newapp", "文小言", R.id.ai_tab_wenxiaoyan_icon),
                Triple("com.metaso", "秘塔AI搜索", R.id.ai_tab_metaso_icon),
                Triple("com.google.android.apps.gemini", "Gemini", R.id.ai_tab_gemini_icon),
                Triple("com.microsoft.copilot", "Copilot", R.id.ai_tab_copilot_icon)
            )
            
            aiTabApps.forEach { (packageName, appName, iconViewId) ->
                val iconView = overlayView.findViewById<ImageView>(iconViewId)
                loadAppIcon(packageName, appName, iconView)
            }
        }
    }
    
    /**
     * 显示近期搜索历史的App图标
     */
    private fun showRecentAppsHistory(overlayView: View) {
        try {
            Log.d(TAG, "显示近期搜索历史的App图标")
            
            val scrollView = overlayView.findViewById<HorizontalScrollView>(R.id.recent_apps_scroll_view)
            val container = overlayView.findViewById<LinearLayout>(R.id.recent_apps_container)
            
            if (scrollView == null || container == null) {
                Log.e(TAG, "近期App容器未找到")
                return
            }
            
            // 获取近期搜索历史
            val historyManager = com.example.aifloatingball.AppSelectionHistoryManager.getInstance(this)
            val recentApps = historyManager.getRecentApps()
            
            if (recentApps.isEmpty()) {
                Toast.makeText(this, "暂无搜索历史", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 切换显示/隐藏
            val currentVisibility = scrollView.visibility
            if (currentVisibility == View.VISIBLE) {
                scrollView.visibility = View.GONE
                Log.d(TAG, "隐藏近期App列表")
            } else {
                // 清空容器
                container.removeAllViews()
                
                // 添加App图标
                recentApps.forEach { appItem ->
                    val appIconView = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            resources.getDimensionPixelSize(R.dimen.platform_icon_size),
                            resources.getDimensionPixelSize(R.dimen.platform_icon_size)
                        ).apply {
                            marginEnd = resources.getDimensionPixelSize(R.dimen.platform_icon_margin)
                        }
                        
                        // 加载应用图标
                        try {
                            val icon = packageManager.getApplicationIcon(appItem.packageName)
                            setImageDrawable(icon)
                        } catch (e: Exception) {
                            setImageResource(R.drawable.ic_search)
                        }
                        
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = ContextCompat.getDrawable(this@AIAppOverlayService, R.drawable.circle_ripple)
                        contentDescription = appItem.appName
                        
                        setOnClickListener {
                            Log.d(TAG, "点击近期App: ${appItem.appName}")
                            // 启动应用
                            try {
                                val launchIntent = packageManager.getLaunchIntentForPackage(appItem.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launchIntent)
                                } else {
                                    Toast.makeText(this@AIAppOverlayService, "无法启动 ${appItem.appName}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "启动应用失败: ${appItem.appName}", e)
                                Toast.makeText(this@AIAppOverlayService, "启动失败: ${appItem.appName}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    container.addView(appIconView)
                }
                
                scrollView.visibility = View.VISIBLE
                Log.d(TAG, "显示近期App列表，共 ${recentApps.size} 个")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示近期App历史失败", e)
            e.printStackTrace()
            Toast.makeText(this, "显示App历史失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 返回到软件tab
     */
    private fun returnToSoftwareTab() {
        try {
            Log.d(TAG, "返回到软件tab")
            
            // 启动SimpleModeActivity并切换到软件tab
            val intent = Intent(this, com.example.aifloatingball.SimpleModeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("show_app_search", true) // 标记显示软件tab（app_search_layout）
                putExtra("state", "APP_SEARCH") // 设置状态为APP_SEARCH
            }
            startActivity(intent)
            
            // 隐藏当前悬浮窗
            hideOverlay()
            stopSelf()
            
        } catch (e: Exception) {
            Log.e(TAG, "返回软件tab失败", e)
            Toast.makeText(this, "返回软件tab失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 设置拖拽监听器
     */
    private fun setupDragListener(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = (v.layoutParams as WindowManager.LayoutParams).x
                    initialY = (v.layoutParams as WindowManager.LayoutParams).y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val layoutParams = v.layoutParams as WindowManager.LayoutParams
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(v, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 执行粘贴操作
     */
    private fun performPaste() {
        try {
            Log.d(TAG, "执行粘贴操作: $query")
            
            // 将文本复制到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AI问题", query)
            clipboard.setPrimaryClip(clip)
            
            // 显示提示
            Toast.makeText(this, "文本已复制到剪贴板，请长按输入框粘贴", Toast.LENGTH_LONG).show()
            
            // 可选：尝试自动粘贴（如果无障碍服务可用）
            tryAutoPaste()
            
        } catch (e: Exception) {
            Log.e(TAG, "粘贴操作失败", e)
            Toast.makeText(this, "粘贴失败，请手动复制", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 尝试自动粘贴
     */
    private fun tryAutoPaste() {
        try {
            val accessibilityService = MyAccessibilityService.getInstance()
            if (accessibilityService != null) {
                // 发送自动粘贴请求
                val intent = Intent("com.example.aifloatingball.AUTO_PASTE").apply {
                    putExtra("package_name", packageName)
                    putExtra("query", query)
                    putExtra("app_name", appName)
                }
                sendBroadcast(intent)
                Log.d(TAG, "已发送自动粘贴请求")
            } else {
                Log.d(TAG, "无障碍服务不可用，跳过自动粘贴")
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动粘贴请求失败", e)
        }
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideOverlay() {
        if (!isOverlayVisible) {
            Log.d(TAG, "悬浮窗未显示，跳过")
            return
        }

        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
            }
            isOverlayVisible = false
            Log.d(TAG, "AI应用悬浮窗已隐藏")

            // 取消注册剪贴板监听器
            unregisterClipboardListener()

            // 停止应用切换监听
            stopAppSwitchMonitoring()

            // 同时隐藏AI菜单
            hideAIMenu()

        } catch (e: Exception) {
            Log.e(TAG, "隐藏悬浮窗失败", e)
        }
    }

    /**
     * 显示AI菜单
     */
    private fun showAIMenu() {
        if (isAIMenuVisible) {
            Log.d(TAG, "AI菜单已显示，跳过")
            return
        }

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            // 创建AI菜单视图
            aiMenuView = createAIMenuView()

            // 设置窗口参数
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )

            // 设置AI菜单位置（靠近主悬浮窗）
            val overlayParams = overlayView?.layoutParams as? WindowManager.LayoutParams
            if (overlayParams != null) {
                // 将菜单放在主悬浮窗的上方，稍微偏移
                params.gravity = Gravity.TOP or Gravity.START
                params.x = overlayParams.x
                params.y = overlayParams.y - 180 // 向上偏移180像素
            } else {
                // 如果无法获取主悬浮窗位置，则居中显示
                params.gravity = Gravity.CENTER
                params.x = 0
                params.y = -50 // 稍微向上偏移
            }

            // 添加到窗口管理器
            wm.addView(aiMenuView, params)
            isAIMenuVisible = true

            Log.d(TAG, "AI菜单已显示")

        } catch (e: Exception) {
            Log.e(TAG, "显示AI菜单失败", e)
            Toast.makeText(this, "AI菜单显示失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建AI菜单视图
     */
    private fun createAIMenuView(): View {
        val inflater = LayoutInflater.from(this)
        val menuView = inflater.inflate(R.layout.ai_menu_overlay, null)

        // 设置AI应用点击事件
        setupAIAppClickListeners(menuView)

        // 加载真实的AI应用图标
        loadAIAppIcons(menuView)

        // 设置关闭按钮
        val closeButton = menuView.findViewById<Button>(R.id.ai_menu_close)
        closeButton.setOnClickListener {
            Log.d(TAG, "AI菜单关闭按钮被点击")
            hideAIMenu()
        }

        // 设置菜单可拖拽
        setupDragListener(menuView)

        // 应用主题适配
        applyThemeToView(menuView)

        return menuView
    }

    /**
     * 设置AI应用点击监听器
     */
    private fun setupAIAppClickListeners(menuView: View) {
        // 不再依赖历史选择的应用，直接为所有AI菜单项设置点击事件

        // 定义所有AI菜单项的配置
        val aiMenuConfigs = listOf(
            // 主流AI应用
            Triple(R.id.ai_menu_grok, "ai.x.grok", "Grok"),
            Triple(R.id.ai_menu_perplexity, "ai.perplexity.app.android", "Perplexity"),
            Triple(R.id.ai_menu_poe, "com.poe.android", "Poe"),
            Triple(R.id.ai_menu_manus, "tech.butterfly.app", "Manus"),
            Triple(R.id.ai_menu_ima, "com.qihoo.namiso", "纳米AI"),
            
            // 扩展更多AI应用以支持软件tab中的所有应用
            Triple(R.id.ai_menu_deepseek, "com.deepseek.chat", "DeepSeek"),
            Triple(R.id.ai_menu_doubao, "com.larus.nova", "豆包"),
            Triple(R.id.ai_menu_chatgpt, "com.openai.chatgpt", "ChatGPT"),
            Triple(R.id.ai_menu_kimi, "com.moonshot.kimichat", "Kimi"),
            Triple(R.id.ai_menu_yuanbao, "com.tencent.hunyuan.app.chat", "腾讯元宝"),
            Triple(R.id.ai_menu_xinghuo, "com.iflytek.spark", "讯飞星火"),
            Triple(R.id.ai_menu_qingyan, "com.zhipuai.qingyan", "智谱清言"),
            Triple(R.id.ai_menu_tongyi, "com.aliyun.tongyi", "通义千问"),
            Triple(R.id.ai_menu_wenxiaoyan, "com.baidu.newapp", "文小言"),
            Triple(R.id.ai_menu_metaso, "com.metaso", "秘塔AI搜索"),
            Triple(R.id.ai_menu_gemini, "com.google.android.apps.gemini", "Gemini"),
            Triple(R.id.ai_menu_copilot, "com.microsoft.copilot", "Copilot")
        )

        // 为每个菜单项设置点击事件
        aiMenuConfigs.forEach { (menuId, packageName, appName) ->
            val menuItem = menuView.findViewById<View>(menuId)
            
            // 检查应用是否已安装
            val isInstalled = try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: Exception) {
                false
            }
            
            if (isInstalled) {
                // 应用已安装，设置点击事件
                menuItem.setOnClickListener {
                    Log.d(TAG, "${appName} 被点击")
                    launchAIApp(packageName, appName)
                    hideAIMenu()
                }
                menuItem.visibility = View.VISIBLE
                Log.d(TAG, "${appName} 已安装，设置点击事件")
            } else {
                // 应用未安装，显示但禁用
                menuItem.setOnClickListener {
                    Log.d(TAG, "${appName} 未安装，显示安装提示")
                    Toast.makeText(this, "${appName} 未安装，请先安装应用", Toast.LENGTH_SHORT).show()
                }
                menuItem.visibility = View.VISIBLE
                // 设置半透明效果表示未安装
                menuItem.alpha = 0.5f
                Log.d(TAG, "${appName} 未安装，设置为半透明")
            }
        }
    }

    /**
     * 启动AI应用
     * 使用与PlatformJumpManager相同的跳转逻辑，支持无限循环跳转
     * 修复：添加额外的应用切换检测保障机制
     */
    private fun launchAIApp(packageName: String, appName: String) {
        try {
            // 获取查询文本：优先使用传入的query，否则从剪贴板获取
            val searchQuery = if (query.isNotEmpty()) {
                query
            } else {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
            
            Log.d(TAG, "🚀 启动AI应用: $appName, 包名: $packageName, 查询: $searchQuery")
            
            // 重置无限循环跳转状态，开始新的跳转循环
            resetSwitchState()
            
            // 设置目标包名，用于后续检测
            targetPackageName = packageName
            
            // 使用与PlatformJumpManager相同的跳转逻辑
            
            // 对于特定AI应用，尝试使用Intent直接发送文本
            if (shouldTryIntentSend(appName, packageName)) {
                if (tryIntentSendForAIApp(packageName, searchQuery, appName)) {
                    return
                }
            }
            
            // 使用通用的AI应用跳转方法
            launchAIAppWithAutoPaste(packageName, searchQuery, appName)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动${appName} 失败", e)
            Toast.makeText(this, "启动${appName} 失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 判断是否应该尝试Intent发送
     */
    private fun shouldTryIntentSend(appName: String, packageName: String): Boolean {
        return when {
            appName.contains("Grok") && packageName == "ai.x.grok" -> true
            appName.contains("Perplexity") && packageName == "ai.perplexity.app.android" -> true
            appName.contains("Poe") && packageName == "com.poe.android" -> true
            appName.contains("Manus") && packageName == "tech.butterfly.app" -> true
            appName.contains("纳米AI") && packageName == "com.qihoo.namiso" -> true
            else -> false
        }
    }
    
    /**
     * 尝试使用Intent直接发送文本到AI应用
     */
    private fun tryIntentSendForAIApp(packageName: String, query: String, appName: String): Boolean {
        try {
            Log.d(TAG, "尝试Intent直接发送到${appName}: $query")
            
            // 方案1：尝试使用ACTION_SEND直接发送文本
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, query)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (sendIntent.resolveActivity(packageManager) != null) {
                startActivity(sendIntent)
                Toast.makeText(this, "正在向${appName}发送问题...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "${appName} Intent发送成功")
                
                // 隐藏当前悬浮窗和菜单
                hideOverlay()
                hideAIMenu()
                
                // 使用智能弹出时机检测
                startSmartOverlayShow(packageName, appName)
                
                return true
            }
            
                Log.d(TAG, "${appName} Intent发送失败，回退到剪贴板方案")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "${appName} Intent发送失败", e)
            return false
        }
    }
    
    /**
     * 启动AI应用并使用自动化粘贴
     * 完全参考PlatformJumpManager的实现
     */
    private fun launchAIAppWithAutoPaste(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "启动AI应用并使用自动化粘贴: ${appName}, 问题: $query")
            
            // 将问题复制到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AI问题", query)
            clipboard.setPrimaryClip(clip)
            
            // 启动AI应用
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "正在启动${appName}...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "${appName}启动成功")
                
                // 隐藏当前悬浮窗和菜单
                hideOverlay()
                hideAIMenu()
                
                // 使用智能弹出时机检测
                startSmartOverlayShow(packageName, appName)
                
            } else {
                Toast.makeText(this, "无法启动${appName}，请检查应用是否已安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动AI应用并自动粘贴失败: ${appName}", e)
            Toast.makeText(this, "启动${appName}失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 智能弹出时机检测
     * 基于应用启动状态和多重保障机制
     */
    private fun startSmartOverlayShow(packageName: String, appName: String) {
        try {
            Log.d(TAG, "🎯 开始智能弹出时机检测: $appName")
            
            // 设置目标包名
            targetPackageName = packageName
            
            // 重置尝试次数
            overlayShowAttempts = 0
            
            // 初始化Handler
            if (overlayShowHandler == null) {
                overlayShowHandler = android.os.Handler(android.os.Looper.getMainLooper())
            }
            
            // 取消之前的任务
            overlayShowRunnable?.let { runnable ->
                overlayShowHandler?.removeCallbacks(runnable)
            }
            
            // 开始智能检测
            startIntelligentDetection(packageName, appName)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 智能弹出时机检测失败", e)
            // 回退到传统延迟方式
            fallbackToDelayedShow(packageName, appName)
        }
    }
    
    /**
     * 开始智能检测
     */
    private fun startIntelligentDetection(packageName: String, appName: String) {
        overlayShowRunnable = object : Runnable {
            override fun run() {
                if (overlayShowAttempts >= maxShowAttempts) {
                    Log.w(TAG, "⚠️ 达到最大尝试次数，回退到传统方式")
                    fallbackToDelayedShow(packageName, appName)
                    return
                }
                
                overlayShowAttempts++
                Log.d(TAG, "🔍 第${overlayShowAttempts}次检测应用启动状态: $appName")
                
                // 检测应用是否在前台
                if (isAppInForeground(packageName)) {
                    Log.d(TAG, "✅ 检测到应用已在前台，立即显示悬浮窗")
                    restartOverlayForApp(packageName, appName)
                    return
                }
                
                // 额外检测：检查是否是目标应用且已启动
                if (targetPackageName == packageName && isAppRunning(packageName)) {
                    Log.d(TAG, "🎯 检测到目标应用已启动，立即显示悬浮窗")
                    restartOverlayForApp(packageName, appName)
                    return
                }
                
                // 目标应用特殊处理：即使未完全启动也尝试显示
                if (targetPackageName == packageName) {
                    Log.d(TAG, "🎯 目标应用特殊处理，延迟50ms显示悬浮窗")
                    overlayShowHandler?.postDelayed({
                        restartOverlayForApp(packageName, appName)
                    }, 50) // 目标应用使用极短延迟
                    return
                }
                
                // 检测应用是否已启动（通过包名存在性）
                if (isAppRunning(packageName)) {
                    Log.d(TAG, "✅ 检测到应用已启动，延迟100ms显示悬浮窗")
                    overlayShowHandler?.postDelayed({
                        restartOverlayForApp(packageName, appName)
                    }, 100) // 进一步减少延迟时间到100ms
                    return
                }
                
                // 继续检测
                val delay = when (overlayShowAttempts) {
                    1 -> 200L   // 第一次检测：200ms（进一步减少）
                    2 -> 400L   // 第二次检测：400ms（进一步减少）
                    3 -> 600L   // 第三次检测：600ms（进一步减少）
                    else -> 800L // 后续检测：800ms（进一步减少）
                }
                
                Log.d(TAG, "⏰ 应用未启动，${delay}ms后重试")
                overlayShowHandler?.postDelayed(this, delay)
            }
        }
        
        // 立即开始第一次检测
        overlayShowHandler?.post(overlayShowRunnable!!)
    }
    
    /**
     * 检测应用是否在前台
     * 修复：使用实时检测，不依赖currentPackageName
     */
    private fun isAppInForeground(packageName: String): Boolean {
        return try {
            val currentApp = getCurrentAppPackageName()
            val isForeground = currentApp == packageName
            Log.d(TAG, "🔍 前台检测: $packageName, 当前应用: $currentApp, 结果: $isForeground")
            isForeground
        } catch (e: Exception) {
            Log.e(TAG, "❌ 前台检测失败", e)
            false
        }
    }
    
    /**
     * 检测应用是否正在运行
     */
    private fun isAppRunning(packageName: String): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningApps = activityManager.runningAppProcesses
            
            val isRunning = runningApps?.any { it.processName == packageName } == true
            Log.d(TAG, "🔍 运行检测: $packageName, 结果: $isRunning")
            isRunning
        } catch (e: Exception) {
            Log.e(TAG, "❌ 运行检测失败", e)
            false
        }
    }
    
    /**
     * 回退到传统延迟方式
     * 提供多重保障机制
     */
    private fun fallbackToDelayedShow(packageName: String, appName: String) {
        Log.d(TAG, "🔄 回退到传统延迟方式: $appName")
        
        // 保障机制1：立即尝试显示
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🔄 保障机制1：立即尝试显示悬浮窗")
            restartOverlayForApp(packageName, appName)
        }, 100) // 进一步减少延迟时间到100ms
        
        // 保障机制2：延迟300ms再次尝试
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🔄 保障机制2：延迟300ms再次尝试")
            if (!isOverlayVisible) {
                restartOverlayForApp(packageName, appName)
            }
        }, 300) // 进一步减少延迟时间到300ms
        
        // 保障机制3：延迟600ms最后尝试
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "🔄 保障机制3：延迟600ms最后尝试")
            if (!isOverlayVisible) {
                restartOverlayForApp(packageName, appName)
            }
        }, 600) // 进一步减少延迟时间到600ms
    }

    /**
     * 重新启动悬浮窗为指定应用
     * 支持无限循环跳转功能
     */
    private fun restartOverlayForApp(packageName: String, appName: String) {
        try {
            Log.d(TAG, "🔄 重新启动悬浮窗为${appName}，支持无限循环跳转")
            
            // 更新当前服务的应用信息
            this.packageName = packageName
            this.appName = appName
            
            // 确保当前应用切换监听处于活跃状态
            if (!isAppSwitchMonitoringEnabled) {
                Log.d(TAG, "重新启用应用切换监听")
                isAppSwitchMonitoringEnabled = true
                initAppSwitchListener()
            }
            
            // 重新显示悬浮窗
            showOverlay()

            Log.d(TAG, "✅ 为${appName} 重新显示悬浮窗成功，无限循环跳转已启用")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 为${appName} 重新显示悬浮窗失败", e)
        }
    }

    /**
     * 加载AI应用的真实图标
     */
    private fun loadAIAppIcons(menuView: View) {
        CoroutineScope(Dispatchers.Main).launch {
            // AI应用配置
            val aiApps = listOf(
                // 主流AI应用
                Triple("ai.x.grok", "Grok", R.id.ai_menu_grok_icon),
                Triple("ai.perplexity.app.android", "Perplexity", R.id.ai_menu_perplexity_icon),
                Triple("com.poe.android", "Poe", R.id.ai_menu_poe_icon),
                Triple("tech.butterfly.app", "Manus", R.id.ai_menu_manus_icon),
                Triple("com.qihoo.namiso", "纳米AI", R.id.ai_menu_ima_icon),
                
                // 扩展更多AI应用
                Triple("com.deepseek.chat", "DeepSeek", R.id.ai_menu_deepseek_icon),
                Triple("com.larus.nova", "豆包", R.id.ai_menu_doubao_icon),
                Triple("com.openai.chatgpt", "ChatGPT", R.id.ai_menu_chatgpt_icon),
                Triple("com.moonshot.kimichat", "Kimi", R.id.ai_menu_kimi_icon),
                Triple("com.tencent.hunyuan.app.chat", "腾讯元宝", R.id.ai_menu_yuanbao_icon),
                Triple("com.iflytek.spark", "讯飞星火", R.id.ai_menu_xinghuo_icon),
                Triple("com.zhipuai.qingyan", "智谱清言", R.id.ai_menu_qingyan_icon),
                Triple("com.aliyun.tongyi", "通义千问", R.id.ai_menu_tongyi_icon),
                Triple("com.baidu.newapp", "文小言", R.id.ai_menu_wenxiaoyan_icon),
                Triple("com.metaso", "秘塔AI搜索", R.id.ai_menu_metaso_icon),
                Triple("com.google.android.apps.gemini", "Gemini", R.id.ai_menu_gemini_icon),
                Triple("com.microsoft.copilot", "Copilot", R.id.ai_menu_copilot_icon)
            )

            aiApps.forEach { (packageName, appName, iconViewId) ->
                val iconView = menuView.findViewById<ImageView>(iconViewId)
                loadAppIcon(packageName, appName, iconView)
            }
        }
    }

    /**
     * 加载单个应用图标
     */
    private fun loadAppIcon(packageName: String, appName: String, iconView: ImageView) {
        try {
            // 1. 优先使用已安装应用的真实图标
            val realIcon = packageManager.getApplicationIcon(packageName)
            iconView.setImageDrawable(realIcon)
            Log.d(TAG, "✅ 加载${appName} 真实图标成功")
        } catch (e: Exception) {
            // 2. 使用预设的高质量图标资源
            val iconResId = getCustomIconResourceId(appName)
            if (iconResId != 0) {
                try {
                    val customIcon = ContextCompat.getDrawable(this, iconResId)
                    iconView.setImageDrawable(customIcon)
                    Log.d(TAG, "✅ 加载${appName} 自定义图标成功")
                } catch (e2: Exception) {
                    Log.w(TAG, "⚠️ 加载${appName} 自定义图标失败，使用默认图标")
                }
            } else {
                Log.w(TAG, "⚠️ ${appName} 未安装且无自定义图标")
            }
        }
    }

    /**
     * 根据应用名称获取自定义图标资源ID
     */
    private fun getCustomIconResourceId(appName: String): Int {
        return when (appName.lowercase()) {
            "grok" -> R.drawable.ic_grok
            "perplexity" -> R.drawable.ic_perplexity
            "poe" -> R.drawable.ic_poe
            "manus" -> R.drawable.ic_manus
            "纳米ai", "ima" -> R.drawable.ic_nano_ai
            "deepseek" -> R.drawable.ic_deepseek
            "豆包", "doubao" -> R.drawable.ic_doubao
            "chatgpt" -> R.drawable.ic_chatgpt
            "kimi" -> R.drawable.ic_kimi
            "腾讯元宝", "yuanbao", "元宝" -> R.drawable.ic_yuanbao
            "讯飞星火", "xinghuo", "星火" -> R.drawable.ic_xinghuo
            "智谱清言", "qingyan", "清言" -> R.drawable.ic_zhipu_qingyan
            "通义千问", "tongyi", "千问" -> R.drawable.ic_tongyi
            "文小言", "wenxiaoyan", "小言" -> R.drawable.ic_wenxiaoyan
            "秘塔ai搜索", "metaso", "秘塔" -> R.drawable.ic_mita_ai
            "gemini" -> R.drawable.ic_gemini
            "copilot" -> R.drawable.ic_copilot
            else -> 0
        }
    }

    /**
     * 应用主题适配
     */
    private fun applyThemeToView(view: View) {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // 设置文字颜色
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK
        val backgroundColor = if (isDarkMode) Color.parseColor("#FF2C2C2C") else Color.WHITE
        val borderColor = if (isDarkMode) Color.parseColor("#FF555555") else Color.parseColor("#FFE0E0E0")
        val buttonNormalColor = if (isDarkMode) Color.parseColor("#FF404040") else Color.parseColor("#FFF5F5F5")
        val buttonPressedColor = if (isDarkMode) Color.parseColor("#FF1976D2") else Color.parseColor("#FFE3F2FD")

        // 应用到所有TextView
        applyTextColorToViews(view, textColor)

        // 应用背景色
        applyBackgroundToView(view, backgroundColor, borderColor)

        // 应用按钮背景
        applyButtonBackgrounds(view, buttonNormalColor, buttonPressedColor)
    }

    private fun applyTextColorToViews(view: View, textColor: Int) {
        if (view is TextView) {
            view.setTextColor(textColor)
        } else if (view is LinearLayout) {
            for (i in 0 until view.childCount) {
                applyTextColorToViews(view.getChildAt(i), textColor)
            }
        }
    }

    private fun applyBackgroundToView(view: View, backgroundColor: Int, borderColor: Int) {
        val background = view.background
        if (background is GradientDrawable) {
            background.setColor(backgroundColor)
            background.setStroke(2, borderColor)
        }
    }

    private fun applyButtonBackgrounds(view: View, normalColor: Int, pressedColor: Int) {
        if (view is Button) {
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(normalColor)
            }
            view.background = drawable
        } else if (view is LinearLayout) {
            for (i in 0 until view.childCount) {
                applyButtonBackgrounds(view.getChildAt(i), normalColor, pressedColor)
            }
        }
    }

    /**
     * 隐藏AI菜单
     */
    private fun hideAIMenu() {
        if (!isAIMenuVisible) {
            Log.d(TAG, "AI菜单未显示，跳过")
            return
        }

        try {
            aiMenuView?.let { view ->
                windowManager?.removeView(view)
                aiMenuView = null
            }
            isAIMenuVisible = false
            Log.d(TAG, "AI菜单已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏AI菜单失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        hideAIMenu()
        unregisterClipboardListener()
        stopAppSwitchMonitoring()
        
        // 清理智能弹出时机检测相关资源
        overlayShowRunnable?.let { runnable ->
            overlayShowHandler?.removeCallbacks(runnable)
        }
        overlayShowHandler = null
        overlayShowRunnable = null
        
        Log.d(TAG, "AI应用悬浮窗服务已销毁")
    }

    /**
     * 创建简易模式悬浮窗视图
     */
    private fun createSimpleOverlayView(@Suppress("UNUSED_PARAMETER") inflater: LayoutInflater): View {
        // 创建主容器
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)

            // 设置背景
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            background = GradientDrawable().apply {
                if (isDarkMode) {
                    setColor(Color.parseColor("#FF2C2C2C"))
                    setStroke(1, Color.parseColor("#FF555555"))
                } else {
                    setColor(Color.WHITE)
                    setStroke(1, Color.parseColor("#FFE0E0E0"))
                }
                cornerRadius = 20f
            }
        }

        // AI按钮
        val aiButton = Button(this).apply {
            id = R.id.overlay_ai_button
            text = "AI"
            textSize = 12f
            setTextColor(if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
            background = createButtonBackground()
            setPadding(24, 8, 24, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx()).apply {
                marginEnd = 8.dpToPx()
            }
        }

        // 返回按钮
        val backButton = Button(this).apply {
            id = R.id.overlay_back_button
            text = "返回"
            textSize = 12f
            setTextColor(if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
            background = createButtonBackground()
            setPadding(24, 8, 24, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx()).apply {
                marginEnd = 8.dpToPx()
            }
        }

        // 关闭按钮
        val closeButton = Button(this).apply {
            id = R.id.overlay_close_button
            text = "关闭"
            textSize = 12f
            setTextColor(if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK)
            background = createButtonBackground()
            setPadding(24, 8, 24, 8)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dpToPx())
        }

        // 添加按钮到容器
        container.addView(aiButton)
        container.addView(backButton)
        container.addView(closeButton)

        return container
    }

    /**
     * 创建按钮背景
     */
    private fun createButtonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            setColor(if (isDarkMode) Color.parseColor("#FF404040") else Color.parseColor("#FFF5F5F5"))
            cornerRadius = 6f
        }
    }

    /**
     * dp转px
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    /**
     * 获取历史选择的应用列表
     */
    private fun getRecentAppsFromPrefs(): List<AppInfo> {
        return try {
            val prefs = getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)
            val jsonString = prefs.getString("recent_apps", null)

            if (jsonString != null) {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<SerializableAppInfo>>() {}.type
                val serializableApps: List<SerializableAppInfo> = gson.fromJson(jsonString, type)

                // 转换回AppInfo并过滤有效的应用，最多返回5个
                val recentApps = mutableListOf<AppInfo>()
                serializableApps.take(5).forEach { serializableApp ->
                    val appInfo = serializableApp.toAppInfo()
                    if (appInfo != null) {
                        recentApps.add(appInfo)
                    }
                }
                recentApps
            } else {
                // 如果没有历史记录，返回默认的AI应用
                getDefaultAIApps()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载历史应用失败", e)
            getDefaultAIApps()
        }
    }

    /**
     * 获取默认的AI应用列表
     */
    private fun getDefaultAIApps(): List<AppInfo> {
        val defaultApps = mutableListOf<AppInfo>()
        val pm = packageManager

        val aiApps = listOf(
            // 主流AI应用
            "ai.x.grok" to "Grok",
            "ai.perplexity.app.android" to "Perplexity",
            "com.poe.android" to "Poe",
            "tech.butterfly.app" to "Manus",
            "com.qihoo.namiso" to "纳米AI",
            
            // 扩展更多AI应用以支持软件tab中的所有应用
            "com.deepseek.chat" to "DeepSeek",
            "com.larus.nova" to "豆包",
            "com.openai.chatgpt" to "ChatGPT",
            "com.moonshot.kimichat" to "Kimi",
            "com.tencent.hunyuan.app.chat" to "腾讯元宝",
            "com.iflytek.spark" to "讯飞星火",
            "com.zhipuai.qingyan" to "智谱清言",
            "com.aliyun.tongyi" to "通义千问",
            "com.baidu.newapp" to "文小言",
            "com.metaso" to "秘塔AI搜索",
            "com.google.android.apps.gemini" to "Gemini",
            "com.microsoft.copilot" to "Copilot"
        )

        aiApps.forEach { (packageName, appName) ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = pm.getApplicationIcon(packageName)
                val label = pm.getApplicationLabel(appInfo).toString()

                defaultApps.add(AppInfo(
                    label = label,
                    packageName = packageName,
                    icon = icon,
                    urlScheme = null
                ))
            } catch (e: Exception) {
                Log.d(TAG, "AI应用 ${appName} 未安装: $packageName")
            }
        }

        return defaultApps
    }

    /**
     * 用于序列化的简化AppInfo数据类
     */
    private data class SerializableAppInfo(
        val label: String,
        val packageName: String,
        val urlScheme: String? = null
    )

    /**
     * 从可序列化格式重建AppInfo
     */
    private fun SerializableAppInfo.toAppInfo(): AppInfo? {
        return try {
            packageManager.getPackageInfo(this.packageName, 0)
            val icon = packageManager.getApplicationIcon(this.packageName)

            AppInfo(
                label = this.label,
                packageName = this.packageName,
                icon = icon,
                urlScheme = this.urlScheme
            )
        } catch (e: Exception) {
            Log.d(TAG, "应用已卸载或无法加载: ${this.label}")
            null
        }
    }

    /**
     * 设置剪贴板预览区域
     */
    private fun setupClipboardPreview(overlayView: View) {
        try {
            Log.d(TAG, "开始设置剪贴板预览区域")
            
            val clipboardContainer = overlayView.findViewById<LinearLayout>(R.id.clipboard_preview_container)
            val clipboardContent = overlayView.findViewById<TextView>(R.id.clipboard_content)
            val clipboardCopyButton = overlayView.findViewById<Button>(R.id.clipboard_copy_button)
            val clipboardClearButton = overlayView.findViewById<Button>(R.id.clipboard_clear_button)
            
            Log.d(TAG, "剪贴板控件查找结果:")
            Log.d(TAG, "  clipboardContainer: $clipboardContainer")
            Log.d(TAG, "  clipboardContent: $clipboardContent")
            Log.d(TAG, "  clipboardCopyButton: $clipboardCopyButton")
            Log.d(TAG, "  clipboardClearButton: $clipboardClearButton")
            
            if (clipboardContainer != null && clipboardContent != null) {
                // 确保剪贴板预览区域隐藏
                clipboardContainer.visibility = View.GONE
                Log.d(TAG, "剪贴板预览区域已设置为隐藏")
                
                // 立即更新剪贴板内容
                updateClipboardContent(clipboardContent, clipboardCopyButton, clipboardClearButton)
                
                // 延迟再次更新，确保内容同步
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "延迟更新剪贴板内容")
                    updateClipboardContent(clipboardContent, clipboardCopyButton, clipboardClearButton)
                }, 200)
                
                Log.d(TAG, "剪贴板预览区域设置完成")
            } else {
                Log.e(TAG, "剪贴板预览区域控件未找到")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置剪贴板预览区域失败", e)
        }
    }

    /**
     * 更新剪贴板内容显示
     */
    private fun updateClipboardContent(contentView: TextView, copyButton: Button?, clearButton: Button?) {
        try {
            Log.d(TAG, "=== 开始更新剪贴板内容显示 ===")
            Log.d(TAG, "Android版本: ${Build.VERSION.SDK_INT}")
            Log.d(TAG, "contentView: $contentView")
            Log.d(TAG, "contentView当前文本: '${contentView.text}'")
            
            // 检查Android版本和剪贴板访问限制
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Android 10+ 检测到剪贴板访问限制")
                handleAndroid10PlusClipboard(contentView, copyButton, clearButton)
                return
            }
            
            // 获取剪贴板服务
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            Log.d(TAG, "剪贴板服务获取成功: $clipboard")
            
            // 获取剪贴板数据
            val clipData = clipboard.primaryClip
            Log.d(TAG, "剪贴板数据: $clipData")
            
            if (clipData != null) {
                Log.d(TAG, "剪贴板数据不为空，itemCount: ${clipData.itemCount}")
                
                if (clipData.itemCount > 0) {
                    val clipItem = clipData.getItemAt(0)
                    val text = clipItem.text?.toString() ?: ""
                    
                    Log.d(TAG, "剪贴板文本内容: '$text'")
                    Log.d(TAG, "剪贴板文本长度: ${text.length}")
                    Log.d(TAG, "剪贴板文本是否为空: ${text.isEmpty()}")
                    Log.d(TAG, "剪贴板文本是否包含测试内容: ${text.contains("AI悬浮球剪贴板测试")}")
                    
                    if (text.isNotEmpty()) {
                        // 显示剪贴板内容
                        Log.d(TAG, "准备设置contentView文本为: '$text'")
                        contentView.text = text
                        Log.d(TAG, "✅ contentView文本已设置为: '${contentView.text}'")
                        
                        // 验证UI更新
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            Log.d(TAG, "延迟验证 - contentView当前文本: '${contentView.text}'")
                        }, 100)
                        
                        // 设置复制按钮点击事件
                        copyButton?.setOnClickListener {
                            try {
                                val newClip = ClipData.newPlainText("复制内容", text)
                                clipboard.setPrimaryClip(newClip)
                                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "剪贴板内容已复制: $text")
                            } catch (e: Exception) {
                                Log.e(TAG, "复制剪贴板内容失败", e)
                                Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        // 设置清空按钮点击事件
                        clearButton?.setOnClickListener {
                            try {
                                val emptyClip = ClipData.newPlainText("", "")
                                clipboard.setPrimaryClip(emptyClip)
                                contentView.text = "暂无内容"
                                Toast.makeText(this, "剪贴板已清空", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "剪贴板已清空")
                            } catch (e: Exception) {
                                Log.e(TAG, "清空剪贴板失败", e)
                                Toast.makeText(this, "清空失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                        Log.d(TAG, "✅ 剪贴板内容更新完成，显示内容")
                    } else {
                        // 剪贴板内容为空字符串
                        contentView.text = "暂无内容"
                        setupEmptyClipboardButtons(copyButton, clearButton)
                        Log.d(TAG, "⚠️ 剪贴板内容为空字符串，显示暂无内容")
                    }
                } else {
                    // 剪贴板项目数量为0
                    contentView.text = "暂无内容"
                    setupEmptyClipboardButtons(copyButton, clearButton)
                    Log.d(TAG, "⚠️ 剪贴板项目数量为0，显示暂无内容")
                }
            } else {
                // 剪贴板数据为null
                contentView.text = "暂无内容"
                setupEmptyClipboardButtons(copyButton, clearButton)
                Log.d(TAG, "⚠️ 剪贴板数据为null，显示暂无内容")
            }
            
            Log.d(TAG, "=== 剪贴板内容显示更新完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 更新剪贴板内容显示失败", e)
            contentView.text = "获取失败"
            setupEmptyClipboardButtons(copyButton, clearButton)
        }
    }

    /**
     * 处理Android 10+的剪贴板访问限制
     */
    private fun handleAndroid10PlusClipboard(contentView: TextView, copyButton: Button?, clearButton: Button?) {
        try {
            Log.d(TAG, "处理Android 10+剪贴板访问限制")
            
            // 方案1：尝试通过无障碍服务获取剪贴板内容
            val accessibilityService = MyAccessibilityService.getInstance()
            if (accessibilityService != null) {
                Log.d(TAG, "无障碍服务可用，尝试获取剪贴板内容")
                
                // 通过无障碍服务获取剪贴板内容
                val clipboardContent = accessibilityService.getClipboardContent()
                if (clipboardContent != null && clipboardContent.isNotEmpty()) {
                    Log.d(TAG, "通过无障碍服务获取到剪贴板内容: '$clipboardContent'")
                    contentView.text = clipboardContent
                    setupClipboardButtons(contentView, copyButton, clearButton, clipboardContent)
                    return
                } else {
                    Log.d(TAG, "无障碍服务未获取到剪贴板内容")
                }
            } else {
                Log.d(TAG, "无障碍服务不可用")
            }
            
            // 方案2：尝试直接访问剪贴板（可能被限制）
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip
                
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotEmpty()) {
                        Log.d(TAG, "直接访问剪贴板成功: '$text'")
                        contentView.text = text
                        setupClipboardButtons(contentView, copyButton, clearButton, text)
                        return
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "直接访问剪贴板被拒绝: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "直接访问剪贴板失败: ${e.message}")
            }
            
            // 方案3：显示限制提示
            Log.d(TAG, "所有剪贴板访问方案都失败，显示限制提示")
            contentView.text = "Android 10+限制访问"
            setupRestrictedClipboardButtons(copyButton, clearButton)
            
        } catch (e: Exception) {
            Log.e(TAG, "处理Android 10+剪贴板访问失败", e)
            contentView.text = "访问失败"
            setupEmptyClipboardButtons(copyButton, clearButton)
        }
    }
    
    /**
     * 设置剪贴板按钮
     */
    private fun setupClipboardButtons(contentView: TextView, copyButton: Button?, clearButton: Button?, text: String) {
        // 设置粘贴按钮点击事件（copyButton现在用作粘贴按钮）
        copyButton?.setOnClickListener {
            try {
                Log.d(TAG, "粘贴按钮被点击，内容: $text")
                performAutoPaste(text)
            } catch (e: Exception) {
                Log.e(TAG, "粘贴操作失败", e)
                Toast.makeText(this, "粘贴失败", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置历史按钮点击事件（clearButton现在用作历史按钮）
        clearButton?.setOnClickListener {
            try {
                Log.d(TAG, "历史按钮被点击")
                showClipboardHistory()
            } catch (e: Exception) {
                Log.e(TAG, "显示历史剪贴板失败", e)
                Toast.makeText(this, "显示历史失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 设置受限剪贴板按钮
     */
    private fun setupRestrictedClipboardButtons(copyButton: Button?, clearButton: Button?) {
        // 粘贴按钮在受限状态下仍然可以尝试粘贴（copyButton现在用作粘贴按钮）
        copyButton?.setOnClickListener {
            Toast.makeText(this, "Android 10+限制剪贴板访问，请手动粘贴", Toast.LENGTH_SHORT).show()
        }
        
        // 历史按钮在受限状态下仍然可以显示历史（clearButton现在用作历史按钮）
        clearButton?.setOnClickListener {
            try {
                Log.d(TAG, "受限状态下显示剪贴板历史")
                showClipboardHistory()
            } catch (e: Exception) {
                Log.e(TAG, "受限状态下显示历史失败", e)
                Toast.makeText(this, "显示历史失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 设置空剪贴板时的按钮状态
     */
    private fun setupEmptyClipboardButtons(copyButton: Button?, clearButton: Button?) {
        // 粘贴按钮在空剪贴板时禁用（copyButton现在用作粘贴按钮）
        copyButton?.setOnClickListener {
            Toast.makeText(this, "剪贴板为空，无法粘贴", Toast.LENGTH_SHORT).show()
        }
        
        // 历史按钮在空剪贴板时仍然可以显示历史（clearButton现在用作历史按钮）
        clearButton?.setOnClickListener {
            try {
                Log.d(TAG, "空剪贴板状态下显示历史")
                showClipboardHistory()
            } catch (e: Exception) {
                Log.e(TAG, "空剪贴板状态下显示历史失败", e)
                Toast.makeText(this, "显示历史失败", Toast.LENGTH_SHORT).show()
            }
        }
        
        Log.d(TAG, "空剪贴板按钮状态已设置")
    }

    /**
     * 调试剪贴板状态
     */
    private fun debugClipboardStatus() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            
            Log.d(TAG, "=== 剪贴板状态调试 ===")
            Log.d(TAG, "clipData: $clipData")
            
            if (clipData != null) {
                Log.d(TAG, "itemCount: ${clipData.itemCount}")
                if (clipData.itemCount > 0) {
                    val clipItem = clipData.getItemAt(0)
                    val text = clipItem.text?.toString()
                    Log.d(TAG, "text: '$text'")
                    Log.d(TAG, "text length: ${text?.length ?: 0}")
                    Log.d(TAG, "text isEmpty: ${text.isNullOrEmpty()}")
                }
            } else {
                Log.d(TAG, "clipData is null")
            }
            Log.d(TAG, "========================")
        } catch (e: Exception) {
            Log.e(TAG, "调试剪贴板状态失败", e)
        }
    }

    /**
     * 更新剪贴板预览内容
     */
    private fun updateClipboardPreview() {
        try {
            Log.d(TAG, "开始更新剪贴板预览")
            debugClipboardStatus()
            
            overlayView?.let { view ->
                val clipboardContainer = view.findViewById<LinearLayout>(R.id.clipboard_preview_container)
                val clipboardContent = view.findViewById<TextView>(R.id.clipboard_content)
                val clipboardCopyButton = view.findViewById<Button>(R.id.clipboard_copy_button)
                val clipboardClearButton = view.findViewById<Button>(R.id.clipboard_clear_button)
                
                Log.d(TAG, "剪贴板控件查找结果:")
                Log.d(TAG, "  clipboardContainer: $clipboardContainer")
                Log.d(TAG, "  clipboardContent: $clipboardContent")
                Log.d(TAG, "  clipboardCopyButton: $clipboardCopyButton")
                Log.d(TAG, "  clipboardClearButton: $clipboardClearButton")
                
                if (clipboardContainer != null && clipboardContent != null) {
                    // 确保剪贴板预览区域隐藏
                    clipboardContainer.visibility = View.GONE
                    
                    // 使用新的更新方法
                    updateClipboardContent(clipboardContent, clipboardCopyButton, clipboardClearButton)
                    
                    Log.d(TAG, "剪贴板预览内容更新完成")
                } else {
                    Log.e(TAG, "剪贴板预览区域控件未找到")
                }
            } ?: run {
                Log.e(TAG, "overlayView 为空")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新剪贴板预览内容失败", e)
        }
    }
    
    /**
     * 注册剪贴板监听器
     */
    private fun registerClipboardListener() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // 先取消之前的监听器（如果存在）
            unregisterClipboardListener()
            
            clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                Log.d(TAG, "剪贴板内容发生变化，但预览已隐藏")
                // 剪贴板预览已隐藏，不再更新UI
                // android.os.Handler(android.os.Looper.getMainLooper()).post {
                //     updateClipboardPreview()
                // }
                // android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                //     updateClipboardPreview()
                // }, 100)
            }
            
            clipboard.addPrimaryClipChangedListener(clipboardListener!!)
            Log.d(TAG, "剪贴板监听器已注册")
            
            // 立即更新一次剪贴板预览（已注释）
            // updateClipboardPreview()
            
            // 测试剪贴板功能
            testClipboardFunctionality()
            
            // 手动刷新剪贴板内容（已注释）
            // android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            //     Log.d(TAG, "手动刷新剪贴板内容")
            //     updateClipboardPreview()
            // }, 1000)
            
        } catch (e: Exception) {
            Log.e(TAG, "注册剪贴板监听器失败", e)
            Toast.makeText(this, "剪贴板监听器注册失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 测试剪贴板功能
     */
    private fun testClipboardFunctionality() {
        try {
            Log.d(TAG, "开始测试剪贴板功能")
            
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // 测试1：检查剪贴板服务是否可用
            Log.d(TAG, "剪贴板服务状态: $clipboard")
            
            // 测试2：获取当前剪贴板内容
            val currentClip = clipboard.primaryClip
            Log.d(TAG, "当前剪贴板内容: $currentClip")
            
            if (currentClip != null) {
                Log.d(TAG, "剪贴板项目数量: ${currentClip.itemCount}")
                if (currentClip.itemCount > 0) {
                    val item = currentClip.getItemAt(0)
                    val text = item.text?.toString()
                    Log.d(TAG, "剪贴板文本: '$text'")
                    Log.d(TAG, "文本长度: ${text?.length ?: 0}")
                }
            }
            
            // 测试3：尝试设置测试内容
            val testText = "AI悬浮球剪贴板测试 - ${System.currentTimeMillis()}"
            val testClip = ClipData.newPlainText("测试", testText)
            clipboard.setPrimaryClip(testClip)
            Log.d(TAG, "已设置测试内容: $testText")
            
            // 延迟验证
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val verifyClip = clipboard.primaryClip
                if (verifyClip != null && verifyClip.itemCount > 0) {
                    val verifyText = verifyClip.getItemAt(0).text?.toString()
                    Log.d(TAG, "验证剪贴板内容: '$verifyText'")
                    if (verifyText == testText) {
                        Log.d(TAG, "✅ 剪贴板功能测试通过")
                        Toast.makeText(this, "剪贴板功能正常", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.w(TAG, "⚠️ 剪贴板内容验证失败")
                        Toast.makeText(this, "剪贴板内容验证失败", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "⚠️ 剪贴板内容为空")
                    Toast.makeText(this, "剪贴板内容为空", Toast.LENGTH_SHORT).show()
                }
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板功能测试失败", e)
            Toast.makeText(this, "剪贴板功能测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 取消注册剪贴板监听器
     */
    private fun unregisterClipboardListener() {
        try {
            clipboardListener?.let { listener ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.removePrimaryClipChangedListener(listener)
                clipboardListener = null
                Log.d(TAG, "剪贴板监听器已取消注册")
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消注册剪贴板监听器失败", e)
        }
    }
    
    /**
     * 执行自动复制和AI提问功能
     */
    private fun performAutoCopyAndAIQuestion() {
        try {
            Log.d(TAG, "执行自动复制和AI提问功能")
            
            // 检查是否有查询内容需要复制
            if (query.isNotEmpty()) {
                Log.d(TAG, "检测到查询内容，自动复制到剪贴板: $query")
                
                // 将查询内容复制到剪贴板
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AI问题", query)
                clipboard.setPrimaryClip(clip)
                
                Log.d(TAG, "查询内容已复制到剪贴板")
                
                // 可选：显示简短提示
                Toast.makeText(this, "问题已复制，可长按输入框粘贴", Toast.LENGTH_SHORT).show()
                
                // 可选：尝试自动粘贴（如果无障碍服务可用）
                tryAutoPaste()
            } else {
                Log.d(TAG, "没有查询内容，跳过自动复制")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "自动复制和AI提问功能执行失败", e)
        }
    }
    
    /**
     * 执行自动粘贴操作
     */
    private fun performAutoPaste(text: String) {
        try {
            Log.d(TAG, "执行自动粘贴操作: $text")
            
            // 确保内容在剪贴板中
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("粘贴内容", text)
            clipboard.setPrimaryClip(clip)
            
            // 尝试通过无障碍服务自动粘贴
            val accessibilityService = MyAccessibilityService.getInstance()
            if (accessibilityService != null) {
                Toast.makeText(this, "正在粘贴...", Toast.LENGTH_SHORT).show()
                
                // 发送自动粘贴请求
                val intent = Intent("com.example.aifloatingball.AUTO_PASTE").apply {
                    putExtra("package_name", packageName)
                    putExtra("query", text)
                    putExtra("app_name", appName)
                }
                sendBroadcast(intent)
                Log.d(TAG, "已发送自动粘贴请求")
            } else {
                Toast.makeText(this, "已复制到剪贴板，请手动粘贴", Toast.LENGTH_LONG).show()
                Log.d(TAG, "无障碍服务不可用，需要手动粘贴")
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动粘贴失败", e)
            Toast.makeText(this, "粘贴失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示剪贴板历史列表
     */
    private fun showClipboardHistory() {
        try {
            Log.d(TAG, "显示剪贴板历史列表")
            
            // 获取剪贴板历史（从SharedPreferences或数据库）
            val history = getClipboardHistory()
            
            if (history.isEmpty()) {
                Toast.makeText(this, "暂无剪贴板历史", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 显示历史列表对话框
            showClipboardHistoryDialog(history)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示剪贴板历史失败", e)
            Toast.makeText(this, "显示历史失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取剪贴板历史记录
     */
    private fun getClipboardHistory(): List<String> {
        return try {
            val prefs = getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
            val historyJson = prefs.getString("history", "[]")
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            gson.fromJson(historyJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "获取剪贴板历史失败", e)
            emptyList()
        }
    }
    
    /**
     * 显示剪贴板历史对话框
     */
    private fun showClipboardHistoryDialog(history: List<String>) {
        try {
            // 创建历史列表悬浮窗
            val historyView = createClipboardHistoryView(history)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            params.gravity = Gravity.CENTER
            
            windowManager?.addView(historyView, params)
            
            Log.d(TAG, "剪贴板历史列表已显示")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示剪贴板历史对话框失败", e)
        }
    }
    
    /**
     * 创建剪贴板历史视图
     */
    private fun createClipboardHistoryView(history: List<String>): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f
            }
        }
        
        // 标题
        val title = TextView(this).apply {
            text = "剪贴板历史"
            textSize = 18f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)
        
        // 历史列表
        history.take(10).forEach { item ->
            val itemView = TextView(this).apply {
                text = item.take(50) + if (item.length > 50) "..." else ""
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(8, 12, 8, 12)
                setOnClickListener {
                    // 点击历史项，更新剪贴板并关闭对话框
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("历史", item))
                    Toast.makeText(this@AIAppOverlayService, "已选择", Toast.LENGTH_SHORT).show()
                    windowManager?.removeView(container)
                }
            }
            container.addView(itemView)
        }
        
        // 关闭按钮
        val closeButton = Button(this).apply {
            text = "关闭"
            setOnClickListener {
                windowManager?.removeView(container)
            }
        }
        container.addView(closeButton)
        
        return container
    }

    /**
     * 返回到灵动岛输入界面
     */
    private fun returnToDynamicIsland() {
        try {
            Log.d(TAG, "返回到灵动岛输入界面")

            // 启动灵动岛服务并显示输入面板
            val intent = Intent(this, com.example.aifloatingball.service.DynamicIslandService::class.java).apply {
                action = "SHOW_INPUT_PANEL"
            }
            startService(intent)

            // 隐藏当前悬浮窗
            hideOverlay()
            stopSelf()

        } catch (e: Exception) {
            Log.e(TAG, "返回灵动岛失败", e)
        }
    }
    
    /**
     * 初始化应用切换监听器
     * 包含无限循环跳转状态管理
     */
    private fun initAppSwitchListener() {
        try {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            appSwitchHandler = android.os.Handler(android.os.Looper.getMainLooper())
            
            // 获取当前应用包名
            currentPackageName = getCurrentAppPackageName()
            
            // 重置无限循环跳转状态
            resetSwitchState()
            
            // 启动定期检查
            startAppSwitchMonitoring()
            
            Log.d(TAG, "🔄 AIAppOverlayService应用切换监听器已初始化，当前应用: $currentPackageName，无限循环跳转已启用")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化应用切换监听器失败", e)
        }
    }
    
    /**
     * 重置无限循环跳转状态
     */
    private fun resetSwitchState() {
        lastSwitchTime = 0
        switchCount = 0
        Log.d(TAG, "🔄 无限循环跳转状态已重置")
    }
    
    /**
     * 启动应用切换监控
     * 优化版本，提高检测频率和准确性
     */
    private fun startAppSwitchMonitoring() {
        if (!isAppSwitchMonitoringEnabled) return
        
        appSwitchRunnable = object : Runnable {
            override fun run() {
                checkAppSwitch()
                // 进一步提高检测频率：每200ms检查一次
                appSwitchHandler?.postDelayed(this, 200)
            }
        }
        appSwitchHandler?.post(appSwitchRunnable!!)
    }
    
    /**
     * 停止应用切换监控
     */
    private fun stopAppSwitchMonitoring() {
        try {
            appSwitchRunnable?.let { runnable ->
                appSwitchHandler?.removeCallbacks(runnable)
            }
            appSwitchRunnable = null
            Log.d(TAG, "AIAppOverlayService应用切换监听已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止应用切换监听失败", e)
        }
    }
    
    /**
     * 检查应用切换
     * 支持无限循环跳转功能，包含状态管理和防重复显示机制
     * 修复：及时更新currentPackageName，确保检测准确性
     */
    private fun checkAppSwitch() {
        if (!isAppSwitchMonitoringEnabled || !isOverlayVisible) return
        
        try {
            val newPackageName = getCurrentAppPackageName()
            
            // 如果包名发生变化，说明用户切换了应用
            if (newPackageName != null && newPackageName != currentPackageName) {
                val currentTime = System.currentTimeMillis()
                
                // 检查冷却时间，防止频繁切换（目标应用跳过冷却）
                val isTargetApp = targetPackageName == newPackageName
                if (!isTargetApp && currentTime - lastSwitchTime < switchCooldown) {
                    Log.d(TAG, "⏰ 应用切换冷却中，跳过: $currentPackageName -> $newPackageName")
                    // 即使跳过，也要更新currentPackageName，确保下次检测准确
                    currentPackageName = newPackageName
                    return
                }
                
                if (isTargetApp) {
                    Log.d(TAG, "🎯 目标应用切换，跳过冷却时间")
                }
                
                // 检查最大跳转次数，防止无限循环
                if (switchCount >= maxSwitchCount) {
                    Log.d(TAG, "⚠️ 已达到最大跳转次数 ($maxSwitchCount)，停止无限循环跳转")
                    Toast.makeText(this, "已达到最大跳转次数，请手动重启服务", Toast.LENGTH_LONG).show()
                    // 即使停止跳转，也要更新currentPackageName
                    currentPackageName = newPackageName
                    return
                }
                
                Log.d(TAG, "🔄 AIAppOverlayService检测到应用切换: $currentPackageName -> $newPackageName (第${switchCount + 1}次)")
                
                // 检查是否切换到了支持的应用（所有应用都支持无限循环跳转）
                if (isSupportedPackage(newPackageName)) {
                    Log.d(TAG, "✅ 检测到切换到支持的应用: $newPackageName，重新显示悬浮窗（无限循环跳转）")
                    
                    // 更新状态管理变量
                    lastSwitchTime = currentTime
                    switchCount++
                    
                    // 更新当前包名和应用信息
                    packageName = newPackageName
                    appName = getAppNameFromPackage(newPackageName)
                    
                    // 立即更新currentPackageName，确保后续检测准确
                    currentPackageName = newPackageName
                    
                    // 检查是否是目标应用（从AIAppOverlayService跳转的应用）
                    val isTargetApp = targetPackageName == newPackageName
                    if (isTargetApp) {
                        Log.d(TAG, "🎯 检测到目标应用切换，立即显示悬浮窗")
                        // 立即显示悬浮窗，不需要延迟
                        hideOverlay()
                        showOverlay()
                        Log.d(TAG, "🔄 目标应用悬浮窗已立即显示: $appName")
                    } else {
                        // 重新显示悬浮窗，支持无限循环
                        hideOverlay()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            showOverlay()
                            Log.d(TAG, "🔄 悬浮窗已重新显示，支持无限循环跳转到: $appName (第${switchCount}次跳转)")
                        }, 100) // 进一步减少延迟时间到100ms
                    }
                } else {
                    Log.d(TAG, "⚠️ 切换到不支持的应用: $newPackageName，保持悬浮窗显示")
                    // 即使不支持，也要更新currentPackageName
                    currentPackageName = newPackageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查应用切换失败", e)
        }
    }
    
    /**
     * 获取当前前台应用的包名
     * 增强版本，提高检测准确性和响应速度
     */
    private fun getCurrentAppPackageName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val time = System.currentTimeMillis()
                
                // 使用更短的时间间隔提高响应速度
                val usageStats = usageStatsManager?.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    time - 300, // 进一步缩短到300ms，提高响应速度
                    time
                )
                
                // 获取最近使用的应用
                val currentApp = usageStats?.maxByOrNull { it.lastTimeUsed }
                
                if (currentApp != null) {
                    Log.d(TAG, "🔍 检测到当前应用: ${currentApp.packageName}, 最后使用时间: ${currentApp.lastTimeUsed}")
                    currentApp.packageName
                } else {
                    Log.d(TAG, "⚠️ 未检测到当前应用")
                    null
                }
            } else {
                Log.d(TAG, "⚠️ Android版本过低，不支持UsageStats")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取当前应用包名失败", e)
            null
        }
    }
    
    /**
     * 检查是否是支持的应用包名（支持所有应用，实现无限循环跳转）
     * 优化版本，确保无限循环跳转的稳定性
     */
    private fun isSupportedPackage(packageName: String): Boolean {
        // 排除一些系统应用和启动器，避免干扰无限循环跳转
        val excludedPackages = listOf(
            "com.android.systemui", // 系统UI
            "com.android.launcher3", // 启动器
            "com.android.launcher", // 启动器
            "com.miui.home", // MIUI启动器
            "com.huawei.android.launcher", // 华为启动器
            "com.oppo.launcher", // OPPO启动器
            "com.vivo.launcher", // VIVO启动器
            "com.samsung.android.launcher", // 三星启动器
            "com.android.packageinstaller", // 包安装器
            "com.android.vending", // Google Play
            "com.android.providers.downloads", // 下载管理器
            "com.android.settings", // 设置（可选排除）
            "com.android.keychain", // 密钥链
            "com.android.providers.media", // 媒体提供者
            "com.android.providers.contacts", // 联系人提供者
            "com.android.providers.calendar", // 日历提供者
            "com.android.providers.telephony", // 电话提供者
            "com.android.providers.settings", // 设置提供者
            "com.android.providers.userdictionary", // 用户字典提供者
            "com.android.providers.blockednumber", // 阻止号码提供者
            "com.android.providers.downloads.ui", // 下载UI
            "com.android.providers.media.module", // 媒体模块
            "com.android.providers.contacts.module", // 联系人模块
            "com.android.providers.calendar.module", // 日历模块
            "com.android.providers.telephony.module", // 电话模块
            "com.android.providers.settings.module", // 设置模块
            "com.android.providers.userdictionary.module", // 用户字典模块
            "com.android.providers.blockednumber.module", // 阻止号码模块
        )
        
        // 如果是不支持的系统应用，返回false
        if (excludedPackages.contains(packageName)) {
            Log.d(TAG, "⚠️ 应用 $packageName 被排除，不支持无限循环跳转")
            return false
        }
        
        // 其他所有应用都支持悬浮窗和无限循环跳转
        Log.d(TAG, "✅ 应用 $packageName 支持无限循环跳转")
        return true
    }
    
    /**
     * 检查是否是AI应用包名
     */
    private fun isAIPackage(packageName: String): Boolean {
        val aiPackages = listOf(
            // 自己的应用
            "com.example.aifloatingball",
            
            // 主流AI应用
            "com.openai.chatgpt", // ChatGPT
            "com.anthropic.claude", // Claude
            "com.google.android.apps.bard", // Bard/Gemini
            "com.baidu.wenxin", // 文心一言
            "com.alibaba.dingtalk", // 钉钉（通义千问）
            "com.iflytek.voiceassistant", // 讯飞星火
            "com.moonshot.kimi", // Kimi
            "com.zhipuai.zhipu", // 智谱AI
            "com.deepseek.deepseek", // DeepSeek
            
            // 其他AI相关应用
            "com.microsoft.cortana", // Cortana
            "com.amazon.alexa", // Alexa
            "com.samsung.bixby", // Bixby
            "com.huawei.hiaiserver", // 小艺
            "com.xiaomi.miui.voiceassistant", // 小爱同学
            "com.oppo.voiceassistant", // Breeno
            "com.vivo.voiceassistant", // Jovi
            
            // 聊天应用
            "com.tencent.mm", // 微信
            "com.tencent.mobileqq", // QQ
            "com.alibaba.android.rimet", // 钉钉
            "com.tencent.wework", // 企业微信
            "com.skype.raider", // Skype
            "com.whatsapp", // WhatsApp
            "com.telegram.messenger", // Telegram
            "com.discord", // Discord
            "com.slack.android", // Slack
            
            // 浏览器应用
            "com.android.chrome", // Chrome
            "com.UCMobile", // UC浏览器
            "com.tencent.mtt", // QQ浏览器
            "com.baidu.browser.apps", // 百度浏览器
            "com.qihoo.browser", // 360浏览器
            "com.sogou.mobile.explorer", // 搜狗浏览器
            "com.opera.browser", // Opera
            "org.mozilla.firefox", // Firefox
            "com.microsoft.emmx", // Edge
            
            // 笔记和办公应用
            "com.evernote", // Evernote
            "com.notion.id", // Notion
            "com.microsoft.office.officehub", // Office
            "com.google.android.apps.docs", // Google Docs
            "com.google.android.apps.sheets", // Google Sheets
            "com.google.android.apps.slides", // Google Slides
            "com.wps.moffice_eng", // WPS Office
            "com.kingsoft.moffice_pro", // 金山办公
            
            // 社交媒体应用
            "com.ss.android.ugc.aweme", // 抖音
            "com.sina.weibo", // 微博
            "com.zhihu.android", // 知乎
            "com.xingin.xhs", // 小红书
            "com.ss.android.ugc.live", // 快手
            "com.tencent.news", // 腾讯新闻
            "com.netease.newsreader.activity", // 网易新闻
            "com.sohu.newsclient", // 搜狐新闻
            
            // 学习和教育应用
            "com.xueersi.parentsmeeting", // 学而思
            "com.tencent.edu", // 腾讯课堂
            "com.zhongxue.app", // 作业帮
            "com.baidu.homework", // 百度作业
            "com.yuanfudao.student", // 猿辅导
            
            // 购物应用
            "com.taobao.taobao", // 淘宝
            "com.tmall.wireless", // 天猫
            "com.jingdong.app.mall", // 京东
            "com.pinduoduo", // 拼多多
            "com.suning.mobile.ebuy", // 苏宁易购
            
            // 视频应用
            "com.tencent.qqlive", // 腾讯视频
            "com.iqiyi.hd", // 爱奇艺
            "com.youku.phone", // 优酷
            "com.bilibili.app.in", // B站
            "com.ss.android.ugc.aweme", // 抖音
            
            // 音乐应用
            "com.netease.cloudmusic", // 网易云音乐
            "com.tencent.qqmusic", // QQ音乐
            "com.kugou.android", // 酷狗音乐
            "com.kuwo.cn", // 酷我音乐
            "com.xiami.music", // 虾米音乐
            
            // 地图和出行应用
            "com.autonavi.minimap", // 高德地图
            "com.baidu.BaiduMap", // 百度地图
            "com.tencent.map", // 腾讯地图
            "com.didi.global", // 滴滴出行
            "com.ubercab", // Uber
            
            // 金融应用
            "com.eg.android.AlipayGphone", // 支付宝
            "com.tencent.mm", // 微信支付
            "com.icbc", // 工商银行
            "com.ccb.ccbnetpay", // 建设银行
            "com.abchina.wallet", // 农业银行
            
            // 工具应用
            "com.adobe.reader", // Adobe Reader
            "com.microsoft.office.outlook", // Outlook
            "com.google.android.gm", // Gmail
            "com.android.email", // 邮件
            "com.android.calendar", // 日历
            "com.android.contacts", // 联系人
            "com.android.mms", // 短信
            "com.android.dialer", // 电话
            
            // 游戏应用（部分主流游戏）
            "com.tencent.tmgp.sgame", // 王者荣耀
            "com.tencent.tmgp.pubgmhd", // 和平精英
            "com.miHoYo.GenshinImpact", // 原神
            "com.netease.hyxd", // 荒野行动
            "com.supercell.clashofclans", // 部落冲突
            "com.supercell.clashroyale", // 皇室战争
            
            // 系统应用
            "com.android.settings", // 设置
            "com.android.launcher3", // 启动器
            "com.android.systemui", // 系统UI
            "com.android.packageinstaller", // 包安装器
            "com.android.vending", // Google Play
            "com.android.providers.downloads", // 下载管理器
        )
        return aiPackages.contains(packageName)
    }
    
    /**
     * 根据包名获取应用名称
     */
    private fun getAppNameFromPackage(packageName: String): String {
        return when (packageName) {
            // 自己的应用
            "com.example.aifloatingball" -> "AI悬浮球"
            
            // 主流AI应用
            "com.openai.chatgpt" -> "ChatGPT"
            "com.anthropic.claude" -> "Claude"
            "com.google.android.apps.bard" -> "Gemini"
            "com.baidu.wenxin" -> "文心一言"
            "com.alibaba.dingtalk" -> "通义千问"
            "com.iflytek.voiceassistant" -> "讯飞星火"
            "com.moonshot.kimi" -> "Kimi"
            "com.zhipuai.zhipu" -> "智谱AI"
            "com.deepseek.deepseek" -> "DeepSeek"
            
            // 其他AI相关应用
            "com.microsoft.cortana" -> "Cortana"
            "com.amazon.alexa" -> "Alexa"
            "com.samsung.bixby" -> "Bixby"
            "com.huawei.hiaiserver" -> "小艺"
            "com.xiaomi.miui.voiceassistant" -> "小爱同学"
            "com.oppo.voiceassistant" -> "Breeno"
            "com.vivo.voiceassistant" -> "Jovi"
            
            // 聊天应用
            "com.tencent.mm" -> "微信"
            "com.tencent.mobileqq" -> "QQ"
            "com.alibaba.android.rimet" -> "钉钉"
            "com.tencent.wework" -> "企业微信"
            "com.skype.raider" -> "Skype"
            "com.whatsapp" -> "WhatsApp"
            "com.telegram.messenger" -> "Telegram"
            "com.discord" -> "Discord"
            "com.slack.android" -> "Slack"
            
            // 浏览器应用
            "com.android.chrome" -> "Chrome"
            "com.UCMobile" -> "UC浏览器"
            "com.tencent.mtt" -> "QQ浏览器"
            "com.baidu.browser.apps" -> "百度浏览器"
            "com.qihoo.browser" -> "360浏览器"
            "com.sogou.mobile.explorer" -> "搜狗浏览器"
            "com.opera.browser" -> "Opera"
            "org.mozilla.firefox" -> "Firefox"
            "com.microsoft.emmx" -> "Edge"
            
            // 笔记和办公应用
            "com.evernote" -> "Evernote"
            "com.notion.id" -> "Notion"
            "com.microsoft.office.officehub" -> "Office"
            "com.google.android.apps.docs" -> "Google Docs"
            "com.google.android.apps.sheets" -> "Google Sheets"
            "com.google.android.apps.slides" -> "Google Slides"
            "com.wps.moffice_eng" -> "WPS Office"
            "com.kingsoft.moffice_pro" -> "金山办公"
            
            // 社交媒体应用
            "com.ss.android.ugc.aweme" -> "抖音"
            "com.sina.weibo" -> "微博"
            "com.zhihu.android" -> "知乎"
            "com.xingin.xhs" -> "小红书"
            "com.ss.android.ugc.live" -> "快手"
            "com.tencent.news" -> "腾讯新闻"
            "com.netease.newsreader.activity" -> "网易新闻"
            "com.sohu.newsclient" -> "搜狐新闻"
            
            // 学习和教育应用
            "com.xueersi.parentsmeeting" -> "学而思"
            "com.tencent.edu" -> "腾讯课堂"
            "com.zhongxue.app" -> "作业帮"
            "com.baidu.homework" -> "百度作业"
            "com.yuanfudao.student" -> "猿辅导"
            
            // 购物应用
            "com.taobao.taobao" -> "淘宝"
            "com.tmall.wireless" -> "天猫"
            "com.jingdong.app.mall" -> "京东"
            "com.pinduoduo" -> "拼多多"
            "com.suning.mobile.ebuy" -> "苏宁易购"
            
            // 视频应用
            "com.tencent.qqlive" -> "腾讯视频"
            "com.iqiyi.hd" -> "爱奇艺"
            "com.youku.phone" -> "优酷"
            "com.bilibili.app.in" -> "B站"
            
            // 音乐应用
            "com.netease.cloudmusic" -> "网易云音乐"
            "com.tencent.qqmusic" -> "QQ音乐"
            "com.kugou.android" -> "酷狗音乐"
            "com.kuwo.cn" -> "酷我音乐"
            "com.xiami.music" -> "虾米音乐"
            
            // 地图和出行应用
            "com.autonavi.minimap" -> "高德地图"
            "com.baidu.BaiduMap" -> "百度地图"
            "com.tencent.map" -> "腾讯地图"
            "com.didi.global" -> "滴滴出行"
            "com.ubercab" -> "Uber"
            
            // 金融应用
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.icbc" -> "工商银行"
            "com.ccb.ccbnetpay" -> "建设银行"
            "com.abchina.wallet" -> "农业银行"
            
            // 工具应用
            "com.adobe.reader" -> "Adobe Reader"
            "com.microsoft.office.outlook" -> "Outlook"
            "com.google.android.gm" -> "Gmail"
            "com.android.email" -> "邮件"
            "com.android.calendar" -> "日历"
            "com.android.contacts" -> "联系人"
            "com.android.mms" -> "短信"
            "com.android.dialer" -> "电话"
            
            // 游戏应用
            "com.tencent.tmgp.sgame" -> "王者荣耀"
            "com.tencent.tmgp.pubgmhd" -> "和平精英"
            "com.miHoYo.GenshinImpact" -> "原神"
            "com.netease.hyxd" -> "荒野行动"
            "com.supercell.clashofclans" -> "部落冲突"
            "com.supercell.clashroyale" -> "皇室战争"
            
            // 系统应用
            "com.android.settings" -> "设置"
            "com.android.launcher3" -> "启动器"
            "com.android.systemui" -> "系统UI"
            "com.android.packageinstaller" -> "包安装器"
            "com.android.vending" -> "Google Play"
            "com.android.providers.downloads" -> "下载管理器"
            
            // 默认返回包名
            else -> {
                // 尝试从包名中提取应用名称
                val packageParts = packageName.split(".")
                if (packageParts.size > 1) {
                    val lastPart = packageParts.last()
                    // 将包名转换为更友好的显示名称
                    lastPart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    packageName
                }
            }
        }
    }
}
