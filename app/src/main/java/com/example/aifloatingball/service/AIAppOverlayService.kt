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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        // 获取历史选择的应用
        val recentApps = getRecentAppsFromPrefs()

        // 获取所有AI菜单项的ID
        val aiMenuIds = listOf(
            R.id.ai_menu_grok,
            R.id.ai_menu_perplexity,
            R.id.ai_menu_poe,
            R.id.ai_menu_manus,
            R.id.ai_menu_ima
        )

        // 为每个菜单项设置点击事件
        aiMenuIds.forEachIndexed { index, menuId ->
            val menuItem = menuView.findViewById<View>(menuId)
            if (index < recentApps.size) {
                val appInfo = recentApps[index]
                menuItem.setOnClickListener {
                    Log.d(TAG, "${appInfo.label}被点击")
                    launchAIApp(appInfo.packageName, appInfo.label)
                    hideAIMenu()
                }
                menuItem.visibility = View.VISIBLE
            } else {
                // 如果历史应用不足5个，隐藏多余的菜单项
                menuItem.visibility = View.GONE
            }
        }
    }

    /**
     * 启动AI应用
     */
    private fun launchAIApp(packageName: String, appName: String) {
        try {
            // 将查询内容复制到剪贴板
            if (query.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AI问题", query)
                clipboard.setPrimaryClip(clip)
                Log.d(TAG, "查询内容已复制到剪贴板: $query")
            }

            // 启动AI应用
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "正在启动$appName...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ 成功启动$appName")

                // 隐藏当前悬浮窗和菜单
                hideOverlay()
                hideAIMenu()

                // 延迟3秒后为新启动的AI应用显示悬浮窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // 重新启动悬浮窗服务，而不是创建新实例
                    restartOverlayForApp(packageName, appName)
                }, 3000)

            } else {
                Toast.makeText(this, "$appName 未安装", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "⚠️ $appName 未安装: $packageName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动$appName 失败", e)
            Toast.makeText(this, "启动$appName 失败", Toast.LENGTH_SHORT).show()
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

            Log.d(TAG, "为$appName 重新显示悬浮窗")
        } catch (e: Exception) {
            Log.e(TAG, "为$appName 重新显示悬浮窗失败", e)
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
            Log.d(TAG, "✅ 加载$appName 真实图标成功")
        } catch (e: Exception) {
            // 2. 使用预设的高质量图标资源
            val iconResId = getCustomIconResourceId(appName)
            if (iconResId != 0) {
                try {
                    val customIcon = ContextCompat.getDrawable(this, iconResId)
                    iconView.setImageDrawable(customIcon)
                    Log.d(TAG, "✅ 加载$appName 自定义图标成功")
                } catch (e2: Exception) {
                    Log.w(TAG, "⚠️ 加载$appName 自定义图标失败，使用默认图标")
                }
            } else {
                Log.w(TAG, "⚠️ $appName 未安装且无自定义图标")
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
        Log.d(TAG, "AI应用悬浮窗服务已销毁")
    }

    /**
     * 创建简易模式悬浮窗视图
     */
    private fun createSimpleOverlayView(inflater: LayoutInflater): View {
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
                Log.d(TAG, "AI应用 $appName 未安装: $packageName")
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
