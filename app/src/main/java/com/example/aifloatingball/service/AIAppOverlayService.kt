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
    
    // 剪贴板监听器
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务以绕过Android 10+的剪贴板访问限制
        startForegroundService()
        
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                query = intent.getStringExtra(EXTRA_QUERY) ?: ""
                packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                isSimpleMode = intent.getBooleanExtra("mode", false) || intent.getStringExtra("mode") == "simple"
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

                Log.d(TAG, "显示悬浮窗: $appName, 简易模式: $isSimpleMode")
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
        if (isOverlayVisible) {
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
        val overlayView = if (isSimpleMode || isOverlayMode) {
            // 简易模式或overlay模式：创建简化的悬浮窗
            createSimpleOverlayView(inflater)
        } else {
            // 完整模式：使用原有布局
            inflater.inflate(R.layout.ai_app_overlay, null)
        }
        
        // 设置应用名称（仅在完整模式下）
        val appNameText = overlayView.findViewById<TextView>(R.id.overlay_app_name)
        appNameText?.text = appName
        
        // 设置剪贴板预览区域（暂时隐藏）
        // setupClipboardPreview(overlayView)
        
        // 保留自动复制和AI提问功能
        performAutoCopyAndAIQuestion()
        
        if (isSimpleMode) {
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
            Triple(R.id.ai_menu_grok, "ai.x.grok", "Grok"),
            Triple(R.id.ai_menu_perplexity, "ai.perplexity.app.android", "Perplexity"),
            Triple(R.id.ai_menu_poe, "com.poe.android", "Poe"),
            Triple(R.id.ai_menu_manus, "tech.butterfly.app", "Manus"),
            Triple(R.id.ai_menu_ima, "com.qihoo.namiso", "纳米AI")
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
     * 使用与PlatformJumpManager相同的跳转逻辑
     */
    private fun launchAIApp(packageName: String, appName: String) {
        try {
            Log.d(TAG, "启动AI应用: $appName, 包名: $packageName, 查询: $query")
            
            // 使用与PlatformJumpManager相同的跳转逻辑
            
            // 对于特定AI应用，尝试使用Intent直接发送文本
            if (shouldTryIntentSend(appName, packageName)) {
                if (tryIntentSendForAIApp(packageName, query, appName)) {
                    return
                }
            }
            
            // 使用通用的AI应用跳转方法
            launchAIAppWithAutoPaste(packageName, query, appName)
            
        } catch (e: Exception) {
            Log.e(TAG, "启动${appName} 失败", e)
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
                
                // 延迟显示悬浮窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    restartOverlayForApp(packageName, appName)
                }, 2000)
                
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
                
                // 延迟显示悬浮窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    restartOverlayForApp(packageName, appName)
                }, 2000)
                
            } else {
                Toast.makeText(this, "无法启动${appName}，请检查应用是否已安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动AI应用并自动粘贴失败: ${appName}", e)
            Toast.makeText(this, "启动${appName}失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 重新启动悬浮窗为指定应用
     */
    private fun restartOverlayForApp(packageName: String, appName: String) {
        try {
            // 更新当前服务的应用信息
            this.packageName = packageName
            this.appName = appName

            // 重新显示悬浮窗
            showOverlay()

            Log.d(TAG, "为${appName} 重新显示悬浮窗")
        } catch (e: Exception) {
            Log.e(TAG, "为${appName} 重新显示悬浮窗失败", e)
        }
    }

    /**
     * 加载AI应用的真实图标
     */
    private fun loadAIAppIcons(menuView: View) {
        CoroutineScope(Dispatchers.Main).launch {
            // AI应用配置
            val aiApps = listOf(
                Triple("ai.x.grok", "Grok", R.id.ai_menu_grok_icon),
                Triple("ai.perplexity.app.android", "Perplexity", R.id.ai_menu_perplexity_icon),
                Triple("com.poe.android", "Poe", R.id.ai_menu_poe_icon),
                Triple("tech.butterfly.app", "Manus", R.id.ai_menu_manus_icon),
                Triple("com.qihoo.namiso", "纳米AI", R.id.ai_menu_ima_icon)
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
            "ai.x.grok" to "Grok",
            "ai.perplexity.app.android" to "Perplexity",
            "com.poe.android" to "Poe",
            "tech.butterfly.app" to "Manus",
            "com.tencent.ima" to "IMA"
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
}
